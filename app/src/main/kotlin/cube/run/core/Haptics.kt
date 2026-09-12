package cube.run.core

import android.content.Context
import cube.run.data.Settings
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
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
    private var worker: Handler? = null
    private val lock = Any()
    private var pending: VibrationEffect? = null
    private var pendingPriority = 0
    private var scheduled = false
    private val tickEffect = if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        else VibrationEffect.createOneShot(10, 70)
    private val clickEffect = if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        else VibrationEffect.createOneShot(16, 160)
    private val heavyEffect = if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        else VibrationEffect.createOneShot(24, 255)
    private val successEffect = VibrationEffect.createWaveform(longArrayOf(0, 26, 70, 40), intArrayOf(0, 170, 0, 255), -1)
    private val failEffect = VibrationEffect.createWaveform(longArrayOf(0, 70, 60, 140), intArrayOf(0, 120, 0, 230), -1)

    // Vibrator calls cross Binder and can take several milliseconds on older phones.
    // Keep them off both rendering threads; a burst has one pending strongest effect,
    // never a queue of stale coin ticks playing after the collision that produced it.
    private val dispatch = object : Runnable {
        override fun run() {
            val next = synchronized(lock) { pending.also { pending = null; pendingPriority = 0 } }
            if (Settings.hapticsEnabled && next != null) runCatching { vib?.vibrate(next) }
            synchronized(lock) {
                if (pending != null) worker?.post(this) else scheduled = false
            }
        }
    }

    fun init(ctx: Context) {
        if (worker != null) return
        vib = if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        worker = Handler(HandlerThread("cube-haptics").apply { start() }.looper)
    }

    fun tick() = submit(tickEffect, 0)
    fun click() = submit(clickEffect, 1)
    fun heavy() = submit(heavyEffect, 2)

    fun success() = submit(successEffect, 3)
    fun fail() = submit(failEffect, 3)

    fun buzz(ms: Int, amp: Int = 200) {
        if (!Settings.hapticsEnabled) return
        submit(VibrationEffect.createOneShot(ms.toLong().coerceAtLeast(1), amp.coerceIn(1, 255)), 2)
    }

    private fun submit(effect: VibrationEffect, priority: Int) {
        if (!Settings.hapticsEnabled) return
        val handler = worker ?: return
        synchronized(lock) {
            if (pending == null || priority >= pendingPriority) { pending = effect; pendingPriority = priority }
            if (!scheduled) { scheduled = true; handler.post(dispatch) }
        }
    }
}
