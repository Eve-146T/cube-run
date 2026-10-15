package cube.run.game.world

import com.badlogic.gdx.graphics.Color
import cube.run.core.Gdx3DGame
import cube.run.game.track.LandingStrip
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Paints a jetpack's landing strip onto the road: a finish-flag pattern
 * anchored to the strip (it travels with the road), and after a hard
 * touchdown a shock ripple that bounces its squares up and out.
 */
internal class LandingStripPaint(private val game: Gdx3DGame) {

    companion object {
        /** The whole strip in big black and white squares. */
        const val LOOK_FLAG = 0
        /** The road's own tiles, chequered edges, a finish band where the descent starts and one at touchdown. */
        const val LOOK_BANDS = 1
        /** A chequer in the road's own colours, light against deep, with a black and white band at touchdown. */
        const val LOOK_TINT = 2
        const val LOOKS = 3
    }

    var look = LOOK_FLAG
    private val white = Color(1f, 1f, 1f, 1f)
    private val ink = Color(0.10f, 0.08f, 0.18f, 1f)
    private val light = Color()
    private val deep = Color()

    /**
     * The strip's share of one road tile, from [far] to [near] (already clipped
     * to the strip). [w] is the lane width, [outer] the unfolded outer lanes'
     * width, [c1]/[c2] and [parity] the tile's own two tones and their order. [glass] > 0 is the
     * see-through space road at that opacity.
     */
    fun paint(strip: LandingStrip, far: Float, near: Float, w: Float, outer: Float, c1: Color, c2: Color, parity: Int,
              fog: Float, alpha: Float, glass: Float, time: Float) {
        val cw = w / 2f
        val cd = cw
        light.set(c1).lerp(white, 0.55f)
        deep.set(c2).lerp(ink, 0.55f)
        val touchBand = 0 // the squares just past touchdown
        val startBand = floor(-strip.descentLen / cd).toInt()
        var k = floor((strip.touchZ - near) / cd).toInt()
        while (true) {
            val cellNear = strip.touchZ - k * cd
            val a = max(cellNear - cd, far)
            val b = min(cellNear, near)
            if (b <= far) break
            if (b - a > 0.01f) {
                val band = k == touchBand || k == touchBand + 1 || (look == LOOK_BANDS && (k == startBand || k == startBand + 1))
                val zc = (a + b) / 2f
                val len = b - a
                if (look == LOOK_BANDS && !band) {
                    for (l in 0 until 3) cell((l - 1) * w, zc, w, len, if ((l + 1 + parity) % 2 == 0) c1 else c2, strip, fog, alpha, glass, time)
                    edgeChecks(k, zc, w, outer, len, strip, fog, alpha, glass, time)
                } else {
                    val fine = look == LOOK_FLAG || band
                    for (j in 0 until 6) {
                        val on = (j + k) % 2 == 0
                        val col = if (fine) (if (on) white else ink) else (if (on) light else deep)
                        cell(-1.5f * w + (j + 0.5f) * cw, zc, cw, len, col, strip, fog, alpha, glass, time)
                    }
                    if (outer > 0.01f) for (side in 0..1) for (j in 0 until 2) {
                        val on = (j + k + side) % 2 == 0
                        val col = if (fine) (if (on) white else ink) else (if (on) light else deep)
                        val x = (1.5f * w + outer * (j + 0.5f) / 2f) * (if (side == 0) -1f else 1f)
                        cell(x, zc, outer / 2f, len, col, strip, fog, alpha, glass, time)
                    }
                }
            }
            k++
        }
    }

    /** LOOK_BANDS: a narrow chequer down each edge of the road. */
    private fun edgeChecks(k: Int, zc: Float, w: Float, outer: Float, len: Float, strip: LandingStrip, fog: Float, alpha: Float, glass: Float, time: Float) {
        val ew = 0.34f
        val x = 1.5f * w + outer - ew / 2f
        cell(-x, zc, ew, len, if (k % 2 == 0) white else ink, strip, fog, alpha, glass, time, top = 0.012f)
        cell(x, zc, ew, len, if (k % 2 == 0) ink else white, strip, fog, alpha, glass, time, top = 0.012f)
    }

    /** One square of the strip, lifted by the shock ripple. [top] raises it over the tile it sits on. */
    private fun cell(x: Float, zc: Float, sx: Float, len: Float, col: Color, strip: LandingStrip,
                     fog: Float, alpha: Float, glass: Float, time: Float, top: Float = 0f) {
        val lift = ripple(strip, x, zc, time)
        if (glass > 0f) game.glassGround(x, -0.04f + top + lift / 2f, zc, sx, 0.06f + lift, len, col, fog, alpha * glass)
        else game.worldGround(x, -0.14f + top + lift / 2f, zc, sx, 0.26f + lift, len, col, fog, alpha)
    }

    /** How far the shock of a hard touchdown has lifted the square at ([x], [z]): a ring racing out from the cube and dying away. */
    private fun ripple(strip: LandingStrip, x: Float, z: Float, time: Float): Float {
        if (strip.impactAt < 0f || strip.impactPower <= 0f) return 0f
        val age = time - strip.impactAt
        if (age < 0f || age > 1.8f) return 0f
        val dx = x - strip.impactX
        val dz = z // centred on the cube, not the scrolling spot it hit: the shock runs on up the road with you
        val d = sqrt(dx * dx + dz * dz)
        val off = (d - 1f - age * 11f) / 1.6f
        return 1.1f * strip.impactPower * exp(-off * off) * exp(-age * 1.9f)
    }
}
