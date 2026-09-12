package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable

/** Static cube geometry, with only transform/color data uploaded for each visible box. */
internal class InstancedWorldBoxes(private val kit: BoxMeshKit, capacity: Int) : Disposable {
    private val mesh = kit.newUnitMesh()
    private val data = FloatArray(capacity * 20)
    private var used = 0
    private val shader = ShaderProgram("""
        #version 300 es
        precision highp float;
        in vec3 a_position;
        in vec3 a_normal;
        in vec4 i_center;
        in vec4 i_size;
        in vec4 i_surface;
        in vec4 i_tint;
        in vec4 i_fog;
        uniform mat4 u_projViewTrans;
        uniform vec3 u_toL1, u_toL2, u_ambient, u_light1, u_light2;
        out vec4 v_color;
        void main() {
            vec3 p = a_position * i_size.xyz;
            float c = i_center.w, s = i_size.w;
            vec3 world = i_center.xyz + vec3(p.x*c + p.z*s,
                p.y + mix(i_surface.x, i_surface.y, a_position.z + 0.5), -p.x*s + p.z*c);
            float slope = (i_surface.y - i_surface.x) / i_size.z;
            vec3 n = vec3(a_normal.x, a_normal.y, a_normal.z - slope*a_normal.y);
            n = normalize(vec3(n.x*c + n.z*s, n.y, -n.x*s + n.z*c));
            vec3 light = u_ambient + max(0.0, dot(n, u_toL1))*u_light1 + max(0.0, dot(n, u_toL2))*u_light2;
            vec3 rgb = min(vec3(1.0), i_tint.rgb * light) * (1.0-i_surface.z) + i_fog.rgb * i_surface.z;
            // Match the existing packed vertex colors, including its even alpha byte.
            v_color = vec4(floor(rgb*255.0)/255.0, floor(floor(i_tint.a*255.0)/2.0)*2.0/255.0);
            gl_Position = u_projViewTrans * vec4(world, 1.0);
        }
    """.trimIndent(), """
        #version 300 es
        precision mediump float;
        in vec4 v_color;
        out vec4 fragColor;
        void main() { fragColor = v_color; }
    """.trimIndent())

    init {
        require(shader.isCompiled) { "instanced box shader: ${shader.log}" }
        mesh.enableInstancedRendering(false, capacity,
            VertexAttribute(Usage.Generic, 4, "i_center"),
            VertexAttribute(Usage.Generic, 4, "i_size"),
            VertexAttribute(Usage.Generic, 4, "i_surface"),
            VertexAttribute(Usage.Generic, 4, "i_tint"),
            VertexAttribute(Usage.Generic, 4, "i_fog"))
    }

    fun begin() { used = 0 }

    fun add(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, c: Float, s: Float,
            back: Float, front: Float, color: Color, fog: Float, fogColor: Color, opacity: Float) {
        var w = used
        data[w++] = x; data[w++] = y; data[w++] = z; data[w++] = c
        data[w++] = sx; data[w++] = sy; data[w++] = sz; data[w++] = s
        data[w++] = back; data[w++] = front; data[w++] = fog; data[w++] = 0f
        data[w++] = color.r; data[w++] = color.g; data[w++] = color.b; data[w++] = opacity
        data[w++] = fogColor.r; data[w++] = fogColor.g; data[w++] = fogColor.b; data[w++] = 0f
        used = w
    }

    fun render(camera: Camera, translucent: Boolean) {
        if (used == 0) return
        mesh.setInstanceData(data, 0, used)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(true)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        if (translucent) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        } else Gdx.gl.glDisable(GL20.GL_BLEND)
        shader.bind(); shader.setUniformMatrix("u_projViewTrans", camera.combined)
        kit.setLightUniforms(shader)
        mesh.render(shader, GL20.GL_TRIANGLES)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_DEPTH_TEST); Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    override fun dispose() { mesh.dispose(); shader.dispose() }
}
