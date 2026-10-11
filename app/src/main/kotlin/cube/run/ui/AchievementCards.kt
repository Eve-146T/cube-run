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
import cube.run.data.Achievements

/**
 * The achievement page's cards: one white card per achievement, in a single column, all built
 * the same way. On top the achievement's picture on its candy tile, its name and what counts,
 * and on the right one badge: the best one won so far (a medal family's metal, a challenge's
 * check), the one waiting to be collected on a glow, or an empty slot when there is none yet.
 * Underneath, the bar and count towards the next goal, and when a badge is waiting a gold CLAIM
 * button with everything it pays. What a reward pays stays a surprise until it can be claimed.
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
        return AchievementCard(activity, dp(PAD * 2 + TILE + 12f), dp(BADGE + 8f)).apply {
            tag = "achievement_card_${definition.id}"
            clipChildren = false; clipToPadding = false
            background = surface(ready)
            setPadding(dp(PAD), dp(PAD), dp(PAD), dp(PAD) + kit.CARD_LIP)
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false; clipToPadding = false
                addView(tile(state), LinearLayout.LayoutParams(dp(TILE), dp(TILE)))
                addView(header(state), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12f) })
                addView(reward(state).also { rewardColumn = it },
                    LinearLayout.LayoutParams(dp(BADGE), dp(BADGE)).apply { marginStart = dp(8f) })
            }, LinearLayout.LayoutParams(-1, -2))
            bottom(state, index, animateFill)?.let { addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) }) }
        }
    }

    /**
     * The card's last row, always the same height: the bar and count towards the next goal, and
     * while a badge is waiting the CLAIM button lying over it. Claiming hands the row back to the
     * bar in place, so the card never grows or shrinks around it.
     */
    private fun bottom(state: Achievements.Snapshot, index: Int, animateFill: Boolean): View? {
        val ready = state.claimableTier != null
        val progress = slot(state, index, animateFill)
        if (!ready && progress == null) return null
        return FrameLayout(activity).apply {
            tag = "achievement_bottom_${state.definition.id}"
            clipChildren = false; clipToPadding = false
            // Large text may need more; the row then grows for bar and button alike.
            minimumHeight = dp(BOTTOM)
            progress?.let {
                if (ready) it.alpha = 0f
                addView(it, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL))
            }
            if (ready) addView(claimButton(state), FrameLayout.LayoutParams(-1, -1))
        }
    }

    /**
     * The card after its CLAIM is tapped: the button melts into the bar beneath it and the gold
     * edge fades. With no bar to come back (the last badge, a one-off dare) the row folds away
     * at the same time instead, so the card never holds an empty gap.
     */
    fun settleClaimed(card: View, button: View) {
        val row = button.parent as? FrameLayout ?: return
        val progress = (0 until row.childCount).map { row.getChildAt(it) }.firstOrNull { it !== button }
        button.isEnabled = false
        button.pivotY = button.height / 2f
        button.animate().alpha(0f).scaleX(.96f).scaleY(.8f).setDuration(200).setInterpolator(Anim.ease).start()
        progress?.animate()?.alpha(1f)?.setStartDelay(80)?.setDuration(220)?.start()
        // Only the gold edge and lip fade to the plain card's; the white body stays solid.
        val radius = dpf(RADIUS)
        val lip = GradientDrawable().apply { cornerRadius = radius; setColor(LIP_READY) }
        val body = GradientDrawable().apply { cornerRadius = radius; setColor(Theme.CARD); setStroke(dp(3f), Theme.GOLD) }
        card.background = LayerDrawable(arrayOf(lip, body)).apply { setLayerInset(1, 0, 0, 0, kit.CARD_LIP) }
        val blend = android.animation.ArgbEvaluator()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260; interpolator = Anim.ease
            addUpdateListener {
                val k = it.animatedValue as Float
                lip.setColor(blend.evaluate(k, LIP_READY, LIP) as Int)
                body.setStroke(dp(3f), Theme.alpha(Theme.GOLD, (255 * (1f - k)).toInt()))
            }
            Anim.cancelOnDetach(card, this)
            start()
        }
        if (progress != null) return
        // The button keeps its own height while the row closes over it: squeezed flat, a candy
        // button would paint garbage across the card.
        row.clipChildren = true
        button.layoutParams = (button.layoutParams as FrameLayout.LayoutParams).apply { height = button.height; gravity = Gravity.TOP }
        val params = row.layoutParams as LinearLayout.LayoutParams
        val height = row.height; val margin = params.topMargin
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 260; interpolator = Anim.ease
            addUpdateListener {
                val k = it.animatedValue as Float
                params.height = (height * k).toInt(); params.topMargin = (margin * k).toInt()
                row.requestLayout(); Anim.repaint(card)
            }
            Anim.cancelOnDetach(row, this)
            start()
        }
    }

    /** A finished achievement on the DONE shelf: its tile, its name and its last badge. */
    fun doneChip(state: Achievements.Snapshot): View = LinearLayout(activity).apply {
        val definition = state.definition
        tag = "achievement_card_${definition.id}"
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false; clipToPadding = false
        background = kit.cardDrawable(Theme.CARD, null, 18f)
        setPadding(dp(10f), dp(8f), dp(12f), dp(8f) + kit.CARD_LIP)
        val icon = tile(state)
        addView(icon, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        addView(kit.text(activity.achievementTitle(definition.id), 17f, Theme.INK, 700, Gravity.START).apply {
            maxLines = 2
            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_FULL
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12f) })
        // A record-keeping achievement keeps its best on show after it is done.
        record(state)?.let { best ->
            addView(kit.text(best, 15f, Theme.INK_SOFT, 700).apply {
                tag = "achievement_best_${definition.id}"
                maxLines = 1
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8f); marginEnd = dp(6f) })
        }
        val badge = ImageView(activity).apply {
            setImageDrawable(rewardArt(definition.tiered, definition.thresholds.lastIndex, true))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(badge, LinearLayout.LayoutParams(dp(36f), dp(36f)))
        contentDescription = activity.getString(R.string.achievement_done_description, activity.achievementTitle(definition.id))
        // Nothing left to do here but play: the tile squishes and the badge hops after it.
        setOnClickListener { Pokes.jelly(icon); postDelayed({ Pokes.wobble(badge) }, 70L) }
    }

    /** The picture on its tile; it says when there is nothing left to do. */
    private fun tile(state: Achievements.Snapshot): ImageView = ImageView(activity).apply {
        val definition = state.definition
        tag = "achievement_icon_${definition.id}"
        setImageDrawable(AchievementTile(definition.id, accent(definition.id)))
        scaleType = ImageView.ScaleType.FIT_CENTER
        setOnClickListener { Pokes.jelly(this) }
        val finished = state.allClaimed || (!definition.tiered && state.nextTarget == null)
        if (finished) contentDescription = activity.getString(if (definition.tiered) R.string.achievement_all_rewards_claimed else R.string.achievement_challenge_complete)
        else importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** The one badge on the right of the card. */
    private fun reward(state: Achievements.Snapshot): ImageView = ImageView(activity).apply {
        tag = "achievement_reward_${state.definition.id}"
        scaleType = ImageView.ScaleType.FIT_CENTER
        bindReward(this, state)
        // A badge you have hops; an empty slot shakes its head.
        setOnClickListener { if (isActivated) Pokes.wobble(this) else Pokes.nudge(this) }
    }

    /**
     * The badge waiting to be collected, on a glow; else the best one won; else the empty slot
     * of the first one. Never the ones still to come.
     */
    fun bindReward(view: ImageView, state: Achievements.Snapshot) {
        val definition = state.definition
        val waiting = state.claimableTier
        val tier = waiting ?: (state.claimedTiers - 1).coerceAtLeast(0)
        val owned = waiting != null || state.claimedTiers > 0
        view.isActivated = owned
        view.setImageDrawable(rewardArt(definition.tiered, tier, owned))
        view.background = if (waiting != null) WaitingGlow(BadgeArt.colorOf(if (definition.tiered) tier else BadgeArt.CHALLENGE)) else null
        // A waiting badge gives a little of its room to the glow.
        val inset = dp(if (waiting != null) 6f else 2f)
        view.setPadding(inset, inset, inset, inset)
        view.contentDescription = when {
            waiting != null -> activity.getString(R.string.achievement_medal_status, badgeName(definition, tier), activity.getString(R.string.achievement_status_ready))
            owned -> activity.getString(R.string.achievement_medal_status, badgeName(definition, tier), activity.getString(R.string.achievement_status_claimed))
            else -> null
        }
        view.importantForAccessibility = if (owned) View.IMPORTANT_FOR_ACCESSIBILITY_YES else View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** The badge just collected, stamped in: won, without its glow. */
    fun bindWon(view: ImageView, definition: Achievements.Definition, tier: Int) {
        view.isActivated = true
        view.setImageDrawable(rewardArt(definition.tiered, tier, true))
        view.background = null
        view.setPadding(dp(2f), dp(2f), dp(2f), dp(2f))
    }

    private fun badgeName(definition: Achievements.Definition, tier: Int): String =
        if (definition.tiered) activity.achievementTierName(tier) else activity.achievementTitle(definition.id)

    /** "Best: 6120" for an achievement that keeps an uncapped personal best, else null. */
    private fun record(state: Achievements.Snapshot): String? = when (state.definition.id) {
        "bounces" -> activity.getString(R.string.achievement_best_bounces, number(state.value))
        in RECORDS -> activity.getString(R.string.achievement_best, number(state.value))
        else -> null
    }

    /** Name, and what counts; once there is nothing left to aim for, the record if it keeps one. */
    private fun header(state: Achievements.Snapshot): View = LinearLayout(activity).apply {
        val definition = state.definition
        orientation = LinearLayout.VERTICAL
        val complete = state.nextTarget == null
        // Claiming changes none of this, so a claimed card keeps its height.
        val best = if (complete) record(state) else null
        val subtitle = when {
            best != null -> best
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
            maxLines = 5
            tag = if (best != null) "achievement_best_${definition.id}" else "achievement_subtitle_${definition.id}"
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(1f) })
    }

    /** Under the name: the bar and count towards the next goal, or nothing once there is none. */
    private fun slot(state: Achievements.Snapshot, index: Int, animateFill: Boolean): View? {
        val definition = state.definition
        return when {
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

    /** The gold CLAIM button, with everything the card has waiting: one tap collects every badge earned. */
    private fun claimButton(state: Achievements.Snapshot): View {
        val amount = (state.claimedTiers until state.earnedTiers).sumOf { Achievements.reward(state.definition, it) }
        lateinit var button: CandyButton
        button = kit.button(android.text.SpannableStringBuilder(activity.getString(R.string.achievement_claim)).append("  ").append(kit.coins(number(amount), 16f)), Theme.GOLD, UiKit.Size.SMALL) {
            onClaim(state, button)
        }.apply {
            // It fills the bar's row exactly, so its text centres in the row rather than padding it out.
            setPadding(dp(16f), 0, dp(16f), 0)
            tag = "achievement_claim_${state.definition.id}"
            maxLines = 1
            textSize = 16f
            contentDescription = activity.getString(R.string.achievement_claim_description, number(amount), activity.achievementTitle(state.definition.id))
        }
        return button
    }

    /** A white card with a darker lip. A waiting reward gets a gold edge. */
    private fun surface(ready: Boolean): Drawable {
        val radius = dpf(RADIUS)
        return LayerDrawable(arrayOf(
            GradientDrawable().apply { cornerRadius = radius; setColor(if (ready) LIP_READY else LIP) },
            GradientDrawable().apply { cornerRadius = radius; setColor(Theme.CARD); if (ready) setStroke(dp(3f), Theme.GOLD) },
        )).apply { setLayerInset(1, 0, 0, 0, kit.CARD_LIP) }
    }

    companion object {
        private const val RADIUS = 22f
        private val LIP_READY = Theme.darken(Theme.GOLD, .2f)
        private val LIP = Theme.alpha(Theme.INK, 26)
        private const val PAD = 14f
        const val TILE = 64f
        const val BADGE = 56f
        /** Achievements that keep an uncapped personal best worth showing ("Best: 6120"). */
        private val RECORDS = setOf("runner", "bubbles", "pile_driver")
        /** The bar's row: as tall as the CLAIM button, whether or not one is waiting. */
        const val BOTTOM = 40f

        /** The achievement's tile colour, picked to stand apart from the picture on it. */
        fun accent(id: String): Int = when (id) {
            "runner", "bounces", "long_con", "stage_fright", "silent_treatment", "voidwalker" -> Theme.SKY
            "bubbles", "untouchable", "scenic_route", "two_ez", "bubble_popper", "powerups", "boxes", "greedy" -> Theme.GRAPE
            "cubes", "cookie", "exactly_67" -> Theme.PINK
            "long_hauler" -> Theme.ORANGE
            "center", "bankrupt", "monk", "neo", "near_miss", "greed" -> Theme.MINT
            "coins", "homeress", "magpie", "house_loses", "nervous_tic" -> Theme.BERRY
            "gambliphobic" -> Theme.LIME
            "coal_miner", "full_kit", "regular" -> Theme.YELLOW
            "just_browsing" -> Theme.GOLD
            "shard_hunter", "insomniac", "globetrotter", "shardsmith" -> DEEP
            else -> Theme.PINK
        }

        /** A deep indigo tile for night and space pictures. */
        private const val DEEP = 0xFF4A3FA6.toInt()
    }
}

/**
 * A card that keeps its text readable: when the name and goal would be squeezed narrower than a
 * few words of the title (a split pane, large text), its badge folds away and the text takes its
 * room.
 */
@SuppressLint("ViewConstructor")
internal class AchievementCard(context: Context, private val fixedStart: Int, private val rewardWidth: Int) : LinearLayout(context) {
    var rewardColumn: View? = null

    init { orientation = VERTICAL }

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
