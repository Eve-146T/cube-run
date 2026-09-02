package cube.run.core

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.content.Intent
import cube.run.core.ui.PreRunMenu
import cube.run.core.ui.RunOverFlow
import cube.run.core.ui.SectionsView
import cube.run.core.ui.ShopView
import cube.run.core.ui.SkinsView
import cube.run.core.ui.Ui
import cube.run.core.ui.UiKit

/**
 * The game's HUD overlay: score / best / coins / boxes at the top, animated
 * centre banners, the "tap to start" prompt, and the panels it hosts — the
 * [PreRunMenu] (toggles + skins + shop), the [ShopView], the [SkinsView] and
 * the [RunOverFlow].
 * Must only be touched from the UI thread (GameHostSession marshals for you).
 *
 * Created programmatically (never inflated from XML) and shows dynamic,
 * single-locale game text, so ViewConstructor / SetTextI18n are suppressed.
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class GameChromeView(private val activity: Activity, accent: Int) : FrameLayout(activity) {

    private val kit = UiKit(activity, accent)
    private fun dp(v: Float) = kit.dp(v)
    private fun shadow(t: TextView, r: Float = 6f) = t.setShadowLayer(dp(r).toFloat(), 0f, dp(2f).toFloat(), 0x90000000.toInt())

    private val scoreText = kit.text("0", 52f, Color.WHITE, heavy = true).also { shadow(it) }
    private val bestText = kit.text("", 15f, Palette.withAlpha(Color.WHITE, 190)).also { shadow(it, 4f) }
    // Coin bank (pre-run) / bank + this run's haul (in-run), gold; mystery boxes beside it.
    private val coinText = kit.text("", 17f, Ui.GOLD).also { shadow(it, 4f) }
    private val boxText = kit.text("", 17f, 0xFFC28BFF.toInt()).also { shadow(it, 4f); it.visibility = GONE }
    // Bubble stock + how to use it. Shown whenever at least one is in stock.
    private val bubbleText = kit.text("", 13f, 0xFF7DF3FF.toInt()).also { shadow(it, 4f); it.visibility = GONE }
    private val bannerText = kit.text("", 40f, accent, heavy = true).also { shadow(it, 8f); it.alpha = 0f }
    // Low-key prompt shown (softly pulsing) until the first touch starts a run.
    private val startHint = kit.text("Tap to start", 22f, Palette.withAlpha(Color.WHITE, 180)).also { shadow(it) }

    private var bankAtStart = Progress.coins
    private var runCoins = 0
    private var runStarted = false
    private var shop: ShopView? = null
    private var skins: SkinsView? = null
    private var sections: SectionsView? = null
    private var runOver: RunOverFlow? = null
    private var startPulse: ValueAnimator? = null
    private val topBox = LinearLayout(activity)
    private val menu = PreRunMenu(activity, kit, { openShop() }, { openSkins() }, { openSections() }, { bankAtStart = Progress.coins; refreshCoins() })
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
        addView(bannerText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        addView(startHint, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        addView(menu, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        refreshCoins()
        setBubbles(Progress.bubbles)
        refreshStartHint()

        startPulse = ValueAnimator.ofFloat(0.45f, 0.8f).apply {
            duration = 1000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { a -> startHint.alpha = a.animatedValue as Float }
            start()
        }

        // Keep the top block clear of the status bar / cutout (the menu handles its own insets).
        setOnApplyWindowInsetsListener { _, insets ->
            val top = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top
            } else {
                @Suppress("DEPRECATION") insets.systemWindowInsetTop
            }
            (topBox.layoutParams as LayoutParams).topMargin = maxOf(dp(48f), top + dp(10f))
            topBox.requestLayout()
            insets
        }
    }

    override fun onDetachedFromWindow() {
        startPulse?.cancel(); startPulse = null
        super.onDetachedFromWindow()
    }

    private fun openShop() {
        if (panelOpen()) return
        val v = ShopView(activity, kit) {
            shop = null
            bankAtStart = Progress.coins
            refreshCoins()
            setBubbles(Progress.bubbles)
        }
        shop = v
        addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun openSkins() {
        if (panelOpen()) return
        topBox.visibility = INVISIBLE; startHint.visibility = INVISIBLE; menu.visibility = INVISIBLE // the wardrobe is the whole screen
        val v = SkinsView(activity, kit) {
            skins = null
            topBox.visibility = VISIBLE; startHint.visibility = VISIBLE; menu.visibility = VISIBLE
            bankAtStart = Progress.coins
            refreshCoins()
        }
        skins = v
        addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun openSections() {
        if (panelOpen()) return
        val v = SectionsView(activity, kit) {
            sections = null
            refreshStartHint()
        }
        sections = v
        addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** "Tap to start", or the chosen section's name when the explorer picked one. */
    private fun refreshStartHint() {
        val chosen = cube.run.game.Sections.byId(Settings.testSection)
        startHint.text = if (chosen != null) "Tap to play ${chosen.name}" else "Tap to start"
    }

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

    /** Hide the "Tap to start" prompt and the pre-run menu once a run has begun. */
    fun hideOptions() {
        runStarted = true
        startPulse?.cancel(); startPulse = null
        shop?.let { removeView(it); shop = null }
        skins?.let { removeView(it); skins = null; topBox.visibility = VISIBLE }
        sections?.let { removeView(it); sections = null }
        setBubbles(bubbleStock)
        if (startHint.visibility == VISIBLE) {
            startHint.animate().alpha(0f).setDuration(160).withEndAction { startHint.visibility = GONE }.start()
        }
        menu.hide()
    }

    fun setScore(v: Int) {
        scoreText.text = v.toString()
        pop(scoreText, 1.18f)
    }

    /** A vivid random hue for the centre banner — fresh each time; the drop shadow keeps it legible. */
    private fun randomBannerColor(): Int =
        Color.HSVToColor(floatArrayOf((Math.random() * 360.0).toFloat(), 0.75f, 1f))

    fun banner(text: String) {
        bannerText.animate().cancel()
        bannerText.text = text
        bannerText.setTextColor(randomBannerColor())
        bannerText.alpha = 1f
        bannerText.scaleX = 0.5f
        bannerText.scaleY = 0.5f
        bannerText.animate()
            .scaleX(1.1f).scaleY(1.1f)
            .setStartDelay(0)
            .setInterpolator(OvershootInterpolator(2.4f))
            .setDuration(220)
            .withEndAction {
                bannerText.animate().alpha(0f)
                    .setStartDelay(380)
                    .setInterpolator(DecelerateInterpolator())
                    .setDuration(300)
                    .withEndAction { bannerText.animate().setStartDelay(0) }
                    .start()
            }
            .start()
    }

    fun showRunOver(score: Int, best: Int, isNewBest: Boolean, coins: Int, boxes: Int) {
        if (runOver != null) return
        topBox.visibility = GONE
        // RESTART = finish + relaunch (NOT recreate): libGDX only disposes GL resources
        // when the activity is truly finishing, so recreate() would leak native meshes.
        // The relaunch stays INSIDE the current task: a fresh intent (never a copy of
        // activity.intent, whose launcher FLAG_ACTIVITY_NEW_TASK would spawn a new task)
        // started BEFORE finish() so the task never empties. An in-task activity open
        // honours the theme's animations (RunSwapAnim crossfades the fresh run over the
        // run-over screen), whereas a task swap always plays the OEM's slide.
        val flow = RunOverFlow(activity, kit, score, best, isNewBest, coins, boxes,
            onRestart = {
                activity.startActivity(Intent(activity, activity.javaClass))
                activity.finish()
            },
            onExit = { activity.finish() },
        )
        runOver = flow
        addView(flow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** The game's 3D gift stage opened a box (GL → UI via the session). */
    fun onBoxOpened(kind: Int, amount: Int) {
        runOver?.onBoxOpened(kind, amount)
    }
}
