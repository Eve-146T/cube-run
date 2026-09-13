package cube.run.game

import androidx.test.core.app.ActivityScenario
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.bot.*
import cube.run.core.Stage
import cube.run.data.Bonus
import cube.run.data.Settings
import cube.run.game.track.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class JetBonusTest {
    @Test fun jetpackCrossesEveryBonusAndCollectsAirCoinsBeforeReturningToHover() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val done = CountDownLatch(1); var failure: Throwable? = null
            Gdx.app.postRunnable {
                val sound = Settings.soundEnabled; val haptics = Settings.hapticsEnabled
                try {
                    Settings.setSoundEnabled(false); Settings.setHapticsEnabled(false)
                    val game = Gdx.app.applicationListener as CubeRun
                    Stage.paused = false; game.onDown(360f, 760f)
                    val track: Track = value(game, "track")
                    val player: Player = value(game, "player")
                    val powers: PowerUps = value(game, "powerUps")
                    val portal = CubeRun::class.java.getDeclaredMethod("crossPortal", Row::class.java).apply { isAccessible = true }
                    val pickup = CubeRun::class.java.getDeclaredMethod("collectPickup", Row::class.java, Float::class.javaPrimitiveType).apply { isAccessible = true }
                    for (hz in listOf(60, 90)) for (world in Bonus.all) for (jetFirst in listOf(true, false)) {
                        val label = "${world.name} hz=$hz jetFirst=$jetFirst"
                        Lanes.reset(); Terrain.reset()
                        track.rows.clear(); field(Track::class.java, "spawnAcc").setFloat(track, -10000f)
                        field(CubeRun::class.java, "bonus").setInt(game, Bonus.NONE)
                        player.hover = false; player.setFlying(false); player.forceGround(0f)
                        player.moveToLane(1); powers.reset()
                        val jet = Row(-10f, arrayListOf()).apply { this.pickup = Pickup.JET }
                        val doorway = Row(0f, arrayListOf()).apply { this.portal = world.id }
                        if (jetFirst) pickup.invoke(game, jet, -10f)
                        portal.invoke(game, doorway)
                        if (!jetFirst) pickup.invoke(game, jet, -10f)
                        powers.jet.start(10f)
                        repeat(hz * 2) { game.tick(1f / hz) }
                        assertTrue("Flight lost: $label", player.flying)
                        assertEquals("Jet pulled down: $label", Player.FLY_Y, player.py, .08f)
                        // Exercise the normal coin collection path at flight height.
                        val coin = Coin(player.px, Player.FLY_Y, 0f)
                        track.rows.add(Row(-.3f, arrayListOf()).apply { coins = arrayListOf(coin) })
                        game.tick(1f / hz)
                        assertTrue("Sky coin missed: $label", coin.taken)
                        if (world.id == Bonus.FLOAT) {
                            powers.jet.start(.12f)
                            repeat(hz / 12) { game.tick(1f / hz) }
                            assertTrue(player.flying)
                            assertTrue("Glide aims below hover: $label", player.flyY >= Player.HOVER_Y)
                            repeat(hz * 2) { game.tick(1f / hz) }
                            assertFalse(player.flying); assertTrue(player.hover)
                            assertEquals(Player.HOVER_Y, player.py, .2f)
                            pickup.invoke(game, Row(-10f, arrayListOf()).apply { this.pickup = Pickup.JET }, -10f)
                            repeat(hz * 2) { game.tick(1f / hz) }
                        }
                        portal.invoke(game, Row(0f, arrayListOf()).apply { this.portal = world.id; portalExit = true })
                        repeat(hz) { game.tick(1f / hz) }
                        assertTrue("Exit cancels flight: $label", player.flying)
                        assertEquals(Player.FLY_Y, player.py, .08f)
                    }
                } catch (t: Throwable) { failure = t }
                finally { Settings.setSoundEnabled(sound); Settings.setHapticsEnabled(haptics); done.countDown() }
            }
            assertTrue(done.await(40, TimeUnit.SECONDS)); failure?.let { throw it }
        }
    }
}
