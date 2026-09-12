package cube.run.response

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Settings
import cube.run.game.Player
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses only Player APIs already present in 2.0. Version 1.4 has no platforms. */
class HistoricalJumpProbeTest {
    @Test fun recordJumpAcceptanceAroundPlatformEdgesAndLandings() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val done = CountDownLatch(1); var failure: Throwable? = null
            Gdx.app.postRunnable {
                val sound = Settings.soundEnabled; val haptics = Settings.hapticsEnabled
                try {
                    Settings.setSoundEnabled(false); Settings.setHapticsEnabled(false); Stage.paused = true
                    val game = Gdx.app.applicationListener
                    val p = game.javaClass.getDeclaredField("player").apply { isAccessible = true }.get(game) as Player
                    fun field(name: String) = Player::class.java.getDeclaredField(name).apply { isAccessible = true }
                    fun velocity() = field("vy").getFloat(p)
                    fun reset(height: Float) {
                        p.forceGround(height)
                        field("slamming").setBoolean(p, false)
                        field("duckT").setFloat(p, 0f)
                    }
                    fun step(dt: Float) = p.update(dt, 30f * dt, 10f, 200f, false, 0f, 0f)
                    val rows = arrayListOf("case,hz,delay_ms,jump_accepted,vertical_velocity")
                    for (hz in listOf(60, 90)) for (frames in listOf(0, 1, 3, 5, 7, 10)) {
                        reset(2f); step(1f / hz)
                        repeat(frames) { step(1f / hz) }
                        p.jump()
                        rows.add("edge,$hz,${frames * 1000f / hz},${velocity() > 8f},${velocity()}")
                    }
                    reset(0f); field("py").setFloat(p, .8f); field("air").setBoolean(p, true); field("vy").setFloat(p, -5f)
                    p.jump(); repeat(6) { step(.01f) }
                    rows.add("landing,100,60,${velocity() > 7f},${velocity()}")
                    File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "response-jumps.csv")
                        .writeText(rows.joinToString("\n", postfix = "\n"))
                } catch (t: Throwable) { failure = t }
                finally {
                    Settings.setSoundEnabled(sound); Settings.setHapticsEnabled(haptics)
                    Stage.paused = false; done.countDown()
                }
            }
            assertTrue(done.await(30, TimeUnit.SECONDS)); failure?.let { throw it }
        }
    }
}
