package cube.run.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.ScreenUtils
import cube.run.GameActivity
import cube.run.bot.LiveBotDriver.Companion.gl
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Skins
import cube.run.intro.NativeCubeView
import cube.run.intro.OpeningClock
import cube.run.intro.OpeningPose
import org.junit.Assert.*
import org.junit.Test

class NativeOpeningTest {
    @Test fun loadingDoesNotRestartTheClockAndBackgroundTimeDoesNotAdvanceIt() {
        val clock = OpeningClock()
        clock.start()
        SystemClock.sleep(80)
        clock.start()
        assertTrue(clock.seconds() >= .07f)
        val before = clock.seconds()
        clock.adoptSystemStart(System.currentTimeMillis()-300)
        assertEquals("Adopting system rotation must not jump the camera", before, clock.seconds(), .02f)
        assertTrue(clock.leadInSeconds > .15f)
        clock.pause()
        val paused = clock.seconds()
        SystemClock.sleep(80)
        assertEquals(paused, clock.seconds(), .001f)
        clock.resume()
        assertEquals(paused, clock.seconds(), .02f)
        clock.finish()
        assertEquals(OpeningPose.DURATION, clock.seconds(), 0f)
    }

    @Test fun nativeAndGlCubeHaveMatchingSilhouettesAcrossTheShot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(GameActivity::class.java).use {
            for (seconds in listOf(0f, .25f, .8f, 1.5f)) {
                val captured = gl { host ->
                    val input = Gdx.input.inputProcessor
                    val game = CubeRun(host.session, launchOpening = true, firstWorld = 0)
                    try {
                        game.create()
                        val opening = CubeRun::class.java.getDeclaredField("opening").apply { isAccessible = true }.get(game) as CubeOpening
                        opening.tick(seconds)
                        game.render()
                        Triple(game.sw, game.sh, ScreenUtils.getFrameBufferPixels(true))
                    } finally {
                        game.dispose(); Gdx.input.inputProcessor = input; Stage.reset()
                    }
                }
                val (w, h, glPixels) = captured
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                try {
                    InstrumentationRegistry.getInstrumentation().runOnMainSync {
                        NativeCubeView(context, OpeningClock(), Skins.get(Progress.skin), cube.run.data.Worlds.get(0).hue).apply {
                            secondsForTest = seconds
                            layout(0, 0, w, h)
                            draw(Canvas(bitmap))
                        }
                    }
                    var overlap = 0; var union = 0
                    for (y in 0 until h) for (x in 0 until w) {
                        val i = (y*w+x)*4
                        fun lit(r: Int, g: Int, b: Int) = maxOf(kotlin.math.abs(r-20), kotlin.math.abs(g-16), kotlin.math.abs(b-46)) > 45
                        val glLit = lit(glPixels[i].toInt() and 255, glPixels[i+1].toInt() and 255, glPixels[i+2].toInt() and 255)
                        val native = bitmap.getPixel(x, y)
                        val nativeLit = lit(native shr 16 and 255, native shr 8 and 255, native and 255)
                        if (glLit || nativeLit) union++
                        if (glLit && nativeLit) overlap++
                    }
                    assertTrue("Visible cube expected at $seconds", union > 1000)
                    assertTrue("Native/GL silhouette mismatch at $seconds: $overlap / $union", overlap.toFloat()/union > .96f)
                } finally { bitmap.recycle() }
            }
        }
    }
}
