package cube.run.game

import androidx.test.core.app.ActivityScenario
import cube.run.GameActivity
import cube.run.bot.JetDescent
import cube.run.bot.LiveBotDriver.Companion.gl
import cube.run.core.Stage
import cube.run.data.Settings
import cube.run.game.track.LandingStrip
import cube.run.game.track.ObType
import cube.run.game.track.ObstacleFactory
import cube.run.game.track.Pickup
import cube.run.game.track.Row
import cube.run.game.track.Track
import org.junit.Assert.*
import org.junit.Test

/** A jetpack flight lands on its strip: the strip stays clear, touchdown is on its line, and only cruising is safe. */
class JetLandingStripTest {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST") private fun <T> value(owner: Any, name: String): T = field(owner, name).get(owner) as T

    @Test fun flightLandsOnAClearStripWithNoHiddenSafeTime() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            gl { game ->
                val sound = Settings.soundEnabled; val haptics = Settings.hapticsEnabled
                try {
                    Settings.setSoundEnabled(false); Settings.setHapticsEnabled(false)
                    Stage.paused = false; game.onTap(360f, 760f)
                    val player: Player = value(game, "player")
                    val track: Track = value(game, "track")
                    repeat(120) { game.tick(1f / 60f) } // up to speed
                    val pickup = CubeRun::class.java.getDeclaredMethod("collectPickup", Row::class.java, Float::class.javaPrimitiveType).apply { isAccessible = true }
                    pickup.invoke(game, Row(-0.5f, arrayListOf()).apply { this.pickup = Pickup.JET }, -0.5f)
                    val strip = track.landing
                    assertNotNull("laid no strip", strip); strip!!
                    assertTrue(player.flying)
                    // it opens up only for the low end of the descent
                    assertFalse(strip.covers(strip.touchZ + strip.descentLen * 0.5f))
                    var descendingFrames = 0
                    var frames = 0
                    while (player.flying && frames < 60 * 30) {
                        // nothing solid (tar included) and no platform is ever laid on the landing
                        for (row in track.rows) if (strip.covers(row.z)) assertTrue("hazard on the landing at ${row.z}",
                            row.obs.none { it.type == ObType.SOLID || it.type == ObType.PLAT })
                        val before = strip.touchZ
                        game.tick(1f / 60f); frames++
                        if (player.flying && player.descending) descendingFrames++
                        if (!player.flying) assertTrue("touched down off the line: $before", before > -2f && before <= 0.5f)
                    }
                    assertFalse("never landed", player.flying)
                    // the checkerboard of coins lies on the ground past touchdown
                    val grid = track.rows.sumOf { row -> row.coins?.count { c -> !c.taken && row.z + c.dz < strip.touchZ && row.z + c.dz > strip.endZ && c.y < 0.6f } ?: 0 }
                    assertTrue("only $grid checkerboard coins", grid >= 8)
                    // a pad cleared off the landing takes the wall only it could clear with it; later walls stay
                    val factory: ObstacleFactory = value(game, "obstacles")
                    val zone = LandingStrip(-60f, 10f, 20f).also { track.landing = it }
                    val pad = Row(zone.endZ + 2f, arrayListOf(factory.pad(1, 0f)))
                    val tall = Row(zone.endZ - 4f, arrayListOf(factory.wall(0f))).apply { needsLaunch = true }
                    val wall = Row(zone.endZ - 12f, arrayListOf(factory.wall(0f)))
                    val laterTall = Row(zone.endZ - 20f, arrayListOf(factory.wall(0f))).apply { needsLaunch = true }
                    val laid = listOf(pad, tall, wall, laterTall)
                    track.rows.clear(); track.rows.addAll(laid); track.clearStrip()
                    assertTrue(pad.obs.isEmpty()); assertTrue(tall.obs.isEmpty())
                    assertEquals(1, wall.obs.size); assertEquals(1, laterTall.obs.size)
                    track.rows.removeAll(laid)
                    assertEquals("descent length", JetDescent.SECONDS * 60f, descendingFrames.toFloat(), 6f)
                    assertFalse(value<Boolean>(game, "dead"))
                } finally {
                    Settings.setSoundEnabled(sound); Settings.setHapticsEnabled(haptics)
                }
            }
        }
    }
}
