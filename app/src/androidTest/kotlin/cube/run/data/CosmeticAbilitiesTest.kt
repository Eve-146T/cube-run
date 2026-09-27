package cube.run.data

import cube.run.data.Skins.Ability
import org.junit.Assert.*
import org.junit.Test

class CosmeticAbilitiesTest {
    @Test fun abilitiesBelongOnlyToTheirRequestedCosmetics() {
        val expected = mapOf(
            1 to listOf(Ability.LOTTERY), 5 to listOf(Ability.POWER_STRETCH),
            6 to listOf(Ability.GOLD_COINS), 8 to listOf(Ability.DOUBLE_JUMP),
            10 to listOf(Ability.BUBBLE_HAUL, Ability.BUBBLE_DIVIDEND),
            11 to listOf(Ability.TOXIC_FORTUNE), 14 to listOf(Ability.CLOSE_SHAVE), 13 to listOf(Ability.PHASE),
            15 to listOf(Ability.COAL), 16 to listOf(Ability.BUBBLE_SAVER, Ability.QUICK_BUBBLE),
            17 to listOf(Ability.FLOATY), 22 to listOf(Ability.ZAPPY),
            23 to listOf(Ability.SPEED), Skins.VOID_ID to listOf(Ability.SECRET),
        )
        Skins.all.forEach { assertEquals("Cube ${it.id}", expected[it.id].orEmpty(), it.abilities) }
        BubbleSkins.all.forEach {
            assertEquals("Bubble ${it.id}", when (it.id) {
                2 -> listOf(Ability.DOUBLE_JUMP)
                3 -> listOf(Ability.GOLD_BUBBLE)
                8 -> listOf(Ability.GIGAJUMP)
                4 -> listOf(Ability.LONG_BUBBLE)
                else -> emptyList()
            }, it.abilities)
        }
        assertTrue(Trails.all.all { it.abilities.isEmpty() })
    }

    @Test fun modifiersHaveNeutralDefaultsAndRetainBubbleSaversExistingPower() {
        Skins.all.forEach {
            assertEquals(if (it.id == 6) 1.2f else if (it.id == 11) 1.6f else 1f, it.coinMultiplier, 0f)
            assertEquals(if (it.id == 5) 1.25f else 1f, it.powerupDurationMultiplier, 0f)
            assertEquals(if (it.id == 16) 0.7f else 1f, it.bubbleCooldownMultiplier, 0f)
        }
        BubbleSkins.all.forEach { assertEquals(if (it.id == 4) 1.3f else 1f, it.durationMultiplier, 0f) }
        assertEquals(0.35f, Skins.get(16).bubbleSaveChance, 0f)
        assertEquals(0.2f, Skins.get(Skins.VOID_ID).bubbleSaveChance, 0f)
    }

    @Test fun renamedCosmeticsKeepStableIdsAndPricesWithTheirNewAppearance() {
        val gambler = Skins.get(1)
        assertEquals("Gambler", gambler.name); assertEquals(6_789, gambler.price)
        assertEquals(Skins.STROBE, gambler.mode)
        assertEquals(setOf(0f, 52f), setOf(gambler.hueAt(0.1f, 0f), gambler.hueAt(0.3f, 0f)))
        val cloud = Skins.get(17)
        assertEquals("Cloud", cloud.name); assertEquals(2_200, cloud.price)
        assertTrue(cloud.sat < 0.1f); assertEquals(1f, cloud.value, 0f)
        assertEquals("Midas Little Toe", Ability.GOLD_COINS.title)
        assertTrue(Ability.LOTTERY.detail.contains("100000"))
        assertTrue(Ability.SECRET.detail.isEmpty())
    }
    @Test fun shopOrderKeepsOwnedIdsStableAndPutsPlainCubesLast() {
        val ids = Wardrobe.shopItems(Wardrobe.CUBE)
        assertEquals(Skins.all.map { it.id }.toSet(), ids.toSet())
        assertEquals(Skins.all.size, ids.size)
        val firstPlain = ids.indexOfFirst { Skins.get(it).abilities.isEmpty() }
        assertTrue(ids.take(firstPlain).all { Skins.get(it).abilities.isNotEmpty() })
        assertTrue(ids.drop(firstPlain).all { Skins.get(it).abilities.isEmpty() })
        assertTrue(ids.indexOf(1) > ids.indexOf(14))
        Skins.all.forEach { assertSame(it, Skins.get(it.id)) }
        assertEquals(499, Skins.get(7).price)
        assertEquals("Purple Cube", Skins.get(19).name); assertEquals(4, Skins.get(19).price)
        assertEquals(3000, Skins.get(23).price)
    }

}
