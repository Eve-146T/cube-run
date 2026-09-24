package cube.run.response

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import cube.run.core.PhysicalAction
import cube.run.core.PhysicalInput
import org.junit.Assert.*
import org.junit.Test

class PhysicalInputTest {
    @Test fun keysMapToActionsWithoutRepeatingOrEatingSystemKeys() {
        val actions = ArrayList<PhysicalAction>()
        val input = PhysicalInput(actions::add)
        val groups = mapOf(
            PhysicalAction.LEFT to listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A),
            PhysicalAction.RIGHT to listOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D),
            PhysicalAction.UP to listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W),
            PhysicalAction.DOWN to listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S),
            PhysicalAction.CONFIRM to listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_BUTTON_A),
            PhysicalAction.BACK to listOf(KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_B),
            PhysicalAction.PAUSE to listOf(KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_BUTTON_START),
            PhysicalAction.BOOST to listOf(KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_BUTTON_X),
        )
        for ((expected, keys) in groups) for (key in keys) {
            actions.clear()
            assertTrue(input.key(KeyEvent(KeyEvent.ACTION_DOWN, key)))
            assertTrue(input.key(KeyEvent(0, 1, KeyEvent.ACTION_DOWN, key, 1)))
            assertTrue(input.key(KeyEvent(KeyEvent.ACTION_UP, key)))
            assertEquals(listOf(expected), actions)
        }
        for (key in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_BACK))
            assertFalse(input.key(KeyEvent(KeyEvent.ACTION_DOWN, key)))
    }

    @Test fun joystickUsesEdgesDeadZoneHistoryAndIndependentDevices() {
        val actions = ArrayList<PhysicalAction>()
        val input = PhysicalInput(actions::add)
        fun move(x: Float = 0f, y: Float = 0f, hatX: Float = 0f, hatY: Float = 0f, device: Int = 1) {
            joystick(x, y, hatX, hatY, device).let { assertTrue(input.motion(it)); it.recycle() }
        }
        move(x = .2f); assertTrue(actions.isEmpty())
        move(x = .8f); move(x = .45f); move(x = .6f)
        assertEquals(listOf(PhysicalAction.RIGHT), actions)
        move(); move(x = .8f); move(x = -.8f)
        move(hatY = -1f); move(hatY = -1f)
        move(hatY = 1f)
        move(hatY = 1f, device = 2)
        assertEquals(listOf(PhysicalAction.RIGHT, PhysicalAction.RIGHT, PhysicalAction.LEFT,
            PhysicalAction.UP, PhysicalAction.DOWN, PhysicalAction.DOWN), actions)
        input.reset(); move(hatY = 1f)
        assertEquals(7, actions.size)
        actions.clear()
        input.reset()
        val history = joystick(hatX = -1f)
        history.addBatch(2, arrayOf(MotionEvent.PointerCoords()), 0)
        assertTrue(input.motion(history)); history.recycle()
        assertEquals(listOf(PhysicalAction.LEFT), actions)
        move(hatX = -1f)
        assertEquals(2, actions.size) // the historical release rearmed the direction
        val mouse = joystick(x = 1f).apply { source = InputDevice.SOURCE_MOUSE }
        assertFalse(input.motion(mouse)); mouse.recycle()
    }

    companion object {
        fun joystick(x: Float = 0f, y: Float = 0f, hatX: Float = 0f, hatY: Float = 0f, device: Int = 1): MotionEvent {
            val coords = MotionEvent.PointerCoords().apply {
                setAxisValue(MotionEvent.AXIS_X, x); setAxisValue(MotionEvent.AXIS_Y, y)
                setAxisValue(MotionEvent.AXIS_HAT_X, hatX); setAxisValue(MotionEvent.AXIS_HAT_Y, hatY)
            }
            return MotionEvent.obtain(0, 1, MotionEvent.ACTION_MOVE, 1,
                arrayOf(MotionEvent.PointerProperties().apply { id = 0 }), arrayOf(coords),
                0, 0, 1f, 1f, device, 0, InputDevice.SOURCE_JOYSTICK, 0)
        }
    }
}
