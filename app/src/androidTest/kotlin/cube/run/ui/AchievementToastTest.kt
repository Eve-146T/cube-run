package cube.run.ui

import android.content.res.Configuration
import android.graphics.Rect
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import cube.run.GameActivity
import cube.run.data.Achievements
import org.junit.Assert.*
import org.junit.Test

/** Component coverage uses real window attachment and callbacks without changing the player's save. */
class AchievementToastTest {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun card(toast: AchievementToast) = field(toast, "card").get(toast) as ViewGroup
    @Suppress("UNCHECKED_CAST")
    private fun pending(toast: AchievementToast) = field(toast, "pending").get(toast) as Map<String, Achievements.Unlock>
    private fun award(id: String, tier: Int) = Achievements.Unlock(Achievements.all.single { it.id == id }, tier)
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun awaitShown(scenario: ActivityScenario<GameActivity>, toast: AchievementToast, timeout: Long = 14000L) {
        val deadline = SystemClock.uptimeMillis() + timeout
        var shown = false
        while (!shown && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { shown = card(toast).visibility == View.VISIBLE && card(toast).alpha >= .99f }
            if (!shown) SystemClock.sleep(40)
        }
        assertTrue("The scheduled award becomes visible", shown)
    }

    private fun withToast(
        fontScale: Float = 1f,
        widthDp: Float? = null,
        test: (ActivityScenario<GameActivity>, AchievementToast, FrameLayout) -> Unit,
    ) {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            lateinit var toast: AchievementToast
            lateinit var root: FrameLayout
            scenario.onActivity { activity ->
                val configuration = Configuration(activity.resources.configuration).apply { this.fontScale = fontScale }
                val kit = UiKit(activity.createConfigurationContext(configuration))
                toast = AchievementToast(activity, kit)
                root = activity.findViewById(android.R.id.content)
                root.addView(toast, FrameLayout.LayoutParams(widthDp?.let(kit::dp) ?: -1, -2).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = kit.dp(32f)
                })
            }
            try { test(scenario, toast, root) }
            finally {
                scenario.onActivity {
                    root.removeView(toast)
                    toast.reset()
                }
            }
        }
    }

    @Test fun pauseAndResumePreserveNewestFamilyTierWithQuietSpacing() = withToast { scenario, toast, _ ->
        var activatedAt = 0L
        scenario.onActivity {
            toast.enqueue(listOf(award("runner", 0)))
            activatedAt = SystemClock.uptimeMillis()
            toast.setRunActive(true)
            assertEquals(View.INVISIBLE, card(toast).visibility)
        }
        awaitShown(scenario, toast)
        val firstShownAt = SystemClock.uptimeMillis()
        assertTrue("A new run keeps its 2.2 second opening grace", firstShownAt - activatedAt >= 2200L)
        scenario.onActivity {
            assertTrue(card(toast).contentDescription.toString().contains("Bronze"))
            toast.enqueue(listOf(award("runner", 1), award("coins", 0)))
            toast.setRunActive(false)
            assertEquals(View.INVISIBLE, card(toast).visibility)
            assertEquals(listOf("runner", "coins"), pending(toast).keys.toList())
            assertEquals("An interrupted lower tier never replaces the newer award", 1, pending(toast).getValue("runner").tier)
            toast.setRunActive(true)
            assertEquals(View.INVISIBLE, card(toast).visibility)
        }
        SystemClock.sleep(700)
        scenario.onActivity { assertEquals("Resume cannot immediately repeat a popup", View.INVISIBLE, card(toast).visibility) }
        awaitShown(scenario, toast)
        assertTrue("The resumed award respects the 10 second spacing", SystemClock.uptimeMillis() - firstShownAt >= 9500L)
        scenario.onActivity {
            assertTrue("The resumed card presents the newest tier", card(toast).contentDescription.toString().contains("Silver"))
            assertEquals("One card represents all runner tiers", listOf("coins"), pending(toast).keys.toList())
        }
    }

    @Test fun actualDetachPreservesNewestTierAndResetDiscardsOldRunAwards() = withToast { scenario, toast, root ->
        scenario.onActivity {
            toast.enqueue(listOf(award("runner", 0)))
            toast.setRunActive(true)
        }
        awaitShown(scenario, toast)
        scenario.onActivity {
            toast.enqueue(listOf(award("runner", 3), award("boxes", 0)))
            val placement = toast.layoutParams
            root.removeView(toast)
            assertFalse(toast.isAttachedToWindow)
            assertEquals(View.INVISIBLE, card(toast).visibility)
            assertEquals(listOf("runner", "boxes"), pending(toast).keys.toList())
            assertEquals(3, pending(toast).getValue("runner").tier)
            root.addView(toast, placement)
            assertTrue(toast.isAttachedToWindow)
            assertEquals("Reattachment keeps the spacing before resuming", View.INVISIBLE, card(toast).visibility)
            toast.reset()
            assertTrue("Starting another run drops all prior-run awards", pending(toast).isEmpty())
            toast.setRunActive(true)
        }
        SystemClock.sleep(2500)
        scenario.onActivity {
            assertEquals("A cancelled detached callback cannot resurrect an old award", View.INVISIBLE, card(toast).visibility)
            assertTrue(pending(toast).isEmpty())
        }
    }

    @Test fun narrowLargeTextCardFitsAndLetsGameplayTouchesThrough() = withToast(fontScale = 1.5f, widthDp = 280f) { scenario, toast, _ ->
        scenario.onActivity {
            toast.enqueue(listOf(award("powerups", 3)))
            toast.setRunActive(true)
        }
        awaitShown(scenario, toast)
        scenario.onActivity {
            val body = card(toast)
            assertTrue("Toast stays inside the 280dp host", body.left >= 0 && body.right <= toast.width)
            assertTrue(body.height <= toast.height)
            val labels = descendants(body).filterIsInstance<TextView>()
            assertEquals("The popup contains only its title and coin amount", 2, labels.size)
            for (label in labels) {
                assertEquals("${label.text} stays on one line", 1, label.lineCount)
                assertEquals("${label.text} is fully readable", 0, label.layout.getEllipsisCount(0))
                assertTrue("${label.text} glyphs fit the actual width", label.layout.getLineWidth(0) <=
                    label.width - label.compoundPaddingLeft - label.compoundPaddingRight + 1f)
                assertTrue("${label.text} has no vertical clipping", label.layout.height <=
                    label.height - label.compoundPaddingTop - label.compoundPaddingBottom)
                val bounds = Rect()
                label.getDrawingRect(bounds)
                body.offsetDescendantRectToMyCoords(label, bounds)
                assertTrue("${label.text} remains within the card", bounds.left >= 0 && bounds.top >= 0 &&
                    bounds.right <= body.width && bounds.bottom <= body.height)
            }
            assertFalse(labels.any { it.text.toString().contains("Ready to claim", ignoreCase = true) })
            assertTrue("No part of a running achievement offers a tap action", descendants(toast).none {
                it.isClickable || it.isLongClickable || it.isFocusable
            })
            val now = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(now, now, action, body.x + body.width / 2f, body.y + body.height / 2f, 0)
                try { assertFalse("Achievement cards leave gameplay touch handling intact", toast.dispatchTouchEvent(event)) }
                finally { event.recycle() }
            }
        }
    }
}
