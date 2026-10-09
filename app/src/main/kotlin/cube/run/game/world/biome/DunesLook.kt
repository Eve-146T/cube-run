package cube.run.game.world.biome

import com.badlogic.gdx.graphics.Color
import cube.run.core.hsvInto
import cube.run.data.Worlds
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sunset Dunes: the road runs straight into a huge striped setting sun.
 * Cacti, striped mesas, palm oases and dunes on the sand, pyramids and dune
 * ridges on the horizon, tumbleweeds bowling past and birds circling high.
 * A great pyramid, a rock arch or a giant cactus comes by. The road's edge
 * is a sandbank.
 */
class DunesLook(world: Worlds.World) : BiomeLook(world) {

    private val cactus = toy {
        block(0f, 0f, 0f, 0.22f, 1.3f, 0.22f, 0)
        block(-0.21f, 0.5f, 0f, 0.2f, 0.14f, 0.14f, 0); block(-0.36f, 0.5f, 0f, 0.14f, 0.5f, 0.14f, 0)
        block(0.21f, 0.72f, 0f, 0.2f, 0.14f, 0.14f, 0); block(0.36f, 0.72f, 0f, 0.14f, 0.38f, 0.14f, 0)
        block(0f, 1.3f, 0f, 0.14f, 0.1f, 0.14f, 1) // a flower on top
        block(-0.36f, 1f, 0f, 0.1f, 0.08f, 0.1f, 1)
    }
    private val mesa = toy {
        val widths = floatArrayOf(1f, 0.92f, 0.86f, 0.8f)
        for ((i, w) in widths.withIndex()) block(0f, i * 0.16f, 0f, w, 0.16f, w * 0.6f, if (i % 2 == 0) 0 else 1)
        block(0f, 0.64f, 0f, 0.84f, 0.06f, 0.5f, 2)
    }
    private val palm = toy {
        for (i in 0 until 6) block(i * 0.035f, i * 0.2f, 0f, 0.12f, 0.2f, 0.12f, 2) // a leaning trunk
        val top = 1.2f; val x = 6 * 0.035f
        block(x, top, 0f, 0.2f, 0.12f, 0.2f, 0)
        block(x + 0.3f, top - 0.04f, 0f, 0.4f, 0.08f, 0.16f, 0); block(x - 0.3f, top - 0.04f, 0f, 0.4f, 0.08f, 0.16f, 0)
        block(x, top - 0.04f, 0.3f, 0.16f, 0.08f, 0.4f, 0); block(x, top - 0.04f, -0.3f, 0.16f, 0.08f, 0.4f, 0)
        block(x + 0.56f, top - 0.16f, 0f, 0.12f, 0.12f, 0.14f, 0); block(x - 0.56f, top - 0.16f, 0f, 0.12f, 0.12f, 0.14f, 0)
        block(x + 0.08f, top - 0.1f, 0.12f, 0.08f, 0.08f, 0.08f, 2) // coconuts
        block(-0.1f, 0f, 0.55f, 0.9f, 0.04f, 0.5f, 1) // a little pool
    }
    private val bush = BiomeToys.cluster(31, 5, 0.7f)
    private val pyramid = BiomeToys.peak(8, 1)
    private val arch = toy { pixels(BiomeToys.arcRows(22, 0.55f, 3), 1f / 11f, 0.22f) }
    private val bird = toy { pixels(listOf("0.....0", ".0...0.", "..000.."), 0.14f, 0.05f) }
    private val weed = BiomeToys.cluster(47, 6, 1.4f)

    override val shapes = listOf(cactus, mesa, palm, bush, pyramid, arch, bird, weed, BiomeToys.bigBall, BiomeToys.ball)

    private val sun = arrayOf(Color(), Color(), Color())
    private val sunBands = floatArrayOf(0.12f, 0.45f)
    private val rayCol = Color()
    private val sand = arrayOf(Color(), Color(), Color())
    private val weedPal = arrayOf(Color(), Color())
    private val birdPal = arrayOf(Color())

    init {
        hsvInto(sun[0], 335f, 0.55f, 1f); hsvInto(sun[1], 22f, 0.75f, 1f); hsvInto(sun[2], 50f, 0.65f, 1f)
        hsvInto(rayCol, 40f, 0.55f, 1f)
        hsvInto(weedPal[0], 34f, 0.5f, 0.75f); hsvInto(weedPal[1], 28f, 0.55f, 0.6f)
        hsvInto(birdPal[0], 290f, 0.5f, 0.35f)
    }

    override val propGap = 6.5f

    override fun seedProp(p: Piece, r: Random) {
        val roll = r.nextFloat()
        p.kind = when { roll < 0.35f -> 0; roll < 0.55f -> 1; roll < 0.7f -> 2; roll < 0.85f -> 3; else -> 4 }
        p.yaw = (r.nextFloat() - 0.5f) * 60f
        when (p.kind) {
            0 -> { p.s = 4f + r.nextFloat() * 3f; p.paint(0, 120f + r.nextFloat() * 30f, 0.62f, 0.7f); p.paint(1, 315f + r.nextFloat() * 40f, 0.7f, 1f) }
            1 -> { p.s = 4f + r.nextFloat() * 3f; p.paint(0, 30f, 0.56f, 0.9f); p.paint(1, 40f, 0.42f, 1f) }   // a dune
            2 -> { p.s = 5f + r.nextFloat() * 5f; p.paint(0, 14f + r.nextFloat() * 10f, 0.72f, 0.95f); p.paint(1, 32f, 0.6f, 1f); p.paint(2, 120f, 0.5f, 0.75f) }
            3 -> { p.s = 4f + r.nextFloat() * 2f; p.paint(0, 105f + r.nextFloat() * 30f, 0.65f, 0.75f); p.paint(1, 190f, 0.7f, 1f); p.paint(2, 28f, 0.5f, 0.7f) }
            else -> { p.s = 2f + r.nextFloat() * 1.5f; p.paint(0, 95f, 0.5f, 0.7f); p.paint(1, 330f, 0.65f, 1f) }
        }
        p.x = clear(p.s * when (p.kind) { 0 -> 0.45f; 1 -> 1.4f; 2 -> 0.55f; 3 -> 0.75f; else -> 0.7f }, r, 12f)
    }

    override fun drawProp(d: BiomeDraw, p: Piece, fog: Float, time: Float) {
        when (p.kind) {
            0 -> d.add(cactus, p.x, 0f, p.z, p.s, p.yaw, p.pal, fog)
            1 -> d.add(BiomeToys.ball, p.x, -p.s * 0.3f, p.z, p.s * 1.4f, p.s * 0.55f, p.s, p.yaw, p.pal, fog, bands = DUNE)
            2 -> d.add(mesa, p.x, 0f, p.z, p.s, p.s * 1.3f, p.s, p.yaw, p.pal, fog)
            3 -> d.add(palm, p.x, 0f, p.z, p.s, p.yaw, p.pal, fog)
            else -> d.add(bush, p.x, 0f, p.z, p.s, p.yaw, p.pal, fog)
        }
    }

    override fun seedFar(p: Piece, r: Random) {
        p.kind = if (r.nextFloat() < 0.6f) 0 else 1
        p.x = BiomeToys.farX(p, r, 0.15f, 0.34f) // the middle of the horizon is the sun's
        p.s = if (p.kind == 0) 70f + r.nextFloat() * 50f else 40f + r.nextFloat() * 30f
        p.yaw = r.nextFloat() * 30f
        p.paint(0, 26f + r.nextFloat() * 10f, 0.6f, 0.88f); p.paint(1, 38f, 0.45f, 1f); p.paint(2, 48f, 0.75f, 1f)
        if (p.kind == 1) { p.paint(0, 36f, 0.5f, 0.95f); p.paint(1, 48f, 0.8f, 1f) }
    }

    override fun drawFar(d: BiomeDraw, p: Piece, haze: Float, time: Float) {
        if (p.kind == 0) d.add(BiomeToys.bigBall, p.x, p.y - p.s * 0.22f, p.z, p.s, p.s * 0.34f, p.s * 0.7f, p.yaw, p.pal, haze, onLand = false, bands = DUNE)
        else d.add(pyramid, p.x, p.y - 6f, p.z, p.s, p.s * 0.62f, p.s, 45f + p.yaw, p.pal, haze, onLand = false)
    }

    override val landmarkKinds = 3

    override fun seedLandmark(p: Piece, r: Random) {
        p.x = 0.24f * 300f; p.rate = 0.45f
        when (p.kind) {
            0 -> { p.s = 80f; p.paint(0, 36f, 0.5f, 1f); p.paint(1, 50f, 0.8f, 1f) }
            1 -> { p.s = 54f; p.paint(0, 10f, 0.72f, 0.95f); p.paint(1, 22f, 0.7f, 1f); p.paint(2, 34f, 0.6f, 1f) }
            else -> { p.s = 22f; p.paint(0, 128f, 0.62f, 0.72f); p.paint(1, 330f, 0.7f, 1f) }
        }
    }

    override fun landmarkReach(p: Piece) = p.s * 0.6f

    override fun drawLandmark(d: BiomeDraw, p: Piece, haze: Float, time: Float) {
        when (p.kind) {
            0 -> d.add(pyramid, p.x, -6f, p.z, p.s, p.s * 0.65f, p.s, 45f, p.pal, haze, onLand = false)
            1 -> d.add(arch, p.x, -8f, p.z, p.s, if (p.x > 0f) -15f else 15f, p.pal, haze, onLand = false)
            else -> d.add(cactus, p.x, -4f, p.z, p.s, 0f, p.pal, haze, onLand = false)
        }
    }

    override val motes = 12

    override fun seedMote(p: Piece, r: Random, anywhere: Boolean) {
        if (r.nextFloat() < 0.35f) { // a bird circling high over the sand
            p.kind = 1
            p.x = (if (r.nextBoolean()) 1f else -1f) * (8f + r.nextFloat() * 10f)
            p.y = 16f + r.nextFloat() * 8f
            p.z = if (anywhere) -90f + r.nextFloat() * 70f else -110f
            p.s = 3f
            p.life = 40f
        } else { // a tumbleweed bowling along the sand, faster than the road
            p.kind = 0
            p.x = (if (r.nextBoolean()) 1f else -1f) * (4.5f + r.nextFloat() * 14f)
            p.y = 0f
            p.z = if (anywhere) -100f + r.nextFloat() * 90f else -112f
            p.vz = 5f + r.nextFloat() * 6f
            p.vx = (if (p.x > 0f) 1f else -1f) * r.nextFloat() * 0.8f // drifting away from the road
            p.s = 0.8f + r.nextFloat() * 0.5f
            p.life = 40f
        }
    }

    override fun drawMote(d: BiomeDraw, p: Piece, fog: Float, time: Float) {
        if (p.kind == 1) {
            val a = time * 0.5f + p.seed * 6.28f
            val flap = 0.6f + 0.4f * abs(sin(time * 5f + p.seed * 10f))
            d.add(bird, p.x + cos(a) * 6f, p.y, p.z + sin(a) * 6f, p.s, p.s * flap, p.s, -a * 57.3f, birdPal, fog * 0.7f, onLand = false)
        } else {
            val hop = abs(sin(time * 4f + p.seed * 9f)) * 0.8f
            d.add(weed, p.x, hop, p.z, p.s, p.s, p.s, p.seed * 90f, weedPal, fog, pitch = time * 260f + p.seed * 360f)
        }
    }

    override fun drawSky(d: BiomeDraw, alpha: Float, time: Float) {
        d.add(BiomeToys.bigBall, 0f, 4f, -330f, 44f, 44f, 44f, time * 1.5f, sun, 0f, glow = 1f, onLand = false, alpha = alpha, bands = sunBands)
    }

    override fun skyRays(sky: SkyPainter, alpha: Float, time: Float) {
        sky.rays(0f, 4f, -376f, 230f, 18, time * 2.5f, rayCol, 0.3f * alpha, 0.45f)
    }

    override val kerb = KERB_BANK
    override fun kerbColors(a: Color, b: Color) { hsvInto(a, 40f, 0.42f, 1f); hsvInto(b, 36f, 0.48f, 0.96f) }
    override fun ground(out: Color, parity: Int) { hsvInto(out, 36f, 0.52f, if (parity == 0) 0.92f else 0.87f) }

    private companion object {
        /** Dunes: the shaded sand below, the lit crest on top. */
        val DUNE = floatArrayOf(0.35f)
    }
}
