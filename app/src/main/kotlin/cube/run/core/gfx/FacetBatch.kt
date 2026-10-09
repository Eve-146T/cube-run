package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import kotlin.math.abs
import kotlin.math.max
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
    /** Same surface with coplanar cells joined, retaining band heights and silhouette. */
    internal val gpuSurface: FacetShape? = null,
) {
    val faces: Int get() = slot.size
    /** Conservative radius, computed once for whole-object visibility rejection. */
    val radius: Float = run {
        var squared = 0f
        var i = 0
        while (i < pos.size) {
            squared = max(squared, pos[i] * pos[i] + pos[i + 1] * pos[i + 1] + pos[i + 2] * pos[i + 2])
            i += 3
        }
        sqrt(squared)
    }
}

/**
 * Box-built shapes grouped by shared surface on GLES3, or in one CPU batch on GLES2.
 * Both paths use the same light rig as [BoxMeshKit.lightFace]. Shapes are turned on
 * all three axes, so asteroids can tumble; [glow] lets a face shine with its
 * own colour (stars, comet tails, a sun). Depth-selected alpha blending fades
 * the visible surface smoothly. The CPU fallback has a fixed vertex budget.
 */
class FacetBatch(private val kit: BoxMeshKit, private val maxVerts: Int = 40000) : Disposable {
    private val vertices by lazy { FloatArray(maxVerts * 4) }
    private val fadingVertices by lazy { FloatArray(maxVerts * 4) }
    private var fadingUsed = 0
    private val meshDelegate = lazy {
        Mesh(false, maxVerts, 0, VertexAttribute(Usage.Position, 3, "a_position"),
            VertexAttribute(Usage.ColorPacked, 4, "a_color"))
    }
    private val mesh by meshDelegate
    private val gpu = if (Gdx.gl30 != null) InstancedFacets(kit) else null
    internal var gpuEnabled = true
    internal var cullingEnabled = true
    internal var compactEnabled = true
    private val visibility = BatchVisibility()
    private val fadeShaderDelegate = lazy {
        ShaderProgram("""
            attribute vec3 a_position;
            attribute vec4 a_color;
            uniform mat4 u_projViewTrans;
            varying vec4 v_color;
            void main() { v_color = vec4(a_color.rgb, min(1.0,a_color.a*255.0/254.0)); gl_Position = u_projViewTrans * vec4(a_position,1.0); }
        """.trimIndent(), """
            #ifdef GL_ES
            precision highp float;
            #endif
            varying vec4 v_color;
            void main() { gl_FragColor = v_color; }
        """.trimIndent()).also { require(it.isCompiled) { it.log } }
    }
    private val fadeShader by fadeShaderDelegate
    private var used = 0
    private var gpuVerts = 0
    private val light = FloatArray(3)
    private val axisLights = FloatArray(18)
    private val m = FloatArray(9)

    fun begin(camera: Camera? = null) { used = 0; fadingUsed = 0; gpuVerts = 0; gpu?.begin(); visibility.begin(if (cullingEnabled) camera else null) }

    /** Upload reusable surfaces at trip entry, before they first become visible. */
    fun prepare(vararg shapes: FacetShape) { gpu?.prepare(*shapes) }

    /** Vertices queued this frame (for budgeting and tests). */
    val queued: Int get() = (used + fadingUsed) / 4 + gpuVerts

    /**
     * Queue [shape] at ([x],[y],[z]), scaled by ([sx],[sy],[sz]) and turned
     * [yaw] about Y, then [pitch] about X, then [roll] about Z (degrees, applied
     * roll first). Face colour = [palette] by slot, or by [bands] (ascending
     * heights) when given; [fog] blends toward [fogColor]. [bent] follows the
     * [WorldBend] (things near the road); the sky stays put.
     */
    fun add(shape: FacetShape, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float,
            yaw: Float, pitch: Float, roll: Float, palette: Array<Color>, fog: Float, fogColor: Color,
            glow: Float = 0f, bands: FloatArray? = null, bent: Boolean = false, opacity: Float = 1f) {
        if (opacity <= 0f) return
        val radius = shape.radius * max(abs(sx), max(abs(sy), abs(sz)))
        var cx = x; var cy = y; var hx = radius; var hy = radius
        if (bent) {
            val farX = WorldBend.dx(z - radius); val nearX = WorldBend.dx(z + radius)
            val farY = WorldBend.dy(z - radius); val nearY = WorldBend.dy(z + radius)
            cx += (farX + nearX) * 0.5f; cy += (farY + nearY) * 0.5f
            hx += abs(farX - nearX) * 0.5f; hy += abs(farY - nearY) * 0.5f
        }
        if (!visibility.visible(cx, cy, z, hx, hy, radius)) return
        rotation(yaw, pitch, roll)
        if (gpuEnabled && gpu != null) {
            if (gpu.add(shape, x, y, z, sx, sy, sz, m, palette, fog, fogColor, glow, bands, bent, opacity, compactEnabled)) gpuVerts += shape.faces * 3
            return
        }
        if (used + fadingUsed + shape.faces * 12 > vertices.size) return
        val target = if (opacity < 1f) fadingVertices else vertices
        var write = if (opacity < 1f) fadingUsed else used
        val p = shape.pos; val n = shape.nrm
        val keep = 1f - fog
        // All library surfaces have one of six axis normals. Light each direction once per object.
        for (axis in 0..2) for (side in 0..1) {
            val value = (if (side == 0) 1f else -1f) / when (axis) { 0 -> sx; 1 -> sy; else -> sz }
            val offset = (axis * 2 + side) * 3
            if (glow < 1f) kit.lightFace(m[axis] * value, m[3 + axis] * value, m[6 + axis] * value, axisLights, offset)
            else for (channel in 0..2) axisLights[offset + channel] = 1f
        }
        for (f in 0 until shape.faces) {
            val nx = n[f * 3]; val ny = n[f * 3 + 1]; val nz = n[f * 3 + 2]
            val axis = if (nx != 0f) 0 else if (ny != 0f) 1 else 2
            val sign = if (when (axis) { 0 -> nx; 1 -> ny; else -> nz } > 0f) 0 else 1
            val offset = (axis * 2 + sign) * 3
            for (channel in 0..2) light[channel] = axisLights[offset + channel]
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
                min(1f, c.b * lb * t) * keep + fogColor.b * fog, opacity)
            for (v in 0 until 3) {
                val i = f * 9 + v * 3
                val px = p[i] * sx; val py = p[i + 1] * sy; val pz = p[i + 2] * sz
                val vz = z + m[6] * px + m[7] * py + m[8] * pz
                target[write++] = x + m[0] * px + m[1] * py + m[2] * pz + (if (bent) WorldBend.dx(vz) else 0f)
                target[write++] = y + m[3] * px + m[4] * py + m[5] * pz + (if (bent) WorldBend.dy(vz) else 0f)
                target[write++] = vz
                target[write++] = bits
            }
        }
        if (opacity < 1f) fadingUsed = write else used = write
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
        gpu?.render(cam)
        if (used == 0 && fadingUsed == 0) return
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(true)
        Gdx.gl.glDepthFunc(GL20.GL_LESS)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_BLEND)
        fadeShader.bind(); fadeShader.setUniformMatrix("u_projViewTrans", cam.combined)
        if (used > 0) {
            mesh.setVertices(vertices, 0, used)
            mesh.render(fadeShader, GL20.GL_TRIANGLES, 0, used / 4)
        }
        if (fadingUsed > 0) {
            mesh.setVertices(fadingVertices, 0, fadingUsed)
            Gdx.gl.glColorMask(false, false, false, false)
            mesh.render(fadeShader, GL20.GL_TRIANGLES, 0, fadingUsed / 4)
            Gdx.gl.glColorMask(true, true, true, true)
            Gdx.gl.glDepthMask(false); Gdx.gl.glDepthFunc(GL20.GL_EQUAL)
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            mesh.render(fadeShader, GL20.GL_TRIANGLES, 0, fadingUsed / 4)
        }
        Gdx.gl.glDepthMask(true); Gdx.gl.glDepthFunc(GL20.GL_LESS)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
    }

    override fun dispose() { if (meshDelegate.isInitialized()) mesh.dispose(); gpu?.dispose(); if (fadeShaderDelegate.isInitialized()) fadeShader.dispose() }
}

/**
 * The shape library, built once and shared by every space scene. Everything
 * is made of boxes, like the rest of the game: planets are chunky voxel
 * balls, asteroids clumps of cubes, rings pixel rings.
 */
object FacetShapes {
    private val balls = HashMap<Int, FacetShape>()
    private data class RingKey(val resolution: Int, val inner: Float, val bands: Int)
    private val rings = HashMap<RingKey, FacetShape>()
    private val clumps = HashMap<Int, FacetShape>()
    private val unitCube by lazy { boxes(listOf(Box(0f, 0f, 0f, 1f, 1f, 1f))) }
    /** A unit cube (half-size 1). */
    fun cube(): FacetShape = unitCube

    /**
     * A voxel ball, radius 1, [res] cells across. Only its outer faces are
     * kept; each face knows its height ([FacetShape.lat]) for bands.
     */
    fun voxelBall(res: Int): FacetShape = balls.getOrPut(res) { buildBall(res) }

    private fun buildBall(res: Int): FacetShape {
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
        // Join exposed coplanar cells. Side faces retain their voxel row height
        // so arbitrary planet bands still pick the same palette at every point.
        val compact = Builder()
        val mask = BooleanArray(res * res)
        val open = BooleanArray(6)
        for (face in 0..5) for (layer in 0 until res) {
            val axis = face / 2
            fun cell(u: Int, v: Int): IntArray = when (axis) {
                0 -> intArrayOf(layer, v, u)
                1 -> intArrayOf(u, layer, v)
                else -> intArrayOf(u, v, layer)
            }
            for (v in 0 until res) for (u in 0 until res) {
                val q = cell(u, v)
                mask[v * res + u] = inside(q[0], q[1], q[2]) &&
                    !inside(q[0] + DX[face], q[1] + DY[face], q[2] + DZ[face])
            }
            for (v in 0 until res) for (u in 0 until res) {
                if (!mask[v * res + u]) continue
                var width = 1
                while (u + width < res && mask[v * res + u + width]) width++
                var height = 1
                if (axis == 1) { // horizontal faces have the same band height across the whole plane
                    while (v + height < res && (0 until width).all { mask[(v + height) * res + u + it] }) height++
                }
                for (dv in 0 until height) for (du in 0 until width) mask[(v + dv) * res + u + du] = false
                val q = cell(u, v)
                val x = (q[0] + .5f - c + if (axis != 0) (width - 1) * .5f else 0f) / c
                val y = (q[1] + .5f - c) / c
                val z = (q[2] + .5f - c + when (axis) { 0 -> (width - 1) * .5f; 1 -> (height - 1) * .5f; else -> 0f }) / c
                open.fill(false); open[face] = true
                compact.box(x, y, z, (if (axis == 0) 1 else width) / c, 1f / c,
                    (when (axis) { 0 -> width; 1 -> height; else -> 1 }) / c, 0, 1f, y, open)
            }
        }
        return out.shape(compact.shape())
    }

    /**
     * A flat pixel ring in the XZ plane between radii [inner] and 1, [res]
     * cells across and one cell thick, in [bands] concentric bands (slots 0, 1, 0…).
     */
    fun voxelRing(res: Int, inner: Float, bands: Int): FacetShape =
        rings.getOrPut(RingKey(res, inner, bands)) { buildRing(res, inner, bands) }

    private fun buildRing(res: Int, inner: Float, bands: Int): FacetShape {
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
    fun clump(variant: Int): FacetShape = clumps.getOrPut(variant) { buildClump(variant) }

    private fun buildClump(variant: Int): FacetShape {
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

    /** A toy built from [parts] (touching, never intersecting), drawn as one object: the roadside of the biomes. */
    fun model(parts: List<Box>): FacetShape = boxes(parts)

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

        fun shape(gpuSurface: FacetShape? = null) = FacetShape(pos.toFloatArray(), nrm.toFloatArray(), slot.toByteArray(), tone.toFloatArray(), lat.toFloatArray(), gpuSurface)
    }
}
