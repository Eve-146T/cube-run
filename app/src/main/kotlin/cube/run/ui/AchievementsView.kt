package cube.run.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.os.SystemClock
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
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
import cube.run.ui.Anim.move
import kotlin.math.sin

/**
 * The trophy room, on the bright candy backdrop like the other menus. The medal case up top
 * counts everything collected; below it the medal wall, every medal family's own medal two to a
 * row (the ones with a medal waiting first), then the challenges, one card per row, and the
 * finished challenges.
 *
 * Claiming is collecting: the medal (or a challenge's plaque) winds up and springs off with a
 * thump and a ring of its colour, a copy flies into its slot in the case, and the coins pour out
 * of it into the bank; in its place comes the next medal waiting, or the one just collected.
 * Every medal the card has waiting is handed over in one go, each a little higher in pitch.
 * The first screenful is built at once and the rest a few cards a frame, so the page opens
 * without a pause.
 */
@SuppressLint("ViewConstructor")
class AchievementsView(activity: Activity, kit: UiKit, private val preparing: Boolean = false, onClose: () -> Unit) :
    Page(activity, kit, activity.getString(R.string.achievements_title), dark = false, onClosed = onClose) {
    private val rows = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14f), dp(4f), dp(14f), dp(28f))
        clipChildren = false; clipToPadding = false
    }
    /** The scrolling sheet: the list, and the claim effects drawn over it (clipped with it). */
    private val sheet = FrameLayout(activity).apply {
        clipChildren = false; clipToPadding = false
        addView(rows, FrameLayout.LayoutParams(-1, -2))
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
    }.apply {
        isVerticalScrollBarEnabled = false
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
        titleView.maxLines = 1
        titleView.setAutoSizeTextTypeUniformWithConfiguration(12, 26, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        addRight(bank)
        content.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleRow, LinearLayout.LayoutParams(-1, -2).apply { marginStart = dp(16f); marginEnd = dp(16f); bottomMargin = dp(4f) })
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }, FrameLayout.LayoutParams(-1, -1))
        val states = Achievements.snapshot()
        // Medals waiting first, then whatever is closest to its next medal, the finished ones last.
        val families = states.filter { it.definition.tiered }.sortedWith(
            compareBy<Achievements.Snapshot> { if (it.claimableTier != null) 0 else if (it.allClaimed) 2 else 1 }
                .thenByDescending { it.claimableTier ?: -1 }.thenByDescending { it.fraction })
        val allChallenges = states.filterNot { it.definition.tiered }
        val challenges = allChallenges.filter { it.claimableTier != null } +
            allChallenges.filter { !it.allClaimed && it.claimableTier == null }.sortedByDescending { it.fraction }
        val done = allChallenges.filter { it.allClaimed }
        rows.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6f) })
        // Everything below the case, in order; each entry makes its view when its turn comes.
        if (families.isNotEmpty()) {
            later += { rows.addView(section(activity.getString(R.string.achievement_section_medals), "${families.sumOf { it.earnedTiers }} / ${families.sumOf { it.definition.thresholds.size }}"), sectionParams()) }
            for ((row, pair) in families.chunked(2).withIndex()) later += {
                rows.addView(wallRow(pair, row * 2), LinearLayout.LayoutParams(-1, -2).apply { if (row > 0) topMargin = dp(12f) })
            }
        }
        fun list(states: List<Achievements.Snapshot>, first: Int, row: (Achievements.Snapshot, Int) -> View) {
            for ((index, state) in states.withIndex()) later += {
                rows.addView(row(state, first + index), LinearLayout.LayoutParams(-1, -2).apply { if (index > 0) topMargin = dp(if (state.allClaimed) 8f else 12f) })
            }
        }
        if (challenges.isNotEmpty()) {
            later += { rows.addView(section(activity.getString(R.string.achievement_section_challenges), "${allChallenges.count { it.earnedTiers > 0 }} / ${allChallenges.size}"), sectionParams()) }
            list(challenges, families.size) { state, i -> cards.card(state, i, animateFill = !preparing) }
        }
        if (done.isNotEmpty()) {
            later += { rows.addView(section(activity.getString(R.string.achievement_section_done), "${done.size}"), sectionParams()) }
            list(done, 0) { state, _ -> cards.doneChip(state) }
        }
        later += { rows.addView(suggestionCard(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28f) }) }
        // A card costs a few milliseconds to make: build what the first screen shows now, the rest over the next frames.
        repeat(minOf(if (preparing) 0 else FIRST_SCREEN, later.size)) { later.removeAt(0)() }
        fun more() {
            if (closing || !isAttachedToWindow) return
            // Yield to input and painting when a slower phone spends most of a frame on one card.
            val startedAt = SystemClock.uptimeMillis()
            var built = 0
            while (later.isNotEmpty() && built < PER_FRAME) {
                later.removeAt(0)()
                built++
                if (SystemClock.uptimeMillis() - startedAt >= BUILD_BUDGET_MS) break
            }
            if (later.isNotEmpty()) postOnAnimation(::more)
            else contentReady = true
        }
        if (later.isNotEmpty()) postOnAnimation(::more)
        contentReady = later.isEmpty()
        hero.bind(states, animate = false)
    }

    /** Build every card still waiting its turn now, for a page measured off screen (a layout review). */
    internal fun buildAll() {
        while (later.isNotEmpty()) later.removeAt(0)()
        contentReady = true
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

    /** Two medal families side by side, as tall as the taller one. */
    private fun wallRow(pair: List<Achievements.Snapshot>, first: Int): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = false
        clipChildren = false; clipToPadding = false
        for ((k, state) in pair.withIndex()) addView(cards.medalCard(state, first + k, animateFill = !preparing),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { if (k > 0) marginStart = dp(12f) })
        if (pair.size == 1) addView(View(activity), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = dp(12f) })
    }

    /** The last card: an invitation to suggest the next achievement. */
    private fun suggestionCard(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(18f), dp(18f), dp(18f), dp(18f) + kit.CARD_LIP)
        background = kit.cardDrawable(Theme.CARD, null, 22f)
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
        addView(kit.text(label, 16f, Theme.INK, 700, Gravity.START).apply { letterSpacing = kit.tracking(.08f) },
            LinearLayout.LayoutParams(0, -2, 1f))
        addView(kit.text(count, 16f, Theme.INK_SOFT, 700).apply { tag = countTag })
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
        button.isEnabled = false
        Haptics.click()
        // The button is spent: it sinks into the card.
        button.pivotY = 0f
        button.move().scaleY(0f).alpha(0f).setDuration(170).start()
        collect(definition, card, 0)
    }

    /** Hand over the next waiting medal of [definition]: into the case, coins into the bank; then the one after, or finish. */
    private fun collect(definition: Achievements.Definition, card: View, step: Int) {
        if (closing || !isAttachedToWindow) return
        val tier = Achievements.snapshot(definition).claimableTier ?: return finish(definition)
        val before = Progress.coins
        if (Achievements.claim(definition.id) <= 0) return finish(definition)
        val after = Achievements.snapshot(definition)
        val more = after.claimableTier != null
        val reward = card.findViewWithTag<ImageView>(if (definition.tiered) "achievement_icon_${definition.id}" else "achievement_reward_${definition.id}")
        // A narrow card folds a challenge's plaque away: it then leaves from the tile.
        val from: View = reward?.takeIf { it.isShown } ?: card.findViewWithTag("achievement_icon_${definition.id}") ?: card
        val launched = {
            // The moment the medal leaves the card.
            SoundFx.play("place", rate = .8f + .12f * step, vol = .8f)
            if (tier == 3 && definition.tiered) SoundFx.play("perfect", rate = 1f, vol = .45f)
            Haptics.success()
            ring(from, AchievementCards.frameColor(definition, tier))
            toCase(from, definition, tier)
            val coins = PayFx.fly(this, kit, from, bank, n = 4 + 2 * tier.coerceAtMost(3), onDone = { if (!more && isAttachedToWindow) finish(definition) })
            countBank(before, coins)
            if (more) postDelayed({ collect(definition, card, step + 1) }, 560L)
        }
        if (reward == null) launched()
        else lift(reward, refill = { if (definition.tiered) cards.bindMedal(reward, after) else cards.bindPlaque(reward, after) }, launched)
    }

    /** Every medal is in: the card settles into its new state. */
    private fun finish(definition: Achievements.Definition) {
        SoundFx.play("success", rate = 1.2f, vol = .5f)
        val states = Achievements.snapshot()
        hero.bind(states, animate = false)
        val held = scroll.scrollY
        val from = rows.findViewWithTag<View>("achievement_card_${definition.id}")?.height ?: 0
        // A wall row is as tall as its taller medal: it simply takes its new height.
        rebuild(definition)?.let { if (!definition.tiered) settle(it, from) }
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
     * The reward winds up (a squash) and springs off the card, big; [launched] runs as it leaves.
     * Its place stays empty a moment, then [refill] puts in what comes next, popping in.
     */
    private fun lift(reward: ImageView, refill: () -> Unit, launched: () -> Unit) {
        var fired = false
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            addUpdateListener {
                val t = it.animatedValue as Float
                if (t < .4f) {
                    val k = t / .4f
                    reward.scaleX = 1f + .16f * k; reward.scaleY = 1f - .18f * k
                } else {
                    if (!fired) { fired = true; launched() }
                    val k = (t - .4f) / .6f
                    val s = (1.16f + .2f * k) * (1f - k)
                    reward.scaleX = s; reward.scaleY = s * (1f + .3f * (1f - k))
                }
                Anim.repaint(reward)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!fired && !cancelled) launched()
                    refill()
                    if (cancelled) { reward.scaleX = 1f; reward.scaleY = 1f } else Anim.popIn(reward, 160L, .2f, 360)
                }
            })
            Anim.cancelOnDetach(reward, this)
            start()
        }
    }

    /** Where [v] sits in the sheet's coordinates: its laid-out box, not its drawn one. */
    private fun inSheet(v: View): android.graphics.RectF {
        val box = android.graphics.Rect(0, 0, v.width, v.height)
        sheet.offsetDescendantRectToMyCoords(v, box)
        return android.graphics.RectF(box)
    }

    /** A ring of the reward's colour bursts out where it left the card, and fades. */
    private fun ring(at: View, color: Int) {
        val box = inSheet(at)
        val size = (maxOf(box.width(), box.height()) * 2.6f).toInt()
        val wave = View(activity).apply {
            background = RingWave(Theme.lighten(color, .1f), dpf(10f))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        sheet.addView(wave, FrameLayout.LayoutParams(size, size))
        wave.x = box.centerX() - size / 2f
        wave.y = box.centerY() - size / 2f
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 520
            interpolator = android.view.animation.DecelerateInterpolator(1.8f)
            addUpdateListener {
                val t = it.animatedValue as Float
                wave.scaleX = .38f + .62f * t; wave.scaleY = wave.scaleX
                wave.alpha = 1f - t
                Anim.repaint(wave)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                // Removing it inside a detach walk would leave a hole in the sheet's children.
                override fun onAnimationEnd(animation: android.animation.Animator) { if (!cancelled) sheet.removeView(wave) }
            })
            Anim.cancelOnDetach(wave, this)
            start()
        }
    }

    /**
     * The medal just handed over hops from the card into its slot in the case, which
     * counts it as it lands. With the case scrolled away it simply counts.
     */
    private fun toCase(from: View, definition: Achievements.Definition, tier: Int) {
        val slot = hero.slotFor(definition, tier)
        val seen = android.graphics.Rect()
        if (hero.visibility != View.VISIBLE || !slot.getGlobalVisibleRect(seen) || seen.height() < slot.height / 2) {
            hero.bind(Achievements.snapshot(), animate = true)
            return
        }
        val a = IntArray(2); val b = IntArray(2); val c = IntArray(2)
        getLocationInWindow(a); from.getLocationInWindow(b); slot.getLocationInWindow(c)
        val size = slot.width
        val sx = b[0] - a[0] + from.width / 2f; val sy = b[1] - a[1] + from.height / 2f
        val ex = c[0] - a[0] + slot.width / 2f; val ey = c[1] - a[1] + slot.height / 2f
        val medal = ImageView(activity).apply {
            setImageDrawable(if (definition.tiered) AchievementMedal(definition.id, AchievementCards.accent(definition.id), tier) else rewardArt(false, tier, true))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(medal, FrameLayout.LayoutParams(size, size))
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 460
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                val arc = sin(t * Math.PI).toFloat()
                medal.x = sx + (ex - sx) * t - size / 2f - arc * dpf(30f)
                medal.y = sy + (ey - sy) * t - size / 2f
                val s = 1.5f - .5f * t + .25f * arc
                medal.scaleX = s; medal.scaleY = s
                Anim.repaint(medal)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (cancelled) return
                    removeView(medal)
                    hero.bind(Achievements.snapshot(), animate = true)
                    Haptics.tick()
                }
            })
            Anim.cancelOnDetach(medal, this)
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
        val state = Achievements.snapshot(definition)
        return (if (definition.tiered) cards.medalCard(state, index, animateFill = false) else cards.card(state, index, animateFill = false)).also { parent.addView(it, index, params) }
    }

    override fun onDetachedFromWindow() {
        bankCount?.cancel()
        super.onDetachedFromWindow()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        if (paying) true else super.dispatchTouchEvent(event)

    // Touches are swallowed while the coins fly, but leaving the page never is.
    override fun onBack() = close()

    private companion object {
        const val FIRST_SCREEN = 5
        const val PER_FRAME = 3
        const val BUILD_BUDGET_MS = 4L
        const val MIN_TITLE_SP = 16f
        const val SUGGEST_URL = "https://github.com/Eve-146T/cube-run/issues/new"
    }
}

/** A thick ring of one colour with a white inner edge: the shock of a medal being struck. */
private class RingWave(private val color: Int, private val thickness: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY()
        val r = bounds.width() / 2f - thickness
        if (r < 2f) return
        paint.strokeWidth = thickness; paint.color = color
        canvas.drawCircle(cx, cy, r, paint)
        paint.strokeWidth = thickness * .35f; paint.color = Theme.alpha(Theme.WHITE, 200)
        canvas.drawCircle(cx, cy, r - thickness * .5f, paint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
