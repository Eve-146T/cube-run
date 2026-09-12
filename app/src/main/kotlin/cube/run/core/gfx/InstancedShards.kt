package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable

/** Tumbling cubes share one static mesh; the GPU transforms and lights their faces. */
internal class InstancedShards(private val kit: BoxMeshKit, capacity: Int) : Disposable {
    private val mesh = kit.newUnitMesh()
    private val data = FloatArray(capacity * 20)
    private var used = 0
    private val shader = ShaderProgram("""
        #version 300 es
        precision highp float;
        in vec3 a_position;
        in vec3 a_normal;
        in vec4 i_col0, i_col1, i_col2, i_col3, i_tint;
        uniform mat4 u_projViewTrans;
        uniform vec3 u_toL1, u_toL2, u_ambient, u_light1, u_light2;
        out vec4 v_color;
        void main() {
            mat4 m = mat4(i_col0, i_col1, i_col2, i_col3);
            vec3 n = normalize(mat3(m) * a_normal);
            vec3 light = u_ambient + max(0.0, dot(n, u_toL1))*u_light1 + max(0.0, dot(n, u_toL2))*u_light2;
            vec3 rgb = min(vec3(1.0), i_tint.rgb * light);
            v_color = vec4(floor(rgb*255.0)/255.0, floor(floor(i_tint.a*255.0)/2.0)*2.0/255.0);
            gl_Position = u_projViewTrans * (m * vec4(a_position, 1.0));
        }
    """.trimIndent(), """
        #version 300 es
        precision mediump float;
        in vec4 v_color;
        out vec4 fragColor;
        void main() { fragColor = v_color; }
    """.trimIndent())

    init {
        require(shader.isCompiled) { "instanced shard shader: ${shader.log}" }
        mesh.enableInstancedRendering(false, capacity,
            VertexAttribute(Usage.Generic, 4, "i_col0"), VertexAttribute(Usage.Generic, 4, "i_col1"),
            VertexAttribute(Usage.Generic, 4, "i_col2"), VertexAttribute(Usage.Generic, 4, "i_col3"),
            VertexAttribute(Usage.Generic, 4, "i_tint"))
    }

    fun begin() { used = 0 }
    fun add(transform: Matrix4, color: Color, alpha: Float) {
        System.arraycopy(transform.`val`, 0, data, used, 16)
        var w = used + 16
        data[w++] = color.r; data[w++] = color.g; data[w++] = color.b; data[w++] = alpha
        used = w
    }

    fun render(camera: Camera) {
        if (used == 0) return
        mesh.setInstanceData(data, 0, used)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(false)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE); Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shader.bind(); shader.setUniformMatrix("u_projViewTrans", camera.combined)
        kit.setLightUniforms(shader)
        mesh.render(shader, GL20.GL_TRIANGLES)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_DEPTH_TEST); Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    override fun dispose() { mesh.dispose(); shader.dispose() }
}
