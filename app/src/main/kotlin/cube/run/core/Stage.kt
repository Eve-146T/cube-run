package cube.run.core

import java.util.concurrent.atomic.AtomicInteger

/**
 * UI → GL channel for the HUD's showcase screens and the pause. The HUD asks
 * the game to present a scene ([mode]): the run-over mystery-box stage ([BOX],
 * with [openRequests] to open boxes), the wardrobe ([SKINS], with the
 * preview fields to try items on) or the results stage ([RESULT]: the
 * equipped cube posed high on a dark stage under the score). [paused]
 * freezes the simulation. The game answers through `GameSession.boxOpened`.
 * Plain volatile/atomic state — no callbacks cross threads.
 */
object Stage {
    const val NONE = 0
    const val BOX = 1
    const val SKINS = 2
    const val RESULT = 3
    /** The shop: the equipped cube small at the top, reacting to what you buy. */
    const val SHOP = 4

    // purchase demos the shop asks the stage to play (see game.stage.Demos)
    const val DEMO_BUBBLE = 1
    const val DEMO_MAGNET = 2
    const val DEMO_MULT = 3
    const val DEMO_JET = 4
    const val DEMO_SAFESTART = 5
    const val DEMO_COINS = 6
    const val DEMO_PORTAL = 7
    const val DEMO_BOX = 8
    const val DEMO_REVIVE = 9

    /** Any real touch cancels the dev attract run before another bot action can land. */
    val interactions = AtomicInteger(0)
    @Volatile var pointerDown = false
    @Volatile var homeScreen = true
    @Volatile internal var botOwner: Any? = null
    val botPlaying get() = botOwner != null
    fun userInteraction(down: Boolean = false) {
        pointerDown = down
        interactions.incrementAndGet()
        botOwner = null
    }

    @Volatile var mode = NONE
    /** Shop navigation's single UI-driven clock: 0 = menu, 1 = showroom. */
    @Volatile var shopProgress = 0f
    const val SHOP_TAP = 1
    const val SHOP_LEFT = 2
    const val SHOP_RIGHT = 3
    const val SHOP_UP = 4
    const val SHOP_DOWN = 5
    /** A hidden showroom gesture; these never purchase, equip, or award anything. */
    val shopPlayRequests = AtomicInteger(0)

    /** The pause menu is up: the game renders its last frame and integrates nothing. */
    @Volatile var paused = false

    /** Seconds into the jackpot show (see [JackpotBeats]), or -1 when none is playing. GL-owned. */
    @Volatile var jackpotClock = -1f
    /** Coins the playing jackpot pays (several simultaneous wins add up). GL-owned. */
    @Volatile var jackpotAmount = 0

    /** Dev tool: the pause card asked for the run to end now (crash → results). */
    @Volatile var endRun = false

    /** Boxes the player has asked to open that the game hasn't started opening yet. */
    val openRequests = AtomicInteger(0)
    val skipBoxRequests = AtomicInteger(0)
    /** GL-owned acknowledgement: the gift camera is still being presented. */
    @Volatile var giftShowing = false
    /** Shop rewards are already banked; the gift stage only presents them. */
    val purchasedBoxRewards = java.util.concurrent.ConcurrentLinkedQueue<cube.run.data.Progress.BoxReward>()

    /** Taps on the HUD's boost button the game hasn't applied yet. */
    val boostRequests = AtomicInteger(0)

    /** A purchase demo the stage should play next (a DEMO_* code, 0 = none). */
    val demoRequests = AtomicInteger(0)

    /** Wardrobe: how many item switches the stage hasn't celebrated yet (a spin-flip each). */
    val previewKicks = AtomicInteger(0)

    /** Wardrobe: purchases the stage hasn't celebrated yet (a big flip, gold rays, shards). */
    val previewBuys = AtomicInteger(0)

    /** The results stage: the sunburst's hue behind the cube, and whether this run set a record. */
    @Volatile var resultHue = 46f
    @Volatile var resultRecord = false

    /** Wardrobe: which category is being browsed (data.Wardrobe.CUBE / BUBBLE / TRAIL). */
    @Volatile var previewCat = 0
    /** Cube skin id to show on the wardrobe stage (-1 = the equipped one). */
    @Volatile var previewSkin = -1
    /** Bubble skin id to show (-1 = the equipped one). */
    @Volatile var previewBubble = -1
    /** Trail id to show (-1 = the equipped one). */
    @Volatile var previewTrail = -1

    fun reset() {
        homeScreen = true; botOwner = null; pointerDown = false
        mode = NONE
        shopProgress = 0f
        shopPlayRequests.set(0)
        paused = false
        endRun = false
        jackpotClock = -1f
        jackpotAmount = 0
        openRequests.set(0)
        skipBoxRequests.set(0)
        giftShowing = false
        purchasedBoxRewards.clear()
        boostRequests.set(0)
        demoRequests.set(0)
        previewKicks.set(0)
        previewBuys.set(0)
        previewCat = 0
        clearPreview()
    }

    /** Leave the wardrobe: clear every try-on. */
    fun clearPreview() {
        previewSkin = -1
        previewBubble = -1
        previewTrail = -1
    }
}
