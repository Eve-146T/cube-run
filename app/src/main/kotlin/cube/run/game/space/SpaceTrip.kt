package cube.run.game.space

import com.badlogic.gdx.graphics.Color
import cube.run.core.hsvInto
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * One visit to Outer Space, rolled when the portal is crossed: the trip's
 * colours, and the journey. Things come up out of the dark one after another
 * and slide past (planets, a ringed giant beneath the road, a family of
 * moons, a space station, a black hole, a sunrise), while stretches of space
 * weather come and go (an asteroid belt, a meteor shower, comets, nebula
 * clouds). Every visit is its own route; a visit shows a lot of space.
 */
class SpaceTrip(seed: Int) {
    private val rnd = Random(seed)

    /** A trip's colours: the dark sky, the road and its accents. */
    class Nebula(
        val skyTopH: Float, val skyTopS: Float, val skyTopV: Float,
        val skyBotH: Float, val skyBotS: Float, val skyBotV: Float,
        val roadH: Float, val roadS: Float, val roadV: Float,
        /** Kerbs, lane lights, gravity rings: the neon of this nebula. */
        val neonH: Float,
        /** Asteroids are tinted toward this hue. */
        val rockH: Float,
        /** Stars glint this colour (white, warmed or cooled). */
        val starH: Float, val starS: Float,
    )

    /** Something on the way: [kind] shows up far ahead once the trip has flown [at] units, on [side] (-1 left, 1 right). */
    class Landmark(val kind: Int, val at: Float, val side: Float, val seed: Int)

    /** A stretch of space weather between [from] and [to] (units flown). */
    class Weather(val kind: Int, val from: Float, val to: Float)

    val nebula: Nebula = NEBULAE[rnd.nextInt(NEBULAE.size)]
    val landmarks = ArrayList<Landmark>()
    val weather = ArrayList<Weather>()
    /** Asteroids around the road, before any belt. */
    val asteroidDensity = 0.75f + rnd.nextFloat() * 0.5f
    val starSeed = rnd.nextInt()
    val rockSeed = rnd.nextInt()

    init {
        // Objects: a planet straight away, then every other kind once in a shuffled order, planets in between.
        val kinds = mutableListOf(RINGED, GIANT, MOONS, STATION, BLACK_HOLE, SUN).apply { shuffle(rnd) }
        val route = ArrayList<Int>()
        route.add(PLANET)
        for (k in kinds) { route.add(k); if (rnd.nextFloat() < 0.6f) route.add(PLANET) }
        var at = 10f
        var side = if (rnd.nextBoolean()) 1f else -1f
        repeat(2) { // a long stay (the section tester) goes round again
            for (k in route) {
                landmarks.add(Landmark(k, at, side, rnd.nextInt()))
                at += 75f + rnd.nextFloat() * 30f
                side = if (rnd.nextFloat() < 0.75f) -side else side
            }
        }
        // Weather: three of the four kinds, one after another with calm between.
        val skies = mutableListOf(BELT, SHOWER, COMETS, NEBULA_CLOUD).apply { shuffle(rnd) }
        var from = 60f + rnd.nextFloat() * 40f
        repeat(2) { round ->
            for (k in skies.take(3)) {
                val len = 110f + rnd.nextFloat() * 60f
                weather.add(Weather(k, from + round * 900f, from + round * 900f + len))
                from += len + 60f + rnd.nextFloat() * 50f
            }
            from = 60f
        }
    }

    /** How strong weather [kind] is [travelled] units in: 0 outside, easing up to 1 and back down over its stretch. */
    fun weatherAt(kind: Int, travelled: Float): Float {
        var best = 0f
        for (w in weather) {
            if (w.kind != kind || travelled < w.from || travelled > w.to) continue
            val edge = min(travelled - w.from, w.to - travelled) / 35f
            best = max(best, min(1f, edge).let { it * it * (3f - 2f * it) })
        }
        return best
    }

    fun skyTop(out: Color): Color = hsvInto(out, nebula.skyTopH, nebula.skyTopS, nebula.skyTopV)
    fun skyBottom(out: Color): Color = hsvInto(out, nebula.skyBotH, nebula.skyBotS, nebula.skyBotV)

    companion object {
        // landmarks
        const val PLANET = 0       // a planet sliding past on one side
        const val RINGED = 1       // a big ringed planet, a little further out
        const val GIANT = 2        // a giant ringed planet passing slowly beneath the road
        const val MOONS = 3        // a family of moons tumbling past overhead
        const val STATION = 4      // a space station, its ring turning
        const val BLACK_HOLE = 5   // a black hole with a burning accretion ring
        const val SUN = 6          // a sun climbing out of the haze, rays and all
        // weather
        const val BELT = 0         // the asteroids crowd in
        const val SHOWER = 1       // shooting stars by the dozen
        const val COMETS = 2       // comets streak across the sky
        const val NEBULA_CLOUD = 3 // glowing clouds drift past

        /** Sky top / bottom, road, neon, rock and star tints, HSV. The sky is the dark of space: black up high, deep blue at the horizon. */
        private val NEBULAE = listOf(
            Nebula(232f, 0.85f, 0.05f, 226f, 0.85f, 0.26f, 264f, 0.62f, 0.46f, 188f, 300f, 190f, 0.15f), // grape soda
            Nebula(222f, 0.90f, 0.06f, 214f, 0.90f, 0.28f, 226f, 0.70f, 0.45f, 322f, 28f, 200f, 0.10f),  // lagoon
            Nebula(236f, 0.80f, 0.04f, 230f, 0.80f, 0.24f, 268f, 0.60f, 0.44f, 46f, 300f, 40f, 0.20f),    // ember
            Nebula(226f, 0.85f, 0.05f, 220f, 0.85f, 0.25f, 240f, 0.66f, 0.44f, 130f, 330f, 150f, 0.12f),  // aurora
            Nebula(234f, 0.85f, 0.05f, 228f, 0.85f, 0.27f, 260f, 0.66f, 0.45f, 52f, 190f, 340f, 0.12f),   // cherry
        )

        /** Planet looks: three HSV colours each (body, band, accent). Bold candy, like the cubes. */
        val PLANET_LOOKS = listOf(
            floatArrayOf(12f, 0.75f, 1f, 36f, 0.6f, 1f, 350f, 0.8f, 0.9f),     // coral
            floatArrayOf(158f, 0.72f, 0.95f, 120f, 0.55f, 1f, 182f, 0.8f, 0.85f), // mint
            floatArrayOf(275f, 0.62f, 1f, 305f, 0.45f, 1f, 255f, 0.72f, 0.9f),  // lilac
            floatArrayOf(200f, 0.8f, 1f, 186f, 0.5f, 1f, 222f, 0.85f, 0.85f),   // ocean
            floatArrayOf(46f, 0.78f, 1f, 30f, 0.7f, 1f, 16f, 0.78f, 0.95f),     // butter
            floatArrayOf(330f, 0.68f, 1f, 310f, 0.48f, 1f, 346f, 0.8f, 0.9f),   // bubblegum
        )
    }
}
