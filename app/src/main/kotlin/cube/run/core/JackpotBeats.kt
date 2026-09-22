package cube.run.core

/**
 * The jackpot's one timeline, in simulation seconds, shared by the 3D show
 * (`game.Jackpot`) and the HUD counter (`ui.JackpotCounter`). The GL thread
 * owns the clock ([Stage.jackpotClock]); the counter only reads it, so a
 * pause freezes both together and nothing can drift apart.
 */
object JackpotBeats {
    /** The world freezes; the cube lifts off and the camera cranes round. */
    const val HIT = 0f
    /** Rays blow open, the road shatters away, the counter pops in and coins erupt. */
    const val BURST = 1.3f
    /** The counter has popped in and starts to roll. */
    const val ROLL = BURST + 0.3f
    /** The counter stops rolling and slams onto the final amount. */
    const val SLAM = 4.6f
    /** Every coin on the road is swept back into the cube. */
    const val GATHER = 4.75f
    /** The camera swings back to the chase and the counter flies into the coin pill. */
    const val RETURN = 5.75f
    /** The counter has landed in the HUD: the run haul shows the new total. */
    const val BANKED = 6.3f
    /** The run takes over again. */
    const val END = 6.85f

    private fun smooth(x: Float) = x.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

    /** 0..1 progress of [t] between [a] and [b], eased at both ends. */
    fun span(t: Float, a: Float, b: Float) = smooth((t - a) / (b - a))

    /**
     * The amount the counter shows at [t]: zero while it pops in, then a
     * roll that races through the low digits and crawls into the last ones.
     */
    fun rolled(amount: Int, t: Float): Int {
        if (t < ROLL) return 0
        if (t >= SLAM) return amount
        val p = (t - ROLL) / (SLAM - ROLL)
        val eased = 1f - (1f - p) * (1f - p) * (1f - p) * (1f - p)
        return (amount * eased.toDouble()).toInt().coerceIn(0, amount)
    }
}
