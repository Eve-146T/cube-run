package cube.run.game.track

/**
 * Where a jetpack flight comes down, anchored in the world: a short stretch
 * of open road around [touchZ] with a checkerboard of coins on the ground
 * past it. It only opens up for the last part of the descent, where the
 * cube is low enough to meet anything, so it does not show far ahead. The
 * jet's clock is its arrival, so the flight always lands in it.
 */
class LandingStrip(touchZ: Float, val descentLen: Float, val runwayLen: Float) {
    var touchZ = touchZ
        private set
    /** The runway's far end: hazards resume past it. */
    val endZ: Float get() = touchZ - runwayLen
    /** Gone behind the camera. */
    val gone: Boolean get() = endZ > 14f

    /** The checkerboard of coins is down (it goes on one row, the first that reaches it). */
    var gridLaid = false

    fun scroll(mv: Float) { touchZ += mv }

    /** Rows here spawn open (pads still come): the low end of the descent and the runway. */
    fun covers(z: Float): Boolean = z >= endZ && z <= touchZ + descentLen * OPEN

    /** The descent's progress (0 where it starts, 1 at touchdown) when the cube reaches world [z]. */
    fun progressAt(z: Float): Float = 1f + (touchZ - z) / descentLen

    /** The checkerboard on the runway: is there a coin in [lane] of grid row [k] (counted from touchdown)? */
    fun coinAt(k: Int, lane: Int): Boolean = (k + lane) % 2 == 0

    /** World z of grid row [k]. */
    fun gridZ(k: Int): Float = touchZ - GRID_LEAD - k * GRID_STEP

    companion object {
        /** The share of the descent, before touchdown, that is kept open: the cube is too high to meet anything above it. */
        const val OPEN = 0.4f
        const val GRID_STEP = 1.6f
        const val GRID_LEAD = 0.8f
    }
}
