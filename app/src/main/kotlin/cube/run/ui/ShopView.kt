package cube.run.ui

import cube.run.R

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.ViewTreeObserver
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
 * The shop: a showroom strip up top where the engine shows your cube (with a sunburst behind
 * it), and a flat candy sheet below it holding the cards, built like the achievement page's
 * (see [ShopCards]). The cards scroll inside the sheet, so nothing ever covers the cube. The
 * mystery box first, then the stock you keep (bubbles, second winds), the power-ups whose
 * duration you level up, the perks, the trophy room and, late in the game, the void. Every
 * purchase goes through [Progress] and plays out where you tapped and on the cube (see
 * [Stage.demoRequests]): the bubble goes up around it, coins fly into it, it lifts on a jet…
 * A mystery box drops in beside the cube and opens right there (see [ShopGiftReveal]).
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class ShopView(
    activity: Activity,
    kit: UiKit,
    private val balance: LinearLayout,
    private val onProgress: (Float) -> Unit,
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
    private var showroomDp = 150f
    private var progress = 0f
    private var navigation: ValueAnimator? = null
    private var balanceCount: ValueAnimator? = null
    private val shopCards = ShopCards(activity, kit)
    private val cards = shopCards.cards
    private var paying = false
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

    private val showroom: FrameLayout = FrameLayout(activity).apply {
        contentDescription = kit.ctx.getString(R.string.text_shop_cube)
        isClickable = true
        clipChildren = false; clipToPadding = false
        setOnClickListener { if (gift.showing) skipGift() else playWithCube(Stage.SHOP_TAP) }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cubeGesture = !closing && progress == 1f && !gift.showing &&
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
                    if (!cubeSwiped && (cubeGesture || gift.showing)) view.performClick()
                    cubeGesture = false
                }
                MotionEvent.ACTION_CANCEL -> cubeGesture = false
            }
            true
        }
    }

    /** What a mystery box bought here gave: the pill in the showroom and the coins it pays out. */
    private val gift: ShopGiftReveal = ShopGiftReveal(this, kit, balance, showroom)

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

    private fun demoOf(u: Progress.Upgrade): Int = when (u) {
        Progress.BUBBLE -> Stage.DEMO_BUBBLE
        Progress.MAGNET -> Stage.DEMO_MAGNET
        Progress.MULT -> Stage.DEMO_MULT
        Progress.JET -> Stage.DEMO_JET
        Progress.FASTERSTART -> 0
        Progress.FASTBUBBLES -> Stage.DEMO_FASTBUBBLES
        Progress.COINVALUE -> Stage.DEMO_COINS
        Progress.PORTALS -> Stage.DEMO_PORTAL
        else -> Stage.DEMO_BOX
    }

    init {
        reserveHeaderFor(balance)
        content.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            // clipChildren stays ON here: it is what clips the sheet's scrolled cards to the sheet
            addView(showroom, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(showroomDp)))
            addView(ScrollView(activity).apply { // the sheet the cards live in
                isVerticalScrollBarEnabled = false
                clipToPadding = false
                // Cards slip away under the showroom softly instead of being sliced on a hard line.
                isVerticalFadingEdgeEnabled = true
                setFadingEdgeLength(dp(18f))
                background = GradientDrawable().apply {
                    cornerRadii = floatArrayOf(dpf(30f), dpf(30f), dpf(30f), dpf(30f), 0f, 0f, 0f, 0f)
                    setColor(ShopCards.BACKDROP)
                }
                addView(list)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        render()
    }

    override fun onCompactChanged(compact: Boolean) {
        showroomDp = if (compact) 0f else 150f
        showroom.visibility = if (compact) View.GONE else View.VISIBLE
        showroom.layoutParams.height = dp(showroomDp)
        list.setPadding(dp(16f), dp(if (compact) 8f else 18f), dp(16f), dp(28f))
        setBackgroundColor(if (compact) ShopCards.BACKDROP else android.graphics.Color.TRANSPARENT)
    }

    /** Rebuild the whole list from [Progress] (cheap; done after every purchase). */
    private fun render(popped: Progress.Upgrade? = null) {
        val sheet = list.parent as? ScrollView
        val keepBottom = sheet != null && sheet.height > 0 && sheet.scrollY + sheet.height >= list.height - dp(12f)
        kit.labelOf(balance).text = Progress.coins.toString()
        list.removeAllViews()
        shopCards.clear()
        fun wide() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        fun gap(top: Float) = wide().apply { topMargin = dp(top) }
        fun section(label: String) =
            list.addView(shopCards.heading(label), wide().apply { topMargin = dp(24f); bottomMargin = dp(10f) })
        list.addView(mysteryCard(), wide())
        section(kit.ctx.getString(R.string.text_consumables))
        list.addView(bubbleCard(), wide())
        list.addView(reviveCard(), gap(12f))
        section(kit.ctx.getString(R.string.text_power_ups))
        for ((i, u) in Progress.upgrades.withIndex()) list.addView(upgradeCard(u), if (i == 0) wide() else gap(12f))
        section(kit.ctx.getString(R.string.text_perks))
        for ((i, u) in Progress.perks.withIndex()) list.addView(perkCard(u), if (i == 0) wide() else gap(12f))
        list.addView(achievementCard(), gap(12f))
        if (Progress.voidAvailable) {
            // Offerings stay here; discovered cosmetics are purchased in the wardrobe.
            val darkness = voidCard()
            cards["darkness"] = darkness
            list.addView(darkness, gap(28f))
        }
        if (Settings.devMode) list.addView(kit.button(activity.getString(R.string.shop_reset_progress), Theme.BERRY) { confirmProgressReset() }.apply {
            tag = "reset_progress"
            contentDescription = activity.getString(R.string.shop_reset_progress)
            setTextColor(Theme.WHITE)
            textSize = 16f
        }, gap(28f))
        popped?.let { u -> // the segment just bought swells and settles
            val bar = shopCards.bars[u.key] ?: return@let
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
        if (compactLayout) {
            Stage.mode = Stage.SHOP
            alpha = 1f
            place(1f)
            return
        }
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

    internal fun warmNavigation() = preparePanelLayer()

    override fun animateExit(onFinished: () -> Unit) {
        cubeGesture = false
        Stage.shopPlayRequests.set(0)
        endGift()
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
        endGift()
        navigation?.cancel()
        balanceCount?.cancel()
        if (Stage.mode == Stage.SHOP) Stage.mode = Stage.NONE
        super.onDetachedFromWindow()
    }

    private fun upgradeCard(u: Progress.Upgrade): View {
        val lvl = Progress.level(u)
        val now = u.duration(lvl)
        val maxed = Progress.nextPrice(u) == null
        return shopCards.level(u.key, kit.ctx.gameText(u.name), u.max, lvl, kit.ctx.getString(R.string.text_seconds, fmt(now)),
            if (maxed) null else kit.ctx.getString(R.string.text_seconds, fmt(now + u.step)),
            offer(Progress.nextPrice(u), u.key, u, demoOf(u)) { Progress.buyUpgrade(u) })
    }

    private fun perkValue(u: Progress.Upgrade, lvl: Int): String = when (u) {
        Progress.FASTERSTART -> kit.ctx.resources.getQuantityString(R.plurals.count_taps, 5 + lvl, 5 + lvl)
        Progress.FASTBUBBLES -> kit.ctx.getString(R.string.text_seconds, fmt(u.duration(lvl)))
        Progress.COINVALUE -> "×${fmt(u.duration(lvl))}"
        Progress.PORTALS -> kit.ctx.resources.getQuantityString(R.plurals.count_rows, 110 - 14 * lvl, 110 - 14 * lvl)
        else -> "×${fmt(u.duration(lvl))}"
    }

    private fun perkCard(u: Progress.Upgrade): View {
        val lvl = Progress.level(u)
        val maxed = Progress.nextPrice(u) == null
        return shopCards.level(u.key, kit.ctx.gameText(u.name), u.max, lvl, perkValue(u, lvl), if (maxed) null else perkValue(u, lvl + 1),
            offer(Progress.nextPrice(u), u.key, u, demoOf(u)) { Progress.buyUpgrade(u) })
    }

    private fun bubbleCard(): View = shopCards.stock(ShopIcon.BUBBLES, kit.ctx.getString(R.string.text_bubble_shield), Progress.bubbles, 5, { ShopIcon(ShopIcon.RACK_BUBBLE) },
        offer(Progress.BUBBLE_PRICE, ShopIcon.BUBBLES, null, Stage.DEMO_BUBBLE) { Progress.buyBubble() })

    /** Second wind: a stock of revives. */
    private fun reviveCard(): View = shopCards.stock(ShopIcon.REVIVES, kit.ctx.getString(R.string.text_second_wind), Progress.revives, 3, { ShopIcon(ShopIcon.RACK_HEART) },
        offer(if (Progress.revives >= Progress.MAX_REVIVES) null else Progress.REVIVE_PRICE, ShopIcon.REVIVES, null, Stage.DEMO_REVIVE) { Progress.buyRevive() })

    private fun mysteryCard(): View = shopCards.mystery(activity.getString(R.string.shop_mystery_box), activity.getString(R.string.shop_mystery_box_detail),
        priceButton(Progress.mysteryBoxPrice, ShopIcon.MYSTERY) {
            pendingBox = Progress.buyMysteryBox()
            pendingBox != null
        }!!)

    private fun achievementCard(): View = shopCards.achievements(activity.getString(R.string.shop_achievements_name), activity.getString(R.string.shop_achievements_detail),
        if (Progress.achievementsUnlocked) CandyButton(activity, Theme.PLAY, activity.getString(R.string.shop_unlocked), 16f, dpf(4f), dpf(14f)).apply {
            // A read-only candy slab must not retain CandyButton's ACTION_DOWN press listener:
            // non-clickable views do not necessarily receive the matching release event.
            setOnTouchListener(null)
            isClickable = false; isLongClickable = false; isFocusable = false
            tag = "achievements_unlocked_status"
            contentDescription = activity.getString(R.string.shop_achievements_unlocked)
            gravity = Gravity.CENTER
            setPadding(dp(16f), 0, dp(16f), 0)
            val check = UnlockedCheckIcon().apply { setBounds(0, 0, dp(20f), dp(20f)) }
            text = SpannableStringBuilder("\uFFFC  ${activity.getString(R.string.shop_unlocked)}").apply {
                setSpan(CenteredImageSpan(check), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else priceButton(Progress.ACHIEVEMENTS_PRICE, ShopIcon.ACHIEVEMENTS) { Progress.buyAchievements() }!!)

    // The card only ever says "???", and only until the first offering: the void speaks in its show.
    private fun voidCard(): View = VoidCardView(activity, kit, if (Progress.voidPurchases == 0) "???" else "",
        priceButton(Progress.voidPrice, "void") { Progress.buyVoid() }!!).also { cards["void"] = it }

    /**
     * What a tap on a price does, or null when there is nothing left to buy: [buy] takes the
     * coins, then they fly from the balance into the button that was tapped.
     */
    private fun offer(price: Int?, key: String, u: Progress.Upgrade?, demo: Int, buy: () -> Boolean): ShopCards.Offer? {
        if (price == null) return null
        return ShopCards.Offer(price, price <= Progress.coins) tap@{ btn ->
            if (paying || closing || progress < 1f) return@tap
            val before = Progress.coins
            if (!buy()) { broke(); return@tap }
            pay(btn, before, key, u, demo, if (key == "mystery") before - price else Progress.coins)
        }
    }

    /**
     * A full-width price for the one-off cards (the mystery box, the trophy room, the void):
     * gold when affordable, dimmed when not.
     */
    private fun priceButton(price: Int?, key: String, buy: () -> Boolean): View? {
        val offer = offer(price, key, null, 0, buy) ?: return null
        lateinit var btn: CandyButton
        // Not affordable yet: frosted glass on the dark void and grape mystery cards, pale gold on a white card.
        val (dim, dimWords) = when (key) {
            "void" -> Theme.alpha(Theme.WHITE, 46) to Theme.alpha(Theme.WHITE, 170)
            ShopIcon.MYSTERY -> Theme.alpha(Theme.WHITE, 64) to Theme.alpha(Theme.WHITE, 235)
            else -> ShopCards.PALE_GOLD to Theme.alpha(Theme.INK, 150)
        }
        btn = kit.button(kit.coins(offer.price, 19f), if (offer.can) Theme.GOLD else dim) { offer.onTap(btn) }
        if (!offer.can) btn.setTextColor(dimWords)
        return if (key == "void") FittedVoidPrice(activity, kit, btn, offer.price) else btn
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
        if (compactLayout && key == "void") { // the black hole needs the showroom; short panes just settle the purchase
            balanceCount?.cancel()
            kit.labelOf(balance).text = displayedBalance.toString()
            paying = false
            render(u)
            SoundFx.play("buy", vol = 0.7f); Haptics.success()
            return
        }
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
        val box = if (key == "mystery") pendingBox.also { pendingBox = null } else null
        // The box drops into the showroom while the coins are still flying.
        if (box != null && !compactLayout) {
            Stage.shopGift = box
            Stage.shopGiftRequests.incrementAndGet()
        }
        val opened = Stage.shopGiftOpened.get()
        val ms = PayFx.fly(this, kit, balance, btn, n = 6, onDone = {
            if (closing) return@fly
            if (box != null) { awaitGift(box, opened); return@fly }
            paying = false
            SoundFx.play("buy", vol = 0.7f); Haptics.success()
            if (!compactLayout) Stage.demoRequests.set(demo) // short panes have no showroom to play it in
            render(u)
            cards[key]?.let { PayFx.flash(it, dpf(ShopCards.RADIUS), lip = kit.CARD_LIP) }
            shopCards.values[key]?.let { Anim.popIn(it, 0, 0.6f, 360) }
        })
        // A box's reward is persisted already, but its value stays a surprise until the box opens.
        balanceCount = Anim.countTo(kit.labelOf(balance), before, displayedBalance, ms)
    }

    // ---------------------------------------------------------------- the mystery box in the showroom

    /** Wait for the showroom box to burst, then say what came out. */
    private fun awaitGift(box: Progress.BoxReward, opened: Int) {
        if (compactLayout) revealGift(box) else gift.await(opened) { revealGift(box) }
    }

    /** The box is open: the page is shoppable again, and the pill says what came out. */
    private fun revealGift(box: Progress.BoxReward) {
        paying = false
        if (closing || !isAttachedToWindow) return
        val shown = kit.labelOf(balance).text.filter { it.isDigit() }.toString().toIntOrNull() ?: Progress.coins
        render()
        preparedState = currentState()
        kit.labelOf(balance).text = shown.toString()
        if (compactLayout) { SoundFx.play("buy", vol = 0.7f); Haptics.success() }
        balanceCount?.cancel()
        balanceCount = gift.show(box, compactLayout, content.top, shown)
    }

    private fun skipGift() = gift.skip()

    private fun endGift() = gift.end()

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
            endGift()
            Stage.clearPreview()
            Stage.shopPlayRequests.set(0)
            Stage.demoRequests.set(0)
            Stage.previewKicks.set(0)
            Stage.previewBuys.set(0)
            Stage.shopGift = null
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
