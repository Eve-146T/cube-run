package cube.run.core

/**
 * Bridge between a game and its host (HUD, scoring, lifecycle).
 * Safe to call from any thread (3D games call from the GL thread).
 */
interface GameSession {
    val score: Int
    val isOver: Boolean

    /** Sets the HUD score. Ignored after gameOver(). */
    fun setScore(v: Int)

    fun addScore(d: Int = 1)

    /** Called once when a run actually begins (first input), e.g. to hide pre-run options. */
    fun runStarted() {}

    /** Coins collected so far this run (banked into [Progress] at game over). */
    fun setCoins(v: Int) {}

    /** Bubble shields left in stock (shown as a HUD hint). */
    fun setBubbles(v: Int) {}

    /** Mystery boxes collected so far this run (opened on the run-over screens). */
    fun setBoxes(v: Int) {}

    /** The 3D gift stage just opened a box (see [Stage]); [kind] is a `Progress.BoxReward` kind. */
    fun boxOpened(kind: Int, amount: Int) {}

    /**
     * Ends the run: banks the coins, saves the high score, plays the
     * success/fail sound + haptic, and shows the run-over screens.
     * The game's update loop keeps running afterwards (for death animation) —
     * guard gameplay logic with [isOver].
     */
    fun gameOver()
}
