package cube.run.game.track

import com.badlogic.gdx.graphics.Color
import cube.run.core.Gdx3DGame
import kotlin.math.max

/**
 * Smashed blocks: when the bubble breaks a row, every solid block splits
 * into chunks that tumble away — up, out and back past the camera — and
 * fall through the floor. Pooled, drawn in the batched pass.
 */
class Debris(private val game: Gdx3DGame) {

    private class Chunk(@JvmField val col: Color) {
        @JvmField var x = 0f; @JvmField var y = 0f; @JvmField var z = 0f
        @JvmField var vx = 0f; @JvmField var vy = 0f; @JvmField var vz = 0f
        @JvmField var s = 0.3f; @JvmField var yaw = 0f; @JvmField var spin = 0f; @JvmField var life = 0f
    }

    private val pool = Array(120) { Chunk(Color()) }
    private var live = 0

    /** Shatter one block at row depth [z]: a grid of chunks flung from its centre. */
    fun smash(ob: Ob, z: Float) {
        val nx = (ob.sx / 0.45f).toInt().coerceIn(1, 4)
        val ny = (ob.sy / 0.45f).toInt().coerceIn(1, 4)
        for (i in 0 until nx) for (j in 0 until ny) {
            if (live >= pool.size) return
            val c = pool[live++]
            c.col.set(ob.col)
            c.s = 0.28f + 0.1f * ((i + j) % 3)
            c.x = ob.x - ob.sx / 2f + ob.sx * (i + 0.5f) / nx
            c.y = ob.bottom + ob.sy * (j + 0.5f) / ny
            c.z = z
            val dx = c.x - ob.x
            c.vx = dx * 4f + (if (i % 2 == 0) 0.6f else -0.6f)
            c.vy = 4f + j * 1.6f + (i % 2) * 1.2f
            c.vz = 9f + (i + j) % 3 * 2f // back past the camera with the world
            c.yaw = 0f; c.spin = 260f + 90f * ((i * 3 + j) % 4)
            c.life = 1.4f
        }
    }

    fun update(dt: Float, mv: Float) {
        var i = 0
        while (i < live) {
            val c = pool[i]
            c.vy -= 22f * dt
            c.x += c.vx * dt; c.y += c.vy * dt; c.z += c.vz * dt + mv
            c.yaw += c.spin * dt
            c.life -= dt
            if (c.life <= 0f || c.y < -1.5f || c.z > 12f) { // retire (swap-remove)
                live--
                val l = pool[live]; pool[live] = c; pool[i] = l
                continue
            }
            i++
        }
    }

    fun render() {
        for (i in 0 until live) {
            val c = pool[i]
            val s = c.s * max(0.2f, kotlin.math.min(1f, c.life * 2f))
            game.worldBoxSpin(c.x, c.y, c.z, s, s, s, c.yaw, c.col)
        }
    }

    fun clear() { live = 0 }
}
