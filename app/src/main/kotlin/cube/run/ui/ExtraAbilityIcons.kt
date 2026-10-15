package cube.run.ui

import android.graphics.Canvas
import android.graphics.Paint
import cube.run.data.Skins.Ability

/** The wardrobe icons of the later abilities, drawn like [AbilityIcon]: flat fills under a 3-unit ink outline, on a 48 grid. */
class ExtraAbilityIcon(private val ability: Ability) : Icon() {

    override fun draw(canvas: Canvas) {
        val s = minOf(bounds.width(), bounds.height()) / 48f
        canvas.save(); canvas.translate(bounds.exactCenterX() - 24f * s, bounds.exactCenterY() - 24f * s); canvas.scale(s, s)
        paint.strokeWidth = 3f; paint.strokeJoin = Paint.Join.ROUND; paint.strokeCap = Paint.Cap.ROUND
        when (ability) {
            Ability.SMOL -> {
                // A low bar overhead, and a small cube sailing beneath it.
                ink(); paint.strokeWidth = 6f; canvas.drawLine(6f, 11f, 42f, 11f, paint)
                paint.color = Theme.BERRY; paint.strokeWidth = 3f; canvas.drawLine(6f, 11f, 42f, 11f, paint)
                fill(Theme.MINT); canvas.drawRoundRect(15f, 21f, 33f, 39f, 4f, 4f, paint)
                ink(); canvas.drawRoundRect(15f, 21f, 33f, 39f, 4f, 4f, paint)
                canvas.drawLine(7f, 44f, 41f, 44f, paint)
            }
            Ability.BUBBLE_SCORE -> {
                // A bubble carrying a bold ×2.
                fill(Theme.CYAN); canvas.drawCircle(24f, 24f, 19f, paint)
                ink(); canvas.drawCircle(24f, 24f, 19f, paint)
                paint.color = Theme.WHITE; canvas.drawArc(11f, 11f, 37f, 37f, 200f, 50f, false, paint)
                fill(Theme.INK); paint.textAlign = Paint.Align.CENTER; paint.isFakeBoldText = true; paint.textSize = 19f
                canvas.drawText("×2", 24f, 31f, paint)
                paint.isFakeBoldText = false
            }
            Ability.POUND_MAGNET -> {
                // A horseshoe magnet slamming down onto the road.
                magnet()
                fill(Theme.BERRY); canvas.drawPath(path, paint)
                fill(Theme.WHITE); canvas.drawRect(10f, 24f, 19f, 30f, paint); canvas.drawRect(29f, 24f, 38f, 30f, paint)
                ink(); canvas.drawPath(path, paint)
                canvas.drawLine(10f, 24f, 19f, 24f, paint); canvas.drawLine(29f, 24f, 38f, 24f, paint)
                paint.color = Theme.GOLD; paint.strokeWidth = 4f; canvas.drawLine(5f, 38f, 43f, 38f, paint)
                ink(); canvas.drawLine(14f, 44f, 20f, 44f, paint); canvas.drawLine(28f, 44f, 34f, 44f, paint)
            }
            Ability.BUBBLE_SLOWMO -> {
                // An hourglass with its sand running slow.
                hourglass(); fill(Theme.CYAN); canvas.drawPath(path, paint)
                path.reset(); path.moveTo(16f, 40f); path.lineTo(32f, 40f); path.lineTo(24f, 32f); path.close()
                fill(Theme.GOLD); canvas.drawPath(path, paint)
                hourglass(); ink(); canvas.drawPath(path, paint)
                canvas.drawLine(8f, 5f, 40f, 5f, paint); canvas.drawLine(8f, 43f, 40f, 43f, paint)
            }
            else -> {}
        }
        paint.style = Paint.Style.FILL; canvas.restore()
    }

    private val path = android.graphics.Path()

    private fun magnet() {
        path.reset(); path.moveTo(10f, 30f); path.lineTo(10f, 18f)
        path.cubicTo(10f, 3f, 38f, 3f, 38f, 18f); path.lineTo(38f, 30f); path.lineTo(29f, 30f); path.lineTo(29f, 18f)
        path.cubicTo(29f, 12f, 19f, 12f, 19f, 18f); path.lineTo(19f, 30f); path.close()
    }

    private fun hourglass() {
        path.reset(); path.moveTo(12f, 6f); path.lineTo(36f, 6f); path.lineTo(26f, 24f)
        path.lineTo(36f, 42f); path.lineTo(12f, 42f); path.lineTo(22f, 24f); path.close()
    }

    private fun fill(color: Int) { paint.style = Paint.Style.FILL; paint.color = color }
    private fun ink() { paint.style = Paint.Style.STROKE; paint.color = Theme.INK; paint.strokeWidth = 3f }

    companion object {
        val handled = setOf(Ability.SMOL, Ability.BUBBLE_SCORE, Ability.POUND_MAGNET, Ability.BUBBLE_SLOWMO)
    }
}
