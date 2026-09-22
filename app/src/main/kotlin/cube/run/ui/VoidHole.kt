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
        val tilt = .27f

        // The far half of the disk, dimmed; the shadow will cover its middle.
        disk(c, r, tilt, 180f, alpha * .55f, h)
        clumps(c, r, tilt, spin, back = true, alpha = alpha, h = h)

        // Lensing: light from behind the hole bends over the top (bright) and under it (faint).
        // Overlapping strokes blend into one glowing arc rather than separate rings.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.BUTT
        val lensHot = mix(0xfffff6ff.toInt(), 0xffffe6a8.toInt(), h)
        val lensCool = mix(0xff7c4dff.toInt(), Theme.GOLD, h)
        for (k in 0..9) {
            val rr = r * (1.05f + k * .045f)
            oval.set(-rr, -rr, rr, rr)
            paint.strokeWidth = r * .09f
            paint.color = mix(lensHot, lensCool, k / 9f)
            val fade = 1f - k / 10f
            paint.alpha = (alpha * 150 * fade * fade).toInt()
            c.drawArc(oval, 186f, 168f, false, paint)
            paint.alpha = (alpha * 55 * fade * fade).toInt()
            c.drawArc(oval, 14f, 152f, false, paint)
        }
        paint.strokeCap = Paint.Cap.ROUND

        // The shadow, then the photon ring hugging it.
        paint.style = Paint.Style.FILL; paint.color = Color.BLACK; paint.alpha = (255 * alpha).toInt()
        c.drawCircle(0f, 0f, r, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = r * .035f
        paint.color = 0xfffbf6ff.toInt(); paint.alpha = (alpha * 240).toInt()
        c.drawCircle(0f, 0f, r * 1.015f, paint)

        // The near half crosses in front of the shadow.
        disk(c, r, tilt, 0f, alpha, h)
        clumps(c, r, tilt, spin, back = false, alpha = alpha, h = h)
        c.restore()
    }

    private fun disk(c: Canvas, r: Float, tilt: Float, start: Float, alpha: Float, h: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.BUTT
        val hot = mix(0xfffff4fb.toInt(), 0xffffe08a.toInt(), h)
        val cool = mix(0xff4b2bb8.toInt(), 0xffff5a2e.toInt(), h * .7f)
        // A soft bloom under the disk, then tightly packed bands that read as one glowing plane.
        val bloom = r * 1.95f
        oval.set(-bloom, -bloom * tilt, bloom, bloom * tilt)
        paint.strokeWidth = r * 1.1f
        paint.color = mix(0xff6a3dff.toInt(), Theme.GOLD, h)
        beamed(c, start, alpha * 60f)
        for (k in 0..11) {
            val rr = r * (1.25f + k * .13f)
            oval.set(-rr, -rr * tilt, rr, rr * tilt)
            paint.strokeWidth = r * .17f
            paint.color = mix(hot, cool, (k / 11f).pow(.8f))
            beamed(c, start, alpha * (235f - k * 15f))
        }
        paint.strokeCap = Paint.Cap.ROUND
    }

    /** Doppler beaming: the approaching (left) side burns brighter than the receding right. */
    private fun beamed(c: Canvas, start: Float, peak: Float) {
        for (s in 0 until 9) {
            val mid = start + (s + .5f) * 20f
            val approaching = .5f - .5f * cos(Math.toRadians(mid.toDouble())).toFloat()
            paint.alpha = (peak * (.3f + .7f * approaching)).toInt().coerceIn(0, 255)
            c.drawArc(oval, start + s * 20f - .6f, 21.2f, false, paint)
        }
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
            paint.alpha = (alpha * (if (back) .5f else 1f) * (30 + 120 * approaching)).toInt()
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
