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
 * A flat-shaded triangle mesh built from boxes: the planets, asteroids and
 * stars of Outer Space. Each face carries a palette [slot], a [tone] (a face
 * a little lighter or darker than its neighbours) and its height [lat] on
 * the shape (-1 bottom … 1 top) for banded planets.
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
 * Box-built shapes in one draw call, lit like the boxes (the light rig is
 * baked into vertex colours by [BoxMeshKit.lightFace]). Shapes are turned on
 * all three axes, so asteroids can tumble; [glow] lets a face shine with its
 * own colour (stars, comet tails, a sun). Opaque only; anything past
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

/**
 * The shape library, built once and shared by every space scene. Everything
 * is made of boxes, like the rest of the game: planets are chunky voxel
 * balls, asteroids clumps of cubes, rings pixel rings.
 */
object FacetShapes {
    /** A unit cube (half-size 1). */
    fun cube(): FacetShape = boxes(listOf(Box(0f, 0f, 0f, 1f, 1f, 1f)))

    /**
     * A voxel ball, radius 1, [res] cells across. Only its outer faces are
     * kept; each face knows its height ([FacetShape.lat]) for bands.
     */
    fun voxelBall(res: Int): FacetShape {
        val c = res / 2f
        fun inside(i: Int, j: Int, k: Int): Boolean {
            if (i !in 0 until res || j !in 0 until res || k !in 0 until res) return false
            val x = i + 0.5f - c; val y = j + 0.5f - c; val z = k + 0.5f - c
            return x * x + y * y + z * z <= c * c + 0.35f
        }
        val out = Builder()
        val h = 1f / res
        for (i in 0 until res) for (j in 0 until res) for (k in 0 until res) {
            if (!inside(i, j, k)) continue
            val x = (i + 0.5f - c) / c; val y = (j + 0.5f - c) / c; val z = (k + 0.5f - c) / c
            val open = BooleanArray(6) { f -> !inside(i + DX[f], j + DY[f], k + DZ[f]) }
            out.box(x, y, z, h * 2f, h * 2f, h * 2f, 0, 1f, y, open)
        }
        return out.shape()
    }

    /**
     * A flat pixel ring in the XZ plane between radii [inner] and 1, [res]
     * cells across and one cell thick, in [bands] concentric bands (slots 0, 1, 0…).
     */
    fun voxelRing(res: Int, inner: Float, bands: Int): FacetShape {
        val c = res / 2f
        fun radius(i: Int, k: Int): Float { val x = (i + 0.5f - c) / c; val z = (k + 0.5f - c) / c; return sqrt(x * x + z * z) }
        fun inside(i: Int, k: Int) = i in 0 until res && k in 0 until res && radius(i, k).let { it in inner..1f }
        val out = Builder()
        val h = 1f / res
        for (i in 0 until res) for (k in 0 until res) {
            if (!inside(i, k)) continue
            val band = ((radius(i, k) - inner) / (1f - inner) * bands).toInt().coerceAtMost(bands - 1)
            val open = BooleanArray(6) { f -> DY[f] != 0 || !inside(i + DX[f], k + DZ[f]) }
            out.box((i + 0.5f - c) / c, 0f, (k + 0.5f - c) / c, h * 2f, h * 1.2f, h * 2f, band % 2, if ((i + k) % 2 == 0) 1f else 0.94f, 0f, open)
        }
        return out.shape()
    }

    /** An asteroid: a big cube with one to three smaller cubes stuck to it, about radius 1. Small ones are slot 1. */
    fun clump(variant: Int): FacetShape {
        val rnd = kotlin.random.Random(300 + variant * 31)
        val parts = arrayListOf(Box(0f, 0f, 0f, 0.72f, 0.66f, 0.7f))
        repeat(1 + variant % 3) {
            val axis = rnd.nextInt(3); val side = if (rnd.nextBoolean()) 1f else -1f
            val s = 0.3f + rnd.nextFloat() * 0.16f
            val o = FloatArray(3) { (rnd.nextFloat() - 0.5f) * 0.6f }
            o[axis] = side * (0.6f + s * 0.4f)
            parts.add(Box(o[0], o[1], o[2], s, s, s, slot = 1))
        }
        return boxes(parts)
    }

    /** A box: centre, half-size, palette slot. */
    class Box(val x: Float, val y: Float, val z: Float, val hx: Float, val hy: Float, val hz: Float, val slot: Int = 0)

    private fun boxes(parts: List<Box>): FacetShape {
        val out = Builder()
        val all = BooleanArray(6) { true }
        for (b in parts) out.box(b.x, b.y, b.z, b.hx * 2f, b.hy * 2f, b.hz * 2f, b.slot, if (b.slot == 0) 1f else 0.92f, b.y, all)
        return out.shape()
    }

    // face order: +x, -x, +y, -y, +z, -z
    private val DX = intArrayOf(1, -1, 0, 0, 0, 0)
    private val DY = intArrayOf(0, 0, 1, -1, 0, 0)
    private val DZ = intArrayOf(0, 0, 0, 0, 1, -1)

    private class Builder {
        val pos = ArrayList<Float>(); val nrm = ArrayList<Float>(); val slot = ArrayList<Byte>()
        val tone = ArrayList<Float>(); val lat = ArrayList<Float>()

        /** The [open] faces of a box at (x,y,z) sized (w,h,d), two triangles each, wound outward. */
        fun box(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, s: Int, t: Float, height: Float, open: BooleanArray) {
            val hx = w / 2f; val hy = h / 2f; val hz = d / 2f
            for (f in 0 until 6) {
                if (!open[f]) continue
                val nx = DX[f].toFloat(); val ny = DY[f].toFloat(); val nz = DZ[f].toFloat()
                // two axes spanning the face
                val (ux, uy, uz) = when { nx != 0f -> Triple(0f, nx, 0f); ny != 0f -> Triple(0f, 0f, ny); else -> Triple(nz, 0f, 0f) }
                val vx = ny * uz - nz * uy; val vy = nz * ux - nx * uz; val vz = nx * uy - ny * ux // v = n × u, so u × v = n
                val cx = x + nx * hx; val cy = y + ny * hy; val cz = z + nz * hz
                fun corner(a: Float, b: Float) = floatArrayOf(cx + (ux * a + vx * b) * hx, cy + (uy * a + vy * b) * hy, cz + (uz * a + vz * b) * hz)
                val c00 = corner(-1f, -1f); val c10 = corner(1f, -1f); val c11 = corner(1f, 1f); val c01 = corner(-1f, 1f)
                for (tri in arrayOf(arrayOf(c00, c10, c11), arrayOf(c00, c11, c01))) {
                    for (p in tri) { pos.add(p[0]); pos.add(p[1]); pos.add(p[2]) }
                    nrm.add(nx); nrm.add(ny); nrm.add(nz); slot.add(s.toByte()); tone.add(t); lat.add(height)
                }
            }
        }

        fun shape() = FacetShape(pos.toFloatArray(), nrm.toFloatArray(), slot.toByteArray(), tone.toFloatArray(), lat.toFloatArray())
    }
}
