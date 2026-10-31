package cube.run.game.space

import com.badlogic.gdx.graphics.Color
import cube.run.core.Gdx3DGame
import cube.run.game.Lanes
import cube.run.game.track.ObType
import cube.run.game.track.Row
import cube.run.game.world.Fog
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Space's voids as real holes in the glass road. Each frame the pits on the
 * road are gathered into whole holes (a void is several slices laid back to
 * back): a rift across the road, or the open lanes beside a bridge. The road
 * leaves those spans open, so you look straight down into space, and one
 * glowing frame outlines each hole, so its full extent reads at a glance.
 */
class SpaceRifts(private val game: Gdx3DGame) {
    private val near = FloatArray(MAX)
    private val far = FloatArray(MAX)
    private val left = FloatArray(MAX)
    private val right = FloatArray(MAX)
    private val lips = Array(MAX) { Color() }
    private val bright = Color()

    /** Holes this frame, nearest first. */
    var count = 0
        private set

    fun near(i: Int) = near[i]
    fun far(i: Int) = far[i]
    fun left(i: Int) = left[i]
    fun right(i: Int) = right[i]

    /** Gather this frame's holes from [rows]. */
    fun update(rows: List<Row>) {
        count = 0
        for (row in rows) {
            if (row.pop <= 0.001f) continue
            for (ob in row.obs) {
                if (!ob.pit || ob.type == ObType.DECO) continue
                val lip = row.obs.firstOrNull { it.pit && it.type == ObType.DECO && abs(it.x - ob.x) < 0.01f }?.col
                add(row.z + ob.sz / 2f, row.z - ob.sz / 2f, ob.x - ob.sx / 2f, ob.x + ob.sx / 2f, lip)
            }
        }
    }

    /** Insert a slice, nearest first, joining it to a hole it continues (touching, and as wide). */
    private fun add(n: Float, f: Float, l: Float, r: Float, lip: Color?) {
        for (i in 0 until count) {
            if (n >= far[i] - JOIN && f <= near[i] + JOIN && abs(l - left[i]) < 0.05f && abs(r - right[i]) < 0.05f) {
                if (n > near[i]) near[i] = n
                if (f < far[i]) far[i] = f
                settle(i)
                return
            }
        }
        if (count == MAX) return
        var i = count++
        while (i > 0 && near[i - 1] < n) { copy(i - 1, i); i-- }
        near[i] = n; far[i] = f; left[i] = l; right[i] = r
        if (lip != null) lips[i].set(lip) else lips[i].set(Color.WHITE)
    }

    private fun copy(from: Int, to: Int) {
        near[to] = near[from]; far[to] = far[from]; left[to] = left[from]; right[to] = right[from]; lips[to].set(lips[from])
    }

    /** After hole [i] grew, merge it with any neighbour of the same width it now touches. */
    private fun settle(i: Int) {
        var k = 0
        while (k < count) {
            if (k != i && near[k] >= far[i] - JOIN && far[k] <= near[i] + JOIN &&
                abs(left[k] - left[i]) < 0.05f && abs(right[k] - right[i]) < 0.05f) {
                near[i] = max(near[i], near[k]); far[i] = min(far[i], far[k])
                for (j in k until count - 1) copy(j + 1, j)
                count--
                return settle(if (k < i) i - 1 else i)
            }
            k++
        }
    }

    /** True when hole [i] opens the lane centred on [x]. */
    fun covers(i: Int, x: Float) = x > left[i] && x < right[i]

    /** The glowing frame round each hole: across both ends and along both sides, flush with the road. */
    fun render() {
        val half = Lanes.halfRoadDrawn
        for (i in 0 until count) {
            val n = near[i]; val f = far[i]
            val l = max(left[i], -half); val r = min(right[i], half)
            bright.set(lips[i]).lerp(Color.WHITE, 0.2f)
            val len = n - f
            game.worldBox((l + r) / 2f, 0f, n, r - l, BAR_H, BAR, bright, Fog.at(n), Fog.appear(n))
            game.worldBox((l + r) / 2f, 0f, f, r - l, BAR_H, BAR, bright, Fog.at(f), Fog.appear(f))
            // The sides in short pieces: a box bends only at its corners, and the road bends between them.
            val pieces = kotlin.math.ceil(len / PIECE).toInt().coerceAtLeast(1)
            val step = len / pieces
            for (k in 0 until pieces) {
                val z = n - step * (k + 0.5f)
                game.worldBox(l + BAR / 2f, 0f, z, BAR, BAR_H, step, bright, Fog.at(z), Fog.appear(z))
                game.worldBox(r - BAR / 2f, 0f, z, BAR, BAR_H, step, bright, Fog.at(z), Fog.appear(z))
            }
        }
    }

    companion object {
        private const val MAX = 12
        /** Slices this close join into one hole. */
        private const val JOIN = 0.3f
        private const val BAR = 0.18f
        private const val BAR_H = 0.1f
        /** The longest straight piece of a hole's side (the road's tiles are 3 long). */
        private const val PIECE = 1.5f
    }
}
