package cube.run.game

import cube.run.data.Settings
import cube.run.game.track.ObstacleFactory
import cube.run.game.track.Pickup
import cube.run.game.track.Track
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PillTestWorldTest {
    @Test fun normalAndDevRunsKeepRedPillsThirtyTimesRarerThanMagnets() {
        val previous = Settings.testPillWorld
        val section = Settings.testSection
        val dev = Settings.devMode
        try {
            Settings.testPillWorld = false; Settings.testSection = -1
            for (developerMode in listOf(false, true)) {
                Settings.setDevMode(developerMode)
                Lanes.reset()
                val track = Track(Random(771), ObstacleFactory(Random(89)))
                track.reset(.2f, 250f)
                track.rows.clear()
                var pills = 0; var magnets = 0; var multipliers = 0
                repeat(30000) {
                    track.spawn(20f, 250f, 10000, .5f)
                    for (row in track.rows) when (row.pickup) {
                        Pickup.RED_PILL -> pills++
                        Pickup.MAGNET -> magnets++
                        Pickup.MULT -> multipliers++
                    }
                    track.rows.clear()
                }
                assertTrue("Pills still occur naturally", pills > 5)
                assertTrue("Pills are far rarer than common power-ups: $pills / $magnets", pills < magnets / 15)
                assertTrue("The normal shuffled bag remains balanced", kotlin.math.abs(magnets-multipliers) <= 1)
                android.util.Log.i("PILL_RARITY", "dev=$developerMode magnets=$magnets redPills=$pills ratio=${magnets.toFloat()/pills}")
            }
        } finally {
            Settings.testPillWorld = previous; Settings.testSection = section; Settings.setDevMode(dev); Lanes.reset()
        }
    }

    @Test fun repeatedPillsLeaveTimeForTheFullExitAtEveryRunningSpeed() {
        val previous = Settings.testPillWorld
        try {
            Settings.testPillWorld = true
            for (speed in listOf(5f, 15f, 40f)) {
                Lanes.reset()
                val track = Track(Random(42), ObstacleFactory(Random(42)))
                track.reset(.2f, 250f)
                assertTrue(track.isPillTest)
                val pill = RedPill()
                var pickups = 0
                var time = 0f
                repeat(8000) {
                    val dt = .01f; time += dt
                    track.spawn(speed*dt, 250f, 0, dt)
                    track.scroll(speed*dt, time, dt)
                    pill.tick(dt, true)
                    for (row in track.rows) {
                        assertTrue("Middle lane stays clear", row.obs.all { it.lateral(0f) > 0f })
                        assertEquals(-1, row.portal)
                        assertNull(row.coins)
                        assertTrue(row.pickup == Pickup.NONE || row.pickup == Pickup.RED_PILL)
                        if (row.pickup == Pickup.RED_PILL && row.z + cube.run.game.track.Row.PICKUP_DZ >= 0f) {
                            assertEquals("Previous pill fully faded before the next arrives", 0f, pill.blend, .0001f)
                            pill.collect(); pickups++; row.pickup = Pickup.NONE
                        }
                    }
                }
                assertTrue("Pills repeat for an extended run", pickups >= 4)
                Settings.testPillWorld = false
                track.reset(.2f, 250f)
                assertFalse("Play normally restores the normal director", track.isPillTest)
                Settings.testPillWorld = true
            }
        } finally { Settings.testPillWorld = previous; Lanes.reset() }
    }
}
