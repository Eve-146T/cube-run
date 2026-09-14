package cube.run.game

import cube.run.bot.*
import cube.run.core.Stage
import cube.run.data.Settings
import cube.run.game.track.Pickup
import cube.run.game.track.Row
import cube.run.game.track.Track
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.ceil

/** The dev-mode attract run. Planning works from copies on a worker, never blocks a frame. */
internal class IdlePilot : AutoCloseable {
    private var idleSeconds = 0f
    private var input = Stage.interactions.get()
    private var running = false
    val active get() = running && Stage.botOwner === this && Settings.devMode
    private var boosts = 0
    private var nextBoost = 0f
    private var nextPlan = 0f
    private var lastAction = -10f
    private var planningSeconds = .06f
    private var worker: java.util.concurrent.ExecutorService? = null
    private data class Decision(val at: Float, val step: Float, val plan: Plan, val seconds: Float)
    private var pending: Future<Decision>? = null
    private var decision: Decision? = null
    private var cursor = 0
    private var lanes = 0
    private var flying = false
    private var hovering = false

    fun ready(dt: Float, home: Boolean): Boolean {
        val touched = Stage.interactions.get()
        if (touched != input) { input = touched; stop() }
        if (!Settings.devMode || Stage.paused || Stage.pointerDown) { stop(); return false }
        if (!home || active) { idleSeconds = 0f; return false }
        idleSeconds += dt
        return idleSeconds >= 120f
    }

    fun start(now: Float) {
        stop()
        running = true; Stage.botOwner = this
        input = Stage.interactions.get()
        boosts = 0; nextBoost = now + .45f; nextPlan = now
    }

    fun drive(now: Float, speed: Float, scale: Float, track: Track, body: Body, swipe: (Int) -> Unit) {
        if (!active || input != Stage.interactions.get()) { stop(); return }
        if (lanes != Lanes.count || flying != body.flying || hovering != body.hover) {
            replan()
            lanes = Lanes.count; flying = body.flying; hovering = body.hover
        }
        if (boosts < 5 && now >= nextBoost) {
            Stage.boostRequests.incrementAndGet()
            boosts++; nextBoost = now + .35f
        }
        pending?.takeIf { it.isDone }?.let { result ->
            pending = null
            val next = result.get()
            planningSeconds = planningSeconds * .7f + next.seconds * .3f
            nextPlan = now + .12f
            if (now - next.at < .5f) {
                decision = next; cursor = 0
                // Commit the next gesture before replanning. Replacing a future slam on
                // every frame can postpone it forever and miss the following springboard.
                val first = next.plan.actions.indices.firstOrNull {
                    next.plan.actions[it] != Action.NONE && next.at + it * next.step >= now - .075f
                }
                if (first != null) nextPlan = next.at + first * next.step + .035f
            }
        }
        decision?.let { d ->
            while (cursor < d.plan.actions.size && d.at + cursor * d.step <= now) {
                val due = d.at + cursor * d.step
                val action = d.plan.actions[cursor++]
                if (action != Action.NONE && now - due < .075f && now - lastAction >= .095f) {
                    swipe(action - 1); lastAction = now
                }
            }
        }
        if (pending != null || now < nextPlan) return
        // The planner's held frames assume coasting while this snapshot is being solved.
        // Do not execute an older plan and then replace it with a conflicting prediction.
        decision = null
        nextPlan = Float.POSITIVE_INFINITY
        val horizon = 1.5f
        val course = Course(-1, "idle bot", 0, 0, false, body.lane,
            track.rows.filter { it.z in -(speed * horizon + 12f)..8f }.map { row ->
                BotRow(row.z, row.obs.map { o -> Obstacle(o.x, o.cy, o.sy, o.halfW, o.type,
                    o.sz, o.ramp, o.sliding, o.slideTo, o.slideRate, o.anim, o.phase, o.used) }, buildList {
                    row.coins?.filter { !it.taken && !it.missed }?.forEach { add(Goodie(it.restX, it.y, it.dz, 1f)) }
                    if (row.pickup != Pickup.NONE) add(Goodie(row.pickupX, .7f, Row.PICKUP_DZ, 5f))
                })
            }, Lanes.count, Lanes.w, now)
        val hold = ceil((planningSeconds + .025f) * scale * 60f).toInt().coerceIn(1, 18)
        val executor = worker ?: Executors.newSingleThreadExecutor { task ->
            Thread(task, "cube-idle-pilot").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
        }.also { worker = it }
        pending = executor.submit<Decision> {
            val before = System.nanoTime()
            val timeline = Timeline(course, speed.coerceAtLeast(4.5f), seconds = horizon, safetyMargin = .025f)
            val plan = Planner(24, 6, stride = 3, gestureCost = .06f, slamCost = .3f, reversalCost = .3f)
                .solve(timeline, body, holdFrames = hold)
            Decision(now, timeline.dt, centerFirst(timeline, plan, body, hold, 6), (System.nanoTime() - before) / 1e9f)
        }
    }

    fun replan() {
        pending?.cancel(true); pending = null; decision = null; cursor = 0; nextPlan = 0f
    }

    fun stop() {
        idleSeconds = 0f; running = false
        if (Stage.botOwner === this) Stage.botOwner = null
        replan()
    }

    override fun close() { stop(); worker?.shutdownNow(); worker = null }
}
