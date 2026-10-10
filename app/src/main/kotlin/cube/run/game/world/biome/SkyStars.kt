package cube.run.game.world.biome

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Stars retain their original fans and sparkles; twinkle and alpha-byte quantization run on the GPU. */
internal class SkyStars(stars: FloatArray, col: Color, base: Float, amplitude: Float,
                        gain: Float, sparkleEvery: Int) : Disposable {
    private val mesh: Mesh
    private val shader = ImmediateModeRenderer20.createDefaultShader(false, true, 0).let { stock ->
        val vertex = stock.vertexShaderSource
            .replace("void main()", """
                attribute vec4 a_twinkle;
                uniform float u_time, u_alpha, u_frequency;
                void main()
            """.trimIndent())
            .replace("v_col = a_color;", """
                v_col = a_color;
                float a = u_alpha * (a_twinkle.y + a_twinkle.z * sin(u_time * u_frequency + a_twinkle.x)) * a_twinkle.w;
                float byteAlpha = floor(floor(a * 255.0) / 2.0) * 2.0;
                v_col.a = a <= 0.004 ? 0.0 : (a_color.a > 0.0 ? byteAlpha / 255.0 : 0.0);
            """.trimIndent())
        ShaderProgram(vertex, stock.fragmentShaderSource).also { stock.dispose(); require(it.isCompiled) { it.log } }
    }
    init {
        val vertices = ArrayList<Float>()
        var phase = 0f
        var starGain = gain
        fun vertex(x: Float, y: Float, inner: Boolean) {
            vertices.add(x); vertices.add(y); vertices.add(0f)
            vertices.add(Color.toFloatBits(col.r, col.g, col.b, if (inner) 1f else 0f))
            vertices.add(phase); vertices.add(base); vertices.add(amplitude); vertices.add(starGain)
        }
        for (i in 0 until stars.size / 4) {
            val u = stars[i * 4]; val v = stars[i * 4 + 1]
            val sparkle = sparkleEvery > 0 && i % sparkleEvery == 0
            val r = stars[i * 4 + 2] * if (sparkle) 4f else 2f
            phase = stars[i * 4 + 3]; starGain = if (sparkle) 1f else gain
            if (sparkle) {
                val w = r * .18f
                vertex(u - w, v, true); vertex(u + w, v, true); vertex(u, v + r, false)
                vertex(u - w, v, true); vertex(u + w, v, true); vertex(u, v - r, false)
                vertex(u, v - w, true); vertex(u, v + w, true); vertex(u + r, v, false)
                vertex(u, v - w, true); vertex(u, v + w, true); vertex(u - r, v, false)
            } else {
                var px = u + r; var py = v
                for (j in 1..10) {
                    val x = u + cos(j * 2f * PI.toFloat() / 10) * r
                    val y = v + sin(j * 2f * PI.toFloat() / 10) * r
                    vertex(u, v, true); vertex(px, py, false); vertex(x, y, false)
                    px = x; py = y
                }
            }
        }
        mesh = Mesh(Mesh.VertexDataType.VertexBufferObject, true, vertices.size / 8, 0,
            VertexAttribute(Usage.Position, 3, "a_position"), VertexAttribute(Usage.ColorPacked, 4, "a_color"),
            VertexAttribute(Usage.Generic, 4, "a_twinkle"))
        mesh.setVertices(vertices.toFloatArray())
    }
    fun render(matrix: Matrix4, time: Float, alpha: Float, frequency: Float) {
        shader.bind(); shader.setUniformMatrix("u_projModelView", matrix)
        shader.setUniformf("u_time", time); shader.setUniformf("u_alpha", alpha); shader.setUniformf("u_frequency", frequency)
        mesh.render(shader, GL20.GL_TRIANGLES)
    }
    fun prepare() { shader.bind(); mesh.bind(shader); mesh.unbind(shader) }
    override fun dispose() { mesh.dispose(); shader.dispose() }
}
