package cube.run.game.space

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import cube.run.core.Gdx3DGame
import cube.run.core.SoundFx
import cube.run.core.gfx.FacetShapes
import cube.run.game.world.Fog
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Everything around the floating road in Outer Space, in two speeds at once,
 * built from chunky boxes like every other world. Far away it is calm: the
 * journey's [landmarks] drifting past (the stars are painted by [SpaceDeco]).
 * Close by it is fast: cube asteroids tumbling past the road, warp streaks
 * that outrun the world. Space weather comes and goes: an asteroid belt,
 * comets (the meteor shower and nebula clouds are painted by [SpaceDeco]).
 */
class SpaceSky(private val game: Gdx3DGame, private val space: SpaceWorld) {

    private class Rock(var x: Float, var y: Float, var z: Float, var size: Float, var yaw: Float, var pitch: Float,
                       var spinY: Float, var spinX: Float, var variant: Int, var whooshed: Boolean = false)
    private class Streak(var x: Float, var y: Float, var z: Float, var len: Float)
    private class Comet(var x: Float, var y: Float, var z: Float, val vx: Float, val vy: Float, val vz: Float, var life: Float)

    val landmarks = SpaceLandmarks(game, space)
    private val cube = FacetShapes.cube()
    private val clumps = Array(4) { FacetShapes.clump(it) }

    private val rocks = ArrayList<Rock>()
    private val streaks = ArrayList<Streak>()
    private val comets = ArrayList<Comet>()
    private var rnd = Random(1)
    private var trip: SpaceTrip? = null
    private var nextComet = 0f
    private var lastWhoosh = -9f

    private val rockPal = arrayOf(Color(), Color())
    private val cometPal = arrayOf(Color(1f, 1f, 1f, 1f))
    private val tailPal = arrayOf(Color())
    private val streakCol = Color()
    private val tmp = Color()

    fun begin(trip: SpaceTrip) {
        this.trip = trip
        game.facets.prepare(cube, *clumps)
        rnd = Random(trip.rockSeed)
        rocks.clear(); streaks.clear(); comets.clear()
        val n = (ROCKS * trip.asteroidDensity).toInt()
        repeat(n) { rocks.add(Rock(0f, 0f, -rnd.nextFloat() * 120f + 10f, 0f, 0f, 0f, 0f, 0f, 0).also { seedRock(it) }) }
        repeat(STREAKS) { streaks.add(Streak(0f, 0f, -rnd.nextFloat() * 110f + 8f, 0f).also { seedStreak(it) }) }
        nextComet = 1.5f
        landmarks.begin(trip)
    }

    fun clear() { trip = null; rocks.clear(); streaks.clear(); comets.clear(); landmarks.clear() }

    /** An asteroid belt crowds the asteroids in while it lasts. */
    private fun crowd(): Float {
        val t = trip ?: return 1f
        return 1f + 2.2f * t.weatherAt(SpaceTrip.BELT, space.travelled)
    }

    private fun seedRock(r: Rock) {
        val crowd = crowd()
        if (rnd.nextFloat() < 0.3f) { // beneath the road: you are flying over them (and see them through the glass)
            r.size = 0.7f + rnd.nextFloat() * rnd.nextFloat() * 2.2f
            r.x = (rnd.nextFloat() - 0.5f) * 10f
            r.y = -2.5f - r.size * 1.3f - rnd.nextFloat() * 8f
        } else {
            // Beside the road, never over it: a rock must not read as an obstacle. Its clump reaches ~1.2 × its size.
            val side = if (rnd.nextBoolean()) -1f else 1f
            val near = rnd.nextFloat() < 0.25f * crowd // some skim right past the road edge
            r.size = if (near) 0.7f + rnd.nextFloat() * 0.5f else 0.7f + rnd.nextFloat() * rnd.nextFloat() * 2.2f
            r.x = side * (ROAD_EDGE + r.size * 1.2f + if (near) 0.6f + rnd.nextFloat() * 2f else 3f + rnd.nextFloat() * 16f)
            r.y = -7f + rnd.nextFloat() * (if (near) 9f else 17f)
        }
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
            if (!r.whooshed && r.z > -2f && r.size > 1.2f && abs(r.x) < 8f && r.y > -5f) {
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
        landmarks.tick(mv, space.travelled)
        if (trip.weatherAt(SpaceTrip.COMETS, space.travelled) > 0.5f) {
            nextComet -= dt
            if (nextComet <= 0f) { nextComet = 2.5f + rnd.nextFloat() * 3f; launchComet() }
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
        if (trip == null) return
        val fogCol = game.fogColor
        val a = space.blend
        if (a <= 0.01f) return
        // Smooth coverage fades reveal the backdrop through arriving scenery.
        val reach = a * a
        landmarks.render(time, reach)
        renderRocks(fogCol, reach)
        for (k in comets) renderComet(k, time, fogCol, reach)
        renderStreaks(speedK, reach)
    }

    private fun renderRocks(fogCol: Color, reach: Float) {
        rockPal[0].set(space.rock); rockPal[1].set(space.rockAccent)
        for (r in rocks) {
            val fog = max(Fog.at(r.z), 1f - reach)
            if (fog >= 0.995f) continue
            game.facets.add(clumps[r.variant], r.x, r.y, r.z, r.size, r.size, r.size, r.yaw, r.pitch, 0f, rockPal, fog, fogCol, bent = true, opacity = reach * SpaceLandmarks.smoothFade((r.z + 130f) / 35f))
        }
    }

    private fun renderComet(k: Comet, time: Float, fogCol: Color, reach: Float) {
        cometPal[0].set(1f, 1f, 1f, 1f)
        tailPal[0].set(space.neonSoft)
        val fade = reach * SpaceLandmarks.smoothFade((4.2f - k.life) / 0.6f) * SpaceLandmarks.smoothFade(k.life / 0.8f)
        game.facets.add(cube, k.x, k.y, k.z, 1.5f, 1.5f, 1.5f, time * 90f, time * 60f, 0f, cometPal, 0f, fogCol, glow = 0.7f, opacity = fade)
        for (i in 1..8) { // the tail: a trail of cubes streaming back along the path, shrinking
            val t = i * 0.09f
            val s = 1.2f * (1f - i / 10f)
            game.facets.add(cube, k.x - k.vx * t, k.y - k.vy * t, k.z - k.vz * t, s, s, s, i * 40f + time * 120f, 30f, 0f,
                tailPal, i / 9f, fogCol, glow = 1f, opacity = fade)
        }
    }

    private fun renderStreaks(speedK: Float, reach: Float) {
        val w = space.warp
        streakCol.set(space.neonSoft).lerp(Color.WHITE, 0.5f)
        for (s in streaks) {
            val fade = max(Fog.at(s.z), 1f - reach * (0.45f + 0.55f * max(speedK, w)))
            if (fade >= 0.99f) continue
            val len = s.len * (2f + 6f * speedK + 22f * w)
            game.worldBox(s.x, s.y, s.z, 0.14f, 0.14f, len, streakCol, fade, Fog.appear(s.z))
        }
    }

    /** Blended extras in the sky (a sun's rays). */
    fun renderShapes(shapes: ShapeRenderer, time: Float) {
        if (trip == null) return
        landmarks.renderShapes(shapes, time)
    }

    private companion object {
        const val DEG = (PI / 180.0).toFloat()
        /** Half the space road (three lanes) plus its kerb and a margin: no rock above the road comes closer to the middle. */
        const val ROAD_EDGE = 3.4f
        const val ROCKS = 20
        const val MAX_ROCKS = 40
        const val STREAKS = 24
    }
}
