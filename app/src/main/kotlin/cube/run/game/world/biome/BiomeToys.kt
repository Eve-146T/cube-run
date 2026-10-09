package cube.run.game.world.biome

import cube.run.core.gfx.FacetShape
import cube.run.core.gfx.FacetShapes
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Builds the biomes' toys from boxes that touch or stack, never intersect
 * (so nothing shimmers). Every toy stands on y = 0 and is about one unit
 * across, scaled up where it is placed; palette slots 0–2 colour its parts.
 */
class Toy {
    private val parts = ArrayList<FacetShapes.Box>()

    /** A box standing on [y0], centred at ([x], [z]), [w] × [h] × [d], in palette [slot]. */
    fun block(x: Float, y0: Float, z: Float, w: Float, h: Float, d: Float, slot: Int = 0) {
        parts.add(FacetShapes.Box(x, y0 + h / 2f, z, w / 2f, h / 2f, d / 2f, slot))
    }

    /** A square stack narrowing upward: [n] steps from [base] wide to [top] wide, [h] tall in all. Returns the top. */
    fun steps(x: Float, y0: Float, z: Float, n: Int, base: Float, top: Float, h: Float, slot: (Int) -> Int = { 0 }): Float {
        val sh = h / n
        for (i in 0 until n) {
            val w = base + (top - base) * i / (n - 1f).coerceAtLeast(1f)
            block(x, y0 + i * sh, z, w, sh, w, slot(i))
        }
        return y0 + h
    }

    /**
     * Pixel art stood upright, facing the road: [rows] top to bottom, '0'–'2'
     * a cell in that slot, anything else empty. [cell] wide, [depth] thick,
     * bottom row on [y0], centred on x. Runs of one slot become one box.
     */
    fun pixels(rows: List<String>, cell: Float, depth: Float, y0: Float = 0f, z: Float = 0f) {
        val wide = rows.maxOf { it.length }
        for ((i, row) in rows.withIndex()) {
            val y = y0 + (rows.size - 1 - i) * cell
            var c = 0
            while (c < row.length) {
                val ch = row[c]
                if (ch !in '0'..'2') { c++; continue }
                var end = c + 1
                while (end < row.length && row[end] == ch) end++
                val x = ((c + end) / 2f - wide / 2f) * cell
                block(x, y, z, (end - c) * cell, cell, depth, ch - '0')
                c = end
            }
        }
    }

    fun build(): FacetShape = FacetShapes.model(parts)
}

fun toy(f: Toy.() -> Unit): FacetShape = Toy().apply(f).build()

object BiomeToys {
    /** A cube, half-size 1 (centred, unlike the toys). */
    val cube: FacetShape get() = FacetShapes.cube()

    // The same cube again, as shapes of their own: the facet batch takes 128 of one shape a frame,
    // and rivers and weather come in the dozens.
    /** River steps. */
    val slab: FacetShape by lazy { FacetShapes.model(listOf(FacetShapes.Box(0f, 0f, 0f, 1f, 1f, 1f))) }
    /** Weather: snowflakes, embers. */
    val speck: FacetShape by lazy { FacetShapes.model(listOf(FacetShapes.Box(0f, 0f, 0f, 1f, 1f, 1f))) }

    /** A round lump of boxes, radius 1, centred: dunes, domes, scoops, hills (sink it into the land). */
    val ball: FacetShape get() = FacetShapes.voxelBall(6)
    val bigBall: FacetShape get() = FacetShapes.voxelBall(9)

    /** A stepped mountain, 1 wide at its foot and 1 tall; slot 1 is its cap (snow, frosting, glowing crater). */
    fun peak(steps: Int, cap: Int): FacetShape = toy {
        steps(0f, 0f, 0f, steps, 1f, 1f / steps, 1f) { if (it >= steps - cap) 1 else 0 }
    }

    /**
     * A mountain range: [n] stepped summits side by side, about 1 wide, the highest 0.5 tall (broad
     * slopes, as mountains are), the others lower; the top [cap] of each summit's [steps] are slot 1
     * (snow, a glowing crest).
     */
    fun range(seed: Int, n: Int, steps: Int, cap: Int): FacetShape = toy {
        val r = Random(seed)
        val top = r.nextInt(n)
        for (k in 0 until n) {
            val x = -0.5f + (k + 0.5f) / n + (r.nextFloat() - 0.5f) * 0.08f
            val h = if (k == top) 0.5f else 0.24f + r.nextFloat() * 0.18f
            val base = h * 2.1f
            steps(x, 0f, (r.nextFloat() - 0.5f) * 0.2f, steps, base, base * 0.14f, h) { if (it >= steps - cap) 1 else 0 }
        }
    }

    /** A cluster of [n] lumps spread on the ground around the centre, ~1 across: clouds and bushes. */
    fun cluster(seed: Int, n: Int, flat: Float): FacetShape = toy {
        val r = Random(seed)
        block(0f, 0f, 0f, 0.62f, 0.34f * flat, 0.5f, 0)
        for (i in 0 until n) {
            val a = i * 6.2832f / n + r.nextFloat() * 0.6f
            val w = 0.26f + r.nextFloat() * 0.14f
            val x = cos(a) * (0.31f + w / 2f)
            val z = sin(a) * (0.25f + w / 2f)
            block(x, 0f, z, w, (0.22f + r.nextFloat() * 0.2f) * flat, w, if (i % 3 == 2) 1 else 0)
        }
        block(0f, 0.34f * flat, 0f, 0.4f, 0.22f * flat, 0.34f, 1)
    }

    /** Pixel rows of a half ring (a rainbow, an arch): [bands] bands from the outside in, [res] cells across. */
    fun arcRows(res: Int, inner: Float, bands: Int): List<String> {
        val h = res / 2
        return List(h) { i ->
            val y = (h - i - 0.5f) / h
            String(CharArray(res) { c ->
                val x = (c + 0.5f - res / 2f) / h
                val d = kotlin.math.sqrt(x * x + y * y)
                if (d > 1f || d < inner) '.' else ('0' + ((1f - d) / (1f - inner) * bands).toInt().coerceAtMost(bands - 1) % 3)
            })
        }
    }

    /** Place a far shape on the horizon: off to one side of where the road vanishes, between two view tangents. */
    fun farX(p: Piece, r: Random, inner: Float, outer: Float): Float {
        val side = if (r.nextBoolean()) 1f else -1f
        return side * (inner + r.nextFloat() * (outer - inner)) * (BiomeScene.CAMERA_Z - p.z)
    }
}
