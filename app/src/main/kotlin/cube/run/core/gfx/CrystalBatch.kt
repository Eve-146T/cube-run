package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import kotlin.math.cos
import kotlin.math.sin

/** Pointed, five-sided crystals; all visible collectibles share one draw call. */
class CrystalBatch(private val kit: BoxMeshKit) : Disposable {
    var terrain: TerrainHeight? = null
    private val template: FloatArray
    private val vertices = FloatArray(180 * 4 * 24)
    private val mesh = Mesh(false, vertices.size / 4, 0,
        VertexAttribute(Usage.Position, 3, "a_position"), VertexAttribute(Usage.ColorPacked, 4, "a_color"))
    private val light = FloatArray(3)
    private var used = 0

    init {
        val raw = ArrayList<Float>()
        fun ring(i: Int, y: Float, radius: Float): Vector3 {
            val a = i * (Math.PI.toFloat() * 2f / 5f)
            return Vector3(cos(a) * radius, y, sin(a) * radius)
        }
        fun face(a: Vector3, b: Vector3, c: Vector3) {
            val n = Vector3(b).sub(a).crs(Vector3(c).sub(a)).nor()
            for (v in arrayOf(a,b,c)) raw.addAll(listOf(v.x,v.y,v.z,n.x,n.y,n.z))
        }
        for (i in 0..4) {
            val a = ring(i, .18f, .30f); val b = ring(i+1, .18f, .30f)
            val c = ring(i, -.43f, .18f); val d = ring(i+1, -.43f, .18f)
            face(Vector3(0f,.55f,0f), b, a)
            face(a,b,c); face(b,d,c)
            face(Vector3(0f,-.43f,0f),c,d)
        }
        template = raw.toFloatArray()
    }

    fun begin() { used = 0 }
    fun crystal(x: Float, y0: Float, z: Float, scale: Float, yaw: Float, color: Color, fog: Float, sky: Color) {
        if (used + template.size / 6 * 4 > vertices.size) return
        val y = y0 + (terrain?.invoke(z) ?: 0f)
        val a = yaw * Math.PI.toFloat() / 180f; val c = cos(a); val s = sin(a)
        for (i in template.indices step 6) {
            val px = template[i]; val py = template[i+1]; val pz = template[i+2]
            val nx = template[i+3]; val ny = template[i+4]; val nz = template[i+5]
            kit.lightFace(nx*c+nz*s, ny, -nx*s+nz*c, light, 0)
            vertices[used++] = x + (px*c+pz*s)*scale
            vertices[used++] = y + py*scale
            vertices[used++] = z + (-px*s+pz*c)*scale
            vertices[used++] = Color.toFloatBits(
                (color.r*light[0]).coerceAtMost(1f)*(1f-fog)+sky.r*fog,
                (color.g*light[1]).coerceAtMost(1f)*(1f-fog)+sky.g*fog,
                (color.b*light[2]).coerceAtMost(1f)*(1f-fog)+sky.b*fog, 1f)
        }
    }
    fun render(cam: Camera) {
        if (used == 0) return
        mesh.setVertices(vertices, 0, used)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(true)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_BLEND)
        kit.shader.bind(); kit.shader.setUniformMatrix("u_projViewTrans", cam.combined)
        mesh.render(kit.shader, GL20.GL_TRIANGLES, 0, used/4)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
    }
    override fun dispose() = mesh.dispose()
}
