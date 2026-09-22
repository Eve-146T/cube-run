package cube.run.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.data.Achievements
import cube.run.data.Progress

/**
 * The trophy room. A trophy ring up top shows how much of everything is earned and collects
 * every waiting reward in one tap; below it the five medal families, then the one-off
 * challenges as a grid of tiles. Claiming flies the coins into the bank, and the medal you
 * claimed stamps down onto its card with a ring of its own colour.
 */
@SuppressLint("ViewConstructor")
class AchievementsView(activity: Activity, kit: UiKit, onClose: () -> Unit) :
    Page(activity, kit, "ACHIEVEMENTS", dark = true, onClosed = onClose) {
    private val rows = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14f), dp(4f), dp(14f), dp(28f))
        clipChildren = false; clipToPadding = false
    }
    /** The scrolling sheet: the list, and the claim stamps drawn over it (clipped with it). */
    private val sheet = FrameLayout(activity).apply {
        clipChildren = false; clipToPadding = false
        addView(rows, FrameLayout.LayoutParams(-1, -2))
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
        addView(sheet)
    }
    private val cards = AchievementCards(activity, kit, ::claim)
    private val hero = AchievementHero(activity, kit, ::claimAll)
    private val grid = ChallengeGrid(activity, kit)
    private var paying = false
    private var bankCount: ValueAnimator? = null

    init {
        background = TrophyRoomBackdrop()
        titleView.maxLines = 1
        titleView.setAutoSizeTextTypeUniformWithConfiguration(12, 23, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        content.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = true; clipToPadding = true
            addView(LinearLayout(activity).apply {
                gravity = Gravity.END
                setPadding(dp(14f), dp(2f), dp(14f), dp(6f))
                addView(bank)
            }, LinearLayout.LayoutParams(-1, -2))
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        val states = Achievements.snapshot()
        val medals = states.filter { it.definition.tiered }
        val challenges = states.filterNot { it.definition.tiered }
        rows.addView(hero, LinearLayout.LayoutParams(-1, -2))
        rows.addView(section("MEDALS", "${medals.sumOf { it.earnedTiers }} / ${medals.sumOf { it.definition.thresholds.size }}"),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22f); bottomMargin = dp(10f) })
        for ((index, state) in medals.withIndex()) rows.addView(cards.card(state, index),
            LinearLayout.LayoutParams(-1, -2).apply { if (index > 0) topMargin = dp(12f) })
        rows.addView(section("CHALLENGES", "${challenges.count { it.earnedTiers > 0 }} / ${challenges.size}"),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24f); bottomMargin = dp(10f) })
        for ((index, state) in challenges.withIndex()) grid.addView(cards.card(state, medals.size + index))
        rows.addView(grid, LinearLayout.LayoutParams(-1, -2))
        hero.bind(states, animate = true, delay = 260L)
    }

    /** A quiet heading between the groups, with how many are earned. */
    private fun section(label: String, count: String): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4f), 0, dp(4f), 0)
        addView(kit.text(label, 13f, Theme.alpha(Theme.WHITE, 215), 700).apply { letterSpacing = kit.tracking(.14f) })
        addView(View(activity).apply {
            background = GradientDrawable().apply { cornerRadius = dpf(1f); setColor(Theme.alpha(Theme.WHITE, 46)) }
        }, LinearLayout.LayoutParams(0, dp(2f), 1f).apply { marginStart = dp(10f); marginEnd = dp(10f) })
        addView(kit.text(count, 13f, Theme.alpha(Theme.WHITE, 215), 700))
    }

    override fun animateEntrance() {
        super.animateEntrance()
        // The trophy, then each card, rise in one after the other.
        for (i in 0 until minOf(rows.childCount, 7)) Anim.riseIn(rows.getChildAt(i), 40L + i * 45L, dpf(26f), 320)
    }

    /** One reward, paid out where you tapped. */
    private fun claim(state: Achievements.Snapshot, button: CandyButton) {
        if (paying || closing) return
        val tier = state.claimableTier ?: return
        val before = Progress.coins
        if (Achievements.claim(state.definition.id) <= 0) return
        paying = true
        button.isEnabled = false
        val ms = PayFx.fly(this, kit, button, bank, n = 5, onDone = {
            if (closing || !isAttachedToWindow) return@fly
            val fresh = rebuild(state.definition)
            settle {
                celebrate(fresh, state.definition, tier)
                SoundFx.play("success", rate = 1.2f, vol = .5f)
                Haptics.success()
            }
            hero.bind(Achievements.snapshot(), animate = true)
        })
        countBank(before, ms)
    }

    /** Every waiting reward at once: the coins pour into the bank, then each card stamps its medal in turn. */
    private fun claimAll(button: CandyButton) {
        if (paying || closing) return
        val before = Progress.coins
        val claimed = ArrayList<Pair<Achievements.Definition, Int>>()
        for (state in Achievements.snapshot()) {
            val first = state.claimableTier ?: continue
            var last = first - 1
            while (Achievements.claim(state.definition.id) > 0) last++
            if (last >= first) claimed += state.definition to last
        }
        if (claimed.isEmpty()) return
        paying = true
        button.isEnabled = false
        val ms = PayFx.fly(this, kit, button, bank, n = 8, onDone = {
            if (closing || !isAttachedToWindow) return@fly
            val fresh = claimed.map { (definition, _) -> rebuild(definition) }
            settle {
                for ((i, pair) in claimed.withIndex()) postDelayed({
                    if (!isAttachedToWindow) return@postDelayed
                    celebrate(fresh[i], pair.first, pair.second)
                    SoundFx.play("coin", rate = 1.2f + i * .08f, vol = .4f); Haptics.tick()
                }, i * 110L)
                postDelayed({ SoundFx.play("success", rate = 1.2f, vol = .5f); Haptics.success() }, claimed.size * 110L)
            }
            hero.bind(Achievements.snapshot(), animate = true)
        })
        countBank(before, ms)
    }

    private fun countBank(before: Int, ms: Long) {
        bankCount?.cancel()
        bankCount = Anim.countTo(kit.labelOf(bank), before, Progress.coins, ms, ::number)
    }

    /** Swap a family's card for its current state, in place (list or grid). */
    private fun rebuild(definition: Achievements.Definition): View {
        val old = rows.findViewWithTag<View>("achievement_card_${definition.id}")
        val parent = old.parent as ViewGroup
        val index = parent.indexOfChild(old)
        val params = old.layoutParams
        parent.removeViewAt(index)
        return cards.card(Achievements.snapshot(definition), index, animateFill = false).also { parent.addView(it, index, params) }
    }

    /**
     * After a rebuild: keep the scroll where it was, then [then] once the new cards are laid out.
     * The celebration runs on the next frame, never inside the draw pass: adding its rings there
     * asks for a layout mid-draw, which was seen to leave the tree with a hole in it.
     */
    private fun settle(then: () -> Unit) {
        val held = scroll.scrollY
        scroll.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                scroll.viewTreeObserver.removeOnPreDrawListener(this)
                val viewport = scroll.height - scroll.paddingTop - scroll.paddingBottom
                val range = ((scroll.getChildAt(0)?.height ?: 0) - viewport).coerceAtLeast(0)
                scroll.scrollTo(0, held.coerceIn(0, range))
                paying = false
                post(then)
                return true
            }
        })
    }

    /** The claimed medal (or a challenge's badge) stamps down onto its freshly lit card. */
    private fun celebrate(card: View, definition: Achievements.Definition, tier: Int) {
        PayFx.flash(card, dpf(20f))
        val target = if (definition.tiered) card.findViewWithTag<View>("achievement_medal_${definition.id}_$tier")
            else card.findViewWithTag("achievement_icon_${definition.id}")
        target ?: return
        Anim.popIn(target, 0, 1.9f, 420)
        stamp(target, if (definition.tiered) medalColor(tier) else Theme.MINT)
    }

    /** Two rings of [color] spread from [at] and fade: the stamp's impact, right where it lands. */
    private fun stamp(at: View, color: Int) {
        val here = IntArray(2); val there = IntArray(2)
        sheet.getLocationInWindow(here); at.getLocationInWindow(there)
        val cx = there[0] - here[0] + at.width / 2f
        val cy = there[1] - here[1] + at.height / 2f
        val size = maxOf(at.width, at.height)
        for (k in 0..1) {
            val ring = View(activity).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(dp(if (k == 0) 3f else 2f), color) }
                alpha = 0f
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            sheet.addView(ring, FrameLayout.LayoutParams(size, size))
            ring.x = cx - size / 2f; ring.y = cy - size / 2f
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 480; startDelay = 150L + k * 110L
                interpolator = Anim.ease
                addUpdateListener {
                    val t = it.animatedValue as Float
                    ring.alpha = 1f - t
                    ring.scaleX = .7f + 1.5f * t; ring.scaleY = ring.scaleX
                    Anim.repaint(ring)
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    private var cancelled = false
                    override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                    // A cancel arrives while the window is tearing the tree down: removing the
                    // ring from inside that walk leaves a hole in its parent's children.
                    override fun onAnimationEnd(animation: android.animation.Animator) { if (!cancelled) sheet.removeView(ring) }
                })
                Anim.cancelOnDetach(ring, this)
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        bankCount?.cancel()
        super.onDetachedFromWindow()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        if (paying) true else super.dispatchTouchEvent(event)

    override fun onBack() { if (!paying) close() }
}

/** Night indigo into deep teal, with a warm glow where the trophy stands and two cool ones lower down. */
private class TrophyRoomBackdrop : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shaders: List<Shader> = emptyList()

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        fun glow(x: Float, y: Float, r: Float, color: Int, a: Int) =
            RadialGradient(x, y, r, Theme.alpha(color, a), Theme.alpha(color, 0), Shader.TileMode.CLAMP)
        shaders = listOf(
            LinearGradient(0f, 0f, 0f, h, intArrayOf(0xFF2B2266.toInt(), 0xFF1E2A5C.toInt(), 0xFF123A4E.toInt()), null, Shader.TileMode.CLAMP),
            glow(w * .5f, h * .2f, w * .75f, Theme.ORANGE, 70),
            glow(w * .02f, h * .58f, w * .7f, Theme.GRAPE, 60),
            glow(w * 1f, h * .88f, w * .75f, Theme.CYAN, 45),
        )
    }

    override fun draw(canvas: Canvas) {
        for (shader in shaders) { paint.shader = shader; canvas.drawRect(bounds, paint) }
        paint.shader = null
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
}
