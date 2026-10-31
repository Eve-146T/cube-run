package cube.run.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.os.SystemClock
import android.graphics.Canvas
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import cube.run.R
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.data.Achievements
import cube.run.data.Progress
import kotlin.math.sin

/**
 * The trophy room: white cards on a flat candy grape. The medal case up top
 * counts everything collected; below it whatever has a reward waiting, then the medal families,
 * the challenges and the finished ones, one card per row.
 *
 * Claiming is collecting: the CLAIM button melts back into the card's bar and the waiting badge
 * is stamped into place where you tapped. It dips, lands in its won colours with a thump and a
 * little wobble, the case above counts it, and a few coins hop out of it into the bank. Every badge the card has waiting is stamped in one go, each a little higher in pitch.
 * The first screenful is built at once and the rest a few cards a frame, so the page opens
 * without a pause.
 */
@SuppressLint("ViewConstructor")
class AchievementsView(activity: Activity, kit: UiKit, private val preparing: Boolean = false,
    private val canPrepareOffscreen: () -> Boolean = { true }, onClose: () -> Unit) :
    Page(activity, kit, activity.getString(R.string.achievements_title), dark = false, onClosed = onClose) {
    private val rows = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14f), dp(4f), dp(14f), dp(28f))
        clipChildren = false; clipToPadding = false
    }
    /** The scrolling sheet: the list, and the claim effects drawn over it (clipped with it). */
    private val sheet = FrameLayout(activity).apply {
        clipChildren = false; clipToPadding = false
        if (!preparing) addView(rows, FrameLayout.LayoutParams(-1, -2))
    }
    private val bank = kit.coinBank(number(Progress.coins))
    private val scroll = object : ScrollView(activity) {
        override fun dispatchDraw(canvas: Canvas) {
            // This is the scrolling viewport, not the tall child: cards must stop below the bank/header.
            val saved = canvas.save()
            canvas.clipRect(0, scrollY, width, scrollY + height)
            super.dispatchDraw(canvas)
            canvas.restoreToCount(saved)
        }

        // While the coins fly the list still scrolls, but every gesture is the list's own: no tap
        // reaches a card or another CLAIM under the finger until the claim has settled.
        override fun onInterceptTouchEvent(event: MotionEvent): Boolean = paying || super.onInterceptTouchEvent(event)
    }.apply {
        isVerticalScrollBarEnabled = false
        // Cards slip away under the title row softly instead of being cut off on a hard line.
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength(dp(18f))
        clipChildren = true; clipToPadding = true
        addView(sheet)
    }
    /** Where the title goes when it cannot share the corner row with the bank (a huge balance, a narrow pane, large text). */
    private val titleRow = FrameLayout(activity).apply { visibility = View.GONE }
    private val cards = AchievementCards(activity, kit, ::claim)
    private val hero = AchievementHero(activity, kit)
    private var paying = false
    private var bankCount: ValueAnimator? = null
    internal var contentReady = false
        private set
    /** Cards not built yet: a few are made each frame after the first screenful. */
    private val later = ArrayList<() -> Unit>()
    private var preparationQueued = false
    private val prepareMore = object : Runnable {
        override fun run() {
            preparationQueued = false
            if (closing || !isAttachedToWindow) return
            // New hidden cards are built in a detached column, so each addition
            // cannot remeasure whichever screen the player is using.
            if (rows.parent != null && visibility != VISIBLE && !cube.run.core.Stage.homeScreen) return
            if (visibility != VISIBLE && !canPrepareOffscreen()) return
            val startedAt = SystemClock.uptimeMillis()
            var built = 0
            while (later.isNotEmpty() && built < PER_FRAME) {
                later.removeAt(0)()
                built++
                if (rows.parent == null && sheet.width > 0) {
                    // Measure each addition while it is offscreen too: attaching an
                    // otherwise unmeasured column would move the stall to menu return.
                    rows.measure(
                        ViewGroup.getChildMeasureSpec(MeasureSpec.makeMeasureSpec(sheet.width, MeasureSpec.EXACTLY),
                            sheet.paddingLeft + sheet.paddingRight, LayoutParams.MATCH_PARENT),
                        ViewGroup.getChildMeasureSpec(MeasureSpec.makeMeasureSpec(scroll.height, MeasureSpec.UNSPECIFIED),
                            sheet.paddingTop + sheet.paddingBottom, LayoutParams.WRAP_CONTENT))
                    rows.layout(0, 0, rows.measuredWidth, rows.measuredHeight)
                }
                if (SystemClock.uptimeMillis() - startedAt >= BUILD_BUDGET_MS) break
            }
            contentReady = later.isEmpty()
            prepareContent()
        }
    }

    internal fun prepareContent() {
        if (contentReady) {
            if (visibility == VISIBLE || cube.run.core.Stage.homeScreen || (preparing && canPrepareOffscreen())) attachRows()
        } else if (!preparationQueued) {
            preparationQueued = true
            postOnAnimation(prepareMore)
        }
    }

    private var paintedRows = 0

    /** Record one card's drawing per frame, rather than the whole collection on menu return. */
    internal fun preparePainting(): Boolean {
        if (rows.parent == null || rows.isLayoutRequested) return false
        if (paintedRows >= rows.childCount) return true
        rows.getChildAt(paintedRows++).let { row ->
            row.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            row.buildLayer()
            row.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        return paintedRows >= rows.childCount
    }

    private fun attachRows() {
        if (rows.parent == null) sheet.addView(rows, FrameLayout.LayoutParams(-1, -2))
    }

    override fun onNavigationShown() { attachRows(); prepareContent() }

    override fun onCompactChanged(compact: Boolean) {
        // Short panes: the medal case gives its height to the first cards (sections keep their counts).
        hero.visibility = if (compact) View.GONE else View.VISIBLE
        for (i in 0 until rows.childCount) {
            val row = rows.getChildAt(i)
            if (row.tag == "achievement_section") row.layoutParams = sectionParams().apply {
                if (compact && i == 1) topMargin = dp(4f) // right under the hidden case
            }
        }
    }

    init {
        // A flat lavender instead of the pastel gradient of the other bright pages: white cards on it were hard to tell apart.
        setBackgroundColor(TrophyRoom.BACKDROP)
        titleView.setTextColor(TrophyRoom.HEADING)
        titleView.maxLines = 1
        titleView.setAutoSizeTextTypeUniformWithConfiguration(12, 26, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        addRight(bank)
        content.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleRow, LinearLayout.LayoutParams(-1, -2).apply { marginStart = dp(16f); marginEnd = dp(16f); bottomMargin = dp(4f) })
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }, FrameLayout.LayoutParams(-1, -1))
        val everything = Achievements.snapshot()
        // A secret stays off the page until it is earned; SECRETS only says how many are left to find.
        val secrets = everything.filter { it.definition.secret }
        val hidden = secrets.filter { it.earnedTiers == 0 }
        val states = everything.filterNot { it in hidden }
        // Rewards waiting first, then whatever is closest to its next medal; finished ones go to the shelf.
        val ready = states.filter { it.claimableTier != null }
            .sortedWith(compareBy<Achievements.Snapshot> { if (it.definition.tiered) 0 else 1 }.thenByDescending { it.claimableTier })
        val open = states.filter { !it.allClaimed && it.claimableTier == null }.sortedByDescending { it.fraction }
        val medals = open.filter { it.definition.tiered }
        val challenges = open.filterNot { it.definition.tiered }
        val done = states.filter { it.allClaimed }
        val allMedals = states.filter { it.definition.tiered }
        val allChallenges = states.filterNot { it.definition.tiered || it.definition.secret }
        rows.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6f) })
        // Everything below the case, in order; each entry makes its view when its turn comes.
        fun list(states: List<Achievements.Snapshot>, first: Int, row: (Achievements.Snapshot, Int) -> View) {
            for ((index, state) in states.withIndex()) later += {
                rows.addView(row(state, first + index), LinearLayout.LayoutParams(-1, -2).apply { if (index > 0) topMargin = dp(if (state.allClaimed) 8f else 12f) })
            }
        }
        if (ready.isNotEmpty()) {
            later += { rows.addView(section(activity.getString(R.string.achievement_section_ready), "${ready.size}", READY_COUNT), sectionParams()) }
            list(ready, 0) { state, i -> cards.card(state, i, animateFill = !preparing) }
        }
        if (medals.isNotEmpty()) {
            later += { rows.addView(section(activity.getString(R.string.achievement_section_medals), "${allMedals.sumOf { it.earnedTiers }} / ${allMedals.sumOf { it.definition.thresholds.size }}"), sectionParams()) }
            list(medals, ready.size) { state, i -> cards.card(state, i, animateFill = !preparing) }
        }
        if (challenges.isNotEmpty()) {
            later += { rows.addView(section(activity.getString(R.string.achievement_section_challenges), "${allChallenges.count { it.earnedTiers > 0 }} / ${allChallenges.size}"), sectionParams()) }
            list(challenges, ready.size + medals.size) { state, i -> cards.card(state, i, animateFill = !preparing) }
        }
        if (hidden.isNotEmpty()) {
            later += { rows.addView(section(activity.getString(R.string.achievement_section_secrets), "${secrets.size - hidden.size} / ${secrets.size}"), sectionParams()) }
            for ((index, _) in hidden.withIndex()) later += {
                rows.addView(cards.secretCard(), LinearLayout.LayoutParams(-1, -2).apply { if (index > 0) topMargin = dp(12f) })
            }
        }
        if (done.isNotEmpty()) {
            later += { rows.addView(section(activity.getString(R.string.achievement_section_done), "${done.size}"), sectionParams()) }
            list(done, 0) { state, _ -> cards.doneChip(state) }
        }
        later += { rows.addView(suggestionCard(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28f) }) }
        // A card costs a few milliseconds to make: build what the first screen shows now, the rest over the next frames.
        repeat(minOf(if (preparing) 0 else FIRST_SCREEN, later.size)) { later.removeAt(0)() }
        contentReady = later.isEmpty()
        prepareContent()
        hero.bind(everything, animate = false)
    }

    /** Build every card still waiting its turn now, for a page measured off screen (a layout review). */
    internal fun buildAll() {
        while (later.isNotEmpty()) later.removeAt(0)()
        contentReady = true
        attachRows()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val room = MeasureSpec.getSize(widthMeasureSpec) - dp(14f + 46f + 12f + 34f) - bank.measuredWidth
        val paint = android.text.TextPaint(titleView.paint).apply {
            textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, MIN_TITLE_SP, resources.displayMetrics)
        }
        val ownRow = paint.measureText(titleView.text.toString()) > room
        if (ownRow == (titleView.parent === titleRow)) return
        (titleView.parent as? ViewGroup)?.removeView(titleView)
        if (ownRow) titleRow.addView(titleView, FrameLayout.LayoutParams(-1, -2))
        else topBar.addView(titleView, 1, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12f) })
        titleRow.visibility = if (ownRow) View.VISIBLE else View.GONE
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** The last card: an invitation to suggest the next achievement. */
    private fun suggestionCard(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(18f), dp(18f), dp(18f), dp(18f) + kit.CARD_LIP)
        background = TrophyRoom.slab(kit, 22f)
        addView(kit.text(activity.getString(R.string.achievement_suggest_title), 21f, Theme.INK, 700))
        addView(kit.text(activity.getString(R.string.achievement_suggest_body), 15f, Theme.INK_SOFT, 500),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6f) })
        addView(kit.button(activity.getString(R.string.achievement_suggest_button), Theme.SKY) {
            activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(SUGGEST_URL)))
        }.apply { tag = "achievement_suggest" },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14f) })
    }

    /** A heading between the groups, with how many are earned. */
    private fun section(label: String, count: String, countTag: String? = null): View = LinearLayout(activity).apply {
        tag = "achievement_section"
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6f), 0, dp(6f), 0)
        addView(kit.text(label, 16f, TrophyRoom.HEADING, 700, Gravity.START).apply { letterSpacing = kit.tracking(.08f) },
            LinearLayout.LayoutParams(0, -2, 1f))
        addView(kit.text(count, 16f, TrophyRoom.HEADING_SOFT, 700).apply { tag = countTag })
    }

    private fun sectionParams() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(if (compactLayout) 8f else 22f); bottomMargin = dp(10f) }

    override fun animateEntrance() {
        super.animateEntrance()
        // The case, then each card, rise in one after the other.
        for (i in 0 until minOf(rows.childCount, 7)) Anim.riseIn(rows.getChildAt(i), 40L + i * 45L, dpf(26f), 320)
    }

    /** Collect everything [state]'s card has waiting, one medal after the other. */
    private fun claim(state: Achievements.Snapshot, button: CandyButton) {
        if (paying || closing) return
        if (state.claimableTier == null) return
        val definition = state.definition
        val card = rows.findViewWithTag<View>("achievement_card_${definition.id}") ?: return
        paying = true
        Pokes.stop(card)
        Haptics.click()
        // The button is spent: it melts back into the bar under it.
        cards.settleClaimed(card, button)
        collect(definition, card, 0)
    }

    /** Stamp the next waiting badge of [definition], coins into the bank; then the one after, or finish. */
    private fun collect(definition: Achievements.Definition, card: View, step: Int) {
        if (closing || !isAttachedToWindow) return
        val tier = Achievements.snapshot(definition).claimableTier ?: return finish(definition)
        val before = Progress.coins
        if (Achievements.claim(definition.id) <= 0) return finish(definition)
        val after = Achievements.snapshot(definition)
        val more = after.claimableTier != null
        val reward = card.findViewWithTag<ImageView>("achievement_reward_${definition.id}")
        // A narrow card folds its badge away: it is then stamped on the tile.
        val at: View = reward?.takeIf { it.isShown } ?: card.findViewWithTag("achievement_icon_${definition.id}") ?: card
        val struck = {
            // The moment the badge lands in its won colours.
            SoundFx.play("place", rate = .8f + .12f * step, vol = .8f)
            if (tier == 3 && definition.tiered) SoundFx.play("perfect", rate = 1f, vol = .45f)
            Haptics.success()
            hero.bind(Achievements.snapshot(), animate = true)
            val coins = PayFx.fly(this, kit, at, bank, n = 3 + tier.coerceIn(0, 3), onDone = { if (!more && isAttachedToWindow) finish(definition) })
            countBank(before, coins)
            if (more) postDelayed({ collect(definition, card, step + 1) }, 560L)
        }
        if (reward == null || reward !== at) struck()
        else stamp(reward, refill = { cards.bindWon(reward, definition, tier) }, struck)
    }

    /** Every badge is in: the card settles into its new state. */
    private fun finish(definition: Achievements.Definition) {
        SoundFx.play("success", rate = 1.2f, vol = .5f)
        val states = Achievements.snapshot()
        hero.bind(states, animate = false)
        rows.findViewWithTag<android.widget.TextView>(READY_COUNT)?.text = "${states.count { it.claimableTier != null }}"
        val held = scroll.scrollY
        val from = rows.findViewWithTag<View>("achievement_card_${definition.id}")?.height ?: 0
        rebuild(definition)?.let { settle(it, from) }
        scroll.scrollTo(0, held)
        paying = false
    }

    /** The rebuilt [card] eases from the [from] height of the card it replaced to its own, so the cards below glide. */
    private fun settle(card: View, from: Int) {
        if (from <= 0 || rows.width == 0) return
        card.measure(MeasureSpec.makeMeasureSpec(rows.width - rows.paddingLeft - rows.paddingRight, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val to = card.measuredHeight
        if (to == from) return
        val params = card.layoutParams
        params.height = from
        ValueAnimator.ofInt(from, to).apply {
            duration = 260; interpolator = Anim.ease
            addUpdateListener { params.height = it.animatedValue as Int; card.requestLayout(); Anim.repaint(card) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { params.height = LinearLayout.LayoutParams.WRAP_CONTENT; card.requestLayout() }
            })
            Anim.cancelOnDetach(card, this)
            start()
        }
    }

    /**
     * The badge is pressed into the card where you tapped: it dips, [refill] swaps in its won
     * state and [struck] plays the landing, then it springs back to size with a little wobble.
     */
    private fun stamp(reward: ImageView, refill: () -> Unit, struck: () -> Unit) {
        val press = .2f
        val spring = android.view.animation.OvershootInterpolator(2.6f)
        var landed = false
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 440
            addUpdateListener {
                val t = it.animatedValue as Float
                if (t < press) {
                    val s = 1f - .14f * Anim.ease.getInterpolation(t / press)
                    reward.scaleX = s; reward.scaleY = s
                } else {
                    if (!landed) { landed = true; refill(); struck() }
                    val k = (t - press) / (1f - press)
                    val s = .86f + .14f * spring.getInterpolation(k)
                    reward.scaleX = s; reward.scaleY = s
                    reward.rotation = 6f * sin(k * 3f * Math.PI.toFloat()) * (1f - k)
                }
                Anim.repaint(reward)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!landed) { landed = true; refill() }
                    reward.scaleX = 1f; reward.scaleY = 1f; reward.rotation = 0f
                }
            })
            Anim.cancelOnDetach(reward, this)
            start()
        }
    }

    private fun countBank(before: Int, ms: Long) {
        bankCount?.cancel()
        bankCount = Anim.countTo(kit.labelOf(bank), before, Progress.coins, ms, ::number)
    }

    /** Swap a card for its current state, in place. */
    private fun rebuild(definition: Achievements.Definition): View? {
        val old = rows.findViewWithTag<View>("achievement_card_${definition.id}") ?: return null
        val parent = old.parent as? ViewGroup ?: return null
        val index = parent.indexOfChild(old)
        val params = old.layoutParams
        parent.removeViewAt(index)
        // Claiming moves no bar: the new card starts filled where the old one was.
        return cards.card(Achievements.snapshot(definition), index, animateFill = false).also { parent.addView(it, index, params) }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(prepareMore); preparationQueued = false
        bankCount?.cancel()
        super.onDetachedFromWindow()
    }

    // Only the list's taps wait for a claim to settle (see [scroll]); leaving the page never does.
    override fun onBack() = close()

    private companion object {
        const val FIRST_SCREEN = 5
        const val PER_FRAME = 3
        const val BUILD_BUDGET_MS = 4L
        const val MIN_TITLE_SP = 16f
        const val READY_COUNT = "achievement_ready_count"
        const val SUGGEST_URL = "https://github.com/Eve-146T/cube-run/issues/new"
    }
}
