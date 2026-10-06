package cube.run.game.space

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/**
 * The flat backdrop of Outer Space, painted behind everything (no depth) on a
 * plane far down the camera's line of sight: the Milky Way as a soft band of
 * glow, soft round stars (thicker along the band), four-pointed sparkles that
 * twinkle, and shooting stars streaking across now and then (a whole shower
 * of them when the trip passes through one). Nothing is pixel-small: tiny
 * specks read as dirt on a phone.
 *
 * Plane coordinates are in units of the half view height at that depth, so
 * the picture fills any phone the same way.
 */
class SpaceDeco(private val space: SpaceWorld) {

    private class Shooter(var u: Float, var v: Float, var du: Float, var dv: Float, var life: Float, val span: Float, var size: Float)

    private var stars = FloatArray(0)       // u, v, radius, brightness, phase
    private var sparkles = FloatArray(0)    // u, v, size, phase, tinted (0/1)
    private var blobs = FloatArray(0)       // u, v, radius along, radius across, angle, alpha
    private val shooters = ArrayList<Shooter>()
    private var rnd = Random(1)
    private var nextShooter = 2f
    /** 0..1: how thick the shooting stars come (a meteor shower raises it). */
    var shower = 0f
    /** 0..1: nebula clouds drifting past. */
    var clouds = 0f
    private var cloudTime = 0f
    private var cloudSeed = 0

    private val plane = Matrix4()
    private val right = Vector3()
    private val up = Vector3()
    private val back = Vector3()
    private val centre = Vector3()
    private val c0 = Color(); private val c1 = Color()
    private val band = Color(); private val bandEdge = Color()

    fun begin(trip: SpaceTrip) {
        val r = Random(trip.starSeed xor 0x5EED)
        rnd = Random(trip.starSeed)
        // The band: a tilted stripe through the sky, a little off centre.
        val angle = (if (r.nextBoolean()) 1f else -1f) * (25f + r.nextFloat() * 40f) * DEG
        val ax = cos(angle); val ay = sin(angle)
        val off = (r.nextFloat() - 0.3f) * 0.6f
        stars = FloatArray(STARS * 5)
        for (i in 0 until STARS) {
            if (i % 5 < 2) { // two in five crowd along the band
                val t = (r.nextFloat() * 2f - 1f) * 2.2f
                val across = gauss(r) * 0.2f
                stars[i * 5] = ax * t - ay * (across + off)
                stars[i * 5 + 1] = ay * t + ax * (across + off)
            } else {
                stars[i * 5] = (r.nextFloat() * 2f - 1f) * WIDE
                stars[i * 5 + 1] = (r.nextFloat() * 2f - 1f) * TALL
            }
            stars[i * 5 + 2] = 0.011f + r.nextFloat() * r.nextFloat() * 0.014f
            stars[i * 5 + 3] = 0.55f + r.nextFloat() * 0.45f
            stars[i * 5 + 4] = r.nextFloat() * 6.28f
        }
        blobs = FloatArray(BLOBS * 6)
        for (i in 0 until BLOBS) {
            val t = (i / (BLOBS - 1f) * 2f - 1f) * 1.9f + (r.nextFloat() - 0.5f) * 0.4f
            blobs[i * 6] = ax * t - ay * off
            blobs[i * 6 + 1] = ay * t + ax * off
            blobs[i * 6 + 2] = 0.45f + r.nextFloat() * 0.35f
            blobs[i * 6 + 3] = 0.14f + r.nextFloat() * 0.1f
            blobs[i * 6 + 4] = angle + (r.nextFloat() - 0.5f) * 0.3f
            blobs[i * 6 + 5] = 0.13f + r.nextFloat() * 0.1f
        }
        sparkles = FloatArray(SPARKLES * 5)
        for (i in 0 until SPARKLES) {
            sparkles[i * 5] = (r.nextFloat() * 2f - 1f) * WIDE
            sparkles[i * 5 + 1] = (r.nextFloat() * 1.7f - 0.6f) * TALL // mostly up in the sky
            sparkles[i * 5 + 2] = 0.025f + r.nextFloat() * 0.03f
            sparkles[i * 5 + 3] = r.nextFloat() * 6.28f
            sparkles[i * 5 + 4] = if (r.nextFloat() < 0.35f) 1f else 0f
        }
        shooters.clear(); nextShooter = 1.5f; shower = 0f; clouds = 0f; cloudTime = 0f
        cloudSeed = r.nextInt()
    }

    fun clear() { shooters.clear(); stars = FloatArray(0); sparkles = FloatArray(0); blobs = FloatArray(0) }

    fun tick(dt: Float) {
        if (clouds > 0f) cloudTime += dt
        nextShooter -= dt
        if (nextShooter <= 0f) {
            nextShooter = if (shower > 0.05f) (0.12f + rnd.nextFloat() * 0.35f) / shower else 1.4f + rnd.nextFloat() * 2.6f
            launch()
        }
        var i = shooters.size - 1
        while (i >= 0) {
            val s = shooters[i]
            s.u += s.du * dt; s.v += s.dv * dt; s.life -= dt
            if (s.life <= 0f) shooters.removeAt(i)
            i--
        }
    }

    private fun launch() {
        if (shooters.size >= 16) return
        // a shower falls one way (its radiant); single ones from anywhere high up
        val dir = if (shower > 0.05f) -0.55f else (if (rnd.nextBoolean()) 1f else -1f) * (0.35f + rnd.nextFloat() * 0.5f)
        val speed = 1.6f + rnd.nextFloat() * 1.2f
        val norm = sqrt(1f + dir * dir)
        val life = 0.45f + rnd.nextFloat() * 0.45f
        shooters.add(Shooter((rnd.nextFloat() * 2f - 1f) * WIDE * 0.8f, 0.2f + rnd.nextFloat() * 0.9f,
            dir / norm * speed, -speed / norm, life, life, 0.006f + rnd.nextFloat() * 0.006f))
    }

    /** Paint the backdrop. Call from the backdrop pass (blended, no depth). */
    fun render(shapes: ShapeRenderer, cam: Camera, time: Float) {
        val a = space.blend * space.blend
        if (a <= 0.01f || stars.isEmpty()) return
        // the plane: far down the line of sight, facing the camera, in units of half its view height
        val scale = DEPTH * tan(fieldOfView(cam) * 0.5f * DEG)
        centre.set(cam.direction).scl(DEPTH).add(cam.position)
        right.set(cam.direction).crs(cam.up).nor()
        up.set(right).crs(cam.direction).nor()
        back.set(cam.direction).scl(-scale)
        plane.set(right.scl(scale), up.scl(scale), back, centre)
        shapes.transformMatrix = plane

        // the Milky Way's glow, then the stars
        band.set(space.neonSoft).lerp(space.skyBottom, 0.4f)
        for (i in 0 until BLOBS) {
            val k = i * 6
            c0.set(band.r, band.g, band.b, blobs[k + 5] * a)
            bandEdge.set(band.r, band.g, band.b, 0f)
            ellipse(shapes, blobs[k], blobs[k + 1], blobs[k + 2], blobs[k + 3], blobs[k + 4], c0, bandEdge)
        }
        for (i in 0 until STARS) { // soft round stars, a slow gentle twinkle
            val k = i * 5
            val tw = 0.85f + 0.15f * sin(time * 1.1f + stars[k + 4] * 5f)
            c0.set(space.star.r, space.star.g, space.star.b, stars[k + 3] * tw * a)
            c1.set(space.star.r, space.star.g, space.star.b, 0f)
            ellipse(shapes, stars[k], stars[k + 1], stars[k + 2], stars[k + 2], 0f, c0, c1)
        }
        for (i in 0 until SPARKLES) {
            val k = i * 5
            val tw = 0.55f + 0.45f * sin(time * 2.3f + sparkles[k + 3] * 4f)
            val tint = if (sparkles[k + 4] > 0f) space.neonSoft else space.star
            sparkle(shapes, sparkles[k], sparkles[k + 1], sparkles[k + 2] * (0.7f + 0.3f * tw), time * 0.3f + sparkles[k + 3], tint, tw * a)
        }
        if (clouds > 0.01f) renderClouds(shapes, a * clouds)
        for (s in shooters) shooter(shapes, s, a)
        shapes.identity()
    }

    /**
     * Nebula clouds: soft glowing puffs that come up out of the middle of the
     * view, swell and drift outward past the edges, one after another, as if
     * the road ran through them.
     */
    private fun renderClouds(shapes: ShapeRenderer, alpha: Float) {
        for (k in 0 until CLOUDS) {
            val phase = cloudTime / CLOUD_LIFE + k / CLOUDS.toFloat()
            val round = phase.toInt()
            val p = phase - round
            val r = Random(cloudSeed + k * 7919 + round * 104729)
            val ang = r.nextFloat() * 6.2832f
            val reach = 0.15f + p * p * 1.6f
            val u = cos(ang) * reach * 1.1f; val v = sin(ang) * reach * 0.9f + 0.1f
            val size = 0.25f + p * 1.1f
            val env = (p * 5f).coerceAtMost(1f) * (1f - p)
            val hue = if (r.nextBoolean()) space.neonSoft else space.neon
            c0.set(hue.r, hue.g, hue.b, 0.3f * env * alpha)
            c1.set(hue.r, hue.g, hue.b, 0f)
            ellipse(shapes, u, v, size, size * (0.55f + r.nextFloat() * 0.3f), ang + 1.2f, c0, c1)
        }
    }

    private fun fieldOfView(cam: Camera): Float = (cam as? com.badlogic.gdx.graphics.PerspectiveCamera)?.fieldOfView ?: 67f

    private fun square(shapes: ShapeRenderer, u: Float, v: Float, s: Float, col: Color) {
        shapes.color = col
        shapes.rect(u - s, v - s, s * 2f, s * 2f)
    }

    /** A soft ellipse: bright in the middle, gone at the rim. */
    private fun ellipse(shapes: ShapeRenderer, u: Float, v: Float, ra: Float, rb: Float, angle: Float, inner: Color, outer: Color) {
        val ca = cos(angle); val sa = sin(angle)
        var px = u + ca * ra; var py = v + sa * ra
        for (j in 1..ELLIPSE) {
            val t = j * 2f * PI.toFloat() / ELLIPSE
            val ex = cos(t) * ra; val ey = sin(t) * rb
            val x = u + ca * ex - sa * ey; val y = v + sa * ex + ca * ey
            shapes.triangle(u, v, px, py, x, y, inner, outer, outer)
            px = x; py = y
        }
    }

    /** A four-pointed star: two thin crossed diamonds and a small glow. */
    private fun sparkle(shapes: ShapeRenderer, u: Float, v: Float, r: Float, spin: Float, tint: Color, alpha: Float) {
        c0.set(tint.r, tint.g, tint.b, alpha)
        c1.set(tint.r, tint.g, tint.b, 0f)
        val w = r * 0.16f
        for (arm in 0 until 4) {
            val ang = spin + arm * PI.toFloat() / 2f
            val ox = cos(ang); val oy = sin(ang)
            shapes.triangle(u - oy * w, v + ox * w, u + oy * w, v - ox * w, u + ox * r, v + oy * r, c0, c0, c1)
        }
        c0.set(tint.r, tint.g, tint.b, alpha * 0.35f)
        ellipse(shapes, u, v, r * 0.45f, r * 0.45f, 0f, c0, c1)
    }

    /** A shooting star: a bright head and a tail thinning to nothing behind it. */
    private fun shooter(shapes: ShapeRenderer, s: Shooter, a: Float) {
        val fade = (s.life / s.span).let { it * (1f - it) * 4f }.coerceIn(0f, 1f) * a
        val len = 0.16f
        val tu = s.u - s.du * len; val tv = s.v - s.dv * len
        val n = sqrt(s.du * s.du + s.dv * s.dv)
        val px = -s.dv / n * s.size; val py = s.du / n * s.size
        c0.set(1f, 1f, 1f, fade)
        c1.set(space.neonSoft.r, space.neonSoft.g, space.neonSoft.b, 0f)
        shapes.triangle(s.u + px, s.v + py, s.u - px, s.v - py, tu, tv, c0, c0, c1)
        c0.set(1f, 1f, 1f, fade)
        square(shapes, s.u, s.v, s.size * 1.3f, c0)
    }

    private fun gauss(r: Random): Float = (r.nextFloat() + r.nextFloat() + r.nextFloat() - 1.5f) / 1.5f

    private companion object {
        const val DEG = (PI / 180.0).toFloat()
        const val DEPTH = 300f
        const val WIDE = 1.4f      // half-width of the painted area (a phone is ~0.5 wide; the camera leans)
        const val TALL = 1.25f
        const val STARS = 70
        const val BLOBS = 7
        const val SPARKLES = 22
        const val ELLIPSE = 14
        const val CLOUDS = 6
        const val CLOUD_LIFE = 7f      // seconds from the middle of the view to past its edge
    }
}
