package cube.run.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import cube.run.GameActivity
import cube.run.R
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.data.Settings
import cube.run.data.Progress
import cube.run.data.Achievements
import cube.run.ui.Anim.move

/**
 * Everything drawn over the 3D surface: the [MainMenu] before a run; the
 * in-run HUD (score, this run's coin haul, boxes, bubble stock, the pause
 * chip, the BOOST button); and the pages it hosts — [ShopView],
 * [WardrobeView], [SectionsView], the [PauseSheet] and the [RunOverFlow].
 * Must only be touched from the UI thread (GameHostSession marshals for you).
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class Hud(private val activity: Activity, openingEntrance: Boolean = false) : FrameLayout(activity) {

    /** Animated overlays can grow into any part of the game window between layouts. */
    override fun gatherTransparentRegion(region: android.graphics.Region?): Boolean {
        // SurfaceView's transparent-region optimization otherwise punches holes
        // using the pill's small entrance bounds and clips it as it grows. Keep
        // the HUD in normal alpha composition for the entire animation.
        if (region != null) {
            val location = IntArray(2)
            getLocationInWindow(location)
            region.op(location[0], location[1], location[0] + width, location[1] + height, android.graphics.Region.Op.DIFFERENCE)
        }
        return false
    }

    companion object {
        /** Intent extra: begin the run on the first frame (RESTART). */
        const val EXTRA_AUTOSTART = "autostart"
        const val EXTRA_IDLE_BOT = "idle_bot"
    }

    private val kit = UiKit(activity)
    private fun dp(v: Float) = kit.dp(v)
    private fun dpf(v: Float) = kit.dpf(v)

    // ---- in-run HUD
    private val scoreText = kit.stageText("0", 60f, stroke = 7f)
    private val haul = kit.iconPill(CoinIcon(), "0", Theme.INK, 18f, Theme.WHITE)
    private val boxes = kit.iconPill(BoxIcon(), "", Theme.INK, 18f, Theme.LAVENDER).apply { visibility = GONE }
    private val bubbles = kit.iconPill(BubbleIcon(), "", Theme.INK, 18f, Theme.lighten(Theme.CYAN, 0.55f)).apply { visibility = GONE }
    private val pauseChip = kit.chip(R.drawable.ic_pause, Theme.WHITE, Theme.INK, activity.getString(R.string.cd_pause)) { pause() }.apply { visibility = GONE }
    private var boost: BoostArrows? = null
    private val bonusVisited = ArrayList<Int>()
    private val topBox = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false; clipToPadding = false
        visibility = GONE
        addView(scoreText)
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false; clipToPadding = false
            addView(haul)
            addView(boxes, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8f) })
            addView(bubbles, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8f) })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = -dp(4f) })
    }

    private var runCoins = 0
    private var score = 0
    private var best = 0
    private var world = ""
    private var runStarted = false
    private var page: Page? = null
    private var preparedShop: ShopView? = null
    private var opening = openingEntrance
    private val prepareShop = Runnable {
        if (isAttachedToWindow && !pageOpen() && width > 0 && height > 0 && preparedShop?.isCurrent() != true) {
            preparedShop = newShop().also { shop ->
                rootWindowInsets?.let { shop.dispatchApplyWindowInsets(it) }
                shop.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
                shop.layout(0, 0, width, height)
            }
        }
    }
    private var languageSheet: LanguageSheet? = null
    private var pauseSheet: PauseSheet? = null
    private var runOver: RunOverFlow? = null
    private var shopBox: RunOverFlow? = null
    private var giftReturnCover: View? = null
    @Volatile private var giftReturnGeneration = 0
    private var voidPurchase: VoidPurchaseView? = null
    private val achievementToast = AchievementToast(activity, kit)
    private val jackpotToast = JackpotToast(activity, kit)
    private val pollAchievements = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            if (runStarted && runOver == null && pauseSheet == null) {
                achievementToast.enqueue(Achievements.drainUnlocks())
            }
            postDelayed(this, 500)
        }
    }
    private val menu: MainMenu = MainMenu(activity, kit, { openShop() }, { openWardrobe() }, { openSections() }, { menu.pulseBank() },
        openAchievements = { openAchievements() }, openLanguages = { openLanguages() }, openingEntrance = openingEntrance)

    /** One launch clock owns the fade. Controls are laid out at their final positions from frame one. */
    fun setOpeningProgress(amount: Float) {
        alpha = amount
        if (amount >= 1f) {
            menu.finishOpeningEntrance()
            if (opening) { opening = false; scheduleShopPreparation() }
        }
    }
    init {
        isClickable = false
        isFocusable = false
        clipChildren = false; clipToPadding = false
        addView(menu, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(topBox, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(44f) })
        addView(pauseChip, LayoutParams(dp(48f), dp(52f)).apply { gravity = Gravity.TOP or Gravity.END; topMargin = dp(48f); marginEnd = dp(14f) })
        addView(achievementToast, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM; bottomMargin = dp(32f); leftMargin = dp(22f); rightMargin = dp(22f)
        })
        addView(jackpotToast, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP; leftMargin = dp(14f); rightMargin = dp(14f)
        })
        topBox.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionJackpot() }
        setBubbles(Progress.bubbles)
        setOnApplyWindowInsetsListener { _, insets ->
            val (l, t, r, b) = insetsOf(insets)
            val endInset = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) l else r
            (topBox.layoutParams as LayoutParams).topMargin = maxOf(dp(44f), t + dp(6f))
            (pauseChip.layoutParams as LayoutParams).apply { topMargin = maxOf(dp(48f), t + dp(10f)); marginEnd = dp(14f) + endInset }
            topBox.requestLayout(); pauseChip.requestLayout()
            (achievementToast.layoutParams as LayoutParams).bottomMargin = dp(32f) + b
            insets
        }
    }

    // ------------------------------------------------------------- pages

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!opening) scheduleShopPreparation()
        if (runStarted) postDelayed(pollAchievements, 500)
    }

    fun settleLanguageTransition() {
        menu.settleLanguageTransition()
        languageSheet?.settleEntrance()
    }

    fun showLanguagesAfterChange() {
        openLanguages()
        settleLanguageTransition()
    }

    fun resumeLanguageIdle() = menu.resumeLanguageIdle()

    override fun onDetachedFromWindow() {
        removeCallbacks(prepareShop)
        removeCallbacks(pollAchievements)
        preparedShop = null
        voidPurchase = null
        giftReturnGeneration++
        giftReturnCover?.let { cover ->
            cover.animate().cancel()
            removeView(cover)
            shopBox?.let { removeView(it) }
            shopBox = null
            // A reattached HUD must not retain a detached curtain or a locked gift
            // reference. Complete the already-paid presentation while offscreen.
            (page as? ShopView)?.let { shop ->
                shop.refreshAfterBox()
                shop.visibility = VISIBLE
                menu.visibility = VISIBLE
                menu.refresh()
                Stage.openRequests.set(0)
                Stage.skipBoxRequests.set(0)
                Stage.shopProgress = 1f
                Stage.mode = Stage.SHOP
            }
        }
        giftReturnCover = null
        super.onDetachedFromWindow()
    }

    private fun scheduleShopPreparation() {
        removeCallbacks(prepareShop)
        postDelayed(prepareShop, 900)
    }

    /** System Back only navigates out of the two stores. */
    fun navigateBack() {
        languageSheet?.let { it.dismiss(); return }
        if (runStarted || pauseSheet != null || runOver != null) return
        if (shopBox != null || voidPurchase != null) return // keep purchased presentations intact
        val current = page
        if (current is ShopView || current is WardrobeView || current is AchievementsView) current.navigateBack()
    }

    private fun pageOpen() = page != null || languageSheet != null || runStarted

    private fun open(p: Page) {
        Stage.homeScreen = false
        page = p
        menu.setShown(false)
        addView(p, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun closed() {
        Stage.homeScreen = true
        page = null
        menu.show()
        setBubbles(Progress.bubbles)
        scheduleShopPreparation()
    }

    private fun newShop() = ShopView(activity, kit, menu.shopBalance, menu::setShopProgress,
        onOpenMysteryBox = ::openPurchasedBox, onVoidPurchase = ::showVoidPurchase,
        onProgressReset = ::refreshAfterProgressReset) {
        page = null
        menu.finishShop()
        Stage.homeScreen = true
        setBubbles(Progress.bubbles)
        scheduleShopPreparation()
    }

    /** Reset the visible caches too, so closing the developer shop shows a fresh game. */
    private fun refreshAfterProgressReset() {
        removeCallbacks(prepareShop)
        preparedShop = null
        achievementToast.reset()
        jackpotToast.reset()
        bonusVisited.clear()
        score = 0; scoreText.text = "0"
        runCoins = 0; kit.labelOf(haul).text = "0"
        kit.labelOf(boxes).text = "×0"; boxes.visibility = GONE
        setBest(0)
        setBubbleCooldown(0)
        setBubbles(Progress.bubbles)
        menu.refresh()
        Anim.repaint(this)
    }

    private fun openLanguages() {
        if (pageOpen()) return
        Stage.homeScreen = false
        removeCallbacks(prepareShop)
        val sheet = LanguageSheet(activity, kit, { code ->
            (activity as GameActivity).changeLanguage(code)
        }, {
            languageSheet = null
            Stage.homeScreen = true
            scheduleShopPreparation()
        })
        languageSheet = sheet
        addView(sheet, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun openShop() {
        if (pageOpen()) return
        Stage.homeScreen = false
        removeCallbacks(prepareShop)
        val shop = preparedShop?.takeIf { it.isCurrent() } ?: newShop()
        preparedShop = null
        menu.beginShop()
        page = shop
        addView(shop, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        menu.bringToFront() // corner controls return above the sheet instead of flashing out from beneath it
    }
    private fun openWardrobe() { if (!pageOpen()) open(WardrobeView(activity, kit) { closed() }) }
    private fun openSections() { if (!pageOpen()) open(SectionsView(activity, kit) { closed() }) }
    private fun openAchievements() {
        if (!pageOpen() && Progress.achievementsUnlocked) open(AchievementsView(activity, kit) { closed() })
    }

    private fun showVoidPurchase(onCovered: () -> Unit, onFinished: () -> Unit) {
        if (voidPurchase != null) return
        val shop = page as? ShopView
        val oldShopAccessibility = shop?.importantForAccessibility
        val oldMenuAccessibility = menu.importantForAccessibility
        shop?.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        menu.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        lateinit var fx: VoidPurchaseView
        // The clue stays in the dialogue; discovered cosmetics are found in the wardrobe.
        fx = VoidPurchaseView(activity, Progress.voidLine) {
            // Rebuild and lay out the evolving card behind an opaque frame. The first
            // visible shop frame is already settled at the same end of the list.
            onCovered()
            viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    viewTreeObserver.removeOnPreDrawListener(this)
                    if (voidPurchase === fx) {
                        val item = shop?.darknessFocus()
                        // A tap can leave the price flush with the viewport edge. Settle its
                        // full face and lower margin while the opaque scene still covers it.
                        item?.findViewWithTag<android.view.View>("void_price_button")?.let { price ->
                            price.requestRectangleOnScreen(android.graphics.Rect(0, -dp(8f),
                                price.width, price.height + dp(16f)), true)
                        }
                        val hostPosition = IntArray(2); val itemPosition = IntArray(2)
                        getLocationInWindow(hostPosition)
                        item?.getLocationInWindow(itemPosition)
                        val x = item?.let { itemPosition[0] - hostPosition[0] + it.width / 2f } ?: width / 2f
                        val y = item?.let { itemPosition[1] - hostPosition[1] + it.height / 2f } ?: height * .7f
                        fx.returnTo(x, y) {
                            voidPurchase = null
                            removeView(fx)
                            shop?.importantForAccessibility = oldShopAccessibility ?: IMPORTANT_FOR_ACCESSIBILITY_AUTO
                            menu.importantForAccessibility = oldMenuAccessibility
                            onFinished()
                            item?.let { Anim.pulse(it, 1.018f, 280) }
                            Anim.repaint(this@Hud)
                        }
                    }
                    return true
                }
            })
            requestLayout()
        }
        voidPurchase = fx
        addView(fx, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        fx.play()
    }

    private fun openPurchasedBox(reward: Progress.BoxReward) {
        if (shopBox != null) return
        val shop = page as? ShopView ?: return
        shop.visibility = INVISIBLE
        menu.visibility = INVISIBLE
        Stage.purchasedBoxRewards.add(reward)
        val flow = RunOverFlow(activity, kit, 0, 0, false, 0, 1, "", emptyList(),
            onRestart = {}, onMenu = { returnFromPurchasedBox(shop) }, boxesOnly = true)
        shopBox = flow
        addView(flow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Keep the gift visible until an opaque curtain covers the actual GL camera handoff. */
    private fun returnFromPurchasedBox(shop: ShopView) {
        if (giftReturnCover != null) return
        val flow = shopBox ?: return
        val generation = ++giftReturnGeneration
        val cover = View(activity).apply {
            tag = "gift_return_cover"
            setBackgroundColor(0xff100a20.toInt())
            alpha = 0f
            isClickable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        giftReturnCover = cover
        addView(cover, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        cover.move().alpha(1f).setDuration(180).setInterpolator(Anim.ease).withEndAction {
            if (giftReturnCover !== cover || generation != giftReturnGeneration) return@withEndAction
            removeView(flow)
            shop.refreshAfterBox()
            shop.visibility = VISIBLE
            menu.visibility = VISIBLE
            menu.refresh()
            Stage.openRequests.set(0)
            Stage.skipBoxRequests.set(0)
            val app = com.badlogic.gdx.Gdx.app
            app.postRunnable {
                if (generation != giftReturnGeneration) return@postRunnable
                Stage.shopProgress = 1f
                Stage.mode = Stage.SHOP
                // Runnables execute before rendering. Waiting for exit, then one more
                // frame, ensures the restored showroom has actually been drawn. A
                // backgrounded app simply retains its opaque curtain until GL resumes.
                app.postRunnable(object : Runnable {
                    override fun run() {
                        if (generation != giftReturnGeneration) return
                        if (Stage.giftShowing) { app.postRunnable(this); return }
                        app.postRunnable {
                            if (generation == giftReturnGeneration) post {
                                if (giftReturnCover !== cover || !isAttachedToWindow) return@post
                                viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                                    override fun onPreDraw(): Boolean {
                                        viewTreeObserver.removeOnPreDrawListener(this)
                                        if (giftReturnCover === cover) {
                                            cover.move().alpha(0f).setDuration(280).setInterpolator(Anim.ease).withEndAction {
                                                if (giftReturnCover === cover) {
                                                    removeView(cover)
                                                    giftReturnCover = null
                                                    shopBox = null
                                                    Anim.repaint(this@Hud)
                                                }
                                            }.start()
                                        }
                                        return true
                                    }
                                })
                                requestLayout()
                                invalidate()
                            }
                        }
                    }
                })
            }
        }.start()
    }

    // ------------------------------------------------------------- HUD values

    fun setBest(best: Int) {
        this.best = best
        menu.setBest(best)
    }

    fun setWorld(name: String) { world = name }

    /** Entered a bonus world (or left one: -1). Remembered for the results. */
    fun setBonus(id: Int) { if (id >= 0 && id !in bonusVisited) bonusVisited.add(id) }

    /** Coins collected this run: the counter pops on each pickup. */
    fun setRunCoins(v: Int) {
        runCoins = v
        kit.labelOf(haul).text = number(v)
        Anim.pulse(haul, 1.18f, 160)
    }

    /** Coins are already in the run haul; celebrate without changing the saved bank. */
    fun showJackpot(amount: Int) {
        if (!runStarted || runOver != null || amount <= 0) return
        positionJackpot()
        jackpotToast.show(amount)
    }

    private fun positionJackpot() {
        val params = jackpotToast.layoutParams as? LayoutParams ?: return
        val top = topBox.bottom + dp(10f)
        val right = if ((0 until childCount).any { getChildAt(it) is BoostArrows }) dp(104f) else dp(14f)
        if (params.topMargin != top || params.rightMargin != right) {
            params.topMargin = top
            params.rightMargin = right
            jackpotToast.layoutParams = params
        }
    }

    fun setBoxes(n: Int) {
        kit.labelOf(boxes).text = "×$n"
        boxes.visibility = if (n > 0) VISIBLE else GONE
        Anim.pulse(boxes, 1.4f)
    }

    private var bubbleStock = 0
    private var bubbleCooldown = 0

    fun setBubbleCooldown(seconds: Int) {
        bubbleCooldown = seconds
        refreshBubbleLabel()
    }

    private fun refreshBubbleLabel() {
        kit.labelOf(bubbles).text = if (bubbleCooldown > 0) kit.ctx.getString(R.string.text_seconds, bubbleCooldown.toString()) else "×$bubbleStock"
        bubbles.alpha = if (bubbleCooldown > 0) .65f else 1f
        bubbles.visibility = if ((bubbleStock > 0 || bubbleCooldown > 0) && runStarted) VISIBLE else GONE
    }

    fun setBubbles(n: Int) {
        bubbleStock = n
        refreshBubbleLabel()
        menu.refresh()
    }

    fun setScore(v: Int) {
        score = v
        scoreText.text = v.toString()
        Anim.pulse(scoreText, 1.14f, 160)
    }

    /** The boost window opened or closed (GL → UI); [taps] used so far. */
    fun setBoost(open: Boolean, taps: Int, max: Int) {
        if (open) {
            val b = boost ?: BoostArrows(activity, ::dpf).also { b ->
                b.max = max
                b.setOnClickListener {
                    if (b.taps >= b.max) return@setOnClickListener
                    b.taps += 1
                    Stage.boostRequests.incrementAndGet()
                    SoundFx.play("tap", rate = 1.1f + b.taps * 0.1f); Haptics.click()
                }
                addView(b, LayoutParams(dp(84f), dp(160f)).apply { gravity = Gravity.TOP or Gravity.END; topMargin = dp(116f); marginEnd = dp(10f) })
                boost = b
                Anim.popIn(b, 250, 0.4f, 420)
            }
            b.taps = taps
        } else {
            val b = boost ?: return
            boost = null
            b.taps = taps
            b.move().translationX(dpf(if (layoutDirection == View.LAYOUT_DIRECTION_RTL) -120f else 120f)).alpha(0f).setDuration(260).setInterpolator(Anim.ease).withEndAction {
                removeView(b)
                positionJackpot()
            }.start()
        }
        positionJackpot()
    }

    /** A run has begun: the menu drops away, the HUD and the pause chip pop in. */
    fun hideOptions() {
        Stage.homeScreen = false
        runStarted = true
        removeCallbacks(pollAchievements)
        postDelayed(pollAchievements, 500)
        // Purchases and result-screen unlocks belong on the achievement page, not the next run.
        Achievements.drainUnlocks()
        achievementToast.reset()
        achievementToast.setRunActive(true)
        jackpotToast.reset()
        jackpotToast.setRunActive(true)
        page?.let { removeView(it); page = null }
        menu.hide()
        setBubbles(bubbleStock)
        topBox.visibility = VISIBLE
        Anim.popIn(topBox, 120, 0.6f)
        pauseChip.visibility = VISIBLE
        Anim.popIn(pauseChip, 200, 0.5f)
    }

    // ------------------------------------------------------------- pause

    /** Freeze the run under the pause card. No-op unless a run is live. */
    fun pause(animate: Boolean = true) {
        if (!runStarted || runOver != null || pauseSheet != null) return
        Stage.paused = true
        achievementToast.setRunActive(false)
        jackpotToast.setRunActive(false)
        pauseChip.visibility = INVISIBLE
        val sheet = PauseSheet(activity, kit,
            onResume = {
                pauseSheet = null
                Stage.paused = false
                achievementToast.setRunActive(true)
                jackpotToast.setRunActive(true)
                pauseChip.visibility = VISIBLE
                Anim.popIn(pauseChip, 0, 0.6f)
            },
            onRestart = { relaunch(autoStart = true) },
            onMenu = { relaunch(autoStart = false) },
        )
        pauseSheet = sheet
        addView(sheet, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        if (!animate) sheet.settleEntrance()
    }

    /** The activity left the foreground mid-run (home, a call): pause so nothing is lost. */
    fun autoPause() { pause(animate = false); pauseSheet?.settleEntrance() }

    // ------------------------------------------------------------- run over

    /**
     * RESTART / MENU = finish + relaunch (NOT recreate): libGDX only disposes GL
     * resources when the activity is truly finishing, so recreate() would leak
     * native meshes. The relaunch stays INSIDE the current task: a fresh intent
     * started BEFORE finish() so the task never empties; the theme's animations
     * crossfade the fresh screen over the old one. [autoStart] makes the new
     * run begin at once.
     */
    private fun relaunch(autoStart: Boolean, idleBot: Boolean = false) {
        SoundFx.play("whoosh", rate = 0.8f)
        activity.startActivity(Intent(activity, activity.javaClass).putExtra(EXTRA_AUTOSTART, autoStart).putExtra(EXTRA_IDLE_BOT, idleBot))
        activity.finish()
    }

    fun showRunOver(score: Int, best: Int, isNewBest: Boolean, coins: Int, boxes: Int, shards: IntArray = IntArray(3)) {
        achievementToast.setRunActive(false)
        jackpotToast.reset()
        if (Stage.botPlaying && Settings.devMode) {
            relaunch(autoStart = true, idleBot = true)
            return
        }
        if (runOver != null) return
        topBox.move().alpha(0f).setDuration(200).withEndAction { topBox.visibility = GONE }.start()
        pauseChip.visibility = GONE
        setBoost(false, 0, 5)
        val flow = RunOverFlow(activity, kit, score, best, isNewBest, coins, boxes, world, bonusVisited,
            shards = shards,
            onRestart = { relaunch(autoStart = true) },
            onMenu = { relaunch(autoStart = false) },
        )
        runOver = flow
        addView(flow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** The game's 3D gift stage opened a box (GL → UI via the session). */
    fun onBoxOpened(kind: Int, amount: Int, cat: Int, id: Int) {
        (shopBox ?: runOver)?.onBoxOpened(kind, amount, cat, id)
    }
}
