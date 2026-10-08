package cube.run.ui

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import cube.run.R
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.data.RedeemCodes
import java.util.concurrent.Executors

/** A normal dialog window owns the keyboard and system Back without resizing Settings. */
class CodeDialog(private val activity: Activity, private val kit: UiKit, private val onGranted: () -> Unit) : Dialog(activity) {
    companion object { private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "code-redemption") } }
    private fun dp(value: Float) = kit.dp(value)
    private var submitting = false
    private val input = EditText(activity).apply {
        tag = "code_entry"
        hint = activity.getString(R.string.codes_enter)
        contentDescription = hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_DONE
        isSingleLine = true
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        textDirection = View.TEXT_DIRECTION_LTR
        setTextColor(Theme.INK); setHintTextColor(Theme.MUTED)
        textSize = 20f; typeface = Fonts.get(activity, 600)
        setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
        background = GradientDrawable().apply {
            cornerRadius = kit.dpf(14f); setColor(Theme.SETTINGS_BLUE)
            setStroke(dp(2f), ColorStateList.valueOf(Theme.SKY))
        }
        setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_DONE) { redeem(); true } else false }
    }
    private val feedback = kit.text("", 17f, Theme.INK_SOFT, 600, Gravity.START).apply {
        tag = "code_feedback"
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        minimumHeight = dp(24f)
    }
    private val redeemButton = kit.button(activity.getString(R.string.codes_redeem), Theme.SKY, UiKit.Size.BIG) { redeem() }.apply {
        tag = "code_redeem"
    }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            setPadding(dp(18f), dp(16f), dp(18f), dp(20f))
            background = kit.cardDrawable(Theme.CARD, null, 24f)
            addView(LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false; clipToPadding = false
                addView(kit.text(activity.getString(R.string.settings_codes), 24f, Theme.INK, 700, Gravity.START),
                    LinearLayout.LayoutParams(0, -2, 1f))
                addView(kit.chip(R.drawable.ic_back, Theme.SETTINGS_BLUE, Theme.INK, activity.getString(R.string.cd_back)) { dismiss() },
                    LinearLayout.LayoutParams(dp(42f), dp(46f)))
            })
            addView(input, LinearLayout.LayoutParams(-1, dp(56f)).apply { topMargin = dp(16f) })
            addView(feedback, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })
            addView(redeemButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })
        }
        setContentView(card)
        setCanceledOnTouchOutside(false)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.35f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        setOnShowListener {
            window?.setLayout(minOf(dp(320f), activity.resources.displayMetrics.widthPixels - dp(28f)), WindowManager.LayoutParams.WRAP_CONTENT)
            input.requestFocus()
            Anim.popIn(card, from = .98f, duration = 120)
        }
    }

    private fun redeem() {
        if (submitting) return
        val code = input.text.toString()
        submitting = true; redeemButton.isEnabled = false
        worker.execute {
            val result = RedeemCodes.redeem(code)
            activity.runOnUiThread {
                if (result is RedeemCodes.Result.Granted) onGranted()
                if (!isShowing) return@runOnUiThread
                submitting = false; redeemButton.isEnabled = true
                feedback.text = when (result) {
                    is RedeemCodes.Result.Granted -> when (val effect = result.effect) {
                        is RedeemCodes.Effect.Coins -> activity.getString(R.string.codes_coins, effect.amount)
                        is RedeemCodes.Effect.Unlock -> activity.getString(R.string.codes_unlocked)
                    }
                    RedeemCodes.Result.Invalid -> activity.getString(R.string.codes_invalid)
                    RedeemCodes.Result.AlreadyUsed -> activity.getString(R.string.codes_used)
                    RedeemCodes.Result.Unavailable -> activity.getString(R.string.codes_retry)
                }
                if (result is RedeemCodes.Result.Granted) {
                    feedback.setTextColor(Theme.INK); Anim.pulse(feedback, amount = 1.05f)
                    Haptics.success(); SoundFx.play("success")
                } else {
                    feedback.setTextColor(Theme.INK_SOFT)
                    if (result == RedeemCodes.Result.Invalid) Anim.shake(input, kit.dpf(4f))
                }
            }
        }
    }
}
