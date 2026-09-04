package cube.run.game

import com.badlogic.gdx.graphics.Color
import cube.run.core.hsvInto
import kotlin.random.Random

/** Obstacle collision behaviour. */
object ObType {
    const val DODGE = 0   // solid — get out of its lane
    const val JUMP = 1    // low wall — be airborne / high enough
    const val DUCK = 2    // overhead bar — be rolling / low enough
    const val DECO = 3    // decoration only (tar-pit rims) — never collides
    const val PLAT = 4    // a raised platform: walk on top (see CubeRun.groundAt), its side is a wall
}

/** Obstacle animation kinds (advanced every frame by [Track.scroll]). */
object ObAnim {
    const val NONE = 0
    const val PISTON = 1  // pumps up out of the floor and sinks back
    const val SWEEP = 2   // glides sideways across all lanes
    const val STOMP = 3   // hangs high, slams down, lifts again
}

/** What a row may carry on the walk lane besides obstacles. */
object Pickup {
    const val NONE = 0
    const val BUBBLE = 1  // shield up on touch
    const val BOX = 2     // a mystery box, opened on the run-over screens (rare)
    const val MAGNET = 3  // timed: a huge coin magnet
    const val MULT = 4    // timed: 2× score
    const val JET = 5     // timed: fly above everything along an air coin line
}

/**
 * One obstacle box. World geometry is drawn through the engine's batched
 * worldBox pass, so this is plain data: position + colour + collision, no
 * ModelInstance. Animated kinds mutate [x] / [cy] / [sy] / [clear] per frame.
 */
class Ob(
    val col: Color, var x: Float, var cy: Float, val halfW: Float,
    val type: Int, var clear: Float,
    val sx: Float, var sy: Float, val sz: Float,
    val sliding: Boolean = false, val slideTo: Float = 0f, val slideRate: Float = 2f,
    val anim: Int = ObAnim.NONE, val phase: Float = 0f,
    /** Platforms only: length of the rising ramp at the front (0 = flat continuation). */
    val ramp: Float = 0f,
)

/**
 * A coin: position relative to its row ([dz] is added to the row's z).
 * [restY] is where it belongs on the ground — air-laid coins settle there if
 * a jetpack flight ends before they arrive.
 */
class Coin(var x: Float, var y: Float, val dz: Float, val restY: Float = y) {
    var taken = false
}

/** One row of the lane-walk: obstacles, optional coins and pickup, scoring state. */
class Row(var z: Float, val obs: ArrayList<Ob>) {
    var safeLane = 1              // the walk lane when this row spawned
    var scored = false
    var minClear = 99f            // tightest clearance seen while crossing (near-miss detect)
    var coins: ArrayList<Coin>? = null
    var pickup = Pickup.NONE
    var pickupX = 0f

    companion object {
        /** Pickups float this far behind their row (between it and the next). */
        const val PICKUP_DZ = -3.2f
    }
}

/**
 * Builds obstacles in the game's visual language. Every kind has a fixed hue
 * offset from the current world hue so its verb reads at a glance: pillars
 * (dodge) +185, sliders +230, low walls (jump) +140, overhead bars (roll) +300,
 * pistons +155, sweepers +120, stompers +210.
 */
class ObstacleFactory(private val rnd: Random, private val laneW: Float) {

    companion object {
        /** Height of a platform's walkable top. A standing jump (apex +1.36) clears it. */
        const val PLAT_TOP = 1.0f
    }

    fun laneX(l: Int) = (l - 1) * laneW

    private fun hsv(h: Float, s: Float, v: Float) = hsvInto(Color(), h, s, v)

    fun pillar(x: Float, hue: Float, sliding: Boolean = false, slideTo: Float = 0f, slideRate: Float = 2f): Ob {
        val h = 2.0f + rnd.nextFloat() * 0.6f
        val c = if (sliding) hsv(hue + 230f, 0.95f, 1f) else hsv(hue + 185f, 0.85f, 1f)
        return Ob(c, x, h / 2f, 0.75f, ObType.DODGE, 0f, 1.5f, h, 0.9f, sliding, slideTo, slideRate)
    }

    /** A solid block sitting ON the ground across every lane — clearly "jump over". */
    fun wall(hue: Float): Ob {
        val w = laneW * 3f + 0.6f; val h = 0.62f // cube must clear this height
        return Ob(hsv(hue + 140f, 0.9f, 1f), 0f, h / 2f, laneW * 1.5f + 0.3f, ObType.JUMP, h, w, h, 0.7f)
    }

    /** A chunky beam floating well above the ground with a clear gap beneath — unmistakably "roll under". */
    fun over(hue: Float): Ob {
        val w = laneW * 3f + 0.6f; val bottom = 0.78f; val top = 1.5f
        return Ob(hsv(hue + 300f, 0.9f, 1f), 0f, (bottom + top) / 2f, laneW * 1.5f + 0.3f, ObType.DUCK, bottom, w, top - bottom, 0.7f)
    }

    fun slider(from: Int, to: Int, hue: Float): Ob =
        pillar(laneX(from), hue, sliding = true, slideTo = laneX(to))

    /** Low jump-wall segment spanning lanes [a]..[b] — same "jump over" language as [wall]. */
    fun wallSeg(a: Int, b: Int, hue: Float): Ob {
        val cx = (laneX(a) + laneX(b)) / 2f
        val w = (b - a) * laneW + laneW * 1.05f; val h = 0.62f
        return Ob(hsv(hue + 140f, 0.9f, 1f), cx, h / 2f, w / 2f - 0.11f, ObType.JUMP, h, w, h, 0.7f)
    }

    /** Overhead-bar segment spanning lanes [a]..[b] — same "roll under" language as [over]. */
    fun overSeg(a: Int, b: Int, hue: Float): Ob {
        val cx = (laneX(a) + laneX(b)) / 2f
        val w = (b - a) * laneW + laneW * 1.05f; val bottom = 0.78f; val top = 1.5f
        return Ob(hsv(hue + 300f, 0.9f, 1f), cx, (bottom + top) / 2f, w / 2f - 0.11f, ObType.DUCK, bottom, w, top - bottom, 0.7f)
    }

    /**
     * A tar pit spanning lanes [a]..[b]: a near-black slab flush with the floor
     * (touching it on the ground kills — any jump clears it) inside a glowing
     * rim so it reads as a hole, not a block.
     */
    fun addTar(a: Int, b: Int, hue: Float, into: ArrayList<Ob>) {
        val cx = (laneX(a) + laneX(b)) / 2f
        val w = (b - a) * laneW + laneW * 1.05f; val h = 0.16f; val d = 1.7f
        into.add(Ob(hsv(hue + 140f, 0.9f, 1f), cx, 0.055f, 0f, ObType.DECO, 0f, w + 0.18f, 0.11f, d + 0.18f)) // rim
        into.add(Ob(Color(0.03f, 0.02f, 0.05f, 1f), cx, h / 2f, w / 2f - 0.11f, ObType.JUMP, h, w, h, d))
    }

    /** A piston in lane [l]: pumps out of the floor and sinks back, out of phase with its neighbours. */
    fun piston(l: Int, hue: Float, phase: Float): Ob =
        Ob(hsv(hue + 155f, 0.9f, 1f), laneX(l), 0f, 0.7f, ObType.JUMP, 0f, 1.4f, 0.01f, 0.8f, anim = ObAnim.PISTON, phase = phase)

    /** A low beam that glides sideways across every lane. Jump it (or time a gap). */
    fun sweeper(hue: Float, phase: Float): Ob {
        val h = 0.36f
        return Ob(hsv(hue + 120f, 0.9f, 1f), 0f, h / 2f, 0.85f, ObType.JUMP, h, 1.7f, h, 0.5f, anim = ObAnim.SWEEP, phase = phase)
    }

    /** A block hanging over lane [l] that slams to the floor and lifts again. */
    fun stomper(l: Int, hue: Float, phase: Float): Ob =
        Ob(hsv(hue + 210f, 0.9f, 1f), laneX(l), 1.5f, 0.62f, ObType.DUCK, 1.0f, 1.25f, 1.0f, 0.9f, anim = ObAnim.STOMP, phase = phase)

    /**
     * One platform segment in lane [l]: [len] deep, its FRONT at the row's z, [top]
     * high, with a [ramp]-long rise at the front (0 for a flat continuation of
     * the previous row's platform). Ramps are drawn as steps; the walkable
     * height is linear along them (CubeRun.groundAt).
     */
    fun platform(l: Int, hue: Float, len: Float, ramp: Float): Ob =
        Ob(hsv(hue + 95f, 0.55f, 0.95f), laneX(l), PLAT_TOP / 2f, 0.78f, ObType.PLAT, PLAT_TOP, 1.56f, PLAT_TOP, len, ramp = ramp)

    /** Segments over every lane except [open]: one wide piece, or two when the gap is the centre. */
    fun addSegsExcept(open: Int, hue: Float, into: ArrayList<Ob>, over: Boolean) {
        fun seg(a: Int, b: Int) = if (over) overSeg(a, b, hue) else wallSeg(a, b, hue)
        when (open) {
            0 -> into.add(seg(1, 2))
            2 -> into.add(seg(0, 1))
            else -> { into.add(seg(0, 0)); into.add(seg(2, 2)) }
        }
    }

    /** Tar pits over every lane except [open]. */
    fun addTarExcept(open: Int, hue: Float, into: ArrayList<Ob>) {
        when (open) {
            0 -> addTar(1, 2, hue, into)
            2 -> addTar(0, 1, hue, into)
            else -> { addTar(0, 0, hue, into); addTar(2, 2, hue, into) }
        }
    }

    /** Wide bar leaving exactly one lane open (twin pillars when the centre is open). */
    fun addOneOpen(open: Int, hue: Float, into: ArrayList<Ob>) {
        if (open == 1) {
            into.add(pillar(laneX(0), hue)); into.add(pillar(laneX(2), hue))
        } else {
            val a = if (open == 0) 1 else 0 // blocked adjacent lane pair
            val cx = (laneX(a) + laneX(a + 1)) / 2f
            val w = laneW + 1.5f
            into.add(Ob(hsv(hue + 185f, 0.85f, 1f), cx, 2.3f / 2f, w / 2f, ObType.DODGE, 0f, w, 2.3f, 0.9f))
        }
    }
}
