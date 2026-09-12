package cube.run.bot

import android.os.SystemClock
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import cube.run.core.gfx.TouchInput
import cube.run.core.gfx.TouchListener
import cube.run.game.*
import cube.run.game.track.Track
import java.io.BufferedWriter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import org.junit.Assert.assertTrue

/** Shared real-input controller for collision-checked demos and protected performance runs. */
internal class LiveBotDriver : AutoCloseable {
    private val sentAt = AtomicLong()
    private val delivery = AtomicLong(24)
    private val pending = AtomicInteger()
    private val acknowledged = AtomicInteger()
    private val ignoredJumps = AtomicInteger()
    private var previousInput: InputProcessor? = null
    var decisions = 0; private set
    var gestures = 0; private set
    var alive = true; private set
    private var startedAt = 0L

    init {
        gl { game ->
            previousInput = Gdx.input.inputProcessor
            Gdx.input.inputProcessor = TouchInput(object : TouchListener {
                override fun onDown(x: Float, y: Float) = game.onDown(x, y)
                override fun onDrag(x: Float, y: Float, dx: Float, dy: Float) = game.onDrag(x, y, dx, dy)
                override fun onUp(x: Float, y: Float) = game.onUp(x, y)
                override fun onTap(x: Float, y: Float) = game.onTap(x, y)
                override fun onSwipe(dir: Int) {
                    if (pending.get() == dir + 1) {
                        val latency = SystemClock.uptimeMillis() - sentAt.get()
                        delivery.set((delivery.get() * 3 + latency) / 4)
                        if (dir == TouchInput.UP && value<Player>(game, "player").air) ignoredJumps.incrementAndGet()
                        acknowledged.incrementAndGet(); pending.set(0)
                    }
                    game.onSwipe(dir)
                }
            }, { Gdx.graphics.width }, { game.session.isOver })
        }
    }

    private data class Snapshot(val course: Course, val body: Body, val speed: Float, val alive: Boolean,
        val score: Int, val coins: Int, val takenAt: Long, val timeScale: Float)

    /** Runs on the instrumentation thread, never on the GL thread. */
    fun drive(keepRunning: () -> Boolean, writer: BufferedWriter? = null) {
        startedAt = SystemClock.uptimeMillis()
        var lastAction = 0L
        var planningEstimate = 35.0
        writer?.appendLine("elapsed_ms,speed,x,y,action,solved_horizon,planning_ms,score,coins,delivery_ms,ignored_jumps,time_scale,nearby")
        while (keepRunning()) {
            val snap = gl { game ->
                val p: Player = value(game, "player")
                Snapshot(Course(-100, "live", 0, 0, false, p.lane,
                    BotFixtures.snapshot(value<Track>(game, "track").rows), Lanes.count, Lanes.w, game.time),
                    BotFixtures.body(p).apply { if (p.flying) flightLeft = value<PowerUps>(game, "powerUps").jet.left },
                    value<Float>(game, "spd"), !value<Boolean>(game, "dead") && !game.session.isOver,
                    game.session.score, value(game, "coinsRun"), SystemClock.uptimeMillis(), game.timeScale)
            }
            if (!snap.alive) { alive = false; break }
            val before = System.nanoTime()
            val scale = snap.timeScale.coerceIn(.1f, 1f)
            val timeline = Timeline(snap.course, snap.speed.coerceAtLeast(4.5f), seconds = 1.5f * scale, safetyMargin = .025f)
            val hold = ceil((planningEstimate + delivery.get() + 5) * scale / (timeline.dt * 1000)).toInt().coerceIn(1, 18)
            val searched = Planner(24, 6, stride = 3, gestureCost = .06f, slamCost = .3f, reversalCost = .3f)
                .solve(timeline, snap.body, holdFrames = hold)
            val plan = centerFirst(timeline, searched, snap.body, hold, 6)
            val planningMs = (System.nanoTime() - before) / 1e6
            planningEstimate = planningEstimate * .7 + planningMs * .3
            val until = snap.takenAt + 200
            val performed = if (writer != null) mutableListOf<String>() else null
            for (frame in plan.actions.indices) {
                val action = plan.actions[frame]
                if (action == 0) continue
                val due = maxOf(lastAction + 100, snap.takenAt + (frame * timeline.dt * 1000 / scale).toLong() - delivery.get())
                if (due > until || !keepRunning()) break
                val current = SystemClock.uptimeMillis()
                if (current - due > 50 || pending.get() != 0) break
                if (due > current) SystemClock.sleep(due - current)
                lastAction = SystemClock.uptimeMillis(); sentAt.set(lastAction); pending.set(action)
                assertTrue(AndroidGestures.flick(action, Gdx.graphics.width, Gdx.graphics.height, sync = false))
                performed?.add(Action.names[action]); gestures++
            }
            if (writer != null) {
                val nearby = snap.course.rows.filter { it.z in -8f..1f }.joinToString(";") { r ->
                    "z=${r.z}:" + r.obstacles.joinToString("|") { "${it.type}@${it.x}/${it.cy}/${it.sy}" }
                }
                writer.appendLine("${SystemClock.uptimeMillis() - startedAt},${snap.speed},${snap.body.x},${snap.body.y},${performed!!.joinToString("+").ifEmpty { "wait" }},${plan.survived},$planningMs,${snap.score},${snap.coins},${delivery.get()},${ignoredJumps.get()},${snap.timeScale},$nearby")
                writer.flush()
            }
            decisions++
            SystemClock.sleep((until - SystemClock.uptimeMillis()).coerceIn(15, 300))
        }
    }

    fun summary(): String = gl { game ->
        alive = alive && !value<Boolean>(game, "dead") && !game.session.isOver
        "alive=$alive seconds=${(SystemClock.uptimeMillis() - startedAt) / 1000f} decisions=$decisions gestures=$gestures acknowledged=${acknowledged.get()} ignoredJumps=${ignoredJumps.get()} score=${game.session.score} coins=${value<Int>(game, "coinsRun")}" }

    override fun close() { gl { Gdx.input.inputProcessor = previousInput } }

    companion object {
        fun <T> gl(action: (CubeRun) -> T): T {
            val done = CountDownLatch(1)
            var result: T? = null; var failure: Throwable? = null
            Gdx.app.postRunnable {
                try { result = action(Gdx.app.applicationListener as CubeRun) }
                catch (t: Throwable) { failure = t } finally { done.countDown() }
            }
            assertTrue("GL bot snapshot timed out", done.await(10, TimeUnit.SECONDS))
            failure?.let { throw it }
            @Suppress("UNCHECKED_CAST") return result as T
        }
    }
}
