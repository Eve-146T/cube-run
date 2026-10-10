package cube.run.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

/**
 * The rewards you collect. Every medal is the same object: a ribbon over a chunky metal disc on a
 * darker lip, with an engraved ring and a gloss. The metal and the emblem say which it is: bronze
 * with one star, silver with two, gold with three, and diamond, a cut gem on crystal blue. A
 * challenge pays a square plaque instead. Not yet won, the same silhouette is a recessed empty
 * slot, so a row of medals always keeps its shape. Drawn on a 64-unit grid.
 */
internal class MedalArt(private val tier: Int, private val owned: Boolean) : Icon() {
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 64f
        canvas.translate(bounds.exactCenterX() - 32f * scale, bounds.exactCenterY() - 32f * scale)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL
        val t = tier.coerceIn(0, 3)
        val metal = if (owned) METALS[t] else SLOT
        ribbon(canvas, path, paint, if (owned) RIBBONS[t] else SLOT, owned)
        // An empty slot is sunk in: its darker edge is on top instead of underneath.
        paint.color = if (owned) Theme.darken(metal, .3f) else SLOT_EDGE
        canvas.drawCircle(32f, if (owned) 44.5f else 39.5f, 20.5f, paint)
        paint.color = metal; canvas.drawCircle(32f, 42f, 20.5f, paint)
        if (owned) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.8f
            paint.color = Theme.alpha(Theme.darken(metal, .18f), 200); canvas.drawCircle(32f, 42f, 16.5f, paint)
            paint.strokeWidth = 2.6f; paint.strokeCap = Paint.Cap.ROUND; paint.color = Theme.alpha(Theme.WHITE, 200)
            rect.set(14f, 24f, 50f, 60f); canvas.drawArc(rect, 200f, 55f, false, paint)
            paint.style = Paint.Style.FILL
        }
        emblem(canvas, t, if (owned) Theme.WHITE else SLOT_EDGE, if (owned) Theme.darken(metal, if (t == 1) .45f else .32f) else null)
        canvas.restoreToCount(save)
    }

    private fun emblem(canvas: Canvas, tier: Int, color: Int, lip: Int?) {
        fun star(x: Float, y: Float, r: Float) {
            for (pass in 0..1) {
                if (pass == 0 && lip == null) continue
                paint.color = if (pass == 0) lip!! else color
                val dy = if (pass == 0) 1.6f else 0f
                path.reset()
                for (k in 0 until 10) {
                    val a = Math.toRadians(k * 36.0 - 90.0)
                    val reach = if (k % 2 == 0) r else r * .48f
                    val px = x + reach * cos(a).toFloat(); val py = y + dy + reach * sin(a).toFloat()
                    if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close(); canvas.drawPath(path, paint)
            }
        }
        when (tier) {
            0 -> star(32f, 42.5f, 11f)
            1 -> { star(24.6f, 42.5f, 8f); star(39.4f, 42.5f, 8f) }
            2 -> { star(32f, 36.2f, 7.2f); star(24.4f, 47f, 6.8f); star(39.6f, 47f, 6.8f) }
            else -> {
                // A cut gem: a flat table, a crown and a point.
                for (pass in 0..1) {
                    if (pass == 0 && lip == null) continue
                    val dy = if (pass == 0) 1.6f else 0f
                    paint.color = if (pass == 0) lip!! else color
                    path.reset(); path.moveTo(25f, 33f + dy); path.lineTo(39f, 33f + dy); path.lineTo(45f, 39.5f + dy)
                    path.lineTo(32f, 54f + dy); path.lineTo(19f, 39.5f + dy); path.close(); canvas.drawPath(path, paint)
                }
                if (lip != null) {
                    paint.color = Theme.lighten(METALS[3], .55f)
                    path.reset(); path.moveTo(19f, 39.5f); path.lineTo(45f, 39.5f); path.lineTo(32f, 54f); path.close(); canvas.drawPath(path, paint)
                    paint.color = Theme.lighten(METALS[3], .25f)
                    path.reset(); path.moveTo(32f, 39.5f); path.lineTo(45f, 39.5f); path.lineTo(32f, 54f); path.close(); canvas.drawPath(path, paint)
                }
            }
        }
    }

    override fun getIntrinsicWidth(): Int = 64
    override fun getIntrinsicHeight(): Int = 64

    companion object {
        /** Bronze, silver, gold, diamond. */
        val METALS = intArrayOf(0xFFD9864F.toInt(), 0xFFA7B5D2.toInt(), 0xFFFFC21F.toInt(), 0xFF3FD2F2.toInt())
        val RIBBONS = intArrayOf(Theme.GRAPE, Theme.SKY, Theme.BERRY, Theme.PINK)
        const val SLOT = 0xFFEDE8FB.toInt()
        const val SLOT_EDGE = 0xFFD3CBEE.toInt()
        const val PLAQUE = 0xFF22CC94.toInt()
        const val PLAQUE_RIBBON = Theme.ORANGE

        /** Two straps meeting behind the disc; the far one in shade. */
        fun ribbon(canvas: Canvas, path: Path, paint: Paint, color: Int, owned: Boolean) {
            paint.style = Paint.Style.FILL
            paint.color = if (owned) Theme.darken(color, .22f) else SLOT_EDGE
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
        val body = if (owned) MedalArt.PLAQUE else MedalArt.SLOT
        MedalArt.ribbon(canvas, path, paint, if (owned) MedalArt.PLAQUE_RIBBON else MedalArt.SLOT, owned)
        paint.style = Paint.Style.FILL
        paint.color = if (owned) Theme.darken(body, .3f) else MedalArt.SLOT_EDGE
        rect.set(13.5f, if (owned) 25.5f else 20.5f, 50.5f, if (owned) 62f else 57f); canvas.drawRoundRect(rect, 10f, 10f, paint)
        paint.color = body; rect.set(13.5f, 23f, 50.5f, 59.5f); canvas.drawRoundRect(rect, 10f, 10f, paint)
        if (owned) {
            paint.color = Theme.alpha(Theme.WHITE, 140); rect.set(19f, 26f, 45f, 29f); canvas.drawRoundRect(rect, 1.5f, 1.5f, paint)
        }
        for (pass in 0..1) {
            if (pass == 0 && !owned) continue
            paint.color = if (pass == 0) Theme.darken(body, .32f) else if (owned) Theme.WHITE else MedalArt.SLOT_EDGE
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

    override fun getIntrinsicWidth(): Int = 64
    override fun getIntrinsicHeight(): Int = 64
}

/** The reward [definition] pays for [tier]: its medal, or a challenge's plaque. */
internal fun rewardArt(tiered: Boolean, tier: Int, owned: Boolean): Icon = if (tiered) MedalArt(tier, owned) else PlaqueArt(owned)

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
 * The light behind a reward waiting to be collected: soft rays of its colour around a clear
 * middle. Flat and still, so it reads as a spotlight rather than a shadow.
 */
internal class RewardRays(private val color: Int) : Icon() {
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY() + bounds.height() * .12f
        val r = bounds.width() * .56f
        paint.style = Paint.Style.FILL
        paint.color = Theme.alpha(Theme.lighten(color, .4f), 90)
        path.reset()
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90.0); val b = Math.toRadians(i * 30.0 - 90.0 + 15.0)
            path.moveTo(cx, cy)
            path.lineTo(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
            path.lineTo(cx + r * cos(b).toFloat(), cy + r * sin(b).toFloat())
            path.close()
        }
        canvas.drawPath(path, paint)
        paint.color = Theme.alpha(Theme.lighten(color, .55f), 170)
        canvas.drawCircle(cx, cy, r * .5f, paint)
    }
}

/** One tier of a medal family, as a dot: [WON] in its metal, [WAITING] ringed in it, [EMPTY] a hollow slot. */
internal class TierDot(private val metal: Int, private val state: Int) : Icon() {
    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY(); val r = minOf(bounds.width(), bounds.height()) / 2f
        paint.style = Paint.Style.FILL
        when (state) {
            WON -> { paint.color = Theme.darken(metal, .3f); canvas.drawCircle(cx, cy, r, paint); paint.color = metal; canvas.drawCircle(cx, cy - r * .12f, r * .82f, paint) }
            WAITING -> {
                paint.color = metal; canvas.drawCircle(cx, cy, r, paint)
                paint.color = Theme.WHITE; canvas.drawCircle(cx, cy, r * .42f, paint)
            }
            else -> {
                paint.color = MedalArt.SLOT_EDGE; canvas.drawCircle(cx, cy, r, paint)
                paint.color = MedalArt.SLOT; canvas.drawCircle(cx, cy + r * .14f, r * .7f, paint)
            }
        }
    }

    companion object {
        const val WON = 0
        const val WAITING = 1
        const val EMPTY = 2
    }
}
