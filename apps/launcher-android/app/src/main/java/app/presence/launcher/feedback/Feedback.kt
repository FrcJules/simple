package app.presence.launcher.feedback

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Small sounds and vibrations that tell Jean-Pierre what just happened without reading: a soft double tone when something was sent,
 * a long vibration when it failed. No assets, no network. Everything is best effort: a phone without vibrator or tone generator
 * simply stays silent, and the screen always says the same thing in words.
 */
class Feedback(context: Context, private val enabled: () -> Boolean) {
    private val app = context.applicationContext
    private var tones: ToneGenerator? = null

    /** A message or a photo was handed over. */
    fun sent() {
        if (!enabled()) return
        tone(ToneGenerator.TONE_PROP_ACK)
        vibrate(longArrayOf(0, 60))
    }

    /** Something failed and the person must look at the screen. */
    fun failed() {
        if (!enabled()) return
        tone(ToneGenerator.TONE_PROP_NACK)
        vibrate(longArrayOf(0, 200, 120, 200))
    }

    fun release() {
        tones?.release()
        tones = null
    }

    private fun tone(type: Int) {
        try {
            val generator = tones ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME_PERCENT).also { tones = it }
            generator.startTone(type, TONE_MS)
        } catch (e: RuntimeException) {
            tones = null // no tone generator on this phone: silence
        }
    }

    private fun vibrate(pattern: LongArray) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (e: SecurityException) {
            // VIBRATE not granted: silence
        }
    }

    private companion object {
        const val VOLUME_PERCENT = 60
        const val TONE_MS = 250
    }
}
