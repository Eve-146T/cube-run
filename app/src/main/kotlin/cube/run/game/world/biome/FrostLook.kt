package cube.run.game.world.biome

import com.badlogic.gdx.graphics.Color
import cube.run.core.hsvInto
import cube.run.data.Worlds
import kotlin.math.sin
import kotlin.random.Random

/**
 * Frost Peaks: a snowfield of snowy pines, snowmen, ice crystals and
 * igloos under great snow-capped mountains and a pale winter sun, snow
 * falling all the while. An ice castle, a giant snowman or an ice arch
 * comes past. The road's edge is a snowbank.
 */
class FrostLook(world: Worlds.World) : BiomeLook(world) {

    private val pine = toy {
        block(0f, 0f, 0f, 0.16f, 0.25f, 0.16f, 2)
        val tiers = floatArrayOf(0.9f, 0.68f, 0.46f, 0.24f)
        var y = 0.25f
        for (w in tiers) {
            block(0f, y, 0f, w, 0.24f, w, 0)
            block(0f, y + 0.24f, 0f, w * 0.8f, 0.06f, w * 0.8f, 1) // snow on each tier
            y += 0.3f
        }
    }
    private val snowman = toy {
        block(0f, 0f, 0f, 0.7f, 0.6f, 0.7f, 0)
        block(0f, 0.6f, 0f, 0.5f, 0.44f, 0.5f, 0)
        block(0f, 1.04f, 0f, 0.6f, 0.08f, 0.6f, 1)    // the scarf
        block(0f, 1.12f, 0f, 0.38f, 0.34f, 0.38f, 0)  // the head
        block(0f, 1.24f, 0.31f, 0.08f, 0.08f, 0.24f, 2) // the carrot
        block(0f, 1.46f, 0f, 0.46f, 0.04f, 0.46f, 1)  // the hat's brim
        block(0f, 1.5f, 0f, 0.3f, 0.26f, 0.3f, 1)     // the hat
    }
    private val ice = toy {
        steps(0f, 0f, 0f, 3, 0.36f, 0.12f, 1.4f) { if (it == 2) 1 else 0 }
        steps(0.32f, 0f, 0.1f, 3, 0.26f, 0.08f, 0.9f) { if (it == 2) 1 else 0 }
        steps(-0.3f, 0f, -0.08f, 3, 0.2f, 0.08f, 0.55f) { if (it == 2) 1 else 0 }
    }
    private val rock = BiomeToys.cluster(5, 5, 0.9f)
    private val igloo = toy {
        steps(0f, 0f, 0f, 4, 1f, 0.4f, 0.6f) { 0 }
        block(0f, 0f, 0.5f + 0.13f, 0.3f, 0.3f, 0.26f, 0) // the entrance
        block(0f, 0f, 0.5f + 0.26f + 0.01f, 0.18f, 0.2f, 0.02f, 1)
    }
    private val mountain = BiomeToys.peak(8, 3)
    private val castle = toy {
        block(0f, 0f, 0f, 1f, 0.36f, 0.3f, 0) // the wall
        for (i in 0 until 5) block(-0.4f + i * 0.2f, 0.36f, 0f, 0.1f, 0.08f, 0.3f, 0) // battlements
        for (sx in floatArrayOf(-0.62f, 0.62f)) {
            block(sx, 0f, 0f, 0.24f, 0.7f, 0.24f, 0)
            steps(sx, 0.7f, 0f, 3, 0.3f, 0.08f, 0.3f) { 1 }
        }
        block(0f, 0.44f, 0f, 0.3f, 0.5f, 0.3f, 0)
        steps(0f, 0.94f, 0f, 4, 0.36f, 0.06f, 0.4f) { 1 }
        block(0f, 0f, 0.15f + 0.01f, 0.2f, 0.24f, 0.02f, 2) // the gate
    }
    private val arch = toy { pixels(BiomeToys.arcRows(24, 0.7f, 2), 1f / 12f, 0.18f) }
    private val flake = BiomeToys.speck

    override val shapes = listOf(pine, snowman, ice, rock, igloo, mountain, castle, arch, flake, BiomeToys.ball, BiomeToys.bigBall)

    private val snow = arrayOf(Color(), Color(), Color())
    private val sunCol = Color()

    init {
        hsvInto(snow[0], 205f, 0.06f, 1f); hsvInto(snow[1], 195f, 0.35f, 1f); hsvInto(snow[2], 25f, 0.8f, 1f)
        hsvInto(sunCol, 50f, 0.15f, 1f)
    }

    override val propGap = 5.5f

    override fun seedProp(p: Piece, r: Random) {
        val roll = r.nextFloat()
        p.kind = when { roll < 0.45f -> 0; roll < 0.6f -> 1; roll < 0.75f -> 2; roll < 0.9f -> 3; else -> 4 }
        p.yaw = r.nextFloat() * 90f
        when (p.kind) {
            0 -> { p.s = 4f + r.nextFloat() * 4f; p.paint(0, 145f + r.nextFloat() * 25f, 0.62f, 0.62f + r.nextFloat() * 0.15f); p.paint(1, 205f, 0.04f, 1f); p.paint(2, 22f, 0.55f, 0.6f) }
            1 -> { p.s = 2.2f + r.nextFloat() * 0.8f; p.yaw = 30f; p.paint(0, 205f, 0.05f, 1f); p.paint(1, SCARF[r.nextInt(SCARF.size)], 0.75f, 0.95f); p.paint(2, 25f, 0.85f, 1f) }
            2 -> { p.s = 2.5f + r.nextFloat() * 2.5f; p.paint(0, 185f + r.nextFloat() * 25f, 0.45f, 1f); p.paint(1, 200f, 0.12f, 1f) }
            3 -> { p.s = 2.6f + r.nextFloat() * 2f; p.paint(0, 220f, 0.22f, 0.8f); p.paint(1, 205f, 0.04f, 1f) }
            else -> { p.s = 3.4f + r.nextFloat() * 1.2f; p.yaw = 35f; p.paint(0, 200f, 0.1f, 1f); p.paint(1, 215f, 0.45f, 0.75f) }
        }
        p.x = clear(p.s * when (p.kind) { 0 -> 0.45f; 1 -> 0.35f; 2 -> 0.5f; 3 -> 0.75f; else -> 0.6f }, r, 12f)
    }

    override fun drawProp(d: BiomeDraw, p: Piece, fog: Float, time: Float) {
        when (p.kind) {
            0 -> d.add(pine, p.x, 0f, p.z, p.s, p.yaw, p.pal, fog)
            1 -> d.add(snowman, p.x, 0f, p.z, p.s, toRoad(p), p.pal, fog)
            2 -> d.add(ice, p.x, 0f, p.z, p.s, p.yaw, p.pal, fog, glow = 0.25f)
            3 -> d.add(rock, p.x, 0f, p.z, p.s * 1.3f, p.s, p.s, p.yaw, p.pal, fog)
            else -> d.add(igloo, p.x, 0f, p.z, p.s, toRoad(p), p.pal, fog)
        }
    }

    override fun seedFar(p: Piece, r: Random) {
        p.x = BiomeToys.farX(p, r, 0.05f, 0.3f)
        p.s = 70f + r.nextFloat() * 70f
        p.yaw = r.nextFloat() * 45f
        p.paint(0, 220f + r.nextFloat() * 30f, 0.42f, 0.72f + r.nextFloat() * 0.14f); p.paint(1, 205f, 0.03f, 1f); p.paint(2, 0f, 0f, 1f)
    }

    override fun drawFar(d: BiomeDraw, p: Piece, haze: Float, time: Float) {
        d.add(mountain, p.x, p.y - 6f, p.z, p.s, p.s * (0.85f + p.seed * 0.5f), p.s, p.yaw, p.pal, haze * 0.6f, onLand = false)
    }

    override val landmarkKinds = 3

    override fun seedLandmark(p: Piece, r: Random) {
        p.x = 0.17f * 300f; p.rate = 0.45f
        when (p.kind) {
            0 -> { p.s = 48f; p.paint(0, 195f, 0.25f, 1f); p.paint(1, 215f, 0.5f, 0.95f); p.paint(2, 210f, 0.6f, 0.6f) }
            1 -> { p.s = 26f; p.paint(0, 205f, 0.05f, 1f); p.paint(1, SCARF[r.nextInt(SCARF.size)], 0.75f, 0.95f); p.paint(2, 25f, 0.85f, 1f) }
            else -> { p.s = 70f; p.paint(0, 190f, 0.35f, 1f); p.paint(1, 205f, 0.1f, 1f) }
        }
    }

    override fun landmarkReach(p: Piece) = if (p.kind == 1) p.s * 0.5f else p.s

    override fun drawLandmark(d: BiomeDraw, p: Piece, haze: Float, time: Float) {
        when (p.kind) {
            0 -> d.add(castle, p.x, -4f, p.z, p.s, if (p.x > 0f) -20f else 20f, p.pal, haze, glow = 0.1f, onLand = false)
            1 -> d.add(snowman, p.x, -4f, p.z, p.s, if (p.x > 0f) -25f else 25f, p.pal, haze, onLand = false)
            else -> d.add(arch, p.x, -10f, p.z, p.s, 0f, p.pal, haze, glow = 0.2f, onLand = false)
        }
    }

    override val motes = 46

    override fun seedMote(p: Piece, r: Random, anywhere: Boolean) {
        p.x = (r.nextFloat() * 2f - 1f) * 22f
        p.z = -80f + r.nextFloat() * 85f
        p.y = if (anywhere) r.nextFloat() * 16f else 14f + r.nextFloat() * 3f
        p.vy = -(1.4f + r.nextFloat() * 1.4f)
        p.vx = 0.4f + r.nextFloat() * 0.8f
        p.rate = 0.9f
        p.s = 0.1f + r.nextFloat() * 0.09f
        p.life = 20f
        p.paint(0, 205f, 0.04f, 1f)
    }

    override fun moveMote(p: Piece, dt: Float): Boolean = super.moveMote(p, dt) && p.y > -0.3f

    override fun drawMote(d: BiomeDraw, p: Piece, fog: Float, time: Float) {
        d.add(flake, p.x + sin(time * 1.4f + p.seed * 12f) * 0.6f, p.y, p.z, p.s, p.s, p.s, time * 60f + p.seed * 360f, p.pal, fog,
            glow = 0.5f, pitch = 30f, onLand = false)
    }

    override fun drawSky(d: BiomeDraw, alpha: Float, time: Float) {
        d.add(BiomeToys.bigBall, 70f, 62f, -330f, 13f, 0f, SUN, 0.05f, glow = 1f, onLand = false, alpha = alpha)
    }

    override fun skyRays(sky: SkyPainter, alpha: Float, time: Float) {
        sky.rays(70f, 62f, -345f, 70f, 12, time * 3f, sunCol, 0.3f * alpha)
    }

    override val kerb = KERB_BANK
    override fun kerbColors(a: Color, b: Color) { hsvInto(a, 205f, 0.05f, 1f); hsvInto(b, 200f, 0.12f, 0.97f) }
    override fun ground(out: Color, parity: Int) { hsvInto(out, 208f, 0.13f, if (parity == 0) 0.96f else 0.91f) }

    private companion object {
        val SCARF = floatArrayOf(355f, 140f, 205f, 45f, 300f)
        val SUN = arrayOf(Color(1f, 0.98f, 0.86f, 1f))
    }
}
