package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.FloatTextureData
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import java.util.IdentityHashMap

/** Static copies of voxel geometry share draws, with transforms fetched from a managed float texture. */
internal class FacetAtlas : Disposable {
    private class Geometry(shape: FacetShape) {
        @JvmField val vertices = FloatArray(shape.faces * 16)
        @JvmField val indices = ShortArray(shape.faces * 3)
        init {
            var w = 0; var index = 0
            for (f in 0 until shape.faces step 2) {
                val normal = f * 3
                val direction = when {
                    shape.nrm[normal] != 0f -> if (shape.nrm[normal] > 0f) 0f else 1f
                    shape.nrm[normal + 1] != 0f -> if (shape.nrm[normal + 1] > 0f) 2f else 3f
                    else -> if (shape.nrm[normal + 2] > 0f) 4f else 5f
                }
                for (corner in intArrayOf(0, 1, 2, 5)) {
                    for (axis in 0..2) vertices[w++] = shape.pos[f * 9 + corner * 3 + axis]
                    vertices[w++] = shape.slot[f].toFloat()
                    vertices[w++] = shape.tone[f]
                    vertices[w++] = shape.lat[f]
                    vertices[w++] = direction
                    vertices[w++] = 0f // Instance row is filled when this template is copied.
                }
                for (offset in intArrayOf(0, 1, 2, 0, 2, 3)) indices[index++] = (f * 2 + offset).toShort()
            }
        }
    }
    private class Chunk : Disposable {
        @JvmField val mesh = FacetAtlasMesh(VERTICES, INDICES)
        @JvmField val vertices = FloatArray(VERTICES * 8)
        // Counts delimit initialized entries. Non-null storage avoids a Kotlin
        // runtime assertion for every slot read in these per-frame scans.
        private val emptySlot = Slot(this, -1, ShortArray(0))
        @JvmField val solidSlots = Array(ROWS) { emptySlot }
        @JvmField val fadingSlots = Array(ROWS) { emptySlot }
        @JvmField var slotsChanged = false
        @JvmField val combined = ShortArray(INDICES)
        @JvmField var vertexCount = 0
        @JvmField var solidCount = 0
        @JvmField var fadingCount = 0
        @JvmField var solidSlotCount = 0
        @JvmField var fadingSlotCount = 0
        private var previousSolid = -1
        private var previousFading = -1
        fun invalidateLayout() { previousSolid = -1; previousFading = -1 }
        fun upload() {
            if (!slotsChanged && previousSolid == solidSlotCount && previousFading == fadingSlotCount) return
            var w = 0
            for (i in 0 until solidSlotCount) {
                val indices = solidSlots[i].indices
                System.arraycopy(indices, 0, combined, w, indices.size); w += indices.size
            }
            for (i in 0 until fadingSlotCount) {
                val indices = fadingSlots[i].indices
                System.arraycopy(indices, 0, combined, w, indices.size); w += indices.size
            }
            mesh.setIndices(combined, 0, w)
            previousSolid = solidSlotCount; previousFading = fadingSlotCount
        }
        override fun dispose() = mesh.dispose()
    }
    private class Slot(@JvmField val chunk: Chunk, @JvmField val row: Int, @JvmField val indices: ShortArray)
    private class Slots {
        @JvmField val solid = ArrayList<Slot>()
        @JvmField val fading = ArrayList<Slot>()
    }
    private class Draws {
        @JvmField val chunks = arrayOfNulls<Chunk>(ROWS)
        @JvmField val starts = IntArray(ROWS)
        @JvmField val counts = IntArray(ROWS)
        @JvmField var used = 0
        fun add(chunk: Chunk, start: Int, count: Int) {
            if (used > 0 && chunks[used - 1] === chunk) counts[used - 1] += count
            else {
                chunks[used] = chunk; starts[used] = start; counts[used] = count; used++
            }
        }
    }
    private val solidDraws = Draws()
    private val fadingDraws = Draws()
    private val slots = IdentityHashMap<FacetShape, Slots>()
    private val geometry = IdentityHashMap<FacetShape, Geometry>()
    private fun geometry(shape: FacetShape) = geometry[shape] ?: Geometry(shape).also { geometry[shape] = it }
    fun prepare(shape: FacetShape) { geometry(shape) }
    private val chunks = ArrayList<Chunk>()
    private val spareChunks = ArrayList<Chunk>()
    private var storagePrepared = false
    /** Allocate the default world's measured three-chunk working set before play. */
    fun prepareStorage(shader: ShaderProgram) {
        if (storagePrepared) return
        repeat(3) {
            val chunk = Chunk()
            chunk.mesh.bindForDraw(shader)
            chunk.mesh.finishDraw(); chunk.mesh.finishIndexBinding()
            spareChunks.add(chunk)
        }
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, 0)
        storagePrepared = true
    }
    private val records = FloatArray(ROWS * STRIDE)
    private val textureData = object : FloatTextureData(10, ROWS, GL30.GL_RGBA32F, GL20.GL_RGBA, GL20.GL_FLOAT, false) {
        override fun consumeCustomData(target: Int) {
            // libGDX's Android path ignores the requested sized internal format.
            // Instance records need full float precision on every GLES3 driver.
            Gdx.gl.glTexImage2D(target, 0, GL30.GL_RGBA32F, 10, ROWS, 0,
                GL20.GL_RGBA, GL20.GL_FLOAT, buffer)
        }
    }
    private val textures = Array(3) {
        Texture(textureData).apply { setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest) }
    }
    private var textureIndex = 0
    private var rows = 0
    private var boundChunk: Chunk? = null
    fun statistics() = "rows=$rows chunks=${chunks.size} vertices=${chunks.sumOf { it.vertexCount }} solidDraws=${solidDraws.used} fadingDraws=${fadingDraws.used}"

    fun begin() {
        solidDraws.used = 0; fadingDraws.used = 0
        for (chunk in chunks) {
            chunk.solidCount = 0; chunk.fadingCount = 0
            chunk.solidSlotCount = 0; chunk.fadingSlotCount = 0
            chunk.slotsChanged = false
        }
    }

    fun add(shape: FacetShape, data: FloatArray, used: Int, solid: Boolean): Boolean {
        if (used == 0) return true
        val shapeSlots = slots[shape] ?: Slots().also { slots[shape] = it }
        val list = if (solid) shapeSlots.solid else shapeSlots.fading
        val count = used / STRIDE
        if (shape.faces * 2 > VERTICES || rows + maxOf(0, count - list.size) > ROWS) {
            // Earlier groups may have changed CPU slot order before this frame
            // falls back. A later atlas frame must rebuild the uploaded layout.
            for (chunk in chunks) chunk.invalidateLayout()
            return false
        }
        while (list.size < count) list.add(newSlot(shape))
        var recordStart = 0
        var recordRow = list[0].row
        var drawChunk = list[0].chunk
        var drawStart = if (solid) drawChunk.solidCount else drawChunk.fadingCount
        var drawCount = 0
        val draws = if (solid) solidDraws else fadingDraws
        for (i in 0 until count) {
            val slot = list[i]
            if (slot.row != recordRow + i - recordStart) {
                System.arraycopy(data, recordStart * STRIDE, records, recordRow * STRIDE, (i - recordStart) * STRIDE)
                recordStart = i; recordRow = slot.row
            }
            val c = slot.chunk
            if (c !== drawChunk) {
                draws.add(drawChunk, drawStart, drawCount)
                drawChunk = c; drawStart = if (solid) c.solidCount else c.fadingCount; drawCount = 0
            }
            drawCount += slot.indices.size
            if (solid) {
                if (c.solidSlots[c.solidSlotCount] !== slot) c.slotsChanged = true
                c.solidSlots[c.solidSlotCount++] = slot
                c.solidCount += slot.indices.size
            } else {
                if (c.fadingSlots[c.fadingSlotCount] !== slot) c.slotsChanged = true
                c.fadingSlots[c.fadingSlotCount++] = slot
                c.fadingCount += slot.indices.size
            }
        }
        System.arraycopy(data, recordStart * STRIDE, records, recordRow * STRIDE, (count - recordStart) * STRIDE)
        draws.add(drawChunk, drawStart, drawCount)
        return true
    }

    private fun newSlot(shape: FacetShape): Slot {
        check(rows < ROWS) { "Facet atlas instance capacity exceeded" }
        val size = shape.faces * 2
        val chunk = chunks.lastOrNull()?.takeIf { it.vertexCount + size <= VERTICES }
            ?: (if (spareChunks.isEmpty()) Chunk() else spareChunks.removeAt(spareChunks.lastIndex)).also { chunks.add(it) }
        val base = chunk.vertexCount
        val row = rows++
        val template = geometry(shape)
        System.arraycopy(template.vertices, 0, chunk.vertices, base * 8, template.vertices.size)
        val rowValue = row.toFloat()
        for (vertex in 0 until size) chunk.vertices[(base + vertex) * 8 + 7] = rowValue
        val indices = ShortArray(template.indices.size)
        for (i in indices.indices) indices[i] = (base + (template.indices[i].toInt() and 65535)).toShort()
        chunk.vertexCount += size
        chunk.mesh.updateVertices(base * 8, chunk.vertices, base * 8, size * 8)
        return Slot(chunk, row, indices)
    }

    fun upload() {
        textures[textureIndex].bind(0)
        textureIndex = (textureIndex + 1) % textures.size
        val buffer = textureData.buffer
        buffer.clear()
        BufferUtils.copy(records, buffer, rows * STRIDE, 0)
        buffer.position(0); buffer.limit(buffer.capacity()) // Managed reload uploads the entire texture.
        Gdx.gl.glTexSubImage2D(GL20.GL_TEXTURE_2D, 0, 0, 0, 10, rows, GL20.GL_RGBA, GL20.GL_FLOAT, buffer)
        for (chunk in chunks) chunk.upload()
    }

    fun render(shader: ShaderProgram, solid: Boolean) {
        // Newly linked sampler uniforms already select unit zero, including after reload.
        // Preserve submission order even when the static library spans multiple 16-bit meshes.
        // Equal-depth faces otherwise choose different colours at coplanar boundaries.
        val draws = if (solid) solidDraws else fadingDraws
        for (i in 0 until draws.used) {
            val chunk = draws.chunks[i]!!
            if (boundChunk !== chunk) {
                chunk.mesh.bindForDraw(shader)
                boundChunk = chunk
            }
            chunk.mesh.drawBound(draws.starts[i] + if (solid) 0 else chunk.solidCount, draws.counts[i])
        }
    }
    fun finishDraw() {
        boundChunk?.mesh?.finishDraw()
        for (chunk in chunks) chunk.mesh.finishIndexBinding()
        boundChunk = null
    }

    override fun dispose() {
        for (chunk in chunks) chunk.dispose()
        for (chunk in spareChunks) chunk.dispose()
        for (texture in textures) texture.dispose()
        geometry.clear(); slots.clear()
    }

    companion object {
        private const val VERTICES = 65536
        private const val INDICES = VERTICES / 4 * 6
        private const val ROWS = 2048
        private const val STRIDE = 40

        fun vertexShader(source: String): String {
            var result = source
            for (i in 0 until 10) result = result.replace("layout(location=${i + 2}) in vec4 i_$i;", "")
            return result.replace("void main() {", """
                uniform highp sampler2D u_instances;
                layout(location=2) in float a_instance;
                void main() {
                ${(0 until 10).joinToString("\n") { "vec4 i_$it = texelFetch(u_instances, ivec2($it, int(a_instance)), 0);" }}
            """.trimIndent())
        }
    }
}
