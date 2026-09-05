package cube.run.data

/**
 * Bonus worlds: where portals take you. Each one bends the game's fabric
 * for a short stretch — more lanes, lanes drifting apart with the cube
 * afloat, rolling hills, or colours that will not sit still — and each
 * unlocks once your best score reaches its bar, so there is always a next
 * thing to reach. A portal only opens to worlds you have unlocked.
 */
object Bonus {
    const val NONE = -1
    const val WIDE = 0      // five lanes: the road opens up
    const val HILLS = 1     // the road rolls up and down under you
    const val FLOAT = 2     // the lanes stretch apart and the cube hovers, drifting between them
    const val KALEIDO = 3   // colours cycle, the camera sways, everything glows — trippy

    class World(val id: Int, val name: String, val unlockScore: Int, val hue: Float, val blurb: String)

    val all: List<World> = listOf(
        World(WIDE, "Wide Open", 60, 165f, "Five lanes. Room to breathe, room to lose yourself."),
        World(HILLS, "Rollercoaster", 150, 30f, "The road rises and falls. Hold on."),
        World(FLOAT, "Zero-G", 260, 200f, "The lanes drift apart and you float between them."),
        World(KALEIDO, "Kaleidoscope", 420, 300f, "Nothing holds its colour. Nothing holds still."),
    )

    fun get(id: Int): World = all[id]

    /** Worlds open at a best score of [best]. */
    fun unlocked(best: Int): List<World> = all.filter { best >= it.unlockScore }

    /** Worlds that [newBest] unlocks that [oldBest] had not. */
    fun newlyUnlocked(oldBest: Int, newBest: Int): List<World> = all.filter { oldBest < it.unlockScore && newBest >= it.unlockScore }

    /** The next world still locked at [best], if any. */
    fun nextLocked(best: Int): World? = all.firstOrNull { best < it.unlockScore }
}
