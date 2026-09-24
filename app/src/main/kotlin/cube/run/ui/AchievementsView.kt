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
import cube.run.R
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.data.Achievements
import cube.run.data.Progress

/**
 * The trophy room. A trophy ring up top shows how much of everything is earned and collects
 * every waiting reward in one tap; below it the medal families, then the challenges, one card
 * per row. Claiming flips the card over like a flap: its new face comes up with the medal
 * just minted spinning in, and the reward pours out of that medal into the bank.
 */
@SuppressLint("ViewConstructor")
class AchievementsView(activity: Activity, kit: UiKit, onClose: () -> Unit) :
    Page(activity, kit, activity.getString(R.string.achievements_title), dark = true, onClosed = onClose) {
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
        // Rewards waiting first, then whatever is closest to its next medal; finished ones go to the shelf.
        val open = states.filterNot { it.allClaimed }
            .sortedWith(compareBy<Achievements.Snapshot> { if (it.claimableTier != null) 0 else 1 }.thenByDescending { it.fraction })
        val medals = open.filter { it.definition.tiered }
        val challenges = open.filterNot { it.definition.tiered }
        val done = states.filter { it.allClaimed }
        val allMedals = states.filter { it.definition.tiered }
        val allChallenges = states.filterNot { it.definition.tiered }
        rows.addView(hero, LinearLayout.LayoutParams(-1, -2))
        fun list(states: List<Achievements.Snapshot>, first: Int, row: (Achievements.Snapshot, Int) -> View) {
            for ((index, state) in states.withIndex()) rows.addView(row(state, first + index),
                LinearLayout.LayoutParams(-1, -2).apply { if (index > 0) topMargin = dp(12f) })
        }
        if (medals.isNotEmpty()) {
            rows.addView(section(activity.getString(R.string.achievement_section_medals), "${allMedals.sumOf { it.earnedTiers }} / ${allMedals.sumOf { it.definition.thresholds.size }}"), sectionParams())
            list(medals, 0) { state, i -> cards.card(state, i) }
        }
        if (challenges.isNotEmpty()) {
            rows.addView(section(activity.getString(R.string.achievement_section_challenges), "${allChallenges.count { it.earnedTiers > 0 }} / ${allChallenges.size}"), sectionParams())
            list(challenges, medals.size) { state, i -> cards.card(state, i) }
        }
        if (done.isNotEmpty()) {
            rows.addView(section(activity.getString(R.string.achievement_section_done), "${done.size}"), sectionParams())
            for ((index, state) in done.withIndex()) rows.addView(cards.doneChip(state),
                LinearLayout.LayoutParams(-1, -2).apply { if (index > 0) topMargin = dp(8f) })
        }
        rows.addView(suggestionCard(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28f) })
        hero.bind(states, animate = true, delay = 260L)
    }

    /** The last card: an invitation to suggest the next achievement. */
    private fun suggestionCard(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(18f), dp(18f), dp(18f), dp(18f) + kit.CARD_LIP)
        val radius = dpf(22f)
        background = android.graphics.drawable.LayerDrawable(arrayOf(
            GradientDrawable().apply { cornerRadius = radius; setColor(Theme.darken(0xFF263950.toInt(), .3f)) },
            GradientDrawable().apply { cornerRadius = radius; setColor(0xFF263950.toInt()); setStroke(dp(2f), Theme.GOLD) },
        )).apply { setLayerInset(1, 0, 0, 0, kit.CARD_LIP) }
        addView(kit.stageText(activity.getString(R.string.achievement_suggest_title), 22f, Theme.WHITE, gravity = Gravity.CENTER, stroke = 2.5f))
        addView(kit.text(activity.getString(R.string.achievement_suggest_body), 16f, Theme.alpha(Theme.WHITE, 225), 500),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })
        addView(kit.button(activity.getString(R.string.achievement_suggest_button), Theme.GOLD) {
            activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(SUGGEST_URL)))
        }.apply { tag = "achievement_suggest" },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16f) })
    }

    /** A heading between the groups, with how many are earned. */
    private fun section(label: String, count: String): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4f), 0, dp(4f), 0)
        addView(kit.stageText(label, 18f, Theme.WHITE, gravity = Gravity.START, stroke = 2f).apply { letterSpacing = kit.tracking(.06f) },
            LinearLayout.LayoutParams(0, -2, 1f))
        addView(kit.stageText(count, 18f, Theme.alpha(Theme.WHITE, 220), stroke = 2f))
    }

    private fun sectionParams() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(26f); bottomMargin = dp(10f) }

    override fun animateEntrance() {
        super.animateEntrance()
        // The trophy, then each card, rise in one after the other.
        for (i in 0 until minOf(rows.childCount, 7)) Anim.riseIn(rows.getChildAt(i), 40L + i * 45L, dpf(26f), 320)
    }

    /** One reward: the card flips to its new face, the medal is minted, and the coins pour out of it. */
    private fun claim(state: Achievements.Snapshot, button: CandyButton) {
        if (paying || closing) return
        val tier = state.claimableTier ?: return
        val before = Progress.coins
        if (Achievements.claim(state.definition.id) <= 0) return
        paying = true
        button.isEnabled = false
        Haptics.click()
        flipAndPay(state.definition, tier, coins = 6, sound = 0) { ms ->
            countBank(before, ms)
        } then {
            SoundFx.play("success", rate = 1.2f, vol = .5f); Haptics.success()
            hero.bind(Achievements.snapshot(), animate = true)
            paying = false
        }
    }

    /** Every waiting reward at once: the ready cards flip one after another, top to bottom, each paying out. */
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
        Haptics.click()
        val seen = android.graphics.Rect()
        // Cards out of sight just take their new face; the ones in view flip in turn.
        val (shown, hidden) = claimed.partition { (definition, _) ->
            rows.findViewWithTag<View>("achievement_card_${definition.id}")?.getLocalVisibleRect(seen) == true
        }
        for ((definition, _) in hidden) rebuild(definition)
        if (shown.isEmpty()) {
            countBank(before, 600L)
            hero.bind(Achievements.snapshot(), animate = true)
            SoundFx.play("success", rate = 1.2f, vol = .5f); Haptics.success()
            paying = false
            return
        }
        countBank(before, shown.size * STAGGER + 900L)
        var left = shown.size
        for ((i, pair) in shown.withIndex()) postDelayed({
            if (!isAttachedToWindow) return@postDelayed
            flipAndPay(pair.first, pair.second, coins = 3, sound = i) {} then {
                if (--left == 0) {
                    SoundFx.play("success", rate = 1.2f, vol = .5f); Haptics.success()
                    hero.bind(Achievements.snapshot(), animate = true)
                    paying = false
                }
            }
        }, i * STAGGER)
    }

    /** A step that runs [next] when it is over. */
    private class Then { var next: () -> Unit = {}; infix fun then(f: () -> Unit) { next = f } }

    /**
     * Flip [definition]'s card over its top edge to its new face, mint the medal for [tier] (or the
     * challenge's badge) with a coin spin, then pour [coins] coins from it into the bank.
     * [onFly] hears how long the coins take; the returned step runs once they have landed.
     */
    private fun flipAndPay(definition: Achievements.Definition, tier: Int, coins: Int, sound: Int, onFly: (Long) -> Unit): Then {
        val step = Then()
        val old = rows.findViewWithTag<View>("achievement_card_${definition.id}")
        if (old == null) { post { step.next() }; return step }
        val held = scroll.scrollY
        flip(old, 0f, 90f, 150L, android.view.animation.AccelerateInterpolator()) {
            if (closing || !isAttachedToWindow) return@flip
            val fresh = rebuild(definition) ?: return@flip step.next()
            fresh.rotationX = -90f
            fresh.cameraDistance = old.cameraDistance
            SoundFx.play("coin", rate = 1.1f + sound * .07f, vol = .45f); Haptics.tick()
            val medal = (if (definition.tiered) fresh.findViewWithTag<View>("achievement_medal_${definition.id}_$tier")
                else fresh.findViewWithTag("achievement_icon_${definition.id}")) ?: fresh
            medal.scaleX = 0f; medal.scaleY = 0f
            // The new face has no size until the next layout: flip it up and strike its medal after that.
            afterLayout(fresh) {
                scroll.scrollTo(0, held)
                flip(fresh, -90f, 0f, 320L, android.view.animation.OvershootInterpolator(1.6f)) {}
                rays(medal, delay = 180L)
                mint(medal, delay = 180L) {
                    if (closing || !isAttachedToWindow) return@mint
                    onFly(PayFx.fly(this, kit, medal, bank, n = coins, onDone = { if (isAttachedToWindow) step.next() }))
                }
            }
        }
        return step
    }

    private fun afterLayout(v: View, then: () -> Unit) {
        v.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                v.viewTreeObserver.removeOnPreDrawListener(this)
                post(then)
                return true
            }
        })
    }

    /** Turn [v] about its top edge from [from] to [to] degrees, repainting every frame over the GL stage. */
    private fun flip(v: View, from: Float, to: Float, ms: Long, curve: android.animation.TimeInterpolator, then: () -> Unit) {
        v.cameraDistance = v.resources.displayMetrics.density * 9000f
        v.pivotX = v.width / 2f; v.pivotY = 0f
        ValueAnimator.ofFloat(from, to).apply {
            duration = ms; interpolator = curve
            addUpdateListener { v.rotationX = it.animatedValue as Float; Anim.repaint(v) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) { if (!cancelled) then() else v.rotationX = 0f }
            })
            Anim.cancelOnDetach(v, this)
            start()
        }
    }

    /** A gold sunburst opens over [at] as it is struck, turns a little and fades. */
    private fun rays(at: View, delay: Long) {
        // Laid-out position, not the drawn one: the card is still turning when this is placed.
        val box = android.graphics.Rect(0, 0, at.width, at.height)
        sheet.offsetDescendantRectToMyCoords(at, box)
        val size = maxOf(at.width, at.height) * 4
        val burst = View(activity).apply {
            background = RayBurst(Theme.GOLD)
            alpha = 0f
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        sheet.addView(burst, FrameLayout.LayoutParams(size, size))
        burst.x = box.exactCenterX() - size / 2f
        burst.y = box.exactCenterY() - size / 2f
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900; startDelay = delay
            addUpdateListener {
                val t = it.animatedValue as Float
                burst.alpha = if (t < .25f) t / .25f else 1f - (t - .25f) / .75f
                burst.rotation = 50f * t
                burst.scaleX = .4f + .6f * Anim.ease.getInterpolation(minOf(1f, t * 1.6f)); burst.scaleY = burst.scaleX
                Anim.repaint(burst)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                // Removing it inside a detach walk would leave a hole in the sheet's children.
                override fun onAnimationEnd(animation: android.animation.Animator) { if (!cancelled) sheet.removeView(burst) }
            })
            Anim.cancelOnDetach(burst, this)
            start()
        }
    }

    /** The new medal is struck: it spins in edge-on like a tossed coin, lands a touch big and settles. */
    private fun mint(v: View, delay: Long, then: () -> Unit) {
        v.scaleX = 0f; v.scaleY = 0f
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 560; startDelay = delay
            interpolator = android.view.animation.DecelerateInterpolator(1.4f)
            addUpdateListener {
                val t = it.animatedValue as Float
                // Two turns about the vertical axis, narrowing to face you; the size overshoots then settles.
                v.rotationY = 720f * (1f - t)
                val grow = if (t < .7f) 1.7f * (t / .7f) else 1.7f - .7f * ((t - .7f) / .3f)
                v.scaleX = grow; v.scaleY = grow
                Anim.repaint(v)
            }
            var fired = false
            addUpdateListener { if (!fired && it.animatedFraction >= .55f) { fired = true; then() } }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { v.rotationY = 0f; v.scaleX = 1f; v.scaleY = 1f }
            })
            Anim.cancelOnDetach(v, this)
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
        return cards.card(Achievements.snapshot(definition), index, animateFill = false).also { parent.addView(it, index, params) }
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
        const val STAGGER = 140L
        const val SUGGEST_URL = "https://github.com/Eve-146T/cube-run/issues/new"
    }
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

/** Rays of one colour around a clear middle, so the medal inside stays visible: the light a freshly struck medal gives off. */
private class RayBurst(private val color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = android.graphics.Path()

    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY(); val r = bounds.width() / 2f
        if (r < 2f) return
        paint.shader = RadialGradient(cx, cy, r, intArrayOf(Theme.alpha(color, 0), Theme.alpha(color, 235), Theme.alpha(Theme.YELLOW, 150), Theme.alpha(Theme.YELLOW, 0)), floatArrayOf(.24f, .32f, .6f, 1f), Shader.TileMode.CLAMP)
        path.reset()
        for (i in 0 until 14) {
            val a = Math.toRadians(i * 360.0 / 14); val b = Math.toRadians(i * 360.0 / 14 + 11.0)
            path.moveTo(cx, cy)
            path.lineTo(cx + r * Math.cos(a).toFloat(), cy + r * Math.sin(a).toFloat())
            path.lineTo(cx + r * Math.cos(b).toFloat(), cy + r * Math.sin(b).toFloat())
            path.close()
        }
        canvas.drawPath(path, paint)
        paint.shader = null
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
