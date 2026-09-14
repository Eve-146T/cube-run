package cube.run.game

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.game.track.Pickup
import cube.run.game.track.Row
import cube.run.game.track.Track
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit review capture only: does not run in the normal test suite. */
class RedPillReviewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible=true }
    private fun gl(action: (CubeRun) -> Unit) {
        val done=CountDownLatch(1); var failure: Throwable?=null
        Gdx.app.postRunnable {
            try { action(Gdx.app.applicationListener as CubeRun) } catch(t: Throwable) { failure=t } finally { done.countDown() }
        }
        assertTrue(done.await(10,TimeUnit.SECONDS)); failure?.let { throw it }
    }
    private fun capture(name: String) {
        SystemClock.sleep(180)
        val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"red-pill-review").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().use { bitmap ->
            File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
    private fun <T> Bitmap.use(block: (Bitmap) -> T): T = try { block(this) } finally { recycle() }

    @Test fun captureTransition() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureRedPill")=="true")
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            SystemClock.sleep(900)
            gl { game ->
                Stage.mode=Stage.NONE; Stage.paused=false
                game.onTap(300f,700f)
                field(game,"testCrashObserver").set(game, {})
            }
            SystemClock.sleep(3700)
            gl { game ->
                Stage.paused=true
                val track=field(game,"track").get(game) as Track
                val player=field(game,"player").get(game) as Player
                track.rows.add(Row(-4f, arrayListOf()).apply { pickup=Pickup.RED_PILL; pickupX=player.px; pop=1f; popStart=game.time-2f })
            }
            gl { game ->
                val flash=game.javaClass.superclass.getDeclaredField("flashColor").apply { isAccessible=true }.get(game) as com.badlogic.gdx.graphics.Color
                flash.a=0f
            }
            capture("01-capsule")
            gl { game ->
                val pill=field(game,"redPill").get(game) as RedPill
                pill.collect(); pill.tick(.45f,true); game.tick(0f)
            }
            capture("02-entering")
            gl { game -> (field(game,"redPill").get(game) as RedPill).tick(.45f,true); game.tick(0f) }
            capture("03-matrix")
            gl { game ->
                val pill=field(game,"redPill").get(game) as RedPill
                pill.timer.stop(); pill.tick(.6f,true); game.tick(0f)
            }
            capture("04-leaving")
            gl { game -> (field(game,"redPill").get(game) as RedPill).tick(.6f,true); game.tick(0f) }
            capture("05-restored")
            // Leave a live transition for a screen recording, preserving the normal eased motion.
            gl { game ->
                val pill=field(game,"redPill").get(game) as RedPill
                (field(game,"track").get(game) as Track).rows.forEach { it.pickup=Pickup.NONE }
                pill.collect(); pill.timer.start(3f)
                Stage.paused=false
            }
            SystemClock.sleep(4600)
        }
        Stage.paused=false
    }
}
