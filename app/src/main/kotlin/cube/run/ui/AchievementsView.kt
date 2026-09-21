package cube.run.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.Layout
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import cube.run.data.Achievements
import cube.run.data.Progress
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.R
import java.text.NumberFormat

/** A small collection of evolving goals, using the game's ink and candy colours. */
@SuppressLint("ViewConstructor", "SetTextI18n")
class AchievementsView(activity: Activity, kit: UiKit, onClose: () -> Unit) :
    Page(activity, kit, activity.getString(R.string.achievements_title), dark = true, onClosed = onClose) {
    private val rows = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14f), dp(8f), dp(14f), dp(28f))
        clipToPadding = false
    }
    private val bank = kit.iconPill(CoinIcon(), number(Progress.coins), Theme.INK, 15f)
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
        addView(rows)
    }
    private var paying = false
    private var bankCount: ValueAnimator? = null

    init {
        // An opaque, restrained backdrop gives the medals a stable field of colour.
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF29324F.toInt(), 0xFF1C2A40.toInt(), 0xFF142D37.toInt()))
        titleView.maxLines = 1
        titleView.setAutoSizeTextTypeUniformWithConfiguration(12, 23, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        addRight(bank)
        content.addView(scroll, LayoutParams(-1, -1))
        for ((index, definition) in Achievements.all.withIndex()) {
            rows.addView(achievementCard(Achievements.snapshot(definition), index),
                LinearLayout.LayoutParams(-1, -2).apply { if (index > 0) topMargin = dp(12f) })
        }
        rows.addView(suggestionCard(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18f) })
    }

    private fun achievementCard(state: Achievements.Snapshot, index: Int, animateFill: Boolean = true): View {
        val definition = state.definition
        val complete = state.nextTarget == null
        val tier = state.claimableTier ?: state.earnedTiers.coerceAtMost(definition.thresholds.lastIndex)
        val accent = when (definition.id) {
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
        val headerColor = Theme.lighten(accent, if (accent == Theme.GRAPE) .26f else .16f)
        val headerInk = Theme.onColor(headerColor)
        val bodyColor = if (state.allClaimed) 0xFF244F4B.toInt() else Theme.lerp(0xFF34465E.toInt(), accent, .08f)
        val best = when (definition.id) {
            "bounces" -> activity.getString(R.string.achievement_best_bounces, number(state.value))
            else -> null
        }
        val subtitle = if (state.allClaimed) best?.let { activity.getString(R.string.achievement_claimed_with_best, it) }
            ?: activity.getString(R.string.achievement_claimed)
        else if (complete && !definition.tiered) best.orEmpty() else activity.achievementGoal(definition.id)
        return LinearLayout(activity).apply {
            tag = "achievement_card_${definition.id}"
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            background = achievementSurface(kit, bodyColor, when {
                state.allClaimed -> Theme.alpha(Theme.MINT, 150)
                state.claimableTier != null -> Theme.GOLD
                else -> Theme.alpha(Theme.WHITE, 90)
            }, 22f)
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
                background = GradientDrawable().apply {
                    setColor(headerColor)
                    cornerRadii = floatArrayOf(dpf(22f), dpf(22f), dpf(22f), dpf(22f), 0f, 0f, 0f, 0f)
                    if (state.allClaimed && !definition.tiered) cornerRadius = dpf(22f)
                }
                addView(ImageView(activity).apply {
                    tag = "achievement_icon_${definition.id}"
                    setImageDrawable(achievementIcon(definition.id))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(Theme.alpha(Theme.WHITE, 88))
                    }
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(48f), dp(48f)))
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(kit.text(activity.achievementTitle(definition.id), 19f, headerInk, 700, Gravity.START).apply { maxLines = 2 })
                    if (subtitle.isNotEmpty()) addView(kit.text(subtitle, 11f, headerInk, 500, Gravity.START).apply {
                        maxLines = if (definition.id in setOf("center", "homeress", "gambliphobic", "cookie")) 3 else 2
                        tag = if (best != null && complete) "achievement_best_${definition.id}"
                            else "achievement_subtitle_${definition.id}"
                    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4f) })
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12f) })
                if (state.allClaimed || (!definition.tiered && complete)) addView(ImageView(activity).apply {
                    setImageDrawable(AchievementCheckIcon())
                    contentDescription = activity.getString(if (definition.tiered) R.string.achievement_all_rewards_claimed else R.string.achievement_challenge_complete)
                }, LinearLayout.LayoutParams(dp(30f), dp(30f)).apply { leftMargin = dp(5f) })
            }, LinearLayout.LayoutParams(-1, -2))

            val body = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = false; clipToPadding = false
                setPadding(dp(16f), dp(10f), dp(16f), dp(12f) + kit.CARD_LIP)
            }
            addView(body, LinearLayout.LayoutParams(-1, -2))
            if (definition.tiered) body.addView(medalTrack(state, tier), LinearLayout.LayoutParams(-1, dp(40f)).apply {
                bottomMargin = dp(if (state.allClaimed) 0f else 6f)
            })

            // Once collected, the completed badge and its medals carry the status.
            // Remove the empty action area instead of leaving a dead button-sized gap.
            if (state.allClaimed) {
                if (!definition.tiered) body.visibility = GONE
                return@apply
            }

            val bottom = AchievementRewardRow(activity, kit).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false; clipToPadding = false
            }
            if (definition.tiered || !complete) {
                val goal = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    val target = definition.thresholds[tier] + if (definition.id == "runner") 1 else 0
                    val ready = state.claimableTier != null
                    val fraction = if (ready) 1f else state.fraction
                    val counter = if (definition.id == "bounces") activity.getString(R.string.achievement_best_progress, number(state.value), number(target))
                        else "${number(if (ready) target else minOf(state.value, target))} / ${number(target)}"
                    addView(kit.stageText(counter, 14f, if (ready) Theme.MINT else Theme.WHITE,
                        gravity = Gravity.START, stroke = 1.5f).apply { maxLines = 1; tag = "achievement-counter" })
                    addView(AchievementProgressBar(activity, kit, if (ready) Theme.MINT else if (definition.tiered) medalColor(tier) else accent, fraction,
                        if (animateFill) 100L + index * 35L else 0L, animateFill,
                        fromFraction = if (!animateFill && !ready) 1f else null).apply {
                        tag = "achievement_progress_${definition.id}"
                        contentDescription = activity.getString(R.string.achievement_percent_complete, (fraction * 100).toInt())
                    }, LinearLayout.LayoutParams(-1, dp(9f)).apply { topMargin = dp(2f); bottomMargin = dp(3f) })
                }
                bottom.addView(goal, LinearLayout.LayoutParams(0, -2, 1f))
            } else {
                bottom.addView(kit.stageText(activity.getString(R.string.achievement_complete), 17f, Theme.MINT, gravity = Gravity.START, stroke = 2f),
                    LinearLayout.LayoutParams(0, -2, 1f))
            }
            bottom.addView(rewardAction(state, index), LinearLayout.LayoutParams(-2, -2))
            body.addView(bottom, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(if (definition.tiered) 2f else 0f) })
        }
    }

    /** The current tier gets a bright ring; earned medals are solid, later medals are faint outlines. */
    private fun medalTrack(state: Achievements.Snapshot, current: Int): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        for (tier in 0..3) {
            if (tier > 0) addView(View(activity).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dpf(1.5f)
                    setColor(if (tier < state.earnedTiers) medalColor(tier - 1) else Theme.alpha(Theme.WHITE, 64))
                }
            }, LinearLayout.LayoutParams(0, dp(3f), 1f).apply { leftMargin = dp(5f); rightMargin = dp(5f) })
            addView(ImageView(activity).apply {
                tag = "achievement_medal_${state.definition.id}_$tier"
                setImageDrawable(MedalIcon(medalColor(tier), tier < state.earnedTiers, tier == 3, dark = true, ribbon = false))
                setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
                if (tier == current && !state.allClaimed) background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(Theme.alpha(medalColor(tier), 25))
                    setStroke(dp(1.5f), medalColor(tier))
                }
                val status = activity.getString(when {
                    tier < state.claimedTiers -> R.string.achievement_status_claimed
                    tier < state.earnedTiers -> R.string.achievement_status_ready
                    tier == current -> R.string.achievement_status_progress
                    else -> R.string.achievement_status_locked
                })
                contentDescription = activity.getString(R.string.achievement_medal_status, activity.achievementTierName(tier), status)
            }, LinearLayout.LayoutParams(dp(40f), dp(40f)))
        }
    }

    private fun rewardAction(state: Achievements.Snapshot, index: Int): View {
        val amount = state.rewardAmount ?: Achievements.reward(state.definition, state.earnedTiers.coerceAtMost(state.definition.thresholds.lastIndex))
        val available = state.claimableTier != null
        if (!available) return LinearLayout(activity).apply {
            tag = "achievement_claim_${state.definition.id}"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumWidth = dp(120f); minimumHeight = dp(46f)
            setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
            addView(kit.iconText(CoinIcon(), "+${number(amount)}", 16f, Theme.YELLOW, iconDp = 20f).apply { gravity = Gravity.CENTER })
            contentDescription = activity.getString(R.string.achievement_reward_locked, number(amount))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        val label = android.text.SpannableStringBuilder(activity.getString(R.string.achievement_claim)).append(" ").append(kit.coins(number(amount), 13f))
        lateinit var button: CandyButton
        button = kit.button(label, Theme.GOLD, UiKit.Size.SMALL) {
            if (paying || closing) return@button
            val before = Progress.coins
            val paid = Achievements.claim(state.definition.id)
            if (paid <= 0) return@button
            paying = true
            button.isEnabled = false
            val ms = PayFx.fly(this, kit, button, bank, n = 5, onDone = {
                if (closing || !isAttachedToWindow) return@fly
                val old = rows.getChildAt(index)
                val params = old.layoutParams
                val heldScroll = scroll.scrollY
                rows.removeViewAt(index)
                val fresh = achievementCard(Achievements.snapshot(state.definition), index, animateFill = false)
                rows.addView(fresh, index, params)
                scroll.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        scroll.viewTreeObserver.removeOnPreDrawListener(this)
                        val viewport = scroll.height - scroll.paddingTop - scroll.paddingBottom
                        val range = ((scroll.getChildAt(0)?.height ?: 0) - viewport).coerceAtLeast(0)
                        scroll.scrollTo(0, heldScroll.coerceIn(0, range))
                        paying = false
                        return true
                    }
                })
                PayFx.flash(fresh, dpf(24f), pulse = false)
                Haptics.success()
                SoundFx.play("success", rate = 1.2f, vol = .5f)
                postOnAnimation { Anim.repaint(this) }
            })
            bankCount?.cancel()
            bankCount = Anim.countTo(kit.labelOf(bank), before, Progress.coins, ms, ::number)
        }.apply {
            tag = "achievement_claim_${state.definition.id}"
            minimumHeight = dp(46f)
            minimumWidth = dp(120f)
            textSize = 13f
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
            contentDescription = activity.getString(R.string.achievement_claim_description, number(amount), activity.achievementTitle(state.definition.id))
        }
        return button
    }

    private fun suggestionCard(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(18f), dp(18f), dp(18f), dp(18f) + kit.CARD_LIP)
        background = achievementSurface(kit, 0xFF263950.toInt(), Theme.GOLD, 22f)
        addView(kit.stageText(activity.getString(R.string.achievement_suggest_title), 20f, Theme.WHITE, gravity = Gravity.CENTER, stroke = 2f))
        addView(kit.stageText(activity.getString(R.string.achievement_suggest_body), 13f, Theme.WHITE, gravity = Gravity.CENTER, stroke = 1.4f).apply {
            alpha = .84f
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7f) })
        addView(kit.button(activity.getString(R.string.achievement_suggest_button), Theme.GOLD, UiKit.Size.SMALL) {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SUGGEST_URL)))
        }.apply { contentDescription = activity.getString(R.string.achievement_suggest_button) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14f) })
    }

    private companion object {
        const val SUGGEST_URL = "https://apps.muxu.click/d/6xn8cb36"
    }

    override fun onDetachedFromWindow() {
        bankCount?.cancel()
        super.onDetachedFromWindow()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        if (paying) true else super.dispatchTouchEvent(event)

    override fun onBack() { if (!paying) close() }
}

/** Measure the actual text, including font scaling, instead of guessing from device configuration. */
@SuppressLint("ViewConstructor")
private class AchievementRewardRow(context: Context, private val kit: UiKit) : LinearLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (childCount == 2) {
            val available = View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            val free = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
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
                leftMargin = if (stack) 0 else kit.dp(10f)
                topMargin = if (stack) kit.dp(8f) else 0
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

internal fun achievementIcon(id: String): android.graphics.drawable.Drawable = when (id) {
    "coins" -> CoinIcon()
    "powerups" -> JetIcon(Theme.SKY)
    "boxes" -> BoxIcon()
    "bubbles" -> BubbleIcon()
    "cubes" -> AchievementCubeIcon(collection = true)
    "bounces" -> AchievementWallIcon()
    "center" -> AchievementCenterIcon()
    "homeress" -> AchievementCoinlessIcon()
    "gambliphobic" -> AchievementBoxAvoidanceIcon()
    "cookie" -> AchievementCookieIcon()
    else -> AchievementCubeIcon(collection = false)
}

/** A quieter version of the common raised card, with a one-pixel-scale edge. */
private fun achievementSurface(kit: UiKit, fill: Int, edge: Int, radius: Float): android.graphics.drawable.Drawable =
    android.graphics.drawable.LayerDrawable(arrayOf(
        GradientDrawable().apply { cornerRadius = kit.dpf(radius); setColor(Theme.darken(fill, .22f)) },
        GradientDrawable().apply {
            cornerRadius = kit.dpf(radius); setColor(fill)
            setStroke(kit.dp(1f), edge)
        },
    )).apply { setLayerInset(1, 0, 0, 0, kit.CARD_LIP) }

internal class AchievementCheckIcon : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 32f, bounds.height() / 32f)
        paint.style = Paint.Style.FILL; paint.color = Theme.MINT
        canvas.drawCircle(16f, 16f, 15f, paint)
        paint.style = Paint.Style.STROKE; paint.color = Theme.INK
        paint.strokeWidth = 3.4f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        path.reset(); path.moveTo(9f, 16f); path.lineTo(14f, 21f); path.lineTo(23f, 11f)
        canvas.drawPath(path, paint)
        canvas.restoreToCount(save)
    }
}

/** Isometric cubes reuse the game's bevelled candy language, with speed lines for running. */
private class AchievementCubeIcon(private val collection: Boolean) : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 48f, bounds.height() / 48f)
        fun cube(x: Float, y: Float, size: Float, color: Int) {
            paint.style = Paint.Style.FILL
            fun face(c: Int, vararg points: Float) {
                path.reset(); path.moveTo(points[0], points[1])
                for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
                path.close(); paint.color = c; canvas.drawPath(path, paint)
            }
            val half = size / 2f; val rise = size / 4f
            face(Theme.lighten(color, .4f), x, y + rise, x + half, y, x + size, y + rise, x + half, y + 2 * rise)
            face(color, x, y + rise, x + half, y + 2 * rise, x + half, y + size, x, y + size - rise)
            face(Theme.darken(color, .2f), x + half, y + 2 * rise, x + size, y + rise, x + size, y + size - rise, x + half, y + size)
        }
        if (collection) {
            cube(1f, 21f, 24f, Theme.LAVENDER)
            cube(23f, 21f, 24f, Theme.GRAPE)
            cube(12f, 3f, 24f, Theme.SKY)
        } else {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.5f; paint.strokeCap = Paint.Cap.ROUND
            paint.color = Theme.WHITE
            canvas.drawLine(3f, 20f, 12f, 20f, paint); canvas.drawLine(6f, 29f, 10f, 29f, paint); canvas.drawLine(3f, 38f, 9f, 38f, paint)
            cube(13f, 7f, 34f, Theme.ORANGE)
        }
        canvas.restoreToCount(save)
    }
}

/** Three straight lanes with the cube held in the centre; every edge shares a common centre. */
private class AchievementCenterIcon : Icon() {
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 48f
        canvas.translate(bounds.exactCenterX() - 24f * scale, bounds.exactCenterY() - 24f * scale)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL
        paint.color = Theme.darken(Theme.MINT, .25f)
        rect.set(17f, 3f, 31f, 45f); canvas.drawRoundRect(rect, 4f, 4f, paint)
        paint.color = Theme.WHITE
        rect.set(4f, 3f, 7f, 45f); canvas.drawRoundRect(rect, 1.5f, 1.5f, paint)
        rect.set(41f, 3f, 44f, 45f); canvas.drawRoundRect(rect, 1.5f, 1.5f, paint)
        rect.set(14f, 14f, 34f, 34f); canvas.drawRoundRect(rect, 4f, 4f, paint)
        paint.color = Theme.MINT
        rect.set(18f, 18f, 30f, 30f); canvas.drawRoundRect(rect, 2f, 2f, paint)
        canvas.restoreToCount(save)
    }
}

/** A crossed coin: the coin silhouette and diagonal share the same visual centre. */
private class AchievementCoinlessIcon : Icon() {
    private val coin = CoinIcon()
    override fun draw(canvas: Canvas) {
        val saved = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 48f
        canvas.translate(bounds.exactCenterX() - 24f * scale, bounds.exactCenterY() - 24f * scale)
        canvas.scale(scale, scale)
        coin.setBounds(4, 4, 44, 44); coin.draw(canvas)
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 7f; paint.color = Theme.INK
        canvas.drawLine(8f, 40f, 40f, 8f, paint)
        paint.strokeWidth = 3.5f; paint.color = Theme.WHITE
        canvas.drawLine(8f, 40f, 40f, 8f, paint)
        canvas.restoreToCount(saved)
    }
}

/** A gift left untouched while the route circles around it. */
private class AchievementBoxAvoidanceIcon : Icon() {
    private val box = BoxIcon()
    private val arrow = Path()
    override fun draw(canvas: Canvas) {
        val saved = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 48f
        canvas.translate(bounds.exactCenterX() - 24f * scale, bounds.exactCenterY() - 24f * scale)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
        paint.color = Theme.WHITE; paint.strokeCap = Paint.Cap.ROUND
        canvas.drawCircle(24f, 24f, 20f, paint)
        box.setBounds(13, 13, 35, 35); box.draw(canvas)
        for (side in 0..1) {
            if (side == 1) canvas.rotate(180f, 24f, 24f)
            paint.style = Paint.Style.FILL; paint.color = Theme.INK
            arrow.reset(); arrow.moveTo(29f, 2f); arrow.lineTo(38f, 6f); arrow.lineTo(30f, 12f); arrow.close()
            canvas.drawPath(arrow, paint)
            paint.color = Theme.WHITE
            arrow.reset(); arrow.moveTo(31f, 4f); arrow.lineTo(35f, 6f); arrow.lineTo(31f, 9f); arrow.close()
            canvas.drawPath(arrow, paint)
        }
        canvas.restoreToCount(saved)
    }
}

/** A chunky chocolate-chip cookie and a small pointer, with no font glyph dependencies. */
private class AchievementCookieIcon : Icon() {
    private val pointer = Path()
    override fun draw(canvas: Canvas) {
        val saved = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 48f
        canvas.translate(bounds.exactCenterX() - 24f * scale, bounds.exactCenterY() - 24f * scale)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL; paint.color = 0xFF9D552D.toInt()
        canvas.drawCircle(24f, 24f, 21f, paint)
        paint.color = 0xFFF2BE75.toInt(); canvas.drawCircle(24f, 24f, 18f, paint)
        paint.color = 0xFF74422C.toInt()
        for ((x, y) in arrayOf(13f to 15f, 28f to 11f, 35f to 21f, 12f to 29f, 21f to 36f))
            canvas.drawCircle(x, y, 2.7f, paint)
        pointer.reset(); pointer.moveTo(21f, 19f); pointer.lineTo(37f, 29f)
        pointer.lineTo(30f, 31f); pointer.lineTo(27f, 39f); pointer.close()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.strokeJoin = Paint.Join.ROUND
        paint.color = Theme.INK; canvas.drawPath(pointer, paint)
        paint.style = Paint.Style.FILL; paint.color = Theme.WHITE; canvas.drawPath(pointer, paint)
        canvas.restoreToCount(saved)
    }
}

private class AchievementWallIcon : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 48f, bounds.height() / 48f)
        paint.style = Paint.Style.FILL; paint.color = Theme.PINK
        rect.set(36f, 3f, 44f, 45f); canvas.drawRoundRect(rect, 3f, 3f, paint)
        paint.style = Paint.Style.STROKE; paint.color = Theme.WHITE
        paint.strokeWidth = 4f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        path.reset(); path.moveTo(6f, 7f); path.lineTo(31f, 24f); path.lineTo(6f, 41f)
        canvas.drawPath(path, paint)
        path.reset(); path.moveTo(6f, 30f); path.lineTo(6f, 41f); path.lineTo(17f, 42f)
        canvas.drawPath(path, paint)
        canvas.restoreToCount(save)
    }
}

internal fun medalColor(tier: Int): Int = when (tier) {
    0 -> 0xFFCC8B60.toInt()
    1 -> 0xFFADB9D0.toInt()
    2 -> Theme.GOLD
    else -> Theme.CYAN
}
internal fun medalName(tier: Int): String = listOf("Bronze", "Silver", "Gold", "Diamond")[tier.coerceIn(0, 3)]
internal fun number(value: Int): String = NumberFormat.getIntegerInstance(java.util.Locale.US).format(value)

/** A tiny embossed medal, with a diamond silhouette for the final tier. */
class MedalIcon(private val color: Int, private val earned: Boolean, private val diamond: Boolean = false,
    private val dark: Boolean = false, private val ribbon: Boolean = true) : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 32f
        canvas.translate(bounds.exactCenterX() - 16f * scale, bounds.exactCenterY() - 16f * scale)
        canvas.scale(scale, scale)
        val faceY = if (!ribbon) 16f else if (diamond) 14f else 13f
        paint.style = Paint.Style.FILL
        if (ribbon) {
            paint.color = if (earned) Theme.darken(color, .22f) else Theme.alpha(Theme.INK, 30)
            path.reset(); path.moveTo(7f, 17f); path.lineTo(6f, 31f); path.lineTo(12f, 27f); path.lineTo(16f, 30f); path.lineTo(19f, 18f); path.close()
            canvas.drawPath(path, paint)
            path.reset(); path.moveTo(17f, 18f); path.lineTo(19f, 30f); path.lineTo(23f, 27f); path.lineTo(26f, 31f); path.lineTo(25f, 17f); path.close()
            canvas.drawPath(path, paint)
        }
        paint.color = if (earned) color else if (dark) Theme.alpha(Theme.LAVENDER, 13) else Theme.CARD_ALT
        if (diamond) {
            path.reset(); path.moveTo(16f, faceY - 13f); path.lineTo(29f, faceY); path.lineTo(16f, faceY + 13f); path.lineTo(3f, faceY); path.close()
            canvas.drawPath(path, paint)
        } else canvas.drawCircle(16f, faceY, 12f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = if (earned) 2f else 1.8f
        paint.color = if (earned) Theme.lighten(color, .55f) else if (dark) Theme.alpha(Theme.WHITE, 130) else Theme.alpha(Theme.INK, 100)
        if (diamond) canvas.drawPath(path, paint) else canvas.drawCircle(16f, faceY, if (earned) 9f else 11f, paint)
        paint.style = Paint.Style.FILL
        if (earned) {
            paint.color = Theme.WHITE
            path.reset(); path.moveTo(16f, faceY - 6f); path.lineTo(18f, faceY - 2f); path.lineTo(22f, faceY); path.lineTo(18f, faceY + 2f); path.lineTo(16f, faceY + 6f); path.lineTo(14f, faceY + 2f); path.lineTo(10f, faceY); path.lineTo(14f, faceY - 2f); path.close()
            canvas.drawPath(path, paint)
        }
        canvas.restoreToCount(save)
    }
}

/** Start the fill when it first enters the viewport, including cards reached by scrolling. */
@SuppressLint("ViewConstructor")
private class AchievementProgressBar(context: Context, private val kit: UiKit, private val color: Int,
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
        paint.color = Theme.alpha(Theme.WHITE, 58); canvas.drawRoundRect(rect, radius, radius, paint)
        if (amount > 0f) {
            rect.right = width * amount
            val cap = minOf(radius, rect.width() / 2f)
            paint.color = Theme.darken(color, .12f); canvas.drawRoundRect(rect, cap, cap, paint)
            rect.bottom -= kit.dpf(2f)
            paint.color = color; canvas.drawRoundRect(rect, cap, cap, paint)
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
