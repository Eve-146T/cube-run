package cube.run.game.world.biome

import com.badlogic.gdx.graphics.Color
import cube.run.core.gfx.FacetShapes
import cube.run.core.hsvInto
import cube.run.data.Worlds
import kotlin.math.sin
import kotlin.random.Random

/**
 * Candy Fields: a mint-frosted land of giant lollipops, ice creams and
 * cupcakes under frosted gumdrop hills and cupcake mountains, cotton-candy
 * clouds drifting over, a
 * rainbow or a giant donut coming past, balloons rising from the fields.
 * The kerb is a candy-cane stripe.
 */
class CandyLook(world: Worlds.World) : BiomeLook(world) {

    private val lollipop = toy {
        block(0f, 0f, 0f, 0.14f, 1f, 0.14f, 2)                 // the stick
        pixels(listOf(".000.", "01110", "01210", "01110", ".000."), 0.2f, 0.16f, y0 = 0.98f) // the swirl
    }
    private val cone = toy {
        for (i in 0 until 4) block(0f, i * 0.2f, 0f, 0.16f + i * 0.1f, 0.2f, 0.16f + i * 0.1f, 2) // the waffle cone
        block(0f, 0.8f, 0f, 0.62f, 0.12f, 0.62f, 0)              // a drip ledge
        block(0f, 0.92f, 0f, 0.5f, 0.32f, 0.5f, 0)                // the first scoop
        block(0f, 1.24f, 0f, 0.42f, 0.1f, 0.42f, 1)
        block(0f, 1.34f, 0f, 0.36f, 0.26f, 0.36f, 1)              // the second scoop
        block(0f, 1.6f, 0f, 0.12f, 0.12f, 0.12f, 0)               // a cherry
    }
    private val cupcake = toy {
        steps(0f, 0f, 0f, 3, 0.62f, 0.74f, 0.42f) { 2 }           // the wrapper
        steps(0f, 0.42f, 0f, 4, 0.84f, 0.2f, 0.64f) { 0 }         // the frosting swirl
        block(0f, 1.06f, 0f, 0.16f, 0.16f, 0.16f, 1)              // the cherry
    }
    private val cane = toy {
        for (i in 0 until 7) block(0f, i * 0.16f, 0f, 0.14f, 0.16f, 0.14f, if (i % 2 == 0) 0 else 1)
        block(0.14f, 1.12f - 0.14f, 0f, 0.14f, 0.14f, 0.14f, 1)
        block(0.28f, 1.12f - 0.14f, 0f, 0.14f, 0.14f, 0.14f, 0)
        block(0.28f, 1.12f - 0.28f, 0f, 0.14f, 0.14f, 0.14f, 1)
    }
    private val gumdrops = toy {
        steps(0f, 0f, 0f, 3, 1f, 0.5f, 0.6f) { 0 }
        steps(0.7f, 0f, 0.15f, 2, 0.4f, 0.24f, 0.36f) { 1 }
        steps(-0.62f, 0f, -0.2f, 2, 0.34f, 0.2f, 0.3f) { 2 }
    }
    private val donut = FacetShapes.voxelRing(12, 0.42f, 2)
    private val rainbow = toy { pixels(BiomeToys.arcRows(30, 0.62f, 3), 1f / 15f, 0.12f) }
    private val balloon = toy {
        block(0f, 0f, 0f, 0.02f, 0.9f, 0.02f, 2)                  // the string
        block(0f, 0.9f, 0f, 0.1f, 0.08f, 0.1f, 0)                 // the knot
        block(0f, 0.98f, 0f, 0.42f, 0.5f, 0.42f, 0)               // the balloon
        block(0f, 1.48f, 0f, 0.28f, 0.1f, 0.28f, 0)
    }

    override val shapes = listOf(lollipop, cone, cupcake, cane, gumdrops, donut, rainbow, balloon, BiomeToys.ball, BiomeToys.bigBall, BiomeToys.slab)

    private val skyPal = arrayOf(Color(), Color(), Color())
    private val donutPal = arrayOf(Color(), Color(), Color())

    init {
        hsvInto(donutPal[0], 32f, 0.5f, 0.95f); hsvInto(donutPal[1], 325f, 0.55f, 1f); hsvInto(donutPal[2], 50f, 0.6f, 1f)
    }

    private fun candy(r: Random): Float = CANDY[r.nextInt(CANDY.size)] + (r.nextFloat() - 0.5f) * 16f

    override val propGap = 6.5f

    // a channel of strawberry milk along the road, frothing white
    override val stream = true
    override fun streamX(wind: Float) = 0.95f + wind * 0.25f
    override val streamWidth = 1.3f
    private val milk = Color().also { hsvInto(it, 338f, 0.38f, 1f) }

    override fun drawStream(d: BiomeDraw, p: Piece, fog: Float, time: Float) {
        p.pal[0].set(milk).lerp(Color.WHITE, (0.5f + 0.5f * sin(time * 2f + p.z * 0.45f)) * 0.45f)
        d.add(BiomeToys.slab, p.x, 0f, p.z, p.s / 2f, 0.05f, BiomeScene.STREAM_STEP / 2f, 0f, p.pal, fog, glow = 0.3f)
    }

    override fun seedProp(p: Piece, r: Random) {
        p.kind = r.nextInt(6)
        p.yaw = (r.nextFloat() - 0.5f) * 40f
        val h = candy(r)
        p.paint(0, h, 0.55f, 1f); p.paint(1, h + 140f + r.nextFloat() * 60f, 0.6f, 1f); p.paint(2, 0f, 0f, 1f)
        when (p.kind) {
            0 -> { p.s = 4f + r.nextFloat() * 3f; p.yaw = 0f }                  // lollipop: faces the road
            1 -> { p.s = 3.2f + r.nextFloat() * 2f; p.paint(2, 34f, 0.55f, 0.95f) } // ice cream
            2 -> { p.s = 3f + r.nextFloat() * 2f; p.paint(2, h + 180f, 0.45f, 1f) } // cupcake
            3 -> { p.s = 4f + r.nextFloat() * 2f; p.paint(0, 0f, 0f, 1f); p.paint(1, 350f, 0.75f, 1f); p.yaw = if (r.nextBoolean()) 0f else 180f }
            4 -> { p.s = 3f + r.nextFloat() * 2.5f; p.paint(2, h + 70f, 0.55f, 1f) } // gumdrops
            else -> { p.s = 2.2f + r.nextFloat() * 1.2f; p.yaw = (r.nextFloat() - 0.5f) * 30f } // donut, on its edge
        }
        p.x = clear(p.s * when (p.kind) { 0 -> 0.55f; 1 -> 0.35f; 2 -> 0.45f; 3 -> 0.4f; 4 -> 0.95f; else -> 1f }, r, 12f)
    }

    override fun drawProp(d: BiomeDraw, p: Piece, fog: Float, time: Float) {
        when (p.kind) {
            0 -> d.add(lollipop, p.x, 0f, p.z, p.s, p.yaw, p.pal, fog)
            1 -> d.add(cone, p.x, 0f, p.z, p.s, p.yaw + time * 10f, p.pal, fog)
            2 -> d.add(cupcake, p.x, 0f, p.z, p.s, p.yaw, p.pal, fog)
            3 -> d.add(cane, p.x, 0f, p.z, p.s, p.yaw, p.pal, fog)
            4 -> d.add(gumdrops, p.x, 0f, p.z, p.s, p.yaw, p.pal, fog)
            else -> d.add(donut, p.x, p.s, p.z, p.s, p.s, p.s, p.yaw, donutPal, fog, pitch = 90f)
        }
    }

    override fun seedFar(p: Piece, r: Random) {
        p.kind = r.nextInt(3)
        p.x = BiomeToys.farX(p, r, 0.07f, 0.3f)
        p.s = 40f + r.nextFloat() * 35f
        p.yaw = r.nextFloat() * 40f
        val h = candy(r)
        p.paint(0, h, 0.62f, 0.95f); p.paint(1, 0f, 0f, 1f); p.paint(2, h + 150f, 0.55f, 1f) // a frosted gumdrop hill
        if (p.kind == 2) { p.paint(0, 330f, 0.45f, 1f); p.paint(1, 355f, 0.8f, 1f); p.paint(2, 48f, 0.5f, 1f) } // a cupcake mountain
    }

    override val horizonTall = 1.6f

    override fun drawFar(d: BiomeDraw, p: Piece, haze: Float, time: Float) {
        when (p.kind) {
            2 -> d.add(cupcake, p.x, p.y - 8f, p.z, p.s, p.s * 0.8f, p.s, p.yaw, p.pal, haze * 0.35f, onLand = false)
            else -> d.add(BiomeToys.bigBall, p.x, p.y - p.s * 0.3f, p.z, p.s * 0.75f, p.s * 0.6f, p.s * 0.75f, p.yaw, p.pal, haze * 0.35f,
                onLand = false, bands = FROSTING)
        }
    }

    override val landmarkKinds = 3

    override fun seedLandmark(p: Piece, r: Random) {
        when (p.kind) {
            0 -> { p.s = 70f; p.x = 22f; p.rate = 0.4f }                    // a rainbow over the road: you drive under it
            1 -> { p.s = 17f; p.x = 0.14f * 340f; p.rate = 0.4f; p.y = 17f } // a giant donut
            else -> { p.s = 20f; p.x = 0.14f * 340f; p.rate = 0.4f; p.paint(0, candy(r), 0.55f, 1f); p.paint(1, candy(r) + 150f, 0.6f, 1f); p.paint(2, 0f, 0f, 1f) }
        }
    }

    override fun landmarkReach(p: Piece) = if (p.kind == 0) p.s else p.s * 0.8f
    override fun landmarkOverRoad(p: Piece) = p.kind == 0

    override fun drawLandmark(d: BiomeDraw, p: Piece, haze: Float, time: Float) {
        when (p.kind) {
            0 -> { hsvInto(skyPal[0], 345f, 0.6f, 1f); hsvInto(skyPal[1], 50f, 0.65f, 1f); hsvInto(skyPal[2], 185f, 0.55f, 1f)
                d.add(rainbow, p.x, -8f, p.z, p.s, 0f, skyPal, haze, onLand = false) }
            1 -> d.add(donut, p.x, p.y, p.z, p.s, p.s, p.s, time * 12f, donutPal, haze, pitch = 90f, onLand = false)
            else -> d.add(lollipop, p.x, -6f, p.z, p.s, sin(time * 0.6f) * 12f, p.pal, haze, onLand = false)
        }
    }

    override val motes = 26

    override fun seedMote(p: Piece, r: Random, anywhere: Boolean) {
        p.x = (if (r.nextBoolean()) 1f else -1f) * (6f + r.nextFloat() * 24f)
        p.z = if (anywhere) -100f + r.nextFloat() * 95f else -102f - r.nextFloat() * 12f // floating in out of the haze
        p.y = r.nextFloat() * 20f
        p.vy = 1.4f + r.nextFloat() * 1.6f
        p.s = 1.8f + r.nextFloat() * 1.2f
        p.life = (32f - p.y) / p.vy
        p.paint(0, candy(r), 0.6f, 1f); p.paint(2, 0f, 0f, 1f)
    }

    override fun drawMote(d: BiomeDraw, p: Piece, fog: Float, time: Float) {
        val radius = balloon.radius * p.s
        if (!d.visible(p.x, p.y, p.z, radius + .4f, radius, radius)) return
        val sway = sin(time * 1.3f + p.seed * 9f)
        d.add(balloon, p.x + sway * 0.4f, p.y, p.z, p.s, p.s, p.s, p.seed * 90f, p.pal, fog, roll = sway * 8f, onLand = false,
            alpha = (p.life / 2f).coerceAtMost(1f), preculled = true)
    }

    /** Cotton-candy clouds drifting across the sky: three puffs each, pink underneath. */
    override fun drawSky(d: BiomeDraw, alpha: Float, time: Float) {
        hsvInto(skyPal[0], 330f, 0.3f, 1f); hsvInto(skyPal[1], 0f, 0f, 1f)
        for (i in 0 until CLOUDS) {
            val z = -230f - i * 25f
            val span = 0.6f * (BiomeScene.CAMERA_Z - z)
            val x = ((i * 0.37f + time * (0.006f + i * 0.0015f)) % 1f) * 2f * span - span
            val y = 34f + (i * 13 % 5) * 7f
            val s = 5.5f + (i * 7 % 4) * 1.6f
            for (k in -1..1) {
                val ps = s * (if (k == 0) 1.25f else 0.85f)
                d.add(BiomeToys.ball, x + k * s * 1.5f, y + (if (k == 0) s * 0.35f else 0f), z, ps * 1.2f, ps, ps, i * 20f + k * 30f, skyPal, 0.1f,
                    onLand = false, alpha = alpha, bands = PUFF)
            }
        }
    }

    override val kerb = KERB_STRIPES
    override fun kerbColors(a: Color, b: Color) { a.set(1f, 1f, 1f, 1f); hsvInto(b, 345f, 0.7f, 1f) }
    override fun ground(out: Color, parity: Int) { hsvInto(out, 150f, 0.42f, if (parity == 0) 0.8f else 0.74f) }

    private companion object {
        const val CLOUDS = 5
        val FROSTING = floatArrayOf(0.7f)
        val PUFF = floatArrayOf(-0.35f)
        /** Pink, lemon, mint, sky, lilac, peach. */
        val CANDY = floatArrayOf(330f, 52f, 150f, 195f, 275f, 18f)
    }
}
