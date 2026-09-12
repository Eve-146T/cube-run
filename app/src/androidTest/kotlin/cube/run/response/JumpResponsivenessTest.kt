package cube.run.response

import androidx.test.core.app.ActivityScenario
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.bot.Body
import cube.run.bot.BotFixtures
import cube.run.bot.value
import cube.run.data.Settings
import cube.run.game.CubeRun
import cube.run.game.Player
import cube.run.core.Stage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class JumpResponsivenessTest {
    @Test fun edgeGraceBufferedLandingAndCancellationUseActualPlayerPhysics() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val done = CountDownLatch(1); var failure: Throwable? = null
            Gdx.app.postRunnable {
                val sound = Settings.soundEnabled; val haptics = Settings.hapticsEnabled
                try {
                    Settings.setSoundEnabled(false); Settings.setHapticsEnabled(false)
                    Stage.paused = true
                    val game = Gdx.app.applicationListener as CubeRun
                    val player: Player = value(game, "player")
                    fun step(dt: Float, ground: Float = 0f) = player.update(dt, 30f * dt, 10f, 200f, false, ground)
                    fun velocity() = value<Float>(player, "vy")
                    for (hz in listOf(60, 90)) for (delayMs in listOf(0, 50, 83, 117)) {
                        BotFixtures.restore(player, Body(y = 2.45f))
                        step(1f / hz) // walk off a two-unit platform
                        repeat(kotlin.math.ceil(delayMs * hz / 1000.0).toInt()) { step(1f / hz) }
                        player.jump()
                        assertEquals("edge grace $delayMs ms at $hz Hz", delayMs < 100, velocity() > 8f)
                    }
                    // A real jump never grants a second mid-air takeoff.
                    BotFixtures.restore(player, Body())
                    player.jump(); step(.05f)
                    val fallingVelocity = velocity(); player.jump()
                    assertEquals(fallingVelocity, velocity(), 0f)
                    repeat(80) { step(.01f) }
                    assertFalse("An old UP must not launch again on landing", player.air)

                    // An UP just before landing takes off on that very landing update.
                    BotFixtures.restore(player, Body(y = .8f, vy = -5f, air = true))
                    player.jump()
                    repeat(6) { step(.01f) }
                    assertTrue("Buffered landing jump lost", player.air && velocity() > 7f)

                    for (cancel in listOf("down", "pad", "flight", "hover", "pause", "save")) {
                        BotFixtures.restore(player, Body(y = .8f, vy = -5f, air = true))
                        player.jump()
                        when (cancel) {
                            "down" -> player.downAction()
                            "pad" -> player.launch(12.5f)
                            "flight" -> player.setFlying(true)
                            "hover" -> { player.hover = true; step(.01f) }
                            "pause" -> game.paused()
                            "save" -> player.forceGround(0f)
                        }
                        assertEquals("$cancel kept a stale UP", 0f, value<Float>(player, "jumpBuffer"), 0f)
                        assertEquals("$cancel kept edge grace", 0f, value<Float>(player, "coyoteLeft"), 0f)
                    }
                    BotFixtures.restore(player, Body())
                    game.onSwipe(cube.run.core.gfx.TouchInput.UP)
                    assertEquals("A paused swipe must not queue a jump", 0f, velocity(), 0f)
                } catch (t: Throwable) { failure = t }
                finally {
                    Settings.setSoundEnabled(sound); Settings.setHapticsEnabled(haptics)
                    Stage.paused = false; done.countDown()
                }
            }
            assertTrue(done.await(30, TimeUnit.SECONDS)); failure?.let { throw it }
        }
    }
}
