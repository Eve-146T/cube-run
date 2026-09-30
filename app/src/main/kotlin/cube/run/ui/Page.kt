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

/** Respect permanent cutouts; transient system bars overlay this immersive game without moving controls. */
fun insetsOf(insets: WindowInsets): IntArray =
    if (Build.VERSION.SDK_INT >= 30) {
        val all = insets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout())
        intArrayOf(all.left, all.top, all.right, all.bottom)
    } else {
        val cutout = insets.displayCutout
        intArrayOf(cutout?.safeInsetLeft ?: 0, cutout?.safeInsetTop ?: 0, cutout?.safeInsetRight ?: 0, cutout?.safeInsetBottom ?: 0)
    }

/**
 * Cutout insets still uncovered where [view] sits. In a split pane Android may lay the
 * window's content out below the status bar, or the game root may pad for a visible
 * bar, while the cutout is still reported: counting it again left a gap that came and
 * went with the status bar. Re-measures [view] when a layout moves it.
 */
internal class Uncovered(private val view: View) : ViewTreeObserver.OnGlobalLayoutListener {
    private var raw = IntArray(4)
    private var used = IntArray(4)
    private val at = IntArray(2)

    init {
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = v.viewTreeObserver.addOnGlobalLayoutListener(this@Uncovered)
            override fun onViewDetachedFromWindow(v: View) = v.viewTreeObserver.removeOnGlobalLayoutListener(this@Uncovered)
        })
    }

    /** [insets] (left, top, right, bottom) less whatever the window and parents already keep clear. */
    fun of(insets: IntArray): IntArray {
        raw = insets.copyOf()
        used = compute()
        return used
    }

    private fun compute(): IntArray {
        val parent = view.parent as? View ?: return raw.copyOf()
        val window = view.rootView
        parent.getLocationInWindow(at)
        val left = at[0] + parent.paddingLeft
        val top = at[1] + parent.paddingTop
        val right = window.width - at[0] - parent.width + parent.paddingRight
        val bottom = window.height - at[1] - parent.height + parent.paddingBottom
        return intArrayOf(maxOf(0, raw[0] - left), maxOf(0, raw[1] - top), maxOf(0, raw[2] - right), maxOf(0, raw[3] - bottom))
    }

    override fun onGlobalLayout() {
        if (!compute().contentEquals(used)) view.requestLayout()
    }
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
    private var instantNavigation = false
    private var deferredEntrance = false
    protected var compactLayout = false
        private set
    protected open fun onCompactChanged(compact: Boolean) {}
    /** Every measure, with the pane's usable height: for adjustments finer than compact/full. */
    protected open fun onPaneMeasured(usableHeight: Int) {}
    private var headerAccessory: View? = null
    protected fun reserveHeaderFor(view: View) { headerAccessory = view }
    private var normalTitleLines = Int.MAX_VALUE
    private var normalTitleSize = 0f
    private var safeTop = 0
    private var safeBottom = 0
    private val uncovered = Uncovered(this)
    private val entrance = object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            viewTreeObserver.removeOnPreDrawListener(this)
            if (!closing) animateEntrance()
            return true
        }
    }

    init {
        layoutDirection = resources.configuration.layoutDirection // Also applies while a shop is prepared offscreen.
        isClickable = true // the page owns every touch: the game must not start under it
        if (!dark) background = kit.pageBackground()

        titleView = if (dark) kit.stageText(title, 26f, gravity = Gravity.START)
        else kit.text(title, 26f, Theme.INK, 700, gravity = Gravity.START)
        titleView.letterSpacing = kit.tracking(0.04f)
        topBar.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            setPadding(dp(14f), dp(10f), dp(16f), dp(6f))
            if (back) addView(kit.backButton { onBack() }, LinearLayout.LayoutParams(dp(46f), dp(50f)))
            addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12f) })
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
            safeTop = t; safeBottom = b
            setPadding(l, maxOf(dp(if (height < dp(CompactLayout.HEIGHT_DP) && height > 0) 8f else 36f), t + dp(6f)), r, b)
            insets
        }
        // the title row starts a little lower than the corner pill so the two line up at the same height
        topBar.setPadding(dp(14f), dp(4f), dp(16f), dp(6f))
        alpha = 0f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // A split pane needs its first row immediately below the status bar.
        // The full-screen 36dp stage gutter otherwise consumes a fifth of it.
        val (_, safeTop, _, safeBottom) = uncovered.of(intArrayOf(0, safeTop, 0, safeBottom))
        val short = CompactLayout.uses(this, MeasureSpec.getSize(heightMeasureSpec), safeTop, safeBottom)
        if (compactLayout != short) {
            compactLayout = short
            if (titleView.parent === topBar) {
                if (short) {
                    normalTitleLines = titleView.maxLines; normalTitleSize = titleView.textSize
                    titleView.maxLines = 1
                    titleView.setAutoSizeTextTypeUniformWithConfiguration(12, 26, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
                } else {
                    titleView.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
                    titleView.maxLines = normalTitleLines
                    titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, normalTitleSize)
                }
            }
            onCompactChanged(short)
        }
        onPaneMeasured(MeasureSpec.getSize(heightMeasureSpec) - safeTop - safeBottom)
        var end = dp(16f)
        if (short && titleView.parent === topBar) headerAccessory?.let { accessory ->
            accessory.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) / 2, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            end += accessory.measuredWidth + dp(8f)
        }
        topBar.setPaddingRelative(dp(14f), dp(if (short) 0f else 10f), end, dp(if (short) 0f else 6f))
        val top = maxOf(dp(if (short) 8f else 36f), safeTop + dp(6f))
        if (paddingTop != top) setPadding(paddingLeft, top, paddingRight, paddingBottom)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!instantNavigation && !deferredEntrance && !closing) {
            onNavigationShown()
            viewTreeObserver.addOnPreDrawListener(entrance)
        }
    }

    /** Cached pages stay attached and laid out, but own neither input nor the GL stage while hidden. */
    internal fun prepareInstantNavigation() {
        instantNavigation = true
        visibility = INVISIBLE
        alpha = 1f
        viewTreeObserver.removeOnPreDrawListener(entrance)
    }

    /** Prepaint a hidden page while retaining its normal entrance/exit durations. */
    internal fun prepareAnimatedNavigation() {
        deferredEntrance = true
        visibility = INVISIBLE
        alpha = 1f
        viewTreeObserver.removeOnPreDrawListener(entrance)
    }

    internal fun showAnimatedPrepared() {
        check(width > 0 && height > 0)
        deferredEntrance = false
        visibility = VISIBLE
        onNavigationShown()
        animateEntrance()
    }

    internal fun showPrepared() {
        closing = false
        onNavigationShown()
        visibility = VISIBLE
        rootView.invalidate() // Recompose the warm layer without re-rasterizing its text.
    }

    protected open fun onNavigationShown() = Unit

    /** Start at the first draw, so construction and list layout do not consume the entrance. */
    protected open fun animateEntrance() {
        // entrance: the page fades in, the content rises, the title pops (the top bar itself never moves:
        // translated over the GL surface it was seen to paint a frame late, which reads as the wrong order)
        alpha = 0f; move().alpha(1f).setDuration(100).start()
        Anim.riseIn(content, 0, dpf(20f), 180)
        Anim.popIn(titleView, 0, 0.85f, 180)
    }

    /** The corner slot (a balance): pinned top-right exactly where the menu keeps its bank pill, so it never shifts between screens. */
    protected fun addRight(v: View) {
        reserveHeaderFor(v)
        addView(v, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.TOP or Gravity.END; topMargin = dp(4f); marginEnd = dp(14f) })
    }

    /** The back button. Pages that need to tidy up first override this and call [close]. */
    protected open fun onBack() = close()

    fun navigateBack() = onBack()

    fun close() {
        if (closing) return
        if (instantNavigation && visibility != VISIBLE) return
        closing = true
        if (instantNavigation) {
            visibility = INVISIBLE
            closing = false // Background preparation can finish while this page is parked.
            onClosed()
            return
        }
        Anim.cancelTree(this)
        animateExit {
            (parent as? FrameLayout)?.removeView(this)
            onClosed()
        }
    }

    protected open fun animateExit(onFinished: () -> Unit) {
        content.move().translationY(dpf(20f)).alpha(0f).setDuration(90).start()
        move().alpha(0f).setDuration(100).withEndAction(onFinished).start()
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(entrance)
        Anim.cancelTree(this)
        super.onDetachedFromWindow()
    }
}

/**
 * A card floating over a dimmed scene (the pause menu). The card springs up from below; [dismiss] drops it and reports.
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
    protected val cardScroll = android.widget.ScrollView(activity).apply {
        isFillViewport = false
        clipChildren = false; clipToPadding = false
        addView(card, FrameLayout.LayoutParams(-1, -2))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val params = cardScroll.layoutParams
        params.width = minOf(dp(320f), (MeasureSpec.getSize(widthMeasureSpec) - dp(24f)).coerceAtLeast(1))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    init {
        isClickable = true
        setBackgroundColor(Theme.SCRIM)
        addView(cardScroll, LayoutParams(dp(320f), LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER; topMargin = dp(8f); bottomMargin = dp(8f) })
        // The backdrop consumes touches; only explicit controls dismiss the pause.
        alpha = 0f
        move().alpha(1f).setDuration(110).start()
        card.alpha = 0f; card.scaleX = 0.86f; card.scaleY = 0.86f; card.translationY = dpf(24f)
        card.move().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(160).setInterpolator(Anim.springSoft).withEndAction { card.requestLayout() }.start()
    }

    /** Backgrounding should leave a settled pause card, not replay its entrance on return. */
    fun settleEntrance() {
        if (closing) return
        Anim.reset(this)
        Anim.reset(card)
    }

    fun dismiss() {
        if (closing) return
        closing = true
        Anim.cancelTree(this)
        card.move().alpha(0f).scaleX(0.9f).scaleY(0.9f).translationY(dpf(16f)).setDuration(90).start()
        move().alpha(0f).setDuration(100).withEndAction {
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
