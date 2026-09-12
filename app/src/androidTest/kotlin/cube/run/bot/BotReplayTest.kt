package cube.run.bot

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Settings
import cube.run.game.*
import cube.run.game.track.Track
import java.io.DataInputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BotReplayTest {
    @Test fun replaySurveyWitnessesThroughRealPhysicsAndCollisions() {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        val courses = DataInputStream(File(dir, "bot-courses.bin").inputStream().buffered()).use(CourseFile::read)
        val archive = ZipFile(File(dir, "bot-witnesses.zip"))
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val done = CountDownLatch(1); var failure: Throwable? = null
            Gdx.app.postRunnable {
                val oldSound = Settings.soundEnabled; val oldHaptics = Settings.hapticsEnabled; val oldRevives = Progress.revives
                try {
                    Settings.setSoundEnabled(false); Settings.setHapticsEnabled(false)
                    field(Progress::class.java, "revives").setInt(Progress, 0)
                    Stage.paused = true
                    val game = Gdx.app.applicationListener as CubeRun
                    field(CubeRun::class.java, "started").setBoolean(game, true)
                    field(CubeRun::class.java, "jetGrace").setFloat(game, 0f)
                    val track: Track = value(game, "track"); val player: Player = value(game, "player")
                    value<Bubble>(game, "bubble").timer.stop()
                    val ground = CubeRun::class.java.getDeclaredMethod("groundAt", Float::class.javaPrimitiveType).apply { isAccessible = true }
                    val collide = CubeRun::class.java.getDeclaredMethod("collide", Float::class.javaPrimitiveType).apply { isAccessible = true }
                    var checked = 0; var failed = 0; var frames = 0
                    File(dir, "bot-witness-results.csv").bufferedWriter().use { out ->
                        out.appendLine("witness,passed,frame")
                        val entries = archive.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement(); val parts = entry.name.removeSuffix(".actions").split('_')
                            val course = courses.first { it.id == parts[0].toInt() && it.seed == parts[1].toInt() && it.mirror == parts[2].toBoolean() && it.entry == parts[3].toInt() && it.phase == parts[4].toFloat() }
                            val speed = parts[5].toFloat()
                            val actions = archive.getInputStream(entry).bufferedReader().use { it.readText().split(',').map(String::toInt) }
                            Lanes.reset()
                            track.rows.clear(); track.rows.addAll(BotFixtures.materialize(course))
                            BotFixtures.restore(player, Body(lane = course.entry, x = (course.entry - 1) * course.width))
                            field(CubeRun::class.java, "dead").setBoolean(game, false)
                            var alive = true; var frame = 0
                            for (a in actions) {
                                if (a != 0) game.onSwipe(a - 1)
                                val gh = ground.invoke(game, player.px) as Float
                                val dt = 1f / 60f; val time = course.phase + (frame + 1) * dt
                                val ev = player.update(dt, speed * dt, time, 200f, false, gh)
                                track.scroll(speed * dt, time, dt)
                                collide.invoke(game, dt)
                                frame++; frames++
                                if (ev == Player.EV_SIDE_HIT || value<Boolean>(game, "dead")) { alive = false; failed++; break }
                            }
                            checked++; out.appendLine("${entry.name},$alive,$frame")
                        }
                    }
                    Log.i("BOT_REPLAY", "witnesses=$checked failed=$failed actual_frames=$frames")
                    assertTrue("No witnesses supplied", checked >= BotFixtures.sections.size)
                    assertEquals("Some solver witnesses failed real game physics", 0, failed)
                } catch (t: Throwable) { failure = t } finally {
                    archive.close(); Settings.setSoundEnabled(oldSound); Settings.setHapticsEnabled(oldHaptics)
                    field(Progress::class.java, "revives").setInt(Progress, oldRevives)
                    Stage.paused = false; done.countDown()
                }
            }
            assertTrue("Witness replay timed out", done.await(180, TimeUnit.SECONDS))
            failure?.let { throw it }
        }
    }
}
