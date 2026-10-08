package cube.run.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The catalog must always offer free starters and all three shard unlock paths. */
class CataloguePricingTest {
    @Test fun freeStartersAndShardRequirementsStayIntact() {
        Wardrobe.cats.forEach { assertEquals(0, Wardrobe.price(it, 0)) }
        val shardCubes = Skins.all.filter { it.shardOnly }
        assertEquals(3, shardCubes.size)
        assertTrue(shardCubes.all { it.price == 0 && it.shardsNeeded == 100 })
        assertEquals(Shards.all.map { it.id }.toSet(), shardCubes.map { it.shardType }.toSet())
    }
}
