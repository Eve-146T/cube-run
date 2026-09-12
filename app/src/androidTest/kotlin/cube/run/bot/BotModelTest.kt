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
import cube.run.game.track.*
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BotModelTest {
    @Test fun magnetAttractionDoesNotEraseTheBotsCoinLanePreference() {
        Lanes.reset()
        val row = Row(-12f, arrayListOf()).apply {
            coins = arrayListOf(Coin(1.7f, .5f, -2f).apply { x = 0f },
                Coin(1.7f, .5f, -4f).apply { x = 0f })
        }
        val course = Course(-1, "magnet lane", 0, 0, false, 1, BotFixtures.snapshot(listOf(row)))
        val timeline = Timeline(course, 12.4f)
        val plan = Planner(beam = 24, inputEvery = 6, stride = 3).solve(timeline)
        assertTrue(plan.survived)
        assertEquals(Action.RIGHT, plan.actions.first { it != Action.NONE })
        assertTrue(plan.rewards > 0f)
    }

    @Test fun exportAllGeneratedSections() {
        val variants = InstrumentationRegistry.getArguments().getString("variants")?.toInt() ?: 6
        val courses = BotFixtures.sections.flatMap { s -> (0 until variants).map { v ->
            BotFixtures.generate(s, 73 + v, v % 2 == 1, if (variants == 2) 1 else v / 2 % 3, (v % 3) * .73f)
        } }
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "bot-courses.bin")
        DataOutputStream(file.outputStream().buffered()).use { CourseFile.write(it, courses) }
        Log.i("BOT", "exported ${courses.size} courses, ${BotFixtures.sections.size} sections to $file")
    }

    @Test fun modelMatchesActualPlayerTrackAndCollision() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val done = CountDownLatch(1)
            var failure: Throwable? = null
            Gdx.app.postRunnable {
                val oldSound = Settings.soundEnabled; val oldHaptics = Settings.hapticsEnabled
                val oldRevives = Progress.revives
                try {
                    Settings.setSoundEnabled(false); Settings.setHapticsEnabled(false)
                    field(Progress::class.java, "revives").setInt(Progress, 0)
                    Stage.paused = true
                    val game = Gdx.app.applicationListener as CubeRun
                    field(CubeRun::class.java, "started").setBoolean(game, true)
                    val track: Track = value(game, "track")
                    val player: Player = value(game, "player")
                    val bubble: Bubble = value(game, "bubble"); bubble.timer.stop()
                    val ground = CubeRun::class.java.getDeclaredMethod("groundAt", Float::class.javaPrimitiveType).apply { isAccessible = true }
                    val collide = CubeRun::class.java.getDeclaredMethod("collide", Float::class.javaPrimitiveType).apply { isAccessible = true }
                    var checked = 0
                    for (section in BotFixtures.sections) for (speed in listOf(12.4f, 30f, 52.5f)) {
                        val course = BotFixtures.generate(section, 73, false, 1, .73f, repeats = 1)
                        val timeline = Timeline(course, speed, conservative = false)
                        track.rows.clear(); track.rows.addAll(BotFixtures.materialize(course))
                        val model = Body(); BotFixtures.restore(player, model)
                        field(CubeRun::class.java, "dead").setBoolean(game, false)
                        val random = Random(section.id)
                        for (frame in timeline.frames.indices) {
                            val action = if (frame % 8 == 0) random.nextInt(5) else 0
                            when (action) {
                                Action.LEFT -> player.moveToLane(player.lane - 1)
                                Action.RIGHT -> player.moveToLane(player.lane + 1)
                                Action.JUMP -> player.jump()
                                Action.DOWN -> player.downAction()
                            }
                            val gh = ground.invoke(game, player.px) as Float
                            val ev = player.update(timeline.dt, speed * timeline.dt, timeline.frames[frame].time, 200f, false, gh)
                            track.scroll(speed * timeline.dt, timeline.frames[frame].time, timeline.dt)
                            collide.invoke(game, timeline.dt)
                            val realAlive = ev != Player.EV_SIDE_HIT && !value<Boolean>(game, "dead")
                            val modelAlive = timeline.step(model, frame, action)
                            val label = "${section.name} speed=$speed frame=$frame action=$action"
                            assertEquals(label, realAlive, modelAlive)
                            if (!realAlive) break
                            assertEquals("x $label", player.px, model.x, .0001f)
                            assertEquals("y $label", player.py, model.y, .0001f)
                            assertEquals("duck $label", player.duck, model.duck, .0001f)
                            assertEquals("air $label", player.air, model.air)
                            assertEquals("edge grace $label", value<Float>(player, "coyoteLeft"), model.coyoteLeft, .0001f)
                            assertEquals("jump buffer $label", value<Float>(player, "jumpBuffer"), model.jumpBuffer, .0001f)
                            checked++
                        }
                    }
                    assertTrue(checked > 1000)
                    Log.i("BOT", "model parity: $checked real physics/collision frames")
                } catch (t: Throwable) { failure = t } finally {
                    Settings.setSoundEnabled(oldSound); Settings.setHapticsEnabled(oldHaptics)
                    field(Progress::class.java, "revives").setInt(Progress, oldRevives)
                    Stage.paused = false; done.countDown()
                }
            }
            assertTrue(done.await(90, TimeUnit.SECONDS))
            failure?.let { throw it }
        }
    }
}
