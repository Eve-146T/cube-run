package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.utils.Disposable
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A bounded batch of world boxes. GLES 3 uploads one transform/color record per
 * visible box and lights a shared cube on the GPU. The GLES 2 fallback packs
 * lit vertices on the CPU, omitting invisible boxes and back faces.
 *
 * Both paths preserve terrain deformation, fog, opacity and original geometry.
 * Queue gameplay-critical boxes first: calls past [maxBoxes] are dropped.
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
    private val instances = if (Gdx.gl30 != null) InstancedWorldBoxes(kit, maxBoxes) else null
    private var useInstances = false
    private val verts = FloatArray(maxBoxes * kit.vertsPerBox * 4)
    private var count = 0
    private var faces = 0
    private var view: PerspectiveCamera? = null
    private val visibility = BatchVisibility()
    private val axisLight = FloatArray(18)     // 6 axis-aligned faces × rgb Lambert factor (constant)
    // Static crystal clusters reuse their orientations over many frames. A bounded,
    // direct-mapped cache avoids solving all six face lights again on every visit.
    private val spinYaw = FloatArray(512) { Float.NaN }
    private val spinLit = BooleanArray(512)
    private val spinLight = FloatArray(512 * 18)
    private val spinCos = FloatArray(512)
    private val spinSin = FloatArray(512)
    private val slopeLight = FloatArray(18)    // terrain-following floor faces
    private var groundZ = Float.NaN
    private var groundDepth = Float.NaN
    private var groundBack = 0f
    private var groundFront = 0f
    private var cachedSlope = Float.NaN
    private val corners = FloatArray(24)       // scratch: 8 transformed corners

    init {
        for (f in 0 until 6) {
            val fi = f * 3
            kit.lightFace(kit.faceNrm[fi], kit.faceNrm[fi + 1], kit.faceNrm[fi + 2], axisLight, fi)
        }
    }

    fun begin(camera: Camera? = null, instanced: Boolean = true) {
        useInstances = instanced && instances != null
        instances?.begin()
        count = 0; faces = 0; translucent = false; groundZ = Float.NaN
        view = camera as? PerspectiveCamera
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
        if (useInstances && sx > 0f && sy > 0f && sz > 0f) {
            if (opacity < 1f) translucent = true
            instances!!.add(x, y0, z, sx, sy, sz, 1f, 0f, back, front, col, fog, fogColor, opacity)
            count++
            return
        }
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
        if (opacity < 1f) translucent = true
        var w = faces * 16
        val cl = kit.cornerLocal
        var nextFace = 0
        while (nextFace < kit.vertsPerBox) {
            val base = nextFace; nextFace += 4
            val fi = kit.faceOf[base] * 3
            val ci0 = kit.cornerOf[base] * 3
            val camera = view
            if (camera != null && sx > 0 && sy > 0 && sz > 0) {
                val dx = camera.position.x - (x + cl[ci0] * sx)
                val dy = camera.position.y - (y0 + (if (cl[ci0 + 2] > 0f) front else back) + cl[ci0 + 1] * sy)
                val dz = camera.position.z - (z + cl[ci0 + 2] * sz)
                if (kit.faceNrm[fi] * dx + kit.faceNrm[fi + 1] * dy +
                    (kit.faceNrm[fi + 2] - slope * kit.faceNrm[fi + 1]) * dz < -.0001f) continue
            }
            val faceColor = packFace(col, fog, light, fi)
            for (v in base until base + 4) {
                val ci = kit.cornerOf[v] * 3
                verts[w++] = x + cl[ci] * sx
                verts[w++] = y0 + (if (cl[ci + 2] > 0f) front else back) + cl[ci + 1] * sy
                verts[w++] = z + cl[ci + 2] * sz
                verts[w++] = faceColor
            }
            faces++
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
            spinLit[slot] = false
        }
        val c = spinCos[slot]; val s = spinSin[slot]
        if (!visibility.visible(x, y, z, (abs(c * sx) + abs(s * sz)) * 0.5f,
                abs(sy) * 0.5f, (abs(s * sx) + abs(c * sz)) * 0.5f)) return
        if (opacity < 1f) translucent = true
        if (useInstances && sx > 0f && sy > 0f && sz > 0f) {
            instances!!.add(x, y, z, sx, sy, sz, c, s, 0f, 0f, col, fog, fogColor, opacity)
            count++
            return
        }
        if (!spinLit[slot]) {
            val fn = kit.faceNrm
            for (f in 0 until 6) {
                val fi = f * 3
                val nx = fn[fi]; val ny = fn[fi + 1]; val nz = fn[fi + 2]
                kit.lightFace(nx * c + nz * s, ny, -nx * s + nz * c, spinLight, lightOffset + fi)
            }
            spinLit[slot] = true
        }
        val cl = kit.cornerLocal
        for (k in 0 until 8) {
            val ci = k * 3
            val lx = cl[ci] * sx; val ly = cl[ci + 1] * sy; val lz = cl[ci + 2] * sz
            corners[ci] = x + lx * c + lz * s
            corners[ci + 1] = y + ly
            corners[ci + 2] = z - lx * s + lz * c
        }
        var w = faces * 16
        var nextFace = 0
        while (nextFace < kit.vertsPerBox) {
            val base = nextFace; nextFace += 4
            val fi = kit.faceOf[base] * 3
            val ci0 = kit.cornerOf[base] * 3
            val camera = view
            if (camera != null && sx > 0 && sy > 0 && sz > 0) {
                val nx = kit.faceNrm[fi]; val nz = kit.faceNrm[fi + 2]
                if ((nx * c + nz * s) * (camera.position.x - corners[ci0]) +
                    kit.faceNrm[fi + 1] * (camera.position.y - corners[ci0 + 1]) +
                    (-nx * s + nz * c) * (camera.position.z - corners[ci0 + 2]) < -.0001f) continue
            }
            val faceColor = packFace(col, fog, spinLight, lightOffset + fi)
            for (v in base until base + 4) {
                val ci = kit.cornerOf[v] * 3
                verts[w++] = corners[ci]
                verts[w++] = corners[ci + 1]
                verts[w++] = corners[ci + 2]
                verts[w++] = faceColor
            }
            faces++
        }
        count++
    }

    private fun packFace(col: Color, fog: Float, light: FloatArray, fi: Int): Float {
        val keep = 1f - fog
        val fr = fogColor.r * fog; val fg = fogColor.g * fog; val fb = fogColor.b * fog
        return Color.toFloatBits(
                min(1f, col.r * light[fi]) * keep + fr,
                min(1f, col.g * light[fi + 1]) * keep + fg,
                min(1f, col.b * light[fi + 2]) * keep + fb,
                opacity,
        )
    }

    /** One opaque, depth-written draw call for every queued box. */
    fun render(cam: Camera) {
        if (useInstances) instances!!.render(cam, translucent)
        val n = faces
        if (n == 0) return
        mesh.setVertices(verts, 0, n * 16)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        if (translucent) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        } else Gdx.gl.glDisable(GL20.GL_BLEND)
        kit.shader.bind()
        kit.shader.setUniformMatrix("u_projViewTrans", cam.combined)
        mesh.render(kit.shader, GL20.GL_TRIANGLES, 0, n * 6)
        // ModelBatch.begin() resets its own state; restore the shared baseline anyway.
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    override fun dispose() {
        instances?.dispose()
        mesh.dispose()
    }
}
