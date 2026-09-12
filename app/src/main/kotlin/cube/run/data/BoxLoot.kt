package cube.run.data

/** Independent reward bands; pity survives app restarts through Progress. */
object BoxLoot {
    fun kind(roll: Float, coinStreak: Int): Int {
        val r = roll.coerceIn(0f, .999999f) * if (coinStreak >= 2) .5f else 1f
        return when {
            r < .1f -> Progress.BoxReward.SKIN
            r < .3f -> Progress.BoxReward.SHARDS
            r < .5f -> Progress.BoxReward.BUBBLE
            else -> Progress.BoxReward.COINS
        }
    }
}
