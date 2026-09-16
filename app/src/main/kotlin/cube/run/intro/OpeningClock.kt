package cube.run.intro

import android.os.SystemClock

/** One clock shared by the native view and GL; loading never restarts the animation. */
class OpeningClock(val launchAppearance: LaunchAppearance = LaunchAppearance.ROSE) {
    @Volatile private var startNanos = 0L
    @Volatile private var pausedNanos = 0L
    @Volatile private var spinOffset = 0f
    @Volatile private var matchingSystem = false
    @Volatile private var releasedAt = -1f
    @Volatile private var tintStart = -1f
    @Volatile private var ended = false

    fun start() { if (startNanos == 0L) startNanos = SystemClock.elapsedRealtimeNanos() }
    /** Android already animated the cube in its starting window. Continue that time. */
    fun adoptSystemStart(epochMillis: Long, durationMillis: Long = 1000L) {
        val ageMs = (System.currentTimeMillis()-epochMillis).coerceAtLeast(0L)
        val systemDuration = durationMillis.coerceAtLeast(0L)/1000f
        spinOffset = minOf(ageMs/1000f, systemDuration)-elapsedSeconds()
        matchingSystem = true
        tintStart = -1f
    }
    /** Called only after the matching native frame has been submitted and the cover removed. */
    fun releaseSystem() {
        releasedAt = elapsedSeconds()
        tintStart = releasedAt
        matchingSystem = false
    }
    fun seconds(): Float = if (ended) OpeningPose.DURATION else elapsedSeconds().coerceAtMost(OpeningPose.DURATION)

    private fun elapsedSeconds(): Float {
        val start = startNanos
        if (start == 0L) return 0f
        val now = pausedNanos.takeIf { it != 0L } ?: SystemClock.elapsedRealtimeNanos()
        return ((now-start)/1e9f).coerceAtLeast(0f)
    }
    fun motionSeconds(): Float = elapsedSeconds()+spinOffset
    /** Rejoin the original deadline; a very late frame still needs time to move the camera. */
    fun sceneSeconds(): Float = when {
        ended -> OpeningPose.DURATION
        matchingSystem -> 0f
        releasedAt < 0f -> seconds()
        else -> ((elapsedSeconds()-releasedAt)/(OpeningPose.DURATION-releasedAt).coerceAtLeast(.6f))
            .coerceIn(0f, 1f)*OpeningPose.DURATION
    }
    fun skinAmount(): Float = when {
        ended -> 1f
        matchingSystem -> 0f
        tintStart < 0f -> 1f
        else -> OpeningPose.ease((elapsedSeconds()-tintStart)/.24f)
    }
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
