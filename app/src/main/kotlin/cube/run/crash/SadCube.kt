package cube.run.crash

import android.content.Context
import android.graphics.Canvas
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
import kotlin.math.abs
import kotlin.math.sin

/**
 * The game's cube wearing a blue-screen ":(" — it bobs, blinks, cries the odd square tear
 * and squashes when poked. [cheer] turns the frown into a smile.
 */
class SadCube(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private val path = Path()
    private val face = Path()
    private val silhouette = Path()
    private val rounder = Paint()
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
        rounder.pathEffect = CornerPathEffect(minOf(w, h) * 0.62f * 0.14f)
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val t = (now - born) / 1000f
        val s = minOf(width, height) * 0.62f
        val cx = width / 2f
        // Drop in, then a slow sad bob with a little tilt.
        val drop = ease((t / 0.55f).coerceAtMost(1f))
        val poke = ((now - pokedAt) / 380f).coerceIn(0f, 1f)
        val squash = if (poke < 1f) sin(poke * PI.toFloat()) * 0.22f * (1f - poke) else 0f
        val landing = if (t in 0.45f..0.9f) sin((t - 0.45f) / 0.45f * PI.toFloat()) * 0.12f else 0f
        val sx = 1f + squash + landing
        val sy = 1f - squash - landing
        val bob = sin(t * 1.6f) * s * 0.035f
        val cy = height * 0.48f + bob - (1f - drop) * height
        canvas.save()
        canvas.translate(cx, cy + s / 2f)
        canvas.rotate(sin(t * 0.9f) * 4f)
        canvas.scale(sx, sy)
        canvas.translate(0f, -s / 2f)
        drawCube(canvas, s, now, t)
        canvas.restore()
        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    private fun drawCube(canvas: Canvas, s: Float, now: Long, t: Float) {
        // The game's chunky cube seen from the chase camera: the front face square on, the
        // bright top and the shaded side peeking out, rounded corners on a darker lip.
        val h = s / 2f
        val o = s * 0.2f
        canvas.translate(-o / 2f, o / 2f)
        path.reset()
        path.moveTo(-h, -h); path.lineTo(-h + o, -h - o); path.lineTo(h + o, -h - o)
        path.lineTo(h + o, h - o); path.lineTo(h, h); path.lineTo(-h, h); path.close()
        rounder.style = Paint.Style.FILL
        silhouette.reset(); rounder.getFillPath(path, silhouette)
        paint.color = Theme.darken(COLOR, 0.45f)
        canvas.save(); canvas.translate(0f, s * 0.09f); canvas.drawPath(silhouette, paint); canvas.restore()
        canvas.save(); canvas.clipPath(silhouette)
        paint.color = COLOR
        box.set(-h, -h, h, h); canvas.drawRect(box, paint)
        paint.color = Theme.lighten(COLOR, 0.38f)
        quad(canvas, -h, -h, -h + o, -h - o, h + o, -h - o, h, -h)
        paint.color = Theme.darken(COLOR, 0.2f)
        quad(canvas, h, -h, h + o, -h - o, h + o, h - o, h, h)
        // Bevel where the top meets the faces, and a gloss on the top's far corner.
        paint.style = Paint.Style.STROKE; paint.strokeWidth = s * 0.04f; paint.strokeCap = Paint.Cap.ROUND
        paint.color = Theme.alpha(Theme.WHITE, 120)
        canvas.drawLine(-h, -h, h, -h, paint); canvas.drawLine(h, -h, h + o, -h - o, paint)
        paint.color = Theme.alpha(Theme.WHITE, 200); paint.strokeWidth = s * 0.05f
        canvas.drawLine(-h + o * 0.75f, -h - o * 0.55f, -h + o * 0.75f + s * 0.22f, -h - o * 0.55f, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()

        val u = s / 10f // the face is drawn on a 10 × 10 grid of chunky pixels
        paint.color = Theme.INK
        val blink = (t % 3.4f) > 3.25f || now - pokedAt < 140
        val eyeH = if (blink) u * 0.35f else u * 1.6f
        for (side in intArrayOf(-1, 1)) {
            val ex = side * u * 2.2f
            px(canvas, ex - u * 0.8f, -u * 1.4f - eyeH / 2f, ex + u * 0.8f, -u * 1.4f + eyeH / 2f, u * 0.3f)
        }
        // Frown ↔ smile: the middle of the mouth sits high (sad) or low (happy).
        val happy = if (cheeredAt < 0) 0f else ease(((now - cheeredAt) / 300f).coerceAtMost(1f))
        val bend = u * (1f - 2f * happy)
        val mouthY = u * 2.3f
        for (i in -2..2) {
            val lift = when (abs(i)) { 0 -> bend; 1 -> bend * 0.75f; else -> 0f }
            val x = i * u * 0.95f
            px(canvas, x - u * 0.5f, mouthY - lift - u * 0.4f, x + u * 0.5f, mouthY - lift + u * 0.4f, u * 0.2f)
        }
        if (happy < 1f) drawTear(canvas, u, t, 1f - happy)
    }

    private fun drawTear(canvas: Canvas, u: Float, t: Float, strength: Float) {
        val cycle = (t - 1.2f) % 2.6f
        if (t < 1.2f || cycle > 1.4f) return
        val k = cycle / 1.4f
        paint.color = Theme.WHITE // a sky-blue tear vanishes on the mint face
        paint.alpha = ((1f - k * k) * 255 * strength).toInt()
        val x = -u * 2.2f
        val y = -u * 0.4f + k * k * u * 4.2f
        px(canvas, x - u * 0.38f, y, x + u * 0.38f, y + u * 0.9f, u * 0.38f)
        paint.alpha = 255
    }

    private fun quad(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float) {
        face.reset(); face.moveTo(x1, y1); face.lineTo(x2, y2); face.lineTo(x3, y3); face.lineTo(x4, y4); face.close()
        canvas.drawPath(face, paint)
    }

    private fun px(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, round: Float) {
        box.set(l, t, r, b); canvas.drawRoundRect(box, round, round, paint)
    }

    private fun ease(k: Float) = 1f - (1f - k) * (1f - k) * (1f - k)

    private companion object {
        const val COLOR = Theme.MINT
    }
}
