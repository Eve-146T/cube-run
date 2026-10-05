package cube.run.game.space

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import cube.run.core.Gdx3DGame
import cube.run.core.SoundFx
import cube.run.core.hsvInto
import cube.run.game.track.ObShape
import cube.run.game.track.Row
import kotlin.math.max
import kotlin.math.min

/**
 * Outer Space while it lasts: the trip being flown, how far into space the
 * picture has turned ([blend]: the ground falls away, the sky becomes a
 * nebula), and the wave that runs down the road at a portal, turning every
 * obstacle it reaches into its space look (and back on the way out).
 *
 * Gameplay (gravity, sections) is the track's and the player's business;
 * this is the scene: [sky] (planets, stars, asteroids, warp streaks),
 * [look] (obstacles), the hum, and the colours the rest of the game blends in.
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
    val look = SpaceLook(game, this)
    private val audio = SpaceAudio()

    // The transformation wave: rows it has reached show their own look, the rest still show [lookBefore].
    private var waveFront = Float.POSITIVE_INFINITY
    private var lookBefore = false

    // trip colours, refreshed on entry
    val skyTop = Color(); val skyBottom = Color()
    val road = Color(); val roadAlt = Color()
    val neon = Color(); val neonSoft = Color()
    val rock = Color(); val rockDark = Color()
    val star = Color()
    private val tmpCol = Color()
    private val tmp = Vector3()

    /** Crossed into space. [instant] skips the fade (a run that starts inside, for testing). */
    fun enter(seed: Int, instant: Boolean = false) {
        val t = SpaceTrip(seed)
        trip = t
        inside = true
        travelled = 0f
        val n = t.nebula
        t.skyTop(skyTop); t.skyBottom(skyBottom)
        hsvInto(road, n.roadH, n.roadS, n.roadV)
        hsvInto(roadAlt, n.roadH + 12f, n.roadS * 0.9f, n.roadV * 1.25f)
        hsvInto(neon, n.neonH, 0.7f, 1f)
        hsvInto(neonSoft, n.neonH, 0.35f, 1f)
        hsvInto(rock, n.rockH, 0.42f, 0.86f)
        hsvInto(rockDark, n.rockH, 0.5f, 0.5f)
        hsvInto(star, n.starH, n.starS, 1f)
        sky.begin(t)
        if (instant) { blend = 1f; waveFront = Float.POSITIVE_INFINITY; lookBefore = true; warp = 0f }
        else { warp = 1f; startWave(lookBefore = false) }
        SoundFx.play("warp", vol = 0.9f)
    }

    /** Through the exit portal: the wave turns the road back, the picture fades home. */
    fun exit() {
        if (!inside) return
        inside = false
        warp = 0.6f
        startWave(lookBefore = true)
        SoundFx.play("warp", rate = 0.8f, vol = 0.8f)
    }

    fun reset() {
        trip = null; inside = false; blend = 0f; warp = 0f; travelled = 0f
        waveFront = Float.POSITIVE_INFINITY; lookBefore = false
        sky.clear()
        audio.stop()
    }

    private fun startWave(lookBefore: Boolean) {
        this.lookBefore = lookBefore
        waveFront = 0f
    }

    /** Does [row] show its space look right now? */
    fun showsSpace(row: Row): Boolean = if (row.z >= waveFront) row.spaceLook else lookBefore

    /**
     * One frame. [mv] is the world's movement, [alive] false once the run is
     * over (the hum fades out with it), [paused] stops the hum at once.
     */
    fun tick(dt: Float, mv: Float, time: Float, rows: List<Row>, alive: Boolean) {
        val target = if (inside) 1f else 0f
        blend += (target - blend) * min(1f, dt * (if (inside) 1.4f else 1.1f))
        if (!inside && blend < 0.004f) { blend = 0f; if (trip != null) { trip = null; sky.clear() } }
        if (inside && blend > 0.996f) blend = 1f
        warp = max(0f, warp - dt / 1.4f)
        if (inside) travelled += mv
        if (waveFront != Float.POSITIVE_INFINITY) {
            waveFront -= dt * 120f
            if (waveFront < -110f) { waveFront = Float.POSITIVE_INFINITY; lookBefore = inside }
        }
        game.burstGravity = 1f - 0.65f * blend
        for (row in rows) {
            val shown = showsSpace(row)
            if (shown != row.shownSpace) {
                row.shownSpace = shown
                if (row.pop > 0.3f && row.z > -70f) { // re-pop: it shrinks away and springs back transformed
                    row.pop = 0.001f; row.popStart = time
                    if (row.z > -45f && row.obs.isNotEmpty()) {
                        val col = if (shown) neon else Color.WHITE
                        game.burst3d(tmp.set(row.safeX(), 1f, row.z), col, n = 10, speed = 4f, size = 0.1f, life = 0.6f, gravity = 2f)
                    }
                }
            }
            if (trip != null && row.spaceLook) meteorCues(row, mv)
        }
        if (trip != null) sky.tick(dt, mv, time)
        audio.tick(if (alive) blend else 0f, dt)
    }

    /** A meteor row: a whistle as the rocks start to fall, a thud as they land. */
    private fun meteorCues(row: Row, mv: Float) {
        if (mv <= 0f || row.obs.none { it.shape == ObShape.METEOR }) return
        val before = row.z - mv
        if (before < SpaceLook.METEOR_FALL_START && row.z >= SpaceLook.METEOR_FALL_START) SoundFx.play("meteor", vol = 0.55f)
        if (before < SpaceLook.METEOR_LAND_Z && row.z >= SpaceLook.METEOR_LAND_Z) {
            SoundFx.play("moonland", rate = 0.7f, vol = 0.8f)
            for (ob in row.obs) if (ob.shape == ObShape.METEOR)
                game.burst3d(tmp.set(ob.x, 0.2f, row.z), rock, n = 8, speed = 3f, size = 0.12f, life = 0.9f, gravity = 1.5f)
        }
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

    /** Camera lean on lane changes (degrees), only in space: a calm glide rather than a snap. */
    fun bank(lateralVelocity: Float): Float = (-lateralVelocity * 0.55f).coerceIn(-4f, 4f) * blend

    /** The road tiles: blended toward this trip's road by [blend]. */
    fun roadTile(out: Color, base: Color, alt: Boolean): Color = out.set(base).lerp(if (alt) roadAlt else road, blend)

    fun kerb(out: Color, base: Color): Color = out.set(base).lerp(neon, blend)

    internal fun scratch(): Color = tmpCol
}
