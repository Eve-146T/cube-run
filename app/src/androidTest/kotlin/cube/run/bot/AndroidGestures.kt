package cube.run.bot

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry

/** Goes through Android's input dispatcher, the SurfaceView and libGDX's normal touch queue. */
internal object AndroidGestures {
    fun flick(action: Int, width: Int, height: Int, sync: Boolean = true): Boolean {
        if (action == Action.NONE) return true
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val now = SystemClock.uptimeMillis()
        val x = width * .5f; val y = height * .58f; val travel = width * .13f
        val dx = when (action) { Action.LEFT -> -travel; Action.RIGHT -> travel; else -> 0f }
        val dy = when (action) { Action.JUMP -> -travel; Action.DOWN -> travel; else -> 0f }
        var accepted = true
        for (kind in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), kind,
                x + if (kind == MotionEvent.ACTION_DOWN) 0f else dx,
                y + if (kind == MotionEvent.ACTION_DOWN) 0f else dy, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try { accepted = automation.injectInputEvent(event, sync) && accepted } finally { event.recycle() }
        }
        return accepted
    }
}
