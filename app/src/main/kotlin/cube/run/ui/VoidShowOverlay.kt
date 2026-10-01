package cube.run.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.net.Uri
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.core.VoidBeats
import cube.run.R

/**
 * The HUD's half of the void show, which the GL stage plays (game.stage.VoidShow).
 * A transparent, touch-swallowing layer over the whole HUD: it follows the GL clock
 * ([Stage.voidClock]), types the void's line over the blast's afterglow, tells the host
 * when to bring the shop back ([onReturn]) and when it is over ([onEnd]). The stage holds
 * on the spoken line until a tap; a tap while it is still typing finishes it. The line types
 * into the space under the reborn cube; a long one either slides up through it like credits
 * or shrinks to fit (an experiment, see [Fit]). A line that is a link opens when tapped.
 */
@SuppressLint("ViewConstructor")
internal class VoidShowOverlay(
    context: Context,
    kit: UiKit,
    private val line: String,
    private val onReturn: () -> Unit,
    private val onEnd: () -> Unit,
) : FrameLayout(context) {
    /** How a line too long for the space under the cube is shown. Experiment: one will go. */
    enum class Fit { SLIDE, SHRINK }
    private val fit = runCatching {
        Fit.valueOf(context.getSharedPreferences("progress", Context.MODE_PRIVATE).getString("void_text_fit", null) ?: "SLIDE")
    }.getOrDefault(Fit.SLIDE)

    private val density = resources.displayMetrics.density
    private val link = line.startsWith("https://")
    private val words = kit.stageText("", 26f).apply {
        gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
        if (link) paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
        setShadowLayer(0f, 0f, 0f, 0) // no drop shadows
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    /** The space under the cube the line lives in; what slides out of its top fades away. */
    private val box = FadeBox(context, 28f * density).apply { addView(words, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)) }
    private val side = kit.dp(28f)
    private var started = false
    private var returned = false
    private var ended = false
    private var shown = -1
    private var waitedFrames = 0
    private var lift = 0f
    private var slid = 0f
    private var scale = 1f
    private var targetScale = 1f
    private var lastFrame = 0L

    private val frame = object : Runnable {
        override fun run() {
            if (ended || !isAttachedToWindow) return
            follow(Stage.voidClock)
            if (!ended) postOnAnimation(this)
        }
    }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.cd_void_stirs)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        addView(box, LayoutParams(LayoutParams.MATCH_PARENT, 0, Gravity.TOP).apply { leftMargin = side; rightMargin = side })
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // From just under the reborn cube down to the bottom edge.
        val top = (h * REGION_TOP).toInt()
        val height = h - top - kit24()
        (box.layoutParams as LayoutParams).let { if (it.height != height || it.topMargin != top) { it.height = height; it.topMargin = top; box.layoutParams = it } }
        breaks = null
    }

    private fun kit24() = (24f * density).toInt()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postOnAnimation(frame)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP || Stage.voidClock <= VoidBeats.NOVA + 0.4f) return true
        val onLink = link && shown == line.length && event.y >= box.top && event.y <= box.bottom
        if (onLink) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(line))) }
        else Stage.voidSkips.incrementAndGet()
        return true
    }

    private fun follow(t: Float) {
        if (t >= 0f) started = true
        else if (!started) {
            // The stage never picked the request up (it was not showing the shop): don't strand the HUD.
            if (++waitedFrames > 90) finish()
            return
        }
        if (started && (t < 0f || t >= VoidBeats.END)) { finish(); return }
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1e9f).coerceAtMost(0.1f)
        lastFrame = now
        // Type the line on the speech clock, which keeps running while the show holds.
        var chars = (Stage.voidSpeech / VoidBeats.typeStep(line.length)).toInt().coerceIn(0, line.length)
        if (chars < line.length && chars > 0 && Character.isHighSurrogate(line[chars - 1])) chars-- // never half an emoji
        if (chars != shown) {
            if (chars > shown && chars > 0 && !line[chars - 1].isWhitespace()) SoundFx.play("tap", rate = .55f + (chars * 7 % 5) * .03f, vol = .25f)
            shown = chars
            if (fit == Fit.SHRINK) targetScale = fitScale(chars)
            words.text = typed(chars)
            if (chars == line.length) contentDescription = line
        }
        val ease = 1f - kotlin.math.exp(-dt * 9f)
        if (fit == Fit.SHRINK && kotlin.math.abs(targetScale - scale) > 0.001f) {
            scale += (targetScale - scale) * ease
            if (kotlin.math.abs(targetScale - scale) < 0.002f) scale = targetScale
            sizeWords()
        }
        // SLIDE: once the line outgrows the space, it glides up so the newest words stay in view.
        val over = (words.height * scale - box.height).coerceAtLeast(0f)
        slid += (over - slid) * ease
        words.translationY = -slid
        box.fade = (slid / (28f * density)).coerceIn(0f, 1f)
        words.alpha = 1f - VoidBeats.span(t, VoidBeats.RETURN - 0.3f, VoidBeats.RETURN)
        lift = density * 12f * VoidBeats.span(t, VoidBeats.SPEAK, VoidBeats.SPEAK + 0.5f)
        box.translationY = -lift
        // The sheet only rises once the camera has carried the cube clear of it.
        if (!returned && t >= VoidBeats.SHEET) { returned = true; onReturn() }
        Anim.repaint(this)
    }

    /** SHRINK: the words are laid out wider and scaled down, so the outline shrinks with them. */
    private fun sizeWords() {
        val lp = words.layoutParams as LayoutParams
        val width = (box.width / scale).toInt()
        if (lp.width != width) { lp.width = width; words.layoutParams = lp; breaks = null }
        words.pivotX = width / 2f; words.pivotY = 0f
        words.scaleX = scale; words.scaleY = scale
        words.translationX = (box.width - width) / 2f
        words.text = typed(shown)
    }

    /** SHRINK: the largest scale at which the words typed so far fit the space. */
    private fun fitScale(chars: Int): Float {
        val inner = box.width - words.totalPaddingLeft - words.totalPaddingRight
        if (inner <= 0 || box.height <= 0 || chars == 0) return scale
        fun fits(s: Float): Boolean {
            val pad = words.totalPaddingLeft + words.totalPaddingRight
            val w = (box.width / s).toInt() - pad
            val h = android.text.StaticLayout.Builder.obtain(line, 0, chars, words.paint, w).setIncludePad(false).build().height +
                words.totalPaddingTop + words.totalPaddingBottom
            return h * s <= box.height
        }
        if (fits(scale)) return scale // it never grows back while the line types
        var lo = MIN_SCALE; var hi = scale
        repeat(8) { val mid = (lo + hi) / 2f; if (fits(mid)) lo = mid else hi = mid }
        return lo
    }

    private var breaks: IntArray? = null

    /**
     * The first [chars] of the line, broken where the whole sentence will break, so nothing
     * re-wraps while it types.
     */
    private fun typed(chars: Int): String {
        val width = (words.layoutParams?.width?.takeIf { it > 0 } ?: words.width) - words.totalPaddingLeft - words.totalPaddingRight
        val ends = breaks ?: if (width <= 0) return line.substring(0, chars) else
            android.text.StaticLayout.Builder.obtain(line, 0, line.length, words.paint, width).build().let { layout ->
                IntArray(layout.lineCount) { layout.getLineEnd(it) }
            }.also { breaks = it }
        val out = StringBuilder()
        var start = 0
        for (end in ends) {
            if (chars <= start) break
            if (out.isNotEmpty()) out.append('\n')
            out.append(line, start, minOf(chars, end).coerceAtLeast(start))
            start = end
        }
        return out.toString().trimEnd()
    }

    private fun finish() {
        if (ended) return
        ended = true
        if (!returned) { returned = true; onReturn() }
        onEnd()
    }

    private companion object {
        /** The top of the space the words get, as a share of the screen: just under the reborn cube. */
        const val REGION_TOP = .72f
        const val MIN_SCALE = .3f
    }
}

/** Clips its child to its bounds and fades out the top [edge] pixels by [fade] (0..1). */
@SuppressLint("ViewConstructor")
private class FadeBox(context: Context, private val edge: Float) : FrameLayout(context) {
    var fade = 0f
    private val eraser = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private var shaderEdge = -1f

    init { clipChildren = true; clipToPadding = true; setWillNotDraw(false) }

    // The words may run taller than the box: it only shows a window onto them.
    override fun measureChildWithMargins(child: android.view.View, widthSpec: Int, widthUsed: Int, heightSpec: Int, heightUsed: Int) =
        super.measureChildWithMargins(child, widthSpec, widthUsed, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightUsed)

    override fun dispatchDraw(canvas: Canvas) {
        if (fade <= 0f) { super.dispatchDraw(canvas); return }
        val saved = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.dispatchDraw(canvas)
        if (shaderEdge != edge) {
            eraser.shader = LinearGradient(0f, 0f, 0f, edge, 0xff000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
            shaderEdge = edge
        }
        eraser.alpha = (255 * fade).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), edge, eraser)
        canvas.restoreToCount(saved)
    }
}
