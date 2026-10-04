package cube.run.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BoxLootPricingTest {
    private fun withOwnership(mask: Int, check: () -> Unit) {
        val fields = listOf("ownedSkins", "ownedBubbleSkins", "ownedTrails").map {
            Progress::class.java.getDeclaredField(it).apply { isAccessible = true }
        }
        val saved = fields.map { it.getInt(Progress) }
        try {
            fields.forEach { it.setInt(Progress, mask) }
            check()
        } finally {
            fields.forEachIndexed { index, field -> field.setInt(Progress, saved[index]) }
        }
    }

    @Test fun newCollectionBoxesCostOneThousandCoins() = withOwnership(1) {
        assertEquals(1_000, Progress.mysteryBoxPrice)
    }

    @Test fun completedCollectionKeepsFullValueForBubbleFallbacks() = withOwnership(-1) {
        assertEquals(800, Progress.mysteryBoxPrice)
    }
}
