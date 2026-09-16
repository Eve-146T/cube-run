package cube.run.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import cube.run.GameActivity
import cube.run.R
import cube.run.data.Skins
import cube.run.data.Worlds
import cube.run.intro.LaunchAppearance
import cube.run.intro.LaunchThemes
import cube.run.intro.NativeCubeView
import cube.run.intro.OpeningClock
import cube.run.ui.Hud
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

    @Test fun menuControlsStayInPlaceThroughoutTheVisibleOpening() {
        val settled = CountDownLatch(1)
        val positions = mutableMapOf<View, Pair<Int, Int>>()
        val errors = mutableListOf<String>()
        var samples = 0
        var finishedAt = 0L
        // ActivityScenario.onActivity may arrive after startup animations have
        // finished. Observe creation before launch, so no entrance frames are missed.
        val callback = ActivityLifecycleCallback { activity, state ->
            if (activity is GameActivity && state == Stage.CREATED) {
                val decor = activity.window.decorView
                decor.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        fun visit(view: View, visible: Float) {
                            if (view.visibility != View.VISIBLE) return
                            val alpha = visible*view.alpha
                            if (alpha > .1f && (view is ViewGroup || view.isClickable && view.contentDescription != null)) {
                                val xy = IntArray(2); view.getLocationOnScreen(xy)
                                val previous = positions.putIfAbsent(view, xy[0] to xy[1])
                                if (previous != null && previous != (xy[0] to xy[1])) errors.add("${view.contentDescription ?: view.javaClass.simpleName}: $previous -> ${xy.toList()}")
                            }
                            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i), alpha)
                        }
                        fun findHud(view: View): Hud? {
                            if (view is Hud) return view
                            if (view is ViewGroup) for (i in 0 until view.childCount) findHud(view.getChildAt(i))?.let { return it }
                            return null
                        }
                        val hud = findHud(decor)
                        if (hud != null && hud.alpha > .1f) {
                            samples++
                            visit(hud, 1f)
                            if (hud.alpha == 1f) {
                                if (finishedAt == 0L) finishedAt = SystemClock.uptimeMillis()
                                if (SystemClock.uptimeMillis()-finishedAt >= 450) {
                                    decor.viewTreeObserver.removeOnPreDrawListener(this)
                                    settled.countDown()
                                }
                            }
                        }
                        return true
                    }
                })
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        instrumentation.runOnMainSync { monitor.addLifecycleCallback(callback) }
        try {
            ActivityScenario.launch(GameActivity::class.java).use {
                assertTrue("Opening did not settle", settled.await(6, TimeUnit.SECONDS))
                assertTrue("Need frames throughout the fade: $samples", samples >= 5)
                assertTrue("Menu controls not observed", positions.size >= 4)
                assertTrue("Buttons shifted during entrance: $errors", errors.isEmpty())
            }
        } finally {
            instrumentation.runOnMainSync { monitor.removeLifecycleCallback(callback) }
        }
    }
}
