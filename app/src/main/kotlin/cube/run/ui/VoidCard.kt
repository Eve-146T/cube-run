package cube.run.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import kotlin.math.min
import kotlin.math.sin

/**
 * The void's shop card: a black slab with a live black hole in it that slowly swallows
 * the stars around it, the last thing the void said, and the price. There is deliberately
 * no progress shown: every offering should feel hopeless. Paying hands this hole to
 * [VoidPurchaseView].
 */
@SuppressLint("ViewConstructor")
internal class VoidCardView(context: Context, kit: UiKit, line: String, price: View) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    val sigil = VoidSigilView(context).apply { tag = "void_sigil" }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = RectF()
    private val clip = Path()
    private val glow = RadialGradient(0f, 0f, 1f, intArrayOf(0x553b1f99, 0x1a2a1470, 0x00000000), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
    private val glowMatrix = android.graphics.Matrix()
    private val stars = Array(46) { i -> floatArrayOf((i * 0.618034f + .05f) % 1f, (i * 0.7548777f + .31f) % 1f, .6f + (i * 7 % 5) * .3f, i * 1.3f) }
    private val onScreen = Rect()
    private val ticker = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000; repeatCount = ValueAnimator.INFINITE
        // Only spend frames while some of the card is actually on screen.
        addUpdateListener { if (getGlobalVisibleRect(onScreen)) { invalidate(); sigil.invalidate() } }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setWillNotDraw(false)
        setPadding(kit.dp(20f), kit.dp(6f), kit.dp(20f), kit.dp(20f))
        addView(sigil, LayoutParams(LayoutParams.MATCH_PARENT, kit.dp(150f)))
        addView(kit.text(line, 19f, 0xffeee6ff.toInt(), 700).apply { minHeight = kit.dp(50f); gravity = Gravity.CENTER },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = kit.dp(2f) })
        addView(price, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = kit.dp(16f) })
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); ticker.start() }
    override fun onDetachedFromWindow() { ticker.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val radius = 24f * density
        body.set(0f, 0f, width.toFloat(), height.toFloat())
        clip.reset(); clip.addRoundRect(body, radius, radius, Path.Direction.CW)
        val saved = c.save(); c.clipPath(clip)
        c.drawColor(0xff040309.toInt())
        val hx = sigil.left + sigil.width / 2f; val hy = sigil.top + sigil.height / 2f
        glowMatrix.setScale(width * .85f, width * .85f); glowMatrix.postTranslate(hx, hy)
        glow.setLocalMatrix(glowMatrix)
        paint.shader = glow; paint.style = Paint.Style.FILL
        c.drawRect(body, paint); paint.shader = null
        val t = VoidHole.clock()
        for (s in stars) {
            // Each star creeps towards the hole on its own line and fades as it goes.
            val sx = s[0] * width; val sy = s[1] * height
            val drift = (t * .03f + s[3] * .13f) % 1f
            val px = sx + (hx - sx) * drift * .6f; val py = sy + (hy - sy) * drift * .6f
            val twinkle = .5f + .5f * sin(t * 1.9f + s[3])
            paint.color = Theme.alpha(if (s[3].toInt() % 3 == 0) 0xffc9b3ff.toInt() else Theme.WHITE, (twinkle * (1f - drift) * 170).toInt())
            c.drawCircle(px, py, s[2] * density, paint)
        }
        c.restoreToCount(saved)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * density; paint.color = 0xff3b2f5e.toInt()
        body.inset(.75f * density, .75f * density)
        c.drawRoundRect(body, radius, radius, paint)
    }
}

/** The card's black hole. It breathes; nothing else about it is still. */
internal class VoidSigilView(context: Context) : View(context) {
    private val hole = VoidHole()
    /** Shadow radius in px, shared with the purchase scene so the hand-over is seamless. */
    val radius get() = min(width, height) * .17f

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(c: Canvas) {
        val t = VoidHole.clock()
        hole.draw(c, width / 2f, height / 2f, radius * (1f + .03f * sin(t * 1.4f)), t)
    }
}
