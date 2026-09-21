package cube.run.data

import cube.run.data.Skins.Ability
import org.junit.Assert.*
import org.junit.Test

class CosmeticAbilitiesTest {
    @Test fun abilitiesBelongOnlyToTheirRequestedCosmetics() {
        val expected = mapOf(
            1 to listOf(Ability.LOTTERY), 5 to listOf(Ability.POWER_STRETCH),
            6 to listOf(Ability.GOLD_COINS), 13 to listOf(Ability.PHASE),
            15 to listOf(Ability.COAL), 16 to listOf(Ability.BUBBLE_SAVER, Ability.QUICK_BUBBLE),
            17 to listOf(Ability.FLOATY), 22 to listOf(Ability.ZAPPY),
            23 to listOf(Ability.SPEED), Skins.VOID_ID to listOf(Ability.SECRET),
        )
        Skins.all.forEach { assertEquals("Cube ${it.id}", expected[it.id].orEmpty(), it.abilities) }
        BubbleSkins.all.forEach {
            assertEquals("Bubble ${it.id}", when (it.id) {
                2 -> listOf(Ability.DOUBLE_JUMP)
                4 -> listOf(Ability.LONG_BUBBLE)
                else -> emptyList()
            }, it.abilities)
        }
        assertTrue(Trails.all.all { it.abilities.isEmpty() })
    }

    @Test fun modifiersHaveNeutralDefaultsAndRetainBubbleSaversExistingPower() {
        Skins.all.forEach {
            assertEquals(if (it.id == 6) 1.2f else 1f, it.coinMultiplier, 0f)
            assertEquals(if (it.id == 5) 1.25f else 1f, it.powerupDurationMultiplier, 0f)
            assertEquals(if (it.id == 16) 0.7f else 1f, it.bubbleCooldownMultiplier, 0f)
        }
        BubbleSkins.all.forEach { assertEquals(if (it.id == 4) 1.3f else 1f, it.durationMultiplier, 0f) }
        assertEquals(0.35f, Skins.get(16).bubbleSaveChance, 0f)
        assertEquals(0.2f, Skins.get(Skins.VOID_ID).bubbleSaveChance, 0f)
    }

    @Test fun renamedCosmeticsKeepStableIdsAndPricesWithTheirNewAppearance() {
        val gambler = Skins.get(1)
        assertEquals("Gambler", gambler.name); assertEquals(1_500, gambler.price)
        assertEquals(Skins.STROBE, gambler.mode)
        assertEquals(setOf(0f, 52f), setOf(gambler.hueAt(0.1f, 0f), gambler.hueAt(0.3f, 0f)))
        val cloud = Skins.get(17)
        assertEquals("Cloud", cloud.name); assertEquals(2_200, cloud.price)
        assertTrue(cloud.sat < 0.1f); assertEquals(1f, cloud.value, 0f)
        assertEquals("Midas Little Toe", Ability.GOLD_COINS.title)
        assertTrue(Ability.LOTTERY.detail.contains("100,000"))
        assertTrue(Ability.SECRET.detail.isEmpty())
    }
}
