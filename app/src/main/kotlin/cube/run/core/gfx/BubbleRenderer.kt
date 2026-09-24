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

    private val model: Model = mb.createSphere(1f, 1f, 1f, 48, 32, Material(ColorAttribute.createDiffuse(Color.WHITE)), (Usage.Position or Usage.Normal).toLong())
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
            uniform float u_styleNext;
            uniform float u_mix;
            varying vec3 v_normal;
            varying vec3 v_view;
            vec3 hsv(float h, float s, float v) {
                vec3 p = abs(fract(vec3(h) + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
                return v * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), s);
            }
            vec4 film(float style, vec3 n, float band, float rim, float spec) {
                if (style > 3.5) {
                    // Keep the void lens inside film so wardrobe style transitions blend both ends.
                    float ndv = abs(dot(n, normalize(v_view)));
                    float angle = atan(n.y, n.x);
                    float arc = smoothstep(-0.25, 0.6, sin(angle * 2.0 - u_time * 0.7));
                    float edge = pow(1.0 - ndv, 5.0);
                    float inner = exp(-abs(ndv - 0.3) * 65.0) * 0.16;
                    vec3 cold = vec3(0.68, 0.59, 0.86);
                    vec3 dark = vec3(0.008, 0.005, 0.016);
                    float light = clamp(edge * (0.45 + 0.55 * arc) + inner, 0.0, 1.0);
                    float opacity = (0.12 + edge * 0.82 + inner) * u_alpha;
                    return vec4(mix(dark, cold, light), clamp(opacity, 0.0, 0.95));
                }
                float hue = mix(u_hueA, u_hueB, band) / 360.0;
                if (style > 1.5 && style < 2.5) hue = fract(u_time * .045 + band * .35 + n.x * .15);
                float flow = .5 + .5 * sin(n.y * 5.0 + n.x * 3.0 + u_time * 2.2);
                float glow = style > 2.5 ? .80 + .20 * flow * flow : 1.0;
                vec3 col = hsv(fract(hue), u_sat, 1.0);
                float a = (rim * .88 + u_fill + spec * .55) * u_alpha * glow;
                return vec4(mix(col, vec3(1.0), spec * .8 + rim * .15), clamp(a, 0.0, 1.0));
            }
            void main() {
                vec3 n = normalize(v_normal);
                vec3 v = normalize(v_view);
                float ndv = abs(dot(n, v));
                float rim = pow(1.0 - ndv, u_rim);
                float band = 0.5 + 0.5 * sin(n.y * 3.0 + n.x * 2.2 + n.z * 1.3 + u_time * .65);
                vec3 l = normalize(vec3(-0.45, 0.85, 0.6));
                vec3 h = normalize(l + v);
                float spec = pow(max(dot(n, h), 0.0), 60.0);
                if (u_mix > .999) gl_FragColor = film(u_styleNext, n, band, rim, spec);
                else if (u_mix < .001) gl_FragColor = film(u_style, n, band, rim, spec);
                else gl_FragColor = mix(film(u_style, n, band, rim, spec), film(u_styleNext, n, band, rim, spec), u_mix);
            }
            """.trimIndent(),
        ).also { require(it.isCompiled) { "bubble shader: ${it.log}" } }
    }

    /**
     * Draw one bubble. Call after the opaque passes; depth-tested, not
     * depth-written, alpha-blended, outward faces only to avoid unsorted shell overlap.
     */
    fun draw(
        cam: Camera, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, yawDeg: Float, time: Float,
        hueA: Float, hueB: Float, sat: Float, rim: Float, fill: Float, alpha: Float, style: Int, nextStyle: Int = style, mix: Float = 0f,
    ) {
        world.setToTranslation(x, y, z).rotate(0f, 1f, 0f, yawDeg).scale(sx, sy, sz)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glCullFace(GL20.GL_BACK)
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
        shader.setUniformf("u_styleNext", nextStyle.toFloat())
        shader.setUniformf("u_mix", mix)
        mesh.render(shader, GL20.GL_TRIANGLES)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
    }

    override fun dispose() {
        shader.dispose()
        model.dispose()
    }
}
