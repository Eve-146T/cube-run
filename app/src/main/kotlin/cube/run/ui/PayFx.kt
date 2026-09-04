package cube.run.ui

import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import cube.run.core.Haptics
import cube.run.core.SoundFx
import kotlin.math.sin

/**
 * What paying looks like: a handful of coins fly from your balance pill
 * into the button you pressed, one after the other, each landing with a
 * rising chime and a bump; then the card you bought from flashes. Every
 * purchase in the app uses this, so spending always happens where you
 * tapped.
 */
object PayFx {

    /** Centre of [v] in [host]'s coordinates. */
    private fun centre(host: View, v: View): FloatArray {
        val a = IntArray(2); val b = IntArray(2)
        host.getLocationInWindow(a); v.getLocationInWindow(b)
        return floatArrayOf(b[0] - a[0] + v.width / 2f, b[1] - a[1] + v.height / 2f)
    }

    /**
     * Fly [n] coins from [from] to [to] inside [host]; [onEach] on every
     * landing, [onDone] after the last. Returns roughly how long it takes (ms).
     */
    fun fly(host: FrameLayout, kit: UiKit, from: View, to: View, n: Int = 6, onEach: () -> Unit = {}, onDone: () -> Unit): Long {
        val s = centre(host, from); val e = centre(host, to)
        val size = kit.dp(20f)
        val step = 55L; val dur = 380L
        for (i in 0 until n) {
            val coin = ImageView(host.context).apply { setImageDrawable(CoinIcon()); alpha = 0f }
            host.addView(coin, FrameLayout.LayoutParams(size, size))
            val side = if (i % 2 == 0) 1f else -1f
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = dur; startDelay = i * step
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addUpdateListener { a ->
                    val t = a.animatedValue as Float
                    val arc = sin(t * 3.14159f)
                    coin.alpha = 1f
                    coin.x = s[0] + (e[0] - s[0]) * t + side * arc * kit.dpf(40f) - size / 2f
                    coin.y = s[1] + (e[1] - s[1]) * t - arc * kit.dpf(60f) - size / 2f
                    val sc = 0.7f + 0.5f * arc
                    coin.scaleX = sc; coin.scaleY = sc
                    coin.rotation = t * 360f * side
                }
                doOnEnd {
                    host.removeView(coin)
                    SoundFx.play("coin", rate = 1.1f + i * 0.12f, vol = 0.6f); Haptics.tick()
                    Anim.pulse(to, 1.12f, 180)
                    onEach()
                    if (i == n - 1) onDone()
                }
                start()
            }
        }
        return (n - 1) * step + dur
    }

    /** A white flash over [card] that fades out (the thing you bought lighting up). */
    fun flash(card: View, radiusPx: Float) {
        val fg = GradientDrawable().apply { cornerRadius = radiusPx; setColor(Theme.WHITE) }
        card.foreground = fg
        ValueAnimator.ofInt(170, 0).apply {
            duration = 520
            interpolator = Anim.ease
            addUpdateListener { a -> fg.alpha = a.animatedValue as Int }
            doOnEnd { card.foreground = null }
            start()
        }
        Anim.pulse(card, 1.03f, 380)
    }

    private fun ValueAnimator.doOnEnd(f: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) { f() }
        })
    }
}
