package cube.run.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.View
import cube.run.core.Haptics
import cube.run.core.SoundFx
import java.util.WeakHashMap
import kotlin.math.PI
import kotlin.math.sin

/**
 * Idle taps on the achievement page. A tap on a card presses the whole card like a button: it
 * dips and springs back with a tap and a tick. Tapping the same card again quickly adds a wiggle
 * that grows the faster you keep at it, so the wiggle is something you work up, not something a
 * single tap sets off.
 */
internal object Pokes {
    private val running = WeakHashMap<View, ValueAnimator>()
    private var last: View? = null
    private var lastAt = 0L
    private var streak = 0

    /** Press [card]; repeated quick presses on it wiggle it more and more. */
    fun press(card: View) {
        val now = SystemClock.uptimeMillis()
        streak = if (card === last && now - lastAt < 380L) (streak + 1).coerceAtMost(6) else 0
        last = card; lastAt = now
        SoundFx.play("tap", rate = 1f + .03f * streak, vol = .5f)
        Haptics.tick()
        val wiggle = 1.1f * streak
        running.remove(card)?.cancel()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (streak == 0) 220L else 360L
            addUpdateListener {
                val t = it.animatedValue as Float
                val dip = if (t < .25f) t / .25f else 1f - Anim.ease.getInterpolation((t - .25f) / .75f)
                card.scaleX = 1f - .035f * dip; card.scaleY = card.scaleX
                card.rotation = wiggle * sin(t * PI.toFloat() * 4f) * (1f - t)
                Anim.repaint(card)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    card.scaleX = 1f; card.scaleY = 1f; card.rotation = 0f
                    if (running[card] === animation) running.remove(card)
                }
            })
            Anim.cancelOnDetach(card, this)
            running[card] = this
            start()
        }
    }

    /** Stop any press on [v] and put it back, before something else animates it. */
    fun stop(v: View) { running.remove(v)?.cancel() }
}
