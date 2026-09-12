package cube.run.core.gfx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import kotlin.math.abs
import kotlin.math.max

/**
 * Everything the batched box passes ([WorldBoxBatch], [ShardSystem]) share:
 *
 *  - a unit-cube **template** extracted from libGDX's own box builder (so the
 *    winding / culling matches ModelBatch-drawn boxes exactly), reduced to
 *    "vertex → which of the 8 corners, which of the 6 faces" lookups so a box
 *    costs 8 corner transforms + 6 face lightings instead of 24 of each;
 *  - the **light rig** that mirrors the [Environment] returned by [environment],
 *    baked into vertex colours (the batched passes have no per-fragment lighting);
 *  - the tiny position+colour **shader** and a factory for the dynamic meshes.
 */
class BoxMeshKit(mb: ModelBuilder) : Disposable {

    /** Template vertex count (24) and index count (36) per box. */
    @JvmField val vertsPerBox: Int
    @JvmField val idxPerBox: Int
    /** Per template vertex: corner index (0..7) / face index (0..5). */
    @JvmField val cornerOf: IntArray
    @JvmField val faceOf: IntArray
    private val tplIdx: ShortArray

    /** The 8 corners of a unit cube (±0.5), xyz triples. */
    @JvmField val cornerLocal = floatArrayOf(
        -.5f, -.5f, -.5f, .5f, -.5f, -.5f, -.5f, .5f, -.5f, .5f, .5f, -.5f,
        -.5f, -.5f, .5f, .5f, -.5f, .5f, -.5f, .5f, .5f, .5f, .5f, .5f,
    )
    /** The 6 face normals: +X -X +Y -Y +Z -Z. */
    @JvmField val faceNrm = floatArrayOf(
        1f, 0f, 0f, -1f, 0f, 0f, 0f, 1f, 0f, 0f, -1f, 0f, 0f, 0f, 1f, 0f, 0f, -1f,
    )

    // light rig — [environment] builds the matching libGDX Environment
    private val ambR = 0.55f; private val ambG = 0.55f; private val ambB = 0.6f
    private val dir1 = Vector3(-0.45f, -0.85f, -0.35f)
    private val toL1 = Vector3(dir1).scl(-1f).nor()
    private val l1R = 0.85f; private val l1G = 0.85f; private val l1B = 0.8f
    private val dir2 = Vector3(0.6f, -0.2f, 0.5f)
    private val toL2 = Vector3(dir2).scl(-1f).nor()
    private val l2R = 0.25f; private val l2G = 0.22f; private val l2B = 0.3f
    private val sN = Vector3()

    val shader: ShaderProgram

    init {
        val tpl = mb.createBox(1f, 1f, 1f, Material(ColorAttribute.createDiffuse(Color.WHITE)), (Usage.Position or Usage.Normal).toLong())
        val m0 = tpl.meshes.first()
        val vCount = m0.numVertices                 // 24
        val fpv = m0.vertexSize / 4                 // floats per vertex
        val raw = FloatArray(vCount * fpv)
        m0.getVertices(raw)
        val pOff = m0.getVertexAttribute(Usage.Position).offset / 4
        val nOff = m0.getVertexAttribute(Usage.Normal).offset / 4
        cornerOf = IntArray(vCount)
        faceOf = IntArray(vCount)
        for (v in 0 until vCount) {
            val px = raw[v * fpv + pOff]; val py = raw[v * fpv + pOff + 1]; val pz = raw[v * fpv + pOff + 2]
            cornerOf[v] = (if (px > 0) 1 else 0) or (if (py > 0) 2 else 0) or (if (pz > 0) 4 else 0)
            val nx = raw[v * fpv + nOff]; val ny = raw[v * fpv + nOff + 1]; val nz = raw[v * fpv + nOff + 2]
            faceOf[v] = when {
                abs(nx) > 0.5f -> if (nx > 0) 0 else 1
                abs(ny) > 0.5f -> if (ny > 0) 2 else 3
                else -> if (nz > 0) 4 else 5
            }
        }
        tplIdx = ShortArray(m0.numIndices)          // 36
        m0.getIndices(tplIdx)
        // Face compaction reuses this repeating quad index pattern.
        check(vCount == 24 && tplIdx.size == 36)
        for (face in 0 until 6) for (k in 0 until 6)
            check(tplIdx[face * 6 + k].toInt() - face * 4 == tplIdx[k].toInt())
        vertsPerBox = vCount
        idxPerBox = tplIdx.size
        tpl.dispose()

        ShaderProgram.pedantic = false
        shader = ShaderProgram(
            """
            attribute vec3 a_position;
            attribute vec4 a_color;
            uniform mat4 u_projViewTrans;
            varying vec4 v_color;
            void main() { v_color = a_color; gl_Position = u_projViewTrans * vec4(a_position, 1.0); }
            """.trimIndent(),
            """
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec4 v_color;
            void main() { gl_FragColor = v_color; }
            """.trimIndent(),
        ).also { require(it.isCompiled) { "box shader: ${it.log}" } }
    }

    /** The lit Environment whose Lambert term this kit bakes into vertex colours. */
    fun environment(): Environment = Environment().apply {
        set(ColorAttribute(ColorAttribute.AmbientLight, ambR, ambG, ambB, 1f))
        add(DirectionalLight().set(l1R, l1G, l1B, dir1.x, dir1.y, dir1.z))
        add(DirectionalLight().set(l2R, l2G, l2B, dir2.x, dir2.y, dir2.z))
    }

    /** Lambert rgb factors for a world-space normal, written to [out] at [off]. */
    fun lightFace(nx: Float, ny: Float, nz: Float, out: FloatArray, off: Int) {
        sN.set(nx, ny, nz).nor()
        val d1 = max(0f, sN.dot(toL1)); val d2 = max(0f, sN.dot(toL2))
        out[off] = ambR + d1 * l1R + d2 * l2R
        out[off + 1] = ambG + d1 * l1G + d2 * l2G
        out[off + 2] = ambB + d1 * l1B + d2 * l2B
    }

    internal fun setLightUniforms(target: ShaderProgram) {
        target.setUniformf("u_toL1", toL1); target.setUniformf("u_toL2", toL2)
        target.setUniformf("u_ambient", ambR, ambG, ambB)
        target.setUniformf("u_light1", l1R, l1G, l1B); target.setUniformf("u_light2", l2R, l2G, l2B)
    }

    internal fun newUnitMesh(): Mesh {
        val mesh = Mesh(true, vertsPerBox, idxPerBox,
            VertexAttribute(Usage.Position, 3, "a_position"), VertexAttribute(Usage.Normal, 3, "a_normal"))
        val data = FloatArray(vertsPerBox * 6)
        for (v in 0 until vertsPerBox) {
            val ci = cornerOf[v] * 3; val fi = faceOf[v] * 3
            for (axis in 0 until 3) { data[v * 6 + axis] = cornerLocal[ci + axis]; data[v * 6 + 3 + axis] = faceNrm[fi + axis] }
        }
        mesh.setVertices(data); mesh.setIndices(tplIdx)
        return mesh
    }

    /** A dynamic mesh holding [boxes] copies of the unit-cube template (pos + packed colour). */
    fun newBatchMesh(boxes: Int): Mesh {
        val mesh = Mesh(
            false, boxes * vertsPerBox, boxes * idxPerBox,
            VertexAttribute(Usage.Position, 3, "a_position"),
            VertexAttribute(Usage.ColorPacked, 4, "a_color"),
        )
        val idx = ShortArray(boxes * idxPerBox)
        for (c in 0 until boxes) {
            val ib = c * idxPerBox; val vb = c * vertsPerBox
            for (k in tplIdx.indices) idx[ib + k] = (tplIdx[k] + vb).toShort()
        }
        mesh.setIndices(idx)
        return mesh
    }

    override fun dispose() {
        shader.dispose()
    }
}
