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
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A flat-shaded triangle mesh: the planets, asteroids and stars of Outer
 * Space. Each face carries a palette [slot], a [tone] (a facet a little
 * lighter or darker than its neighbours) and its height [lat] on the shape
 * (-1 bottom … 1 top) for banded planets.
 */
class FacetShape(
    /** Three corners per face, xyz each. */
    val pos: FloatArray,
    /** One outward normal per face. */
    val nrm: FloatArray,
    val slot: ByteArray,
    val tone: FloatArray,
    val lat: FloatArray,
) {
    val faces: Int get() = slot.size
}

/**
 * Faceted shapes in one draw call, lit like the boxes (the light rig is
 * baked into vertex colours by [BoxMeshKit.lightFace]). Shapes are turned on
 * all three axes, so rocks can tumble; [glow] lets a face shine with its own
 * colour (stars, comet tails, warm planet rims). Opaque only; anything past
 * the vertex budget is dropped, so queue the important things first.
 */
class FacetBatch(private val kit: BoxMeshKit, private val maxVerts: Int = 40000) : Disposable {
    private val vertices = FloatArray(maxVerts * 4)
    private val mesh = Mesh(false, maxVerts, 0,
        VertexAttribute(Usage.Position, 3, "a_position"), VertexAttribute(Usage.ColorPacked, 4, "a_color"))
    private var used = 0
    private val light = FloatArray(3)
    private val m = FloatArray(9)

    fun begin() { used = 0 }

    /** Vertices queued this frame (for budgeting and tests). */
    val queued: Int get() = used / 4

    /**
     * Queue [shape] at ([x],[y],[z]), scaled by ([sx],[sy],[sz]) and turned
     * [yaw] about Y, then [pitch] about X, then [roll] about Z (degrees, applied
     * roll first). Face colour = [palette] by slot, or by [bands] (ascending
     * heights) when given; [fog] blends toward [fogColor].
     */
    fun add(shape: FacetShape, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float,
            yaw: Float, pitch: Float, roll: Float, palette: Array<Color>, fog: Float, fogColor: Color,
            glow: Float = 0f, bands: FloatArray? = null) {
        if (used + shape.faces * 12 > vertices.size) return
        rotation(yaw, pitch, roll)
        val p = shape.pos; val n = shape.nrm
        val keep = 1f - fog
        for (f in 0 until shape.faces) {
            // normals under a non-uniform scale: divide by the scale, then turn
            val lx = n[f * 3] / sx; val ly = n[f * 3 + 1] / sy; val lz = n[f * 3 + 2] / sz
            if (glow < 1f) { // a glowing face is its own light: skip the rig
                val wx = m[0] * lx + m[1] * ly + m[2] * lz
                val wy = m[3] * lx + m[4] * ly + m[5] * lz
                val wz = m[6] * lx + m[7] * ly + m[8] * lz
                kit.lightFace(wx, wy, wz, light, 0)
            } else { light[0] = 1f; light[1] = 1f; light[2] = 1f }
            var slot = shape.slot[f].toInt()
            if (bands != null) {
                slot = 0
                for (b in bands) if (shape.lat[f] > b) slot++
            }
            val c = palette[slot % palette.size]
            val t = shape.tone[f]
            val lr = light[0] + (1f - light[0]) * glow
            val lg = light[1] + (1f - light[1]) * glow
            val lb = light[2] + (1f - light[2]) * glow
            val bits = Color.toFloatBits(
                min(1f, c.r * lr * t) * keep + fogColor.r * fog,
                min(1f, c.g * lg * t) * keep + fogColor.g * fog,
                min(1f, c.b * lb * t) * keep + fogColor.b * fog, 1f)
            for (v in 0 until 3) {
                val i = f * 9 + v * 3
                val px = p[i] * sx; val py = p[i + 1] * sy; val pz = p[i + 2] * sz
                vertices[used++] = x + m[0] * px + m[1] * py + m[2] * pz
                vertices[used++] = y + m[3] * px + m[4] * py + m[5] * pz
                vertices[used++] = z + m[6] * px + m[7] * py + m[8] * pz
                vertices[used++] = bits
            }
        }
    }

    /** R = Ry(yaw) · Rx(pitch) · Rz(roll), row-major into [m]. */
    private fun rotation(yaw: Float, pitch: Float, roll: Float) {
        val d = Math.PI.toFloat() / 180f
        val cy = cos(yaw * d); val sy = sin(yaw * d)
        val cp = cos(pitch * d); val sp = sin(pitch * d)
        val cr = cos(roll * d); val sr = sin(roll * d)
        // Rx · Rz
        val a0 = cr; val a1 = -sr; val a2 = 0f
        val a3 = cp * sr; val a4 = cp * cr; val a5 = -sp
        val a6 = sp * sr; val a7 = sp * cr; val a8 = cp
        // Ry · (Rx · Rz)
        m[0] = cy * a0 + sy * a6; m[1] = cy * a1 + sy * a7; m[2] = cy * a2 + sy * a8
        m[3] = a3; m[4] = a4; m[5] = a5
        m[6] = -sy * a0 + cy * a6; m[7] = -sy * a1 + cy * a7; m[8] = -sy * a2 + cy * a8
    }

    fun render(cam: Camera) {
        if (used == 0) return
        mesh.setVertices(vertices, 0, used)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(true)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_BLEND)
        kit.shader.bind(); kit.shader.setUniformMatrix("u_projViewTrans", cam.combined)
        mesh.render(kit.shader, GL20.GL_TRIANGLES, 0, used / 4)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
    }

    override fun dispose() = mesh.dispose()
}

/** The shape library: built once, shared by every space scene. */
object FacetShapes {
    /** A smooth-ish faceted ball (subdivided icosahedron), radius 1. Faces are a little uneven in tone, like cut candy. */
    fun ball(subdivisions: Int, toneSeed: Int = 7): FacetShape = build(sphere(subdivisions), toneSeed, jitter = 0f, craters = 0f)

    /** A rock: a lumpy ball, radius ~1, with a few dark crater faces (slot 1). [detail] 0 is 20 faces (far away), 1 is 80. */
    fun rock(variant: Int, detail: Int = 1): FacetShape = build(sphere(detail), 300 + variant * 31, jitter = 0.32f, craters = if (detail == 0) 0.2f else 0.16f)

    /** A star glint: a flat four-pointed diamond facing +z (two faces): stars are far, so they never turn. */
    fun glint(): FacetShape {
        val pos = floatArrayOf(0f, 1f, 0f, -0.45f, 0f, 0f, 0.45f, 0f, 0f, 0.45f, 0f, 0f, -0.45f, 0f, 0f, 0f, -1f, 0f)
        return FacetShape(pos, floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f), byteArrayOf(0, 0), floatArrayOf(1f, 1f), floatArrayOf(0.5f, -0.5f))
    }

    /** A star point: an octahedron, radius 1. */
    fun star(): FacetShape {
        val v = arrayOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, -1f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f))
        val faces = arrayOf(intArrayOf(0, 2, 4), intArrayOf(4, 2, 1), intArrayOf(1, 2, 5), intArrayOf(5, 2, 0),
            intArrayOf(4, 3, 0), intArrayOf(1, 3, 4), intArrayOf(5, 3, 1), intArrayOf(0, 3, 5))
        return build(Mesh3(v.toMutableList(), faces.toMutableList()), 1, 0f, 0f)
    }

    /**
     * A flat ring in the XZ plane between radii [inner] and [outer] (outer = 1),
     * split into [bands] concentric bands (slots 0, 1, 0…), seen from both sides.
     */
    fun ring(segments: Int, inner: Float, bands: Int): FacetShape {
        val pos = ArrayList<Float>(); val nrm = ArrayList<Float>(); val slot = ArrayList<Byte>()
        val tone = ArrayList<Float>(); val lat = ArrayList<Float>()
        fun tri(ax: Float, az: Float, bx: Float, bz: Float, cx: Float, cz: Float, up: Boolean, s: Int, t: Float) {
            // wind so the face's normal points up (top side) or down (underside)
            val ny = (bz - az) * (cx - ax) - (bx - ax) * (cz - az)
            if ((ny > 0f) == up) pos.addAll(listOf(ax, 0f, az, bx, 0f, bz, cx, 0f, cz)) else pos.addAll(listOf(ax, 0f, az, cx, 0f, cz, bx, 0f, bz))
            nrm.addAll(listOf(0f, if (up) 1f else -1f, 0f)); slot.add(s.toByte()); tone.add(t); lat.add(0f)
        }
        for (b in 0 until bands) {
            val r0 = inner + (1f - inner) * b / bands
            val r1 = inner + (1f - inner) * (b + 1) / bands
            for (i in 0 until segments) {
                val a0 = i * 2f * Math.PI.toFloat() / segments
                val a1 = (i + 1) * 2f * Math.PI.toFloat() / segments
                val c0 = cos(a0); val s0 = sin(a0); val c1 = cos(a1); val s1 = sin(a1)
                val t = if (i % 2 == 0) 1f else 0.93f
                for (up in booleanArrayOf(true, false)) {
                    tri(c0 * r0, s0 * r0, c1 * r1, s1 * r1, c1 * r0, s1 * r0, up, b % 2, t)
                    tri(c0 * r0, s0 * r0, c0 * r1, s0 * r1, c1 * r1, s1 * r1, up, b % 2, t)
                }
            }
        }
        return FacetShape(pos.toFloatArray(), nrm.toFloatArray(), slot.toByteArray(), tone.toFloatArray(), lat.toFloatArray())
    }

    private class Mesh3(val v: MutableList<FloatArray>, val f: MutableList<IntArray>)

    private fun sphere(subdivisions: Int): Mesh3 {
        val t = (1f + sqrt(5f)) / 2f
        val v = mutableListOf(
            floatArrayOf(-1f, t, 0f), floatArrayOf(1f, t, 0f), floatArrayOf(-1f, -t, 0f), floatArrayOf(1f, -t, 0f),
            floatArrayOf(0f, -1f, t), floatArrayOf(0f, 1f, t), floatArrayOf(0f, -1f, -t), floatArrayOf(0f, 1f, -t),
            floatArrayOf(t, 0f, -1f), floatArrayOf(t, 0f, 1f), floatArrayOf(-t, 0f, -1f), floatArrayOf(-t, 0f, 1f))
        for (p in v) normalize(p)
        var f = mutableListOf(
            intArrayOf(0, 11, 5), intArrayOf(0, 5, 1), intArrayOf(0, 1, 7), intArrayOf(0, 7, 10), intArrayOf(0, 10, 11),
            intArrayOf(1, 5, 9), intArrayOf(5, 11, 4), intArrayOf(11, 10, 2), intArrayOf(10, 7, 6), intArrayOf(7, 1, 8),
            intArrayOf(3, 9, 4), intArrayOf(3, 4, 2), intArrayOf(3, 2, 6), intArrayOf(3, 6, 8), intArrayOf(3, 8, 9),
            intArrayOf(4, 9, 5), intArrayOf(2, 4, 11), intArrayOf(6, 2, 10), intArrayOf(8, 6, 7), intArrayOf(9, 8, 1))
        repeat(subdivisions) {
            val mid = HashMap<Long, Int>()
            fun midpoint(a: Int, b: Int): Int {
                val key = if (a < b) a.toLong() shl 32 or b.toLong() else b.toLong() shl 32 or a.toLong()
                return mid.getOrPut(key) {
                    val p = floatArrayOf((v[a][0] + v[b][0]) / 2f, (v[a][1] + v[b][1]) / 2f, (v[a][2] + v[b][2]) / 2f)
                    normalize(p); v.add(p); v.size - 1
                }
            }
            val next = ArrayList<IntArray>(f.size * 4)
            for (tri in f) {
                val a = midpoint(tri[0], tri[1]); val b = midpoint(tri[1], tri[2]); val c = midpoint(tri[2], tri[0])
                next.add(intArrayOf(tri[0], a, c)); next.add(intArrayOf(tri[1], b, a))
                next.add(intArrayOf(tri[2], c, b)); next.add(intArrayOf(a, b, c))
            }
            f = next
        }
        return Mesh3(v, f)
    }

    private fun normalize(p: FloatArray) {
        val l = sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2])
        p[0] /= l; p[1] /= l; p[2] /= l
    }

    /** Flat faces with outward normals; [jitter] pushes corners in/out (shared corners move together, so no cracks). */
    private fun build(mesh: Mesh3, seed: Int, jitter: Float, craters: Float): FacetShape {
        val rnd = kotlin.random.Random(seed)
        val verts = mesh.v.map { p ->
            val k = 1f + (rnd.nextFloat() - 0.5f) * 2f * jitter
            floatArrayOf(p[0] * k, p[1] * k * (1f - jitter * 0.3f), p[2] * k)
        }
        val n = mesh.f.size
        val pos = FloatArray(n * 9); val nrm = FloatArray(n * 3); val slot = ByteArray(n)
        val tone = FloatArray(n); val lat = FloatArray(n)
        for ((i, tri) in mesh.f.withIndex()) {
            val a = verts[tri[0]]; val b = verts[tri[1]]; val c = verts[tri[2]]
            val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
            val vx = c[0] - a[0]; val vy = c[1] - a[1]; val vz = c[2] - a[2]
            var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
            val cx = (a[0] + b[0] + c[0]) / 3f; val cy = (a[1] + b[1] + c[1]) / 3f; val cz = (a[2] + b[2] + c[2]) / 3f
            val corners = if (nx * cx + ny * cy + nz * cz < 0f) { nx = -nx; ny = -ny; nz = -nz; arrayOf(a, c, b) } else arrayOf(a, b, c)
            val l = sqrt(nx * nx + ny * ny + nz * nz)
            nrm[i * 3] = nx / l; nrm[i * 3 + 1] = ny / l; nrm[i * 3 + 2] = nz / l
            for ((k, p) in corners.withIndex()) { pos[i * 9 + k * 3] = p[0]; pos[i * 9 + k * 3 + 1] = p[1]; pos[i * 9 + k * 3 + 2] = p[2] }
            val crater = rnd.nextFloat() < craters
            slot[i] = if (crater) 1 else 0
            tone[i] = if (crater) 0.8f else 0.92f + rnd.nextFloat() * 0.16f
            lat[i] = cy / sqrt(cx * cx + cy * cy + cz * cz)
        }
        return FacetShape(pos, nrm, slot, tone, lat)
    }
}
