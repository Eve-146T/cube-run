package cube.run.game.track

import com.badlogic.gdx.graphics.Color
import cube.run.core.Gdx3DGame
import cube.run.game.Terrain
import kotlin.math.max
import kotlin.math.min

enum class ObstacleCueStyle(val duckOnly: Boolean = false) {
    ORIGINAL, EDGE_BANDS, ARROWS, HAZARD, ACTION_COLORS, SHADOW_ARROWS,
    DUCK_ARROWS(true), DUCK_DOUBLE(true), DUCK_CENTER(true), DUCK_FULL(true), DUCK_INSET(true), DUCK_TIPS(true)
}

/** Review treatments with no collision objects. Smooth duck arrows use the existing shapes pass. */
class ObstacleCues(private val game: Gdx3DGame) {
    var style = ObstacleCueStyle.DUCK_INSET
    private val ink = Color(.065f, .075f, .11f, 1f)
    private val white = Color(.98f, .98f, .94f, 1f)
    private val yellow = Color(1f, .8f, .08f, 1f)
    private val duck = Color(.12f, .72f, 1f, 1f)
    private val jump = Color(1f, .43f, .12f, 1f)
    private val shadow = Color(.10f, .08f, .15f, 1f)

    fun body(ob: Ob, original: Color): Color =
        if (style == ObstacleCueStyle.ACTION_COLORS && ob.cue != ObCue.NONE) {
            if (ob.cue == ObCue.DUCK) duck else jump
        } else original

    fun render(ob: Ob, z: Float, fog: Float, p: Float) {
        if (style == ObstacleCueStyle.ORIGINAL || style.duckOnly || ob.cue == ObCue.NONE || p <= .001f) return
        val w = ob.sx * if (p >= .999f) 1f else (.4f + .6f * p)
        val h = ob.sy * if (p >= .999f) 1f else p
        val cy = if (ob.grounded) ob.bottom + h / 2f else ob.cy
        val down = ob.cue == ObCue.DUCK
        // Match the body's terrain sample, even though decals sit just ahead of its face.
        val face = z + ob.sz / 2f + .014f
        val terrain = Terrain.y(z) - Terrain.y(face)
        fun faceBox(x: Float, y: Float, sx: Float, sy: Float, col: Color, depth: Float = .028f) {
            game.worldBox(x, y + terrain, face, sx, sy, depth, col, fog)
        }
        fun band(hazard: Boolean) {
            val bh = min(.15f * p, h * .28f)
            val y = cy + (if (down) 1f else -1f) * (h / 2f - bh * .7f)
            faceBox(ob.x, y, w + .014f, bh * 1.65f, ink)
            faceBox(ob.x, y, w, bh, if (hazard) yellow else white, .044f)
            if (hazard) {
                val n = max(2, (w / .42f).toInt())
                val step = w / n
                for (i in 0 until n) faceBox(ob.x - w / 2f + step * (i + .5f), y, step * .44f, bh, ink, .06f)
            }
        }
        fun arrows() {
            val count = max(1, (ob.sx / 1.6f).toInt())
            val size = min(h * .8f, .55f * p)
            val unit = size / 5f
            for (i in 0 until count) {
                val x = ob.x + (i - (count - 1) / 2f) * (w / count)
                faceBox(x, cy, size * 1.35f, size * 1.15f, ink, .042f)
                // Three stepped rows form a wide V / inverted V, legible without color.
                for (row in 0..2) {
                    val y = cy + (if (down) 1f else -1f) * (row - 1) * unit
                    if (row == 0) faceBox(x, y, unit * 1.25f, unit * 1.15f, white, .06f)
                    else for (side in -1..1 step 2)
                        faceBox(x + side * row * unit, y, unit * 1.25f, unit * 1.15f, white, .06f)
                }
            }
        }
        when (style) {
            ObstacleCueStyle.EDGE_BANDS -> band(false)
            ObstacleCueStyle.ARROWS -> arrows()
            ObstacleCueStyle.HAZARD -> band(true)
            ObstacleCueStyle.ACTION_COLORS -> arrows()
            ObstacleCueStyle.SHADOW_ARROWS -> {
                arrows()
                if (down) game.worldGround(ob.x, .018f, z, w, .024f, ob.sz * 1.4f, shadow, fog)
            }
            else -> Unit // Original and duck-only treatments do not add box decorations.
        }
    }
}
