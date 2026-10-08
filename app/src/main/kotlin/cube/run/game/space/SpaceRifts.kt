package cube.run.game.space

import com.badlogic.gdx.graphics.Color
import cube.run.core.Gdx3DGame
import cube.run.game.Lanes
import cube.run.game.track.ObType
import cube.run.game.track.Row
import cube.run.game.world.Fog

/**
 * Space's rifts as real holes in the glass road. Each frame the full-width
 * pits on the road are gathered into whole gaps (a rift is several slices
 * laid back to back); the road leaves those spans open, so you look straight
 * down into space, and one glowing frame outlines each gap, so its full
 * length reads at a glance. The pits still collide exactly as they always did.
 */
class SpaceRifts(private val game: Gdx3DGame) {
    private val near = FloatArray(MAX)
    private val far = FloatArray(MAX)
    private val lips = Array(MAX) { Color() }
    private val bright = Color()

    /** Gaps this frame, nearest first. */
    var count = 0
        private set

    fun near(i: Int) = near[i]
    fun far(i: Int) = far[i]

    /** Gather this frame's gaps from [rows]. */
    fun update(rows: List<Row>) {
        count = 0
        for (row in rows) {
            if (row.pop <= 0.001f) continue
            for (ob in row.obs) {
                if (!ob.pit || ob.type == ObType.DECO || ob.sx < WIDE) continue
                val lip = row.obs.firstOrNull { it.pit && it.type == ObType.DECO }?.col
                add(row.z + ob.sz / 2f, row.z - ob.sz / 2f, lip)
            }
        }
    }

    /** Insert a slice, nearest first, joining it to a gap it touches. */
    private fun add(n: Float, f: Float, lip: Color?) {
        for (i in 0 until count) {
            if (n >= far[i] - JOIN && f <= near[i] + JOIN) { // touches gap i: grow it
                if (n > near[i]) near[i] = n
                if (f < far[i]) far[i] = f
                settle(i)
                return
            }
        }
        if (count == MAX) return
        var i = count++
        while (i > 0 && near[i - 1] < n) { near[i] = near[i - 1]; far[i] = far[i - 1]; lips[i].set(lips[i - 1]); i-- }
        near[i] = n; far[i] = f
        if (lip != null) lips[i].set(lip) else lips[i].set(Color.WHITE)
    }

    /** After gap [i] grew, merge it with any neighbour it now touches. */
    private fun settle(i: Int) {
        var k = 0
        while (k < count) {
            if (k != i && near[k] >= far[i] - JOIN && far[k] <= near[i] + JOIN) {
                near[i] = maxOf(near[i], near[k]); far[i] = minOf(far[i], far[k])
                for (j in k until count - 1) { near[j] = near[j + 1]; far[j] = far[j + 1]; lips[j].set(lips[j + 1]) }
                count--
                return settle(if (k < i) i - 1 else i)
            }
            k++
        }
    }

    /** The glowing frame round each gap: across both ends and along both sides, flush with the road. */
    fun render() {
        val half = Lanes.halfRoadDrawn
        for (i in 0 until count) {
            val n = near[i]; val f = far[i]
            bright.set(lips[i]).lerp(Color.WHITE, 0.2f)
            val len = n - f
            val mid = (n + f) / 2f
            game.worldBox(0f, 0f, n, half * 2f, BAR_H, BAR, bright, Fog.at(n))
            game.worldBox(0f, 0f, f, half * 2f, BAR_H, BAR, bright, Fog.at(f))
            for (side in -1..1 step 2) game.worldBox(side * (half - BAR / 2f), 0f, mid, BAR, BAR_H, len, bright, Fog.at(mid))
        }
    }

    companion object {
        /** Pits at least this wide (a full road) are rifts. */
        const val WIDE = 4f
        private const val MAX = 8
        /** Slices this close join into one gap. */
        private const val JOIN = 0.3f
        private const val BAR = 0.18f
        private const val BAR_H = 0.1f
    }
}
