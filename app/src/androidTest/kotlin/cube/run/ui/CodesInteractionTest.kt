package cube.run.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.GameActivity
import cube.run.R
import cube.run.data.Progress
import org.junit.Assert.*
import org.junit.Test

class CodesInteractionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun awaitNode(text: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            val nodes = instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text).orEmpty()
            nodes.firstOrNull()?.let { return it }
            SystemClock.sleep(20)
        }
        error("Visible code control not found: $text")
    }

    @Test fun settingsCodeEntryAwardsOnceAndShowsTheDuplicateResult() {
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val saved = prefs.all
        prefs.edit().clear().commit()
        Progress.init(context)
        try {
            ActivityScenario.launch<GameActivity>(Intent(context, GameActivity::class.java).putExtra(Hud.EXTRA_AUTOSTART, false)).use { scenario ->
                var title = ""; var entry = ""; var redeem = ""; var success = ""; var duplicate = ""
                scenario.onActivity { activity ->
                    title = activity.getString(R.string.settings_title); entry = activity.getString(R.string.codes_enter)
                    redeem = activity.getString(R.string.codes_redeem); success = activity.getString(R.string.codes_coins, 500)
                    duplicate = activity.getString(R.string.codes_used)
                    val root = activity.findViewById<View>(android.R.id.content)
                    all(root).single { it is CandyChip && it.isShown && it.contentDescription == title }.performClick()
                    all(root).single { it.tag == "settings_codes" && it.isShown }.performClick()
                }
                val input = awaitNode(entry)
                assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "CoIn500")
                }))
                assertTrue(awaitNode(redeem).performAction(AccessibilityNodeInfo.ACTION_CLICK))
                awaitNode(success)
                assertEquals(500, Progress.coins)
                assertTrue(awaitNode(redeem).performAction(AccessibilityNodeInfo.ACTION_CLICK))
                awaitNode(duplicate)
                assertEquals("The visible form cannot pay twice", 500, Progress.coins)
                if (InstrumentationRegistry.getArguments().getString("captureCodes") == "true") {
                    val bitmap = instrumentation.uiAutomation.takeScreenshot()
                    java.io.File(context.getExternalFilesDir(null), "codes-dialog.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        } finally {
            val edit = prefs.edit().clear()
            for ((key, value) in saved) when (value) {
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is String -> edit.putString(key, value)
                is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
            edit.commit(); Progress.init(context)
        }
    }
}
