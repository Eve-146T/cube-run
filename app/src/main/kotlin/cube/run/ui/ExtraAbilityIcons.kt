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
            else -> {}
        }
        paint.style = Paint.Style.FILL; canvas.restore()
    }

    private fun fill(color: Int) { paint.style = Paint.Style.FILL; paint.color = color }
    private fun ink() { paint.style = Paint.Style.STROKE; paint.color = Theme.INK; paint.strokeWidth = 3f }

    companion object {
        val handled = setOf(Ability.SMOL)
    }
}
