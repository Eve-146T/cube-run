package cube.run.game.space

import com.badlogic.gdx.graphics.Color
import cube.run.core.hsvInto
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * One visit to Outer Space, rolled when the portal is crossed: which nebula
 * the sky is, which planets hang in it, how thick the asteroids are, and the
 * one big thing that happens on the way (a giant planet sliding past, an
 * asteroid belt, comets, a sunrise). No two trips look alike.
 */
class SpaceTrip(seed: Int) {
    private val rnd = Random(seed)

    /** A candy nebula: sky, road and accents. */
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

    class Planet(
        /** Direction from the road (degrees: left/right of straight ahead, above/below the horizon) and distance. */
        val baseAzimuth: Float, val baseElevation: Float, var distance: Float,
        val radius: Float,
        val colors: Array<Color>,
        /** Band edges up the planet (-1..1), or null for a plain voxel ball. */
        val bands: FloatArray?,
        val tilt: Float, val spin: Float,
        /** Ring radius relative to the planet (0 = none), tilt of the ring. */
        val ring: Float, val ringTilt: Float, val ringColors: Array<Color>,
        val moons: Int,
        /** How fast it closes in, as a fraction of the run's speed (planets are far: barely). */
        val approach: Float,
        /** Glow 0..1: a sun shines with its own light. */
        val glow: Float = 0f,
        /** Over the trip it slides this far sideways and climbs this far (degrees): the fly-by and the sunrise. */
        val slide: Float = 0f, val rise: Float = 0f,
    ) {
        var azimuth = baseAzimuth
        var elevation = baseElevation

        /** Move along the set piece's path, [p] = 0..1 through the trip. */
        fun follow(p: Float) {
            val k = p * p * (3f - 2f * p)
            azimuth = baseAzimuth + slide * k
            elevation = baseElevation + rise * k
        }

        val x: Float get() = distance * sin(azimuth * DEG) * cos(elevation * DEG)
        val y: Float get() = distance * sin(elevation * DEG)
        val z: Float get() = -distance * cos(azimuth * DEG) * cos(elevation * DEG)
    }

    val nebula: Nebula = NEBULAE[rnd.nextInt(NEBULAE.size)]
    val setPiece: Int = rnd.nextInt(4)
    val planets = ArrayList<Planet>()
    /** Asteroids around the road (the belt set piece thickens them). */
    val asteroidDensity = 0.75f + rnd.nextFloat() * 0.5f
    val starSeed = rnd.nextInt()
    val rockSeed = rnd.nextInt()

    init {
        // Two planets, on opposite sides so they frame the road, at different depths.
        val side = if (rnd.nextBoolean()) 1f else -1f
        planets.add(planet(side * (6f + rnd.nextFloat() * 7f), 3f + rnd.nextFloat() * 9f, 230f + rnd.nextFloat() * 50f, 17f + rnd.nextFloat() * 11f))
        planets.add(planet(-side * (8f + rnd.nextFloat() * 6f), -4f + rnd.nextFloat() * 16f, 160f + rnd.nextFloat() * 50f, 7f + rnd.nextFloat() * 7f))
        when (setPiece) {
            FLYBY -> planets.add(planet(side * 3f, -7f - rnd.nextFloat() * 3f, 360f, 52f, approach = 0.3f, ringChance = 1f, slide = side * 24f))
            SUNRISE -> {
                val sunColors = arrayOf(hsvInto(Color(), 44f + rnd.nextFloat() * 14f, 0.45f, 1f), hsvInto(Color(), 30f, 0.6f, 1f))
                planets.add(Planet(-side * 3f, -9f, 330f, 18f, sunColors, null, 0f, 6f, 0f, 0f, sunColors, 0, 0.012f, glow = 1f, rise = 15f))
            }
        }
    }

    private fun planet(azimuth: Float, elevation: Float, distance: Float, radius: Float, approach: Float = 0.025f, ringChance: Float = 0.45f, slide: Float = 0f): Planet {
        val look = PLANET_LOOKS[rnd.nextInt(PLANET_LOOKS.size)]
        val colors = Array(3) { hsvInto(Color(), look[it * 3] + (rnd.nextFloat() - 0.5f) * 14f, look[it * 3 + 1], look[it * 3 + 2]) }
        val bands = when (rnd.nextInt(3)) {
            0 -> null
            1 -> floatArrayOf(-0.55f, -0.2f, 0.15f, 0.5f)
            else -> floatArrayOf(-0.7f, -0.35f, -0.05f, 0.3f, 0.62f)
        }
        val ringed = rnd.nextFloat() < ringChance
        val ringColors = arrayOf(hsvInto(Color(), look[3] + 10f, 0.35f, 1f), hsvInto(Color(), look[6], 0.45f, 0.9f))
        return Planet(azimuth, elevation, distance, radius, colors, bands,
            tilt = (rnd.nextFloat() - 0.5f) * 40f, spin = 2f + rnd.nextFloat() * 5f,
            ring = if (ringed) 1.7f + rnd.nextFloat() * 0.6f else 0f, ringTilt = 10f + rnd.nextFloat() * 22f,
            ringColors = ringColors, moons = rnd.nextInt(3), approach = approach, slide = slide)
    }

    fun skyTop(out: Color): Color = hsvInto(out, nebula.skyTopH, nebula.skyTopS, nebula.skyTopV)
    fun skyBottom(out: Color): Color = hsvInto(out, nebula.skyBotH, nebula.skyBotS, nebula.skyBotV)

    companion object {
        const val FLYBY = 0     // a giant ringed planet slides slowly past below the road
        const val BELT = 1      // the asteroids crowd in: you cross a belt
        const val COMETS = 2    // comets streak across the sky now and then
        const val SUNRISE = 3   // a sun climbs out of the haze, rays and all

        private const val DEG = (PI / 180.0).toFloat()

        /** Sky top / bottom, road, neon, rock and star tints, HSV. Saturated like the other bonus worlds: candy, never black. */
        private val NEBULAE = listOf(
            Nebula(262f, 0.85f, 0.56f, 318f, 0.62f, 0.94f, 264f, 0.62f, 0.46f, 188f, 300f, 190f, 0.15f), // grape soda
            Nebula(228f, 0.90f, 0.55f, 186f, 0.66f, 0.90f, 226f, 0.70f, 0.45f, 322f, 28f, 200f, 0.10f),  // lagoon
            Nebula(272f, 0.80f, 0.52f, 22f, 0.66f, 0.96f, 268f, 0.60f, 0.44f, 46f, 300f, 40f, 0.20f),    // ember nebula
            Nebula(244f, 0.85f, 0.52f, 158f, 0.56f, 0.86f, 240f, 0.66f, 0.44f, 130f, 330f, 150f, 0.12f), // aurora
            Nebula(266f, 0.85f, 0.54f, 342f, 0.62f, 0.94f, 260f, 0.66f, 0.45f, 52f, 190f, 340f, 0.12f),  // cherry cosmos
        )

        /** Planet looks: three HSV colours each (body, band, accent). Bold candy, like the cubes. */
        private val PLANET_LOOKS = listOf(
            floatArrayOf(12f, 0.75f, 1f, 36f, 0.6f, 1f, 350f, 0.8f, 0.9f),     // coral
            floatArrayOf(158f, 0.72f, 0.95f, 120f, 0.55f, 1f, 182f, 0.8f, 0.85f), // mint
            floatArrayOf(275f, 0.62f, 1f, 305f, 0.45f, 1f, 255f, 0.72f, 0.9f),  // lilac
            floatArrayOf(200f, 0.8f, 1f, 186f, 0.5f, 1f, 222f, 0.85f, 0.85f),   // ocean
            floatArrayOf(46f, 0.78f, 1f, 30f, 0.7f, 1f, 16f, 0.78f, 0.95f),     // butter
            floatArrayOf(330f, 0.68f, 1f, 310f, 0.48f, 1f, 346f, 0.8f, 0.9f),   // bubblegum
        )
    }
}
