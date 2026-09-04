package cube.run.game

import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import kotlin.math.max
import kotlin.math.sin

/**
 * Timed power-ups picked up on the track: the big magnet, 2× score and the
 * jetpack. Each is a countdown (its length is a `Progress` upgrade); the game
 * reads `active` to change the rules and this class draws every timer (the
 * bubble's included) as chunky candy bars low on the screen.
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

    val magnet = Timer(1f, 0.30f, 0.42f)
    val mult = Timer(1f, 0.35f, 0.72f)
    val jet = Timer(0.28f, 0.72f, 1f)

    fun reset() { magnet.stop(); mult.stop(); jet.stop() }

    /** Every running timer as a rounded draining bar with a dark lip, stacked bottom-centre; the last seconds flicker. */
    fun drawBars(shapes: ShapeRenderer, w: Float, h: Float, time: Float, shield: Timer?) {
        var row = 0
        fun bar(t: Timer) {
            if (!t.active) return
            val frac = (t.left / t.duration).coerceIn(0f, 1f)
            val bw = w * 0.46f; val bh = h * 0.011f
            val x0 = (w - bw) / 2f; val y0 = h * 0.05f + row * h * 0.022f
            val r = bh / 2f
            shapes.setColor(0.1f, 0.06f, 0.2f, 0.55f)             // the track (with a lip)
            roundBar(shapes, x0, y0 - bh * 0.25f, bw, bh, r)
            shapes.setColor(1f, 1f, 1f, 0.22f)
            roundBar(shapes, x0, y0, bw, bh, r)
            val shimmer = 0.9f + 0.1f * sin(time * 7f + row)
            if (t.left < 3f && sin(time * 28f) < 0f) shapes.setColor(1f, 0.45f, 0.35f, 0.95f)
            else shapes.setColor(t.r * shimmer, t.g * shimmer, t.b * shimmer, 1f)
            if (frac > 0.01f) roundBar(shapes, x0, y0, bw * frac, bh, r)
            shapes.setColor(1f, 1f, 1f, 0.35f)                     // gloss
            if (frac > 0.01f) roundBar(shapes, x0 + r, y0 + bh * 0.55f, max(0f, bw * frac - 2 * r), bh * 0.25f, bh * 0.12f)
            row++
        }
        shield?.let { bar(it) }
        bar(magnet)
        bar(mult)
        bar(jet)
    }

    /** A rounded horizontal bar from circles + a rect (ShapeRenderer has no round rects). */
    private fun roundBar(shapes: ShapeRenderer, x: Float, y: Float, w: Float, h: Float, r: Float) {
        if (w <= 0f) return
        val rr = kotlin.math.min(r, w / 2f)
        shapes.circle(x + rr, y + h / 2f, rr, 12)
        shapes.circle(x + w - rr, y + h / 2f, rr, 12)
        shapes.rect(x + rr, y, max(0f, w - 2 * rr), h)
    }
}
