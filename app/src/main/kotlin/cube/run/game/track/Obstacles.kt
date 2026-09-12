package cube.run.game.track

import com.badlogic.gdx.graphics.Color
import cube.run.core.hsvInto
import cube.run.game.Lanes
import kotlin.math.abs
import kotlin.random.Random

/** Obstacle collision behaviour. */
object ObType {
    const val SOLID = 0   // a block: hits if the cube overlaps it, laterally AND vertically (pillars, walls, bars, tar…)
    const val DECO = 1    // decoration only (tar-pit rims, stripes) — never collides
    const val PLAT = 2    // a raised platform: walk on top (see CubeRun.groundAt), its side is a wall
    const val PAD = 3     // a bounce pad: harmless; touching it on the ground launches you high
}

/** Obstacle animation kinds (advanced every frame by [Track.scroll]). */
object ObAnim {
    const val NONE = 0
    const val PISTON = 1    // pumps up out of the floor and sinks back
    const val SWEEP = 2     // glides sideways across all lanes
    const val STOMP = 3     // hangs high, slams down, lifts again
    const val PENDULUM = 4  // a block swinging sideways at chest height — roll under or step aside
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
 * ModelInstance. Animated kinds mutate [x] / [cy] / [sy] per frame.
 *
 * Collision is a plain box test: the cube must overlap it sideways AND
 * vertically ([bottom]..[top]). So a pillar is jumped when you are high
 * enough (a platform, a bounce pad), a wall when your bottom clears it, a
 * bar when your rolled head ducks under it — one rule for everything.
 */
class Ob(
    val col: Color, var x: Float, var cy: Float, val halfW: Float,
    val type: Int,
    val sx: Float, var sy: Float, val sz: Float,
    val sliding: Boolean = false, val slideTo: Float = 0f, val slideRate: Float = 2f,
    val anim: Int = ObAnim.NONE, val phase: Float = 0f,
    /** Platforms only: length of the rising ramp at the front (0 = flat continuation). */
    val ramp: Float = 0f,
    /** A tar pit: drawn sunk into the road as a void (the collision box stays where it is). */
    val pit: Boolean = false,
) {
    /** Pads: launched the player already (once per pass). */
    var used = false

    val bottom: Float get() = cy - sy / 2f
    val top: Float get() = cy + sy / 2f
    /** Platforms: the walkable height (their top). */
    val clear: Float get() = top
    /** Sits on the floor (grows upward when it streams in) rather than hanging. */
    val grounded: Boolean get() = bottom < 0.12f

    /** Sideways gap between the cube (centre [px], half width 0.36) and this obstacle: negative = overlapping. */
    fun lateral(px: Float): Float = abs(px - x) - (halfW + 0.36f)
}

/**
 * A coin: position relative to its row ([dz] is added to the row's z).
 * [restY] is where it belongs on the ground — air-laid coins settle there if
 * a jetpack flight ends before they arrive.
 */
class Coin(var x: Float, var y: Float, val dz: Float, val restY: Float = y) {
    /** Authored lane before magnet attraction; also used by the demo controller. */
    val restX = x
    var taken = false
    /** Slid past the cube uncollected: still drawn (it glides by), but it no longer counts or pulls. */
    var missed = false
}

/** One row of the lane-walk: obstacles, optional coins and pickup, scoring state. */
class Row(var z: Float, val obs: ArrayList<Ob>) {
    /** Stable per-row phase: scrolling must never accelerate pickup animation. */
    val visualPhase = z % 6.2831855f
    var laneCount = 3            // geometry when generated, even before its portal is crossed
    var safeLane = 1              // the walk lane when this row spawned
    fun safeX(): Float = if (laneCount == 5) (safeLane - 2) * Lanes.NORMAL_W else (safeLane - 1) * Lanes.w
    var scored = false
    var minClear = 99f            // tightest clearance seen while crossing (near-miss detect)
    var coins: ArrayList<Coin>? = null
    var pickup = Pickup.NONE
    var pickupX = 0f
    /** Idle rows are decoration before the run starts: coins only, never scored. */
    var idle = false
    /** A portal row: crossing it enters this bonus world (data.Bonus id), or leaves one when [portalExit]. */
    var portal = -1
    var portalExit = false
    /**
     * Stream-in: 0 while still out in the haze, then springs 0→1 (with a
     * little overshoot) as the row comes into clear view. Rendering scales
     * the obstacles, coins and pickups by it.
     */
    var pop = 0f
    var popStart = -1f

    companion object {
        /** Pickups float this far behind their row (between it and the next). */
        const val PICKUP_DZ = -3.2f
        /** Rows start streaming in once they are this close (just inside the haze). */
        const val POP_Z = -64f
    }
}

/**
 * Builds obstacles in the game's visual language. Every kind has a fixed hue
 * offset from the current world hue so its verb reads at a glance: pillars
 * (dodge) +185, sliders +230, low walls (jump) +140, overhead bars (roll) +300,
 * pistons +155, sweepers +120, stompers +210, pendulums +260, tall walls +140
 * (striped), pads +85.
 */
class ObstacleFactory(private val rnd: Random) {

    private val laneW: Float get() = Lanes.NORMAL_W

    companion object {
        /** Height of a platform's walkable top. A standing jump (apex +1.36) clears it. */
        const val PLAT_TOP = 1.0f
        /** Pillar height: a jump from a platform or a bounce pad clears it, a ground jump never does. */
        const val PILLAR_H = 2.0f
        /** A tall wall: only a bounce pad (or a platform) gets you over. */
        const val TALL_H = 1.35f
    }

    /** Lane x in the three-lane language sections are written in (the road may be wider or stretched right now). */
    fun laneX(l: Int) = (l - 1) * Lanes.w

    private fun hsv(h: Float, s: Float, v: Float) = hsvInto(Color(), h, s, v)

    fun pillar(x: Float, hue: Float, sliding: Boolean = false, slideTo: Float = 0f, slideRate: Float = 2f): Ob {
        val h = PILLAR_H + rnd.nextFloat() * 0.25f
        val c = if (sliding) hsv(hue + 230f, 0.95f, 1f) else hsv(hue + 185f, 0.85f, 1f)
        return Ob(c, x, h / 2f, 0.75f, ObType.SOLID, 1.5f, h, 0.9f, sliding, slideTo, slideRate)
    }

    /** A solid block sitting ON the ground across every lane — clearly "jump over". */
    fun wall(hue: Float): Ob {
        val w = laneW * 3f + 0.6f; val h = 0.62f
        return Ob(hsv(hue + 140f, 0.9f, 1f), 0f, h / 2f, laneW * 1.5f + 0.3f, ObType.SOLID, w, h, 0.7f)
    }

    /** A wall too tall for a standing jump — bounce off a pad, or come down off a platform. */
    fun tallWall(hue: Float, into: ArrayList<Ob>) {
        val w = laneW * 3f + 0.6f; val h = TALL_H
        into.add(Ob(hsv(hue + 140f, 0.9f, 1f), 0f, h / 2f, laneW * 1.5f + 0.3f, ObType.SOLID, w, h, 0.7f))
        into.add(Ob(hsv(hue + 140f, 0.25f, 1f), 0f, h * 0.72f, 0f, ObType.DECO, w + 0.04f, 0.12f, 0.74f)) // a bright stripe: "this one is tall"
    }

    /** A chunky beam floating well above the ground with a clear gap beneath — unmistakably "roll under". */
    fun over(hue: Float): Ob {
        val w = laneW * 3f + 0.6f; val bottom = 0.78f; val top = 1.5f
        return Ob(hsv(hue + 300f, 0.9f, 1f), 0f, (bottom + top) / 2f, laneW * 1.5f + 0.3f, ObType.SOLID, w, top - bottom, 0.7f)
    }

    fun slider(from: Int, to: Int, hue: Float): Ob =
        pillar(laneX(from), hue, sliding = true, slideTo = laneX(to))

    /** Low jump-wall segment spanning lanes [a]..[b] — same "jump over" language as [wall]. */
    fun wallSeg(a: Int, b: Int, hue: Float): Ob {
        val cx = (laneX(a) + laneX(b)) / 2f
        val w = (b - a) * laneW + laneW * 1.05f; val h = 0.62f
        return Ob(hsv(hue + 140f, 0.9f, 1f), cx, h / 2f, w / 2f - 0.11f, ObType.SOLID, w, h, 0.7f)
    }

    /** Overhead-bar segment spanning lanes [a]..[b] — same "roll under" language as [over]. */
    fun overSeg(a: Int, b: Int, hue: Float): Ob {
        val cx = (laneX(a) + laneX(b)) / 2f
        val w = (b - a) * laneW + laneW * 1.05f; val bottom = 0.78f; val top = 1.5f
        return Ob(hsv(hue + 300f, 0.9f, 1f), cx, (bottom + top) / 2f, w / 2f - 0.11f, ObType.SOLID, w, top - bottom, 0.7f)
    }

    /**
     * A tar pit spanning lanes [a]..[b]: a near-black slab flush with the floor
     * (touching it on the ground kills — any jump clears it) inside a glowing
     * rim so it reads as a hole, not a block.
     */
    fun addTar(a: Int, b: Int, hue: Float, into: ArrayList<Ob>) {
        val cx = (laneX(a) + laneX(b)) / 2f
        val w = (b - a) * laneW + laneW * 1.05f; val h = 0.16f; val d = 1.7f
        into.add(Ob(hsv(hue + 140f, 0.9f, 1f), cx, 0.0f, 0f, ObType.DECO, w + 0.22f, 0.05f, d + 0.22f, pit = true)) // the glowing lip, flush with the road
        into.add(Ob(Color(0.03f, 0.02f, 0.05f, 1f), cx, h / 2f, w / 2f - 0.11f, ObType.SOLID, w, h, d, pit = true))
    }

    /** A piston in lane [l]: pumps out of the floor and sinks back, out of phase with its neighbours. */
    fun piston(l: Int, hue: Float, phase: Float): Ob =
        Ob(hsv(hue + 155f, 0.9f, 1f), laneX(l), 0f, 0.7f, ObType.SOLID, 1.4f, 0.01f, 0.8f, anim = ObAnim.PISTON, phase = phase)

    /** A low beam that glides sideways across every lane. Jump it (or time a gap). */
    fun sweeper(hue: Float, phase: Float): Ob {
        val h = 0.36f
        return Ob(hsv(hue + 120f, 0.9f, 1f), 0f, h / 2f, 0.85f, ObType.SOLID, 1.7f, h, 0.5f, anim = ObAnim.SWEEP, phase = phase)
    }

    /** A block hanging over lane [l] that slams to the floor and lifts again. */
    fun stomper(l: Int, hue: Float, phase: Float): Ob =
        Ob(hsv(hue + 210f, 0.9f, 1f), laneX(l), 1.5f, 0.62f, ObType.SOLID, 1.25f, 1.0f, 0.9f, anim = ObAnim.STOMP, phase = phase)

    /** A block swinging sideways at chest height: roll under it, or step out of its arc. */
    fun pendulum(hue: Float, phase: Float): Ob {
        val bottom = 0.7f; val top = 1.7f
        return Ob(hsv(hue + 260f, 0.85f, 1f), 0f, (bottom + top) / 2f, 0.65f, ObType.SOLID, 1.3f, top - bottom, 0.9f, anim = ObAnim.PENDULUM, phase = phase)
    }

    /** A springy slab in lane [l]: run onto it and it launches you high. */
    fun pad(l: Int, hue: Float): Ob =
        Ob(hsv(hue + 85f, 0.9f, 1f), laneX(l), 0.07f, 0.7f, ObType.PAD, 1.4f, 0.14f, 1.3f)

    /**
     * One platform segment in lane [l]: [len] deep, its FRONT at the row's z, [top]
     * high, with a [ramp]-long rise at the front (0 for a flat continuation of
     * the previous row's platform). Ramps are drawn as steps; the walkable
     * height is linear along them (CubeRun.groundAt).
     */
    fun platform(l: Int, hue: Float, len: Float, ramp: Float): Ob =
        Ob(hsv(hue + 95f, 0.55f, 0.95f), laneX(l), PLAT_TOP / 2f, 0.78f, ObType.PLAT, 1.56f, PLAT_TOP, len, ramp = ramp)

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
            into.add(Ob(hsv(hue + 185f, 0.85f, 1f), cx, PILLAR_H / 2f + 0.1f, w / 2f, ObType.SOLID, w, PILLAR_H + 0.2f, 0.9f))
        }
    }
}
