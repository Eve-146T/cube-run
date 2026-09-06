package cube.run.ui

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.data.Progress
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
    onClose: () -> Unit,
) : Page(activity, kit, "SHOP", dark = true, onClosed = onClose) {

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
    private val preparedState = currentState()
    private var cubeDownX = 0f
    private var cubeDownY = 0f
    private var cubeGesture = false
    private var cubeSwiped = false

    private val showroom = View(activity).apply {
        contentDescription = "Shop cube"
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

    private fun currentState(): List<Int> = listOf(Progress.coins, Progress.bubbles, Progress.revives) +
        (Progress.upgrades + Progress.perks).map { Progress.level(it) }

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
        Progress.BUBBLE -> "How long a bubble stays up"
        Progress.MAGNET -> "Pulls in every coin from far away"
        Progress.MULT -> "Every row counts double"
        else -> "Fly above everything along a coin line"
    }

    private fun demoOf(u: Progress.Upgrade): Int = when (u) {
        Progress.BUBBLE -> Stage.DEMO_BUBBLE
        Progress.MAGNET -> Stage.DEMO_MAGNET
        Progress.MULT -> Stage.DEMO_MULT
        Progress.JET -> Stage.DEMO_JET
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
        kit.labelOf(balance).text = Progress.coins.toString()
        list.removeAllViews()
        bars.clear(); cards.clear(); nowViews.clear(); nextViews.clear()
        list.addView(bubbleCard(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        list.addView(heading("POWER-UPS"), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(22f); leftMargin = dp(8f); bottomMargin = dp(2f) })
        for (u in Progress.upgrades) {
            list.addView(upgradeCard(u), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
        }
        list.addView(heading("PERKS"), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(22f); leftMargin = dp(8f); bottomMargin = dp(2f) })
        list.addView(reviveCard(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
        for (u in Progress.perks) {
            list.addView(perkCard(u), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
        }
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
        Stage.shopPlayRequests.set(0)
        navigation?.cancel()
        balanceCount?.cancel()
        if (Stage.mode == Stage.SHOP) Stage.mode = Stage.NONE
        super.onDetachedFromWindow()
    }

    private fun heading(t: String) = kit.stageText(t, 13f, Theme.alpha(Theme.WHITE, 230), weight = 700, gravity = Gravity.START, stroke = 1.5f).apply { letterSpacing = 0.16f }

    /** A glass card with a coloured header band (icon + name + blurb). */
    private fun card(key: String, color: Int, icon: Drawable, name: String, blurb: String, body: LinearLayout.() -> Unit): View {
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
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(color, Theme.lighten(color, 0.22f))).apply {
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
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(12f) })
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
        addView(kit.stageText("×$n", 30f, color, stroke = 3.5f), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(12f) })
        val lit = if (n == 0) 0 else ((n - 1) % slots) + 1
        for (i in 0 until slots) {
            addView(ImageView(activity).apply { setImageDrawable(icon()); alpha = if (i < lit) 1f else 0.22f }, LinearLayout.LayoutParams(dp(24f), dp(24f)).apply { if (i > 0) leftMargin = dp(3f) })
        }
    }

    private fun bubbleCard(): View = card("bubbles", Theme.BUBBLE, BubbleIcon(Theme.WHITE), "Bubble shield", "Double-tap in a run: takes one hit, smashes the row") {
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            addView(rack(5, Progress.bubbles, { BubbleIcon() }, Theme.lighten(Theme.CYAN, 0.5f)).also { nowViews["bubbles"] = it }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(priceButton(Progress.BUBBLE_PRICE, "bubbles", null, Stage.DEMO_BUBBLE) { Progress.buyBubble() },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(10f) })
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
            if (next != null) {
                addView(kit.stageText(now, 16f, Theme.alpha(Theme.WHITE, 215), stroke = 2f, gravity = Gravity.START).apply { maxLines = 1 }.also { nowViews[u.key] = it })
                addView(kit.stageText("→", 16f, Theme.alpha(Theme.WHITE, 190), stroke = 2f), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(6f); rightMargin = dp(6f) })
                addView(kit.stageText(next, 30f, Theme.lighten(color, 0.3f), stroke = 3.5f, gravity = Gravity.START).apply { maxLines = 1 }.also { nextViews[u.key] = it })
            } else {
                addView(kit.stageText(now, 30f, Theme.YELLOW, stroke = 3.5f, gravity = Gravity.START).apply { maxLines = 1 }.also { nowViews[u.key] = it })
            }
            addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
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
        return card(u.key, color, iconOf(u), u.name, blurb(u)) {
            levelBody(this, u, color, "${fmt(now)} s", if (maxed) null else "${fmt(now + u.step)} s")
        }
    }

    private fun perkColor(u: Progress.Upgrade): Int = when (u) {
        Progress.SAFESTART -> Theme.ORANGE
        Progress.COINVALUE -> Theme.GOLD
        Progress.PORTALS -> Theme.MINT
        else -> Theme.GRAPE
    }

    private fun perkIcon(u: Progress.Upgrade): Drawable = when (u) {
        Progress.SAFESTART -> BubbleIcon(Theme.WHITE)
        Progress.COINVALUE -> CoinIcon()
        Progress.PORTALS -> PortalIcon(Theme.WHITE)
        else -> BoxIcon(Theme.WHITE)
    }

    private fun perkBlurb(u: Progress.Upgrade): String = when (u) {
        Progress.SAFESTART -> "Every run begins under a bubble"
        Progress.COINVALUE -> "Every coin is worth more"
        Progress.PORTALS -> "Portals to bonus worlds open sooner"
        else -> "Mystery boxes turn up more often"
    }

    private fun perkValue(u: Progress.Upgrade, lvl: Int): String = when (u) {
        Progress.SAFESTART -> if (lvl == 0) "none" else "${fmt(3f + 1.5f * lvl)} s"
        Progress.COINVALUE -> "×${fmt(u.duration(lvl))}"
        Progress.PORTALS -> "${110 - 14 * lvl} rows"
        else -> "×${fmt(1f + 0.5f * lvl)}"
    }

    private fun perkCard(u: Progress.Upgrade): View {
        val color = perkColor(u)
        val lvl = Progress.level(u)
        val maxed = Progress.nextPrice(u) == null
        return card(u.key, color, perkIcon(u), u.name, perkBlurb(u)) {
            levelBody(this, u, color, perkValue(u, lvl), if (maxed) null else perkValue(u, lvl + 1))
        }
    }

    /** Second wind: a stock of revives. */
    private fun reviveCard(): View = card("revives", Theme.PINK, HeartIcon(Theme.WHITE), "Second wind", "A crash is not the end: back up, bubbled, still running") {
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            addView(rack(3, Progress.revives, { HeartIcon(Theme.PINK) }, Theme.lighten(Theme.PINK, 0.4f)).also { nowViews["revives"] = it }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(priceButton(Progress.REVIVE_PRICE, "revives", null, Stage.DEMO_REVIVE) { Progress.buyRevive() },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(10f) })
        })
    }

    /**
     * Gold when affordable, a quiet glass slab when not, a MAX badge when
     * there is nothing left to buy. A tap pays right here: [buy] takes the
     * coins, then they fly from the balance into this button.
     */
    private fun priceButton(price: Int?, key: String, u: Progress.Upgrade?, demo: Int, buy: () -> Boolean): View {
        if (price == null) return kit.pill("MAX", Theme.alpha(Theme.WHITE, 60), Theme.WHITE, 13f).apply { letterSpacing = 0.1f }
        val can = price <= Progress.coins
        lateinit var btn: CandyButton
        btn = kit.button(kit.coins(price, 15f), if (can) Theme.GOLD else Theme.alpha(Theme.WHITE, 46), UiKit.Size.SMALL) {
            if (paying || closing || progress < 1f) return@button
            val before = Progress.coins
            if (!buy()) { broke(); return@button }
            pay(btn, before, key, u, demo)
        }
        if (!can) btn.setTextColor(Theme.alpha(Theme.WHITE, 170))
        return btn
    }

    private fun fmt(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)

    /**
     * Paying, where you tapped: coins fly from the balance into the button
     * while the balance drains; then the card flashes, its numbers roll over
     * (the big NEXT value becomes the small NOW), the new segment pops, and
     * the cube up top plays the thing you just bought.
     */
    private fun pay(btn: View, before: Int, key: String, u: Progress.Upgrade?, demo: Int) {
        paying = true
        Haptics.click()
        val ms = PayFx.fly(this, kit, balance, btn, n = 6, onDone = {
            paying = false
            if (closing) return@fly
            SoundFx.play("success", rate = 1.4f, vol = 0.55f); Haptics.success()
            Stage.demoRequests.set(demo)
            render(u)
            cards[key]?.let { PayFx.flash(it, dpf(24f)) }
            nowViews[key]?.let { Anim.popIn(it, 0, 0.6f, 360) }
            nextViews[key]?.let { Anim.slideIn(it, 60, dpf(30f), 320) }
        })
        balanceCount = Anim.countTo(kit.labelOf(balance), before, Progress.coins, ms)
    }

    private fun broke() {
        SoundFx.play("tap", rate = 0.6f); Haptics.tick()
        Anim.shake(balance, dpf(8f))
    }

    override fun onBack() {
        close()
    }
}
