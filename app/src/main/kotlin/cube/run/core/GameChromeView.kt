package cube.run.core

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import cube.run.R
import cube.run.core.ui.PauseMenu
import cube.run.core.ui.PreRunMenu
import cube.run.core.ui.RunOverFlow
import cube.run.core.ui.SectionsView
import cube.run.core.ui.ShopView
import cube.run.core.ui.SkinsView
import cube.run.core.ui.Ui
import cube.run.core.ui.UiKit
import cube.run.game.Sections

/**
 * The game's HUD overlay: score / best / coins / boxes at the top, the pause
 * button, the "tap to start" prompt (with a test-mode notice when a test
 * tool is on), and the full-screen pages it hosts — the [PreRunMenu]'s
 * [ShopView], [SkinsView] and [SectionsView], the [PauseMenu] and the
 * [RunOverFlow].
 * Must only be touched from the UI thread (GameHostSession marshals for you).
 *
 * Created programmatically (never inflated from XML) and shows dynamic,
 * single-locale game text, so ViewConstructor / SetTextI18n are suppressed.
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class GameChromeView(private val activity: Activity, accent: Int) : FrameLayout(activity) {

    companion object {
        /** Intent extra: begin the run on the first frame (RESTART). */
        const val EXTRA_AUTOSTART = "autostart"
    }

    private val kit = UiKit(activity, accent)
    private fun dp(v: Float) = kit.dp(v)
    private fun shadow(t: TextView, r: Float = 6f) = t.setShadowLayer(dp(r).toFloat(), 0f, dp(2f).toFloat(), 0x90000000.toInt())

    private val scoreText = kit.text("0", 52f, Color.WHITE, heavy = true).also { shadow(it) }
    private val bestText = kit.text("", 15f, Palette.withAlpha(Color.WHITE, 190)).also { shadow(it, 4f) }
    // Coin bank (pre-run) / bank + this run's haul (in-run), gold; mystery boxes beside it.
    private val coinText = kit.text("", 17f, Ui.GOLD).also { shadow(it, 4f) }
    private val boxText = kit.text("", 17f, Ui.PURPLE_LIGHT).also { shadow(it, 4f); it.visibility = GONE }
    // Bubble stock + how to use it. Shown whenever at least one is in stock.
    private val bubbleText = kit.text("", 13f, Ui.CYAN_LIGHT).also { shadow(it, 4f); it.visibility = GONE }
    // Low-key prompt shown (softly pulsing) until the first touch starts a run.
    private val startHint = kit.text("Tap to start", 22f, Palette.withAlpha(Color.WHITE, 180)).also { shadow(it) }
    // Amber notice under the prompt while a test tool (section test / dev mode) is on.
    private val testPill = kit.text("", 12f, Ui.INK).apply {
        setPadding(dp(14f), dp(6f), dp(14f), dp(6f))
        background = GradientDrawable().apply { cornerRadius = dp(16f).toFloat(); setColor(0xFFFFC14A.toInt()) }
        visibility = GONE
    }
    private val pauseBtn = kit.iconButton(R.drawable.ic_pause, Color.WHITE, activity.getString(R.string.cd_pause)) { pause() }.apply { visibility = GONE }

    private var bankAtStart = Progress.coins
    private var runCoins = 0
    private var score = 0
    private var runStarted = false
    private var shop: ShopView? = null
    private var skins: SkinsView? = null
    private var sections: SectionsView? = null
    private var pauseMenu: PauseMenu? = null
    private var runOver: RunOverFlow? = null
    private var startPulse: ValueAnimator? = null
    private val topBox = LinearLayout(activity)
    private val menu = PreRunMenu(activity, kit, { openShop() }, { openSkins() }, { openSections() }, { bankAtStart = Progress.coins; refreshCoins(); refreshStartHint() })
    private fun panelOpen() = shop != null || skins != null || sections != null || runStarted

    init {
        isClickable = false
        isFocusable = false

        topBox.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false; clipToPadding = false // the pop animations scale past their bounds
            addView(scoreText)
            addView(bestText)
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false; clipToPadding = false
                addView(coinText)
                addView(boxText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(14f) })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2f) })
            addView(bubbleText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f) })
        }
        addView(topBox, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(48f) })
        addView(pauseBtn, LayoutParams(dp(44f), dp(44f)).apply { gravity = Gravity.TOP or Gravity.END; topMargin = dp(48f); rightMargin = dp(14f) })
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(startHint)
            addView(testPill, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        addView(menu, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        refreshCoins()
        setBubbles(Progress.bubbles)
        refreshStartHint()

        startPulse = ValueAnimator.ofFloat(0.45f, 0.8f).apply {
            duration = 1000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { a -> startHint.alpha = a.animatedValue as Float }
            start()
        }

        // Keep the top block + pause button clear of the status bar / cutout (the menu handles its own insets).
        setOnApplyWindowInsetsListener { _, insets ->
            val top: Int; val right: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val all = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                top = all.top; right = all.right
            } else {
                @Suppress("DEPRECATION") top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION") right = insets.systemWindowInsetRight
            }
            (topBox.layoutParams as LayoutParams).topMargin = maxOf(dp(48f), top + dp(10f))
            (pauseBtn.layoutParams as LayoutParams).apply { topMargin = maxOf(dp(48f), top + dp(10f)); rightMargin = dp(14f) + right }
            topBox.requestLayout(); pauseBtn.requestLayout()
            insets
        }
    }

    override fun onDetachedFromWindow() {
        startPulse?.cancel(); startPulse = null
        super.onDetachedFromWindow()
    }

    // ------------------------------------------------------------- pre-run pages

    /** The pre-run chrome steps aside while a full-screen page is up. */
    private fun showPreRun(show: Boolean) {
        val v = if (show) VISIBLE else INVISIBLE
        topBox.visibility = v; startHint.visibility = v; menu.visibility = v
        testPill.visibility = if (show && testPill.text.isNotEmpty()) VISIBLE else GONE
    }

    private fun openShop() {
        if (panelOpen()) return
        showPreRun(false)
        val v = ShopView(activity, kit) {
            shop = null
            showPreRun(true)
            bankAtStart = Progress.coins
            refreshCoins()
            setBubbles(Progress.bubbles)
        }
        shop = v
        addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun openSkins() {
        if (panelOpen()) return
        showPreRun(false)
        val v = SkinsView(activity, kit) {
            skins = null
            showPreRun(true)
            bankAtStart = Progress.coins
            refreshCoins()
        }
        skins = v
        addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun openSections() {
        if (panelOpen()) return
        showPreRun(false)
        val v = SectionsView(activity, kit) {
            sections = null
            refreshStartHint()
            showPreRun(true)
        }
        sections = v
        addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Says plainly when the next run is a test (one section on loop, or the dev review cycle). */
    private fun refreshStartHint() {
        val chosen = Sections.byId(Settings.testSection)
        testPill.text = when {
            chosen != null -> "SECTION TEST  ·  ${chosen.name} on loop"
            Settings.devMode -> "DEV MODE  ·  new sections on loop, unlimited coins"
            else -> ""
        }
        testPill.visibility = if (testPill.text.isNotEmpty() && !runStarted) VISIBLE else GONE
    }

    // ------------------------------------------------------------- HUD values

    fun setBest(best: Int) {
        bestText.text = if (best > 0) "BEST $best" else ""
    }

    private fun refreshCoins() {
        coinText.text = "${Ui.COIN} ${bankAtStart + runCoins}"
    }

    private fun pop(t: TextView, s: Float = 1.25f) {
        t.animate().cancel()
        t.scaleX = s; t.scaleY = s
        t.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
    }

    /** Coins collected this run: the counter shows bank + haul and pops on each pickup. */
    fun setRunCoins(v: Int) {
        runCoins = v
        refreshCoins()
        pop(coinText)
    }

    fun setBoxes(n: Int) {
        boxText.text = "▣ ×$n"
        boxText.visibility = if (n > 0) VISIBLE else GONE
        pop(boxText, 1.4f)
    }

    private var bubbleStock = 0

    /** Stock + the how-to before a run; just the stock (small, out of the way) during one. */
    fun setBubbles(n: Int) {
        bubbleStock = n
        bubbleText.text = if (runStarted) "◯ ×$n" else "◯ ×$n  ·  double-tap to use"
        bubbleText.visibility = if (n > 0) VISIBLE else GONE
    }

    /** Hide the "Tap to start" prompt and the pre-run menu once a run has begun; show the pause button. */
    fun hideOptions() {
        runStarted = true
        startPulse?.cancel(); startPulse = null
        shop?.let { removeView(it); shop = null }
        skins?.let { removeView(it); skins = null }
        sections?.let { removeView(it); sections = null }
        topBox.visibility = VISIBLE
        testPill.visibility = GONE
        setBubbles(bubbleStock)
        if (startHint.visibility == VISIBLE) {
            startHint.animate().alpha(0f).setDuration(160).withEndAction { startHint.visibility = GONE }.start()
        }
        menu.hide()
        pauseBtn.alpha = 0f; pauseBtn.visibility = VISIBLE
        pauseBtn.animate().alpha(1f).setDuration(200).start()
    }

    fun setScore(v: Int) {
        score = v
        scoreText.text = v.toString()
        pop(scoreText, 1.18f)
    }

    // ------------------------------------------------------------- pause

    /** Freeze the run under the pause menu. No-op unless a run is live. */
    fun pause() {
        if (!runStarted || runOver != null || pauseMenu != null) return
        Stage.paused = true
        pauseBtn.visibility = GONE
        topBox.visibility = INVISIBLE // the menu shows the score itself
        val v = PauseMenu(activity, kit, score,
            onResume = {
                pauseMenu = null
                Stage.paused = false
                pauseBtn.visibility = VISIBLE
                topBox.visibility = VISIBLE
            },
            onRestart = { relaunch(autoStart = true) },
            onMenu = { relaunch(autoStart = false) },
        )
        pauseMenu = v
        addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** The activity left the foreground mid-run (home, a call): pause so nothing is lost. */
    fun autoPause() = pause()

    // ------------------------------------------------------------- run over

    /**
     * RESTART / MENU = finish + relaunch (NOT recreate): libGDX only disposes GL
     * resources when the activity is truly finishing, so recreate() would leak
     * native meshes. The relaunch stays INSIDE the current task: a fresh intent
     * (never a copy of activity.intent, whose launcher FLAG_ACTIVITY_NEW_TASK
     * would spawn a new task) started BEFORE finish() so the task never empties.
     * An in-task activity open honours the theme's animations (RunSwapAnim
     * crossfades the fresh screen over the old one), whereas a task swap always
     * plays the OEM's slide. [autoStart] makes the new run begin at once.
     */
    private fun relaunch(autoStart: Boolean) {
        activity.startActivity(Intent(activity, activity.javaClass).putExtra(EXTRA_AUTOSTART, autoStart))
        activity.finish()
    }

    fun showRunOver(score: Int, best: Int, isNewBest: Boolean, coins: Int, boxes: Int) {
        if (runOver != null) return
        topBox.visibility = GONE
        pauseBtn.visibility = GONE
        val flow = RunOverFlow(activity, kit, score, best, isNewBest, coins, boxes,
            onRestart = { relaunch(autoStart = true) },
            onMenu = { relaunch(autoStart = false) },
        )
        runOver = flow
        addView(flow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** The game's 3D gift stage opened a box (GL → UI via the session). */
    fun onBoxOpened(kind: Int, amount: Int) {
        runOver?.onBoxOpened(kind, amount)
    }
}
