package cube.run.ui

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.FocusFinder
import android.view.View
import android.view.ViewGroup
import cube.run.core.PhysicalAction

/** Constrain hardware focus to the topmost menu, including custom clickable views. */
class HardwareNavigation {
    private var selected: View? = null
    private var originalForeground: Drawable? = null

    fun clear() {
        selected?.let { it.foreground = originalForeground; it.clearFocus() }
        selected = null
        originalForeground = null
    }

    fun target(root: ViewGroup): View? = selected?.takeIf { view ->
        view.isShown && view.isEnabled && generateSequence(view as View?) { it.parent as? View }.any { it === root }
    }.also { if (it == null) clear() }

    fun move(root: ViewGroup, action: PhysicalAction, start: View? = null) {
        val candidates = ArrayList<View>()
        fun collect(v: View) {
            if (!v.isShown || !v.isEnabled) return
            if (v.hasOnClickListeners() || v === start) {
                v.isFocusableInTouchMode = true
                candidates.add(v)
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i))
        }
        collect(root)
        val direction = when (action) {
            PhysicalAction.LEFT -> View.FOCUS_LEFT
            PhysicalAction.RIGHT -> View.FOCUS_RIGHT
            PhysicalAction.UP -> View.FOCUS_UP
            else -> View.FOCUS_DOWN
        }
        val current = target(root)
        val next = if (current == null) start ?: candidates.firstOrNull()
            else FocusFinder.getInstance().findNextFocus(root, current, direction)?.takeIf { it in candidates }
        if (next == null || next === current) return
        clear()
        selected = next
        originalForeground = next.foreground
        val density = next.resources.displayMetrics.density
        fun outline(color: Int, width: Float) = GradientDrawable().apply {
            cornerRadius = 14f * density
            setColor(android.graphics.Color.TRANSPARENT)
            setStroke((width * density).toInt(), color)
        }
        val ring = LayerDrawable(arrayOf(outline(Theme.INK, 5f), outline(Theme.WHITE, 2f)))
        next.foreground = originalForeground?.let { LayerDrawable(arrayOf(it, ring)) } ?: ring
        next.requestFocusFromTouch()
        next.requestRectangleOnScreen(Rect(0, 0, next.width, next.height))
    }
}
