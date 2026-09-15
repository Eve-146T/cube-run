package cube.run.intro

import android.os.SystemClock

/** One clock shared by the native view and GL; loading never restarts the animation. */
class OpeningClock(val launchAppearance: LaunchAppearance = LaunchAppearance.ROSE) {
    @Volatile private var startNanos = 0L
    @Volatile private var pausedNanos = 0L
    @Volatile private var spinOffset = 0f
    @Volatile private var systemDuration = Float.POSITIVE_INFINITY
    @Volatile private var matchingSystem = false
    @Volatile private var releasedAt = -1f
    @Volatile private var tintStart = -1f
    @Volatile private var ended = false

    fun start() { if (startNanos == 0L) startNanos = SystemClock.elapsedRealtimeNanos() }
    /** Android already animated the cube in its starting window. Continue that time. */
    fun adoptSystemStart(epochMillis: Long, durationMillis: Long = 1000L) {
        val ageMs = (System.currentTimeMillis()-epochMillis).coerceIn(0L, 2000L)
        systemDuration = durationMillis.coerceAtLeast(0L)/1000f
        spinOffset = minOf(ageMs/1000f, systemDuration)-seconds()
        matchingSystem = true
        tintStart = -1f
    }
    /** Called only after the matching native frame has been submitted and the cover removed. */
    fun releaseSystem() {
        spinOffset = motionSeconds()-seconds()
        systemDuration = Float.POSITIVE_INFINITY
        releasedAt = seconds()
        tintStart = releasedAt
        matchingSystem = false
    }
    fun seconds(): Float {
        if (ended) return OpeningPose.DURATION
        val start = startNanos
        if (start == 0L) return 0f
        val now = pausedNanos.takeIf { it != 0L } ?: SystemClock.elapsedRealtimeNanos()
        return ((now-start)/1e9f).coerceIn(0f, OpeningPose.DURATION)
    }
    fun motionSeconds(): Float = minOf(seconds()+spinOffset, systemDuration)
    /** The camera stays with the system cube until handoff, then rejoins the original deadline. */
    fun sceneSeconds(): Float = when {
        ended -> OpeningPose.DURATION
        matchingSystem -> 0f
        releasedAt < 0f -> seconds()
        else -> ((seconds()-releasedAt)/(OpeningPose.DURATION-releasedAt).coerceAtLeast(.001f))
            .coerceIn(0f, 1f)*OpeningPose.DURATION
    }
    fun skinAmount(): Float = when {
        ended -> 1f
        matchingSystem -> 0f
        tintStart < 0f -> 1f
        else -> OpeningPose.ease((seconds()-tintStart)/.24f)
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
