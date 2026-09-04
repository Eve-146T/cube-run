package cube.run.core.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import cube.run.R
import cube.run.core.Haptics
import cube.run.core.Palette
import cube.run.core.SoundFx

/**
 * The HUD's look: bright cards, dark ink, candy accents. Every panel builds
 * from these tokens + [UiKit] helpers so the pieces read as one screen.
 */
object Ui {
    const val CARD = 0xFFFDFBFF.toInt()      // near-white card face
    const val CARD_ALT = 0xFFF1EEFF.toInt()  // tiles on the card
    const val PAGE_TOP = 0xFFEFEBFF.toInt()  // bright full-screen pages: gradient top…
    const val PAGE_BOTTOM = 0xFFFDFBFF.toInt() // …to bottom
    const val INK = 0xFF1E1840.toInt()       // primary text
    const val MUTED = 0xFF7B7797.toInt()     // secondary text
    const val GOLD = 0xFFFFB300.toInt()      // coins
    const val GOLD_INK = 0xFFD98A00.toInt()  // coin text on white
    const val GREEN_INK = 0xFF1FA33A.toInt() // accent text on white
    const val CYAN = 0xFF17B4D8.toInt()      // bubble
    const val CYAN_LIGHT = 0xFF7DF3FF.toInt() // bubble on dark
    const val PURPLE = 0xFF8E44FF.toInt()    // mystery box
    const val PURPLE_LIGHT = 0xFFC28BFF.toInt() // mystery box on dark
    const val SCRIM = 0x70100C24             // behind panels: dims, doesn't black out
    const val SCRIM_DARK = 0xC00E0A20.toInt() // the pause: the run is still there, just dimmed
    const val COIN = "⬤"
}

/** Small factory for the HUD's programmatic widgets. */
@SuppressLint("ClickableViewAccessibility")
class UiKit(private val ctx: Context, val accent: Int) {
    private val density = ctx.resources.displayMetrics.density
    fun dp(v: Float) = (v * density).toInt()

    enum class Style { FILLED, GOLD, OUTLINE, MUTED, LIGHT }

    fun text(
        t: CharSequence, size: Float, color: Int = Ui.INK, bold: Boolean = true,
        gravity: Int = Gravity.CENTER, heavy: Boolean = false,
    ): TextView = TextView(ctx).apply {
        text = t
        textSize = size
        setTextColor(color)
        typeface = if (heavy) Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD) else if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        this.gravity = gravity
    }

    /** White text with a soft drop shadow, for anything sitting on the 3D stage. */
    fun stageText(t: CharSequence, size: Float, color: Int = Color.WHITE, heavy: Boolean = false, gravity: Int = Gravity.CENTER): TextView =
        text(t, size, color, heavy = heavy, gravity = gravity).apply { setShadowLayer(dp(6f).toFloat(), 0f, dp(2f).toFloat(), 0xA0000000.toInt()) }

    /**
     * A pill button. [FILLED] = accent, [GOLD] = coin colour, [OUTLINE] = ink stroke,
     * [MUTED] = disabled look, [LIGHT] = white outline for dark stages.
     */
    fun button(label: String, style: Style, small: Boolean = false, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            text = label
            textSize = if (small) 14f else 19f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            gravity = Gravity.CENTER
            if (small) setPadding(dp(14f), dp(8f), dp(14f), dp(8f)) else setPadding(dp(36f), dp(13f), dp(36f), dp(13f))
            val bg = GradientDrawable().apply { cornerRadius = dp(30f).toFloat() }
            when (style) {
                Style.FILLED -> { setTextColor(Ui.INK); bg.setColor(accent) }
                Style.GOLD -> { setTextColor(Ui.INK); bg.setColor(Ui.GOLD) }
                Style.OUTLINE -> { setTextColor(Ui.INK); bg.setColor(Color.TRANSPARENT); bg.setStroke(dp(2f), Palette.withAlpha(Ui.INK, 110)) }
                Style.MUTED -> { setTextColor(Ui.MUTED); bg.setColor(Color.TRANSPARENT); bg.setStroke(dp(1f), Palette.withAlpha(Ui.MUTED, 110)) }
                Style.LIGHT -> { setTextColor(Color.WHITE); bg.setColor(Palette.withAlpha(Color.WHITE, 30)); bg.setStroke(dp(2f), Palette.withAlpha(Color.WHITE, 170)) }
            }
            background = bg
            setOnClickListener {
                SoundFx.play("tap"); Haptics.click()
                onClick()
            }
        }

    /** A bright rounded card with the accent as its rim. */
    fun card(strokeColor: Int = accent): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(28f).toFloat()
            setColor(Ui.CARD)
            setStroke(dp(3f), strokeColor)
        }
    }

    /** A soft tile inside a card (stat boxes). */
    fun tile(fill: Int = Ui.CARD_ALT): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = dp(16f).toFloat()
            setColor(fill)
        }
    }

    /** The bright full-screen page background. */
    fun pageBackground(): Drawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Ui.PAGE_TOP, Ui.PAGE_BOTTOM))

    /** Paint a round icon button: [fill] disc, [ring] stroke, [tint]ed glyph. */
    fun paintIcon(v: ImageView, icon: Int, tint: Int, fill: Int, ring: Int) {
        v.setImageResource(icon)
        v.imageTintList = ColorStateList.valueOf(tint)
        v.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke(dp(2f), ring)
        }
    }

    /**
     * A round, icon-only button. On dark stages ([dark]) it is a translucent white
     * disc with a coloured ring; on bright pages a white disc with an ink ring.
     */
    fun iconButton(icon: Int, tint: Int, label: String, dark: Boolean = true, onClick: () -> Unit): ImageView = ImageView(ctx).apply {
        val pad = dp(11f)
        setPadding(pad, pad, pad, pad)
        scaleType = ImageView.ScaleType.FIT_CENTER
        isClickable = true
        isFocusable = true
        contentDescription = label
        if (dark) paintIcon(this, icon, tint, Palette.withAlpha(Color.WHITE, 46), Palette.withAlpha(tint, 200))
        else paintIcon(this, icon, tint, Ui.CARD, Palette.withAlpha(Ui.INK, 60))
        setOnClickListener { onClick() }
        setOnTouchListener { v, ev -> // a little press squash; the click still fires
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(60).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
    }

    /**
     * A round on/off toggle (sound, vibration, dev): tapping flips it, confirms
     * with sound + haptic honouring the *new* state, then repaints.
     */
    fun toggleButton(iconOn: Int, iconOff: Int, label: String, isOn: () -> Boolean, set: (Boolean) -> Unit): ImageView {
        lateinit var v: ImageView
        fun paint() {
            val on = isOn()
            paintIcon(v, if (on) iconOn else iconOff, if (on) accent else Palette.withAlpha(Color.WHITE, 150),
                Palette.withAlpha(Color.WHITE, 46), Palette.withAlpha(if (on) accent else Color.WHITE, if (on) 200 else 80))
            v.contentDescription = "$label ${if (on) "on" else "off"}"
        }
        v = iconButton(iconOn, accent, label) {
            set(!isOn())
            paint()
        }
        paint()
        return v
    }

    /** The one back button: every full-screen page puts it in the top-left corner. */
    fun backButton(dark: Boolean, onClick: () -> Unit): ImageView =
        iconButton(R.drawable.ic_back, if (dark) Color.WHITE else Ui.INK, ctx.getString(R.string.cd_back), dark) { SoundFx.play("tap"); Haptics.click(); onClick() }
}

/**
 * The frame every full-screen page shares: the back button in the top-left
 * corner, a title beside it, an optional right-hand slot, and the page's
 * [content] below — all kept clear of the status bar, cutout and nav bar.
 * [dark] pages sit on the engine's stage (white text, no background); bright
 * ones paint [UiKit.pageBackground]. Fades in on creation, [close] fades out,
 * removes the page and reports through [onClosed].
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
abstract class FullScreen(
    protected val activity: Activity,
    protected val kit: UiKit,
    title: String,
    protected val dark: Boolean,
    private val onClosed: () -> Unit,
    /** False for pages you cannot leave backwards (the run-over sequence). */
    back: Boolean = true,
) : FrameLayout(activity) {

    protected fun dp(v: Float) = kit.dp(v)
    protected val topBar = LinearLayout(activity)
    protected val content = FrameLayout(activity)
    protected val titleView: TextView
    private val body = LinearLayout(activity)
    private var closing = false

    init {
        isClickable = true // the page owns every touch: the game must not start under it
        if (!dark) background = kit.pageBackground()
        alpha = 0f
        animate().alpha(1f).setDuration(200).start()

        titleView = if (dark) kit.stageText(title, 24f, heavy = true, gravity = Gravity.START)
        else kit.text(title, 24f, Ui.INK, heavy = true, gravity = Gravity.START)
        titleView.letterSpacing = 0.05f
        topBar.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(10f), dp(16f), dp(6f))
            if (back) addView(kit.backButton(dark) { onBack() }, LinearLayout.LayoutParams(dp(44f), dp(44f)))
            addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(12f) })
        }
        body.apply {
            orientation = LinearLayout.VERTICAL
            addView(topBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setPadding(0, dp(36f), 0, dp(24f))
        setOnApplyWindowInsetsListener { _, insets ->
            val top: Int; val bottom: Int; val left: Int; val right: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val all = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                top = all.top; bottom = all.bottom; left = all.left; right = all.right
            } else {
                @Suppress("DEPRECATION") top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION") bottom = insets.systemWindowInsetBottom
                @Suppress("DEPRECATION") left = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION") right = insets.systemWindowInsetRight
            }
            setPadding(left, maxOf(dp(36f), top + dp(6f)), right, maxOf(dp(24f), bottom + dp(8f)))
            insets
        }
    }

    /** Something for the top bar's right end (a balance, a count). */
    protected fun addRight(v: View) {
        topBar.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(10f) })
    }

    /** The back button. Pages that need to tidy up first override this and call [close]. */
    protected open fun onBack() = close()

    fun close() {
        if (closing) return
        closing = true
        animate().alpha(0f).setDuration(160).withEndAction {
            (parent as? FrameLayout)?.removeView(this)
            onClosed()
        }.start()
    }
}
