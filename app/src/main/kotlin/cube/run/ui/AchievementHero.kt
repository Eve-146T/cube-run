package cube.run.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import cube.run.data.Achievements
import kotlin.math.cos
import kotlin.math.sin

/**
 * The top of the achievement page: a trophy inside a ring that fills with everything earned so
 * far, the medal haul by metal (and the challenges done), and one gold button that collects
 * every waiting reward at once. The button is only there when something is waiting.
 */
@SuppressLint("ViewConstructor")
internal class AchievementHero(context: Context, private val kit: UiKit, onClaimAll: (CandyButton) -> Unit) : LinearLayout(context) {
    private val ring = TrophyRing(context, kit)
    private val total = kit.stageText("", 20f, stroke = 2.5f).apply { tag = "achievement_total"; maxLines = 1 }
    private val tallyIcons = Array(5) { ImageView(context) }
    private val tallyCounts = Array(5) { kit.stageText("0", 16f, stroke = 2f).apply { maxLines = 1 } }
    private var earned = -1
    lateinit var claimAll: CandyButton
        private set

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false; clipToPadding = false
        setPadding(0, kit.dp(2f), 0, kit.dp(4f))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        addView(ring, LayoutParams(kit.dp(140f), kit.dp(140f)))
        addView(total, LayoutParams(-2, -2).apply { topMargin = kit.dp(2f) })
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false; clipToPadding = false
            for (i in 0..4) addView(LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                clipChildren = false; clipToPadding = false
                addView(tallyIcons[i].apply { setPadding(kit.dp(2f), kit.dp(2f), kit.dp(2f), kit.dp(2f)) }, LayoutParams(kit.dp(32f), kit.dp(32f)))
                addView(tallyCounts[i], LayoutParams(-2, -2))
            }, LayoutParams(-2, -2).apply { if (i > 0) marginStart = kit.dp(if (i == 4) 26f else 14f) })
        }, LayoutParams(-2, -2).apply { topMargin = kit.dp(10f) })
        claimAll = kit.button("", Theme.GOLD) { onClaimAll(claimAll) }.apply {
            tag = "achievement_claim_all"
            maxLines = 1
            visibility = GONE // nothing waiting until bind says so
        }
        addView(claimAll, LayoutParams(-1, -2).apply { topMargin = kit.dp(16f); marginStart = kit.dp(20f); marginEnd = kit.dp(20f) })
    }

    /** Show [states]; [animate] rolls the ring and the total from where they were. */
    fun bind(states: List<Achievements.Snapshot>, animate: Boolean, delay: Long = 0L) {
        val max = states.sumOf { it.definition.thresholds.size }
        val now = states.sumOf { it.earnedTiers }
        val tallies = IntArray(5)
        for (s in states) if (s.definition.tiered) for (t in 0 until s.earnedTiers) tallies[t]++ else if (s.earnedTiers > 0) tallies[4]++
        for (i in 0..4) {
            tallyIcons[i].setImageDrawable(if (i == 4) AchievementCheckIcon() else MedalIcon(medalColor(i), tallies[i] > 0, i == 3, dark = true, ribbon = false))
            tallyIcons[i].alpha = if (tallies[i] > 0 || i < 4) 1f else .45f
            tallyCounts[i].text = number(tallies[i])
            tallyCounts[i].setTextColor(if (tallies[i] > 0) Theme.WHITE else Theme.alpha(Theme.WHITE, 120))
        }
        val from = if (earned < 0) 0 else earned
        earned = now
        ring.fillTo(if (max == 0) 0f else now / max.toFloat(), animate, delay)
        if (animate && now != from) {
            total.text = "$from / $max"
            postDelayed({ Anim.countTo(total, from, now, 1100L) { "$it / $max" } }, delay)
        } else total.text = "$now / $max"
        contentDescription = "$now of $max achievements earned"

        val waiting = states.sumOf { s ->
            val first = s.claimableTier ?: return@sumOf 0
            (first until s.earnedTiers).sumOf { Achievements.reward(s.definition, it) }
        }
        if (waiting > 0) {
            claimAll.text = android.text.SpannableStringBuilder("CLAIM ALL ").append(kit.coins(number(waiting), 18f))
            claimAll.contentDescription = "Claim all rewards, ${number(waiting)} coins"
            claimAll.isEnabled = true
            if (claimAll.visibility != VISIBLE) { claimAll.visibility = VISIBLE; claimAll.alpha = 1f; claimAll.scaleX = 1f; claimAll.scaleY = 1f }
        } else if (claimAll.visibility == VISIBLE) {
            if (!animate) claimAll.visibility = GONE
            else collapse(claimAll)
        }
    }

    /** The spent button shrinks away and the list closes up behind it. */
    private fun collapse(v: View) {
        val start = v.height
        val params = v.layoutParams as LayoutParams
        val margin = params.topMargin
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 320; interpolator = Anim.ease
            addUpdateListener {
                val k = it.animatedValue as Float
                v.alpha = k; v.scaleX = .8f + .2f * k; v.scaleY = .8f + .2f * k
                params.height = (start * k).toInt(); params.topMargin = (margin * k).toInt()
                v.layoutParams = params
                Anim.repaint(v)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (cancelled) return
                    v.visibility = GONE
                    params.height = LayoutParams.WRAP_CONTENT; params.topMargin = margin
                    v.layoutParams = params
                }
            })
            Anim.cancelOnDetach(v, this)
            start()
        }
    }
}

/**
 * A trophy in a ring of every medal colour. The ring fills to the share of achievements earned,
 * a bright spark riding its leading end, over a still sunburst behind the cup. Nothing here
 * repaints once the fill has landed: a page that never goes idle stalls instrumented tests.
 */
private class TrophyRing(context: Context, private val kit: UiKit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trophy = TrophyIcon()
    private val arc = RectF()
    private val rays = Path()
    private val disc = Path()
    private var amount = 0f
    private var animator: ValueAnimator? = null
    private var glow: RadialGradient? = null
    private var inner: RadialGradient? = null
    private var sweep: SweepGradient? = null

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun fillTo(target: Float, animate: Boolean, delay: Long) {
        animator?.cancel()
        if (!animate) { amount = target; invalidate(); return }
        animator = ValueAnimator.ofFloat(amount, target).apply {
            duration = 1100; startDelay = delay
            interpolator = android.view.animation.PathInterpolator(.2f, .9f, .3f, 1f)
            addUpdateListener { amount = it.animatedValue as Float; Anim.repaint(this@TrophyRing) }
            Anim.cancelOnDetach(this@TrophyRing, this)
            start()
        }
    }

    private val stroke get() = kit.dpf(11f)
    private val radius get() = minOf(width, height) / 2f - stroke - kit.dpf(4f)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val cx = w / 2f; val cy = h / 2f
        glow = RadialGradient(cx, cy, minOf(w, h) / 2f, intArrayOf(Theme.alpha(Theme.YELLOW, 120), Theme.alpha(Theme.ORANGE, 45), 0),
            floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        val light = Theme.lighten(0xFF3B2F86.toInt(), .05f)
        inner = RadialGradient(cx, cy - radius * .25f, radius * 1.1f, intArrayOf(light, 0xFF1B1644.toInt()), null, Shader.TileMode.CLAMP)
        sweep = SweepGradient(cx, cy, intArrayOf(medalColor(0), medalColor(1), Theme.YELLOW, medalColor(3), medalColor(0)), null).apply {
            setLocalMatrix(Matrix().apply { setRotate(-90f, cx, cy) })
        }
        disc.reset(); disc.addCircle(cx, cy, radius - stroke / 2f, Path.Direction.CW)
        rays.reset()
        val far = radius * 1.2f
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0); val b = Math.toRadians(i * 30.0 + 13.0)
            rays.moveTo(cx, cy)
            rays.lineTo(cx + far * cos(a).toFloat(), cy + far * sin(a).toFloat())
            rays.lineTo(cx + far * cos(b).toFloat(), cy + far * sin(b).toFloat())
            rays.close()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = radius
        paint.style = Paint.Style.FILL
        paint.shader = glow; canvas.drawCircle(cx, cy, minOf(width, height) / 2f, paint)
        paint.shader = inner; canvas.drawCircle(cx, cy, r - stroke / 2f, paint); paint.shader = null
        val saved = canvas.save()
        canvas.clipPath(disc)
        paint.color = Theme.alpha(Theme.WHITE, 20); canvas.drawPath(rays, paint)
        canvas.restoreToCount(saved)
        arc.set(cx - r, cy - r, cx + r, cy + r)
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = stroke; paint.color = Theme.alpha(0xFF0B0826.toInt(), 150); canvas.drawCircle(cx, cy, r, paint)
        paint.strokeWidth = kit.dpf(1.5f); paint.color = Theme.alpha(Theme.WHITE, 60); canvas.drawCircle(cx, cy, r - stroke / 2f, paint)
        if (amount > 0f) {
            val sweepAngle = 360f * amount.coerceIn(0f, 1f)
            paint.shader = sweep; paint.color = Theme.WHITE
            paint.strokeWidth = stroke * 1.9f; paint.alpha = 55; canvas.drawArc(arc, -90f, sweepAngle, false, paint)
            paint.strokeWidth = stroke; paint.alpha = 255; canvas.drawArc(arc, -90f, sweepAngle, false, paint)
            paint.shader = null
            val end = Math.toRadians((-90f + sweepAngle).toDouble())
            paint.style = Paint.Style.FILL; paint.color = Theme.WHITE
            canvas.drawCircle(cx + r * cos(end).toFloat(), cy + r * sin(end).toFloat(), stroke * .3f, paint)
        }
        paint.style = Paint.Style.FILL
        val cup = r * 1.1f
        trophy.setBounds((cx - cup / 2f).toInt(), (cy - cup / 2f - kit.dpf(2f)).toInt(), (cx + cup / 2f).toInt(), (cy + cup / 2f - kit.dpf(2f)).toInt())
        trophy.draw(canvas)
    }
}
