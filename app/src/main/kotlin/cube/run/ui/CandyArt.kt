package cube.run.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * The candy picture language shared by the achievement tiles and the shop: flat saturated
 * shapes on a darker lip, a white gloss, drawn on a 48-unit grid and centred on their painted
 * pixels. Subclasses draw in [picture] with the shape helpers below.
 */
internal abstract class CandyArt(private val key: String) : Icon() {
    protected val path = Path()

    /** Draw the picture on the 48-unit grid. */
    protected abstract fun picture(canvas: Canvas)

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 48f
        canvas.translate(bounds.exactCenterX() - 24f * scale, bounds.exactCenterY() - 24f * scale)
        canvas.scale(scale, scale)
        val shift = centering.getOrPut(key) { measureShift() }
        canvas.translate(shift.x, shift.y)
        render(canvas)
        canvas.restoreToCount(save)
    }

    private fun render(canvas: Canvas) {
        lastCanvas = canvas
        picture(canvas)
        lastCanvas = null
    }

    /** Lips and sparkles make each drawing lopsided: move its painted pixels onto the tile's centre. */
    private fun measureShift(): android.graphics.PointF {
        val size = 96
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        render(Canvas(bitmap).apply { scale(size / 48f, size / 48f) })
        val pixels = IntArray(size * size); bitmap.getPixels(pixels, 0, size, 0, 0, size, size); bitmap.recycle()
        var left = size; var top = size; var right = -1; var bottom = -1
        for (y in 0 until size) for (x in 0 until size) if (pixels[y * size + x] ushr 24 > 24) {
            left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
        }
        if (right < 0) return android.graphics.PointF()
        val half = size / 48f
        return android.graphics.PointF(24f - (left + right + 1) / 2f / half, 24f - (top + bottom + 1) / 2f / half)
    }

    protected fun fill(color: Int) { paint.style = Paint.Style.FILL; paint.color = color }

    /** One shape twice: a darker copy dropped below it as its lip, then the shape itself. */
    protected inline fun lipped(color: Int, draw: () -> Unit) {
        fill(Theme.darken(color, .3f)); lastCanvas?.let { it.save(); it.translate(0f, 2.5f); draw(); it.restore() }
        fill(color); draw()
    }

    /** The canvas being drawn on, for the shape helpers below. */
    protected var lastCanvas: Canvas? = null

    protected fun disc(x: Float, y: Float, r: Float, color: Int) {
        fill(Theme.darken(color, .3f)); lastCanvas!!.drawCircle(x, y + 2.5f, r, paint)
        fill(color); lastCanvas!!.drawCircle(x, y, r, paint)
    }

    protected fun gloss(x: Float, y: Float, r: Float) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.6f; paint.color = Theme.alpha(Theme.WHITE, 200)
        rect.set(x - r + 4f, y - r + 4f, x + r - 4f, y + r - 4f); lastCanvas!!.drawArc(rect, 200f, 60f, false, paint)
    }

    protected fun shape(vararg p: Float) {
        path.reset(); path.moveTo(p[0], p[1]); for (i in 2 until p.size step 2) path.lineTo(p[i], p[i + 1]); path.close()
        lastCanvas!!.drawPath(path, paint)
    }

    protected fun round(l: Float, t: Float, r: Float, b: Float, radius: Float) {
        rect.set(l, t, r, b); lastCanvas!!.drawRoundRect(rect, radius, radius, paint)
    }

    protected fun line(x: Float, y: Float, xx: Float, yy: Float, color: Int, width: Float) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = width; paint.color = color
        lastCanvas!!.drawLine(x, y, xx, yy, paint)
    }

    protected fun icon(icon: Drawable, x: Float, y: Float, size: Float) {
        icon.setBounds(0, 0, 480, 480)
        val c = lastCanvas!!; c.save(); c.translate(x, y); c.scale(size / 480f, size / 480f); icon.draw(c); c.restore()
    }

    /** A four-point twinkle. */
    protected fun sparkle(x: Float, y: Float, r: Float, color: Int = Theme.WHITE) {
        fill(color)
        shape(x, y - r, x + r * .28f, y - r * .28f, x + r, y, x + r * .28f, y + r * .28f, x, y + r, x - r * .28f, y + r * .28f, x - r, y, x - r * .28f, y - r * .28f)
    }

    protected fun crescent(x: Float, y: Float) {
        path.reset(); path.addCircle(x, y, 16f, Path.Direction.CW)
        val bite = Path().apply { addCircle(x + 9f, y - 7f, 13f, Path.Direction.CW) }
        path.op(bite, Path.Op.DIFFERENCE)
        lastCanvas!!.drawPath(path, paint)
    }

    protected fun arrow(x: Float, y: Float, dir: Float, color: Int) {
        lipped(color) {
            shape(x, y - 3.5f, x + dir * 24f, y - 3.5f, x + dir * 24f, y - 9f, x + dir * 36f, y, x + dir * 24f, y + 9f, x + dir * 24f, y + 3.5f, x, y + 3.5f)
        }
    }

    protected fun burst(x: Float, y: Float, color: Int, size: Float = 10f, core: Int = Theme.YELLOW) {
        path.reset()
        for (k in 0 until 16) {
            val r = if (k % 2 == 0) size else size / 2f
            val a = Math.toRadians(k * 22.5 - 90.0)
            val px = x + (r * Math.cos(a)).toFloat(); val py = y + (r * Math.sin(a)).toFloat()
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        fill(Theme.darken(color, .3f)); lastCanvas!!.save(); lastCanvas!!.translate(0f, 2f); lastCanvas!!.drawPath(path, paint); lastCanvas!!.restore()
        fill(color); lastCanvas!!.drawPath(path, paint)
        fill(core); lastCanvas!!.drawCircle(x, y, size * .3f, paint)
    }

    /**
     * A chunky glossy question mark in the game's rounded font, its baseline at ([x], [y]),
     * [size] high: a darker lip under it, a paler top and a white glint on the hook.
     */
    protected fun mystery(x: Float, y: Float, size: Float, color: Int, weight: Float = 0f) {
        val c = lastCanvas!!
        paint.typeface = Fonts.cached(700); paint.textSize = size; paint.textAlign = Paint.Align.CENTER
        val glyph = Path(); paint.getTextPath("?", 0, 1, x, y, glyph)
        if (weight > 0f) fatten(glyph, weight)
        paint.textAlign = Paint.Align.LEFT
        val box = RectF(); glyph.computeBounds(box, true)
        fill(Theme.darken(color, .38f)); c.save(); c.translate(0f, size * .07f); c.drawPath(glyph, paint); c.restore()
        fill(color); c.drawPath(glyph, paint)
        c.save(); c.clipPath(glyph)
        fill(Theme.lighten(color, .4f)); c.drawRect(box.left, box.top, box.right, box.top + box.height() * .22f, paint)
        fill(Theme.alpha(Theme.WHITE, 230))
        rect.set(box.left + box.width() * .12f, box.top + box.height() * .08f, box.left + box.width() * .4f, box.top + box.height() * .17f)
        c.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint)
        c.restore()
    }

    /** Grow [outline] by [weight] on every side, with round corners: fatter letters. */
    protected fun fatten(outline: Path, weight: Float) {
        val stroke = Paint(paint).apply { style = Paint.Style.STROKE; strokeWidth = weight * 2f; strokeJoin = Paint.Join.ROUND }
        val grown = Path(); stroke.getFillPath(outline, grown); outline.op(grown, Path.Op.UNION)
    }

    /** A pointy polygon with every corner rounded off by [radius]. */
    protected fun rounded(out: Path, radius: Float, vararg p: Float) {
        out.reset()
        val n = p.size / 2
        for (i in 0 until n) {
            val x = p[2 * i]; val y = p[2 * i + 1]
            val px = p[2 * ((i + n - 1) % n)]; val py = p[2 * ((i + n - 1) % n) + 1]
            val nx = p[2 * ((i + 1) % n)]; val ny = p[2 * ((i + 1) % n) + 1]
            val d1 = kotlin.math.hypot(px - x, py - y); val d2 = kotlin.math.hypot(nx - x, ny - y)
            val k1 = minOf(radius, d1 / 2f) / d1; val k2 = minOf(radius, d2 / 2f) / d2
            if (i == 0) out.moveTo(x + (px - x) * k1, y + (py - y) * k1) else out.lineTo(x + (px - x) * k1, y + (py - y) * k1)
            out.quadTo(x, y, x + (nx - x) * k2, y + (ny - y) * k2)
        }
        out.close()
    }

    /**
     * The chunky toy cube at ([x], [y]) in a [size] square: soft rounded corners, a darker lip
     * under it, a bright top, a mid left face, a shaded right face and a gloss on top.
     */
    protected fun toyCube(x: Float, y: Float, size: Float, color: Int) {
        val c = lastCanvas!!
        val h = size / 2f; val r = size / 4f
        val outline = Path()
        fun silhouette(dy: Float) = rounded(outline, size * .14f, x + h, y + dy, x + size, y + r + dy, x + size, y + size - r + dy, x + h, y + size + dy, x, y + size - r + dy, x, y + r + dy)
        fill(Theme.darken(color, .45f)); silhouette(size * .09f); c.drawPath(outline, paint)
        silhouette(0f)
        c.save(); c.clipPath(outline)
        val left = { u: Float, v: Float -> floatArrayOf(x + u * h, y + r + u * r + v * 2f * r) }
        val right = { u: Float, v: Float -> floatArrayOf(x + h + u * h, y + 2f * r - u * r + v * 2f * r) }
        val top = { u: Float, w: Float -> floatArrayOf(x + u * h + w * h, y + r + u * r - w * r) }
        fun face(color: Int, map: (Float, Float) -> FloatArray, a: Float = 0f, b: Float = 1f, cc: Float = 0f, d: Float = 1f) {
            val p1 = map(a, cc); val p2 = map(b, cc); val p3 = map(b, d); val p4 = map(a, d)
            fill(color); shape(p1[0], p1[1], p2[0], p2[1], p3[0], p3[1], p4[0], p4[1])
        }
        face(color, left); face(Theme.darken(color, .2f), right); face(Theme.lighten(color, .38f), top)
        // Bevel: a light edge where the top meets the sides, and a gloss on the top's far corner.
        paint.style = Paint.Style.STROKE; paint.strokeWidth = size * .05f; paint.strokeJoin = Paint.Join.ROUND; paint.color = Theme.alpha(Theme.WHITE, 120)
        val a = left(0f, 0f); val m = left(1f, 0f); val e = right(1f, 0f)
        path.reset(); path.moveTo(a[0], a[1]); path.lineTo(m[0], m[1]); path.lineTo(e[0], e[1]); c.drawPath(path, paint)
        val g1 = top(.18f, .62f); val g2 = top(.18f, .86f); val g3 = top(.42f, .86f)
        paint.strokeWidth = size * .07f; paint.strokeCap = Paint.Cap.ROUND; paint.color = Theme.alpha(Theme.WHITE, 200)
        path.reset(); path.moveTo(g1[0], g1[1]); path.lineTo(g2[0], g2[1]); path.lineTo(g3[0], g3[1]); c.drawPath(path, paint)
        c.restore()
    }

    /** A glossy candy bubble: a cyan rim on a darker lip, a pale face and a big white glint. */
    protected fun bubble(x: Float, y: Float, r: Float) {
        val c = lastCanvas!!
        fill(Theme.darken(Theme.CYAN, .3f)); c.drawCircle(x, y + r * .12f, r, paint)
        fill(Theme.CYAN); c.drawCircle(x, y, r, paint)
        fill(Theme.lighten(Theme.CYAN, .62f)); c.drawCircle(x, y, r * .78f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = r * .16f; paint.strokeCap = Paint.Cap.ROUND; paint.color = Theme.WHITE
        rect.set(x - r * .55f, y - r * .55f, x + r * .55f, y + r * .55f); c.drawArc(rect, 195f, 70f, false, paint)
        fill(Theme.WHITE); c.drawCircle(x + r * .38f, y + r * .38f, r * .1f, paint)
    }

    /** [n] coins seen edge-on, stacked from [bottom] up, the top one face-up. */
    protected fun coinStack(cx: Float, bottom: Float, n: Int) {
        val w = 17f
        for (i in 0 until n) {
            val y = bottom - i * 4.6f
            fill(Theme.darken(Theme.GOLD, .38f)); rect.set(cx - w / 2f, y - 7f, cx + w / 2f, y + 1.5f); lastCanvas!!.drawRoundRect(rect, w / 2f, 3.5f, paint)
            fill(Theme.darken(Theme.GOLD, .12f)); rect.set(cx - w / 2f, y - 7f, cx + w / 2f, y - .5f); lastCanvas!!.drawRoundRect(rect, w / 2f, 3.2f, paint)
            fill(Theme.GOLD); rect.set(cx - w / 2f, y - 9f, cx + w / 2f, y - 3f); lastCanvas!!.drawOval(rect, paint)
        }
        val top = bottom - (n - 1) * 4.6f
        fill(Theme.lighten(Theme.GOLD, .35f)); rect.set(cx - w * .3f, top - 7.5f, cx + w * .3f, top - 4.5f); lastCanvas!!.drawOval(rect, paint)
    }

    /** The game's bevelled isometric cube. */
    protected fun cube(x: Float, y: Float, size: Float, color: Int) {
        val half = size / 2f; val rise = size / 4f
        fill(Theme.lighten(color, .4f)); shape(x, y + rise, x + half, y, x + size, y + rise, x + half, y + 2 * rise)
        fill(color); shape(x, y + rise, x + half, y + 2 * rise, x + half, y + size, x, y + size - rise)
        fill(Theme.darken(color, .2f)); shape(x + half, y + 2 * rise, x + size, y + rise, x + size, y + size - rise, x + half, y + size)
    }

    private companion object {
        val centering = java.util.concurrent.ConcurrentHashMap<String, android.graphics.PointF>()
    }
}
