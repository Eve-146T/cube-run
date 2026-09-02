package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.utils.Disposable
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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
class WorldBoxBatch(private val kit: BoxMeshKit, private val maxBoxes: Int = 480) : Disposable {

    /** Distance-haze target colour (set per frame to match the sky). */
    val fogColor = Color(0.1f, 0.1f, 0.2f, 1f)

    private val mesh = kit.newBatchMesh(maxBoxes)
    private val verts = FloatArray(maxBoxes * kit.vertsPerBox * 4)
    private var count = 0
    private val axisLight = FloatArray(18)     // 6 axis-aligned faces × rgb Lambert factor (constant)
    private val spinLight = FloatArray(18)     // same, for the last spin yaw (cached)
    private var spinCacheYaw = Float.NaN
    private val packed = FloatArray(6)         // scratch: per-face packed colour
    private val corners = FloatArray(24)       // scratch: 8 transformed corners

    init {
        for (f in 0 until 6) {
            val fi = f * 3
            kit.lightFace(kit.faceNrm[fi], kit.faceNrm[fi + 1], kit.faceNrm[fi + 2], axisLight, fi)
        }
    }

    fun begin() { count = 0 }

    /** Queue one axis-aligned box (centre position, full sizes). [fog] 0..1 blends toward [fogColor]. */
    fun box(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, col: Color, fog: Float = 0f) {
        if (count >= maxBoxes) return
        packFaces(col, fog, axisLight)
        var w = count * kit.vertsPerBox * 4
        val cl = kit.cornerLocal
        for (v in 0 until kit.vertsPerBox) {
            val ci = kit.cornerOf[v] * 3
            verts[w++] = x + cl[ci] * sx
            verts[w++] = y + cl[ci + 1] * sy
            verts[w++] = z + cl[ci + 2] * sz
            verts[w++] = packed[kit.faceOf[v]]
        }
        count++
    }

    /**
     * Like [box] but rotated [yawDeg] about its own vertical axis (coins, pickups).
     * The face lighting is re-solved only when the yaw changes between calls, so a
     * field of coins spinning in lockstep costs about the same as static boxes.
     */
    fun boxSpin(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, yawDeg: Float, col: Color, fog: Float = 0f) {
        if (count >= maxBoxes) return
        val rad = yawDeg * (Math.PI.toFloat() / 180f)
        val c = cos(rad); val s = sin(rad)
        if (yawDeg != spinCacheYaw) {
            spinCacheYaw = yawDeg
            val fn = kit.faceNrm
            for (f in 0 until 6) {
                val fi = f * 3
                val nx = fn[fi]; val ny = fn[fi + 1]; val nz = fn[fi + 2]
                kit.lightFace(nx * c + nz * s, ny, -nx * s + nz * c, spinLight, fi)
            }
        }
        packFaces(col, fog, spinLight)
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

    private fun packFaces(col: Color, fog: Float, light: FloatArray) {
        val keep = 1f - fog
        val fr = fogColor.r * fog; val fg = fogColor.g * fog; val fb = fogColor.b * fog
        for (f in 0 until 6) {
            val fi = f * 3
            packed[f] = Color.toFloatBits(
                min(1f, col.r * light[fi]) * keep + fr,
                min(1f, col.g * light[fi + 1]) * keep + fg,
                min(1f, col.b * light[fi + 2]) * keep + fb,
                1f,
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
        Gdx.gl.glDisable(GL20.GL_BLEND)
        kit.shader.bind()
        kit.shader.setUniformMatrix("u_projViewTrans", cam.combined)
        mesh.render(kit.shader, GL20.GL_TRIANGLES, 0, n * kit.idxPerBox)
        // ModelBatch.begin() resets its own state; restore the shared baseline anyway.
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
    }

    override fun dispose() {
        mesh.dispose()
    }
}
