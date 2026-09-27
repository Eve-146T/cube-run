package cube.run.ui

import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.Gdx3DGame
import cube.run.data.Progress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Measures actual menu readiness, including the results activity handoff and fresh GL frames. */
class MenuReturnTimingTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun ui(action: () -> Unit) = instrumentation.runOnMainSync(action)
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner: Any, name: String): T =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner) as T
    private fun launch(): GameActivity = instrumentation.startActivitySync(
        Intent(instrumentation.targetContext, GameActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(Hud.EXTRA_AUTOSTART, false)
    ) as GameActivity
    private fun awaitMenu(activity: GameActivity, timeout: Long = 5000) {
        val deadline = SystemClock.uptimeMillis() + timeout
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            ui {
                val menu = field<MainMenu>(field<Hud>(activity, "hud"), "menu")
                ready = menu.isShown && listOf("top", "middle", "rightChips", "bank").all { name ->
                    val view = field<View>(menu, name)
                    view.alpha >= .99f && kotlin.math.abs(view.translationY) < .5f
                }
            }
            if (!ready) SystemClock.sleep(10)
        }
        assertTrue("Menu controls became visible and settled", ready)
    }
    private fun awaitScene() {
        val drawn = CountDownLatch(1)
        (Gdx.app.applicationListener as Gdx3DGame).afterFreshSceneFrame { drawn.countDown() }
        assertTrue("Fresh game scene rendered", drawn.await(5, TimeUnit.SECONDS))
    }

    @Test fun resultsReturnMakesMenuAndSceneReadyWithoutLaunchStagger() {
        var activity = launch()
        try {
            awaitMenu(activity); awaitScene()
            repeat(3) { cycle ->
                ui { field<Hud>(activity, "hud").showRunOver(0, 100, false, 0, 0) }
                SystemClock.sleep(350)
                val monitor = instrumentation.addMonitor(GameActivity::class.java.name, null, false)
                val startedAt = SystemClock.uptimeMillis()
                try {
                    ui {
                        val flow = field<RunOverFlow>(field<Hud>(activity, "hud"), "runOver")
                        field<View>(flow, "page").performClick()
                        field<View>(flow, "page").performClick()
                    }
                    activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as? GameActivity
                        ?: error("Results did not return to the game activity")
                    awaitMenu(activity); awaitScene()
                    val elapsed = SystemClock.uptimeMillis() - startedAt
                    android.util.Log.i("MenuReturn", "Results cycle $cycle: menu and GL scene ready in $elapsed ms")
                    assertTrue("Results return took $elapsed ms", elapsed < 1000)
                } finally { instrumentation.removeMonitor(monitor) }
            }
        } finally { ui { activity.finish() } }
    }

    @Test fun achievementsBackRestoresMenuControlsQuickly() {
        val activity = launch()
        val unlocked = Progress.achievementsUnlocked
        val unlockField = Progress::class.java.getDeclaredField("achievementsUnlocked").apply { isAccessible = true }
        try {
            awaitMenu(activity); awaitScene()
            repeat(3) { cycle ->
                ui {
                    unlockField.setBoolean(null, true)
                    val hud = field<Hud>(activity, "hud")
                    hud.javaClass.getDeclaredMethod("openAchievements").apply { isAccessible = true }.invoke(hud)
                }
                SystemClock.sleep(300)
                val startedAt = SystemClock.uptimeMillis()
                ui { field<Page>(field<Hud>(activity, "hud"), "page").navigateBack() }
                awaitMenu(activity)
                val elapsed = SystemClock.uptimeMillis() - startedAt
                android.util.Log.i("MenuReturn", "Achievements cycle $cycle: controls ready in $elapsed ms")
                assertTrue("Achievements Back took $elapsed ms", elapsed < 350)
                ui { assertNull(field<Page?>(field<Hud>(activity, "hud"), "page")) }
            }
        } finally { ui { unlockField.setBoolean(null, unlocked); activity.finish() } }
    }
}
