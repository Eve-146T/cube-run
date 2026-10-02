package cube.run.game.track

import com.badlogic.gdx.graphics.Color
import cube.run.core.Gdx3DGame
import kotlin.math.ceil

/** A block keeps its silhouette, then peels into weightless ink and ivory tiles. */
class ZenDissolve(private val game: Gdx3DGame) {
    private class Tile {
        val source = Color()
        val target = Color()
        var x = 0f; var y = 0f; var z = 0f
        var sx = 0f; var sy = 0f; var sz = 0f
        var dx = 0f; var dz = 0f
        var delay = 0f; var age = 0f; var duration = 0f; var spin = 0f
    }

    private val pool = Array(384) { Tile() }
    private val color = Color()
    private var live = 0

    fun melt(ob: Ob, z: Float) {
        val nx = ceil(ob.sx / 0.5f).toInt().coerceIn(1, 6)
        val ny = ceil(ob.sy / 0.5f).toInt().coerceIn(1, 6)
        val nz = ceil(ob.sz / 0.5f).toInt().coerceIn(1, 3)
        for (j in 0 until ny) for (i in 0 until nx) for (k in 0 until nz) {
            // Recycle the oldest tile on overflow, so a new collision always gets a silhouette.
            val t = if (live < pool.size) pool[live++] else {
                var oldest = 0
                for (n in 1 until live) if (pool[n].age > pool[oldest].age) oldest = n
                pool[oldest]
            }
            val pattern = (i * 7 + j * 11 + k * 3) % 7
            t.source.set(ob.col)
            t.target.set(if ((i + j + k) % 3 == 0) INK else IVORY)
            t.sx = ob.sx / nx; t.sy = ob.sy / ny; t.sz = ob.sz / nz
            t.x = ob.x - ob.sx * 0.5f + (i + 0.5f) * t.sx
            t.y = ob.bottom + (j + 0.5f) * t.sy
            t.z = z - ob.sz * 0.5f + (k + 0.5f) * t.sz
            t.dx = (t.x - ob.x) * 0.55f + (pattern - 3) * 0.055f
            t.dz = (k - (nz - 1) * 0.5f) * 0.3f
            // Top tiles release first; lower tiles follow in a soft wave.
            t.delay = (ny - 1 - j) * 0.025f + pattern * 0.008f
            t.duration = 0.65f + pattern * 0.035f
            t.spin = (if ((i + k) % 2 == 0) 1f else -1f) * (35f + pattern * 6f)
            t.age = 0f
        }
    }

    fun update(dt: Float, mv: Float) {
        var i = 0
        while (i < live) {
            val t = pool[i]
            t.age += dt
            val p = ((t.age - t.delay) / t.duration).coerceIn(0f, 1f)
            // Released tiles gradually leave the rushing world and linger around the impact.
            t.z += mv * (1f - 0.75f * p)
            if (t.age >= t.delay + t.duration || t.z > 12f) {
                live--
                pool[i] = pool[live]; pool[live] = t
            } else i++
        }
    }

    fun render() {
        for (i in 0 until live) {
            val t = pool[i]
            val p = ((t.age - t.delay) / t.duration).coerceIn(0f, 1f)
            val ease = p * p * (3f - 2f * p)
            val shrink = 1f - ease
            color.set(t.source).lerp(t.target, (t.age / 0.18f).coerceIn(0f, 1f))
            game.worldBoxSpin(t.x + t.dx * ease, t.y + 1.4f * ease,
                t.z + t.dz * ease, t.sx * shrink, t.sy * shrink, t.sz * shrink,
                t.spin * ease, color)
        }
    }

    fun clear() { live = 0 }

    private companion object {
        val INK = Color(0.06f, 0.06f, 0.07f, 1f)
        val IVORY = Color(0.96f, 0.97f, 1f, 1f)
    }
}
