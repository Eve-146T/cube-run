package cube.run.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import cube.run.R
import cube.run.core.Haptics
import cube.run.core.SoundFx

/**
 * The slab every candy control is drawn as: a rounded face sitting on a
 * darker lip, a soft gloss across the top, and a press that pushes the face
 * down onto the lip. Shared by [CandyButton] and [CandyChip].
 */
class CandyPainter(private val radius: Float, private val lip: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    var color = Theme.MINT
        set(v) { field = v; lipColor = Theme.darken(v, 0.32f); gloss = Theme.alpha(Theme.lighten(v, 0.6f), 110) }
    private var lipColor = Theme.darken(color, 0.32f)
    private var gloss = Theme.alpha(Theme.lighten(color, 0.6f), 110)
    /** 0 = resting on its lip, 1 = pushed fully down. */
    var press = 0f
    /** A ring around the face (e.g. a white outline on the stage), 0 = none. */
    var ring = 0f
    var ringColor = Theme.WHITE

    fun offset() = lip * press.coerceIn(0f, 1f)

    fun scale() = 1f - 0.04f * press

    fun draw(c: Canvas, w: Float, h: Float) {
        val off = offset()
        paint.style = Paint.Style.FILL
        paint.color = lipColor
        rect.set(0f, lip, w, h)
        c.drawRoundRect(rect, radius, radius, paint)
        paint.color = color
        rect.set(0f, off, w, h - lip + off)
        c.drawRoundRect(rect, radius, radius, paint)
        paint.color = gloss
        rect.set(radius * 0.5f, off + radius * 0.35f, w - radius * 0.5f, off + (h - lip) * 0.42f)
        c.drawRoundRect(rect, radius * 0.7f, radius * 0.7f, paint)
        if (ring > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = ring
            paint.color = ringColor
            rect.set(ring / 2f, off + ring / 2f, w - ring / 2f, h - lip + off - ring / 2f)
            c.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.FILL
        }
    }
}

/** Press feedback shared by the candy controls: squash to the lip, spring back (the click still fires). */
@SuppressLint("ClickableViewAccessibility")
private fun View.candyTouch(painter: CandyPainter, sound: Boolean = true) {
    // Press feedback belongs to the drawing, so touching a rising button cannot
    // cancel its entrance, arrow nudge, purchase pulse, or exit.
    var pressAnim: android.animation.ValueAnimator? = null
    fun press(to: Float, ms: Long) {
        pressAnim?.cancel()
        pressAnim = android.animation.ValueAnimator.ofFloat(painter.press, to).apply {
            duration = ms
            interpolator = if (to == 0f) android.view.animation.OvershootInterpolator(3f) else Anim.ease
            addUpdateListener { painter.press = it.animatedValue as Float; Anim.repaint(this@candyTouch) }
            start()
        }
    }
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) {
            pressAnim?.cancel(); pressAnim = null; painter.press = 0f
        }
    })
    setOnTouchListener { v, ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                press(1f, 70)
                if (sound) Haptics.tick()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                press(0f, 220)
            }
        }
        false
    }
}

/** A chunky candy button: coloured slab, darker lip, gloss; label in the display font. */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class CandyButton(ctx: Context, color: Int, label: CharSequence, textSize: Float, private val lipPx: Float, radiusPx: Float) : TextView(ctx) {
    private val painter = CandyPainter(radiusPx, lipPx).also { it.color = color }
    var color: Int
        get() = painter.color
        set(v) { painter.color = v; setTextColor(Theme.onColor(v)); invalidate() }

    init {
        text = label
        this.textSize = textSize
        typeface = Fonts.get(ctx, 700)
        gravity = Gravity.CENTER
        letterSpacing = 0.04f
        setTextColor(Theme.onColor(color))
        isClickable = true
        isFocusable = true
        candyTouch(painter)
    }

    fun setLabel(t: CharSequence) { text = t }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(left, top, right, bottom + lipPx.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.scale(painter.scale(), painter.scale(), width / 2f, height / 2f)
        painter.draw(canvas, width.toFloat(), height.toFloat())
        canvas.translate(0f, painter.offset())
        super.onDraw(canvas)
        canvas.restore()
    }
}

/** A square candy chip with an icon (menu buttons, toggles, back). */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class CandyChip(ctx: Context, color: Int, private val lipPx: Float, radiusPx: Float) : ImageView(ctx) {
    private val painter = CandyPainter(radiusPx, lipPx).also { it.color = color }
    var color: Int
        get() = painter.color
        set(v) { painter.color = v; invalidate() }

    init {
        scaleType = ScaleType.FIT_CENTER
        isClickable = true
        isFocusable = true
        candyTouch(painter)
    }

    fun ring(px: Float, color: Int) { painter.ring = px; painter.ringColor = color; invalidate() }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(left, top, right, bottom + lipPx.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.scale(painter.scale(), painter.scale(), width / 2f, height / 2f)
        painter.draw(canvas, width.toFloat(), height.toFloat())
        canvas.translate(0f, painter.offset())
        super.onDraw(canvas)
        canvas.restore()
    }
}

/** Text with an outline (white on ink is the stage look): the stroke is painted first, then the fill. */
@SuppressLint("ViewConstructor")
class OutlineTextView(ctx: Context, private val strokePx: Float, private val strokeColor: Int) : TextView(ctx) {
    private var drawingStroke = false
    private var fill = Theme.WHITE

    override fun setTextColor(color: Int) {
        fill = color
        super.setTextColor(color)
    }

    override fun invalidate() {
        if (drawingStroke) return // the stroke pass swaps colours; don't loop
        super.invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (strokePx > 0f) {
            drawingStroke = true
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokePx
            paint.strokeJoin = Paint.Join.ROUND
            super.setTextColor(strokeColor)
            super.onDraw(canvas)
            paint.style = Paint.Style.FILL
            super.setTextColor(fill)
            drawingStroke = false
        }
        super.onDraw(canvas)
    }
}

/** A row of [max] segments, [level] of them lit — the shop's level bars, the run-over rank. */
@SuppressLint("ViewConstructor")
class SegmentBar(ctx: Context, private val max: Int, private val gapPx: Float, private val radiusPx: Float) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    var level = 0
        set(v) { field = v; invalidate() }
    var color = Theme.MINT
        set(v) { field = v; invalidate() }
    var offColor = Theme.alpha(Theme.INK, 24)
    /** Segment currently popping (after a purchase), 0..1 extra scale. */
    var popIndex = -1
    var pop = 0f
        set(v) { field = v; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val segW = (w - gapPx * (max - 1)) / max
        for (i in 0 until max) {
            val x0 = i * (segW + gapPx)
            val grow = if (i == popIndex) pop * h * 0.45f else 0f
            paint.color = if (i < level) color else offColor
            rect.set(x0, -grow, x0 + segW, h + grow)
            canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
        }
    }
}

/** Factory for the HUD's programmatic widgets. Everything goes through here so the pieces match. */
@SuppressLint("ClickableViewAccessibility")
class UiKit(val ctx: Context) {
    private val density = ctx.resources.displayMetrics.density
    fun dp(v: Float) = (v * density).toInt()
    fun dpf(v: Float) = v * density

    enum class Size { BIG, NORMAL, SMALL }

    fun text(
        t: CharSequence, size: Float, color: Int = Theme.INK, weight: Int = 600, gravity: Int = Gravity.CENTER,
    ): TextView = TextView(ctx).apply {
        text = t
        textSize = size
        setTextColor(color)
        typeface = Fonts.get(ctx, weight)
        this.gravity = gravity
        includeFontPadding = false
    }

    /** White text with an ink outline: the look of everything drawn over the 3D stage. */
    fun stageText(t: CharSequence, size: Float, color: Int = Theme.WHITE, weight: Int = 700, gravity: Int = Gravity.CENTER, stroke: Float = size / 7f): OutlineTextView =
        OutlineTextView(ctx, dpf(stroke), Theme.INK).apply {
            text = t
            textSize = size
            setTextColor(color)
            typeface = Fonts.get(ctx, weight)
            this.gravity = gravity
            includeFontPadding = false
            val pad = dp(stroke + 2f)
            setPadding(pad, pad, pad, pad)
            setShadowLayer(dpf(4f), 0f, dpf(3f), 0x60000000)
        }

    /** A candy button. [color] is the slab; the label colour follows automatically. */
    fun button(label: CharSequence, color: Int, size: Size = Size.NORMAL, onClick: () -> Unit): CandyButton {
        val textSize = when (size) { Size.BIG -> 22f; Size.NORMAL -> 18f; Size.SMALL -> 14f }
        val radius = when (size) { Size.BIG -> 22f; Size.NORMAL -> 18f; Size.SMALL -> 14f }
        val lip = when (size) { Size.BIG -> 6f; Size.NORMAL -> 5f; Size.SMALL -> 4f }
        return CandyButton(ctx, color, label, textSize, dpf(lip), dpf(radius)).apply {
            when (size) {
                Size.BIG -> setPadding(dp(34f), dp(14f), dp(34f), dp(14f))
                Size.NORMAL -> setPadding(dp(26f), dp(11f), dp(26f), dp(11f))
                Size.SMALL -> setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
            }
            setOnClickListener { SoundFx.play("tap"); Haptics.click(); onClick() }
        }
    }

    /** A square icon chip. */
    fun chip(icon: Int, color: Int, tint: Int = Theme.onColor(color), label: String, onClick: () -> Unit): CandyChip =
        CandyChip(ctx, color, dpf(4f), dpf(14f)).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(tint)
            val pad = dp(11f)
            setPadding(pad, pad, pad, pad)
            contentDescription = label
            setOnClickListener { SoundFx.play("tap"); Haptics.click(); onClick() }
        }

    /** An on/off chip (sound, vibration, dev): lit in [onColor] when on, ghosted when off. */
    fun toggle(iconOn: Int, iconOff: Int, label: String, onColor: Int, isOn: () -> Boolean, set: (Boolean) -> Unit): CandyChip {
        lateinit var v: CandyChip
        fun paint() {
            val on = isOn()
            v.setImageResource(if (on) iconOn else iconOff)
            v.color = if (on) onColor else Theme.alpha(Theme.WHITE, 235)
            v.imageTintList = ColorStateList.valueOf(if (on) Theme.onColor(onColor) else Theme.MUTED)
            v.contentDescription = "$label ${if (on) "on" else "off"}"
        }
        v = chip(iconOn, onColor, label = label) { set(!isOn()); paint() }
        paint()
        return v
    }

    /** The one back button: every page puts it in the top-left corner. */
    fun backButton(onClick: () -> Unit): CandyChip = chip(R.drawable.ic_back, Theme.WHITE, Theme.INK, ctx.getString(R.string.cd_back), onClick)

    /** A white card with soft rounded corners and a faint lip. */
    fun card(fill: Int = Theme.CARD, stroke: Int? = null, radius: Float = 24f): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = cardDrawable(fill, stroke, radius)
    }

    /** The lip under every card face (px): views centring content on a card add this to their bottom padding. */
    val CARD_LIP: Int get() = dp(4f)

    fun cardDrawable(fill: Int = Theme.CARD, stroke: Int? = null, radius: Float = 24f): Drawable =
        android.graphics.drawable.LayerDrawable(arrayOf(
            GradientDrawable().apply { cornerRadius = dpf(radius); setColor(Theme.alpha(Theme.INK, 26)) },
            GradientDrawable().apply { cornerRadius = dpf(radius); setColor(fill); if (stroke != null) setStroke(dp(2.5f), stroke) },
        )).apply { setLayerInset(1, 0, 0, 0, CARD_LIP) }

    /** A small rounded label (a count, a price, a status). */
    fun pill(t: CharSequence, color: Int, textColor: Int = Theme.onColor(color), size: Float = 13f): TextView =
        text(t, size, textColor, 700).apply {
            setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
            background = GradientDrawable().apply { cornerRadius = dpf(20f); setColor(color) }
        }

    /** A translucent white pill for stats on the stage. */
    fun stagePill(value: CharSequence, label: String, valueColor: Int = Theme.WHITE): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
        background = GradientDrawable().apply { cornerRadius = dpf(20f); setColor(Theme.alpha(Theme.WHITE, 34)); setStroke(dp(1.5f), Theme.alpha(Theme.WHITE, 60)) }
        addView(stageText(value, 20f, valueColor, stroke = 2.5f))
        addView(text(label.uppercase(), 10f, Theme.alpha(Theme.WHITE, 200), 600).apply { letterSpacing = 0.12f })
    }

    fun segments(max: Int): SegmentBar = SegmentBar(ctx, max, dpf(3f), dpf(4f))

    /** "[coin] 120": the one way a coin amount is written anywhere in the app. [sizeSp] = the text size it sits in. */
    fun coins(amount: CharSequence, sizeSp: Float): CharSequence {
        val d = CoinIcon()
        val px = (sizeSp * 1.15f * ctx.resources.displayMetrics.scaledDensity).toInt()
        d.setBounds(0, 0, px, px)
        return android.text.SpannableStringBuilder("\u2009 \u2009").apply {
            setSpan(CenteredImageSpan(d), 1, 2, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(amount)
        }
    }

    fun coins(amount: Int, sizeSp: Float): CharSequence = coins(amount.toString(), sizeSp)

    /** An icon beside a value: "(coin) 120". [stage] = outlined white text for the 3D stage. */
    fun iconText(icon: Drawable, t: CharSequence, size: Float, color: Int, stage: Boolean = false, iconDp: Float = size * 1.1f): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            addView(ImageView(ctx).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)))
            val tv = if (stage) stageText(t, size, color, stroke = size / 8f) else text(t, size, color, 700)
            addView(tv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(6f) })
        }

    /** The text inside an [iconText]. */
    fun labelOf(row: LinearLayout): TextView = row.getChildAt(1) as TextView

    /** A white candy pill holding an icon + value (the HUD's coin bank, bubble stock…). */
    fun iconPill(icon: Drawable, t: CharSequence, color: Int = Theme.INK, size: Float = 15f, fill: Int = Theme.WHITE): LinearLayout =
        iconText(icon, t, size, color, iconDp = size * 1.05f).apply { // the icon matches the digits' height, not the whole line
            setPadding(dp(12f), dp(7f), dp(14f), dp(7f) + CARD_LIP) // the lip sits under the face: pad it so the content centres on the face
            background = cardDrawable(fill, null, 22f)
        }

    /** The bright page background: a gradient with big soft candy blobs. */
    fun pageBackground(): Drawable = BokehDrawable(Theme.PAGE_TOP, Theme.PAGE_BOTTOM)
}

/** A gradient with a few big soft-coloured circles: the page backdrop. */
class BokehDrawable(private val top: Int, private val bottom: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobs = intArrayOf(Theme.PINK, Theme.SKY, Theme.MINT, Theme.YELLOW, Theme.GRAPE)

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat(); val h = b.height().toFloat()
        paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, top, bottom, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(b, paint)
        paint.shader = null
        val spots = floatArrayOf(0.1f, 0.12f, 0.24f, 0.92f, 0.32f, 0.2f, 0.18f, 0.7f, 0.22f, 0.86f, 0.55f, 0.26f, 0.5f, 0.98f, 0.28f)
        for (i in blobs.indices) {
            val cx = w * spots[i * 3]; val cy = h * spots[i * 3 + 1]; val r = w * spots[i * 3 + 2]
            paint.shader = android.graphics.RadialGradient(cx, cy, r, Theme.alpha(blobs[i], 70), Theme.alpha(blobs[i], 0), android.graphics.Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, paint)
        }
        paint.shader = null
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
}

/**
 * An inline drawable centred on the text's x-height (ImageSpan's own
 * alignments sit on the baseline). The line's metrics grow evenly above and
 * below, so the text never drops toward the bottom of its button.
 */
class CenteredImageSpan(d: Drawable) : android.text.style.ImageSpan(d) {
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val b = drawable.bounds
        if (fm != null) {
            val pfm = paint.fontMetricsInt
            val centre = (pfm.ascent + pfm.descent) / 2
            val half = b.height() / 2 + 1
            fm.ascent = minOf(pfm.ascent, centre - half); fm.top = minOf(pfm.top, fm.ascent)
            fm.descent = maxOf(pfm.descent, centre + half); fm.bottom = maxOf(pfm.bottom, fm.descent)
        }
        return b.right
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val d = drawable
        val fm = paint.fontMetricsInt
        val ty = y + (fm.ascent + fm.descent) / 2f - d.bounds.height() / 2f
        canvas.save()
        canvas.translate(x, ty)
        d.draw(canvas)
        canvas.restore()
    }
}

/**
 * The boost control shown for the opening seconds of a run: five stacked
 * chevrons in the candy style — each tap lights one from the bottom up and
 * pushes the run faster (the game applies it through Stage.boostRequests).
 * Lit chevrons are hot orange with an ink outline and shimmer; the rest
 * wait as pale ghosts. The whole stack is the tap target.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class BoostArrows(ctx: Context, private val dpf: (Float) -> Float) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND; color = Theme.INK }
    private val path = android.graphics.Path()
    private var shimmer = 0f
    private var anim: android.animation.ValueAnimator? = null
    private var pop = 0f            // 1 → 0: the chevron just lit swells and settles (the stack itself never moves)
    private var popIndex = -1
    private var popAnim: android.animation.ValueAnimator? = null
    var taps = 0
        set(v) {
            if (v > field) { // one more lit: pop it
                popIndex = v - 1; pop = 1f
                popAnim?.cancel()
                popAnim = android.animation.ValueAnimator.ofFloat(1f, 0f).apply {
                    duration = 320; interpolator = Anim.spring
                    addUpdateListener { a -> pop = a.animatedValue as Float; Anim.repaint(this@BoostArrows) }
                    start()
                }
            }
            field = v; invalidate()
        }
    var max = 5

    init { isClickable = true }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim = android.animation.ValueAnimator.ofFloat(0f, 6.2832f).apply {
            duration = 1400; repeatCount = android.animation.ValueAnimator.INFINITE; interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { a -> shimmer = a.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() { anim?.cancel(); anim = null; super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val gap = h / max
        val cw = w * 0.34f; val ch = gap * 0.34f // leaves room for the press/pulse scale inside the view
        line.strokeWidth = dpf(3.5f)
        for (i in 0 until max) {
            val lit = i < taps
            val yBase = h - gap * (i + 0.5f) + ch * 0.5f
            val cx = w / 2f
            val k = if (i == popIndex) 1f + 0.35f * pop else 1f // the freshly lit one swells from its own centre
            val cy = yBase - ch * 0.5f
            path.reset()
            path.moveTo(cx - cw * k, cy + ch * 0.5f * k)
            path.lineTo(cx, cy - ch * 1.1f * k)
            path.lineTo(cx + cw * k, cy + ch * 0.5f * k)
            path.lineTo(cx + cw * 0.62f * k, cy + ch * 1.05f * k)
            path.lineTo(cx, cy - ch * 0.05f * k)
            path.lineTo(cx - cw * 0.62f * k, cy + ch * 1.05f * k)
            path.close()
            if (lit) {
                val wave = 0.5f + 0.5f * kotlin.math.sin(shimmer - i * 0.9f)
                fill.color = Theme.lerp(Theme.ORANGE, Theme.YELLOW, wave)
                canvas.drawPath(path, fill)
                line.color = Theme.INK
                canvas.drawPath(path, line)
            } else {
                fill.color = Theme.alpha(Theme.WHITE, 70)
                canvas.drawPath(path, fill)
                line.color = Theme.alpha(Theme.INK, 70)
                canvas.drawPath(path, line)
            }
        }
    }
}
