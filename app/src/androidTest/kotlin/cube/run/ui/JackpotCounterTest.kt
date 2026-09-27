package cube.run.ui

import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.GameActivity
import cube.run.core.JackpotBeats
import cube.run.core.Stage
import org.junit.Assert.*
import org.junit.Test

/** The counter only follows the GL-owned clock: no award, draw or purchase happens here. */
class JackpotCounterTest {
    private fun ui(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun badge(counter: JackpotCounter) = counter.findViewWithTag<View>("jackpot_badge")

    private fun withCounter(test: (JackpotCounter, UiKit) -> Unit) {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            lateinit var root: FrameLayout
            lateinit var counter: JackpotCounter
            lateinit var kit: UiKit
            scenario.onActivity { activity ->
                kit = UiKit(activity)
                root = activity.findViewById(android.R.id.content)
                counter = JackpotCounter(activity, kit, { PointF(kit.dpf(100f), kit.dpf(90f)) })
                root.addView(counter, FrameLayout.LayoutParams(-1, -1))
            }
            try { test(counter, kit) }
            finally {
                ui { counter.reset(); root.removeView(counter) }
                Stage.jackpotClock = -1f; Stage.jackpotAmount = 0
            }
        }
    }

    private fun at(t: Float) { Stage.jackpotClock = t; SystemClock.sleep(120) }

    @Test fun followsTheShowClockAndNeverTakesATouch() = withCounter { counter, kit ->
        Stage.jackpotAmount = 250_000
        at(.2f)
        ui { counter.setRunActive(true); counter.show() }
        SystemClock.sleep(120)
        ui { assertEquals("Hidden until the rays blow open", View.INVISIBLE, badge(counter).visibility) }
        at(JackpotBeats.BURST + 1f)
        ui {
            assertEquals(View.VISIBLE, badge(counter).visibility)
            assertTrue("Still inside the screen", badge(counter).measuredWidth <= counter.width)
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, counter.width / 2f, counter.height * .66f, 0)
            try { assertFalse("Gameplay receives touches through the counter", counter.dispatchTouchEvent(down)) }
            finally { down.recycle() }
        }
        at(JackpotBeats.SLAM + .1f)
        ui { assertEquals("Jackpot. 250,000 coins.", badge(counter).contentDescription?.toString()) }
        ui { counter.setRunActive(false) }
        ui { assertEquals("The pause card hides it", View.INVISIBLE, badge(counter).visibility) }
        ui { counter.setRunActive(true) }
        at(JackpotBeats.RETURN + .3f)
        ui {
            assertEquals("Resumes with the frozen show", View.VISIBLE, badge(counter).visibility)
            assertTrue("Shrinking toward the coin pill", badge(counter).scaleX < 1f)
        }
        at(-1f)
        ui { assertEquals("Gone when the show ends", View.INVISIBLE, badge(counter).visibility) }
    }

    @Test fun rollIsMonotonicAndLandsExactlyOnTheSlam() {
        var last = 0
        var t = 0f
        while (t < JackpotBeats.END) {
            val v = JackpotBeats.rolled(1_000_000, t)
            assertTrue(v >= last)
            last = v
            t += 1f / 60f
        }
        assertEquals(0, JackpotBeats.rolled(1_000_000, JackpotBeats.BURST - .01f))
        assertEquals(1_000_000, JackpotBeats.rolled(1_000_000, JackpotBeats.SLAM))
        assertEquals(Int.MAX_VALUE, JackpotBeats.rolled(Int.MAX_VALUE, JackpotBeats.SLAM))
    }
}
