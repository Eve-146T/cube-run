package cube.run.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import cube.run.data.Wardrobe
import kotlin.math.min

private fun drawVoid(c: Canvas, p: Paint, x: Float, y: Float, r: Float, angle: Float, opacity: Float) {
    p.style = Paint.Style.FILL; p.color = Theme.alpha(0xff000000.toInt(), (255 * opacity).toInt())
    c.drawCircle(x, y, r, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.025f
    p.color = Theme.alpha(0xff81758f.toInt(), (210 * opacity).toInt())
    c.drawCircle(x, y, r, p)
    c.save(); c.rotate(angle - 22f, x, y)
    p.color = Theme.alpha(0xffe8dff7.toInt(), (230 * opacity).toInt())
    p.strokeWidth = r * 0.045f
    c.drawArc(x - r * 1.32f, y - r * 0.41f, x + r * 1.32f, y + r * 0.41f, 8f, 151f, false, p)
    p.color = Theme.alpha(0xff696074.toInt(), (210 * opacity).toInt())
    c.drawArc(x - r * 1.32f, y - r * 0.41f, x + r * 1.32f, y + r * 0.41f, 185f, 152f, false, p)
    c.restore(); p.style = Paint.Style.FILL
}

internal class UnlockedCheckIcon : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val b = bounds; val r = min(b.width(), b.height()) * .4f
        val x = b.exactCenterX(); val y = b.exactCenterY()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = r * .32f
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND; paint.color = Theme.INK
        path.reset(); path.moveTo(x - r, y); path.lineTo(x - r * .3f, y + r * .65f); path.lineTo(x + r, y - r * .7f)
        canvas.drawPath(path, paint)
    }
}

internal class AchievementShopIcon : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val b = bounds; val x = b.exactCenterX(); val y = b.exactCenterY(); val r = min(b.width(), b.height()) * 0.39f
        paint.color = Theme.INK; paint.style = Paint.Style.FILL
        path.reset(); path.moveTo(x - r * 0.7f, y - r); path.lineTo(x + r * 0.7f, y - r)
        path.lineTo(x + r * 0.55f, y - r * 0.03f); path.quadTo(x, y + r * 0.7f, x - r * 0.55f, y - r * 0.03f); path.close()
        canvas.drawPath(path, paint)
        canvas.drawRect(x - r * 0.12f, y + r * 0.25f, x + r * 0.12f, y + r * 0.78f, paint)
        canvas.drawRoundRect(x - r * 0.48f, y + r * 0.7f, x + r * 0.48f, y + r * 0.94f, r * 0.1f, r * 0.1f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = r * 0.17f
        canvas.drawArc(x - r, y - r * 0.78f, x, y + r * 0.02f, 65f, 205f, false, paint)
        canvas.drawArc(x, y - r * 0.78f, x + r, y + r * 0.02f, -90f, 205f, false, paint)
        paint.style = Paint.Style.FILL
    }
}

/** Quiet monochrome previews retain each secret's silhouette before purchase. */
internal class SecretCosmeticIcon(private val category: Int) : Icon() {
    private val path = Path()
    private var opacity = 255
    override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun getAlpha() = opacity
    override fun draw(c: Canvas) {
        val checkpoint = if (opacity == 255) c.save() else
            c.saveLayerAlpha(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), opacity)
        val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY()
        val r = min(bounds.width(), bounds.height()) * 0.34f
        val pale = 0xffc7bfd8.toInt()
        when (category) {
            Wardrobe.CUBE -> {
                path.reset(); path.moveTo(cx, cy - r); path.lineTo(cx + r, cy - r * 0.5f)
                path.lineTo(cx + r, cy + r * 0.5f); path.lineTo(cx, cy + r)
                path.lineTo(cx - r, cy + r * 0.5f); path.lineTo(cx - r, cy - r * 0.5f); path.close()
                paint.style = Paint.Style.FILL; paint.color = 0xff030306.toInt(); c.drawPath(path, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = r * 0.065f; paint.color = pale; c.drawPath(path, paint)
                paint.color = 0xff716780.toInt()
                c.drawLine(cx - r, cy - r * 0.5f, cx, cy, paint); c.drawLine(cx, cy, cx + r, cy - r * 0.5f, paint)
                c.drawLine(cx, cy, cx, cy + r, paint)
            }
            Wardrobe.TRAIL -> {
                for (i in 0..4) {
                    val x = cx - r + i * r * 0.42f; val y = cy + r * 0.55f - i * r * 0.26f
                    val size = r * (0.12f + i * 0.075f)
                    paint.style = Paint.Style.FILL; paint.color = Theme.alpha(pale, 45 + i * 45)
                    c.save(); c.rotate(-17f, x, y); c.drawRoundRect(x - size, y - size, x + size, y + size, size * 0.24f, size * 0.24f, paint); c.restore()
                }
            }
            else -> {
                drawVoid(c, paint, cx, cy, r * 0.82f, -5f, 1f)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = r * 0.03f; paint.color = 0xff62596f.toInt()
                c.drawCircle(cx, cy, r * 1.17f, paint)
            }
        }
        paint.style = Paint.Style.FILL
        c.restoreToCount(checkpoint)
    }
}
