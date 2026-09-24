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
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LanguageTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    // Later English-text tests share this save, so leave no language behind.
    @After fun forgetLanguage() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().remove("language").commit()
    }

    // Track the current activity for the final explicit recreation/persistence check.
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
        tapAt(point[0], point[1])
    }

    private fun tapAt(x: Int, y: Int) {
        val down = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x.toFloat(), y.toFloat(), 0)
            instrumentation.sendPointerSync(event)
            event.recycle()
            SystemClock.sleep(60)
        }
        // Cube Run renders and animates continuously, so Android's global "idle"
        // condition may never arrive. The targeted view-state waits around each
        // interaction provide the deterministic synchronization this test needs.
        SystemClock.sleep(120)
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
                var originalActivity: GameActivity? = null
                var originalSurface: View? = null
                onActivity { a ->
                    originalActivity = a
                    originalSurface = views(a.window.decorView).filterIsInstance<android.view.SurfaceView>().single()
                }
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
                await { a -> views(a.window.decorView).any { it is LanguageSheet && it.alpha == 1f } }
                var outsideY = 0
                onActivity { outsideY = it.window.decorView.height / 2 }
                tapAt(4, outsideY)
                await { a -> views(a.window.decorView).none { it is LanguageSheet } }
                assertTrue(Stage.homeScreen)
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
                            views(a.window.decorView).filterIsInstance<Hud>().count() == 1 &&
                            views(a.window.decorView).any { it is LanguageSheet && it.isShown && it.alpha == 1f }
                    }
                    onActivity { a ->
                        assertSame("Language changes must keep the activity", originalActivity, a)
                        assertSame("Language changes must keep the 3D surface", originalSurface, views(a.window.decorView).filterIsInstance<android.view.SurfaceView>().single())
                        val sheet = views(a.window.decorView).filterIsInstance<LanguageSheet>().single()
                        val labels = views(sheet).filterIsInstance<TextView>().map { it.text.toString() }.toList()
                        assertTrue(labels.contains("American"))
                        assertFalse(labels.contains("English"))
                        Languages.options.forEach { assertFalse(labels.contains(a.getString(it.country))) }
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
                    if (code == "de") {
                        tap(R.string.cd_back)
                        await { a -> views(a.window.decorView).none { it is LanguageSheet } }
                        tap(R.string.cd_skins)
                        onActivity { a ->
                            val labels = views(a.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }.toList()
                            assertTrue(labels.contains("BLASE")); assertTrue(labels.contains("SPUR"))
                        }
                        capture("wardrobe-de")
                        tap(R.string.cd_back)
                        SystemClock.sleep(400)
                        tap(R.string.cd_languages)
                    }
                }
                onActivity { it.changeLanguage("he") }
                await { a -> a.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL && views(a.window.decorView).filterIsInstance<Hud>().count() == 1 && views(a.window.decorView).any { it is LanguageSheet } }
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

    @Test fun rtlVisualReviewCoversAbilitiesShardsPauseAndRewards() {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val original = prefs.getString("language", null)
        fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
        fun call(owner: Any, name: String) = owner.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(owner)
        fun checkText(root: View) {
            for (label in views(root).filterIsInstance<TextView>().filter { it.isShown && it.alpha > .99f }) {
                val layout = label.layout ?: continue
                assertTrue("RTL label exceeds its height: ${label.text}",
                    layout.height <= label.height - label.compoundPaddingTop - label.compoundPaddingBottom + 2)
                for (line in 0 until layout.lineCount) {
                    assertEquals("Clipped RTL label: ${label.text}", 0, layout.getEllipsisCount(line))
                    assertTrue("RTL label exceeds its width: ${label.text}", layout.getLineWidth(line) <= label.width - label.compoundPaddingLeft - label.compoundPaddingRight + 2)
                }
            }
        }
        try {
            Languages.select(context, "he")
            ActivityScenario.launch<GameActivity>(Intent(context, GameActivity::class.java).putExtra(Hud.EXTRA_AUTOSTART, false)).use {
                await { a -> views(a.window.decorView).any { it is MainMenu && it.isShown } }
                SystemClock.sleep(700)
                tap(R.string.cd_skins)
                SystemClock.sleep(400)
                onActivity { a ->
                    val page = views(a.window.decorView).filterIsInstance<WardrobeView>().single()
                    field(page, "index").setInt(page, 23); call(page, "applyPreview"); call(page, "render")
                }
                SystemClock.sleep(500)
                tapMatching { a, view -> view.contentDescription == a.getString(R.string.text_show_ability, a.gameText("Speed")) }
                capture("rtl-ability")
                onActivity { a -> checkText(views(a.window.decorView).filterIsInstance<WardrobeView>().single()) }
                for (count in listOf(33, 100)) {
                    onActivity { a ->
                        val page = views(a.window.decorView).filterIsInstance<WardrobeView>().single()
                        field(page, "index").setInt(page, 20); call(page, "applyPreview"); call(page, "render")
                        val action = field(page, "action").get(page) as CandyButton
                        ShardDisplay(UiKit(a)).bind(action, cube.run.data.Skins.get(20), count)
                    }
                    capture("rtl-shards-$count")
                    onActivity { a -> checkText(views(a.window.decorView).filterIsInstance<WardrobeView>().single()) }
                }
                tap(R.string.cd_back)
                SystemClock.sleep(400)
                tap(R.string.cd_shop)
                SystemClock.sleep(400)
                onActivity { a -> views(a.window.decorView).filterIsInstance<android.widget.ScrollView>().first().fullScroll(View.FOCUS_DOWN) }
                capture("rtl-shop-perks")
                onActivity { a ->
                    val page = views(a.window.decorView).filterIsInstance<ShopView>().single()
                    checkText(page)
                    for (button in views(page).filterIsInstance<CandyButton>()) {
                        val point = IntArray(2); button.getLocationOnScreen(point)
                        assertTrue("Price must stay inside the screen", point[0] >= 0 && point[0] + button.width <= a.window.decorView.width)
                    }
                }
                tap(R.string.cd_back)
                SystemClock.sleep(400)
                lateinit var fixture: View
                onActivity { a ->
                    Stage.paused = true
                    fixture = PauseSheet(a, UiKit(a), {}, {}, {})
                    a.addContentView(fixture, android.widget.FrameLayout.LayoutParams(-1, -1))
                }
                capture("rtl-pause")
                onActivity { a ->
                    checkText(fixture)
                    (fixture.parent as ViewGroup).removeView(fixture)
                    views(a.window.decorView).filterIsInstance<Hud>().single().visibility = View.INVISIBLE
                    Stage.paused = false
                    fixture = RunOverFlow(a, UiKit(a), 1234, 1000, true, 234, 2, "Candy Fields", emptyList(), {}, {}, intArrayOf(3, 7, 12))
                    a.addContentView(fixture, android.widget.FrameLayout.LayoutParams(-1, -1))
                }
                SystemClock.sleep(1800)
                capture("rtl-results")
                onActivity { a ->
                    checkText(fixture)
                    val total = views(fixture).filterIsInstance<TextView>().single { it.text.toString() == "+234" }
                    val hint = views(fixture).filterIsInstance<TextView>().single { it.text.toString() == a.getString(R.string.text_tap_to_continue) }
                    val totalBounds = android.graphics.Rect(); val hintBounds = android.graphics.Rect()
                    assertTrue("Coin total must be visible", total.getGlobalVisibleRect(totalBounds))
                    hint.getGlobalVisibleRect(hintBounds)
                    assertTrue("Coin total must not overlap the next action", totalBounds.bottom < hintBounds.top)
                    call(fixture, "showBoxes")
                }
                capture("rtl-boxes")
                onActivity {
                    field(fixture, "boxBusy").setBoolean(fixture, true)
                    (fixture as RunOverFlow).onBoxOpened(cube.run.data.Progress.BoxReward.SHARDS, 33, 0, 0)
                }
                capture("rtl-reward")
                onActivity { checkText(fixture); (fixture.parent as ViewGroup).removeView(fixture) }

                // Check actual rendered fill direction, without changing saved upgrade levels.
                onActivity { a ->
                    val bar = UiKit(a).segments(5).apply { layoutDirection = View.LAYOUT_DIRECTION_RTL; level = 2; color = android.graphics.Color.RED; offColor = android.graphics.Color.BLUE }
                    bar.layout(0, 0, 200, 30)
                    val image = Bitmap.createBitmap(200, 30, Bitmap.Config.ARGB_8888)
                    bar.draw(android.graphics.Canvas(image))
                    assertEquals(android.graphics.Color.RED, image.getPixel(185, 15))
                    assertEquals(android.graphics.Color.BLUE, image.getPixel(15, 15))
                    image.recycle()
                    val painter = CandyPainter(4f, 2f).apply { rtl = true; color = android.graphics.Color.BLUE; progress = .25f; progressColor = android.graphics.Color.RED }
                    val button = Bitmap.createBitmap(200, 40, Bitmap.Config.ARGB_8888)
                    painter.draw(android.graphics.Canvas(button), 200f, 40f)
                    assertEquals(android.graphics.Color.RED, button.getPixel(185, 30))
                    assertEquals(android.graphics.Color.BLUE, button.getPixel(15, 30))
                    button.recycle()
                }
            }
        } finally {
            Stage.paused = false; Stage.mode = Stage.NONE; Stage.clearPreview()
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
