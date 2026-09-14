package cube.run.game

import kotlin.math.max
import kotlin.math.min

/** A visual-only timed pickup. Its blend keeps moving from its current value on re-collection. */
class RedPill {
    val timer = PowerUps.Timer(.1f, 1f, .38f)
    private var progress = 0f
    val blend: Float get() = progress * progress * (3f - 2f * progress)

    fun collect() { timer.start(DURATION) }
    fun reset() { timer.stop(); progress = 0f }
    fun tick(dt: Float, alive: Boolean) {
        if (!alive) timer.stop()
        if (timer.active) timer.tick(dt)
        progress = if (timer.active) min(1f, progress + dt / ENTER)
            else max(0f, progress - dt / LEAVE)
    }

    companion object {
        const val DURATION = 12f
        const val ENTER = .9f
        const val LEAVE = 1.2f
    }
}
