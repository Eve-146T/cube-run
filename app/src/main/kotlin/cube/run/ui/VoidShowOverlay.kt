package cube.run.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.core.VoidBeats

/**
 * The HUD's half of the void show, which the GL stage plays (game.stage.VoidShow).
 * A transparent, touch-swallowing layer over the whole HUD: it follows the GL clock
 * ([Stage.voidClock]), types the void's line over the blast's afterglow, tells the host
 * when to bring the shop back ([onReturn]) and when it is over ([onEnd]). A tap after the
 * blast skips to the return.
 */
@SuppressLint("ViewConstructor")
internal class VoidShowOverlay(
    context: Context,
    kit: UiKit,
    private val line: String,
    private val onReturn: () -> Unit,
    private val onEnd: () -> Unit,
) : FrameLayout(context) {
    private val words = kit.stageText("", 26f).apply {
        gravity = Gravity.CENTER
        setShadowLayer(0f, 0f, 0f, 0) // no drop shadow (it would also show the untyped words)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var started = false
    private var returned = false
    private var ended = false
    private var shown = -1
    private var waitedFrames = 0

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
        contentDescription = "The void stirs"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        addView(words, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            leftMargin = kit.dp(28f); rightMargin = kit.dp(28f)
        })
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Under the blast's remnant, above the reborn cube's reach.
        words.translationY = height * .75f - words.height / 2f
    }

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
        if (event.actionMasked == MotionEvent.ACTION_UP && Stage.voidClock > VoidBeats.NOVA + 0.4f) Stage.voidSkips.incrementAndGet()
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
        // Type the line: fast enough to finish well before the shop returns.
        val per = (1.4f / line.length.coerceAtLeast(1)).coerceAtMost(0.045f)
        val chars = ((t - VoidBeats.SPEAK) / per).toInt().coerceIn(0, line.length)
        if (chars != shown) {
            if (chars > shown && chars > 0 && !line[chars - 1].isWhitespace()) SoundFx.play("tap", rate = .55f + (chars * 7 % 5) * .03f, vol = .25f)
            shown = chars
            words.text = SpannableString(line).apply {
                if (chars < line.length) setSpan(ForegroundColorSpan(Color.TRANSPARENT), chars, line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (chars == line.length) contentDescription = line
        }
        words.alpha = 1f - VoidBeats.span(t, VoidBeats.RETURN - 0.3f, VoidBeats.RETURN)
        words.translationY = height * .75f - words.height / 2f - resources.displayMetrics.density * 12f * VoidBeats.span(t, VoidBeats.SPEAK, VoidBeats.SPEAK + 0.5f)
        // The sheet only rises once the camera has carried the cube clear of it.
        if (!returned && t >= VoidBeats.SHEET) { returned = true; onReturn() }
        Anim.repaint(this)
    }

    private fun finish() {
        if (ended) return
        ended = true
        if (!returned) { returned = true; onReturn() }
        onEnd()
    }
}
