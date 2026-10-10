package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.glutils.IndexBufferObject
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.glutils.VertexBufferObjectWithVAO
import com.badlogic.gdx.utils.BufferUtils

/**
 * Instances keep their attribute bindings in the geometry's VAO. libGDX's
 * general instance buffer enables, describes and disables all instance attributes on
 * every draw, including the second fade pass. Here those bindings change only
 * when a GL context is created. Mesh still owns context-loss restoration.
 *
 * The shaders explicitly reserve locations 0/1 for vertices and 2 onward
 * for instances. Other meshes use their own VAOs, so their divisors are untouched.
 */
internal class InstanceMesh private constructor(
    private val data: Data, indices: Int,
) : Mesh(data, IndexBufferObject(true, indices), false) {
    constructor(vertices: Int, indices: Int, capacity: Int, stride: Int = 40,
                geometry: VertexAttributes = VertexAttributes(VertexAttribute(Usage.Position, 3, "a_position"),
                    VertexAttribute(Usage.Generic, 4, "a_surface"))) : this(Data(vertices, capacity, stride, geometry), indices)

    override fun setInstanceData(values: FloatArray, offset: Int, count: Int): Mesh {
        data.setInstances(values, offset, count)
        return this
    }

    override fun render(shader: ShaderProgram, primitiveType: Int) {
        if (data.instances == 0) return
        bind(shader)
        Gdx.gl30.glDrawElementsInstanced(primitiveType, numIndices, GL20.GL_UNSIGNED_SHORT, 0, data.instances)
        unbind(shader)
    }

    private class Data(vertices: Int, capacity: Int, private val stride: Int, geometry: VertexAttributes) :
        VertexBufferObjectWithVAO(true, vertices, geometry) {
        private val bytes = BufferUtils.newUnsafeByteBuffer(capacity * stride * 4)
        private var instanceHandle = Gdx.gl.glGenBuffer()
        private var configured = false
        private var dirty = false
        private val previous = FloatArray(capacity * stride)
        private var previousCount = -1
        var instances = 0
            private set

        fun setInstances(values: FloatArray, offset: Int, count: Int) {
            require(count % stride == 0 && count * 4 <= bytes.capacity())
            if (count == previousCount) {
                var i = 0
                while (i < count && values[offset + i] == previous[i]) i++
                if (i == count) return // Stationary panorama: retain the existing GPU records.
            }
            System.arraycopy(values, offset, previous, 0, count)
            previousCount = count
            bytes.clear()
            BufferUtils.copy(values, bytes, count, offset)
            bytes.position(0); bytes.limit(count * 4)
            instances = count / stride
            dirty = true
        }

        override fun bind(shader: ShaderProgram, locations: IntArray?) {
            if (!configured) {
                // libGDX 1.13 retains cachedLocations when invalidate() replaces the VAO.
                // Force its attribute cache to refresh before using the replacement.
                super.bind(shader, IntArray(attributes.size()) { -1 })
            }
            super.bind(shader, locations)
            if (!configured || dirty) {
                val gl = Gdx.gl30
                gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, instanceHandle)
                if (dirty) {
                    gl.glBufferData(GL20.GL_ARRAY_BUFFER, bytes.limit(), bytes, GL20.GL_STREAM_DRAW)
                    dirty = false
                }
                if (!configured) {
                    for (i in 0 until stride / 4) {
                        gl.glEnableVertexAttribArray(i + 2)
                        gl.glVertexAttribPointer(i + 2, 4, GL20.GL_FLOAT, false, stride * 4, i * 16)
                        gl.glVertexAttribDivisor(i + 2, 1)
                    }
                    configured = true
                }
                gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, 0)
            }
        }

        override fun invalidate() {
            super.invalidate()
            instanceHandle = Gdx.gl.glGenBuffer()
            configured = false
            dirty = true
        }

        override fun dispose() {
            Gdx.gl.glDeleteBuffer(instanceHandle)
            BufferUtils.disposeUnsafeByteBuffer(bytes)
            super.dispose()
        }
    }

}
