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
import cube.run.data.Achievements

/**
 * The achievement page's cards. The five medal families are wide cards: a glossy band (badge,
 * name, what counts), the four medals on one track with each target under its medal, then the
 * next goal and its payout. The one-off challenges are tiles for a two-column [ChallengeGrid].
 * A reward waiting to be claimed lights its card up: a gold edge, a sheen sweeping over the band
 * and a glowing, beating medal. Fully claimed families recede into slate.
 */
@SuppressLint("SetTextI18n")
internal class AchievementCards(
    private val activity: Activity,
    private val kit: UiKit,
    private val onClaim: (Achievements.Snapshot, CandyButton) -> Unit,
) {
    private fun dp(v: Float) = kit.dp(v)
    private fun dpf(v: Float) = kit.dpf(v)

    fun card(state: Achievements.Snapshot, index: Int, animateFill: Boolean = true): View =
        if (state.definition.tiered) medalCard(state, index, animateFill) else challengeTile(state, index, animateFill)

    private fun medalCard(state: Achievements.Snapshot, index: Int, animateFill: Boolean): View {
        val definition = state.definition
        val tier = state.claimableTier ?: state.earnedTiers.coerceAtMost(definition.thresholds.lastIndex)
        return LinearLayout(activity).apply {
            tag = "achievement_card_${definition.id}"
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            background = surface(state)
            addView(band(state, tile = false), LinearLayout.LayoutParams(-1, -2))
            val body = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = false; clipToPadding = false
                setPadding(dp(14f), dp(12f), dp(14f), dp(12f) + kit.CARD_LIP)
            }
            addView(body, LinearLayout.LayoutParams(-1, -2))
            val track = medalTrack(state, tier)
            body.addView(track, LinearLayout.LayoutParams(-1, dp(44f)))
            body.addView(TierLabels(activity, kit, track, definition.thresholds.map(::compact), IntArray(4) {
                when {
                    it < state.earnedTiers -> Theme.lighten(medalColor(it), .25f)
                    it == state.earnedTiers -> Theme.WHITE
                    else -> Theme.alpha(Theme.WHITE, 110)
                }
            }), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2f) })
            // Once every medal is collected the track says it all: no dead reward row.
            if (state.allClaimed) return@apply
            body.addView(rewardRow(state, index, animateFill, tier), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })
        }
    }

    /** A challenge: badge and name on the band, then how far along it is and what it pays. */
    private fun challengeTile(state: Achievements.Snapshot, index: Int, animateFill: Boolean): View {
        val definition = state.definition
        val complete = state.nextTarget == null
        return LinearLayout(activity).apply {
            tag = "achievement_card_${definition.id}"
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            background = surface(state)
            // The band takes any height the row gives the tile, so neighbouring tiles line up their goals.
            addView(band(state, tile = true), LinearLayout.LayoutParams(-1, 0, 1f))
            if (state.allClaimed) return@apply
            val body = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = false; clipToPadding = false
                setPadding(dp(12f), dp(10f), dp(12f), dp(12f) + kit.CARD_LIP)
            }
            addView(body, LinearLayout.LayoutParams(-1, -2))
            if (complete) body.addView(kit.stageText("Complete", 16f, Theme.MINT, stroke = 2f).apply { maxLines = 1 },
                LinearLayout.LayoutParams(-1, -2))
            else body.addView(goal(state, index, animateFill, 0, accent(definition.id)), LinearLayout.LayoutParams(-1, -2))
            body.addView(rewardAction(state, wide = true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })
        }
    }

    private fun band(state: Achievements.Snapshot, tile: Boolean): LinearLayout {
        val definition = state.definition
        val accent = accent(definition.id)
        val bright = Theme.lighten(accent, if (accent == Theme.GRAPE) .26f else .16f)
        val color = if (state.allClaimed) Theme.lerp(bright, 0xFF3F4D70.toInt(), .55f) else bright
        val ink = if (state.allClaimed) Theme.WHITE else Theme.onColor(color)
        val complete = state.nextTarget == null
        val closed = state.allClaimed && !definition.tiered
        val best = if (definition.id == "bounces") "Best: ${number(state.value)} bounces" else null
        val subtitle = if (state.allClaimed) best?.let { "Claimed · $it" } ?: "Claimed" else when (definition.id) {
            "runner" -> "Single-run score"
            "coins" -> "Collected over time"
            "cubes" -> "Cube collection"
            "powerups" -> "Power Ups collected"
            "boxes" -> "Mystery boxes opened"
            "bubbles" -> if (complete) "" else "Hold 1,000 bubbles at once"
            "bounces" -> if (complete) best.orEmpty() else "67 wall bounces in one run"
            "center" -> if (complete) "" else "Reach 100 without leaving the middle lane"
            "homeress" -> if (complete) "" else "Score 60 without picking up a coin"
            "gambliphobic" -> if (complete) "" else "Miss 10 mystery boxes in one run"
            "cookie" -> if (complete) "" else "Toggle sound 1,000 times"
            else -> definition.description
        }
        val radius = dpf(20f)
        val badge = ImageView(activity).apply {
            tag = "achievement_icon_${definition.id}"
            setImageDrawable(achievementIcon(definition.id))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Theme.alpha(Theme.WHITE, 96)) }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (state.allClaimed) alpha = .8f
        }
        val check = if (state.allClaimed || (!definition.tiered && complete)) ImageView(activity).apply {
            setImageDrawable(AchievementCheckIcon())
            contentDescription = if (definition.tiered) "All rewards claimed" else "Challenge complete"
        } else null
        val title = kit.text(definition.title, if (tile) 16f else 19f, ink, 700, Gravity.START).apply { maxLines = 2 }
        val sub = if (subtitle.isEmpty()) null else kit.text(subtitle, 11f, Theme.alpha(ink, 220), 500, Gravity.START).apply {
            maxLines = if (tile) 4 else 2
            tag = if (best != null && complete) "achievement_best_${definition.id}" else "achievement_subtitle_${definition.id}"
        }
        return SheenBand(activity, if (closed) floatArrayOf(radius, radius, radius, radius, radius, radius, radius, radius)
            else floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)).apply {
            shine = state.claimableTier != null
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Theme.lighten(color, .14f), color)).apply { cornerRadii = corners }
            if (tile) {
                orientation = LinearLayout.VERTICAL
                gravity = if (closed) Gravity.CENTER_VERTICAL else Gravity.TOP
                setPadding(dp(12f), dp(12f), dp(12f), dp(12f) + if (closed) kit.CARD_LIP else 0)
                addView(FrameLayout(activity).apply {
                    addView(badge, FrameLayout.LayoutParams(dp(46f), dp(46f), Gravity.START or Gravity.CENTER_VERTICAL))
                    check?.let { addView(it, FrameLayout.LayoutParams(dp(28f), dp(28f), Gravity.END or Gravity.CENTER_VERTICAL)) }
                }, LinearLayout.LayoutParams(-1, -2))
                addView(title, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })
                sub?.let { addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3f) }) }
            } else {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
                addView(badge, LinearLayout.LayoutParams(dp(50f), dp(50f)))
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(title)
                    sub?.let { addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4f) }) }
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12f) })
                check?.let { addView(it, LinearLayout.LayoutParams(dp(30f), dp(30f)).apply { marginStart = dp(6f) }) }
            }
        }
    }

    /**
     * Four medals joined by one track. Claimed medals are solid, the one waiting to be claimed
     * glows and beats, the one being worked on wears a ring, later ones are faint. The link into
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
            }), LinearLayout.LayoutParams(0, dp(6f), 1f).apply { marginStart = dp(4f); marginEnd = dp(4f) })
            val waiting = tier >= state.claimedTiers && tier < state.earnedTiers
            addView(ImageView(activity).apply {
                tag = "achievement_medal_${state.definition.id}_$tier"
                setImageDrawable(MedalIcon(medalColor(tier), tier < state.earnedTiers, tier == 3, dark = true, ribbon = false))
                setPadding(dp(5f), dp(5f), dp(5f), dp(5f))
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
                if (tier == state.claimableTier) Anim.cancelOnDetach(this, Anim.heartbeat(this, 1.12f, 1000))
                contentDescription = "${medalName(tier)}: ${when {
                    tier < state.claimedTiers -> "claimed"
                    tier < state.earnedTiers -> "reward ready"
                    tier == current -> "in progress"
                    else -> "locked"
                }}"
            }, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        }
    }

    private fun rewardRow(state: Achievements.Snapshot, index: Int, animateFill: Boolean, tier: Int): View =
        AchievementRewardRow(activity, kit).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            addView(goal(state, index, animateFill, tier, medalColor(tier)), LinearLayout.LayoutParams(0, -2, 1f))
            addView(rewardAction(state, wide = false), LinearLayout.LayoutParams(-2, -2))
        }

    /** "1,340 / 2,000" over its bar. A reward ready to claim shows its target reached, in mint. */
    private fun goal(state: Achievements.Snapshot, index: Int, animateFill: Boolean, tier: Int, color: Int): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val definition = state.definition
            val target = definition.thresholds[tier] + if (definition.id == "runner") 1 else 0
            val ready = state.claimableTier != null
            val fraction = if (ready) 1f else state.fraction
            // Good Runner means beating its number; once it is beaten, show the number it beat.
            val shown = if (ready) definition.thresholds[tier] else target
            val counter = if (definition.id == "bounces") "Best: ${number(state.value)} / ${number(target)}"
                else "${number(if (ready) shown else minOf(state.value, target))} / ${number(shown)}"
            addView(kit.stageText(counter, 14f, if (ready) Theme.MINT else Theme.WHITE, gravity = Gravity.START, stroke = 1.5f)
                .apply { maxLines = 1; tag = "achievement-counter" })
            addView(AchievementProgressBar(activity, kit, if (ready) Theme.MINT else color, fraction,
                if (animateFill) 120L + index * 35L else 0L, animateFill,
                fromFraction = if (!animateFill && !ready) 1f else null).apply {
                tag = "achievement_progress_${definition.id}"
                contentDescription = "${(fraction * 100).toInt()} percent complete"
            }, LinearLayout.LayoutParams(-1, dp(10f)).apply { topMargin = dp(3f); bottomMargin = dp(3f) })
        }

    /** The bright gold CLAIM button when a reward is waiting, otherwise just what the next one pays. */
    private fun rewardAction(state: Achievements.Snapshot, wide: Boolean): View {
        val amount = state.rewardAmount ?: Achievements.reward(state.definition, state.earnedTiers.coerceAtMost(state.definition.thresholds.lastIndex))
        if (state.claimableTier == null) return LinearLayout(activity).apply {
            tag = "achievement_claim_${state.definition.id}"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumWidth = if (wide) 0 else dp(112f); minimumHeight = dp(44f)
            setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
            addView(kit.iconText(CoinIcon(), "+${number(amount)}", 16f, Theme.YELLOW, iconDp = 20f).apply { gravity = Gravity.CENTER })
            contentDescription = "Reward: ${number(amount)} coins, locked"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        lateinit var button: CandyButton
        button = kit.button(android.text.SpannableStringBuilder("CLAIM ").append(kit.coins(number(amount), 13f)), Theme.GOLD, UiKit.Size.SMALL) {
            onClaim(state, button)
        }.apply {
            tag = "achievement_claim_${state.definition.id}"
            minimumHeight = dp(46f)
            minimumWidth = if (wide) 0 else dp(120f)
            textSize = 13f
            maxLines = 1
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
            contentDescription = "Claim ${number(amount)} coins for ${state.definition.title}"
        }
        return button
    }

    /** A card face with a darker lip under it. Waiting rewards get a gold edge; claimed ones fade back. */
    private fun surface(state: Achievements.Snapshot): Drawable {
        val accent = accent(state.definition.id)
        val fill = if (state.allClaimed) 0xFF2C3654.toInt() else Theme.lerp(0xFF303C68.toInt(), accent, .1f)
        val ready = state.claimableTier != null
        val edge = when {
            ready -> Theme.GOLD
            state.allClaimed -> Theme.alpha(Theme.WHITE, 36)
            else -> Theme.alpha(Theme.WHITE, 70)
        }
        val radius = dpf(20f)
        return LayerDrawable(arrayOf(
            GradientDrawable().apply { cornerRadius = radius; setColor(if (ready) Theme.darken(Theme.GOLD, .35f) else Theme.darken(fill, .3f)) },
            GradientDrawable().apply { cornerRadius = radius; setColor(fill); setStroke(dp(if (ready) 2f else 1f), edge) },
        )).apply { setLayerInset(1, 0, 0, 0, kit.CARD_LIP) }
    }

    companion object {
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
            else -> Theme.PINK
        }
    }
}

/**
 * The challenge tiles, two to a row when they fit (one per row on narrow screens or with large
 * text). Tiles in a row share the taller one's height.
 */
internal class ChallengeGrid(context: Context, private val kit: UiKit) : ViewGroup(context) {
    private val gap = kit.dp(12f)
    private var columns = 2

    init { clipChildren = false; clipToPadding = false }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val scale = kit.ctx.resources.configuration.fontScale
        columns = if (width - paddingLeft - paddingRight >= kit.dp(310f) && scale <= 1.15f) 2 else 1
        val column = (width - paddingLeft - paddingRight - gap * (columns - 1)) / columns
        val exactColumn = MeasureSpec.makeMeasureSpec(column, MeasureSpec.EXACTLY)
        var height = paddingTop + paddingBottom
        var i = 0
        while (i < childCount) {
            val row = (i until minOf(i + columns, childCount)).map(::getChildAt)
            row.forEach { it.measure(exactColumn, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)) }
            val tallest = row.maxOf { it.measuredHeight }
            row.forEach { it.measure(exactColumn, MeasureSpec.makeMeasureSpec(tallest, MeasureSpec.EXACTLY)) }
            height += tallest + if (i > 0) gap else 0
            i += columns
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        var y = paddingTop
        var i = 0
        while (i < childCount) {
            var tallest = 0
            for (c in 0 until columns) {
                val child = getChildAt(i + c) ?: break
                val slot = if (rtl) columns - 1 - c else c
                val x = paddingLeft + slot * (child.measuredWidth + gap)
                child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
                tallest = maxOf(tallest, child.measuredHeight)
            }
            y += tallest + gap
            i += columns
        }
    }
}

/**
 * A card's coloured band. While a reward waits on its card, a soft sheen sweeps across it every
 * few seconds, clipped to the band's rounded corners.
 */
@SuppressLint("ViewConstructor")
internal class SheenBand(context: Context, val corners: FloatArray) : LinearLayout(context) {
    var shine = false
        set(value) { field = value; setWillNotDraw(!value); invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val box = RectF()
    private val born = SystemClock.uptimeMillis() + (Math.random() * 900).toLong()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        box.set(0f, 0f, w.toFloat(), h.toFloat())
        clip.reset(); clip.addRoundRect(box, corners, Path.Direction.CW)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!shine || width == 0) return
        val t = (SystemClock.uptimeMillis() - born).mod(SWEEP_EVERY)
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
    }
}

/** The link between two medals: faint when locked, filled (bronze into silver…) as far as you have come. */
private class MedalLink(context: Context, private val from: Int, private val to: Int, private val fill: Float) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.shader = null; paint.color = Theme.alpha(Theme.WHITE, 56)
        canvas.drawRoundRect(box, r, r, paint)
        if (fill <= 0f) return
        box.right = maxOf(width * fill.coerceIn(0f, 1f), height.toFloat())
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, from, to, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(box, r, r, paint)
        paint.shader = null
    }
}

/** Each medal's target, centred under it: 500, 1K, 25K… */
@SuppressLint("ViewConstructor")
private class TierLabels(context: Context, kit: UiKit, private val track: ViewGroup,
    private val labels: List<String>, private val colors: IntArray) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.get(kit.ctx, 700)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, kit.ctx.resources.displayMetrics)
        textAlign = Paint.Align.CENTER
    }

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val metrics = paint.fontMetrics
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (metrics.descent - metrics.ascent + 1f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val medals = (0 until track.childCount).map(track::getChildAt).filterIsInstance<ImageView>()
        val baseline = -paint.fontMetrics.ascent
        for ((i, medal) in medals.withIndex()) {
            val label = labels.getOrNull(i) ?: continue
            val half = paint.measureText(label) / 2f
            val x = (track.left - left + medal.left + medal.width / 2f).coerceIn(half, width - half)
            paint.color = colors[i]
            canvas.drawText(label, x, baseline, paint)
        }
    }
}

/** Measure the actual text, including font scaling, instead of guessing from device configuration. */
@SuppressLint("ViewConstructor")
internal class AchievementRewardRow(context: Context, private val kit: UiKit) : LinearLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (childCount == 2) {
            val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            val goal = getChildAt(0)
            val reward = getChildAt(1)
            goal.measure(free, free)
            reward.measure(free, free)
            val stack = goal.measuredWidth + reward.measuredWidth + kit.dp(10f) > available
            orientation = if (stack) VERTICAL else HORIZONTAL
            (goal.layoutParams as LayoutParams).apply {
                width = if (stack) LayoutParams.MATCH_PARENT else 0
                weight = if (stack) 0f else 1f
            }
            (reward.layoutParams as LayoutParams).apply {
                width = if (stack) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT
                marginStart = if (stack) 0 else kit.dp(10f)
                topMargin = if (stack) kit.dp(8f) else 0
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

/** Start the fill when it first enters the viewport, including cards reached by scrolling. */
@SuppressLint("ViewConstructor")
internal class AchievementProgressBar(context: Context, private val kit: UiKit, private val color: Int,
    private val target: Float, private val delay: Long, animateFill: Boolean = true, fromFraction: Float? = null) : View(context) {
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
        paint.color = Theme.alpha(Theme.WHITE, 50); canvas.drawRoundRect(rect, radius, radius, paint)
        if (amount > 0f) {
            rect.right = maxOf(width * amount, height.toFloat())
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
