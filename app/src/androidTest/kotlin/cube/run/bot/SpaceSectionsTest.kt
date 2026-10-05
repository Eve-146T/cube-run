package cube.run.bot

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Bonus
import cube.run.data.Progress
import cube.run.data.Settings
import cube.run.game.*
import cube.run.game.space.SpaceSections
import cube.run.game.track.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Outer Space's sections under low gravity: every one is survivable at every
 * speed the run reaches, by a player who acts at most every 8 frames; the
 * bot's low-gravity model matches the real cube frame for frame; and the
 * stretch lasts its 69 rows, then the road returns.
 */
@RunWith(AndroidJUnit4::class)
class SpaceSectionsTest {
    private var previousSection = -1

    @Before fun setup() { previousSection = Settings.testSection; Lanes.reset() }
    @After fun restore() { Settings.testSection = previousSection; Lanes.reset() }

    private val sections get() = SpaceSections.pool + SpaceSections.intro

    /** The real decoder in space at [speed]: the low-gravity spacing depends on it. */
    private fun generate(section: Sect, seed: Int, mirror: Boolean, entry: Int, speed: Float, repeats: Int = 2): Course {
        val saved = Settings.testSection
        Settings.testSection = 0 // the bare-section policy, without pickups
        try {
            Lanes.reset()
            val rng = Random(seed)
            val track = Track(rng, ObstacleFactory(rng))
            track.forceBonus(Bonus.SPACE)
            track.speed = speed
            field(Track::class.java, "curSafe").setInt(track, entry)
            field(Track::class.java, "mirror").setBoolean(track, mirror && section.mirrorable)
            val spawn = Track::class.java.getDeclaredMethod("spawnStep", Int::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).apply { isAccessible = true }
            val gap = Track::class.java.getDeclaredMethod("gapFor", Int::class.javaPrimitiveType).apply { isAccessible = true }
            var z = -16f
            repeat(repeats) {
                for (code in section.steps) {
                    z -= gap.invoke(track, code) as Float
                    spawn.invoke(track, code, z, 200f)
                }
            }
            return Course(section.id, section.name, section.tier, seed, mirror, entry, BotFixtures.snapshot(track.rows), phase = seed * .37f)
        } finally { Settings.testSection = saved; Lanes.reset() }
    }

    /** Every speed the run reaches, from the start of a run to the ceiling. */
    private val speeds = listOf(12.4f, 18f, 24f, 30f)

    private fun variants(section: Sect, speed: Float) = (0 until 6).map { v ->
        generate(section, 73 + v, v % 2 == 1, v / 2 % 3, speed).copy(lowG = true)
    }

    /**
     * A quick on-device pass: each section once per speed, by a player who acts
     * at most every 8 frames. The full grid (every mirror and entry lane, three
     * player profiles) runs on the host from [exportSpaceCourses]:
     * `./gradlew :bot:run --args="space-courses-18.bin out 18"`.
     */
    @Test fun everySpaceSectionIsSurvivableOnLowGravity() {
        val failures = ArrayList<String>()
        var trials = 0
        for (section in sections) for (speed in speeds) {
            val course = variants(section, speed)[trials % 6]
            val survival = course.copy(rows = course.rows.map { it.copy(goodies = emptyList()) })
            val timeline = Timeline(survival, speed)
            var plan = Planner(64, 8).solve(timeline)
            if (!plan.survived) plan = Planner(192, 8).solve(timeline)
            trials++
            if (!plan.survived) failures.add("${section.name} speed=$speed mirror=${course.mirror} entry=${course.entry} reached ${plan.reachedFrame}/${timeline.frames.size}")
            else assertTrue("${section.name}: plan must replay literally", replay(Timeline(course, speed, conservative = false), plan.actions))
        }
        Log.i("SPACE", "survivability: ${trials - failures.size}/$trials")
        assertTrue("Unsurvivable space sections:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** The full grid for the host survey: one file per speed (low-gravity spacing depends on the speed). */
    @Test fun exportSpaceCourses() {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        for (speed in speeds) {
            val courses = sections.flatMap { variants(it, speed) }
            val file = java.io.File(dir, "space-courses-${speed.toInt()}.bin")
            java.io.DataOutputStream(file.outputStream().buffered()).use { CourseFile.write(it, courses) }
            Log.i("SPACE", "exported ${courses.size} courses at $speed to $file")
        }
    }

    @Test fun lowGravityModelMatchesTheRealCube() {
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
                    // jumps and landings burst sparks: the renderer must be past the launch opening
                    cube.run.core.Gdx3DGame::class.java.getDeclaredMethod("finishRendererStartup").apply { isAccessible = true }.invoke(game)
                    field(CubeRun::class.java, "started").setBoolean(game, true)
                    val track: Track = value(game, "track")
                    val player: Player = value(game, "player")
                    val bubble: Bubble = value(game, "bubble"); bubble.timer.stop()
                    val ground = CubeRun::class.java.getDeclaredMethod("groundAt", Float::class.javaPrimitiveType).apply { isAccessible = true }
                    val collide = CubeRun::class.java.getDeclaredMethod("collide", Float::class.javaPrimitiveType).apply { isAccessible = true }
                    var checked = 0
                    for (section in sections) for (speed in listOf(12.4f, 30f)) {
                        val course = generate(section, 73, false, 1, speed, repeats = 1)
                        val timeline = Timeline(course, speed, conservative = false)
                        track.rows.clear(); track.rows.addAll(BotFixtures.materialize(course))
                        val model = Body(lowG = true); BotFixtures.restore(player, model)
                        player.lowGravity = true
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
                            assertEquals("air $label", player.air, model.air)
                            checked++
                        }
                    }
                    assertTrue(checked > 500)
                    Log.i("SPACE", "low-gravity parity: $checked frames")
                } catch (t: Throwable) { failure = t } finally {
                    player(Gdx.app.applicationListener as CubeRun).lowGravity = false
                    Settings.setSoundEnabled(oldSound); Settings.setHapticsEnabled(oldHaptics)
                    field(Progress::class.java, "revives").setInt(Progress, oldRevives)
                    Stage.paused = false; done.countDown()
                }
            }
            assertTrue(done.await(120, TimeUnit.SECONDS))
            failure?.let { throw it }
        }
    }

    private fun player(game: CubeRun): Player = value(game, "player")

    @Test fun theSpaceStretchLastsHalfAgainTheLongestOtherWorld() {
        val longestOther = Bonus.all.filter { it.id != Bonus.SPACE }.maxOf { it.rows }
        assertEquals(longestOther * 3 / 2, Bonus.get(Bonus.SPACE).rows)
        Settings.testSection = -1
        for (seed in 1..6) {
            Lanes.reset()
            val rng = Random(seed)
            val track = Track(rng, ObstacleFactory(rng)).apply { portalPool = listOf(Bonus.SPACE); portalEvery = 0 }
            track.reset(0.2f, 120f)
            val seen = ArrayList<Row>()
            var guard = 0
            while (seen.none { it.portalExit } && guard++ < 20000) {
                track.spawn(1f, 120f, 900, dt = 1f / 20f) // 20 units/s
                for (r in track.rows) if (r !in seen) seen.add(r)
                for (r in track.rows) r.z += 1f
                track.rows.removeAll { it.z > 12f }
            }
            val entry = seen.indexOfFirst { it.portal == Bonus.SPACE && !it.portalExit }
            val exit = seen.indexOfFirst { it.portalExit }
            assertTrue("seed $seed: no space portal", entry >= 0 && exit > entry)
            val inside = seen.subList(entry + 1, exit)
            assertTrue("every row inside is drawn in space", inside.all { it.spaceLook })
            assertTrue("no row outside is", seen.subList(0, entry + 1).none { it.spaceLook } )
            val counted = inside.count { !it.scoreless }
            // Sections are never cut short, so the exit waits for the current one to finish.
            assertTrue("seed $seed: $counted rows", counted in 69 until 69 + 9)
            Log.i("SPACE", "seed $seed: ${inside.size} rows inside, $counted counted")
        }
    }
}
