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
import kotlin.math.cos
import kotlin.math.sin

/** The same twelve-sided coin, with a shared mesh instead of 72 rebuilt vertices per coin. */
internal class InstancedPrisms(private val kit: BoxMeshKit, sides: Int, capacity: Int) : Disposable {
    private val mesh = Mesh(true, sides * 6, sides * 6 + (sides - 2) * 6,
        VertexAttribute(Usage.Position, 3, "a_position"),
        VertexAttribute(Usage.Normal, 3, "a_normal"))
    private val data = FloatArray(capacity * 16)
    private var used = 0
    private val shader = ShaderProgram("""
        #version 300 es
        precision highp float;
        in vec3 a_position, a_normal;
        in vec4 i_center, i_shape, i_tint, i_fog;
        uniform mat4 u_projViewTrans;
        uniform vec3 u_toL1, u_toL2, u_ambient, u_light1, u_light2;
        out vec4 v_color;
        void main() {
            float c = i_center.w, s = i_shape.w;
            vec3 p = a_position * vec3(i_shape.x, i_shape.x, i_shape.y);
            vec3 world = vec3(i_center.x + p.x*c + p.z*s, i_center.y + p.y, i_center.z - p.x*s + p.z*c);
            vec3 rawNormal = vec3(a_normal.x*c + a_normal.z*s, a_normal.y, -a_normal.x*s + a_normal.z*c);
            vec3 n = normalize(rawNormal);
            vec3 light = u_ambient + max(0.0, dot(n,u_toL1))*u_light1 + max(0.0, dot(n,u_toL2))*u_light2;
            bool cap = a_normal.z != 0.0;
            float d = max(0.0, rawNormal.x*0.35 + rawNormal.z*0.94);
            float strength = cap ? 0.86 + 0.5*(d*d*d) : 0.74;
            float floorLight = cap ? 0.8 : 0.55;
            vec3 rgb = min(vec3(1.0), i_tint.rgb * strength * max(vec3(floorLight), light)) * i_shape.z + i_fog.rgb;
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
        require(shader.isCompiled) { "instanced coin shader: ${shader.log}" }
        val vertices = FloatArray(sides * 6 * 6)
        val indices = ShortArray(sides * 6 + (sides - 2) * 6)
        var v = 0
        fun vertex(k: Int, z: Float, nx: Float, ny: Float, nz: Float) {
            val a = (k + 0.5f) * (2f * Math.PI.toFloat() / sides)
            vertices[v++] = cos(a); vertices[v++] = sin(a); vertices[v++] = z
            vertices[v++] = nx; vertices[v++] = ny; vertices[v++] = nz
        }
        var w = 0
        for (k in 0 until sides) {
            val a = (k + 1f) * (2f * Math.PI.toFloat() / sides)
            val nx = cos(a); val ny = sin(a); val k1 = (k + 1) % sides
            vertex(k, .5f, nx, ny, 0f); vertex(k1, .5f, nx, ny, 0f)
            vertex(k1, -.5f, nx, ny, 0f); vertex(k, -.5f, nx, ny, 0f)
            val q = k * 4
            indices[w++] = q.toShort(); indices[w++] = (q + 3).toShort(); indices[w++] = (q + 2).toShort()
            indices[w++] = q.toShort(); indices[w++] = (q + 2).toShort(); indices[w++] = (q + 1).toShort()
        }
        for (k in 0 until sides) vertex(k, .5f, 0f, 0f, 1f)
        for (k in 0 until sides) vertex(k, -.5f, 0f, 0f, -1f)
        val f = sides * 4; val b = sides * 5
        for (k in 1 until sides - 1) {
            indices[w++] = f.toShort(); indices[w++] = (f + k).toShort(); indices[w++] = (f + k + 1).toShort()
        }
        for (k in 1 until sides - 1) {
            indices[w++] = b.toShort(); indices[w++] = (b + k + 1).toShort(); indices[w++] = (b + k).toShort()
        }
        mesh.setVertices(vertices); mesh.setIndices(indices)
        mesh.enableInstancedRendering(false, capacity,
            VertexAttribute(Usage.Generic, 4, "i_center"), VertexAttribute(Usage.Generic, 4, "i_shape"),
            VertexAttribute(Usage.Generic, 4, "i_tint"), VertexAttribute(Usage.Generic, 4, "i_fog"))
    }

    fun begin() { used = 0 }

    fun add(x: Float, y: Float, z: Float, radius: Float, thickness: Float, c: Float, s: Float,
            color: Color, fog: Float, fogColor: Color, opacity: Float) {
        var w = used
        data[w++] = x; data[w++] = y; data[w++] = z; data[w++] = c
        data[w++] = radius; data[w++] = thickness; data[w++] = 1f - fog; data[w++] = s
        data[w++] = color.r; data[w++] = color.g; data[w++] = color.b; data[w++] = opacity
        data[w++] = fogColor.r * fog; data[w++] = fogColor.g * fog; data[w++] = fogColor.b * fog; data[w++] = 0f
        used = w
    }

    fun render(camera: Camera, translucent: Boolean) {
        if (used == 0) return
        mesh.setInstanceData(data, 0, used)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(true); Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        if (translucent) {
            Gdx.gl.glEnable(GL20.GL_BLEND); Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        } else Gdx.gl.glDisable(GL20.GL_BLEND)
        shader.bind(); shader.setUniformMatrix("u_projViewTrans", camera.combined)
        kit.setLightUniforms(shader)
        mesh.render(shader, GL20.GL_TRIANGLES)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_DEPTH_TEST); Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    override fun dispose() { mesh.dispose(); shader.dispose() }
}
