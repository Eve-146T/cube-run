package cube.run.data

/** Independent reward bands; pity survives app restarts through Progress. */
object BoxLoot {
    /**
     * Pity's stationary streak weights are 4/7, 2/7, 1/7. Reward weights are
     * coins 3/7, skins 4/35, shards 8/35, bubbles 8/35. Coins average 1100;
     * four bubbles cost 480. Cosmetics use the actual category-then-item mean.
     * Shards have no coin price: conservatively value a completed shard cube at
     * the mean regular cube price, prorated by required shards (17.5 per drop).
     * Exhausted collections follow openBox's fallback rules. Round up to 100.
     */
    fun purchasePrice(): Int {
        val bubbleValue = 4.0 * Progress.BUBBLE_PRICE
        val categories = Wardrobe.cats.filter { Progress.unowned(it).isNotEmpty() }
        val shardSkins = Shards.all.mapNotNull { Skins.forShard(it.id) }
            .filter { !Progress.owns(Wardrobe.CUBE, it.id) }
        val regularCubeValue = Skins.all.filter { it.price > 0 && it.id != Skins.VOID_ID }
            .map { it.price.toDouble() }.average().takeIf { it.isFinite() } ?: bubbleValue
        val shardValue = if (shardSkins.isEmpty()) bubbleValue else
            shardSkins.map { regularCubeValue * 17.5 / it.shardsNeeded.coerceAtLeast(1) }.average()
        val skinValue = if (categories.isEmpty()) shardValue else categories.map { cat ->
            Progress.unowned(cat).map { Wardrobe.price(cat, it).toDouble() }.average()
        }.average()
        val mean = (3.0 / 7) * 1100 + (4.0 / 35) * skinValue + (8.0 / 35) * shardValue + (8.0 / 35) * bubbleValue
        return kotlin.math.ceil(mean / 100).toInt().coerceAtLeast(1) * 100
    }

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
