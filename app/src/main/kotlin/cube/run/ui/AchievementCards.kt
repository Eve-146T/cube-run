package cube.run.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import cube.run.R
import cube.run.core.Haptics
import cube.run.data.Achievements

/**
 * The achievement page's cards: one white card per achievement, in a single column. On the left
 * the achievement's picture on its candy tile; in the middle its name, what counts, and either how
 * far along it is or, when a reward is waiting, a gold CLAIM button with everything it pays; on
 * the right the reward itself: the medal (or a challenge's plaque) waiting to be collected, or
 * the empty slot of the one being worked towards, with a medal family's four tiers as dots under
 * it. What a reward pays stays a surprise until it can be claimed.
 */
@SuppressLint("SetTextI18n")
internal class AchievementCards(
    private val activity: Activity,
    private val kit: UiKit,
    private val onClaim: (Achievements.Snapshot, CandyButton) -> Unit,
) {
    private fun dp(v: Float) = kit.dp(v)
    private fun dpf(v: Float) = kit.dpf(v)

    fun card(state: Achievements.Snapshot, index: Int, animateFill: Boolean = true): View {
        val definition = state.definition
        val ready = state.claimableTier != null
        return AchievementCard(activity, dp(TILE + 12f + 22f), dp(REWARD + 12f)).apply {
            tag = "achievement_card_${definition.id}"
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            background = surface(ready)
            setPadding(dp(12f), dp(12f), dp(10f), dp(12f) + kit.CARD_LIP)
            addView(tile(state), LinearLayout.LayoutParams(dp(TILE), dp(TILE)))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = false; clipToPadding = false
                addView(header(state), LinearLayout.LayoutParams(-1, -2))
                slot(state, index, animateFill)?.let {
                    addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })
                }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12f) })
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                clipChildren = false; clipToPadding = false
                rewardColumn = this
                addView(reward(state), LinearLayout.LayoutParams(dp(REWARD), dp(REWARD)))
                if (definition.tiered) addView(tierDots(state), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(1f) })
            }, LinearLayout.LayoutParams(dp(REWARD + 6f), -2).apply { marginStart = dp(6f) })
        }
    }

    /** A finished achievement on the DONE shelf: its tile, its name and everything it paid out. */
    fun doneChip(state: Achievements.Snapshot): View = LinearLayout(activity).apply {
        val definition = state.definition
        tag = "achievement_card_${definition.id}"
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false; clipToPadding = false
        background = kit.cardDrawable(Theme.CARD, null, 18f)
        setPadding(dp(10f), dp(8f), dp(10f), dp(8f) + kit.CARD_LIP)
        val icon = tile(state)
        addView(icon, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        addView(kit.text(activity.achievementTitle(definition.id), 17f, Theme.INK, 700, Gravity.START).apply {
            maxLines = 2
            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_FULL
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12f) })
        // Bouncer keeps its personal best on show after it is done.
        if (definition.id == "bounces") addView(kit.text(number(state.value), 17f, Theme.INK_SOFT, 700).apply {
            tag = "achievement_best_${definition.id}"
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8f); marginEnd = dp(4f) })
        val won = if (definition.tiered) (0..3).map { MedalArt(it, true) } else listOf(PlaqueArt(true))
        for ((i, art) in won.withIndex()) addView(ImageView(activity).apply {
            setImageDrawable(art)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(30f), dp(30f)).apply { if (i > 0) marginStart = -dp(5f) })
        contentDescription = activity.getString(R.string.achievement_done_description, activity.achievementTitle(definition.id))
        setOnClickListener { Anim.popIn(icon, 0, 1.15f, 320); Haptics.tick() }
    }

    /** The picture on its tile; it says when there is nothing left to do. */
    private fun tile(state: Achievements.Snapshot): ImageView = ImageView(activity).apply {
        val definition = state.definition
        tag = "achievement_icon_${definition.id}"
        setImageDrawable(AchievementTile(definition.id, accent(definition.id)))
        scaleType = ImageView.ScaleType.FIT_CENTER
        val finished = state.allClaimed || (!definition.tiered && state.nextTarget == null)
        if (finished) contentDescription = activity.getString(if (definition.tiered) R.string.achievement_all_rewards_claimed else R.string.achievement_challenge_complete)
        else importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** The reward on the right of the card. */
    private fun reward(state: Achievements.Snapshot): ImageView = ImageView(activity).apply {
        tag = "achievement_reward_${state.definition.id}"
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        bindReward(this, state)
    }

    /**
     * The medal waiting to be collected, shining on a sunburst; else the empty slot of the next
     * one; once everything is paid, the last one won.
     */
    fun bindReward(view: ImageView, state: Achievements.Snapshot) {
        val definition = state.definition
        val waiting = state.claimableTier
        view.setImageDrawable(when {
            waiting != null -> rewardArt(definition.tiered, waiting, true)
            state.allClaimed -> rewardArt(definition.tiered, definition.thresholds.lastIndex, true)
            else -> rewardArt(definition.tiered, state.earnedTiers, false)
        })
        view.background = if (waiting != null) RewardRays(if (definition.tiered) MedalArt.METALS[waiting] else MedalArt.PLAQUE) else null
    }

    /** A medal family's four tiers as dots: won ones in their metal, waiting ones ringed, the rest empty. */
    private fun tierDots(state: Achievements.Snapshot): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        for (tier in 0..3) addView(ImageView(activity).apply {
            tag = "achievement_medal_${state.definition.id}_$tier"
            bindDot(this, state, tier)
        }, LinearLayout.LayoutParams(dp(DOT), dp(DOT)).apply { if (tier > 0) marginStart = dp(3f) })
    }

    fun bindDot(dot: ImageView, state: Achievements.Snapshot, tier: Int) {
        dot.setImageDrawable(TierDot(MedalArt.METALS[tier], when {
            tier < state.claimedTiers -> TierDot.WON
            tier < state.earnedTiers -> TierDot.WAITING
            else -> TierDot.EMPTY
        }))
        dot.contentDescription = activity.getString(R.string.achievement_medal_status, activity.achievementTierName(tier), activity.getString(when {
            tier < state.claimedTiers -> R.string.achievement_status_claimed
            tier < state.earnedTiers -> R.string.achievement_status_ready
            tier == state.earnedTiers -> R.string.achievement_status_progress
            else -> R.string.achievement_status_locked
        }))
    }

    /** Name and what counts (or "Claimed" once it is all paid out). */
    private fun header(state: Achievements.Snapshot): View = LinearLayout(activity).apply {
        val definition = state.definition
        orientation = LinearLayout.VERTICAL
        val complete = state.nextTarget == null
        val best = if (definition.id == "bounces") activity.getString(R.string.achievement_best_bounces, number(state.value)) else null
        val subtitle = when {
            state.allClaimed -> best?.let { activity.getString(R.string.achievement_claimed_with_best, it) } ?: activity.getString(R.string.achievement_claimed)
            definition.id == "bounces" && complete -> best.orEmpty()
            // A finished one-off dare needs no reminder of what it asked.
            !definition.tiered && complete -> ""
            else -> activity.achievementGoal(definition.id)
        }
        addView(kit.text(activity.achievementTitle(definition.id), 20f, Theme.INK, 700, Gravity.START).apply {
            maxLines = 2
            // Long single words (German compounds) shrink, then hyphenate, never split at random.
            setAutoSizeTextTypeUniformWithConfiguration(16, 20, 1, TypedValue.COMPLEX_UNIT_SP)
            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_FULL
        }, LinearLayout.LayoutParams(-1, -2))
        if (subtitle.isNotEmpty()) addView(kit.text(subtitle, 15f, Theme.INK_SOFT, 500, Gravity.START).apply {
            maxLines = 3
            tag = if (best != null && complete) "achievement_best_${definition.id}" else "achievement_subtitle_${definition.id}"
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2f) })
    }

    /** Under the name: the CLAIM button, or the bar and count towards the next goal, or nothing. */
    private fun slot(state: Achievements.Snapshot, index: Int, animateFill: Boolean): View? {
        val definition = state.definition
        return when {
            state.claimableTier != null -> claimButton(state)
            state.nextTarget == null -> null
            // A one-off dare is done or not: a "0 / 1" count would only ever read 0.
            !definition.tiered && definition.thresholds.single() == 1 -> null
            else -> progress(state, state.earnedTiers, index, animateFill)
        }
    }

    /** A bar in the achievement's colour, and "1340 / 2K" beside it. */
    private fun progress(state: Achievements.Snapshot, tier: Int, index: Int, animateFill: Boolean): View = LinearLayout(activity).apply {
        tag = "achievement_slot_${state.definition.id}"
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(bar(state, index, animateFill), LinearLayout.LayoutParams(0, dp(12f), 1f))
        addView(kit.text(counter(state, tier), 15f, Theme.INK, 700, Gravity.END).apply {
            maxLines = 1; tag = "achievement-counter"
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10f) })
    }

    /**
     * "1,340 / 2K". Good Runner asks you to beat its number, not to reach one more than it:
     * the counter shows the number on the medal, and on the exact boundary says so rather than
     * showing a full-looking 1,000 / 1,000 with no reward behind it.
     */
    private fun counter(state: Achievements.Snapshot, tier: Int): String {
        val definition = state.definition
        val target = definition.thresholds[tier]
        val beat = definition.id == "runner" && state.value >= target
        // Non-breaking around the slash: a narrow column must never split the count over two lines.
        return if (definition.id == "bounces") activity.getString(R.string.achievement_best_progress, number(state.value), number(target)).replace(" / ", " / ")
            else "${number(minOf(state.value, target))} / ${compact(if (beat) target + 1 else target)}"
    }

    /** How far towards the next goal, filled in the achievement's own colour. */
    private fun bar(state: Achievements.Snapshot, index: Int, animateFill: Boolean): View =
        AchievementProgressBar(activity, kit, accent(state.definition.id), state.fraction,
            if (animateFill) 120L + index * 35L else 0L, animateFill, trough = Theme.alpha(Theme.INK, 22)).apply {
            tag = "achievement_progress_${state.definition.id}"
            contentDescription = activity.getString(R.string.achievement_percent_complete, (state.fraction * 100).toInt())
        }

    /** The gold CLAIM button, with everything the card has waiting: one tap collects every medal earned. */
    private fun claimButton(state: Achievements.Snapshot): View {
        val amount = (state.claimedTiers until state.earnedTiers).sumOf { Achievements.reward(state.definition, it) }
        lateinit var button: CandyButton
        button = kit.button(android.text.SpannableStringBuilder(activity.getString(R.string.achievement_claim)).append("  ").append(kit.coins(number(amount), 17f)), Theme.GOLD, UiKit.Size.SMALL) {
            onClaim(state, button)
        }.apply {
            tag = "achievement_claim_${state.definition.id}"
            maxLines = 1
            textSize = 17f
            contentDescription = activity.getString(R.string.achievement_claim_description, number(amount), activity.achievementTitle(state.definition.id))
        }
        return button
    }

    /** A white card with a darker lip. A waiting reward gets a gold edge. */
    private fun surface(ready: Boolean): Drawable {
        val radius = dpf(RADIUS)
        return LayerDrawable(arrayOf(
            GradientDrawable().apply { cornerRadius = radius; setColor(if (ready) Theme.darken(Theme.GOLD, .2f) else Theme.alpha(Theme.INK, 26)) },
            GradientDrawable().apply { cornerRadius = radius; setColor(Theme.CARD); if (ready) setStroke(dp(3f), Theme.GOLD) },
        )).apply { setLayerInset(1, 0, 0, 0, kit.CARD_LIP) }
    }

    companion object {
        private const val RADIUS = 22f
        const val TILE = 60f
        const val REWARD = 56f
        const val DOT = 9f

        /** The colour of what a claim of [tier] gives: the medal's metal, or a challenge's own colour. */
        fun frameColor(definition: Achievements.Definition, tier: Int): Int =
            if (definition.tiered) MedalArt.METALS[tier.coerceIn(0, 3)] else accent(definition.id)

        /** The achievement's tile colour, picked to stand apart from the picture on it. */
        fun accent(id: String): Int = when (id) {
            "runner", "bounces", "near_miss", "pile_driver", "long_con", "stage_fright", "silent_treatment", "shardsmith", "voidwalker" -> Theme.SKY
            "coins", "bubbles", "regular", "untouchable", "greed", "scenic_route", "two_ez" -> Theme.GRAPE
            "cubes", "cookie", "exactly_67" -> Theme.PINK
            "powerups", "long_hauler" -> Theme.ORANGE
            "boxes", "center", "bankrupt", "monk", "neo" -> Theme.MINT
            "homeress", "magpie", "bubble_popper", "house_loses", "nervous_tic" -> Theme.BERRY
            "gambliphobic" -> Theme.LIME
            "greedy", "coal_miner", "full_kit" -> Theme.YELLOW
            "just_browsing" -> Theme.GOLD
            "shard_hunter", "insomniac", "globetrotter" -> DEEP
            else -> Theme.PINK
        }

        /** A deep indigo tile for night and space pictures. */
        private const val DEEP = 0xFF4A3FA6.toInt()
    }
}

/**
 * A card row that keeps its text readable: when the name and goal would be squeezed narrower
 * than a few words of the title (a split pane, large text), the reward column folds away and
 * the text takes its room.
 */
@SuppressLint("ViewConstructor")
internal class AchievementCard(context: Context, private val fixedStart: Int, private val rewardWidth: Int) : LinearLayout(context) {
    var rewardColumn: View? = null

    init { orientation = HORIZONTAL }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val title = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 20f, resources.displayMetrics)
        val room = MeasureSpec.getSize(widthMeasureSpec) - fixedStart - rewardWidth
        rewardColumn?.visibility = if (room < title * 7.5f) GONE else VISIBLE
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

/** Start the fill when it first enters the viewport, including cards reached by scrolling. */
@SuppressLint("ViewConstructor")
internal class AchievementProgressBar(context: Context, private val kit: UiKit, private val color: Int,
    private val target: Float, private val delay: Long, animateFill: Boolean = true, fromFraction: Float? = null,
    private val trough: Int = Theme.alpha(Theme.WHITE, 50)) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var amount = fromFraction ?: if (animateFill) 0f else target.coerceIn(0f, 1f)
    private var played = !animateFill && fromFraction == null
    private var fill: ValueAnimator? = null
    private var observedTree: ViewTreeObserver? = null
    private val visibleRect = android.graphics.Rect()
    private val beforeDraw = ViewTreeObserver.OnPreDrawListener { startWhenVisible(); true }
    private val afterScroll = ViewTreeObserver.OnScrollChangedListener { startWhenVisible() }

    private fun stopWatching() {
        observedTree?.takeIf { it.isAlive }?.let {
            it.removeOnPreDrawListener(beforeDraw)
            it.removeOnScrollChangedListener(afterScroll)
        }
        observedTree = null
    }

    private fun startWhenVisible() {
        if (!played && width > 0 && height > 0 && isShown && getLocalVisibleRect(visibleRect)) {
            played = true
            stopWatching()
            fill = ValueAnimator.ofFloat(amount, target.coerceIn(0f, 1f)).apply {
                duration = 850; startDelay = delay
                interpolator = PathInterpolator(.16f, 1f, .3f, 1f)
                addUpdateListener {
                    amount = it.animatedValue as Float
                    // Local damage alone can leave sibling text and parent transforms stale over GL.
                    Anim.repaint(this@AchievementProgressBar)
                }
                start()
            }
            Anim.repaint(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!played) {
            observedTree = viewTreeObserver.also {
                it.addOnPreDrawListener(beforeDraw)
                it.addOnScrollChangedListener(afterScroll)
            }
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = height / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = trough; canvas.drawRoundRect(rect, radius, radius, paint)
        if (amount > 0f) {
            val length = maxOf(width * amount, height.toFloat())
            if (layoutDirection == LAYOUT_DIRECTION_RTL) rect.left = width - length else rect.right = length
            val cap = minOf(radius, rect.width() / 2f)
            paint.color = Theme.darken(color, .18f); canvas.drawRoundRect(rect, cap, cap, paint)
            rect.bottom -= kit.dpf(2f)
            paint.color = color; canvas.drawRoundRect(rect, cap, cap, paint)
            // A gloss stripe along the top of the fill.
            rect.set(rect.left + cap * .6f, kit.dpf(1.5f), rect.right - cap * .6f, kit.dpf(3.5f))
            if (rect.width() > 0f) { paint.color = Theme.alpha(Theme.WHITE, 90); canvas.drawRoundRect(rect, kit.dpf(1f), kit.dpf(1f), paint) }
        }
    }

    override fun onDetachedFromWindow() {
        stopWatching()
        fill?.cancel(); fill = null
        amount = target.coerceIn(0f, 1f)
        played = true
        super.onDetachedFromWindow()
    }
}
