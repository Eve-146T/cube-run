package cube.run.core

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import cube.run.core.gfx.TouchInput
import kotlin.math.abs

enum class PhysicalAction(val swipe: Int? = null) {
    LEFT(TouchInput.LEFT), RIGHT(TouchInput.RIGHT), UP(TouchInput.UP), DOWN(TouchInput.DOWN),
    CONFIRM, BACK, PAUSE, BOOST,
}

/** UI-thread input decoder. One press/axis excursion produces one discrete action. */
class PhysicalInput(private val action: (PhysicalAction) -> Unit) {
    private val axes = HashMap<Int, IntArray>()

    fun key(event: KeyEvent): Boolean {
        val mapped = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> PhysicalAction.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> PhysicalAction.RIGHT
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> PhysicalAction.UP
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> PhysicalAction.DOWN
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_BUTTON_A -> PhysicalAction.CONFIRM
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_B -> PhysicalAction.BACK
            KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_BUTTON_START -> PhysicalAction.PAUSE
            KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_BUTTON_X -> PhysicalAction.BOOST
            else -> return false
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && !event.isCanceled) action(mapped)
        return true
    }

    fun motion(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.actionMasked != MotionEvent.ACTION_MOVE) return false
        val state = axes.getOrPut(event.deviceId) { IntArray(2) }
        fun sample(history: Int) {
            fun axis(id: Int): Float {
                val v = if (history < 0) event.getAxisValue(id) else event.getHistoricalAxisValue(id, history)
                val flat = event.device?.getMotionRange(id, event.source)?.flat ?: 0f
                return if (abs(v) <= flat) 0f else v
            }
            for (i in 0..1) {
                val hat = axis(if (i == 0) MotionEvent.AXIS_HAT_X else MotionEvent.AXIS_HAT_Y)
                val stick = axis(if (i == 0) MotionEvent.AXIS_X else MotionEvent.AXIS_Y)
                val v = if (abs(hat) > .5f) hat else stick
                // Hysteresis prevents a noisy stick from retriggering at the threshold.
                val next = when {
                    v <= -.5f -> -1
                    v >= .5f -> 1
                    abs(v) <= .25f -> 0
                    else -> state[i]
                }
                if (next != 0 && next != state[i]) action(when {
                    i == 0 && next < 0 -> PhysicalAction.LEFT
                    i == 0 -> PhysicalAction.RIGHT
                    next < 0 -> PhysicalAction.UP
                    else -> PhysicalAction.DOWN
                })
                state[i] = next
            }
        }
        for (h in 0 until event.historySize) sample(h)
        sample(-1)
        return true
    }

    fun reset() = axes.clear()
}
