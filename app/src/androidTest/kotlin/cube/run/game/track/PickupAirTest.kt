package cube.run.game.track

import androidx.test.ext.junit.runners.AndroidJUnit4
import cube.run.data.Bonus
import cube.run.data.Settings
import cube.run.game.Lanes
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.random.Random

/** Pickups keep the air around them clear of coins (#60); obstacles never take the road's colour (#71). */
@RunWith(AndroidJUnit4::class)
class PickupAirTest {
    private var previousSection = -1
    private var previousDev = false

    @Before fun setup() {
        previousSection = Settings.testSection; previousDev = Settings.devMode
        Settings.testSection = -1; Settings.setDevMode(false)
        Lanes.reset()
    }

    @After fun restore() {
        Settings.testSection = previousSection; Settings.setDevMode(previousDev)
        Lanes.reset()
    }

    @Test fun noCoinSitsWithinOneSlotOfAPickup() {
        var pickups = 0
        for (seed in 0 until 30) {
            Lanes.reset()
            val random = Random(seed)
            val track = Track(random, ObstacleFactory(random)).apply {
                portalPool = listOf(Bonus.WIDE, Bonus.HILLS); portalEvery = 60
                reset(0.6f, 205f)
            }
            // Scroll like the game does, and judge each pickup once its neighbours on both sides are laid.
            val judged = HashSet<Row>()
            var t = 0f
            while (track.rowsSpawned < 400) {
                track.spawn(0.5f, 205f, 1000)
                t += 1f / 60f
                track.scroll(0.5f, t, 1f / 60f)
                for (p in track.rows) {
                    if (p.pickup == Pickup.NONE || p.z < -70f || !judged.add(p)) continue
                    pickups++
                    val pz = p.z + Row.PICKUP_DZ
                    for (r in track.rows) for (c in r.coins.orEmpty()) {
                        val near = abs(r.z + c.dz - pz) < Track.PICKUP_AIR && abs(c.x - p.pickupX) < p.laneWidth * 1.5f
                        assertFalse("seed $seed: a coin crowds the pickup at z $pz", near)
                    }
                }
            }
        }
        assertTrue("pickups were laid", pickups > 100)
    }

    @Test fun obstacleHuesStayClearOfTheRoad() {
        val factory = ObstacleFactory(Random(1))
        val hsv = FloatArray(3)
        for (road in 0 until 360 step 7) {
            factory.roadHue = road.toFloat()
            for (base in 0 until 360 step 3) {
                val obs = ArrayList<Ob>()
                obs.add(factory.pillar(0f, base.toFloat()))
                obs.add(factory.wall(base.toFloat()))
                obs.add(factory.over(base.toFloat()))
                obs.add(factory.pad(1, base.toFloat()))
                obs.add(factory.platform(1, base.toFloat(), 6.5f, 0f))
                obs.add(factory.stomper(1, base.toFloat(), 0f))
                obs.add(factory.pendulum(base.toFloat(), 0f))
                for (ob in obs) {
                    ob.col.toHsv(hsv)
                    val d = abs(((hsv[0] - road) % 360f + 540f) % 360f - 180f)
                    assertTrue("road $road base $base: obstacle hue ${hsv[0]} too close", d >= ObstacleFactory.ROAD_APART - 0.5f)
                }
            }
        }
        // Without a road (Outer Space paints its own) the kinds keep their exact offsets.
        factory.roadHue = Float.NaN
        factory.pillar(0f, 0f).col.toHsv(hsv)
        assertEquals(185f, hsv[0], 0.5f)
    }

}
