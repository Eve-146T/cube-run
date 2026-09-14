package cube.run.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import cube.run.R
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** The launch portal opens directly onto the first live game frame. */
class OpeningView(context: Context) : View(context) {
    private val ink = 0xFF14102E.toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mask = Path()
    private val mark = context.getDrawable(R.drawable.opening_mark)!!
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var started = false

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun reveal() {
        if (started || !isAttachedToWindow) return
        started = true
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 850L
            interpolator = DecelerateInterpolator(1.25f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
                if (progress >= 1f) (parent as? ViewGroup)?.removeView(this@OpeningView)
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val density = resources.displayMetrics.density
        val radius = hypot(cx, cy) * 1.5f * progress * progress
        // Rounded square portal: grow it past every corner before removing the overlay.
        mask.rewind(); mask.fillType = Path.FillType.EVEN_ODD
        mask.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        if (radius > 0f) mask.addRoundRect(cx-radius, cy-radius, cx+radius, cy+radius,
            radius*.24f, radius*.24f, Path.Direction.CW)
        paint.color = ink; canvas.drawPath(mask, paint)
        if (progress > 0f && progress < .94f) {
            paint.color = 0xFF5DE8D2.toInt(); paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f*density*(1f-progress)
            canvas.drawRoundRect(cx-radius, cy-radius, cx+radius, cy+radius, radius*.24f, radius*.24f, paint)
            paint.style = Paint.Style.FILL
            for (i in 0..7) {
                val a = i*Math.PI.toFloat()/4f + .2f
                val reach = (55f+progress*210f)*density
                val x = cx+cos(a)*reach; val y = cy+sin(a)*reach
                val size = (4f+i%3)*density*(1f-progress)
                paint.color = if (i%2==0) 0xFF5DE8D2.toInt() else 0xFF6378FF.toInt()
                canvas.save(); canvas.rotate(progress*150f+i*30f, x, y)
                canvas.drawRect(x-size, y-size, x+size, y+size, paint); canvas.restore()
            }
        }
        val visible = (1f-progress*2.8f).coerceIn(0f, 1f)
        if (visible > 0f) {
            val half = 144f*density*(1f-progress*.5f)
            mark.setBounds((cx-half).toInt(), (cy-half).toInt(), (cx+half).toInt(), (cy+half).toInt())
            mark.alpha = (255*visible).toInt()
            canvas.save(); canvas.rotate(progress*35f, cx, cy); mark.draw(canvas); canvas.restore()
        }
    }

    // Touch skips the reveal, without accidentally starting a run underneath it.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (started) { animator?.cancel(); (parent as? ViewGroup)?.removeView(this) }
        return true
    }

    override fun onDetachedFromWindow() { animator?.cancel(); animator = null; super.onDetachedFromWindow() }
}
