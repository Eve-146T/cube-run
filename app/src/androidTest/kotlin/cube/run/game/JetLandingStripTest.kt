package cube.run.game

import androidx.test.core.app.ActivityScenario
import cube.run.GameActivity
import cube.run.bot.JetDescent
import cube.run.bot.LiveBotDriver.Companion.gl
import cube.run.core.Stage
import cube.run.data.Settings
import cube.run.game.track.ObType
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
        for (variant in 0 until JetFlight.LANDS) ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            gl { game ->
                val sound = Settings.soundEnabled; val haptics = Settings.hapticsEnabled
                val land = Settings.testJetLand
                try {
                    Settings.setSoundEnabled(false); Settings.setHapticsEnabled(false)
                    Settings.testJetLand = variant
                    Stage.paused = false; game.onTap(360f, 760f)
                    val player: Player = value(game, "player")
                    val track: Track = value(game, "track")
                    val flight: JetFlight = value(game, "jetFlight")
                    repeat(120) { game.tick(1f / 60f) } // up to speed
                    val pickup = CubeRun::class.java.getDeclaredMethod("collectPickup", Row::class.java, Float::class.javaPrimitiveType).apply { isAccessible = true }
                    pickup.invoke(game, Row(-0.5f, arrayListOf()).apply { this.pickup = Pickup.JET }, -0.5f)
                    val strip = track.landing
                    assertNotNull("variant $variant laid no strip", strip); strip!!
                    assertTrue(player.flying)
                    var descendingFrames = 0
                    var frames = 0
                    while (player.flying && frames < 60 * 30) {
                        // nothing solid (tar included), no platform and no pad is ever laid on the strip
                        for (row in track.rows) if (strip.covers(row.z)) assertTrue("variant $variant: hazard on the strip at ${row.z}",
                            row.obs.none { it.type == ObType.SOLID || it.type == ObType.PLAT || it.type == ObType.PAD })
                        val before = strip.touchZ
                        game.tick(1f / 60f); frames++
                        if (player.flying && player.descending) descendingFrames++
                        if (!player.flying) assertTrue("variant $variant touched down off the line: $before", before > -2f && before <= 0.5f)
                    }
                    assertFalse("variant $variant never landed", player.flying)
                    assertEquals("variant $variant: descent length", JetDescent.seconds(flight.descent) * 60f, descendingFrames.toFloat(), 6f)
                    assertFalse(value<Boolean>(game, "dead"))
                } finally {
                    Settings.testJetLand = land
                    Settings.setSoundEnabled(sound); Settings.setHapticsEnabled(haptics)
                }
            }
        }
    }
}
