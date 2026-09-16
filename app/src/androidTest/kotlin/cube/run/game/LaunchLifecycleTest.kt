package cube.run.game

import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import cube.run.GameActivity
import cube.run.bot.LiveBotDriver.Companion.gl
import cube.run.core.Stage
import cube.run.ui.Hud
import org.junit.Assert.*
import org.junit.Test

class LaunchLifecycleTest {
    private fun hud(activity: GameActivity): View? = GameActivity::class.java
        .getDeclaredField("hud").apply { isAccessible = true }.get(activity) as View?

    private fun ready(scenario: ActivityScenario<GameActivity>, settled: Boolean = false) {
        val until = SystemClock.uptimeMillis() + 5000
        var attached = false
        while (!attached && SystemClock.uptimeMillis() < until) {
            scenario.onActivity {
                val view = hud(it)
                attached = view?.isAttachedToWindow == true && (!settled || view.alpha == 1f && view.translationY == 0f)
            }
            if (!attached) SystemClock.sleep(20)
        }
        assertTrue("The first cube frame must be followed by an attached menu", attached)
    }

    @Test fun deferredMenuSettlesAndSurvivesBackgroundAndRecreation() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            ready(scenario)
            gl { it.finishOpening() }
            ready(scenario, settled = true)
            scenario.onActivity {
                assertEquals(1f, hud(it)!!.alpha, .001f)
                assertEquals(0f, hud(it)!!.translationY, .001f)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            SystemClock.sleep(200)
            scenario.moveToState(Lifecycle.State.RESUMED)
            ready(scenario)
            scenario.onActivity {
                assertEquals(1f, hud(it)!!.alpha, .001f)
                assertEquals(0f, hud(it)!!.translationY, .001f)
            }
            scenario.recreate()
            ready(scenario)
            scenario.onActivity {
                assertEquals(1f, hud(it)!!.alpha, .001f)
                assertEquals(0f, hud(it)!!.translationY, .001f)
            }
            assertEquals(Stage.NONE, Stage.mode)
        }
    }

    @Test fun closingDuringLaunchDoesNotAttachAnOldMenuToTheNextActivity() {
        repeat(3) {
            ActivityScenario.launch(GameActivity::class.java).close()
        }
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            ready(scenario)
            gl { it.finishOpening() }
            ready(scenario, settled = true)
            scenario.onActivity { assertEquals(1f, hud(it)!!.alpha, .001f) }
        }
    }

    @Test fun tappingTheStartingCubeSkipsTheIntroWithoutStartingARun() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<FrameLayout>(android.R.id.content)
                val root = content.getChildAt(0) as FrameLayout
                assertTrue("The opening touch cover must accept an early tap", root.getChildAt(root.childCount-1).performClick())
            }
            ready(scenario, settled = true)
            gl { game ->
                val started = CubeRun::class.java.getDeclaredField("started").apply { isAccessible = true }.getBoolean(game)
                assertFalse("Skipping the intro must leave the menu ready", started)
            }
            scenario.onActivity { activity ->
                val root = activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as FrameLayout
                assertEquals("The opening cover must be removed", 2, root.childCount)
            }
        }
    }

    @Test fun restartStillStartsTheRunWithoutAnOpeningCover() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), GameActivity::class.java)
            .putExtra(Hud.EXTRA_AUTOSTART, true)
        ActivityScenario.launch<GameActivity>(intent).use { scenario ->
            ready(scenario, settled = true)
            val until = SystemClock.uptimeMillis() + 5000
            var started = false
            while (!started && SystemClock.uptimeMillis() < until) {
                started = gl { game -> CubeRun::class.java.getDeclaredField("started").apply { isAccessible = true }.getBoolean(game) }
                if (!started) SystemClock.sleep(20)
            }
            assertTrue("Restart must automatically begin the run", started)
            scenario.onActivity { activity ->
                val root = activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as FrameLayout
                assertEquals(2, root.childCount)
            }
        }
    }
}
