package cube.run.bot

/**
 * How a jetpack flight comes down onto its landing strip, shared by the game
 * and the bot model. [height] takes the descent's progress (0 where it
 * starts, 1 at touchdown) and returns the share of the cruising height left.
 */
object JetDescent {
    /** The jet cuts out over the strip: a short hop, then a heavy drop onto it. */
    const val FALL = 0
    /** The jet throttles down: an eased glide that settles onto the strip. */
    const val GLIDE = 1

    /** Seconds from the start of the descent to touchdown. */
    fun seconds(mode: Int): Float = if (mode == GLIDE) 1.4f else 0.7f

    fun height(s: Float, mode: Int): Float {
        val t = s.coerceIn(0f, 1f)
        if (mode == GLIDE) return 1f - t * t * (3f - 2f * t)
        // a ballistic arc: the cough lifts it a touch, gravity takes over
        val u = t * (1f + HOP) - HOP
        return (1f - u * u) / (1f - HOP * HOP)
    }

    private const val HOP = 0.25f
}
