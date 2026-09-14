package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.utils.Disposable

/** Real mesh edges, depth tested against the black faces; no diagonal triangle seams. */
class MatrixWireBatch(private val kit: BoxMeshKit) : Disposable {
    var amount = 0f
    private val mesh = Mesh(false, 60000, 0,
        VertexAttribute(Usage.Position, 3, "a_position"), VertexAttribute(Usage.ColorPacked, 4, "a_color"))
    private val data = FloatArray(60000 * 4)
    private var used = 0
    private val corners = FloatArray(24)
    fun begin() { used = 0 }

    fun box(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float,
            c: Float, s: Float, back: Float, front: Float, fog: Float, opacity: Float) {
        if (amount <= 0f) return
        for (i in 0..7) {
            val lx = (if (i and 1 == 0) -.5f else .5f) * sx
            val ly = (if (i and 2 == 0) -.5f else .5f) * sy
            val lz = (if (i and 4 == 0) -.5f else .5f) * sz
            corners[i * 3] = x + lx * c + lz * s
            corners[i * 3 + 1] = y + ly + if (i and 4 == 0) back else front
            corners[i * 3 + 2] = z - lx * s + lz * c
        }
        val color = color(fog, opacity)
        for (i in 0..7) for (bit in 0..2) {
            val axis = 1 shl bit
            if (i and axis != 0) continue
            val a = i * 3; val b = (i or axis) * 3
            edge(corners[a], corners[a+1], corners[a+2], corners[b], corners[b+1], corners[b+2], color)
        }
    }

    fun color(fog: Float, opacity: Float): Float = Color.toFloatBits(.12f, 1f, .38f, amount * (1f-fog) * opacity)

    fun edge(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, color: Float) {
        if (used + 8 > data.size) return
        data[used++] = ax; data[used++] = ay; data[used++] = az; data[used++] = color
        data[used++] = bx; data[used++] = by; data[used++] = bz; data[used++] = color
    }

    fun render(camera: Camera) {
        if (used == 0) return
        mesh.setVertices(data, 0, used)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glLineWidth(1.5f)
        kit.shader.bind(); kit.shader.setUniformMatrix("u_projViewTrans", camera.combined)
        mesh.render(kit.shader, GL20.GL_LINES, 0, used / 4)
        Gdx.gl.glLineWidth(1f)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDepthFunc(GL20.GL_LESS)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
    }
    override fun dispose() { mesh.dispose() }
}
