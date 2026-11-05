package com.quackify.backend.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.quackify.backend.core.log.Logs

private val log = Logs.of(Logs.Module.FOCUS)

internal class AudioFocus(private val context: Context) {

    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var request: Any? = null

    @Volatile
    private var held = false

    @Volatile
    private var ducked = false

    @Volatile
    private var pausedForTransient = false

    var onPause: (() -> Unit)? = null

    var onResume: (() -> Unit)? = null

    var onDuck: ((Boolean) -> Unit)? = null

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                held = true
                if (ducked) {
                    ducked = false
                    log.i("focus gain, unducking")
                    onDuck?.invoke(false)
                }
                if (pausedForTransient) {
                    pausedForTransient = false
                    log.i("focus back, resuming what the other app interrupted")
                    onResume?.invoke()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                log.i("focus lost for good, pausing and letting go")
                onPause?.invoke()
                abandon()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                held = false
                // CAN_DUCK can escalate into a full transient loss: without
                // unducking here the GAIN branch below never fires and the
                // sink stays at the ducked volume after the resume.
                if (ducked) {
                    ducked = false
                    log.i("focus lost for a moment, unducking before pausing")
                    onDuck?.invoke(false)
                }
                pausedForTransient = true
                log.i("focus lost for a moment, pausing")
                onPause?.invoke()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (!ducked) {
                    ducked = true
                    log.i("focus can duck, lowering the volume")
                    onDuck?.invoke(true)
                }
            }

            else -> log.v("focus change $change")
        }
    }

    private fun attributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    fun acquire(): Boolean {
        val audio = manager
        if (audio == null) {
            log.w("focus: no audio manager")
            return false
        }
        if (held) return true
        val granted = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val built = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes())
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(listener)
                    .build()
                request = built
                audio.requestAudioFocus(built)
            } else {
                @Suppress("DEPRECATION")
                audio.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        }.getOrElse {
            log.w("focus request threw ${it.javaClass.simpleName}: ${it.message}")
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
        held = granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!held) log.w("focus refused, another app is playing")
        return held
    }

    fun abandon() {
        val audio = manager ?: return
        if (!held && request == null) return
        // A later acquire() that is simply granted never re-delivers GAIN,
        // so the duck level has to be restored here while letting go.
        if (ducked) {
            ducked = false
            log.i("letting go while ducked, restoring the volume")
            onDuck?.invoke(false)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                (request as? AudioFocusRequest)?.let { audio.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audio.abandonAudioFocus(listener)
            }
        }
        request = null
        held = false
        ducked = false
        pausedForTransient = false
    }
}
