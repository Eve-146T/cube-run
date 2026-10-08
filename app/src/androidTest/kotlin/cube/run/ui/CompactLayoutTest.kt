package cube.run.ui

import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import cube.run.GameActivity
import cube.run.data.Progress
import cube.run.data.Skins
import org.junit.Assert.*
import org.junit.Test

/** Exercise the same instances through compact/full transitions: rebuilding pages loses state. */
class CompactLayoutTest {
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun field(view: Any, name: String): Any = view.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(view)!!
    private fun measure(view: View, kit: UiKit, width: Int, height: Int) {
        repeat(2) {
            view.measure(View.MeasureSpec.makeMeasureSpec(kit.dp(width.toFloat()), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(kit.dp(height.toFloat()), View.MeasureSpec.EXACTLY))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
    }
    private fun inside(root: ViewGroup, child: View) {
        val rect = Rect(0, 0, child.width, child.height)
        root.offsetDescendantRectToMyCoords(child, rect)
        assertTrue("${child.tag ?: child.javaClass.simpleName}: $rect outside ${root.width} x ${root.height}",
            rect.left >= 0 && rect.top >= 0 && rect.right <= root.width && rect.bottom <= root.height)
    }
    private val sizes = listOf(360 to 375, 320 to 426, 280 to 320, 360 to 500, 360 to 250, 360 to 720, 360 to 375, 360 to 720)

    @Test fun settingsControlsStayInsideTheirRowsThroughCompactAndRtlLayouts() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            for (scale in listOf(1f, 1.5f)) {
                val kit = UiKit(activity.createConfigurationContext(Configuration(activity.resources.configuration).apply { fontScale = scale }))
                val page = SettingsView(activity, kit, {}, {}, {}) {}
                for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                    page.layoutDirection = direction
                    for ((w, h) in sizes) {
                        measure(page, kit, w, h)
                        if (w >= 320 && h >= 720 && scale == 1f) {
                            val scroller = all(page).filterIsInstance<ScrollView>().single()
                            assertTrue("Full-screen settings must not have a tiny scroll range",
                                scroller.getChildAt(0).height <= scroller.height - scroller.paddingTop - scroller.paddingBottom)
                        }
                        for (control in all(page).filter { it is CandySwitch || it is LevelPicker }) {
                            inside(control.parent as ViewGroup, control)
                            assertTrue("Control has a usable width at $w x $h / $scale", control.width >= kit.dp(48f))
                            if (control is CandySwitch) {
                                val row = control.parent as ViewGroup
                                assertTrue("Whole switch row is a touch target", row.isClickable && row.height >= kit.dp(48f))
                                assertEquals("Switch is centred in its row", row.height / 2f, control.top + control.height / 2f, kit.dp(1f).toFloat())
                            }
                        }
                        for (label in all(page).filterIsInstance<TextView>().filter { it.visibility == View.VISIBLE }) {
                            assertTrue("Label has width at $w x $h / $scale: ${label.text}", label.width > 0)
                        }
                    }
                }
                Anim.cancelTree(page)
            }
        } }
    }

    @Test fun settingsSlidersPickPersistAndReachTheirMinimumInBothDirections() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            val settings = cube.run.data.Settings
            val savedVolume = settings.volume; val savedSpeed = settings.startSpeed; val savedSound = settings.soundEnabled
            val savedStartEnabled = settings.startSpeedEnabled
            val taps = Progress.totalHapticTaps
            try {
                for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                    settings.setVolume(1); settings.setStartSpeed(0); settings.setSoundEnabled(false)
                    val kit = UiKit(activity)
                    val page = SettingsView(activity, kit, {}, {}, {}) {}
                    page.layoutDirection = direction
                    measure(page, kit, 360, 720)
                    val volume = all(page).filterIsInstance<VolumeSteps>().single()
                    val speed = all(page).filterIsInstance<StartSpeedBar>().single()
                    fun gesture(view: View, fraction: Float, cancel: Boolean = false) {
                        val x = view.width * if (direction == View.LAYOUT_DIRECTION_RTL) 1f - fraction else fraction
                        val now = android.os.SystemClock.uptimeMillis()
                        for ((index, action) in listOf(android.view.MotionEvent.ACTION_DOWN,
                            if (cancel) android.view.MotionEvent.ACTION_CANCEL else android.view.MotionEvent.ACTION_UP).withIndex()) {
                            val event = android.view.MotionEvent.obtain(now, now + index * 80L, action, x, view.height / 2f, 0)
                            assertTrue(view.dispatchTouchEvent(event)); event.recycle()
                        }
                    }
                    gesture(volume, .45f)
                    assertEquals(5, settings.volume)
                    assertTrue("Adjusting volume unmutes sound", settings.soundEnabled)
                    gesture(volume, .45f); assertEquals(4, settings.volume)
                    gesture(speed, .98f); assertEquals(Progress.maxStartPresses, settings.startSpeed)
                    (field(page, "speedTile") as View).performClick()
                    assertFalse("The start-speed icon disables the preset", settings.startSpeedEnabled)
                    assertEquals("Disabling keeps the selected steps", Progress.maxStartPresses, speed.level)
                    assertEquals("A disabled preset starts normally", 0, settings.effectiveStartSpeed)
                    settings.init(activity)
                    assertFalse("The disabled state persists", settings.startSpeedEnabled)
                    assertEquals("The selected level persists while disabled", Progress.maxStartPresses, settings.startSpeed)
                    (field(page, "speedTile") as View).performClick()
                    assertTrue(settings.startSpeedEnabled)
                    assertEquals("Toggling back restores the preset", Progress.maxStartPresses, settings.effectiveStartSpeed)
                    (field(page, "speedTile") as View).performClick()
                    gesture(speed, .02f)
                    assertTrue("Selecting steps re-enables the preset", settings.startSpeedEnabled)
                    gesture(speed, .98f)
                    gesture(speed, .02f); gesture(speed, .02f)
                    assertEquals("The zero-speed start stays reachable", 0, settings.startSpeed)
                    gesture(speed, .02f, cancel = true)
                    assertEquals("Cancel must not act as a second tap", 1, settings.startSpeed)
                    settings.init(activity)
                    assertEquals(4, settings.volume); assertEquals(1, settings.startSpeed)
                    assertEquals("Sliders do not count as haptic-toggle taps", taps, Progress.totalHapticTaps)
                    Anim.cancelTree(page)
                }
            } finally {
                settings.setVolume(savedVolume); settings.setStartSpeed(savedSpeed); settings.setSoundEnabled(savedSound)
                settings.setStartSpeedEnabled(savedStartEnabled)
            }
        } }
    }

    @Test fun menuKeepsStartAndNavigationSeparateAtEverySize() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            for (scale in listOf(1f, 1.5f)) {
                val kit = UiKit(activity.createConfigurationContext(Configuration(activity.resources.configuration).apply { fontScale = scale }))
                val menu = MainMenu(activity, kit, {}, {}, {}, {}, openingEntrance = true)
                menu.setBest(12345)
                for ((w, h) in sizes) {
                    measure(menu, kit, w, h)
                    val top = menu.getChildAt(0)
                    val middle = menu.getChildAt(3)
                    val settings = menu.getChildAt(4)
                    val actions = menu.getChildAt(5)
                    // A third of a phone at 150% text is past what the brand can shrink to.
                    if (h < 300 && scale > 1f) { inside(menu, settings); inside(menu, actions); continue }
                    assertTrue("Logo meets Play at $w x $h / $scale", top.bottom <= middle.top)
                    assertTrue("Play meets navigation at $w x $h / $scale", middle.bottom <= minOf(settings.top, actions.top))
                    inside(menu, settings); inside(menu, actions)
                }
                Anim.cancelTree(menu)
            }
        } }
    }

    @Test fun gameplayLeavesRoomForTheTrackAndRestoresScoreSize() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            val kit = UiKit(activity)
            val hud = Hud(activity)
            hud.setBoost(true, 0, 3)
            for ((w, h) in sizes) {
                measure(hud, kit, w, h)
                val score = field(hud, "scoreText") as TextView
                assertEquals(if (h < CompactLayout.HEIGHT_DP) 32f else 60f, score.textSize / activity.resources.displayMetrics.scaledDensity, .1f)
                val boost = field(hud, "boost") as View
                inside(hud, boost)
                assertTrue(boost.height >= kit.dp(48f))
            }
            Anim.cancelTree(hud)
        } }
    }

    @Test fun scrollingRewardsDoesNotCountAsTapToContinue() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            val kit = UiKit(activity)
            var taps = 0
            val scroll = TapScrollView(activity) { taps++ }
            scroll.addView(View(activity), android.widget.FrameLayout.LayoutParams(kit.dp(280f), kit.dp(900f)))
            measure(scroll, kit, 280, 180)
            fun gesture(move: Boolean) {
                val down = android.os.SystemClock.uptimeMillis()
                val events = if (move) listOf(0 to 120f, 2 to 40f, 1 to 40f) else listOf(0 to 80f, 1 to 80f)
                events.forEachIndexed { index, (action, y) ->
                    val event = android.view.MotionEvent.obtain(down, down + index * 100L, action, 50f, y, 0)
                    scroll.dispatchTouchEvent(event); event.recycle()
                }
            }
            gesture(true)
            assertEquals("A scroll must not advance a reward", 0, taps)
            gesture(false)
            assertEquals("A tap on the content still advances once", 1, taps)
        } }
    }

    @Test fun wardrobeKeepsPurchaseVisibleAndRestoresItsFullLayout() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            for (scale in listOf(1f, 1.5f)) {
                val kit = UiKit(activity.createConfigurationContext(Configuration(activity.resources.configuration).apply { fontScale = scale }))
                val page = WardrobeView(activity, kit, onClose = {})
                val ability = field(page, "abilityDisplay") as AbilityDisplay
                ability.bind(Skins.get(1).abilities, "gambler")
                for ((w, h) in sizes) {
                    measure(page, kit, w, h)
                    val action = page.findViewWithTag<View>("wardrobe_action_button")
                    inside(page, action)
                    assertTrue(action.height >= kit.dp(48f))
                    val chip = all(ability.floating).filterIsInstance<CandyChip>().first()
                    val left = field(page, "left") as View
                    val right = field(page, "right") as View
                    listOf(left, right, field(page, "tabs") as View, field(page, "dots") as View).filter { it.visibility == View.VISIBLE }.forEach { inside(page, it) }
                    fun rect(view: View) = Rect(0, 0, view.width, view.height).also { page.offsetDescendantRectToMyCoords(view, it) }
                    assertFalse("Ability corner meets the arrows at $w x $h / $scale", Rect.intersects(rect(chip), rect(left)))
                    if (h < CompactLayout.HEIGHT_DP) {
                        // The stage frames the item in the gap between the tabs and the details.
                        val tabs = rect(field(page, "tabs") as View)
                        val details = rect(field(page, "itemDetails") as View)
                        assertTrue("No room left for the item at $w x $h / $scale: tabs=$tabs details=$details name=${(field(page, "name") as View).height} action=${action.height}", details.top - tabs.bottom >= kit.dp(if (h < 300 && scale > 1f) 32f else if (h < 360) 48f else 96f))
                        assertTrue("Arrows flank the name", rect(left).top >= tabs.bottom)
                        chip.performClick()
                        measure(page, kit, w, h)
                        inside(page, action)
                        val detail = all(ability.floating).filterIsInstance<TextView>()
                        assertTrue("The ability explanation opens", detail.any { it.text.contains("250k") })
                        chip.performClick()
                    }
                }
                Anim.cancelTree(page)
            }
        } }
    }

    @Test fun shopRemovesShowroomAndRestoresItWithoutResettingScroll() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            val kit = UiKit(activity)
            val shop = ShopView(activity, kit, kit.coinBank("0"), {}, onVoidPurchase = { _, _ -> }, onClose = {})
            for ((w, h) in sizes) {
                measure(shop, kit, w, h)
                val showroom = field(shop, "showroom") as View
                assertEquals(if (h < CompactLayout.HEIGHT_DP) View.GONE else View.VISIBLE, showroom.visibility)
                val scroll = all(shop).filterIsInstance<ScrollView>().first()
                assertTrue("Shop gives content most of the compact window", h >= CompactLayout.HEIGHT_DP || scroll.height > kit.dp(h * .65f))
                scroll.scrollTo(0, kit.dp(40f))
                val before = scroll.scrollY
                measure(shop, kit, w, h)
                assertEquals(before, scroll.scrollY)
            }
            Anim.cancelTree(shop)
        } }
    }

    @Test fun pauseFitsNarrowWindowsAndLanguagesStillConstruct() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            val kit = UiKit(activity)
            val pause = PauseSheet(activity, kit, {}, {}, {})
            for ((w, h) in sizes) {
                measure(pause, kit, w, h)
                all(pause).filterIsInstance<CandyButton>().forEach { inside(pause, it) }
            }
            val language = LanguageSheet(activity, kit, {}, {})
            measure(language, kit, 280, 320)
            assertTrue(all(language).filterIsInstance<ScrollView>().first { it.parent == language }.width <= language.width)
            Anim.cancelTree(pause); Anim.cancelTree(language)
        } }
    }

    @Test fun resultsStayReadableAndContinueWorksAfterResizing() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            val kit = UiKit(activity)
            var closed = 0
            val flow = RunOverFlow(activity, kit, 1234, 1234, true, 99, 0, "", emptyList(), {}, { closed++ })
            val prompt = activity.getString(cube.run.R.string.text_tap_for_the_menu)
            for ((w, h) in sizes) {
                measure(flow, kit, w, h)
                val hint = all(flow).filterIsInstance<TextView>().first { it.text.toString() == prompt }
                inside(flow, hint)
                assertNull("Tap prompts remain plain", hint.background)
                val score = field(flow, "scoreText") as TextView
                val column = score.parent as View
                assertTrue(score.textSize >= kit.dp(40f))
                assertEquals(if (h < CompactLayout.HEIGHT_DP) 64f else 104f, score.textSize / activity.resources.displayMetrics.scaledDensity, .1f)
                // Short panes keep the stage: the cube and its sunburst get the room above the column.
                if (h < CompactLayout.HEIGHT_DP) assertTrue((column.layoutParams as ViewGroup.MarginLayoutParams).topMargin >= kit.dp(104f))
                assertTrue((field(flow, "resultsCard") as View).layoutParams.width <= kit.dp(w - 24f))
            }
            measure(flow, kit, 360, 375)
            flow.confirm() // the first tap settles the count, as at full height
            flow.confirm()
            assertEquals(1, closed)
            Anim.cancelTree(flow)
        } }
    }

    @Test fun boxRewardAndFooterSurviveRepeatedResizing() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            val kit = UiKit(activity)
            val flow = RunOverFlow(activity, kit, 0, 0, false, 0, 2, "", emptyList(), {}, {}, boxesOnly = true)
            for ((w, h) in sizes) {
                measure(flow, kit, w, h)
                inside(flow, flow.findViewWithTag<View>("gift_footer_hint"))
            }
            flow.onBoxOpened(cube.run.data.Progress.BoxReward.COINS, 250, 0, 0)
            for ((w, h) in sizes) {
                measure(flow, kit, w, h)
                assertTrue(flow.findViewWithTag<TextView>("gift_reward_value").text.contains("250"))
                inside(flow, flow.findViewWithTag<View>("gift_footer_hint"))
            }
            Anim.cancelTree(flow)
        } }
    }
}

/** Opt-in screenshots use real attached Android views and the current save. */
class CompactLayoutCaptureTest {
    @Test fun captureCompactScreens() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        org.junit.Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("captureCompact") == "true")
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            android.os.SystemClock.sleep(1800)
            var previous: View? = null
            fun show(label: String, create: (GameActivity, UiKit) -> View) {
                scenario.onActivity { activity ->
                    val root = activity.findViewById<ViewGroup>(android.R.id.content)
                    previous?.let { root.removeView(it) }
                    previous = create(activity, UiKit(activity)).also { root.addView(it, ViewGroup.LayoutParams(-1, -1)) }
                }
                android.os.SystemClock.sleep(1200)
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "compact-review/$label.png")
                file.parentFile!!.mkdirs()
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            show("results") { activity, kit -> RunOverFlow(activity, kit, 1234, 1234, true, 99, 0, "", emptyList(), {}, {}) }
            show("achievements") { activity, kit -> AchievementsView(activity, kit, onClose = {}) }
            show("pause") { activity, kit -> PauseSheet(activity, kit, {}, {}, {}) }
            show("box") { activity, kit -> RunOverFlow(activity, kit, 0, 0, false, 0, 2, "", emptyList(), {}, {}, boxesOnly = true) }
            show("reward") { activity, kit -> RunOverFlow(activity, kit, 0, 0, false, 0, 2, "", emptyList(), {}, {}, boxesOnly = true).apply {
                postDelayed({ onBoxOpened(cube.run.data.Progress.BoxReward.COINS, 250, 0, 0) }, 300)
            } }
            scenario.onActivity { activity -> previous?.let { activity.findViewById<ViewGroup>(android.R.id.content).removeView(it) } }
        }
    }
}
