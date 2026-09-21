package cube.run.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Wardrobe
import cube.run.ui.Anim.move

/**
 * The run-over sequence, full screen, one focused page at a time on the
 * engine's stage (your cube floating in a world-tinted sky):
 *  1. the results — your cube up top with a sunburst spinning behind it (in
 *     the 3D stage, so the cube stays in front), a huge score counting up,
 *     stars that slam in and coins. One tap skips the count, the next moves on;
 *  2. the mystery boxes (only if any were collected) — the game renders the
 *     box in 3D ([Stage.BOX]); a tap opens one, the reward pops up as a
 *     card, the next tap brings the next box (already opening) or, after
 *     the last, moves on;
 *  3. and then straight back to the main menu (everything else lives there).
 * Leaving hands off immediately to the activity crossfade, keeping this screen visible underneath.
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class RunOverFlow(
    private val activity: Activity,
    private val kit: UiKit,
    private val score: Int,
    private val best: Int,
    private val isNewBest: Boolean,
    private val coins: Int,
    private val boxes: Int,
    private val world: String,
    private val bonusVisited: List<Int>,
    private val onRestart: () -> Unit,
    private val onMenu: () -> Unit,
    private val shards: IntArray = IntArray(3),
    private val boxesOnly: Boolean = false,
) : FrameLayout(activity) {

    private fun dp(v: Float) = kit.dp(v)
    private fun dpf(v: Float) = kit.dpf(v)
    private var page: View? = null
    private val anims = ArrayList<ValueAnimator>()
    private val pending = ArrayList<Runnable>()
    private var rewardBeat: ValueAnimator? = null
    private var leaving = false

    private fun later(ms: Long, action: () -> Unit) {
        val task = object : Runnable {
            override fun run() { pending.remove(this); action() }
        }
        pending.add(task)
        postDelayed(task, ms)
    }

    private fun stopEffects() {
        boxReadyTask?.let { removeCallbacks(it) }; boxReadyTask = null
        pending.forEach { removeCallbacks(it) }
        pending.clear()
        scoreAnim?.cancel(); coinAnim?.cancel()
        rewardBeat?.cancel(); rewardBeat = null
        anims.forEach { it.cancel() }
        anims.clear()
        Anim.cancelTree(this)
    }

    // ------------------------------------------------------------- transitions

    /** Slide the old page out to the left, the new one in from the right. */
    private fun swap(next: View) {
        page?.let { old ->
            old.move().alpha(0f).translationX(-dpf(60f)).setDuration(160).withEndAction { removeView(old) }.start()
        }
        page = next
        addView(next, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        Anim.slideIn(next, 0, dpf(70f), 260)
    }

    /** Start the destination immediately; the window transition keeps these results visible until it is ready. */
    private fun leave(action: () -> Unit) {
        if (leaving) return
        leaving = true
        stopEffects()
        Haptics.click()
        action()
    }

    /** A soft hint that breathes at the bottom of a page (hide it with visibility, not alpha). */
    private fun tapHint(text: String): TextView = kit.stageText(text, 14f, Theme.alpha(Theme.WHITE, 220), weight = 600, stroke = 2f).apply {
        letterSpacing = 0.14f
        anims.add(Anim.breathe(this, 0.5f, 1f, 700))
    }

    /** One stat cell inside the results card: icon (optional), value, label. */
    private fun cell(icon: Drawable?, value: CharSequence, label: String, color: Int = Theme.WHITE): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        clipChildren = false; clipToPadding = false
        setPadding(dp(6f), dp(10f), dp(6f), dp(10f))
        if (icon != null) addView(ImageView(activity).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(26f), dp(26f)).apply { bottomMargin = dp(4f) })
        addView(kit.stageText(value, 22f, color, stroke = 2.5f).apply { maxLines = 1 })
        addView(kit.text(label.uppercase(), 10f, Theme.alpha(Theme.WHITE, 200), 700).apply { letterSpacing = 0.1f; maxLines = 1 })
    }

    /** The glass the results sit on. */
    private fun glass(): android.graphics.drawable.Drawable = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = dpf(26f); setColor(Theme.alpha(Theme.WHITE, 34)); setStroke(dp(1.5f), Theme.alpha(Theme.WHITE, 80))
    }

    // ------------------------------------------------------------- 1. results

    private var counting = true
    private var scoreAnim: ValueAnimator? = null
    private var coinAnim: ValueAnimator? = null
    private lateinit var scoreText: TextView
    private lateinit var coinText: TextView
    private lateinit var stars: LinearLayout
    private var record: TextView? = null

    /** Stars out of five, by score — the rating, no words needed. */
    private fun starCount(): Int = when {
        score >= 300 -> 5
        score >= 180 -> 4
        score >= 100 -> 3
        score >= 40 -> 2
        else -> 1
    }

    /** The sunburst's hue by rating (yellow for a record). */
    private fun rayHue(): Float = if (isNewBest) 48f else when (starCount()) { 5 -> 330f; 4 -> 265f; 3 -> 200f; 2 -> 160f; else -> 28f }

    private fun showResults() {
        Stage.resultHue = rayHue()
        Stage.resultRecord = isNewBest
        Stage.mode = Stage.RESULT // the engine poses your cube up top, the sunburst behind it
        val host = FrameLayout(activity).apply {
            isClickable = true
            clipChildren = false; clipToPadding = false
            setOnClickListener { if (counting) finishCount() else next() }
        }
        if (isNewBest) host.addView(CelebrationView(activity, focusY = 0.17f, rays = false, count = 160), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // the column: (NEW RECORD!) the score, then ONE card with the stars and the stats
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false; clipToPadding = false
        }
        if (isNewBest) { // the one line of words worth having
            val r = kit.stageText("NEW RECORD!", 30f, Theme.YELLOW, stroke = 4.5f).apply { letterSpacing = 0.06f; alpha = 0f }
            record = r
            column.addView(r)
        }
        scoreText = kit.stageText("0", 104f, stroke = 11f)
        column.addView(scoreText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = -dp(14f) })
        scoreAnim = Anim.countUp(scoreText, score, 1300)

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false; clipToPadding = false
            background = glass()
            setPadding(dp(16f), dp(14f), dp(16f), dp(10f))
        }
        stars = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false; clipToPadding = false
            val n = starCount()
            for (i in 0 until 5) {
                addView(ImageView(activity).apply { setImageDrawable(StarIcon(Theme.YELLOW, i < n)); alpha = 0f },
                    LinearLayout.LayoutParams(dp(if (i == 2) 44f else 36f), dp(if (i == 2) 44f else 36f)).apply { leftMargin = dp(3f); rightMargin = dp(3f); gravity = Gravity.CENTER_VERTICAL })
            }
        }
        card.addView(stars, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48f)))
        card.addView(View(activity).apply { background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dpf(2f); setColor(Theme.alpha(Theme.WHITE, 70)) } },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2f)).apply { topMargin = dp(10f); leftMargin = dp(10f); rightMargin = dp(10f) })
        val stats = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false; clipToPadding = false
            val coinCell = cell(CoinIcon(), "+0", "coins", Theme.YELLOW)
            coinText = coinCell.getChildAt(1) as TextView
            addView(coinCell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        card.addView(stats, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f) })
        if (shards.any { it > 0 }) {
            val collected = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                for (kind in cube.run.data.Shards.all) {
                    val count = shards.getOrElse(kind.id) { 0 }
                    if (count > 0) addView(kit.iconPill(ShardIcon(Theme.hsv(kind.hue, .6f, 1f)), "+$count", Theme.WHITE, 16f,
                        fill = Theme.alpha(Theme.WHITE, 25)).apply { contentDescription = "$count ${kind.name} collected" },
                        LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(5f); rightMargin = dp(5f) })
                }
            }
            card.addView(collected, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })
        }
        column.addView(card, LinearLayout.LayoutParams(dp(300f), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
        later(700) { if (counting) coinAnim = Anim.countUp(coinText, coins, 900) { "+$it" } }
        later(1700) { counting = false }
        later(1350) { if (counting) slamStamp() }

        host.addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP; topMargin = dp(200f) // the stage frames the cube at 156 dp
        })
        host.addView(tapHint(if (boxes > 0) "TAP TO CONTINUE" else "TAP FOR THE MENU"), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; bottomMargin = dp(32f)
        })
        swap(host)
        record?.let { r -> Anim.popIn(r, 100, 0.3f, 480) { anims.add(Anim.heartbeat(r, 1.05f, 900)) } }
        Anim.popIn(scoreText, 160, 0.3f, 460)
        Anim.riseIn(card, 420, dpf(30f))
    }

    private var stamped = false

    /** The stars slam in one by one, each with a thump, the lit ones bigger. */
    private fun slamStamp() {
        if (stamped) return
        stamped = true
        val n = starCount()
        for (i in 0 until 5) {
            val v = stars.getChildAt(i)
            later(90L * i) {
                v.alpha = 1f; v.scaleX = 2.4f; v.scaleY = 2.4f
                v.move().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(Anim.spring).start()
                if (i < n) { SoundFx.play("pop", rate = 1f + i * 0.15f); Haptics.click() }
            }
        }
        later(620) { if (n == 5) anims.add(Anim.heartbeat(stars, 1.04f, 900)) }
        SoundFx.play(if (isNewBest) "success" else "perfect", rate = if (isNewBest) 1f else 0.9f); Haptics.heavy()
    }

    /** First tap: everything lands at its final value right away. */
    private fun finishCount() {
        counting = false
        scoreAnim?.cancel(); coinAnim?.cancel()
        scoreText.text = score.toString()
        coinText.text = "+$coins"
        slamStamp()
        SoundFx.play("tap"); Haptics.tick()
    }

    private fun next() {
        if (boxes > 0) showBoxes() else leave(onMenu)
    }

    // ------------------------------------------------------------- 2. boxes

    private var boxesLeft = boxes
    private var boxBusy = false
    private var boxRewardReady = false
    private var skipBoxAnimation = false
    private var boxReadyTask: Runnable? = null
    private var rewardCard: LinearLayout? = null
    private var rewardBig: TextView? = null
    private var rewardSub: TextView? = null
    private var boxHint: TextView? = null
    private var boxRack: LinearLayout? = null
    private var boxHost: FrameLayout? = null
    private var rewardSizeSp = 36f
    private var rewardLabel: (Float) -> CharSequence = { "" }

    /** Fit the actual glyphs and icon together, including Android's non-linear font scaling. */
    private fun fitBoxText(view: TextView, width: Int, sizeSp: Float, label: (Float) -> CharSequence = { view.text }) {
        val available = (width - view.compoundPaddingLeft - view.compoundPaddingRight - dp(3f)).coerceAtLeast(1)
        val paint = TextPaint(view.paint)
        val nominal = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, view.resources.displayMetrics)
        fun fits(px: Float): Boolean {
            paint.textSize = px
            return Layout.getDesiredWidth(label(px), paint) <= available
        }
        var low = 1f
        var high = nominal
        if (fits(nominal)) low = nominal else repeat(16) {
            val candidate = (low + high) / 2f
            if (fits(candidate)) low = candidate else high = candidate
        }
        val resized = kotlin.math.abs(view.textSize - low) > .01f
        if (resized) view.setTextSize(TypedValue.COMPLEX_UNIT_PX, low)
        val text = label(low)
        if (resized || view.text.toString() != text.toString()) view.text = text
    }

    private fun rewardWithIcon(amount: String, px: Float, icon: Drawable): CharSequence {
        val size = (px * 1.15f).toInt().coerceAtLeast(1)
        icon.setBounds(0, 0, size, size)
        return SpannableStringBuilder("\u2009 \u2009").apply {
            setSpan(CenteredImageSpan(icon), 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(amount)
        }
    }

    private fun showBoxes() {
        stopEffects()
        val host = FrameLayout(activity).apply {
            isClickable = true
            clipChildren = false; clipToPadding = false
            setOnClickListener { tapBox() }
        }
        boxHost = host
        lateinit var heading: TextView
        val top = object : LinearLayout(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                fitBoxText(heading, MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight, 28f)
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20f), 0, dp(20f), 0)
            clipChildren = false; clipToPadding = false
            heading = kit.stageText(if (boxes == 1) "MYSTERY BOX" else "MYSTERY BOXES", 28f, Theme.LAVENDER, stroke = 4f).apply {
                letterSpacing = 0.06f; setSingleLine(); setHorizontallyScrolling(false)
            }
            addView(heading, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            boxRack = LinearLayout(activity).apply { // one icon per box; opened ones go quiet
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false; clipToPadding = false
                for (i in 0 until boxes) addView(ImageView(activity).apply { setImageDrawable(BoxIcon()) }, LinearLayout.LayoutParams(dp(24f), dp(24f)).apply { leftMargin = dp(3f); rightMargin = dp(3f) })
            }
            addView(boxRack, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f) })
        }
        host.addView(top, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.TOP; topMargin = dp(24f) })
        val bottom = object : LinearLayout(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val contentWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
                boxHint?.let { fitBoxText(it, contentWidth, 14f) }
                rewardCard?.layoutParams?.width = minOf(contentWidth, dp(360f))
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // Room for the spring and reward heartbeat without clipping the card's corners.
            setPadding(dp(20f), 0, dp(20f), 0)
            clipChildren = false; clipToPadding = false
            rewardCard = object : LinearLayout(activity) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val contentWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
                    rewardSub?.let { fitBoxText(it, contentWidth, 12f) }
                    rewardBig?.let { fitBoxText(it, contentWidth, rewardSizeSp, rewardLabel) }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                }
            }.apply {
                tag = "gift_reward_card"
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(18f), dp(14f), dp(18f), dp(14f) + kit.CARD_LIP)
                background = kit.cardDrawable(Theme.CARD, null, 26f)
                alpha = 0f
                rewardSub = kit.text("", 12f, Theme.MUTED, 700).apply {
                    tag = "gift_reward_category"; letterSpacing = 0.1f; setSingleLine(); setHorizontallyScrolling(false)
                }
                rewardBig = kit.text("", 36f, Theme.INK, 700).apply {
                    tag = "gift_reward_value"; setSingleLine(); setHorizontallyScrolling(false)
                }
                addView(rewardSub, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                addView(rewardBig, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }
            addView(rewardCard, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            boxHint = tapHint("TAP TO OPEN").apply {
                tag = "gift_footer_hint"; letterSpacing = .08f; setSingleLine(); setHorizontallyScrolling(false)
            }
            // A reserved footer keeps the reward in place as the next-action wording changes.
            addView(boxHint, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48f)).apply { topMargin = dp(12f) })
        }
        host.addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM; bottomMargin = dp(16f) })
        Stage.openRequests.set(0)
        Stage.skipBoxRequests.set(0)
        Stage.mode = Stage.BOX
        swap(host)
        Anim.popIn(top, 120, 0.6f)
    }

    /** A tap opens the next box; once the last one is open, a tap moves on. */
    private fun tapBox() {
        if (boxBusy) {
            skipBoxAnimation = true
            Stage.skipBoxRequests.incrementAndGet()
            if (boxRewardReady) finishBoxAnimation()
            return
        }
        if (boxesLeft <= 0) { leave(onMenu); return }
        boxBusy = true
        boxRewardReady = false; skipBoxAnimation = false
        rewardBeat?.cancel(); rewardBeat = null
        boxesLeft--
        boxHint?.visibility = INVISIBLE
        rewardCard?.move()?.alpha(0f)?.scaleX(0.7f)?.scaleY(0.7f)?.setDuration(150)?.start()
        Stage.openRequests.incrementAndGet() // the game shakes + opens it, then calls onBoxOpened
    }

    /** The 3D stage just opened a box: pop the reward card in. */
    fun onBoxOpened(kind: Int, amount: Int, cat: Int, id: Int) {
        if (leaving || boxRewardReady) return
        boxRewardReady = true
        val big = rewardBig ?: return
        val sub = rewardSub ?: return
        big.setTextColor(Theme.INK)
        rewardSizeSp = 36f
        val rare: Boolean
        when (kind) {
            Progress.BoxReward.SKIN -> {
                rare = true
                sub.text = "NEW ${Wardrobe.label(cat)}!"
                sub.setTextColor(Theme.darken(Theme.PINK, .35f))
                val name = Wardrobe.name(cat, id).uppercase()
                rewardLabel = { name }
            }
            Progress.BoxReward.SHARDS -> {
                val k = cube.run.data.Shards.get(id)
                val col = Theme.darken(Theme.hsv(k.hue, 0.7f, 0.9f), .45f)
                rare = amount >= 20
                sub.text = k.name.uppercase()
                sub.setTextColor(col)
                rewardLabel = { px -> rewardWithIcon("+$amount", px, ShardIcon(Theme.hsv(k.hue, 0.75f, 1f))) }
            }
            Progress.BoxReward.BUBBLE -> {
                rare = false
                sub.text = "BUBBLE SHIELD"
                sub.setTextColor(Theme.darken(Theme.CYAN, .5f))
                rewardLabel = { "+$amount" }
            }
            else -> {
                rare = amount >= 100
                sub.text = ""
                rewardSizeSp = if (rare) 44f else 36f
                rewardLabel = { px -> rewardWithIcon("+$amount", px, CoinIcon()) }
            }
        }
        sub.visibility = if (sub.text.isEmpty()) GONE else VISIBLE
        // Assign now for accessibility; measurement then fits the same label and icon as one unit.
        big.text = rewardLabel(big.textSize)
        rewardCard?.requestLayout()
        rewardCard?.let { c ->
            rewardBeat?.cancel(); rewardBeat = null
            Anim.popIn(c, 0, 0.3f, 460) {
                if (rare) rewardBeat = Anim.heartbeat(c, 1.04f, 800)
            }
        }
        if (rare) boxHost?.addView(CelebrationView(activity, focusY = 0.55f, rays = false, count = 140, burst = true, seconds = 3f), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        boxRack?.let { r -> // the box just opened dims
            val opened = boxes - boxesLeft - 1
            r.getChildAt(opened)?.move()?.alpha(0.3f)?.scaleX(0.8f)?.scaleY(0.8f)?.setDuration(300)?.start()
        }
        if (skipBoxAnimation) finishBoxAnimation()
        else {
            boxReadyTask = Runnable { finishBoxAnimation() }.also { postDelayed(it, 900) }
        }
    }

    private fun finishBoxAnimation() {
        boxReadyTask?.let { removeCallbacks(it) }; boxReadyTask = null
        rewardBeat?.cancel(); rewardBeat = null
        rewardCard?.let { Anim.reset(it) }
        boxHost?.let { host ->
            for (i in host.childCount - 1 downTo 0) if (host.getChildAt(i) is CelebrationView) host.removeViewAt(i)
        }
        boxBusy = false
        boxHint?.text = if (boxesLeft > 0) "TAP FOR THE NEXT BOX" else if (boxesOnly) "TAP TO RETURN" else "TAP FOR THE MENU"
        boxHint?.visibility = VISIBLE
    }

    // Start only after all animation handles and result/box state are initialized.
    init {
        isClickable = true
        clipChildren = false; clipToPadding = false
        setPadding(0, dp(36f), 0, dp(24f))
        setOnApplyWindowInsetsListener { _, insets ->
            val (_, t, _, b) = insetsOf(insets)
            setPadding(0, maxOf(dp(36f), t + dp(6f)), 0, maxOf(dp(24f), b + dp(8f)))
            insets
        }
        if (boxesOnly) showBoxes() else showResults()
    }

    override fun onDetachedFromWindow() {
        stopEffects()
        super.onDetachedFromWindow()
    }
}
