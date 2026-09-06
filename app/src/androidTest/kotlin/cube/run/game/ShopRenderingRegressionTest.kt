package cube.run.game

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.ScreenUtils
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.core.Gdx3DGame
import cube.run.data.Settings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real framebuffer checks in the bright desert palette where the navigation artifacts stood out. */
@RunWith(AndroidJUnit4::class)
class ShopRenderingRegressionTest {
    private lateinit var scenario: ActivityScenario<GameActivity>
    private var previousWorld = -1

    @Before fun launchDunes() {
        previousWorld = Settings.testWorld
        val intent = Intent(ApplicationProvider.getApplicationContext(), GameActivity::class.java).putExtra("world", 4)
        scenario = ActivityScenario.launch(intent)
        SystemClock.sleep(800)
    }

    @After fun finish() {
        Stage.paused = false
        Stage.mode = Stage.NONE
        Stage.shopProgress = 0f
        scenario.close()
        Settings.testWorld = previousWorld
    }

    private fun gl(action: (CubeRun) -> Unit) {
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        Gdx.app.postRunnable {
            try { action(Gdx.app.applicationListener as CubeRun) }
            catch (t: Throwable) { failure = t }
            finally { done.countDown() }
        }
        assertTrue("GL rendering timed out", done.await(10, TimeUnit.SECONDS))
        failure?.let { throw it }
    }

    @Test fun returningFromShopDoesNotRestartTheDesertSkyFade() = gl { game ->
        Stage.paused = true // compare exact handoff frames with no time/lighting changes
        val top = Color(game.bgTop)
        val bottom = Color(game.bgBottom)
        Stage.mode = Stage.SHOP
        Stage.shopProgress = 1f
        game.render()
        Stage.shopProgress = 0f
        game.render()
        assertColor(top, game.bgTop)
        assertColor(bottom, game.bgBottom)
        Stage.mode = Stage.NONE
        game.render()
        assertColor(top, game.bgTop)
        assertColor(bottom, game.bgBottom)
    }

    @Test fun glowKeepsItsLivePulseThroughoutReturnAndMenuHandoff() = gl { game ->
        Stage.paused = true
        Stage.mode = Stage.SHOP
        Stage.shopProgress = 1f
        game.render()
        val player = game.javaClass.getDeclaredField("player").apply { isAccessible = true }.get(game)
        val shell = player.javaClass.getDeclaredField("shellInst").apply { isAccessible = true }.get(player) as ModelInstance
        val clock = Gdx3DGame::class.java.getDeclaredField("time").apply { isAccessible = true }
        val initialTime = game.time
        for (elapsed in listOf(0.19f, 0.43f, 0.67f)) {
            clock.setFloat(game, initialTime + elapsed)
            Stage.shopProgress = 1f
            game.render()
            val liveSize = shell.transform.getScale(Vector3()).x
            for (progress in listOf(0.8f, 0.4f, 0.05f, 0f)) {
                Stage.shopProgress = progress
                game.render()
                assertEquals("Glow froze toward its captured size at progress $progress", liveSize, shell.transform.getScale(Vector3()).x, 0.0001f)
            }
        }
        val beforeHandoff = shell.transform.getScale(Vector3()).x
        Stage.mode = Stage.NONE
        game.render()
        assertEquals("Glow size reset when idle resumed", beforeHandoff, shell.transform.getScale(Vector3()).x, 0.0001f)
    }

    @Test fun shopGesturesBounceAndSpinWithoutChangingProgress() = gl { game ->
        Stage.paused = true
        Stage.mode = Stage.SHOP
        Stage.shopProgress = 1f
        game.render()
        val coins = cube.run.data.Progress.coins
        val bubbles = cube.run.data.Progress.bubbles
        val player = game.javaClass.getDeclaredField("player").apply { isAccessible = true }.get(game) as Player
        val body = player.javaClass.getDeclaredField("inst").apply { isAccessible = true }.get(player) as ModelInstance
        val restingY = player.py
        Stage.shopPlayRequests.set(Stage.SHOP_TAP)
        game.tick(0.05f)
        assertTrue("Tap should lift the cube", player.py > restingY + 0.05f)
        var yaw = body.transform.getRotation(com.badlogic.gdx.math.Quaternion(), true).yaw
        Stage.shopPlayRequests.set(Stage.SHOP_LEFT)
        game.tick(0.03f)
        var next = body.transform.getRotation(com.badlogic.gdx.math.Quaternion(), true).yaw
        assertTrue("Left swipe should fling left", (next - yaw + 540f) % 360f - 180f < 0f)
        yaw = next
        Stage.shopPlayRequests.set(Stage.SHOP_RIGHT)
        game.tick(0.03f)
        next = body.transform.getRotation(com.badlogic.gdx.math.Quaternion(), true).yaw
        assertTrue("Right swipe should fling right", (next - yaw + 540f) % 360f - 180f > 0f)
        repeat(120) { game.tick(1f / 60f) }
        assertEquals("The cube should settle back into its showroom", restingY, player.py, 0.001f)
        assertEquals(coins, cube.run.data.Progress.coins)
        assertEquals(bubbles, cube.run.data.Progress.bubbles)
        assertEquals(0, game.session.score)
    }

    @Test fun raysBelowTheCubeDoNotAppearSuddenlyWhenTheLastRoadTileDisappears() = gl { game ->
        Stage.paused = true
        Stage.mode = Stage.SHOP
        val density = Gdx.graphics.density
        val x = Gdx.graphics.width / 4
        val y = Gdx.graphics.height - (240f * density).toInt()
        val width = Gdx.graphics.width / 2
        val height = (30f * density).toInt()
        // This strip is below the cube and above the shop sheet, across the formerly clipped rays.
        Stage.shopProgress = 0.9999f
        game.render()
        val before = ScreenUtils.getFrameBufferPixels(x, y, width, height, false)
        Stage.shopProgress = 1f
        game.render()
        val after = ScreenUtils.getFrameBufferPixels(x, y, width, height, false)
        var difference = 0L
        for (i in before.indices) if (i % 4 != 3) {
            difference += abs((before[i].toInt() and 255) - (after[i].toInt() and 255))
        }
        val mean = difference.toFloat() / (width * height * 3)
        assertTrue("Final-frame ray reveal changed the strip by $mean brightness levels", mean < 3f)
    }

    private fun assertColor(expected: Color, actual: Color) {
        assertEquals("red channel", expected.r, actual.r, 0.001f)
        assertEquals("green channel", expected.g, actual.g, 0.001f)
        assertEquals("blue channel", expected.b, actual.b, 0.001f)
    }
}
