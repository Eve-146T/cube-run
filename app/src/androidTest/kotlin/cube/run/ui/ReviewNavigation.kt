package cube.run.ui

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.GameActivity

/** Capture helpers tap real controls; never attach pages or override their state. */
internal class ReviewNavigation(private val scenario: ActivityScenario<GameActivity>) {
    private fun find(view: View, matches: (View) -> Boolean): View? {
        // A visible child can still sit under the launch touch blocker while
        // its HUD fades in. Wait for ancestors as well as the control itself.
        if (!view.isShown || view.alpha < .99f) return null
        if (matches(view)) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            find(view.getChildAt(i), matches)?.let { return it }
        }
        return null
    }

    fun tap(descriptionId: Int) = tapMatching("description resource $descriptionId") { activity, view ->
        view.contentDescription?.toString() == activity.getString(descriptionId)
    }

    fun tapText(text: String) = tapMatching(text) { _, view ->
        view is TextView && view.isClickable && view.text.toString() == text
    }

    private fun tapMatching(label: String, matches: (GameActivity, View) -> Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val point = IntArray(2)
        val deadline = SystemClock.uptimeMillis() + 8000
        var ready = false
        do {
            scenario.onActivity { activity ->
                val control = find(activity.window.decorView) { matches(activity, it) }
                if (control != null && control.width > 0 && control.height > 0) {
                    control.getLocationOnScreen(point)
                    point[0] += control.width / 2; point[1] += control.height / 2
                    ready = true
                }
            }
            if (!ready) SystemClock.sleep(100)
        } while (!ready && SystemClock.uptimeMillis() < deadline)
        check(ready) { "Missing visible review control: $label" }
        val down = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point[0].toFloat(), point[1].toFloat(), 0)
            instrumentation.sendPointerSync(event)
            event.recycle()
            SystemClock.sleep(60)
        }
        instrumentation.waitForIdleSync()
    }
}
