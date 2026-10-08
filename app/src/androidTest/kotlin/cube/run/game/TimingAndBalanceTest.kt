package cube.run.game

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.random.Random
import cube.run.game.track.Track
import cube.run.data.Bonus
import cube.run.core.FrameStepper
import cube.run.core.Stage
import cube.run.data.BoxLoot
import cube.run.data.Progress
import cube.run.data.Settings
import cube.run.game.track.Coin
import cube.run.game.track.Row
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimingAndBalanceTest {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }

    @Test fun realRunLaunchUsesThePresetFromItsFirstFrameAndAfterRestart() {
        ActivityScenario.launch(GameActivity::class.java).use {
            val done = CountDownLatch(1)
            var failure: Throwable? = null
            Gdx.app.postRunnable {
                val game = Gdx.app.applicationListener as CubeRun
                val oldStart = Settings.startSpeed; val oldEnabled = Settings.startSpeedEnabled
                val oldSkin = Progress.skin
                @Suppress("UNCHECKED_CAST")
                val perks = field(Progress, "perkLevels").get(null) as MutableMap<String, Int>
                val oldPerk = perks[Progress.FASTERSTART.key]
                try {
                    field(Progress, "skin").setInt(null, 0)
                    for (owned in listOf(0, 5)) {
                        perks[Progress.FASTERSTART.key] = owned
                        for ((preset, enabled) in listOf(0 to true, 3 to true, 10 to true, 10 to false)) {
                            Settings.setStartSpeed(preset); Settings.setStartSpeedEnabled(enabled)
                            repeat(2) { restart ->
                                (game.session as cube.run.core.GameHostSession).resetToMenu()
                                game.resetToMenu()
                                game.onTap(0f, 0f)
                                assertTrue("Start input begins the run", field(game, "started").getBoolean(game))
                                val used = if (enabled) preset.coerceAtMost(5 + owned) else 0
                                val expected = when (used) { 0 -> 12.4f; 3 -> 17.92f; 5 -> 21.6f; 10 -> 30f; else -> error("Unexpected fixture") }
                                val difficulty = field(game, "difficulty").get(game) as Difficulty
                                assertEquals("Run initialization keeps preset $preset ($enabled)", expected, difficulty.speed(), .001f)
                                val launch = if (used == 0) 4.5f else expected
                                assertEquals("Preset applies before the first simulation frame", launch, field(game, "spd").getFloat(game), .001f)
                                val track = field(game, "track").get(game) as Track
                                assertEquals("Initial track spacing uses the launch difficulty", expected, track.speed, .001f)
                                track.rows.clear()
                                val row = Row(-10f, arrayListOf()); track.rows.add(row)
                                game.tick(1f / 60f)
                                assertEquals("The road actually moves at the selected tempo", -10f + launch / 60f, row.z, .001f)
                                android.util.Log.i("StartSpeed", "preset=$preset enabled=$enabled owned=$owned restart=$restart firstFrameSpeed=${field(game, "spd").getFloat(game)}")
                                repeat(100) { game.tick(1f / 60f) }
                                assertEquals("The selected speed survives the launch animation", expected, difficulty.speed(), .05f)
                            }
                        }
                    }
                } catch (t: Throwable) { failure = t } finally {
                    Settings.setStartSpeed(oldStart); Settings.setStartSpeedEnabled(oldEnabled)
                    field(Progress, "skin").setInt(null, oldSkin)
                    if (oldPerk == null) perks.remove(Progress.FASTERSTART.key) else perks[Progress.FASTERSTART.key] = oldPerk
                    (game.session as cube.run.core.GameHostSession).resetToMenu(); game.resetToMenu()
                    done.countDown()
                }
            }
            assertTrue(done.await(30, TimeUnit.SECONDS)); failure?.let { throw it }
        }
    }

    @Test fun elapsedTimeIsPreservedAt90HzAndDuringHitches() {
        for (fps in listOf(90, 60, 30, 20, 8)) {
            val clock = FrameStepper(); var elapsed = 0.0
            repeat(fps * 10) { clock.advance(1f / fps, false) { dt ->
                assertTrue(dt <= 1f / 55f); elapsed += dt
            } }
            assertEquals("$fps FPS lost game time", 10.0, elapsed, .0001)
        }
        val clock = FrameStepper(); var elapsed = 0.0
        clock.advance(.8f, false) { elapsed += it }
        assertTrue("Long stalls must retain their excess time", clock.pendingSeconds > .25)
        clock.advance(.02f, false) { elapsed += it }
        assertEquals(.82, elapsed, .00001)
        clock.advance(9f, true) { assertEquals(0f, it) }
        clock.advance(.01f, false) { assertEquals(.01f, it) }
    }

    @Test fun actualDistanceAndPowerTimersKeepPaceAtLowFrameRates() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val done = CountDownLatch(1); var failure: Throwable? = null
            Gdx.app.postRunnable {
                val sound = Settings.soundEnabled; val haptics = Settings.hapticsEnabled
                try {
                    Settings.setSoundEnabled(false); Settings.setHapticsEnabled(false)
                    val game = Gdx.app.applicationListener as CubeRun
                    Stage.paused = false; game.onTap(360f, 760f)
                    field(game, "runSkin").set(game, cube.run.data.Skins.get(0)) // timing fixture uses Classic regardless of the saved cosmetic
                    val track = field(game, "track").get(game) as Track
                    val difficulty = field(game, "difficulty").get(game) as Difficulty
                    val powers = field(game, "powerUps").get(game) as PowerUps
                    val bubble = field(game, "bubble").get(game) as Bubble
                    val player = field(game, "player").get(game) as Player
                    track.forceBonus(Bonus.NONE); Terrain.set(false)
                    for (fps in listOf(90, 60, 30, 20, 8)) {
                        track.rows.clear(); field(track, "spawnAcc").setFloat(track, -10000f)
                        field(game, "dist").setFloat(game, 0f); field(game, "runT").setFloat(game, 10f)
                        field(game, "jetBoost").setFloat(game, 0f)
                        player.setFlying(false); player.forceGround(0f)
                        difficulty.boostTo(1f); bubble.reset(); powers.reset(); powers.magnet.start(20f)
                        val clock = FrameStepper()
                        repeat(fps * 10) { clock.advance(1f / fps, false) { game.tick(it) } }
                        assertEquals("$fps FPS distance", 300f, field(game, "dist").getFloat(game), .01f)
                        assertEquals("$fps FPS timer", 10f, powers.magnet.left, .003f)
                        assertFalse("Empty timing fixture must survive", game.session.isOver)
                    }
                } catch (t: Throwable) { failure = t }
                finally { Settings.setSoundEnabled(sound); Settings.setHapticsEnabled(haptics); done.countDown() }
            }
            assertTrue(done.await(30, TimeUnit.SECONDS)); failure?.let { throw it }
        }
    }

    @Test fun boxPitySurvivesReloadAndBanksTheActualRewards() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Progress.init(context)
        field(Progress, "boxCoinStreak").setInt(Progress, 0)
        val coinRoll = object : Random() {
            override fun nextBits(bitCount: Int) = 0
            override fun nextFloat() = .9f
        }
        val coins = Progress.coins; val bubbles = Progress.bubbles; val opened = Progress.boxesOpened
        val first = Progress.openBox(coinRoll); val second = Progress.openBox(coinRoll)
        assertEquals(Progress.BoxReward.COINS, first.kind); assertEquals(Progress.BoxReward.COINS, second.kind)
        assertTrue(first.amount >= 500); assertEquals(coins + first.amount + second.amount, Progress.coins)
        Progress.init(context)
        val third = Progress.openBox(coinRoll)
        assertEquals(Progress.BoxReward.BUBBLE, third.kind)
        assertEquals(bubbles + third.amount, Progress.bubbles); assertEquals(opened + 3, Progress.boxesOpened)
        Progress.init(context)
        assertEquals(0, field(Progress, "boxCoinStreak").getInt(Progress))
        assertEquals(bubbles + third.amount, Progress.bubbles)
    }

    @Test fun pricesAndBoxBandsMatchTheProgressionPolicy() {
        val all = Progress.upgrades + Progress.perks
        for (u in all) for (level in 0 until u.max - 1) assertTrue(u.price(level + 1) > u.price(level))
        val total = all.sumOf { u -> (0 until u.max).sumOf { u.price(it) } }
        assertTrue("Economy total: $total", total in 1_100_000..1_300_000)
        assertTrue(Progress.COINVALUE.price(9) > 100_000)
        val kinds = (0 until 1000).map { BoxLoot.kind(it / 1000f, 0) }.toSet()
        assertEquals(setOf(0, 1, 2, 3), kinds)
        for (i in 0 until 1000) assertNotEquals(Progress.BoxReward.COINS, BoxLoot.kind(i / 1000f, 2))
        assertEquals(Progress.BoxReward.BUBBLE, BoxLoot.kind(.4f, 0))
    }

    @Test fun scrollingDoesNotChangeThePickupPhaseOrOriginalCoinLane() {
        val row = Row(-30f, arrayListOf()); val phase = row.visualPhase
        repeat(1000) { row.z += 1.7f; assertEquals(phase, row.visualPhase, 0f) }
        val coin = Coin(1.7f, .5f, -3f); coin.x = 0f
        assertEquals(1.7f, coin.restX, 0f)
    }

    @Test fun bubbleCooldownAndPurchasedBoostLimitUseTheRealGame() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val done = CountDownLatch(1); var failure: Throwable? = null
            Gdx.app.postRunnable {
                val oldDev = Settings.devMode
                val oldStart = Settings.startSpeed
                val oldStartEnabled = Settings.startSpeedEnabled
                try {
                    val game = Gdx.app.applicationListener as CubeRun
                    Stage.paused = false; game.onTap(360f, 760f)
                    field(game, "runSkin").set(game, cube.run.data.Skins.get(0)) // timing fixture uses Classic regardless of the saved cosmetic
                    val bubble = field(game, "bubble").get(game) as Bubble
                    bubble.reset(); bubble.duration = .01f; bubble.activate(0f, .45f, quiet = true)
                    bubble.update(.02f, 0f, 0f, .45f)
                    assertFalse(bubble.ready); assertEquals(5f, bubble.cooldownLeft, .001f)
                    val stock = Progress.bubbles
                    val activate = CubeRun::class.java.getDeclaredMethod("tryBubble").apply { isAccessible = true }
                    assertEquals(false, activate.invoke(game)); assertEquals(stock, Progress.bubbles)
                    bubble.update(4.9f, 0f, 0f, .45f); assertFalse(bubble.ready)
                    bubble.update(.11f, 0f, 0f, .45f); assertTrue(bubble.ready)
                    bubble.activate(0f, .45f, quiet = true); bubble.pop(0f, .45f)
                    assertEquals(5f, bubble.cooldownLeft, .001f)
                    bubble.reset(); assertTrue(bubble.ready)

                    val difficulty = Difficulty(); val fire = FireBoost(game, difficulty)
                    for (dev in listOf(false, true)) {
                        Settings.setDevMode(dev)
                        // Preset presses consume only their share of the same boost budget.
                        for (preset in listOf(0, 3, Progress.maxStartPresses, 10)) {
                            Settings.setStartSpeed(preset); difficulty.reset(); fire.reset()
                            val used = preset.coerceAtMost(Progress.maxStartPresses)
                            if (used > 0) assertTrue("Preset raises the launch speed", difficulty.speed() > difficulty.minSpd)
                            Stage.boostRequests.set(50)
                            assertEquals(Progress.maxStartPresses - used, fire.tick(1f, 0f, .45f))
                        }
                        Settings.setStartSpeed(3); Settings.setStartSpeedEnabled(false)
                        difficulty.reset(); fire.reset(); Stage.boostRequests.set(50)
                        assertEquals("Disabled preset leaves every boost press available", Progress.maxStartPresses, fire.tick(1f, 0f, .45f))
                        assertEquals("Disabling remembers the configured tempo", 3, Settings.startSpeed)
                        Settings.setStartSpeedEnabled(true); difficulty.reset(); fire.reset()
                        Stage.boostRequests.set(50)
                        assertEquals("Re-enabling applies the remembered tempo", Progress.maxStartPresses - 3, fire.tick(1f, 0f, .45f))
                        Settings.setStartSpeed(0); difficulty.reset(); fire.reset()
                        Stage.boostRequests.set(50)
                        assertEquals(Progress.maxStartPresses, fire.tick(1f, 0f, .45f))
                        val speed = difficulty.speed()
                        Stage.boostRequests.set(1); assertEquals(0, fire.tick(1f, 0f, .45f))
                        assertEquals(speed, difficulty.speed(), 0f)
                        fire.reset(); Stage.boostRequests.set(1)
                        assertEquals(0, fire.tick(16f, 0f, .45f))
                    }
                } catch (t: Throwable) { failure = t }
                finally { Settings.setDevMode(oldDev); Settings.setStartSpeed(oldStart); Settings.setStartSpeedEnabled(oldStartEnabled); Stage.boostRequests.set(0); done.countDown() }
            }
            assertTrue(done.await(25, TimeUnit.SECONDS)); failure?.let { throw it }
        }
    }
}
