package cube.run.game

import cube.run.game.track.Sections
import kotlin.math.min

/**
 * Difficulty is one axis: a normalised level [diff] (0..1) that drives speed
 * ([speed]) and the unlocked section tier ([tier]). It auto-advances over real
 * time. Speed is linear up to the "blue line" (the cruising max) and then
 * gives diminishing returns toward the absolute ceiling.
 */
class Difficulty {
    val minSpd = 10f          // speed at diff = 0
    val maxSpd = 30f          // absolute ceiling (diff = 1); only ever approached, never the cruise speed
    val blueLine = 0.85f      // the cruising max; past here speed barely climbs
    val cruiseSpd = minSpd + (maxSpd - minSpd) * blueLine // speed at the blue line (27)
    private val rampSeconds = 700f // real seconds for diff to auto-climb the full 0..1 range
    val startDiff = 0.12f     // difficulty a run begins at with no fire boost

    var diff = startDiff
        private set

    fun reset() { diff = startDiff }

    /** Auto-climb toward the ceiling. */
    fun ramp(dt: Float) { if (diff < 1f) diff = min(1f, diff + dt / rampSeconds) }

    /** Jump straight to [d] if that's higher than where we are (fire boost). */
    fun boostTo(d: Float) { if (d > diff) diff = d }

    /** Speed for the current level: linear up to the blue line, diminishing returns beyond it. */
    fun speed(): Float =
        if (diff <= blueLine) {
            minSpd + (maxSpd - minSpd) * diff
        } else {
            val o = (diff - blueLine) / (1f - blueLine)
            cruiseSpd + (maxSpd - cruiseSpd) * (1f - (1f - o) * (1f - o))
        }

    fun tier(): Int = Sections.tierFor(diff)
}
