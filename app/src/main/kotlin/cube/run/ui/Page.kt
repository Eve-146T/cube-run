package cube.run.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import cube.run.ui.Anim.move

/** Reads the system bar + cutout insets the same way everywhere. */
fun insetsOf(insets: WindowInsets): IntArray =
    if (Build.VERSION.SDK_INT >= 30) {
        val all = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        intArrayOf(all.left, all.top, all.right, all.bottom)
    } else {
        @Suppress("DEPRECATION")
        intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
    }

/**
 * The frame every full-screen page shares: the back button in the top-left
 * corner, a title beside it, an optional right-hand slot, and the page's
 * [content] below — all kept clear of the status bar, cutout and nav bar.
 * [dark] pages sit on the engine's 3D stage (outlined white text, no
 * background); bright ones paint the candy backdrop. The page rises in on
 * creation; [close] drops it away and reports through [onClosed].
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
abstract class Page(
    protected val activity: Activity,
    protected val kit: UiKit,
    title: String,
    protected val dark: Boolean,
    private val onClosed: () -> Unit,
    /** False for pages you cannot leave backwards (the run-over sequence). */
    back: Boolean = true,
) : FrameLayout(activity) {

    protected fun dp(v: Float) = kit.dp(v)
    protected fun dpf(v: Float) = kit.dpf(v)
    protected val topBar = LinearLayout(activity)
    protected val content = FrameLayout(activity)
    protected val titleView: TextView
    private val body = LinearLayout(activity)
    protected var closing = false
        private set
    private val entrance = object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            viewTreeObserver.removeOnPreDrawListener(this)
            if (!closing) animateEntrance()
            return true
        }
    }

    init {
        isClickable = true // the page owns every touch: the game must not start under it
        if (!dark) background = kit.pageBackground()

        titleView = if (dark) kit.stageText(title, 26f, gravity = Gravity.START)
        else kit.text(title, 26f, Theme.INK, 700, gravity = Gravity.START)
        titleView.letterSpacing = 0.04f
        topBar.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            setPadding(dp(14f), dp(10f), dp(16f), dp(6f))
            if (back) addView(kit.backButton { onBack() }, LinearLayout.LayoutParams(dp(46f), dp(50f)))
            addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(12f) })
        }
        content.clipChildren = false; content.clipToPadding = false // rows inside (the wardrobe's tabs) must not be clipped by their own row while they rise
        body.apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            addView(topBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setPadding(0, dp(36f), 0, 0)
        setOnApplyWindowInsetsListener { _, insets ->
            val (l, t, r, b) = insetsOf(insets)
            setPadding(l, maxOf(dp(36f), t + dp(6f)), r, b) // edge to edge at the bottom: pages place their own bottom margins
            insets
        }
        // the title row starts a little lower than the corner pill so the two line up at the same height
        topBar.setPadding(dp(14f), dp(4f), dp(16f), dp(6f))
        alpha = 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!closing) viewTreeObserver.addOnPreDrawListener(entrance)
    }

    /** Start at the first draw, so construction and list layout do not consume the entrance. */
    protected open fun animateEntrance() {
        // entrance: the page fades in, the content rises, the title pops (the top bar itself never moves:
        // translated over the GL surface it was seen to paint a frame late, which reads as the wrong order)
        alpha = 0f; move().alpha(1f).setDuration(140).start()
        Anim.riseIn(content, 0, dpf(40f), 260)
        Anim.popIn(titleView, 40, 0.7f, 300)
    }

    /** The corner slot (a balance): pinned top-right exactly where the menu keeps its bank pill, so it never shifts between screens. */
    protected fun addRight(v: View) {
        addView(v, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.TOP or Gravity.END; topMargin = dp(4f); rightMargin = dp(14f) })
    }

    /** The back button. Pages that need to tidy up first override this and call [close]. */
    protected open fun onBack() = close()

    fun close() {
        if (closing) return
        closing = true
        Anim.cancelTree(this)
        animateExit {
            (parent as? FrameLayout)?.removeView(this)
            onClosed()
        }
    }

    protected open fun animateExit(onFinished: () -> Unit) {
        content.move().translationY(dpf(40f)).alpha(0f).setDuration(130).start()
        move().alpha(0f).setDuration(140).withEndAction(onFinished).start()
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(entrance)
        Anim.cancelTree(this)
        super.onDetachedFromWindow()
    }
}

/**
 * A card floating over a dimmed scene (the pause menu): tap the scrim to
 * dismiss. The card springs up from below; [dismiss] drops it and reports.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
abstract class Sheet(
    protected val activity: Activity,
    protected val kit: UiKit,
    private val onDismissed: () -> Unit,
) : FrameLayout(activity) {

    protected fun dp(v: Float) = kit.dp(v)
    protected fun dpf(v: Float) = kit.dpf(v)
    protected val card: LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false; clipToPadding = false
        isClickable = true // taps inside stay inside
        background = kit.cardDrawable(Theme.CARD, null, 30f)
        setPadding(dp(22f), dp(20f), dp(22f), dp(24f))
    }
    private var closing = false

    init {
        isClickable = true
        setBackgroundColor(Theme.SCRIM)
        addView(card, LayoutParams(dp(320f), LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        setOnClickListener { dismiss() }
        alpha = 0f
        move().alpha(1f).setDuration(110).start()
        card.alpha = 0f; card.scaleX = 0.86f; card.scaleY = 0.86f; card.translationY = dpf(24f)
        card.move().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(200).setInterpolator(Anim.springSoft).withEndAction { card.requestLayout() }.start()
    }

    fun dismiss() {
        if (closing) return
        closing = true
        Anim.cancelTree(this)
        card.move().alpha(0f).scaleX(0.9f).scaleY(0.9f).translationY(dpf(16f)).setDuration(110).start()
        move().alpha(0f).setDuration(120).withEndAction {
            (parent as? FrameLayout)?.removeView(this)
            onDismissed()
        }.start()
    }

    override fun onDetachedFromWindow() {
        Anim.cancelTree(this)
        super.onDetachedFromWindow()
    }

    /** A thin divider line inside the card. */
    protected fun divider(): View = View(activity).apply {
        background = GradientDrawable().apply { cornerRadius = dpf(2f); setColor(Theme.alpha(Theme.INK, 18)) }
    }
}
