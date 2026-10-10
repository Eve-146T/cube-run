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
 * What sits behind a reward waiting to be collected: a flat disc of its colour, paled, with a
 * white ring, like the spot it is lifted from. Flat and still, so it reads as a spotlight
 * rather than a shadow.
 */
internal class WaitingGlow(private val color: Int) : Icon() {
    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY()
        val r = minOf(bounds.width(), bounds.height()) / 2f
        paint.style = Paint.Style.FILL
        paint.color = Theme.lighten(color, .45f); canvas.drawCircle(cx, cy, r, paint)
        paint.color = Theme.lighten(color, .72f); canvas.drawCircle(cx, cy, r * .86f, paint)
    }
}

/**
 * A medal family's own medal: its picture stamped on a face of its colour inside a chunky metal
 * rim, hung on a ribbon of its colour, with a banner across the bottom that carries the tier:
 * one, two or three stars for bronze, silver and gold, a gem for diamond. [tier] is the last one
 * collected; before the first it is a pale blank, its face a hint of its colour with the picture
 * sunk in as a silhouette.
 * [glow], the next metal, sits behind it as a pale disc while a medal waits to be collected.
 * Drawn on a 100-unit grid.
 */
internal class AchievementMedal(id: String, private val color: Int, private val tier: Int, private val glow: Int? = null) : Icon() {
    private val art = achievementIcon(id)
    private val path = Path()
    /** A blank keeps a hint of its family's colour: a pale face with the picture sunk in a shade deeper. */
    private val blankFace = Theme.lighten(color, .84f)
    private val sunk = Paint().also { it.colorFilter = android.graphics.PorterDuffColorFilter(Theme.lighten(color, .62f), android.graphics.PorterDuff.Mode.SRC_IN) }

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 100f
        canvas.translate(bounds.exactCenterX() - 50f * scale, bounds.exactCenterY() - 50f * scale)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL
        val won = tier >= 0
        val t = tier.coerceIn(0, 3)
        // Diamond is ice: a pale rim around a crystal-blue groove, so it never melts into a sky-blue face.
        val metal = if (!won) MedalArt.SLOT else if (t == 3) ICE else MedalArt.METALS[t]
        val groove = if (t == 3) MedalArt.METALS[3] else Theme.darken(metal, .22f)
        glow?.let {
            paint.color = Theme.lighten(it, .5f); canvas.drawCircle(50f, 55f, 45f, paint)
            paint.color = Theme.lighten(it, .74f); canvas.drawCircle(50f, 55f, 40.5f, paint)
        }
        // The ribbon: two straps from the top meeting behind the disc, the far one in shade.
        paint.color = if (won) Theme.darken(color, .34f) else MedalArt.SLOT_EDGE
        path.reset(); path.moveTo(57f, 2f); path.lineTo(75f, 2f); path.lineTo(61f, 32f); path.lineTo(43f, 32f); path.close(); canvas.drawPath(path, paint)
        paint.color = if (won) Theme.darken(color, .1f) else MedalArt.SLOT
        path.reset(); path.moveTo(25f, 2f); path.lineTo(43f, 2f); path.lineTo(57f, 32f); path.lineTo(39f, 32f); path.close(); canvas.drawPath(path, paint)
        if (won) {
            paint.color = Theme.alpha(Theme.WHITE, 120)
            path.reset(); path.moveTo(31f, 2f); path.lineTo(35.5f, 2f); path.lineTo(49.5f, 32f); path.lineTo(45f, 32f); path.close(); canvas.drawPath(path, paint)
        }
        // The disc on its lip; a blank one is sunk in, its edge on top.
        paint.color = if (!won) MedalArt.SLOT_EDGE else if (t == 3) Theme.darken(MedalArt.METALS[3], .25f) else Theme.darken(metal, .32f)
        canvas.drawCircle(50f, if (won) 60.5f else 53.5f, 36f, paint)
        paint.color = metal; canvas.drawCircle(50f, 57f, 36f, paint)
        paint.color = if (won) groove else MedalArt.SLOT_EDGE; canvas.drawCircle(50f, 57f, 29f, paint)
        paint.color = if (won) color else blankFace; canvas.drawCircle(50f, 58f, 27.5f, paint)
        if (won) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.4f; paint.strokeCap = Paint.Cap.ROUND
            paint.color = Theme.alpha(Theme.WHITE, 190)
            rect.set(17.5f, 24.5f, 82.5f, 89.5f); canvas.drawArc(rect, 195f, 60f, false, paint)
            paint.style = Paint.Style.FILL
            if (t == 3) for (a in intArrayOf(-30, 150, 200)) glint(canvas, 50f + 32.5f * cos(Math.toRadians(a.toDouble())).toFloat(), 57f + 32.5f * sin(Math.toRadians(a.toDouble())).toFloat())
        }
        // The picture, stamped on the face.
        art.setBounds(32, 40, 68, 76)
        if (won) art.draw(canvas)
        else { canvas.saveLayer(30f, 38f, 70f, 78f, sunk); art.draw(canvas); canvas.restore() }
        if (won) banner(canvas, t, metal)
        canvas.restoreToCount(save)
    }

    /** A four-pointed sparkle cut into the ice rim. */
    private fun glint(canvas: Canvas, x: Float, y: Float) {
        paint.color = Theme.WHITE
        path.reset(); path.moveTo(x, y - 4.2f); path.lineTo(x + 1.3f, y - 1.3f); path.lineTo(x + 4.2f, y); path.lineTo(x + 1.3f, y + 1.3f)
        path.lineTo(x, y + 4.2f); path.lineTo(x - 1.3f, y + 1.3f); path.lineTo(x - 4.2f, y); path.lineTo(x - 1.3f, y - 1.3f); path.close()
        canvas.drawPath(path, paint)
    }

    /** A plaque across the bottom of the rim carrying the tier's stars, or the diamond's gem. */
    private fun banner(canvas: Canvas, tier: Int, metal: Int) {
        val body = if (tier == 3) Theme.darken(MedalArt.METALS[3], .2f) else Theme.darken(metal, if (tier == 1) .38f else .26f)
        val width = if (tier == 2) 50f else 40f
        paint.color = Theme.darken(body, .3f); rect.set(50f - width / 2f, 83f, 50f + width / 2f, 99f); canvas.drawRoundRect(rect, 7f, 7f, paint)
        paint.color = body; rect.set(50f - width / 2f, 80f, 50f + width / 2f, 96f); canvas.drawRoundRect(rect, 7f, 7f, paint)
        paint.color = Theme.WHITE
        fun star(x: Float) {
            path.reset()
            for (k in 0 until 10) {
                val a = Math.toRadians(k * 36.0 - 90.0)
                val reach = if (k % 2 == 0) 6f else 2.9f
                val px = x + reach * cos(a).toFloat(); val py = 88.5f + reach * sin(a).toFloat()
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close(); canvas.drawPath(path, paint)
        }
        when (tier) {
            0 -> star(50f)
            1 -> { star(43.5f); star(56.5f) }
            2 -> { star(37f); star(50f); star(63f) }
            else -> {
                path.reset(); path.moveTo(45f, 83.5f); path.lineTo(55f, 83.5f); path.lineTo(59.5f, 87.5f)
                path.lineTo(50f, 94f); path.lineTo(40.5f, 87.5f); path.close(); canvas.drawPath(path, paint)
            }
        }
    }

    override fun getIntrinsicWidth(): Int = 100
    override fun getIntrinsicHeight(): Int = 100

    private companion object {
        const val ICE = 0xFFC4F3FF.toInt()
    }
}
