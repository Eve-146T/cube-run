package cube.run.core

import java.util.concurrent.atomic.AtomicInteger

/**
 * UI → GL channel for the HUD's showcase screens and the pause. The HUD asks
 * the game to present a scene ([mode]): the run-over mystery-box stage ([BOX],
 * with [openRequests] to open boxes), the wardrobe ([SKINS], with [previewSkin]
 * to try on a skin) or the results stage ([RESULT]: the equipped cube posed
 * high on a dark stage under the score). [paused] freezes the simulation.
 * The game answers through `GameSession.boxOpened`.
 * Plain volatile/atomic state — no callbacks cross threads.
 */
object Stage {
    const val NONE = 0
    const val BOX = 1
    const val SKINS = 2
    const val RESULT = 3

    @Volatile var mode = NONE

    /** The pause menu is up: the game renders its last frame and integrates nothing. */
    @Volatile var paused = false

    /** Boxes the player has asked to open that the game hasn't started opening yet. */
    val openRequests = AtomicInteger(0)

    /** Skin id to show on the wardrobe stage (-1 = the equipped one). */
    @Volatile var previewSkin = -1

    fun reset() {
        mode = NONE
        paused = false
        openRequests.set(0)
        previewSkin = -1
    }
}
