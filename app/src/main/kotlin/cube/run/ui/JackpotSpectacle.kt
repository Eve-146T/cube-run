package cube.run.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.*

/** A bounded, allocation-free casino burst. The road and its input stay exposed. */
internal class JackpotSpectacle(context: Context, private val kit: UiKit) {
    var time = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.get(context, 700) }
    private val colors = intArrayOf(Theme.GOLD, Theme.PINK, Theme.CYAN, Theme.MINT, Theme.WHITE)
    private val digits = Array(10) { it.toString() }

    fun draw(c: Canvas, width: Float, height: Float) {
        val fade = ((3.8f - time) / .35f).coerceIn(0f, 1f)
        val cx = width / 2f
        val cy = kit.dpf(108f)
        val unit = min(kit.dpf(1f), width / 290f)
        fun color(value: Int, opacity: Float = 1f) {
            paint.color = value; paint.alpha = (255 * fade * opacity).toInt().coerceIn(0, 255)
        }
        c.save()
        c.clipRect(0f, 0f, width, height)
        // Rotating segmented sunburst: open gaps preserve the live scene underneath.
        c.save(); c.translate(cx, cy); c.rotate(time * 32f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 9f * unit
        for (i in 0 until 20) {
            color(colors[i % colors.size], .48f)
            c.drawLine(76f * unit, 0f, (112f + 15f * sin(time * 12f + i)) * unit, 0f, paint)
            c.rotate(18f)
        }
        c.restore()
        // Three expanding impact rings, each launched on a reel's landing.
        for (i in 0..2) {
            val age = time - .28f - i * .13f
            if (age in 0f.. .7f) {
                color(colors[i], (1f - age / .7f) * .85f)
                paint.strokeWidth = (7f - age * 7f) * unit
                c.drawCircle(cx, cy, (30f + age * 200f) * unit, paint)
            }
        }
        paint.style = Paint.Style.FILL
        // Deterministic ballistic coins and confetti; no per-frame particle allocations.
        for (i in 0 until 88) {
            val age = time - (i % 11) * .055f - .3f
            if (age <= 0f) continue
            val angle = i * 2.39996f
            val speed = (65f + (i * 37 % 135)) * unit
            val x = cx + cos(angle) * speed * age
            val y = cy + sin(angle) * speed * age + 46f * unit * age * age
            val opacity = ((2.6f - age) / .6f).coerceIn(0f, 1f)
            c.save(); c.translate(x, y); c.rotate(i * 31f + age * (150f + i % 5 * 80f))
            if (i % 3 == 0) {
                c.scale(.25f + abs(cos(age * 9f + i)) * .75f, 1f)
                color(Theme.INK, opacity); c.drawCircle(0f, 0f, 9f * unit, paint)
                color(Theme.GOLD, opacity); c.drawCircle(0f, 0f, 7f * unit, paint)
                color(Theme.WHITE, opacity); c.drawRect(-1f * unit, -4f * unit, 1f * unit, 4f * unit, paint)
            } else {
                color(colors[i % colors.size], opacity)
                c.drawRect(-3f * unit, -6f * unit, 3f * unit, 6f * unit, paint)
            }
            c.restore()
        }
        // Slot windows stop left to right, then dance while the payout stays steady.
        for (i in 0..2) {
            val stop = .28f + i * .13f
            val settled = time >= stop
            val x = cx + (i - 1) * 49f * unit
            val y = kit.dpf(32f) + if (settled) sin((time - stop) * 16f) * 3f * unit else 0f
            c.save(); c.translate(x, y)
            c.rotate(if (settled) sin(time * 10f + i) * 4f else 0f)
            color(Theme.INK); c.drawRoundRect(-23f * unit, -27f * unit, 23f * unit, 30f * unit, 9f * unit, 9f * unit, paint)
            color(if (settled) Theme.GOLD else Theme.WHITE)
            c.drawRoundRect(-20f * unit, -25f * unit, 20f * unit, 25f * unit, 7f * unit, 7f * unit, paint)
            c.save(); c.clipRect(-20f * unit, -24f * unit, 20f * unit, 24f * unit)
            color(Theme.INK); paint.textSize = 43f * unit; paint.textAlign = Paint.Align.CENTER
            if (settled) c.drawText("7", 0f, 15f * unit, paint) else {
                val scroll = time * 32f + i * 3f
                for (row in -1..1) c.drawText(digits[((scroll.toInt() + row) % 10 + 10) % 10], 0f,
                    (15f + (row - scroll % 1f) * 48f) * unit, paint)
            }
            c.restore(); c.restore()
        }
        c.restore()
    }
}
