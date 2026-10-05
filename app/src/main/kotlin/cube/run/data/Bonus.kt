package cube.run.data

/**
 * Bonus worlds: where portals take you. Each one bends the game's fabric
 * for a stretch — more lanes, lanes drifting apart with the cube afloat,
 * rolling hills, colours that will not sit still, or open space with low
 * gravity — and each unlocks once your best score reaches its bar, so there
 * is always a next thing to reach. A portal only opens to worlds you have
 * unlocked.
 */
object Bonus {
    const val NONE = -1
    const val WIDE = 0      // five lanes: the road opens up
    const val HILLS = 1     // the road rolls up and down under you
    const val FLOAT = 2     // the lanes stretch apart and the cube hovers, drifting between them
    const val KALEIDO = 3   // colours cycle, the camera sways, everything glows — trippy
    const val SPACE = 4     // the ground falls away: low gravity among planets, stars and asteroids

    class World(
        val id: Int, val name: String, val unlockScore: Int, val hue: Float, val blurb: String,
        /** Rows the stretch lasts before the exit portal. */
        val rows: Int,
        /** The road's shape inside: lane count and spacing. */
        val lanes: Int = 3, val laneW: Float = 1.7f,
    )

    private const val LONGEST_CLASSIC = 46

    val all: List<World> = listOf(
        World(WIDE, "Wide Open", 60, 165f, "Five lanes. Room to breathe, room to lose yourself.", rows = 42, lanes = 5),
        World(HILLS, "Rollercoaster", 150, 30f, "The road rises and falls. Hold on.", rows = 30),
        World(FLOAT, "Zero-G", 260, 200f, "The lanes drift apart and you float between them.", rows = LONGEST_CLASSIC, laneW = 2.6f),
        World(KALEIDO, "Kaleidoscope", 420, 300f, "Nothing holds its colour. Nothing holds still.", rows = LONGEST_CLASSIC),
        // Half as long again as the longest of the others: space is a journey, not a detour.
        World(SPACE, "Outer Space", 560, 265f, "The ground falls away. Stars rush by; planets take their time.", rows = LONGEST_CLASSIC * 3 / 2),
    )

    fun get(id: Int): World = all[id]

    /** Worlds open at a best score of [best]. */
    fun unlocked(best: Int): List<World> = all.filter { best >= it.unlockScore }

    /** Worlds that [newBest] unlocks that [oldBest] had not. */
    fun newlyUnlocked(oldBest: Int, newBest: Int): List<World> = all.filter { oldBest < it.unlockScore && newBest >= it.unlockScore }

    /** The next world still locked at [best], if any. */
    fun nextLocked(best: Int): World? = all.firstOrNull { best < it.unlockScore }
}
