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

    fun render(shapes: ShapeRenderer, track: Track, fogColor: Color, opacity: Float, matrix: Float) {
        if (opacity <= .001f) return
        for (row in track.rows) {
            val p = row.pop
            if (p <= .001f || row.z < -Fog.end) continue
            val fog = Fog.at(row.z)
            for (ob in row.obs) {
                if (ob.cue != ObCue.DUCK) continue
                val w = ob.sx * if (p >= .999f) 1f else (.4f + .6f * p)
                val h = ob.sy * if (p >= .999f) 1f else p
                val count = max(1, (ob.sx / 1.6f).toInt())
                val width = min(.72f * p, w / count * .55f)
                val height = min(h * .46f, .34f * p)
                val thickness = height * .38f
                val y = ob.cy + Terrain.y(row.z)
                val z = row.z + ob.sz / 2f + .025f
                face.set(Color.WHITE).lerp(ob.col, .12f).lerp(matrixGreen, matrix).lerp(fogColor, fog)
                face.a = opacity
                bevel.set(ob.col).mul(.55f).lerp(matrixGreen, matrix).lerp(fogColor, fog)
                bevel.a = opacity
                for (i in 0 until count) {
                    val x = ob.x + (i - (count - 1) / 2f) * w / count
                    chevron(shapes, x, y - .035f * p, z, width, height, thickness, bevel)
                    chevron(shapes, x, y, z + .002f, width, height, thickness, face)
                }
            }
        }
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
