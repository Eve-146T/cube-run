package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.utils.Disposable
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Coins: every one a [sides]-gonal prism (a fat disc standing on its edge,
 * spun about the vertical axis), all in ONE draw call with the same baked
 * lighting + fog as [WorldBoxBatch]. The rim is lit per side so the edge
 * reads as a real thickness when the coin turns; the faces are lit by their
 * true normal but never below a bright floor, and flash a glint as they
 * swing through the key light — so a spinning coin sparkles instead of
 * going dull.
 */
class PrismBatch(private val kit: BoxMeshKit, private val sides: Int = 12, private val max: Int = 520) : Disposable {

    /** Distance-haze target colour (set per frame to match the sky). */
    val fogColor = Color(0.1f, 0.1f, 0.2f, 1f)
    /** Ground height added to every coin's y by its z (the rolling-hills bonus); null = flat. */
    var terrain: ((Float) -> Float)? = null

    private val vertsPer = sides * 4 + sides * 2
    private val idxPer = sides * 6 + (sides - 2) * 3 * 2
    private val mesh: Mesh
    private val verts = FloatArray(max * vertsPer * 4)
    private var count = 0
    private val light = FloatArray(3)
    // unit polygon (radius 1) corner directions
    private val cx = FloatArray(sides)
    private val cy = FloatArray(sides)
    private val nx = FloatArray(sides)   // side normals (local XY)
    private val ny = FloatArray(sides)

    init {
        for (k in 0 until sides) {
            val a = (k + 0.5f) * (2f * Math.PI.toFloat() / sides)
            cx[k] = cos(a); cy[k] = sin(a)
            val m = (k + 1f) * (2f * Math.PI.toFloat() / sides)
            nx[k] = cos(m); ny[k] = sin(m)
        }
        mesh = Mesh(false, max * vertsPer, max * idxPer,
            VertexAttribute(Usage.Position, 3, "a_position"), VertexAttribute(Usage.ColorPacked, 4, "a_color"))
        val idx = ShortArray(max * idxPer)
        var w = 0
        for (c in 0 until max) {
            val vb = c * vertsPer
            for (k in 0 until sides) { // side quads: v0 v1 v2 v3 = front-k, front-k+1, back-k+1, back-k
                // wound so the outward face is counter-clockwise from outside (else the rim gets culled)
                val q = vb + k * 4
                idx[w++] = q.toShort(); idx[w++] = (q + 3).toShort(); idx[w++] = (q + 2).toShort()
                idx[w++] = q.toShort(); idx[w++] = (q + 2).toShort(); idx[w++] = (q + 1).toShort()
            }
            val f = vb + sides * 4          // front cap fan
            for (k in 1 until sides - 1) { idx[w++] = f.toShort(); idx[w++] = (f + k).toShort(); idx[w++] = (f + k + 1).toShort() }
            val b = f + sides               // back cap fan (reverse winding)
            for (k in 1 until sides - 1) { idx[w++] = b.toShort(); idx[w++] = (b + k + 1).toShort(); idx[w++] = (b + k).toShort() }
        }
        mesh.setIndices(idx)
    }

    fun begin() { count = 0 }

    private fun packed(col: Color, k: Float, fog: Float, lx: Float, ly: Float, lz: Float, floor: Float): Float {
        kit.lightFace(lx, ly, lz, light, 0)
        val keep = 1f - fog
        return Color.toFloatBits(
            min(1f, col.r * k * max(floor, light[0])) * keep + fogColor.r * fog,
            min(1f, col.g * k * max(floor, light[1])) * keep + fogColor.g * fog,
            min(1f, col.b * k * max(floor, light[2])) * keep + fogColor.b * fog,
            1f,
        )
    }

    /**
     * Queue one coin: centre ([x],[y],[z]), [r] radius, [t] thickness, spun
     * [yawDeg] about Y (0 = face toward +Z, the camera). [col] is the face
     * colour; the rim is drawn a little darker.
     */
    fun coin(x: Float, y0: Float, z: Float, r: Float, t: Float, yawDeg: Float, col: Color, fog: Float = 0f) {
        if (count >= max) return
        val y = y0 + (terrain?.invoke(z) ?: 0f)
        val rad = yawDeg * (Math.PI.toFloat() / 180f)
        val c = cos(rad); val s = sin(rad)
        val hz = t / 2f
        var w = count * vertsPer * 4
        // local (lx, ly, lz) -> world: (x + lx*c + lz*s, y + ly, z - lx*s + lz*c)
        for (k in 0 until sides) {
            val k1 = (k + 1) % sides
            val sideCol = packed(col, 0.74f, fog, nx[k] * c, ny[k], -nx[k] * s, 0.55f)
            val ax = cx[k] * r; val ay = cy[k] * r
            val bx = cx[k1] * r; val by = cy[k1] * r
            // front-k, front-k1, back-k1, back-k
            verts[w++] = x + ax * c + hz * s; verts[w++] = y + ay; verts[w++] = z - ax * s + hz * c; verts[w++] = sideCol
            verts[w++] = x + bx * c + hz * s; verts[w++] = y + by; verts[w++] = z - bx * s + hz * c; verts[w++] = sideCol
            verts[w++] = x + bx * c - hz * s; verts[w++] = y + by; verts[w++] = z - bx * s - hz * c; verts[w++] = sideCol
            verts[w++] = x + ax * c - hz * s; verts[w++] = y + ay; verts[w++] = z - ax * s - hz * c; verts[w++] = sideCol
        }
        // the faces: true normal (front = +Z spun), a bright floor, and a glint when it swings through the light
        val fx = s; val fz = c
        val glintF = 0.86f + 0.5f * glint(fx, fz)
        val frontCol = packed(col, glintF, fog, fx, 0f, fz, 0.8f)
        for (k in 0 until sides) {
            val ax = cx[k] * r; val ay = cy[k] * r
            verts[w++] = x + ax * c + hz * s; verts[w++] = y + ay; verts[w++] = z - ax * s + hz * c; verts[w++] = frontCol
        }
        val glintB = 0.86f + 0.5f * glint(-fx, -fz)
        val backCol = packed(col, glintB, fog, -fx, 0f, -fz, 0.8f)
        for (k in 0 until sides) {
            val ax = cx[k] * r; val ay = cy[k] * r
            verts[w++] = x + ax * c - hz * s; verts[w++] = y + ay; verts[w++] = z - ax * s - hz * c; verts[w++] = backCol
        }
        count++
    }

    /** How squarely a face normal (nx, nz) meets the glint direction (toward the camera, a little to the right): 0..1, sharp. */
    private fun glint(nx: Float, nz: Float): Float {
        val d = nx * 0.35f + nz * 0.94f
        if (d <= 0f) return 0f
        return d * d * d
    }

    /** One opaque, depth-written draw call for every queued coin. */
    fun render(cam: Camera) {
        val n = count
        if (n == 0) return
        mesh.setVertices(verts, 0, n * vertsPer * 4)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        kit.shader.bind()
        kit.shader.setUniformMatrix("u_projViewTrans", cam.combined)
        mesh.render(kit.shader, GL20.GL_TRIANGLES, 0, n * idxPer)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
    }

    override fun dispose() { mesh.dispose() }
}
