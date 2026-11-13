package com.quackify.backend.player

import android.content.Context
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream

private val log = Logs.of(Logs.Module.SOURCE)

/**
 * Fetches OGG/FLAC file ids for a track through the extended-metadata endpoint.
 * The request and response are plain protobuf, parsed by hand to avoid codegen.
 * Wire schema (extendedmetadata.proto + audio_files_extension.proto):
 *   BatchedEntityRequest  { header=1, entity_request=2{ entity_uri=1, query=2{ extension_kind=1 } } }
 *   BatchedExtensionResponse { extended_metadata=2{ extension_kind=2, extension_data=3{
 *       entity_uri=2, extension_data=3(Any{ type_url=1, value=2 }) } } }
 *   AudioFilesExtensionResponse { files=1{ file=1{ file_id=1(bytes), format=2 }, bitrate=4 } }
 */
internal object AudioMeta {

    private const val URL = "https://spclient.wg.spotify.com/extended-metadata/v0/extended-metadata"

    private const val KIND_AUDIO_FILES = 5

    const val OGG_VORBIS_96 = 0
    const val OGG_VORBIS_160 = 1
    const val OGG_VORBIS_320 = 2
    const val FLAC_FLAC = 16
    const val FLAC_FLAC_24BIT = 22

    val PLAYABLE_FORMATS = listOf(
        OGG_VORBIS_320, OGG_VORBIS_160, OGG_VORBIS_96,
        FLAC_FLAC_24BIT, FLAC_FLAC,
    )

    data class AudioEntry(val fileId: String, val format: Int, val bitrate: Int)

    private val mutex = Mutex()

    private val cache = LinkedHashMap<String, List<AudioEntry>>()

    private const val CACHE_MAX = 64

    suspend fun audioFiles(context: Context, uri: String): List<AudioEntry> {
        if (!uri.startsWith("spotify:track:")) return emptyList()
        mutex.withLock { cache[uri] }?.let { return it }
        val blob = Net.postBlob(
            URL,
            request(listOf(uri)),
            "application/x-protobuf",
            PlayerHeaders.of(context),
            tag = "extmeta",
        )?.takeIf { it.ok }
        if (blob == null) {
            log.w("extmeta failed for '${uri.takeLast(12)}'")
            return emptyList()
        }
        val files = runCatching { parse(blob.bytes, uri) }
            .getOrElse {
                log.w("extmeta parse failed '${uri.takeLast(12)}' ${it.javaClass.simpleName}: ${it.message}")
                emptyList()
            }
        if (files.isNotEmpty()) {
            mutex.withLock {
                cache[uri] = files
                while (cache.size > CACHE_MAX) cache.remove(cache.keys.first())
            }
            log.i("extmeta '${uri.takeLast(12)}' " +
                    files.joinToString(" ") { "f${it.format}/${it.bitrate}b" }
            )
        }
        return files
    }

    fun best(files: List<AudioEntry>): AudioEntry? =
        PLAYABLE_FORMATS.firstNotNullOfOrNull { format ->
            files.filter { it.format == format }.maxByOrNull { it.bitrate }
        }

    private fun request(uris: List<String>): ByteArray = ByteArrayOutputStream(64 + uris.size * 48).apply {
        uris.forEach { uri ->
            val query = ByteArrayOutputStream().apply {
                write(0x08)
                writeVarint(this, KIND_AUDIO_FILES.toLong())
            }.toByteArray()
            val entity = ByteArrayOutputStream().apply {
                field(1, uri.toByteArray())
                write(query)
            }.toByteArray()
            field(2, entity)
        }
    }.toByteArray()

    private fun ByteArrayOutputStream.field(number: Int, payload: ByteArray) {
        write((number shl 3) or 2)
        writeVarint(this, payload.size.toLong())
        write(payload)
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v and -0x80L != 0L) {
            out.write(((v and 0x7fL) or 0x80L).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    private fun parse(data: ByteArray, wantedUri: String): List<AudioEntry> {
        val reader = Reader(data)
        val files = mutableListOf<AudioEntry>()
        while (!reader.done()) {
            val (field, wire) = reader.tag()
            if (field != 2 || wire != 2) { reader.skip(wire); continue }
            val array = Reader(reader.bytes())
            var kind = -1
            val entries = mutableListOf<ByteArray>()
            while (!array.done()) {
                val (f, w) = array.tag()
                when {
                    f == 2 && w == 0 -> kind = array.varint().toInt()
                    f == 3 && w == 2 -> entries += array.bytes()
                    else -> array.skip(w)
                }
            }
            if (kind != KIND_AUDIO_FILES) continue
            for (entry in entries) {
                val data0 = Reader(entry)
                var uri = ""
                var value = ByteArray(0)
                while (!data0.done()) {
                    val (f, w) = data0.tag()
                    when {
                        f == 2 && w == 2 -> uri = String(data0.bytes())
                        f == 3 && w == 2 -> value = data0.bytes()
                        else -> data0.skip(w)
                    }
                }
                if (uri != wantedUri || value.isEmpty()) continue
                val any = Reader(value)
                var payload = ByteArray(0)
                while (!any.done()) {
                    val (f, w) = any.tag()
                    if (f == 2 && w == 2) payload = any.bytes() else any.skip(w)
                }
                if (payload.isEmpty()) continue
                val response = Reader(payload)
                while (!response.done()) {
                    val (f, w) = response.tag()
                    if (f != 1 || w != 2) { response.skip(w); continue }
                    val extended = Reader(response.bytes())
                    var file = ByteArray(0)
                    var bitrate = 0
                    while (!extended.done()) {
                        val (ef, ew) = extended.tag()
                        when {
                            ef == 1 && ew == 2 -> file = extended.bytes()
                            ef == 4 && ew == 0 -> bitrate = extended.varint().toInt()
                            else -> extended.skip(ew)
                        }
                    }
                    if (file.isEmpty()) continue
                    val audio = Reader(file)
                    var fileId = ByteArray(0)
                    var format = -1
                    while (!audio.done()) {
                        val (af, aw) = audio.tag()
                        when {
                            af == 1 && aw == 2 -> fileId = audio.bytes()
                            af == 2 && aw == 0 -> format = audio.varint().toInt()
                            else -> audio.skip(aw)
                        }
                    }
                    if (fileId.size == 20 && format in PLAYABLE_FORMATS) {
                        files += AudioEntry(
                            fileId = fileId.joinToString("") { "%02x".format(it) },
                            format = format,
                            bitrate = bitrate,
                        )
                    }
                }
            }
        }
        return files.distinctBy { it.fileId }
    }

    private class Reader(private val data: ByteArray) {
        private var pos = 0

        fun done(): Boolean = pos >= data.size

        fun tag(): Pair<Int, Int> {
            val value = varint()
            return (value ushr 3).toInt() to (value and 7L).toInt()
        }

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (shift <= 63) {
                if (pos >= data.size) throw IllegalStateException("truncated varint")
                val byte = data[pos++].toInt() and 255
                result = result or ((byte and 127).toLong() shl shift)
                if (byte and 128 == 0) return result
                shift += 7
            }
            throw IllegalStateException("varint overflow")
        }

        fun bytes(): ByteArray {
            val length = varint().toInt()
            if (length < 0 || pos + length > data.size) throw IllegalStateException("truncated field")
            return data.copyOfRange(pos, pos + length).also { pos += length }
        }

        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> pos += 8
                2 -> bytes()
                5 -> pos += 4
                else -> throw IllegalStateException("wire $wire")
            }
        }
    }
}
