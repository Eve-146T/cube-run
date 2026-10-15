package cube.run.ui

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
 * [fallOut] keeps pieces opaque until they fall below the screen, instead of
 * fading them after [seconds].
 *
 * Every piece and its darker lip is written into one triangle list each
 * frame and drawn with a single [Canvas.drawVertices]: per-piece canvas
 * transforms and anti-aliased round rects cost hundreds of draw operations a
 * frame on top of the GL stage and stuttered on fast, high-refresh phones.
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
    private val fallOut: Boolean = false,
) : View(ctx) {

    private class Confetto(
        var x: Float, var y: Float, var vx: Float, var vy: Float, var rot: Float, var vr: Float,
        val w: Float, val h: Float, val color: Int, val kind: Int, var flutter: Float, val flutterV: Float,
    ) {
        // These shades never change while a piece tumbles; only alpha changes.
        val shadeColor = Theme.darken(color, 0.35f)
        val backColor = Theme.darken(color, 0.2f)
    }

    private val rnd = Random(System.nanoTime())
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val confPaint = Paint()
    private val ray = Path()
    private val rayCount = 16
    private var angle = 0f
    private val confetti = ArrayList<Confetto>()
    private var lastT = 0L
    private var elapsed = 0f
    private val palette = intArrayOf(Theme.GOLD, Theme.MINT, Theme.SKY, Theme.GRAPE, Theme.PINK, Theme.WHITE, Theme.LIME, Theme.ORANGE, Theme.CYAN)
    private var spin: ValueAnimator? = null

    // One triangle list for every piece: a lip and a face, each a quad (6 vertices) or a hexagon (12).
    private val verts = FloatArray(count * 24 * 2)
    private val colors = IntArray(count * 24)
    private var n = 0

    init {
        isClickable = false
        isFocusable = false
        // Hardware canvases only draw vertices from API 29.
        if (android.os.Build.VERSION.SDK_INT < 29) setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun vertex(x: Float, y: Float, color: Int) {
        verts[n * 2] = x; verts[n * 2 + 1] = y; colors[n] = color; n++
    }

    /** A rectangle of half size [hw]×[hh] turned by ([cs],[sn]) about ([x],[y]). */
    private fun quad(x: Float, y: Float, hw: Float, hh: Float, cs: Float, sn: Float, color: Int) {
        val ax = cs * hw; val ay = sn * hw; val bx = -sn * hh; val by = cs * hh
        val x0 = x - ax - bx; val y0 = y - ay - by; val x1 = x + ax - bx; val y1 = y + ay - by
        val x2 = x + ax + bx; val y2 = y + ay + by; val x3 = x - ax + bx; val y3 = y - ay + by
        vertex(x0, y0, color); vertex(x1, y1, color); vertex(x2, y2, color)
        vertex(x0, y0, color); vertex(x2, y2, color); vertex(x3, y3, color)
    }

    /** A dot: a hexagon fan of radius [r]. */
    private fun dot(x: Float, y: Float, r: Float, color: Int) {
        for (k in 0 until 4) {
            vertex(x + r * HEX_X[0], y + r * HEX_Y[0], color)
            vertex(x + r * HEX_X[k + 1], y + r * HEX_Y[k + 1], color)
            vertex(x + r * HEX_X[k + 2], y + r * HEX_Y[k + 2], color)
        }
    }

    private companion object {
        val HEX_X = FloatArray(6) { cos(it * 1.0471976f) }
        val HEX_Y = FloatArray(6) { sin(it * 1.0471976f) }
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
        if (rays) rayPaint.shader = RadialGradient(
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
        val alpha = if (fallOut) 1f else ((seconds - elapsed) / 1.2f).coerceIn(0f, 1f)
        if (alpha > 0f) {
            val a = (255 * alpha).toInt()
            n = 0
            for (i in confetti.lastIndex downTo 0) {
                val c = confetti[i]
                // gravity + air drag: bursts slow into a gentle rain
                c.vy += h * (if (burst) 0.9f else 0.25f) * dt
                val drag = 1f - (if (burst) 1.8f else 0.4f) * dt
                c.vx *= drag; c.vy = if (c.vy > 0f) c.vy * (1f - 0.9f * dt) + h * 0.28f * dt * 0.9f else c.vy * drag
                c.x += (c.vx + sin(elapsed * 2.5f + c.rot) * w * 0.05f) * dt
                c.y += c.vy * dt
                c.rot += c.vr * dt
                c.flutter += c.flutterV * dt
                if (fallOut) {
                    // Retire only below the viewport, with room for rotation and
                    // the shadow. Upward pieces remain live and fall back in.
                    val radius = maxOf(c.w, c.h)
                    if (c.y - radius > h) {
                        confetti[i] = confetti.last()
                        confetti.removeAt(confetti.lastIndex)
                        continue
                    }
                    // Keep the burst inside the sides so pieces leave by falling.
                    if (c.x < radius) { c.x = radius; c.vx = abs(c.vx) * .35f }
                    else if (c.x > w - radius) { c.x = w - radius; c.vx = -abs(c.vx) * .35f }
                }
                if (c.y > h + c.h || c.y < -h) continue
                val fl = abs(cos(c.flutter)) // tumbling: the piece thins as it turns edge-on
                val hw = c.w * (0.15f + 0.85f * fl) / 2f; val hh = c.h / 2f
                val shade = Theme.alpha(c.shadeColor, a)
                val face = Theme.alpha(if (fl > 0.5f) c.color else c.backColor, a)
                if (c.kind == 1) {
                    val r = hw.coerceAtLeast(1f)
                    dot(c.x, c.y + hh * 0.15f, r, shade); dot(c.x, c.y, r, face)
                } else {
                    val rad = Math.toRadians(c.rot.toDouble())
                    val cs = cos(rad).toFloat(); val sn = sin(rad).toFloat()
                    // the lip sits below the face in the piece's own frame, as when it was drawn rotated
                    quad(c.x - sn * hh * 0.2f, c.y + cs * hh * 0.2f, hw, hh, cs, sn, shade)
                    quad(c.x, c.y, hw, hh, cs, sn, face)
                }
            }
            if (n > 0) canvas.drawVertices(Canvas.VertexMode.TRIANGLES, n * 2, verts, 0, null, 0, colors, 0, null, 0, 0, confPaint)
        }
        if (!rays && (alpha <= 0f || fallOut && confetti.isEmpty())) {
            spin?.cancel(); spin = null
            (parent as? android.view.ViewGroup)?.post { (parent as? android.view.ViewGroup)?.removeView(this) }
        }
    }
}
