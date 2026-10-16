package cube.run.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import cube.run.R
import cube.run.core.Haptics
import cube.run.core.SoundFx
import kotlin.math.cos
import kotlin.math.sin

/**
 * The shop's cards, built like the achievement page's slabs but each in its item's own colour
 * on a flat butter backdrop: the item's picture on its candy tile, its name, and what the next
 * purchase gives (what you have small, what you buy big). Along the bottom, one big gold candy
 * button carries the level bar (or your stock) and the price: tapping it buys the next one.
 * Maxed out, the bar lies flat with a mint tick. The mystery box gets a deep grape card with slow rays.
 */
@SuppressLint("SetTextI18n")
internal class ShopCards(private val activity: Activity, private val kit: UiKit) {
    /** The cards, bars and value lines of the last [clear]ed render, by item key. */
    val cards = HashMap<String, View>()
    val bars = HashMap<String, SegmentBar>()
    val values = HashMap<String, View>()

    private fun dp(v: Float) = kit.dp(v)

    fun clear() { cards.clear(); bars.clear(); values.clear() }

    /** What a card's buy button needs: the price, whether you can pay it now, and what a tap does. */
    class Offer(val price: Int, val can: Boolean, val onTap: (View) -> Unit)

    /** A group heading on the backdrop. */
    fun heading(label: String): View = kit.text(label, 16f, INK, 700, Gravity.START).apply {
        letterSpacing = kit.tracking(.08f)
        setPadding(dp(6f), 0, dp(6f), 0)
    }

    /** Something you keep a stock of: the count, and a rack that fills up on the buy button. */
    fun stock(key: String, name: String, count: Int, slots: Int, rackIcon: () -> Drawable, offer: Offer?): View {
        val lit = if (count == 0) 0 else ((count - 1) % slots) + 1
        val rack = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            for (i in 0 until slots) addView(ImageView(activity).apply {
                setImageDrawable(rackIcon()); if (i >= lit) tag = EMPTY
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(26f), dp(26f)).apply { if (i > 0) marginStart = dp(4f) })
        }
        return card(key, name, value("×$count", null), bottom(key, rack, offer))
    }

    /** A level you buy up: "now → NEXT", and the level bar on the buy button. */
    fun level(key: String, name: String, max: Int, level: Int, now: String, next: String?, offer: Offer?): View {
        val bar = kit.segments(max).apply { this.level = level }
        bars[key] = bar
        return card(key, name, value(now, next), bottom(key, bar, offer, barHeight = dp(12f)))
    }

    /** The one-off: the trophy room. [action] is its price, or the unlocked status. */
    fun achievements(name: String, detail: String, action: View): View =
        card(ShopIcon.ACHIEVEMENTS, name, kit.text(detail, 15f, SOFT, 500, Gravity.START), action.also { it.minimumHeight = dp(BOTTOM) })

    /** The mystery box: a deep grape card with slow rays behind its glossy question mark. */
    fun mystery(name: String, detail: String, price: View): View = MysteryCard().apply {
        cards[ShopIcon.MYSTERY] = this
        setOnClickListener { Pokes.press(this) }
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(activity).apply {
                setImageDrawable(ShopIcon(ShopIcon.MYSTERY))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(HERO), dp(HERO)))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(kit.text(name, 22f, Theme.WHITE, 700, Gravity.START).apply { maxLines = 2 })
                addView(kit.text(detail, 15f, SOFT, 500, Gravity.START),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(1f) })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10f) })
        }, LinearLayout.LayoutParams(-1, -2))
        addView(price, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14f) })
    }

    /** A card in the item's own colour: tile, name and [line] on top, [bottomRow] under them. */
    private fun card(key: String, name: String, line: View, bottomRow: View): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false; clipToPadding = false
        background = TrophyRoom.slab(kit, RADIUS, lip = Theme.darken(face(key), .3f), face = face(key))
        setPadding(dp(PAD), dp(PAD), dp(PAD), dp(PAD) + kit.CARD_LIP)
        setOnClickListener { Pokes.press(this) }
        cards[key] = this
        values[key] = line
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            addView(ImageView(activity).apply {
                setImageDrawable(AchievementTile(ShopIcon(key), ShopIcon.tile(key)))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(TILE), dp(TILE)))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = false; clipToPadding = false
                addView(kit.text(name, 20f, Theme.WHITE, 700, Gravity.START).apply {
                    maxLines = 2
                    setAutoSizeTextTypeUniformWithConfiguration(16, 20, 1, TypedValue.COMPLEX_UNIT_SP)
                    hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_FULL
                }, LinearLayout.LayoutParams(-1, -2))
                addView(line, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(1f) })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12f) })
        }, LinearLayout.LayoutParams(-1, -2))
        addView(bottomRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })
    }

    /**
     * What you have, small and soft, then what you buy, big and white. Maxed out, what you have
     * is all there is, so it is the big one.
     */
    private fun value(now: String, next: String?): TextView {
        val big = kit.dp(25f * activity.resources.configuration.fontScale)
        val text = SpannableStringBuilder()
        // A value never splits across lines ("96 / Reihen"): a narrow card wraps before the new value instead.
        fun whole(s: String) = s.replace(' ', '\u00A0')
        fun strong(s: String) {
            val start = text.length
            text.append(whole(s))
            text.setSpan(ForegroundColorSpan(Theme.WHITE), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(AbsoluteSizeSpan(big), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (next == null) strong(now)
        else {
            text.append(whole(now)).append(if (activity.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) "  ←  " else "  →  ")
            strong(next)
        }
        return kit.text(text, 16f, SOFT, 700, Gravity.START).apply { maxLines = 2 }
    }

    /**
     * The card's last row. While there is something to buy, it is one big candy button the width
     * of the card: the level bar (or the stock rack) on its face, the price at its end. A tap
     * anywhere on it buys the next one. Maxed out, the bar lies flat on the card with a mint tick.
     */
    private fun bottom(key: String, main: View, offer: Offer?, barHeight: Int = -2): View {
        val bar = main as? SegmentBar
        // An empty rack slot is the item's shape in the trough colour, like an empty bar segment.
        fun empty(trough: Int) = (main as? ViewGroup)?.let { rack ->
            for (i in 0 until rack.childCount) (rack.getChildAt(i) as? ImageView)?.takeIf { it.tag == EMPTY }
                ?.setColorFilter(trough, android.graphics.PorterDuff.Mode.SRC_IN)
        }
        if (offer == null) return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(BOTTOM)
            bar?.apply { color = Theme.WHITE; offColor = Theme.darken(face(key), .25f) }
            empty(Theme.darken(face(key), .25f))
            addView(main, LinearLayout.LayoutParams(0, barHeight, 1f))
            addView(ImageView(activity).apply {
                setImageDrawable(rewardArt(false, 0, true))
                contentDescription = activity.getString(R.string.text_max)
            }, LinearLayout.LayoutParams(dp(BOTTOM), dp(BOTTOM)).apply { marginStart = dp(12f) })
        }
        // Not enough coins yet: frosted glass on the card's colour, the price still there, plainly not lit.
        val slab = if (offer.can) Theme.GOLD else Theme.alpha(Theme.WHITE, 64)
        val words = if (offer.can) Theme.INK else Theme.WHITE
        val trough = if (offer.can) Theme.darken(Theme.GOLD, .14f) else Theme.alpha(Theme.darken(face(key), .45f), 90)
        bar?.apply { color = if (offer.can) Theme.WHITE else Theme.alpha(Theme.WHITE, 235); offColor = trough }
        empty(trough)
        val price = kit.text(kit.coins(offer.price, 19f), 19f, words, 700).apply { setSingleLine() }
        return CandyRow(activity, slab, kit.dpf(5f), kit.dpf(16f)).apply {
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            minimumHeight = dp(BUTTON)
            contentDescription = price.text
            addView(main, LinearLayout.LayoutParams(0, barHeight, 1f))
            addView(price, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(14f) })
            setOnClickListener { SoundFx.play("tap"); Haptics.click(); offer.onTap(this) }
        }
    }

    /** The card's face: the item's colour, deepened just enough for white words. */
    private fun face(key: String): Int {
        val c = ShopIcon.color(key)
        val l = (0.299f * android.graphics.Color.red(c) + 0.587f * android.graphics.Color.green(c) + 0.114f * android.graphics.Color.blue(c)) / 255f
        return Theme.darken(c, maxOf(.05f, 1f - .6f / l))
    }

    /** The mystery box's card: a deep grape slab on its lip, slow rays from the box and twinkles. */
    private inner class MysteryCard : LinearLayout(activity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val face = Path()
        private val rays = Path()
        private val turned = Path()
        private val turning = android.graphics.Matrix()
        private val box = RectF()
        private val star = Path()
        private var turn = 0f
        private val spin = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 60_000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { turn = it.animatedValue as Float; if (isShown) Anim.repaint(this@MysteryCard) }
        }

        init {
            orientation = VERTICAL
            setWillNotDraw(false)
            clipChildren = false; clipToPadding = false
            setPadding(dp(18f), dp(16f), dp(18f), dp(18f) + kit.CARD_LIP)
        }

        override fun onAttachedToWindow() { super.onAttachedToWindow(); spin.start() }
        override fun onDetachedFromWindow() { spin.cancel(); super.onDetachedFromWindow() }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            val r = kit.dpf(RADIUS)
            box.set(0f, 0f, w.toFloat(), (h - kit.CARD_LIP).toFloat())
            face.reset(); face.addRoundRect(box, r, r, Path.Direction.CW)
            rays.reset()
            val reach = maxOf(w, h).toFloat() * 1.2f
            for (k in 0 until 14) {
                val a = Math.toRadians(k * 360.0 / 14); val b = Math.toRadians(k * 360.0 / 14 + 11.0)
                rays.moveTo(0f, 0f)
                rays.lineTo((cos(a) * reach).toFloat(), (sin(a) * reach).toFloat())
                rays.lineTo((cos(b) * reach).toFloat(), (sin(b) * reach).toFloat())
                rays.close()
            }
        }

        override fun onDraw(canvas: Canvas) {
            val r = kit.dpf(RADIUS)
            paint.color = Theme.darken(DEEP_GRAPE, .32f)
            box.set(0f, kit.CARD_LIP.toFloat(), width.toFloat(), height.toFloat()); canvas.drawRoundRect(box, r, r, paint)
            paint.color = DEEP_GRAPE; canvas.drawPath(face, paint)
            // Rays turn slowly around the question mark, cut to the card's face.
            val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
            val focus = if (rtl) width - paddingRight - kit.dpf(HERO) / 2f else paddingLeft + kit.dpf(HERO) / 2f
            turning.setRotate(turn); turning.postTranslate(focus, paddingTop + kit.dpf(HERO) / 2f)
            rays.transform(turning, turned)
            turned.op(face, Path.Op.INTERSECT)
            paint.color = Theme.alpha(Theme.WHITE, 24); canvas.drawPath(turned, paint)
            // In the far corner from the question mark.
            twinkle(canvas, width * (if (rtl) .07f else .93f), kit.dpf(20f), kit.dpf(8f))
            twinkle(canvas, width * (if (rtl) .20f else .80f), kit.dpf(40f), kit.dpf(5f))
        }

        private fun twinkle(canvas: Canvas, x: Float, y: Float, r: Float) {
            paint.color = Theme.alpha(Theme.YELLOW, 235)
            val p = star; p.reset()
            p.moveTo(x, y - r); p.lineTo(x + r * .28f, y - r * .28f); p.lineTo(x + r, y); p.lineTo(x + r * .28f, y + r * .28f)
            p.lineTo(x, y + r); p.lineTo(x - r * .28f, y + r * .28f); p.lineTo(x - r, y); p.lineTo(x - r * .28f, y - r * .28f); p.close()
            canvas.drawPath(p, paint)
        }
    }

    companion object {
        /** The flat butter behind the cards: the shop button's yellow, calm under the coloured cards. */
        val BACKDROP = 0xFFFFD98A.toInt()
        const val INK = Theme.INK
        /** Smaller words on a coloured card. */
        val SOFT = Theme.alpha(Theme.WHITE, 215)
        /** The update card's deep grape, so the white words and twinkles stand out. */
        const val DEEP_GRAPE = 0xFF6A35E8.toInt()
        const val RADIUS = 22f
        const val PAD = 14f
        const val TILE = 64f
        /** The mystery box's question mark. */
        const val HERO = 84f
        /** The flat bottom row of a maxed card: as tall as its tick. */
        const val BOTTOM = 40f
        /** The buy button along a card's bottom. */
        const val BUTTON = 54f
        private const val EMPTY = "empty"
    }
}
