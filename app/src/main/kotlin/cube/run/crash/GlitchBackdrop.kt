package cube.run.crash

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import cube.run.ui.Theme
import kotlin.random.Random

/**
 * A flat, saturated "blue" screen that isn't always blue, with clusters of candy-coloured
 * blocks gnawing at its corners like a broken display. A few blocks flicker now and then.
 */
class GlitchBackdrop(context: Context, val color: Int, seed: Long) : View(context) {
    private val random = Random(seed)
    private val paint = Paint()
    private val candies = intArrayOf(Theme.YELLOW, Theme.MINT, Theme.SKY, Theme.PINK, Theme.LIME, Theme.ORANGE, Theme.GRAPE, Theme.WHITE)
        .filter { it != color }
    private class Block(val col: Int, val row: Int, var color: Int, var on: Boolean)
    private var blocks = emptyList<Block>()
    private var cell = 0f

    /** Text that must stay readable: no block is drawn behind these. */
    var keepClear: List<View> = emptyList()
    private val clear = ArrayList<android.graphics.RectF>()
    private val here = IntArray(2)
    private val there = IntArray(2)

    private val flicker = object : Runnable {
        override fun run() {
            repeat(3) { blocks.randomOrNull(random)?.let { it.on = random.nextFloat() < 0.7f; it.color = candies.random(random) } }
            invalidate()
            postDelayed(this, 140L + random.nextLong(260L))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cell = resources.displayMetrics.density * 22f
        val cols = (w / cell).toInt() + 1
        val rows = (h / cell).toInt() + 1
        val out = ArrayList<Block>()
        // Corner clusters thin out with distance; a ragged band crosses near the bottom.
        fun cluster(c0: Int, r0: Int, reach: Float) {
            for (c in 0 until cols) for (r in 0 until rows) {
                val d = kotlin.math.hypot((c - c0).toFloat(), (r - r0).toFloat())
                if (random.nextFloat() < 1f - d / reach) out += Block(c, r, candies.random(random), true)
            }
        }
        cluster(cols - 1, 0, 5.5f)
        cluster(0, rows - 1, 6.5f)
        val band = rows - 9 + random.nextInt(3)
        for (c in 0 until cols) if (random.nextFloat() < 0.18f) out += Block(c, band, candies.random(random), true)
        blocks = out
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); post(flicker) }
    override fun onDetachedFromWindow() { removeCallbacks(flicker); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(color)
        getLocationInWindow(here)
        clear.clear()
        for (v in keepClear) if (v.isShown) {
            v.getLocationInWindow(there)
            val x = (there[0] - here[0]).toFloat()
            val y = (there[1] - here[1]).toFloat()
            // A text view spans the column; only its inked lines need the space.
            val layout = (v as? android.widget.TextView)?.layout
            val left = layout?.let { l -> (0 until l.lineCount).minOf { l.getLineLeft(it) } + v.paddingLeft } ?: 0f
            val right = layout?.let { l -> (0 until l.lineCount).maxOf { l.getLineRight(it) } + v.paddingLeft } ?: v.width.toFloat()
            clear += android.graphics.RectF(x + left - cell / 2, y - cell / 2, x + right + cell / 2, y + v.height + cell / 2)
        }
        for (b in blocks) if (b.on) {
            val l = b.col * cell
            val t = b.row * cell
            if (clear.any { it.intersects(l, t, l + cell, t + cell) }) continue
            paint.color = b.color
            canvas.drawRect(l, t, l + cell, t + cell, paint)
        }
    }
}
