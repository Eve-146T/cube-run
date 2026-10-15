package cube.run.bot

/**
 * How a jetpack flight comes down onto its landing, shared by the game and
 * the bot model: the jet cuts out, a short hop, then a drop. [height] takes
 * the descent's progress (0 where it starts, 1 at touchdown) and returns the
 * share of the cruising height left.
 */
object JetDescent {
    /** Seconds from the start of the descent to touchdown. */
    const val SECONDS = 0.7f

    fun height(s: Float): Float {
        val t = s.coerceIn(0f, 1f)
        // a ballistic arc: the cough lifts it a touch, gravity takes over
        val u = t * (1f + HOP) - HOP
        return (1f - u * u) / (1f - HOP * HOP)
    }

    private const val HOP = 0.25f
}
