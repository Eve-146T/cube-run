package cube.run.game

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Settings
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class IdlePilotTest {
    private val originalDev = Settings.devMode
    private val originalSection = Settings.testSection
    @After fun reset() {
        Settings.setDevMode(originalDev); Settings.testSection = originalSection
        Stage.userInteraction(); Stage.paused = false
    }
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun gl(action: (CubeRun) -> Unit) {
        val done = CountDownLatch(1); var failure: Throwable? = null
        Gdx.app.postRunnable {
            try { action(Gdx.app.applicationListener as CubeRun) } catch (t: Throwable) { failure = t } finally { done.countDown() }
        }
        assertTrue(done.await(10, TimeUnit.SECONDS)); failure?.let { throw it }
    }

    @Test fun requiresTwoUninterruptedMinutesInDevHomeScreen() {
        Stage.reset()
        IdlePilot().use { pilot ->
            Settings.setDevMode(false)
            assertFalse(pilot.ready(200f, true))
            Settings.setDevMode(true)
            assertFalse(pilot.ready(119f, true))
            Stage.userInteraction()
            assertFalse(pilot.ready(1f, true))
            assertFalse(pilot.ready(118f, true))
            assertTrue(pilot.ready(1f, true))
            assertFalse(pilot.ready(1f, false))
            assertFalse(pilot.ready(119f, true))
            Stage.paused = true
            assertFalse(pilot.ready(10f, true))
            Stage.paused = false
            assertFalse(pilot.ready(119f, true))
            Stage.userInteraction(down = true)
            assertFalse(pilot.ready(200f, true))
            Stage.pointerDown = false
            assertFalse(pilot.ready(119f, true))
            assertTrue(pilot.ready(1f, true))
        }
    }

    @Test fun disposingAnOldRunCannotCancelTheNewPilot() {
        Stage.reset(); Settings.setDevMode(true)
        val old = IdlePilot(); val current = IdlePilot()
        try {
            old.start(0f); current.start(0f); old.close()
            assertTrue(current.active)
            Stage.userInteraction()
            assertFalse(current.active)
        } finally { old.close(); current.close() }
    }

    @Test fun retimedCommittedGestureRunsOnceAndUpdatesThePlanningSnapshot() {
        Stage.reset(); Settings.setDevMode(true); Lanes.reset()
        IdlePilot().use { pilot ->
            pilot.start(0f)
            field(pilot, "lanes").setInt(pilot, Lanes.count)
            val first = IdlePilot.Event(1, 0f, cube.run.bot.Action.RIGHT)
            val plan = cube.run.bot.Plan(true, IntArray(90), 90, 0f, 0, 0)
            val old = IdlePilot.Decision(0f, 1f/60f, plan, .01f, 12, listOf(first))
            val replacement = old.copy(events = listOf(first.copy(at = .002f),
                IdlePilot.Event(2, .12f, cube.run.bot.Action.LEFT)))
            field(pilot, "decision").set(pilot, old)
            field(pilot, "pending").set(pilot, java.util.concurrent.CompletableFuture.completedFuture(replacement))
            val random = kotlin.random.Random(3)
            val track = cube.run.game.track.Track(random, cube.run.game.track.ObstacleFactory(random))
            val body = cube.run.bot.Body(lane = 0, x = -1.7f)
            val inputs = arrayListOf<Int>()
            pilot.drive(.001f, 30f, 1f, track, body) { inputs.add(it) }
            assertEquals("A search in this slice must see the lane we just selected", 1, body.lane)
            pilot.drive(.11f, 30f, 1f, track, body) { inputs.add(it) }
            assertEquals("The retimed prefix must not repeat RIGHT", listOf(1), inputs)
            pilot.drive(.121f, 30f, 1f, track, body) { inputs.add(it) }
            assertEquals("LEFT must execute on time, without a repeated input consuming its cooldown", listOf(1, 0), inputs)
            assertEquals(0, body.lane)
        }
    }

    @Test fun startsBoostsFiveTimesAndHandsTheSameRunToRealTouch() {
        Settings.setDevMode(true)
        Settings.testSection = 56 // Coin-only section: the injected barrier below is the test's obstacle.

        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            SystemClock.sleep(700)
            gl { game ->
                val pilot = field(game, "idlePilot").get(game) as IdlePilot
                field(pilot, "idleSeconds").setFloat(pilot, 119.99f)
            }
            SystemClock.sleep(4000)
            var time = 0f
            gl { game ->
                val pilot = field(game, "idlePilot").get(game) as IdlePilot
                assertTrue("Bot starts itself", pilot.active)
                assertTrue(field(game, "started").getBoolean(game))
                val fire = field(game, "fire").get(game)!!
                assertEquals("Exactly five opening boosts", 5, field(fire, "taps").getInt(fire))
                assertNotNull("Obstacle planning is running", field(pilot, "worker").get(pilot))
                time = game.time
            }
            var laneBefore = 0
            var hits = 0
            lateinit var barrier: cube.run.game.track.Row
            gl { game ->
                val player = field(game, "player").get(game) as Player
                laneBefore = player.lane
                val track = field(game, "track").get(game) as cube.run.game.track.Track
                track.rows.clear()
                field(game, "runSkin").set(game, cube.run.data.Skins.get(0))
                (field(game, "bubble").get(game) as Bubble).reset()
                field(game, "testCrashObserver").set(game, { hits++ })
                barrier = cube.run.game.track.Row(-24f, arrayListOf(
                    cube.run.game.track.Ob(com.badlogic.gdx.graphics.Color.WHITE, Lanes.x(laneBefore), 4f,
                        .6f, cube.run.game.track.ObType.SOLID, 1.2f, 8f, 1f)
                ), Lanes.w)
                track.rows.add(barrier)
                // The fixture replaced the entire visible course; discard its old prediction.
                (field(game, "idlePilot").get(game) as IdlePilot).replan()
            }
            var steered = false
            repeat(14) {
                SystemClock.sleep(100)
                gl { game ->
                    val player = field(game, "player").get(game) as Player
                    if (player.lane != laneBefore) steered = true
                }
            }
            gl { game ->
                assertTrue("The live planner steers around a blocking obstacle", steered)
                assertTrue("The entire barrier passed the player", barrier.z > 1.2f)
                assertEquals("No collision, phase or revival needed", 0, hits)
                assertFalse("Bot survives the obstacle", field(game, "dead").getBoolean(game))
            }
            scenario.onActivity {
                val at = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(at, at, MotionEvent.ACTION_DOWN, 300f, 650f, 0)
                val up = MotionEvent.obtain(at, at + 20, MotionEvent.ACTION_UP, 300f, 650f, 0)
                it.dispatchTouchEvent(down)
                assertFalse("Touch cancels on the UI thread, before another bot frame", Stage.botPlaying)
                it.dispatchTouchEvent(up); down.recycle(); up.recycle()
            }
            SystemClock.sleep(100)
            gl { game ->
                assertFalse((field(game, "idlePilot").get(game) as IdlePilot).active)
                assertTrue(field(game, "started").getBoolean(game))
                assertTrue("Takeover keeps the current run", game.time > time)
            }
        }
    }
}
