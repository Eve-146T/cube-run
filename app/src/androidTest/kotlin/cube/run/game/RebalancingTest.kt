package cube.run.game

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.core.gfx.TouchInput
import cube.run.data.Progress
import cube.run.data.Settings
import cube.run.data.Skins
import cube.run.data.Trails
import cube.run.data.Wardrobe
import cube.run.game.stage.GiftStage
import cube.run.game.track.Ob
import cube.run.game.track.ObType
import cube.run.game.track.Row
import cube.run.game.track.Track
import cube.run.ui.Hud
import cube.run.ui.PauseSheet
import cube.run.ui.WardrobeView
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class RebalancingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var prefs: SharedPreferences
    private lateinit var saved: Map<String, *>
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun call(owner: Any, name: String) = owner.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(owner)

    @Before fun prepare() {
        prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        saved = prefs.all
        prefs.edit().clear().putInt("coins", 100000).commit()
        Progress.init(context)
        Settings.setDevMode(false)
    }

    @After fun restore() {
        val edit = prefs.edit().clear()
        for ((key, value) in saved) when (value) {
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is String -> edit.putString(key, value)
        }
        edit.commit(); Progress.init(context)
        Stage.paused = false; Stage.mode = Stage.NONE
    }

    private fun gl(action: (CubeRun) -> Unit) {
        val done = CountDownLatch(1); var failure: Throwable? = null
        Gdx.app.postRunnable {
            try { action(Gdx.app.applicationListener as CubeRun) }
            catch (t: Throwable) { failure = t }
            finally { done.countDown() }
        }
        assertTrue(done.await(20, TimeUnit.SECONDS)); failure?.let { throw it }
    }

    private fun withGame(skin: Int = 0, action: (CubeRun) -> Unit) {
        prefs.edit().putInt("skin", skin).putInt("owned_skins", 1 or (1 shl skin)).commit()
        Progress.init(context)
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            gl { game ->
                Stage.paused = false; game.onTap(360f, 760f); Stage.paused = true
                (field(game, "bubble").get(game) as Bubble).reset()
                (field(game, "track").get(game) as Track).rows.clear()
                action(game)
            }
        }
    }

    @Test fun secondWindStockCapsWithoutChargingAndCanBeReplenished() {
        repeat(3) { assertTrue(Progress.buyRevive()) }
        val coins = Progress.coins
        assertFalse(Progress.buyRevive()); assertEquals(coins, Progress.coins)
        Progress.init(context)
        assertEquals(3, Progress.revives); assertFalse(Progress.buyRevive())
        assertTrue(Progress.useRevive()); assertTrue(Progress.buyRevive())
        assertEquals(3, Progress.revives)
        // Existing players keep previously purchased stock, but cannot buy more above the cap.
        prefs.edit().putInt("revives", 7).commit(); Progress.init(context)
        assertFalse(Progress.buyRevive()); assertEquals(7, Progress.revives)
    }

    private fun roll(value: Float) = object : Random() {
        override fun nextBits(bitCount: Int) = 0
        override fun nextFloat() = value
    }

    @Test fun bubblegumSavesExactlyBelow35PercentAndRequiresStock() {
        val chance = Skins.get(16).bubbleSaveChance
        assertEquals(.35f, chance, 0f)
        assertFalse(Progress.useBubble(chance, roll(0f)))
        Progress.addBubble(1)
        assertTrue(Progress.useBubble(chance, roll(.34999f))); assertEquals(1, Progress.bubbles)
        assertTrue(Progress.useBubble(chance, roll(.35f))); assertEquals(0, Progress.bubbles)
        Progress.init(context); assertEquals(0, Progress.bubbles)
        Progress.addBubble(1)
        assertTrue(Progress.useBubble(Skins.get(0).bubbleSaveChance, roll(0f)))
        assertEquals(0, Progress.bubbles)
    }

    @Test fun fasterStartsUnlockSixThroughTenWithoutCycling() = withGame { game ->
        val difficulty = Difficulty(); val fire = FireBoost(game, difficulty)
        for (dev in listOf(false, true)) for (level in 0..5) {
            Settings.setDevMode(dev)
            prefs.edit().putInt(Progress.FASTERSTART.key, level).commit(); Progress.init(context)
            assertEquals(5 + level, Progress.maxStartPresses)
            difficulty.reset(); fire.reset()
            Stage.boostRequests.set(50)
            assertEquals(5 + level, fire.tick(1f, 0f, .45f))
            val speed = difficulty.speed()
            Stage.boostRequests.set(1); assertEquals(0, fire.tick(1f, 0f, .45f))
            assertEquals(speed, difficulty.speed(), 0f)
            if (level == 0) assertEquals(21.6f, speed, .001f)
            if (level == 5) assertEquals(30f, speed, .001f)
        }
        fire.reset(); Stage.boostRequests.set(1)
        assertEquals(0, fire.tick(16f, 0f, .45f))
    }

    private fun obstacle() = Ob(Color.CYAN, 0f, .8f, .7f, ObType.SOLID, 1.4f, 1.6f, .9f)

    @Test fun ghostPhasesOneWholeObstacleWithoutDestroyingItThenDiesOnTheNext() = withGame(13) { game ->
        val track = field(game, "track").get(game) as Track
        val first = obstacle(); val row = Row(0f, arrayListOf(first)); track.rows.add(row)
        repeat(12) { game.tick(0f) }
        assertFalse(field(game, "dead").getBoolean(game))
        assertTrue(field(game, "phaseUsed").getBoolean(game)); assertSame(first, row.obs.single())
        row.z = 3f
        track.rows.add(Row(0f, arrayListOf(obstacle())))
        game.tick(0f)
        assertTrue(field(game, "dead").getBoolean(game))
    }

    @Test fun ghostKeepsItsPhaseWhileABubbleAbsorbsTheHit() = withGame(13) { game ->
        val track = field(game, "track").get(game) as Track
        track.rows.add(Row(0f, arrayListOf(obstacle())))
        val bubble = field(game, "bubble").get(game) as Bubble
        bubble.activate(0f, .45f, quiet = true); game.tick(0f)
        assertFalse(field(game, "phaseUsed").getBoolean(game))
        assertFalse(field(game, "dead").getBoolean(game)); assertFalse(bubble.active)
        assertEquals(5f, bubble.cooldownLeft, .001f)
    }

    @Test fun speedyCubeActuallyRunsThirtyPercentFaster() = withGame(23) { game ->
        val difficulty = field(game, "difficulty").get(game) as Difficulty
        field(game, "runT").setFloat(game, 10f)
        game.tick(0f)
        assertEquals(difficulty.speed() * 1.3f, field(game, "spd").getFloat(game), .001f)
        difficulty.boostTo(1f); game.tick(0f)
        assertEquals(39f, field(game, "spd").getFloat(game), .001f)
    }

    @Test fun noTrailCanBeBoughtEquippedAndReloaded() {
        val trail = Trails.all.single { it.name == "No trail" }
        assertTrue(trail.price > 0); assertEquals(0f, trail.rate, 0f); assertEquals(0, trail.count)
        assertTrue(Progress.buy(Wardrobe.TRAIL, trail.id)); Progress.equip(Wardrobe.TRAIL, trail.id)
        Progress.init(context); assertEquals(trail.id, Progress.trail)
    }

    @Test fun swipesDoNotStartRunsAndPauseAndBackStayPut() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            gl { game ->
                val input = Gdx.input.inputProcessor as TouchInput
                input.touchDown(360, 760, 0, 0); input.touchDragged(360, 500, 0); input.touchUp(360, 500, 0, 0)
                assertFalse(field(game, "started").getBoolean(game))
                input.touchDown(360, 760, 0, 0); input.touchCancelled(360, 760, 0, 0)
                assertFalse(field(game, "started").getBoolean(game))
                input.touchDown(360, 760, 0, 0); input.touchUp(360, 760, 0, 0)
                assertTrue(field(game, "started").getBoolean(game))
            }
            scenario.onActivity { activity ->
                val hud = field(activity, "hud").get(activity) as Hud
                hud.hideOptions(); hud.pause()
                val pause = field(hud, "pauseSheet").get(hud) as PauseSheet
                pause.performClick(); assertTrue(Stage.paused)
                activity.onBackPressed(); assertTrue(Stage.paused); assertFalse(activity.isFinishing)
            }
        }
    }

    @Test fun backOnlyClosesStoresAndClearsWardrobePreview() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val hud = field(activity, "hud").get(activity) as Hud
                activity.onBackPressed(); assertFalse(activity.isFinishing)
                call(hud, "openWardrobe")
                assertTrue(field(hud, "page").get(hud) is WardrobeView)
                assertTrue(Stage.previewSkin >= 0)
                activity.onBackPressed()
                assertEquals(-1, Stage.previewSkin); assertEquals(Stage.NONE, Stage.mode)
            }
        }
    }

    @Test fun boxSkipDuringDropShakeAndRevealAwardsEachBoxOnlyOnce() = withGame { game ->
        val gift = GiftStage(game)
        gift.enter(Color(), Color())
        val before = Progress.boxesOpened
        Stage.openRequests.set(1); Stage.skipBoxRequests.set(1)
        gift.update(0f, 0f)
        assertEquals(before + 1, Progress.boxesOpened)
        repeat(10) { Stage.skipBoxRequests.set(1); gift.update(.01f, 0f) }
        assertEquals(before + 1, Progress.boxesOpened)
        Stage.openRequests.set(1); gift.update(0f, 0f)
        gift.update(1f, 0f) // next box lands and begins shaking
        Stage.skipBoxRequests.set(1); gift.update(0f, 0f)
        assertEquals(before + 2, Progress.boxesOpened)
        repeat(10) { gift.update(.1f, 0f) }
        assertEquals(before + 2, Progress.boxesOpened)
        gift.exit()
    }

    @Test fun ghostPhasesThroughPlatformSidesUntilTheyPass() = withGame(13) { game ->
        val track = field(game, "track").get(game) as Track
        val platform = Ob(Color.CYAN, 0f, .5f, .7f, ObType.PLAT, 1.4f, 1f, 4f)
        track.rows.add(Row(1f, arrayListOf(platform)))
        repeat(12) { game.tick(0f) }
        assertTrue(field(game, "phaseUsed").getBoolean(game))
        assertFalse(field(game, "dead").getBoolean(game))
        val player = field(game, "player").get(game) as Player
        assertEquals(player.ground, player.py, .001f)
        assertSame(platform, track.rows.single().obs.single())
    }

    @Test fun tappingSkipsBoxUiWithoutSpendingTheNextBox() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            lateinit var flow: cube.run.ui.RunOverFlow
            val before = Progress.boxesOpened
            scenario.onActivity { activity ->
                val hud = field(activity, "hud").get(activity) as Hud
                hud.showRunOver(10, 10, false, 0, 2)
                flow = field(hud, "runOver").get(hud) as cube.run.ui.RunOverFlow
                call(flow, "showBoxes")
                call(flow, "tapBox"); call(flow, "tapBox")
            }
            gl { it.tick(0f) }
            scenario.onActivity {
                assertEquals(before + 1, Progress.boxesOpened)
                assertEquals(1, field(flow, "boxesLeft").getInt(flow))
                assertFalse(field(flow, "boxBusy").getBoolean(flow))
                val card = field(flow, "rewardCard").get(flow) as android.view.View
                assertEquals(1f, card.alpha, .001f)
                call(flow, "tapBox"); call(flow, "tapBox")
            }
            gl { it.tick(0f) }
            scenario.onActivity {
                assertEquals(before + 2, Progress.boxesOpened)
                assertEquals(0, field(flow, "boxesLeft").getInt(flow))
                assertFalse(field(flow, "boxBusy").getBoolean(flow))
            }
        }
    }

    @Test fun cosmeticsCanCombineAbilities() {
        val skin = Skins.Skin(24, "Combined fixture", 0, Skins.FIXED,
            abilities = listOf(Skins.Ability.PHASE, Skins.Ability.SPEED, Skins.Ability.BUBBLE_SAVER))
        assertEquals(3, skin.abilities.size)
        assertEquals(1.3f, skin.speedMultiplier, .001f)
        assertEquals(.35f, skin.bubbleSaveChance, .001f)
    }
}
