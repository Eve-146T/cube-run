package cube.run.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.min

/** The little pictures the HUD uses instead of glyphs. All vector, all candy, all drawn the same way everywhere. */
abstract class Icon : Drawable() {
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val rect = RectF()
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = 48
    override fun getIntrinsicHeight(): Int = 48
}

/** THE coin, matching the 3D one: a gold disc with a bevelled rim, a paler raised centre and a gloss arc. Used for every coin amount in the app. */
class CoinIcon : Icon() {
    override fun draw(canvas: Canvas) {
        val b = bounds
        val cx = b.exactCenterX(); val cy = b.exactCenterY()
        val r = min(b.width(), b.height()) / 2f
        paint.style = Paint.Style.FILL
        paint.color = Theme.darken(Theme.GOLD, 0.38f)        // the rim
        canvas.drawCircle(cx, cy, r, paint)
        paint.color = Theme.GOLD                              // the face
        canvas.drawCircle(cx, cy, r * 0.8f, paint)
        paint.color = Theme.darken(Theme.GOLD, 0.16f)        // the step down to the raised centre
        canvas.drawCircle(cx, cy + r * 0.06f, r * 0.5f, paint)
        paint.color = Theme.lighten(Theme.GOLD, 0.32f)       // the raised centre
        canvas.drawCircle(cx, cy - r * 0.02f, r * 0.46f, paint)
        paint.style = Paint.Style.STROKE                      // gloss along the top-left of the face
        paint.strokeWidth = r * 0.13f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Theme.alpha(Theme.WHITE, 200)
        rect.set(cx - r * 0.66f, cy - r * 0.66f, cx + r * 0.66f, cy + r * 0.66f)
        canvas.drawArc(rect, 200f, 55f, false, paint)
        paint.style = Paint.Style.FILL
    }
}

/** A soap bubble: a cyan ring with a soft fill and a highlight. */
class BubbleIcon(private val color: Int = Theme.CYAN) : Icon() {
    override fun draw(canvas: Canvas) {
        val b = bounds
        val cx = b.exactCenterX(); val cy = b.exactCenterY()
        val r = min(b.width(), b.height()) / 2f
        paint.style = Paint.Style.FILL
        paint.color = Theme.alpha(color, 70)
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.22f
        paint.color = color
        canvas.drawCircle(cx, cy, r * 0.86f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Theme.alpha(Theme.WHITE, 220)
        rect.set(cx - r * 0.6f, cy - r * 0.68f, cx - r * 0.05f, cy - r * 0.3f)
        canvas.drawOval(rect, paint)
    }
}

/** A gift box: a purple cube with a gold ribbon. */
class BoxIcon(private val color: Int = Theme.GRAPE) : Icon() {
    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()).toFloat()
        val x0 = b.exactCenterX() - s / 2f; val y0 = b.exactCenterY() - s / 2f
        val r = s * 0.18f
        paint.style = Paint.Style.FILL
        paint.color = Theme.darken(color, 0.3f)
        rect.set(x0, y0 + s * 0.12f, x0 + s, y0 + s)
        canvas.drawRoundRect(rect, r, r, paint)
        paint.color = color
        rect.set(x0, y0 + s * 0.05f, x0 + s, y0 + s * 0.92f)
        canvas.drawRoundRect(rect, r, r, paint)
        paint.color = Theme.GOLD
        canvas.drawRect(x0 + s * 0.4f, y0 + s * 0.05f, x0 + s * 0.6f, y0 + s * 0.92f, paint)
        canvas.drawRect(x0, y0 + s * 0.38f, x0 + s, y0 + s * 0.56f, paint)
        paint.color = Theme.lighten(Theme.GOLD, 0.3f)
        canvas.drawCircle(x0 + s * 0.5f, y0 + s * 0.12f, s * 0.12f, paint)
    }
}

/** A trophy cup for the best score. */
class TrophyIcon : Icon() {
    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()).toFloat()
        val x0 = b.exactCenterX() - s / 2f; val y0 = b.exactCenterY() - s / 2f
        paint.style = Paint.Style.FILL
        paint.color = Theme.darken(Theme.YELLOW, 0.25f)
        rect.set(x0 + s * 0.2f, y0 + s * 0.08f, x0 + s * 0.8f, y0 + s * 0.62f)
        canvas.drawRoundRect(rect, s * 0.15f, s * 0.3f, paint)
        paint.color = Theme.YELLOW
        rect.set(x0 + s * 0.24f, y0 + s * 0.05f, x0 + s * 0.76f, y0 + s * 0.56f)
        canvas.drawRoundRect(rect, s * 0.14f, s * 0.28f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.09f
        paint.color = Theme.YELLOW
        rect.set(x0 + s * 0.02f, y0 + s * 0.12f, x0 + s * 0.34f, y0 + s * 0.46f)
        canvas.drawArc(rect, 90f, 180f, false, paint)
        rect.set(x0 + s * 0.66f, y0 + s * 0.12f, x0 + s * 0.98f, y0 + s * 0.46f)
        canvas.drawArc(rect, 270f, 180f, false, paint)
        paint.style = Paint.Style.FILL
        paint.color = Theme.darken(Theme.YELLOW, 0.3f)
        canvas.drawRect(x0 + s * 0.42f, y0 + s * 0.55f, x0 + s * 0.58f, y0 + s * 0.78f, paint)
        rect.set(x0 + s * 0.25f, y0 + s * 0.74f, x0 + s * 0.75f, y0 + s * 0.94f)
        canvas.drawRoundRect(rect, s * 0.08f, s * 0.08f, paint)
        paint.color = Theme.alpha(Theme.WHITE, 170)
        rect.set(x0 + s * 0.3f, y0 + s * 0.1f, x0 + s * 0.42f, y0 + s * 0.34f)
        canvas.drawOval(rect, paint)
    }
}

/** A flame (the boost): a hot outer tongue, a bright core, a lick of white. */
class FlameIcon(private val color: Int = Theme.YELLOW) : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()).toFloat()
        val x0 = b.exactCenterX() - s / 2f; val y0 = b.exactCenterY() - s / 2f
        paint.style = Paint.Style.FILL
        fun flame(scale: Float, col: Int, lean: Float) {
            val cx = x0 + s / 2f; val bottom = y0 + s * 0.96f
            val w = s * 0.44f * scale; val h = s * 0.92f * scale
            path.reset()
            path.moveTo(cx + lean * w, bottom - h)
            path.cubicTo(cx + w * 1.05f, bottom - h * 0.6f, cx + w * 0.95f, bottom - h * 0.05f, cx, bottom)
            path.cubicTo(cx - w * 0.95f, bottom - h * 0.05f, cx - w * 1.05f, bottom - h * 0.6f, cx + lean * w, bottom - h)
            paint.color = col
            canvas.drawPath(path, paint)
        }
        flame(1f, Theme.darken(color, 0.3f), 0.25f)
        flame(0.92f, color, 0.18f)
        flame(0.58f, Theme.lighten(color, 0.55f), 0.05f)
        flame(0.3f, Theme.WHITE, 0f)
    }
}

/** A horseshoe magnet: a fat red U on a darker lip, silver tips. */
class MagnetIcon(private val color: Int = Theme.MAGNET) : Icon() {
    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()).toFloat()
        val x0 = b.exactCenterX() - s / 2f; val y0 = b.exactCenterY() - s / 2f
        paint.strokeCap = Paint.Cap.BUTT
        fun horseshoe(col: Int, dy: Float, w: Float) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = w
            paint.color = col
            rect.set(x0 + s * 0.2f, y0 + s * 0.1f + dy, x0 + s * 0.8f, y0 + s * 0.7f + dy)
            canvas.drawArc(rect, 180f, 180f, false, paint)
            canvas.drawLine(x0 + s * 0.32f, y0 + s * 0.4f + dy, x0 + s * 0.32f, y0 + s * 0.8f + dy, paint)
            canvas.drawLine(x0 + s * 0.68f, y0 + s * 0.4f + dy, x0 + s * 0.68f, y0 + s * 0.8f + dy, paint)
        }
        horseshoe(Theme.INK, 0f, s * 0.36f)                         // an ink outline, like everything on the stage
        horseshoe(color, 0f, s * 0.24f)
        paint.style = Paint.Style.FILL                                // pale tips with a dark seam
        paint.color = Theme.INK
        canvas.drawRect(x0 + s * 0.14f, y0 + s * 0.66f, x0 + s * 0.5f, y0 + s * 0.98f, paint)
        canvas.drawRect(x0 + s * 0.5f, y0 + s * 0.66f, x0 + s * 0.86f, y0 + s * 0.98f, paint)
        paint.color = Theme.lighten(Theme.SKY, 0.7f)
        canvas.drawRect(x0 + s * 0.2f, y0 + s * 0.72f, x0 + s * 0.44f, y0 + s * 0.92f, paint)
        canvas.drawRect(x0 + s * 0.56f, y0 + s * 0.72f, x0 + s * 0.8f, y0 + s * 0.92f, paint)
        paint.color = Theme.alpha(Theme.WHITE, 170)
        rect.set(x0 + s * 0.3f, y0 + s * 0.18f, x0 + s * 0.46f, y0 + s * 0.3f)
        canvas.drawOval(rect, paint)
    }
}

/** "2×" on a fat starburst with a darker lip. */
class MultIcon(private val color: Int = Theme.MULT) : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()).toFloat()
        val cx = b.exactCenterX(); val cy = b.exactCenterY()
        paint.style = Paint.Style.FILL
        fun burst(col: Int, dy: Float) {
            path.reset()
            for (i in 0 until 20) {
                val r = if (i % 2 == 0) s * 0.5f else s * 0.4f
                val a = Math.toRadians((i * 18f - 90f).toDouble())
                val px = cx + (r * Math.cos(a)).toFloat(); val py = cy + dy + (r * Math.sin(a)).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            paint.color = col
            canvas.drawPath(path, paint)
        }
        burst(Theme.darken(color, 0.35f), s * 0.06f)
        burst(color, 0f)
        paint.color = Theme.onColor(color)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = s * 0.46f
        paint.isFakeBoldText = true
        canvas.drawText("2×", cx, cy + s * 0.16f, paint)
        paint.isFakeBoldText = false
    }
}

/** A little rocket (the jetpack): nose cone, porthole, fins, a proper flame. */
class JetIcon(private val color: Int = Theme.JET) : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()).toFloat()
        val x0 = b.exactCenterX() - s / 2f; val y0 = b.exactCenterY() - s / 2f
        val cx = x0 + s / 2f
        paint.style = Paint.Style.FILL
        // flame: orange tongue, yellow core
        paint.color = Theme.ORANGE
        path.reset(); path.moveTo(cx - s * 0.2f, y0 + s * 0.72f); path.lineTo(cx + s * 0.2f, y0 + s * 0.72f); path.lineTo(cx, y0 + s * 1.0f); path.close()
        canvas.drawPath(path, paint)
        paint.color = Theme.YELLOW
        path.reset(); path.moveTo(cx - s * 0.1f, y0 + s * 0.72f); path.lineTo(cx + s * 0.1f, y0 + s * 0.72f); path.lineTo(cx, y0 + s * 0.9f); path.close()
        canvas.drawPath(path, paint)
        // fins
        paint.color = Theme.darken(color, 0.3f)
        path.reset(); path.moveTo(cx - s * 0.2f, y0 + s * 0.45f); path.lineTo(cx - s * 0.44f, y0 + s * 0.78f); path.lineTo(cx - s * 0.2f, y0 + s * 0.74f); path.close(); canvas.drawPath(path, paint)
        path.reset(); path.moveTo(cx + s * 0.2f, y0 + s * 0.45f); path.lineTo(cx + s * 0.44f, y0 + s * 0.78f); path.lineTo(cx + s * 0.2f, y0 + s * 0.74f); path.close(); canvas.drawPath(path, paint)
        // body with a rounded nose
        paint.color = color
        path.reset()
        path.moveTo(cx - s * 0.22f, y0 + s * 0.74f)
        path.lineTo(cx - s * 0.22f, y0 + s * 0.36f)
        path.cubicTo(cx - s * 0.22f, y0 + s * 0.1f, cx - s * 0.08f, y0, cx, y0)
        path.cubicTo(cx + s * 0.08f, y0, cx + s * 0.22f, y0 + s * 0.1f, cx + s * 0.22f, y0 + s * 0.36f)
        path.lineTo(cx + s * 0.22f, y0 + s * 0.74f)
        path.close()
        canvas.drawPath(path, paint)
        // a lighter stripe down the side, the nose tip, the porthole
        paint.color = Theme.lighten(color, 0.35f)
        canvas.drawRect(cx - s * 0.18f, y0 + s * 0.2f, cx - s * 0.1f, y0 + s * 0.7f, paint)
        paint.color = Theme.BERRY
        path.reset()
        path.moveTo(cx - s * 0.17f, y0 + s * 0.18f)
        path.cubicTo(cx - s * 0.12f, y0 + s * 0.05f, cx + s * 0.12f, y0 + s * 0.05f, cx + s * 0.17f, y0 + s * 0.18f)
        path.close()
        canvas.drawPath(path, paint)
        paint.color = Theme.INK
        canvas.drawCircle(cx, y0 + s * 0.38f, s * 0.11f, paint)
        paint.color = Theme.lighten(Theme.SKY, 0.6f)
        canvas.drawCircle(cx, y0 + s * 0.38f, s * 0.075f, paint)
    }
}

/** A shard: a crystal in the shard kind's colour, with an ink outline and a facet highlight. */
class ShardIcon(private val color: Int) : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()).toFloat()
        val cx = b.exactCenterX(); val cy = b.exactCenterY()
        path.reset()
        path.moveTo(cx, cy - s * 0.46f)
        path.lineTo(cx + s * 0.3f, cy - s * 0.12f)
        path.lineTo(cx + s * 0.18f, cy + s * 0.46f)
        path.lineTo(cx - s * 0.18f, cy + s * 0.46f)
        path.lineTo(cx - s * 0.3f, cy - s * 0.12f)
        path.close()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.14f
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Theme.INK
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawPath(path, paint)
        path.reset()
        path.moveTo(cx, cy - s * 0.46f)
        path.lineTo(cx - s * 0.3f, cy - s * 0.12f)
        path.lineTo(cx - s * 0.04f, cy + s * 0.1f)
        path.close()
        paint.color = Theme.alpha(Theme.WHITE, 170)
        canvas.drawPath(path, paint)
    }
}

/** A portal: a fat ring of candy beads. */
class PortalIcon(private val color: Int = Theme.MINT) : Icon() {
    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()).toFloat()
        val cx = b.exactCenterX(); val cy = b.exactCenterY()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.16f
        paint.color = Theme.darken(color, 0.35f)
        canvas.drawCircle(cx, cy + s * 0.05f, s * 0.36f, paint)
        paint.color = color
        canvas.drawCircle(cx, cy, s * 0.36f, paint)
        paint.style = Paint.Style.FILL
        for (i in 0 until 8) {
            val a = Math.toRadians((i * 45f - 90f).toDouble())
            paint.color = if (i % 2 == 0) Theme.WHITE else Theme.lighten(color, 0.5f)
            canvas.drawCircle(cx + (s * 0.36f * Math.cos(a)).toFloat(), cy + (s * 0.36f * Math.sin(a)).toFloat(), s * 0.07f, paint)
        }
        paint.color = Theme.alpha(Theme.WHITE, 120)
        canvas.drawCircle(cx, cy, s * 0.16f, paint)
    }
}

/** A heart with a shine (a second wind). */
class HeartIcon(private val color: Int = Theme.PINK) : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()).toFloat()
        val cx = b.exactCenterX(); val cy = b.exactCenterY()
        paint.style = Paint.Style.FILL
        fun heart(col: Int, dy: Float, scale: Float) {
            val w = s * 0.46f * scale; val top = cy - s * 0.28f * scale + dy; val bottom = cy + s * 0.44f * scale + dy
            path.reset()
            path.moveTo(cx, bottom)
            path.cubicTo(cx - w * 1.2f, bottom - (bottom - top) * 0.55f, cx - w * 1.1f, top - s * 0.05f, cx, top + s * 0.12f * scale)
            path.cubicTo(cx + w * 1.1f, top - s * 0.05f, cx + w * 1.2f, bottom - (bottom - top) * 0.55f, cx, bottom)
            path.close()
            paint.color = col
            canvas.drawPath(path, paint)
        }
        heart(Theme.darken(color, 0.35f), s * 0.06f, 1f)
        heart(color, 0f, 1f)
        paint.color = Theme.alpha(Theme.WHITE, 170)
        rect.set(cx - s * 0.34f, cy - s * 0.24f, cx - s * 0.12f, cy - s * 0.04f)
        canvas.drawOval(rect, paint)
    }
}

/**
 * A fat five-point star in the candy style: rounded points, an ink outline
 * like the stage text, a gloss. [color] = lit; ghosted when [lit] is false.
 */
class StarIcon(private val color: Int = Theme.YELLOW, private val lit: Boolean = true) : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()).toFloat()
        val cx = b.exactCenterX(); val cy = b.exactCenterY()
        fun star(scale: Float, dy: Float) {
            path.reset()
            for (i in 0 until 10) {
                val r = (if (i % 2 == 0) s * 0.46f else s * 0.22f) * scale
                val a = Math.toRadians((i * 36f - 90f).toDouble())
                val px = cx + (r * Math.cos(a)).toFloat(); val py = cy + dy + (r * Math.sin(a)).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
        }
        paint.pathEffect = android.graphics.CornerPathEffect(s * 0.07f)
        paint.strokeJoin = Paint.Join.ROUND
        star(1f, 0f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.16f
        paint.color = if (lit) Theme.INK else Theme.alpha(Theme.INK, 110)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.color = if (lit) color else Theme.alpha(Theme.WHITE, 90)
        canvas.drawPath(path, paint)
        if (lit) {
            star(0.5f, -s * 0.04f)
            paint.color = Theme.lighten(color, 0.5f)
            canvas.drawPath(path, paint)
        }
        paint.pathEffect = null
    }
}
