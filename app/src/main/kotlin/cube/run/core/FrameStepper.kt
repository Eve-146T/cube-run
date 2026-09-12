package cube.run.core

import kotlin.math.ceil
import kotlin.math.min

/** Consume real elapsed time in collision-safe slices, keeping any excess as debt. */
class FrameStepper {
    var pendingSeconds = 0.0
        private set
    var lastSteps = 0
        private set

    fun reset() { pendingSeconds = 0.0; lastSteps = 0 }

    inline fun advance(elapsed: Float, paused: Boolean, step: (Float) -> Unit) {
        val duration = take(elapsed, paused)
        if (lastSteps == 0) { step(0f); return }
        val dt = (duration / lastSteps).toFloat()
        repeat(lastSteps) { step(dt) }
    }

    fun take(elapsed: Float, paused: Boolean): Double {
        if (paused) { reset(); return 0.0 }
        if (elapsed.isFinite() && elapsed > 0f) pendingSeconds += elapsed.toDouble()
        // A normal 60/90 Hz frame is one update. Hitches get multiple small
        // updates rather than tunnelling through rows or throwing away time.
        val duration = min(pendingSeconds, 32.0 / 60.0)
        lastSteps = if (duration > 0) ceil(duration * 55.0).toInt() else 0
        pendingSeconds -= duration
        return duration
    }
}
