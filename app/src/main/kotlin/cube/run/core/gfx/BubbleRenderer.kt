package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable

/**
 * A soap bubble: a sphere whose surface is (almost) invisible face-on and
 * glows at the rim (fresnel), tinted by a hue that slides around the sphere
 * and drifts with time like a real soap film, with a small specular
 * highlight. One custom shader, one mesh, drawn blended after the opaque
 * world. [style] picks the colour rule (see data.BubbleSkins).
 */
class BubbleRenderer(mb: ModelBuilder) : Disposable {

    private val model: Model = mb.createSphere(1f, 1f, 1f, 28, 20, Material(ColorAttribute.createDiffuse(Color.WHITE)), (Usage.Position or Usage.Normal).toLong())
    private val mesh: Mesh = model.meshes.first()
    private val world = Matrix4()
    private val shader: ShaderProgram

    init {
        ShaderProgram.pedantic = false
        shader = ShaderProgram(
            """
            attribute vec3 a_position;
            attribute vec3 a_normal;
            uniform mat4 u_projViewTrans;
            uniform mat4 u_worldTrans;
            uniform vec3 u_camPos;
            varying vec3 v_normal;
            varying vec3 v_view;
            void main() {
                vec4 wp = u_worldTrans * vec4(a_position, 1.0);
                v_normal = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);
                v_view = normalize(u_camPos - wp.xyz);
                gl_Position = u_projViewTrans * wp;
            }
            """.trimIndent(),
            """
            #ifdef GL_ES
            precision mediump float;
            #endif
            uniform float u_time;
            uniform float u_hueA;
            uniform float u_hueB;
            uniform float u_sat;
            uniform float u_rim;
            uniform float u_fill;
            uniform float u_alpha;
            uniform float u_style;
            varying vec3 v_normal;
            varying vec3 v_view;
            vec3 hsv(float h, float s, float v) {
                vec3 p = abs(fract(vec3(h) + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
                return v * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), s);
            }
            void main() {
                vec3 n = normalize(v_normal);
                vec3 v = normalize(v_view);
                float ndv = abs(dot(n, v));
                float rim = pow(1.0 - ndv, u_rim);
                float band = 0.5 + 0.5 * sin(n.y * 3.0 + n.x * 2.2 + n.z * 1.3 + u_time * 1.7);
                float hue = mix(u_hueA, u_hueB, band) / 360.0;
                if (u_style > 1.5 && u_style < 2.5) hue = fract(u_time * 0.12 + band * 0.35 + n.x * 0.15);
                float flick = 1.0;
                if (u_style > 2.5) flick = 0.55 + 0.45 * step(0.0, sin(u_time * 38.0 + n.y * 7.0 + n.x * 3.0));
                vec3 col = hsv(fract(hue), u_sat, 1.0);
                vec3 l = normalize(vec3(-0.45, 0.85, 0.6));
                vec3 h = normalize(l + v);
                float spec = pow(max(dot(n, h), 0.0), 60.0);
                float a = (rim * 0.95 + u_fill + spec * 0.7) * u_alpha * flick;
                gl_FragColor = vec4(mix(col, vec3(1.0), spec * 0.8 + rim * 0.15), clamp(a, 0.0, 1.0));
            }
            """.trimIndent(),
        ).also { require(it.isCompiled) { "bubble shader: ${it.log}" } }
    }

    /**
     * Draw one bubble. Call after the opaque passes; depth-tested, not
     * depth-written, alpha-blended, both faces (the back rim doubles the glow).
     */
    fun draw(
        cam: Camera, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, yawDeg: Float, time: Float,
        hueA: Float, hueB: Float, sat: Float, rim: Float, fill: Float, alpha: Float, style: Int,
    ) {
        world.setToTranslation(x, y, z).rotate(0f, 1f, 0f, yawDeg).scale(sx, sy, sz)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shader.bind()
        shader.setUniformMatrix("u_projViewTrans", cam.combined)
        shader.setUniformMatrix("u_worldTrans", world)
        shader.setUniformf("u_camPos", cam.position.x, cam.position.y, cam.position.z)
        shader.setUniformf("u_time", time)
        shader.setUniformf("u_hueA", hueA)
        shader.setUniformf("u_hueB", hueB)
        shader.setUniformf("u_sat", sat)
        shader.setUniformf("u_rim", rim)
        shader.setUniformf("u_fill", fill)
        shader.setUniformf("u_alpha", alpha)
        shader.setUniformf("u_style", style.toFloat())
        mesh.render(shader, GL20.GL_TRIANGLES)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
    }

    override fun dispose() {
        shader.dispose()
        model.dispose()
    }
}
