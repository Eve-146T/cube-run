package cube.run.game

import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import cube.run.core.GameSession
import cube.run.core.Gdx3DGame
import cube.run.data.Bonus
import cube.run.data.Progress
import cube.run.data.Settings
import cube.run.game.track.ObstacleFactory
import cube.run.game.track.Pickup
import cube.run.game.track.Track
import cube.run.game.world.Scenery
import cube.run.game.world.WorldRunner
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PerformanceCourseTest {
    private fun fixture(body: () -> Unit) {
        val dev = Settings.devMode; val world = Settings.testWorld; val section = Settings.testSection
        val pill = Settings.testPillWorld; val bonus = Settings.testBonus; val now = Settings.testBonusNow
        val boxes = Settings.testBoxes
        try { body() } finally {
            Settings.leavePerformanceCourse()
            Settings.setDevMode(dev); Settings.testWorld = world; Settings.testSection = section
            Settings.testPillWorld = pill; Settings.testBonus = bonus; Settings.testBonusNow = now
            Settings.testBoxes = boxes; Lanes.reset()
        }
    }

    @Test fun denseCourseRepeatsWithoutPickupsOrPortalsAndNormalPortalsReturn() = fixture {
        Settings.setDevMode(false)
        val bank = Progress.coins; val skin = Progress.skin
        Settings.selectPerformanceCourse()
        Settings.selectPerformanceCourse() // repeated selection must not overwrite the restore point
        assertTrue(Settings.performanceCourse)
        assertEquals(skin, Progress.skin)
        assertEquals(2, Settings.testWorld)
        fun course(): List<String> {
            Lanes.reset()
            val random = Random(73)
            val track = Track(random, ObstacleFactory(random)).apply {
                portalPool = listOf(Bonus.HILLS); portalEvery = 1
                reset(.2f, 250f)
                rows.clear()
            }
            val result = ArrayList<String>()
            repeat(300) {
                track.spawn(20f, 250f, 10000, .5f)
                for (row in track.rows) {
                    assertEquals("No surprise upgrade changes the workload", Pickup.NONE, row.pickup)
                    assertEquals("No bonus portal changes the workload", -1, row.portal)
                    assertTrue("Coin-only course", row.obs.isEmpty())
                    result.add(row.coins?.joinToString { "${it.x}/${it.y}/${it.dz}" } ?: "")
                }
                track.rows.clear()
            }
            assertTrue("The course must contain dense coins", result.count { it.count { ch -> ch == '/' } >= 24 } > 100)
            return result
        }
        assertEquals("Repeated course layout", course(), course())
        Settings.leavePerformanceCourse()
        assertFalse(Settings.performanceCourse)
        assertFalse(Settings.devMode)
        assertEquals(bank, Progress.coins)
        Settings.testSection = -1
        Settings.setDevMode(true)
        val track = Track(Random(73), ObstacleFactory(Random(74))).apply {
            portalPool = listOf(Bonus.HILLS); portalEvery = 1
            reset(.2f, 250f); rows.clear()
        }
        var portals = 0
        repeat(100) {
            track.spawn(20f, 250f, 10000, .5f)
            portals += track.rows.count { it.portal >= 0 }
            track.rows.clear()
        }
        assertTrue("Ordinary portals resume after leaving the test", portals > 0)
    }

    @Test fun fixedBiomeCanReturnToOrdinaryWorldRotation() = fixture {
        val session = object : GameSession {
            override val score = 0
            override val isOver = false
            override fun setScore(v: Int) {}
            override fun addScore(d: Int) {}
            override fun gameOver() {}
        }
        val game = object : Gdx3DGame(session) {
            override fun init() {}
            override fun tick(dt: Float) {}
            override fun renderWorld(batch: ModelBatch, env: Environment) {}
        }
        val scenery = Scenery(game, Random(74))
        val worlds = WorldRunner(game, scenery, Random(73))
        Settings.selectPerformanceCourse()
        worlds.reset(2)
        worlds.onRow(10000)
        val pending = worlds.javaClass.getDeclaredField("pendingWorld").apply { isAccessible = true }
        assertNull("Performance course keeps the selected biome", pending.get(worlds))
        Settings.leavePerformanceCourse()
        worlds.onRow(10000)
        assertNotNull("Normal biome changes resume", pending.get(worlds))
    }
}
