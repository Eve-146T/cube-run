package cube.run.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.*

/** Screen-sized casino takeover. One clock, no per-frame particle objects. */
internal class JackpotSpectacle(context: Context, private val kit: UiKit) {
    var time = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.get(context, 700) }
    private val path = Path()
    private val colors = intArrayOf(Theme.GOLD, Theme.PINK, Theme.CYAN, Theme.WHITE)
    private val digits = Array(10) { it.toString() }

    fun draw(c: Canvas, width: Float, height: Float) {
        val t = time
        val fade = min((t / .12f).coerceIn(0f, 1f), ((4.8f - t) / .4f).coerceIn(0f, 1f))
        val cx = width / 2f
        val cy = height * .43f
        val u = min(width / 360f, height / 600f)
        fun ink(value: Int, opacity: Float = 1f) {
            paint.color = value; paint.alpha = (255 * fade * opacity).toInt().coerceIn(0, 255)
        }
        paint.style = Paint.Style.FILL
        ink(0xff080311.toInt(), .94f)
        c.drawRect(0f, 0f, width, height, paint)
        // Rotating rays fill the screen; a dark stage below protects the lettering.
        c.save(); c.translate(cx, cy); c.rotate(t * 23f)
        for (i in 0 until 24) {
            ink(if (i % 2 == 0) Theme.GOLD else Theme.PINK, .18f)
            path.rewind(); path.moveTo(0f, 0f)
            path.lineTo(-height * .09f, -height); path.lineTo(height * .035f, -height); path.close()
            c.drawPath(path, paint); c.rotate(15f)
        }
        c.restore()
        paint.style = Paint.Style.STROKE
        for (i in 0..3) {
            val age = t - (.4f + i * .2f)
            if (age in 0f..1.2f) {
                ink(colors[i], (1f - age / 1.2f) * .9f)
                paint.strokeWidth = (10f - age * 7f) * u
                c.drawCircle(cx, cy, (40f + age * 400f) * u, paint)
            }
        }
        paint.style = Paint.Style.FILL
        // Sustained coin fountains from both lower corners.
        for (i in 0 until 160) {
            val age = t - .85f - (i % 40) * .065f
            if (age < 0f) continue
            val side = if (i % 2 == 0) 1f else -1f
            val speed = (90f + i * 37 % 210) * u
            val x = (if (side > 0) 0f else width) + side * speed * age
            val y = height * .95f - (350f + i * 23 % 350) * u * age + 180f * u * age * age
            c.save(); c.translate(x, y); c.rotate(i * 33f + age * 270f)
            val radius = (if (i % 9 == 0) 22f else 8f + i % 7) * u
            if (i % 3 != 0) {
                c.scale(.25f + abs(cos(age * 8f + i)) * .75f, 1f)
                ink(Theme.INK); c.drawCircle(0f, 0f, radius + 2f * u, paint)
                ink(Theme.GOLD); c.drawCircle(0f, 0f, radius, paint)
                ink(Theme.YELLOW); c.drawCircle(-radius * .12f, -radius * .12f, radius * .72f, paint)
                ink(Theme.INK); paint.textSize = radius * 1.5f; paint.textAlign = Paint.Align.CENTER
                c.drawText("$", 0f, radius * .5f, paint)
            } else {
                ink(colors[i % 4]); c.drawRect(-3f * u, -10f * u, 3f * u, 10f * u, paint)
            }
            c.restore()
        }
        ink(0xff100820.toInt(), .94f)
        path.rewind(); path.moveTo(0f, height * .45f); path.lineTo(width, height * .40f)
        path.lineTo(width, height * .65f); path.lineTo(0f, height * .69f); path.close()
        c.drawPath(path, paint)
        ink(Theme.GOLD); paint.strokeWidth = 3f * u
        c.drawLine(0f, height * .45f, width, height * .40f, paint)
        c.drawLine(0f, height * .69f, width, height * .65f, paint)
        // Oversized wheels stop and rebound individually.
        for (i in 0..2) {
            val stop = .4f + i * .2f
            val settled = t >= stop
            val age = (t - stop).coerceAtLeast(0f)
            val kick = exp(-age * 10f) * sin(age * 28f)
            val x = cx + (i - 1) * 103f * u
            val y = height * .29f + kick * 30f * u
            c.save(); c.translate(x, y); c.rotate((i - 1) * 7f + sin(t * 8f + i) * 2f)
            val zoom = if (settled) 1f + kick * .28f else 1f
            c.scale(zoom, zoom)
            ink(Theme.PINK); c.drawRoundRect(-49f*u, -67f*u, 49f*u, 73f*u, 12f*u, 12f*u, paint)
            ink(Theme.GOLD); c.drawRoundRect(-47f*u, -70f*u, 47f*u, 64f*u, 12f*u, 12f*u, paint)
            ink(0xff100820.toInt()); c.drawRoundRect(-42f*u, -65f*u, 42f*u, 59f*u, 8f*u, 8f*u, paint)
            c.save(); c.clipRect(-42f*u, -64f*u, 42f*u, 58f*u)
            paint.textSize = 112f*u; paint.textAlign = Paint.Align.CENTER
            ink(if (settled) Theme.GOLD else Theme.WHITE)
            if (settled) c.drawText("7", 0f, 36f*u, paint) else {
                val scroll = t * 26f + i * 3f
                for (row in -1..1) c.drawText(digits[((scroll.toInt()+row)%10+10)%10], 0f,
                    (36f + (row - scroll % 1f) * 115f)*u, paint)
            }
            c.restore(); c.restore()
        }
        for (i in 0 until 48) {
            val y = height * i / 47f
            ink(if ((i + (t*18f).toInt()) % 4 == 0) Theme.WHITE else Theme.GOLD, .9f)
            c.drawCircle(5f*u, y, 3f*u, paint); c.drawCircle(width-5f*u, y, 3f*u, paint)
        }
    }
}
