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
 * Idle taps on the achievement page: tiles, badges and the case can be poked just for the feel
 * of it. Each poke answers right where you tap with a little squash, hop or shake, a tick and a
 * soft blip that climbs in pitch while you keep tapping, so a run of taps feels like playing
 * with a toy rather than pressing a button.
 */
internal object Pokes {
    private val running = WeakHashMap<View, ValueAnimator>()
    private var streak = 0
    private var lastAt = 0L

    /** Squash and stretch, like pressing a jelly sweet: for tiles. */
    fun jelly(v: View) {
        feel(1f)
        run(v, 460, { t ->
            val k = sin(t * PI.toFloat() * 3f) * (1f - t)
            v.scaleX = 1f + .16f * k; v.scaleY = 1f - .16f * k
        }) { v.scaleX = 1f; v.scaleY = 1f }
    }

    /** A hop with a wobble, like flicking a pin: for badges you own. */
    fun wobble(v: View) {
        feel(1.15f)
        val hop = 8f * v.resources.displayMetrics.density
        run(v, 520, { t ->
            v.rotation = 12f * sin(t * PI.toFloat() * 4f) * (1f - t)
            v.translationY = -hop * sin(t * PI.toFloat()).coerceAtLeast(0f) * (1f - t * .5f)
        }) { v.rotation = 0f; v.translationY = 0f }
    }

    /** A quick head-shake, "not yet": for empty slots. */
    fun nudge(v: View) {
        feel(.8f)
        val reach = 5f * v.resources.displayMetrics.density
        run(v, 320, { t -> v.translationX = reach * sin(t * PI.toFloat() * 5f) * (1f - t) }) { v.translationX = 0f }
    }

    /** Stop any poke on [v] and put it back, before something else animates it. */
    fun stop(v: View) { running.remove(v)?.cancel() }

    /** A tick, and a blip a little higher for every tap in a quick run of them. */
    private fun feel(pitch: Float) {
        val now = SystemClock.uptimeMillis()
        streak = if (now - lastAt < 650L) (streak + 1).coerceAtMost(8) else 0
        lastAt = now
        SoundFx.play("blip", rate = pitch + .06f * streak, vol = .45f)
        Haptics.tick()
    }

    private fun run(v: View, ms: Long, frame: (Float) -> Unit, reset: () -> Unit) {
        stop(v)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms
            addUpdateListener { frame(it.animatedValue as Float); Anim.repaint(v) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { reset(); if (running[v] === animation) running.remove(v) }
            })
            Anim.cancelOnDetach(v, this)
            running[v] = this
            start()
        }
    }
}
