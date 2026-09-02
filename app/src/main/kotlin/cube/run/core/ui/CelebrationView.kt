package cube.run.core.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import cube.run.core.Palette
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The new-record flourish: a slowly turning sunburst behind the game-over
 * card plus a shower of confetti. Full-screen, non-interactive, drawn with
 * plain Canvas calls; [focusY] (0..1) is where the rays radiate from.
 * Programmatic-only (never inflated), hence the suppressed ViewConstructor.
 */
@SuppressLint("ViewConstructor")
class CelebrationView(ctx: Context, private val accent: Int, private val focusY: Float = 0.32f) : View(ctx) {

    private class Confetto(var x: Float, var y: Float, var vx: Float, var vy: Float, var rot: Float, var vr: Float, val w: Float, val h: Float, val color: Int)

    private val rnd = Random(System.nanoTime())
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val confPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ray = Path()
    private val rays = 16
    private var angle = 0f
    private val confetti = ArrayList<Confetto>()
    private var lastT = 0L
    private var elapsed = 0f
    private val palette = intArrayOf(Ui.GOLD, accent, Ui.CYAN, Ui.PURPLE, 0xFFFF4BD8.toInt(), 0xFFFFFFFF.toInt())
    private var spin: ValueAnimator? = null

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastT = System.nanoTime()
        spin = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 24_000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { a -> angle = a.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        spin?.cancel(); spin = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = w / 2f; val cy = h * focusY
        rayPaint.shader = RadialGradient(
            cx, cy, h * 0.55f,
            intArrayOf(Palette.withAlpha(Ui.GOLD, 150), Palette.withAlpha(Ui.GOLD, 60), Palette.withAlpha(Ui.GOLD, 0)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        confetti.clear()
        repeat(90) {
            confetti.add(Confetto(
                x = rnd.nextFloat() * w, y = -rnd.nextFloat() * h * 0.6f,
                vx = (rnd.nextFloat() - 0.5f) * w * 0.15f, vy = h * (0.25f + rnd.nextFloat() * 0.35f),
                rot = rnd.nextFloat() * 360f, vr = (rnd.nextFloat() - 0.5f) * 540f,
                w = w * (0.014f + rnd.nextFloat() * 0.012f), h = w * (0.008f + rnd.nextFloat() * 0.008f),
                color = palette[rnd.nextInt(palette.size)],
            ))
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h * focusY
        val r = h * 0.7f
        // sunburst: alternating wedges, rotating
        for (i in 0 until rays) {
            val a0 = Math.toRadians((angle + i * 360f / rays).toDouble())
            val a1 = Math.toRadians((angle + (i + 0.5f) * 360f / rays).toDouble())
            ray.reset()
            ray.moveTo(cx, cy)
            ray.lineTo(cx + r * cos(a0).toFloat(), cy + r * sin(a0).toFloat())
            ray.lineTo(cx + r * cos(a1).toFloat(), cy + r * sin(a1).toFloat())
            ray.close()
            canvas.drawPath(ray, rayPaint)
        }
        // confetti: simple ballistic drift, fades out after a few seconds
        val now = System.nanoTime()
        val dt = ((now - lastT) / 1e9f).coerceIn(0f, 0.05f)
        lastT = now
        elapsed += dt
        val alpha = ((5.5f - elapsed) / 1.5f).coerceIn(0f, 1f)
        if (alpha > 0f) {
            for (c in confetti) {
                c.x += (c.vx + sin((elapsed * 3f + c.rot) * 0.05f) * w * 0.04f) * dt
                c.y += c.vy * dt
                c.rot += c.vr * dt
                if (c.y > h + c.h) continue
                confPaint.color = Palette.withAlpha(c.color, (255 * alpha).toInt())
                canvas.save()
                canvas.rotate(c.rot, c.x, c.y)
                canvas.drawRect(c.x - c.w / 2, c.y - c.h / 2, c.x + c.w / 2, c.y + c.h / 2, confPaint)
                canvas.restore()
            }
        }
    }
}
