package cube.run.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import cube.run.ui.Anim.move

/** Celebrates coins already earned by the run; this view never awards or spends anything. */
@SuppressLint("ViewConstructor")
class JackpotToast(activity: Activity, private val kit: UiKit) : FrameLayout(activity) {
    private var active = false
    private var currentAmount = 0
    private var pendingAmount = 0
    private val title = kit.text("JACKPOT", 13f, Theme.INK, 700).apply {
        tag = "jackpot_title"
        letterSpacing = .13f
        setSingleLine(); setHorizontallyScrolling(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val amount = kit.text("", 24f, Theme.INK, 700).apply {
        tag = "jackpot_amount"
        setSingleLine(); setHorizontallyScrolling(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var labelChanged = false
    private val card = object : LinearLayout(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight - kit.dp(2f)).coerceAtLeast(1)
            fit(title, available, 13f) { "JACKPOT" }
            fit(amount, available, 24f, ::amountLabel)
            labelChanged = false
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply {
        tag = "jackpot_card"
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = kit.cardDrawable(Theme.GOLD, null, 22f)
        setPadding(kit.dp(16f), kit.dp(9f), kit.dp(16f), kit.dp(12f) + kit.CARD_LIP)
        addView(title, LinearLayout.LayoutParams(-1, -2))
        addView(amount, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(2f) })
        visibility = INVISIBLE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val reveal = Runnable { showPending() }
    private val retreat = Runnable {
        card.move().alpha(0f).translationY(-kit.dpf(6f)).setDuration(200).withEndAction {
            currentAmount = 0
            card.visibility = INVISIBLE
            schedule()
        }.start()
    }

    init {
        isClickable = false; isFocusable = false
        clipChildren = false; clipToPadding = false
        addView(card, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Air around the spring keeps the rounded corners inside narrow hosts.
        card.layoutParams.width = (MeasureSpec.getSize(widthMeasureSpec) - kit.dp(12f)).coerceIn(1, kit.dp(252f))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun fit(view: TextView, width: Int, sizeSp: Float, label: (Float) -> CharSequence) {
        val paint = TextPaint(view.paint)
        val nominal = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, view.resources.displayMetrics)
        fun fits(px: Float): Boolean { paint.textSize = px; return Layout.getDesiredWidth(label(px), paint) <= width }
        var low = 1f
        var high = nominal
        if (fits(nominal)) low = nominal else repeat(16) {
            val candidate = (low + high) / 2f
            if (fits(candidate)) low = candidate else high = candidate
        }
        if (labelChanged || kotlin.math.abs(view.textSize - low) > .01f) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, low)
            view.text = label(low)
        }
    }

    private fun amountLabel(px: Float): CharSequence {
        val coin = CoinIcon().apply {
            val edge = (px * 1.15f).toInt().coerceAtLeast(1)
            setBounds(0, 0, edge, edge)
        }
        return SpannableStringBuilder("\u2009").apply {
            append(" ", CenteredImageSpan(coin), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append("\u2009+").append(number(currentAmount))
        }
    }

    /** Coalesce simultaneous wins instead of stacking banners. */
    fun show(amount: Int) {
        if (amount <= 0) return
        pendingAmount = (pendingAmount.toLong() + amount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (currentAmount > 0) interrupt()
        schedule()
    }

    fun setRunActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) schedule() else interrupt()
    }

    fun reset() {
        active = false
        interrupt()
        pendingAmount = 0
    }

    private fun schedule() {
        removeCallbacks(reveal)
        if (active && currentAmount == 0 && pendingAmount > 0 && isAttachedToWindow && windowVisibility == VISIBLE) post(reveal)
    }

    private fun showPending() {
        if (!active || currentAmount > 0 || pendingAmount <= 0 || !isAttachedToWindow || windowVisibility != VISIBLE) return
        currentAmount = pendingAmount
        pendingAmount = 0
        labelChanged = true
        amount.text = amountLabel(amount.textSize)
        card.contentDescription = "Jackpot. ${number(currentAmount)} coins."
        card.visibility = VISIBLE
        card.requestLayout()
        Anim.popIn(card, from = .88f, duration = 300L)
        removeCallbacks(retreat)
        postDelayed(retreat, 2800L)
    }

    /** Pausing or detaching must not consume a win the player has not finished seeing. */
    private fun interrupt() {
        removeCallbacks(reveal); removeCallbacks(retreat)
        if (currentAmount > 0) {
            pendingAmount = (pendingAmount.toLong() + currentAmount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            currentAmount = 0
        }
        Anim.reset(card)
        card.visibility = INVISIBLE
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); schedule() }
    override fun onDetachedFromWindow() { interrupt(); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) schedule() else interrupt()
    }
}
