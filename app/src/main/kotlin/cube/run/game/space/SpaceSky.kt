package cube.run.game.space

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import cube.run.core.Gdx3DGame
import cube.run.core.SoundFx
import cube.run.core.gfx.FacetShape
import cube.run.core.gfx.FacetShapes
import cube.run.game.world.Fog
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Everything around the floating road in Outer Space, in two speeds at once.
 * Far away it is calm: planets that barely move, slowly turning, ringed and
 * mooned; a sky full of stars. Close by it is fast: asteroids tumbling past
 * the road, stars stretched into warp streaks that outrun the world. On top
 * the trip's set piece: a giant planet sliding past beneath you, an asteroid
 * belt, comets, or a sunrise.
 */
class SpaceSky(private val game: Gdx3DGame, private val space: SpaceWorld) {

    private class Rock(var x: Float, var y: Float, var z: Float, var size: Float, var yaw: Float, var pitch: Float,
                       var spinY: Float, var spinX: Float, var variant: Int, var whooshed: Boolean = false)
    private class Streak(var x: Float, var y: Float, var z: Float, var len: Float)
    private class Comet(var x: Float, var y: Float, var z: Float, val vx: Float, val vy: Float, val vz: Float, var life: Float)

    private val planetBody = FacetShapes.ball(2)
    private val moonBody = FacetShapes.ball(1, toneSeed = 11)
    private val ringShape = FacetShapes.ring(40, 0.62f, 3)
    private val starShape = FacetShapes.star()
    private val glint = FacetShapes.glint()
    private val rocksNear = Array(4) { FacetShapes.rock(it) }
    private val rocksFar = Array(3) { FacetShapes.rock(it, detail = 0) }

    private val rocks = ArrayList<Rock>()
    private val streaks = ArrayList<Streak>()
    private val comets = ArrayList<Comet>()
    private var stars = FloatArray(0)           // x, y, z, size, phase per star
    private var rnd = Random(1)
    private var trip: SpaceTrip? = null
    private var nextComet = 0f
    private var lastWhoosh = -9f

    private val one = arrayOf(Color())
    private val rockPal = arrayOf(Color(), Color())
    private val cometPal = arrayOf(Color(1f, 1f, 1f, 1f))
    private val tailPal = arrayOf(Color())
    private val streakCol = Color()
    private val tmp = Color()
    private val sunRay = Color()

    fun begin(trip: SpaceTrip) {
        this.trip = trip
        rnd = Random(trip.rockSeed)
        rocks.clear(); streaks.clear(); comets.clear()
        val n = (ROCKS * trip.asteroidDensity).toInt()
        repeat(n) { rocks.add(Rock(0f, 0f, -rnd.nextFloat() * 120f + 10f, 0f, 0f, 0f, 0f, 0f, 0).also { seedRock(it) }) }
        repeat(STREAKS) { streaks.add(Streak(0f, 0f, -rnd.nextFloat() * 110f + 8f, 0f).also { seedStreak(it) }) }
        val sr = Random(trip.starSeed)
        stars = FloatArray(STARS * 5)
        for (i in 0 until STARS) {
            // a shell around the camera, wider than the view so a lean never shows its edge, below the road too
            val az = (sr.nextFloat() - 0.5f) * 84f * DEG
            val el = (-52f + sr.nextFloat() * 86f) * DEG
            val d = 190f + sr.nextFloat() * 70f
            stars[i * 5] = d * sin(az) * cos(el)
            stars[i * 5 + 1] = 3f + d * sin(el)
            stars[i * 5 + 2] = 6f - d * cos(az) * cos(el)
            stars[i * 5 + 3] = if (sr.nextFloat() < 0.12f) 1.25f + sr.nextFloat() * 0.6f else 0.45f + sr.nextFloat() * 0.55f
            stars[i * 5 + 4] = sr.nextFloat() * 6.28f
        }
        nextComet = 1.5f
    }

    fun clear() { trip = null; rocks.clear(); streaks.clear(); comets.clear() }

    /** The belt set piece crowds the asteroids in through the middle of the trip. */
    private fun crowd(): Float {
        val t = trip ?: return 1f
        if (t.setPiece != SpaceTrip.BELT) return 1f
        val p = (space.travelled / TRIP_LENGTH).coerceIn(0f, 1f)
        return 1f + 2.2f * sin(p * PI.toFloat()).let { it * it }
    }

    private fun seedRock(r: Rock) {
        val crowd = crowd()
        if (rnd.nextFloat() < 0.3f) { // beneath the road: you are flying over them
            r.x = (rnd.nextFloat() - 0.5f) * 10f
            r.y = -4f - rnd.nextFloat() * 9f
        } else {
            val side = if (rnd.nextBoolean()) -1f else 1f
            val near = rnd.nextFloat() < 0.25f * crowd // some skim right past the road edge
            r.x = side * (if (near) 4.4f + rnd.nextFloat() * 2.5f else 7f + rnd.nextFloat() * 16f)
            r.y = -7f + rnd.nextFloat() * (if (near) 9f else 17f)
        }
        r.size = (0.35f + rnd.nextFloat() * rnd.nextFloat() * 2.2f) * (if (abs(r.x) < 7f && r.y > -4f) 0.75f else 1f)
        r.yaw = rnd.nextFloat() * 360f; r.pitch = rnd.nextFloat() * 360f
        r.spinY = (rnd.nextFloat() - 0.5f) * 70f; r.spinX = (rnd.nextFloat() - 0.5f) * 50f
        r.variant = rnd.nextInt(4)
        r.whooshed = false
    }

    private fun seedStreak(s: Streak) {
        val a = rnd.nextFloat() * 6.2832f
        val r = 3.6f + rnd.nextFloat() * 9f
        s.x = cos(a) * r * 1.3f
        s.y = 2f + sin(a) * r
        if (abs(s.x) < 3.2f && s.y > -0.6f) s.y = -0.6f - rnd.nextFloat() * 4f // never across the road itself
        s.len = 0.6f + rnd.nextFloat() * 0.8f
    }

    fun tick(dt: Float, mv: Float, time: Float) {
        val trip = trip ?: return
        val crowd = crowd()
        val target = (ROCKS * trip.asteroidDensity * crowd).toInt().coerceAtMost(MAX_ROCKS)
        while (rocks.size < target) rocks.add(Rock(0f, 0f, -110f - rnd.nextFloat() * 20f, 0f, 0f, 0f, 0f, 0f, 0).also { seedRock(it) })
        var i = rocks.size - 1
        while (i >= 0) {
            val r = rocks[i]
            r.z += mv
            r.yaw += r.spinY * dt; r.pitch += r.spinX * dt
            if (!r.whooshed && r.z > -2f && r.size > 0.9f && abs(r.x) < 8f && r.y > -5f) {
                r.whooshed = true
                if (time - lastWhoosh > 1.1f) { lastWhoosh = time; SoundFx.play("flyby", rate = 0.85f + rnd.nextFloat() * 0.3f, vol = 0.45f) }
            }
            if (r.z > 14f) {
                if (rocks.size > target) rocks.removeAt(i)
                else { r.z = -110f - rnd.nextFloat() * 20f; seedRock(r) }
            }
            i--
        }
        val streakSpeed = 1.9f + space.warp * 3f
        for (s in streaks) {
            s.z += mv * streakSpeed
            if (s.z > 10f) { s.z = -100f - rnd.nextFloat() * 20f; seedStreak(s) }
        }
        val progress = (space.travelled / TRIP_LENGTH).coerceIn(0f, 1f)
        for (p in trip.planets) { // the far worlds close in, very slowly; a fly-by slides past, a sun climbs
            val min = p.radius * max(1f, p.ring) + 125f
            if (p.distance > min) p.distance = max(min, p.distance - mv * p.approach)
            p.follow(progress)
        }
        if (trip.setPiece == SpaceTrip.COMETS) {
            nextComet -= dt
            if (nextComet <= 0f) { nextComet = 5f + rnd.nextFloat() * 4f; launchComet() }
        }
        var c = comets.size - 1
        while (c >= 0) {
            val k = comets[c]
            k.x += k.vx * dt; k.y += k.vy * dt; k.z += k.vz * dt; k.life -= dt
            if (k.life <= 0f) comets.removeAt(c)
            c--
        }
    }

    private fun launchComet() {
        val side = if (rnd.nextBoolean()) 1f else -1f
        val z = -130f - rnd.nextFloat() * 50f
        comets.add(Comet(side * 90f, 30f + rnd.nextFloat() * 25f, z, -side * (48f + rnd.nextFloat() * 14f), -6f - rnd.nextFloat() * 6f, 9f, 4.2f))
        SoundFx.play("flyby", rate = 0.6f, vol = 0.3f)
    }

    /** Queue the backdrop (after the road, so the batch never drops gameplay for scenery). */
    fun render(time: Float, speedK: Float) {
        val trip = trip ?: return
        val fogCol = game.fogColor
        val a = space.blend
        if (a <= 0.01f) return
        // A fade in or out is a fly-through: things fly in from the far haze rather than fading (opaque only).
        val reach = a * a
        renderStars(time, reach)
        for (p in trip.planets) renderPlanet(p, time, fogCol, reach)
        renderRocks(fogCol, reach)
        for (k in comets) renderComet(k, time, fogCol)
        renderStreaks(speedK, reach)
    }

    private fun renderStars(time: Float, reach: Float) {
        one[0].set(space.star)
        val fog = 1f - reach
        for (i in 0 until STARS) {
            val s = stars[i * 5 + 3] * (0.72f + 0.28f * sin(time * 2.1f + stars[i * 5 + 4] * 3f))
            game.facets.add(glint, stars[i * 5], stars[i * 5 + 1], stars[i * 5 + 2], s, s, s,
                0f, 0f, 0f, one, fog, game.fogColor, glow = 1f)
        }
    }

    private fun renderPlanet(p: SpaceTrip.Planet, time: Float, fogCol: Color, reach: Float) {
        val x = p.x; val y = p.y + 3f; val z = p.z + 6f
        val r = p.radius
        val haze = max(0.14f * (1f - p.glow * 0.8f), 1f - reach) // a sun shines through the haze, but still fades out with the trip
        game.facets.add(planetBody, x, y, z, r, r, r, time * p.spin, p.tilt, p.tilt * 0.5f, p.colors, haze, fogCol,
            glow = 0.08f + p.glow, bands = p.bands)
        if (p.ring > 0f) {
            val rr = r * p.ring
            game.facets.add(ringShape, x, y, z, rr, rr, rr, time * 1.5f, p.ringTilt, p.tilt * 0.4f, p.ringColors, haze, fogCol, glow = 0.12f)
        }
        for (m in 0 until p.moons) {
            val ang = time * (0.09f + m * 0.05f) + m * 2.4f
            val mr = r * (2.0f + m * 0.5f)
            val ms = r * (0.16f + m * 0.05f)
            game.facets.add(moonBody, x + cos(ang) * mr, y + sin(ang) * mr * 0.35f, z + sin(ang) * mr * 0.6f, ms, ms, ms,
                time * 12f, 0f, 0f, p.ringColors, haze, fogCol, glow = 0.05f)
        }
    }

    private fun renderRocks(fogCol: Color, reach: Float) {
        rockPal[0].set(space.rock); rockPal[1].set(space.rockDark)
        for (r in rocks) {
            val fog = max(Fog.at(r.z), 1f - reach)
            if (fog >= 0.995f) continue
            val shape: FacetShape = if (r.z < -45f || r.size < 0.7f || abs(r.x) > 12f) rocksFar[r.variant % rocksFar.size] else rocksNear[r.variant]
            game.facets.add(shape, r.x, r.y, r.z, r.size, r.size * 0.85f, r.size, r.yaw, r.pitch, 0f, rockPal, fog, fogCol)
        }
    }

    private fun renderComet(k: Comet, time: Float, fogCol: Color) {
        cometPal[0].set(1f, 1f, 1f, 1f)
        tailPal[0].set(space.neonSoft)
        val fade = min(1f, k.life / 0.8f)
        game.facets.add(rocksNear[0], k.x, k.y, k.z, 1.6f, 1.6f, 1.6f, time * 90f, time * 60f, 0f, cometPal, 1f - fade, fogCol, glow = 0.7f)
        for (i in 1..14) { // the tail streams back along the path, thinning out
            val t = i * 0.055f
            val s = 1.3f * (1f - i / 16f)
            game.facets.add(starShape, k.x - k.vx * t, k.y - k.vy * t, k.z - k.vz * t, s, s, s, i * 40f, 30f, 0f,
                tailPal, 1f - fade * (1f - i / 15f), fogCol, glow = 1f)
        }
    }

    private fun renderStreaks(speedK: Float, reach: Float) {
        val w = space.warp
        streakCol.set(space.neonSoft).lerp(Color.WHITE, 0.5f)
        for (s in streaks) {
            val fade = max(Fog.at(s.z), 1f - reach * (0.45f + 0.55f * max(speedK, w)))
            if (fade >= 0.99f) continue
            val len = s.len * (2f + 6f * speedK + 22f * w)
            game.worldBox(s.x, s.y, s.z, 0.06f, 0.06f, len, streakCol, fade)
        }
    }

    /** A sun's rays (the sunrise set piece), drawn as blended shapes behind the world. */
    fun renderShapes(shapes: ShapeRenderer, time: Float) {
        val trip = trip ?: return
        val sun = trip.planets.firstOrNull { it.glow > 0f } ?: return
        sunRay.set(sun.colors[0])
        game.sunburst(shapes, sun.x, sun.y + 3f, sun.z + 6f - sun.radius, sun.radius * 3.2f, 14, time * 4f, sunRay, 0.22f * space.blend * space.blend)
    }

    private companion object {
        const val DEG = (PI / 180.0).toFloat()
        const val ROCKS = 30
        const val MAX_ROCKS = 56
        const val STREAKS = 36
        const val STARS = 170
        /** About how far a visit flies (69 rows at their spacing): the slow set pieces are paced over it. */
        const val TRIP_LENGTH = 560f
    }
}
