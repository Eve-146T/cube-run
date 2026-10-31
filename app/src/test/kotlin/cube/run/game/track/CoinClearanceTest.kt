package cube.run.game.track

import cube.run.data.Bonus
import cube.run.game.Lanes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CoinClearanceTest {
    private fun track(seed: Int = 1): Pair<Track, ObstacleFactory> {
        Lanes.reset()
        val rng = Random(seed)
        val fx = ObstacleFactory(rng)
        return Track(rng, fx) to fx
    }

    @Test fun noCoinIsLaidInsideAnObstacle() {
        for (world in listOf(Bonus.NONE, Bonus.WIDE, Bonus.HILLS, Bonus.FLOAT, Bonus.KALEIDO)) for (speed in listOf(14f, 30f)) for (seed in 0 until 6) {
            val (track, _) = track(seed)
            track.tier = 3; track.speed = speed
            track.reset(0.2f, 0f, initialBonus = world)
            var time = 0f; val dt = 1f / 60f
            repeat(60 * 60) {
                time += dt
                track.scroll(speed * dt, time, dt); track.spawn(speed * dt, 0f, 0, dt)
                for (r in track.rows) for (c in r.coins.orEmpty()) {
                    if (c.taken) continue
                    val z = r.z + c.dz
                    assertEquals("world $world speed $speed seed $seed: coin at ${c.x}, ${c.y}, $z", c.y, track.coinClearY(c.x, c.y, z), 0f)
                }
            }
        }
    }

    @Test fun aPulledCoinHopsOverAWallAndDucksUnderABar() {
        val (track, fx) = track()
        track.rows.add(Row(-10f, arrayListOf(fx.wall(0f))))
        track.rows.add(Row(-20f, arrayListOf(fx.over(0f))))
        val wall = track.rows[0].obs[0]
        val bar = track.rows[1].obs[0]
        assertTrue(track.coinClearY(0f, 0.5f, -10f) >= wall.top + Track.COIN_R)
        assertTrue(track.coinClearY(0f, 1.0f, -20f) <= bar.bottom - Track.COIN_R)
        assertEquals(0.5f, track.coinClearY(0f, 0.5f, -15f), 0f) // open road between them
        assertEquals(1.75f, track.coinClearY(0f, 1.75f, -10f), 0f) // the jump arc over the wall is already clear
    }
}
