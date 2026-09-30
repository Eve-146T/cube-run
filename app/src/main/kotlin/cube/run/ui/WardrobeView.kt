package cube.run.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import cube.run.R
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Shards
import cube.run.data.Skins
import cube.run.data.Wardrobe
import cube.run.ui.Anim.move
import kotlin.math.abs

/**
 * The wardrobe, full screen on the engine's dark stage: three categories —
 * CUBE skins, BUBBLE skins and TRAILs — as candy tabs under the title. The
 * game itself shows the item ([Stage.SKINS] + the preview fields): the cube
 * turning, the cube inside its bubble, or the cube looping through the air
 * shedding its trail. This page frames it: name and price, dots, arrows or
 * a swipe to browse, EQUIP / BUY.
 */
@SuppressLint("SetTextI18n", "ViewConstructor", "ClickableViewAccessibility")
class WardrobeView(activity: Activity, kit: UiKit, abilityStyle: Int = 0, onClose: () -> Unit) : Page(activity, kit, kit.ctx.getString(R.string.text_wardrobe), dark = true, onClosed = onClose) {

    private var cat = Wardrobe.CUBE
    private var index = Progress.equipped(cat)
    private val balance = kit.coinBank("")
    private val tabs = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; clipChildren = false; clipToPadding = false }
    private val tabViews = ArrayList<TextView>()
    private val name = kit.stageText("", 32f, stroke = 4f)
    private val shardDisplay = ShardDisplay(kit)
    private val dots = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    private val abilityDisplay = AbilityDisplay(activity, kit, abilityStyle)
    private val action: CandyButton
    private val actionLabel: WardrobeActionLabel
    private val left: View
    private val right: View
    private val itemDetails: LinearLayout
    private var paying = false
    private var downX = 0f
    private var downY = 0f
    private var swiped = false

    override fun onCompactChanged(compact: Boolean) {
        // Short panes keep the full layout and the real stage, just tighter:
        // the tabs hug the title, the details sit at the bottom edge, and the
        // stage frames the item in whatever gap is left between them.
        (tabs.layoutParams as FrameLayout.LayoutParams).topMargin = dp(if (compact) 0f else 4f)
        tabViews.forEach { tab ->
            tab.textSize = if (compact) 12f else 13f
            tab.setPadding(dp(if (compact) 12f else 16f), dp(if (compact) 5f else 7f), dp(if (compact) 12f else 16f), dp(if (compact) 5f else 7f))
        }
        for (arrow in listOf(left, right)) (arrow.layoutParams as FrameLayout.LayoutParams).apply {
            width = dp(if (compact) 46f else 54f); height = dp(if (compact) 50f else 58f)
            marginStart = dp(if (compact) 8f else 12f); marginEnd = marginStart
            resolveLayoutDirection(layoutDirection)
        }
        (itemDetails.layoutParams as FrameLayout.LayoutParams).bottomMargin = dp(if (compact) 8f else 112f)
        if (!compact) { Stage.focusFraction = Float.NaN; Stage.focusSpan = 0f }
        updateDotSpacing()
    }

    private val windowPosition = IntArray(2)
    private var tight = false
    private var fullButtonHeight = 0

    override fun onPaneMeasured(usableHeight: Int) {
        // A third of a phone keeps every control, a little closer together.
        tight = compactLayout && usableHeight < dp(360f)
        (dots.layoutParams as LinearLayout.LayoutParams).topMargin = dp(if (tight) 3f else if (compactLayout) 6f else 12f)
        (actionLabel.layoutParams as LinearLayout.LayoutParams).topMargin = dp(if (tight) 6f else if (compactLayout) 10f else 18f)
        dots.visibility = if (tight) View.GONE else View.VISIBLE
        // The same candy button, only less tall: at full height it would take a third of the pane.
        val buttonHeight = if (tight) dp(58f) else fullButtonHeight
        if (action.minimumHeight != buttonHeight) action.minimumHeight = buttonHeight
        val size = if (tight) 20f else if (compactLayout) 24f else 32f
        if (name.textSize != size * resources.displayMetrics.scaledDensity) name.textSize = size
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (!compactLayout) return
        // The middle of a short pane is covered by the tabs or the details: the
        // stage frames the item in the real gap between them, and the arrows flank
        // its name so they stay clear of the ability corner.
        val middle = (tabs.bottom + itemDetails.top) / 2
        // A third of a phone: the ability corner reaches the name row, so they flank the button.
        val beside = if (tight) actionLabel else name
        val row = itemDetails.top + beside.top + beside.height / 2
        for (arrow in listOf(left, right)) arrow.layout(arrow.left, row - arrow.measuredHeight / 2, arrow.right, row + arrow.measuredHeight - arrow.measuredHeight / 2)
        val at = windowPosition
        content.getLocationInWindow(at)
        val window = rootView.height
        // The framed point sits a little below the item's visual centre.
        if (window > 0 && !closing) {
            Stage.focusFraction = (at[1] + middle + dp(12f)).toFloat() / window
            Stage.focusSpan = (itemDetails.top - tabs.bottom).toFloat() / window
        }
    }

    init {
        addRight(balance)

        // ---- tabs under the title
        for (c in Wardrobe.cats) {
            val t = kit.text(kit.ctx.gameText(Wardrobe.label(c)).uppercase(resources.configuration.locales[0]), 13f, Theme.WHITE, 700).apply {
                letterSpacing = kit.tracking(0.1f)
                setPadding(dp(16f), dp(7f), dp(16f), dp(7f))
                setOnClickListener { switchTo(c) }
            }
            tabViews.add(t)
            tabs.addView(t, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(4f); marginEnd = dp(4f) })
        }
        content.addView(tabs, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.TOP; topMargin = dp(4f) })

        // ---- side arrows, mid-screen
        left = kit.chip(R.drawable.ic_chevron_left, Theme.WHITE, Theme.INK, activity.getString(R.string.cd_prev)) { step(-1) }
        right = kit.chip(R.drawable.ic_chevron_right, Theme.WHITE, Theme.INK, activity.getString(R.string.cd_next)) { step(1) }
        content.addView(left, FrameLayout.LayoutParams(dp(54f), dp(58f)).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START; marginStart = dp(12f) })
        content.addView(right, FrameLayout.LayoutParams(dp(54f), dp(58f)).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END; marginEnd = dp(12f) })

        // ---- bottom: name, status, dots, action
        action = kit.button("", Theme.PLAY, UiKit.Size.BIG) { act() }.apply {
            // Keep the generous height of the former two-line shard unlock button.
            minimumHeight = dp(34f) + kotlin.math.ceil(paint.fontSpacing + paint.fontMetrics.bottom - paint.fontMetrics.top).toInt()
            tag = "wardrobe_action_button"
            setSingleLine()
            setHorizontallyScrolling(false)
        }
        actionLabel = WardrobeActionLabel(activity, kit, action)
        fullButtonHeight = action.minimumHeight
        itemDetails = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false; clipToPadding = false
            addView(name)
            addView(abilityDisplay.inline, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8f); marginStart = dp(22f); marginEnd = dp(22f)
            })
            addView(dots, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
            addView(actionLabel, LinearLayout.LayoutParams(dp(230f), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18f) })
        }
        content.addView(itemDetails, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM; bottomMargin = dp(112f)
        })
        content.addView(abilityDisplay.floating, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(22f); marginEnd = dp(22f)
            if (abilityStyle == 6) { gravity = Gravity.BOTTOM; bottomMargin = dp(28f) }
            else { gravity = Gravity.TOP; topMargin = dp(108f) }
        })
        if (abilityStyle == 0) tabs.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val params = abilityDisplay.floating.layoutParams as? FrameLayout.LayoutParams ?: return@addOnLayoutChangeListener
            val top = view.bottom + dp(10f)
            if (params.topMargin != top) {
                params.topMargin = top
                abilityDisplay.floating.layoutParams = params
            }
        }
        render()
    }

    override fun animateEntrance() {
        super.animateEntrance()
        for (i in 0 until tabs.childCount) Anim.riseIn(tabs.getChildAt(i), i * 15L, dpf(8f), 160)
        Anim.popIn(left, 0, 0.85f, 180); Anim.popIn(right, 0, 0.85f, 180)
        Anim.riseIn(itemDetails, 0, dpf(16f), 180)
    }

    override fun onNavigationShown() {
        if (!compactLayout) { Stage.focusFraction = Float.NaN; Stage.focusSpan = 0f }
        Stage.previewCat = cat
        Stage.mode = Stage.SKINS
        applyPreview()
    }

    /** Push the browsed item into the engine's preview slots. */
    private fun applyPreview() {
        Stage.previewCat = cat
        when (cat) {
            Wardrobe.CUBE -> Stage.previewSkin = index
            Wardrobe.BUBBLE -> Stage.previewBubble = index
            else -> Stage.previewTrail = index
        }
    }

    private fun switchTo(c: Int) {
        if (c == cat) return
        SoundFx.play("tap"); Haptics.tick()
        Stage.clearPreview()
        cat = c
        index = Progress.equipped(cat)
        applyPreview() // the stage morphs: the bubble inflates / the cube glides out onto its loop
        Anim.riseIn(name, distancePx = dpf(16f), duration = 240)
        render()
    }

    /** A horizontal swipe anywhere browses; taps fall through to the buttons. Returns true on a swipe. */
    private fun swipe(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; swiped = false }
            MotionEvent.ACTION_MOVE -> {
                if (swiped) return true
                val dx = ev.x - downX; val dy = ev.y - downY
                if (abs(dx) > dp(48f) && abs(dx) > abs(dy) * 1.5f) {
                    swiped = true
                    step((if (dx < 0) 1 else -1) * (if (layoutDirection == View.LAYOUT_DIRECTION_RTL) -1 else 1))
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { val was = swiped; swiped = false; return was }
        }
        return false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = swipe(ev)
    override fun onTouchEvent(event: MotionEvent): Boolean { swipe(event); return true }

    private fun step(d: Int) {
        val visible = visibleItems()
        val n = visible.size
        val position = visible.indexOf(index).coerceAtLeast(0)
        index = visible[((position + d) % n + n) % n]
        applyPreview()
        Stage.previewKicks.incrementAndGet() // the stage spin-flips the cube with a pop
        Haptics.tick()
        val visualDirection = d * if (layoutDirection == View.LAYOUT_DIRECTION_RTL) -1 else 1
        Anim.slideIn(name, fromX = visualDirection * dpf(40f), duration = 220)
        val arrow = if (d > 0) right else left
        Anim.reset(arrow)
        arrow.translationX = visualDirection * dpf(8f)
        arrow.move().translationX(0f).setDuration(220).setInterpolator(Anim.ease).start()
        render()
    }

    private fun visibleItems(): List<Int> = Wardrobe.shopItems(cat).filter { Progress.secretAvailable(cat, it) }

    private fun render() {
        val owned = Progress.owns(cat, index)
        val equipped = Progress.equipped(cat) == index
        val price = Wardrobe.price(cat, index)
        val shardSkin = if (cat == Wardrobe.CUBE) Skins.get(index).takeIf { it.shardOnly } else null
        kit.labelOf(balance).text = number(Progress.coins)
        name.text = kit.ctx.gameText(Wardrobe.name(cat, index)).uppercase(kit.ctx.resources.configuration.locales[0])
        action.visibility = View.VISIBLE
        action.maxLines = 1
        action.setAutoSizeTextTypeUniformWithConfiguration(12, 22, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        action.contentDescription = null
        action.setProgress()
        val canUnlock = shardSkin != null && Progress.shards(shardSkin.shardType) >= shardSkin.shardsNeeded
        actionLabel.bind(when {
            equipped -> kit.ctx.getString(R.string.text_equipped)
            owned -> kit.ctx.getString(R.string.text_equip)
            shardSkin != null -> kit.ctx.getString(R.string.text_unlock)
            else -> null
        }, price)
        action.color = when {
            equipped -> Theme.alpha(Theme.WHITE, 200)
            owned -> Theme.PLAY
            shardSkin != null -> if (canUnlock) Theme.hsv(Shards.get(shardSkin.shardType).hue, 0.7f, 1f) else Theme.alpha(Theme.MUTED, 200)
            price <= Progress.coins -> Theme.GOLD
            else -> Theme.alpha(Theme.MUTED, 200)
        }
        if (shardSkin != null && !owned) shardDisplay.bind(action, shardSkin, Progress.shards(shardSkin.shardType))
        action.alpha = if (equipped) 0.7f else 1f
        for ((i, t) in tabViews.withIndex()) {
            val on = Wardrobe.cats[i] == cat
            t.setTextColor(if (on) Theme.INK else Theme.WHITE)
            t.background = GradientDrawable().apply {
                cornerRadius = dpf(20f)
                setColor(if (on) Theme.WHITE else Theme.alpha(Theme.WHITE, 40))
                setStroke(dp(1.5f), Theme.alpha(Theme.WHITE, if (on) 0 else 90))
            }
        }
        // Every void discovery keeps its question button, without revealing gameplay details.
        val displayedAbilities = if (Wardrobe.isSecret(cat, index)) listOf(Skins.Ability.SECRET)
            else Wardrobe.abilities(cat, index)
        abilityDisplay.bind(displayedAbilities, "$cat:$index")
        dots.removeAllViews()
        for (i in visibleItems()) {
            dots.addView(View(activity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(when { i == index -> Theme.WHITE; Progress.owns(cat, i) -> Theme.alpha(Theme.WHITE, 130); else -> Theme.alpha(Theme.WHITE, 55) })
                }
            }, LinearLayout.LayoutParams(dp(if (i == index) 9f else 6f), dp(if (i == index) 9f else 6f)).apply { marginStart = dp(3f); marginEnd = dp(3f) })
        }
        updateDotSpacing()
    }

    private fun updateDotSpacing() {
        for (i in 0 until dots.childCount) {
            (dots.getChildAt(i).layoutParams as LinearLayout.LayoutParams).apply {
                marginStart = dp(if (compactLayout) 1.5f else 3f); marginEnd = marginStart
            }
        }
    }

    private fun equip(category: Int, id: Int) {
        Progress.equip(category, id)
        if (category == Wardrobe.CUBE) cube.run.intro.LaunchAppearance.remember(activity)
    }

    private fun act() {
        if (paying) return
        val before = Progress.coins
        val id = index; val c = cat
        val shardSkin = if (cat == Wardrobe.CUBE) Skins.get(index).takeIf { it.shardOnly } else null
        when {
            Progress.equipped(cat) == index -> {}
            Progress.owns(cat, index) -> { equip(cat, index); SoundFx.play("tap"); Haptics.tick(); Anim.pulse(name, 1.15f); Stage.previewKicks.incrementAndGet(); render() }
            shardSkin != null -> { // shards, not coins
                if (Progress.unlockWithShards(index)) {
                    equip(c, id)
                    Stage.previewBuys.incrementAndGet()
                    Anim.pulse(name, 1.25f)
                    render()
                } else { SoundFx.play("tap", rate = 0.6f); Haptics.tick(); Anim.shake(action, dpf(8f)) }
            }
            Progress.buy(cat, index) -> { // paid: coins fly from the balance into the button, then the cube celebrates and it's yours
                paying = true
                // Commit the choice with the purchase; its visual completion may
                // be cancelled if the user leaves the page while coins are flying.
                equip(c, id)
                Haptics.click()
                val ms = PayFx.fly(this, kit, balance, action, n = 6, onDone = {
                    paying = false
                    if (!closing && cat == c && index == id) {
                        Stage.previewBuys.incrementAndGet()
                        Anim.pulse(name, 1.25f)
                        render()
                    }
                })
                Anim.countTo(kit.labelOf(balance), before, Progress.coins, ms, ::number)
            }
            else -> { // can't afford: nudge the balance
                SoundFx.play("tap", rate = 0.6f); Haptics.tick()
                Anim.shake(balance, dpf(8f))
            }
        }
    }

    override fun onBack() {
        Stage.clearPreview()
        Stage.focusFraction = Float.NaN; Stage.focusSpan = 0f
        Stage.mode = Stage.NONE
        close()
    }
}

/** Measure the purchase label and its coin together before the candy button is laid out. */
@SuppressLint("ViewConstructor")
private class WardrobeActionLabel(
    activity: Activity,
    private val kit: UiKit,
    private val button: CandyButton,
) : FrameLayout(activity) {
    private var status: String? = ""
    private var amount = ""
    private var labelChanged = true
    private var fittedPx = Float.NaN

    init {
        clipChildren = false; clipToPadding = false
        addView(button, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun bind(status: String?, price: Int) {
        this.status = status
        amount = number(price)
        labelChanged = true
        button.contentDescription = status ?: button.context.getString(R.string.cd_buy_coins, amount)
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val paint = TextPaint(button.paint)
        val nominal = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 22f, button.resources.displayMetrics)
        paint.textSize = nominal
        val fullWidth = Layout.getDesiredWidth(labelAt(nominal), paint)
        // Preserve the usual big-button padding for short labels. Longer prices use
        // the otherwise empty edge space before reducing the visible letter size.
        val horizontal = kit.dp(if (fullWidth <= width - kit.dp(70f)) 34f else 18f)
        if (button.paddingLeft != horizontal) button.setPadding(horizontal, kit.dp(14f), horizontal, kit.dp(14f))
        val budget = (width - button.compoundPaddingLeft - button.compoundPaddingRight - kit.dp(2f)).coerceAtLeast(1)
        var size = nominal
        if (fullWidth > budget) {
            var low = 1f
            var high = nominal
            repeat(16) {
                val candidate = (low + high) / 2f
                paint.textSize = candidate
                if (Layout.getDesiredWidth(labelAt(candidate), paint) <= budget) low = candidate else high = candidate
            }
            size = low
        }
        if (labelChanged || size != fittedPx) {
            labelChanged = false
            fittedPx = size
            // Commit exactly the pixels that were measured; SP scales nonlinearly on
            // newer Android versions, and the coin must track the same physical size.
            button.text = labelAt(size)
            button.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun labelAt(sizePx: Float): CharSequence = status ?: SpannableStringBuilder(button.context.getString(R.string.wardrobe_buy)).apply {
        val coin = CoinIcon().apply {
            val edge = (sizePx * 1.15f).toInt().coerceAtLeast(1)
            setBounds(0, 0, edge, edge)
        }
        append(" ", CenteredImageSpan(coin), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        append("\u2009").append(amount)
    }
}
