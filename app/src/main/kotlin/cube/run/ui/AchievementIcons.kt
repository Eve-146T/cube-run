package cube.run.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/** The achievement page's drawn artwork: one icon per family, the medals, and the shared number formats. */
internal fun achievementIcon(id: String): android.graphics.drawable.Drawable = when (id) {
    "homeress" -> AchievementCoinlessIcon()
    "bounces" -> AchievementWallIcon()
    "cookie", "bubbles", "center", "gambliphobic", "runner", "coins", "cubes", "powerups", "boxes", "greed", "pile_driver", "globetrotter", "long_hauler", "shardsmith", "regular", "bubble_popper", "near_miss",
    "untouchable", "house_loses", "voidwalker", "greedy", "scenic_route", "coal_miner",
    "magpie", "shard_hunter", "full_kit", "long_con", "insomniac", "bankrupt",
    "exactly_67", "just_browsing", "two_ez", "nervous_tic", "silent_treatment", "very_silent_treatment", "secret", "stage_fright", "neo", "monk",
    "icarus", "poltergeist", "juggernaut", "full_throttle", "sky_sweeper", "news", "tweaks" -> ChallengeIcon(id)
    "trailblazer", "bubble_bath" -> CollectionIcon(id)
    else -> ChallengeIcon("runner")
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
