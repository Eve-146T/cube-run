package cube.run.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Coin repricing must preserve the free starters and the separate shard economy. */
class CataloguePricingTest {
    @Test fun ordinaryCollectionsUseTheRepricedCoinEconomy() {
        assertEquals(89_200, Skins.all.filter { it.id != Skins.VOID_ID }.sumOf { it.price })
        assertEquals(34_900, BubbleSkins.all.filter { it.id != BubbleSkins.VOID_ID }.sumOf { it.price })
        assertEquals(33_500, Trails.all.filter { it.id != Trails.VOID_ID }.sumOf { it.price })
        assertEquals(42, Wardrobe.cats.sumOf { cat ->
            (0 until Wardrobe.count(cat)).count { Wardrobe.price(cat, it) > 0 }
        })
    }

    @Test fun secretCosmeticsUseTheSameMultiplier() {
        assertEquals(300_000, Wardrobe.price(Wardrobe.CUBE, Skins.VOID_ID))
        assertEquals(500_000, Wardrobe.price(Wardrobe.TRAIL, Trails.VOID_ID))
        assertEquals(750_000, Wardrobe.price(Wardrobe.BUBBLE, BubbleSkins.VOID_ID))
    }

    @Test fun freeStartersAndShardRequirementsStayIntact() {
        Wardrobe.cats.forEach { assertEquals(0, Wardrobe.price(it, 0)) }
        val shardCubes = Skins.all.filter { it.shardOnly }
        assertEquals(3, shardCubes.size)
        assertTrue(shardCubes.all { it.price == 0 && it.shardsNeeded == 100 })
        assertEquals(Shards.all.map { it.id }.toSet(), shardCubes.map { it.shardType }.toSet())
    }
}
