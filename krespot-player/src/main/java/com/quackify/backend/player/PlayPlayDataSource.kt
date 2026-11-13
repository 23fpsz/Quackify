package com.quackify.backend.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import com.quackify.backend.core.log.Logs
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private val log = Logs.of(Logs.Module.SOURCE)

/**
 * Serves a PlayPlay encrypted OGG/FLAC file to ExoPlayer as if it were clear.
 * The first 0xA7 bytes of an OGG file are the plaintext container header; the
 * rest is AES-128-CTR with the fixed audio IV, keystream indexed by absolute
 * file position. FLAC files are encrypted from byte 0 with no header skip.
 */
internal class PlayPlayDataSource(
    private val url: String,
    private val key: ByteArray,
    private val byteSkip: Long,
) : BaseDataSource(/* isNetwork= */ true) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var response: okhttp3.Response? = null

    private var stream: InputStream? = null

    private var header = ByteArray(0)

    private var headerPos = 0

    private var remaining = 0L

    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val position = dataSpec.position.coerceAtLeast(0L)
        val length = dataSpec.length
        val headerEnd = if (byteSkip > 0 && position < byteSkip) {
            if (length >= 0) minOf(byteSkip, position + length) else byteSkip
        } else {
            position
        }
        header = if (headerEnd > position) plainRange(position, headerEnd) else ByteArray(0)
        headerPos = 0
        val headerLen = header.size.toLong()
        val encStart = maxOf(position, byteSkip)
        val encLen = if (length >= 0) (length - headerLen).coerceAtLeast(0L) else -1L
        var contentLength = -1L
        var blockOffset = 0
        if (encLen != 0L) {
            val alignedPos = encStart - (encStart % 16)
            blockOffset = (encStart % 16).toInt()
            val rangeEnd = if (encLen >= 0) alignedPos + blockOffset + encLen - 1 else null
            val range = if (rangeEnd != null) "bytes=$alignedPos-$rangeEnd" else "bytes=$alignedPos-"
            val call = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("Range", range)
                    .header("Accept-Encoding", "identity")
                    .build()
            ).execute()
            if (!call.isSuccessful || (call.code != 206 && !(call.code == 200 && alignedPos == 0L))) {
                val code = call.code
                call.close()
                throw IOException("playplay cdn http $code for range $range")
            }
            val body = call.body ?: run {
                call.close()
                throw IOException("playplay cdn empty body")
            }
            contentLength = body.contentLength()
            val ivCounter = AUDIO_IV.add(BigInteger.valueOf(alignedPos / 16))
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                IvParameterSpec(counterBytes(ivCounter)),
            )
            val cipherStream = CipherInputStream(body.byteStream(), cipher)
            if (blockOffset > 0) {
                val scratch = ByteArray(blockOffset)
                var skipped = 0
                while (skipped < blockOffset) {
                    val n = cipherStream.read(scratch, skipped, blockOffset - skipped)
                    if (n == -1) {
                        call.close()
                        throw IOException("playplay stream ended inside the skipped block")
                    }
                    skipped += n
                }
            }
            response = call
            stream = cipherStream
        }
        remaining = when {
            length >= 0 -> length
            contentLength >= 0 -> headerLen + (contentLength - blockOffset).coerceAtLeast(0L)
            else -> C.LENGTH_UNSET.toLong()
        }
        opened = true
        transferStarted(dataSpec)
        return when {
            length >= 0 -> length
            remaining >= 0 -> remaining
            else -> C.LENGTH_UNSET.toLong()
        }
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        if (headerPos < header.size) {
            var take = minOf(readLength, header.size - headerPos)
            if (remaining > 0) take = minOf(take, remaining.toInt())
            System.arraycopy(header, headerPos, buffer, offset, take)
            headerPos += take
            if (remaining > 0) remaining -= take
            bytesTransferred(take)
            return take
        }
        val source = stream ?: run {
            if (remaining > 0) throw IOException("playplay stream ended early")
            return C.RESULT_END_OF_INPUT
        }
        val read = source.read(buffer, offset, readLength)
        if (read == -1) {
            if (remaining > 0) throw IOException("playplay stream ended early")
            return C.RESULT_END_OF_INPUT
        }
        if (remaining > 0) remaining -= read
        bytesTransferred(read)
        return read
    }

    private fun plainRange(from: Long, to: Long): ByteArray {
        val range = "bytes=$from-${to - 1}"
        val call = client.newCall(
            Request.Builder()
                .url(url)
                .header("Range", range)
                .header("Accept-Encoding", "identity")
                .build()
        ).execute()
        if (!call.isSuccessful) {
            val code = call.code
            call.close()
            throw IOException("playplay cdn http $code for header range $range")
        }
        call.body!!.use { body ->
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(4096)
            while (out.size() < (to - from).toInt()) {
                val n = body.byteStream().read(chunk)
                if (n == -1) break
                out.write(chunk, 0, n)
            }
            return out.toByteArray()
        }
    }

    override fun getUri(): Uri? = response?.request?.url?.toString()?.let { Uri.parse(it) }

    override fun close() {
        runCatching { stream?.close() }
        runCatching { response?.close() }
        stream = null
        response = null
        header = ByteArray(0)
        headerPos = 0
        remaining = 0L
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun counterBytes(value: BigInteger): ByteArray {
        val full = value.toByteArray()
        return when {
            full.size == 16 -> full
            full.size > 16 -> full.copyOfRange(full.size - 16, full.size)
            else -> ByteArray(16 - full.size) + full
        }
    }

    internal class Factory(
        private val url: String,
        private val key: ByteArray,
        private val flac: Boolean,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            PlayPlayDataSource(url, key, if (flac) 0L else OGG_SKIP)
    }

    companion object {
        private val AUDIO_IV = BigInteger("72e067fbddcbcf77ebe8bc643f630d93", 16)
        const val OGG_SKIP = 0xA7L
    }
}
