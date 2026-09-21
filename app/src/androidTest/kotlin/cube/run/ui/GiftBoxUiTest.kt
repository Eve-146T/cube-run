package cube.run.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.text.Spanned
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Wardrobe
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Native layout coverage; hold the renderer while exercising component-only open requests. */
class GiftBoxUiTest {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun call(flow: RunOverFlow, method: String) =
        RunOverFlow::class.java.getDeclaredMethod(method).apply { isAccessible = true }.invoke(flow)
    private fun text(flow: RunOverFlow, tag: String) = flow.findViewWithTag<TextView>(tag)

    private fun withGift(test: (ActivityScenario<GameActivity>, RunOverFlow, UiKit) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val real = instrumentation.targetContext
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            lateinit var flow: RunOverFlow
            lateinit var container: FrameLayout
            lateinit var root: FrameLayout
            lateinit var kit: UiKit
            var oldMode = Stage.NONE
            var oldPaused = false
            var saved: Map<String, *> = emptyMap<String, Any>()
            scenario.onActivity { activity ->
                saved = real.getSharedPreferences("progress", Context.MODE_PRIVATE).all
                oldMode = Stage.mode; oldPaused = Stage.paused
                Stage.paused = true
            }
            // Let the render loop observe pause before creating a page that can request boxes.
            SystemClock.sleep(120)
            scenario.onActivity { activity ->
                val config = Configuration(activity.resources.configuration).apply { fontScale = 1.5f }
                kit = UiKit(activity.createConfigurationContext(config))
                root = activity.findViewById(android.R.id.content)
                container = FrameLayout(activity).apply { setBackgroundColor(0xff100a20.toInt()) }
                flow = RunOverFlow(activity, kit, 0, 0, false, 0, 2, "", emptyList(), {}, {}, boxesOnly = true)
                container.addView(flow, FrameLayout.LayoutParams(kit.dp(280f), -1, Gravity.CENTER))
                root.addView(container, FrameLayout.LayoutParams(-1, -1))
            }
            SystemClock.sleep(500) // Geometry assertions measure the settled page, not its entrance translation.
            // Stage.paused pauses gameplay, while gift animations intentionally keep ticking.
            // Hold the actual GL queue so these component taps cannot open a real box.
            val held = CountDownLatch(1)
            val release = CountDownLatch(1)
            Gdx.app.postRunnable { held.countDown(); release.await(8, TimeUnit.SECONDS) }
            assertTrue("Renderer held for native component checks", held.await(5, TimeUnit.SECONDS))
            try { test(scenario, flow, kit) }
            finally {
                try {
                    scenario.onActivity {
                        root.removeView(container)
                        Stage.openRequests.set(0); Stage.skipBoxRequests.set(0)
                        Stage.mode = oldMode; Stage.paused = oldPaused
                        assertEquals("Component previews never change the player's save", saved,
                            real.getSharedPreferences("progress", Context.MODE_PRIVATE).all)
                    }
                } finally { release.countDown() }
            }
        }
    }

    private fun measure(flow: RunOverFlow, kit: UiKit, widthDp: Float = 280f) {
        flow.measure(View.MeasureSpec.makeMeasureSpec(kit.dp(widthDp), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(kit.dp(720f), View.MeasureSpec.EXACTLY))
        flow.layout(0, 0, flow.measuredWidth, flow.measuredHeight)
    }

    private fun assertFits(view: TextView) {
        assertEquals("${view.tag} remains one line: ${view.text}", 1, view.lineCount)
        val available = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
        assertTrue("${view.tag} renders every glyph inside its content width",
            view.layout.getLineWidth(0) <= available + 1f)
        assertEquals("Reward labels are never ellipsized", 0, view.layout.getEllipsisCount(0))
        assertTrue("${view.tag} has room for all font and icon metrics",
            view.layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureHardwareAchievements") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "achievements-hardware").apply { mkdirs() }
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun narrowLargeFontFitsLongestRewardsAndTheirIcons() = withGift { scenario, flow, kit ->
        scenario.onActivity {
            val cases = listOf(
                intArrayOf(Progress.BoxReward.COINS, 1000000, 0, 0),
                intArrayOf(Progress.BoxReward.SHARDS, 1000, 0, 0),
                intArrayOf(Progress.BoxReward.BUBBLE, 1000, 0, 0),
            ) + (0..2).map { cat ->
                val longest = (0 until Wardrobe.count(cat)).maxBy { Wardrobe.name(cat, it).length }
                intArrayOf(Progress.BoxReward.SKIN, 1, cat, longest)
            }
            for ((kind, amount, cat, id) in cases) {
                field(flow, "boxRewardReady").setBoolean(flow, false)
                flow.onBoxOpened(kind, amount, cat, id)
                call(flow, "finishBoxAnimation")
                measure(flow, kit)
                val value = text(flow, "gift_reward_value")
                assertFits(value)
                val category = text(flow, "gift_reward_category")
                if (category.visibility == View.VISIBLE) assertFits(category)
                assertFits(text(flow, "gift_footer_hint"))
                (value.text as? Spanned)?.getSpans(0, value.text.length, CenteredImageSpan::class.java)?.forEach { span ->
                    assertEquals("The reward icon tracks the actual fitted font size",
                        (value.textSize * 1.15f).toInt(), span.drawable.bounds.width())
                }
                val card = flow.findViewWithTag<View>("gift_reward_card")
                val bounds = Rect(0, 0, card.width, card.height)
                flow.offsetDescendantRectToMyCoords(card, bounds)
                val heartbeatExtra = card.width * .02f
                assertTrue("Reward heartbeat leaves its left rounded corner visible", bounds.left - heartbeatExtra >= kit.dp(10f))
                assertTrue("Reward heartbeat leaves its right rounded corner visible", bounds.right + heartbeatExtra <= flow.width - kit.dp(10f))
            }
            for (width in listOf(280f, 600f)) {
                measure(flow, kit, width)
                val card = flow.findViewWithTag<View>("gift_reward_card")
                val bounds = Rect(0, 0, card.width, card.height)
                flow.offsetDescendantRectToMyCoords(card, bounds)
                val springExtra = card.width * .039f // .3→1 pop with spring tension1.8 peaks at1.077×.
                assertTrue("The full entrance spring stays inside a ${width}dp screen", bounds.left - springExtra >= 0f)
                assertTrue("The full entrance spring stays inside a ${width}dp screen", bounds.right + springExtra <= flow.width)
                assertTrue("Large windows keep a focused reward card", card.width <= kit.dp(360f))
            }
            measure(flow, kit)
        }
        SystemClock.sleep(100)
        capture("gift-reward-280dp-font150")
    }

    @Test fun openingHidesHintAndReservedFooterNeverJumpsBetweenActions() = withGift { scenario, flow, kit ->
        scenario.onActivity {
            measure(flow, kit)
            val hint = text(flow, "gift_footer_hint")
            val initial = Rect(0, 0, hint.width, hint.height)
            flow.offsetDescendantRectToMyCoords(hint, initial)
            call(flow, "tapBox")
            assertEquals("Opening has no skip prompt", View.INVISIBLE, hint.visibility)
            assertFalse(hint.text.contains("SKIP"))
            assertEquals(1, Stage.openRequests.get())
            // A second tap may finish the spectacle, but cannot spend or request another box.
            call(flow, "tapBox")
            assertEquals(1, Stage.openRequests.get())
            flow.onBoxOpened(Progress.BoxReward.COINS, 500, 0, 0)
            measure(flow, kit)
            assertEquals(View.VISIBLE, hint.visibility)
            assertEquals("TAP FOR THE NEXT BOX", hint.text.toString())
            assertFits(hint)
            val next = Rect(0, 0, hint.width, hint.height)
            flow.offsetDescendantRectToMyCoords(hint, next)
            assertEquals("Changing the action text cannot shift the footer", initial, next)
            field(flow, "boxesLeft").setInt(flow, 0)
            call(flow, "finishBoxAnimation")
            measure(flow, kit)
            assertEquals("TAP TO RETURN", hint.text.toString())
            assertFits(hint)
            val final = Rect(0, 0, hint.width, hint.height)
            flow.offsetDescendantRectToMyCoords(hint, final)
            assertEquals(initial, final)
        }
    }
}
