package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A real 3D black hole (and the star it becomes): a black event-horizon sphere
 * with a thin photon rim; a tilted accretion disk whose hot inner edge streams
 * round at Kepler speeds and burns brighter on the side racing towards the camera;
 * a camera-facing lensing halo, where the far side of the disk is bent up over
 * the shadow; and an expanding supernova shell. Additive glow, depth-tested, so
 * the horizon hides the far half of the disk and the cube can pass in front.
 */
class VoidRenderer(mb: ModelBuilder) : Disposable {

    private val sphereModel: Model = mb.createSphere(2f, 2f, 2f, 48, 32,
        Material(ColorAttribute.createDiffuse(Color.WHITE)), (Usage.Position or Usage.Normal).toLong())
    private val sphere: Mesh = sphereModel.meshes.first()
    private val disk: Mesh = annulus(128, 10)
    private val quad: Mesh = Mesh(true, 4, 6, VertexAttribute.Position()).apply {
        setVertices(floatArrayOf(-1f, -1f, 0f, 1f, -1f, 0f, 1f, 1f, 0f, -1f, 1f, 0f))
        setIndices(shortArrayOf(0, 1, 2, 0, 2, 3))
    }
    private val world = Matrix4()
    private val right = Vector3()
    private val up = Vector3()

    private val horizonShader = program("""
        attribute vec3 a_position;
        attribute vec3 a_normal;
        uniform mat4 u_projViewTrans;
        uniform mat4 u_worldTrans;
        uniform vec3 u_camPos;
        varying float v_rim;
        void main() {
            vec4 wp = u_worldTrans * vec4(a_position, 1.0);
            vec3 n = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);
            v_rim = 1.0 - abs(dot(n, normalize(u_camPos - wp.xyz)));
            gl_Position = u_projViewTrans * wp;
        }
    """, """
        uniform vec3 u_rimColor;
        varying float v_rim;
        void main() {
            // Pure black, but a hairline of light clings to the very edge (the photon sphere).
            float edge = pow(v_rim, 9.0);
            gl_FragColor = vec4(u_rimColor * edge, 1.0);
        }
    """)

    private val diskShader = program("""
        attribute vec3 a_position;
        attribute vec2 a_texCoord0;
        uniform mat4 u_projViewTrans;
        uniform mat4 u_worldTrans;
        uniform vec3 u_camPos;
        varying vec2 v_polar;
        varying float v_approach;
        void main() {
            vec4 wp = u_worldTrans * vec4(a_position, 1.0);
            // Orbital velocity is tangential (counter-clockwise seen from above).
            vec3 tangent = normalize((u_worldTrans * vec4(-a_position.z, 0.0, a_position.x, 0.0)).xyz);
            v_approach = dot(tangent, normalize(u_camPos - wp.xyz));
            v_polar = a_texCoord0;
            gl_Position = u_projViewTrans * wp;
        }
    """, """
        uniform float u_time;
        uniform float u_heat;
        uniform float u_alpha;
        uniform float u_spin;
        varying vec2 v_polar;
        varying float v_approach;
        void main() {
            float r = v_polar.x;             // 0 inner edge .. 1 outer edge
            float a = v_polar.y;             // angle, radians
            // Kepler: the inner disk laps the outer one. Streaks are sheared into spirals.
            // The inner disk laps the outer one. Two rigid layers blended by radius give that shear
            // without winding the arms into ever tighter rings as time goes on.
            float fast = u_time * u_spin * 2.2;
            float slow = u_time * u_spin * 0.8;
            float s1 = mix(sin(a * 3.0 - fast + r * 4.0), sin(a * 3.0 - slow + r * 4.0 + 1.3), r);
            float s2 = mix(sin(a * 5.0 - fast * 1.3 + r * 6.5 + 1.7), sin(a * 5.0 - slow * 1.3 + r * 6.5 + 0.4), r);
            float s3 = mix(sin(a * 2.0 - fast * 0.7 - r * 2.5 + 4.1), sin(a * 2.0 - slow * 0.7 - r * 2.5 + 2.2), r);
            float streak = 0.62 + 0.2 * s1 + 0.12 * s2 + 0.14 * s3;
            float temp = pow(1.0 - r, 2.2);
            vec3 cool = mix(vec3(0.34, 0.16, 0.95), vec3(1.0, 0.36, 0.12), u_heat);
            vec3 warm = mix(vec3(0.86, 0.66, 1.0), vec3(1.0, 0.78, 0.28), u_heat);
            vec3 col = mix(cool, warm, smoothstep(0.1, 0.75, temp));
            col = mix(col, vec3(1.0), smoothstep(0.7, 1.0, temp) * 0.85);
            float beam = clamp(1.0 + 0.9 * v_approach, 0.25, 2.2);
            float edge = smoothstep(0.0, 0.05, r) * (1.0 - smoothstep(0.35, 1.0, r));
            float light = (0.12 + 1.9 * temp) * streak * beam * edge * u_alpha;
            gl_FragColor = vec4(col * light, 1.0);
        }
    """)

    private val haloShader = program("""
        attribute vec3 a_position;
        uniform mat4 u_projViewTrans;
        uniform vec3 u_center;
        uniform vec3 u_right;
        uniform vec3 u_up;
        uniform float u_size;
        varying vec2 v_uv;
        void main() {
            v_uv = a_position.xy * u_size;
            vec3 wp = u_center + u_right * v_uv.x + u_up * v_uv.y;
            gl_Position = u_projViewTrans * vec4(wp, 1.0);
        }
    """, """
        uniform float u_time;
        uniform float u_heat;
        uniform float u_alpha;
        uniform float u_radius;
        uniform float u_tilt;
        varying vec2 v_uv;
        void main() {
            float d = length(v_uv) / u_radius;
            if (d < 1.0) discard;
            float ang = atan(v_uv.y, v_uv.x);
            // The far side of the disk, lensed into a ring: bright over the top, thinner beneath.
            float over = 0.5 + 0.5 * sin(ang + u_tilt); // 1 straight over the top
            float band = exp(-pow((d - 1.18) / (0.12 + 0.16 * over), 2.0));
            float photon = exp(-pow((d - 1.035) / 0.025, 2.0));
            float swirl = 0.75 + 0.25 * sin(ang * 6.0 - u_time * 2.4 + d * 9.0);
            float glow = exp(-(d - 1.0) * 2.2) * 0.18;
            vec3 cool = mix(vec3(0.62, 0.45, 1.0), vec3(1.0, 0.62, 0.22), u_heat);
            vec3 col = cool * (band * (0.35 + 0.95 * over) * swirl + glow) + vec3(1.0, 0.96, 1.0) * photon * 0.9;
            gl_FragColor = vec4(col * u_alpha, 1.0);
        }
    """)

    private val glowShader = program("""
        attribute vec3 a_position;
        uniform mat4 u_projViewTrans;
        uniform vec3 u_center;
        uniform vec3 u_right;
        uniform vec3 u_up;
        uniform float u_size;
        varying vec2 v_uv;
        void main() {
            v_uv = a_position.xy;
            vec3 wp = u_center + (u_right * a_position.x + u_up * a_position.y) * u_size;
            gl_Position = u_projViewTrans * vec4(wp, 1.0);
        }
    """, """
        uniform vec3 u_color;
        uniform float u_alpha;
        uniform float u_gas;
        uniform float u_seed;
        uniform float u_time;
        varying vec2 v_uv;
        void main() {
            float d = length(v_uv);
            // A soft gaussian glow; as gas it is torn into slowly turning wisps.
            float core = exp(-d * d * 5.0);
            float a = atan(v_uv.y, v_uv.x);
            float wisp = 0.55 + 0.25 * sin(a * 3.0 + u_seed + u_time * 0.4 + d * 5.0) + 0.2 * sin(a * 5.0 - u_seed * 1.7 - u_time * 0.3 + d * 8.0);
            float light = mix(core, core * wisp, u_gas) * (1.0 - smoothstep(0.8, 1.0, d));
            gl_FragColor = vec4(u_color * light * u_alpha, 1.0);
        }
    """)

    private val shellShader = program("""
        attribute vec3 a_position;
        attribute vec3 a_normal;
        uniform mat4 u_projViewTrans;
        uniform mat4 u_worldTrans;
        uniform vec3 u_camPos;
        varying float v_ndv;
        varying vec3 v_n;
        void main() {
            vec4 wp = u_worldTrans * vec4(a_position, 1.0);
            v_n = a_normal;
            vec3 n = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);
            v_ndv = abs(dot(n, normalize(u_camPos - wp.xyz)));
            gl_Position = u_projViewTrans * wp;
        }
    """, """
        uniform vec3 u_color;
        uniform float u_alpha;
        uniform float u_time;
        varying float v_ndv;
        varying vec3 v_n;
        void main() {
            // A blast front: brightest where you look along its surface, rippling with filaments.
            // A thin shock front, not a filled ball: only the grazing edge glows.
            float rim = pow(1.0 - v_ndv, 5.0);
            float fil = 0.6 + 0.4 * sin(v_n.x * 17.0 + v_n.y * 11.0 - u_time * 3.0) * sin(v_n.z * 13.0 + u_time * 2.0);
            gl_FragColor = vec4(u_color * rim * fil * u_alpha, 1.0);
        }
    """)

    /**
     * The black hole at [center], horizon radius [r]. [tilt] is the disk's tilt from edge-on
     * (degrees, towards the camera), [heat] 0 violet → 1 gold-hot, [spin] how fast it turns.
     */
    fun drawHole(cam: Camera, center: Vector3, r: Float, time: Float, heat: Float, spin: Float, diskAlpha: Float, haloAlpha: Float, tilt: Float) {
        if (r <= 0.001f) return
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDepthMask(true)
        // The horizon: opaque, writes depth so it hides the far half of the disk.
        world.setToTranslation(center).scale(r, r, r)
        horizonShader.bind()
        horizonShader.setUniformMatrix("u_projViewTrans", cam.combined)
        horizonShader.setUniformMatrix("u_worldTrans", world)
        horizonShader.setUniformf("u_camPos", cam.position)
        horizonShader.setUniformf("u_rimColor", 0.85f + 0.15f * heat, 0.8f, 1f)
        sphere.render(horizonShader, GL20.GL_TRIANGLES)

        Gdx.gl.glDepthMask(false)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE)
        if (diskAlpha > 0.003f) {
            // Face the camera's yaw, then tip the disk [tilt] degrees open towards it.
            val yaw = Math.toDegrees(kotlin.math.atan2((cam.position.x - center.x).toDouble(), (cam.position.z - center.z).toDouble())).toFloat()
            world.setToTranslation(center).rotate(Vector3.Y, yaw).rotate(Vector3.X, tilt).rotate(Vector3.Z, -9f).scale(r, r, r)
            diskShader.bind()
            diskShader.setUniformMatrix("u_projViewTrans", cam.combined)
            diskShader.setUniformMatrix("u_worldTrans", world)
            diskShader.setUniformf("u_camPos", cam.position)
            diskShader.setUniformf("u_time", time)
            diskShader.setUniformf("u_heat", heat)
            diskShader.setUniformf("u_alpha", diskAlpha)
            diskShader.setUniformf("u_spin", spin)
            disk.render(diskShader, GL20.GL_TRIANGLES)
        }
        if (haloAlpha > 0.003f) {
            right.set(cam.direction).crs(cam.up).nor()
            up.set(right).crs(cam.direction).nor()
            haloShader.bind()
            haloShader.setUniformMatrix("u_projViewTrans", cam.combined)
            haloShader.setUniformf("u_center", center)
            haloShader.setUniformf("u_right", right)
            haloShader.setUniformf("u_up", up)
            haloShader.setUniformf("u_size", r * 2.4f)
            haloShader.setUniformf("u_radius", r)
            haloShader.setUniformf("u_time", time)
            haloShader.setUniformf("u_heat", heat)
            haloShader.setUniformf("u_alpha", haloAlpha)
            haloShader.setUniformf("u_tilt", -0.16f) // match the disk's slight roll
            quad.render(haloShader, GL20.GL_TRIANGLES)
        }
        restore()
    }

    /**
     * A camera-facing glow of radius [size] at [center]: a hot core, a star, or (with [gas] = 1)
     * a torn wisp of nebula. Additive, depth-tested.
     */
    fun drawGlow(cam: Camera, center: Vector3, size: Float, color: Color, alpha: Float, gas: Float = 0f, seed: Float = 0f, time: Float = 0f) {
        if (alpha <= 0.003f || size <= 0f) return
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE)
        right.set(cam.direction).crs(cam.up).nor()
        up.set(right).crs(cam.direction).nor()
        glowShader.bind()
        glowShader.setUniformMatrix("u_projViewTrans", cam.combined)
        glowShader.setUniformf("u_center", center)
        glowShader.setUniformf("u_right", right)
        glowShader.setUniformf("u_up", up)
        glowShader.setUniformf("u_size", size)
        glowShader.setUniformf("u_color", color.r, color.g, color.b)
        glowShader.setUniformf("u_alpha", alpha)
        glowShader.setUniformf("u_gas", gas)
        glowShader.setUniformf("u_seed", seed)
        glowShader.setUniformf("u_time", time)
        quad.render(glowShader, GL20.GL_TRIANGLES)
        restore()
    }

    /** The supernova's blast front: a glowing shell of radius [r]. */
    fun drawShell(cam: Camera, center: Vector3, r: Float, color: Color, alpha: Float, time: Float) {
        if (alpha <= 0.003f || r <= 0f) return
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE)
        world.setToTranslation(center).rotate(Vector3.Y, time * 20f).scale(r, r, r)
        shellShader.bind()
        shellShader.setUniformMatrix("u_projViewTrans", cam.combined)
        shellShader.setUniformMatrix("u_worldTrans", world)
        shellShader.setUniformf("u_camPos", cam.position)
        shellShader.setUniformf("u_color", color.r, color.g, color.b)
        shellShader.setUniformf("u_alpha", alpha)
        shellShader.setUniformf("u_time", time)
        sphere.render(shellShader, GL20.GL_TRIANGLES)
        restore()
    }

    private fun restore() {
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDepthMask(true)
    }

    override fun dispose() {
        horizonShader.dispose(); diskShader.dispose(); haloShader.dispose(); shellShader.dispose(); glowShader.dispose()
        sphereModel.dispose(); disk.dispose(); quad.dispose()
    }

    private companion object {
        const val INNER = 1.45f
        const val OUTER = 4.6f

        fun program(vertex: String, fragment: String): ShaderProgram {
            ShaderProgram.pedantic = false
            val precision = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
            return ShaderProgram(vertex.trimIndent(), precision + fragment.trimIndent())
                .also { require(it.isCompiled) { "void shader: ${it.log}" } }
        }

        /** A flat ring in the XZ plane, [INNER]..[OUTER] horizon radii; uv = (radial 0..1, angle). */
        fun annulus(segments: Int, rings: Int): Mesh {
            val verts = FloatArray((segments + 1) * (rings + 1) * 5)
            var v = 0
            for (s in 0..segments) {
                val a = s * 2f * PI.toFloat() / segments
                for (k in 0..rings) {
                    val u = k / rings.toFloat()
                    val rr = INNER + (OUTER - INNER) * u
                    verts[v++] = cos(a) * rr; verts[v++] = 0f; verts[v++] = sin(a) * rr
                    verts[v++] = u; verts[v++] = a
                }
            }
            val idx = ShortArray(segments * rings * 6)
            var i = 0
            for (s in 0 until segments) for (k in 0 until rings) {
                val a = (s * (rings + 1) + k).toShort(); val b = (a + 1).toShort()
                val c = ((s + 1) * (rings + 1) + k).toShort(); val d = (c + 1).toShort()
                idx[i++] = a; idx[i++] = c; idx[i++] = b; idx[i++] = b; idx[i++] = c; idx[i++] = d
            }
            return Mesh(true, verts.size / 5, idx.size, VertexAttribute.Position(), VertexAttribute.TexCoords(0)).apply {
                setVertices(verts); setIndices(idx)
            }
        }
    }
}
