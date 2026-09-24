package cube.run.game

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Skins
import cube.run.game.track.Coin
import cube.run.game.track.Ob
import cube.run.game.track.ObType
import cube.run.game.track.Row
import cube.run.game.track.Track
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZappyAbilityTest {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun withPlayer(action: (CubeRun, Player) -> Unit) {
        try {
            ActivityScenario.launch(GameActivity::class.java).use {
                val done = CountDownLatch(1)
                var failure: Throwable? = null
                Gdx.app.postRunnable {
                    try {
                        Stage.paused = true
                        val game = Gdx.app.applicationListener as CubeRun
                        val player = field(game, "player").get(game) as Player
                        player.zappyEnabled = true
                        player.setFlying(false); player.hover = false
                        player.forceGround(0f)
                        action(game, player)
                    } catch (t: Throwable) { failure = t }
                    finally { done.countDown() }
                }
                assertTrue("Zappy GL callback completed", done.await(20, TimeUnit.SECONDS))
                failure?.let { throw it }
            }
        } finally { Stage.paused = false; Stage.previewSkin = -1 }
    }

    @Test fun teleportIsImmediateAndPreservesJumpAndRoll() = withPlayer { _, player ->
        player.downAction()
        player.update(.025f, 0f, 0f, 0f, false, 0f)
        val duck = player.duck
        val duckTime = field(player, "duckT").getFloat(player)
        val groundY = player.py
        assertTrue(player.moveToLane(0))
        assertEquals(Lanes.x(0), player.px, 0f)
        assertEquals(groundY, player.py, 0f)
        assertEquals(duck, player.duck, 0f)
        assertEquals(duckTime, field(player, "duckT").getFloat(player), 0f)

        player.jump()
        player.update(.025f, 0f, .025f, 0f, false, 0f)
        val jumpY = player.py
        val velocity = field(player, "vy").getFloat(player)
        assertTrue(player.moveToLane(2))
        assertEquals(Lanes.x(2), player.px, 0f)
        assertEquals(jumpY, player.py, 0f)
        assertEquals(velocity, field(player, "vy").getFloat(player), 0f)
        assertTrue(player.air)
        repeat(20) {
            val target = if (it % 2 == 0) 0 else 2
            assertTrue(player.moveToLane(target))
            assertEquals("Opposite queued input has no animation lock", Lanes.x(target), player.px, 0f)
        }
        assertFalse(player.moveToLane(3))
        assertEquals(jumpY, player.py, 0f)
    }

    @Test fun teleportSkipsIntermediateCoinsAndObstaclesButDestinationStillCollides() = withPlayer { game, player ->
        field(game, "started").setBoolean(game, true)
        field(game, "dead").setBoolean(game, false)
        field(game, "jetGrace").setFloat(game, 0f)
        field(game, "runSkin").set(game, Skins.get(22))
        (field(game, "bubble").get(game) as Bubble).reset()
        val track = field(game, "track").get(game) as Track
        track.rows.clear()
        fun obstacle(x: Float) = Ob(Color.CYAN, x, .7f, .7f, ObType.SOLID, 1.4f, 1.4f, .9f)
        val middleCoin = Coin(0f, player.ground, 0f)
        val destinationCoin = Coin(Lanes.x(2), player.ground, 0f)
        val row = Row(0f, arrayListOf(obstacle(0f))).apply {
            coins = arrayListOf(middleCoin, destinationCoin)
        }
        track.rows.add(row)
        var hits = 0
        val observer = field(game, "testCrashObserver")
        observer.set(game, { hits++; Unit })
        val collide = CubeRun::class.java.getDeclaredMethod("collide", Float::class.javaPrimitiveType).apply { isAccessible = true }
        try {
            player.moveToLane(0)
            player.moveToLane(2)
            collide.invoke(game, 0f)
            assertEquals("No collision while crossing the middle lane", 0, hits)
            assertFalse("No pickup along the skipped path", middleCoin.taken)
            assertTrue("Destination coin remains collectible", destinationCoin.taken)
            row.obs.clear(); row.obs.add(obstacle(Lanes.x(2)))
            collide.invoke(game, 0f)
            assertEquals("Zappy grants no collision immunity", 1, hits)
        } finally { observer.set(game, null) }
    }

    @Test fun previewCannotGrantTeleportAndEffectsClearOnShowcase() = withPlayer { _, player ->
        player.moveToLane(0)
        val effects = field(player, "zappyFx").get(player)!!
        val traces = field(effects, "traces").get(effects) as Array<*>
        fun active() = traces.count { field(it!!, "age").getFloat(it) < .18f }
        assertEquals(1, active())
        player.update(0f, 0f, 0f, 0f, false, 0f)
        player.update(0f, 0f, 0f, 0f, false, 0f)
        assertEquals("Pause freezes the effect clock", 1, active())
        player.showcase(0f, 0f)
        assertEquals("No electrical traces follow into the wardrobe", 0, active())
        player.zappyEnabled = false
        Stage.previewSkin = 22
        player.update(0f, 0f, 0f, 0f, false, 0f)
        val before = player.px
        assertTrue(player.moveToLane(2))
        assertEquals("Preview does not change equipped movement", before, player.px, 0f)
    }
}
