package cube.run.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import cube.run.R
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.data.RedeemCodes
import cube.run.data.Progress
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
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                feedback.text = ""
            }
            override fun afterTextChanged(s: Editable?) {}
        })
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
            isFocusableInTouchMode = true
            clipChildren = false; clipToPadding = false
            setPadding(dp(18f), dp(16f), dp(18f), dp(20f))
            background = kit.cardDrawable(Theme.CARD, null, 24f)
            addView(LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false; clipToPadding = false
                addView(kit.backButton { dismiss() }.apply { tag = "codes_back" },
                    LinearLayout.LayoutParams(dp(46f), dp(50f)))
                addView(kit.text(activity.getString(R.string.settings_codes), 26f, Theme.INK, 700, Gravity.START).apply { tag = "codes_title" },
                    LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12f) })
            })
            addView(input, LinearLayout.LayoutParams(-1, dp(56f)).apply { topMargin = dp(16f) })
            addView(feedback, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })
            addView(redeemButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })
        }
        setContentView(card)
        setCanceledOnTouchOutside(false)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setWindowAnimations(0)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.35f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
        setOnShowListener {
            window?.setLayout(minOf(dp(320f), activity.resources.displayMetrics.widthPixels - dp(28f)), WindowManager.LayoutParams.WRAP_CONTENT)
            card.requestFocus()
        }
    }

    private fun redeem() {
        if (submitting) return
        val code = input.text.toString().trim()
        if (code.isEmpty()) {
            feedback.setTextColor(Theme.INK_SOFT)
            feedback.text = activity.getString(R.string.codes_enter)
            return
        }
        submitting = true; redeemButton.isEnabled = false; input.isEnabled = false
        feedback.setTextColor(Theme.INK_SOFT)
        feedback.text = activity.getString(R.string.codes_redeeming)
        worker.execute {
            val result = RedeemCodes.redeem(code)
            activity.runOnUiThread {
                if (result is RedeemCodes.Result.Granted) onGranted()
                if (!isShowing) return@runOnUiThread
                submitting = false; redeemButton.isEnabled = true; input.isEnabled = true
                feedback.text = when (result) {
                    is RedeemCodes.Result.Granted -> when (val effect = result.effect) {
                        is RedeemCodes.Effect.Coins -> activity.getString(R.string.codes_coins, effect.amount, Progress.coins)
                        is RedeemCodes.Effect.Unlock -> activity.getString(R.string.codes_unlocked, activity.gameText(effect.name))
                    }
                    RedeemCodes.Result.Invalid -> activity.getString(R.string.codes_invalid)
                    RedeemCodes.Result.AlreadyUsed -> activity.getString(R.string.codes_used)
                    RedeemCodes.Result.Unavailable -> activity.getString(R.string.codes_retry)
                }
                if (result is RedeemCodes.Result.Granted) {
                    feedback.setTextColor(Theme.INK)
                    (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(input.windowToken, 0)
                    redeemButton.requestFocus()
                    Haptics.success(); SoundFx.play("success")
                } else {
                    feedback.setTextColor(Theme.INK_SOFT)
                }
            }
        }
    }
}
