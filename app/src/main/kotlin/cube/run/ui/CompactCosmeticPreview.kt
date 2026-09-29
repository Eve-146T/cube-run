package cube.run.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import cube.run.data.*

/** A still colour/shape swatch; browsing never needs a full-screen animated showroom. */
internal class CompactCosmeticPreview(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var cat = Wardrobe.CUBE
    private var item = 0
    fun bind(category: Int, id: Int) {
        cat = category; item = id
        contentDescription = context.gameText(Wardrobe.name(cat, item))
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        val scale = minOf(width, height) / 64f
        canvas.save(); canvas.translate(width / 2f, height / 2f); canvas.scale(scale, scale)
        fun face(color: Int, vararg points: Float) {
            path.reset(); path.moveTo(points[0], points[1])
            for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
            path.close(); paint.color = color; canvas.drawPath(path, paint)
        }
        when (cat) {
            Wardrobe.CUBE -> {
                val skin = Skins.get(item)
                val color = Theme.hsv(skin.hueAt(0f, 200f), skin.sat, skin.valueAt(0f))
                face(Theme.lighten(color, .2f), 0f,-26f, 23f,-14f, 0f,-2f, -23f,-14f)
                face(color, -23f,-14f, 0f,-2f, 0f,26f, -23f,13f)
                face(Theme.darken(color,.25f), 0f,-2f, 23f,-14f, 23f,13f, 0f,26f)
            }
            Wardrobe.BUBBLE -> {
                val bubble = BubbleSkins.get(item)
                val color = Theme.hsv(bubble.hue, bubble.sat, 1f)
                paint.color = Theme.alpha(color, 65); canvas.drawCircle(0f,0f,25f,paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = color
                canvas.drawCircle(0f,0f,25f,paint)
                paint.color = Theme.WHITE; canvas.drawArc(-19f,-19f,19f,19f,210f,60f,false,paint)
                paint.style = Paint.Style.FILL
            }
            else -> {
                val trail = Trails.get(item)
                for (i in 0..6) {
                    paint.color = Theme.hsv(trail.hueAt(0f,i), trail.sat, trail.value)
                    val x = -27f + i * 9f; val y = if (i % 2 == 0) -5f else 7f
                    canvas.drawRect(x-3f,y-3f,x+3f,y+3f,paint)
                }
            }
        }
        canvas.restore()
    }
}
