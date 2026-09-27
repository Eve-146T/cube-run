package cube.run.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Shader
import android.view.View
import android.widget.FrameLayout
import cube.run.core.JackpotBeats
import cube.run.R
import cube.run.core.Stage
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * The jackpot's number: a big gold coin count that pops in when the rays
 * blow open, rolls up while the coins pour out, slams onto the total and
 * then flies into the HUD's coin pill. It follows the GL-owned
 * [Stage.jackpotClock], so a pause freezes it with the 3D show. It never
 * awards anything and never takes a touch.
 */
@SuppressLint("ViewConstructor")
class JackpotCounter(context: Context, private val kit: UiKit, private val target: () -> PointF?,
                     private val onShowing: (Boolean) -> Unit = {}) : FrameLayout(context) {
    private var active = false
    private var playing = false
    private var announced = false

    /** Coin + number at its natural size, rasterized once per value and moved as a layer (no shimmer). */
    private val badge = Badge(context).apply {
        tag = "jackpot_badge"
        setLayerType(LAYER_TYPE_HARDWARE, null)
        visibility = INVISIBLE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private val frame = object : Runnable {
        override fun run() {
            if (!playing) return
            if (!present()) { stop(); return }
            postOnAnimation(this)
        }
    }

    init {
        isClickable = false; isFocusable = false
        clipChildren = false; clipToPadding = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(badge, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    /** A jackpot show started on the GL thread: follow its clock. */
    fun show() {
        announced = false
        play()
    }

    fun setRunActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) play() else { stop(); badge.visibility = INVISIBLE }
    }

    fun reset() {
        active = false
        stop()
        badge.visibility = INVISIBLE
    }

    private fun play() {
        if (!active || playing || Stage.jackpotClock < 0f || !isAttachedToWindow) return
        playing = true
        onShowing(true)
        postOnAnimation(frame)
    }

    private fun stop() {
        if (playing) onShowing(false)
        playing = false
        removeCallbacks(frame)
    }

    /** Lay the badge out for the clock's current moment. False once the show is over. */
    private fun present(): Boolean {
        val t = Stage.jackpotClock
        val amount = Stage.jackpotAmount
        if (t < 0f || amount <= 0) { badge.visibility = INVISIBLE; return false }
        if (t < JackpotBeats.BURST || t > JackpotBeats.BANKED + 0.1f || width == 0) { badge.visibility = INVISIBLE; return true }
        badge.fitTo((width * 0.94f / (1f + KICK)).toInt(), amount) // room for the slam's kick at the widest total
        badge.value = JackpotBeats.rolled(amount, t)
        if (!announced && t >= JackpotBeats.SLAM) {
            announced = true
            badge.contentDescription = context.getString(R.string.cd_jackpot_win, number(amount))
        }
        // Pop in with an overshoot; a kick on every slam; a pulse while the digits race.
        val inT = ((t - JackpotBeats.BURST) / 0.45f).coerceIn(0f, 1f)
        val pop = if (inT >= 1f) 1f else 1f + 2.2f * (inT - 1f) * (inT - 1f) * (inT - 1f) + 1.2f * (inT - 1f) * (inT - 1f)
        val kick = if (t >= JackpotBeats.SLAM) KICK * exp(-(t - JackpotBeats.SLAM) * 7f) else 0f
        val race = if (t < JackpotBeats.SLAM) 0.025f * kotlin.math.sin(t * 40f) * (1f - (t - JackpotBeats.BURST) / (JackpotBeats.SLAM - JackpotBeats.BURST)) else 0f
        // Then shrink into the coin pill.
        val fly = JackpotBeats.span(t, JackpotBeats.RETURN, JackpotBeats.BANKED)
        val homeX = width / 2f
        val homeY = height * 0.635f
        val goal = target()
        val gx = goal?.x ?: homeX
        val gy = goal?.y ?: kit.dpf(90f)
        val cx = homeX + (gx - homeX) * fly
        val cy = homeY + (gy - homeY) * fly - kit.dpf(60f) * kotlin.math.sin(fly * Math.PI.toFloat())
        val endScale = kit.dpf(22f) / max(1f, badge.measuredHeight.toFloat())
        val scale = max(0.01f, pop * (1f + kick + race)) * (1f + (endScale - 1f) * fly)
        badge.pivotX = badge.measuredWidth / 2f
        badge.pivotY = badge.measuredHeight / 2f
        badge.translationX = cx - badge.measuredWidth / 2f
        badge.translationY = cy - badge.measuredHeight / 2f
        badge.scaleX = scale; badge.scaleY = scale
        // It melts into the pill on the frame the pill takes the new total.
        badge.alpha = min(1f, inT * 3f) * (1f - JackpotBeats.span(t, JackpotBeats.BANKED - 0.02f, JackpotBeats.BANKED + 0.08f))
        badge.visibility = VISIBLE
        return true
    }

    private companion object { const val KICK = 0.18f }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); play() }
    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }

    /** The coin and the gold number, outlined in ink like all stage text. */
    private inner class Badge(context: Context) : View(context) {
        private val nominal = kit.dpf(76f)
        private var size = nominal
        private var maxWidth = Int.MAX_VALUE
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Fonts.get(context, 700)
            fontFeatureSettings = "tnum" // the digits hold still while they roll
        }
        private val stroke = Paint(fill).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            color = Theme.INK
        }
        private val coin = CoinIcon()
        private var label = "0"
        private var shaderFor = -1f

        private var sizedFor = -1

        var value = -1
            set(v) {
                if (field == v) return
                field = v
                label = number(v)
                invalidate()
            }

        /** Re-measure only when the room or the total changes, never per rolled digit. */
        fun fitTo(available: Int, amount: Int) {
            if (available == maxWidth && amount == sizedFor) return
            maxWidth = available; sizedFor = amount
            requestLayout()
        }

        private fun widthAt(px: Float): Float {
            fill.textSize = px
            // Size for the widest total this amount can roll through, so the badge never jumps.
            val widest = number(sizedFor.coerceAtLeast(0)).replace(Regex("[0-9]"), "8")
            return px * 1.05f + px * 0.22f + fill.measureText(widest) + px * 0.3f
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            size = nominal
            while (size > kit.dpf(24f) && widthAt(size) > maxWidth) size -= kit.dpf(2f)
            fill.textSize = size; stroke.textSize = size
            stroke.strokeWidth = size / 6.5f
            val fm = fill.fontMetrics
            setMeasuredDimension(widthAt(size).toInt(), (fm.descent - fm.ascent + size * 0.3f).toInt())
        }

        override fun onDraw(canvas: Canvas) {
            val fm = fill.fontMetrics
            val baseline = height / 2f - (fm.ascent + fm.descent) / 2f
            if (shaderFor != size) {
                shaderFor = size
                // bright yellow at the top of the glyphs into deep gold, like the coins
                fill.shader = LinearGradient(0f, baseline + fm.ascent * 0.8f, 0f, baseline, Theme.lighten(Theme.YELLOW, 0.35f), Theme.GOLD, Shader.TileMode.CLAMP)
            }
            val textW = fill.measureText(label)
            val coinD = size * 1.05f
            val gap = size * 0.22f
            var x = (width - (coinD + gap + textW)) / 2f
            val cy = height / 2f
            val ring = stroke.strokeWidth / 2f
            stroke.style = Paint.Style.FILL
            canvas.drawCircle(x + coinD / 2f, cy, coinD / 2f + ring, stroke)
            stroke.style = Paint.Style.STROKE
            coin.setBounds(x.toInt(), (cy - coinD / 2f).toInt(), (x + coinD).toInt(), (cy + coinD / 2f).toInt())
            coin.draw(canvas)
            x += coinD + gap
            canvas.drawText(label, x, baseline, stroke)
            canvas.drawText(label, x, baseline, fill)
        }
    }
}
