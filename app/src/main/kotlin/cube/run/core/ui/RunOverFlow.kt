package cube.run.core.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import cube.run.core.Haptics
import cube.run.core.Palette
import cube.run.core.Progress
import cube.run.core.Stage
import cube.run.core.SoundFx

/**
 * The run-over sequence, one focused screen at a time:
 *  1. the score — counts up, celebrates a record ([CelebrationView]), shows the coin haul;
 *  2. the mystery boxes (only if any were collected) — the game renders the box in
 *     3D ([Stage]); this page is just the framing, the tap, and the reward text;
 *  3. the exit — bank / best / bubbles and RESTART / EXIT.
 *
 * Programmatic single-locale game UI, so SetTextI18n / ViewConstructor are
 * intentionally suppressed.
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
    private val onRestart: () -> Unit,
    private val onExit: () -> Unit,
) : FrameLayout(activity) {

    private fun dp(v: Float) = kit.dp(v)
    private var page: View? = null
    private var boxesLeft = boxes
    private var boxBusy = false
    private var rewardText: TextView? = null
    private var boxHint: TextView? = null
    private var boxCount: TextView? = null
    private var boxContinue: View? = null
    private val anims = ArrayList<ValueAnimator>()

    init {
        isClickable = true // swallow touches so the (dead) game never sees them
        showScore()
    }

    // ------------------------------------------------------------- transitions

    /** Slide the old page out to the left, the new one in from the right. */
    private fun swap(next: View) {
        page?.let { old ->
            old.animate().alpha(0f).translationX(-dp(60f).toFloat()).setDuration(180)
                .withEndAction { removeView(old) }.start()
        }
        page = next
        next.alpha = 0f
        next.translationX = dp(60f).toFloat()
        addView(next, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        next.animate().alpha(1f).translationX(0f).setDuration(260).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun cardHost(child: View, scrim: Boolean, width: Int = dp(300f)): FrameLayout = FrameLayout(activity).apply {
        if (scrim) setBackgroundColor(Ui.SCRIM)
        addView(child, LayoutParams(width, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
    }

    private fun countUp(t: TextView, prefix: String, to: Int, ms: Long, tickEvery: Int = 4) {
        if (to <= 0) { t.text = "$prefix$to"; return }
        var lastTick = -1
        anims.add(ValueAnimator.ofInt(0, to).apply {
            duration = ms
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener { a ->
                val v = a.animatedValue as Int
                t.text = "$prefix$v"
                if (v / tickEvery != lastTick) { lastTick = v / tickEvery; SoundFx.play("tick", rate = 1.2f + 0.6f * v / to, vol = 0.35f) }
            }
            start()
        })
    }

    // ------------------------------------------------------------- 1. score

    private fun showScore() {
        val host = FrameLayout(activity).apply { setBackgroundColor(Ui.SCRIM) }
        if (isNewBest) host.addView(CelebrationView(activity, kit.accent), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val card = kit.card(if (isNewBest) Ui.GOLD else kit.accent).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24f), dp(24f), dp(24f), dp(22f))
        }
        val title = kit.text(if (isNewBest) "NEW RECORD!" else "RUN OVER", if (isNewBest) 30f else 26f,
            if (isNewBest) Ui.GOLD_INK else Ui.INK, heavy = true).apply { letterSpacing = 0.04f }
        card.addView(title)
        if (isNewBest) {
            title.scaleX = 0.4f; title.scaleY = 0.4f
            title.animate().scaleX(1f).scaleY(1f).setDuration(520).setInterpolator(OvershootInterpolator(2.6f)).start()
            anims.add(ValueAnimator.ofFloat(1f, 1.06f, 1f).apply {
                duration = 900; repeatCount = ValueAnimator.INFINITE; startDelay = 600
                addUpdateListener { a -> val s = a.animatedValue as Float; title.scaleX = s; title.scaleY = s }
                start()
            })
        }
        val scoreText = kit.text("0", 68f, Ui.INK, heavy = true)
        card.addView(scoreText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f) })
        countUp(scoreText, "", score, 1300)
        card.addView(kit.text(if (isNewBest) "previous best $best" else "BEST $best", 15f, Ui.MUTED),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2f) })

        val coinText = kit.text("${Ui.COIN} +0", 24f, Ui.GOLD_INK, heavy = true)
        card.addView(kit.tile().apply {
            setPadding(0, dp(12f), 0, dp(12f))
            addView(coinText)
            addView(kit.text("coins collected", 12f, Ui.MUTED))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18f) })
        postDelayed({ countUp(coinText, "${Ui.COIN} +", coins, 900, tickEvery = 2) }, 700)

        if (boxes > 0) {
            card.addView(kit.tile(Palette.withAlpha(Ui.PURPLE, 28)).apply {
                setPadding(0, dp(12f), 0, dp(12f))
                addView(kit.text("▣ ×$boxes", 24f, Ui.PURPLE, heavy = true))
                addView(kit.text(if (boxes == 1) "mystery box to open" else "mystery boxes to open", 12f, Ui.MUTED))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10f) })
        }

        card.addView(kit.button(if (boxes > 0) "OPEN BOXES" else "CONTINUE", UiKit.Style.FILLED) {
            if (boxes > 0) showBoxes() else showEnd()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(22f) })

        host.addView(card, LayoutParams(dp(300f), LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        swap(host)
    }

    // ------------------------------------------------------------- 2. boxes

    private fun pill(text: String, size: Float, color: Int) = kit.text(text, size, color, heavy = true).apply {
        setPadding(dp(22f), dp(10f), dp(22f), dp(10f))
        background = GradientDrawable().apply { cornerRadius = dp(24f).toFloat(); setColor(Ui.CARD) }
    }

    private fun showBoxes() {
        // no scrim: the game draws the box itself, this page only frames it
        val host = FrameLayout(activity).apply {
            isClickable = true
            setOnClickListener { tapBox() }
        }
        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(pill("MYSTERY BOX", 22f, Ui.PURPLE), LinearLayout.LayoutParams(dp(240f), LinearLayout.LayoutParams.WRAP_CONTENT))
            boxCount = kit.text(leftText(), 14f, Ui.CARD).also { it.setShadowLayer(dp(4f).toFloat(), 0f, dp(1f).toFloat(), 0xA0000000.toInt()) }
            addView(boxCount, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f) })
        }
        host.addView(top, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; topMargin = dp(72f)
        })
        val bottom = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            rewardText = kit.text("", 34f, Ui.GOLD, heavy = true).also {
                it.setShadowLayer(dp(8f).toFloat(), 0f, dp(2f).toFloat(), 0xB0000000.toInt())
                it.alpha = 0f
            }
            addView(rewardText)
            boxHint = pill("TAP TO OPEN", 16f, Ui.INK)
            addView(boxHint, LinearLayout.LayoutParams(dp(200f), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16f) })
            boxContinue = kit.button("CONTINUE", UiKit.Style.FILLED) { showEnd() }.apply { visibility = GONE }
            addView(boxContinue, LinearLayout.LayoutParams(dp(220f), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16f) })
        }
        host.addView(bottom, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; bottomMargin = dp(56f)
        })
        anims.add(ValueAnimator.ofFloat(1f, 1.08f).apply { // the hint breathes
            duration = 600; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { a -> val s = a.animatedValue as Float; boxHint?.scaleX = s; boxHint?.scaleY = s }
            start()
        })
        Stage.mode = Stage.BOX
        swap(host)
    }

    private fun leftText() = when (boxesLeft) {
        0 -> "all opened"
        1 -> "1 box left"
        else -> "$boxesLeft boxes left"
    }

    private fun tapBox() {
        if (boxBusy || boxesLeft <= 0) return
        boxBusy = true
        boxesLeft--
        boxHint?.animate()?.alpha(0f)?.setDuration(120)?.start()
        rewardText?.animate()?.alpha(0f)?.setDuration(150)?.start()
        Stage.openRequests.incrementAndGet() // the game shakes + opens it, then calls onBoxOpened
    }

    /** The 3D stage just opened a box: pop the reward in. */
    fun onBoxOpened(kind: Int, amount: Int) {
        val bubble = kind == Progress.BoxReward.BUBBLE
        rewardText?.apply {
            text = if (bubble) "+$amount BUBBLE" else "+$amount ${Ui.COIN}"
            setTextColor(if (bubble) 0xFF7DF3FF.toInt() else Ui.GOLD)
            alpha = 1f; scaleX = 0.3f; scaleY = 0.3f
            animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(OvershootInterpolator(2.6f)).start()
        }
        boxCount?.text = leftText()
        postDelayed({
            boxBusy = false
            if (boxesLeft > 0) boxHint?.animate()?.alpha(1f)?.setDuration(200)?.start()
            else boxContinue?.apply { visibility = VISIBLE; alpha = 0f; animate().alpha(1f).setDuration(240).start() }
        }, 1400)
    }

    // ------------------------------------------------------------- 3. exit

    private fun showEnd() {
        Stage.mode = Stage.NONE
        val card = kit.card().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24f), dp(24f), dp(24f), dp(22f))
        }
        card.addView(kit.text("GO AGAIN?", 26f, Ui.INK, heavy = true).apply { letterSpacing = 0.04f })
        card.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            fun stat(v: String, label: String, color: Int) = kit.tile().apply {
                setPadding(0, dp(10f), 0, dp(10f))
                addView(kit.text(v, 19f, color, heavy = true))
                addView(kit.text(label, 11f, Ui.MUTED))
            }
            addView(stat(maxOf(best, score).toString(), "best", Ui.INK), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(stat("${Ui.COIN} ${Progress.coins}", "bank", Ui.GOLD_INK), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8f) })
            addView(stat("◯ ×${Progress.bubbles}", "bubbles", Ui.CYAN), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8f) })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18f) })
        card.addView(kit.button("RESTART", UiKit.Style.FILLED) { onRestart() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(22f) })
        card.addView(kit.button("EXIT", UiKit.Style.OUTLINE) { onExit() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10f) })
        SoundFx.play("tap"); Haptics.tick()
        swap(cardHost(card, scrim = true))
    }

    override fun onDetachedFromWindow() {
        for (a in anims) a.cancel()
        anims.clear()
        super.onDetachedFromWindow()
    }
}
