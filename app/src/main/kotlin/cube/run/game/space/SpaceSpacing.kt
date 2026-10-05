package cube.run.game.space

import cube.run.game.Player
import kotlin.math.max

/**
 * Row spacing in Outer Space, from the low-gravity arcs themselves. A jump
 * there hangs about a second, so the road after one has to grow with the
 * speed; a gravity ring's launch peaks late, so the hull it carries you over
 * sits where the arc is highest.
 */
object SpaceSpacing {
    private const val GRAVITY = 26f * Player.LOW_G
    private const val JUMP_V = 8.4f * Player.LOW_G_LAUNCH
    private const val RING_V = 12.5f * Player.LOW_G_LAUNCH

    /** Seconds a standing low-gravity jump spends in the air (≈0.98, against 0.65 on the ground). */
    const val AIR_TIME = 2f * JUMP_V / GRAVITY
    /** Seconds from a gravity ring to the top of its arc. */
    const val RING_PEAK = RING_V / GRAVITY

    /** A plain dodge row: a little roomier than the ground's 6.5 — space is calmer. */
    const val DODGE = 7.2f

    /**
     * Between dodge rows at [speed]: lane changes glide (rate 10 against 13 on
     * the ground), so at speed the gap keeps the same time to react and arrive.
     */
    fun dodge(speed: Float) = max(DODGE, speed * 0.29f)
    /** Between the slices of one rift: they overlap into a single chasm. */
    const val RIFT_SLICE = 1.65f

    /** After a row you jump: land (the second half of the arc) and get a beat to read the next row. */
    fun afterJump(speed: Float) = max(9.5f, 0.55f * AIR_TIME * speed + 2.5f)

    /** From a gravity ring to the hull it clears: the hull meets the cube at the top of its arc. */
    fun ringToHull(speed: Float) = max(7f, RING_PEAK * speed)

    /** Height of a ring launch [t] seconds after it, from the ground (cube centre). */
    fun ringArcY(t: Float) = 0.45f + RING_V * t - 0.5f * GRAVITY * t * t // 0.45: the resting cube centre
}
