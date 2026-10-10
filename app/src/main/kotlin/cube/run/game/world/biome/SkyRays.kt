package cube.run.game.world.biome

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import kotlin.math.cos
import kotlin.math.sin

/** The sun's ray fan is static; only its matrix and quantized colour change. */
internal class SkyRays : Disposable {
    private class Fan(val radius: Float, val count: Int, val width: Float) {
        val mesh = Mesh(Mesh.VertexDataType.VertexBufferObject, true, count * 3, 0,
            VertexAttribute(Usage.Position, 3, "a_position"), VertexAttribute(Usage.Generic, 1, "a_alpha"))
        init {
            val data = FloatArray(count * 12)
            val step = 6.2832f / count
            for (i in 0 until count) {
                val a0 = i * step; val a1 = a0 + step * width
                val p = i * 12
                data[p + 3] = 1f
                data[p + 4] = radius * cos(a0); data[p + 5] = radius * sin(a0)
                data[p + 8] = radius * cos(a1); data[p + 9] = radius * sin(a1)
            }
            mesh.setVertices(data)
        }
    }
    private val fans = ArrayList<Fan>()
    private val matrix = Matrix4()
    private val transform = Matrix4()
    private val color = Color()
    private val shader = ImmediateModeRenderer20.createDefaultShader(false, true, 0).let { stock ->
        val vertex = stock.vertexShaderSource
            .replace("attribute vec4 a_color;", "uniform vec4 u_color; attribute float a_alpha;")
            .replace("v_col = a_color;", "v_col = vec4(u_color.rgb, u_color.a * a_alpha);")
        ShaderProgram(vertex, stock.fragmentShaderSource).also { stock.dispose(); require(it.isCompiled) { it.log } }
    }
    private fun fan(radius: Float, count: Int, width: Float) =
        fans.firstOrNull { it.radius == radius && it.count == count && it.width == width }
            ?: Fan(radius, count, width).also { fans.add(it) }
    fun prepare(radius: Float, count: Int, width: Float) {
        shader.bind()
        fan(radius, count, width).mesh.let { it.bind(shader); it.unbind(shader) }
    }

    fun render(projection: Matrix4, x: Float, y: Float, z: Float, radius: Float, count: Int,
               angle: Float, col: Color, alpha: Float, width: Float) {
        val fan = fan(radius, count, width)
        transform.setToTranslation(x, y, z).rotate(Vector3.Z, angle)
        matrix.set(projection).mul(transform)
        // Same packed channel values as ShapeRenderer, including its even alpha byte.
        color.set((col.r * 255f).toInt() / 255f, (col.g * 255f).toInt() / 255f,
            (col.b * 255f).toInt() / 255f, ((alpha * 255f).toInt() and 254) / 255f)
        shader.bind(); shader.setUniformMatrix("u_projModelView", matrix); shader.setUniformf("u_color", color)
        fan.mesh.render(shader, GL20.GL_TRIANGLES)
    }

    override fun dispose() { for (fan in fans) fan.mesh.dispose(); shader.dispose() }
}
