package cube.run.game

import androidx.test.core.app.ActivityScenario
import cube.run.GameActivity
import cube.run.bot.*
import cube.run.bot.LiveBotDriver.Companion.gl
import cube.run.core.Stage
import cube.run.data.Settings
import cube.run.data.Skins
import cube.run.game.track.Track
import org.junit.Assert.*
import org.junit.Test

class JetPredictionTest {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST") private fun <T> value(owner: Any, name: String): T = field(owner, name).get(owner) as T

    @Test fun predictedJetExpiryAndSlowdownMatchRealPhysicsAt60And90Hz() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            gl { game ->
                val sound = Settings.soundEnabled
                val haptics = Settings.hapticsEnabled
                try {
                    Settings.setSoundEnabled(false); Settings.setHapticsEnabled(false)
                    Stage.paused = false; game.onTap(360f, 760f); Stage.paused = true
                    val player: Player = value(game, "player")
                    val track: Track = value(game, "track")
                    val powers: PowerUps = value(game, "powerUps")
                    value<Difficulty>(game, "difficulty").boostTo(1f)
                    field(game, "runSkin").set(game, Skins.get(23))
                    track.rows.clear(); field(track, "spawnAcc").setFloat(track, -10000f)
                    for (hz in listOf(60, 90)) {
                        Lanes.reset(); field(game, "bonus").setInt(game, -1)
                        player.hover = false; player.setFlying(false); player.forceGround(0f); player.setFlying(true)
                        powers.reset(); powers.jet.start(.3f)
                        field(game, "runT").setFloat(game, 10f)
                        field(game, "jetBoost").setFloat(game, 1f)
                        field(game, "jetGrace").setFloat(game, 0f)
                        val body = player.pilotBody(.3f)
                        val timeline = Timeline(Course(0, "jet landing", 0, 0, false, player.lane, emptyList()),
                            68.25f, dt = 1f/hz, seconds = 2f, motion = JetMotion(39f, 1f, .3f))
                        for (frame in timeline.frames.indices) {
                            game.tick(timeline.dt)
                            assertTrue(timeline.step(body, frame, Action.NONE))
                            val label = "$hz Hz frame=$frame"
                            assertEquals("speed $label", value<Float>(game, "spd"), timeline.frames[frame].movement/timeline.dt, .0001f)
                            assertEquals("height $label", player.py, body.y, .0001f)
                            assertEquals("flight $label", player.flying, body.flying)
                            assertEquals("landing grace $label", value<Float>(game, "jetGrace"), body.landingGrace, .0001f)
                        }
                        assertEquals(0f, body.landingGrace, 0f)
                        assertFalse(player.flying)
                    }
                } finally {
                    Stage.paused = false
                    Settings.setSoundEnabled(sound); Settings.setHapticsEnabled(haptics)
                }
            }
        }
    }
}
