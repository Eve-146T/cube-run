package cube.run.game

import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import kotlin.math.max
import kotlin.math.sin

/**
 * Timed power-ups picked up on the track: the big magnet, 2× score and the
 * jetpack. Each is a countdown (its length is a `Progress` upgrade); the game
 * reads `active` to change the rules and this class draws every timer (the
 * bubble's included) as stacked bars low on the screen.
 */
class PowerUps {

    class Timer(val r: Float, val g: Float, val b: Float) {
        var left = 0f
            private set
        var duration = 1f
            private set
        val active: Boolean get() = left > 0f

        fun start(seconds: Float) { left = seconds; duration = seconds }
        fun stop() { left = 0f }

        /** Count down; returns true on the frame it runs out. */
        fun tick(dt: Float): Boolean {
            if (left <= 0f) return false
            left = max(0f, left - dt)
            return left == 0f
        }
    }

    val magnet = Timer(1f, 0.38f, 0.3f)
    val mult = Timer(1f, 0.35f, 0.75f)
    val jet = Timer(0.62f, 0.5f, 1f)

    fun reset() { magnet.stop(); mult.stop(); jet.stop() }

    /** Every running timer as a thin draining bar, stacked bottom-centre; the last seconds flicker. */
    fun drawBars(shapes: ShapeRenderer, w: Float, h: Float, time: Float, shield: Timer?) {
        var row = 0
        fun bar(t: Timer, r: Float, g: Float, b: Float) {
            if (!t.active) return
            val frac = (t.left / t.duration).coerceIn(0f, 1f)
            val bw = w * 0.44f; val bh = h * 0.007f
            val x0 = (w - bw) / 2f; val y0 = h * 0.045f + row * h * 0.016f
            shapes.setColor(1f, 1f, 1f, 0.18f)
            shapes.rect(x0, y0, bw, bh)
            val shimmer = 0.85f + 0.15f * sin(time * 7f + row)
            if (t.left < 3f && sin(time * 28f) < 0f) shapes.setColor(1f, 0.5f, 0.4f, 0.9f)
            else shapes.setColor(r * shimmer, g * shimmer, b * shimmer, 0.95f)
            shapes.rect(x0, y0, bw * frac, bh)
            row++
        }
        shield?.let { bar(it, 0.45f, 0.92f, 1f) }
        bar(magnet, magnet.r, magnet.g, magnet.b)
        bar(mult, mult.r, mult.g, mult.b)
        bar(jet, jet.r, jet.g, jet.b)
    }
}
