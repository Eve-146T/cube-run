package cube.run.bot

import kotlin.math.*
import kotlin.random.Random

data class Plan(val survived: Boolean, val actions: IntArray, val reachedFrame: Int,
    val rewards: Float, val expanded: Long, val peakGesturesPerSecond: Int)

/** Bounded search, not an impossibility proof. Dead candidates can never outrank living ones. */
class Planner(private val beam: Int = 96, private val inputEvery: Int = 1, private val stride: Int = 1,
    private val gestureCost: Float = .002f, private val slamCost: Float = 0f, private val reversalCost: Float = 0f) {
    private class Node(val body: Body, val previous: Node?, val action: Int, val cooldown: Int,
        val reward: Float, val gestures: Int, val rank: Float, val cost: Float = 0f)

    fun solve(t: Timeline, initial: Body = Body(lane = t.course.entry,
        x = (t.course.entry - (t.course.lanes - 1) / 2f) * t.course.width), holdFrames: Int = 0): Plan {
        var expanded = 0L
        val held = initial.copy()
        val hold = holdFrames.coerceIn(0, t.frames.size)
        for (frame in 0 until hold) if (!t.step(held, frame, 0)) return Plan(false, IntArray(0), frame, 0f, 0, 0)
        var nodes = listOf(Node(held, null, 0, 0, 0f, 0, 0f))
        var reached = hold
        for (frame in hold until t.frames.size step stride) {
            val end = min(frame + stride, t.frames.size)
            val candidates = HashMap<Long, Node>(beam * 4)
            for (n in nodes) for (a in 0..4) {
                if (a != 0 && (n.cooldown > 0 ||
                    a == Action.LEFT && n.body.lane == 0 || a == Action.RIGHT && n.body.lane == t.course.lanes - 1 ||
                    a == Action.JUMP && (n.body.air && n.body.coyoteLeft <= 0f || n.body.flying || n.body.hover) ||
                    a == Action.DOWN && (n.body.flying || n.body.hover || n.body.duckT > .28f))) continue
                val b = n.body.copy()
                expanded++
                var alive = true
                var reward = n.reward
                for (step in frame until end) {
                    if (!t.step(b, step, if (step == frame) a else 0)) { alive = false; break }
                    for (g in t.frames[step].goods) if (abs(g.x - b.x) < .8f && abs(g.y - b.y) < .8f) reward += g.value
                }
                if (!alive) continue
                val gestures = n.gestures + if (a == 0) 0 else 1
                val target = (n.body.lane - (t.course.lanes - 1) / 2f) * t.course.width
                val reversing = a == Action.LEFT && target - n.body.x > .4f ||
                    a == Action.RIGHT && target - n.body.x < -.4f
                val cost = n.cost + (if (a == 0) 0f else gestureCost) +
                    (if (a == Action.DOWN && n.body.air) slamCost else 0f) +
                    (if (reversing) reversalCost else 0f)
                val cooldown = max(0, (if (a == 0) n.cooldown else inputEvery) - (end - frame))
                // Keep escape trajectories in the beam even when the coin line tempts
                // candidates to remain in a lane that is about to close.
                val forecast = b.copy()
                var coast = 0
                var expectedReward = reward
                for (ahead in end until min(t.frames.size, end + 36)) {
                    if (!t.step(forecast, ahead, Action.NONE)) break
                    for (g in t.frames[ahead].goods) if (abs(g.x - forecast.x) < .8f && abs(g.y - forecast.y) < .8f) expectedReward += g.value
                    coast++
                }
                val penalty = if (slamCost == 0f && reversalCost == 0f) gestures * gestureCost else cost
                val rank = coast * 10f + expectedReward * .03f - penalty - abs(b.x) * .0001f
                val node = Node(b, n, a, cooldown, reward, gestures, rank, cost)
                val key = key(b, cooldown)
                val old = candidates[key]
                if (old == null || node.rank > old.rank) candidates[key] = node
            }
            if (candidates.isEmpty()) break
            nodes = candidates.values.sortedByDescending { it.rank }.take(beam)
            reached = end
        }
        val best = nodes.maxBy { it.rank }
        val actions = IntArray(reached)
        var n: Node? = best
        if (reached > hold) for (i in (hold + (reached - hold - 1) / stride * stride) downTo hold step stride) {
            actions[i] = n!!.action; n = n.previous
        }
        val peak = if (actions.isEmpty()) 0 else actions.indices.maxOf { from ->
            (from until min(actions.size, from + ceil(1 / t.dt).toInt())).count { actions[it] != 0 }
        }
        return Plan(reached == t.frames.size, actions, reached, best.reward, expanded, peak)
    }

    private fun key(b: Body, cooldown: Int): Long {
        var k = b.lane.toLong()
        fun add(v: Int, radix: Int) { k = k * radix + v.coerceIn(0, radix - 1) }
        add(((b.x + 10f) * 10).roundToInt(), 256)
        add((b.y * 10).roundToInt(), 128)
        add(((b.vy + 30f) * 1.5f).roundToInt(), 128)
        add((b.duck * 10).roundToInt(), 16)
        add((b.duckT * 20).roundToInt(), 16)
        add(cooldown, 64)
        add(if (b.air) 1 else 0, 2); add(if (b.slam) 1 else 0, 2)
        add((b.coyoteLeft * 100).roundToInt(), 16)
        add((b.jumpBuffer * 100).roundToInt(), 16)
        return k xor (b.pads * -7046029254386353131L)
    }
}

fun replay(t: Timeline, actions: IntArray, initial: Body = Body(lane = t.course.entry,
    x = (t.course.entry - (t.course.lanes - 1) / 2f) * t.course.width)): Boolean {
    val b = initial.copy()
    return t.frames.indices.all { t.step(b, it, actions.getOrElse(it) { 0 }) }
}

/** Cheap online timing refinement using the actual current (possibly airborne) state. */
fun centerFirst(t: Timeline, plan: Plan, initial: Body, minimum: Int, cadence: Int): Plan {
    if (!plan.survived) return plan
    val actions = plan.actions.copyOf()
    val first = actions.indexOfFirst { it != 0 }
    if (first < 0) return plan
    val next = (first + 1 until actions.size).firstOrNull { actions[it] != 0 } ?: (actions.size + cadence - 1)
    val action = actions[first]; actions[first] = 0
    var start = -1; var bestStart = first; var bestEnd = first
    for (at in max(minimum, first - 12)..min(first + 6, next - cadence)) {
        actions[at] = action
        val safe = replay(t, actions, initial)
        actions[at] = 0
        if (safe) {
            if (start < 0) start = at
            if (at - start > bestEnd - bestStart) { bestStart = start; bestEnd = at }
        } else start = -1
    }
    actions[(bestStart + bestEnd) / 2] = action
    return plan.copy(actions = actions)
}

/** Move each gesture toward the middle of its verified safe timing window, ahead of coin greed. */
fun centerTiming(t: Timeline, plan: Plan, cadence: Int): Plan {
    if (!plan.survived) return plan
    val actions = plan.actions.copyOf()
    val events = actions.indices.filter { actions[it] != 0 }.toMutableList()
    repeat(2) {
        for (i in events.indices) {
            val old = events[i]; val action = actions[old]
            val lo = max(max(0, old - 12), if (i > 0) events[i - 1] + cadence else 0)
            val hi = min(min(actions.lastIndex, old + 12), if (i < events.lastIndex) events[i + 1] - cadence else actions.lastIndex)
            actions[old] = 0
            var bestStart = old; var bestEnd = old; var start = -1
            for (at in lo..hi) {
                actions[at] = action
                val safe = replay(t, actions)
                actions[at] = 0
                if (safe) {
                    if (start < 0) start = at
                    if (at - start > bestEnd - bestStart) { bestStart = start; bestEnd = at }
                } else start = -1
            }
            val at = (bestStart + bestEnd) / 2
            actions[at] = action; events[i] = at
        }
    }
    check(replay(t, actions))
    val body = Body(lane = t.course.entry, x = (t.course.entry - (t.course.lanes - 1) / 2f) * t.course.width)
    var rewards = 0f
    for (frame in actions.indices) {
        t.step(body, frame, actions[frame])
        for (g in t.frames[frame].goods) if (abs(g.x - body.x) < .8f && abs(g.y - body.y) < .8f) rewards += g.value
    }
    val peak = events.maxOfOrNull { at -> events.count { it >= at && it < at + ceil(1 / t.dt).toInt() } } ?: 0
    return plan.copy(actions = actions, rewards = rewards, peakGesturesPerSecond = peak)
}

/** Independently jitter every gesture; colliding deliveries are serialized, never discarded. */
fun jitterSurvival(t: Timeline, plan: Plan, jitterFrames: Int, trials: Int = 12): Int {
    return (0 until trials).count { seed ->
        val random = Random(seed)
        val delayed = IntArray(t.frames.size)
        var last = -1
        for (i in plan.actions.indices) if (plan.actions[i] != 0) {
            val at = max(last + 1, (i + random.nextInt(-jitterFrames, jitterFrames + 1)).coerceAtLeast(0))
            if (at < delayed.size) delayed[at] = plan.actions[i]
            last = at
        }
        replay(t, delayed)
    }
}
