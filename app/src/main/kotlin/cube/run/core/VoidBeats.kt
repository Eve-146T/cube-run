package cube.run.core

/**
 * The void offering's one timeline, in simulation seconds, shared by the 3D show
 * (`game.stage.VoidShow`) and the HUD (`ui.VoidShowOverlay`). The GL thread owns the
 * clock ([Stage.voidClock]); the HUD only reads it, so a pause freezes both together.
 */
object VoidBeats {
    /** The shop sheet drops away; the sky drains and a black hole opens above the cube. */
    const val FORM = 0f
    /** Coins pour out of the bank, fall onto the disk and orbit down into it. */
    const val FEED = 0.8f
    /** The cube itself is pulled up into it, stretched thin, and gone behind the horizon. */
    const val PULL = 1.75f
    const val TAKEN = 2.75f
    /** The disk spins up as the hole shrinks to a single point of light. */
    const val COLLAPSE = 2.75f
    /** It goes supernova. */
    const val NOVA = 3.3f
    /** The cube is assembled again from shards where it was. */
    const val REBIRTH = 3.8f
    /** The void says its piece. */
    const val SPEAK = 4.15f
    /** The camera heads back to the shop. A tap after the blast skips straight here. */
    const val RETURN = 6.6f
    /** The shop sheet rises back once the cube is clear of it. */
    const val SHEET = RETURN + 0.35f
    const val END = RETURN + 0.95f

    private fun smooth(x: Float) = x.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

    /** 0..1 progress of [t] between [a] and [b], eased at both ends. */
    fun span(t: Float, a: Float, b: Float) = smooth((t - a) / (b - a))
}
