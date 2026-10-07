package cube.run.game.space

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One static mesh per trip; the vertex shader animates the original twinkles and sparkles. */
internal class SpaceBackdrop : Disposable {
    private val mesh = Mesh(true, 5000, 0,
        VertexAttribute(Usage.Generic, 4, "a_shape"),
        VertexAttribute(Usage.Generic, 4, "a_motion"))
    private val shader = ShaderProgram("""
        attribute vec4 a_shape, a_motion;
        uniform mat4 u_projViewTrans;
        uniform float u_time, u_alpha;
        uniform vec3 u_star, u_neon, u_band;
        varying vec4 v_color;
        void main() {
            float type = a_motion.y;
            float tw = 1.0;
            vec2 local = a_shape.zw;
            if (type > 0.5 && type < 1.5) tw = 0.85 + 0.15*sin(u_time*1.1+a_motion.x*5.0);
            if (type > 1.5) {
                tw = 0.55 + 0.45*sin(u_time*2.3+a_motion.x*4.0);
                float spin = u_time*0.3 + a_motion.x;
                float c = cos(spin), s = sin(spin);
                local = vec2(c*local.x-s*local.y,s*local.x+c*local.y)*(0.7+0.3*tw);
            }
            vec3 rgb = type < 0.5 ? u_band : (a_motion.w > 0.5 ? u_neon : u_star);
            float alpha = a_motion.z*tw*u_alpha;
            v_color = vec4(floor(rgb*255.0)/255.0,floor(floor(alpha*255.0)/2.0)*2.0/254.0);
            gl_Position = u_projViewTrans*vec4(a_shape.xy+local,0.0,1.0);
        }
    """.trimIndent(), """
        #ifdef GL_ES
        precision mediump float;
        #endif
        varying vec4 v_color;
        void main() { gl_FragColor = v_color; }
    """.trimIndent())
    private val band = Color()
    private val projectionPlane = Matrix4()
    private var count = 0

    init { require(shader.isCompiled) { "space backdrop shader: ${shader.log}" } }

    fun build(stars: FloatArray, blobs: FloatArray, sparkles: FloatArray) {
        val vertices = FloatArray(5000 * 8)
        var w = 0
        fun vertex(u: Float, v: Float, x: Float, y: Float, phase: Float, type: Float, alpha: Float, tint: Float) {
            vertices[w++] = u; vertices[w++] = v; vertices[w++] = x; vertices[w++] = y
            vertices[w++] = phase; vertices[w++] = type; vertices[w++] = alpha; vertices[w++] = tint
        }
        fun ellipse(u: Float, v: Float, ra: Float, rb: Float, angle: Float, phase: Float, type: Float, alpha: Float, tint: Float) {
            val ca = cos(angle); val sa = sin(angle)
            var px = ca * ra; var py = sa * ra
            for (j in 1..14) {
                val ex = CIRCLE_X[j] * ra; val ey = CIRCLE_Y[j] * rb
                val x = ca * ex - sa * ey; val y = sa * ex + ca * ey
                vertex(u, v, 0f, 0f, phase, type, alpha, tint)
                vertex(u, v, px, py, phase, type, 0f, tint)
                vertex(u, v, x, y, phase, type, 0f, tint)
                px = x; py = y
            }
        }
        for (k in blobs.indices step 6) ellipse(blobs[k], blobs[k+1], blobs[k+2], blobs[k+3], blobs[k+4], 0f, 0f, blobs[k+5], 0f)
        for (k in stars.indices step 5) ellipse(stars[k], stars[k+1], stars[k+2], stars[k+2], 0f, stars[k+4], 1f, stars[k+3], 0f)
        for (k in sparkles.indices step 5) {
            val u = sparkles[k]; val v = sparkles[k+1]; val radius = sparkles[k+2]
            val phase = sparkles[k+3]; val tint = sparkles[k+4]
            val width = radius * .16f
            for (arm in 0..3) {
                val ox = when (arm) { 0 -> 1f; 2 -> -1f; else -> 0f }
                val oy = when (arm) { 1 -> 1f; 3 -> -1f; else -> 0f }
                vertex(u, v, -oy*width, ox*width, phase, 2f, 1f, tint)
                vertex(u, v, oy*width, -ox*width, phase, 2f, 1f, tint)
                vertex(u, v, ox*radius, oy*radius, phase, 2f, 0f, tint)
            }
            ellipse(u, v, radius*.45f, radius*.45f, 0f, phase, 2f, .35f, tint)
        }
        mesh.setVertices(vertices, 0, w)
        count = w / 8
    }

    fun render(projection: Matrix4, plane: Matrix4, time: Float, alpha: Float, space: SpaceWorld) {
        if (count == 0) return
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(false)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE); Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shader.bind(); shader.setUniformMatrix("u_projViewTrans", projectionPlane.set(projection).mul(plane))
        shader.setUniformf("u_time", time); shader.setUniformf("u_alpha", alpha)
        shader.setUniformf("u_star", space.star.r, space.star.g, space.star.b)
        shader.setUniformf("u_neon", space.neonSoft.r, space.neonSoft.g, space.neonSoft.b)
        band.set(space.neonSoft).lerp(space.skyBottom, .4f)
        shader.setUniformf("u_band", band.r, band.g, band.b)
        mesh.render(shader, GL20.GL_TRIANGLES, 0, count)
    }

    override fun dispose() { mesh.dispose(); shader.dispose() }

    private companion object {
        val CIRCLE_X = FloatArray(15) { cos(it * 2f * PI.toFloat() / 14) }
        val CIRCLE_Y = FloatArray(15) { sin(it * 2f * PI.toFloat() / 14) }
    }
}
