package cube.run.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Confetti + (optionally) a slowly turning sunburst: the new-record
 * flourish, the mystery-box "rare pull" pop, a purchase. Full-screen,
 * non-interactive, plain Canvas. Pieces come in three shapes (squares,
 * dots, ribbons), tumble (their width flutters as they turn), catch the
 * air (drag) and drift sideways. [focusY] (0..1) is where the rays radiate
 * from and where a [burst] starts; otherwise the confetti rains from the top.
 */
@SuppressLint("ViewConstructor")
class CelebrationView(
    ctx: Context,
    private val focusY: Float = 0.32f,
    private val rays: Boolean = true,
    private val rayColor: Int = Theme.GOLD,
    private val count: Int = 140,
    private val burst: Boolean = false,
    private val seconds: Float = 5.5f,
) : View(ctx) {

    private class Confetto(
        var x: Float, var y: Float, var vx: Float, var vy: Float, var rot: Float, var vr: Float,
        val w: Float, val h: Float, val color: Int, val kind: Int, var flutter: Float, val flutterV: Float,
    )

    private val rnd = Random(System.nanoTime())
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val confPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ray = Path()
    private val rect = RectF()
    private val rayCount = 16
    private var angle = 0f
    private val confetti = ArrayList<Confetto>()
    private var lastT = 0L
    private var elapsed = 0f
    private val palette = intArrayOf(Theme.GOLD, Theme.MINT, Theme.SKY, Theme.GRAPE, Theme.PINK, Theme.WHITE, Theme.LIME, Theme.ORANGE, Theme.CYAN)
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
            cx, cy, h * 0.6f,
            intArrayOf(Theme.alpha(rayColor, 170), Theme.alpha(rayColor, 70), Theme.alpha(rayColor, 0)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        confetti.clear()
        repeat(count) {
            val kind = rnd.nextInt(3)
            val base = w * (0.016f + rnd.nextFloat() * 0.014f)
            val cw = if (kind == 2) base * 0.55f else base
            val ch = if (kind == 2) base * 2.6f else base * (0.7f + rnd.nextFloat() * 0.3f)
            val color = palette[rnd.nextInt(palette.size)]
            if (burst) { // out from the focus point in every direction, then rain
                val a = rnd.nextFloat() * 6.2832f
                val sp = h * (0.35f + rnd.nextFloat() * 0.75f)
                confetti.add(Confetto(cx, cy, cos(a) * sp, sin(a) * sp - h * 0.25f, rnd.nextFloat() * 360f, (rnd.nextFloat() - 0.5f) * 900f,
                    cw, ch, color, kind, rnd.nextFloat() * 6.28f, 4f + rnd.nextFloat() * 6f))
            } else {
                confetti.add(Confetto(rnd.nextFloat() * w, -rnd.nextFloat() * h * 0.7f, (rnd.nextFloat() - 0.5f) * w * 0.12f, h * (0.18f + rnd.nextFloat() * 0.3f),
                    rnd.nextFloat() * 360f, (rnd.nextFloat() - 0.5f) * 500f, cw, ch, color, kind, rnd.nextFloat() * 6.28f, 3f + rnd.nextFloat() * 5f))
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h * focusY
        if (rays) {
            val r = h * 0.75f
            for (i in 0 until rayCount) {
                val a0 = Math.toRadians((angle + i * 360f / rayCount).toDouble())
                val a1 = Math.toRadians((angle + (i + 0.5f) * 360f / rayCount).toDouble())
                ray.reset()
                ray.moveTo(cx, cy)
                ray.lineTo(cx + r * cos(a0).toFloat(), cy + r * sin(a0).toFloat())
                ray.lineTo(cx + r * cos(a1).toFloat(), cy + r * sin(a1).toFloat())
                ray.close()
                canvas.drawPath(ray, rayPaint)
            }
        }
        val now = System.nanoTime()
        val dt = ((now - lastT) / 1e9f).coerceIn(0f, 0.05f)
        lastT = now
        elapsed += dt
        val alpha = ((seconds - elapsed) / 1.2f).coerceIn(0f, 1f)
        if (alpha > 0f) {
            for (c in confetti) {
                // gravity + air drag: bursts slow into a gentle rain
                c.vy += h * (if (burst) 0.9f else 0.25f) * dt
                val drag = 1f - (if (burst) 1.8f else 0.4f) * dt
                c.vx *= drag; c.vy = if (c.vy > 0f) c.vy * (1f - 0.9f * dt) + h * 0.28f * dt * 0.9f else c.vy * drag
                c.x += (c.vx + sin(elapsed * 2.5f + c.rot) * w * 0.05f) * dt
                c.y += c.vy * dt
                c.rot += c.vr * dt
                c.flutter += c.flutterV * dt
                if (c.y > h + c.h || c.y < -h) continue
                val fl = abs(cos(c.flutter)) // tumbling: the piece thins as it turns edge-on
                val a = (255 * alpha).toInt()
                canvas.save()
                canvas.rotate(c.rot, c.x, c.y)
                val hw = c.w * (0.15f + 0.85f * fl) / 2f; val hh = c.h / 2f
                shadePaint.color = Theme.alpha(Theme.darken(c.color, 0.35f), a)
                confPaint.color = Theme.alpha(if (fl > 0.5f) c.color else Theme.darken(c.color, 0.2f), a)
                when (c.kind) {
                    1 -> { canvas.drawCircle(c.x, c.y + hh * 0.15f, hw.coerceAtLeast(1f), shadePaint); canvas.drawCircle(c.x, c.y, hw.coerceAtLeast(1f), confPaint) }
                    else -> {
                        rect.set(c.x - hw, c.y - hh + hh * 0.2f, c.x + hw, c.y + hh + hh * 0.2f); canvas.drawRoundRect(rect, hw * 0.4f, hw * 0.4f, shadePaint)
                        rect.set(c.x - hw, c.y - hh, c.x + hw, c.y + hh); canvas.drawRoundRect(rect, hw * 0.4f, hw * 0.4f, confPaint)
                    }
                }
                canvas.restore()
            }
        } else if (!rays) {
            (parent as? android.view.ViewGroup)?.post { (parent as? android.view.ViewGroup)?.removeView(this) }
        }
    }
}
