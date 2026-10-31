package cube.run.game.track

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import cube.run.game.Terrain
import cube.run.game.world.Fog
import kotlin.math.max
import kotlin.math.min

/** Pale, beveled chevrons painted directly onto duck obstacles, like the game's raised details. */
class DuckArrows {
    private val face = Color()
    private val bevel = Color()
    private val matrixGreen = Color(.08f, 1f, .3f, 1f)

    fun render(shapes: ShapeRenderer, track: Track, fogColor: Color, opacity: Float, matrix: Float, style: ObstacleCueStyle) {
        if (opacity <= .001f) return
        for (row in track.rows) {
            val p = row.pop
            if (p <= .001f || row.z < -Fog.end) continue
            val fog = Fog.at(row.z)
            for (ob in row.obs) {
                if (ob.cue != ObCue.DUCK) continue
                val w = ob.sx
                val h = ob.sy
                val count = if (style == ObstacleCueStyle.DUCK_CENTER) 1 else max(1, (ob.sx / 1.6f).toInt())
                val width = min(.72f, w / count * .55f)
                val height = min(h * .46f, .34f)
                val thickness = height * .38f
                val y = ob.cy + Terrain.y(row.z)
                val z = row.z + ob.sz / 2f + .025f
                if (style == ObstacleCueStyle.DUCK_INSET) {
                    face.set(ob.col).mul(.35f)
                    bevel.set(Color.WHITE).lerp(ob.col, .48f)
                } else {
                    face.set(Color.WHITE).lerp(ob.col, .12f)
                    bevel.set(ob.col).mul(.55f)
                }
                face.lerp(matrixGreen, matrix).lerp(fogColor, fog)
                face.a = opacity * p
                bevel.lerp(matrixGreen, matrix).lerp(fogColor, fog)
                bevel.a = opacity * p
                for (i in 0 until count) {
                    val x = ob.x + (i - (count - 1) / 2f) * w / count
                    fun mark(mx: Float, my: Float, mw: Float, mh: Float, thick: Float = mh * .38f) {
                        chevron(shapes, mx, my - .035f, z, mw, mh, thick, bevel)
                        chevron(shapes, mx, my, z + .002f, mw, mh, thick, face)
                    }
                    when (style) {
                        ObstacleCueStyle.DUCK_DOUBLE -> {
                            val dh = min(h * .27f, .19f)
                            val spacing = dh * 1.15f
                            mark(x, y + spacing / 2f, width * .84f, dh)
                            mark(x, y - spacing / 2f, width * .84f, dh)
                        }
                        ObstacleCueStyle.DUCK_CENTER -> mark(x, y, min(w * .62f, 1.35f), min(h * .60f, .43f))
                        ObstacleCueStyle.DUCK_FULL -> {
                            val ah = min(h * .68f, .49f)
                            arrow(shapes, x, y - .035f, z, width * .8f, ah, bevel)
                            arrow(shapes, x, y, z + .002f, width * .8f, ah, face)
                        }
                        ObstacleCueStyle.DUCK_TIPS -> {
                            val th = min(h * .39f, .28f)
                            val ty = y - h / 2f + th / 2f + .045f
                            tip(shapes, x, ty - .025f, z, width * .62f, th, bevel)
                            tip(shapes, x, ty, z + .002f, width * .62f, th, face)
                        }
                        else -> mark(x, y, width, height, thickness)
                    }
                }
            }
        }
    }

    private fun tip(shapes: ShapeRenderer, x: Float, y: Float, z: Float, width: Float, height: Float, color: Color) {
        val r = shapes.renderer
        if (r.numVertices + 3 > r.maxVertices) shapes.flush()
        r.color(color); r.vertex(x - width / 2f, y + height / 2f, z)
        r.color(color); r.vertex(x, y - height / 2f, z)
        r.color(color); r.vertex(x + width / 2f, y + height / 2f, z)
    }

    private fun arrow(shapes: ShapeRenderer, x: Float, y: Float, z: Float, width: Float, height: Float, color: Color) {
        tip(shapes, x, y - height * .20f, z, width, height * .6f, color)
        val r = shapes.renderer
        if (r.numVertices + 6 > r.maxVertices) shapes.flush()
        val left = x - width * .14f; val right = x + width * .14f
        val top = y + height / 2f; val bottom = y + height * .09f
        fun vertex(px: Float, py: Float) { r.color(color); r.vertex(px, py, z) }
        vertex(left, top); vertex(left, bottom); vertex(right, bottom)
        vertex(left, top); vertex(right, bottom); vertex(right, top)
    }

    /** Four triangles share the world-shapes batch: smooth diagonals without one draw per arrow. */
    private fun chevron(shapes: ShapeRenderer, x: Float, y: Float, z: Float,
                        width: Float, height: Float, thickness: Float, color: Color) {
        val renderer = shapes.renderer
        if (renderer.numVertices + 12 > renderer.maxVertices) shapes.flush()
        val left = x - width / 2f; val right = x + width / 2f
        val top = y + height / 2f; val bottom = y - height / 2f
        fun vertex(px: Float, py: Float) { renderer.color(color); renderer.vertex(px, py, z) }
        vertex(left, top); vertex(left, top - thickness); vertex(x, bottom)
        vertex(left, top); vertex(x, bottom); vertex(x, bottom + thickness)
        vertex(x, bottom); vertex(right, top - thickness); vertex(right, top)
        vertex(x, bottom); vertex(right, top); vertex(x, bottom + thickness)
    }
}
