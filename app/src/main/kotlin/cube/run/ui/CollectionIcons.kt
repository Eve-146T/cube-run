package cube.run.ui

import android.graphics.Canvas
import android.graphics.Paint

/**
 * The pictures for the wardrobe-collection achievements: one bold, glossy silhouette each, in the
 * same lipped candy language as [ChallengeIcon] and on the same 48-unit grid.
 */
internal class CollectionIcon(private val id: String) : Icon() {

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 48f
        canvas.translate(bounds.exactCenterX() - 24f * scale, bounds.exactCenterY() - 24f * scale)
        canvas.scale(scale, scale)
        when (id) {
            "trailblazer" -> {
                // A glossy orb and the fat streaks it leaves behind.
                for ((y, from) in listOf(13f to 5f, 24f to 2f, 35f to 5f)) {
                    fill(Theme.darken(Theme.WHITE, .3f)); round(canvas, from, y - 3f + 2.5f, 20f, y + 3f + 2.5f, 3f)
                    fill(Theme.WHITE); round(canvas, from, y - 3f, 20f, y + 3f, 3f)
                }
                val cx = 32f; val cy = 24f; val r = 14f
                fill(Theme.darken(Theme.YELLOW, .3f)); canvas.drawCircle(cx, cy + 2.5f, r, paint)
                fill(Theme.YELLOW); canvas.drawCircle(cx, cy, r, paint)
                fill(Theme.lighten(Theme.YELLOW, .5f)); canvas.drawCircle(cx, cy - r * .08f, r * .66f, paint)
                shine(canvas, cx, cy, r, 2.6f)
            }
            "bubble_bath" -> {
                // A heap of foam: three bubbles, one big.
                bubble(canvas, 19f, 29f, 16f); bubble(canvas, 36f, 15f, 9f); bubble(canvas, 38f, 36f, 7f)
            }
        }
        canvas.restoreToCount(save)
    }

    private fun fill(color: Int) { paint.style = Paint.Style.FILL; paint.color = color }

    private fun round(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float) {
        rect.set(l, t, r, b); canvas.drawRoundRect(rect, radius, radius, paint)
    }

    private fun shine(canvas: Canvas, x: Float, y: Float, r: Float, width: Float) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = width; paint.strokeCap = Paint.Cap.ROUND; paint.color = Theme.WHITE
        rect.set(x - r * .55f, y - r * .55f, x + r * .55f, y + r * .55f); canvas.drawArc(rect, 195f, 70f, false, paint)
    }

    /** A glossy candy bubble, the same as the Big Bubble card's. */
    private fun bubble(canvas: Canvas, x: Float, y: Float, r: Float) {
        fill(Theme.darken(Theme.CYAN, .3f)); canvas.drawCircle(x, y + r * .12f, r, paint)
        fill(Theme.CYAN); canvas.drawCircle(x, y, r, paint)
        fill(Theme.lighten(Theme.CYAN, .62f)); canvas.drawCircle(x, y, r * .78f, paint)
        shine(canvas, x, y, r, r * .16f)
        fill(Theme.WHITE); canvas.drawCircle(x + r * .38f, y + r * .38f, r * .1f, paint)
    }
}
