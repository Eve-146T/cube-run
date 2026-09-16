package cube.run.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.ScreenUtils
import cube.run.GameActivity
import cube.run.bot.LiveBotDriver.Companion.gl
import cube.run.core.Stage
import cube.run.data.Skins
import cube.run.data.Worlds
import cube.run.intro.LaunchAppearance
import cube.run.intro.NativeCubeView
import cube.run.intro.OpeningClock
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchLightingTest {
    @Test fun ghostAndOpaqueCubesKeepTheirBrightnessWhenGlTakesOver() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val errors = mutableListOf<String>()
        ActivityScenario.launch(GameActivity::class.java).use {
            for (skinId in listOf(0, 13)) for (seconds in listOf(0f, .3f, .7f, 1.2f)) {
                val captured = gl { host ->
                    val input = Gdx.input.inputProcessor
                    val preview = Stage.previewSkin
                    val game = CubeRun(host.session, launchOpening = true, firstWorld = 0)
                    try {
                        game.create()
                        Stage.previewSkin = skinId
                        val player = CubeRun::class.java.getDeclaredField("player").apply { isAccessible = true }.get(game) as Player
                        player.update(0f, 0f, seconds, Worlds.get(0).hue, trail = false, groundH = 0f)
                        val opening = CubeRun::class.java.getDeclaredField("opening").apply { isAccessible = true }.get(game) as CubeOpening
                        opening.tick(seconds)
                        game.render()
                        Triple(game.sw, game.sh, ScreenUtils.getFrameBufferPixels(true))
                    } finally {
                        game.dispose(); Gdx.input.inputProcessor = input; Stage.reset(); Stage.previewSkin = preview
                    }
                }
                val (width, height, rgba) = captured
                val gpu = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val native = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    val colors = IntArray(width*height) { i ->
                        val j = i*4
                        (255 shl 24) or ((rgba[j].toInt() and 255) shl 16) or
                            ((rgba[j+1].toInt() and 255) shl 8) or (rgba[j+2].toInt() and 255)
                    }
                    gpu.setPixels(colors, 0, width, 0, 0, width, height)
                    val appearance = LaunchAppearance(Skins.get(skinId), Worlds.get(0).hue)
                    instrumentation.runOnMainSync {
                        NativeCubeView(context, OpeningClock(appearance), appearance.skin, appearance.worldHue).apply {
                            secondsForTest = seconds
                            layout(0, 0, width, height)
                            draw(Canvas(native))
                        }
                    }
                    for ((label, bitmap) in listOf("gl" to gpu, "native" to native)) {
                        File(context.getExternalFilesDir(null), "lighting-$skinId-$seconds-$label.png").outputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    }
                    fun lit(c: Int) = maxOf(c shr 16 and 255, c shr 8 and 255, c and 255) > 90
                    val nativePixels = IntArray(width*height)
                    native.getPixels(nativePixels, 0, width, 0, 0, width, height)
                    val offsets = intArrayOf(-2, 0, 2, -2*width, 2*width)
                    var count = 0; var difference = 0L
                    // Compare visible interiors; antialiasing at silhouette edges is renderer-specific.
                    for (y in 2 until height-2) for (x in 2 until width-2) {
                        val i = y*width+x
                        if (!offsets.all { d -> lit(nativePixels[i+d]) && lit(colors[i+d]) }) continue
                        val a = nativePixels[i]; val b = colors[i]
                        for (shift in intArrayOf(0,8,16)) difference += kotlin.math.abs((a shr shift and 255)-(b shr shift and 255))
                        count++
                    }
                    assertTrue("Cube interior must be visible", count > 1000)
                    val mean = difference.toDouble()/(count*3)
                    android.util.Log.i("LAUNCH_LIGHTING", "skin=$skinId seconds=$seconds meanRgbError=$mean pixels=$count")
                    if (mean >= 3) errors.add("skin=$skinId time=$seconds: mean RGB error $mean")
                } finally { native.recycle(); gpu.recycle() }
            }
        }
        assertTrue("Cube brightness changed at renderer handoff: $errors", errors.isEmpty())
    }
}
