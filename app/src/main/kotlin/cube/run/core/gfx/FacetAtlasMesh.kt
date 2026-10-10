package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.glutils.IndexBufferObject
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.glutils.VertexData
import com.badlogic.gdx.utils.BufferUtils
import java.nio.FloatBuffer

/** Static geometry grows with the shape population; ordinary frames update only instance records and indices. */
internal class FacetAtlasMesh private constructor(private val data: Data, private val indexData: IndexBufferObject) :
    Mesh(data, indexData, false) {
    constructor(vertices: Int, indices: Int) : this(Data(vertices), IndexBufferObject(false, indices))
    private var indexDirty = true
    private var indexActive = false
    override fun setIndices(indices: ShortArray, offset: Int, count: Int): Mesh {
        indexDirty = true
        return super.setIndices(indices, offset, count)
    }
    fun bindForDraw(shader: ShaderProgram) {
        data.bind(shader)
        if (indexDirty || !data.indexConfigured) {
            indexData.bind()
            indexActive = true
            indexDirty = false; data.indexConfigured = true
        }
    }
    fun drawBound(start: Int, count: Int) {
        if (count > 0) Gdx.gl.glDrawElements(GL20.GL_TRIANGLES, count, GL20.GL_UNSIGNED_SHORT, start * 2)
    }
    fun finishDraw() { Gdx.gl30.glBindVertexArray(0) }
    fun finishIndexBinding() {
        // Unbind on VAO zero so the atlas VAO retains its element buffer. Also clear
        // libGDX's bound flag: next frame's CPU setIndices must defer its GL upload.
        if (indexActive) { indexData.unbind(); indexActive = false }
    }

    private class Data(vertices: Int) : VertexData {
        private val attributes = VertexAttributes(
            VertexAttribute(Usage.Position, 3, "a_position"),
            VertexAttribute(Usage.Generic, 4, "a_surface"),
            VertexAttribute(Usage.Generic, 1, "a_instance"))
        private val bytes = BufferUtils.newUnsafeByteBuffer(vertices * 32)
        private val buffer = bytes.asFloatBuffer()
        private val handles = BufferUtils.newIntBuffer(1)
        private var handle = Gdx.gl.glGenBuffer()
        private var vao = newVao()
        private var allocated = false
        private var dirtyStart = Int.MAX_VALUE
        private var dirtyEnd = 0
        private var used = 0
        var indexConfigured = false
        private fun newVao(): Int {
            handles.clear(); Gdx.gl30.glGenVertexArrays(1, handles)
            return handles.get(0)
        }
        override fun getAttributes() = attributes
        override fun getNumVertices() = used / 8
        override fun getNumMaxVertices() = buffer.capacity() / 8
        override fun getBuffer() = getBuffer(true)
        override fun getBuffer(forWriting: Boolean): FloatBuffer {
            if (forWriting) { dirtyStart = 0; dirtyEnd = used }
            return buffer
        }
        override fun setVertices(values: FloatArray, offset: Int, count: Int) {
            used = count
            updateVertices(0, values, offset, count)
        }
        override fun updateVertices(target: Int, values: FloatArray, offset: Int, count: Int) {
            buffer.clear(); buffer.position(target)
            buffer.put(values, offset, count); buffer.position(0)
            used = maxOf(used, target + count)
            dirtyStart = minOf(dirtyStart, target); dirtyEnd = maxOf(dirtyEnd, target + count)
        }
        override fun bind(shader: ShaderProgram) = bind(shader, null)
        override fun bind(shader: ShaderProgram, locations: IntArray?) {
            val gl = Gdx.gl30
            gl.glBindVertexArray(vao)
            if (!allocated || dirtyEnd > dirtyStart) gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, handle)
            if (!allocated) {
                gl.glBufferData(GL20.GL_ARRAY_BUFFER, bytes.capacity(), null, GL20.GL_STATIC_DRAW)
                for (i in 0..2) {
                    gl.glEnableVertexAttribArray(i)
                    gl.glVertexAttribPointer(i, if (i == 0) 3 else if (i == 1) 4 else 1,
                        GL20.GL_FLOAT, false, 32, if (i == 0) 0 else if (i == 1) 12 else 28)
                }
                allocated = true
            }
            if (dirtyEnd > dirtyStart) {
                buffer.position(dirtyStart); buffer.limit(dirtyEnd)
                gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, dirtyStart * 4, (dirtyEnd - dirtyStart) * 4, buffer)
                buffer.clear()
                dirtyStart = Int.MAX_VALUE; dirtyEnd = 0
            }
        }
        override fun unbind(shader: ShaderProgram) = unbind(shader, null)
        override fun unbind(shader: ShaderProgram, locations: IntArray?) {
            Gdx.gl30.glBindVertexArray(0)
            Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, 0)
        }
        override fun invalidate() {
            handle = Gdx.gl.glGenBuffer(); vao = newVao(); allocated = false
            indexConfigured = false
            dirtyStart = 0; dirtyEnd = used
        }
        override fun dispose() {
            Gdx.gl.glDeleteBuffer(handle)
            handles.clear(); handles.put(vao); handles.flip(); Gdx.gl30.glDeleteVertexArrays(1, handles)
            BufferUtils.disposeUnsafeByteBuffer(bytes)
        }
    }
}
