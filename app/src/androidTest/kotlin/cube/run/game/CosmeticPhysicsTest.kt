package cube.run.game

import androidx.test.core.app.ActivityScenario
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.bot.value
import cube.run.core.Stage
import cube.run.data.Settings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Exercise the actual rendered player, not a second copy of its physics. */
class CosmeticPhysicsTest {
    private fun withPlayer(block: (Player, Bubble) -> Unit) {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val done = CountDownLatch(1)
            var failure: Throwable? = null
            Gdx.app.postRunnable {
                val sound = Settings.soundEnabled; val haptics = Settings.hapticsEnabled
                val paused = Stage.paused
                try {
                    Stage.paused = true
                    Settings.setSoundEnabled(false); Settings.setHapticsEnabled(false)
                    val game = Gdx.app.applicationListener as CubeRun
                    val player: Player = value(game, "player")
                    player.setFlying(false); player.hover = false; player.floaty = false
                    player.doubleJumpEnabled = false; player.forceGround(0f)
                    block(player, value(game, "bubble"))
                } catch (t: Throwable) { failure = t }
                finally {
                    Settings.setSoundEnabled(sound); Settings.setHapticsEnabled(haptics)
                    Stage.paused = paused; done.countDown()
                }
            }
            assertTrue("Physics frame completed", done.await(20, TimeUnit.SECONDS))
            failure?.let { throw it }
        }
    }
    private fun step(player: Player, dt: Float = .01f, ground: Float = 0f) =
        player.update(dt, 30f * dt, 10f, 200f, false, ground)
    private fun velocity(player: Player) = value<Float>(player, "vy")

    @Test fun mintGrantsOneAirJumpAndReactivationCannotRefillIt() = withPlayer { p, _ ->
        p.doubleJumpEnabled = true
        // Decorative menu hops must not become a fresh gameplay jump charge.
        p.idle(4f); step(p)
        val idleVelocity = velocity(p)
        p.jump(); assertEquals(idleVelocity, velocity(p), 0f)
        p.forceGround(0f)
        p.jump(); repeat(15) { step(p) }
        assertTrue(velocity(p) < 5f)
        p.jump(); assertEquals(8.4f, velocity(p), .001f)
        step(p); val afterExtra = velocity(p)
        p.jump(); assertEquals("No third takeoff", afterExtra, velocity(p), 0f)
        p.doubleJumpEnabled = false; p.doubleJumpEnabled = true
        p.jump(); assertEquals("New shield cannot refill a used airborne jump", afterExtra, velocity(p), 0f)
        repeat(100) { step(p) }
        assertFalse(p.air)
        p.jump(); step(p); p.jump()
        assertEquals("Landing grants a new double jump", 8.4f, velocity(p), .001f)
        p.forceGround(0f); p.jump(); step(p); p.doubleJumpEnabled = false
        val expiredVelocity = velocity(p)
        p.jump(); assertEquals("Expired shield cannot double jump", expiredVelocity, velocity(p), 0f)
        p.forceGround(0f); p.doubleJumpEnabled = true; p.jump(); p.setFlying(true)
        p.jump(); assertEquals("No jump while flying", 0f, velocity(p), 0f)
        p.setFlying(false); p.hover = true; p.jump()
        assertEquals("No jump in zero gravity", 0f, velocity(p), 0f)
    }

    @Test fun cloudHasLongerHangTimeWithoutChangingSlamOrFlight() = withPlayer { p, _ ->
        fun arc(floaty: Boolean): Pair<Float, Float> {
            p.floaty = floaty; p.forceGround(0f); p.jump()
            var elapsed = 0f; var peak = p.py
            while (p.air && elapsed < 3f) { step(p); elapsed += .01f; peak = maxOf(peak, p.py) }
            assertFalse("Jump must land", p.air)
            return elapsed to peak
        }
        val normal = arc(false); val cloud = arc(true)
        assertTrue("Floaty hangs at least 25% longer", cloud.first > normal.first * 1.25f)
        assertEquals("Normal obstacle clearance remains familiar", normal.second, cloud.second, .2f)
        p.jump(); repeat(15) { step(p) }; p.downAction(); step(p)
        assertEquals("Slam keeps its sharp acceleration", -19.26f, velocity(p), .01f)
        p.setFlying(true); p.flyY = Player.FLY_Y
        repeat(100) { step(p) }
        assertEquals(Player.FLY_Y, p.py, .15f)
    }

    @Test fun bubblegumCooldownAppliesToExpiryAndPopAndPlasmaDurationIsPreserved() = withPlayer { _, b ->
        b.reset(); b.cooldownDuration = 3.5f; b.duration = 13f
        b.activate(0f, .45f, quiet = true)
        assertEquals(13f, b.timeLeft, .001f)
        b.pop(0f, .45f)
        assertEquals(3.5f, b.cooldownLeft, .001f)
        b.update(3.49f, 1f, 0f, .45f); assertFalse(b.ready)
        b.update(.02f, 1f, 0f, .45f); assertTrue(b.ready)
        b.duration = .1f; b.activate(0f, .45f, quiet = true)
        assertTrue(b.update(.11f, 1f, 0f, .45f))
        assertEquals(3.5f, b.cooldownLeft, .001f)
        b.cooldownDuration = 5f; b.reset(); b.activate(0f, .45f, quiet = true)
        b.pop(0f, .45f); assertEquals(5f, b.cooldownLeft, .001f)
    }
}
