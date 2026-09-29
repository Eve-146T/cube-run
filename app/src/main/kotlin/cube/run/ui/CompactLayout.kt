package cube.run.ui

import android.view.View

/** Use the available pane, never the physical display, to select content-first layouts. */
internal object CompactLayout {
    fun uses(heightPx: Int, density: Float, top: Int = 0, bottom: Int = 0): Boolean =
        heightPx - top - bottom < 480f * density
    fun uses(view: View, heightPx: Int, top: Int = 0, bottom: Int = 0) =
        uses(heightPx, view.resources.displayMetrics.density, top, bottom)
}
