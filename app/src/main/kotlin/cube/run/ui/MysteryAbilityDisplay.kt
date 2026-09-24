package cube.run.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.ScrollView
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.data.Skins
import cube.run.R
import cube.run.ui.Anim.move

/** An actual disclosure control that keeps the void's ability a mystery in every preview. */
@SuppressLint("SetTextI18n")
class MysteryAbilityDisplay(
    context: Context,
    private val kit: UiKit,
    private val canToggle: () -> Boolean = { true },
    private val onExpandedChanged: (Boolean) -> Unit = {},
    private val revealTarget: View? = null,
) {
    var expanded = false
        private set
    private var pendingReveal: ViewTreeObserver.OnPreDrawListener? = null

    val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false; clipToPadding = false
        setPadding(kit.dp(16f), kit.dp(12f), kit.dp(16f), kit.dp(16f))
        background = kit.cardDrawable(Theme.WHITE, null, 20f)
        visibility = View.GONE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "??? ??? ???"
        addView(kit.text("???", 15f, Theme.INK, 500, Gravity.START).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                pendingReveal?.let { v.viewTreeObserver.removeOnPreDrawListener(it) }
                pendingReveal = null
                Anim.reset(v)
                v.visibility = if (expanded) View.VISIBLE else View.GONE
            }
        })
    }

    val button = CandyChip(context, Theme.WHITE, kit.dpf(4f), kit.dpf(16f)).apply {
        setImageDrawable(AbilityIcon(Skins.Ability.SECRET))
        setPadding(kit.dp(10f), kit.dp(10f), kit.dp(10f), kit.dp(10f))
        contentDescription = context.getString(R.string.cd_show_unknown_ability)
        setOnClickListener { toggle() }
    }

    fun toggle() {
        if (!canToggle()) return
        expanded = !expanded
        button.isSelected = expanded
        button.color = if (expanded) Theme.LAVENDER else Theme.WHITE
        button.contentDescription = button.context.getString(if (expanded) R.string.cd_hide_unknown_ability else R.string.cd_show_unknown_ability)
        SoundFx.play("tap")
        Haptics.click()
        pendingReveal?.let { panel.viewTreeObserver.removeOnPreDrawListener(it) }
        pendingReveal = null
        Anim.reset(panel)
        if (expanded) {
            panel.visibility = View.VISIBLE
            panel.pivotX = 0f
            panel.pivotY = kit.dpf(24f)
            panel.alpha = 0f
            panel.scaleX = .92f
            panel.scaleY = .96f
            panel.translationX = -kit.dpf(10f)
            panel.move().alpha(1f).scaleX(1f).scaleY(1f).translationX(0f)
                .setDuration(260).setInterpolator(Anim.springSoft).start()
            revealWithinViewport()
        } else {
            panel.move().alpha(0f).scaleX(.94f).scaleY(.97f).translationX(-kit.dpf(8f)).setDuration(120).withEndAction {
                panel.visibility = View.GONE
                Anim.reset(panel)
            }.start()
        }
        onExpandedChanged(expanded)
        panel.requestLayout()
        Anim.repaint(panel)
    }

    /** A bottom-of-shop disclosure must not unfold below the visible edge. */
    private fun revealWithinViewport() {
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                panel.viewTreeObserver.removeOnPreDrawListener(this)
                if (pendingReveal !== this) return true
                pendingReveal = null
                if (!expanded || !panel.isAttachedToWindow) return true
                val iconPosition = IntArray(2)
                val panelPosition = IntArray(2)
                button.getLocationOnScreen(iconPosition)
                panel.getLocationOnScreen(panelPosition)
                val target = revealTarget
                if (target != null && target.isAttachedToWindow) {
                    var ancestor = target.parent
                    while (ancestor != null && ancestor !is ScrollView) ancestor = ancestor.parent
                    val viewport = (ancestor as? ScrollView)?.let { it.height - it.paddingTop - it.paddingBottom }
                    val targetPosition = IntArray(2)
                    target.getLocationOnScreen(targetPosition)
                    // Keep the entire shop card visible when possible. On very short screens,
                    // prioritize the revealed answer and the purchase action beneath it.
                    val top = if (viewport == null || target.height <= viewport - kit.dp(16f)) 0
                        else (panelPosition[1] - targetPosition[1] - kit.dp(8f)).coerceAtLeast(0)
                    target.requestRectangleOnScreen(Rect(0, top, target.width, target.height + kit.dp(8f)), false)
                    return true
                }
                // Include the persistent button so scrolling never separates it from its answer.
                panel.requestRectangleOnScreen(Rect(0,
                    minOf(0, iconPosition[1] - panelPosition[1]) - kit.dp(8f),
                    panel.width, panel.height + kit.dp(8f)), false)
                return true
            }
        }
        pendingReveal = listener
        panel.viewTreeObserver.addOnPreDrawListener(listener)
    }
}
