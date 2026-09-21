package cube.run.ui

import android.app.Activity
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import cube.run.R
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.data.Languages

/** A separate language menu, using the same card and motion as the pause sheet. */
@SuppressLint("ViewConstructor")
class LanguageSheet(
    activity: Activity,
    kit: UiKit,
    onSelected: (String) -> Unit,
    onDismissed: () -> Unit,
) : Sheet(activity, kit, onDismissed) {
    private var outsideTouch = false

    // Include the scroll container's padding in the backdrop; consume the whole gesture.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val bounds = android.graphics.Rect()
            card.getGlobalVisibleRect(bounds)
            outsideTouch = !bounds.contains(event.rawX.toInt(), event.rawY.toInt())
        }
        if (outsideTouch) {
            if (event.actionMasked == MotionEvent.ACTION_UP) { outsideTouch = false; dismiss() }
            if (event.actionMasked == MotionEvent.ACTION_CANCEL) outsideTouch = false
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    init {
        accessibilityPaneTitle = activity.getString(R.string.languages_title)
        // Keep the card reachable on small displays and with larger system text.
        removeView(card)
        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            clipToPadding = false
            setPadding(dp(16f), dp(24f), dp(16f), dp(24f))
            addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(scroll, LayoutParams(dp(368f), LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        setOnApplyWindowInsetsListener { _, insets ->
            val (l, t, r, b) = insetsOf(insets)
            setPadding(l, t, r, b)
            insets
        }
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val available = width - paddingLeft - paddingRight
            val target = minOf(dp(368f), available)
            if (target > 0 && scroll.layoutParams.width != target) {
                scroll.layoutParams = (scroll.layoutParams as LayoutParams).apply { width = target }
            }
        }
        card.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            addView(kit.backButton { dismiss() }, LinearLayout.LayoutParams(dp(46f), dp(50f)))
            addView(kit.text(activity.getString(R.string.languages_title), 26f, Theme.INK, 700, Gravity.START),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12f) })
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(20f) })

        val current = Languages.current(activity)
        for (option in Languages.options) {
            val selected = option.code == current
            val cost = Languages.switchCost(current, option.code)
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(72f)
                setPaddingRelative(dp(14f), dp(14f), dp(12f), dp(14f) + kit.CARD_LIP)
                background = RippleDrawable(ColorStateList.valueOf(Theme.alpha(Theme.GRAPE, 28)),
                    kit.cardDrawable(if (selected) Theme.lighten(Theme.MINT, 0.82f) else Theme.CARD_ALT,
                        if (selected) Theme.MINT else null, 20f),
                    GradientDrawable().apply { cornerRadius = dpf(20f); setColor(Theme.WHITE) })
                isSelected = selected
                isFocusable = true
                contentDescription = activity.getString(R.string.language_option, option.nativeName, activity.getString(option.country))
                if (cost > 0) contentDescription = "$contentDescription, ${activity.getString(R.string.language_switch_cost, cost)}"
                accessibilityDelegate = object : View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = "android.widget.RadioButton"
                        info.isCheckable = true
                        info.isChecked = selected
                    }
                }
                setOnClickListener {
                    SoundFx.play("tap"); Haptics.click()
                    if (!selected) onSelected(option.code)
                }
            }
            row.addView(ImageView(activity).apply {
                setImageResource(option.flag)
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(42f), dp(28f)))
            row.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                addView(kit.text(option.nativeName, 21f, Theme.INK, 600, Gravity.START).apply {
                    textDirection = View.TEXT_DIRECTION_FIRST_STRONG
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                if (cost > 0) addView(kit.text(activity.getString(R.string.language_switch_cost, cost), 13f, Theme.INK_SOFT, 600, Gravity.START))
            }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12f); marginEnd = dp(8f) })
            row.addView(SelectionMark(activity, kit, selected), LinearLayout.LayoutParams(dp(24f), dp(24f)))
            card.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                if (option != Languages.options.first()) topMargin = dp(12f)
            })
        }
    }

    private class SelectionMark(activity: Activity, kit: UiKit, private val selected: Boolean) : View(activity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val check = Path()
        private val stroke = kit.dpf(2f)
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height / 2f
            paint.style = if (selected) Paint.Style.FILL else Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = if (selected) Theme.MINT else Theme.alpha(Theme.INK_SOFT, 80)
            canvas.drawCircle(cx, cy, minOf(cx, cy) - stroke, paint)
            if (selected) {
                paint.style = Paint.Style.STROKE; paint.color = Theme.INK
                paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
                check.reset(); check.moveTo(width * 0.29f, height * 0.5f)
                check.lineTo(width * 0.44f, height * 0.65f); check.lineTo(width * 0.72f, height * 0.35f)
                canvas.drawPath(check, paint)
            }
        }
    }
}
