package cube.run.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The black hole every void offering is made of, drawn the same way on the shop card
 * and in the purchase scene so one can hand over to the other mid-frame: a violet
 * halo, the far side of a tilted accretion disk bent up and over the shadow, a thin
 * photon ring, and the near side of the disk crossing in front. Clumps in the disk
 * orbit at Kepler speeds; the approaching (left) side burns brighter.
 */
internal class VoidHole {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val oval = RectF()
    private val shaderMatrix = Matrix()
    private val halo = RadialGradient(0f, 0f, 1f, intArrayOf(0x667b4dff, 0x283a1f8f, 0x00000000), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
    private val fedHalo = RadialGradient(0f, 0f, 1f, intArrayOf(0x88ffb800.toInt(), 0x30ff6a3d, 0x00000000), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
    private val clumps = Array(28) { i ->
        // radius factor, phase (deg), sweep (deg), width factor — fixed per slot, never reshuffled
        floatArrayOf(1.32f + (i * 37 % 29) / 29f * 1.25f, i * 137.5f % 360f, 10f + (i * 13 % 7) * 3f, .6f + (i % 3) * .3f)
    }

    /**
     * [r] is the shadow's radius; the disk reaches about 2.7 r. [hunger] (0..1) is how hard it
     * is feeding: gold-hot, faster, brighter. [time] is seconds on [clock] unless a scene drives it.
     */
    fun draw(c: Canvas, x: Float, y: Float, r: Float, time: Float = clock(), hunger: Float = 0f, alpha: Float = 1f) {
        if (alpha <= 0f || r <= 0f) return
        val h = hunger.coerceIn(0f, 1f)
        c.save(); c.translate(x, y)

        // Halo: violet at rest, gold while it is eating coins.
        paint.style = Paint.Style.FILL
        shaderMatrix.setScale(r * (3.3f + h * .9f), r * (3.3f + h * .9f))
        halo.setLocalMatrix(shaderMatrix); paint.shader = halo
        paint.alpha = (255 * alpha * (1f - h * .5f)).toInt(); c.drawCircle(0f, 0f, r * (3.3f + h * .9f), paint)
        if (h > 0f) {
            fedHalo.setLocalMatrix(shaderMatrix); paint.shader = fedHalo
            paint.alpha = (255 * alpha * h).toInt(); c.drawCircle(0f, 0f, r * (3.3f + h * .9f), paint)
        }
        paint.shader = null

        c.rotate(-12f)
        val spin = time * (1f + h * 3.2f)
        val tilt = .33f

        // The far half of the disk; the shadow will cover its middle.
        disk(c, r, tilt, 180f, alpha, h) // same brightness as the near half: no seam where they meet
        clumps(c, r, tilt, spin, back = true, alpha = alpha, h = h)

        // Lensing: the far side of the disk bent into a crisp ring over the top, fainter beneath,
        // inside a soft glow.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.BUTT
        val lensHot = mix(0xfffff6ff.toInt(), 0xffffe6a8.toInt(), h)
        val lensCool = mix(0xff8a5cff.toInt(), Theme.GOLD, h)
        oval.set(-r * 1.28f, -r * 1.28f, r * 1.28f, r * 1.28f)
        paint.strokeWidth = r * .42f; paint.color = lensCool; paint.alpha = (alpha * 38).toInt()
        c.drawArc(oval, 0f, 360f, false, paint)
        oval.set(-r * 1.1f, -r * 1.1f, r * 1.1f, r * 1.1f)
        for (k in 0 until 12) { // brightest straight over the top
            val mid = 180f + (k + .5f) * 15f
            val over = sin(Math.toRadians((mid - 180f).toDouble())).toFloat()
            paint.strokeWidth = r * (.07f + .06f * over); paint.color = mix(lensCool, lensHot, .4f + .6f * over)
            paint.alpha = (alpha * (120 + 135 * over)).toInt()
            c.drawArc(oval, 180f + k * 15f - .5f, 16f, false, paint)
        }
        paint.strokeWidth = r * .05f; paint.color = lensCool; paint.alpha = (alpha * 110).toInt()
        c.drawArc(oval, 10f, 160f, false, paint)
        paint.strokeCap = Paint.Cap.ROUND

        // The shadow, then the photon ring hugging it.
        paint.style = Paint.Style.FILL; paint.color = Color.BLACK; paint.alpha = (255 * alpha).toInt()
        c.drawCircle(0f, 0f, r, paint)
        // The photon ring: a hairline, brighter on the approaching (left) side.
        paint.style = Paint.Style.STROKE; paint.strokeWidth = r * .03f
        oval.set(-r * 1.02f, -r * 1.02f, r * 1.02f, r * 1.02f)
        paint.color = 0xfffbf6ff.toInt(); paint.alpha = (alpha * 220).toInt(); c.drawArc(oval, 90f, 180f, false, paint)
        paint.alpha = (alpha * 90).toInt(); c.drawArc(oval, 270f, 180f, false, paint)

        // The near half crosses in front of the shadow.
        disk(c, r, tilt, 0f, alpha, h)
        clumps(c, r, tilt, spin, back = false, alpha = alpha, h = h)
        c.restore()
    }

    // One smooth disk: a radial temperature gradient (white-hot inner edge to violet, fading out),
    // multiplied by a left-to-right falloff for Doppler beaming. Built once; only matrices move.
    private val diskShader = android.graphics.ComposeShader(
        RadialGradient(0f, 0f, 1f,
            intArrayOf(0x00ffffff, 0x00ffffff, 0xfffff4fb.toInt(), 0xffc9a8ff.toInt(), 0xcc6a3de0.toInt(), 0x552a1480, 0x00000000),
            floatArrayOf(0f, .27f, .3f, .4f, .58f, .8f, 1f), Shader.TileMode.CLAMP),
        android.graphics.LinearGradient(-1f, 0f, 1f, 0f, intArrayOf(0xffffffff.toInt(), 0xccffffff.toInt(), 0x44ffffff), null, Shader.TileMode.CLAMP),
        android.graphics.PorterDuff.Mode.MULTIPLY)

    private fun disk(c: Canvas, r: Float, tilt: Float, start: Float, alpha: Float, h: Float) {
        val outer = r * 4.8f
        shaderMatrix.setScale(outer, outer * tilt)
        diskShader.setLocalMatrix(shaderMatrix)
        paint.style = Paint.Style.FILL
        paint.shader = diskShader
        paint.alpha = (255 * alpha).toInt()
        // The far half (start 180) above the centre line, the near half below it.
        val saved = c.save()
        if (start >= 180f) c.clipRect(-outer, -outer, outer, 0f) else c.clipRect(-outer, 0f, outer, outer)
        oval.set(-outer, -outer * tilt, outer, outer * tilt)
        c.drawOval(oval, paint)
        c.restoreToCount(saved)
        paint.shader = null
    }

    private fun clumps(c: Canvas, r: Float, tilt: Float, spin: Float, back: Boolean, alpha: Float, h: Float) {
        paint.style = Paint.Style.STROKE
        for (clump in clumps) {
            val rf = clump[0]
            val angle = ((clump[1] + spin * 95f * (1.3f / rf).pow(1.5f)) % 360f + 360f) % 360f
            val mid = angle + clump[2] / 2f
            val front = (mid % 360f) < 180f
            if (front == back) continue
            val rr = r * rf
            oval.set(-rr, -rr * tilt, rr, rr * tilt)
            val approaching = .5f - .5f * cos(Math.toRadians(mid.toDouble())).toFloat() // 1 on the left
            paint.strokeWidth = r * .035f * clump[3]
            paint.color = mix(0xffe9dcff.toInt(), Theme.GOLD, h)
            paint.alpha = (alpha * (if (back) .4f else .7f) * (20 + 90 * approaching)).toInt()
            c.drawArc(oval, angle, clump[2] * (1f + h), false, paint)
        }
    }

    companion object {
        /** A shared clock so the card's hole and the scene's hole spin in phase. */
        fun clock(): Float = (SystemClock.uptimeMillis() % 3_600_000L) / 1000f

        fun mix(a: Int, b: Int, t: Float): Int {
            val u = t.coerceIn(0f, 1f)
            return Color.argb(
                (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * u).toInt(),
                (Color.red(a) + (Color.red(b) - Color.red(a)) * u).toInt(),
                (Color.green(a) + (Color.green(b) - Color.green(a)) * u).toInt(),
                (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * u).toInt())
        }
    }
}
