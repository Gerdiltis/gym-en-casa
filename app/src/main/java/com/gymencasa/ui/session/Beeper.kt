package com.gymencasa.ui.session

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.gymencasa.domain.session.SessionEffect
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Turns session effects into beeps and vibration. */
class Beeper @Inject constructor(@param:ApplicationContext context: Context) {
    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }.getOrNull()
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    fun play(effect: SessionEffect, sound: Boolean, vibration: Boolean) {
        when (effect) {
            is SessionEffect.CountdownTick -> {
                if (sound) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            }
            SessionEffect.WorkStarted -> {
                if (sound) tone?.startTone(ToneGenerator.TONE_PROP_ACK, 250)
                if (vibration) vibrate(longArrayOf(0, 120))
            }
            SessionEffect.SetFinished -> {
                if (sound) tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700)
                if (vibration) vibrate(longArrayOf(0, 300, 120, 300))
            }
            SessionEffect.SessionFinished -> {
                if (sound) tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 1200)
                if (vibration) vibrate(longArrayOf(0, 500, 150, 500))
            }
        }
    }

    private fun vibrate(pattern: LongArray) {
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    fun release() = tone?.release()
}
