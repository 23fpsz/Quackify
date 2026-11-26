package com.quackify.backend.library.lyrics

import android.content.Context
import com.quackify.backend.library.lyrics.LyricsTranslation.MAX_BATCH_CHARS
import com.quackify.backend.library.lyrics.LyricsTranslation.MAX_CACHE_BYTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * On-demand lyric translation
 *
 * Translations are fetched from Google Translate's public web endpoint
 * (client=dict-chrome-ex) in batches: lines are joined with a private-use
 * marker between them, one request covers up to [MAX_BATCH_CHARS] characters,
 * and two requests run at a time so a long lyric stays fast without
 * competing with playback for every connection.
 *
 * Results are kept in a bounded cache (memory + gzipped files under the
 * app's cache directory, capped at [MAX_CACHE_BYTES]) so repeat listens
 * don't pay for the same translation again and Android may reclaim the
 * space when it needs it.
 */
object LyricsTranslation {

    sealed interface Result {
        data class Translated(
            val texts: List<String>,
            val sourceLanguage: String,
            val fromCache: Boolean,
        ) : Result

        data class SameLanguage(val language: String) : Result
        data object Unavailable : Result
    }

    private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
    private const val CACHE_DIRECTORY = "lyrics_translation_v1"
    private const val CACHE_VERSION = 1
    private const val MAX_CACHE_BYTES = 2L * 1024L * 1024L
    private const val MAX_BATCH_CHARS = 3_500
    private const val MAX_PARALLEL_REQUESTS = 2
    private val markerRegex = Regex("\\uE000[^\\uE001]*\\uE001")

    private val diskMutex = Mutex()
    private val memory = object {
        private val entries = object : LinkedHashMap<String, CachedTranslation>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedTranslation>) =
                size > 12
        }

        @Synchronized
        fun get(key: String): CachedTranslation? = entries[key]

        @Synchronized
        fun put(key: String, value: CachedTranslation) {
            entries[key] = value
        }
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .callTimeout(12, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private class CachedTranslation(
        val version: Int = CACHE_VERSION,
        val sourceLanguage: String,
        val targetLanguage: String,
        val texts: List<String>,
    )

    private data class Slot(val lineIndex: Int, val text: String)

    private data class Batch(val slots: List<Slot>, val payload: String)

    private data class BatchAnswer(
        val translations: List<String>,
        val sourceLanguage: String,
        val sourceWeight: Int,
    )

    suspend fun translate(
        context: Context,
        trackId: String,
        lines: List<String>,
        targetLanguageTag: String,
    ): Result {
        val target = targetLanguageTag.trim()
        if (target.isBlank() || lines.isEmpty()) return Result.Unavailable

        val slots = lines.mapIndexed { index, text -> Slot(index, text) }.filter { it.text.isNotBlank() }
        if (slots.isEmpty()) return Result.Unavailable

        val cacheKey = cacheKey(trackId, target, slots)
        val cached = memory.get(cacheKey) ?: readCache(context, cacheKey)?.also {
            memory.put(cacheKey, it)
        }
        if (cached != null && cached.version == CACHE_VERSION && cached.texts.size == slots.size) {
            return if (sameLanguage(cached.sourceLanguage, target)) {
                Result.SameLanguage(cached.sourceLanguage)
            } else {
                Result.Translated(cached.texts, cached.sourceLanguage, fromCache = true)
            }
        }

        val batches = batches(slots)
        val answers = coroutineScope {
            batches.chunked(MAX_PARALLEL_REQUESTS).flatMap { group ->
                group.map { batch -> async { requestBatch(batch, target) } }.awaitAll()
            }
        }
        if (answers.any { it == null }) return Result.Unavailable
        val complete = answers.filterNotNull()
        val source = complete
            .groupBy { canonicalLanguage(it.sourceLanguage) }
            .maxByOrNull { (_, values) -> values.sumOf { it.sourceWeight } }
            ?.key
            .orEmpty()
        if (source.isBlank()) return Result.Unavailable

        val translated = complete.flatMap { it.translations }
        if (translated.size != slots.size) return Result.Unavailable
        if (translated.any { it.isBlank() }) return Result.Unavailable

        val entry = CachedTranslation(
            sourceLanguage = source,
            targetLanguage = target,
            texts = translated,
        )
        memory.put(cacheKey, entry)
        writeCache(context, cacheKey, entry)

        return if (sameLanguage(source, target)) {
            Result.SameLanguage(source)
        } else {
            Result.Translated(translated, source, fromCache = false)
        }
    }

    private fun batches(slots: List<Slot>): List<Batch> {
        val result = mutableListOf<Batch>()
        var current = mutableListOf<Slot>()
        var length = 0

        fun flush() {
            if (current.isEmpty()) return
            result += Batch(current.toList(), payload(current))
            current = mutableListOf()
            length = 0
        }

        slots.forEach { slot ->
            val added = slot.text.length + if (current.isEmpty()) 0 else 8
            if (current.isNotEmpty() && length + added > MAX_BATCH_CHARS) flush()
            current += slot
            length += added
        }
        flush()
        return result
    }

    private fun payload(slots: List<Slot>): String = buildString {
        slots.forEachIndexed { index, slot ->
            if (index > 0) append('\n').append(marker(index)).append('\n')
            append(slot.text)
        }
    }

    private fun marker(index: Int): String = "\uE000${index.toString().padStart(4, '0')}\uE001"

    /**
     * One blocking request. Always called on [Dispatchers.IO]: the call sites
     * launch batches with `async { }` inheriting a main-thread context, and
     * Android refuses sockets there — the NetworkOnMainThreadException was
     * swallowed by the runCatching below, which read as "translation
     * unavailable" with no trace of a request ever being made.
     */
    private suspend fun requestBatch(batch: Batch, target: String): BatchAnswer? =
        withContext(Dispatchers.IO) {
            runCatching {
                val form = FormBody.Builder()
                    .add("client", "dict-chrome-ex")
                    .add("sl", "auto")
                    .add("tl", target)
                    .add("dt", "t")
                    .add("q", batch.payload)
                    .build()
                val request = Request.Builder()
                    .url(ENDPOINT)
                    .header("User-Agent", "Quackify/1.0")
                    .header("Accept", "application/json")
                    .post(form)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val body = response.body.string()
                    val root = JSONArray(body)
                    val translatedBody = buildString {
                        for (i in 0 until root.getJSONArray(0).length()) {
                            append(root.getJSONArray(0).getJSONArray(i).optString(0))
                        }
                    }
                    val source = root.opt(2)?.let { it as? String ?: "" } ?: ""
                    val parts = translatedBody.split(markerRegex).map(String::trim)
                    if (source.isBlank() || parts.size != batch.slots.size || parts.any { it.isBlank() }) {
                        return@runCatching null
                    }
                    BatchAnswer(parts, source, batch.payload.length)
                }
            }.getOrNull()
        }

    fun canonicalLanguage(tag: String): String {
        val normalized = tag.replace('_', '-')
        val language = Locale.forLanguageTag(normalized).language.lowercase(Locale.ROOT)
        return when (language) {
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            else -> language
        }
    }

    fun sameLanguage(first: String, second: String): Boolean =
        canonicalLanguage(first) == canonicalLanguage(second)

    private fun cacheKey(trackId: String, target: String, slots: List<Slot>): String {
        val source = buildString {
            append(trackId).append('\u0000').append(target)
            slots.forEach { append('\u0000').append(it.text) }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private suspend fun readCache(context: Context, key: String): CachedTranslation? =
        withContext(Dispatchers.IO) {
            diskMutex.withLock {
                val file = File(File(context.cacheDir, CACHE_DIRECTORY), "$key.json.gz")
                if (!file.isFile) return@withLock null
                runCatching {
                    GZIPInputStream(FileInputStream(file)).bufferedReader().use { reader ->
                        val root = JSONObject(reader.readText())
                        CachedTranslation(
                            version = root.optInt("version", -1),
                            sourceLanguage = root.optString("sourceLanguage"),
                            targetLanguage = root.optString("targetLanguage"),
                            texts = root.optJSONArray("texts")?.let { array ->
                                (0 until array.length()).map { array.optString(it) }
                            }.orEmpty(),
                        )
                    }.takeIf { it.version == CACHE_VERSION }
                }.getOrNull()
            }
        }

    private suspend fun writeCache(context: Context, key: String, value: CachedTranslation) {
        withContext(Dispatchers.IO) {
            diskMutex.withLock {
                val directory = File(context.cacheDir, CACHE_DIRECTORY)
                if (!directory.exists() && !directory.mkdirs()) return@withLock
                val destination = File(directory, "$key.json.gz")
                val temporary = File(directory, "$key.tmp")
                runCatching {
                    val json = JSONObject()
                        .put("version", value.version)
                        .put("sourceLanguage", value.sourceLanguage)
                        .put("targetLanguage", value.targetLanguage)
                        .put("texts", JSONArray(value.texts))
                    GZIPOutputStream(FileOutputStream(temporary)).bufferedWriter().use {
                        it.write(json.toString())
                    }
                    if (!temporary.renameTo(destination)) {
                        temporary.copyTo(destination, overwrite = true)
                        temporary.delete()
                    }
                    trimCache(directory)
                }.onFailure { temporary.delete() }
            }
        }
    }

    private fun trimCache(directory: File) {
        val files = directory.listFiles { file -> file.extension == "gz" }
            ?.sortedByDescending(File::lastModified)
            .orEmpty()
        var kept = 0L
        files.forEach { file ->
            kept += file.length()
            if (kept > MAX_CACHE_BYTES) file.delete()
        }
    }
}
