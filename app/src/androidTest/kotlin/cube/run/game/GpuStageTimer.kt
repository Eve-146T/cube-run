package cube.run.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.utils.BufferUtils

/** Diagnostic only: asynchronous stage queries, never a blocking result read. */
internal class GpuStageTimer(private val samples: MutableMap<String, ArrayList<Float>>) {
    private val ids = BufferUtils.newIntBuffer(256)
    private val value = BufferUtils.newIntBuffer(1)
    private val tags = arrayOfNulls<String>(256)
    private var active = false
    private var cursor = 0
    init {
        check(Gdx.graphics.supportsExtension("GL_EXT_disjoint_timer_query"))
        Gdx.gl30.glGenQueries(ids.capacity(), ids)
    }
    fun stage(tag: String, collect: Boolean) {
        if (active) { Gdx.gl30.glEndQuery(TIME_ELAPSED); active = false }
        if (tag == "sim") {
            for (i in tags.indices) {
                val previous = tags[i] ?: continue
                value.clear()
                Gdx.gl30.glGetQueryObjectuiv(ids[i], RESULT_AVAILABLE, value)
                if (value[0] == 0) continue
                value.clear()
                Gdx.gl30.glGetQueryObjectuiv(ids[i], RESULT, value)
                samples.getOrPut("gpu_$previous") { ArrayList() }
                    .add(((value[0].toLong() and 0xffffffffL) / 1e6).toFloat())
                tags[i] = null
            }
        }
        if (!collect || tag == "swap" || tags[cursor] != null) return
        Gdx.gl30.glBeginQuery(TIME_ELAPSED, ids[cursor])
        tags[cursor] = tag
        cursor = (cursor + 1) % tags.size
        active = true
    }
    fun dispose() {
        if (active) Gdx.gl30.glEndQuery(TIME_ELAPSED)
        value.clear(); Gdx.gl.glGetIntegerv(DISJOINT, value)
        check(value[0] == 0) { "Disjoint GPU timer measurements must be excluded" }
        ids.position(0); Gdx.gl30.glDeleteQueries(ids.capacity(), ids)
        check(Gdx.gl.glGetError() == GL20.GL_NO_ERROR) { "GPU timer queries failed" }
    }
    private companion object {
        const val TIME_ELAPSED = 0x88BF
        const val RESULT_AVAILABLE = 0x8867
        const val RESULT = 0x8866
        const val DISJOINT = 0x8FBB
    }
}
