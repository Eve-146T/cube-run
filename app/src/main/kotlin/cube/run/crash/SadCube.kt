package cube.run.crash

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import cube.run.core.Haptics
import cube.run.ui.Theme
import kotlin.math.PI
import kotlin.math.sin

/**
 * The game's classic cube, sad: it takes the colour opposite [backdrop] the way the classic
 * skin takes the colour opposite the world. It bobs, blinks, trembles, cries the odd tear
 * and squashes when poked. [cheer] lifts the brows and turns the frown into a smile.
 */
class SadCube(context: Context, backdrop: Int) : View(context) {
    private val color = FloatArray(3).let { Color.colorToHSV(backdrop, it); Theme.hsv(it[0] + 180f, 0.55f, 1f) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rounder = Paint()
    private val box = RectF()
    private val path = Path()
    private val silhouette = Path()
    private val born = SystemClock.uptimeMillis()
    private var pokedAt = -10_000L
    private var cheeredAt = -1L

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun cheer() { if (cheeredAt < 0) cheeredAt = SystemClock.uptimeMillis() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            pokedAt = SystemClock.uptimeMillis()
            Haptics.tick()
        }
        return super.onTouchEvent(event)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rounder.pathEffect = CornerPathEffect(size() * 0.13f)
    }

    private fun size() = minOf(width, height) * 0.6f

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val t = (now - born) / 1000f
        val s = size()
        // Drop in, then a slow sad bob with a little tilt.
        val drop = ease((t / 0.55f).coerceAtMost(1f))
        val poke = ((now - pokedAt) / 380f).coerceIn(0f, 1f)
        val squash = if (poke < 1f) sin(poke * PI.toFloat()) * 0.22f * (1f - poke) else 0f
        val landing = if (t in 0.45f..0.9f) sin((t - 0.45f) / 0.45f * PI.toFloat()) * 0.12f else 0f
        val bob = sin(t * 1.6f) * s * 0.035f
        val cy = height * 0.5f + bob - (1f - drop) * height
        canvas.save()
        canvas.translate(width / 2f, cy + s / 2f)
        canvas.rotate(sin(t * 0.9f) * 4f)
        canvas.scale(1f + squash + landing, 1f - squash - landing)
        canvas.translate(0f, -s / 2f)
        drawCube(canvas, s)
        drawFace(canvas, s / 10f, now, t)
        canvas.restore()
        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    /** Front face square on, the bright top and the shaded side peeking out, rounded and outlined. */
    private fun drawCube(canvas: Canvas, s: Float) {
        val h = s / 2f
        val dx = s * 0.24f
        val dy = -s * 0.2f
        canvas.translate(-dx / 2f, -dy / 2f)
        path.reset()
        path.moveTo(-h, h); path.lineTo(-h, -h); path.lineTo(-h + dx, -h + dy); path.lineTo(h + dx, -h + dy)
        path.lineTo(h + dx, h + dy); path.lineTo(h, h); path.close()
        silhouette.reset(); rounder.getFillPath(path, silhouette)
        canvas.save(); canvas.clipPath(silhouette)
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawRect(-h, -h, h, h, paint)
        paint.color = Theme.lighten(color, 0.4f)
        quad(canvas, -h, -h, -h + dx, -h + dy, h + dx, -h + dy, h, -h)
        paint.color = Theme.darken(color, 0.18f)
        quad(canvas, h, -h, h + dx, -h + dy, h + dx, h + dy, h, h)
        // Bevels where the faces meet, and a gloss on the top.
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = s * 0.035f
        paint.color = Theme.alpha(Theme.WHITE, 140)
        canvas.drawLine(-h + s * 0.06f, -h, h, -h, paint); canvas.drawLine(h, -h, h + dx, -h + dy, paint)
        paint.color = Theme.alpha(Theme.WHITE, 70)
        canvas.drawLine(h, -h, h, h - s * 0.06f, paint)
        paint.color = Theme.alpha(Theme.WHITE, 210); paint.strokeWidth = s * 0.05f
        canvas.drawLine(-h + dx * 0.7f, -h + dy * 0.5f, -h + dx * 0.7f + s * 0.2f, -h + dy * 0.5f, paint)
        canvas.restore()
        paint.color = Theme.darken(color, 0.55f); paint.strokeWidth = s * 0.03f
        canvas.drawPath(silhouette, paint)
        paint.style = Paint.Style.FILL
    }

    /** Drawn on the front face, on a 10 × 10 grid of [u]. */
    private fun drawFace(canvas: Canvas, u: Float, now: Long, t: Float) {
        val happy = if (cheeredAt < 0) 0f else ease(((now - cheeredAt) / 300f).coerceAtMost(1f))
        val blink = (t % 3.4f) > 3.25f || now - pokedAt < 160
        // Blush first, so the eyes and tear sit on top of it.
        paint.color = Theme.lighten(Theme.PINK, 0.4f)
        for (side in intArrayOf(-1, 1)) round(canvas, side * 3.1f * u - 0.75f * u, 0.7f * u, side * 3.1f * u + 0.75f * u, 1.4f * u, 0.35f * u)
        for (side in intArrayOf(-1, 1)) {
            val ex = side * 2.1f * u
            paint.color = Theme.INK
            if (blink) {
                round(canvas, ex - 0.85f * u, -0.95f * u, ex + 0.85f * u, -0.55f * u, 0.2f * u)
            } else {
                round(canvas, ex - 0.85f * u, -1.9f * u, ex + 0.85f * u, 0.3f * u, 0.65f * u)
                paint.color = Theme.WHITE
                round(canvas, ex - 0.5f * u, -1.55f * u, ex - 0.05f * u, -1.1f * u, 0.2f * u)
            }
            // Brows: inner ends raised when sad, level and higher when cheered up.
            paint.color = Theme.INK
            canvas.save()
            canvas.translate(ex, (-2.75f - 0.35f * happy) * u)
            canvas.rotate(side * 16f * (1f - happy))
            round(canvas, -0.9f * u, -0.22f * u, 0.9f * u, 0.22f * u, 0.22f * u)
            canvas.restore()
        }
        // The mouth bends from a trembling frown to a smile.
        val tremble = sin(t * 26f) * 0.07f * u * (1f - happy)
        val ends = (2.9f - 0.4f * happy) * u
        val middle = (1.9f + 2.2f * happy) * u + tremble
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 0.55f * u; paint.color = Theme.INK
        path.reset(); path.moveTo(-1.4f * u, ends); path.quadTo(0f, 2f * middle - ends, 1.4f * u, ends)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        if (happy < 1f) drawTear(canvas, u, t, 1f - happy)
    }

    /** A drop wells up under the left eye, runs down the cheek and fades. */
    private fun drawTear(canvas: Canvas, u: Float, t: Float, strength: Float) {
        val cycle = (t - 1.2f) % 2.8f
        if (t < 1.2f || cycle > 1.6f) return
        val grow = (cycle / 0.4f).coerceAtMost(1f)
        val fall = ((cycle - 0.4f) / 1.2f).coerceAtLeast(0f)
        val r = 0.42f * u * grow
        val x = -2.8f * u
        val y = 0.5f * u + fall * fall * 3.6f * u
        val a = ((1f - fall * fall) * strength * 255).toInt()
        path.reset()
        path.moveTo(x, y - r * 2.3f)
        path.cubicTo(x + r * 0.4f, y - r * 1.3f, x + r, y - r * 0.7f, x + r, y)
        path.arcTo(x - r, y - r, x + r, y + r, 0f, 180f, false)
        path.cubicTo(x - r, y - r * 0.7f, x - r * 0.4f, y - r * 1.3f, x, y - r * 2.3f)
        path.close()
        paint.color = Theme.alpha(Theme.lighten(Theme.SKY, 0.35f), a)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.14f * u
        paint.color = Theme.alpha(Theme.darken(Theme.SKY, 0.25f), a)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.color = Theme.alpha(Theme.WHITE, a)
        canvas.drawCircle(x - r * 0.35f, y - r * 0.2f, r * 0.28f, paint)
    }

    private fun quad(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float) {
        path.reset(); path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.lineTo(x4, y4); path.close()
        canvas.drawPath(path, paint)
    }

    private fun round(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float) {
        box.set(l, t, r, b); canvas.drawRoundRect(box, radius, radius, paint)
    }

    private fun ease(k: Float) = 1f - (1f - k) * (1f - k) * (1f - k)
}
