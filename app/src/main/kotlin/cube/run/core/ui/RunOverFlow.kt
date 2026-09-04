package cube.run.core.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import cube.run.core.Haptics
import cube.run.core.Palette
import cube.run.core.Progress
import cube.run.core.SoundFx
import cube.run.core.Stage

/**
 * The run-over sequence, full screen, one focused page at a time on the
 * engine's dark stage:
 *  1. the results — your cube (the skin you ran with) posed up top, the score
 *     counting up beneath it, a record celebration, the coin haul. One tap
 *     skips the count, the next tap moves on;
 *  2. the mystery boxes (only if any were collected) — the game renders the
 *     box in 3D ([Stage.BOX]); a tap opens one, the next tap brings the next
 *     box (already opening) or, after the last, moves on;
 *  3. go again — best / bank / bubbles, RESTART (straight into a new run) or MENU.
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
    private val onMenu: () -> Unit,
) : FrameLayout(activity) {

    private fun dp(v: Float) = kit.dp(v)
    private var page: View? = null
    private val anims = ArrayList<ValueAnimator>()

    init {
        isClickable = true // swallow touches so the (dead) game never sees them
        setPadding(0, dp(36f), 0, dp(24f))
        setOnApplyWindowInsetsListener { _, insets ->
            val top: Int; val bottom: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val all = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                top = all.top; bottom = all.bottom
            } else {
                @Suppress("DEPRECATION") top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION") bottom = insets.systemWindowInsetBottom
            }
            setPadding(0, maxOf(dp(36f), top + dp(6f)), 0, maxOf(dp(24f), bottom + dp(8f)))
            insets
        }
        showResults()
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

    private fun countUp(t: TextView, prefix: String, to: Int, ms: Long, tickEvery: Int = 4): ValueAnimator? {
        if (to <= 0) { t.text = "$prefix$to"; return null }
        var lastTick = -1
        return ValueAnimator.ofInt(0, to).apply {
            duration = ms
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener { a ->
                val v = a.animatedValue as Int
                t.text = "$prefix$v"
                if (v / tickEvery != lastTick) { lastTick = v / tickEvery; SoundFx.play("tick", rate = 1.2f + 0.6f * v / to, vol = 0.35f) }
            }
            anims.add(this)
            start()
        }
    }

    /** A translucent white pill with a value and a label, for the stat rows. */
    private fun statPill(value: String, label: String, color: Int): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
        background = GradientDrawable().apply { cornerRadius = dp(18f).toFloat(); setColor(Palette.withAlpha(Color.WHITE, 28)) }
        addView(kit.stageText(value, 20f, color, heavy = true))
        addView(kit.stageText(label, 11f, Palette.withAlpha(Color.WHITE, 170)))
    }

    /** A soft "tap to continue" that breathes at the bottom of a page (hide it with visibility, not alpha). */
    private fun tapHint(text: String): TextView = kit.stageText(text, 14f, Palette.withAlpha(Color.WHITE, 200)).apply {
        letterSpacing = 0.1f
        anims.add(ValueAnimator.ofFloat(0.5f, 1f).apply {
            duration = 700; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { a -> alpha = a.animatedValue as Float }
            start()
        })
    }

    // ------------------------------------------------------------- 1. results

    private var counting = true
    private var scoreAnim: ValueAnimator? = null
    private var coinAnim: ValueAnimator? = null
    private lateinit var scoreText: TextView
    private lateinit var coinText: TextView

    private fun showResults() {
        Stage.mode = Stage.RESULT // the engine poses your cube in the top third
        val host = FrameLayout(activity).apply {
            isClickable = true
            setOnClickListener { if (counting) finishCount() else next() }
        }
        if (isNewBest) host.addView(CelebrationView(activity, kit.accent), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = kit.stageText(if (isNewBest) "NEW RECORD!" else "RUN OVER", if (isNewBest) 32f else 26f,
            if (isNewBest) Ui.GOLD else Color.WHITE, heavy = true).apply { letterSpacing = 0.06f }
        column.addView(title)
        if (isNewBest) {
            title.scaleX = 0.4f; title.scaleY = 0.4f
            title.animate().scaleX(1f).scaleY(1f).setDuration(520).setInterpolator(OvershootInterpolator(2.6f)).start()
            anims.add(ValueAnimator.ofFloat(1f, 1.06f, 1f).apply {
                duration = 900; repeatCount = ValueAnimator.INFINITE; startDelay = 600
                addUpdateListener { a -> val s = a.animatedValue as Float; title.scaleX = s; title.scaleY = s }
                start()
            })
        }
        scoreText = kit.stageText("0", 80f, heavy = true)
        column.addView(scoreText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2f) })
        scoreAnim = countUp(scoreText, "", score, 1300)
        column.addView(kit.stageText(if (isNewBest) "previous best $best" else "BEST $best", 16f, Palette.withAlpha(Color.WHITE, 190)),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2f) })

        coinText = kit.stageText("${Ui.COIN} +0", 22f, Ui.GOLD, heavy = true)
        column.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dp(18f), dp(10f), dp(18f), dp(10f))
                background = GradientDrawable().apply { cornerRadius = dp(18f).toFloat(); setColor(Palette.withAlpha(Color.WHITE, 28)) }
                addView(coinText)
                addView(kit.stageText("coins", 11f, Palette.withAlpha(Color.WHITE, 170)))
            })
            if (boxes > 0) addView(statPill("▣ ×$boxes", if (boxes == 1) "mystery box" else "mystery boxes", Ui.PURPLE_LIGHT),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(10f) })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(22f) })
        postDelayed({
            if (counting) coinAnim = countUp(coinText, "${Ui.COIN} +", coins, 900, tickEvery = 2)
        }, 700)
        postDelayed({ counting = false }, 1700)

        host.addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER; topMargin = dp(150f) // the cube sits above this block
        })
        host.addView(tapHint("TAP TO CONTINUE"), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; bottomMargin = dp(32f)
        })
        swap(host)
    }

    /** First tap: everything lands at its final value right away. */
    private fun finishCount() {
        counting = false
        scoreAnim?.cancel(); coinAnim?.cancel()
        scoreText.text = score.toString()
        coinText.text = "${Ui.COIN} +$coins"
        SoundFx.play("tap"); Haptics.tick()
    }

    private fun next() {
        if (boxes > 0) showBoxes() else showEnd()
    }

    // ------------------------------------------------------------- 2. boxes

    private var boxesLeft = boxes
    private var boxBusy = false
    private var rewardText: TextView? = null
    private var boxHint: TextView? = null
    private var boxCount: TextView? = null

    private fun showBoxes() {
        // no scrim: the game draws the box itself, this page only frames it
        val host = FrameLayout(activity).apply {
            isClickable = true
            setOnClickListener { tapBox() }
        }
        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(kit.stageText("MYSTERY BOX", 26f, Ui.PURPLE_LIGHT, heavy = true).apply { letterSpacing = 0.06f })
            boxCount = kit.stageText(leftText(), 14f, Palette.withAlpha(Color.WHITE, 190))
            addView(boxCount, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f) })
        }
        host.addView(top, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { // full width: a wrapped spaced title clips
            gravity = Gravity.TOP; topMargin = dp(24f)
        })
        val bottom = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            rewardText = kit.stageText("", 36f, Ui.GOLD, heavy = true).also { it.alpha = 0f }
            addView(rewardText)
            boxHint = tapHint("TAP TO OPEN")
            addView(boxHint, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18f) })
        }
        host.addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM; bottomMargin = dp(32f)
        })
        Stage.mode = Stage.BOX
        swap(host)
    }

    private fun leftText() = when (boxesLeft) {
        0 -> "all opened"
        1 -> "1 to open"
        else -> "$boxesLeft to open"
    }

    /** A tap opens the next box; once the last one is open, a tap moves on. */
    private fun tapBox() {
        if (boxBusy) return
        if (boxesLeft <= 0) { showEnd(); return }
        boxBusy = true
        boxesLeft--
        boxHint?.visibility = INVISIBLE
        rewardText?.animate()?.alpha(0f)?.setDuration(150)?.start()
        Stage.openRequests.incrementAndGet() // the game shakes + opens it, then calls onBoxOpened
    }

    /** The 3D stage just opened a box: pop the reward in. */
    fun onBoxOpened(kind: Int, amount: Int) {
        val bubble = kind == Progress.BoxReward.BUBBLE
        rewardText?.apply {
            text = if (bubble) "+$amount BUBBLE" else "+$amount ${Ui.COIN}"
            setTextColor(if (bubble) Ui.CYAN_LIGHT else Ui.GOLD)
            alpha = 1f; scaleX = 0.3f; scaleY = 0.3f
            animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(OvershootInterpolator(2.6f)).start()
        }
        boxCount?.text = leftText()
        postDelayed({
            boxBusy = false
            boxHint?.text = if (boxesLeft > 0) "TAP FOR THE NEXT BOX" else "TAP TO CONTINUE"
            boxHint?.visibility = VISIBLE
        }, 900)
    }

    // ------------------------------------------------------------- 3. go again

    private fun showEnd() {
        Stage.mode = Stage.RESULT // your cube is back up top
        val host = FrameLayout(activity)
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(kit.stageText("GO AGAIN?", 32f, heavy = true).apply { letterSpacing = 0.06f })
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(statPill(maxOf(best, score).toString(), "best", Color.WHITE))
                addView(statPill("${Ui.COIN} ${Progress.coins}", "bank", Ui.GOLD), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8f) })
                addView(statPill("◯ ×${Progress.bubbles}", "bubbles", Ui.CYAN_LIGHT), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8f) })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18f) })
            addView(kit.button("RESTART", UiKit.Style.FILLED) { onRestart() },
                LinearLayout.LayoutParams(dp(240f), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(30f) })
            addView(kit.button("MENU", UiKit.Style.LIGHT) { onMenu() },
                LinearLayout.LayoutParams(dp(240f), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
        }
        host.addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER; topMargin = dp(150f)
        })
        SoundFx.play("tap"); Haptics.tick()
        swap(host)
    }

    override fun onDetachedFromWindow() {
        for (a in anims) a.cancel()
        anims.clear()
        super.onDetachedFromWindow()
    }
}
