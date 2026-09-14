package cube.run.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
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

/** Run explicitly with -e captureAbilities true to export the native design alternatives. */
class AbilityReviewTest {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun call(owner: Any, name: String) = owner.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(owner)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val out get() = File(instrumentation.targetContext.getExternalFilesDir(null), "ability-review").apply { mkdirs() }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(400)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun clickDescription(view: View, text: String): Boolean {
        if (view.contentDescription?.toString() == text) { view.performClick(); return true }
        if (view is ViewGroup) for (i in 0 until view.childCount) if (clickDescription(view.getChildAt(i), text)) return true
        return false
    }

    @Test fun captureEightNativeLayouts() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureAbilities") == "true")
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            SystemClock.sleep(800)
            val styles = InstrumentationRegistry.getArguments().getString("abilityStyle")?.toIntOrNull()
                ?.let { listOf(it.coerceIn(0, 7)) } ?: (0..7).toList()
            for (subject in listOf("ghost", "bubblegum", "multiple")) for (style in styles) {
                lateinit var page: WardrobeView
                lateinit var display: AbilityDisplay
                lateinit var root: FrameLayout
                scenario.onActivity { activity ->
                    (field(activity, "hud").get(activity) as Hud).apply {
                            // Retain Hud's full-window alpha composition over SurfaceView.
                            for (i in 0 until childCount) getChildAt(i).visibility = View.INVISIBLE
                        }
                    root = activity.findViewById(android.R.id.content)
                    page = WardrobeView(activity, UiKit(activity), abilityStyle = style) {}
                    field(page, "index").setInt(page, if (subject == "bubblegum") 16 else 13)
                    call(page, "applyPreview"); call(page, "render")
                    root.addView(page, FrameLayout.LayoutParams(-1, -1))
                    display = field(page, "abilityDisplay").get(page) as AbilityDisplay
                    if (subject == "multiple") display.bind(listOf(Skins.Ability.PHASE, Skins.Ability.BUBBLE_SAVER), "multi-review")
                }
                SystemClock.sleep(750)
                // Settle the preview and freeze its pose for equivalent comparisons.
                val settled = CountDownLatch(1)
                Gdx.app.postRunnable { Stage.paused = true; settled.countDown() }
                assertTrue(settled.await(10, TimeUnit.SECONDS))
                capture("$subject-$style-closed")
                if (style in listOf(0, 1, 4, 6)) {
                    scenario.onActivity {
                        val method = AbilityDisplay::class.java.getDeclaredMethod("toggle", Int::class.javaPrimitiveType).apply { isAccessible = true }
                        if (style == 0) assertTrue(clickDescription(display.floating, "Show ${if (subject == "bubblegum") "Bubble saver" else "Phase"} ability"))
                        else method.invoke(display, 0)
                    }
                    capture("$subject-$style-open")
                    if (subject == "multiple" && style != 6) {
                        scenario.onActivity {
                            val method = AbilityDisplay::class.java.getDeclaredMethod("toggle", Int::class.javaPrimitiveType).apply { isAccessible = true }
                            method.invoke(display, 1)
                        }
                        capture("$subject-$style-second")
                    }
                    scenario.onActivity {
                        // Changing cosmetics must close the old explanation and show the new abilities.
                        display.bind(listOf(Skins.Ability.SPEED), "changed-cosmetic")
                        assertEquals(-1, field(display, "selected").getInt(display))
                    }
                }
                scenario.onActivity { root.removeView(page) }
            }
            scenario.onActivity { activity ->
                Stage.paused = false
                val hud = field(activity, "hud").get(activity) as Hud
                hud.visibility = View.VISIBLE
                call(hud, "openShop")
            }
            SystemClock.sleep(900)
            capture("consumables")
            scenario.onActivity { activity ->
                for ((name, icon) in listOf("magnet" to MagnetIcon(), "heart" to HeartIcon())) {
                    val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
                    icon.setBounds(0, 0, 192, 192); icon.draw(Canvas(bitmap))
                    File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        }
        Stage.paused = false; Stage.mode = Stage.NONE; Stage.clearPreview()
    }
}
