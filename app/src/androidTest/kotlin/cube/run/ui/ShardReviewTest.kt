package cube.run.ui

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Skins
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Native layout checks; fixture counts never modify player progress. */
class ShardReviewTest {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun call(owner: Any, name: String) = owner.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(owner)
    @Test fun progressAndUnlockUseTheSameButtonWithoutCoveringTheCube() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val capture = InstrumentationRegistry.getArguments().getString("captureShards") == "true"
        val out = File(instrumentation.targetContext.getExternalFilesDir(null), "shard-review").apply { mkdirs() }
        try {
            ActivityScenario.launch(GameActivity::class.java).use { scenario ->
                scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
                SystemClock.sleep(1000)
                var baseline = 0
                for (id in listOf(0,20,21,22,19,13)) {
                    val counts = if (id in 20..22) listOf(0,33,99,100) else listOf(0)
                    for (have in counts) {
                        lateinit var page: WardrobeView
                        lateinit var root: FrameLayout
                        lateinit var action: CandyButton
                        scenario.onActivity { activity ->
                            Stage.paused = false
                            (field(activity,"hud").get(activity) as Hud).apply {
                                for (i in 0 until childCount) getChildAt(i).visibility = View.INVISIBLE
                            }
                            root = activity.findViewById(android.R.id.content)
                            page = WardrobeView(activity,UiKit(activity)) {}
                            field(page,"index").setInt(page,id); call(page,"applyPreview"); call(page,"render")
                            action = field(page,"action").get(page) as CandyButton
                            if (id in 20..22) (field(page,"shardDisplay").get(page) as ShardDisplay).bind(action,Skins.get(id),have)
                            action.isEnabled = false
                            root.addView(page,FrameLayout.LayoutParams(-1,-1))
                        }
                        SystemClock.sleep(if (capture) 900 else 360)
                        scenario.onActivity {
                            assertEquals(View.VISIBLE,action.visibility)
                            if (baseline == 0) baseline = action.height
                            assertEquals("Every action retains its full height",baseline,action.height)
                            assertTrue("Button fits on screen",action.width <= page.width)
                            val layout = action.layout
                            for (i in 0 until layout.lineCount) assertEquals("Label fits without truncation",0,layout.getEllipsisCount(i))
                            if (id in 20..22) {
                                assertEquals(2,action.lineCount)
                                assertTrue(action.text.toString().contains(if(have>=100) "UNLOCK" else "$have/100"))
                                assertTrue(action.contentDescription.contains(Skins.get(id).name))
                            }
                        }
                        if (capture) {
                            val bitmap = instrumentation.uiAutomation.takeScreenshot()
                            File(out,"cube-$id-$have.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                            bitmap.recycle()
                        }
                        scenario.onActivity { root.removeView(page) }
                    }
                }
                // Exercise all bubble shaders and the transitions on the real GL renderer.
                lateinit var page: WardrobeView
                scenario.onActivity { activity ->
                    page = WardrobeView(activity,UiKit(activity)) {}
                    field(page,"cat").setInt(page,1)
                    activity.addContentView(page,FrameLayout.LayoutParams(-1,-1))
                }
                for(id in 0..9) {
                    scenario.onActivity { field(page,"index").setInt(page,id); call(page,"applyPreview"); call(page,"render") }
                    SystemClock.sleep(800)
                    cube.run.bot.LiveBotDriver.gl { game ->
                        val bubble = field(game,"bubble").get(game) as cube.run.game.Bubble
                        val shown = field(bubble,"shown").get(bubble) as FloatArray
                        val wanted = cube.run.data.BubbleSkins.get(id)
                        fun difference(a: Float,b: Float) = kotlin.math.abs(((a-b)%360f+540f)%360f-180f)
                        assertEquals("The displayed film reaches the selected hue",0f,difference(shown[0],wanted.hue),1f)
                        assertEquals("Both ends of the film keep the intended hue range",0f,difference(shown[1],wanted.hue2),1f)
                        assertTrue("No full-wheel hue wrap",kotlin.math.abs(shown[1]-shown[0]) <= 180f)
                    }
                    if(capture) {
                        val bitmap = instrumentation.uiAutomation.takeScreenshot()
                        File(out,"bubble-$id.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
                    }
                }
            }
        } finally { Stage.paused = false; Stage.mode = Stage.NONE; Stage.clearPreview() }
    }
}
