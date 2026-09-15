package cube.run.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.ScreenUtils
import cube.run.GameActivity
import cube.run.R
import cube.run.bot.LiveBotDriver.Companion.gl
import cube.run.core.Gdx3DGame
import cube.run.core.Stage
import cube.run.data.Progress
import org.junit.Assert.*
import org.junit.Test

class LaunchRendererTest {
    @Test fun startingCubeFitsAndroidsCircularIconMask() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cube = context.getDrawable(R.drawable.launch_cube)!!
        val bitmap = Bitmap.createBitmap(288, 288, Bitmap.Config.ARGB_8888)
        try {
            cube.setBounds(0, 0, 288, 288)
            cube.draw(Canvas(bitmap))
            var visible = 0
            for (y in 0 until 288) for (x in 0 until 288) {
                if (bitmap.getPixel(x, y) ushr 24 > 8) {
                    visible++
                    val dx = x + .5f - 144; val dy = y + .5f - 144
                    assertTrue("Launch cube clipped by the 192dp circular mask", dx*dx + dy*dy <= 96*96)
                }
            }
            assertTrue("The system launch screen must contain a visible cube", visible > 8000)
        } finally { bitmap.recycle() }
    }

    @Test fun firstFrameDrawsEquippedCubeBeforeBatchesAndCanStartImmediately() {
        ActivityScenario.launch(GameActivity::class.java).use {
            gl { host ->
                val input = Gdx.input.inputProcessor
                val game = CubeRun(host.session, launchOpening = true)
                fun field(name: String) = Gdx3DGame::class.java.getDeclaredField(name).apply { isAccessible = true }.get(game)
                try {
                    game.create()
                    var reported = false
                    game.onFirstFrame = { reported = true }
                    assertNull("Scenery must not gate the first cube", field("world"))
                    assertNull("Particles must not gate the first cube", field("shards"))
                    game.render()
                    assertFalse("Do not uncover a buffer before its first swap", reported)
                    assertNull(field("world"))
                    assertEquals(0f, game.time, 0f)
                    val pixels = ScreenUtils.getFrameBufferPixels(game.sw/2-16, game.sh/2-16, 32, 32, false)
                    assertTrue("The first GL frame must contain cube pixels", pixels.indices.any { i ->
                        i % 4 == 0 && (pixels[i].toInt() and 255) > 40
                    })
                    val player = CubeRun::class.java.getDeclaredField("player").apply { isAccessible = true }.get(game) as Player
                    assertEquals("The real cube must use the equipped skin", Progress.skin, player.skin.id)
                    game.render()
                    assertTrue(reported)
                    game.onTap(0f, 0f)
                    assertNotNull("Immediate start must finish particle preparation", field("shards"))
                    game.render()
                } finally {
                    game.dispose()
                    Gdx.input.inputProcessor = input
                    Stage.reset()
                }
            }
        }
    }

    @Test fun rendererCanBeDisposedBeforeOrJustAfterTheFirstCubeFrame() {
        ActivityScenario.launch(GameActivity::class.java).use {
            gl { host ->
                val input = Gdx.input.inputProcessor
                try {
                    for (drawCube in listOf(false, true)) {
                        val game = CubeRun(host.session, launchOpening = true)
                        try {
                            game.create()
                            if (drawCube) game.render()
                            game.pause()
                            game.resume()
                        } finally { game.dispose() }
                    }
                } finally {
                    Gdx.input.inputProcessor = input
                    Stage.reset()
                }
            }
        }
    }
}
