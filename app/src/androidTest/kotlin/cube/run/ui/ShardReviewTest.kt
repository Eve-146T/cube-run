package cube.run.ui

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Skins
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit native screenshot review. Fixtures change views only; player progress is untouched. */
class ShardReviewTest {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun call(owner: Any, name: String) = owner.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(owner)

    @Test fun captureThreeNativeLayouts() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureShards") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val out = File(instrumentation.targetContext.getExternalFilesDir(null), "shard-review").apply { mkdirs() }
        var actionHeight = -1
        try {
            ActivityScenario.launch(GameActivity::class.java).use { scenario ->
                scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
                SystemClock.sleep(800)
                for (style in 0..2) for (have in listOf(33, 100)) {
                    lateinit var page: WardrobeView
                    lateinit var root: FrameLayout
                    lateinit var action: CandyButton
                    scenario.onActivity { activity ->
                        Stage.paused = false
                        (field(activity, "hud").get(activity) as Hud).apply {
                            // Retain Hud's full-window alpha composition over SurfaceView.
                            for (i in 0 until childCount) getChildAt(i).visibility = View.INVISIBLE
                        }
                        root = activity.findViewById(android.R.id.content)
                        page = WardrobeView(activity, UiKit(activity), shardStyle = style) {}
                        field(page, "index").setInt(page, 21)
                        call(page, "applyPreview"); call(page, "render")
                        val display = field(page, "shardDisplay").get(page) as ShardDisplay
                        display.bind(Skins.get(21), have)
                        action = field(page, "action").get(page) as CandyButton
                        action.setLabel("UNLOCK")
                        action.color = if (have >= 100) Theme.hsv(195f, 0.7f, 1f) else Theme.alpha(Theme.MUTED, 200)
                        action.alpha = 1f
                        action.visibility = if (display.showAction) View.VISIBLE else View.INVISIBLE
                        action.isEnabled = false // Capture fixture cannot spend the phone's actual shards.
                        root.addView(page, FrameLayout.LayoutParams(-1, -1))
                    }
                    SystemClock.sleep(900)
                    val settled = CountDownLatch(1)
                    Gdx.app.postRunnable { Stage.paused = true; settled.countDown() }
                    assertTrue(settled.await(10, TimeUnit.SECONDS))
                    instrumentation.waitForIdleSync()
                    scenario.onActivity {
                        assertEquals("Unlock must remain one line", 1, action.lineCount)
                        if (actionHeight < 0) actionHeight = action.height
                        assertEquals("All shard variants retain the same action height", actionHeight, action.height)
                    }
                    scenario.onActivity {
                        fun describe(view: View): String {
                            if (view is android.widget.TextView && view.visibility == View.VISIBLE) {
                                assertEquals("Wardrobe text must fit: ${view.text}", 1, view.lineCount)
                            }
                            val info = if (view is android.widget.TextView) " text=${view.text} lines=${view.lineCount} layout=${view.layout?.width} scroll=${view.scrollX},${view.scrollY}" else ""
                            return "${view.javaClass.simpleName} ${view.width}x${view.height} at ${view.x},${view.y}$info\n" +
                                if (view is android.view.ViewGroup) (0 until view.childCount).joinToString("") { describe(view.getChildAt(it)) } else ""
                        }
                        File(out, "layout-$style-$have.txt").writeText(describe(page))
                    }
                    val bitmap = instrumentation.uiAutomation.takeScreenshot()
                    assertNotNull(bitmap)
                    File(out, "frost-$style-${if (have >= 100) "ready" else "progress"}.png")
                        .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    scenario.onActivity { root.removeView(page) }
                }
            }
        } finally {
            Stage.paused = false; Stage.mode = Stage.NONE; Stage.clearPreview()
        }
    }
}
