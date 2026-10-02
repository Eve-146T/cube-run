package cube.run.game

import android.content.Intent
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import cube.run.KeypadWindowProbeActivity
import cube.run.ui.Hud
import org.junit.Assert.*
import org.junit.Test

class KeypadWindowTest {
    @Test fun keypadFrameworkDoesNotAddItsMenuBarAtLaunchOrRecreation() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), KeypadWindowProbeActivity::class.java)
            .putExtra(Hud.EXTRA_AUTOSTART, false)
        ActivityScenario.launch<KeypadWindowProbeActivity>(intent).use { scenario ->
            fun checkWindow() = scenario.onActivity { activity ->
                val decor = activity.window.decorView as ViewGroup
                assertNull("TCL's Activity content setter adds a 30px bar outside system insets",
                    decor.findViewWithTag<View>(KeypadWindowProbeActivity.MENU_BAR_TAG))
                val root = activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as FrameLayout
                val surface = root.getChildAt(0) as SurfaceView
                val hud = root.getChildAt(1) as Hud
                assertTrue(surface.height > 0)
                assertEquals("GL and controls must occupy the same window", surface.height, hud.height)
                assertEquals(root.height - root.paddingBottom, surface.bottom)
                // The Window still owns the installed content and dispatches focus/input.
                assertTrue(activity.hasWindowFocus())
                for (key in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)) {
                    assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key)))
                    assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key)))
                }
            }
            checkWindow()
            scenario.recreate()
            checkWindow()
        }
    }
}
