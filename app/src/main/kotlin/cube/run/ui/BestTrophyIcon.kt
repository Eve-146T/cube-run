package cube.run.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.min

/**
 * The best score's trophy, drawn like a sticker: every part is first stamped
 * in ink a little fatter than itself, then painted on top, so it keeps an ink
 * outline like the logo letters and reads on every world's sky. A chunky cup
 * with a pale rim, handles, a stem and a two-tone base; a white star and a
 * glint on the cup. Drawn on a 44-unit grid (the parts sit inside 2..42).
 */
class BestTrophyIcon(private val outline: Float = 2.4f) : Icon() {
    private val handles = Path().apply {
        moveTo(11f, 10.5f); lineTo(7.2f, 10.5f); cubicTo(5.7f, 10.5f, 4.8f, 11.5f, 4.8f, 13f); cubicTo(4.8f, 17.6f, 8f, 20.8f, 12.8f, 21.2f)
    }
    private val handleShade = Path().apply {
        moveTo(29f, 10.5f); lineTo(32.8f, 10.5f); cubicTo(34.3f, 10.5f, 35.2f, 11.5f, 35.2f, 13f); cubicTo(35.2f, 17.6f, 32f, 20.8f, 27.2f, 21.2f)
    }
    private val stem = Path().apply { addRect(17.4f, 25.5f, 22.6f, 30.5f, Path.Direction.CW) }
    private val base = Path().apply { addRoundRect(10.2f, 29.4f, 29.8f, 37f, 2.4f, 2.4f, Path.Direction.CW) }
    private val baseShade = Path().apply {
        addRoundRect(10.2f, 33.6f, 29.8f, 37f, floatArrayOf(0f, 0f, 0f, 0f, 2.4f, 2.4f, 2.4f, 2.4f), Path.Direction.CW)
    }
    private val cup = Path().apply {
        moveTo(10f, 7.5f); lineTo(30f, 7.5f); lineTo(30f, 16f)
        cubicTo(30f, 22.2f, 25.6f, 26.8f, 20f, 26.8f); cubicTo(14.4f, 26.8f, 10f, 22.2f, 10f, 16f); close()
    }
    private val cupShade = Path().apply {
        moveTo(24.6f, 7.5f); lineTo(30f, 7.5f); lineTo(30f, 16f)
        cubicTo(30f, 22.2f, 25.6f, 26.8f, 20f, 26.8f); cubicTo(23.2f, 25f, 24.6f, 21f, 24.6f, 16f); close()
    }
    private val rim = Path().apply { addRoundRect(6.7f, 4.6f, 33.3f, 9.2f, 1.9f, 1.9f, Path.Direction.CW) }
    private val star = Path().apply {
        val p = floatArrayOf(20f, 11.4f, 21.45f, 14.35f, 24.7f, 14.82f, 22.35f, 17.11f, 22.9f, 20.34f,
            20f, 18.82f, 17.1f, 20.34f, 17.65f, 17.11f, 15.3f, 14.82f, 18.55f, 14.35f)
        moveTo(p[0], p[1]); for (i in 2 until p.size step 2) lineTo(p[i], p[i + 1]); close()
    }
    private val solids = listOf(stem, base, cup, rim)

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()) / 44f
        canvas.save()
        canvas.translate(b.exactCenterX() - 22f * s, b.exactCenterY() - 22f * s)
        canvas.scale(s, s)
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        // the ink stamp
        paint.color = Theme.INK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = HANDLE + 2f * outline
        canvas.drawPath(handles, paint); canvas.drawPath(handleShade, paint)
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.strokeWidth = 2f * outline
        for (part in solids) canvas.drawPath(part, paint)
        // the paint
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = HANDLE
        paint.color = GOLD; canvas.drawPath(handles, paint)
        paint.color = SHADE; canvas.drawPath(handleShade, paint)
        paint.style = Paint.Style.FILL
        paint.color = SHADE; canvas.drawPath(stem, paint)
        paint.color = GOLD; canvas.drawPath(base, paint)
        paint.color = SHADE; canvas.drawPath(baseShade, paint)
        paint.color = GOLD; canvas.drawPath(cup, paint)
        paint.color = SHADE; canvas.drawPath(cupShade, paint)
        paint.color = LIGHT; canvas.drawPath(rim, paint)
        paint.color = Theme.WHITE; canvas.drawPath(star, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.4f
        paint.color = Theme.alpha(Theme.WHITE, 204)
        canvas.drawLine(13.4f, 12f, 13.4f, 16.2f, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()
    }

    private companion object {
        const val HANDLE = 3.6f
        const val GOLD = 0xFFFFC93C.toInt()
        const val SHADE = 0xFFF2A114.toInt()
        const val LIGHT = 0xFFFFE58A.toInt()
    }
}
