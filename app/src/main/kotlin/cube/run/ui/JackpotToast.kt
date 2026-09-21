package cube.run.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.view.animation.LinearInterpolator
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
import cube.run.core.Stage
import cube.run.core.SoundFx
import cube.run.core.Haptics

/** Celebrates coins already earned by the run; this view never awards or spends anything. */
@SuppressLint("ViewConstructor")
class JackpotToast(activity: Activity, private val kit: UiKit) : FrameLayout(activity) {
    private var active = false
    private var currentAmount = 0
    private var pendingAmount = 0
    private val spectacle = JackpotSpectacle(activity, kit)
    private var clock: ValueAnimator? = null
    private var soundBeat = -1
    private val title = kit.text("JACKPOT", 76f, Theme.GOLD, 700).apply {
        tag = "jackpot_title"
        letterSpacing = .015f
        setShadowLayer(kit.dpf(2f), 0f, kit.dpf(5f), Theme.INK)
        setSingleLine(); setHorizontallyScrolling(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val amount = kit.text("", 46f, Theme.WHITE, 700).apply {
        tag = "jackpot_amount"
        setSingleLine(); setHorizontallyScrolling(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var labelChanged = false
    private val card = object : LinearLayout(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight - kit.dp(2f)).coerceAtLeast(1)
            fit(title, available, 76f) { "JACKPOT" }
            fit(amount, available, 46f, ::amountLabel)
            labelChanged = false
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply {
        tag = "jackpot_card"
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(kit.dp(4f), kit.dp(9f), kit.dp(4f), kit.dp(12f))
        addView(title, LinearLayout.LayoutParams(-1, -2))
        addView(amount, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(2f) })
        visibility = INVISIBLE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val reveal = Runnable { showPending() }
    private val retreat = Runnable {
        card.move().alpha(0f).scaleX(1.3f).scaleY(1.3f).setDuration(400).withEndAction {
            currentAmount = 0
            Stage.jackpotCelebrating = false
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
        card.layoutParams.width = (MeasureSpec.getSize(widthMeasureSpec) * .94f).toInt().coerceAtLeast(1)
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
        if (currentAmount > 0) {
            // Simultaneous winning coins must not continually restart the reveal.
            currentAmount = (currentAmount.toLong() + pendingAmount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            pendingAmount = 0
            labelChanged = true
            this.amount.text = amountLabel(this.amount.textSize)
            card.contentDescription = "Jackpot. ${number(currentAmount)} coins."
            card.requestLayout()
            return
        }
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
        Stage.jackpotCelebrating = true
        pendingAmount = 0
        labelChanged = true
        amount.text = amountLabel(amount.textSize)
        card.contentDescription = "Jackpot. ${number(currentAmount)} coins."
        card.visibility = VISIBLE
        card.requestLayout()
        Anim.popIn(card, delay = 850L, from = 1.8f, duration = 300L)
        soundBeat = -1
        clock?.cancel()
        clock = ValueAnimator.ofFloat(0f, 4.8f).apply {
            duration = 4800L
            interpolator = LinearInterpolator()
            addUpdateListener {
                spectacle.time = it.animatedValue as Float
                val t = spectacle.time
                val kick = ((1.5f - t) / .65f).coerceIn(0f, 1f)
                card.rotation = -4f + kotlin.math.sin(t * 50f) * 7f * kick
                card.translationY = height * .045f + kotlin.math.sin(t * 8f) * kit.dpf(4f)
                val beat = when { t < .4f -> 0; t < .6f -> 1; t < .85f -> 2; t < 1.1f -> 3; else -> 4 }
                if (beat > soundBeat) {
                    soundBeat = beat
                    when (beat) {
                        0 -> SoundFx.play("rise", rate = .7f)
                        1, 2 -> { SoundFx.play("place", rate = .7f + beat * .3f); Haptics.heavy() }
                        3 -> { SoundFx.play("boom"); Haptics.success() }
                        4 -> SoundFx.play("success", rate = 1.15f)
                    }
                }
                Anim.repaint(this@JackpotToast)
            }
            start()
        }
        removeCallbacks(retreat)
        postDelayed(retreat, 4400L)
    }

    /** Pausing or detaching must not consume a win the player has not finished seeing. */
    private fun interrupt() {
        clock?.cancel(); clock = null
        Stage.jackpotCelebrating = false
        card.rotation = 0f
        removeCallbacks(reveal); removeCallbacks(retreat)
        if (currentAmount > 0) {
            pendingAmount = (pendingAmount.toLong() + currentAmount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            currentAmount = 0
        }
        Anim.reset(card)
        card.visibility = INVISIBLE
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (currentAmount > 0) spectacle.draw(canvas, width.toFloat(), height.toFloat())
        super.dispatchDraw(canvas)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); schedule() }
    override fun onDetachedFromWindow() { interrupt(); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) schedule() else interrupt()
    }
}
