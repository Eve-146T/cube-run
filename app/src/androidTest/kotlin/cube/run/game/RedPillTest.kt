package cube.run.game

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.game.track.Pickup
import cube.run.game.track.Row
import cube.run.game.track.Track
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RedPillTest {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun gl(action: (CubeRun) -> Unit) {
        val done = CountDownLatch(1); var failure: Throwable? = null
        Gdx.app.postRunnable {
            try { action(Gdx.app.applicationListener as CubeRun) } catch (t: Throwable) { failure=t } finally { done.countDown() }
        }
        assertTrue(done.await(10, TimeUnit.SECONDS)); failure?.let { throw it }
    }

    @Test fun pickupBlendsInAndOutWithoutChangingItsDuration() {
        val pill = RedPill()
        pill.collect()
        assertTrue(pill.timer.active); assertEquals(0f, pill.blend, 0f)
        pill.tick(RedPill.ENTER/2f, true)
        assertEquals(.5f, pill.blend, .0001f)
        pill.tick(RedPill.ENTER/2f, true)
        assertEquals(1f, pill.blend, .0001f)
        repeat(1200) { pill.tick(.01f, true) }
        assertFalse(pill.timer.active)
        assertTrue(pill.blend > 0f && pill.blend < 1f)
        pill.tick(RedPill.LEAVE, true)
        assertEquals(0f, pill.blend, 0f)
    }

    @Test fun pauseHoldsBlendAndRecollectionDoesNotSnap() {
        val pill = RedPill(); pill.collect(); pill.tick(.3f, true)
        val blend = pill.blend; val left = pill.timer.left
        pill.tick(0f, true)
        assertEquals(blend, pill.blend, 0f); assertEquals(left, pill.timer.left, 0f)
        pill.collect(); assertEquals(blend, pill.blend, 0f)
        assertEquals(RedPill.DURATION, pill.timer.left, 0f)
        pill.tick(.1f, true); assertTrue(pill.blend > blend)
        val beforeDeath = pill.blend
        pill.tick(.05f, false)
        assertFalse(pill.timer.active); assertTrue(pill.blend < beforeDeath && pill.blend > 0f)
        pill.reset(); assertEquals(0f, pill.blend, 0f)
    }

    @Test fun realTrackPickupActivatesEffectAndBothRenderPathsDraw() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            SystemClock.sleep(800)
            gl { game ->
                Stage.mode=Stage.NONE; Stage.paused=false
                game.onTap(300f, 700f)
                Stage.paused=true
                val track = field(game,"track").get(game) as Track
                val player = field(game,"player").get(game) as Player
                track.rows.clear()
                val row = Row(-Row.PICKUP_DZ, arrayListOf()).apply { pickup=Pickup.RED_PILL; pickupX=player.px }
                track.rows.add(row)
                game.tick(0f)
                assertEquals(Pickup.NONE, row.pickup)
                val pill = field(game,"redPill").get(game) as RedPill
                assertTrue(pill.timer.active)
                pill.tick(RedPill.ENTER, true)
                game.tick(0f)
                assertEquals(0f, game.bgTop.r, .0001f)
                assertEquals(0f, game.bgTop.g, .0001f)
                assertEquals(0f, game.bgTop.b, .0001f)
                track.rows.add(Row(-8f, arrayListOf()).apply { pickup=Pickup.RED_PILL; pickupX=player.px; pop=1f; popStart=game.time-2f })
            }
            SystemClock.sleep(300)
            var instancedRenderer: Any? = null
            gl { game ->
                assertEquals("Instanced world and capsule must render cleanly", 0, Gdx.gl.glGetError())
                val base = game.javaClass.superclass
                val world = base.getDeclaredField("world").apply { isAccessible=true }.get(game)!!
                val wires = base.getDeclaredField("matrixWires").apply { isAccessible=true }.get(game)!!
                assertTrue("World edges are queued", field(wires,"used").getInt(wires) > 0)
                val capsules = base.getDeclaredField("capsules").apply { isAccessible=true }.get(game)!!
                assertTrue("Rounded capsule geometry is queued", field(capsules,"used").getInt(capsules) > 0)
                instancedRenderer = field(world,"instances").get(world)
                field(world,"instances").set(world,null)
            }
            SystemClock.sleep(300)
            gl { game ->
                assertEquals("GLES 2 geometry path must render cleanly", 0, Gdx.gl.glGetError())
                val world=game.javaClass.superclass.getDeclaredField("world").apply { isAccessible=true }.get(game)!!
                field(world,"instances").set(world,instancedRenderer)
            }
        }
        Stage.paused=false
    }
}
