package cube.run.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import cube.run.R
import cube.run.core.Haptics
import cube.run.data.Achievements

/**
 * The achievement page's cards: one white card per achievement, in a single column. The top row
 * is the badge, the name and what counts; below it how far along it is and what the next reward
 * pays. Medal families show their four medals on one track instead of a bar. A reward waiting to
 * be claimed puts a wide gold CLAIM button on its card; fully claimed ones fade back.
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
        val tier = state.claimableTier ?: state.earnedTiers.coerceAtMost(definition.thresholds.lastIndex)
        val complete = state.nextTarget == null
        return SheenBand(activity, FloatArray(8) { dpf(RADIUS) }).apply {
            tag = "achievement_card_${definition.id}"
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            shine = state.claimableTier != null
            background = surface(state)
            setPadding(dp(14f), dp(14f), dp(14f), dp(14f) + kit.CARD_LIP)
            if (state.allClaimed) alpha = .82f
            addView(header(state), LinearLayout.LayoutParams(-1, -2))
            if (state.allClaimed) return@apply
            val footer = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) }
            when {
                state.claimableTier != null -> addView(claimButton(state), footer)
                // A one-off dare is done or not: a "0 / 1" bar would only ever be empty.
                !definition.tiered && definition.thresholds.single() == 1 -> addView(reward(state, tier), footer.apply { gravity = Gravity.END })
                !complete -> addView(counterLine(state, tier), footer)
            }
            if (definition.tiered) addView(medalTrack(state, tier).apply { tag = "achievement_progress_${definition.id}" },
                LinearLayout.LayoutParams(-1, dp(40f)).apply { topMargin = dp(8f) })
            else if (!complete && definition.thresholds.single() > 1) addView(bar(state, index, animateFill),
                LinearLayout.LayoutParams(-1, dp(12f)).apply { topMargin = dp(8f) })
        }
    }

    /** A finished achievement on the DONE shelf: a slim row with its badge, its name and a check. */
    fun doneChip(state: Achievements.Snapshot): View = LinearLayout(activity).apply {
        val definition = state.definition
        tag = "achievement_card_${definition.id}"
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply { cornerRadius = dpf(18f); setColor(Theme.alpha(Theme.WHITE, 40)) }
        setPadding(dp(10f), dp(8f), dp(14f), dp(8f))
        val icon = badge(definition.id, 40f)
        addView(icon, LinearLayout.LayoutParams(dp(40f), dp(40f)))
        addView(kit.text(activity.achievementTitle(definition.id), 17f, Theme.WHITE, 700, Gravity.START).apply {
            maxLines = 2
            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_FULL
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12f) })
        addView(ImageView(activity).apply {
            setImageDrawable(if (definition.tiered) MedalIcon(medalColor(3), true, true, dark = true, ribbon = false) else AchievementCheckIcon())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(28f), dp(28f)))
        contentDescription = activity.getString(R.string.achievement_done_description, activity.achievementTitle(definition.id))
        setOnClickListener { Anim.popIn(icon, 0, 1.15f, 320); Haptics.tick() }
    }

    /** The accent-coloured disc with the achievement's picture on it. */
    private fun badge(id: String, size: Float): ImageView = ImageView(activity).apply {
        tag = "achievement_icon_$id"
        setImageDrawable(achievementIcon(id))
        scaleType = ImageView.ScaleType.FIT_CENTER
        val pad = dp(size * .17f)
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Theme.lighten(accent(id), .72f)) }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Badge, name and what counts (or "Claimed" once it is all paid out), with a check when finished. */
    private fun header(state: Achievements.Snapshot): View = LinearLayout(activity).apply {
        val definition = state.definition
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val complete = state.nextTarget == null
        val best = if (definition.id == "bounces") activity.getString(R.string.achievement_best_bounces, number(state.value)) else null
        val subtitle = when {
            state.allClaimed -> best?.let { activity.getString(R.string.achievement_claimed_with_best, it) } ?: activity.getString(R.string.achievement_claimed)
            definition.id == "bounces" && complete -> best.orEmpty()
            // A finished one-off dare needs no reminder of what it asked.
            !definition.tiered && complete -> ""
            else -> activity.achievementGoal(definition.id)
        }
        addView(badge(definition.id, 54f), LinearLayout.LayoutParams(dp(54f), dp(54f)))
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
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
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12f) })
        if (state.allClaimed || (!definition.tiered && complete)) addView(ImageView(activity).apply {
            setImageDrawable(AchievementCheckIcon())
            contentDescription = activity.getString(if (definition.tiered) R.string.achievement_all_rewards_claimed else R.string.achievement_challenge_complete)
        }, LinearLayout.LayoutParams(dp(30f), dp(30f)).apply { marginStart = dp(8f) })
    }

    /** "1,340 / 2,000" and, at the far end, what reaching it pays. */
    private fun counterLine(state: Achievements.Snapshot, tier: Int): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(kit.text(counter(state, tier), 17f, Theme.INK, 700, Gravity.START).apply {
            maxLines = 1; tag = "achievement-counter"
            // A long count shrinks rather than being cut off.
            setAutoSizeTextTypeUniformWithConfiguration(12, 17, 1, TypedValue.COMPLEX_UNIT_SP)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(reward(state, tier), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8f) })
    }

    /** What the next reward pays: a quiet coin amount, never a button. */
    private fun reward(state: Achievements.Snapshot, tier: Int): View =
        kit.iconText(CoinIcon(), "+${number(Achievements.reward(state.definition, tier))}", 17f, Theme.INK_SOFT, iconDp = 19f).apply {
            tag = "achievement_claim_${state.definition.id}"
            contentDescription = activity.getString(R.string.achievement_reward_locked, number(Achievements.reward(state.definition, tier)))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

    /**
     * Four medals joined by one track. Claimed medals are solid, the one waiting to be claimed
     * glows, the one being worked on wears a ring, later ones are faint outlines. The link into
     * the medal being worked on fills as you get closer.
     */
    private fun medalTrack(state: Achievements.Snapshot, current: Int): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false; clipToPadding = false
        for (tier in 0..3) {
            if (tier > 0) addView(MedalLink(activity, medalColor(tier - 1), medalColor(tier), when {
                tier < state.earnedTiers -> 1f
                tier == state.earnedTiers -> state.fraction
                else -> 0f
            }), LinearLayout.LayoutParams(0, dp(8f), 1f).apply { marginStart = dp(4f); marginEnd = dp(4f) })
            val waiting = tier >= state.claimedTiers && tier < state.earnedTiers
            addView(ImageView(activity).apply {
                tag = "achievement_medal_${state.definition.id}_$tier"
                setImageDrawable(MedalIcon(medalColor(tier), tier < state.earnedTiers, tier == 3, ribbon = false))
                setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
                background = when {
                    waiting -> GradientDrawable().apply {
                        gradientType = GradientDrawable.RADIAL_GRADIENT
                        gradientRadius = dpf(22f)
                        colors = intArrayOf(Theme.alpha(Theme.lighten(medalColor(tier), .3f), 200), Theme.alpha(medalColor(tier), 0))
                    }
                    tier == current && !state.allClaimed -> GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(Theme.alpha(medalColor(tier), 30))
                        setStroke(dp(2f), medalColor(tier))
                    }
                    else -> null
                }
                contentDescription = activity.getString(R.string.achievement_medal_status, activity.achievementTierName(tier), activity.getString(when {
                    tier < state.claimedTiers -> R.string.achievement_status_claimed
                    tier < state.earnedTiers -> R.string.achievement_status_ready
                    tier == current -> R.string.achievement_status_progress
                    else -> R.string.achievement_status_locked
                }))
            }, LinearLayout.LayoutParams(dp(40f), dp(40f)))
        }
    }

    /**
     * "1,340 / 2,000". Good Runner asks you to beat its number, not to reach one more than it:
     * the counter shows the number on the medal, and on the exact boundary says so rather than
     * showing a full-looking 1,000 / 1,000 with no reward behind it.
     */
    private fun counter(state: Achievements.Snapshot, tier: Int, short: Boolean = false): String {
        val definition = state.definition
        val target = definition.thresholds[tier]
        val ready = state.claimableTier != null
        val beat = definition.id == "runner" && !ready && state.value >= target
        // Non-breaking around the slash: a narrow column must never split the count over two lines.
        return if (definition.id == "bounces") activity.getString(R.string.achievement_best_progress, number(state.value), number(target)).replace(" / ", "\u00A0/\u00A0")
            else "${number(if (ready) target else minOf(state.value, target))}\u00A0/\u00A0${(if (short) ::compact else ::number)(if (beat) target + 1 else target)}"
    }

    /** How far along a challenge is. A reward ready to claim shows it full, in mint. */
    private fun bar(state: Achievements.Snapshot, index: Int, animateFill: Boolean): View {
        val ready = state.claimableTier != null
        val fraction = if (ready) 1f else state.fraction
        return AchievementProgressBar(activity, kit, if (ready) Theme.MINT else accent(state.definition.id), fraction,
            if (animateFill) 120L + index * 35L else 0L, animateFill,
            fromFraction = if (!animateFill && !ready) 1f else null, trough = Theme.alpha(Theme.INK, 26)).apply {
            tag = "achievement_progress_${state.definition.id}"
            contentDescription = activity.getString(R.string.achievement_percent_complete, (fraction * 100).toInt())
        }
    }

    /** The wide gold CLAIM button, with what it pays. */
    private fun claimButton(state: Achievements.Snapshot): View {
        val amount = state.rewardAmount ?: Achievements.reward(state.definition, state.claimableTier ?: 0)
        lateinit var button: CandyButton
        button = kit.button(android.text.SpannableStringBuilder(activity.getString(R.string.achievement_claim)).append("  ").append(kit.coins(number(amount), 18f)), Theme.GOLD) {
            onClaim(state, button)
        }.apply {
            tag = "achievement_claim_${state.definition.id}"
            maxLines = 1
            contentDescription = activity.getString(R.string.achievement_claim_description, number(amount), activity.achievementTitle(state.definition.id))
        }
        return button
    }

    /** A white card with a darker lip. A waiting reward gets a gold edge. */
    private fun surface(state: Achievements.Snapshot): Drawable {
        val ready = state.claimableTier != null
        val radius = dpf(RADIUS)
        return LayerDrawable(arrayOf(
            GradientDrawable().apply { cornerRadius = radius; setColor(if (ready) Theme.darken(Theme.GOLD, .2f) else 0xFFB9B2DA.toInt()) },
            GradientDrawable().apply { cornerRadius = radius; setColor(Theme.CARD); if (ready) setStroke(dp(3f), Theme.GOLD) },
        )).apply { setLayerInset(1, 0, 0, 0, kit.CARD_LIP) }
    }

    companion object {
        private const val RADIUS = 22f

        fun accent(id: String): Int = when (id) {
            "runner" -> Theme.ORANGE
            "coins" -> Theme.GOLD
            "cubes" -> Theme.GRAPE
            "powerups" -> Theme.SKY
            "boxes" -> Theme.GRAPE
            "bubbles" -> Theme.CYAN
            "center" -> Theme.MINT
            "homeress" -> Theme.GOLD
            "gambliphobic" -> Theme.LAVENDER
            "cookie" -> Theme.ORANGE
            "globetrotter" -> Theme.SKY
            "long_hauler" -> Theme.ORANGE
            "shardsmith" -> Theme.LAVENDER
            "regular" -> Theme.MINT
            "bubble_popper" -> Theme.CYAN
            "near_miss" -> Theme.BERRY
            "untouchable" -> Theme.LIME
            "house_loses" -> Theme.GOLD
            "voidwalker" -> Theme.GRAPE
            "greedy" -> Theme.YELLOW
            "scenic_route" -> Theme.SKY
            "coal_miner" -> 0xFF8994AD.toInt()
            "magpie" -> Theme.GOLD
            "shard_hunter" -> Theme.LAVENDER
            "full_kit" -> Theme.MINT
            "long_con" -> Theme.ORANGE
            "insomniac" -> 0xFF5268A5.toInt()
            "bankrupt" -> 0xFF8798B8.toInt()
            "exactly_67" -> Theme.BERRY
            "just_browsing" -> Theme.CYAN
            "two_ez" -> Theme.LIME
            "nervous_tic" -> Theme.PINK
            "silent_treatment" -> 0xFF708BB3.toInt()
            "stage_fright" -> Theme.BERRY
            else -> Theme.PINK
        }
    }
}

/**
 * A card that can shine. When a reward is waiting on it, a soft sheen sweeps across it a few
 * times, clipped to its rounded corners, and then it goes quiet.
 */
@SuppressLint("ViewConstructor")
internal class SheenBand(context: Context, val corners: FloatArray) : LinearLayout(context) {
    var shine = false
        set(value) { field = value; setWillNotDraw(!value); invalidate() }
    private var sweeps = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val box = RectF()
    private val seen = android.graphics.Rect()
    /** Set on the first draw with the band actually on screen: a card below the fold still gets its sweeps. */
    private var born = 0L

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        box.set(0f, 0f, w.toFloat(), h.toFloat())
        clip.reset(); clip.addRoundRect(box, corners, Path.Direction.CW)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!shine || width == 0) return
        if (sweeps >= SWEEPS) return
        if (born == 0L) {
            if (!isShown || !getLocalVisibleRect(seen)) return
            born = SystemClock.uptimeMillis() + 260L
        }
        val elapsed = SystemClock.uptimeMillis() - born
        if (elapsed < 0L) { postDelayed({ Anim.repaint(this) }, -elapsed); return }
        sweeps = (elapsed / SWEEP_EVERY).toInt()
        val t = elapsed.mod(SWEEP_EVERY)
        if (t < SWEEP) {
            val p = t / SWEEP.toFloat()
            val x = -width * .5f + width * 2f * (p * p * (3f - 2f * p))
            val half = width * .22f
            paint.shader = LinearGradient(x - half, 0f, x + half, height * .6f,
                intArrayOf(0, Theme.alpha(Theme.WHITE, 110), 0), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            val saved = canvas.save()
            canvas.clipPath(clip)
            canvas.drawRect(box, paint)
            canvas.restoreToCount(saved)
            paint.shader = null
            postOnAnimation { Anim.repaint(this) }
        } else postDelayed({ Anim.repaint(this) }, SWEEP_EVERY - t)
    }

    private companion object {
        const val SWEEP = 900L
        const val SWEEP_EVERY = 3200L
        const val SWEEPS = 3
    }
}

/** The link between two medals: faint when locked, filled (bronze into silver…) as far as you have come. */
private class MedalLink(context: Context, private val from: Int, private val to: Int, private val fill: Float) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.shader = null; paint.color = Theme.alpha(Theme.INK, 26)
        canvas.drawRoundRect(box, r, r, paint)
        if (fill <= 0f) return
        // The track mirrors in Hebrew, so the link has to grow from the same end the medals do.
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val length = maxOf(width * fill.coerceIn(0f, 1f), height.toFloat())
        if (rtl) box.left = width - length else box.right = length
        paint.shader = LinearGradient(if (rtl) width.toFloat() else 0f, 0f, if (rtl) 0f else width.toFloat(), 0f, from, to, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(box, r, r, paint)
        paint.shader = null
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
