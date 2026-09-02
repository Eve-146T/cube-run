package cube.run.core.gfx

import com.badlogic.gdx.InputAdapter
import kotlin.math.abs

/** What a game hears from [TouchInput]. All callbacks arrive on the GL thread. */
interface TouchListener {
    fun onDown(x: Float, y: Float)
    fun onDrag(x: Float, y: Float, dx: Float, dy: Float)
    fun onUp(x: Float, y: Float)
    fun onTap(x: Float, y: Float)
    /** [dir] is one of [TouchInput.LEFT] / [RIGHT] / [UP] / [DOWN]. */
    fun onSwipe(dir: Int)

    /**
     * When true, swipes fire continuously within a single touch: every time the
     * finger travels far enough from the last fired point, another [onSwipe] is
     * emitted (so you can steer left/right/left without lifting). When false, a
     * touch yields at most one swipe (the classic flick).
     */
    fun smoothSwipeEnabled(): Boolean = false
}

/**
 * Single-pointer touch → down / drag / up / tap / swipe. Thresholds scale with
 * the screen width ([swipeDist], [tapSlop]); [blocked] gates everything (used
 * to ignore touches once the game is over).
 */
class TouchInput(
    private val listener: TouchListener,
    private val screenWidth: () -> Int,
    private val blocked: () -> Boolean,
) : InputAdapter() {

    companion object {
        const val LEFT = 0
        const val RIGHT = 1
        const val UP = 2
        const val DOWN = 3
    }

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downAt = 0L
    private var swiped = false
    private val swipeDist get() = screenWidth() * 0.085f
    private val tapSlop get() = screenWidth() * 0.03f

    override fun touchDown(x: Int, y: Int, pointer: Int, button: Int): Boolean {
        if (pointer != 0 || blocked()) return false
        downX = x.toFloat(); downY = y.toFloat()
        lastX = downX; lastY = downY
        downAt = System.currentTimeMillis()
        swiped = false
        listener.onDown(downX, downY)
        return true
    }

    override fun touchDragged(x: Int, y: Int, pointer: Int): Boolean {
        if (pointer != 0 || blocked()) return false
        val fx = x.toFloat(); val fy = y.toFloat()
        listener.onDrag(fx, fy, fx - lastX, fy - lastY)
        lastX = fx; lastY = fy
        // smooth mode: the game interprets the drag positionally (in onDrag).
        // classic mode: a single flick per touch.
        if (!listener.smoothSwipeEnabled() && !swiped) {
            val dx = fx - downX
            val dy = fy - downY
            if (abs(dx) > swipeDist || abs(dy) > swipeDist) {
                swiped = true
                listener.onSwipe(
                    if (abs(dx) > abs(dy)) { if (dx > 0) RIGHT else LEFT }
                    else { if (dy > 0) DOWN else UP },
                )
            }
        }
        return true
    }

    override fun touchUp(x: Int, y: Int, pointer: Int, button: Int): Boolean {
        if (pointer != 0 || blocked()) return false
        val fx = x.toFloat(); val fy = y.toFloat()
        listener.onUp(fx, fy)
        if (!swiped && abs(fx - downX) < tapSlop && abs(fy - downY) < tapSlop &&
            System.currentTimeMillis() - downAt < 350
        ) {
            listener.onTap(fx, fy)
        }
        return true
    }
}
