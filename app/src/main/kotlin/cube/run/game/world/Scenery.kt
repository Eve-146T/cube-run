package cube.run.game.world

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import cube.run.core.Gdx3DGame
import cube.run.core.hsvInto
import cube.run.data.Worlds
import cube.run.game.Lanes
import cube.run.game.world.biome.BiomeLook
import cube.run.game.world.biome.BiomeScene
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Distance haze: crisp until [start], fully dissolved into the sky by [end]. Rows spawn beyond [end]. */
object Fog {
    const val start = 70f
    const val end = 98f

    /** Fog factor for world geometry at [z] (negative = ahead of the player). */
    @Suppress("NOTHING_TO_INLINE")
    inline fun at(z: Float): Float {
        val value = (-z - start) / (end - start)
        return if (value < 0f) 0f else if (value > 1f) 1f else value
    }

    /** Things come into view: invisible where they are born ([appearFar] and beyond), whole by [appearNear]. */
    /** Off only to measure what the fade-in saves (tests). */
    @JvmField @Volatile var fadeIn = true
    const val appearFar = 100f
    const val appearNear = 88f

    /**
     * How much of something at [z] is there (0…1): the world's far end fades in instead of
     * popping up a tile or a post at a time. Fog only tints toward one colour, never the
     * sky, sun or mountains behind, so it cannot hide a newcomer on its own.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun appear(z: Float): Float {
        if (!fadeIn) return 1f
        val value = (appearFar + z) / (appearFar - appearNear)
        val t = if (value < 0f) 0f else if (value > 1f) 1f else value
        return t * t * (3f - 2f * t)
    }
}

/**
 * The scrolling backdrop: a seamless two-tone floor with the biome's kerb and
 * land, roadside decoration in the current world's style, wind streaks that
 * fly past faster than the world at speed, and the occasional world gate.
 * Everything further out (the land's toys, the horizon, landmarks, weather
 * and sky) is the [biome]'s. All plain data drawn
 * through the batched world-box pass. Pieces are recoloured/restyled for
 * the *current* world when they wrap, so a new world grows in from the
 * horizon behind its gate instead of snapping. Every decoration is built
 * from boxes that touch or stack — never intersect — so nothing shimmers.
 */
class Scenery(private val game: Gdx3DGame, private val rnd: Random) {

    private class Tile(@JvmField val col: Color, @JvmField val col2: Color, @JvmField val ground: Color, @JvmField var z: Float, @JvmField val parity: Int) {
        @JvmField val kerb = Color()
        @JvmField val light = Color()
        @JvmField var kerbStyle = BiomeLook.KERB_PLAIN
    }
    private class Post(@JvmField val col: Color, @JvmField val col2: Color, @JvmField val col3: Color, @JvmField var z: Float, @JvmField var x: Float, @JvmField var style: Int, @JvmField var h: Float, @JvmField var seed: Float, @JvmField var kind: Int)
    private class Streak(@JvmField var x: Float, @JvmField var y: Float, @JvmField var z: Float, @JvmField var len: Float)
    private class Gate(@JvmField var z: Float, @JvmField val col: Color, @JvmField var passed: Boolean = false, @JvmField var held: Boolean = false, @JvmField val start: Boolean = false)

    private val tileD = 3f
    private val tileRows = 40       // floor reaches past the fog wall (8 - 39*3 = -109)
    private val postGap = 6.6f
    private val postPairs = 17      // last pair ≈ -100, born inside the fog
    private val streakCount = 22

    private val tiles = ArrayList<Tile>()
    private val posts = ArrayList<Post>()
    private val streaks = ArrayList<Streak>()
    private val gates = ArrayList<Gate>()
    private val streakCol = Color(1f, 1f, 1f, 1f)
    private val white = Color(1f, 1f, 1f, 1f)
    private val edgeCol = Color()
    private val dark = Color(0.10f, 0.08f, 0.18f, 1f)
    private var world: Worlds.World = Worlds.get(0)
    /** Outer Space (null outside a run's reach): the land falls away under it, the road takes its colours. */
    var space: cube.run.game.space.SpaceWorld? = null
    private val tileA = Color(); private val tileB = Color()
    private val kerbB = Color()
    /** The biome around the road, beyond the roadside. */
    val biome = BiomeScene(game)
    private var dropY = 0f          // how far the land (and everything on it) has fallen away

    private val laneW: Float get() = Lanes.NORMAL_W

    private fun paintTile(t: Tile) {
        // two candy tones a few degrees apart: a soft check, no dark seams
        hsvInto(t.col, world.floorH, world.floorS, world.floorAltV)
        hsvInto(t.col2, world.floorH + 14f, world.floorS, world.floorV)
        // the land the road runs through: the same family, quieter, striped with the tiles
        val look = biome.lookOf(world)
        look.ground(t.ground, t.parity)
        // the road's edge, in the biome's style
        t.kerbStyle = look.kerb
        look.kerbColors(t.kerb, kerbB)
        if (t.kerbStyle == BiomeLook.KERB_LIGHTS) t.light.set(kerbB)
        else if (t.parity == 1) t.kerb.set(kerbB)
    }

    private fun seedPost(p: Post) {
        p.style = world.deco
        p.seed = rnd.nextFloat()
        p.kind = rnd.nextInt(3)
        p.h = when (world.deco) {
            Worlds.TOWERS -> 2.5f + rnd.nextFloat() * 4f
            Worlds.CRYSTALS -> 1.8f + rnd.nextFloat() * 2.4f
            Worlds.SPIKES -> 1.6f + rnd.nextFloat() * 1.2f
            Worlds.CACTI -> 1.8f + rnd.nextFloat() * 1f
            Worlds.RINGS -> 1.6f + rnd.nextFloat() * 1.2f
            else -> 0.9f + rnd.nextFloat() * 0.8f
        }
        hsvInto(p.col, world.postH, world.postS, world.postV)
        hsvInto(p.col2, world.postH + (if (world.deco == Worlds.CRYSTALS) 18f + p.seed * 18f else 50f + p.seed * 60f), world.postS, world.postV) // lava stays hot
        hsvInto(p.col3, world.postH + 130f + p.seed * 40f, world.postS * 0.9f, world.postV)
        val side = if (p.x < 0f) -1f else 1f
        p.x = side * (laneW * 2.5f + 1.1f + (if (world.deco == Worlds.TOWERS) rnd.nextFloat() * 2.5f else rnd.nextFloat() * 0.6f))
    }

    private fun seedStreak(s: Streak) {
        val side = if (rnd.nextBoolean()) -1f else 1f
        s.x = side * (laneW * 1.5f + 2.2f + rnd.nextFloat() * 4f)
        s.y = 0.4f + rnd.nextFloat() * 4.5f
        s.len = 3f + rnd.nextFloat() * 5f
    }

    fun init(world: Worlds.World, seed: Int = Random.nextInt()) { // never the course's random numbers
        tiles.clear(); posts.clear(); streaks.clear(); gates.clear()
        this.world = world
        biome.init(world, seed)
        for (r in 0 until tileRows) {
            val t = Tile(Color(), Color(), Color(), 8f - r * tileD, r % 2)
            paintTile(t)
            tiles.add(t)
        }
        var pz = 6f
        repeat(postPairs) {
            for (side in intArrayOf(-1, 1)) {
                val p = Post(Color(), Color(), Color(), pz, side.toFloat(), 0, 1f, 0f, 0)
                seedPost(p)
                posts.add(p)
            }
            pz -= postGap
        }
        repeat(streakCount) { streaks.add(Streak(0f, 0f, -rnd.nextFloat() * 100f, 0f).also { seedStreak(it) }) }
    }

    /** Pieces born from now on take this world's colours and style. */
    fun setWorld(world: Worlds.World) { this.world = world; biome.setWorld(world) }

    /** The player passed the gate into [world]: its horizon, weather and sky take over. */
    fun enter(world: Worlds.World) = biome.enter(world)

    /** Drift the biome's weather and ease its sky ([mv]: this frame's road movement). */
    fun tick(dt: Float, mv: Float) = biome.tick(dt, mv)

    /** The biome's sunbursts (world shapes pass, unbent). [amount]: how much of the world is showing. */
    fun renderShapes(shapes: ShapeRenderer, time: Float, amount: Float) = biome.renderShapes(shapes, time, dropY, amount)

    /** The biome's painted sky, behind everything. */
    fun renderBackdrop(shapes: ShapeRenderer, cam: Camera, time: Float, amount: Float) = biome.renderBackdrop(shapes, cam, time, dropY, amount)

    /** Drop a gate at the horizon in [col]: the doorway to the next world. */
    fun spawnGate(col: Color) { gates.add(Gate(-102f, Color(col))) }

    /** The start gate: waits a little way down the road until the run begins ([release]), then comes at you. */
    fun spawnStartGate(col: Color) { gates.add(Gate(-30f, Color(col), held = true, start = true)) }

    /** The run began: everything held still starts moving with the world. */
    fun release() { for (g in gates) g.held = false }

    /** Take down gates already behind the player: a camera swung round behind the cube would look through them. */
    fun clearPassedGates() { gates.removeAll { it.passed } }

    companion object {
        const val PASSED_WORLD = 1
        const val PASSED_START = 2
        /** How see-through the road gets in space (0 solid … 1 gone). */
        const val GLASS = 0.55f
    }

    /** Scroll by [mv]. Returns PASSED_WORLD / PASSED_START on the frame a gate passes the player, else 0. */
    fun scroll(mv: Float): Int {
        for (t in tiles) {
            t.z += mv
            if (t.z > 8f) { t.z -= tileRows * tileD; paintTile(t) }
        }
        for (p in posts) {
            p.z += mv
            if (p.z > 8f) { p.z -= postPairs * postGap; seedPost(p) }
        }
        biome.scroll(mv)
        for (s in streaks) { // wind streaks fly past faster than the world (parallax sells the speed)
            s.z += mv * 1.6f
            if (s.z > 10f) { s.z = -102f - rnd.nextFloat() * 18f; seedStreak(s) } // born unseen, past Fog.appearFar
        }
        var passed = 0
        var i = gates.size - 1
        while (i >= 0) {
            val g = gates[i]
            if (!g.held) g.z += mv
            if (!g.passed && g.z > 0f) { g.passed = true; passed = if (g.start) PASSED_START else PASSED_WORLD }
            if (g.z > 14f) gates.removeAt(i)
            i--
        }
        return passed
    }

    /**
     * The road and the land, queued FIRST so the batch never drops them:
     * seamless tiles, a bright kerb along each edge, and the ground running
     * out to the haze on both sides (what the roadside stands on).
     */
    fun renderRoad() {
        val sp = space?.blend ?: 0f
        dropY = sp * sp * 70f // the ground falls away beneath the road
        val w = Lanes.w
        val u = Lanes.unfold
        val kerb = Lanes.halfRoadDrawn + 0.12f
        val landW = 30f
        val landX = kerb + 0.12f + landW / 2f
        for (t in tiles) {
            val fog = Fog.at(t.z)
            val a = Fog.appear(t.z)
            val c1 = if (sp > 0f) space!!.roadTile(tileA, t.col, false) else t.col
            val c2 = if (sp > 0f) space!!.roadTile(tileB, t.col2, true) else t.col2
            // the three core lanes, then the two outer ones growing out from the edges as the road unfolds
            if (sp > 0f) { // in space the road turns to glass: stars, rocks and planets show through beneath you
                val alpha = 1f - GLASS * sp
                // the tile, minus any rift crossing it: the gap stays open to space
                val rifts = space!!.rifts
                var top = t.z + tileD / 2f
                val bottom = t.z - tileD / 2f
                for (i in 0 until rifts.count) {
                    val hn = rifts.near(i); val hf = rifts.far(i)
                    if (hf >= top || hn <= bottom) continue
                    if (hn < top) glassPiece(hn, top, w, t.parity, c1, c2, fog, alpha * a)
                    top = min(top, hf)
                }
                if (top > bottom) glassPiece(bottom, top, w, t.parity, c1, c2, fog, alpha * a)
            } else for (l in 0 until 3) game.worldGround((l - 1) * w, -0.14f, t.z, w, 0.26f, tileD, if ((l + 1 + t.parity) % 2 == 0) c1 else c2, fog, a)
            if (u > 0.01f) {
                val ow = w * u
                game.worldGround(-(1.5f * w + ow / 2f), -0.14f, t.z, ow, 0.26f, tileD, if (t.parity == 0) c1 else c2, fog, a)
                game.worldGround(1.5f * w + ow / 2f, -0.14f, t.z, ow, 0.26f, tileD, if (t.parity == 0) c1 else c2, fog, a)
            }
            renderKerb(t, kerb, fog, sp, a)
            if (dropY < 60f) {
                game.worldGround(-landX, -0.16f - dropY, t.z, landW, 0.3f, tileD, t.ground, fog, a)
                game.worldGround(landX, -0.16f - dropY, t.z, landW, 0.3f, tileD, t.ground, fog, a)
            }
        }
    }

    /** The road's edge on one tile row, both sides, in the style of the biome the row was born in. */
    private fun renderKerb(t: Tile, x: Float, fog: Float, sp: Float, a: Float) {
        edgeCol.set(t.kerb)
        if (sp > 0f) space!!.kerb(edgeCol, edgeCol)
        when (t.kerbStyle) {
            BiomeLook.KERB_BANK -> { // a lumpy bank, its inner edge where the kerb's is
                val w = if (t.parity == 0) 0.5f else 0.42f
                val h = if (t.parity == 0) 0.52f else 0.42f
                val bx = x - 0.12f + w / 2f
                game.worldGround(-bx, -0.1f + (h - 0.34f) / 2f, t.z, w, h, tileD, edgeCol, fog, a)
                game.worldGround(bx, -0.1f + (h - 0.34f) / 2f, t.z, w, h, tileD, edgeCol, fog, a)
            }
            else -> {
                game.worldGround(-x, -0.1f, t.z, 0.24f, 0.34f, tileD, edgeCol, fog, a)
                game.worldGround(x, -0.1f, t.z, 0.24f, 0.34f, tileD, edgeCol, fog, a)
                if (t.kerbStyle == BiomeLook.KERB_LIGHTS && t.parity == 0 && sp < 0.5f) { // a runway light on every other tile
                    game.worldBox(-x, 0.19f, t.z, 0.2f, 0.12f, 0.5f, t.light, fog, a)
                    game.worldBox(x, 0.19f, t.z, 0.2f, 0.12f, 0.5f, t.light, fog, a)
                }
            }
        }
    }

    /** One stretch of glass road, from [far] to [near], three lanes wide. */
    private fun glassPiece(far: Float, near: Float, w: Float, parity: Int, c1: Color, c2: Color, fog: Float, alpha: Float) {
        val len = near - far
        if (len < 0.02f) return
        for (l in 0 until 3) game.glassGround((l - 1) * w, -0.04f, (near + far) / 2f, w, 0.06f, len, if ((l + 1 + parity) % 2 == 0) c1 else c2, fog, alpha)
    }

    /** Everything beside and above the road: the roadside, the gates, the wind. [wind] 0..1 = how vivid the speed streaks are. */
    fun render(wind: Float, time: Float) {
        if (dropY < 60f) for (p in posts) renderPost(p, time)
        game.renderProbe?.invoke("biome")
        biome.render(time, dropY)
        game.renderProbe?.invoke("wind")
        for (g in gates) renderGate(g, time)
        if (wind > 0f && dropY < 1f) { // faint at cruising speed, vivid near the ceiling (fog doubles as fade)
            for (s in streaks) {
                val fade = max(Fog.at(s.z), 1f - wind * 0.75f)
                game.worldBox(s.x, s.y, s.z, 0.05f, 0.05f, s.len * (0.6f + wind * 0.8f), streakCol, fade, Fog.appear(s.z))
            }
        }
    }

    /** Roadside pieces stand on the land: when it falls away (Outer Space) they fall with it. */
    /** How much of the post being drawn has come into view (see [Fog.appear]). */
    private var postAlpha = 1f

    private fun postBox(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, col: Color, fog: Float = 0f) =
        game.worldBox(x, y - dropY, z, sx, sy, sz, col, fog, postAlpha)

    private fun postSpin(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, yaw: Float, col: Color, fog: Float = 0f) =
        game.worldBoxSpin(x, y - dropY, z, sx, sy, sz, yaw, col, fog, postAlpha)

    private fun renderPost(p: Post, time: Float) {
        val fog = Fog.at(p.z)
        postAlpha = Fog.appear(p.z)
        if (postAlpha <= 0f) return
        val x = p.x; val z = p.z
        when (p.style) {
            Worlds.LOLLIPOPS -> when (p.kind) { // candy: gumdrops, candy canes, cupcakes — clean stacked shapes
                0 -> { // gumdrop: a fat base with a smaller dome on top
                    postSpin(x, p.h * 0.35f, z, 1.1f, p.h * 0.7f, 1.1f, 45f, p.col, fog)
                    postSpin(x, p.h * 0.7f + p.h * 0.18f, z, 0.7f, p.h * 0.36f, 0.7f, 45f, p.col2, fog)
                }
                1 -> { // candy cane: striped stack
                    val n = 5; val seg = (p.h + 1.2f) / n
                    for (i in 0 until n) postBox(x, seg * (i + 0.5f), z, 0.34f, seg, 0.34f, if (i % 2 == 0) white else p.col, fog)
                    postBox(x - 0.3f, seg * n + 0.1f, z, 0.9f, 0.34f, 0.34f, p.col, fog)
                }
                else -> { // cupcake: wrapper, frosting, cherry
                    postBox(x, 0.35f, z, 1.0f, 0.7f, 1.0f, p.col3, fog)
                    postSpin(x, 0.7f + 0.3f, z, 1.15f, 0.6f, 1.15f, 45f, p.col2, fog)
                    postSpin(x, 1.3f + 0.22f, z, 0.6f, 0.44f, 0.6f, 45f, white, fog)
                    postBox(x, 1.74f + 0.15f, z, 0.3f, 0.3f, 0.3f, p.col, fog)
                }
            }
            Worlds.TOWERS -> { // a dark block with lit windows on its face
                postBox(x, p.h / 2f, z, 1.3f, p.h, 1.3f, dark, fog)
                var y = 0.6f
                var k = 0
                val face = if (x < 0f) x + 0.66f else x - 0.66f
                while (y < p.h - 0.4f && k < 7) {
                    val lit = sin(p.seed * 20f + k * 3.1f) > -0.2f
                    if (lit) postBox(face, y, z, 0.06f, 0.28f, 0.6f, if (k % 2 == 0) p.col else p.col2, fog)
                    y += 0.7f; k++
                }
                postBox(x, p.h + 0.15f, z, 0.5f, 0.3f, 0.5f, p.col, fog) // a lit cap
            }
            Worlds.CRYSTALS -> { // a shard cluster: three tapering stacks side by side
                spike(x, z, p.h, 0.7f, 45f + p.seed * 30f, p.col, fog)
                spike(x + 0.7f, z + 0.4f, p.h * 0.55f, 0.45f, 20f + p.seed * 50f, p.col2, fog)
                spike(x - 0.65f, z - 0.35f, p.h * 0.4f, 0.38f, 60f + p.seed * 40f, p.col2, fog)
            }
            Worlds.SPIKES -> spike(x, z, p.h, 1.2f, 0f, p.col, fog, cap = white)
            Worlds.CACTI -> { // a trunk with two arms and a flower
                postBox(x, p.h / 2f, z, 0.5f, p.h, 0.5f, p.col3, fog)
                postBox(x - 0.55f, p.h * 0.55f, z, 0.6f, 0.34f, 0.34f, p.col3, fog)
                postBox(x - 0.68f, p.h * 0.55f + 0.55f, z, 0.34f, 0.8f, 0.34f, p.col3, fog)
                postBox(x + 0.55f, p.h * 0.4f, z, 0.6f, 0.34f, 0.34f, p.col3, fog)
                postBox(x + 0.68f, p.h * 0.4f + 0.5f, z, 0.34f, 0.7f, 0.34f, p.col3, fog)
                postSpin(x, p.h + 0.2f, z, 0.36f, 0.36f, 0.36f, time * 30f, p.col, fog)
            }
            Worlds.RINGS -> { // a moon-base light pylon: a white mast on a foot, its lamp blinking in turn
                postBox(x, 0.15f, z, 0.8f, 0.3f, 0.8f, white, fog)
                postBox(x, 0.3f + p.h / 2f, z, 0.24f, p.h, 0.24f, white, fog)
                postBox(x, 0.3f + p.h + 0.04f, z, 0.6f, 0.08f, 0.6f, p.col2, fog)
                val on = sin(time * 3f - z * 0.25f) > 0f // the lamps chase along the road
                postSpin(x, 0.3f + p.h + 0.38f, z, 0.6f, 0.6f, 0.6f, time * 40f, if (on) p.col else white, fog)
            }
        }
    }

    /** A tapering stack of shrinking boxes (no intersections). */
    private fun spike(x: Float, z: Float, h: Float, base: Float, yaw: Float, col: Color, fog: Float, cap: Color = col) {
        val n = 3
        for (i in 0 until n) {
            val s = base * (1f - i * 0.3f)
            val seg = h / n
            postSpin(x, seg * (i + 0.5f), z, s, seg, s, yaw, if (i == n - 1) cap else col, fog)
        }
    }

    /** Two tall pylons and a beam across the road, in the coming world's colour. The start gate adds bunting and flags. */
    private fun renderGate(g: Gate, time: Float) {
        val fog = Fog.at(g.z)
        val a = Fog.appear(g.z) // a world's gate comes up out of the distance
        val x = Lanes.halfRoadDrawn + 1.1f
        val h = if (g.start) 5.4f else 4.0f // the start gate stands taller: the camera passes clean under its bunting
        game.worldBox(-x, h / 2f, g.z, 0.5f, h, 0.5f, g.col, fog, a)
        game.worldBox(x, h / 2f, g.z, 0.5f, h, 0.5f, g.col, fog, a)
        game.worldBox(0f, h + 0.15f, g.z, x * 2f + 0.5f, 0.4f, 0.5f, g.col, fog, a)
        game.worldBox(0f, h + 0.55f, g.z, 1.4f, 0.4f, 0.5f, white, fog, a)
        if (g.start) { // a chequered start banner under the beam, a chequered line on the road, a spinning star on each pylon
            val n = 12
            val cw = (2 * x - 0.6f) / n
            for (i in 0 until n) {
                val bx = -x + 0.3f + cw * (i + 0.5f)
                game.worldBox(bx, h - 0.22f, g.z, cw, 0.36f, 0.2f, if (i % 2 == 0) white else dark, fog, a)
            }
            val m = 8
            val lw = (Lanes.halfRoadDrawn * 2f) / m
            for (i in 0 until m) for (k in 0 until 2) {
                game.worldBox(-Lanes.halfRoadDrawn + lw * (i + 0.5f), 0.0f, g.z + (k - 0.5f) * 0.55f, lw, 0.03f, 0.55f, if ((i + k) % 2 == 0) white else dark, fog, a)
            }
            hsvInto(edgeCol, 50f, 0.8f, 1f)
            game.worldBoxSpin(-x, h + 0.55f, g.z, 0.55f, 0.55f, 0.55f, time * 140f, edgeCol, fog, a)
            game.worldBoxSpin(x, h + 0.55f, g.z, 0.55f, 0.55f, 0.55f, -time * 140f, edgeCol, fog, a)
        }
    }
}
