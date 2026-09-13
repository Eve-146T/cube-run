package cube.run.game.track

import cube.run.data.Bonus
import cube.run.data.Settings
import cube.run.game.Lanes
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class TestWorldsTest {
    @Test fun presetsHaveBalancedPortalsAndHoverCompatibleRows() {
        assertEquals(8, TestWorlds.all.size)
        assertEquals(8, TestWorlds.all.map { it.id }.distinct().size)
        for (world in TestWorlds.all) {
            var bonus = Bonus.NONE
            for (cue in world.cues) {
                assertTrue(cue.gap >= 9.5f)
                if (cue.code == Step.PORTAL) {
                    if (cue.exit) { assertEquals(cue.world, bonus); bonus = Bonus.NONE }
                    else { assertEquals(Bonus.NONE, bonus); bonus = cue.world }
                } else if (bonus == Bonus.FLOAT) assertTrue(cue.code in 0..2 || cue.code == Step.EM)
            }
            assertEquals(Bonus.NONE, bonus)
        }
    }

    @Test fun jetAndExitPadScenariosContainTheReportedOrder() {
        for (id in 0..2) {
            val cues = TestWorlds.byId(id)!!.cues
            val jet = cues.indexOfFirst { it.pickup == Pickup.JET }
            val entry = cues.indexOfFirst { it.code == Step.PORTAL }
            assertEquals(id == 0, jet < entry)
            if (id == 2) {
                val exit = cues.indexOfFirst { it.exit }
                assertTrue(cues.subList(jet + 1, exit).sumOf { it.gap.toDouble() } > TestWorlds.JET_SECONDS * 52.5)
            }
        }
        for (id in 4..5) {
            val cues = TestWorlds.byId(id)!!.cues
            val exit = cues.indexOfFirst { it.exit }
            assertEquals(if (id == 4) Step.pd(0) else Step.pd(2), cues[exit + 1].code)
            assertEquals(Step.TW, cues[exit + 2].code)
        }
    }

    @Test fun realDecoderLoopsEachPresetAndSuppressesRandomPickups() {
        val old = Settings.testScenario
        try {
            for (world in TestWorlds.all) {
                Settings.testScenario = world.id; Lanes.reset()
                val t = Track(Random(73), ObstacleFactory(Random(73)))
                t.reset(0f, 200f)
                while (t.rowsSpawned < world.cues.size * 2) t.spawn(14f, 200f, 0)
                for (i in 0 until world.cues.size * 2) {
                    val cue = world.cues[i % world.cues.size]; val row = t.rows[i]
                    assertEquals("${world.name} row $i pickup", cue.pickup, row.pickup)
                    if (cue.code == Step.PORTAL) { assertEquals(cue.world, row.portal); assertEquals(cue.exit, row.portalExit) }
                    else assertEquals(-1, row.portal)
                }
                // Restart starts at the first cue, rather than continuing an old loop.
                t.reset(0f, 200f)
                assertEquals(world.cues.first().pickup, t.rows.first().pickup)
            }
            Settings.testScenario = -1
            val t = Track(Random(73), ObstacleFactory(Random(73))); t.reset(0f, 200f)
            assertEquals(-38f, t.rows.first().z, .001f)
        } finally { Settings.testScenario = old; Lanes.reset() }
    }
}
