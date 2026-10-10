package cube.run.ui

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage.RESUMED
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.game.CubeRun
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Time the real pause RESTART action through a complete submitted game frame. */
class PauseRestartTimingTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private fun ui(action: () -> Unit) = inst.runOnMainSync(action)
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner: Any, name: String): T {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true }.get(owner) as T }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        error("Missing $name")
    }

    private fun liveActivity(): GameActivity {
        val deadline = SystemClock.uptimeMillis() + 8000
        while (SystemClock.uptimeMillis() < deadline) {
            var activity: GameActivity? = null
            ui {
                activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(RESUMED)
                    .filterIsInstance<GameActivity>().firstOrNull { current ->
                        val hud = runCatching { field<Hud>(current, "hud") }.getOrNull()
                        hud != null && field<Boolean>(hud, "runStarted") && field<PauseSheet?>(hud, "pauseSheet") == null
                    }
            }
            activity?.let { return it }
            SystemClock.sleep(5)
        }
        error("Restart did not reveal a live run")
    }

    @Test fun pauseRestartLatency() {
        var activity = inst.startActivitySync(Intent(inst.targetContext, GameActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(Hud.EXTRA_AUTOSTART, true)) as GameActivity
        try {
            activity = liveActivity()
            repeat(4) { cycle ->
                val oldGame = Gdx.app.applicationListener as CubeRun
                val surface = field<View>(activity, "gameSurface")
                val owned = field<List<Any>>(oldGame, "owned").size
                val primed = CountDownLatch(1)
                Gdx.app.postRunnable {
                    oldGame.session.setScore(37)
                    oldGame.session.setCoins(7)
                    val powers = field<cube.run.game.PowerUps>(oldGame, "powerUps")
                    powers.jet.start(20f); powers.magnet.start(20f); powers.mult.start(20f)
                    field<cube.run.game.Player>(oldGame, "player").setFlying(true)
                    oldGame.slowMo(.1f, 20f)
                    primed.countDown()
                }
                assertTrue(primed.await(2, TimeUnit.SECONDS))
                ui { field<Hud>(activity, "hud").pause(animate = false) }
                val started = SystemClock.uptimeMillis()
                ui {
                    val hud = field<Hud>(activity, "hud")
                    val restart = field<View>(field<PauseSheet>(hud, "pauseSheet"), "restart")
                    restart.performClick(); restart.performClick()
                    assertTrue("Ignore physical input until the new scene is ready",
                        hud.handlePhysicalAction(cube.run.core.PhysicalAction.PAUSE))
                }
                activity = liveActivity()
                val game = Gdx.app.applicationListener as CubeRun
                val drawn = CountDownLatch(1)
                game.afterFreshSceneFrame { drawn.countDown() }
                assertTrue("Restarted scene submitted", drawn.await(5, TimeUnit.SECONDS))
                val elapsed = SystemClock.uptimeMillis() - started
                Log.i("PauseRestart", "cycle=$cycle readyMs=$elapsed reusedGame=${game === oldGame}")
                assertTrue("Pause restart took $elapsed ms", elapsed < 350)
                assertSame("Keep renderer resources", oldGame, game)
                assertSame("Keep the GL surface", surface, field<View>(activity, "gameSurface"))
                assertEquals(owned, field<List<Any>>(game, "owned").size)
                val powers = field<cube.run.game.PowerUps>(game, "powerUps")
                assertFalse(powers.jet.active || powers.magnet.active || powers.mult.active)
                assertFalse(field<cube.run.game.Player>(game, "player").flying)
                assertEquals(1f, game.timeScale, .001f)
                ui {
                    val hud = field<Hud>(activity, "hud")
                    assertNull(field<View?>(hud, "restartBlocker"))
                    assertEquals("0", field<android.widget.TextView>(hud, "scoreText").text.toString())
                    val pause = field<View>(hud, "pauseChip")
                    assertTrue(pause.isShown)
                    assertEquals(1f, pause.alpha, .001f)
                    val boost = field<BoostArrows?>(hud, "boost")
                    if (cube.run.data.Settings.effectiveStartSpeed < cube.run.data.Progress.maxStartPresses) {
                        assertNotNull("The new run keeps its available boost control", boost)
                        assertTrue(boost!!.isShown)
                        assertEquals(1f, boost.alpha, .001f)
                        assertEquals(cube.run.data.Settings.effectiveStartSpeed, boost.taps)
                    } else assertNull("A fully applied start preset has no remaining boosts", boost)
                }
                assertFalse(cube.run.core.Stage.paused)
                assertFalse(game.session.isOver)
                assertEquals(0, game.session.score)
            }
        } finally { ui { activity.finish() } }
    }
}
