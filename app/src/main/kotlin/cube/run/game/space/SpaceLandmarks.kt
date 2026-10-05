package cube.run.game.space

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import cube.run.core.Gdx3DGame
import cube.run.core.gfx.FacetShapes
import cube.run.core.hsvInto
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * The journey's landmarks: each comes up out of the dark far ahead when the
 * trip reaches it, drifts closer more slowly than the road (so it feels
 * far, and calm) and slides past on its side, growing as it goes, until it
 * is behind you. Then the next one is already on its way.
 */
class SpaceLandmarks(private val game: Gdx3DGame, private val space: SpaceWorld) {

    private class Live(val plan: SpaceTrip.Landmark) {
        var x = 0f; var y = 0f; var z = SPAWN_Z
        var rate = 0.5f
        var radius = 10f
        val colors = arrayOf(Color(), Color(), Color())
        var bands: FloatArray? = null
        var ring = 0f; var ringTilt = 0f
        val ringColors = arrayOf(Color(), Color())
        var moons = 0
        var tilt = 0f; var spin = 0f
        var travelled = 0f
    }

    private val small = FacetShapes.voxelBall(6)
    private val big = FacetShapes.voxelBall(9)
    private val ringShape = FacetShapes.voxelRing(14, 0.62f, 3)
    private val disc = FacetShapes.voxelRing(16, 0.42f, 3)
    private val halo = FacetShapes.voxelRing(14, 0.82f, 1)
    private val cube = FacetShapes.cube()

    private val live = ArrayList<Live>()
    private var next = 0
    private var trip: SpaceTrip? = null

    private val pal1 = arrayOf(Color())
    private val pal2 = arrayOf(Color(), Color())
    private val black = arrayOf(Color(0.03f, 0.02f, 0.06f, 1f))
    private val hot = arrayOf(Color(), Color())
    private val panel = arrayOf(Color())
    private val rayCol = Color()

    init {
        hsvInto(hot[0], 28f, 0.8f, 1f); hsvInto(hot[1], 330f, 0.7f, 1f)
        hsvInto(panel[0], 214f, 0.72f, 0.96f)
    }

    fun begin(trip: SpaceTrip) { this.trip = trip; live.clear(); next = 0 }

    fun clear() { trip = null; live.clear(); next = 0 }

    fun tick(mv: Float, travelled: Float) {
        val trip = trip ?: return
        while (next < trip.landmarks.size && trip.landmarks[next].at <= travelled) spawn(trip.landmarks[next++])
        var i = live.size - 1
        while (i >= 0) {
            val l = live[i]
            l.z += mv * l.rate
            l.travelled += mv
            val gone = l.z > 30f + l.radius * max(1f, l.ring) || (l.plan.kind == SpaceTrip.SUN && l.travelled > SUN_STAY)
            if (gone) live.removeAt(i)
            i--
        }
    }

    private fun spawn(plan: SpaceTrip.Landmark) {
        val r = Random(plan.seed)
        val l = Live(plan)
        val side = plan.side
        look(l, r)
        when (plan.kind) {
            SpaceTrip.PLANET -> {
                l.radius = 8f + r.nextFloat() * 8f
                l.x = side * (l.radius + 14f + r.nextFloat() * 20f); l.y = -6f + r.nextFloat() * 24f
                l.rate = 0.5f + r.nextFloat() * 0.15f
                l.ring = if (r.nextFloat() < 0.3f) 1.8f + r.nextFloat() * 0.4f else 0f
                l.moons = r.nextInt(3)
            }
            SpaceTrip.RINGED -> {
                l.radius = 13f + r.nextFloat() * 6f
                l.ring = 1.9f + r.nextFloat() * 0.4f
                l.x = side * (l.radius * l.ring + 12f + r.nextFloat() * 8f); l.y = 6f + r.nextFloat() * 14f
                l.rate = 0.42f
                l.moons = 1 + r.nextInt(2)
            }
            SpaceTrip.GIANT -> {
                l.radius = 42f; l.ring = 2f
                l.x = side * 10f; l.y = -64f
                l.rate = 0.3f
            }
            SpaceTrip.MOONS -> {
                l.radius = 3.5f + r.nextFloat() * 1.5f
                l.x = side * (20f + r.nextFloat() * 10f); l.y = 14f + r.nextFloat() * 10f
                l.rate = 0.6f
                l.moons = 4
            }
            SpaceTrip.STATION -> {
                l.radius = 14f
                l.x = side * (26f + r.nextFloat() * 8f); l.y = 8f + r.nextFloat() * 8f
                l.rate = 0.55f
            }
            SpaceTrip.BLACK_HOLE -> {
                l.radius = 8f
                l.x = side * (30f + r.nextFloat() * 10f); l.y = 12f + r.nextFloat() * 10f
                l.rate = 0.4f
            }
            SpaceTrip.SUN -> {
                l.radius = 20f
                l.x = side * (8f + r.nextFloat() * 20f); l.y = -22f
                l.rate = 0.04f
                hsvInto(l.colors[0], 44f + r.nextFloat() * 12f, 0.45f, 1f); hsvInto(l.colors[1], 30f, 0.6f, 1f)
            }
        }
        live.add(l)
    }

    /** Colours, bands, tilt and spin from the landmark's own seed. */
    private fun look(l: Live, r: Random) {
        val look = SpaceTrip.PLANET_LOOKS[r.nextInt(SpaceTrip.PLANET_LOOKS.size)]
        for (k in 0 until 3) hsvInto(l.colors[k], look[k * 3] + (r.nextFloat() - 0.5f) * 14f, look[k * 3 + 1], look[k * 3 + 2])
        l.bands = when (r.nextInt(3)) {
            0 -> null
            1 -> floatArrayOf(-0.55f, -0.2f, 0.15f, 0.5f)
            else -> floatArrayOf(-0.7f, -0.35f, -0.05f, 0.3f, 0.62f)
        }
        hsvInto(l.ringColors[0], look[3] + 10f, 0.35f, 1f); hsvInto(l.ringColors[1], look[6], 0.45f, 0.9f)
        l.ringTilt = 12f + r.nextFloat() * 22f
        l.tilt = (r.nextFloat() - 0.5f) * 40f
        l.spin = (2f + r.nextFloat() * 5f) * (if (r.nextBoolean()) 1f else -1f)
    }

    /** Queue the landmarks into the facet batch. [reach] fades the whole journey in and out with the trip. */
    fun render(time: Float, reach: Float) {
        val fogCol = game.fogColor
        for (l in live) {
            // out of the far dark: fully hazed at the spawn distance, clear 100 units nearer
            val haze = max(((-l.z - 230f) / 100f).coerceIn(0f, 1f), 1f - reach)
            if (haze >= 0.995f && l.plan.kind != SpaceTrip.SUN) continue
            when (l.plan.kind) {
                SpaceTrip.MOONS -> moons(l, time, haze, fogCol)
                SpaceTrip.STATION -> station(l, time, haze, fogCol)
                SpaceTrip.BLACK_HOLE -> blackHole(l, time, haze, fogCol)
                SpaceTrip.SUN -> sun(l, time, max(1f - 0.86f * sunFade(l), 1f - reach))
                else -> planet(l, time, haze, fogCol)
            }
        }
    }

    private fun planet(l: Live, time: Float, haze: Float, fogCol: Color) {
        val r = l.radius
        game.facets.add(if (r > 15f) big else small, l.x, l.y, l.z, r, r, r, time * l.spin, l.tilt, l.tilt * 0.5f, l.colors, haze, fogCol,
            glow = 0.08f, bands = l.bands)
        if (l.ring > 0f) {
            val rr = r * l.ring
            game.facets.add(ringShape, l.x, l.y, l.z, rr, rr, rr, time * 1.5f, l.ringTilt, l.tilt * 0.4f, l.ringColors, haze, fogCol, glow = 0.12f)
        }
        for (m in 0 until l.moons) {
            val ang = time * (0.12f + m * 0.05f) + m * 2.4f
            val mr = r * (2.0f + m * 0.5f)
            val ms = r * (0.13f + m * 0.04f)
            game.facets.add(cube, l.x + cos(ang) * mr, l.y + sin(ang) * mr * 0.35f, l.z + sin(ang) * mr * 0.6f, ms, ms, ms,
                time * 12f + m * 40f, 25f, 0f, l.ringColors, haze, fogCol, glow = 0.05f)
        }
    }

    /** A family of moons: little worlds of different colours tumbling round each other. */
    private fun moons(l: Live, time: Float, haze: Float, fogCol: Color) {
        for (m in 0 until l.moons) {
            val ang = time * 0.25f + m * 1.57f
            val d = l.radius * (1.8f + m * 0.7f)
            val s = l.radius * (1f - m * 0.18f)
            pal2[0].set(l.colors[m % 3]); pal2[1].set(l.ringColors[m % 2])
            game.facets.add(small, l.x + cos(ang) * d, l.y + sin(ang * 1.3f) * d * 0.5f, l.z + sin(ang) * d * 0.6f, s, s, s,
                time * (10f + m * 6f), l.tilt + m * 20f, 0f, pal2, haze, fogCol, glow = 0.06f, bands = BANDS2)
        }
    }

    /** A space station: a hub, a turning ring of modules, two solar wings, blinking beacons. */
    private fun station(l: Live, time: Float, haze: Float, fogCol: Color) {
        pal1[0].set(l.colors[0]).lerp(Color.WHITE, 0.55f)
        game.facets.add(cube, l.x, l.y, l.z, 2.6f, 2.6f, 2.6f, time * 8f, 15f, 0f, pal1, haze, fogCol, glow = 0.05f)
        val spinRing = time * 0.35f
        for (k in 0 until MODULES) {
            val a = spinRing + k * 6.2832f / MODULES
            val cx = cos(a) * 12f; val cy = sin(a) * 12f
            pal1[0].set(if (k % 2 == 0) l.colors[1] else l.colors[2])
            game.facets.add(cube, l.x + cx, l.y + cy * 0.9f, l.z + cy * 0.35f, 1.6f, 1.6f, 1.6f, a * 57.3f, 20f, 0f, pal1, haze, fogCol, glow = 0.05f)
        }
        for (k in -1..1 step 2) { // spokes and wings
            pal1[0].set(l.colors[0]).lerp(Color.WHITE, 0.3f)
            game.facets.add(cube, l.x + k * 6f, l.y, l.z, 3.4f, 0.5f, 0.5f, 0f, 15f, 0f, pal1, haze, fogCol)
            game.facets.add(cube, l.x + k * 17f, l.y, l.z, 4.5f, 0.25f, 3f, 0f, 15f + sin(time * 0.4f) * 8f, 0f, panel, haze, fogCol, glow = 0.1f)
        }
        val blink = sin(time * 4f) > 0f
        pal1[0].set(if (blink) space.neon else Color.WHITE)
        for (k in -1..1 step 2) game.facets.add(cube, l.x + k * 21.6f, l.y, l.z, 0.6f, 0.6f, 0.6f, time * 90f, 0f, 0f, pal1, haze, fogCol, glow = 1f)
    }

    /** A black hole: a ball of nothing, its accretion ring burning round it, a bright ring of bent light hugging it. */
    private fun blackHole(l: Live, time: Float, haze: Float, fogCol: Color) {
        val r = l.radius
        game.facets.add(disc, l.x, l.y, l.z, r * 3f, r * 3f, r * 3f, time * 40f, 72f, l.tilt * 0.3f, hot, haze, fogCol, glow = 1f)
        game.facets.add(small, l.x, l.y, l.z, r, r, r, time * 5f, 0f, 0f, black, haze, fogCol)
        pal1[0].set(1f, 0.92f, 0.75f, 1f)
        game.facets.add(halo, l.x, l.y, l.z + 0.5f, r * 1.3f, r * 1.3f, r * 1.3f, time * 25f, 90f, 0f, pal1, haze, fogCol, glow = 1f)
    }

    /** The sun: a glowing ball climbing out of the haze (its rays are drawn in [renderShapes]). */
    private fun sun(l: Live, time: Float, haze: Float) {
        val y = sunY(l)
        game.facets.add(big, l.x, y, l.z, l.radius, l.radius, l.radius, time * 3f, 0f, 0f, l.colors, haze, game.fogColor, glow = 1f)
    }

    /** A sun barely moves closer: it fades in over the first stretch it is up, and out again before it goes. */
    private fun sunFade(l: Live): Float =
        (l.travelled / 60f).coerceIn(0f, 1f) * (1f - ((l.travelled - SUN_STAY * 0.8f) / (SUN_STAY * 0.2f)).coerceIn(0f, 1f))

    private fun sunY(l: Live) = l.y + 32f * (l.travelled / SUN_STAY).coerceIn(0f, 1f).let { it * (2f - it) }

    /** Blended extras: the sun's rays. Draw with the world's bend switched off (the sky does not bend). */
    fun renderShapes(shapes: ShapeRenderer, time: Float) {
        val a = space.blend * space.blend
        for (l in live) {
            if (l.plan.kind != SpaceTrip.SUN) continue
            val fade = sunFade(l)
            rayCol.set(l.colors[0])
            game.sunburst(shapes, l.x, sunY(l), l.z - l.radius, l.radius * 3.2f, 14, time * 4f, rayCol, 0.24f * a * fade)
        }
    }

    private companion object {
        const val SPAWN_Z = -330f
        const val MODULES = 10
        /** How long (units flown) a sun stays up before it sinks back into the haze. */
        const val SUN_STAY = 380f
        val BANDS2 = floatArrayOf(-0.25f, 0.3f)
    }
}
