package cube.run.response

import cube.run.core.gfx.TouchInput
import cube.run.core.gfx.TouchListener
import org.junit.Assert.*
import org.junit.Test

class TouchInputTest {
    private class Listener : TouchListener {
        val swipes = ArrayList<Int>()
        var taps = 0
        var drags = 0
        var smooth = false
        override fun onDown(x: Float, y: Float) {}
        override fun onDrag(x: Float, y: Float, dx: Float, dy: Float) { drags++ }
        override fun onUp(x: Float, y: Float) {}
        override fun onTap(x: Float, y: Float) { taps++ }
        override fun onSwipe(dir: Int) { swipes.add(dir) }
        override fun smoothSwipeEnabled() = smooth
    }

    @Test fun shortFlicksRecognizeAllDirectionsBeforeTheOldThreshold() {
        val l = Listener(); val input = TouchInput(l, { 720 }, { false })
        for ((dx, dy) in listOf(-40 to 0, 40 to 0, 0 to -40, 0 to 40)) {
            input.touchDown(360, 760, 0, 0)
            input.touchDragged(360 + dx, 760 + dy, 0)
            input.touchDragged(360 - dx * 3, 760 - dy * 3, 0)
            input.touchUp(360 + dx, 760 + dy, 0, 0)
        }
        assertEquals(listOf(TouchInput.LEFT, TouchInput.RIGHT, TouchInput.UP, TouchInput.DOWN), l.swipes)
        assertEquals(0, l.taps)
    }

    @Test fun releaseOnlyFlickIsRecognizedAndTapJitterStaysATap() {
        val l = Listener(); val input = TouchInput(l, { 720 }, { false })
        input.touchDown(360, 760, 0, 0)
        input.touchUp(310, 760, 0, 0)
        input.touchDown(360, 760, 0, 0)
        input.touchDragged(366, 754, 0)
        input.touchUp(369, 751, 0, 0)
        assertEquals(listOf(TouchInput.LEFT), l.swipes)
        assertEquals(1, l.taps)
    }

    @Test fun cancellationOtherPointersAndBlockedTouchesCannotCreateActions() {
        val l = Listener(); var blocked = false
        val input = TouchInput(l, { 720 }, { blocked })
        input.touchDown(360, 760, 0, 0)
        input.touchCancelled(360, 760, 0, 0)
        input.touchUp(200, 760, 0, 0)
        input.touchDragged(200, 760, 0)
        input.touchDown(360, 760, 1, 0)
        input.touchUp(200, 760, 1, 0)
        input.touchDown(360, 760, 0, 0)
        blocked = true
        input.touchDragged(200, 760, 0)
        blocked = false
        input.touchUp(200, 760, 0, 0)
        assertTrue(l.swipes.isEmpty()); assertEquals(0, l.taps)
    }

    @Test fun diagonalUsesDominantAxisAndSmoothModeKeepsItsOwnControls() {
        val l = Listener(); val input = TouchInput(l, { 720 }, { false })
        input.touchDown(360, 760, 0, 0)
        input.touchDragged(390, 710, 0)
        input.touchUp(390, 710, 0, 0)
        l.smooth = true
        input.touchDown(360, 760, 0, 0)
        input.touchDragged(600, 760, 0)
        input.touchUp(600, 760, 0, 0)
        assertEquals(listOf(TouchInput.UP), l.swipes)
        assertEquals(2, l.drags)
    }
}
