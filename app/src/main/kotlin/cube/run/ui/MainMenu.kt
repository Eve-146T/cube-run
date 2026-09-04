package cube.run.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import cube.run.R
import cube.run.ui.Anim.move
import cube.run.data.Progress
import cube.run.data.Settings

/**
 * The main menu, drawn over the idling 3D world: the logo, your best score
 * under a trophy, the coin bank (tap: shop) and bubble stock (tap: shop) in
 * the corners, "TAP TO START" breathing in the middle, and the chips along
 * the bottom — sound / vibration / dev / explorer on the left, wardrobe and
 * shop on the right. Everything pops in staggered; [hide] drops it all when
 * a run begins.
 */
@SuppressLint("ViewConstructor", "SetTextI18n")
class MainMenu(
    private val activity: Activity,
    private val kit: UiKit,
    private val openShop: () -> Unit,
    private val openWardrobe: () -> Unit,
    private val openSections: () -> Unit,
    private val onDevToggled: () -> Unit,
) : FrameLayout(activity) {

    private fun dp(v: Float) = kit.dp(v)
    private fun dpf(v: Float) = kit.dpf(v)
    private val anims = ArrayList<ValueAnimator>()

    /** Every letter of the logo is its own view, so the word can ripple. */
    private val letters = ArrayList<View>()

    private fun word(text: String, color: Int): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false; clipToPadding = false
        for (ch in text) {
            val v = kit.stageText(ch.toString(), 62f, color, stroke = 7f).apply { setLayerType(View.LAYER_TYPE_HARDWARE, null) }
            letters.add(v)
            addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = -dp(6f); rightMargin = -dp(6f) })
        }
    }

    private val logo = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false; clipToPadding = false
        rotation = -4f
        addView(word("CUBE", Theme.WHITE), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(word("RUN", Theme.YELLOW), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = -dp(18f) })
    }

    /** The letters ripple: each bobs and tilts a little out of step with its neighbour. */
    private fun ripple(): ValueAnimator = ValueAnimator.ofFloat(0f, 6.2832f).apply {
        duration = 2400; repeatCount = ValueAnimator.INFINITE; interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener { a ->
            val t = a.animatedValue as Float
            for ((i, v) in letters.withIndex()) {
                v.translationY = kotlin.math.sin(t + i * 0.75f) * dpf(4f)
                v.rotation = kotlin.math.sin(t * 0.5f + i * 0.9f) * 4f
            }
            Anim.repaint(logo)
        }
        start()
    }
    private val bestRow = kit.iconText(TrophyIcon(), "", 22f, Theme.WHITE, stage = true, iconDp = 28f).apply { visibility = GONE }
    private val bank = kit.iconPill(CoinIcon(), "0", Theme.INK, 16f).apply { setOnClickListener { openShop() } }
    private val bubbles = kit.iconPill(BubbleIcon(), "", Theme.INK, 16f).apply { visibility = GONE; setOnClickListener { openShop() } }
    private val tapHint = kit.stageText("TAP TO START", 22f, Theme.WHITE, stroke = 3f).apply { letterSpacing = 0.12f }

    private val leftChips = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false; clipToPadding = false
        val size = dp(44f)
        addView(kit.toggle(R.drawable.ic_sound_on, R.drawable.ic_sound_off, activity.getString(R.string.cd_sound), Theme.SKY,
            { Settings.soundEnabled }, { Settings.setSoundEnabled(it) }), LinearLayout.LayoutParams(size, size + dp(4f)))
        addView(kit.toggle(R.drawable.ic_haptic_on, R.drawable.ic_haptic_off, activity.getString(R.string.cd_haptics), Theme.SKY,
            { Settings.hapticsEnabled }, { Settings.setHapticsEnabled(it) }), LinearLayout.LayoutParams(size, size + dp(4f)).apply { leftMargin = dp(8f) })
        // Dev mode (this launch only): the run serves the sections under review on loop,
        // and every tap of it fills the bank, so anything in the shop can be tried.
        addView(kit.toggle(R.drawable.ic_dev_on, R.drawable.ic_dev_off, activity.getString(R.string.cd_dev), Theme.ORANGE,
            { Settings.devMode }, { Settings.setDevMode(it); if (it) Progress.enterDev() else Progress.leaveDev(); onDevToggled() }), LinearLayout.LayoutParams(size, size + dp(4f)).apply { leftMargin = dp(8f) })
        addView(kit.chip(R.drawable.ic_sections, Theme.WHITE, Theme.INK, activity.getString(R.string.cd_sections)) { openSections() },
            LinearLayout.LayoutParams(size, size + dp(4f)).apply { leftMargin = dp(8f) })
    }

    private val rightChips = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false; clipToPadding = false
        val size = dp(58f)
        addView(kit.chip(R.drawable.ic_skins, Theme.GRAPE, Theme.WHITE, activity.getString(R.string.cd_skins)) { openWardrobe() }.apply { val p = dp(13f); setPadding(p, p, p, p) },
            LinearLayout.LayoutParams(size, size + dp(4f)))
        addView(kit.chip(R.drawable.ic_shop, Theme.GOLD, Theme.INK, activity.getString(R.string.cd_shop)) { openShop() }.apply { val p = dp(13f); setPadding(p, p, p, p) },
            LinearLayout.LayoutParams(size, size + dp(4f)).apply { leftMargin = dp(10f) })
    }

    private val top = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false; clipToPadding = false
        addView(logo)
        addView(bestRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2f) })
    }
    private val middle = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false; clipToPadding = false
        addView(tapHint)
    }

    init {
        isClickable = false
        isFocusable = false
        clipChildren = false; clipToPadding = false
        addView(top, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(70f) })
        addView(bank, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.TOP or Gravity.END; topMargin = dp(40f); rightMargin = dp(14f) })
        addView(bubbles, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.TOP or Gravity.START; topMargin = dp(40f); leftMargin = dp(14f) })
        addView(middle, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER; topMargin = dp(40f) })
        addView(leftChips, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM or Gravity.START; leftMargin = dp(14f); bottomMargin = dp(28f) })
        addView(rightChips, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM or Gravity.END; rightMargin = dp(14f); bottomMargin = dp(26f) })
        setOnApplyWindowInsetsListener { _, insets ->
            val (l, t, r, b) = insetsOf(insets)
            (top.layoutParams as LayoutParams).topMargin = maxOf(dp(70f), t + dp(40f))
            // the same corner as every page's balance pill (page padding max(36, t+6) + 4): it never shifts between screens
            (bank.layoutParams as LayoutParams).apply { topMargin = maxOf(dp(40f), t + dp(10f)); rightMargin = dp(14f) + r }
            (bubbles.layoutParams as LayoutParams).apply { topMargin = maxOf(dp(40f), t + dp(10f)); leftMargin = dp(14f) + l }
            (leftChips.layoutParams as LayoutParams).apply { leftMargin = dp(14f) + l; bottomMargin = dp(28f) + b }
            (rightChips.layoutParams as LayoutParams).apply { rightMargin = dp(14f) + r; bottomMargin = dp(26f) + b }
            requestLayout()
            insets
        }
        refresh()
        show()
    }

    /** The entrance (also replayed coming back from a page): everything pops in staggered, the logo bobs. */
    fun show() {
        for (a in anims) a.cancel()
        anims.clear()
        Anim.popIn(logo, 60, 0.4f, 520)
        Anim.riseIn(bestRow, 220, dpf(20f))
        Anim.popIn(bank, 260, 0.6f)
        Anim.popIn(bubbles, 300, 0.6f)
        Anim.riseIn(middle, 320, dpf(24f))
        Anim.stagger(leftChips, dpf(40f), 280, 60)
        Anim.stagger(rightChips, dpf(40f), 420, 80)
        anims.add(Anim.breathe(tapHint, 0.55f, 1f, 750))
        logo.postDelayed({ if (isAttachedToWindow && visibility == VISIBLE) anims.add(ripple()) }, 620)
    }

    /** Re-read the bank / stock / best (after the shop, the wardrobe, a dev toggle). */
    fun refresh() {
        kit.labelOf(bank).text = Progress.coins.toString()
        kit.labelOf(bubbles).text = "×${Progress.bubbles}"
        bubbles.visibility = if (Progress.bubbles > 0) VISIBLE else GONE
    }

    fun setBest(best: Int) {
        kit.labelOf(bestRow).text = best.toString()
        bestRow.visibility = if (best > 0) VISIBLE else GONE
    }

    /** Called when the bank changes while the menu is up: pulse it. */
    fun pulseBank() { refresh(); Anim.pulse(bank) }

    fun hide() {
        if (visibility != VISIBLE) return
        for (a in anims) a.cancel()
        top.move().translationY(-dpf(60f)).alpha(0f).setDuration(220).start()
        middle.move().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(160).start()
        leftChips.move().translationY(dpf(80f)).alpha(0f).setDuration(220).start()
        rightChips.move().translationY(dpf(80f)).alpha(0f).setDuration(220).start()
        bank.move().alpha(0f).translationY(-dpf(30f)).setDuration(200).start()
        bubbles.move().alpha(0f).translationY(-dpf(30f)).setDuration(200).withEndAction { visibility = GONE }.start()
    }

    override fun onDetachedFromWindow() {
        for (a in anims) a.cancel()
        anims.clear()
        super.onDetachedFromWindow()
    }

    /**
     * A page is opening (false) or closing (true). Opening: everything drops
     * away quickly (the logo up, the chips down) before the page fades in;
     * closing: it is put back so [show] can pop it all in again.
     */
    fun setShown(show: Boolean) {
        val parts = listOf(top, middle, leftChips, rightChips, bank, bubbles)
        if (show) {
            for (a in anims) a.cancel()
            anims.clear()
            for (p in parts) { p.animate().cancel(); p.animate().setStartDelay(0); p.alpha = 1f; p.translationY = 0f; p.scaleX = 1f; p.scaleY = 1f; p.visibility = VISIBLE }
            bubbles.visibility = if (Progress.bubbles > 0) VISIBLE else GONE
        } else {
            for (a in anims) a.cancel()
            anims.clear()
            for (p in parts) p.animate().cancel()
            top.move().translationY(-dpf(40f)).alpha(0f).setDuration(140).withEndAction { top.visibility = INVISIBLE }.start()
            middle.move().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(120).withEndAction { middle.visibility = INVISIBLE }.start()
            leftChips.move().translationY(dpf(50f)).alpha(0f).setDuration(140).withEndAction { leftChips.visibility = INVISIBLE }.start()
            rightChips.move().translationY(dpf(50f)).alpha(0f).setDuration(140).withEndAction { rightChips.visibility = INVISIBLE }.start()
            bank.move().alpha(0f).translationY(-dpf(20f)).setDuration(120).withEndAction { bank.visibility = INVISIBLE }.start()
            bubbles.move().alpha(0f).translationY(-dpf(20f)).setDuration(120).withEndAction { bubbles.visibility = INVISIBLE }.start()
        }
    }
}
