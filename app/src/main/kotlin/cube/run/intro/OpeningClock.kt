package cube.run.intro

import android.os.SystemClock

/** One clock shared by the native view and GL; loading never restarts the animation. */
class OpeningClock {
    @Volatile private var startNanos = 0L
    @Volatile private var pausedNanos = 0L
    @Volatile var leadInSeconds = 0f; private set
    @Volatile private var tintStart = -1f
    @Volatile private var ended = false

    fun start() { if (startNanos == 0L) startNanos = SystemClock.elapsedRealtimeNanos() }
    /** Android already animated the cube in its starting window. Continue that time. */
    fun adoptSystemStart(epochMillis: Long) {
        val ageMs = (System.currentTimeMillis()-epochMillis).coerceIn(0L, 2000L)
        leadInSeconds = (ageMs/1000f-seconds()).coerceAtLeast(0f)
        tintStart = seconds()
    }
    fun seconds(): Float {
        if (ended) return OpeningPose.DURATION
        val start = startNanos
        if (start == 0L) return 0f
        val now = pausedNanos.takeIf { it != 0L } ?: SystemClock.elapsedRealtimeNanos()
        return ((now-start)/1e9f).coerceIn(0f, OpeningPose.DURATION)
    }
    /** Blend the system's fixed rose colour into the equipped skin after transfer. */
    fun skinAmount(): Float = if (tintStart < 0f) 1f else OpeningPose.ease((seconds()-tintStart)/.16f)
    fun finish() { ended = true }
    fun pause() { if (pausedNanos == 0L) pausedNanos = SystemClock.elapsedRealtimeNanos() }
    fun resume() {
        val paused = pausedNanos
        if (paused != 0L) {
            if (startNanos != 0L) startNanos += SystemClock.elapsedRealtimeNanos()-paused
            pausedNanos = 0L
        }
    }
}
