package cube.run.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

/**
 * An achievement's badge: its picture on a white face inside a chunky frame, standing on a
 * darker lip. The frame is what you collect. A medal family's frame is round and changes with
 * the highest medal claimed (a plain bronze ring, a scalloped silver rosette, a gold sunburst,
 * a cut diamond); a challenge's is a square sticker in its own colour. Nothing claimed yet is a
 * plain grey frame, so the picture still reads in colour.
 */
internal class AchievementBadge(private val id: String, private val tiered: Boolean, private val tier: Int) : Icon() {
    private val art = achievementIcon(id)
    private val path = Path()

    /** The frame's colour: grey until something is claimed. */
    private val frame: Int = when {
        tier < 0 -> NEUTRAL
        tiered -> medalColor(tier)
        else -> AchievementCards.accent(id)
    }

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 64f
        canvas.translate(bounds.exactCenterX() - 32f * scale, bounds.exactCenterY() - 32f * scale)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL
        // The lip, then the frame on it, then the face.
        outline(34.5f, 29.5f); paint.color = Theme.darken(frame, .3f); canvas.drawPath(path, paint)
        outline(31.5f, 29.5f); paint.color = frame; canvas.drawPath(path, paint)
        if (tiered && tier == 3) facets(canvas)
        // A gloss across the top of the frame.
        paint.color = Theme.alpha(Theme.WHITE, if (tier < 0) 120 else 105)
        if (tiered) {
            rect.set(8f, 7.5f, 56f, 55.5f); paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.strokeCap = Paint.Cap.ROUND
            canvas.drawArc(rect, 205f, 70f, false, paint); paint.style = Paint.Style.FILL
        } else {
            rect.set(13f, 5.5f, 51f, 8.5f); canvas.drawRoundRect(rect, 1.5f, 1.5f, paint)
        }
        face(canvas)
        art.setBounds(16, 15, 48, 47)
        art.draw(canvas)
        if (tiered && tier == 3) sparkle(canvas, 52f, 11f, 5f)
        canvas.restoreToCount(save)
    }

    private fun face(canvas: Canvas) {
        paint.color = Theme.darken(frame, .12f)
        if (tiered) canvas.drawCircle(32f, 32.5f, 20.5f, paint) else { rect.set(11f, 11f, 53f, 53f); canvas.drawRoundRect(rect, 10f, 10f, paint) }
        paint.color = Theme.WHITE
        if (tiered) canvas.drawCircle(32f, 31.5f, 19.5f, paint) else { rect.set(12f, 11f, 52f, 51f); canvas.drawRoundRect(rect, 9f, 9f, paint) }
    }

    /** The frame's silhouette centred on [cy], reaching [r]. */
    private fun outline(cy: Float, r: Float) {
        path.reset()
        if (!tiered) {
            val half = r - 1.5f
            rect.set(32f - half, cy - half, 32f + half, cy + half)
            path.addRoundRect(rect, 14f, 14f, Path.Direction.CW)
            return
        }
        when (tier) {
            1 -> star(cy, r, 12, 0.9f, smooth = true)
            2 -> star(cy, r, 16, .88f, smooth = false)
            3 -> {
                // A cut gem: a wide flat table on top, eight facets around.
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0 + 22.5)
                    val x = 32f + r * cos(a).toFloat(); val y = cy + r * sin(a).toFloat()
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            else -> path.addCircle(32f, cy, r, Path.Direction.CW)
        }
    }

    /** [points] lobes between [r] and [r] × [inner]: scallops when [smooth], sharp rays otherwise. */
    private fun star(cy: Float, r: Float, points: Int, inner: Float, smooth: Boolean) {
        val steps = if (smooth) points * 8 else points * 2
        for (k in 0 until steps) {
            val a = k * 2.0 * Math.PI / steps - Math.PI / 2
            val reach = if (smooth) r * (inner + (1f - inner) * (.5f + .5f * cos(points * a).toFloat()))
                else if (k % 2 == 0) r else r * inner
            val x = 32f + reach * cos(a).toFloat(); val y = cy + reach * sin(a).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    /** The diamond's cut: a light crown on the upper left, shade on the lower right. */
    private fun facets(canvas: Canvas) {
        val save = canvas.save()
        outline(31.5f, 29.5f); canvas.clipPath(path)
        paint.color = Theme.lighten(frame, .45f)
        path.reset(); path.moveTo(0f, 0f); path.lineTo(64f, 0f); path.lineTo(0f, 64f); path.close()
        canvas.drawPath(path, paint)
        paint.color = Theme.darken(frame, .1f)
        path.reset(); path.moveTo(64f, 24f); path.lineTo(64f, 64f); path.lineTo(24f, 64f); path.close()
        canvas.drawPath(path, paint)
        canvas.restoreToCount(save)
    }

    private fun sparkle(canvas: Canvas, x: Float, y: Float, r: Float) {
        paint.color = Theme.WHITE
        path.reset(); path.moveTo(x, y - r); path.lineTo(x + r * .3f, y - r * .3f); path.lineTo(x + r, y); path.lineTo(x + r * .3f, y + r * .3f)
        path.lineTo(x, y + r); path.lineTo(x - r * .3f, y + r * .3f); path.lineTo(x - r, y); path.lineTo(x - r * .3f, y - r * .3f); path.close()
        canvas.drawPath(path, paint)
    }

    override fun getIntrinsicWidth(): Int = 64
    override fun getIntrinsicHeight(): Int = 64

    companion object {
        /** The frame before anything is claimed. */
        const val NEUTRAL = 0xFFD6CFEE.toInt()
    }
}

/** A challenge sticker for the medal case: the challenges' square frame with a star on it. */
internal class StickerIcon(private val earned: Boolean) : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 32f
        canvas.translate(bounds.exactCenterX() - 16f * scale, bounds.exactCenterY() - 16f * scale)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL
        if (!earned) {
            rect.set(4f, 4f, 28f, 28f)
            paint.color = Theme.CARD_ALT; canvas.drawRoundRect(rect, 7f, 7f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.8f; paint.color = Theme.alpha(Theme.INK, 100)
            canvas.drawRoundRect(rect, 7f, 7f, paint)
            canvas.restoreToCount(save); return
        }
        rect.set(3f, 5f, 29f, 30f); paint.color = Theme.darken(Theme.MINT, .32f); canvas.drawRoundRect(rect, 8f, 8f, paint)
        rect.set(3f, 2.5f, 29f, 27.5f); paint.color = Theme.MINT; canvas.drawRoundRect(rect, 8f, 8f, paint)
        rect.set(7f, 4.5f, 25f, 6.5f); paint.color = Theme.alpha(Theme.WHITE, 120); canvas.drawRoundRect(rect, 1f, 1f, paint)
        paint.color = Theme.WHITE
        val cx = 16f; val cy = 15.5f
        path.reset()
        for (k in 0 until 10) {
            val a = Math.toRadians(k * 36.0 - 90.0)
            val r = if (k % 2 == 0) 8.5f else 3.8f
            val x = cx + r * cos(a).toFloat(); val y = cy + r * sin(a).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close(); canvas.drawPath(path, paint)
        canvas.restoreToCount(save)
    }
}

/** The glow of a frame about to be struck: a soft ring of [color] peeking out around the badge. */
internal class BadgeHalo(private val color: Int, private val badge: Float) : android.graphics.drawable.Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        val r = bounds.width() / 2f
        val inner = (badge / 2f / r).coerceIn(0f, .95f)
        paint.shader = android.graphics.RadialGradient(bounds.exactCenterX(), bounds.exactCenterY(), r,
            intArrayOf(Theme.alpha(color, 255), Theme.alpha(color, 255), Theme.alpha(Theme.lighten(color, .2f), 150), Theme.alpha(color, 0)),
            floatArrayOf(0f, inner * .9f, inner + (1f - inner) * .35f, 1f), android.graphics.Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), bounds.width() / 2f, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
