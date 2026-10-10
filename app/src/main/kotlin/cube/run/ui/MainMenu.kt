package cube.run.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import cube.run.R
import cube.run.ui.Anim.move
import cube.run.data.Progress

/**
 * The main menu, drawn over the idling 3D world: the logo, your best score
 * beside an ink-outlined trophy, the coin bank (tap: shop) and bubble stock
 * (tap: shop) in the corners, "TAP TO START" breathing in the middle, and the
 * chips along the bottom — settings on the left, achievements, wardrobe and
 * shop on the right. Everything pops in staggered; [hide] drops it all when
 * a run begins.
 */
@SuppressLint("ViewConstructor", "SetTextI18n")
class MainMenu(
    private val activity: Activity,
    private val kit: UiKit,
    private val openShop: () -> Unit,
    private val openWardrobe: () -> Unit,
    private val openSettings: () -> Unit,
    private val openAchievements: () -> Unit = {},
    private var openingEntrance: Boolean = false,
    returningToMenu: Boolean = false,
) : FrameLayout(activity) {

    private fun dp(v: Float) = kit.dp(v)
    private fun dpf(v: Float) = kit.dpf(v)
    private var compact = false
    private var compactAppearance = false
    private var safeInsets = intArrayOf(0, 0, 0, 0)
    private val uncovered = Uncovered(this)
    private val anims = ArrayList<ValueAnimator>()
    private val startRipple = Runnable {
        if (isAttachedToWindow && visibility == VISIBLE && top.visibility == VISIBLE) anims.add(ripple())
    }

    private fun stopIdle() {
        logo.removeCallbacks(startRipple)
        for (a in anims) a.cancel()
        anims.clear()
    }

    /** Every letter of the logo is its own view, so the word can ripple. */
    private val letters = ArrayList<View>()

    private fun word(text: String, color: Int): LinearLayout = LinearLayout(activity).apply {
        layoutDirection = View.LAYOUT_DIRECTION_LTR // The brand keeps its letter order in every language.
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false; clipToPadding = false
        for (ch in text) {
            val v = kit.stageText(ch.toString(), 62f, color, stroke = 7f).apply { setLayerType(View.LAYER_TYPE_HARDWARE, null) }
            letters.add(v)
            addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = -dp(6f); marginEnd = -dp(6f) })
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
        val born = System.nanoTime()
        addUpdateListener { a ->
            val t = a.animatedValue as Float
            val ramp = ((System.nanoTime() - born) / 1.2e9f).coerceIn(0f, 1f) // eases in from still, so the entrance never jumps
            for ((i, v) in letters.withIndex()) {
                v.translationY = kotlin.math.sin(t + i * 0.75f) * dpf(4f) * ramp
                v.rotation = kotlin.math.sin(t + i * 0.9f + 1.2f) * 3.5f * ramp // same period as the bob, so the loop is seamless
            }
            Anim.repaint(logo)
        }
        start()
    }
    private val bestRow = kit.iconText(BestTrophyIcon(), "", 30f, Theme.WHITE, stage = true, iconDp = 38f, stroke = 6.5f).apply { visibility = GONE }
    private val bank = kit.coinBank("0").apply { setOnClickListener { openShop() } }
    private val bubbles = kit.iconPill(BubbleIcon(), "", Theme.INK, 16f, Theme.lighten(Theme.CYAN, 0.55f)).apply { visibility = GONE; setOnClickListener { openShop() } }
    private val tapHint = kit.stageText(activity.getString(R.string.tap_to_start), 22f, Theme.WHITE, stroke = 3f).apply {
        letterSpacing = if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) 0f else 0.12f
    }
    val startControl: android.view.View get() = tapHint

    private val settings = kit.chip(R.drawable.ic_settings, Theme.SETTINGS_BLUE, Theme.INK, activity.getString(R.string.settings_title)) { openSettings() }.apply {
        val p = dp(13f); setPadding(p, p, p, p)
    }

    /** The left group: settings alone (sound, vibration, language and the debug tools live in it). */
    private val leftChips = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.BOTTOM
        clipChildren = false; clipToPadding = false
        addView(settings, LinearLayout.LayoutParams(dp(58f), dp(62f)))
    }

    private val achievements = kit.chip(R.drawable.ic_achievements, Theme.ORANGE, Theme.WHITE, activity.getString(R.string.achievements_title)) { openAchievements() }.apply {
        val p = dp(13f); setPadding(p, p, p, p)
    }

    private val rightChips = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false; clipToPadding = false
        val size = dp(58f)
        addView(achievements, LinearLayout.LayoutParams(size, size + dp(4f)).apply { marginEnd = dp(10f) })
        addView(kit.chip(R.drawable.ic_skins, Theme.GRAPE, Theme.WHITE, activity.getString(R.string.cd_skins)) { openWardrobe() }.apply { val p = dp(13f); setPadding(p, p, p, p) },
            LinearLayout.LayoutParams(size, size + dp(4f)))
        addView(kit.chip(R.drawable.ic_shop, Theme.GOLD, Theme.INK, activity.getString(R.string.cd_shop)) { openShop() }.apply { val p = dp(13f); setPadding(p, p, p, p) },
            LinearLayout.LayoutParams(size, size + dp(4f)).apply { marginStart = dp(10f) })
    }

    /** Keep a real gutter beside the settings on narrow phones, even after the third chip appears. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cutouts = uncovered.of(safeInsets)
        val available = MeasureSpec.getSize(widthMeasureSpec) - (cutouts?.get(0) ?: 0) - (cutouts?.get(2) ?: 0)
        val usableHeight = MeasureSpec.getSize(heightMeasureSpec) - (cutouts?.get(1) ?: 0) - (cutouts?.get(3) ?: 0)
        compact = CompactLayout.uses(this, usableHeight)
        val compactRows = compact && available < dp(352f)
        // Compact: one line of letters that grows with the pane, within its width.
        val letterSize = if (!compact) 62f else minOf(24f + (usableHeight / resources.displayMetrics.density - 375f) / 8f,
            available / resources.displayMetrics.density / 9.5f).coerceIn(22f, 40f)
        for (letter in letters) {
            val text = letter as android.widget.TextView
            val pixels = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                letterSize, resources.displayMetrics)
            if (text.textSize != pixels) text.textSize = letterSize
            (text.layoutParams as LinearLayout.LayoutParams).apply {
                marginStart = -dp(6f); marginEnd = marginStart
                resolveLayoutDirection(layoutDirection) // relative margins set after inflation stay unapplied otherwise
            }
        }
        top.orientation = LinearLayout.VERTICAL
        top.gravity = Gravity.CENTER
        (logo.layoutParams as LinearLayout.LayoutParams).apply {
            width = LayoutParams.MATCH_PARENT
            weight = 0f
        }
        (bestRow.layoutParams as LinearLayout.LayoutParams).apply {
            topMargin = if (compact) 0 else dp(2f)
            marginEnd = 0
        }
        kit.labelOf(bestRow).textSize = if (compact) 18f else 30f
        bestRow.getChildAt(0).layoutParams.apply { width = dp(if (compact) 26f else 38f); height = width }
        val singleLine = compact
        logo.gravity = Gravity.CENTER_HORIZONTAL
        logo.orientation = if (singleLine) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        (logo.getChildAt(1).layoutParams as LinearLayout.LayoutParams).apply {
            topMargin = if (singleLine) 0 else -dp(18f * letterSize / 62f)
            marginStart = if (singleLine) dp(16f) else 0
            resolveLayoutDirection(layoutDirection) // the word gap between CUBE and RUN
        }
        (top.layoutParams as LayoutParams).topMargin = maxOf(dp(if (compact) 56f else 70f),
            (cutouts?.get(1) ?: 0) + dp(if (compact) 48f else 40f))
        for (pill in listOf(bank, bubbles)) {
            (pill.layoutParams as LayoutParams).topMargin = maxOf(dp(if (compact) 16f else 40f),
                (cutouts?.get(1) ?: 0) + dp(10f))
        }
        // Short and narrow (a flip phone, a split pane on a small phone): the two groups
        // stand as columns at the edges, so neither row lands on the cube in the middle.
        // Too short even for that, they fall back to centred rows under the prompt.
        val columns = compactRows && usableHeight >= dp(400f)
        val rows = compactRows && !columns
        rightChips.orientation = if (columns) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        for (i in 0 until rightChips.childCount) (rightChips.getChildAt(i).layoutParams as LinearLayout.LayoutParams).apply {
            marginEnd = if (!columns && i == 0) dp(10f) else 0
            if (i == 2) marginStart = if (columns) 0 else dp(10f)
            topMargin = if (columns && i > 0) dp(8f) else 0
            resolveLayoutDirection(layoutDirection)
        }
        if (compactAppearance != compact) {
            compactAppearance = compact
            middle.setPadding(dp(if (compact) 16f else 0f), 0, dp(if (compact) 16f else 0f), 0)
        }
        (leftChips.layoutParams as LayoutParams).apply {
            gravity = Gravity.BOTTOM or if (rows) Gravity.CENTER_HORIZONTAL else Gravity.START
            marginStart = if (rows) 0 else dp(14f) + (cutouts?.get(0) ?: 0)
            if (rows) { leftMargin = 0; rightMargin = 0; marginEnd = 0 }
            resolveLayoutDirection(layoutDirection)
        }
        (rightChips.layoutParams as LayoutParams).apply {
            gravity = Gravity.BOTTOM or if (rows) Gravity.CENTER_HORIZONTAL else Gravity.END
            marginEnd = if (rows) 0 else dp(14f) + (cutouts?.get(2) ?: 0)
            if (rows) { leftMargin = 0; rightMargin = 0; marginStart = 0 }
            resolveLayoutDirection(layoutDirection)
        }
        val count = if (Progress.achievementsUnlocked) 3 else 2
        // The settings chip and the right group share the row while a gutter remains between them.
        val separateRows = rows || !compactRows && available < dp(28f + 16f) + dp(48f) * (count + 1) + dp(10f) * (count - 1)
        val shared = if (separateRows) count else count + 1
        val chipSize = ((available - dp(28f) - (if (separateRows) 0 else dp(16f)) - dp(10f) * (count - 1)) / shared)
            .coerceIn(dp(48f), dp(if (compact) 48f else 58f))
        for (chip in toolbarChips()) chip.layoutParams.apply { width = chipSize; height = chipSize + dp(4f) }
        // Large display-size settings can leave less than 300dp. Keep real touch targets and
        // move the settings columns above the actions instead of letting the rows overlap.
        (leftChips.layoutParams as LayoutParams).bottomMargin = dp(if (compact) 12f else 28f) + (cutouts?.get(3) ?: 0) +
            if (separateRows) chipSize + dp(if (compact) 8f else 20f) else 0
        (rightChips.layoutParams as LayoutParams).bottomMargin = dp(if (compact) 10f else 26f) + (cutouts?.get(3) ?: 0)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (compact) {
            val footer = maxOf(leftChips.measuredHeight + (leftChips.layoutParams as LayoutParams).bottomMargin,
                rightChips.measuredHeight + (rightChips.layoutParams as LayoutParams).bottomMargin)
            val titleSpace = MeasureSpec.getSize(heightMeasureSpec) - footer -
                (top.layoutParams as LayoutParams).topMargin - middle.measuredHeight - dp(12f)
            // Saved scores, debug controls and font scaling all change the space
            // required. Fit the brand after measuring those real controls.
            var fittedSize = letterSize
            while (top.measuredHeight > titleSpace && fittedSize > 12f) {
                fittedSize -= 2f
                for (letter in letters) (letter as android.widget.TextView).textSize = fittedSize
                if (!singleLine) (logo.getChildAt(1).layoutParams as LinearLayout.LayoutParams).topMargin =
                    -dp(18f * fittedSize / 62f)
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        var fits = true
        if (compact) {
            // The centre of a short pane lies inside the logo. Put the start
            // prompt in the actual gap above the controls, preserving touch sizes.
            // A third of a phone has no gap: the prompt goes (any tap still starts).
            val gapTop = this.top.bottom + dp(6f)
            val gapBottom = minOf(leftChips.top, rightChips.top) - dp(6f)
            fits = gapBottom - gapTop >= middle.measuredHeight
            val y = if (fits) gapTop + (gapBottom - gapTop - middle.measuredHeight) / 2 else minOf(gapTop, gapBottom)
            middle.layout(middle.left, y, middle.right, if (fits) y + middle.measuredHeight else y)
        }
        val shown = if (fits) VISIBLE else INVISIBLE
        if (tapHint.visibility != shown) tapHint.visibility = shown
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
        addView(bank, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.TOP or Gravity.END; topMargin = dp(40f); marginEnd = dp(14f) })
        addView(bubbles, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.TOP or Gravity.START; topMargin = dp(40f); marginStart = dp(14f) })
        addView(middle, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER; topMargin = dp(40f) })
        addView(leftChips, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM or Gravity.START; marginStart = dp(14f); bottomMargin = dp(28f) })
        addView(rightChips, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM or Gravity.END; marginEnd = dp(14f); bottomMargin = dp(26f) })
        setOnApplyWindowInsetsListener { _, insets ->
            safeInsets = insetsOf(insets)
            val (physicalLeft, t, physicalRight, b) = safeInsets
            val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
            val l = if (rtl) physicalRight else physicalLeft
            val r = if (rtl) physicalLeft else physicalRight
            (top.layoutParams as LayoutParams).topMargin = maxOf(dp(70f), t + dp(40f))
            // the same corner as every page's balance pill (page padding max(36, t+6) + 4): it never shifts between screens
            (bank.layoutParams as LayoutParams).apply { topMargin = maxOf(dp(40f), t + dp(10f)); marginEnd = dp(14f) + r }
            (bubbles.layoutParams as LayoutParams).apply { topMargin = maxOf(dp(40f), t + dp(10f)); marginStart = dp(14f) + l }
            (leftChips.layoutParams as LayoutParams).apply { marginStart = dp(14f) + l; bottomMargin = dp(28f) + b }
            (rightChips.layoutParams as LayoutParams).apply { marginEnd = dp(14f) + r; bottomMargin = dp(26f) + b }
            requestLayout()
            insets
        }
        refresh()
        if (openingEntrance) setShown(true) else show(returning = returningToMenu)
    }

    fun finishOpeningEntrance() {
        if (!openingEntrance) return
        openingEntrance = false
        anims.add(Anim.breathe(tapHint, 1f, 0.55f, 750))
        anims.add(ripple())
    }

    /** Keep the launch flourish, but bring navigation controls back together on page returns. */
    fun show(returning: Boolean = false) {
        setShown(true)
        refresh()
        for (v in letters) { v.translationY = 0f; v.rotation = 0f }
        if (returning) {
            Anim.reset(logo)
            Anim.reset(bestRow)
            for (part in listOf(top, middle, leftChips, rightChips, bank, bubbles)) {
                Anim.riseIn(part, distancePx = dpf(12f), duration = 140)
            }
            for (chip in toolbarChips()) Anim.reset(chip)
            anims.add(Anim.breathe(tapHint, 0.55f, 1f, 750))
            logo.postDelayed(startRipple, 140)
            return
        }
        Anim.popIn(logo, 60, 0.4f, 520)
        Anim.riseIn(bestRow, 220, dpf(20f))
        Anim.popIn(bank, 260, 0.6f)
        Anim.popIn(bubbles, 300, 0.6f)
        Anim.riseIn(middle, 320, dpf(24f))
        for (chip in toolbarChips()) Anim.riseIn(chip, 280, dpf(40f))
        anims.add(Anim.breathe(tapHint, 0.55f, 1f, 750))
        logo.postDelayed(startRipple, 620)
    }

    /** Navigation reuses the already painted menu; no launch motion or delayed controls. */
    fun showInstant() {
        setShown(true)
        refresh()
        // Auto-start can cancel the staggered launch before any of these buttons fade in.
        for (chip in toolbarChips()) Anim.reset(chip)
        for (part in listOf(logo, bestRow, tapHint)) Anim.reset(part)
        for (letter in letters) { Anim.reset(letter); letter.rotation = 0f }
        anims.add(Anim.breathe(tapHint, 0.55f, 1f, 750))
        logo.post(startRipple)
    }

    fun hideInstant() {
        stopIdle()
        visibility = INVISIBLE
    }

    /** A locale crossfade keeps the menu in place instead of replaying its launch entrance. */
    fun settleLanguageTransition() {
        setShown(true)
        for (part in listOf(logo, bestRow, tapHint)) Anim.reset(part)
        for (chip in toolbarChips()) Anim.reset(chip)
        for (letter in letters) Anim.reset(letter)
    }

    fun resumeLanguageIdle() {
        anims.add(Anim.breathe(tapHint, 0.55f, 1f, 750))
        logo.post(startRipple)
    }

    /** Re-read the bank / stock / best (after the shop, the wardrobe, a dev toggle). */
    fun refresh() {
        kit.labelOf(bank).text = Progress.coins.toString()
        kit.labelOf(bubbles).text = "×${Progress.bubbles}"
        bubbles.visibility = if (Progress.bubbles > 0) VISIBLE else GONE
        achievements.visibility = if (Progress.achievementsUnlocked) VISIBLE else GONE
    }

    fun setBest(best: Int) {
        kit.labelOf(bestRow).text = best.toString()
        bestRow.visibility = if (best > 0) VISIBLE else GONE
    }

    /** Called when the bank changes while the menu is up: pulse it. */
    fun pulseBank() { refresh(); Anim.pulse(bank) }

    /** The shop uses this very same pill for payment: it never leaves its corner. */
    val shopBalance: LinearLayout get() = bank
    private var shopNavigating = false

    /** Animate actual controls together, including the nested utility/debug rows. */
    private fun toolbarChips(): List<View> = buildList {
        for (i in 0 until leftChips.childCount) add(leftChips.getChildAt(i))
        for (i in 0 until rightChips.childCount) add(rightChips.getChildAt(i))
    }

    // During shop navigation these controls draw over the departing sheet, but the shop owns input.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        if (shopNavigating) false else super.dispatchTouchEvent(event)

    fun beginShop() {
        shopNavigating = true
        logo.removeCallbacks(startRipple)
        for (a in anims) a.pause()
        Anim.cancelTree(this)
        for (v in listOf(top, logo, bestRow, middle, leftChips, rightChips, bank, bubbles)) Anim.reset(v)
        for (chip in toolbarChips()) Anim.reset(chip)
        setShopProgress(0f)
    }

    /** The panel's clock also moves the menu out of its way; Back reverses these exact poses. */
    fun setShopProgress(progress: Float) {
        val retreat = (progress / 0.65f).coerceIn(0f, 1f)
        for (v in listOf(top, middle, leftChips, rightChips, bubbles)) v.alpha = 1f - retreat
        top.translationY = -dpf(24f) * retreat
        middle.translationY = -dpf(12f) * retreat
        leftChips.translationY = dpf(20f) * retreat
        rightChips.translationY = dpf(20f) * retreat
        bubbles.translationY = -dpf(12f) * retreat
        Anim.repaint(this)
    }

    fun finishShop() {
        shopNavigating = false
        setShopProgress(0f)
        refresh()
        for (a in anims) a.resume()
        if (anims.isEmpty()) anims.add(Anim.breathe(tapHint, 0.55f, 1f, 750))
        if (anims.size == 1) anims.add(ripple())
    }

    fun hide() {
        if (visibility != VISIBLE) return
        stopIdle()
        Anim.cancelTree(this)
        top.move().translationY(-dpf(60f)).alpha(0f).setDuration(220).start()
        middle.move().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(160).start()
        leftChips.move().translationY(dpf(80f)).alpha(0f).setDuration(220).start()
        rightChips.move().translationY(dpf(80f)).alpha(0f).setDuration(220).start()
        bank.move().alpha(0f).translationY(-dpf(30f)).setDuration(200).start()
        bubbles.move().alpha(0f).translationY(-dpf(30f)).setDuration(200).withEndAction { visibility = GONE }.start()
    }

    override fun onDetachedFromWindow() {
        stopIdle()
        Anim.cancelTree(this)
        super.onDetachedFromWindow()
    }

    /**
     * A page is opening (false) or closing (true). Opening: everything drops
     * away quickly (the logo up, the chips down) before the page fades in;
     * closing: it is put back so [show] can pop it all in again.
     */
    fun setShown(show: Boolean) {
        val parts = listOf(top, middle, leftChips, rightChips, bank, bubbles)
        stopIdle()
        Anim.cancelTree(this)
        if (show) {
            visibility = VISIBLE
            for (p in parts) { Anim.reset(p); p.visibility = VISIBLE }
            bubbles.visibility = if (Progress.bubbles > 0) VISIBLE else GONE
        } else {
            top.move().translationY(-dpf(40f)).alpha(0f).setDuration(140).withEndAction { top.visibility = INVISIBLE }.start()
            middle.move().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(120).withEndAction { middle.visibility = INVISIBLE }.start()
            leftChips.move().translationY(dpf(50f)).alpha(0f).setDuration(140).withEndAction { leftChips.visibility = INVISIBLE }.start()
            rightChips.move().translationY(dpf(50f)).alpha(0f).setDuration(140).withEndAction { rightChips.visibility = INVISIBLE }.start()
            bank.move().alpha(0f).translationY(-dpf(20f)).setDuration(120).withEndAction { bank.visibility = INVISIBLE }.start()
            bubbles.move().alpha(0f).translationY(-dpf(20f)).setDuration(120).withEndAction { bubbles.visibility = INVISIBLE }.start()
        }
    }
}
