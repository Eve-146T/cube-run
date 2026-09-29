package cube.run.ui

import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import cube.run.GameActivity
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
    private val sizes = listOf(360 to 375, 320 to 426, 280 to 320, 360 to 720, 360 to 375, 360 to 720)

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
                assertEquals(if (h < 480) 32f else 60f, score.textSize / activity.resources.displayMetrics.scaledDensity, .1f)
                val boost = field(hud, "boost") as View
                inside(hud, boost)
                assertTrue(boost.height >= kit.dp(48f))
            }
            Anim.cancelTree(hud)
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
                    if (h < 480) {
                        assertNotNull(page.findViewWithTag<View>("compact_wardrobe_scroll"))
                        val chip = all(ability.floating).filterIsInstance<CandyChip>().first()
                        chip.performClick()
                        measure(page, kit, w, h)
                        inside(page, action)
                        val detail = all(ability.floating).filterIsInstance<TextView>()
                        assertTrue("Long ability text remains in the scrollable content", detail.any { it.text.contains("250k") })
                        chip.performClick()
                    } else assertNull(page.findViewWithTag<View>("compact_wardrobe_scroll"))
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
                assertEquals(if (h < 480) View.GONE else View.VISIBLE, showroom.visibility)
                val scroll = all(shop).filterIsInstance<ScrollView>().first()
                assertTrue("Shop gives content most of the compact window", h >= 480 || scroll.height > kit.dp(h * .65f))
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
            for ((w, h) in sizes) {
                measure(flow, kit, w, h)
                val button = flow.findViewWithTag<View>("compact_results_continue")
                if (h < 480) {
                    assertNotNull(button); inside(flow, button)
                    val score = field(flow, "scoreText") as TextView
                    assertEquals("1234", score.text.toString())
                    assertEquals(1f, score.alpha)
                    assertEquals(1f, (field(flow, "page") as View).alpha)
                    assertTrue(score.textSize >= kit.dp(40f))
                    assertEquals(1f, (field(flow, "scoreText") as View).parent.let { it as View }.scaleX)
                } else assertNull(button)
            }
            measure(flow, kit, 360, 375)
            flow.findViewWithTag<View>("compact_results_continue").performClick()
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
