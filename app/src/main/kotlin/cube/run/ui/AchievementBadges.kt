package cube.run.ui

import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

/**
 * The badges a medal family pays out. Every badge is the same chunky hexagon: a metal frame on a
 * darker lip, a sunk face, a gloss along the top edge and white rank chevrons stamped on it.
 * Bronze has one chevron, silver two, gold three; diamond is crystal blue with a cut gem.
 * Not yet won, the same hexagon is a pale recessed slot, so a row of badges keeps its shape.
 * Drawn on a 64-unit grid.
 */
internal class BadgeArt(private val tier: Int, private val owned: Boolean) : Icon() {
    private val path = Path()
    private val rounded = CornerPathEffect(7f)

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 64f
        canvas.translate(bounds.exactCenterX() - 32f * scale, bounds.exactCenterY() - 32f * scale)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL
        val t = tier.coerceIn(0, 3)
        val metal = if (owned) METALS[t] else SLOT
        paint.pathEffect = rounded
        // An empty slot is sunk in: its darker edge is on top instead of underneath. Either way
        // badge and edge together sit centred in the box.
        val frame = if (owned) 30.5f else 33.5f
        paint.color = if (owned) Theme.darken(metal, .3f) else SLOT_EDGE
        hexagon(if (owned) frame + 3f else frame - 3f, 29f); canvas.drawPath(path, paint)
        canvas.translate(0f, frame - 31f)
        paint.color = metal; hexagon(31f, 29f); canvas.drawPath(path, paint)
        paint.color = if (owned) Theme.darken(metal, if (t == 2) .2f else .14f) else Theme.darken(SLOT, .04f)
        hexagon(31f, 21f); canvas.drawPath(path, paint)
        paint.pathEffect = null
        if (owned) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.6f; paint.strokeCap = Paint.Cap.ROUND
            paint.color = Theme.alpha(Theme.WHITE, 190)
            canvas.drawLine(10.5f, 20f, 26f, 8.5f, paint)
            paint.style = Paint.Style.FILL
        }
        emblem(canvas, t, if (owned) Theme.WHITE else SLOT_EDGE, if (owned) Theme.darken(metal, .4f) else null)
        canvas.restoreToCount(save)
    }

    /** A pointy-topped hexagon of radius [r] around (32, [cy]). */
    private fun hexagon(cy: Float, r: Float) {
        path.reset()
        for (k in 0 until 6) {
            val a = Math.toRadians(k * 60.0 - 90.0)
            val x = 32f + r * cos(a).toFloat(); val y = cy + r * sin(a).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    /** One to three chevrons, or diamond's gem; stamped in, so a darker copy sits under each. */
    private fun emblem(canvas: Canvas, tier: Int, color: Int, lip: Int?) {
        for (pass in 0..1) {
            if (pass == 0 && lip == null) continue
            val dy = if (pass == 0) 1.8f else 0f
            paint.color = if (pass == 0) lip!! else color
            if (tier == 3) {
                paint.style = Paint.Style.FILL
                path.reset(); path.moveTo(25f, 23f + dy); path.lineTo(39f, 23f + dy); path.lineTo(45f, 29.5f + dy)
                path.lineTo(32f, 42f + dy); path.lineTo(19f, 29.5f + dy); path.close(); canvas.drawPath(path, paint)
                if (pass == 1 && lip != null) {
                    paint.color = Theme.lighten(METALS[3], .55f)
                    path.reset(); path.moveTo(19f, 29.5f); path.lineTo(45f, 29.5f); path.lineTo(32f, 42f); path.close(); canvas.drawPath(path, paint)
                }
                continue
            }
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
            val top = 31f - tier * 3.6f
            for (i in 0..tier) {
                val y = top + i * 7.2f + dy
                path.reset(); path.moveTo(22.5f, y + 4.5f); path.lineTo(32f, y - 3f); path.lineTo(41.5f, y + 4.5f)
                canvas.drawPath(path, paint)
            }
            paint.style = Paint.Style.FILL
        }
    }

    override fun getIntrinsicWidth(): Int = 64
    override fun getIntrinsicHeight(): Int = 64

    companion object {
        /** Bronze, silver, gold, diamond. */
        val METALS = intArrayOf(0xFFD9864F.toInt(), 0xFFA7B5D2.toInt(), 0xFFFFC21F.toInt(), 0xFF3FD2F2.toInt())
        const val SLOT = 0xFFEDE8FB.toInt()
        const val SLOT_EDGE = 0xFFD3CBEE.toInt()
        const val PLAQUE = 0xFF22CC94.toInt()
        const val PLAQUE_RIBBON = Theme.ORANGE
    }
}

/** A challenge's reward: a square plaque with a star, on the same ribbon as the medals. */
internal class PlaqueArt(private val owned: Boolean) : Icon() {
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 64f
        canvas.translate(bounds.exactCenterX() - 32f * scale, bounds.exactCenterY() - 32f * scale)
        canvas.scale(scale, scale)
        val body = if (owned) BadgeArt.PLAQUE else BadgeArt.SLOT
        ribbon(canvas, path, paint, if (owned) BadgeArt.PLAQUE_RIBBON else BadgeArt.SLOT, owned)
        paint.style = Paint.Style.FILL
        paint.color = if (owned) Theme.darken(body, .3f) else BadgeArt.SLOT_EDGE
        rect.set(13.5f, if (owned) 25.5f else 20.5f, 50.5f, if (owned) 62f else 57f); canvas.drawRoundRect(rect, 10f, 10f, paint)
        paint.color = body; rect.set(13.5f, 23f, 50.5f, 59.5f); canvas.drawRoundRect(rect, 10f, 10f, paint)
        if (owned) {
            paint.color = Theme.alpha(Theme.WHITE, 140); rect.set(19f, 26f, 45f, 29f); canvas.drawRoundRect(rect, 1.5f, 1.5f, paint)
        }
        for (pass in 0..1) {
            if (pass == 0 && !owned) continue
            paint.color = if (pass == 0) Theme.darken(body, .32f) else if (owned) Theme.WHITE else BadgeArt.SLOT_EDGE
            val dy = if (pass == 0) 1.6f else 0f
            path.reset()
            for (k in 0 until 10) {
                val a = Math.toRadians(k * 36.0 - 90.0)
                val reach = if (k % 2 == 0) 10f else 4.8f
                val px = 32f + reach * cos(a).toFloat(); val py = 42f + dy + reach * sin(a).toFloat()
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close(); canvas.drawPath(path, paint)
        }
        canvas.restoreToCount(save)
    }

    /** Two straps meeting behind the plaque; the far one in shade. */
    private fun ribbon(canvas: Canvas, path: Path, paint: Paint, color: Int, owned: Boolean) {
        paint.style = Paint.Style.FILL
        paint.color = if (owned) Theme.darken(color, .22f) else BadgeArt.SLOT_EDGE
        path.reset(); path.moveTo(37f, 3f); path.lineTo(50f, 3f); path.lineTo(39f, 29f); path.lineTo(27f, 29f); path.close()
        canvas.drawPath(path, paint)
        paint.color = color
        path.reset(); path.moveTo(14f, 3f); path.lineTo(27f, 3f); path.lineTo(37f, 29f); path.lineTo(25f, 29f); path.close()
        canvas.drawPath(path, paint)
        if (owned) {
            paint.color = Theme.alpha(Theme.WHITE, 150)
            path.reset(); path.moveTo(19f, 3f); path.lineTo(22.5f, 3f); path.lineTo(32.5f, 29f); path.lineTo(29f, 29f); path.close()
            canvas.drawPath(path, paint)
        }
    }

    override fun getIntrinsicWidth(): Int = 64
    override fun getIntrinsicHeight(): Int = 64
}

/** The reward a claim of [tier] pays: a medal family's badge, or a challenge's plaque. */
internal fun rewardArt(tiered: Boolean, tier: Int, owned: Boolean): Icon = if (tiered) BadgeArt(tier, owned) else PlaqueArt(owned)

/**
 * An achievement's picture on its own chunky candy tile: a rounded square in the achievement's
 * colour on a darker lip, with a gloss band, the picture filling most of the face.
 */
internal class AchievementTile(id: String, private val color: Int) : Icon() {
    private val art = achievementIcon(id)

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 64f
        canvas.translate(bounds.exactCenterX() - 32f * scale, bounds.exactCenterY() - 32f * scale)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL
        paint.color = Theme.darken(color, .28f); rect.set(1f, 5f, 63f, 63f); canvas.drawRoundRect(rect, 17f, 17f, paint)
        paint.color = color; rect.set(1f, 1f, 63f, 59f); canvas.drawRoundRect(rect, 17f, 17f, paint)
        paint.color = Theme.alpha(Theme.lighten(color, .6f), 120); rect.set(9f, 4.5f, 55f, 10f); canvas.drawRoundRect(rect, 3f, 3f, paint)
        art.setBounds(9, 7, 55, 53)
        art.draw(canvas)
        canvas.restoreToCount(save)
    }

    override fun getIntrinsicWidth(): Int = 64
    override fun getIntrinsicHeight(): Int = 64
}

/**
 * What sits behind a reward waiting to be collected: a flat pale halo of its colour in its own
 * shape (a hexagon behind a badge, a disc behind a plaque), like the spot it is about to be
 * stamped into. Flat and still, so it reads as a spotlight rather than a shadow.
 */
internal class WaitingGlow(private val color: Int, private val hexagon: Boolean = false) : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY()
        val r = minOf(bounds.width(), bounds.height()) / 2f
        paint.style = Paint.Style.FILL
        for ((shade, size) in listOf(.45f to 1f, .72f to .86f)) {
            paint.color = Theme.lighten(color, shade)
            if (!hexagon) { canvas.drawCircle(cx, cy, r * size, paint); continue }
            paint.pathEffect = CornerPathEffect(r * .22f)
            path.reset()
            for (k in 0 until 6) {
                val angle = Math.toRadians(k * 60.0 - 90.0)
                val x = cx + r * size * cos(angle).toFloat(); val y = cy + r * size * sin(angle).toFloat()
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close(); canvas.drawPath(path, paint)
            paint.pathEffect = null
        }
    }
}
