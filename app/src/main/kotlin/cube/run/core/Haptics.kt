package cube.run.core

import android.content.Context
import cube.run.data.Settings
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptic feedback shared by every game.
 * tick   – subtle, for rapid repeated events (movement, counting)
 * click  – standard, for taps/placements
 * heavy  – impactful, for smashes/landings
 * success/fail – game-over flourishes
 * buzz   – custom one-shot
 */
object Haptics {
    private var vib: Vibrator? = null

    fun init(ctx: Context) {
        vib = if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun tick() = predefined(VibrationEffect.EFFECT_TICK, 10, 70)
    fun click() = predefined(VibrationEffect.EFFECT_CLICK, 16, 160)
    fun heavy() = predefined(VibrationEffect.EFFECT_HEAVY_CLICK, 24, 255)

    fun success() = waveform(longArrayOf(0, 26, 70, 40), intArrayOf(0, 170, 0, 255))
    fun fail() = waveform(longArrayOf(0, 70, 60, 140), intArrayOf(0, 120, 0, 230))

    fun buzz(ms: Int, amp: Int = 200) {
        if (!Settings.hapticsEnabled) return
        runCatching { vib?.vibrate(VibrationEffect.createOneShot(ms.toLong().coerceAtLeast(1), amp.coerceIn(1, 255))) }
    }

    /** Predefined haptic on API 29+, a hand-tuned one-shot approximation on Android 9. */
    private fun predefined(effect: Int, fallbackMs: Long, fallbackAmp: Int) {
        if (!Settings.hapticsEnabled) return
        runCatching {
            vib?.vibrate(
                if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(effect)
                else VibrationEffect.createOneShot(fallbackMs, fallbackAmp),
            )
        }
    }

    private fun waveform(times: LongArray, amps: IntArray) {
        if (!Settings.hapticsEnabled) return
        runCatching { vib?.vibrate(VibrationEffect.createWaveform(times, amps, -1)) }
    }
}
