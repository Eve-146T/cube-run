package cube.run.game.world.biome

import com.badlogic.gdx.graphics.Color
import cube.run.core.gfx.FacetShape
import cube.run.core.hsvInto
import cube.run.data.Worlds
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Neon City at night: towers with lit windows crowding the land, a skyline
 * on the horizon under a big moon and stars, flying cars streaming past
 * overhead; a blimp, fireworks or a giant neon heart up over the street.
 * The kerb is a neon stripe.
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
    private val heart = toy {
        pixels(listOf(".11.11.", "1111111", "1112111", ".11111.", "..111..", "...1..."), 1f / 7f, 0.1f)
    }
    private val fin = toy { block(0f, -0.5f, 0f, 0.3f, 1f, 0.06f, 1); block(0f, -0.03f, 0f, 0.3f, 0.06f, 0.8f, 1) }
    private val car = toy {
        block(0f, 0f, 0f, 0.5f, 0.18f, 1f, 0)
        block(0f, 0.18f, 0.05f, 0.36f, 0.14f, 0.5f, 0)
        block(0f, 0.04f, 0.5f + 0.01f, 0.4f, 0.08f, 0.02f, 1) // tail lights (toward the camera)
        block(0f, 0.04f, -0.5f - 0.01f, 0.4f, 0.08f, 0.02f, 2) // head lights
    }

    override val shapes: List<FacetShape> = towers.flatMap { listOf(it.body, it.lit) } +
        listOf(billboard, fin, heart, car, BiomeToys.cube, BiomeToys.bigBall, BiomeToys.speck) + skylines

    private val moonPal = arrayOf(Color(), Color())
    private val sign = arrayOf(Color(1f, 0.95f, 0.6f, 1f))
    private val sparkPal = arrayOf(Color())
    private val star = Color()
    private val stars = FloatArray(STARS * 4).also {
        val r = Random(77)
        for (i in 0 until STARS) {
            it[i * 4] = (r.nextFloat() * 2f - 1f) * 1.3f; it[i * 4 + 1] = 0.3f + r.nextFloat() * 0.85f
            it[i * 4 + 2] = 0.004f + r.nextFloat() * 0.005f; it[i * 4 + 3] = r.nextFloat() * 6.3f
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
        p.x = BiomeToys.farX(p, r, 0.1f, 0.32f)
        p.s = 80f + r.nextFloat() * 50f
        p.yaw = (r.nextFloat() - 0.5f) * 20f
        p.paint(0, 255f, 0.6f, 0.22f); p.paint(1, neon(r), 0.55f, 1f); p.paint(2, 0f, 0f, 1f)
    }

    override val horizonTall = 1.8f

    override fun drawFar(d: BiomeDraw, p: Piece, haze: Float, time: Float) {
        d.add(skylines[p.kind], p.x, p.y - 4f, p.z, p.s, p.s * (0.3f + p.seed * 0.18f), p.s, p.yaw, p.pal, haze, glow = 0.15f, onLand = false)
    }

    override val landmarkKinds = 3

    // The towers hide anything standing at street level, so the city's landmarks are up in the sky over the street.
    override fun seedLandmark(p: Piece, r: Random) {
        p.x = 6f + r.nextFloat() * 8f; p.rate = 0.45f
        val n = neon(r); p.paint(0, 265f, 0.35f, 0.75f); p.paint(1, n, 0.75f, 1f); p.paint(2, n + 150f, 0.6f, 1f)
        when (p.kind) {
            0 -> { p.s = 9f; p.y = 48f }   // a blimp with a glowing sign, above the tallest masts
            1 -> { p.s = 1f; p.y = 46f }   // fireworks over the street
            else -> { p.s = 26f; p.y = 38f } // a giant neon heart hanging in the sky
        }
    }

    override fun landmarkOverRoad(p: Piece) = true

    override fun landmarkReach(p: Piece) = if (p.kind == 1) 30f else p.s * 1.2f

    override fun drawLandmark(d: BiomeDraw, p: Piece, haze: Float, time: Float) {
        when (p.kind) {
            0 -> { // a blimp drifting over the street, its sign band glowing
                val bob = sin(time * 0.6f) * 1.2f
                d.add(BiomeToys.bigBall, p.x, p.y + bob, p.z, p.s * 2.4f, p.s, p.s, 0f, p.pal, haze, glow = 0.15f, onLand = false)
                d.add(BiomeToys.cube, p.x, p.y + bob, p.z + 0.2f, p.s * 1.7f, p.s * 0.28f, p.s * 1.02f, 0f, sign, haze, glow = 0.9f + 0.1f * sin(time * 6f), onLand = false)
                d.add(BiomeToys.cube, p.x, p.y + bob - p.s * 1.1f, p.z, p.s * 0.5f, p.s * 0.2f, p.s * 0.3f, 0f, p.pal, haze, onLand = false) // the gondola
                d.add(fin, p.x - p.s * 2.4f, p.y + bob, p.z, p.s, 0f, p.pal, haze, onLand = false)
            }
            1 -> for (k in 0 until BURSTS) { // fireworks: sparks flying out of a point and falling away
                val t = (time * 0.42f + k / BURSTS.toFloat()) % 1f
                val cx = p.x + ((k * 7 % 5) - 2f) * 9f; val cy = p.y + (k * 3 % 4) * 6f; val cz = p.z - (k % 3) * 25f
                sparkPal[0].set(p.pal[1 + k % 2])
                val r = 4f + t * 16f
                for (j in 0 until SPARKS) {
                    val a = j * 6.2832f / SPARKS + k
                    d.add(BiomeToys.speck, cx + cos(a) * r, cy + sin(a) * r - t * t * 9f, cz, 0.9f * (1f - t) + 0.2f, t * 200f,
                        sparkPal, haze, glow = 1f, onLand = false, alpha = ((1f - t) * 3f).coerceAtMost(1f))
                }
            }
            else -> d.add(heart, p.x, p.y, p.z, p.s, p.s, p.s, sin(time * 0.5f) * 12f, p.pal, haze, glow = 0.8f + 0.2f * sin(time * 5f), onLand = false)
        }
    }

    override val motes = 14

    override fun seedMote(p: Piece, r: Random, anywhere: Boolean) {
        // Skyways over the street, clear of the towers (they start 5.2 out): traffic overtaking you on
        // the right, oncoming on the left, two lanes at two heights each way. One speed per direction,
        // so no car ever drives through another.
        val ahead = r.nextBoolean()
        p.x = (if (ahead) 1f else -1f) * (if (r.nextBoolean()) 1.3f else 3f)
        p.y = if (r.nextBoolean()) 8.5f else 11f
        p.z = if (anywhere) -100f + r.nextFloat() * 100f else if (ahead) 10f + r.nextFloat() * 25f else -104f - r.nextFloat() * 25f
        p.vz = if (ahead) -55f else 16f
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
        const val BURSTS = 4
        const val SPARKS = 10
        const val STARS = 36
        /** Pink, cyan, lemon, lime, violet, orange. */
        val NEON = floatArrayOf(315f, 185f, 55f, 110f, 275f, 25f)
    }
}
