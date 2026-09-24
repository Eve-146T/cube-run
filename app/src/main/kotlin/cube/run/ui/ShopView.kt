package cube.run.ui

import cube.run.R

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.Layout
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Settings
import android.view.animation.PathInterpolator

/**
 * The shop: a showroom strip up top where the engine shows your cube (with
 * a sunburst behind it), and a dark sheet below it holding the cards — the
 * cards scroll inside the sheet, so nothing ever covers the cube. Bubble
 * shields to stock up on, one bold card per power-up whose duration you
 * level up, then the perks. Cards are glass over the sheet — a coloured
 * header band with the item's icon, what you have now and what the next
 * level gives, a bar of segments that pops as it fills, and a coin price
 * button. Every purchase goes through [Progress] and plays out on the cube
 * (see [Stage.demoRequests]): the bubble goes up around it, coins fly into
 * it, it lifts on a jet of flame…
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class ShopView(
    activity: Activity,
    kit: UiKit,
    private val balance: LinearLayout,
    private val onProgress: (Float) -> Unit,
    private val onOpenMysteryBox: (Progress.BoxReward) -> Unit = {},
    private val onVoidPurchase: (() -> Unit, () -> Unit) -> Unit,
    private val onProgressReset: () -> Unit = {},
    onClose: () -> Unit,
) : Page(activity, kit, kit.ctx.getString(R.string.text_shop), dark = true, onClosed = onClose) {

    private val list = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false; clipToPadding = false
        setPadding(dp(16f), dp(18f), dp(16f), dp(28f))
    }
    /** How tall the showroom strip is (the cube lives there; the camera is aimed to match). */
    private val showroomDp = 150f
    private var progress = 0f
    private var navigation: ValueAnimator? = null
    private var balanceCount: ValueAnimator? = null
    private val bars = HashMap<String, SegmentBar>()
    private val cards = HashMap<String, View>()
    private val nowViews = HashMap<String, View>()
    private val nextViews = HashMap<String, View>()
    private var paying = false
    private val glass = Theme.alpha(Theme.WHITE, 36)
    private val glassLine = Theme.alpha(Theme.WHITE, 80)
    private var preparedState = currentState()
    private var pendingBox: Progress.BoxReward? = null
    private var cubeDownX = 0f
    private var cubeDownY = 0f
    private var cubeGesture = false
    private var cubeSwiped = false
    private var resetConfirmation: ResetProgressSheet? = null

    internal fun darknessFocus(): View? = cards["darkness"]

    /**
     * The void show wants the whole stage: the sheet drops away and the header fades in place
     * (a header translated over the GL surface paints a frame late). [away] = false brings the
     * sheet back opaque, sliding up; [instant] snaps to the resting state.
     */
    internal fun stepAside(away: Boolean, instant: Boolean = false) {
        content.animate().cancel(); topBar.animate().cancel()
        if (instant) { content.translationY = 0f; content.alpha = 1f; topBar.alpha = 1f; return }
        if (away) {
            content.animate().translationY(height * .45f).alpha(0f).setStartDelay(120).setDuration(380)
                .setInterpolator(Anim.ease).setUpdateListener { Anim.repaint(this) }.start()
            topBar.animate().alpha(0f).setStartDelay(120).setDuration(260).setUpdateListener { Anim.repaint(this) }.start()
        } else {
            content.alpha = 1f
            content.animate().translationY(0f).setStartDelay(0).setDuration(560)
                .setInterpolator(Anim.ease).setUpdateListener { Anim.repaint(this) }.start()
            topBar.animate().alpha(1f).setStartDelay(260).setDuration(300).setUpdateListener { Anim.repaint(this) }.start()
        }
    }

    // Keep a payment's target fixed until it settles, including the short coin flight.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        if (paying) true else super.dispatchTouchEvent(event)

    private val showroom = View(activity).apply {
        contentDescription = kit.ctx.getString(R.string.text_shop_cube)
        isClickable = true
        setOnClickListener { playWithCube(Stage.SHOP_TAP) }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cubeGesture = !closing && progress == 1f &&
                        kotlin.math.abs(event.x - view.width / 2f) <= dpf(70f) &&
                        kotlin.math.abs(event.y - view.height / 2f) <= dpf(65f)
                    cubeDownX = event.x; cubeDownY = event.y; cubeSwiped = false
                }
                MotionEvent.ACTION_MOVE -> if (cubeGesture && !cubeSwiped) {
                    val dx = event.x - cubeDownX; val dy = event.y - cubeDownY
                    if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) > dpf(24f)) {
                        cubeSwiped = true
                        playWithCube(if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                            if (dx > 0f) Stage.SHOP_RIGHT else Stage.SHOP_LEFT
                        } else if (dy < 0f) Stage.SHOP_UP else Stage.SHOP_DOWN)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (cubeGesture && !cubeSwiped) view.performClick()
                    cubeGesture = false
                }
                MotionEvent.ACTION_CANCEL -> cubeGesture = false
            }
            true
        }
    }

    private fun playWithCube(gesture: Int) {
        if (!closing && progress == 1f && !paying) Stage.shopPlayRequests.set(gesture)
    }

    private fun currentState(): List<Int> = listOf(Progress.coins, Progress.bubbles, Progress.revives,
        Progress.ownedSkins, Progress.ownedBubbleSkins, Progress.ownedTrails,
        if (Progress.achievementsUnlocked) 1 else 0, Progress.voidPurchases,
        if (Progress.voidAvailable) 1 else 0, if (Settings.devMode) 1 else 0) +
        (Progress.upgrades + Progress.perks).map { Progress.level(it) }

    fun refreshAfterBox() { render(); preparedState = currentState() }

    /** A prepared page must never show prices or stock from before a wardrobe purchase/dev toggle. */
    fun isCurrent() = preparedState == currentState()

    /** Each power-up's colour, matching its pickup on the track. */
    private fun colorOf(u: Progress.Upgrade): Int = when (u) {
        Progress.BUBBLE -> Theme.BUBBLE
        Progress.MAGNET -> Theme.MAGNET
        Progress.MULT -> Theme.MULT
        else -> Theme.JET
    }

    private fun iconOf(u: Progress.Upgrade): Drawable = when (u) {
        Progress.BUBBLE -> BubbleIcon(Theme.WHITE)
        Progress.MAGNET -> MagnetIcon()
        Progress.MULT -> MultIcon(Theme.WHITE)
        else -> JetIcon(Theme.WHITE)
    }

    private fun blurb(u: Progress.Upgrade): String = when (u) {
        Progress.BUBBLE -> kit.ctx.getString(R.string.text_how_long_a_bubble_stays_up)
        Progress.MAGNET -> kit.ctx.getString(R.string.text_pulls_in_every_coin_from_far_away)
        Progress.MULT -> kit.ctx.getString(R.string.text_every_row_counts_double)
        else -> kit.ctx.getString(R.string.text_fly_above_everything_along_a_coin_line)
    }

    private fun demoOf(u: Progress.Upgrade): Int = when (u) {
        Progress.BUBBLE -> Stage.DEMO_BUBBLE
        Progress.MAGNET -> Stage.DEMO_MAGNET
        Progress.MULT -> Stage.DEMO_MULT
        Progress.JET -> Stage.DEMO_JET
        Progress.FASTERSTART -> 0
        Progress.SAFESTART -> Stage.DEMO_SAFESTART
        Progress.COINVALUE -> Stage.DEMO_COINS
        Progress.PORTALS -> Stage.DEMO_PORTAL
        else -> Stage.DEMO_BOX
    }

    init {
        content.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            // clipChildren stays ON here: it is what clips the sheet's scrolled cards to the sheet
            addView(showroom, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(showroomDp)))
            addView(ScrollView(activity).apply { // the sheet the cards live in
                isVerticalScrollBarEnabled = false
                clipToPadding = false
                background = GradientDrawable().apply {
                    cornerRadii = floatArrayOf(dpf(30f), dpf(30f), dpf(30f), dpf(30f), 0f, 0f, 0f, 0f)
                    setColor(Theme.INK)
                }
                addView(list)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        render()
    }

    /** Rebuild the whole list from [Progress] (cheap; done after every purchase). */
    private fun render(popped: Progress.Upgrade? = null) {
        val sheet = list.parent as? ScrollView
        val keepBottom = sheet != null && sheet.height > 0 && sheet.scrollY + sheet.height >= list.height - dp(12f)
        kit.labelOf(balance).text = Progress.coins.toString()
        list.removeAllViews()
        bars.clear(); cards.clear(); nowViews.clear(); nextViews.clear()
        list.addView(heading(kit.ctx.getString(R.string.text_consumables)), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8f); bottomMargin = dp(12f) })
        list.addView(bubbleCard(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        list.addView(reviveCard(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
        list.addView(mysteryCard(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
        list.addView(heading(kit.ctx.getString(R.string.text_power_ups)), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(22f); marginStart = dp(8f); bottomMargin = dp(2f) })
        for (u in Progress.upgrades) {
            list.addView(upgradeCard(u), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
        }
        list.addView(heading(kit.ctx.getString(R.string.text_perks)), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(22f); marginStart = dp(8f); bottomMargin = dp(2f) })
        for (u in Progress.perks) {
            list.addView(perkCard(u), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
        }
        list.addView(achievementCard(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
        if (Progress.voidAvailable) {
            // Offerings stay here; discovered cosmetics are purchased in the wardrobe.
            val darkness = voidCard()
            cards["darkness"] = darkness
            list.addView(darkness, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(28f) })
        }
        if (Settings.devMode) list.addView(kit.button("RESET PROGRESS", Theme.BERRY) { confirmProgressReset() }.apply {
            tag = "reset_progress"
            contentDescription = "Reset progress"
            setTextColor(Theme.WHITE)
            textSize = 16f
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(28f) })
        popped?.let { u -> // the segment just bought swells and settles
            val bar = bars[u.key] ?: return@let
            bar.popIndex = Progress.level(u) - 1
            ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 420; interpolator = Anim.spring
                addUpdateListener { a -> bar.pop = a.animatedValue as Float }
                Anim.cancelOnDetach(bar, this)
                start()
            }
        }
        if (keepBottom && sheet != null) sheet.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                sheet.viewTreeObserver.removeOnPreDrawListener(this)
                if (!closing) sheet.scrollTo(0, list.height)
                return true
            }
        })
        postOnAnimation { Anim.repaint(this) }
    }

    /** An opaque sheet rises from the bottom; the header waits for the menu title to clear. */
    override fun animateEntrance() {
        // Rasterize the cards before changing the GL scene; slide a cached layer, not dozens of labels.
        preparePanelLayer()
        Stage.shopProgress = 0f
        Stage.mode = Stage.SHOP
        alpha = 1f
        place(0f)
        navigate(1f) {}
    }

    private fun place(value: Float) {
        progress = value
        Stage.shopProgress = value
        val travel = (height - paddingTop - content.top - dp(showroomDp)).coerceAtLeast(0)
        content.translationY = travel * (1f - value)
        val header = ((value - 0.65f) / 0.35f).coerceIn(0f, 1f)
        topBar.alpha = header
        topBar.translationY = -dpf(12f) * (1f - header)
        onProgress(value)
        Anim.repaint(this)
    }

    private fun navigate(target: Float, onFinished: () -> Unit) {
        navigation?.cancel()
        preparePanelLayer()
        navigation = ValueAnimator.ofFloat(progress, target).apply {
            duration = ((if (target == 1f) 340 else 380) * kotlin.math.abs(target - progress)).toLong().coerceAtLeast(1)
            interpolator = if (target == 1f) PathInterpolator(0.2f, 0f, 0f, 1f) else PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener { place(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) {
                        content.setLayerType(View.LAYER_TYPE_NONE, null)
                        onFinished()
                    }
                }
            })
            start()
        }
    }

    private fun preparePanelLayer() {
        if (content.width > 0 && content.height > 0 && content.layerType != View.LAYER_TYPE_HARDWARE) {
            content.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            content.buildLayer()
        }
    }

    override fun animateExit(onFinished: () -> Unit) {
        cubeGesture = false
        Stage.shopPlayRequests.set(0)
        balanceCount?.cancel()
        kit.labelOf(balance).text = Progress.coins.toString()
        navigate(0f) {
            Stage.mode = Stage.NONE
            onFinished()
        }
    }

    override fun onDetachedFromWindow() {
        resetConfirmation?.let { Anim.cancelTree(it); (it.parent as? ViewGroup)?.removeView(it) }
        resetConfirmation = null
        Stage.shopPlayRequests.set(0)
        navigation?.cancel()
        balanceCount?.cancel()
        if (Stage.mode == Stage.SHOP) Stage.mode = Stage.NONE
        super.onDetachedFromWindow()
    }

    private fun heading(t: String) = kit.stageText(t, 13f, Theme.alpha(Theme.WHITE, 230), weight = 700, gravity = Gravity.START, stroke = 1.5f).apply { letterSpacing = kit.tracking(0.16f) }

    /** A glass card with a coloured header band (icon + name + blurb). */
    private fun card(key: String, color: Int, icon: Drawable, name: String, blurb: String, solid: Boolean = false, body: LinearLayout.() -> Unit): View {
        val outer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            background = GradientDrawable().apply { cornerRadius = dpf(24f); setColor(glass); setStroke(dp(1.5f), glassLine) }
        }
        cards[key] = outer
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            background = (if (solid) GradientDrawable().apply { setColor(color) }
                else GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(color, Theme.lighten(color, 0.22f)))).apply {
                cornerRadii = floatArrayOf(dpf(24f), dpf(24f), dpf(24f), dpf(24f), 0f, 0f, 0f, 0f)
            }
            addView(ImageView(activity).apply { // the icon on a soft white badge, so it reads on any band
                setImageDrawable(icon)
                val p = dp(7f); setPadding(p, p, p, p)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Theme.alpha(Theme.WHITE, 70)) }
            }, LinearLayout.LayoutParams(dp(48f), dp(48f)))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(kit.text(name, 19f, Theme.onColor(color), 700, Gravity.START))
                addView(kit.text(blurb, 12f, Theme.alpha(Theme.onColor(color), 215), 500, Gravity.START))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12f) })
        }
        outer.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        outer.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            setPadding(dp(16f), dp(12f), dp(16f), dp(14f))
            body()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return outer
    }

    /** Your stock: the count, big, then a rack of [slots] icons that fills up as you buy (every full rack starts a fresh one). */
    private fun rack(slots: Int, n: Int, icon: () -> Drawable, color: Int): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(kit.stageText("×$n", 30f, color, stroke = 3.5f), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(12f) })
        val lit = if (n == 0) 0 else ((n - 1) % slots) + 1
        for (i in 0 until slots) {
            addView(ImageView(activity).apply { setImageDrawable(icon()); alpha = if (i < lit) 1f else 0.22f }, LinearLayout.LayoutParams(dp(24f), dp(24f)).apply { if (i > 0) marginStart = dp(3f) })
        }
    }

    private fun bubbleCard(): View = card("bubbles", Theme.BUBBLE, BubbleIcon(Theme.WHITE), kit.ctx.getString(R.string.text_bubble_shield), kit.ctx.getString(R.string.text_double_tap_to_block_a_hit)) {
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            addView(rack(5, Progress.bubbles, { BubbleIcon() }, Theme.lighten(Theme.CYAN, 0.5f)).also { nowViews["bubbles"] = it }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(priceButton(Progress.BUBBLE_PRICE, "bubbles", null, Stage.DEMO_BUBBLE) { Progress.buyBubble() },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(10f) })
        })
    }

    /** "now → NEXT" (what you are buying is the big, coloured one), the level bar, the price: shared by power-ups and perks. */
    private fun levelBody(host: LinearLayout, u: Progress.Upgrade, color: Int, now: String, next: String?) {
        val lvl = Progress.level(u)
        val price = Progress.nextPrice(u)
        val maxed = price == null
        host.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            val values = LinearLayout(activity).apply {
                val stacked = resources.configuration.screenWidthDp / resources.configuration.fontScale < 350 &&
                    (now.length > 4 || (next?.length ?: 0) > 4)
                orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false; clipToPadding = false
                fun value(text: String, size: Float, tint: Int, weight: Float, target: MutableMap<String, View>) {
                    addView(kit.stageText(text, size, tint, stroke = if (size > 16) 3.5f else 2f, gravity = Gravity.START).apply {
                        target[u.key] = this
                    }, if (stacked) LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    else LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight))
                }
                if (next != null) {
                    value(now, 16f, Theme.alpha(Theme.WHITE, 215), 1f, nowViews)
                    if (!stacked) addView(kit.stageText(if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) "←" else "→", 16f, Theme.alpha(Theme.WHITE, 190), stroke = 2f),
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(3f); marginEnd = dp(3f) })
                    value(next, if (stacked) 24f else 30f, Theme.lighten(color, .3f), 1.5f, nextViews)
                } else value(now, 30f, Theme.YELLOW, 1f, nowViews)
            }
            addView(values, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(10f) })
            addView(priceButton(price, u.key, u, demoOf(u)) { Progress.buyUpgrade(u) })
        })
        val bar = kit.segments(u.max).apply { level = lvl; this.color = if (maxed) Theme.GOLD else Theme.lighten(color, 0.15f); offColor = Theme.alpha(Theme.WHITE, 60) }
        bars[u.key] = bar
        host.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12f)).apply { topMargin = dp(12f) })
    }

    private fun upgradeCard(u: Progress.Upgrade): View {
        val color = colorOf(u)
        val lvl = Progress.level(u)
        val now = u.duration(lvl)
        val maxed = Progress.nextPrice(u) == null
        return card(u.key, color, iconOf(u), kit.ctx.gameText(u.name), blurb(u)) {
            levelBody(this, u, color, kit.ctx.getString(R.string.text_seconds, fmt(now)), if (maxed) null else kit.ctx.getString(R.string.text_seconds, fmt(now + u.step)))
        }
    }

    private fun perkColor(u: Progress.Upgrade): Int = when (u) {
        Progress.FASTERSTART -> Theme.ORANGE
        Progress.SAFESTART -> Theme.ORANGE
        Progress.COINVALUE -> Theme.GOLD
        Progress.PORTALS -> Theme.MINT
        else -> Theme.GRAPE
    }

    private fun perkIcon(u: Progress.Upgrade): Drawable = when (u) {
        Progress.FASTERSTART -> JetIcon(Theme.WHITE)
        Progress.SAFESTART -> BubbleIcon(Theme.WHITE)
        Progress.COINVALUE -> CoinIcon()
        Progress.PORTALS -> PortalIcon(Theme.WHITE)
        else -> BoxIcon(Theme.WHITE)
    }

    private fun perkBlurb(u: Progress.Upgrade): String = when (u) {
        Progress.FASTERSTART -> kit.ctx.getString(R.string.text_more_boost_presses_at_the_start_of_every_run)
        Progress.SAFESTART -> kit.ctx.getString(R.string.text_every_run_begins_under_a_bubble)
        Progress.COINVALUE -> kit.ctx.getString(R.string.text_every_coin_is_worth_more)
        Progress.PORTALS -> kit.ctx.getString(R.string.text_portals_to_bonus_worlds_open_sooner)
        else -> kit.ctx.getString(R.string.text_mystery_boxes_turn_up_more_often)
    }

    private fun perkValue(u: Progress.Upgrade, lvl: Int): String = when (u) {
        Progress.FASTERSTART -> kit.ctx.resources.getQuantityString(R.plurals.count_taps, 5 + lvl, 5 + lvl)
        Progress.SAFESTART -> if (lvl == 0) kit.ctx.getString(R.string.text_none) else kit.ctx.getString(R.string.text_seconds, fmt(3f + 1.5f * lvl))
        Progress.COINVALUE -> "×${fmt(u.duration(lvl))}"
        Progress.PORTALS -> kit.ctx.resources.getQuantityString(R.plurals.count_rows, 110 - 14 * lvl, 110 - 14 * lvl)
        else -> "×${fmt(1f + 0.5f * lvl)}"
    }

    private fun perkCard(u: Progress.Upgrade): View {
        val color = perkColor(u)
        val lvl = Progress.level(u)
        val maxed = Progress.nextPrice(u) == null
        return card(u.key, color, perkIcon(u), kit.ctx.gameText(u.name), perkBlurb(u)) {
            levelBody(this, u, color, perkValue(u, lvl), if (maxed) null else perkValue(u, lvl + 1))
        }
    }

    /** Second wind: a stock of revives. */
    private fun reviveCard(): View = card("revives", Theme.PINK, HeartIcon(Theme.WHITE), kit.ctx.getString(R.string.text_second_wind), kit.ctx.getString(R.string.text_revive_with_a_bubble)) {
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            addView(rack(3, Progress.revives, { HeartIcon(Theme.PINK) }, Theme.lighten(Theme.PINK, 0.4f)).also { nowViews["revives"] = it }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(priceButton(if (Progress.revives >= Progress.MAX_REVIVES) null else Progress.REVIVE_PRICE, "revives", null, Stage.DEMO_REVIVE) { Progress.buyRevive() },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(10f) })
        })
    }

    private fun mysteryCard(): View = card("mystery", Theme.GRAPE, BoxIcon(Theme.WHITE), activity.getString(R.string.shop_mystery_box), activity.getString(R.string.shop_mystery_box_detail), solid = true) {
        addView(priceButton(Progress.mysteryBoxPrice, "mystery", null, 0) {
            pendingBox = Progress.buyMysteryBox()
            pendingBox != null
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun achievementCard(): View = card("achievements", Theme.GOLD, AchievementShopIcon(), activity.getString(R.string.achievements_title),
        activity.getString(R.string.shop_achievements_detail), solid = true) {
        if (Progress.achievementsUnlocked) addView(CandyButton(activity, Theme.PLAY, activity.getString(R.string.shop_unlocked), 16f, dpf(5f), dpf(18f)).apply {
            // A read-only candy slab must not retain CandyButton's ACTION_DOWN press listener:
            // non-clickable views do not necessarily receive the matching release event.
            setOnTouchListener(null)
            isClickable = false; isLongClickable = false; isFocusable = false
            tag = "achievements_unlocked_status"
            contentDescription = activity.getString(R.string.shop_achievements_unlocked)
            setPadding(dp(20f), dp(12f), dp(20f), dp(12f))
            val check = UnlockedCheckIcon().apply { setBounds(0, 0, dp(22f), dp(22f)) }
            text = SpannableStringBuilder("\uFFFC  ${activity.getString(R.string.shop_unlocked)}").apply {
                setSpan(CenteredImageSpan(check), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        else addView(priceButton(Progress.ACHIEVEMENTS_PRICE, "achievements", null, 0) { Progress.buyAchievements() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun voidCard(): View = VoidCardView(activity, kit, Progress.voidLine,
        priceButton(Progress.voidPrice, "void", null, 0) { Progress.buyVoid() }).also { cards["void"] = it }

    /**
     * Gold when affordable, a quiet glass slab when not, a MAX badge when
     * there is nothing left to buy. A tap pays right here: [buy] takes the
     * coins, then they fly from the balance into this button.
     */
    private fun priceButton(price: Int?, key: String, u: Progress.Upgrade?, demo: Int, buy: () -> Boolean): View {
        if (price == null) return kit.pill(kit.ctx.getString(R.string.text_max), Theme.alpha(Theme.WHITE, 60), Theme.WHITE, 13f).apply { letterSpacing = kit.tracking(0.1f) }
        val can = price <= Progress.coins
        lateinit var btn: CandyButton
        val fullWidth = key == "mystery" || key == "achievements" || key == "void"
        btn = kit.button(kit.coins(price, if (fullWidth) 19f else 15f), if (can) Theme.GOLD else Theme.alpha(Theme.WHITE, 46),
            if (fullWidth) UiKit.Size.NORMAL else UiKit.Size.SMALL) {
            if (paying || closing || progress < 1f) return@button
            val before = Progress.coins
            if (!buy()) { broke(); return@button }
            pay(btn, before, key, u, demo, if (key == "mystery") before - price else Progress.coins)
        }
        if (!can) btn.setTextColor(Theme.alpha(Theme.WHITE, 170))
        return if (key == "void") FittedVoidPrice(activity, kit, btn, price) else btn
    }

    private fun fmt(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(kit.ctx.resources.configuration.locales[0], v)

    /**
     * Paying, where you tapped: coins fly from the balance into the button
     * while the balance drains; then the card flashes, its numbers roll over
     * (the big NEXT value becomes the small NOW), the new segment pops, and
     * the cube up top plays the thing you just bought.
     */
    private fun pay(btn: View, before: Int, key: String, u: Progress.Upgrade?, demo: Int, displayedBalance: Int) {
        paying = true
        Haptics.click()
        if (key == "void") {
            // The void takes over immediately. Its opaque scene owns the payment animation
            // and sits above the shared menu bank, outside this page's inset content.
            // Where you tapped: the button punches in and the card's hole flares before the sheet drops.
            Anim.pulse(btn, 1.1f, 200)
            cards["void"]?.let { Anim.pulse(it, 1.03f, 260) }
            // The coins pour out of the bank into the hole on the 3D stage: the balance drops
            // by each coin as it goes down (Stage.voidFed), and lands exactly on the new bank.
            balanceCount?.cancel()
            val label = kit.labelOf(balance)
            Stage.voidFed = 0f
            val drain = object : Runnable {
                override fun run() {
                    val fed = Stage.voidFed
                    label.text = (before - ((before - displayedBalance) * fed.toDouble()).toLong()).toString()
                    if (fed < 1f && paying && isAttachedToWindow) postOnAnimation(this)
                }
            }
            postOnAnimation(drain)
            onVoidPurchase({ if (isAttachedToWindow && !closing) render() }) {
                paying = false
                removeCallbacks(drain)
                label.text = displayedBalance.toString()
            }
            return
        }
        val ms = PayFx.fly(this, kit, balance, btn, n = 6, onDone = {
            if (closing) return@fly
            paying = false
            SoundFx.play("success", rate = 1.4f, vol = 0.55f); Haptics.success()
            Stage.demoRequests.set(demo)
            render(u)
            cards[key]?.let { PayFx.flash(it, dpf(24f)) }
            nowViews[key]?.let { Anim.popIn(it, 0, 0.6f, 360) }
            nextViews[key]?.let { Anim.slideIn(it, 60, dpf(30f), 320) }
            if (key == "mystery") pendingBox?.let { pendingBox = null; onOpenMysteryBox(it) }
        })
        // The reward is persisted already, but its value stays a surprise until the box opens.
        balanceCount = Anim.countTo(kit.labelOf(balance), before, displayedBalance, ms)
    }

    private fun broke() {
        SoundFx.play("tap", rate = 0.6f); Haptics.tick()
        Anim.shake(balance, dpf(8f))
    }

    private fun confirmProgressReset() {
        if (!Settings.devMode || paying || closing || progress < 1f || resetConfirmation != null) return
        val host = parent as? FrameLayout ?: return
        val sheet = ResetProgressSheet(activity, kit, onDismissed = { resetConfirmation = null }) {
            if (!Progress.resetForDeveloper()) return@ResetProgressSheet false
            balanceCount?.cancel()
            pendingBox = null
            Stage.clearPreview()
            Stage.shopPlayRequests.set(0)
            Stage.demoRequests.set(0)
            Stage.previewKicks.set(0)
            Stage.previewBuys.set(0)
            Stage.purchasedBoxRewards.clear()
            render()
            preparedState = currentState()
            onProgressReset()
            true
        }
        resetConfirmation = sheet
        // The menu's shared bank is a sibling above the shop. A full-window sibling sheet
        // covers that bank too, owns backdrop touches, and leaves the shop's stacking intact.
        host.addView(sheet, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    override fun onBack() {
        resetConfirmation?.let { it.dismiss(); return }
        if (!paying) close()
    }
}

/** Keep the coin and even a billion-coin offering centered together on one candy face. */
@SuppressLint("ViewConstructor")
private class FittedVoidPrice(
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
            contentDescription = "Offer $amount coins"
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
private class ResetProgressSheet(
    activity: Activity,
    kit: UiKit,
    onDismissed: () -> Unit,
    private val onConfirm: () -> Boolean,
) : Sheet(activity, kit, onDismissed) {
    private var committing = false
    private val explanation = kit.text("Delete all coins, upgrades, cosmetics, achievements and scores?\nThis cannot be undone.", 15f, Theme.INK, 500)

    init {
        tag = "reset_progress_confirmation"
        card.addView(kit.text("RESET PROGRESS?", 23f, Theme.INK, 700))
        card.addView(explanation, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })
        card.addView(kit.button("CANCEL", Theme.LAVENDER) { if (!committing) dismiss() }.apply {
            tag = "reset_progress_cancel"
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22f) })
        card.addView(kit.button("RESET", Theme.BERRY) {
            if (!committing) {
                committing = true
                if (onConfirm()) dismiss() else {
                    committing = false
                    explanation.text = "Couldn't save the reset. Please try again."
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
