package cube.run.game

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import cube.run.GameActivity
import cube.run.core.Gdx3DGame
import cube.run.core.Stage
import cube.run.core.gfx.ShardSystem
import cube.run.data.Progress
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
class SmashRegressionTest {
    private fun field(type: Class<*>, name: String) = type.getDeclaredField(name).apply { isAccessible = true }

    private fun withGame(action: (CubeRun) -> Unit) {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val done = CountDownLatch(1)
            var failure: Throwable? = null
            Gdx.app.postRunnable {
                try {
                    Stage.paused = true
                    action(Gdx.app.applicationListener as CubeRun)
                } catch (t: Throwable) { failure = t }
                finally { done.countDown() }
            }
            assertTrue("GL test timed out", done.await(20, TimeUnit.SECONDS))
            failure?.let { throw it }
        }
        Stage.paused = false
    }

    private fun obstacle(type: Int) = Ob(Color.CYAN, 0f, 0.7f, 0.7f, type, 1.4f, 1.4f, 0.9f)

    @Test fun highSpeedCollisionConsumesOneReviveAndPreservesNonSolidsAndOutsideRows() = withGame { game ->
        Stage.paused = false
        game.onDown(360f, 760f)
        Stage.paused = true
        (field(CubeRun::class.java, "difficulty").get(game) as Difficulty).boostTo(1f)
        field(CubeRun::class.java, "runT").setFloat(game, 10f)
        (field(CubeRun::class.java, "bubble").get(game) as Bubble).timer.stop()
        val track = field(CubeRun::class.java, "track").get(game) as Track
        track.rows.clear()
        val hit = Row(0f, arrayListOf(obstacle(ObType.SOLID)))
        val ahead = Row(-70f, arrayListOf(obstacle(ObType.SOLID), obstacle(ObType.DECO), obstacle(ObType.PAD), obstacle(ObType.PLAT)))
        val outside = Row(-80f, arrayListOf(obstacle(ObType.SOLID)))
        val behind = Row(2f, arrayListOf(obstacle(ObType.SOLID)))
        track.rows.addAll(listOf(hit, ahead, outside, behind))
        val revives = Progress.revives
        try {
            field(Progress::class.java, "revives").setInt(Progress, 2)
            game.tick(0f) // actual collision -> crash -> useRevive -> Second Wind
            assertEquals(1, Progress.revives)
            assertTrue(hit.obs.isEmpty())
            assertEquals(listOf(ObType.DECO, ObType.PAD, ObType.PLAT), ahead.obs.map { it.type })
            assertEquals(1, outside.obs.size)
            assertEquals(1, behind.obs.size)
            assertFalse(field(CubeRun::class.java, "dead").getBoolean(game))
            assertEquals(3f, (field(CubeRun::class.java, "bubble").get(game) as Bubble).timer.left, 0.001f)
        } finally {
            field(Progress::class.java, "revives").setInt(Progress, revives)
            // Restore through Progress's actual preferences rather than assuming its file name.
            val prefs = field(Progress::class.java, "prefs").get(Progress) as android.content.SharedPreferences
            assertTrue(prefs.edit().putInt("revives", revives).commit())
        }
    }

    @Test fun oversizedAndRepeatedBurstsStayBoundedAndReturnAllParticlesToPool() = withGame { game ->
        val shards = field(Gdx3DGame::class.java, "shards").get(game) as ShardSystem
        repeat(20) { game.burst3d(Vector3(), Color.CYAN, n = 100_000) }
        assertEquals(240, shards.count)
        shards.update(3f)
        assertEquals(0, shards.count)
        repeat(8) { game.burst3d(Vector3(), Color.WHITE, n = 30) }
        assertEquals(240, shards.count)
        shards.update(3f)
        assertEquals(0, shards.count)
    }
}
