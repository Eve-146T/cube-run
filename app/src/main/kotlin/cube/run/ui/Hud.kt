package cube.run.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import cube.run.R
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.ui.Anim.move

/**
 * Everything drawn over the 3D surface: the [MainMenu] before a run; the
 * in-run HUD (score, this run's coin haul, boxes, bubble stock, the pause
 * chip, the BOOST button); and the pages it hosts — [ShopView],
 * [WardrobeView], [SectionsView], the [PauseSheet] and the [RunOverFlow].
 * Must only be touched from the UI thread (GameHostSession marshals for you).
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class Hud(private val activity: Activity) : FrameLayout(activity) {

    companion object {
        /** Intent extra: begin the run on the first frame (RESTART). */
        const val EXTRA_AUTOSTART = "autostart"
    }

    private val kit = UiKit(activity)
    private fun dp(v: Float) = kit.dp(v)
    private fun dpf(v: Float) = kit.dpf(v)

    // ---- in-run HUD
    private val scoreText = kit.stageText("0", 60f, stroke = 7f)
    private val haul = kit.iconPill(CoinIcon(), "0", Theme.INK, 18f, Theme.WHITE)
    private val boxes = kit.iconPill(BoxIcon(), "", Theme.INK, 18f, Theme.LAVENDER).apply { visibility = GONE }
    private val bubbles = kit.iconPill(BubbleIcon(), "", Theme.INK, 18f, Theme.lighten(Theme.CYAN, 0.55f)).apply { visibility = GONE }
    private val pauseChip = kit.chip(R.drawable.ic_pause, Theme.WHITE, Theme.INK, activity.getString(R.string.cd_pause)) { pause() }.apply { visibility = GONE }
    private var boost: BoostArrows? = null
    private val bonusVisited = ArrayList<Int>()
    private val topBox = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false; clipToPadding = false
        visibility = GONE
        addView(scoreText)
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false; clipToPadding = false
            addView(haul)
            addView(boxes, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8f) })
            addView(bubbles, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8f) })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = -dp(4f) })
    }

    private var runCoins = 0
    private var score = 0
    private var best = 0
    private var world = ""
    private var runStarted = false
    private var page: Page? = null
    private var pauseSheet: PauseSheet? = null
    private var runOver: RunOverFlow? = null
    private val menu: MainMenu = MainMenu(activity, kit, { openShop() }, { openWardrobe() }, { openSections() }, { menu.pulseBank() })

    init {
        isClickable = false
        isFocusable = false
        clipChildren = false; clipToPadding = false
        addView(menu, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(topBox, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(44f) })
        addView(pauseChip, LayoutParams(dp(48f), dp(52f)).apply { gravity = Gravity.TOP or Gravity.END; topMargin = dp(48f); rightMargin = dp(14f) })
        setBubbles(Progress.bubbles)
        setOnApplyWindowInsetsListener { _, insets ->
            val (_, t, r, _) = insetsOf(insets)
            (topBox.layoutParams as LayoutParams).topMargin = maxOf(dp(44f), t + dp(6f))
            (pauseChip.layoutParams as LayoutParams).apply { topMargin = maxOf(dp(48f), t + dp(10f)); rightMargin = dp(14f) + r }
            topBox.requestLayout(); pauseChip.requestLayout()
            insets
        }
    }

    // ------------------------------------------------------------- pages

    private fun pageOpen() = page != null || runStarted

    private fun open(p: Page) {
        page = p
        menu.setShown(false)
        addView(p, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun closed() {
        page = null
        menu.setShown(true)
        menu.show()
        menu.refresh()
        setBubbles(Progress.bubbles)
    }

    private fun openShop() { if (!pageOpen()) open(ShopView(activity, kit) { closed() }) }
    private fun openWardrobe() { if (!pageOpen()) open(WardrobeView(activity, kit) { closed() }) }
    private fun openSections() { if (!pageOpen()) open(SectionsView(activity, kit) { closed() }) }

    // ------------------------------------------------------------- HUD values

    fun setBest(best: Int) {
        this.best = best
        menu.setBest(best)
    }

    fun setWorld(name: String) { world = name }

    /** Entered a bonus world (or left one: -1). Remembered for the results. */
    fun setBonus(id: Int) { if (id >= 0 && id !in bonusVisited) bonusVisited.add(id) }

    /** Coins collected this run: the counter pops on each pickup. */
    fun setRunCoins(v: Int) {
        runCoins = v
        kit.labelOf(haul).text = v.toString()
        Anim.pulse(haul, 1.18f, 160)
    }

    fun setBoxes(n: Int) {
        kit.labelOf(boxes).text = "×$n"
        boxes.visibility = if (n > 0) VISIBLE else GONE
        Anim.pulse(boxes, 1.4f)
    }

    private var bubbleStock = 0

    fun setBubbles(n: Int) {
        bubbleStock = n
        kit.labelOf(bubbles).text = "×$n"
        bubbles.visibility = if (n > 0 && runStarted) VISIBLE else GONE
        menu.refresh()
    }

    fun setScore(v: Int) {
        score = v
        scoreText.text = v.toString()
        Anim.pulse(scoreText, 1.14f, 160)
    }

    /** The boost window opened or closed (GL → UI); [taps] used so far. */
    fun setBoost(open: Boolean, taps: Int, max: Int) {
        if (open) {
            val b = boost ?: BoostArrows(activity, ::dpf).also { b ->
                b.max = max
                b.setOnClickListener {
                    if (b.taps >= b.max) return@setOnClickListener
                    b.taps++
                    Stage.boostRequests.incrementAndGet()
                    SoundFx.play("tap", rate = 1.1f + b.taps * 0.1f); Haptics.click()
                    Anim.pulse(b, 1.15f, 220)
                }
                addView(b, LayoutParams(dp(84f), dp(160f)).apply { gravity = Gravity.TOP or Gravity.END; topMargin = dp(116f); rightMargin = dp(10f) })
                boost = b
                Anim.popIn(b, 250, 0.4f, 420)
            }
            b.taps = taps
        } else {
            val b = boost ?: return
            boost = null
            b.taps = taps
            b.move().translationX(dpf(120f)).alpha(0f).setDuration(260).setInterpolator(Anim.ease).withEndAction { removeView(b) }.start()
        }
    }

    /** A run has begun: the menu drops away, the HUD and the pause chip pop in. */
    fun hideOptions() {
        runStarted = true
        page?.let { removeView(it); page = null }
        menu.hide()
        setBubbles(bubbleStock)
        topBox.visibility = VISIBLE
        Anim.popIn(topBox, 120, 0.6f)
        pauseChip.visibility = VISIBLE
        Anim.popIn(pauseChip, 200, 0.5f)
    }

    // ------------------------------------------------------------- pause

    /** Freeze the run under the pause card. No-op unless a run is live. */
    fun pause() {
        if (!runStarted || runOver != null || pauseSheet != null) return
        Stage.paused = true
        pauseChip.visibility = INVISIBLE
        val sheet = PauseSheet(activity, kit,
            onResume = {
                pauseSheet = null
                Stage.paused = false
                pauseChip.visibility = VISIBLE
                Anim.popIn(pauseChip, 0, 0.6f)
            },
            onRestart = { relaunch(autoStart = true) },
            onMenu = { relaunch(autoStart = false) },
        )
        pauseSheet = sheet
        addView(sheet, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** The activity left the foreground mid-run (home, a call): pause so nothing is lost. */
    fun autoPause() = pause()

    // ------------------------------------------------------------- run over

    /**
     * RESTART / MENU = finish + relaunch (NOT recreate): libGDX only disposes GL
     * resources when the activity is truly finishing, so recreate() would leak
     * native meshes. The relaunch stays INSIDE the current task: a fresh intent
     * started BEFORE finish() so the task never empties; the theme's animations
     * crossfade the fresh screen over the old one. [autoStart] makes the new
     * run begin at once.
     */
    private fun relaunch(autoStart: Boolean) {
        SoundFx.play("whoosh", rate = 0.8f)
        activity.startActivity(Intent(activity, activity.javaClass).putExtra(EXTRA_AUTOSTART, autoStart))
        activity.finish()
    }

    fun showRunOver(score: Int, best: Int, isNewBest: Boolean, coins: Int, boxes: Int) {
        if (runOver != null) return
        topBox.move().alpha(0f).setDuration(200).withEndAction { topBox.visibility = GONE }.start()
        pauseChip.visibility = GONE
        setBoost(false, 0, 5)
        val flow = RunOverFlow(activity, kit, score, best, isNewBest, coins, boxes, world, bonusVisited,
            onRestart = { relaunch(autoStart = true) },
            onMenu = { relaunch(autoStart = false) },
        )
        runOver = flow
        addView(flow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** The game's 3D gift stage opened a box (GL → UI via the session). */
    fun onBoxOpened(kind: Int, amount: Int, cat: Int, id: Int) {
        runOver?.onBoxOpened(kind, amount, cat, id)
    }
}
