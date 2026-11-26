package com.gymencasa.ui.session

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.gymencasa.R
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.gymencasa.domain.session.SessionEffect
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Turns session effects into beeps, vibration and pre-recorded voice cues (res/raw/voz_*.m4a). */
class Beeper @Inject constructor(@param:ApplicationContext context: Context) {
    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }.getOrNull()
    private val handler = Handler(Looper.getMainLooper())
    private val voicePool: SoundPool? = runCatching {
        SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
        ).build()
    }.getOrNull()
    private val voices: Map<SessionEffect, Int> = voicePool?.let { pool ->
        mapOf(
            SessionEffect.VoiceRest to pool.load(context, R.raw.voz_descansa, 1),
            SessionEffect.VoiceNextSet to pool.load(context, R.raw.voz_siguiente_serie, 1),
            SessionEffect.VoiceNextExercise to pool.load(context, R.raw.voz_siguiente_ejercicio, 1),
        )
    }.orEmpty()
    /** Uptime when the last beep ends: the voice waits for it so they never overlap. */
    private var toneEndsAt = 0L

    private fun beep(type: Int, durationMs: Int) {
        tone?.startTone(type, durationMs)
        toneEndsAt = SystemClock.uptimeMillis() + durationMs
    }
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    fun play(effect: SessionEffect, sound: Boolean, vibration: Boolean, voice: Boolean = false) {
        when (effect) {
            SessionEffect.VoiceRest, SessionEffect.VoiceNextSet, SessionEffect.VoiceNextExercise -> {
                val id = voices[effect] ?: return
                if (!voice) return
                val wait = (toneEndsAt + 60 - SystemClock.uptimeMillis()).coerceAtLeast(0)
                handler.postDelayed({
                    val stream = voicePool?.play(id, 1f, 1f, 1, 0, 1f) ?: 0
                    Log.d(TAG, "voice $effect stream=$stream")
                }, wait)
            }
            is SessionEffect.CountdownTick -> {
                if (sound) beep(ToneGenerator.TONE_PROP_BEEP, 120)
            }
            SessionEffect.WorkStarted -> {
                if (sound) beep(ToneGenerator.TONE_PROP_ACK, 250)
                if (vibration) vibrate(longArrayOf(0, 120))
            }
            SessionEffect.SetFinished -> {
                if (sound) beep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700)
                if (vibration) vibrate(longArrayOf(0, 300, 120, 300))
            }
            SessionEffect.SessionFinished -> {
                if (sound) beep(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 1200)
                if (vibration) vibrate(longArrayOf(0, 500, 150, 500))
            }
        }
    }

    private fun vibrate(pattern: LongArray) {
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private companion object { const val TAG = "Beeper" }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        tone?.release()
        voicePool?.release()
    }
}
