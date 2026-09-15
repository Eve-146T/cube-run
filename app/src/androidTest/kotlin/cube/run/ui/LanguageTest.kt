package cube.run.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.GameActivity
import cube.run.R
import cube.run.core.Stage
import cube.run.data.Languages
import cube.run.data.Progress
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LanguageTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    // Language changes intentionally finish/relaunch to dispose libGDX resources.
    // Track the resumed instance rather than ActivityScenario's original instance.
    private fun onActivity(block: (GameActivity) -> Unit) {
        instrumentation.runOnMainSync {
            androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                .filterIsInstance<GameActivity>().firstOrNull()?.let(block)
        }
    }

    private fun tap(id: Int) = tapMatching { activity, view ->
        view.contentDescription == activity.getString(id)
    }

    /** Exercise Android hit testing, including every ancestor's bounds. */
    private fun tapMatching(matches: (GameActivity, View) -> Boolean) {
        val point = IntArray(2)
        await { activity ->
            val view = views(activity.window.decorView).firstOrNull {
                it.isShown && it.alpha == 1f && matches(activity, it)
            }
            if (view == null || view.width == 0 || view.translationY != 0f) false else {
                view.getLocationOnScreen(point)
                point[0] += view.width / 2; point[1] += view.height / 2
                var parent = view.parent
                while (parent is View) {
                    if (parent.alpha < 1f || parent.translationY != 0f) return@await false
                    val bounds = android.graphics.Rect()
                    parent.getGlobalVisibleRect(bounds)
                    assertTrue("Touch target outside ancestor bounds: ${view.contentDescription}", bounds.contains(point[0], point[1]))
                    parent = parent.parent
                }
                true
            }
        }
        val down = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point[0].toFloat(), point[1].toFloat(), 0)
            instrumentation.sendPointerSync(event)
            event.recycle()
            SystemClock.sleep(60)
        }
        instrumentation.waitForIdleSync()
    }

    private fun views(v: View): Sequence<View> = sequence {
        yield(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) yieldAll(views(v.getChildAt(i)))
    }

    private fun await(check: (GameActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        do {
            var ready = false
            onActivity { ready = check(it) }
            if (ready) return
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("Language UI did not settle")
    }

    private fun capture(name: String) {
        SystemClock.sleep(400)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "language-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun pickerSwitchesAllLanguagesAndPreservesProgressAcrossRecreation() {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val original = prefs.getString("language", null)
        try {
            Languages.select(context, "en")
            val intent = Intent(context, GameActivity::class.java).putExtra(Hud.EXTRA_AUTOSTART, false)
            ActivityScenario.launch<GameActivity>(intent).use { scenario ->
                await { a -> views(a.window.decorView).any { it is MainMenu && it.isShown } }
                val bank = Progress.coins
                val bubbles = Progress.bubbles
                onActivity { a -> views(a.window.decorView).filterIsInstance<MainMenu>().single().show() }
                repeat(9) {
                    SystemClock.sleep(70)
                    onActivity { a ->
                        val menu = views(a.window.decorView).filterIsInstance<MainMenu>().single()
                        val chips = views(menu).filterIsInstance<CandyChip>().toList()
                        val globe = chips.single { it.contentDescription == a.getString(R.string.cd_languages) }
                        for (chip in chips) {
                            assertEquals("Toolbar buttons must fade together", globe.alpha, chip.alpha, 0.02f)
                            assertEquals("Toolbar buttons must rise together", globe.translationY, chip.translationY, 1f)
                        }
                    }
                }
                capture("home-en")
                tap(R.string.cd_languages)
                for (code in listOf("en", "de", "he", "en")) {
                    if (code != Languages.current(context)) {
                        tapMatching { a, view ->
                            val option = Languages.options.single { it.code == code }
                            val description = a.getString(R.string.language_option, option.nativeName, a.getString(option.country))
                            view.contentDescription == description
                        }
                    }
                    await { a ->
                        a.resources.configuration.locales[0].language in (if (code == "he") listOf("he", "iw") else listOf(code)) &&
                            views(a.window.decorView).any { it is LanguageSheet && it.isShown && it.alpha == 1f }
                    }
                    onActivity { a ->
                        val sheet = views(a.window.decorView).filterIsInstance<LanguageSheet>().single()
                        assertEquals(if (code == "he") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR, sheet.layoutDirection)
                        val rows = views(sheet).filter { it.isClickable && it.contentDescription?.contains(",") == true }.toList()
                        assertEquals(3, rows.size)
                        assertEquals(1, rows.count { it.isSelected })
                        assertTrue(rows.single { it.isSelected }.contentDescription.startsWith(Languages.options.single { it.code == code }.nativeName))
                        assertFalse(Stage.homeScreen)
                        assertEquals(bank, Progress.coins)
                        assertEquals(bubbles, Progress.bubbles)
                        for (label in views(sheet).filterIsInstance<TextView>()) {
                            assertTrue("Label has no width: ${label.text}", label.width > 0)
                            val layout = label.layout
                            if (layout != null) for (line in 0 until layout.lineCount) {
                                assertEquals("Clipped label: ${label.text}", 0, layout.getEllipsisCount(line))
                                assertTrue("Overflowing label: ${label.text}", layout.getLineWidth(line) <= label.width + 1)
                            }
                        }
                    }
                    capture(code)
                }
                onActivity { it.changeLanguage("he") }
                await { a -> a.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL && views(a.window.decorView).any { it is LanguageSheet } }
                onActivity { a -> views(a.window.decorView).filterIsInstance<Hud>().single().navigateBack() }
                await { a -> views(a.window.decorView).none { it is LanguageSheet } }
                capture("home-he")
                assertTrue(Stage.homeScreen)
                tap(R.string.cd_skins)
                capture("wardrobe-he")
                tap(R.string.cd_back)
                SystemClock.sleep(400)
                tap(R.string.cd_shop)
                capture("shop-he")
                onActivity { it.recreate() }
                await { a -> a.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL }
                assertEquals("he", Languages.current(context))
            }
        } finally {
            onActivity { it.finish() }
            prefs.edit().apply { if (original == null) remove("language") else putString("language", original) }.commit()
        }
    }

    @Test fun everyLanguageResolvesResourcesAndUnknownPreferenceFallsBack() {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val original = prefs.getString("language", null)
        try {
            for ((code, title) in listOf("en" to "Languages", "de" to "Sprachen", "he" to "שפות")) {
                Languages.select(context, code)
                val localized = Languages.wrap(context)
                assertEquals(title, localized.getString(R.string.languages_title))
                assertFalse(localized.resources.getQuantityString(R.plurals.count_taps, 2, 2).isEmpty())
                assertFalse(localized.gameText("Classic").isEmpty())
            }
            prefs.edit().putString("language", "unsupported").commit()
            assertTrue(Languages.options.any { it.code == Languages.current(context) })
            prefs.edit().putString("language", "iw").commit()
            assertEquals("he", Languages.current(context))
        } finally {
            prefs.edit().apply { if (original == null) remove("language") else putString("language", original) }.commit()
        }
    }
}
