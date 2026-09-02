package cube.run.core.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import cube.run.R
import cube.run.core.Haptics
import cube.run.core.Palette
import cube.run.core.Progress
import cube.run.core.Settings
import cube.run.core.SoundFx

/**
 * The bottom strip shown before a run: sound / vibration / dev-mode toggles
 * + the section explorer (bottom-left) and the skins + shop buttons (bottom-right),
 * all icon-only. Keeps itself clear of the nav bar and cutouts; [hide] fades it
 * out once a run begins.
 */
@SuppressLint("ViewConstructor")
class PreRunMenu(
    private val activity: Activity,
    private val kit: UiKit,
    private val openShop: () -> Unit,
    private val openSkins: () -> Unit,
    private val openSections: () -> Unit,
    private val onDevCoins: () -> Unit,
) : FrameLayout(activity) {

    private fun dp(v: Float) = kit.dp(v)
    private val accent = kit.accent

    /**
     * A round, icon-only toggle: translucent white fill, coloured ring when on.
     * Tapping flips it, gives audio + haptic confirmation that honours the *new*
     * state (muting is silent, un-muting isn't), then repaints.
     */
    private fun roundButton(icon: Int, tint: Int, label: String, onClick: () -> Unit) = ImageView(activity).apply {
        val pad = dp(11f)
        setPadding(pad, pad, pad, pad)
        scaleType = ImageView.ScaleType.FIT_CENTER
        isClickable = true
        isFocusable = true
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(tint)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Palette.withAlpha(Color.WHITE, 46))
            setStroke(dp(2f), Palette.withAlpha(tint, 200))
        }
        contentDescription = label
        setOnClickListener { onClick() }
    }

    private fun toggle(iconOn: Int, iconOff: Int, label: String, isOn: () -> Boolean, set: (Boolean) -> Unit): ImageView {
        lateinit var v: ImageView
        fun paint() {
            val on = isOn()
            v.setImageResource(if (on) iconOn else iconOff)
            v.imageTintList = ColorStateList.valueOf(if (on) accent else Palette.withAlpha(Color.WHITE, 150))
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Palette.withAlpha(Color.WHITE, 46))
                setStroke(dp(2f), Palette.withAlpha(if (on) accent else Color.WHITE, if (on) 200 else 80))
            }
            v.contentDescription = "$label ${if (on) "on" else "off"}"
        }
        v = roundButton(iconOn, accent, label) {
            set(!isOn())
            SoundFx.play("tap"); Haptics.tick()
            paint()
        }
        paint()
        return v
    }

    private val toggleBox = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val size = dp(42f)
        addView(toggle(R.drawable.ic_sound_on, R.drawable.ic_sound_off, activity.getString(R.string.cd_sound),
            { Settings.soundEnabled }, { Settings.setSoundEnabled(it) }), LinearLayout.LayoutParams(size, size))
        addView(toggle(R.drawable.ic_haptic_on, R.drawable.ic_haptic_off, activity.getString(R.string.cd_haptics),
            { Settings.hapticsEnabled }, { Settings.setHapticsEnabled(it) }), LinearLayout.LayoutParams(size, size).apply { leftMargin = dp(8f) })
        // Dev mode: the run serves only the sections under review, announcing each by name —
        // and every tap of it fills the bank, so anything in the shop can be tried.
        addView(toggle(R.drawable.ic_dev_on, R.drawable.ic_dev_off, activity.getString(R.string.cd_dev),
            { Settings.devMode }, { Settings.setDevMode(it); Progress.giveDevCoins(); onDevCoins() }), LinearLayout.LayoutParams(size, size).apply { leftMargin = dp(8f) })
        // Section explorer: pick any section and play it on loop.
        addView(roundButton(R.drawable.ic_sections, Palette.withAlpha(Color.WHITE, 200), activity.getString(R.string.cd_sections)) {
            SoundFx.play("tap"); Haptics.click()
            openSections()
        }, LinearLayout.LayoutParams(size, size).apply { leftMargin = dp(8f) })
    }

    private val shopBox = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        val size = dp(50f)
        addView(roundButton(R.drawable.ic_skins, 0xFFC28BFF.toInt(), activity.getString(R.string.cd_skins)) {
            SoundFx.play("tap"); Haptics.click(); openSkins()
        }, LinearLayout.LayoutParams(size, size))
        addView(roundButton(R.drawable.ic_shop, Ui.GOLD, activity.getString(R.string.cd_shop)) {
            SoundFx.play("tap"); Haptics.click(); openShop()
        }, LinearLayout.LayoutParams(size, size).apply { leftMargin = dp(10f) })
    }

    init {
        isClickable = false
        isFocusable = false
        addView(toggleBox, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            leftMargin = dp(14f); bottomMargin = dp(28f)
        })
        addView(shopBox, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            rightMargin = dp(14f); bottomMargin = dp(26f)
        })
        setOnApplyWindowInsetsListener { _, insets ->
            val left: Int; val right: Int; val bottom: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val nav = insets.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
                left = nav.left; right = nav.right; bottom = nav.bottom
            } else {
                @Suppress("DEPRECATION") left = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION") right = insets.systemWindowInsetRight
                @Suppress("DEPRECATION") bottom = insets.systemWindowInsetBottom
            }
            (toggleBox.layoutParams as LayoutParams).apply { leftMargin = dp(14f) + left; bottomMargin = dp(28f) + bottom }
            (shopBox.layoutParams as LayoutParams).apply { rightMargin = dp(14f) + right; bottomMargin = dp(26f) + bottom }
            toggleBox.requestLayout(); shopBox.requestLayout()
            insets
        }
    }

    fun hide() {
        if (visibility != VISIBLE) return
        animate().alpha(0f).setDuration(160).withEndAction { visibility = GONE }.start()
    }
}
