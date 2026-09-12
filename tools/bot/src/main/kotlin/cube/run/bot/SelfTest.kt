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
    println("Bot self-tests passed: unreachable states, held inputs, stride reconstruction, safe/fatal loot, timing centering, flight expiry, fixture codec")
}
