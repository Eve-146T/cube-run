package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Faceted crystals and irregular coal lumps share one draw call. */
class CrystalBatch(private val kit: BoxMeshKit) : Disposable {
    var terrain: TerrainHeight? = null
    private val template: FloatArray
    private val gemTemplate = cutGemTemplate()
    private val coalTemplates = Array(3) { coalTemplate(it) }
    private val vertices = FloatArray(360 * 128 * 4)
    private val mesh = Mesh(false, vertices.size / 4, 0,
        VertexAttribute(Usage.Position, 3, "a_position"), VertexAttribute(Usage.ColorPacked, 4, "a_color"))
    private val light = FloatArray(3)
    private var used = 0
    private var translucent = false

    init {
        val raw = ArrayList<Float>()
        fun ring(i: Int, y: Float, radius: Float): Vector3 {
            val a = i * (Math.PI.toFloat() * 2f / 5f)
            return Vector3(cos(a) * radius, y, sin(a) * radius)
        }
        fun face(a: Vector3, b: Vector3, c: Vector3) {
            val n = Vector3(b).sub(a).crs(Vector3(c).sub(a)).nor()
            for (v in arrayOf(a,b,c)) raw.addAll(listOf(v.x,v.y,v.z,n.x,n.y,n.z))
        }
        for (i in 0..4) {
            val a = ring(i, .18f, .30f); val b = ring(i+1, .18f, .30f)
            val c = ring(i, -.43f, .18f); val d = ring(i+1, -.43f, .18f)
            face(Vector3(0f,.55f,0f), b, a)
            face(a,b,c); face(b,d,c)
            face(Vector3(0f,-.43f,0f),c,d)
        }
        template = raw.toFloatArray()
    }

    fun begin() { used = 0; translucent = false }
    fun crystal(x: Float, y: Float, z: Float, scale: Float, yaw: Float, color: Color, fog: Float, sky: Color) =
        queue(template, 6, x, y, z, scale, yaw, color, fog, sky)

    fun gem(x: Float, y: Float, z: Float, scale: Float, yaw: Float, color: Color, fog: Float, sky: Color) =
        queue(gemTemplate, 7, x, y, z, scale, yaw, color, fog, sky)

    fun coal(x: Float, y: Float, z: Float, scale: Float, yaw: Float, variant: Int, color: Color, fog: Float, sky: Color) =
        queue(coalTemplates[Math.floorMod(variant, coalTemplates.size)], 7, x, y, z, scale, yaw, color, fog, sky)

    private fun queue(shape: FloatArray, stride: Int, x: Float, y0: Float, z: Float, scale: Float, yaw: Float, color: Color, fog: Float, sky: Color) {
        if (used + shape.size / stride * 4 > vertices.size) return
        if (color.a < 1f) translucent = true
        val y = y0 + (terrain?.invoke(z) ?: 0f)
        val a = yaw * Math.PI.toFloat() / 180f; val c = cos(a); val s = sin(a)
        for (i in shape.indices step stride) {
            val px = shape[i]; val py = shape[i+1]; val pz = shape[i+2]
            val nx = shape[i+3]; val ny = shape[i+4]; val nz = shape[i+5]
            val tone = if (stride == 7) shape[i+6] else 1f
            if (i % (stride * 3) == 0) kit.lightFace(nx*c+nz*s, ny, -nx*s+nz*c, light, 0)
            vertices[used++] = x + (px*c+pz*s)*scale
            vertices[used++] = y + py*scale
            vertices[used++] = z + (-px*s+pz*c)*scale
            vertices[used++] = Color.toFloatBits(
                (color.r*light[0]*tone).coerceAtMost(1f)*(1f-fog)+sky.r*fog,
                (color.g*light[1]*tone).coerceAtMost(1f)*(1f-fog)+sky.g*fog,
                (color.b*light[2]*tone).coerceAtMost(1f)*(1f-fog)+sky.b*fog, color.a)
        }
    }

    /** A jeweller's cut diamond: broad octagonal girdle, flat table, bevelled crown and pointed pavilion. */
    private fun cutGemTemplate(): FloatArray {
        val raw = ArrayList<Float>()
        fun ring(radius: Float, y: Float) = Array(8) { i ->
            val angle = (i + .5f) * Math.PI.toFloat() / 4f
            Vector3(cos(angle) * radius, y, sin(angle) * radius)
        }
        val table = ring(.19f, .22f)
        val upper = ring(.4f, .035f)
        val lower = ring(.4f, -.015f)
        fun face(a: Vector3, b: Vector3, c: Vector3, tone: Float) {
            val normal = Vector3(b).sub(a).crs(Vector3(c).sub(a)).nor()
            val outward = normal.dot(Vector3(a).add(b).add(c)) >= 0f
            if (!outward) normal.scl(-1f)
            for (v in if (outward) arrayOf(a, b, c) else arrayOf(a, c, b))
                raw.addAll(listOf(v.x, v.y, v.z, normal.x, normal.y, normal.z, tone))
        }
        for (i in 0..7) {
            val next = (i + 1) % 8
            face(Vector3(0f, .22f, 0f), table[i], table[next], 1.6f)
            face(table[i], upper[i], upper[next], if (i % 2 == 0) 1.4f else .9f)
            face(table[i], upper[next], table[next], if (i % 2 == 0) 1.1f else 1.55f)
            face(upper[i], lower[i], lower[next], .65f)
            face(upper[i], lower[next], upper[next], .65f)
            face(lower[i], Vector3(0f, -.4f, 0f), lower[next], if (i % 2 == 0) .75f else 1.15f)
        }
        return raw.toFloatArray()
    }

    /** Broken, offset rings give coal broad chipped faces instead of box corners or gem tips. */
    private fun coalTemplate(variant: Int): FloatArray {
        val random = Random(146 + variant * 97)
        val raw = ArrayList<Float>()
        val rings = Array(3) { ring -> Array(7) { i ->
            val angle = (i + ring * .13f) * (Math.PI.toFloat() * 2f / 7f)
            val radius = (if (ring == 1) .31f else .235f) * (.8f + random.nextFloat() * .35f)
            Vector3(cos(angle) * radius + (ring - 1) * .025f,
                (ring - 1) * .17f + (random.nextFloat() - .5f) * .07f,
                sin(angle) * radius - ring * .018f)
        } }
        fun face(a: Vector3, b: Vector3, c: Vector3, tone: Float) {
            val n = Vector3(b).sub(a).crs(Vector3(c).sub(a)).nor()
            val outward = n.dot(Vector3(a).add(b).add(c)) >= 0f
            if (!outward) n.scl(-1f)
            for (v in if (outward) arrayOf(a, b, c) else arrayOf(a, c, b))
                raw.addAll(listOf(v.x, v.y, v.z, n.x, n.y, n.z, tone))
        }
        for (i in 0..6) {
            val next = (i + 1) % 7
            for (ring in 0..1) {
                val a = rings[ring][i]; val b = rings[ring][next]
                val c = rings[ring+1][i]; val d = rings[ring+1][next]
                face(a, b, c, if ((i + variant) % 5 == 0) .42f else .75f + random.nextFloat() * .5f)
                face(b, d, c, .65f + random.nextFloat() * .6f)
            }
            face(Vector3(-.035f, -.225f, .015f), rings[0][next], rings[0][i], .7f)
            face(Vector3(.045f, .225f, -.045f), rings[2][i], rings[2][next], 1.1f + random.nextFloat() * .5f)
        }
        return raw.toFloatArray()
    }
    fun render(cam: Camera) {
        if (used == 0) return
        mesh.setVertices(vertices, 0, used)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(true)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        if (translucent) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        } else Gdx.gl.glDisable(GL20.GL_BLEND)
        kit.shader.bind(); kit.shader.setUniformMatrix("u_projViewTrans", cam.combined); WorldBend.apply(kit.shader)
        mesh.render(kit.shader, GL20.GL_TRIANGLES, 0, used/4)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }
    override fun dispose() = mesh.dispose()
}
