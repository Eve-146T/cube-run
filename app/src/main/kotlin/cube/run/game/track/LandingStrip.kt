package cube.run.game.track

/**
 * A jetpack flight's landing strip: a stretch of road anchored in the world
 * that the track keeps free of hazards and the road paints like a finish
 * line. The descent runs over its near part, the cube touches down at
 * [touchZ], and the runway beyond gives a beat on the ground before the
 * hazards resume. The jet's clock is the strip's arrival, so the flight
 * always lands on it, whatever the speed did on the way.
 */
class LandingStrip(touchZ: Float, val descentLen: Float, val runwayLen: Float) {
    var touchZ = touchZ
        private set
    /** Where the descent starts: the strip's near end. */
    val startZ: Float get() = touchZ + descentLen
    /** The runway's far end: hazards resume past it. */
    val endZ: Float get() = touchZ - runwayLen
    /** Gone behind the camera. */
    val gone: Boolean get() = endZ > 14f

    /** Seconds of game time at touchdown (negative before it); [impactX] and [impactPower] shape the strip's shock ripple. */
    var impactAt = -1f
    var impactX = 0f
    var impactPower = 0f

    fun scroll(mv: Float) { touchZ += mv }

    /** Rows here spawn clear. A faster flight starts down a little early, so the near margin is generous. */
    fun covers(z: Float): Boolean = z >= endZ && z <= startZ + descentLen * 0.5f

    /** The descent's progress (0 where it starts, 1 at touchdown) when the cube reaches world [z]. */
    fun progressAt(z: Float): Float = 1f + (touchZ - z) / descentLen
}
