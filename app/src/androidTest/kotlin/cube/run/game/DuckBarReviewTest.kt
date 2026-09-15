package cube.run.game

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.game.track.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class DuckBarReviewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun gl(action: (CubeRun) -> Unit) {
        val done = CountDownLatch(1); var failure: Throwable? = null
        Gdx.app.postRunnable {
            try { action(Gdx.app.applicationListener as CubeRun) }
            catch (t: Throwable) { failure = t } finally { done.countDown() }
        }
        assertTrue(done.await(15, TimeUnit.SECONDS)); failure?.let { throw it }
    }

    @Test fun cuesDescribeActionsInsteadOfCurrentHeight() {
        val factory = ObstacleFactory(Random(16))
        assertEquals(ObCue.JUMP, factory.wall(0f).cue)
        assertEquals(ObCue.JUMP, factory.wallSeg(0, 0, 0f).cue)
        assertEquals(ObCue.DUCK, factory.over(0f).cue)
        assertEquals(ObCue.DUCK, factory.overSeg(1, 2, 0f).cue)
        assertEquals(ObCue.DUCK, factory.pendulum(0f, 0f).cue)
        assertEquals(ObCue.NONE, factory.stomper(0, 0f, 0f).cue)
        assertEquals(ObCue.NONE, factory.pillar(0f, 0f).cue)
        val tall = arrayListOf<Ob>(); factory.tallWall(0f, tall)
        assertTrue(tall.all { it.cue == ObCue.NONE })
        val bar = factory.overSeg(1, 2, 0f)
        val bottom = bar.bottom
        Row(-5f, arrayListOf(bar)).alignLaneSpacing(2.5f)
        assertEquals(ObCue.DUCK, bar.cue)
        assertEquals(bottom, bar.bottom, .00001f)
    }

    /** Opt-in native captures. All candidates share each frozen scene and camera. */
    @Test fun captureCandidates() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureDuckBars") == "true")
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            SystemClock.sleep(1200)
            gl { game ->
                Stage.mode = Stage.NONE; Stage.paused = false
                game.onTap(300f, 700f)
                field(game, "testCrashObserver").set(game, {})
                val track = field(game, "track").get(game) as Track
                track.rows.clear(); field(track, "spawnAcc").setFloat(track, -10000f)
                // Let the launch gate and particles pass even on a slow software emulator.
                repeat(360) { game.tick(1f / 60f) }
                Stage.paused = true
            }
            SystemClock.sleep(900) // Let the native HUD finish its launch animation too.
            try {
                for (scene in listOf("duck", "jump", "hills", "moving")) {
                    gl { game ->
                        Terrain.reset()
                        if (scene == "hills") { Terrain.set(true); Terrain.scroll(0f, 1f) }
                        val track = field(game, "track").get(game) as Track
                        track.rows.clear()
                        val factory = ObstacleFactory(Random(16))
                        val worlds = field(game, "worlds").get(game) as cube.run.game.world.WorldRunner
                        val hue = worlds.hue
                        fun row(z: Float, vararg obs: Ob) {
                            track.rows.add(Row(z, ArrayList(obs.toList())).apply { pop = 1f })
                        }
                        when (scene) {
                            "jump" -> { row(-5f, factory.wall(hue)); row(-14f, factory.over(hue)) }
                            "moving" -> {
                                row(-5f, factory.pendulum(hue, 0f).apply { x = -.8f })
                                row(-13f, factory.overSeg(1, 2, hue), factory.wallSeg(0, 0, hue))
                            }
                            else -> { row(-5f, factory.over(hue)); row(-14f, factory.wall(hue)) }
                        }
                        val tall = arrayListOf<Ob>(); factory.tallWall(hue, tall)
                        track.rows.add(Row(-25f, tall).apply { pop = 1f })
                        row(-22f, factory.pad(1, hue))
                        row(-37f, factory.overSeg(0, 1, hue), factory.pillar(1.7f, hue))
                        game.tick(0f)
                        val flash = game.javaClass.superclass.getDeclaredField("flashColor").apply { isAccessible = true }.get(game) as com.badlogic.gdx.graphics.Color
                        flash.a = 0f
                    }
                    for (style in ObstacleCueStyle.entries) {
                        gl { game -> (field(game, "trackArt").get(game) as TrackRenderer).cueStyle = style }
                        SystemClock.sleep(240)
                        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "duck-review").apply { mkdirs() }
                        val bitmap = instrumentation.uiAutomation.takeScreenshot()
                        try {
                            File(dir, "${style.ordinal}-$scene.png").outputStream().use {
                                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                            }
                        } finally { bitmap.recycle() }
                    }
                }
            } finally {
                gl { game ->
                    (field(game, "trackArt").get(game) as TrackRenderer).cueStyle = ObstacleCueStyle.DUCK_ARROWS
                    Terrain.reset(); Stage.paused = false
                }
            }
        }
    }
}
