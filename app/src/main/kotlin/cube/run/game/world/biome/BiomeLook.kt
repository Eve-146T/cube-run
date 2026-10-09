package cube.run.game.world.biome

import com.badlogic.gdx.graphics.Color
import cube.run.core.gfx.FacetShape
import cube.run.core.hsvInto
import cube.run.data.Worlds
import kotlin.random.Random

/** One thing in a biome's layers: a prop on the land, a far shape on the horizon, a landmark, a mote of weather. */
class Piece {
    var look: BiomeLook? = null
    var kind = 0
    var x = 0f; var y = 0f; var z = 0f
    var s = 1f
    var yaw = 0f
    var seed = 0f
    /** How fast it closes in, as a share of the road's speed (far things are slow). */
    var rate = 1f
    /** Road units flown since it came up. */
    var travelled = 0f
    /** Far shapes: 0 sunk below the horizon … 1 standing (rises and sinks with its world). */
    var show = 0f
    var vx = 0f; var vy = 0f; var vz = 0f
    var life = 0f
    val pal = arrayOf(Color(), Color(), Color())

    fun paint(slot: Int, h: Float, s: Float, v: Float) { hsvInto(pal[slot], h, s, v) }
}

/**
 * What makes a biome itself, beyond its palette: the toys on the land
 * beside the road, the shapes on its horizon, the landmarks that come past
 * now and then, its weather, its sky and the kerb of its road. Everything is
 * built from chunky boxes ([BiomeToys]) and drawn through [BiomeDraw].
 *
 * Positions: x across (the road is |x| < 3), y up from the land, z along the
 * road (negative ahead). Seeding never touches the course's random numbers.
 */
abstract class BiomeLook(val world: Worlds.World) {

    /** Every shape this biome draws, uploaded before it first shows. */
    abstract val shapes: List<FacetShape>

    // ---- the land beside the road: props scroll with it, born in the haze
    /** Distance along the road between props on one side. */
    open val propGap = 7f
    /** Place a fresh prop: kind, |x| (the side is chosen for it), y, size, turn and colours. Keep its foot off the road: see [clear]. */
    abstract fun seedProp(p: Piece, r: Random)

    /** A turn of [Piece.yaw] degrees toward the road, from whichever side the prop stands on. */
    protected fun toRoad(p: Piece): Float = if (p.x > 0f) -p.yaw else p.yaw

    /** An |x| for a prop reaching [half] either side of its middle: its near edge [CLEAR] or more from the road's middle, up to [spread] further. */
    protected fun clear(half: Float, r: Random, spread: Float = 10f): Float = CLEAR + half + r.nextFloat() * spread
    abstract fun drawProp(d: BiomeDraw, p: Piece, fog: Float, time: Float)

    // ---- a channel running along each side of the road (lava, strawberry milk), in steps one tile long
    open val stream = false
    /** The channel's middle, out from the kerb, at [wind] (-1…1 as it winds), and its width. */
    open fun streamX(wind: Float): Float = 1f + wind * 0.25f
    open val streamWidth = 2f
    open fun drawStream(d: BiomeDraw, p: Piece, fog: Float, time: Float) {}

    // ---- the horizon: big shapes drifting by slowly, far beyond the land
    abstract fun seedFar(p: Piece, r: Random)
    abstract fun drawFar(d: BiomeDraw, p: Piece, haze: Float, time: Float)

    // ---- landmarks: one set piece at a time, sliding past at a distance
    open val landmarkKinds = 0
    /** Place landmark [Piece.kind]: x, y, size, [Piece.rate], colours. */
    open fun seedLandmark(p: Piece, r: Random) {}
    open fun drawLandmark(d: BiomeDraw, p: Piece, haze: Float, time: Float) {}
    /** How far a landmark reaches from its centre (it leaves once that is off screen). */
    open fun landmarkReach(p: Piece): Float = p.s

    // ---- weather: motes around the camera
    open val motes = 0
    /** A fresh mote. [anywhere] spreads it through the whole volume (the biome just arrived), else it starts at its source. */
    open fun seedMote(p: Piece, r: Random, anywhere: Boolean) {}
    /** Move a mote by its own drift; it also scrolls with the road at [Piece.rate]. False once it is gone. */
    open fun moveMote(p: Piece, dt: Float): Boolean {
        p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt
        p.life -= dt
        return p.life > 0f
    }
    open fun drawMote(d: BiomeDraw, p: Piece, fog: Float, time: Float) {}

    // ---- the sky: fixtures that stay up while the biome lasts (a sun, a moon, clouds), and painted stars
    open fun drawSky(d: BiomeDraw, alpha: Float, time: Float) {}
    open fun skyRays(sky: SkyPainter, alpha: Float, time: Float) {}
    open fun paintSky(sky: SkyPainter, alpha: Float, time: Float) {}

    // ---- the road's edge and the land's colour
    open val kerb = KERB_PLAIN
    /** Kerb colours for a tile row: [a] on even rows, [b] on odd ones. */
    open fun kerbColors(a: Color, b: Color) {
        hsvInto(a, world.floorH + 30f, world.floorS * 0.6f, 1f); b.set(a)
    }
    open fun ground(out: Color, parity: Int) {
        hsvInto(out, world.floorH - 22f, world.floorS * 0.6f, world.floorAltV * (if (parity == 0) 0.8f else 0.74f))
    }

    companion object {
        /** Props keep this far from the road's middle: past the roadside posts and any channel along the kerb. */
        const val CLEAR = 5.2f
        const val KERB_PLAIN = 0
        /** Alternating colours, row by row (candy stripes, neon). */
        const val KERB_STRIPES = 1
        /** A low edge with a glowing light on every other row (a runway). */
        const val KERB_LIGHTS = 2
        /** A lumpy bank, taller on every other row (snow, sand). */
        const val KERB_BANK = 3
    }
}
