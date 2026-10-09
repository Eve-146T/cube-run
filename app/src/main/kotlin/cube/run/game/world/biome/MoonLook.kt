package cube.run.game.world.biome

import com.badlogic.gdx.graphics.Color
import cube.run.core.gfx.FacetShapes
import cube.run.core.hsvInto
import cube.run.data.Worlds
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Deep Space, on the ground: a moon base. Domes, dishes, blinking masts,
 * craters and moon rocks on pale dust, moon hills on the horizon, a huge
 * ringed planet and a little blue one hanging in a starry sky, satellites
 * crossing over. A rocket lifts off its tower, a giant radar dish or a dome
 * town comes by. The road's edge is a runway, lit on every other tile.
 * (Outer Space, the bonus world, is the one that floats.)
 */
class MoonLook(world: Worlds.World) : BiomeLook(world) {

    private val dish = toy {
        block(0f, 0f, 0f, 0.3f, 0.12f, 0.3f, 0)
        block(0f, 0.12f, 0f, 0.1f, 0.5f, 0.1f, 0)
    }
    private val dishBowl = FacetShapes.voxelRing(10, 0.2f, 2)
    private val mast = toy {
        block(0f, 0f, 0f, 0.3f, 0.1f, 0.3f, 0)
        for (i in 0 until 5) block(0f, 0.1f + i * 0.3f, 0f, if (i % 2 == 0) 0.12f else 0.08f, 0.3f, if (i % 2 == 0) 0.12f else 0.08f, 0)
        block(0.12f, 0.9f, 0f, 0.14f, 0.04f, 0.04f, 2); block(-0.12f, 1.2f, 0f, 0.14f, 0.04f, 0.04f, 2)
    }
    private val crater = FacetShapes.voxelRing(12, 0.62f, 2)
    private val rocks = FacetShapes.clump(2)
    private val rocket = toy {
        block(0f, 0f, 0f, 0.3f, 1.1f, 0.3f, 0)
        steps(0f, 1.1f, 0f, 3, 0.24f, 0.08f, 0.36f) { 1 }
        for (s in intArrayOf(-1, 1)) block(s * 0.21f, 0f, 0f, 0.12f, 0.34f, 0.08f, 1) // fins
        block(0f, 0.7f, 0.15f + 0.01f, 0.14f, 0.14f, 0.02f, 2) // a porthole
    }
    private val tower = toy {
        block(0f, 0f, 0f, 0.3f, 1.3f, 0.3f, 0)
        for (i in 0 until 3) block(0.15f + 0.12f, 0.35f + i * 0.35f, 0f, 0.24f, 0.05f, 0.06f, 2)
    }
    private val satellite = toy {
        block(0f, 0f, 0f, 0.3f, 0.3f, 0.3f, 0)
        block(0.15f + 0.3f, 0.1f, 0f, 0.6f, 0.1f, 0.24f, 1); block(-0.15f - 0.3f, 0.1f, 0f, 0.6f, 0.1f, 0.24f, 1)
    }

    override val shapes = listOf(dish, dishBowl, mast, crater, rocks, rocket, tower, satellite, BiomeToys.ball, BiomeToys.bigBall, BiomeToys.cube,
        FacetShapes.voxelRing(14, 0.62f, 3))

    private val ringShape = FacetShapes.voxelRing(14, 0.62f, 3)
    private val planet = arrayOf(Color(), Color(), Color())
    private val planetBands = floatArrayOf(-0.45f, 0.1f)
    private val ringPal = arrayOf(Color(), Color())
    private val earth = arrayOf(Color(), Color(), Color())
    private val earthBands = floatArrayOf(-0.3f, 0.4f)
    private val flame = arrayOf(Color(), Color())
    private val blink = arrayOf(Color())
    private val dome = floatArrayOf(0.25f, 0.5f)
    private val starCol = Color()
    private val stars = FloatArray(STARS * 4).also {
        val r = Random(91)
        for (i in 0 until STARS) {
            it[i * 4] = (r.nextFloat() * 2f - 1f) * 1.35f; it[i * 4 + 1] = 0.3f + r.nextFloat() * 0.9f
            it[i * 4 + 2] = 0.004f + r.nextFloat() * r.nextFloat() * 0.007f; it[i * 4 + 3] = r.nextFloat() * 6.3f
        }
    }

    init {
        hsvInto(planet[0], 12f, 0.7f, 1f); hsvInto(planet[1], 30f, 0.55f, 1f); hsvInto(planet[2], 350f, 0.6f, 0.95f)
        hsvInto(ringPal[0], 165f, 0.5f, 1f); hsvInto(ringPal[1], 185f, 0.4f, 0.95f)
        hsvInto(earth[0], 150f, 0.6f, 0.85f); hsvInto(earth[1], 205f, 0.7f, 1f); hsvInto(earth[2], 0f, 0f, 1f)
        hsvInto(flame[0], 45f, 0.8f, 1f); hsvInto(flame[1], 15f, 0.9f, 1f)
        hsvInto(starCol, 220f, 0.08f, 1f)
    }

    override val propGap = 6f

    override fun seedProp(p: Piece, r: Random) {
        val roll = r.nextFloat()
        p.kind = when { roll < 0.25f -> 0; roll < 0.42f -> 1; roll < 0.55f -> 2; roll < 0.78f -> 3; else -> 4 }
        p.yaw = r.nextFloat() * 90f
        p.paint(0, 230f, 0.06f, 1f); p.paint(1, 185f, 0.75f, 1f); p.paint(2, 330f, 0.6f, 1f)
        when (p.kind) {
            0 -> { p.s = 3f + r.nextFloat() * 3f; p.paint(2, 230f, 0.06f, 1f) }    // a dome: white, a band of windows
            1 -> { p.s = 4f + r.nextFloat() * 2f; p.yaw = 20f } // a dish, looking up over the road
            2 -> p.s = 5f + r.nextFloat() * 3f                                    // a mast
            3 -> { p.s = 4f + r.nextFloat() * 4f; p.paint(0, 255f, 0.26f, 0.4f); p.paint(1, 252f, 0.22f, 0.56f) } // a crater
            else -> { p.s = 1.2f + r.nextFloat() * 1.2f; p.paint(0, 262f, 0.3f, 0.6f); p.paint(1, 250f, 0.2f, 0.75f) } // moon rocks
        }
        p.x = clear(p.s * when (p.kind) { 0 -> 1f; 1 -> 0.6f; 2 -> 0.2f; 3 -> 1f; else -> 1.3f }, r, 12f)
    }

    override fun drawProp(d: BiomeDraw, p: Piece, fog: Float, time: Float) {
        when (p.kind) {
            0 -> d.add(BiomeToys.ball, p.x, -p.s * 0.15f, p.z, p.s, p.s * 0.8f, p.s, p.yaw, p.pal, fog, glow = 0.1f, bands = dome)
            1 -> {
                d.add(dish, p.x, 0f, p.z, p.s, p.yaw, p.pal, fog)
                d.add(dishBowl, p.x, p.s * 0.72f, p.z, p.s * 0.55f, p.s * 0.55f, p.s * 0.55f, toRoad(p) - 90f * (if (p.x > 0f) 1f else -1f) + sin(time * 0.3f + p.seed * 5f) * 25f, p.pal, fog, pitch = 50f)
            }
            2 -> {
                d.add(mast, p.x, 0f, p.z, p.s, p.yaw, p.pal, fog)
                blink[0].set(if (sin(time * 3f + p.seed * 12f) > 0.3f) p.pal[2] else Color.WHITE)
                d.add(BiomeToys.cube, p.x, p.s * 1.6f + 0.25f, p.z, 0.25f, 0f, blink, fog, glow = 1f)
            }
            3 -> d.add(crater, p.x, 0f, p.z, p.s, p.s * 0.6f, p.s, p.yaw, p.pal, fog)
            else -> d.add(rocks, p.x, p.s * 0.6f, p.z, p.s, p.yaw, p.pal, fog)
        }
    }

    override fun seedFar(p: Piece, r: Random) {
        p.x = BiomeToys.farX(p, r, 0.05f, 0.3f)
        p.s = 60f + r.nextFloat() * 70f
        p.yaw = r.nextFloat() * 30f
        p.paint(0, 258f, 0.3f, 0.42f + r.nextFloat() * 0.1f); p.paint(1, 250f, 0.22f, 0.62f); p.paint(2, 0f, 0f, 1f)
    }

    override val horizonTall = 1.6f

    override fun drawFar(d: BiomeDraw, p: Piece, haze: Float, time: Float) {
        d.add(BiomeToys.bigBall, p.x, p.y - p.s * 0.12f, p.z, p.s, p.s * (0.18f + p.seed * 0.1f), p.s * 0.7f, p.yaw, p.pal, haze * 0.8f, onLand = false, bands = HILL)
    }

    override val landmarkKinds = 3

    override fun seedLandmark(p: Piece, r: Random) {
        p.x = 0.14f * 340f; p.rate = 0.4f
        p.paint(0, 230f, 0.06f, 1f); p.paint(1, 355f, 0.72f, 1f); p.paint(2, 185f, 0.75f, 1f)
        if (p.kind == 2) { p.paint(1, 185f, 0.75f, 1f); p.paint(2, 230f, 0.06f, 1f) }
        p.s = when (p.kind) { 0 -> 26f; 1 -> 30f; else -> 16f }
    }

    override fun landmarkReach(p: Piece) = p.s * 1.2f

    override fun drawLandmark(d: BiomeDraw, p: Piece, haze: Float, time: Float) {
        when (p.kind) {
            0 -> { // a rocket on its tower: once it is well up the road it lifts off, flame and all
                d.add(tower, p.x + p.s * 0.45f, -4f, p.z, p.s, 0f, p.pal, haze, onLand = false)
                val t = max(0f, p.travelled - 110f) / 40f
                val lift = t * t * 30f
                d.add(rocket, p.x, -4f + lift, p.z, p.s, time * 10f * t, p.pal, haze, onLand = false)
                if (t > 0f) for (k in 0 until 6) {
                    val f = (time * 6f + k * 0.37f) % 1f
                    d.add(BiomeToys.cube, p.x + (k % 3 - 1) * 0.8f, -4f + lift - 1f - f * 9f, p.z, (1.6f - f) * 2f, k * 40f, flame, haze, glow = 1f,
                        onLand = false, alpha = (1f - f) * (t * 3f).coerceAtMost(1f))
                }
            }
            1 -> {
                d.add(dish, p.x, -4f, p.z, p.s, 0f, p.pal, haze, onLand = false)
                d.add(dishBowl, p.x, -4f + p.s * 0.72f, p.z, p.s * 0.6f, p.s * 0.6f, p.s * 0.6f, (if (p.x > 0f) 200f else -20f) + sin(time * 0.2f) * 20f, p.pal, haze, pitch = 55f, onLand = false)
            }
            else -> for (k in 0 until 4) { // a little town of domes
                val s = p.s * (1f - k * 0.18f)
                d.add(BiomeToys.ball, p.x + (k - 1.5f) * p.s * 1.1f, -4f - s * 0.15f, p.z + (k % 2) * p.s * 0.8f, s, s * 0.8f, s, k * 30f, p.pal, haze, glow = 0.15f, onLand = false, bands = dome)
            }
        }
    }

    override val motes = 4

    override fun seedMote(p: Piece, r: Random, anywhere: Boolean) {
        val dir = if (r.nextBoolean()) 1f else -1f
        p.x = if (anywhere) (r.nextFloat() * 2f - 1f) * 30f else -dir * 34f
        p.vx = dir * (3f + r.nextFloat() * 3f)
        p.y = 16f + r.nextFloat() * 10f
        p.z = -40f - r.nextFloat() * 50f
        p.rate = 0.15f
        p.s = 2.4f
        p.life = 30f
        p.paint(0, 230f, 0.05f, 1f); p.paint(1, 215f, 0.7f, 0.95f)
    }

    override fun moveMote(p: Piece, dt: Float): Boolean = super.moveMote(p, dt) && kotlin.math.abs(p.x) < 40f

    override fun drawMote(d: BiomeDraw, p: Piece, fog: Float, time: Float) {
        d.add(satellite, p.x, p.y, p.z, p.s, time * 15f + p.seed * 360f, p.pal, fog * 0.6f, glow = 0.2f, onLand = false)
    }

    override fun drawSky(d: BiomeDraw, alpha: Float, time: Float) {
        d.add(BiomeToys.bigBall, -54f, 52f, -330f, 28f, 28f, 28f, time * 1.2f, planet, 0.12f, glow = 0.35f, pitch = 15f, onLand = false, alpha = alpha, bands = planetBands)
        d.add(ringShape, -54f, 52f, -330f, 58f, 58f, 58f, time * 2f, ringPal, 0.12f, glow = 0.35f, pitch = 14f, roll = -12f, onLand = false, alpha = alpha)
        d.add(BiomeToys.ball, 64f, 66f, -320f, 9f, 9f, 9f, time * 6f, earth, 0.05f, glow = 0.4f, onLand = false, alpha = alpha, bands = earthBands)
    }

    override fun paintSky(sky: SkyPainter, alpha: Float, time: Float) {
        for (i in 0 until STARS) {
            val tw = 0.6f + 0.4f * sin(time * 1.3f + stars[i * 4 + 3])
            val r = stars[i * 4 + 2]
            if (i % 9 == 0) sky.sparkle(stars[i * 4], stars[i * 4 + 1], r * 4f, starCol, alpha * tw)
            else sky.glow(stars[i * 4], stars[i * 4 + 1], r * 2f, starCol, alpha * tw * 0.85f)
        }
    }

    override val kerb = KERB_LIGHTS
    override fun kerbColors(a: Color, b: Color) { hsvInto(a, 235f, 0.25f, 0.7f); hsvInto(b, 185f, 0.7f, 1f) }
    override fun ground(out: Color, parity: Int) { hsvInto(out, 255f, 0.28f, if (parity == 0) 0.46f else 0.42f) }

    private companion object {
        const val STARS = 60
        val HILL = floatArrayOf(0.3f)
    }
}
