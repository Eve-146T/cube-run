package cube.run.game

import android.content.Intent
import android.graphics.Insets
import android.os.Build
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import cube.run.GameActivity
import cube.run.SplitWindowProbeActivity
import cube.run.ui.Hud
import cube.run.ui.MainMenu
import cube.run.ui.UiKit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class WindowGeometryTest {
    @Test fun transientSystemBarsKeepTheFloatingTitleAndBothCountersInPlace() {
        assumeTrue(Build.VERSION.SDK_INT >= 30)
        val intent = Intent(ApplicationProvider.getApplicationContext(), GameActivity::class.java)
            .putExtra(Hud.EXTRA_AUTOSTART, false)
        ActivityScenario.launch<GameActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as FrameLayout
                val hud = root.getChildAt(1) as Hud
                val menu = (0 until hud.childCount).map(hud::getChildAt).filterIsInstance<MainMenu>().single()
                menu.getChildAt(2).visibility = View.VISIBLE
                val hidden = WindowInsets.Builder()
                    .setDisplayCutout(android.view.DisplayCutout(android.graphics.Rect(0, 53, 0, 0),
                        listOf(android.graphics.Rect(230, 0, 490, 53))))
                    .setInsets(WindowInsets.Type.systemGestures(), Insets.of(60, 77, 60, 64))
                    .setVisible(WindowInsets.Type.systemBars(), false).build()
                fun anchors(insets: WindowInsets): List<List<Int>> {
                    root.dispatchApplyWindowInsets(insets)
                    root.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1520, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, 720, 1520)
                    return (0..2).map { index ->
                        val view = menu.getChildAt(index)
                        val at = IntArray(2); view.getLocationInWindow(at)
                        listOf(at[0], at[1], view.width, view.height)
                    }
                }
                val baseline = anchors(hidden)
                val shown = WindowInsets.Builder(hidden)
                    .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 56, 0, 0))
                    .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 24))
                    .setVisible(WindowInsets.Type.systemBars(), true).build()
                assertEquals("Overview swipe must not move the title, coins or bubbles", baseline, anchors(shown))
                assertEquals("Hiding bars must preserve those same anchors", baseline, anchors(hidden))
            }
        }
    }

    @Test fun shortMenusKeepTheLogoPromptAndControlsSeparateAndExpandAgain() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val kit = UiKit(activity)
                val menu = MainMenu(activity, kit, {}, {}, {}, {}, openingEntrance = true)
                menu.setBest(12)
                for ((width, height) in listOf(360f to 347f, 320f to 426f, 360f to 720f)) {
                    val w = kit.dp(width); val h = kit.dp(height)
                    menu.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                    menu.layout(0, 0, w, h)
                    val title = menu.getChildAt(0)
                    val prompt = menu.getChildAt(3)
                    val utilities = menu.getChildAt(4)
                    val actions = menu.getChildAt(5)
                    assertTrue("Logo and start prompt overlap at ${width}x$height: ${title.bottom} > ${prompt.top}", title.bottom <= prompt.top)
                    assertTrue("Start prompt overlaps controls at ${width}x$height",
                        prompt.bottom <= minOf(utilities.top, actions.top))
                    assertTrue(utilities.bottom <= h)
                    assertTrue(actions.bottom <= h)
                    val logo = (title as android.view.ViewGroup).getChildAt(0) as android.view.ViewGroup
                    val word = logo.getChildAt(0) as android.view.ViewGroup
                    val size = (word.getChildAt(0) as android.widget.TextView).textSize / activity.resources.displayMetrics.scaledDensity
                    if (height >= 500f) assertEquals("Full-screen branding must recover after resizing", 62f, size, .01f)
                    else assertTrue("Short panes must fit the brand above the prompt", size in 20f..46f)
                }
            }
        }
    }

    @Test fun aPermanentNavigationBarDoesNotCoverTheGameAndHidingItRestoresTheViewport() {
        assumeTrue(Build.VERSION.SDK_INT >= 30)
        val intent = Intent(ApplicationProvider.getApplicationContext(), SplitWindowProbeActivity::class.java)
            .putExtra(Hud.EXTRA_AUTOSTART, false)
        ActivityScenario.launch<SplitWindowProbeActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as FrameLayout
                val surface = root.getChildAt(0) as SurfaceView
                val hud = root.getChildAt(1) as Hud
                fun layout() {
                    root.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, 240, 320)
                }
                val visible = WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 30))
                    .setVisible(WindowInsets.Type.navigationBars(), true)
                    .build()
                root.dispatchApplyWindowInsets(visible)
                layout()
                assertEquals("The reported 30px bar must reserve usable window space", 30, root.paddingBottom)
                assertEquals(290, surface.height)
                assertEquals("GL and HUD must share the usable viewport", surface.height, hud.height)
                assertEquals(290, hud.bottom)

                // A visible split-screen status bar must not leave a blank scene strip.
                root.dispatchApplyWindowInsets(WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 24, 0, 0))
                    .setVisible(WindowInsets.Type.statusBars(), true).build())
                layout()
                assertEquals("Scene must extend behind status icons", 0, surface.top)
                assertEquals(320, surface.height)
                assertEquals("Controls must remain below the status bar", 24, hud.top)
                assertEquals(296, hud.height)

                // Pixel-style gesture navigation is an overlay, including the
                // pill revealed during the swipe into overview.
                root.dispatchApplyWindowInsets(WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 24))
                    .setInsets(WindowInsets.Type.systemGestures(), Insets.of(16, 0, 16, 24))
                    .setInsets(WindowInsets.Type.tappableElement(), Insets.NONE)
                    .setVisible(WindowInsets.Type.navigationBars(), true).build())
                layout()
                assertEquals("Gesture pill must overlay the scene", 0, root.paddingBottom)
                assertEquals(320, surface.height)
                assertEquals(320, hud.height)

                // A split-screen caption and side navigation bar must also be respected.
                root.dispatchApplyWindowInsets(WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.navigationBars(), Insets.of(20, 0, 0, 0))
                    .setInsets(WindowInsets.Type.captionBar(), Insets.of(0, 24, 0, 0))
                    .setVisible(WindowInsets.Type.systemBars(), true).build())
                layout()
                assertEquals(20, surface.left)
                assertEquals(24, surface.top)
                assertEquals(220, surface.width)
                assertEquals(296, surface.height)

                root.dispatchApplyWindowInsets(WindowInsets.Builder(visible)
                    .setInsets(WindowInsets.Type.navigationBars(), Insets.NONE)
                    .setVisible(WindowInsets.Type.navigationBars(), false).build())
                layout()
                assertEquals(0, root.paddingBottom)
                assertEquals(240, surface.width)
                assertEquals(320, surface.height)
                assertEquals(surface.height, hud.height)
            }
        }
    }
}
