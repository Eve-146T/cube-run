package cube.run.game.world.biome

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import cube.run.core.Gdx3DGame
import cube.run.core.gfx.FacetShape
import cube.run.game.Terrain
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Queues a biome's toys into the facet batch: standing on the land (they
 * ride the Rollercoaster's hills and fall away with the land in Outer Space)
 * and fading with the world (the menu fades the road in and out).
 */
class BiomeDraw(game: Gdx3DGame) {
    @PublishedApi @JvmField internal val facets = game.facets
    @PublishedApi @JvmField internal val fogColor = game.fogColor
    /** How far the land has fallen away beneath the road (Outer Space). */
    @JvmField var drop = 0f
    /** The whole scene's opacity. */
    @JvmField var opacity = 1f
    /** Stretches what is drawn upward from the ground (y = 0): the horizon's height, per biome. */
    @JvmField var tall = 1f
    @PublishedApi @JvmField internal var groundZ = Float.NaN
    @PublishedApi @JvmField internal var groundY = 0f
    /** Terrain moves between frames; adjacent parts at one depth share its exact sample. */
    internal fun begin() { groundZ = Float.NaN }
    /** A conservative animation envelope can reject offscreen weather before its trig work. */
    @Suppress("NOTHING_TO_INLINE")
    inline fun visible(x: Float, y: Float, z: Float, hx: Float, hy: Float, hz: Float) =
        facets.visible(x, y, z, hx, hy, hz)

    /**
     * [shape] standing at ([x], [y], [z]) scaled [sx]/[sy]/[sz], turned [yaw] (then [pitch], [roll]).
     * [onLand] things ride the land; the sky does not. [alpha] fades this one alone. [bands] colours by height, as on planets.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun add(shape: FacetShape, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, yaw: Float,
            palette: Array<Color>, fog: Float, glow: Float = 0f, pitch: Float = 0f, roll: Float = 0f,
            onLand: Boolean = true, alpha: Float = 1f, bands: FloatArray? = null, preculled: Boolean = false) {
        val o = opacity * alpha
        if (o <= 0.004f || fog >= 0.995f) return
        val ground = if (onLand) {
            if (z != groundZ) { groundZ = z; groundY = Terrain.y(z) }
            groundY - drop
        } else 0f
        facets.add(shape, x, y * tall + ground, z, sx, sy * tall, sz, yaw, pitch, roll, palette, fog, fogColor,
            glow = glow, bands = bands, bent = false, opacity = if (o >= 0.996f) 1f else o, preculled = preculled)
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun add(shape: FacetShape, x: Float, y: Float, z: Float, s: Float, yaw: Float, palette: Array<Color>, fog: Float,
            glow: Float = 0f, onLand: Boolean = true, alpha: Float = 1f, preculled: Boolean = false) =
        add(shape, x, y, z, s, s, s, yaw, palette, fog, glow, onLand = onLand, alpha = alpha, preculled = preculled)
}

/**
 * Paints a biome's sky: soft round stars on a plane far down the camera's
 * line of sight (drawn behind everything), and sunbursts in the world.
 * Plane coordinates are in half view heights, so any phone is filled alike.
 */
class SkyPainter(private val game: Gdx3DGame) {
    internal var fastRaysEnabled = true
    internal var fastStarsEnabled = true
    private val stars = java.util.IdentityHashMap<FloatArray, SkyStars>()
    private val combined = Matrix4()
    private val raysDelegate = lazy { SkyRays() }
    private val rays by raysDelegate
    fun dispose() { if (raysDelegate.isInitialized()) rays.dispose(); for (mesh in stars.values) mesh.dispose(); stars.clear() }
    fun prepareRays(radius: Float, count: Int, width: Float = .5f) = rays.prepare(radius, count, width)
    fun prepareStars(data: FloatArray, col: Color, base: Float, amplitude: Float, gain: Float, sparkleEvery: Int = 0) {
        val mesh = stars[data] ?: SkyStars(data, col, base, amplitude, gain, sparkleEvery).also { stars[data] = it }
        mesh.prepare()
    }
    private val plane = Matrix4()
    private val right = Vector3(); private val up = Vector3(); private val back = Vector3(); private val centre = Vector3()
    private val inner = Color(); private val outer = Color()
    private var shapes: ShapeRenderer? = null

    /** Start painting on the backdrop plane. */
    fun beginPlane(shapes: ShapeRenderer, cam: Camera) {
        val fov = (cam as? PerspectiveCamera)?.fieldOfView ?: 60f
        val scale = DEPTH * tan(fov * 0.5f * DEG)
        centre.set(cam.direction).scl(DEPTH).add(cam.position)
        right.set(cam.direction).crs(cam.up).nor()
        up.set(right).crs(cam.direction).nor()
        back.set(cam.direction).scl(-scale)
        plane.set(right.scl(scale), up.scl(scale), back, centre)
        shapes.transformMatrix = plane
        this.shapes = shapes
    }

    fun endPlane() { shapes?.identity(); shapes = null }

    /** False selects the original CPU fans for framebuffer comparisons. */
    fun stars(data: FloatArray, col: Color, alpha: Float, time: Float, base: Float,
              amplitude: Float, frequency: Float, gain: Float, sparkleEvery: Int = 0): Boolean {
        if (!fastStarsEnabled) return false
        val s = shapes ?: return true
        val mesh = stars[data] ?: SkyStars(data, col, base, amplitude, gain, sparkleEvery).also { stars[data] = it }
        s.flush()
        combined.set(s.projectionMatrix).mul(s.transformMatrix)
        mesh.render(combined, time, alpha, frequency)
        return true
    }

    /** For the world pass (sunbursts). */
    fun beginWorld(shapes: ShapeRenderer) { this.shapes = shapes }

    /** A soft round glow at ([u], [v]) on the plane: bright in the middle, gone at the rim. */
    fun glow(u: Float, v: Float, r: Float, col: Color, alpha: Float) {
        val s = shapes ?: return
        if (alpha <= 0.004f) return
        inner.set(col.r, col.g, col.b, alpha); outer.set(col.r, col.g, col.b, 0f)
        var px = u + r; var py = v
        for (j in 1..SIDES) {
            val x = u + CX[j] * r; val y = v + CY[j] * r
            s.triangle(u, v, px, py, x, y, inner, outer, outer)
            px = x; py = y
        }
    }

    /** A four-pointed sparkle at ([u], [v]) on the plane. */
    fun sparkle(u: Float, v: Float, r: Float, col: Color, alpha: Float) {
        val s = shapes ?: return
        if (alpha <= 0.004f) return
        inner.set(col.r, col.g, col.b, alpha); outer.set(col.r, col.g, col.b, 0f)
        val w = r * 0.18f
        s.triangle(u - w, v, u + w, v, u, v + r, inner, inner, outer)
        s.triangle(u - w, v, u + w, v, u, v - r, inner, inner, outer)
        s.triangle(u, v - w, u, v + w, u + r, v, inner, inner, outer)
        s.triangle(u, v - w, u, v + w, u - r, v, inner, inner, outer)
    }

    /** A fan of rays round a world point (a sun), facing the camera. */
    fun rays(x: Float, y: Float, z: Float, r: Float, n: Int, angle: Float, col: Color, alpha: Float, width: Float = 0.5f) {
        val s = shapes ?: return
        if (alpha <= 0.004f) return
        if (fastRaysEnabled) {
            s.flush()
            rays.render(s.projectionMatrix, x, y, z, r, n, angle, col, alpha, width)
        } else game.sunburst(s, x, y, z, r, n, angle, col, alpha, width)
    }

    private companion object {
        const val DEG = (PI / 180.0).toFloat()
        const val DEPTH = 300f
        const val SIDES = 10
        val CX = FloatArray(SIDES + 1) { cos(it * 2f * PI.toFloat() / SIDES) }
        val CY = FloatArray(SIDES + 1) { sin(it * 2f * PI.toFloat() / SIDES) }
    }
}
