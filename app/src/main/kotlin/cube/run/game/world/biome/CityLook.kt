package cube.run.game.world.biome

import com.badlogic.gdx.graphics.Color
import cube.run.core.gfx.FacetShape
import cube.run.core.gfx.FacetShapes
import cube.run.core.hsvInto
import cube.run.data.Worlds
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Neon City at night: towers with lit windows crowding the land, a skyline
 * on the horizon under a big moon and stars, flying cars streaming past
 * overhead, a ferris wheel, a neon heart or a radio mast coming by. The
 * kerb is a neon stripe.
 */
class CityLook(world: Worlds.World) : BiomeLook(world) {

    /** A tower: its body, and its windows (drawn again, glowing, in the same place). */
    private class Tower(val body: FacetShape, val lit: FacetShape)

    private fun tower(seed: Int, levels: List<FloatArray>): Tower {
        val r = Random(seed)
        val body = toy {
            var y = 0f
            for (l in levels) { block(0f, y, 0f, l[0], l[1], l[0], 0); y += l[1] }
            block(0f, y, 0f, 0.05f, 0.7f, 0.05f, 2) // the mast
        }
        val lit = toy {
            var y = 0f
            for (l in levels) {
                val w = l[0]; val half = w / 2f + 0.012f
                var row = y + 0.18f
                while (row < y + l[1] - 0.2f) {
                    val slot = if (r.nextFloat() < 0.5f) 1 else 2
                    if (r.nextFloat() < 0.8f) block(0f, row, half, w * 0.78f, 0.12f, 0.024f, slot)
                    if (r.nextFloat() < 0.8f) block(half, row, 0f, 0.024f, 0.12f, w * 0.78f, slot)
                    if (r.nextFloat() < 0.8f) block(-half, row, 0f, 0.024f, 0.12f, w * 0.78f, slot)
                    row += 0.3f
                }
                y += l[1]
            }
            block(0f, y + 0.7f, 0f, 0.14f, 0.14f, 0.14f, 1) // the beacon
        }
        return Tower(body, lit)
    }

    private val towers = listOf(
        tower(1, listOf(floatArrayOf(1f, 3.6f), floatArrayOf(0.7f, 0.8f))),
        tower(2, listOf(floatArrayOf(1.2f, 1.8f), floatArrayOf(0.9f, 1.6f), floatArrayOf(0.6f, 1.2f))),
        tower(3, listOf(floatArrayOf(1.4f, 1.5f), floatArrayOf(1f, 1.2f))),
        tower(4, listOf(floatArrayOf(0.8f, 4.6f))),
    )
    private val billboard = toy {
        block(-0.3f, 0f, 0f, 0.08f, 1f, 0.08f, 0); block(0.3f, 0f, 0f, 0.08f, 1f, 0.08f, 0)
        block(0f, 1f, 0f, 1.3f, 0.6f, 0.1f, 1)
        block(0f, 1.1f, 0.05f + 0.012f, 0.9f, 0.4f, 0.024f, 2)
    }
    private val skylines = List(3) { k ->
        toy {
            val r = Random(40 + k)
            var x = -0.5f
            while (x < 0.5f) {
                val w = 0.06f + r.nextFloat() * 0.1f
                val h = 0.2f + r.nextFloat() * r.nextFloat() * 0.8f
                block(x + w / 2f, 0f, (r.nextFloat() - 0.5f) * 0.1f, w, h, 0.12f, 0)
                if (r.nextFloat() < 0.6f) block(x + w / 2f, h, 0f, w * 0.4f, 0.03f, 0.04f, 1)
                x += w
            }
        }
    }
    private val wheel = FacetShapes.voxelRing(16, 0.86f, 1)
    private val wheelLegs = toy {
        for (s in intArrayOf(-1, 1)) for (i in 0 until 5) block(s * (0.05f + (4 - i) * 0.07f), i * 0.2f, 0f, 0.1f, 0.2f, 0.1f, 0)
    }
    private val heart = toy {
        pixels(listOf(".11.11.", "1111111", "1112111", ".11111.", "..111..", "...1..."), 1f / 7f, 0.1f, y0 = 1f)
        block(0f, 0f, 0f, 0.06f, 1f, 0.06f, 0)
    }
    private val mast = toy {
        steps(0f, 0f, 0f, 6, 0.3f, 0.06f, 1f) { if (it % 2 == 0) 0 else 2 }
    }
    private val car = toy {
        block(0f, 0f, 0f, 0.5f, 0.18f, 1f, 0)
        block(0f, 0.18f, 0.05f, 0.36f, 0.14f, 0.5f, 0)
        block(0f, 0.04f, 0.5f + 0.01f, 0.4f, 0.08f, 0.02f, 1) // tail lights (toward the camera)
        block(0f, 0.04f, -0.5f - 0.01f, 0.4f, 0.08f, 0.02f, 2) // head lights
    }

    override val shapes: List<FacetShape> = towers.flatMap { listOf(it.body, it.lit) } +
        listOf(billboard, wheel, wheelLegs, heart, mast, car, BiomeToys.cube, BiomeToys.bigBall) + skylines

    private val moonPal = arrayOf(Color(), Color())
    private val beacon = arrayOf(Color())
    private val star = Color()
    private val stars = FloatArray(STARS * 4).also {
        val r = Random(77)
        for (i in 0 until STARS) {
            it[i * 4] = (r.nextFloat() * 2f - 1f) * 1.3f; it[i * 4 + 1] = 0.3f + r.nextFloat() * 0.85f
            it[i * 4 + 2] = 0.008f + r.nextFloat() * 0.012f; it[i * 4 + 3] = r.nextFloat() * 6.3f
        }
    }

    init {
        hsvInto(moonPal[0], 55f, 0.18f, 1f); hsvInto(moonPal[1], 45f, 0.3f, 0.95f)
        hsvInto(star, 60f, 0.1f, 1f)
    }

    private fun neon(r: Random) = NEON[r.nextInt(NEON.size)]

    override val propGap = 5.5f

    override fun seedProp(p: Piece, r: Random) {
        p.kind = if (r.nextFloat() < 0.18f) 4 else r.nextInt(4)
        p.yaw = if (r.nextFloat() < 0.3f) 45f else 0f
        p.s = 3.4f + r.nextFloat() * 2.4f
        p.x = clear(p.s * 0.75f, r, 10f)
        p.paint(0, 245f + r.nextFloat() * 30f, 0.55f, 0.32f + r.nextFloat() * 0.12f)
        val n = neon(r); p.paint(1, n, 0.7f, 1f); p.paint(2, n + 40f + r.nextFloat() * 120f, 0.6f, 1f)
        if (p.kind == 4) { p.s = 3f + r.nextFloat(); p.x = clear(p.s * 0.7f, r, 3f) }
    }

    override fun drawProp(d: BiomeDraw, p: Piece, fog: Float, time: Float) {
        if (p.kind == 4) { d.add(billboard, p.x, 0f, p.z, p.s, p.s, p.s, if (p.x > 0f) -20f else 20f, p.pal, fog, glow = 0.55f); return }
        val t = towers[p.kind]
        d.add(t.body, p.x, 0f, p.z, p.s, p.yaw, p.pal, fog)
        d.add(t.lit, p.x, 0f, p.z, p.s, p.yaw, p.pal, fog, glow = 0.85f)
    }

    override fun seedFar(p: Piece, r: Random) {
        p.kind = r.nextInt(skylines.size)
        p.x = BiomeToys.farX(p, r, 0.05f, 0.3f)
        p.s = 90f + r.nextFloat() * 60f
        p.yaw = (r.nextFloat() - 0.5f) * 20f
        p.paint(0, 255f, 0.6f, 0.22f); p.paint(1, neon(r), 0.55f, 1f); p.paint(2, 0f, 0f, 1f)
    }

    override fun drawFar(d: BiomeDraw, p: Piece, haze: Float, time: Float) {
        d.add(skylines[p.kind], p.x, p.y - 4f, p.z, p.s, p.s * (0.7f + p.seed * 0.4f), p.s, p.yaw, p.pal, haze * 0.8f, glow = 0.15f, onLand = false)
    }

    override val landmarkKinds = 3

    override fun seedLandmark(p: Piece, r: Random) {
        p.x = 0.17f * 300f; p.rate = 0.5f
        val n = neon(r); p.paint(0, 265f, 0.4f, 0.55f); p.paint(1, n, 0.75f, 1f); p.paint(2, n + 150f, 0.6f, 1f)
        p.s = when (p.kind) { 0 -> 24f; 1 -> 22f; else -> 60f }
    }

    override fun drawLandmark(d: BiomeDraw, p: Piece, haze: Float, time: Float) {
        when (p.kind) {
            0 -> { // a ferris wheel turning, its cabins hanging level
                val r = p.s
                d.add(wheelLegs, p.x, -2f, p.z, r * 2.2f, r * 1.1f, r, 0f, p.pal, haze, onLand = false)
                d.add(wheel, p.x, r * 1.1f - 2f, p.z, r, r, r, 0f, p.pal, haze, glow = 0.3f, pitch = 90f, onLand = false)
                for (k in 0 until CABINS) {
                    val a = time * 0.14f + k * 6.2832f / CABINS
                    d.add(BiomeToys.cube, p.x + cos(a) * r, r * 1.1f - 2f + sin(a) * r - 1.4f, p.z, 1.1f, 0f,
                        p.pal, haze, glow = 0.5f, onLand = false)
                }
            }
            1 -> d.add(heart, p.x, -2f, p.z, p.s, p.s, p.s, 0f, p.pal, haze, glow = 0.8f + 0.2f * sin(time * 5f), onLand = false)
            else -> {
                d.add(mast, p.x, -2f, p.z, p.s * 0.5f, p.s, p.s * 0.5f, 45f, p.pal, haze, onLand = false)
                beacon[0].set(if (sin(time * 4f) > 0f) Color.RED else Color.WHITE)
                d.add(BiomeToys.cube, p.x, p.s - 0.4f, p.z, 1.6f, 0f, beacon, haze, glow = 1f, onLand = false)
            }
        }
    }

    override fun landmarkReach(p: Piece) = if (p.kind == 0) p.s * 1.2f else p.s * 0.5f

    override val motes = 14

    override fun seedMote(p: Piece, r: Random, anywhere: Boolean) {
        val ahead = r.nextBoolean() // overtaking you, or coming the other way
        p.x = (if (r.nextBoolean()) 1f else -1f) * (4.5f + r.nextFloat() * 8f)
        p.y = 7f + r.nextFloat() * 5f
        p.z = if (anywhere) -100f + r.nextFloat() * 100f else if (ahead) 10f else -110f
        p.vz = if (ahead) -(45f + r.nextFloat() * 20f) else 10f + r.nextFloat() * 15f
        p.yaw = if (ahead) 0f else 180f
        p.s = 2.4f
        p.life = 12f
        val n = neon(r); p.paint(0, n, 0.55f, 0.9f); p.paint(1, 350f, 0.85f, 1f); p.paint(2, 55f, 0.2f, 1f)
    }

    override fun moveMote(p: Piece, dt: Float): Boolean = super.moveMote(p, dt) && p.z > -125f

    override fun drawMote(d: BiomeDraw, p: Piece, fog: Float, time: Float) {
        d.add(car, p.x, p.y + sin(time * 2f + p.seed * 7f) * 0.2f, p.z, p.s, p.yaw, p.pal, fog, glow = 0.35f, onLand = false)
    }

    override fun drawSky(d: BiomeDraw, alpha: Float, time: Float) {
        d.add(BiomeToys.bigBall, -62f, 55f, -330f, 15f, time * 2f, moonPal, 0.05f, glow = 0.85f, onLand = false, alpha = alpha)
    }

    override fun paintSky(sky: SkyPainter, alpha: Float, time: Float) {
        for (i in 0 until STARS) {
            val tw = 0.55f + 0.45f * sin(time * 1.7f + stars[i * 4 + 3])
            sky.glow(stars[i * 4], stars[i * 4 + 1], stars[i * 4 + 2] * 2f, star, alpha * tw * 0.8f)
        }
    }

    override val kerb = KERB_STRIPES
    override fun kerbColors(a: Color, b: Color) { hsvInto(a, 315f, 0.75f, 1f); hsvInto(b, 185f, 0.75f, 1f) }

    private companion object {
        const val CABINS = 8
        const val STARS = 36
        /** Pink, cyan, lemon, lime, violet, orange. */
        val NEON = floatArrayOf(315f, 185f, 55f, 110f, 275f, 25f)
    }
}
