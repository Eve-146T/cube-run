package cube.run.game.world.biome

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import cube.run.core.Gdx3DGame
import cube.run.data.Worlds
import cube.run.game.Lanes
import cube.run.game.world.Fog
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A biome around the road, at every distance at once, like Outer Space:
 *  - props on the land beside the road, scrolling with it and born in the haze;
 *  - a horizon of big shapes drifting by slowly (they sink away and the next
 *    biome's rise in their place when a gate is passed);
 *  - landmarks: one set piece at a time sliding past at a distance;
 *  - weather around the camera, and the biome's sky (a sun, a moon, stars).
 *
 * Props and landmarks are born for the world the road is being built for
 * (behind its gate); the horizon, weather and sky follow the world the
 * player is in. It keeps its own random numbers, never the course's.
 */
class BiomeScene(private val game: Gdx3DGame) {
    private val looks = arrayOfNulls<BiomeLook>(Worlds.all.size)
    private var rnd = Random(7)
    private val draw = BiomeDraw(game)
    val sky = SkyPainter(game)

    private val props = ArrayList<Piece>()
    private val far = ArrayList<Piece>()
    private val landmarks = ArrayList<Piece>()
    private val motes = ArrayList<Piece>()
    private val streams = ArrayList<Piece>()
    private val farthest = FloatArray(2)
    private var windPhase = 0f
    /** Which layers draw (tests measure them one by one): [SKY], [PROPS], [STREAMS], [FAR], [LANDMARKS], [MOTES]. */
    internal var layers = ALL
    /** 0…1: the biome fades out of the picture (the Red Pill strips the world to its wireframe). */
    var veil = 0f

    /** The world new props and landmarks are born for. */
    private var spawnLook: BiomeLook? = null
    /** The world the player is in: its horizon, weather and sky. */
    private var skyLook: BiomeLook? = null
    /** How much of each world's sky is up (by world id), easing over the gate's cross-fade. */
    private val weight = FloatArray(Worlds.all.size)
    private var nextLandmark = 0f
    private var landmarkTurn = 0
    private var landmarkSide = 1f

    /** [world]'s look (built, and its shapes uploaded, the first time it is asked for). */
    fun lookOf(world: Worlds.World): BiomeLook = looks[world.id] ?: BiomeLooks.of(world).also { looks[world.id] = it }
    private val prepared = BooleanArray(Worlds.all.size)

    /** Start over in [world], everything in place at once. [seed] fixes the layout (captures, tests). */
    fun init(world: Worlds.World, seed: Int = Random.nextInt()) {
        rnd = Random(seed)
        val l = lookOf(world)
        spawnLook = l; skyLook = l
        weight.fill(0f); weight[world.id] = 1f
        props.clear(); far.clear(); landmarks.clear(); motes.clear(); streams.clear()
        windPhase = rnd.nextFloat() * 6.28f
        for (i in 0 until STREAM_STEPS) for (side in 0..1) streams.add(Piece().also { seedStream(it, l, side, 8f - i * STREAM_STEP) })
        for (side in 0..1) {
            var z = 10f - rnd.nextFloat() * l.propGap
            while (z > PROP_FAR) { props.add(Piece().also { seedProp(it, l, side, z) }); z -= gap(l) }
            farthest[side] = z + gap(l)
        }
        repeat(FAR_COUNT) { far.add(Piece().also { seedFar(it, l, FAR_NEAR - rnd.nextFloat() * (FAR_NEAR - FAR_SPAWN)); it.show = 1f }) }
        repeat(MOTE_POOL) { motes.add(Piece()) }
        nextLandmark = 40f + rnd.nextFloat() * 60f
        landmarkTurn = rnd.nextInt(8)
        landmarkSide = if (rnd.nextBoolean()) 1f else -1f
    }

    /** Props and landmarks born from now on belong to [world] (a gate was dropped at the horizon). */
    fun setWorld(world: Worlds.World) { spawnLook = lookOf(world) }

    /** The player passed the gate into [world]: its horizon rises, its weather and sky come in. */
    fun enter(world: Worlds.World) { skyLook = lookOf(world) }

    private fun gap(l: BiomeLook) = l.propGap * (0.7f + rnd.nextFloat() * 0.6f)

    private fun seedProp(p: Piece, l: BiomeLook, side: Int, z: Float) {
        p.look = l; p.z = z; p.y = 0f; p.yaw = 0f; p.s = 1f; p.kind = 0
        p.seed = rnd.nextFloat()
        l.seedProp(p, rnd)
        if (side == 0) p.x = -p.x
    }

    /** One step of each side's river; the rivers wind a little further with every row. */
    private fun seedStream(p: Piece, l: BiomeLook, side: Int, z: Float) {
        p.look = l; p.z = z; p.seed = rnd.nextFloat()
        if (side == 0) windPhase += 0.21f
        val wind = sin(windPhase + side * 2.1f) * 0.7f + sin(windPhase * 0.37f + side) * 0.3f
        p.x = (if (side == 0) -1f else 1f) * l.streamX(wind)
        p.s = l.streamWidth
        p.kind = if (l.stream) 1 else 0
    }

    private fun seedFar(p: Piece, l: BiomeLook, z: Float) {
        p.look = l; p.z = z; p.y = 0f; p.yaw = 0f; p.s = 1f; p.kind = 0; p.rate = FAR_RATE
        p.seed = rnd.nextFloat()
        p.show = 0f
        l.seedFar(p, rnd)
    }

    /** Scroll by [mv] road units. (Index loops throughout the per-frame paths: no iterators, no garbage.) */
    fun scroll(mv: Float) {
        val spawn = spawnLook ?: return
        farthest[0] = 0f; farthest[1] = 0f
        for (i in props.indices) {
            val p = props[i]
            p.z += mv
            val side = if (p.x < 0f) 0 else 1
            farthest[side] = min(farthest[side], p.z)
        }
        for (i in props.indices) {
            val p = props[i]
            if (p.z <= PROP_GONE) continue
            val side = if (p.x < 0f) 0 else 1 // round again: the next prop on its side, out in the haze
            val z = min(farthest[side] - gap(spawn), PROP_FAR)
            seedProp(p, spawn, side, z)
            farthest[side] = z
        }
        for (i in streams.indices) {
            val p = streams[i]
            p.z += mv
            if (p.z > STREAM_GONE) seedStream(p, spawn, if (p.x < 0f) 0 else 1, p.z - STREAM_STEPS * STREAM_STEP)
        }
        val sky = skyLook
        for (i in far.indices) {
            val p = far[i]
            p.z += mv * p.rate
            if (p.z > FAR_NEAR && sky != null) { seedFar(p, sky, FAR_SPAWN - rnd.nextFloat() * 30f); p.show = 1f }
        }
        var i = landmarks.size - 1
        while (i >= 0) {
            val p = landmarks[i]
            p.z += mv * p.rate; p.travelled += mv
            val depth = CAMERA_Z - p.z
            val gone = depth < 12f || abs(p.x) - (p.look?.landmarkReach(p) ?: 0f) > depth * VIEW_W
            if (gone) landmarks.removeAt(i)
            i--
        }
        nextLandmark -= mv
        if (nextLandmark <= 0f && landmarks.isEmpty() && spawn.landmarkKinds > 0) {
            val p = Piece()
            p.look = spawn; p.kind = landmarkTurn++ % spawn.landmarkKinds
            p.z = LANDMARK_SPAWN; p.rate = 0.5f; p.travelled = 0f; p.y = 0f; p.s = 1f
            p.seed = rnd.nextFloat()
            spawn.seedLandmark(p, rnd)
            if (!spawn.landmarkOverRoad(p)) p.x = max(abs(p.x), spawn.landmarkReach(p) + BiomeLook.LANDMARK_CLEAR)
            p.x = abs(p.x) * landmarkSide
            landmarkSide = -landmarkSide
            landmarks.add(p)
            nextLandmark = 160f + rnd.nextFloat() * 120f
        }
    }

    /** Ease the sky weights; drift the weather. */
    fun tick(dt: Float, mv: Float) {
        val sky = skyLook ?: return
        for (id in weight.indices) {
            val target = if (id == sky.world.id) 1f else 0f
            weight[id] = if (target > weight[id]) min(1f, weight[id] + dt / FADE) else max(0f, weight[id] - dt / FADE)
        }
        for (i in far.indices) {
            val p = far[i]
            val l = p.look ?: continue
            val target = if (l === sky) 1f else 0f
            p.show = if (target > p.show) min(1f, p.show + dt / FADE) else max(0f, p.show - dt / FADE)
            if (p.show <= 0f && l !== sky) seedFar(p, sky, p.z) // sunk: the new world's shape rises in its place
        }
        // weather: the sky world's motes, as many as its weight allows
        val want = (sky.motes * weight[sky.world.id]).toInt()
        var have = 0
        for (i in motes.indices) { val p = motes[i]; if (p.look === sky && p.life > 0f) have++ }
        var started = 0
        for (i in motes.indices) {
            val p = motes[i]
            if (p.life > 0f) {
                p.z += mv * p.rate
                if (!p.look!!.moveMote(p, dt) || p.z > 12f) p.life = 0f
                continue
            }
            if (have < want && started < 4) {
                p.look = sky; p.rate = 1f; p.vx = 0f; p.vy = 0f; p.vz = 0f; p.yaw = 0f; p.s = 1f
                p.seed = rnd.nextFloat()
                sky.seedMote(p, rnd, anywhere = weight[sky.world.id] < 0.99f || want - have > sky.motes / 2)
                have++; started++
            }
        }
    }

    /** Queue the biome's facets. [drop]: how far the land has fallen away (Outer Space hides the biome). */
    fun render(time: Float, drop: Float) {
        if (drop >= 60f || veil >= 1f) return
        for (id in looks.indices) { // upload each biome's shapes before they first show
            val l = looks[id] ?: continue
            if (!prepared[id]) { prepared[id] = true; game.facets.prepare(*l.shapes.toTypedArray()) }
        }
        val base = game.worldOpacity * (1f - drop / 60f) * (1f - veil)
        draw.drop = drop
        draw.opacity = base
        if (layers and SKY != 0) for (id in weight.indices) if (weight[id] > 0.004f) looks[id]?.drawSky(draw, weight[id], time)
        if (layers and PROPS != 0) for (i in props.indices) { val p = props[i]; p.look?.drawProp(draw, p, Fog.at(p.z), time) }
        if (layers and STREAMS != 0) {
            val edge = Lanes.halfRoadDrawn + KERB_OUT
            for (i in streams.indices) {
                val p = streams[i]
                if (p.kind != 1) continue
                val x = p.x // out from the kerb, wherever the road's edge is
                p.x = if (x < 0f) x - edge else x + edge
                p.look?.drawStream(draw, p, Fog.at(p.z), time)
                p.x = x
            }
        }
        if (layers and FAR != 0) for (i in far.indices) {
            val p = far[i]
            val l = p.look ?: continue
            // shapes rise out of the horizon and sink back into it (solid: a fade would cost a second pass)
            val up = p.show * smooth((p.z - FAR_SPAWN) / 30f) * smooth((FAR_NEAR - p.z) / 25f)
            if (up <= 0.01f) continue
            val y = p.y
            p.y -= (1f - up) * (1f - up) * SINK
            l.drawFar(draw, p, FAR_HAZE, time)
            p.y = y
        }
        if (layers and LANDMARKS != 0) for (i in landmarks.indices) {
            val p = landmarks[i]
            val l = p.look ?: continue
            draw.opacity = base * smooth(p.travelled / 70f)
            l.drawLandmark(draw, p, LANDMARK_HAZE * (1f - smooth(p.travelled / 260f)), time)
        }
        if (layers and MOTES != 0) for (i in motes.indices) {
            val p = motes[i]
            if (p.life <= 0f) continue
            val l = p.look ?: continue
            val w = weight[l.world.id]
            if (w <= 0f) continue
            draw.opacity = base * w
            l.drawMote(draw, p, Fog.at(p.z), time)
        }
        draw.opacity = base
    }

    /** Sunbursts and the like (world shapes pass, the sky's bend switched off). [amount]: how much of the world is showing. */
    fun renderShapes(shapes: ShapeRenderer, time: Float, drop: Float, amount: Float) {
        val a = amount * (1f - drop / 60f) * (1f - veil)
        if (a <= 0.004f) return
        sky.beginWorld(shapes)
        for (id in weight.indices) if (weight[id] > 0.004f) looks[id]?.skyRays(sky, weight[id] * a, time)
        sky.endPlane()
    }

    /** Stars and glows painted behind everything. */
    fun renderBackdrop(shapes: ShapeRenderer, cam: Camera, time: Float, drop: Float, amount: Float) {
        val a = amount * (1f - drop / 60f) * (1f - veil)
        if (a <= 0.004f) return
        sky.beginPlane(shapes, cam)
        for (id in weight.indices) if (weight[id] > 0.004f) looks[id]?.paintSky(sky, weight[id] * a, time)
        sky.endPlane()
    }

    /** Live pieces by layer: props, horizon, landmarks, motes (for budget tests). */
    val counts: IntArray get() = intArrayOf(props.size, far.size, landmarks.size, motes.count { it.life > 0f })

    companion object {
        const val SKY = 1; const val PROPS = 2; const val STREAMS = 4; const val FAR = 8; const val LANDMARKS = 16; const val MOTES = 32
        const val ALL = 63
        private fun smooth(v: Float): Float { val t = v.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
        /** Props are born here, in the haze, and recycled once behind the camera. */
        const val PROP_FAR = -112f
        const val PROP_GONE = 14f
        /** River steps: one tile long, as far as the road reaches. */
        const val STREAM_STEP = 3f
        const val STREAM_STEPS = 40
        const val STREAM_GONE = 9.5f
        /** The kerb's outer edge, past the road's. */
        const val KERB_OUT = 0.36f
        /** The horizon: shapes come up at [FAR_SPAWN] and leave at [FAR_NEAR], closing in at [FAR_RATE]. */
        const val FAR_SPAWN = -330f
        const val FAR_NEAR = -150f
        const val FAR_RATE = 0.22f
        const val FAR_COUNT = 12
        const val FAR_HAZE = 0.42f
        const val SINK = 70f
        const val LANDMARK_SPAWN = -340f
        const val LANDMARK_HAZE = 0.3f
        const val MOTE_POOL = 48
        /** The sky cross-fade at a gate (matches the WorldRunner's). */
        const val FADE = 2.4f
        const val CAMERA_Z = 6f
        /** Half the view's width as a tangent, with room for the camera's sway. */
        const val VIEW_W = 0.34f
    }
}
