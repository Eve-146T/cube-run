package cube.run.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/** The achievement page's drawn artwork: one icon per family, the medals, and the shared number formats. */
internal fun achievementIcon(id: String): android.graphics.drawable.Drawable = when (id) {
    "homeress" -> AchievementCoinlessIcon()
    "bounces", "bubbles", "center", "gambliphobic", "cookie", "runner", "coins", "cubes", "powerups", "boxes", "greed", "pile_driver", "globetrotter", "long_hauler", "shardsmith", "regular", "bubble_popper", "near_miss",
    "untouchable", "house_loses", "voidwalker", "greedy", "scenic_route", "coal_miner",
    "magpie", "shard_hunter", "full_kit", "long_con", "insomniac", "bankrupt",
    "exactly_67", "just_browsing", "two_ez", "nervous_tic", "silent_treatment", "stage_fright", "neo", "monk" -> ChallengeIcon(id)
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

internal fun number(value: Int): String = value.toString()

/** A threshold under its medal: 500, 1K, 25K, 500K. */
internal fun compact(value: Int): String = when {
    value >= 1_000_000 && value % 1_000_000 == 0 -> "${value / 1_000_000}M"
    value >= 1000 && value % 1000 == 0 -> "${value / 1000}K"
    else -> number(value)
}
