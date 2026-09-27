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
    "exactly_67", "just_browsing", "two_ez", "nervous_tic", "silent_treatment", "stage_fright", "neo" -> ChallengeIcon(id)
    else -> AchievementCubeIcon(collection = false)
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
internal fun number(value: Int): String = NumberFormat.getIntegerInstance(java.util.Locale.US).format(value)

/**
 * A medal you can tell by its outline as well as its metal: bronze is a plain coin, silver a
 * scalloped rosette, gold a sunburst and diamond a cut gem. Earned ones stand on a darker lip with
 * a gloss and a white star; locked ones are just the faint outline of what is coming.
 */
class MedalIcon(private val color: Int, private val earned: Boolean, private val diamond: Boolean = false,
    private val dark: Boolean = false, private val ribbon: Boolean = true) : Icon() {
    private val path = Path()
    private val tier = (0..3).firstOrNull { medalColor(it) == color } ?: if (diamond) 3 else 0

    private fun outline(cy: Float, grow: Float = 0f) {
        path.reset()
        when {
            tier == 3 -> {
                path.moveTo(10f - grow, cy - 10f - grow); path.lineTo(22f + grow, cy - 10f - grow); path.lineTo(28.5f + grow, cy - 3.5f)
                path.lineTo(16f, cy + 14.5f + grow); path.lineTo(3.5f - grow, cy - 3.5f)
            }
            tier == 0 -> path.addCircle(16f, cy, 12f + grow, Path.Direction.CW)
            else -> {
                // Silver: ten soft scallops. Gold: twelve sharp rays.
                val points = if (tier == 1) 60 else 24
                for (k in 0 until points) {
                    val a = k * 2.0 * Math.PI / points - Math.PI / 2
                    val r = grow + if (tier == 1) 11.4f + 1.3f * kotlin.math.cos(10 * a).toFloat() else if (k % 2 == 0) 13f else 10.6f
                    val x = 16f + r * kotlin.math.cos(a).toFloat(); val y = cy + r * kotlin.math.sin(a).toFloat()
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
            }
        }
        path.close()
    }

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 32f
        canvas.translate(bounds.exactCenterX() - 16f * scale, bounds.exactCenterY() - 16f * scale)
        canvas.scale(scale, scale)
        // Without a ribbon the medal is centred as painted: an earned one's lip counts too.
        // The gem reaches further below its girdle than above it.
        val faceY = (if (ribbon) 13f else if (earned) 15.4f else 16f) - if (tier == 3) 2f else 0f
        paint.style = Paint.Style.FILL
        paint.strokeJoin = Paint.Join.ROUND
        if (ribbon) {
            paint.color = if (earned) Theme.darken(color, .22f) else Theme.alpha(Theme.INK, 30)
            path.reset(); path.moveTo(7f, 17f); path.lineTo(6f, 31f); path.lineTo(12f, 27f); path.lineTo(16f, 30f); path.lineTo(19f, 18f); path.close()
            canvas.drawPath(path, paint)
            path.reset(); path.moveTo(17f, 18f); path.lineTo(19f, 30f); path.lineTo(23f, 27f); path.lineTo(26f, 31f); path.lineTo(25f, 17f); path.close()
            canvas.drawPath(path, paint)
        }
        if (!earned) {
            outline(faceY)
            paint.color = if (dark) Theme.alpha(Theme.LAVENDER, 13) else Theme.CARD_ALT
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.8f
            paint.color = if (dark) Theme.alpha(Theme.WHITE, 130) else Theme.alpha(Theme.INK, 100)
            canvas.drawPath(path, paint)
            canvas.restoreToCount(save)
            return
        }
        // The lip, then the face.
        outline(faceY + 1.2f); paint.color = Theme.darken(color, .32f); canvas.drawPath(path, paint)
        outline(faceY); paint.color = color; canvas.drawPath(path, paint)
        if (tier == 3) {
            // Facets: a bright table and crown on the left, a shaded pavilion on the right.
            paint.color = Theme.lighten(color, .55f)
            path.reset(); path.moveTo(10f, faceY - 10f); path.lineTo(22f, faceY - 10f); path.lineTo(19f, faceY - 3.5f); path.lineTo(13f, faceY - 3.5f); path.close()
            canvas.drawPath(path, paint)
            paint.color = Theme.lighten(color, .25f)
            path.reset(); path.moveTo(3.5f, faceY - 3.5f); path.lineTo(13f, faceY - 3.5f); path.lineTo(16f, faceY + 14.5f); path.close()
            canvas.drawPath(path, paint)
            paint.color = Theme.darken(color, .18f)
            path.reset(); path.moveTo(28.5f, faceY - 3.5f); path.lineTo(19f, faceY - 3.5f); path.lineTo(16f, faceY + 14.5f); path.close()
            canvas.drawPath(path, paint)
            star(canvas, 23.5f, faceY - 7.5f, 3.2f)
        } else {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.6f
            paint.color = Theme.lighten(color, .5f); canvas.drawCircle(16f, faceY, 8.2f, paint)
            paint.strokeWidth = 1.8f; paint.strokeCap = Paint.Cap.ROUND; paint.color = Theme.alpha(Theme.WHITE, 190)
            rect.set(7f, faceY - 9f, 25f, faceY + 9f); canvas.drawArc(rect, 200f, 55f, false, paint)
            star(canvas, 16f, faceY, 5.6f)
        }
        canvas.restoreToCount(save)
    }

    private fun star(canvas: Canvas, x: Float, y: Float, r: Float) {
        paint.style = Paint.Style.FILL; paint.color = Theme.WHITE
        path.reset(); path.moveTo(x, y - r); path.lineTo(x + r * .32f, y - r * .32f); path.lineTo(x + r, y); path.lineTo(x + r * .32f, y + r * .32f)
        path.lineTo(x, y + r); path.lineTo(x - r * .32f, y + r * .32f); path.lineTo(x - r, y); path.lineTo(x - r * .32f, y - r * .32f); path.close()
        canvas.drawPath(path, paint)
    }
}

/** A threshold under its medal: 500, 1K, 25K, 500K. */
internal fun compact(value: Int): String = when {
    value >= 1_000_000 && value % 1_000_000 == 0 -> "${value / 1_000_000}M"
    value >= 1000 && value % 1000 == 0 -> "${value / 1000}K"
    else -> number(value)
}
