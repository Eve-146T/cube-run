package cube.run.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.R
import cube.run.data.Skins
import cube.run.data.Worlds
import cube.run.intro.LaunchAppearance
import cube.run.intro.LaunchThemes
import cube.run.intro.NativeCubeView
import cube.run.intro.OpeningClock
import org.junit.Assert.*
import org.junit.Test

class LaunchHandoffTest {
    @Test fun everySavedPaletteMatchesTheNativeCubeBeforeTheCoverLeaves() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val density = context.resources.displayMetrics.density
        val width = (392*density).toInt(); val height = (850*density).toInt()
        val native = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val vector = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val cases = Skins.all.map { LaunchThemes.skins[it.id] to LaunchAppearance(it, Worlds.get(0).hue) } +
            Worlds.all.drop(1).map { LaunchThemes.worlds[it.id-1] to LaunchAppearance(Skins.get(0), it.hue) } +
            (R.style.GameTheme to LaunchAppearance.ROSE)
        try {
            instrumentation.runOnMainSync {
                for ((theme, appearance) in cases) {
                    val themed = ContextThemeWrapper(context, theme)
                    val drawable = themed.getDrawable(R.drawable.launch_cube_motion)!!
                    val canvas = Canvas(vector)
                    canvas.drawColor(cube.run.intro.OpeningPose.INK)
                    val size = (288*density).toInt()
                    drawable.setBounds((width-size)/2, (height-size)/2, (width+size)/2, (height+size)/2)
                    drawable.draw(canvas)
                    val clock = OpeningClock(appearance)
                    clock.adoptSystemStart(System.currentTimeMillis(), 0)
                    NativeCubeView(context, clock, appearance.skin, appearance.worldHue).apply {
                        secondsForTest = 0f
                        layout(0, 0, width, height)
                        draw(Canvas(native))
                    }
                    var error = 0L
                    val radius = (100*density).toInt()
                    for (y in height/2-radius until height/2+radius) for (x in width/2-radius until width/2+radius) {
                        val a = vector.getPixel(x,y); val b = native.getPixel(x,y)
                        for (shift in intArrayOf(0,8,16)) error += kotlin.math.abs((a shr shift and 255)-(b shr shift and 255))
                    }
                    val mean = error.toDouble()/(radius*radius*4*3)
                    assertTrue("System/native colour or geometry differs for skin ${appearance.skin.id}, hue ${appearance.worldHue}: $mean", mean < 2.5)
                }
            }
        } finally { native.recycle(); vector.recycle() }
    }
}
