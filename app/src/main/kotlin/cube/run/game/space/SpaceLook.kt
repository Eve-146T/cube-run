package cube.run.game.space

import com.badlogic.gdx.graphics.Color
import cube.run.core.Gdx3DGame
import cube.run.core.gfx.FacetShapes
import cube.run.core.hsvInto
import cube.run.game.track.ObAnim
import cube.run.game.track.ObCue
import cube.run.game.track.ObShape
import cube.run.game.track.ObType
import cube.run.game.track.Ob
import cube.run.game.track.Row
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * How the road's obstacles look in Outer Space, built from a few chunky
 * boxes like everything else in the game. Every one keeps its exact
 * collision box and its colour (the colour still says the verb); only the
 * thing changes: pillars become tumbling asteroids, low walls energy fences
 * between beacons, overhead bars station girders, tar pits black holes, a
 * full-width pit a rift in the floating road, pads gravity rings, platforms
 * moon rock, pistons plasma vents, sweepers solar drones, pendulums
 * tethered satellites. Meteors fall out of the sky onto their landing
 * marks; station hulls carry a neon band and lit windows.
 */
class SpaceLook(private val game: Gdx3DGame, private val space: SpaceWorld) {

    private val clumps = Array(4) { FacetShapes.clump(it) }
    private val cube = FacetShapes.cube()
    private val frame = FacetShapes.frame(0.3f)

    private val pal2 = arrayOf(Color(), Color())
    private val pal1 = arrayOf(Color())
    private val glowPal = arrayOf(Color())
    private val panel = Color()
    private val white = Color(1f, 1f, 1f, 1f)
    private val voidCol = Color()
    private val ember = Color()
    private val a = Color()
    private val b = Color()

    init {
        hsvInto(panel, 214f, 0.72f, 0.96f)
        hsvInto(ember, 30f, 0.85f, 1f)
    }

    /** Draw [row]'s obstacles in the space look. [fog] is the row's haze, [p] its stream-in. */
    fun renderRow(row: Row, fog: Float, time: Float, p: Float) {
        voidCol.set(space.skyTop).mul(0.45f, 0.45f, 0.55f, 1f) // a rift shows the deep sky, darker than any road
        val phase = row.visualPhase
        for ((i, ob) in row.obs.withIndex()) {
            val z = row.z
            when {
                ob.type == ObType.PLAT -> moonSlab(ob, z, fog, p, phase)
                ob.type == ObType.PAD -> gravityRing(ob, z, fog, time, p)
                ob.pit -> if (ob.sx > 4f) rift(ob, row, fog, time, p) else blackHole(ob, z, fog, time, p, phase)
                ob.type == ObType.DECO -> {} // painted stripes belong to the ground's walls
                ob.shape == ObShape.METEOR -> meteor(ob, z, fog, time, p, phase + i)
                ob.shape == ObShape.HULL -> hull(ob, z, fog, time, p)
                ob.anim == ObAnim.PISTON -> plasmaVent(ob, z, fog)
                ob.anim == ObAnim.SWEEP -> drone(ob, z, fog, time, p)
                ob.anim == ObAnim.STOMP -> stomper(ob, z, fog, time, p, phase)
                ob.anim == ObAnim.PENDULUM -> satellite(ob, z, fog, time, p)
                ob.cue == ObCue.JUMP -> fence(ob, z, fog, time, p, phase)
                ob.cue == ObCue.DUCK -> girder(ob, z, fog, time, p)
                else -> asteroid(ob, z, fog, time, p, phase + i * 1.7f)
            }
        }
    }

    /** The obstacle's own colour, its small cubes a lighter candy shade. */
    private fun clumpColours(ob: Ob) {
        pal2[0].set(ob.col)
        pal2[1].set(ob.col).lerp(white, 0.4f)
    }

    private fun blink(time: Float, rate: Float, offset: Float): Color = if (sin(time * rate + offset) > 0f) space.neon else white

    /** A pillar: a big tumbling cube asteroid filling its box, two pebbles in orbit (two asteroids for a double-width block). */
    private fun asteroid(ob: Ob, z: Float, fog: Float, time: Float, p: Float, phase: Float) {
        clumpColours(ob)
        val h = ob.sy * p
        val cy = ob.bottom + h / 2f
        val wide = ob.halfW > 1.2f
        val n = if (wide) 2 else 1
        val w = ob.sx / n
        for (k in 0 until n) {
            val x = ob.x + if (wide) (k - 0.5f) * w else 0f
            game.facets.add(clumps[(abs(phase * 7f).toInt() + k) % clumps.size], x, cy, z, w * 0.5f / 0.8f * p, h * 0.5f / 0.72f, max(ob.sz, 1.1f) * 0.5f / 0.8f * p,
                time * 22f + phase * 90f + k * 70f, 8f * sin(time * 0.9f + phase), 0f, pal2, fog, game.fogColor)
        }
        if (p > 0.9f) for (k in 0 until 2) { // pebbles circling it
            val ang = time * (1.4f + k * 0.3f) + k * 3.1f + phase
            val r = ob.halfW * 0.95f + 0.35f
            game.facets.add(cube, ob.x + cos(ang) * r, cy + 0.5f * sin(ang * 0.7f + k), z + sin(ang) * r * 0.6f, 0.16f, 0.16f, 0.16f,
                time * 120f, time * 80f, 0f, pal2, fog, game.fogColor)
        }
    }

    /**
     * A meteor: falls out of the sky on a slant, trailing fire, onto the frame
     * that marked its spot; then lies there, still glowing. Lands well before
     * it can matter ([METEOR_LAND_Z]); the collision is the landed boulder.
     */
    private fun meteor(ob: Ob, z: Float, fog: Float, time: Float, p: Float, phase: Float) {
        pal2[0].set(ob.col); pal2[1].set(ember)
        val fall = ((METEOR_LAND_Z - z) / (METEOR_LAND_Z - METEOR_FALL_START)).coerceIn(0f, 1f)
        val side = if (ob.x >= 0f) 1f else -1f
        val x = ob.x + side * fall * 9f
        val y = ob.cy + fall * 36f
        if (fall > 0f) {
            // the landing mark: a square on the road that tightens as the rock comes down
            glowPal[0].set(space.neon)
            val rr = 0.6f + 0.35f * fall + 0.05f * sin(time * 9f)
            game.facets.add(frame, ob.x, 0.03f, z, rr, 0.05f, rr, time * 60f, 0f, 0f, glowPal, fog, game.fogColor, glow = 1f)
            // the fire trail, back up along the path
            glowPal[0].set(ember)
            for (k in 1..4) {
                val t = k * 0.9f
                val ts = 0.32f * (1f - k / 5.5f)
                game.facets.add(cube, x + side * t * 0.25f, y + t, z, ts, ts, ts, k * 50f + time * 200f, 30f, 0f,
                    glowPal, fog, game.fogColor, glow = 1f)
            }
        }
        val spin = if (fall > 0f) time * 260f else phase * 40f
        game.facets.add(clumps[abs(phase * 5f).toInt() % clumps.size], x, y, z, 0.62f * p, ob.sy * 0.5f * p, 0.58f * p,
            spin, if (fall > 0f) time * 190f else 10f, 0f, pal2, fog, game.fogColor, glow = if (fall > 0f) 0.35f else 0.12f)
    }

    /** A low wall: an energy fence between two posts with beacons on top: bright and unmistakably "jump". */
    private fun fence(ob: Ob, z: Float, fog: Float, time: Float, p: Float, phase: Float) {
        val h = ob.sy * p
        val w = ob.sx * (0.4f + 0.6f * p)
        a.set(ob.col).lerp(white, 0.15f)
        b.set(ob.col).mul(0.78f, 0.78f, 0.82f, 1f)
        game.worldBox(ob.x, h * 0.45f, z, w - 0.3f, h * 0.9f, 0.2f, a, fog)                        // the field
        game.worldBox(ob.x, h - 0.1f, z, w, 0.2f, 0.3f, b, fog)                                    // the top rail
        val beacon = blink(time, 5f, phase)
        for (k in -1..1 step 2) {
            val x = ob.x + k * (w / 2f - 0.15f)
            game.worldBox(x, (h + 0.2f) / 2f, z, 0.3f, h + 0.2f, 0.34f, b, fog)                   // the posts
            game.worldBox(x, h + 0.36f, z, 0.26f, 0.26f, 0.26f, beacon, fog)                     // the beacons
        }
    }

    /** An overhead bar: a chunky station girder with a lit stripe and a lamp at each end. */
    private fun girder(ob: Ob, z: Float, fog: Float, time: Float, p: Float) {
        val w = ob.sx * (0.4f + 0.6f * p)
        val hh = ob.sy * p
        a.set(ob.col)
        b.set(ob.col).lerp(white, 0.45f)
        game.worldBox(ob.x, ob.cy, z, w, hh, ob.sz, a, fog)                                         // the beam
        game.worldBox(ob.x, ob.cy, z + ob.sz / 2f + 0.02f, w - 0.5f, hh * 0.35f, 0.06f, b, fog)    // the lit stripe
        val lamp = blink(time, 4f, z)
        for (k in -1..1 step 2) game.worldBox(ob.x + k * w / 2f, ob.cy, z, 0.36f, hh + 0.16f, 0.36f, lamp, fog)
    }

    /** A full-width pit: a gap in the floating road, deep space showing through, neon lips at its ends. */
    private fun rift(ob: Ob, row: Row, fog: Float, time: Float, p: Float) {
        val w = ob.sx * p
        if (ob.type == ObType.DECO) { // the lip: only along the outer edges, and across the ends of the chasm
            a.set(ob.col).lerp(white, 0.15f)
            val d = ob.sz
            game.worldBox(ob.x - w / 2f, 0.02f, row.z, 0.2f, 0.14f, d, a, fog)
            game.worldBox(ob.x + w / 2f, 0.02f, row.z, 0.2f, 0.14f, d, a, fog)
            if (!row.scoreless) game.worldBox(ob.x, 0.02f, row.z + d / 2f, w, 0.14f, 0.2f, a, fog)
            if (row.riftEnd) game.worldBox(ob.x, 0.02f, row.z - d / 2f, w, 0.14f, 0.2f, a, fog)
            return
        }
        game.worldBox(ob.x, 0f, row.z, w - 0.1f, 0.05f, ob.sz + 0.02f, voidCol, fog)             // the hole: deep sky
        pal1[0].set(space.star)
        for (k in 0 until 4) { // far stars seen through it
            val sx = ob.x + ((row.visualPhase * 13f + k * 1.37f) % 1f - 0.5f) * (w - 0.6f)
            val sz = row.z + ((row.visualPhase * 7f + k * 0.61f) % 1f - 0.5f) * (ob.sz - 0.3f)
            val s = 0.09f + 0.02f * sin(time * 3f + k * 2f)
            game.facets.add(cube, sx, 0.04f, sz, s, s * 0.4f, s, time * 40f + k * 30f, 0f, 0f, pal1, fog, game.fogColor, glow = 1f)
        }
    }

    /** A tar pit in a lane: a little black hole, a glowing square spinning round it, cubes spiralling in. */
    private fun blackHole(ob: Ob, z: Float, fog: Float, time: Float, p: Float, phase: Float) {
        val r = ob.sx * 0.5f * p
        if (ob.type == ObType.DECO) { // the lip: the glowing accretion square
            glowPal[0].set(ob.col).lerp(white, 0.2f)
            game.facets.add(frame, ob.x, 0.03f, z, r * 0.9f, 0.05f, ob.sz * 0.46f * p, time * 90f, 0f, 0f, glowPal, fog, game.fogColor, glow = 1f)
            return
        }
        a.set(0.08f, 0.04f, 0.14f, 1f)
        game.worldBox(ob.x, 0f, z, r * 1.3f, 0.04f, ob.sz * 0.66f * p, a, fog)
        glowPal[0].set(space.neonSoft)
        for (k in 0 until 3) { // cubes falling in
            val u = ((time * 0.6f + k / 3f + phase) % 1f + 1f) % 1f
            val rr = r * 0.95f * (1f - u)
            val ang = time * 3f + k * 2.1f + u * 6f
            val s = 0.12f * (1f - u * 0.5f)
            game.facets.add(cube, ob.x + cos(ang) * rr, 0.1f + 0.1f * (1f - u), z + sin(ang) * rr * 0.9f, s, s, s,
                time * 200f, 45f, 0f, glowPal, fog, game.fogColor, glow = 1f)
        }
    }

    /** A bounce pad: a glowing square on the road with a second one bobbing above it, cubes lifting off. */
    private fun gravityRing(ob: Ob, z: Float, fog: Float, time: Float, p: Float) {
        val r = 0.72f * (0.5f + 0.5f * p)
        glowPal[0].set(space.neon)
        game.facets.add(frame, ob.x, 0.05f, z, r, 0.06f, r, time * 120f, 0f, 0f, glowPal, fog, game.fogColor, glow = 1f)
        a.set(space.neonSoft)
        game.worldBox(ob.x, 0.02f, z, r * 0.9f, 0.04f, r * 0.9f, a, fog)
        glowPal[0].set(ob.col).lerp(white, 0.3f)
        val bob = 0.45f + 0.15f * sin(time * 3f)
        game.facets.add(frame, ob.x, bob, z, r * 0.78f, 0.06f, r * 0.78f, -time * 160f, 0f, 0f, glowPal, fog, game.fogColor, glow = 1f)
        for (k in 0 until 2) {
            val u = (time * 0.9f + k * 0.5f) % 1f
            val s = 0.13f * (1f - u * 0.6f)
            game.facets.add(cube, ob.x + cos(k * 3.1f + time) * r * 0.4f, 0.15f + u * 1.6f, z + sin(k * 3.1f + time) * r * 0.4f,
                s, s, s, time * 90f, 45f, 0f, glowPal, fog, game.fogColor, glow = 1f)
        }
    }

    /** A platform: a slab of candy moon rock with square craters on top and a neon edge along its roof. */
    private fun moonSlab(ob: Ob, front: Float, fog: Float, p: Float, phase: Float) {
        a.set(ob.col).lerp(space.rock, 0.25f)
        b.set(a).mul(0.84f, 0.84f, 0.88f, 1f)
        val top = ob.clear * p
        val bodyLen = ob.sz - ob.ramp
        val cz = front - ob.ramp - bodyLen / 2f
        game.worldBox(ob.x, top / 2f, cz, ob.sx, top, bodyLen, a, fog)
        game.worldBox(ob.x - ob.sx / 2f + 0.08f, top, cz, 0.16f, 0.08f, bodyLen, space.neon, fog)
        game.worldBox(ob.x + ob.sx / 2f - 0.08f, top, cz, 0.16f, 0.08f, bodyLen, space.neon, fog)
        for (k in 0 until 2) { // craters
            val u = ((phase * 3.7f + k * 0.37f) % 1f + 1f) % 1f
            val v = ((phase * 5.3f + k * 0.53f) % 1f + 1f) % 1f
            game.worldBox(ob.x + (u - 0.5f) * (ob.sx - 0.8f), top + 0.01f, cz + (v - 0.5f) * (bodyLen - 0.8f), 0.5f, 0.04f, 0.5f, b, fog)
        }
        if (ob.ramp > 0f) { // the ramp as rock steps
            val n = 3
            val d = ob.ramp / n
            for (i in 0 until n) {
                val h = top * (i + 1) / n
                game.worldBox(ob.x, h / 2f, front - d * (i + 0.5f), ob.sx, h, d, if (i % 2 == 0) a else b, fog)
            }
        }
    }

    /** A piston: a plasma vent: a grate in the road that spits a glowing column. */
    private fun plasmaVent(ob: Ob, z: Float, fog: Float) {
        a.set(ob.col).mul(0.6f, 0.6f, 0.68f, 1f)
        game.worldBox(ob.x, 0.01f, z, ob.sx, 0.03f, ob.sz, a, fog)
        game.worldBox(ob.x, 0.025f, z, ob.sx * 0.7f, 0.03f, ob.sz * 0.7f, space.neonSoft, fog)
        if (ob.sy < 0.03f) return
        a.set(ob.col).lerp(white, 0.25f)
        game.worldBox(ob.x, ob.cy, z, ob.sx * 0.85f, ob.sy, ob.sz * 0.85f, a, fog)
        game.worldBox(ob.x, ob.cy, z, ob.sx * 0.45f, ob.sy + 0.04f, ob.sz * 0.45f, white, fog)
    }

    /** A sweeper: a solar drone skimming the road, its panels spread across its box. */
    private fun drone(ob: Ob, z: Float, fog: Float, time: Float, p: Float) {
        val w = ob.sx * p
        game.worldBox(ob.x, ob.cy, z, 0.5f, ob.sy, 0.5f, ob.col, fog)                               // the body
        game.worldBox(ob.x, ob.cy, z, w, 0.12f, ob.sz * 0.9f, panel, fog)                         // the panels
        game.worldBox(ob.x, ob.top + 0.1f, z, 0.2f, 0.2f, 0.2f, blink(time, 7f, z), fog)
    }

    /** A stomper: a cube boulder hung in the dark that drops onto the road, burning as it falls. */
    private fun stomper(ob: Ob, z: Float, fog: Float, time: Float, p: Float, phase: Float) {
        clumpColours(ob)
        game.facets.add(clumps[abs(phase * 3f).toInt() % clumps.size], ob.x, ob.cy, z, ob.sx * 0.5f / 0.8f * p, ob.sy * 0.5f / 0.72f * p,
            ob.sz * 0.5f / 0.8f * p, time * 30f + phase * 50f, 0f, 0f, pal2, fog, game.fogColor)
        val falling = cos(time * 2.6f + ob.phase) < 0f && ob.bottom > 0.25f
        if (falling) {
            glowPal[0].set(ember)
            for (k in 1..2) {
                val s = 0.22f * (1f - k / 3.5f)
                game.facets.add(cube, ob.x, ob.top + k * 0.36f, z, s, s, s, time * 300f, 30f, 0f, glowPal, fog, game.fogColor, glow = 1f)
            }
        }
    }

    /** A pendulum: a satellite swinging on its tether from a station beam overhead. */
    private fun satellite(ob: Ob, z: Float, fog: Float, time: Float, p: Float) {
        a.set(ob.col)
        b.set(ob.col).mul(0.78f, 0.78f, 0.82f, 1f)
        game.worldBox(ob.x, ob.cy, z, ob.sx * 0.5f * p, ob.sy * p, ob.sz * 0.7f, a, fog)            // the body
        game.worldBox(ob.x, ob.cy, z, ob.sx * p, ob.sy * 0.55f * p, 0.12f, panel, fog)             // the wings
        game.worldBox(ob.x * 0.5f, ob.top + 1.1f, z, abs(ob.x) + 0.16f, 0.16f, 0.16f, b, fog)     // the tether
        game.worldBox(0f, ob.top + 2.2f, z, 6.4f * p, 0.3f, 0.36f, b, fog)                         // the beam
        game.worldBox(ob.x, ob.top + 0.1f, z, 0.2f, 0.2f, 0.2f, blink(time, 6f, 0f), fog)
    }

    /** A station hull: one big block, a neon band along its top, three lit windows and a beacon. */
    private fun hull(ob: Ob, z: Float, fog: Float, time: Float, p: Float) {
        val h = ob.sy * p
        val w = ob.sx * (0.4f + 0.6f * p)
        val face = z + ob.sz / 2f + 0.02f
        game.worldBox(ob.x, h / 2f, z, w, h, ob.sz, ob.col, fog)
        game.worldBox(ob.x, h - 0.14f, face, w, 0.28f, 0.06f, space.neon, fog)
        for (k in 0 until 3) game.worldBox(ob.x - w / 2f + w * (k + 0.5f) / 3f, h * 0.55f, face, w / 5f, h * 0.24f, 0.06f, space.neonSoft, fog)
        b.set(ob.col).mul(0.78f, 0.78f, 0.82f, 1f)
        game.worldBox(ob.x, h + 0.3f, z, 0.16f, 0.6f, 0.16f, b, fog)
        game.worldBox(ob.x, h + 0.7f, z, 0.28f, 0.28f, 0.28f, blink(time, 3f, 0f), fog)
    }

    companion object {
        /** Meteors start falling when their row is this far ahead … */
        const val METEOR_FALL_START = -64f
        /** … and are down by here, long before they can matter. */
        const val METEOR_LAND_Z = -18f
    }
}
