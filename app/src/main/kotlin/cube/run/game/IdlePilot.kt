package cube.run.game

import cube.run.bot.*
import cube.run.core.Stage
import cube.run.data.Settings
import cube.run.game.track.Pickup
import cube.run.game.track.Row
import cube.run.game.track.Track
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong
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
    internal data class Event(val id: Long, val at: Float, val action: Int)
    private val eventSequence = AtomicLong()
    private var lastEvent = 0L
    internal data class Decision(val at: Float, val step: Float, val plan: Plan, val seconds: Float, val held: Int, val events: List<Event>)
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
        lastAction = now - 10f; lastEvent = 0L; planningSeconds = .06f
        boosts = 0; nextBoost = now + .45f; nextPlan = now
    }

    fun drive(now: Float, speed: Float, scale: Float, track: Track, body: Body, motion: JetMotion? = null, swipe: (Int) -> Unit) {
        if (!active || input != Stage.interactions.get()) { stop(); return }
        if (lanes != Lanes.count || flying != body.flying || hovering != body.hover) {
            replan()
            lanes = Lanes.count; flying = body.flying; hovering = body.hover
        }
        if (boosts < 5 && now >= nextBoost) {
            Stage.boostRequests.incrementAndGet()
            boosts++; nextBoost = now + .35f
        }
        decision?.let { d ->
            while (cursor < d.events.size && d.events[cursor].at <= now) {
                val event = d.events[cursor]
                if (event.id <= lastEvent) { cursor++; continue }
                val action = event.action
                if (action != Action.NONE && now-lastAction < .095f) break
                cursor++
                if (action != Action.NONE) {
                    swipe(action-1); lastAction = now; lastEvent = event.id
                    // The snapshot was taken on entry, before this input. A new
                    // search in this same slice must start from the updated state.
                    Action.apply(body, action, Lanes.count)
                }
            }
        }
        pending?.takeIf { it.isDone }?.let { result ->
            pending = null
            val next = result.get()
            planningSeconds = maxOf(planningSeconds * .92f, next.seconds)
            nextPlan = now + .08f
            // A replacement must arrive before the end of its committed prefix.
            // Otherwise its first newly chosen move may already be in the past.
            if (next.plan.survived && now-next.at <= next.held*next.step) {
                decision = next
                // Prefix timing can move by a fraction of a frame. Identity, not
                // its adjusted timestamp, tells us whether an input already fired.
                cursor = next.events.indexOfFirst { it.id > lastEvent }.let { if (it < 0) next.events.size else it }
            } else nextPlan = now
        }
        if (pending != null || now < nextPlan) return
        // Continue the verified plan while its replacement is solved. The replacement
        // simulates those same committed inputs instead of assuming the player coasts.
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
        val hold = ceil((planningSeconds + .055f) * scale * 60f).toInt().coerceIn(4, 24)
        val prefix = IntArray(hold)
        val committed = ArrayList<Event>()
        decision?.let { d ->
            var earliest = (lastAction + .10f-now).coerceAtLeast(0f)
            for (i in cursor until d.events.size) {
                val event = d.events[i]
                val due = maxOf(event.at-now, earliest, 0f)
                val frame = ceil(due*60f).toInt()
                if (frame >= hold) break
                prefix[frame] = event.action
                committed.add(event.copy(at = now+due))
                earliest = due+.10f
            }
        }
        val cooldown = ceil((lastAction+.10f-now).coerceAtLeast(0f)*60f).toInt()
        val executor = worker ?: Executors.newSingleThreadExecutor { task ->
            Thread(task, "cube-idle-pilot").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
        }.also { worker = it }
        pending = executor.submit<Decision> {
            val before = System.nanoTime()
            val timeline = Timeline(course, speed.coerceAtLeast(4.5f), seconds = horizon, safetyMargin = .10f, motion = motion)
            val searched = Planner(24, 6, stride = 3, gestureCost = .06f, slamCost = .3f, reversalCost = .3f)
                .solve(timeline, body, holdFrames = hold, heldActions = prefix, initialCooldown = cooldown)
            val plan = centerFirst(timeline, searched, body, hold, 6)
            val events = committed + (hold until plan.actions.size).filter { plan.actions[it] != Action.NONE }
                .map { Event(eventSequence.incrementAndGet(), now+it*timeline.dt, plan.actions[it]) }
            Decision(now, timeline.dt, plan, (System.nanoTime() - before) / 1e9f, hold, events)
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
