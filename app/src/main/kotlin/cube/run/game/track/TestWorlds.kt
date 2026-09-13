package cube.run.game.track

import cube.run.data.Bonus

/** Fixed, looping human-test courses. They use normal pickups, portals and collisions. */
object TestWorlds {
    data class Cue(val code: Int = Step.EM, val gap: Float = 14f,
        val pickup: Int = Pickup.NONE, val world: Int = Bonus.NONE, val exit: Boolean = false)
    data class World(val id: Int, val name: String, val detail: String, val route: String, val cues: List<Cue>)
    const val JET_SECONDS = 6f
    private fun portal(world: Int, exit: Boolean = false) = Cue(Step.PORTAL, world = world, exit = exit)
    private fun road(lanes: List<Int>) = lanes.map { Cue(Step.dg(it)) }
    private val drift = road(listOf(1, 0, 0, 1, 2, 2, 1))
    private fun floating(beforeJet: Boolean = false, insideJet: Boolean = false, expire: Boolean = false, pad: Int? = null): List<Cue> = buildList {
        add(Cue())
        if (beforeJet) add(Cue(pickup = Pickup.JET))
        add(portal(Bonus.FLOAT))
        add(Cue())
        if (insideJet || expire) add(Cue(pickup = Pickup.JET))
        if (expire) repeat(5) { addAll(drift) } else addAll(drift)
        if (pad != null) add(Cue(Step.dg(pad)))
        add(portal(Bonus.FLOAT, true))
        if (pad != null) {
            add(Cue(Step.pd(pad), gap = 16f))
            add(Cue(Step.TW, gap = 9.5f))
            add(Cue(gap = 18f))
        } else addAll(road(listOf(1, 0, 1, 2, 1)))
        add(Cue(gap = 20f)); add(Cue(gap = 20f))
    }
    val all = listOf(
        World(0, "JET BEFORE ZERO-G", "Collect the jet, then enter the portal.", "JET  →  ZERO-G", floating(beforeJet = true)),
        World(1, "JET INSIDE ZERO-G", "Pick up a jet while already floating.", "ZERO-G  →  JET", floating(insideJet = true)),
        World(2, "JET EXPIRES IN ZERO-G", "A six-second jet returns you to hover height.", "JET  →  HOVER", floating(expire = true)),
        World(3, "ZERO-G ALIGNMENT", "Check lane gaps and coins on entry and exit.", "WIDEN  →  NARROW", floating()),
        World(4, "EXIT TO LEFT PAD", "Catch the left pad just after the portal.", "EXIT  →  LEFT PAD", floating(pad = 0)),
        World(5, "EXIT TO RIGHT PAD", "Catch the right pad just after the portal.", "EXIT  →  RIGHT PAD", floating(pad = 2)),
        World(6, "WIDE ROAD TRANSITIONS", "Follow five lanes, then return to three.", "3 LANES  →  5  →  3", buildList {
            add(Cue()); add(portal(Bonus.WIDE)); repeat(42) { add(Cue(Step.WIDE)) }
            add(portal(Bonus.WIDE, true)); addAll(road(listOf(1, 0, 1, 2, 1))); add(Cue(gap = 20f))
        }),
        World(7, "ROLLING HILLS", "Jump, duck and collect coins over the hills.", "FLAT  →  HILLS  →  FLAT", buildList {
            add(Cue()); add(portal(Bonus.HILLS))
            repeat(3) { addAll(listOf(Cue(Step.CF), Cue(Step.JP), Cue(Step.dg(1)), Cue(Step.DK))) }
            add(portal(Bonus.HILLS, true)); add(Cue(gap = 20f))
        })
    )
    fun byId(id: Int) = all.firstOrNull { it.id == id }
}
