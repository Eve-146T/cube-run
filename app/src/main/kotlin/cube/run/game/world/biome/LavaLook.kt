package cube.run.game.world.biome

import com.badlogic.gdx.graphics.Color
import cube.run.core.gfx.FacetShapes
import cube.run.core.hsvInto
import cube.run.data.Worlds
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lava Caves: glowing crystals, bubbling lava pools and smoking vents on a
 * dark red land, stalactites hanging from a cave roof you never see,
 * volcanoes on the horizon under a burning glow, embers rising all round.
 * An erupting volcano, a lava fall or a cave mouth comes past.
 */
class LavaLook(world: Worlds.World) : BiomeLook(world) {

    private val crystals = toy {
        steps(0f, 0f, 0f, 3, 0.34f, 0.14f, 1.3f) { if (it == 2) 1 else 0 }
        steps(0.3f, 0f, 0.12f, 3, 0.24f, 0.1f, 0.8f) { if (it == 2) 1 else 0 }
        steps(-0.28f, 0f, -0.1f, 3, 0.22f, 0.08f, 0.6f) { if (it == 2) 1 else 0 }
    }
    private val poolRim = toy {
        block(0f, 0f, -0.5f, 1.2f, 0.16f, 0.2f, 0); block(0f, 0f, 0.5f, 1.2f, 0.16f, 0.2f, 0)
        block(-0.5f, 0f, 0f, 0.2f, 0.16f, 0.8f, 0); block(0.5f, 0f, 0f, 0.2f, 0.16f, 0.8f, 0)
        block(0.68f, 0f, 0.3f, 0.16f, 0.26f, 0.2f, 2); block(-0.66f, 0f, -0.25f, 0.14f, 0.2f, 0.18f, 2)
    }
    private val poolLava = toy { block(0f, 0f, 0f, 0.8f, 0.1f, 0.8f, 1) }
    private val vent = toy {
        steps(0f, 0f, 0f, 3, 1f, 0.5f, 0.6f) { 0 }
        block(0f, 0.6f, 0f, 0.3f, 0.06f, 0.3f, 1)
    }
    private val stalactite = toy {
        block(0f, -0.3f, 0f, 0.6f, 0.3f, 0.6f, 0)
        block(0f, -0.7f, 0f, 0.4f, 0.4f, 0.4f, 0)
        block(0f, -1.1f, 0f, 0.22f, 0.4f, 0.22f, 0)
        block(0f, -1.3f, 0f, 0.1f, 0.2f, 0.1f, 1)
        block(0.45f, -0.25f, 0.1f, 0.3f, 0.25f, 0.3f, 0)
        block(0.45f, -0.6f, 0.1f, 0.14f, 0.35f, 0.14f, 1)
    }
    private val volcano = toy { // a stepped cone, its crater glowing, lava running down its face
        val n = 6
        val h = 0.45f / n // broad and low: a volcano, not a tower
        for (i in 0 until n) {
            val w = 1f - i * 0.15f
            block(0f, i * h, 0f, w, h, w, if (i == n - 1) 1 else 0)
            block((i % 2) * 0.06f - 0.03f, i * h, w / 2f + 0.006f, 0.11f, h, 0.012f, 2)
        }
    }
    private val spire = BiomeToys.peak(4, 1)
    private val cliff = toy {
        block(0f, 0f, 0f, 1f, 1f, 0.5f, 0)
        block(0.08f, 0.05f, 0.25f + 0.02f, 0.14f, 0.95f, 0.04f, 1) // the lava fall down its face
        block(0.08f, 0f, 0.25f + 0.04f + 0.15f, 0.5f, 0.04f, 0.3f, 1) // its pool
    }
    private val caveMouth = toy { pixels(BiomeToys.arcRows(26, 0.62f, 3), 1f / 13f, 0.3f) }
    private val ember = BiomeToys.cube
    private val clump = FacetShapes.clump(1)

    override val shapes = listOf(crystals, poolRim, poolLava, vent, stalactite, volcano, spire, cliff, caveMouth, ember, clump, BiomeToys.slab, BiomeToys.speck)

    private val hot = arrayOf(Color(), Color(), Color())
    private val glowCol = Color()
    private val rockPal = arrayOf(Color(), Color(), Color())

    init {
        hsvInto(hot[0], 30f, 0.9f, 1f); hsvInto(hot[1], 52f, 0.8f, 1f); hsvInto(hot[2], 12f, 0.95f, 1f)
        hsvInto(rockPal[0], 355f, 0.62f, 0.42f); hsvInto(rockPal[1], 40f, 0.85f, 1f); hsvInto(rockPal[2], 8f, 0.7f, 0.55f)
        hsvInto(glowCol, 22f, 0.9f, 1f)
    }

    override val propGap = 6f

    // a channel of lava along the road
    override val stream = true
    override fun streamX(wind: Float) = 0.95f + wind * 0.25f
    override val streamWidth = 1.3f

    override fun drawStream(d: BiomeDraw, p: Piece, fog: Float, time: Float) {
        p.pal[0].set(hot[0]).lerp(hot[1], 0.5f + 0.5f * sin(time * 3f + p.z * 0.5f)) // a glow rolling down the river
        d.add(BiomeToys.slab, p.x, 0f, p.z, p.s / 2f, 0.05f, BiomeScene.STREAM_STEP / 2f, 0f, p.pal, fog, glow = 1f)
    }

    override fun seedProp(p: Piece, r: Random) {
        val roll = r.nextFloat()
        p.kind = when { roll < 0.07f -> 4; roll < 0.37f -> 0; roll < 0.67f -> 1; roll < 0.86f -> 2; else -> 3 }
        p.yaw = r.nextFloat() * 90f
        p.paint(0, 355f + r.nextFloat() * 15f, 0.65f, 0.45f + r.nextFloat() * 0.1f)
        p.paint(1, 28f + r.nextFloat() * 25f, 0.85f, 1f); p.paint(2, 10f, 0.75f, 0.62f)
        when (p.kind) {
            0 -> { p.s = 2.6f + r.nextFloat() * 2.2f; p.x = clear(p.s * 0.6f, r, 12f); p.paint(0, 18f + r.nextFloat() * 20f, 0.85f, 1f); p.paint(1, 52f, 0.6f, 1f) }
            1 -> { p.s = 4f + r.nextFloat() * 3f; p.x = clear(p.s * 0.85f, r, 12f) }
            2 -> { p.s = 1.6f + r.nextFloat() * 1.4f; p.x = clear(p.s * 1.3f, r, 12f) }
            3 -> { p.s = 3f + r.nextFloat() * 2f; p.x = clear(p.s * 0.5f, r, 12f) }
            else -> { p.s = 3f + r.nextFloat() * 2.5f; p.x = 0.5f + r.nextFloat() * 20f; p.y = 18f + r.nextFloat() * 5f + (if (p.x < 4f) 4f else 0f) // high over the road
                p.paint(0, 14f + r.nextFloat() * 14f, 0.8f, 0.85f); p.paint(1, 48f, 0.85f, 1f) }
        }
    }

    override fun drawProp(d: BiomeDraw, p: Piece, fog: Float, time: Float) {
        when (p.kind) {
            0 -> d.add(crystals, p.x, 0f, p.z, p.s, p.yaw, p.pal, fog, glow = 0.3f)
            1 -> {
                d.add(poolRim, p.x, 0f, p.z, p.s, p.s, p.s, p.yaw, p.pal, fog)
                d.add(poolLava, p.x, 0f, p.z, p.s, p.s, p.s, p.yaw, hot, fog, glow = 0.85f + 0.15f * sin(time * 3f + p.seed * 9f))
                bubble(d, p, p.s * 0.1f, fog, time, 0.35f)
            }
            2 -> { // a magma rock: a dark clump with a glowing heart showing through on top
                d.add(clump, p.x, p.s * 0.7f, p.z, p.s, p.yaw, rockPal, fog)
                d.add(ember, p.x, p.s * 1.36f + 0.25f, p.z, 0.25f * p.s, p.yaw, hot, fog, glow = 1f)
            }
            3 -> {
                d.add(vent, p.x, 0f, p.z, p.s, p.yaw, p.pal, fog, glow = 0.15f)
                bubble(d, p, p.s * 0.66f, fog, time, 0.25f)
            }
            else -> d.add(stalactite, p.x, p.y, p.z, p.s, p.yaw, p.pal, fog, glow = 0.25f)
        }
    }

    /** A blob of lava popping up out of a pool or vent and falling back. */
    private fun bubble(d: BiomeDraw, p: Piece, y0: Float, fog: Float, time: Float, size: Float) {
        val t = (time * 0.8f + p.seed * 7f) % 1f
        val h = 4f * t * (1f - t) * p.s * 0.5f
        d.add(ember, p.x + (p.seed - 0.5f) * p.s * 0.3f, y0 + h + size, p.z, size, time * 90f, hot, fog, glow = 1f)
    }

    override fun seedFar(p: Piece, r: Random) {
        p.kind = if (r.nextFloat() < 0.8f) 0 else 1
        p.x = BiomeToys.farX(p, r, 0.05f, 0.3f)
        p.s = if (p.kind == 0) 60f + r.nextFloat() * 50f else 22f + r.nextFloat() * 20f
        p.yaw = 0f // the lava faces you
        p.paint(0, 8f + r.nextFloat() * 10f, 0.78f, 0.78f + r.nextFloat() * 0.12f); p.paint(1, 48f, 0.85f, 1f); p.paint(2, 36f, 0.95f, 1f)
    }

    override val horizonTall = 1.5f
    override val farCount = 7

    override fun drawFar(d: BiomeDraw, p: Piece, haze: Float, time: Float) {
        if (p.kind == 0) {
            val h = p.s * (1f + p.seed * 0.4f)
            d.add(volcano, p.x, p.y - 6f, p.z, p.s, h, p.s, p.yaw, p.pal, haze * 0.4f, glow = 0.12f, onLand = false)
            d.add(ember, p.x, p.y - 6f + h * 0.45f + 1.6f, p.z, p.s * 0.13f, 1.6f, p.s * 0.13f, p.yaw, hot, haze * 0.3f,
                glow = 0.8f + 0.2f * sin(time * 2f + p.seed * 6f), onLand = false)
        } else d.add(spire, p.x, p.y - 6f, p.z, p.s, p.s * 2.4f, p.s, p.yaw, p.pal, haze, onLand = false)
    }

    override val landmarkKinds = 3

    override fun seedLandmark(p: Piece, r: Random) {
        p.x = 0.15f * 340f; p.rate = 0.4f
        p.paint(0, 8f, 0.7f, 0.66f); p.paint(1, 34f, 0.9f, 1f); p.paint(2, 52f, 0.7f, 1f)
        p.s = when (p.kind) { 0 -> 64f; 1 -> 40f; else -> 56f }
        if (p.kind == 2) { p.x = 8f; p.paint(0, 12f, 0.72f, 0.82f); p.paint(1, 28f, 0.85f, 1f); p.paint(2, 46f, 0.9f, 1f) } // a cave mouth over the road
    }

    override fun landmarkReach(p: Piece) = if (p.kind == 2) p.s else p.s * 0.6f
    override fun landmarkOverRoad(p: Piece) = p.kind == 2

    override fun drawLandmark(d: BiomeDraw, p: Piece, haze: Float, time: Float) {
        when (p.kind) {
            0 -> { // an erupting volcano, lava bombs arcing out of it
                val h = p.s * 1.6f
                d.add(volcano, p.x, -6f, p.z, p.s, h, p.s, 10f, p.pal, haze, onLand = false)
                val top = h * 0.45f - 6f // the crater (the model is 0.45 tall)
                for (k in 0 until BOMBS) {
                    val t = (time * 0.45f + k / BOMBS.toFloat()) % 1f
                    val dir = (k * 0.61f % 1f - 0.5f) * 2f
                    val x = p.x + dir * t * p.s * 0.45f
                    val y = top + t * 34f - t * t * 40f
                    d.add(ember, x, y, p.z + (k % 3 - 1) * 4f, 1.6f + (k % 2) * 0.8f, t * 300f, hot, haze * 0.5f, glow = 1f, onLand = false,
                        alpha = (t * 8f).coerceAtMost(1f) * ((1f - t) * 4f).coerceAtMost(1f))
                }
                d.add(ember, p.x, top + 1.5f, p.z, 5f, 1.5f, 5f, 0f, hot, haze * 0.5f, glow = 1f, onLand = false)
            }
            1 -> d.add(cliff, p.x, -6f, p.z, p.s, p.s * 1.1f, p.s, if (p.x > 0f) -25f else 25f, p.pal, haze, glow = 0.1f, onLand = false)
            else -> { // a cave mouth: the road runs into it, glowing drips hanging from its roof
                d.add(caveMouth, p.x, -10f, p.z, p.s, 0f, p.pal, haze, glow = 0.15f, onLand = false)
                for (k in 0 until DRIPS) {
                    val a = (k + 0.5f) / DRIPS * 3.1416f
                    val r = p.s * 0.62f
                    d.add(stalactite, p.x + kotlin.math.cos(a) * r, -10f + kotlin.math.sin(a) * r, p.z, p.s * 0.09f, k * 50f, p.pal, haze, glow = 0.3f, onLand = false)
                }
            }
        }
    }

    override val motes = 40

    override fun seedMote(p: Piece, r: Random, anywhere: Boolean) {
        // sparks out of the lava along the road, or out of the haze further off
        val channel = r.nextFloat() < 0.6f
        p.x = (if (r.nextBoolean()) 1f else -1f) * (if (channel) 3.3f + r.nextFloat() * 1.1f else 6f + r.nextFloat() * 18f)
        p.z = if (anywhere || channel) -90f + r.nextFloat() * 92f else -101f - r.nextFloat() * 10f
        p.y = if (anywhere) r.nextFloat() * 12f else if (channel) 0.05f else r.nextFloat() * 8f
        p.vy = 1.8f + r.nextFloat() * 2.4f
        p.vx = (r.nextFloat() - 0.5f) * 1.2f
        p.s = 0.11f + r.nextFloat() * 0.12f
        p.life = (14f - p.y) / p.vy
        p.paint(0, 20f + r.nextFloat() * 35f, 0.85f, 1f)
    }

    override fun drawMote(d: BiomeDraw, p: Piece, fog: Float, time: Float) {
        val flicker = 0.75f + 0.25f * sin(time * 11f + p.seed * 20f) // a spark grows as it comes out of the lava
        d.add(BiomeToys.speck, p.x + sin(time * 2f + p.seed * 9f) * 0.5f, p.y, p.z, p.s * flicker * p.show, time * 140f + p.seed * 360f, p.pal, fog,
            glow = 1f, onLand = false, alpha = (p.life / 1.2f).coerceAtMost(1f))
    }

    override fun skyRays(sky: SkyPainter, alpha: Float, time: Float) {
        sky.rays(0f, -14f, -360f, 210f, 16, time * 2f, glowCol, 0.16f * alpha, 0.55f)
    }

    /** A red rock rim between the road and its lava. */
    override fun kerbColors(a: Color, b: Color) { hsvInto(a, 6f, 0.72f, 0.62f); b.set(a) }
    override fun ground(out: Color, parity: Int) { hsvInto(out, 356f, 0.62f, if (parity == 0) 0.3f else 0.27f) }

    private companion object { const val BOMBS = 7; const val DRIPS = 7 }
}
