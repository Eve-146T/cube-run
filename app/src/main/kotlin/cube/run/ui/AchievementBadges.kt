package cube.run.ui

import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

/**
 * The badges achievements pay out. Every badge is the same chunky hexagon: a metal frame on a
 * darker lip, a sunk face, a gloss along the top edge and one emblem stamped on it. A medal
 * family's badge carries a star, and its metal says how far you got: bronze, silver, gold, then
 * crystal-blue diamond with a cut gem instead. A challenge's badge is mint with a check mark.
 * Not yet won, the same hexagon is a pale recessed slot. Drawn on a 64-unit grid.
 */
internal class BadgeArt(private val kind: Int, private val owned: Boolean) : Icon() {
    private val path = Path()
    private val rounded = CornerPathEffect(7f)

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 64f
        canvas.translate(bounds.exactCenterX() - 32f * scale, bounds.exactCenterY() - 32f * scale)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL
        val metal = if (owned) colorOf(kind) else SLOT
        paint.pathEffect = rounded
        // Won or empty, the badge sits on the same lip in the same place: only its colours change.
        paint.color = if (owned) Theme.darken(metal, .3f) else SLOT_EDGE
        hexagon(33.5f, 29f); canvas.drawPath(path, paint)
        canvas.translate(0f, -.5f)
        paint.color = metal; hexagon(31f, 29f); canvas.drawPath(path, paint)
        paint.color = if (owned) Theme.darken(metal, if (kind == GOLD) .2f else .14f) else Theme.darken(SLOT, .04f)
        hexagon(31f, 21f); canvas.drawPath(path, paint)
        paint.pathEffect = null
        if (owned) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.6f; paint.strokeCap = Paint.Cap.ROUND
            paint.color = Theme.alpha(Theme.WHITE, 190)
            canvas.drawLine(10.5f, 20f, 26f, 8.5f, paint)
            paint.style = Paint.Style.FILL
        }
        val lip = if (owned) Theme.darken(metal, .4f) else null
        for (pass in 0..1) {
            if (pass == 0 && lip == null) continue
            val dy = if (pass == 0) 1.8f else 0f
            paint.color = if (pass == 0) lip!! else if (owned) Theme.WHITE else SLOT_EDGE
            when (kind) {
                DIAMOND -> gem(canvas, dy, pass == 1 && owned)
                CHALLENGE -> check(canvas, dy)
                else -> star(canvas, dy)
            }
        }
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

    private fun star(canvas: Canvas, dy: Float) {
        path.reset()
        for (k in 0 until 10) {
            val a = Math.toRadians(k * 36.0 - 90.0)
            val reach = if (k % 2 == 0) 12.5f else 5.6f
            val x = 32f + reach * cos(a).toFloat(); val y = 32.5f + dy + reach * sin(a).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close(); canvas.drawPath(path, paint)
    }

    /**
     * A cut gem: a flat table and a faceted crown over a deep point, lit from the top left, with
     * a twinkle. Empty, the same outline in one colour.
     */
    private fun gem(canvas: Canvas, dy: Float, facets: Boolean) {
        fun poly(vararg p: Float) {
            path.reset(); path.moveTo(p[0], p[1] + dy)
            for (i in 2 until p.size step 2) path.lineTo(p[i], p[i + 1] + dy)
            path.close(); canvas.drawPath(path, paint)
        }
        poly(24f, 21f, 40f, 21f, 46f, 28f, 32f, 44f, 18f, 28f)
        if (!facets) return
        val ice = METALS[DIAMOND]
        paint.color = Theme.lighten(ice, .82f); poly(24f, 21f, 40f, 21f, 36f, 28f, 28f, 28f)
        paint.color = Theme.lighten(ice, .5f); poly(18f, 28f, 24f, 21f, 28f, 28f)
        paint.color = Theme.lighten(ice, .62f); poly(40f, 21f, 46f, 28f, 36f, 28f)
        paint.color = Theme.lighten(ice, .35f); poly(18f, 28f, 28f, 28f, 32f, 44f)
        paint.color = Theme.lighten(ice, .7f); poly(28f, 28f, 36f, 28f, 32f, 44f)
        paint.color = Theme.lighten(ice, .2f); poly(36f, 28f, 46f, 28f, 32f, 44f)
        paint.color = Theme.WHITE
        path.reset()
        for (k in 0 until 8) {
            val a = Math.toRadians(k * 45.0 - 90.0); val reach = if (k % 2 == 0) 4.2f else 1.2f
            val x = 24.5f + reach * cos(a).toFloat(); val y = 23.5f + reach * sin(a).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close(); canvas.drawPath(path, paint)
    }

    private fun check(canvas: Canvas, dy: Float) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 6f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        path.reset(); path.moveTo(22f, 32f + dy); path.lineTo(29f, 39f + dy); path.lineTo(42.5f, 24.5f + dy)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    override fun getIntrinsicWidth(): Int = 64
    override fun getIntrinsicHeight(): Int = 64

    companion object {
        const val GOLD = 2
        const val DIAMOND = 3
        /** A challenge's badge rather than a medal family tier. */
        const val CHALLENGE = 4
        /** Bronze, silver, gold, diamond. */
        val METALS = intArrayOf(0xFFD9864F.toInt(), 0xFFA7B5D2.toInt(), 0xFFFFC21F.toInt(), 0xFF3FD2F2.toInt())
        const val CHALLENGE_COLOR = 0xFF22CC94.toInt()
        const val SLOT = 0xFFEDE8FB.toInt()
        const val SLOT_EDGE = 0xFFD3CBEE.toInt()

        fun colorOf(kind: Int): Int = if (kind == CHALLENGE) CHALLENGE_COLOR else METALS[kind.coerceIn(0, 3)]
    }
}

/** The badge a claim of [tier] pays: a medal family's tier, or a challenge's check. */
internal fun rewardArt(tiered: Boolean, tier: Int, owned: Boolean): Icon = BadgeArt(if (tiered) tier.coerceIn(0, 3) else BadgeArt.CHALLENGE, owned)

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
 * What sits behind a badge waiting to be collected: a flat pale halo of its colour in its
 * hexagon shape, like the spot it is about to be stamped into. Flat and still, so it reads as a
 * spotlight rather than a shadow.
 */
internal class WaitingGlow(private val color: Int) : Icon() {
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY()
        val r = minOf(bounds.width(), bounds.height()) / 2f
        paint.style = Paint.Style.FILL
        paint.pathEffect = CornerPathEffect(r * .22f)
        for ((shade, size) in listOf(.45f to 1f, .72f to .86f)) {
            paint.color = Theme.lighten(color, shade)
            path.reset()
            for (k in 0 until 6) {
                val angle = Math.toRadians(k * 60.0 - 90.0)
                val x = cx + r * size * cos(angle).toFloat(); val y = cy + r * size * sin(angle).toFloat()
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close(); canvas.drawPath(path, paint)
        }
        paint.pathEffect = null
    }
}
