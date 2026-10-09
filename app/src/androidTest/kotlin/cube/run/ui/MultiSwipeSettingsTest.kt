package cube.run.ui

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.R
import cube.run.data.Settings
import cube.run.game.CubeRun
import org.junit.Assert.*
import org.junit.Test

class MultiSwipeSettingsTest {
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()

    @Test fun multiswipeTogglePersistsAndDrivesTheGame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val hadValue = prefs.contains("multi_swipe")
        val oldValue = prefs.getBoolean("multi_swipe", false)
        prefs.edit().remove("multi_swipe").commit(); Settings.init(context)
        try {
            assertFalse("Multiswipe is opt-in", Settings.multiSwipe)
            repeat(2) { launch ->
                ActivityScenario.launch<GameActivity>(Intent(context, GameActivity::class.java)
                    .putExtra(Hud.EXTRA_AUTOSTART, false)).use { scenario ->
                    scenario.onActivity { activity ->
                        val root = activity.findViewById<View>(android.R.id.content)
                        val title = activity.getString(R.string.settings_title)
                        all(root).single { it is CandyChip && it.isShown && it.contentDescription == title }.performClick()
                        val row = all(root).single { it.tag == "settings_multiswipe" && it.isShown }
                        assertEquals(launch == 1, Settings.multiSwipe)
                        assertEquals(Settings.multiSwipe, (Gdx.app.applicationListener as CubeRun).multiSwipeEnabled())
                        val before = row.createAccessibilityNodeInfo()
                        assertTrue(before.isCheckable); assertEquals(Settings.multiSwipe, before.isChecked)
                        assertTrue(row.performClick())
                        assertEquals(launch == 0, Settings.multiSwipe)
                        assertEquals(Settings.multiSwipe, (Gdx.app.applicationListener as CubeRun).multiSwipeEnabled())
                        assertEquals(Settings.multiSwipe, prefs.getBoolean("multi_swipe", false))
                        val after = row.createAccessibilityNodeInfo()
                        assertEquals(Settings.multiSwipe, after.isChecked)
                        assertTrue(row.contentDescription.contains(activity.getString(R.string.settings_multiswipe)))
                    }
                    instrumentation.waitForIdleSync()
                    if (launch == 0 && InstrumentationRegistry.getArguments().getString("captureMultiswipe") == "true") {
                        android.os.SystemClock.sleep(350)
                        val bitmap = instrumentation.uiAutomation.takeScreenshot()
                        java.io.File(context.getExternalFilesDir(null), "multiswipe-settings.png").outputStream().use {
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                        }
                        bitmap.recycle()
                    }
                }
                Settings.init(context)
                assertEquals(launch == 0, Settings.multiSwipe)
            }
        } finally {
            val edit = prefs.edit()
            if (hadValue) edit.putBoolean("multi_swipe", oldValue) else edit.remove("multi_swipe")
            edit.commit(); Settings.init(context)
        }
    }
}
