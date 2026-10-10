package cube.run.game.world.biome

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import cube.run.core.Gdx3DGame
import cube.run.data.Worlds
import cube.run.game.Lanes
import cube.run.game.world.Fog
import cube.run.core.gfx.fastMagnitude as abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A biome around the road, at every distance at once, like Outer Space:
 *  - props on the land beside the road, scrolling with it and born in the haze;
 *  - a horizon of big shapes drifting by slowly (they fade into one another
 *    when a gate is passed);
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
    // The opening constructs this scene before the renderer's facet batch exists.
    private val draw by lazy { BiomeDraw(game) }
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

    /** Build and upload every biome before gameplay, rather than hitch at its first gate. */
    fun prepareAll() {
        draw.begin()
        for (world in Worlds.all) {
            val look = lookOf(world)
            if (!prepared[world.id]) {
                game.facets.prepare(*look.shapes.toTypedArray())
                look.prepareSky(sky)
                prepared[world.id] = true
            }
        }
    }

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
        if (horizon != NONE) repeat(FAR_COUNT) { i ->
            far.add(Piece().also { it.index = i; it.layer = if (horizon == LAYERED) i % 2 else 0; seedFar(it, l, farZ(i)); it.show = 1f })
        }
        repeat(MOTE_POOL) { motes.add(Piece()) }
        for (i in 0 until l.motes) { // the scene starts with its weather already about
            val p = motes[i]
            p.look = l; p.rate = 1f; p.seed = rnd.nextFloat(); p.show = 1f
            l.seedMote(p, rnd, anywhere = true)
        }
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
        p.look = l; p.z = z; p.y = 0f; p.yaw = 0f; p.s = 1f; p.kind = 0
        p.rate = when (horizon) { DRIFT -> FAR_RATE; CREEP -> CREEP_RATE; else -> 0f }
        p.seed = rnd.nextFloat()
        p.show = 0f
        l.seedFar(p, rnd)
        if (p.layer == 1) { p.s *= 0.5f; p.y -= 2f } // the ridge in front: lower and smaller, so the range shows over it
        if (horizon == LOW) p.s *= 0.6f
    }

    /** Where a horizon shape stands when the scene starts or a gate swaps it, by [horizon] style. */
    private fun farZ(i: Int): Float = when (horizon) {
        DRIFT -> FAR_NEAR - rnd.nextFloat() * (FAR_NEAR - FAR_SPAWN)
        LAYERED -> if (i % 2 == 1) -185f - rnd.nextFloat() * 20f else -300f + rnd.nextFloat() * 25f
        else -> -300f + rnd.nextFloat() * 70f // a panorama, as good as infinitely far
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
            if (p.z > FAR_NEAR && sky != null) { seedFar(p, sky, FAR_SPAWN - rnd.nextFloat() * 30f); p.show = 1f } // drifted past: round again
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
            weight[id] = approach(weight[id], target, dt / FADE)
        }
        for (i in far.indices) {
            val p = far[i]
            val l = p.look ?: continue
            val target = if (l === sky) 1f else 0f
            p.show = approach(p.show, target, dt / FADE)
            if (p.show <= 0f && l !== sky) seedFar(p, sky, p.z) // faded out: the new world's shape fades in in its place
        }
        // weather: the sky world's motes, as many as its weight allows
        val want = (sky.motes * weight[sky.world.id]).toInt()
        var have = 0
        for (i in motes.indices) { val p = motes[i]; if (p.look === sky && p.life > 0f) have++ }
        var started = 0
        for (i in motes.indices) {
            val p = motes[i]
            if (p.life > 0f) {
                p.show = min(1f, p.show + dt / MOTE_FADE)
                p.z += mv * p.rate
                if (!p.look!!.moveMote(p, dt) || p.z > MOTE_GONE) p.life = 0f
                continue
            }
            if (have < want && started < 4) {
                p.look = sky; p.rate = 1f; p.vx = 0f; p.vy = 0f; p.vz = 0f; p.yaw = 0f; p.s = 1f; p.show = 0f
                p.seed = rnd.nextFloat()
                sky.seedMote(p, rnd, anywhere = weight[sky.world.id] < 0.99f) // arriving with its biome; otherwise out of sight
                have++; started++
            }
        }
    }

    /** Queue the biome's facets. [drop]: how far the land has fallen away (Outer Space hides the biome). */
    fun render(time: Float, drop: Float) {
        if (drop >= 60f || veil >= 1f) return
        val draw = this.draw
        draw.begin()
        for (id in looks.indices) { // upload each biome's shapes before they first show
            val l = looks[id] ?: continue
            if (!prepared[id]) { prepared[id] = true; game.facets.prepare(*l.shapes.toTypedArray()) }
        }
        val base = game.worldOpacity * (1f - drop / 60f) * (1f - veil)
        draw.drop = drop
        draw.opacity = base
        if (layers and SKY != 0) for (id in weight.indices) if (weight[id] > 0.004f) looks[id]?.drawSky(draw, weight[id], time)
        if (layers and PROPS != 0) for (i in props.indices) {
            val p = props[i]
            draw.opacity = base * Fog.appear(p.z) // out of the distance, not out of nowhere
            p.look?.drawProp(draw, p, Fog.at(p.z), time)
        }
        if (layers and STREAMS != 0) {
            val edge = Lanes.halfRoadDrawn + KERB_OUT
            for (i in streams.indices) {
                val p = streams[i]
                if (p.kind != 1) continue
                val x = p.x // out from the kerb, wherever the road's edge is
                p.x = if (x < 0f) x - edge else x + edge
                draw.opacity = base * Fog.appear(p.z)
                p.look?.drawStream(draw, p, Fog.at(p.z), time)
                p.x = x
            }
        }
        if (layers and FAR != 0) for (i in far.indices) {
            val p = far[i]
            val l = p.look ?: continue
            if (p.index >= l.farCount) continue // this biome keeps its horizon sparser
            // horizon shapes never slide: they fade in where they come up, out where they leave, and swap at a gate by fading
            val travel = if (p.rate > 0f) smooth((p.z - FAR_SPAWN) / 30f) * smooth((FAR_NEAR - p.z) / 25f) else 1f
            draw.opacity = base * p.show * travel
            draw.tall = if (horizon == LOW) l.horizonTall else 1f
            l.drawFar(draw, p, if (p.layer == 1) FAR_HAZE * 0.45f else FAR_HAZE, time)
            draw.tall = 1f
        }
        if (layers and LANDMARKS != 0) for (i in landmarks.indices) {
            val p = landmarks[i]
            val l = p.look ?: continue
            draw.opacity = base * smooth(p.travelled / LANDMARK_FADE)
            l.drawLandmark(draw, p, LANDMARK_HAZE * (1f - smooth(p.travelled / 260f)), time)
        }
        if (layers and MOTES != 0) for (i in motes.indices) {
            val p = motes[i]
            if (p.life <= 0f) continue
            val l = p.look ?: continue
            val w = weight[l.world.id]
            if (w <= 0f) continue
            draw.opacity = base * w * p.show * Fog.appear(p.z) // weather fades in wherever it starts
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
        /** [v] moved [step] toward [target], stopping there (a value already there stays put). */
        private fun approach(v: Float, target: Float, step: Float) = if (v < target) min(target, v + step) else max(target, v - step)
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
        const val CREEP_RATE = 0.03f

        // Horizon styles, to compare (debug builds: --ei horizon N)
        /** Shapes come up far off, drift slowly closer and leave, fading in and out. */
        const val DRIFT = 0
        /** A still panorama, as if infinitely far; it swaps at a gate by fading. */
        const val PANORAMA = 1
        /** The panorama, closing in very slowly: you feel the travel but never see it slide. */
        const val CREEP = 2
        /** A still range with a lower, crisper ridge in front of it. */
        const val LAYERED = 3
        /** Sky only. */
        const val NONE = 4
        /** The still panorama, smaller: a low range far away. */
        const val LOW = 5
        @Volatile var horizon = LOW
        const val LANDMARK_SPAWN = -340f
        const val LANDMARK_HAZE = 0.3f
        /** Road units a landmark takes to fade in: quick, or a big arch hangs there half-there. */
        const val LANDMARK_FADE = 30f
        const val MOTE_POOL = 48
        /** Motes are gone this far behind the camera (traffic overtaking you starts back there). */
        const val MOTE_GONE = 40f
        /** Seconds a mote takes to fade in. */
        const val MOTE_FADE = 1.2f
        /** The sky cross-fade at a gate (matches the WorldRunner's). */
        const val FADE = 2.4f
        const val CAMERA_Z = 6f
        /** Half the view's width as a tangent, with room for the camera's sway. */
        const val VIEW_W = 0.34f
    }
}
