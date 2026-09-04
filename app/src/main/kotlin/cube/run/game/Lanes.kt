package cube.run.game

/**
 * The road's geometry, in one place, because bonus worlds bend it: how many
 * lanes there are and how far apart they sit. Everything that positions
 * itself by lane (the player, obstacles, coins, tiles, kerbs) asks here, so
 * a portal can widen the road to five lanes or stretch the three apart and
 * the whole game follows.
 */
object Lanes {
    /** Lane count (3 normally, 5 in the WIDE bonus). */
    @Volatile var count = 3
    /** Lane spacing (world units). */
    @Volatile var w = 1.7f
    /** The spacing eases toward this (the FLOAT bonus stretches the lanes apart). */
    @Volatile var targetW = 1.7f

    const val NORMAL_W = 1.7f

    /** Centre x of lane [l] (0 = leftmost). */
    fun x(l: Int): Float = (l - (count - 1) / 2f) * w

    val last: Int get() = count - 1
    val middle: Int get() = count / 2

    /** Half the road's width (to the outer lane edge). */
    val halfRoad: Float get() = w * count / 2f

    /** How far the two outer lanes have unfolded (0 = a three-lane road, 1 = five), eased: the road widens instead of snapping. */
    @Volatile var unfold = 0f
    /** Half the road's width as drawn (follows [unfold]). */
    val halfRoadDrawn: Float get() = w * (3f + 2f * unfold) / 2f

    fun reset() { count = 3; w = NORMAL_W; targetW = NORMAL_W; unfold = 0f }

    /** Ease the spacing and the unfolding toward their targets. */
    fun tick(dt: Float) {
        if (w != targetW) w += (targetW - w) * kotlin.math.min(1f, dt * 2.5f)
        if (kotlin.math.abs(w - targetW) < 0.005f) w = targetW
        val u = if (count > 3) 1f else 0f
        unfold += (u - unfold) * kotlin.math.min(1f, dt * 3f)
        if (kotlin.math.abs(unfold - u) < 0.004f) unfold = u
    }
}

/**
 * The rolling ground of the HILLS bonus: a height for every z that scrolls
 * with the run. Zero everywhere else. Rendering adds it to everything on
 * the road; collision is unaffected (the cube and the row it meets share
 * the same z, so the same offset).
 */
object Terrain {
    @Volatile var amp = 0f
    private var targetAmp = 0f
    private var phase = 0f
    private const val K = 0.24f      // wavelength ≈ 26 units

    fun y(z: Float): Float = if (amp == 0f) 0f else amp * kotlin.math.sin(z * K + phase)

    /** Slope at z (for the camera's pitch and the cube's lean). */
    fun slope(z: Float): Float = if (amp == 0f) 0f else amp * K * kotlin.math.cos(z * K + phase)

    fun set(on: Boolean) { targetAmp = if (on) 1.25f else 0f }

    fun scroll(mv: Float, dt: Float) {
        phase -= mv * K
        amp += (targetAmp - amp) * kotlin.math.min(1f, dt * 1.5f)
        if (kotlin.math.abs(amp - targetAmp) < 0.01f) amp = targetAmp
    }

    fun reset() { amp = 0f; targetAmp = 0f; phase = 0f }
}
