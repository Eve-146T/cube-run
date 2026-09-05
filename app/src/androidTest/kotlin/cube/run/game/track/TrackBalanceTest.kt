package cube.run.game.track

import androidx.test.ext.junit.runners.AndroidJUnit4
import cube.run.data.Bonus
import cube.run.data.Progress
import cube.run.data.Settings
import cube.run.game.Lanes
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.random.Random

/** Exercise the real director with seeded runs, including rows born before their portal is crossed. */
@RunWith(AndroidJUnit4::class)
class TrackBalanceTest {
    private var previousSection = -1

    @Before fun setup() {
        previousSection = Settings.testSection
        Settings.testSection = -1
        Lanes.reset()
    }

    @After fun restore() {
        Settings.testSection = previousSection
        Lanes.reset()
    }

    private fun track(seed: Int, tier: Int = 0): Track {
        val random = Random(seed)
        return Track(random, ObstacleFactory(random)).apply {
            this.tier = tier
            reset(0.2f, 205f)
        }
    }

    private fun generate(track: Track, count: Int, score: Int = 100) {
        while (track.rowsSpawned < count) track.spawn(1f, 205f, score)
    }

    @Test fun fiveLanesRequireMovementAndReturnSafelyThroughTheExit() {
        repeat(40) { seed ->
            Lanes.reset()
            val track = track(seed).apply { portalPool = listOf(Bonus.WIDE); portalEvery = 0 }
            generate(track, 105)
            val entry = track.rows.indexOfFirst { it.portal == Bonus.WIDE && !it.portalExit }
            assertTrue(entry >= 0)
            val wide = track.rows.drop(entry + 1).takeWhile { it.laneCount == 5 }
            assertEquals(42, wide.size)
            assertEquals(track.rows[entry].safeX(), wide.first().safeX(), 0.001f)
            assertEquals((0..4).toSet(), wide.map { it.safeLane }.toSet())
            for ((index, row) in wide.withIndex()) {
                assertEquals(4, row.obs.size)
                for (lane in 0..4) {
                    val x = (lane - 2) * Lanes.NORMAL_W
                    assertEquals("seed $seed row $index lane $lane", lane == row.safeLane, row.obs.none { it.lateral(x) < 0f })
                }
                if (index > 0) assertTrue(abs(row.safeLane - wide[index - 1].safeLane) <= 1)
            }
            // Outside the exit approach, no lane stays clear for three consecutive rows.
            for (window in wide.dropLast(4).windowed(3)) assertTrue(window.map { it.safeLane }.distinct().size > 1)
            val exit = track.rows[entry + 1 + wide.size]
            assertTrue(exit.portalExit)
            assertEquals(wide.last().safeX(), exit.safeX(), 0.001f)
            assertTrue(wide.any { it.pickup != Pickup.NONE })
            for (row in wide.filter { it.pickup != Pickup.NONE }) assertEquals(row.safeX(), row.pickupX, 0.001f)
        }
    }

    @Test fun everyPickupKindKeepsReturningAfterTheScoreUnlock() {
        val kinds = setOf(Pickup.MAGNET, Pickup.MULT, Pickup.JET, Pickup.BUBBLE, Pickup.BOX)
        val bagSize = 5 + Progress.level(Progress.LUCKYBOX)
        for (tier in 0..3) repeat(20) { seed ->
            val track = track(seed, tier)
            generate(track, 600)
            val pickups = track.rows.mapIndexedNotNull { index, row -> if (row.pickup == Pickup.NONE) null else index to row.pickup }
            assertTrue("tier $tier seed $seed has too few pickups", pickups.size >= 25)
            assertTrue(pickups.first().first in 12..65)
            for (kind in kinds) {
                val positions = pickups.filter { it.second == kind }.map { it.first }
                val rarity = if (kind == Pickup.JET || kind == Pickup.BOX) 2 else 1
                assertTrue("kind $kind missing at tier $tier seed $seed", positions.first() < 12 + bagSize * 26 * rarity)
                assertTrue(positions.size >= pickups.size / (bagSize * rarity))
                for ((a, b) in positions.zipWithNext()) assertTrue("kind $kind drought: ${b - a} rows", b - a < bagSize * 48 * rarity)
            }
            for ((a, b) in pickups.zipWithNext()) assertTrue(b.first - a.first >= 10)
        }
    }

    @Test fun jetpacksAndBoxesWaitForTheActualScoreEvenAfterHundredsOfRows() {
        for (bonus in listOf(Bonus.NONE, Bonus.WIDE, Bonus.HILLS)) {
            val track = track(21).apply {
                if (bonus != Bonus.NONE) { portalPool = listOf(bonus); portalEvery = 0 }
            }
            generate(track, 400, score = 99)
            assertTrue(track.rows.none { it.pickup == Pickup.JET || it.pickup == Pickup.BOX })
            assertTrue(track.rows.any { it.pickup == Pickup.MAGNET })
            val beforeUnlock = track.rows.size
            generate(track, 900, score = 100)
            val unlocked = track.rows.drop(beforeUnlock)
            assertTrue(unlocked.any { it.pickup == Pickup.JET })
            assertTrue(unlocked.any { it.pickup == Pickup.BOX })
            track.reset(0.2f, 205f)
            generate(track, 200, score = 0)
            assertTrue(track.rows.none { it.pickup == Pickup.JET || it.pickup == Pickup.BOX })
        }
    }

    @Test fun jetpackFrequencyIsHalfTheUnchangedCommonPickupFrequency() {
        val track = track(53)
        generate(track, 6000)
        fun count(kind: Int) = track.rows.count { it.pickup == kind }
        val magnets = count(Pickup.MAGNET)
        assertTrue(magnets > 40)
        assertTrue(abs(count(Pickup.MULT) - magnets) <= 1)
        assertTrue(abs(count(Pickup.BUBBLE) - magnets) <= 1)
        assertEquals(magnets / 2f, count(Pickup.JET).toFloat(), 1.5f)
        if (Progress.level(Progress.LUCKYBOX) == 0) {
            assertEquals(magnets / 2f, count(Pickup.BOX).toFloat(), 1.5f)
        }
    }

    @Test fun hillsMixObstaclesAndCoinsAndFinishAfterAShortStretch() {
        var visitsWithPickups = 0
        repeat(20) { seed ->
            val track = track(seed).apply { portalPool = listOf(Bonus.HILLS); portalEvery = 0 }
            generate(track, 100)
            val entry = track.rows.indexOfFirst { it.portal == Bonus.HILLS && !it.portalExit }
            val hills = track.rows.drop(entry + 1).takeWhile { !it.portalExit }
            assertTrue(hills.size in 30..35)
            assertTrue(hills.count { it.obs.isNotEmpty() } >= hills.size * 0.4f)
            assertTrue(hills.any { it.obs.isEmpty() && !it.coins.isNullOrEmpty() })
            assertTrue(hills.any { row -> row.obs.any { it.top < 0.7f || it.bottom > 0.7f } })
            if (hills.any { it.pickup != Pickup.NONE }) visitsWithPickups++
            for ((a, b) in hills.zipWithNext()) assertTrue(abs(a.safeLane - b.safeLane) <= 1)
        }
        // Some short visits contain only skipped rare slots; hills still support pickups.
        assertTrue(visitsWithPickups >= 10)
    }

    @Test fun repeatedPillarsHaveDifferentStableColoursWithinOneBiome() {
        val track = track(7)
        val colours = track.rows.take(4).map { it.obs.first().col.toIntBits() }
        assertEquals(4, colours.distinct().size)
        val first = track.rows.first().obs.first().col.toIntBits()
        track.scroll(1f, 1f, 0.016f)
        assertEquals(first, track.rows.first().obs.first().col.toIntBits())
    }
}
