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
import kotlin.math.sin
import kotlin.math.min

/** Rounded capsule with a genuine centre seam: one red hemisphere/body, one white. */
class CapsuleBatch(private val kit: BoxMeshKit) : Disposable {
    var terrain: TerrainHeight? = null
    private val template: FloatArray
    private val mesh: Mesh
    private val vertices: FloatArray
    private val light = FloatArray(3)
    private var used = 0

    init {
        val rings = ArrayList<Pair<Float, Float>>()
        for (i in 0..5) {
            val a = i * Math.PI.toFloat() / 10f
            rings.add((-.3f - .28f * cos(a)) to (.28f * sin(a)))
        }
        rings.add(0f to .28f); rings.add(.3f to .28f)
        for (i in 1..5) {
            val a = i * Math.PI.toFloat() / 10f
            rings.add((.3f + .28f * sin(a)) to (.28f * cos(a)))
        }
        val raw = ArrayList<Float>()
        fun vertex(r: Int, sector: Int, red: Boolean) {
            val (x, radius) = rings[r]
            val a = sector * (Math.PI.toFloat() / 8f)
            val y = radius * cos(a); val z = radius * sin(a)
            val nx = when { x < -.3f -> (x + .3f) / .28f; x > .3f -> (x - .3f) / .28f; else -> 0f }
            raw.addAll(listOf(x, y, z, nx, y / .28f, z / .28f, if (red) 1f else 0f))
        }
        for (r in 0 until rings.lastIndex) for (j in 0 until 16) {
            val red = rings[r + 1].first <= 0f
            vertex(r,j,red); vertex(r+1,j+1,red); vertex(r+1,j,red)
            vertex(r,j,red); vertex(r,j+1,red); vertex(r+1,j+1,red)
        }
        template = raw.toFloatArray()
        val capacity = template.size / 7 * 12
        vertices = FloatArray(capacity * 4)
        mesh = Mesh(false, capacity, 0,
            VertexAttribute(Usage.Position, 3, "a_position"), VertexAttribute(Usage.ColorPacked, 4, "a_color"))
    }
    fun begin() { used = 0 }

    fun pill(x: Float, y0: Float, z: Float, scale: Float, yaw: Float, fog: Float, sky: Color) {
        if (used + template.size / 7 * 4 > vertices.size) return
        val y = y0 + (terrain?.invoke(z) ?: 0f)
        val a = yaw * Math.PI.toFloat() / 180f
        val c = cos(a); val s = sin(a)
        for (i in template.indices step 7) {
            val px = template[i]; val py = template[i+1]; val pz = template[i+2]
            val nx = template[i+3]; val ny = template[i+4]; val nz = template[i+5]
            kit.lightFace(nx*c+nz*s, ny, -nx*s+nz*c, light, 0)
            val red = template[i+6] > .5f
            val keep = 1f-fog
            val color = Color.toFloatBits(
                min(1f, light[0] * (if (red) .96f else .97f)) * keep + sky.r*fog,
                min(1f, light[1] * (if (red) .06f else .97f)) * keep + sky.g*fog,
                min(1f, light[2] * (if (red) .12f else 1f)) * keep + sky.b*fog, 1f)
            vertices[used++] = x + (px*c+pz*s) * scale
            vertices[used++] = y + py * scale
            vertices[used++] = z + (-px*s+pz*c) * scale
            vertices[used++] = color
        }
    }
    fun render(camera: Camera) {
        if (used == 0) return
        mesh.setVertices(vertices, 0, used)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(true)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_BLEND)
        kit.shader.bind(); kit.shader.setUniformMatrix("u_projViewTrans", camera.combined)
        mesh.render(kit.shader, GL20.GL_TRIANGLES, 0, used/4)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
    }
    override fun dispose() { mesh.dispose() }
}
