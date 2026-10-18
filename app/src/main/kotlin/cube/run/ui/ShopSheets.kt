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
import cube.run.R

/** Keep the coin and even a billion-coin offering centered together on one candy face. */
@SuppressLint("ViewConstructor")
internal class FittedVoidPrice(
    activity: Activity,
    private val kit: UiKit,
    private val button: CandyButton,
    price: Int,
) : FrameLayout(activity) {
    private val amount = number(price)
    private var fittedPx = Float.NaN

    init {
        clipChildren = false; clipToPadding = false
        button.apply {
            tag = "void_price_button"
            contentDescription = context.getString(R.string.cd_offer_coins, amount)
            gravity = Gravity.CENTER
            minimumHeight = kit.dp(52f)
            setSingleLine()
            setHorizontallyScrolling(false)
            setPadding(kit.dp(18f), kit.dp(11f), kit.dp(18f), kit.dp(11f))
        }
        addView(button, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight -
            button.compoundPaddingLeft - button.compoundPaddingRight - kit.dp(2f)
        val reference = TextPaint(button.paint).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 19f, button.resources.displayMetrics)
        }
        var size = reference.textSize
        val budget = available.coerceAtLeast(1).toFloat()
        if (Layout.getDesiredWidth(labelAt(size), reference) > budget) {
            var low = 0f
            var high = size
            repeat(16) {
                val candidate = (low + high) / 2f
                reference.textSize = candidate
                if (Layout.getDesiredWidth(labelAt(candidate), reference) <= budget) low = candidate else high = candidate
            }
            size = low
        }
        if (size != fittedPx) {
            fittedPx = size
            // Android's enlarged-font scale is nonlinear. Commit the exact measured pixel
            // size and span together instead of converting a proportional SP estimate again.
            button.text = labelAt(size)
            button.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun labelAt(textSizePx: Float): CharSequence {
        val coin = CoinIcon().apply {
            val edge = (textSizePx * 1.15f).toInt().coerceAtLeast(1)
            setBounds(0, 0, edge, edge)
        }
        return SpannableStringBuilder("\u2009 \u2009").apply {
            setSpan(CenteredImageSpan(coin), 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(amount)
        }
    }
}

/** Uses the pause sheet's native card and candy controls; only the explicit red action erases. */
@SuppressLint("ViewConstructor")
internal class ResetProgressSheet(
    activity: Activity,
    kit: UiKit,
    onDismissed: () -> Unit,
    private val onConfirm: () -> Boolean,
) : Sheet(activity, kit, onDismissed) {
    private var committing = false
    private val explanation = kit.text(context.getString(R.string.shop_reset_explanation), 15f, Theme.INK, 500)

    init {
        tag = "reset_progress_confirmation"
        card.addView(kit.text(context.getString(R.string.shop_reset_question), 23f, Theme.INK, 700))
        card.addView(explanation, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })
        card.addView(kit.button(context.getString(R.string.shop_cancel), Theme.LAVENDER) { if (!committing) dismiss() }.apply {
            tag = "reset_progress_cancel"
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22f) })
        card.addView(kit.button(context.getString(R.string.shop_reset), Theme.BERRY) {
            if (!committing) {
                committing = true
                if (onConfirm()) dismiss() else {
                    committing = false
                    explanation.text = context.getString(R.string.shop_reset_failed)
                }
            }
        }.apply {
            tag = "reset_progress_confirm"
            setTextColor(Theme.WHITE)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        card.layoutParams.width = (MeasureSpec.getSize(widthMeasureSpec) - dp(32f)).coerceAtMost(dp(320f))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
