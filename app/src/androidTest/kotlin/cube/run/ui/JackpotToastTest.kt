package cube.run.ui

import android.content.res.Configuration
import android.graphics.Rect
import android.os.SystemClock
import android.text.Spanned
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import cube.run.GameActivity
import org.junit.Assert.*
import org.junit.Test

/** Attached native feedback only: no purchase, random draw, or award is performed. */
class JackpotToastTest {
    // ActivityScenario.onActivity waits for global idle, which a running spectacle never promises.
    private fun ui(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)

    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun card(toast: JackpotToast) = toast.findViewWithTag<ViewGroup>("jackpot_card")
    private fun amount(toast: JackpotToast) = toast.findViewWithTag<TextView>("jackpot_amount")
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun awaitUi(scenario: ActivityScenario<GameActivity>, label: String, timeout: Long = 5000L, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        var passed = false
        while (!passed && SystemClock.uptimeMillis() < deadline) {
            ui { passed = condition() }
            if (!passed) SystemClock.sleep(25)
        }
        assertTrue(label, passed)
    }

    private fun awaitShown(scenario: ActivityScenario<GameActivity>, toast: JackpotToast) =
        awaitUi(scenario, "Jackpot reaches its readable settled state") {
            card(toast).visibility == View.VISIBLE && card(toast).alpha >= .99f &&
                kotlin.math.abs(card(toast).scaleX - 1f) < .01f
        }

    private fun withToast(test: (ActivityScenario<GameActivity>, JackpotToast, FrameLayout, UiKit) -> Unit) {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            lateinit var root: FrameLayout
            lateinit var toast: JackpotToast
            lateinit var kit: UiKit
            scenario.onActivity { activity ->
                val config = Configuration(activity.resources.configuration).apply { fontScale = 1.5f }
                kit = UiKit(activity.createConfigurationContext(config))
                root = activity.findViewById(android.R.id.content)
                toast = JackpotToast(activity, kit)
                root.addView(toast, FrameLayout.LayoutParams(kit.dp(280f), -1, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            }
            try { test(scenario, toast, root, kit) }
            finally { ui { toast.reset(); root.removeView(toast) } }
        }
    }

    @Test fun pauseDetachAndResetPreserveExactlyOneCombinedWin() = withToast { scenario, toast, root, _ ->
        ui { toast.setRunActive(true); toast.show(250000) }
        awaitShown(scenario, toast)
        ui {
            assertTrue(amount(toast).text.toString().endsWith("+250,000"))
            assertEquals("real animation goes here", toast.findViewWithTag<TextView>("jackpot_title").text.toString())
            toast.setRunActive(false)
            assertEquals(View.INVISIBLE, card(toast).visibility)
            assertEquals(250000, field(toast, "pendingAmount").getInt(toast))
            toast.show(250000)
            assertEquals(500000, field(toast, "pendingAmount").getInt(toast))
            assertEquals(View.INVISIBLE, card(toast).visibility)
            toast.setRunActive(true)
        }
        awaitShown(scenario, toast)
        ui {
            assertTrue(amount(toast).text.toString().endsWith("+500,000"))
            val placement = toast.layoutParams
            root.removeView(toast)
            assertEquals(View.INVISIBLE, card(toast).visibility)
            assertEquals("Detach preserves the combined win without adding it twice", 500000, field(toast, "pendingAmount").getInt(toast))
            root.addView(toast, placement)
        }
        awaitShown(scenario, toast)
        ui {
            assertTrue(amount(toast).text.toString().endsWith("+500,000"))
            toast.reset()
            toast.setRunActive(true)
            assertEquals(0, field(toast, "currentAmount").getInt(toast))
            assertEquals(0, field(toast, "pendingAmount").getInt(toast))
        }
        SystemClock.sleep(5100)
        ui {
            assertEquals("A new run cannot resurrect an interrupted prior win", View.INVISIBLE, card(toast).visibility)
        }
    }

    @Test fun bannerRetiresAfterItsReadableHoldAndIgnoresInvalidAmounts() = withToast { scenario, toast, _, _ ->
        ui { toast.setRunActive(true); toast.show(0); toast.show(-1) }
        SystemClock.sleep(100)
        ui { assertEquals(View.INVISIBLE, card(toast).visibility); toast.show(250000) }
        awaitShown(scenario, toast)
        SystemClock.sleep(1900)
        ui { assertEquals("A win remains readable for its intended hold", View.VISIBLE, card(toast).visibility) }
        awaitUi(scenario, "The banner leaves without a tap", timeout = 2500) { card(toast).visibility == View.INVISIBLE }
        ui {
            assertEquals(0, field(toast, "currentAmount").getInt(toast))
            assertEquals(0, field(toast, "pendingAmount").getInt(toast))
        }
    }

    @Test fun narrowLargeFontAndBoostColumnFitWithoutStealingGameplayTouches() = withToast { scenario, toast, _, kit ->
        ui { toast.setRunActive(true); toast.show(250000) }
        awaitShown(scenario, toast)
        for (width in listOf(280f, 162f)) {
            ui {
                // 162dp is the remaining width on a280dp screen with BOOST's104dp column.
                toast.layoutParams = (toast.layoutParams as FrameLayout.LayoutParams).apply { this.width = kit.dp(width) }
            }
            awaitUi(scenario, "The requested narrow host is laid out") { toast.width == kit.dp(width) }
            ui {
                val body = card(toast)
                assertTrue(body.left >= 0 && body.right <= toast.width)
                val labels = descendants(body).filterIsInstance<TextView>()
                assertEquals(2, labels.size)
                for (label in labels) {
                    assertEquals("${label.text} stays one line", 1, label.lineCount)
                    assertEquals("${label.text} never drops digits", 0, label.layout.getEllipsisCount(0))
                    assertTrue("${label.text} fits the actual content width", label.layout.getLineWidth(0) <=
                        label.width - label.compoundPaddingLeft - label.compoundPaddingRight + 1f)
                    assertTrue("${label.text} has no clipped vertical metrics", label.layout.height <=
                        label.height - label.compoundPaddingTop - label.compoundPaddingBottom)
                    val rect = Rect(0, 0, label.width, label.height)
                    body.offsetDescendantRectToMyCoords(label, rect)
                    assertTrue(rect.left >= 0 && rect.right <= body.width && rect.top >= 0 && rect.bottom <= body.height)
                }
                val value = amount(toast)
                assertTrue("Even the BOOST-width award stays readable", value.textSize >= kit.dpf(16f))
                val spans = value.text as Spanned
                val coin = spans.getSpans(0, spans.length, CenteredImageSpan::class.java).single()
                assertEquals((value.textSize * 1.15f).toInt(), coin.drawable.bounds.width())
                assertTrue("The celebration offers no tap or claim action", descendants(toast).none {
                    it.isClickable || it.isLongClickable || it.isFocusable
                })
                val now = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(now, now, action, body.x + body.width / 2f, body.y + body.height / 2f, 0)
                    try { assertFalse("Gameplay receives touches through the jackpot", toast.dispatchTouchEvent(event)) }
                    finally { event.recycle() }
                }
            }
        }
    }
}
