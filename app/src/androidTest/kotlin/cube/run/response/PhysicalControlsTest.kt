package cube.run.response

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.bot.Body
import cube.run.bot.BotFixtures
import cube.run.bot.value
import cube.run.core.Stage
import cube.run.data.Settings
import cube.run.game.CubeRun
import cube.run.game.Player
import cube.run.ui.Hud
import cube.run.ui.RunOverFlow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class PhysicalControlsTest {
    private fun gl(block: (CubeRun, Player) -> Unit) {
        val done = CountDownLatch(1)
        var error: Throwable? = null
        Gdx.app.postRunnable {
            try {
                val game = Gdx.app.applicationListener as CubeRun
                block(game, value(game, "player"))
            } catch (t: Throwable) { error = t } finally { done.countDown() }
        }
        assertTrue("GL thread timed out", done.await(20, TimeUnit.SECONDS))
        error?.let { throw it }
    }

    private fun key(scenario: ActivityScenario<GameActivity>, code: Int, repeat: Int = 0) {
        scenario.onActivity {
            assertTrue(it.dispatchKeyEvent(KeyEvent(0, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, code, repeat)))
            assertTrue(it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)))
        }
    }

    private fun launch(autoStart: Boolean = true): ActivityScenario<GameActivity> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scenario = ActivityScenario.launch<GameActivity>(Intent(context, GameActivity::class.java)
            .putExtra(Hud.EXTRA_AUTOSTART, autoStart))
        scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
        gl { _, _ -> }
        if (autoStart) {
            // Autostart happens in tick(), after create(), and the HUD follows
            // on the UI thread. A first GL callback is not a run-ready signal.
            await {
                var ready = false
                scenario.onActivity { activity -> ready = value(value<Hud>(activity, "hud"), "runStarted") }
                ready
            }
        }
        return scenario
    }

    private fun await(condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 20_000
        while (!condition()) {
            assertTrue("Timed out waiting for state transition", SystemClock.uptimeMillis() < until)
            SystemClock.sleep(20)
        }
    }

    @Test fun physicalDirectionsUseRealPlayerActions() {
        launch().use { scenario ->
            val saved = Settings.smoothControl
            try {
                Settings.setSmoothControl(true)
                gl { _, p -> Stage.paused = false; BotFixtures.restore(p, Body(lane = 1)) }
                key(scenario, KeyEvent.KEYCODE_DPAD_LEFT)
                gl { _, p -> assertEquals(0, p.lane) }
                key(scenario, KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 1)
                gl { _, p -> assertEquals("Held key must not move", 0, p.lane) }
                key(scenario, KeyEvent.KEYCODE_D)
                gl { _, p -> assertEquals(1, p.lane) }
                scenario.onActivity { activity ->
                    PhysicalInputTest.joystick(hatX = 1f).let {
                        assertTrue(activity.dispatchGenericMotionEvent(it)); it.recycle()
                    }
                }
                gl { _, p -> assertEquals(2, p.lane); BotFixtures.restore(p, Body()) }
                key(scenario, KeyEvent.KEYCODE_DPAD_UP)
                gl { _, p ->
                    assertTrue("UP should jump", p.air)
                    // Keep the airborne DOWN case independent of how long the
                    // UI thread takes to deliver the next key on slow devices.
                    BotFixtures.restore(p, Body(y = 100f, air = true))
                }
                key(scenario, KeyEvent.KEYCODE_DPAD_DOWN)
                gl { _, p -> assertTrue("DOWN in air should slam", value(p, "slamming")); BotFixtures.restore(p, Body()) }
                key(scenario, KeyEvent.KEYCODE_S)
                gl { _, p -> assertTrue("DOWN on ground should duck", value<Float>(p, "duckT") > 0f) }
            } finally { Settings.setSmoothControl(saved) }
        }
    }

    @Test fun pauseConsumesMovementAndConfirmResumesWithoutClickingTheGame() {
        launch().use { scenario ->
            key(scenario, KeyEvent.KEYCODE_BUTTON_START)
            assertTrue(Stage.paused)
            var lane = -1
            gl { _, p -> lane = p.lane }
            key(scenario, KeyEvent.KEYCODE_DPAD_RIGHT)
            gl { _, p -> assertEquals(lane, p.lane) }
            key(scenario, KeyEvent.KEYCODE_BUTTON_B)
            await { !Stage.paused }
            assertFalse(Stage.paused)
            key(scenario, KeyEvent.KEYCODE_P)
            assertTrue(Stage.paused)
            key(scenario, KeyEvent.KEYCODE_ENTER)
            await { !Stage.paused }
            assertFalse(Stage.paused)
        }
    }

    @Test fun menuFocusCanReturnToStartAndConfirmStartsTheRun() {
        launch(autoStart = false).use { scenario ->
            assertTrue(Stage.homeScreen)
            key(scenario, KeyEvent.KEYCODE_DPAD_DOWN)
            scenario.onActivity { activity ->
                val hud: Hud = value(activity, "hud")
                assertNotNull("Menu navigation must give a visible control focus", hud.findFocus())
            }
            key(scenario, KeyEvent.KEYCODE_DPAD_CENTER)
            gl { game, _ -> assertTrue("Confirm on start should start the run", value(game, "started")) }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertFalse(Stage.homeScreen)
        }
    }

    @Test fun confirmAdvancesResultsAndOpensBoxesWithoutStartingAnotherRun() {
        launch(autoStart = false).use { scenario ->
            scenario.onActivity { activity ->
                val hud: Hud = value(activity, "hud")
                hud.showRunOver(100, 100, false, 0, 1)
                val flow: RunOverFlow = value(hud, "runOver")
                fun press() {
                    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A))
                    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A))
                }
                assertEquals(Stage.RESULT, Stage.mode)
                press()
                assertFalse("Confirm skips the score count", value(flow, "counting"))
                press()
                assertEquals(Stage.BOX, Stage.mode)
                press()
                assertEquals("Confirm opens the box", 0, value<Int>(flow, "boxesLeft"))
                assertTrue(value(flow, "boxBusy"))
            }
            gl { game, _ -> assertFalse(value(game, "started")) }
        }
    }

    @Test fun boostUsesTheHudButtonAndStopsAtItsLimit() {
        launch().use { scenario ->
            scenario.onActivity { activity ->
                val hud: Hud = value(activity, "hud")
                hud.setBoost(true, 0, 5)
                val button: cube.run.ui.BoostArrows = value(hud, "boost")
                repeat(7) {
                    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_X))
                    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_X))
                }
                assertEquals(5, button.taps)
                hud.setBoost(false, 5, 5)
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B))
                assertNull(value<Any?>(hud, "boost"))
            }
        }
    }
}
