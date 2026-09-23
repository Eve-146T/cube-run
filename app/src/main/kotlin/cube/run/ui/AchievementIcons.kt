package cube.run.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import java.text.NumberFormat

/** The achievement page's drawn artwork: one icon per family, the medals, and the shared number formats. */
internal fun achievementIcon(id: String): android.graphics.drawable.Drawable = when (id) {
    "coins" -> CoinIcon()
    "powerups" -> JetIcon(Theme.SKY)
    "boxes" -> BoxIcon()
    "bubbles" -> BubbleIcon()
    "cubes" -> AchievementCubeIcon(collection = true)
    "bounces" -> AchievementWallIcon()
    "center" -> AchievementCenterIcon()
    "homeress" -> AchievementCoinlessIcon()
    "gambliphobic" -> AchievementBoxAvoidanceIcon()
    "cookie" -> AchievementCookieIcon()
    "globetrotter", "long_hauler", "shardsmith", "regular", "bubble_popper", "near_miss",
    "untouchable", "house_loses", "voidwalker", "greedy", "scenic_route", "coal_miner",
    "magpie", "shard_hunter", "full_kit", "long_con", "insomniac", "bankrupt",
    "exactly_67", "just_browsing", "two_ez", "nervous_tic", "silent_treatment", "stage_fright" -> AchievementMotifIcon(id)
    else -> AchievementCubeIcon(collection = false)
}

/** Raised candy badges with a distinct pictogram for each new goal. */
private class AchievementMotifIcon(private val id: String) : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 48f
        canvas.translate(bounds.exactCenterX() - 24f * scale, bounds.exactCenterY() - 24f * scale)
        canvas.scale(scale, scale)
        val accent = AchievementCards.accent(id)
        // The same stepped face, dark rim and small gloss used by the shop's candy icons.
        paint.style = Paint.Style.FILL
        paint.color = Theme.darken(accent, .42f)
        canvas.drawCircle(24f, 25f, 23f, paint)
        paint.color = accent
        canvas.drawCircle(24f, 22f, 21f, paint)
        paint.color = Theme.lighten(accent, .34f)
        canvas.drawCircle(24f, 21f, 17.5f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Theme.alpha(Theme.WHITE, 230)
        rect.set(7f, 4f, 41f, 38f)
        canvas.drawArc(rect, 200f, 72f, false, paint)
        paint.color = Theme.INK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.3f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        fun line(x: Float, y: Float, xx: Float, yy: Float) = canvas.drawLine(x, y, xx, yy, paint)
        fun ring(x: Float, y: Float, r: Float) = canvas.drawCircle(x, y, r, paint)
        fun oval(l: Float, t: Float, r: Float, b: Float) { rect.set(l, t, r, b); canvas.drawOval(rect, paint) }
        fun box(l: Float, t: Float, r: Float, b: Float, radius: Float = 3f) { rect.set(l, t, r, b); canvas.drawRoundRect(rect, radius, radius, paint) }
        fun shape(vararg p: Float) {
            path.reset(); path.moveTo(p[0], p[1]); for (i in 2 until p.size step 2) path.lineTo(p[i], p[i + 1]); path.close()
            canvas.drawPath(path, paint)
        }
        fun dot(x: Float, y: Float, r: Float = 2f) {
            val old = paint.style; paint.style = Paint.Style.FILL; canvas.drawCircle(x, y, r, paint); paint.style = old
        }
        when (id) {
            "globetrotter" -> { ring(24f, 24f, 18f); oval(15f, 6f, 33f, 42f); line(6f, 24f, 42f, 24f); line(10f, 14f, 38f, 14f); line(10f, 34f, 38f, 34f) }
            "long_hauler" -> { shape(17f, 4f, 31f, 4f, 43f, 44f, 5f, 44f); line(24f, 9f, 24f, 15f); line(24f, 23f, 24f, 29f); line(24f, 37f, 24f, 43f) }
            "shardsmith" -> { shape(24f, 3f, 38f, 18f, 24f, 43f, 10f, 18f); line(10f, 18f, 38f, 18f); line(24f, 3f, 24f, 43f); line(10f, 18f, 24f, 30f); line(38f, 18f, 24f, 30f) }
            "regular" -> { box(7f, 9f, 41f, 42f); line(7f, 18f, 41f, 18f); line(15f, 5f, 15f, 13f); line(33f, 5f, 33f, 13f); dot(15f, 26f); dot(24f, 26f); dot(33f, 26f); dot(15f, 35f); dot(24f, 35f) }
            "bubble_popper" -> { ring(20f, 23f, 13f); line(33f, 12f, 41f, 5f); line(36f, 22f, 45f, 22f); line(32f, 34f, 40f, 42f); dot(15f, 17f, 2.5f) }
            "near_miss" -> { box(33f, 7f, 42f, 41f); shape(25f, 7f, 9f, 24f, 25f, 41f); line(9f, 24f, 29f, 24f); line(24f, 18f, 30f, 24f); line(24f, 30f, 30f, 24f) }
            "untouchable" -> { shape(24f, 4f, 40f, 11f, 37f, 30f, 24f, 43f, 11f, 30f, 8f, 11f); line(16f, 24f, 22f, 30f); line(22f, 30f, 33f, 18f) }
            "house_loses" -> { box(7f, 7f, 41f, 41f, 7f); dot(15f, 15f, 2.5f); dot(33f, 15f, 2.5f); dot(24f, 24f, 2.5f); dot(15f, 33f, 2.5f); dot(33f, 33f, 2.5f) }
            "voidwalker" -> { ring(24f, 24f, 17f); ring(24f, 24f, 10f); dot(24f, 24f, 6f); line(7f, 9f, 4f, 4f); line(41f, 39f, 45f, 44f) }
            "greedy" -> { box(5f, 21f, 29f, 42f); box(19f, 6f, 43f, 27f); line(24f, 6f, 24f, 27f); line(10f, 21f, 10f, 42f) }
            "scenic_route" -> { ring(14f, 14f, 8f); ring(34f, 14f, 8f); ring(14f, 34f, 8f); ring(34f, 34f, 8f); dot(14f, 14f); dot(34f, 14f); dot(14f, 34f); dot(34f, 34f) }
            "coal_miner" -> { shape(13f, 32f, 22f, 23f, 33f, 27f, 38f, 38f, 22f, 42f); line(10f, 8f, 37f, 17f); line(25f, 13f, 15f, 34f); line(9f, 9f, 14f, 5f) }
            "magpie" -> { oval(5f, 29f, 25f, 40f); oval(14f, 20f, 34f, 31f); oval(23f, 11f, 43f, 22f); line(31f, 14f, 36f, 19f) }
            "shard_hunter" -> { shape(9f, 9f, 19f, 9f, 23f, 17f, 14f, 27f, 5f, 17f); shape(29f, 7f, 40f, 7f, 45f, 16f, 35f, 27f, 25f, 16f); shape(20f, 25f, 31f, 25f, 36f, 34f, 26f, 45f, 16f, 34f) }
            "full_kit" -> { box(4f, 4f, 22f, 22f); box(26f, 4f, 44f, 22f); box(4f, 26f, 22f, 44f); box(26f, 26f, 44f, 44f); ring(13f, 13f, 4f); line(31f, 9f, 39f, 17f); line(31f, 17f, 39f, 9f); line(10f, 35f, 17f, 35f); shape(34f, 30f, 39f, 39f, 29f, 39f) }
            "long_con" -> { ring(24f, 26f, 17f); line(24f, 26f, 24f, 14f); line(24f, 26f, 33f, 31f); line(18f, 4f, 30f, 4f) }
            "insomniac" -> { paint.style = Paint.Style.FILL; canvas.drawCircle(22f, 24f, 14f, paint); paint.color = Theme.lighten(accent, .34f); canvas.drawCircle(29f, 18f, 12f, paint); paint.color = Theme.INK; dot(10f, 9f); dot(39f, 37f) }
            "bankrupt" -> { box(5f, 14f, 43f, 39f); box(28f, 20f, 43f, 31f); line(10f, 9f, 35f, 9f); ring(35f, 25f, 1f) }
            "exactly_67" -> { paint.style = Paint.Style.FILL; paint.typeface = android.graphics.Typeface.DEFAULT_BOLD; paint.textSize = 27f; canvas.drawText("67", 8f, 33f, paint) }
            "just_browsing" -> { box(8f, 16f, 40f, 42f); path.reset(); path.moveTo(16f, 18f); path.cubicTo(16f, 1f, 32f, 1f, 32f, 18f); canvas.drawPath(path, paint); oval(15f, 24f, 33f, 35f); dot(24f, 29f, 3f) }
            "two_ez" -> { line(5f, 16f, 41f, 16f); line(33f, 8f, 41f, 16f); line(33f, 24f, 41f, 16f); line(43f, 32f, 7f, 32f); line(15f, 24f, 7f, 32f); line(15f, 40f, 7f, 32f) }
            "nervous_tic" -> { ring(24f, 24f, 19f); line(19f, 15f, 19f, 33f); line(29f, 15f, 29f, 33f); line(5f, 5f, 9f, 9f) }
            "silent_treatment" -> { shape(6f, 19f, 14f, 19f, 24f, 10f, 24f, 38f, 14f, 29f, 6f, 29f); line(33f, 16f, 43f, 32f); line(43f, 16f, 33f, 32f) }
            "stage_fright" -> { box(7f, 5f, 41f, 13f); line(12f, 13f, 12f, 43f); line(36f, 13f, 36f, 43f); line(18f, 27f, 30f, 39f); line(30f, 27f, 18f, 39f) }
        }
        canvas.restoreToCount(save)
    }
}

internal class AchievementCheckIcon : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 32f, bounds.height() / 32f)
        paint.style = Paint.Style.FILL; paint.color = Theme.MINT
        canvas.drawCircle(16f, 16f, 15f, paint)
        paint.style = Paint.Style.STROKE; paint.color = Theme.INK
        paint.strokeWidth = 3.4f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        path.reset(); path.moveTo(9f, 16f); path.lineTo(14f, 21f); path.lineTo(23f, 11f)
        canvas.drawPath(path, paint)
        canvas.restoreToCount(save)
    }
}

/** Isometric cubes reuse the game's bevelled candy language, with speed lines for running. */
private class AchievementCubeIcon(private val collection: Boolean) : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 48f, bounds.height() / 48f)
        fun cube(x: Float, y: Float, size: Float, color: Int) {
            paint.style = Paint.Style.FILL
            fun face(c: Int, vararg points: Float) {
                path.reset(); path.moveTo(points[0], points[1])
                for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
                path.close(); paint.color = c; canvas.drawPath(path, paint)
            }
            val half = size / 2f; val rise = size / 4f
            face(Theme.lighten(color, .4f), x, y + rise, x + half, y, x + size, y + rise, x + half, y + 2 * rise)
            face(color, x, y + rise, x + half, y + 2 * rise, x + half, y + size, x, y + size - rise)
            face(Theme.darken(color, .2f), x + half, y + 2 * rise, x + size, y + rise, x + size, y + size - rise, x + half, y + size)
        }
        if (collection) {
            cube(1f, 21f, 24f, Theme.LAVENDER)
            cube(23f, 21f, 24f, Theme.GRAPE)
            cube(12f, 3f, 24f, Theme.SKY)
        } else {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.5f; paint.strokeCap = Paint.Cap.ROUND
            paint.color = Theme.WHITE
            canvas.drawLine(3f, 20f, 12f, 20f, paint); canvas.drawLine(6f, 29f, 10f, 29f, paint); canvas.drawLine(3f, 38f, 9f, 38f, paint)
            cube(13f, 7f, 34f, Theme.ORANGE)
        }
        canvas.restoreToCount(save)
    }
}

/** Three straight lanes with the cube held in the centre; every edge shares a common centre. */
private class AchievementCenterIcon : Icon() {
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 48f
        canvas.translate(bounds.exactCenterX() - 24f * scale, bounds.exactCenterY() - 24f * scale)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL
        paint.color = Theme.darken(Theme.MINT, .25f)
        rect.set(17f, 3f, 31f, 45f); canvas.drawRoundRect(rect, 4f, 4f, paint)
        paint.color = Theme.WHITE
        rect.set(4f, 3f, 7f, 45f); canvas.drawRoundRect(rect, 1.5f, 1.5f, paint)
        rect.set(41f, 3f, 44f, 45f); canvas.drawRoundRect(rect, 1.5f, 1.5f, paint)
        rect.set(14f, 14f, 34f, 34f); canvas.drawRoundRect(rect, 4f, 4f, paint)
        paint.color = Theme.MINT
        rect.set(18f, 18f, 30f, 30f); canvas.drawRoundRect(rect, 2f, 2f, paint)
        canvas.restoreToCount(save)
    }
}

/** A crossed coin: the coin silhouette and diagonal share the same visual centre. */
private class AchievementCoinlessIcon : Icon() {
    private val coin = CoinIcon()
    override fun draw(canvas: Canvas) {
        val saved = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 48f
        canvas.translate(bounds.exactCenterX() - 24f * scale, bounds.exactCenterY() - 24f * scale)
        canvas.scale(scale, scale)
        coin.setBounds(4, 4, 44, 44); coin.draw(canvas)
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 7f; paint.color = Theme.INK
        canvas.drawLine(8f, 40f, 40f, 8f, paint)
        paint.strokeWidth = 3.5f; paint.color = Theme.WHITE
        canvas.drawLine(8f, 40f, 40f, 8f, paint)
        canvas.restoreToCount(saved)
    }
}

/** A gift left untouched while the route circles around it. */
private class AchievementBoxAvoidanceIcon : Icon() {
    private val box = BoxIcon()
    private val arrow = Path()
    override fun draw(canvas: Canvas) {
        val saved = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 48f
        canvas.translate(bounds.exactCenterX() - 24f * scale, bounds.exactCenterY() - 24f * scale)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
        paint.color = Theme.WHITE; paint.strokeCap = Paint.Cap.ROUND
        canvas.drawCircle(24f, 24f, 20f, paint)
        box.setBounds(13, 13, 35, 35); box.draw(canvas)
        for (side in 0..1) {
            if (side == 1) canvas.rotate(180f, 24f, 24f)
            paint.style = Paint.Style.FILL; paint.color = Theme.INK
            arrow.reset(); arrow.moveTo(29f, 2f); arrow.lineTo(38f, 6f); arrow.lineTo(30f, 12f); arrow.close()
            canvas.drawPath(arrow, paint)
            paint.color = Theme.WHITE
            arrow.reset(); arrow.moveTo(31f, 4f); arrow.lineTo(35f, 6f); arrow.lineTo(31f, 9f); arrow.close()
            canvas.drawPath(arrow, paint)
        }
        canvas.restoreToCount(saved)
    }
}

/** A chunky chocolate-chip cookie and a small pointer, with no font glyph dependencies. */
private class AchievementCookieIcon : Icon() {
    private val pointer = Path()
    override fun draw(canvas: Canvas) {
        val saved = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 48f
        canvas.translate(bounds.exactCenterX() - 24f * scale, bounds.exactCenterY() - 24f * scale)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL; paint.color = 0xFF9D552D.toInt()
        canvas.drawCircle(24f, 24f, 21f, paint)
        paint.color = 0xFFF2BE75.toInt(); canvas.drawCircle(24f, 24f, 18f, paint)
        paint.color = 0xFF74422C.toInt()
        for ((x, y) in arrayOf(13f to 15f, 28f to 11f, 35f to 21f, 12f to 29f, 21f to 36f))
            canvas.drawCircle(x, y, 2.7f, paint)
        pointer.reset(); pointer.moveTo(21f, 19f); pointer.lineTo(37f, 29f)
        pointer.lineTo(30f, 31f); pointer.lineTo(27f, 39f); pointer.close()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.strokeJoin = Paint.Join.ROUND
        paint.color = Theme.INK; canvas.drawPath(pointer, paint)
        paint.style = Paint.Style.FILL; paint.color = Theme.WHITE; canvas.drawPath(pointer, paint)
        canvas.restoreToCount(saved)
    }
}

private class AchievementWallIcon : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 48f, bounds.height() / 48f)
        paint.style = Paint.Style.FILL; paint.color = Theme.PINK
        rect.set(36f, 3f, 44f, 45f); canvas.drawRoundRect(rect, 3f, 3f, paint)
        paint.style = Paint.Style.STROKE; paint.color = Theme.WHITE
        paint.strokeWidth = 4f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        path.reset(); path.moveTo(6f, 7f); path.lineTo(31f, 24f); path.lineTo(6f, 41f)
        canvas.drawPath(path, paint)
        path.reset(); path.moveTo(6f, 30f); path.lineTo(6f, 41f); path.lineTo(17f, 42f)
        canvas.drawPath(path, paint)
        canvas.restoreToCount(save)
    }
}

internal fun medalColor(tier: Int): Int = when (tier) {
    0 -> 0xFFCC8B60.toInt()
    1 -> 0xFFADB9D0.toInt()
    2 -> Theme.GOLD
    else -> Theme.CYAN
}
internal fun medalName(tier: Int): String = listOf("Bronze", "Silver", "Gold", "Diamond")[tier.coerceIn(0, 3)]
internal fun number(value: Int): String = NumberFormat.getIntegerInstance(java.util.Locale.US).format(value)

/** A tiny embossed medal, with a diamond silhouette for the final tier. */
class MedalIcon(private val color: Int, private val earned: Boolean, private val diamond: Boolean = false,
    private val dark: Boolean = false, private val ribbon: Boolean = true) : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 32f
        canvas.translate(bounds.exactCenterX() - 16f * scale, bounds.exactCenterY() - 16f * scale)
        canvas.scale(scale, scale)
        val faceY = if (!ribbon) 16f else if (diamond) 14f else 13f
        paint.style = Paint.Style.FILL
        if (ribbon) {
            paint.color = if (earned) Theme.darken(color, .22f) else Theme.alpha(Theme.INK, 30)
            path.reset(); path.moveTo(7f, 17f); path.lineTo(6f, 31f); path.lineTo(12f, 27f); path.lineTo(16f, 30f); path.lineTo(19f, 18f); path.close()
            canvas.drawPath(path, paint)
            path.reset(); path.moveTo(17f, 18f); path.lineTo(19f, 30f); path.lineTo(23f, 27f); path.lineTo(26f, 31f); path.lineTo(25f, 17f); path.close()
            canvas.drawPath(path, paint)
        }
        paint.color = if (earned) color else if (dark) Theme.alpha(Theme.LAVENDER, 13) else Theme.CARD_ALT
        if (diamond) {
            path.reset(); path.moveTo(16f, faceY - 13f); path.lineTo(29f, faceY); path.lineTo(16f, faceY + 13f); path.lineTo(3f, faceY); path.close()
            canvas.drawPath(path, paint)
        } else canvas.drawCircle(16f, faceY, 12f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = if (earned) 2f else 1.8f
        paint.color = if (earned) Theme.lighten(color, .55f) else if (dark) Theme.alpha(Theme.WHITE, 130) else Theme.alpha(Theme.INK, 100)
        if (diamond) canvas.drawPath(path, paint) else canvas.drawCircle(16f, faceY, if (earned) 9f else 11f, paint)
        paint.style = Paint.Style.FILL
        if (earned) {
            paint.color = Theme.WHITE
            path.reset(); path.moveTo(16f, faceY - 6f); path.lineTo(18f, faceY - 2f); path.lineTo(22f, faceY); path.lineTo(18f, faceY + 2f); path.lineTo(16f, faceY + 6f); path.lineTo(14f, faceY + 2f); path.lineTo(10f, faceY); path.lineTo(14f, faceY - 2f); path.close()
            canvas.drawPath(path, paint)
        }
        canvas.restoreToCount(save)
    }
}

/** A threshold under its medal: 500, 1K, 25K, 500K. */
internal fun compact(value: Int): String = when {
    value >= 1_000_000 && value % 1_000_000 == 0 -> "${value / 1_000_000}M"
    value >= 1000 && value % 1000 == 0 -> "${value / 1000}K"
    else -> number(value)
}
