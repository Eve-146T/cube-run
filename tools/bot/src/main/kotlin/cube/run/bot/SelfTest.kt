package cube.run.bot

import java.io.*

fun selfTest() {
    val impossible = Course(0, "sealed", 0, 0, false, 1, listOf(BotRow(0f, listOf(
        Obstacle(0f, 10f, 20f, 20f, 0, 1f, 0f, false, 0f, 0f, 0, 0f)))))
    for (stride in listOf(1, 3)) {
        val t = Timeline(impossible, 10f, seconds = .5f)
        val result = Planner(8, 1, stride).solve(t)
        check(!result.survived && result.actions.isEmpty()) { "An empty search must return a failure without indexing an empty plan" }
        check(!Planner(8, 1, stride).solve(t, holdFrames = 3).survived)
    }
    val coins = Course(1, "loot", 0, 0, false, 1, listOf(BotRow(-12f, emptyList(), listOf(Goodie(1.7f, .45f, 0f, 8f)))))
    for (stride in listOf(1, 3)) {
        val t = Timeline(coins, 12f)
        val plan = Planner(24, 6, stride).solve(t, holdFrames = 3)
        check(plan.survived && replay(t, plan.actions) && plan.rewards == 8f) { "Safe optional reward missed, stride=$stride: $plan" }
        val centered = centerTiming(t, plan, 6)
        check(centered.survived && replay(t, centered.actions))
        val events = centered.actions.indices.filter { centered.actions[it] != 0 }
        check(events.zipWithNext().all { it.second - it.first >= 6 })
    }
    val bytes = ByteArrayOutputStream()
    val padReturn = Course(4, "late springboard return", 0, 0, false, 0, listOf(
        BotRow(-5.1688857f, listOf(Obstacle(.85f, 1.1f, 2.2f, 1.6f, 0, 1f, 0f, false, 0f, 0f, 0, 0f))),
        BotRow(-11.269015f, listOf(Obstacle(0f, .07f, .14f, .7f, 3, 1f, 0f, false, 0f, 0f, 0, 0f))),
        BotRow(-17.96551f, listOf(Obstacle(0f, .675f, 1.35f, 2.85f, 0, 1f, 0f, false, 0f, 0f, 0, 0f)))))
    val returning = Body(lane = 0, x = -.3714964f, y = 3.1976452f, vy = -2.803528f, air = true)
    val padTimeline = Timeline(padReturn, 30f, seconds = .7f, safetyMargin = .10f)
    check(!replay(padTimeline, IntArray(42).apply { this[20] = Action.RIGHT }, returning)) {
        "A last-frame springboard edge catch must not be treated as a reliable launch"
    }
    check(replay(padTimeline, IntArray(42).apply { this[16] = Action.RIGHT }, returning)) {
        "Returning to the springboard interior in time remains possible"
    }
    val prefixTimeline = Timeline(coins, 12f, seconds = 1.5f)
    val prefix = IntArray(9).apply { this[7] = Action.RIGHT }
    val prefixPlan = Planner(24, 6, 3).solve(prefixTimeline, holdFrames = 9, heldActions = prefix)
    check(prefixPlan.survived && prefixPlan.actions.take(9) == prefix.toList())
    val centeredPrefix = centerFirst(prefixTimeline, prefixPlan, Body(), 9, 6)
    check(centeredPrefix.actions.take(9) == prefix.toList())
    check(centeredPrefix.actions.indices.filter { centeredPrefix.actions[it] != 0 }.zipWithNext().all { (a, b) -> b-a >= 6 })
    val bait = Course(2, "fatal loot", 0, 0, false, 1, listOf(BotRow(-8f,
        listOf(Obstacle(0f, 4f, 8f, .5f, 0, 1f, 0f, false, 0f, 0f, 0, 0f)),
        listOf(Goodie(0f, .45f, 0f, 10000f)))))
    val baitTimeline = Timeline(bait, 12f)
    val safe = Planner(24, 6).solve(baitTimeline)
    check(safe.survived && safe.rewards == 0f && replay(baitTimeline, safe.actions)) {
        "Survival must take priority over a fatal pickup, regardless of its value"
    }
    DataOutputStream(bytes).use { CourseFile.write(it, listOf(impossible, coins)) }
    check(CourseFile.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray()))) == listOf(impossible, coins))
    val flight = Body(y = 1f, flying = true, flightLeft = .02f)
    val empty = Timeline(coins, 12f)
    check(empty.step(flight, 0, 0) && flight.flying)
    check(empty.step(flight, 1, 0) && !flight.flying && flight.air && flight.vy < 0)
    for (hz in listOf(60, 90)) {
        val floatCourse = Course(3, "jet in Zero-G", 0, 0, false, 1, emptyList(), width = 2.6f)
        val timeline = Timeline(floatCourse, 30f, dt = 1f / hz, seconds = 5f)
        val body = Body(y = 1.4f, hover = true, flying = true, flightLeft = 3f)
        for (frame in timeline.frames.indices) {
            check(timeline.step(body, frame, Action.NONE))
            if (frame == hz) check(body.y > 5f) { "Zero-G overrides the jetpack at $hz Hz: ${body.y}" }
            if (body.flying) check(body.flyY >= 1.4f) { "Jet glide aims below the hover floor" }
        }
        check(!body.flying && body.hover && kotlin.math.abs(body.y - 1.4f) < .2f)
    }
    println("Bot self-tests passed: unreachable states, committed input handoff, springboard interior, stride reconstruction, safe/fatal loot, timing centering, flight expiry, Zero-G jet priority/glide, fixture codec")
}
