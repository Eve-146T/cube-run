package cube.run.ui

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.roundToInt

/**
 * A medal family's ladder across the card: one bar from the start to the diamond, with the four
 * badges sitting on it as evenly spaced checkpoints. The bar fills in the achievement's colour as
 * far as it has got (each stretch between two badges is one tier, however big its numbers), so a
 * badge is reached exactly when the fill arrives under it.
 */
@SuppressLint("ViewConstructor")
internal class AchievementTrack(context: Context, private val bar: android.view.View, val nodes: List<ImageView>,
    private val node: Int, private val barHeight: Int) : FrameLayout(context) {
    init {
        clipChildren = false; clipToPadding = false
        addView(bar)
        for (n in nodes) addView(n)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val exactly = { size: Int -> MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY) }
        for (n in nodes) n.measure(exactly(node), exactly(node))
        // The bar ends under the middle of the last badge: full means the last one is reached.
        bar.measure(exactly(width - node / 2), exactly(barHeight))
        setMeasuredDimension(width, node)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val barTop = (node - barHeight) / 2
        if (rtl) bar.layout(width - bar.measuredWidth, barTop, width, barTop + barHeight)
        else bar.layout(0, barTop, bar.measuredWidth, barTop + barHeight)
        val reach = width - node / 2f
        for ((i, n) in nodes.withIndex()) {
            val centre = reach * (i + 1) / nodes.size
            val start = ((if (rtl) width - centre else centre) - node / 2f).roundToInt()
            n.layout(start, 0, start + node, node)
        }
    }
}
