package com.spotiduck.backend.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.spotiduck.backend.core.log.Logs

private val log = Logs.of(Logs.Module.SINK)

internal class AudioSink(
    private val sampleRate: Int,
    private val channels: Int,
) {

    private var track: AudioTrack = build()

    @Volatile
    private var held = false

    @Volatile
    private var volume = 1f

    private fun build(): AudioTrack {
        val channelMask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val min = AudioTrack.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        val bytes = if (min > 0) min * 8 else 65_536
        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build(),
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        log.i("sink ${sampleRate}Hz ${channels}ch buffer=${bytes}B min=${min}B state=${created.state}")
        return created
    }

    fun start() {
        held = false
        runCatching { track.setVolume(volume) }
        val played = runCatching { track.play() }
        val failure = played.exceptionOrNull()
        if (failure == null) {
            log.i("sink play state=${track.playState} underruns=${underruns()}")
        } else {
            log.w("sink play refused ${failure.javaClass.simpleName}: ${failure.message} state=${track.state}")
        }
    }

    fun pause() {
        held = true
        runCatching { track.pause() }
    }

    fun resume() {
        held = false
        runCatching { track.play() }
    }

    fun volume(level: Float) {
        volume = level.coerceIn(0f, 1f)
        runCatching { track.setVolume(volume) }
    }

    fun flush() {
        runCatching {
            track.pause()
            track.flush()
            if (!held) track.play()
        }
    }

    fun write(pcm: ShortArray, count: Int): Int = runCatching { track.write(pcm, 0, count) }.getOrElse {
        log.w("sink write threw ${it.javaClass.simpleName}: ${it.message}")
        -1
    }

    fun alive(): Boolean = track.playState == AudioTrack.PLAYSTATE_PLAYING

    fun underruns(): Int = runCatching { track.underrunCount }.getOrDefault(-1)

    fun rebuild() {
        runCatching {
            track.pause()
            track.flush()
            track.release()
        }
        track = build()
        runCatching { track.setVolume(volume) }
        if (!held) runCatching { track.play() }
        log.i("sink rebuilt state=${track.state} play=${track.playState}")
    }

    fun release() {
        runCatching {
            track.pause()
            track.flush()
            track.release()
        }
    }
}
