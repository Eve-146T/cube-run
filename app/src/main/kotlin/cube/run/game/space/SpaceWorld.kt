package cube.run.game.space

import com.badlogic.gdx.graphics.Color
import cube.run.core.Gdx3DGame
import cube.run.core.SoundFx
import cube.run.core.gfx.WorldBend
import cube.run.core.hsvInto
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Outer Space while it lasts: the trip being flown and how far into space
 * the picture has turned ([blend]: the ground falls away, the sky turns to
 * the dark of space).
 *
 * Gameplay (gravity, sections) is the track's and the player's business;
 * this is the scene: [sky] (planets, stars, asteroids, warp streaks), the
 * hum, and the colours the rest of the game blends in. The obstacles keep
 * their ordinary look.
 */
class SpaceWorld(private val game: Gdx3DGame) {

    /** The trip on screen; it outlives the exit until the picture has faded back. */
    var trip: SpaceTrip? = null
        private set
    /** Inside space (from the entry portal to the exit portal). */
    var inside = false
        private set
    /** 0 → 1: how much of the picture is space. */
    var blend = 0f
        private set
    /** 1 → 0: the hyperspace burst at a portal. */
    var warp = 0f
        private set
    /** World units flown since the entry portal (drives the trip's slow set pieces). */
    var travelled = 0f
        private set

    val sky = SpaceSky(game, this)
    val deco = SpaceDeco(this)
    private val audio = SpaceAudio()

    // trip colours, refreshed on entry
    val skyTop = Color(); val skyBottom = Color()
    val road = Color(); val roadAlt = Color()
    val neon = Color(); val neonSoft = Color()
    val rock = Color(); val rockAccent = Color()
    val star = Color()

    /** Crossed into space. [instant] skips the fade (a run that starts inside, for testing). */
    fun enter(seed: Int, instant: Boolean = false) {
        val t = SpaceTrip(seed)
        trip = t
        inside = true
        travelled = 0f
        val n = t.nebula
        t.skyTop(skyTop); t.skyBottom(skyBottom)
        hsvInto(road, n.roadH, n.roadS, n.roadV)
        hsvInto(roadAlt, n.roadH + 10f, n.roadS * 0.85f, n.roadV * 1.35f) // a clear checker, like every road
        hsvInto(neon, n.neonH, 0.7f, 1f)
        hsvInto(neonSoft, n.neonH, 0.35f, 1f)
        hsvInto(rock, n.rockH, 0.6f, 1f)
        hsvInto(rockAccent, n.rockH + 28f, 0.72f, 0.95f)
        hsvInto(star, n.starH, n.starS, 1f)
        sky.begin(t)
        deco.begin(t)
        if (instant) { blend = 1f; warp = 0f } else warp = 1f
        SoundFx.play("warp", vol = 0.9f)
    }

    /** Through the exit portal: the picture fades home. */
    fun exit() {
        if (!inside) return
        inside = false
        warp = 0.6f
        SoundFx.play("warp", rate = 0.8f, vol = 0.8f)
    }

    fun reset() {
        trip = null; inside = false; blend = 0f; warp = 0f; travelled = 0f
        bendX = 0f; bendY = 0f; WorldBend.x = 0f; WorldBend.y = 0f
        sky.clear(); deco.clear()
        audio.stop()
        game.burstGravity = 1f
    }

    /**
     * One frame. [mv] is the world's movement, [alive] false once the run is
     * over (the hum fades out with it).
     */
    fun tick(dt: Float, mv: Float, time: Float, alive: Boolean) {
        val target = if (inside) 1f else 0f
        // in: eased; out: a steady 1.6 s, so nothing of space lingers in the next world's sky
        blend = if (inside) blend + (target - blend) * min(1f, dt * 1.4f) else max(0f, blend - dt / 1.6f)
        if (!inside && blend < 0.004f) { blend = 0f; if (trip != null) { trip = null; sky.clear(); deco.clear() } }
        if (inside && blend > 0.996f) blend = 1f
        warp = max(0f, warp - dt / 1.4f)
        if (inside) travelled += mv
        game.burstGravity = 1f - 0.65f * blend
        bend(dt)
        trip?.let {
            deco.shower = it.weatherAt(SpaceTrip.SHOWER, travelled)
            deco.clouds = it.weatherAt(SpaceTrip.NEBULA_CLOUD, travelled)
            sky.tick(dt, mv, time); deco.tick(dt)
        }
        audio.tick(if (alive) blend else 0f, dt)
    }

    // The road's sweep (see [WorldBend]): eased toward a slow wander through the trip, gone outside space.
    private var bendX = 0f
    private var bendY = 0f

    /**
     * Space's road winds: it sweeps left and right in long bends and rolls
     * over gentle rises and dips, at the pace of the trip, so the road ahead
     * is never quite where you expect it. Gameplay stays straight.
     */
    private fun bend(dt: Float) {
        val seed = trip?.starSeed ?: 0
        val a = (seed and 0xFF) / 40f; val b = (seed ushr 8 and 0xFF) / 40f
        val k = travelled
        val wantX = if (inside) BEND_X * (0.65f * sin(k * 0.0105f + a) + 0.35f * sin(k * 0.0047f + b)) else 0f
        val wantY = if (inside) BEND_Y * (0.7f * sin(k * 0.0079f + b) - 0.3f) else 0f
        val ease = min(1f, dt * 1.2f)
        bendX += (wantX - bendX) * ease; bendY += (wantY - bendY) * ease
        WorldBend.x = bendX * blend; WorldBend.y = bendY * blend
    }

    /** Blend the sky toward the nebula. */
    fun tintSky(top: Color, bottom: Color) {
        if (blend <= 0f) return
        top.lerp(skyTop, blend); bottom.lerp(skyBottom, blend)
    }

    /** The haze sits low in space: more horizon, less zenith. */
    fun fogMix(base: Float): Float = base + (0.3f - base) * blend

    /** The run was paused or the app left the foreground: silence the hum (the next frame restarts it). */
    fun pauseAudio() = audio.stop()

    /** Camera lean (degrees), only in space: a calm glide on lane changes, and into the road's bends. */
    fun bank(lateralVelocity: Float): Float = ((-lateralVelocity * 0.55f).coerceIn(-4f, 4f) - bendX * 700f) * blend

    /** The road tiles: blended toward this trip's road by [blend]. */
    fun roadTile(out: Color, base: Color, alt: Boolean): Color = out.set(base).lerp(if (alt) roadAlt else road, blend)

    fun kerb(out: Color, base: Color): Color = out.set(base).lerp(neon, blend)

    private companion object {
        /** Sideways curvature at the widest bend (units per unit² ahead): the road 60 units on swings ~12 aside. */
        const val BEND_X = 0.0035f
        /** Up/down curvature: mostly dipping away over a horizon, now and then rising. */
        const val BEND_Y = 0.0016f
    }
}
