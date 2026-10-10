package cube.run.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

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
    "cookie" -> AchievementHapticIcon()
    "greed" -> ChallengeIcon(id)
    "pile_driver" -> ChallengeIcon(id)
    "globetrotter", "long_hauler", "shardsmith", "regular", "bubble_popper", "near_miss",
    "untouchable", "house_loses", "voidwalker", "greedy", "scenic_route", "coal_miner",
    "magpie", "shard_hunter", "full_kit", "long_con", "insomniac", "bankrupt",
    "exactly_67", "just_browsing", "two_ez", "nervous_tic", "silent_treatment", "stage_fright", "neo", "monk" -> ChallengeIcon(id)
    else -> AchievementCubeIcon(collection = false)
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

/** A little phone vibrating around a fingertip's tap. */
private class AchievementHapticIcon : Icon() {
    private val waves = Path()
    override fun draw(canvas: Canvas) {
        val saved = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 48f
        canvas.translate(bounds.exactCenterX() - 24f * scale, bounds.exactCenterY() - 24f * scale)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL; paint.color = Theme.darken(Theme.SKY, .3f)
        rect.set(13f, 6f, 35f, 44f); canvas.drawRoundRect(rect, 5f, 5f, paint)
        paint.color = Theme.SKY
        rect.set(13f, 3f, 35f, 41f); canvas.drawRoundRect(rect, 5f, 5f, paint)
        paint.color = Theme.WHITE
        rect.set(17f, 8f, 31f, 34f); canvas.drawRoundRect(rect, 2f, 2f, paint)
        paint.color = Theme.SKY; canvas.drawCircle(24f, 21f, 4f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.strokeCap = Paint.Cap.ROUND
        paint.color = Theme.INK
        waves.reset(); waves.moveTo(8f, 14f); waves.lineTo(5f, 19f); waves.lineTo(8f, 24f); waves.lineTo(5f, 29f)
        waves.moveTo(40f, 14f); waves.lineTo(43f, 19f); waves.lineTo(40f, 24f); waves.lineTo(43f, 29f)
        canvas.drawPath(waves, paint)
        paint.style = Paint.Style.FILL; paint.color = Theme.GOLD
        rect.set(23f, 23f, 29f, 39f); canvas.drawRoundRect(rect, 3f, 3f, paint)
        canvas.restoreToCount(saved)
    }
}

private class AchievementWallIcon : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 48f, bounds.height() / 48f)
        // The wall, then the bounce off it: a chunky white arrow in and back out.
        paint.style = Paint.Style.FILL; paint.color = Theme.darken(Theme.PINK, .3f)
        rect.set(36f, 5.5f, 45f, 46f); canvas.drawRoundRect(rect, 3f, 3f, paint)
        paint.color = Theme.PINK; rect.set(36f, 3f, 45f, 43.5f); canvas.drawRoundRect(rect, 3f, 3f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 5.5f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        for (pass in 0..1) {
            paint.color = if (pass == 0) Theme.alpha(Theme.INK, 70) else Theme.WHITE
            val dy = if (pass == 0) 2f else 0f
            path.reset(); path.moveTo(5f, 8f + dy); path.lineTo(31f, 23f + dy); path.lineTo(7f, 38f + dy)
            path.moveTo(6f, 29f + dy); path.lineTo(7f, 38f + dy); path.lineTo(16f, 39f + dy)
            canvas.drawPath(path, paint)
        }
        canvas.restoreToCount(save)
    }
}

internal fun number(value: Int): String = value.toString()

/** A threshold under its medal: 500, 1K, 25K, 500K. */
internal fun compact(value: Int): String = when {
    value >= 1_000_000 && value % 1_000_000 == 0 -> "${value / 1_000_000}M"
    value >= 1000 && value % 1000 == 0 -> "${value / 1000}K"
    else -> number(value)
}
