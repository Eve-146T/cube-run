package cube.run.ui

import android.view.View

/** Use the available pane, never the physical display, to select content-first layouts. */
internal object CompactLayout {
    fun uses(heightPx: Int, density: Float, top: Int = 0, bottom: Int = 0): Boolean =
        heightPx - top - bottom < 480f * density
    fun uses(view: View, heightPx: Int, top: Int = 0, bottom: Int = 0) =
        uses(heightPx, view.resources.displayMetrics.density, top, bottom)
}

/** Scrolling must not swallow the page-wide tap-to-continue gesture. */
internal class TapScrollView(context: android.content.Context, private val onTap: () -> Unit) : android.widget.ScrollView(context) {
    private var downX = 0f
    private var downY = 0f
    private var dragged = false
    private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; dragged = false }
            android.view.MotionEvent.ACTION_MOVE -> if (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop) dragged = true
            android.view.MotionEvent.ACTION_UP -> if (!dragged) { onTap(); super.onTouchEvent(event); return true }
            android.view.MotionEvent.ACTION_CANCEL -> dragged = true
        }
        return super.onTouchEvent(event)
    }
}
