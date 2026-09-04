package cube.run.ui

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import cube.run.core.SoundFx

/**
 * The motion vocabulary. Every screen uses the same handful of moves so the
 * app feels like one thing: things *pop* in with a little overshoot, *rise*
 * into place, *pulse* when their value changes, *breathe* while waiting, and
 * *shake* when they refuse.
 */
object Anim {
    val spring = OvershootInterpolator(1.8f)
    val springSoft = OvershootInterpolator(1.1f)
    val ease = DecelerateInterpolator(1.6f)

    /**
     * Settle a view after an entrance. Over the GL surface, the renderer was
     * seen to leave the top of a freshly translated view unpainted until the
     * next layout pass (the page's title bar simply never appeared), so every
     * entrance ends by pinning its final values and asking for a layout.
     */
    private fun settle(v: View) {
        v.alpha = 1f; v.scaleX = 1f; v.scaleY = 1f; v.translationX = 0f; v.translationY = 0f
        // a view's property animator REMEMBERS its start delay: clear it, or the next animate() on this
        // view (a fade-out, a drop) waits that long first and looks like it plays late or backwards
        v.animate().setStartDelay(0).setUpdateListener(null)
        v.requestLayout()
        (v.parent as? View)?.requestLayout()
    }

    /** Scale + fade in with overshoot. */
    fun popIn(v: View, delay: Long = 0, from: Float = 0.5f, duration: Long = 320) {
        v.alpha = 0f; v.scaleX = from; v.scaleY = from
        v.animate().cancel()
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(delay).setDuration(duration).setInterpolator(spring).withEndAction { settle(v) }.start()
    }

    /** Slide up into place + fade in (no overshoot: translations never leave their parent's clip). */
    fun riseIn(v: View, delay: Long = 0, distancePx: Float, duration: Long = 300) {
        v.alpha = 0f; v.translationY = distancePx
        v.animate().cancel()
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(duration).setInterpolator(ease).withEndAction { settle(v) }.start()
    }

    /** Slide in from the side + fade in. */
    fun slideIn(v: View, delay: Long = 0, fromX: Float, duration: Long = 360) {
        v.alpha = 0f; v.translationX = fromX
        v.animate().cancel()
        v.animate().alpha(1f).translationX(0f).setStartDelay(delay).setDuration(duration).setInterpolator(ease).withEndAction { settle(v) }.start()
    }

    /** Every child of [group] rises in, one after the other. */
    fun stagger(group: ViewGroup, distancePx: Float, first: Long = 40, step: Long = 35) {
        for (i in 0 until group.childCount) riseIn(group.getChildAt(i), first + i * step, distancePx)
    }

    /** A quick swell on a value change. */
    fun pulse(v: View, amount: Float = 1.25f, duration: Long = 200) {
        v.animate().cancel()
        v.scaleX = amount; v.scaleY = amount
        v.animate().scaleX(1f).scaleY(1f).setDuration(duration).setInterpolator(ease).start()
    }

    /** A sideways "no". */
    fun shake(v: View, px: Float) {
        v.animate().cancel(); v.translationX = 0f
        v.animate().translationX(px).setDuration(45).withEndAction {
            v.animate().translationX(-px * 0.7f).setDuration(45).withEndAction {
                v.animate().translationX(0f).setDuration(90).setInterpolator(spring).start()
            }.start()
        }.start()
    }

    /** A soft repeating alpha breath; cancel the returned animator when done. */
    fun breathe(v: View, min: Float = 0.5f, max: Float = 1f, period: Long = 800): ValueAnimator =
        ValueAnimator.ofFloat(min, max).apply {
            duration = period; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { a -> v.alpha = a.animatedValue as Float }
            start()
        }

    /** A slow up/down bob (idle showpieces). Drawn from a cached layer, so outlined text glides instead of shimmering. */
    fun bob(v: View, px: Float, period: Long = 1400): ValueAnimator =
        ValueAnimator.ofFloat(-px, px).apply {
            duration = period; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            addUpdateListener { a -> v.translationY = a.animatedValue as Float }
            start()
        }

    /** A slow heartbeat scale (idle attention). */
    fun heartbeat(v: View, amount: Float = 1.05f, period: Long = 900): ValueAnimator =
        ValueAnimator.ofFloat(1f, amount, 1f).apply {
            duration = period; repeatCount = ValueAnimator.INFINITE
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            addUpdateListener { a -> val s = a.animatedValue as Float; v.scaleX = s; v.scaleY = s }
            start()
        }

    /** Roll [t] from [from] to [to] (a balance draining, a stock growing), no ticks. */
    fun countTo(t: TextView, from: Int, to: Int, ms: Long, format: (Int) -> String = { it.toString() }): ValueAnimator =
        ValueAnimator.ofInt(from, to).apply {
            duration = ms
            interpolator = ease
            addUpdateListener { a -> t.text = format(a.animatedValue as Int) }
            start()
        }

    /**
     * Count [t] from 0 to [to] with rising ticks. [format] renders a value.
     * Returns the animator (null when there is nothing to count).
     */
    fun countUp(t: TextView, to: Int, ms: Long, tickEvery: Int = 4, format: (Int) -> String = { it.toString() }): ValueAnimator? {
        if (to <= 0) { t.text = format(0); return null }
        var lastTick = -1
        return ValueAnimator.ofInt(0, to).apply {
            duration = ms
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener { a ->
                val v = a.animatedValue as Int
                t.text = format(v)
                if (v / tickEvery != lastTick) { lastTick = v / tickEvery; SoundFx.play("tick", rate = 1.2f + 0.6f * v / to, vol = 0.35f) }
            }
            start()
        }
    }
}
