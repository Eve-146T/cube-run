package cube.run.core.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import cube.run.core.Haptics
import cube.run.core.Palette
import cube.run.core.SoundFx

/**
 * The HUD's look: bright cards, dark ink, candy accents. Every panel
 * ([ShopView], [GameOverCard]) builds from these tokens + [UiKit] helpers so
 * the pieces read as one screen.
 */
object Ui {
    const val CARD = 0xFFFDFBFF.toInt()      // near-white card face
    const val CARD_ALT = 0xFFF1EEFF.toInt()  // tiles on the card
    const val INK = 0xFF1E1840.toInt()       // primary text
    const val MUTED = 0xFF7B7797.toInt()     // secondary text
    const val GOLD = 0xFFFFB300.toInt()      // coins
    const val GOLD_INK = 0xFFD98A00.toInt()  // coin text on white
    const val GREEN_INK = 0xFF1FA33A.toInt() // accent text on white
    const val CYAN = 0xFF17B4D8.toInt()      // bubble
    const val PURPLE = 0xFF8E44FF.toInt()    // mystery box
    const val SCRIM = 0x70100C24             // behind panels: dims, doesn't black out
    const val COIN = "⬤"
}

/** Small factory for the HUD's programmatic widgets. */
class UiKit(private val ctx: Context, val accent: Int) {
    private val density = ctx.resources.displayMetrics.density
    fun dp(v: Float) = (v * density).toInt()

    enum class Style { FILLED, GOLD, OUTLINE, MUTED }

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

    /** A pill button. [FILLED] = accent, [GOLD] = coin colour, [OUTLINE] = ink stroke, [MUTED] = disabled look. */
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
}
