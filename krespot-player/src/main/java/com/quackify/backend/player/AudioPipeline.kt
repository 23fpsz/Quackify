package com.quackify.backend.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player

/**
 * What the player is actually being fed, as one read for the now-playing
 * pipeline sheet: the decoder's own answer first (mime, rate, channels,
 * bitrate — measured, not promised) and, next to it, what the source
 * declared (manifest bitrate, quality tier, file or video id). A null
 * anywhere means "not stated"; the sheet leaves that line out rather than
 * guessing.
 */
data class AudioPipelineSnapshot(
    val sourceKey: String,
    val mimeType: String? = null,
    val codecs: String? = null,
    val sampleRateHz: Int? = null,
    val channels: Int? = null,
    val bitDepth: Int? = null,
    val decoderBitrateBps: Int? = null,
    val declaredBitrateBps: Int? = null,
    val declaredQuality: String? = null,
    val fileId: String? = null,
    val videoId: String? = null,
) {
    companion object {
        const val SOURCE_VIDEO = "music_video"
    }
}

data class DecoderFormat(
    val mimeType: String? = null,
    val codecs: String? = null,
    val sampleRateHz: Int? = null,
    val channels: Int? = null,
    val bitDepth: Int? = null,
    val bitrateBps: Int? = null,
)

/**
 * The live audio renderer's format, or null before it has been configured.
 * Must be read on the player's application thread.
 */
internal fun Player.decoderAudioFormat(): DecoderFormat? {
    val format: Format = audioFormat ?: return null
    return DecoderFormat(
        mimeType = format.sampleMimeType,
        codecs = format.codecs?.takeIf { it.isNotBlank() },
        sampleRateHz = format.sampleRate.takeIf { it > 0 },
        channels = format.channelCount.takeIf { it > 0 },
        bitDepth = when (format.pcmEncoding) {
            C.ENCODING_PCM_8BIT -> 8
            C.ENCODING_PCM_16BIT -> 16
            C.ENCODING_PCM_24BIT -> 24
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
            else -> null
        },
        bitrateBps = format.bitrate.takeIf { it > 0 },
    )
}
