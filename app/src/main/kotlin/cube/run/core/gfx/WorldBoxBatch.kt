package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.utils.Disposable
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Every static box in the world (floor tiles, posts, obstacles, coins) in ONE
 * dynamic mesh / draw call. This game's frame cost is CPU-side ModelBatch
 * submission, not the GPU: 100+ renderables cost ~7 ms on a low-end phone,
 * this pass ~1 ms.
 *
 * Boxes are axis-aligned ([box]) or spun about their vertical axis ([boxSpin]),
 * so the Environment's whole Lambert term collapses into 6 per-face constants.
 * A per-box **fog** factor blends the lit colour toward [fogColor] — that's what
 * makes a long draw distance readable: distant geometry dissolves into the sky
 * instead of popping in. Drawn opaque with depth-write BEFORE the ModelBatch
 * pass, so blended ModelBatch materials still sort correctly against the world.
 *
 * Per frame: [begin], queue boxes, [render]. Boxes past [maxBoxes] are dropped,
 * so queue gameplay-critical boxes first.
 */
class WorldBoxBatch(private val kit: BoxMeshKit, private val maxBoxes: Int = 900) : Disposable {

    /** Distance-haze target colour (set per frame to match the sky). */
    val fogColor = Color(0.1f, 0.1f, 0.2f, 1f)
    /** Ground height added to every box's y by its z (the rolling-hills bonus); null = flat. */
    var terrain: TerrainHeight? = null
        set(value) { field = value; groundZ = Float.NaN }
    /** Applied when geometry is queued, so scenery can dissolve independently of stage particles. */
    var opacity = 1f
    private var translucent = false

    private val mesh = kit.newBatchMesh(maxBoxes)
    private val verts = FloatArray(maxBoxes * kit.vertsPerBox * 4)
    private var count = 0
    private val visibility = BatchVisibility()
    private val axisLight = FloatArray(18)     // 6 axis-aligned faces × rgb Lambert factor (constant)
    // Static crystal clusters reuse their orientations over many frames. A bounded,
    // direct-mapped cache avoids solving all six face lights again on every visit.
    private val spinYaw = FloatArray(512) { Float.NaN }
    private val spinLight = FloatArray(512 * 18)
    private val spinCos = FloatArray(512)
    private val spinSin = FloatArray(512)
    private val slopeLight = FloatArray(18)    // terrain-following floor faces
    private var groundZ = Float.NaN
    private var groundDepth = Float.NaN
    private var groundBack = 0f
    private var groundFront = 0f
    private var cachedSlope = Float.NaN
    private val packed = FloatArray(6)         // scratch: per-face packed colour
    private val corners = FloatArray(24)       // scratch: 8 transformed corners

    init {
        for (f in 0 until 6) {
            val fi = f * 3
            kit.lightFace(kit.faceNrm[fi], kit.faceNrm[fi + 1], kit.faceNrm[fi + 2], axisLight, fi)
        }
    }

    fun begin(camera: Camera? = null) {
        count = 0; translucent = false; groundZ = Float.NaN
        visibility.begin(camera)
    }

    /** Queue one axis-aligned box (centre position, full sizes). [fog] 0..1 blends toward [fogColor]. */
    fun box(x: Float, y0: Float, z: Float, sx: Float, sy: Float, sz: Float, col: Color, fog: Float = 0f, followTerrain: Boolean = false) {
        if (count >= maxBoxes) return
        // Ground pieces meet at the SAME sampled height along each shared edge.
        // Rigid obstacles still translate as a whole at their centre.
        val back: Float
        val front: Float
        if (followTerrain) {
            // Lanes, kerbs and land share the same two heights within a tile row.
            if (z != groundZ || sz != groundDepth) {
                groundZ = z; groundDepth = sz
                groundBack = terrain?.invoke(z - sz / 2f) ?: 0f
                groundFront = terrain?.invoke(z + sz / 2f) ?: 0f
            }
            back = groundBack; front = groundFront
        } else {
            back = terrain?.invoke(z) ?: 0f
            front = back
        }
        if (!visibility.visible(x, y0 + (front + back) * 0.5f, z,
                abs(sx) * 0.5f, (abs(sy) + abs(front - back)) * 0.5f, abs(sz) * 0.5f)) return
        val slope = if (sz > 0f) (front - back) / sz else 0f
        val light = if (slope == 0f) axisLight else slopeLight.also {
            if (slope != cachedSlope) {
                cachedSlope = slope
                val normalScale = 1f / sqrt(1f + slope * slope)
                for (f in 0 until 6) {
                    val fi = f * 3
                    val nx = kit.faceNrm[fi]; val ny = kit.faceNrm[fi + 1]; val nz = kit.faceNrm[fi + 2]
                    val scale = if (ny == 0f) 1f else normalScale
                    kit.lightFace(nx * scale, ny * scale, (nz - slope * ny) * scale, it, fi)
                }
            }
        }
        packFaces(col, fog, light)
        var w = count * kit.vertsPerBox * 4
        val cl = kit.cornerLocal
        for (v in 0 until kit.vertsPerBox) {
            val ci = kit.cornerOf[v] * 3
            verts[w++] = x + cl[ci] * sx
            verts[w++] = y0 + (if (cl[ci + 2] > 0f) front else back) + cl[ci + 1] * sy
            verts[w++] = z + cl[ci + 2] * sz
            verts[w++] = packed[kit.faceOf[v]]
        }
        count++
    }

    /**
     * Like [box] but rotated [yawDeg] about its own vertical axis (coins, pickups).
     * Orientations share cached trigonometry and face lighting across calls and
     * frames; changing color, fog, size or terrain does not invalidate the light.
     */
    fun boxSpin(x: Float, y0: Float, z: Float, sx: Float, sy: Float, sz: Float, yawDeg: Float, col: Color, fog: Float = 0f) {
        if (count >= maxBoxes) return
        val y = y0 + (terrain?.invoke(z) ?: 0f)
        val bits = yawDeg.toRawBits()
        val slot = ((bits * -1640531527) ushr 23)
        val lightOffset = slot * 18
        if (yawDeg != spinYaw[slot]) {
            spinYaw[slot] = yawDeg
            val rad = yawDeg * (Math.PI.toFloat() / 180f)
            val c = cos(rad); val s = sin(rad)
            spinCos[slot] = c; spinSin[slot] = s
            val fn = kit.faceNrm
            for (f in 0 until 6) {
                val fi = f * 3
                val nx = fn[fi]; val ny = fn[fi + 1]; val nz = fn[fi + 2]
                kit.lightFace(nx * c + nz * s, ny, -nx * s + nz * c, spinLight, lightOffset + fi)
            }
        }
        val c = spinCos[slot]; val s = spinSin[slot]
        if (!visibility.visible(x, y, z, (abs(c * sx) + abs(s * sz)) * 0.5f,
                abs(sy) * 0.5f, (abs(s * sx) + abs(c * sz)) * 0.5f)) return
        packFaces(col, fog, spinLight, lightOffset)
        val cl = kit.cornerLocal
        for (k in 0 until 8) {
            val ci = k * 3
            val lx = cl[ci] * sx; val ly = cl[ci + 1] * sy; val lz = cl[ci + 2] * sz
            corners[ci] = x + lx * c + lz * s
            corners[ci + 1] = y + ly
            corners[ci + 2] = z - lx * s + lz * c
        }
        var w = count * kit.vertsPerBox * 4
        for (v in 0 until kit.vertsPerBox) {
            val ci = kit.cornerOf[v] * 3
            verts[w++] = corners[ci]
            verts[w++] = corners[ci + 1]
            verts[w++] = corners[ci + 2]
            verts[w++] = packed[kit.faceOf[v]]
        }
        count++
    }

    private fun packFaces(col: Color, fog: Float, light: FloatArray, offset: Int = 0) {
        if (opacity < 1f) translucent = true
        val keep = 1f - fog
        val fr = fogColor.r * fog; val fg = fogColor.g * fog; val fb = fogColor.b * fog
        for (f in 0 until 6) {
            val fi = offset + f * 3
            packed[f] = Color.toFloatBits(
                min(1f, col.r * light[fi]) * keep + fr,
                min(1f, col.g * light[fi + 1]) * keep + fg,
                min(1f, col.b * light[fi + 2]) * keep + fb,
                opacity,
            )
        }
    }

    /** One opaque, depth-written draw call for every queued box. */
    fun render(cam: Camera) {
        val n = count
        if (n == 0) return
        mesh.setVertices(verts, 0, n * kit.vertsPerBox * 4)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        if (translucent) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        } else Gdx.gl.glDisable(GL20.GL_BLEND)
        kit.shader.bind()
        kit.shader.setUniformMatrix("u_projViewTrans", cam.combined)
        mesh.render(kit.shader, GL20.GL_TRIANGLES, 0, n * kit.idxPerBox)
        // ModelBatch.begin() resets its own state; restore the shared baseline anyway.
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    override fun dispose() {
        mesh.dispose()
    }
}
