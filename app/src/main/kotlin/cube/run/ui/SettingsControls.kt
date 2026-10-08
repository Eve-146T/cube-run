package cube.run.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import cube.run.core.Haptics
import kotlin.math.ceil
import kotlin.math.min

/** Quiet blue slots keep the whole settings page in the same colour family. */
private const val SLOT = 0xFFE4F0FA.toInt()
private const val SLOT_LIP = 0xFFBFD8EB.toInt()

/** A coin as a plain white glyph (a disc with a ring), for a gold tile. */
class CoinGlyph(private val ring: Int = Theme.GOLD) : Icon() {
    var off = false
    override fun draw(canvas: Canvas) {
        val b = bounds
        val r = min(b.width(), b.height()) / 2f
        paint.style = Paint.Style.FILL
        paint.color = if (off) Theme.MUTED else Theme.WHITE
        canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), r * 0.86f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.2f
        paint.color = if (off) SLOT else ring
        canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), r * 0.5f, paint)
        paint.style = Paint.Style.FILL
    }
}

/**
 * On/off: a little white cube that slides along a blue slot. The row
 * it sits in takes the tap (a bigger target); [set] only moves the cube.
 */
@SuppressLint("ViewConstructor")
class CandySwitch(ctx: Context, private val dpf: (Float) -> Float) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var pos = 0f
    private var anim: ValueAnimator? = null
    private var pressure = 0f
    private var pressAnim: ValueAnimator? = null
    var on = false
        private set

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun set(value: Boolean, animate: Boolean) {
        if (on == value && (animate || pos == if (value) 1f else 0f)) return
        on = value
        anim?.cancel()
        val to = if (value) 1f else 0f
        if (!animate || !isAttachedToWindow) { pos = to; invalidate(); return }
        anim = ValueAnimator.ofFloat(pos, to).apply {
            duration = 150; interpolator = Anim.ease
            addUpdateListener { pos = it.animatedValue as Float; Anim.repaint(this@CandySwitch) }
            start()
        }
    }

    internal fun press(down: Boolean) {
        pressAnim?.cancel()
        pressAnim = ValueAnimator.ofFloat(pressure, if (down) 1f else 0f).apply {
            duration = if (down) 45 else 120
            interpolator = Anim.ease
            addUpdateListener { pressure = it.animatedValue as Float; Anim.repaint(this@CandySwitch) }
            start()
        }
    }

    internal fun tap() {
        pressure = maxOf(pressure, .65f)
        press(false)
    }

    override fun onDetachedFromWindow() {
        anim?.cancel(); pressAnim?.cancel(); pressure = 0f
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val k = pos.coerceIn(0f, 1f)
        val lip = dpf(3f)
        val r = dpf(13f)
        val face = h - lip
        paint.color = Theme.lerp(SLOT_LIP, Theme.darken(Theme.SKY, 0.28f), k)
        rect.set(0f, 0f, w, h); canvas.drawRoundRect(rect, r, r, paint)
        paint.color = Theme.lerp(SLOT, Theme.SKY, k)
        rect.set(0f, 0f, w, face); canvas.drawRoundRect(rect, r, r, paint)
        val pad = dpf(4f)
        // Centre the thumb on the track's face, excluding both bottom lips.
        val cube = face - 2 * pad
        val travel = w - 2 * pad - cube
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val x = pad + travel * (if (rtl) 1f - k else k)
        val depression = lip * pressure.coerceIn(0f, 1f)
        paint.color = Theme.lerp(SLOT_LIP, Theme.darken(Theme.SKY, 0.38f), k)
        rect.set(x, pad + lip, x + cube, pad + cube + lip); canvas.drawRoundRect(rect, dpf(8f), dpf(8f), paint)
        paint.color = Theme.WHITE
        rect.set(x, pad + depression, x + cube, pad + cube + depression); canvas.drawRoundRect(rect, dpf(8f), dpf(8f), paint)
        paint.color = Theme.lerp(SLOT_LIP, Theme.darken(Theme.SKY, .25f), k)
        canvas.drawCircle(x + cube / 2f, face / 2f + depression, dpf(2f), paint)
    }
}

/**
 * A level picked by touch: tap or drag along it; tapping the highest lit step
 * again steps one down (so the lowest level is reachable on a short bar).
 * Announces itself as a slider and takes the accessibility scroll actions.
 */
@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
abstract class LevelPicker(ctx: Context, private val label: () -> String) : View(ctx) {
    protected open val animatePress = true
    abstract val min: Int
    abstract val max: Int
    var level = 0
        protected set
    var onPicked: (Int) -> Unit = {}
    private var downLevel = -1
    private var dragged = false

    init { isFocusable = true; isClickable = true }

    /** The level under [x] (1..max), mirrored for right-to-left layouts. */
    private fun levelAt(x: Float): Int {
        val u = (if (layoutDirection == LAYOUT_DIRECTION_RTL) width - x else x) / width.coerceAtLeast(1)
        return ceil(u * max).toInt().coerceIn(1, max)
    }

    fun show(value: Int) {
        val next = value.coerceIn(min, max)
        if (level == next) return
        level = next; invalidate()
    }

    private fun pick(value: Int) {
        val v = value.coerceIn(min, max)
        if (v == level) return
        val old = level
        level = v
        changed(old, v)
        onPicked(v)
        invalidate()
    }

    /** The level just changed by touch or accessibility. */
    protected open fun changed(from: Int, to: Int) { Haptics.tick() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (animatePress) {
                    animate().cancel()
                    animate().scaleY(.9f).setDuration(45).start()
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                downLevel = levelAt(event.x); dragged = false
                if (downLevel != level) { dragged = true; pick(downLevel) }
            }
            MotionEvent.ACTION_MOVE -> {
                val at = levelAt(event.x)
                if (at != downLevel) { dragged = true; downLevel = at; pick(at) }
            }
            MotionEvent.ACTION_UP -> {
                if (!dragged && downLevel == level) pick(level - 1)
                releasePress()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> { downLevel = -1; releasePress() }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun releasePress() {
        parent?.requestDisallowInterceptTouchEvent(false)
        if (!animatePress) return
        animate().cancel()
        animate().scaleY(1f).setDuration(150).setInterpolator(Anim.springSoft).start()
    }

    override fun onDetachedFromWindow() {
        animate().cancel(); scaleY = 1f
        super.onDetachedFromWindow()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.contentDescription = label()
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, min.toFloat(), max.toFloat(), level.toFloat())
        if (level < max) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        if (level > min) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when (action) {
        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { if (animatePress) Anim.tap(this); pick(level + 1); true }
        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { if (animatePress) Anim.tap(this); pick(level - 1); true }
        else -> super.performAccessibilityAction(action, arguments)
    }
}

/** Volume as a staircase of chunky steps that grow toward the loud end; greyed while muted. */
@SuppressLint("ViewConstructor")
class VolumeSteps(ctx: Context, private val dpf: (Float) -> Float, label: () -> String) : LevelPicker(ctx, label) {
    override val animatePress = false
    override val min = 1
    override val max = cube.run.data.Settings.VOLUME_STEPS
    var muted = false
        set(v) { if (field != v) { field = v; invalidate() } }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val gap = dpf(4f)
        val step = (w - gap * (max - 1)) / max
        val r = min(dpf(5f), step / 2f)
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        for (i in 0 until max) {
            val x = (if (rtl) max - 1 - i else i) * (step + gap)
            val top = h * (1f - (0.36f + 0.64f * i / (max - 1)))
            val lit = i < level
            rect.set(x, top, x + step, h)
            paint.color = when { !lit -> SLOT; muted -> 0xFFD9D1EF.toInt(); else -> Theme.darken(Theme.SKY, 0.22f) }
            canvas.drawRoundRect(rect, r, r, paint)
            if (lit && !muted) { // the face sits on a 3dp lip
                paint.color = Theme.SKY
                rect.set(x, top, x + step, h - dpf(3f))
                canvas.drawRoundRect(rect, r, r, paint)
            }
        }
    }
}

/**
 * The start speed: the boost chevrons laid on their side, one interlocking
 * candy arrow per press you own (five, up to ten with Even faster starts),
 * pointing the way the run goes. Lit ones glow orange to yellow, the extra
 * presses sky blue (as in the run); the one just lit pops.
 */
@SuppressLint("ViewConstructor")
class StartSpeedBar(ctx: Context, private val dpf: (Float) -> Float, label: () -> String) : LevelPicker(ctx, label) {
    override val min = 0
    override var max = 5
        private set
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val corners = CornerPathEffect(dpf(5f))
    private var pop = 0f
    private var popIndex = -1
    private var popAnim: ValueAnimator? = null

    fun configure(presses: Int, value: Int) {
        val next = presses.coerceIn(1, 10)
        if (max != next) { max = next; invalidate() }
        show(value)
    }

    override fun changed(from: Int, to: Int) {
        super.changed(from, to)
        popIndex = (if (to > from) to - 1 else from - 1).coerceAtLeast(0)
        popAnim?.cancel()
        popAnim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 180; interpolator = Anim.ease
            addUpdateListener { pop = it.animatedValue as Float; Anim.repaint(this@StartSpeedBar) }
            start()
        }
    }

    override fun onDetachedFromWindow() { popAnim?.cancel(); super.onDetachedFromWindow() }

    private fun colorOf(i: Int): Int =
        if (i < 5) Theme.lerp(Theme.ORANGE, Theme.YELLOW, i / 4f)
        else Theme.lerp(0xFF287BE8.toInt(), Theme.SKY, (i - 5) / 4f)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val lip = dpf(4f)
        val face = h - lip
        val notch = face * 0.34f                       // how far each arrow's point reaches into the next
        val gap = dpf(3f)
        val cell = (w - notch) / max                     // the arrows interlock, so they share the notch
        paint.pathEffect = corners
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        canvas.save()
        if (rtl) canvas.scale(-1f, 1f, w / 2f, h / 2f)
        for (i in 0 until max) {
            val x0 = i * cell + gap / 2f
            val x1 = (i + 1) * cell + notch - gap / 2f
            val lit = i < level
            val k = if (i == popIndex) 1f + 0.12f * pop else 1f
            canvas.save()
            canvas.scale(k, k, (x0 + x1) / 2f, face / 2f)
            arrow(x0, x1, 0f, face, notch, first = i == 0)
            paint.color = if (lit) Theme.darken(colorOf(i), 0.3f) else SLOT_LIP
            canvas.save(); canvas.translate(0f, lip); canvas.drawPath(path, paint); canvas.restore()
            paint.color = if (lit) colorOf(i) else SLOT
            canvas.drawPath(path, paint)
            if (lit) { // gloss along the top
                paint.color = Theme.alpha(Theme.WHITE, 90)
                arrow(x0 + notch * 0.5f, x1 - notch * 0.5f, face * 0.14f, face * 0.36f, notch * 0.5f, first = i == 0)
                canvas.drawPath(path, paint)
            }
            canvas.restore()
        }
        canvas.restore()
        paint.pathEffect = null
    }

    /** One arrow from [x0] to its point at [x1]; all but the first have a notch where the previous point sits. */
    private fun arrow(x0: Float, x1: Float, top: Float, bottom: Float, notch: Float, first: Boolean) {
        val mid = (top + bottom) / 2f
        path.reset()
        path.moveTo(x0, top)
        path.lineTo(x1 - notch, top)
        path.lineTo(x1, mid)
        path.lineTo(x1 - notch, bottom)
        path.lineTo(x0, bottom)
        if (!first) path.lineTo(x0 + notch, mid)
        path.close()
    }
}
