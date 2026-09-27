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
import android.graphics.SweepGradient
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import kotlin.math.min
import kotlin.math.sin

/**
 * The void's shop card: a black slab with a live black hole in it that slowly swallows the
 * stars around it, a soft nebula behind it, a glow that creeps round the card's edge like an
 * event horizon, the last thing the void said, and the gold price button every card uses. There is
 * deliberately no progress shown: every offering should feel hopeless. Paying hands over to
 * the 3D show (game.stage.VoidShow).
 */
@SuppressLint("ViewConstructor")
internal class VoidCardView(context: Context, kit: UiKit, line: String, price: View) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    val sigil = VoidSigilView(context).apply { tag = "void_sigil" }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = RectF()
    private val clip = Path()
    private val glow = RadialGradient(0f, 0f, 1f, intArrayOf(0x663b1f99, 0x1f2a1470, 0x00000000), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
    private val rose = RadialGradient(0f, 0f, 1f, intArrayOf(0x33b0306e, 0x00000000), null, Shader.TileMode.CLAMP)
    private var edge: SweepGradient? = null
    private val shaderMatrix = android.graphics.Matrix()
    private val stars = Array(56) { i -> floatArrayOf((i * 0.618034f + .05f) % 1f, (i * 0.7548777f + .31f) % 1f, .6f + (i * 7 % 5) * .3f, i * 1.3f) }
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
        // The price slab punches out past its own bounds when you pay: never clip it.
        clipChildren = false; clipToPadding = false
        setPadding(kit.dp(20f), kit.dp(4f), kit.dp(20f), kit.dp(22f))
        addView(sigil, LayoutParams(LayoutParams.MATCH_PARENT, kit.dp(158f)))
        addView(kit.text(line, 19f, 0xfff1eaff.toInt(), 700).apply { minHeight = kit.dp(50f); gravity = Gravity.CENTER },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(price, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = kit.dp(18f) })
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); ticker.start() }
    override fun onDetachedFromWindow() { ticker.cancel(); super.onDetachedFromWindow() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        edge = SweepGradient(w / 2f, h / 2f,
            // Two glints on opposite sides, circling the card like light round a horizon.
            intArrayOf(0x00000000, 0x889b5cff.toInt(), 0xffd9c4ff.toInt(), 0x889b5cff.toInt(), 0x00000000, 0x889b5cff.toInt(), 0xffd9c4ff.toInt(), 0x889b5cff.toInt(), 0x00000000),
            floatArrayOf(0f, .12f, .2f, .28f, .5f, .62f, .7f, .78f, 1f))
    }

    override fun onDraw(c: Canvas) {
        val radius = 24f * density
        body.set(0f, 0f, width.toFloat(), height.toFloat())
        clip.reset(); clip.addRoundRect(body, radius, radius, Path.Direction.CW)
        val saved = c.save(); c.clipPath(clip)
        c.drawColor(0xff05030c.toInt())
        val hx = sigil.left + sigil.width / 2f; val hy = sigil.top + sigil.height / 2f
        paint.style = Paint.Style.FILL
        // A faint rose cloud low on the card, a violet one around the hole.
        shaderMatrix.setScale(width * .7f, width * .7f); shaderMatrix.postTranslate(width * .2f, height * .8f)
        rose.setLocalMatrix(shaderMatrix); paint.shader = rose; c.drawRect(body, paint)
        shaderMatrix.setScale(width * .9f, width * .9f); shaderMatrix.postTranslate(hx, hy)
        glow.setLocalMatrix(shaderMatrix); paint.shader = glow; c.drawRect(body, paint)
        paint.shader = null
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
        // The rim: a dim line, and a glow that creeps round it.
        body.inset(.75f * density, .75f * density)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * density; paint.color = 0xff2e2450.toInt()
        c.drawRoundRect(body, radius, radius, paint)
        edge?.let {
            shaderMatrix.setRotate(t * 24f % 360f, width / 2f, height / 2f)
            it.setLocalMatrix(shaderMatrix)
            paint.shader = it; paint.strokeWidth = 2.2f * density
            c.drawRoundRect(body, radius, radius, paint)
            paint.shader = null
        }
        paint.style = Paint.Style.FILL
    }
}

/** The card's black hole. It breathes; nothing else about it is still. */
internal class VoidSigilView(context: Context) : View(context) {
    private val hole = VoidHole()
    /** Shadow radius in px, shared with the purchase scene so the hand-over is seamless. */
    val radius get() = min(width, height) * .21f

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(c: Canvas) {
        val t = VoidHole.clock()
        hole.draw(c, width / 2f, height / 2f, radius * (1f + .03f * sin(t * 1.4f)), t)
    }
}
