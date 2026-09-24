package cube.run.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import cube.run.core.Gdx3DGame
import cube.run.core.Haptics
import cube.run.core.JackpotBeats
import cube.run.core.JackpotBeats.BANKED
import cube.run.core.JackpotBeats.BURST
import cube.run.core.JackpotBeats.END
import cube.run.core.JackpotBeats.GATHER
import cube.run.core.JackpotBeats.RETURN
import cube.run.core.JackpotBeats.SLAM
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.core.hsvInto
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Gambler's jackpot, played in the run's own world. The run freezes on
 * the winning pickup; the cube lifts off and turns gold while the camera
 * cranes round; rays blow open behind it, a shockwave rolls down the road
 * and shatters every obstacle ahead; the cube erupts in a fountain of real
 * 3D coins that rain and bounce over the road while the HUD counter rolls;
 * the counter slams, the coins spiral back into the cube, and the camera
 * swings back to the chase on a road blasted clear. Timing lives in
 * [JackpotBeats]; the conductor only freezes the run and banks the coins.
 */
class Jackpot(private val game: Gdx3DGame, private val rnd: Random) {

    var active = false
        private set
    private var t = 0f
    private var amount = 0
    private var banked = false

    // where the cube was and how the chase camera looked when the jackpot hit
    private var homeX = 0f
    private var homeY = 0f
    private val camFrom = Vector3()
    private val lookFrom = Vector3()
    private val upFrom = Vector3()
    private var fovFrom = 60f
    private val skyTop = Color()
    private val skyBottom = Color()

    // the cube's show pose
    var cubeY = 0f
        private set
    var cubeYaw = 0f
        private set
    var cubeTip = 0f
        private set
    var cubeScale = 1f
        private set
    var cubeGold = 0f
        private set
    private var spin = 0f
    private var yawAtReturn = Float.NaN
    private var punch = 0f

    private val gold = Color()
    private val face = Color()
    private val warm = Color()
    private val rose = Color()
    private val white = Color(1f, 1f, 1f, 1f)
    private val tmp = Vector3()
    private val look = Vector3()

    // coins: x y z vx vy vz yaw spin r | gather: sx sy sz delay
    private val cap = 260
    private val stride = 13
    private val c = FloatArray(cap * stride)
    private val state = IntArray(cap)   // 0 gone, 1 loose, 2 gathering
    private var coins = 0
    private var emitCarry = 0f
    private var absorbed = 0
    private var gatherTotal = 0

    // sound pacing
    private var nextRoll = 0f
    private var nextChime = 0f
    private var nextTick = 0f
    private var lastChime = 0f

    init {
        hsvInto(gold, 45f, 0.85f, 1f)
        hsvInto(face, 50f, 0.55f, 1f)
        hsvInto(warm, 44f, 0.55f, 1f)
        hsvInto(rose, 332f, 0.5f, 0.92f)
    }

    /** The clock the counter reads; -1 when nothing plays. */
    val clock get() = if (active) t else -1f

    /**
     * A win. Starts the show from the chase camera and sky as they are now,
     * or adds to the one already playing.
     */
    fun start(won: Int, px: Float, py: Float, cam: PerspectiveCamera, top: Color, bottom: Color) {
        if (won <= 0) return
        amount = (amount.toLong() + won).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        Stage.jackpotAmount = amount
        if (active) return
        active = true; banked = false
        t = 0f; spin = 0f; cubeYaw = 0f; cubeTip = 0f; punch = 0f; yawAtReturn = Float.NaN
        homeX = px; homeY = py
        camFrom.set(cam.position)
        lookFrom.set(cam.direction).scl(12f).add(cam.position)
        upFrom.set(cam.up)
        fovFrom = cam.fieldOfView
        skyTop.set(top); skyBottom.set(bottom)
        coins = 0; emitCarry = 0f; absorbed = 0; gatherTotal = 0
        java.util.Arrays.fill(state, 0)
        nextRoll = 0.3f; nextChime = BURST; nextTick = BURST; lastChime = -1f
        Stage.jackpotClock = 0f
        game.releaseSlowMo()
        // The hit: everything stops dead on the winning coin.
        SoundFx.play("boom", rate = 0.55f)
        SoundFx.play("whoosh", rate = 0.7f, vol = 0.8f)
        Haptics.heavy()
        game.flash(white, 0.35f)
        game.burst3d(tmp.set(px, py, 0f), gold, n = 26, speed = 5f, size = 0.12f, life = 0.8f, gravity = 2f)
        game.burst3d(tmp, white, n = 10, speed = 7f, size = 0.08f, life = 0.5f, gravity = 2f)
    }

    /** True once the counter has landed in the HUD: the conductor shows the new total then. */
    fun takeBanked(): Boolean {
        if (!active || banked || t < BANKED) return false
        banked = true
        return true
    }

    /** How far down the road the shockwave has shattered (z, negative = ahead); +∞ before the burst. */
    val waveZ get() = if (active && t >= BURST) -(t - BURST) * 60f else Float.POSITIVE_INFINITY

    /** Advance the show; on its last frame it hands the clock back to the run. */
    fun update(dt: Float) {
        if (!active) return
        val before = t
        t += dt
        Stage.jackpotClock = t
        fun crossed(beat: Float) = before < beat && t >= beat

        // ---- the cube: lift off, spin up, turn gold; settle back down on the lane
        val lift = JackpotBeats.span(t, 0.05f, 1.2f) * (1f - JackpotBeats.span(t, RETURN, END - 0.1f))
        cubeY = homeY + lift * (2.35f - homeY * 0.5f).coerceAtLeast(0.6f) + lift * 0.1f * sin(t * 2.6f)
        cubeGold = JackpotBeats.span(t, 0.1f, 1.0f) * (1f - JackpotBeats.span(t, RETURN + 0.2f, END))
        val spinTarget = when {
            t < BURST -> 120f + 1100f * JackpotBeats.span(t, 0.2f, BURST)
            t < SLAM -> 1200f - 1020f * JackpotBeats.span(t, BURST, BURST + 1.2f)
            t < RETURN -> 180f + 900f * JackpotBeats.span(t, GATHER, GATHER + 0.4f) * (1f - JackpotBeats.span(t, GATHER + 0.7f, RETURN))
            else -> 0f
        }
        spin += (spinTarget - spin) * min(1f, dt * 6f)
        if (t < RETURN) cubeYaw += spin * dt
        else {
            // A cube looks the same every quarter turn: glide on to the next one and hand back square.
            if (yawAtReturn.isNaN()) yawAtReturn = cubeYaw
            val landing = (kotlin.math.floor(yawAtReturn / 90f) + 2f) * 90f
            cubeYaw = yawAtReturn + (landing - yawAtReturn) * JackpotBeats.span(t, RETURN, END - 0.15f)
        }
        cubeTip = 14f * sin(t * 3.1f) * JackpotBeats.span(t, 0.3f, 1.2f) * (1f - JackpotBeats.span(t, RETURN, END - 0.2f))
        punch = max(0f, punch - dt * 3.2f)
        cubeScale = 1f + 0.28f * JackpotBeats.span(t, 0.2f, BURST) * (1f - JackpotBeats.span(t, SLAM, RETURN + 0.4f)) + punch * 0.35f

        // ---- sound and touch, beat by beat
        if (t < BURST && t >= nextRoll) { // a drum roll that races into the burst
            val k = (t / BURST).coerceIn(0f, 1f)
            SoundFx.play("tap", rate = 0.8f + 0.9f * k, vol = 0.35f + 0.45f * k)
            if (k > 0.35f) Haptics.tick()
            nextRoll = t + 0.17f - 0.14f * k
        }
        if (crossed(0.12f)) SoundFx.play("rise", rate = 0.6f, vol = 0.8f)
        if (crossed(0.75f)) SoundFx.play("rise", rate = 0.9f, vol = 0.8f)
        if (crossed(BURST)) burst()
        if (crossed(SLAM)) slam()
        if (crossed(GATHER)) { SoundFx.play("whoosh", rate = 0.85f); beginGather() }
        if (crossed(RETURN)) SoundFx.play("whoosh", rate = 1.25f, vol = 0.8f)
        if (crossed(BANKED)) { SoundFx.play("coin", rate = 1.5f); SoundFx.play("perfect", rate = 1.3f, vol = 0.7f); Haptics.success() }
        if (t in BURST..SLAM) {
            val k = (t - BURST) / (SLAM - BURST)
            if (t >= nextChime) { // the coins pouring out: chimes that climb with the counter
                SoundFx.play("coin", rate = 0.95f + 0.55f * k + rnd.nextFloat() * 0.06f, vol = 0.55f)
                nextChime = t + 0.06f + 0.05f * k
            }
            if (t >= nextTick) { Haptics.tick(); nextTick = t + 0.09f + 0.1f * k }
        }

        // ---- sparkle round the cube while it glows
        if (cubeGold > 0.3f && rnd.nextFloat() < 0.6f) {
            val a = rnd.nextFloat() * 6.2832f
            game.burst3d(tmp.set(homeX + cos(a) * 0.9f, cubeY + (rnd.nextFloat() - 0.5f) * 1.2f, sin(a) * 0.9f),
                if (rnd.nextBoolean()) gold else white, n = 1, speed = 0.6f, size = 0.06f, life = 0.6f, gravity = -1.5f)
        }

        emit(dt)
        moveCoins(dt)

        if (t >= END) finish()
    }

    private fun burst() {
        SoundFx.play("boom", rate = 0.8f)
        SoundFx.play("fanfare")
        SoundFx.play("success", rate = 1.0f, vol = 0.7f)
        Haptics.heavy()
        game.flash(white, 0.6f)
        punch = 1f
        game.burst3d(tmp.set(homeX, cubeY, 0f), gold, n = 60, speed = 11f, size = 0.16f, life = 1.2f, gravity = 8f)
        game.burst3d(tmp, white, n = 24, speed = 14f, size = 0.1f, life = 0.7f, gravity = 4f)
        // an opening salvo, then a steady fountain
        repeat(40) { spawnCoin(burst = true) }
    }

    private fun slam() {
        SoundFx.play("perfect", rate = 1.0f)
        SoundFx.play("success", rate = 1.25f, vol = 0.8f)
        SoundFx.play("boom", rate = 1.3f, vol = 0.5f)
        Haptics.success()
        game.flash(gold, 0.4f)
        punch = 0.8f
        game.burst3d(tmp.set(homeX, cubeY, 0f), white, n = 30, speed = 9f, size = 0.1f, life = 0.7f, gravity = 3f)
    }

    // ------------------------------------------------------------------ coins

    private fun emit(dt: Float) {
        if (t < BURST || t > SLAM - 0.3f) return
        val k = (t - BURST) / (SLAM - 0.3f - BURST)
        emitCarry += dt * (70f - 40f * k)
        while (emitCarry >= 1f) { emitCarry -= 1f; spawnCoin(burst = false) }
    }

    private fun spawnCoin(burst: Boolean) {
        var i = -1
        for (j in 0 until cap) if (state[j] == 0) { i = j; break }
        if (i < 0) return
        val o = i * stride
        // Mostly up and away from the camera, fanned to both sides; never into the lens.
        val a = (rnd.nextFloat() - 0.5f) * 3.8f
        val h = (if (burst) 4f else 2.8f) + rnd.nextFloat() * (if (burst) 4.5f else 3.5f)
        c[o] = homeX; c[o + 1] = cubeY + 0.3f; c[o + 2] = 0f
        c[o + 3] = sin(a) * h
        c[o + 4] = (if (burst) 6f else 7f) + rnd.nextFloat() * 3.5f
        c[o + 5] = -abs(cos(a)) * h * 1.3f - 1.5f
        c[o + 6] = rnd.nextFloat() * 360f
        c[o + 7] = (500f + rnd.nextFloat() * 700f) * (if (rnd.nextBoolean()) 1f else -1f)
        c[o + 8] = if (rnd.nextFloat() < 0.08f) 0.4f else 0.22f + rnd.nextFloat() * 0.07f
        state[i] = 1
        coins = max(coins, i + 1)
    }

    private fun beginGather() {
        var n = 0
        for (i in 0 until coins) if (state[i] == 1) n++
        gatherTotal = max(1, n)
        var k = 0
        for (i in 0 until coins) {
            if (state[i] != 1) continue
            val o = i * stride
            c[o + 9] = c[o]; c[o + 10] = c[o + 1]; c[o + 11] = c[o + 2]
            // the nearest coins answer first; the swirl sweeps in from the edges
            c[o + 12] = GATHER + 0.45f * (k++ / gatherTotal.toFloat()) + rnd.nextFloat() * 0.05f
            state[i] = 2
        }
    }

    private fun moveCoins(dt: Float) {
        for (i in 0 until coins) {
            val o = i * stride
            when (state[i]) {
                1 -> {
                    c[o + 4] -= 18f * dt
                    c[o] += c[o + 3] * dt; c[o + 1] += c[o + 4] * dt; c[o + 2] += c[o + 5] * dt
                    val r = c[o + 8]
                    if (c[o + 1] < r) { // the road: bounce, then roll to rest standing on edge
                        c[o + 1] = r
                        if (c[o + 4] < -2.5f) {
                            if (c[o + 4] < -8f && rnd.nextFloat() < 0.25f) SoundFx.play("tap", rate = 1.6f + rnd.nextFloat() * 0.3f, vol = 0.18f)
                            c[o + 4] *= -0.42f; c[o + 3] *= 0.7f; c[o + 5] *= 0.7f
                        } else {
                            c[o + 4] = 0f
                            val f = max(0f, 1f - 4f * dt)
                            c[o + 3] *= f; c[o + 5] *= f
                        }
                    }
                    c[o + 7] *= max(0f, 1f - 0.5f * dt)
                    if (abs(c[o + 7]) < 160f) c[o + 7] = if (c[o + 7] < 0f) -160f else 160f
                    c[o + 6] += c[o + 7] * dt
                }
                2 -> {
                    val u = ((t - c[o + 12]) / 0.45f)
                    if (u < 0f) { c[o + 6] += c[o + 7] * dt; continue }
                    if (u >= 1f) { absorb(i); continue }
                    val e = u * u * u
                    val sx = c[o + 9] - homeX; val sz = c[o + 11]
                    // swirl the far way round, so no coin sweeps past the lens
                    val ang = u * PI.toFloat() * 0.9f * (if (sx > 0f) -1f else 1f)
                    val ca = cos(ang); val sa = sin(ang)
                    c[o] = homeX + (sx * ca - sz * sa) * (1f - e)
                    c[o + 2] = min(0.5f, (sx * sa + sz * ca) * (1f - e))
                    c[o + 1] = c[o + 10] + (cubeY - c[o + 10]) * e + sin(u * PI.toFloat()) * 1.6f
                    c[o + 6] += 900f * dt
                }
            }
        }
    }

    private fun absorb(i: Int) {
        state[i] = 0
        absorbed++
        val o = i * stride
        punch = max(punch, 0.45f)
        if (t - lastChime > 0.045f) {
            lastChime = t
            SoundFx.play("coin", rate = 1f + 0.8f * (absorbed / gatherTotal.toFloat()).coerceAtMost(1f), vol = 0.5f)
        }
        if (absorbed % 3 == 0) game.burst3d(tmp.set(c[o], c[o + 1], c[o + 2]), gold, n = 3, speed = 2.5f, size = 0.07f, life = 0.35f, gravity = 0f)
    }

    private fun finish() {
        active = false
        amount = 0
        coins = 0
        java.util.Arrays.fill(state, 0)
        Stage.jackpotClock = -1f
        Stage.jackpotAmount = 0
        // the run winds back up out of the freeze instead of snapping to speed
        game.slowMo(0.2f, 0.3f)
    }

    /** Stop at once without a show (the run was torn down). */
    fun cancel() { if (active) finish() }

    // ---------------------------------------------------------------- camera

    /**
     * The chase camera cranes low and to the side of the floating cube, the
     * road stretching away past it; it drifts round during the fountain and
     * swings back to exactly where the chase left it.
     */
    fun aimCamera(cam: PerspectiveCamera) {
        val into = JackpotBeats.span(t, 0f, 1.15f)
        val back = JackpotBeats.span(t, RETURN, END)
        val k = into * (1f - back)
        val orbit = (-10f + 22f * JackpotBeats.span(t, 0.4f, GATHER + 0.6f)) * (PI.toFloat() / 180f)
        val dist = 8.6f - 0.8f * JackpotBeats.span(t, SLAM, RETURN)
        tmp.set(homeX + sin(orbit) * dist, cubeY + 1.9f, cos(orbit) * dist)
        look.set(homeX, cubeY - 0.4f, 0f)
        cam.position.set(camFrom).lerp(tmp, k)
        tmp.set(lookFrom).lerp(look, k)
        cam.up.set(upFrom).lerp(Vector3.Y, k).nor()
        cam.lookAt(tmp)
        cam.up.set(upFrom).lerp(Vector3.Y, k).nor()
        cam.fieldOfView = fovFrom + (58f - fovFrom) * k
    }

    /** The world's sky warms to gold over rose for the show. */
    fun tintSky(top: Color, bottom: Color) {
        val k = 0.8f * JackpotBeats.span(t, 0.1f, BURST) * (1f - JackpotBeats.span(t, RETURN, END))
        top.set(skyTop).lerp(warm, k)
        bottom.set(skyBottom).lerp(rose, k)
    }

    // ---------------------------------------------------------------- render

    fun render() {
        for (i in 0 until coins) {
            if (state[i] == 0) continue
            val o = i * stride
            val r = c[o + 8]
            game.worldCoin(c[o], c[o + 1], c[o + 2], r, r * 0.39f, c[o + 6], gold)
            game.worldCoin(c[o], c[o + 1], c[o + 2], r * 0.64f, r * 0.56f, c[o + 6], face)
        }
    }

    /** The rays behind the cube and the shockwaves on the road. Call from renderWorldShapes. */
    fun renderShapes(shapes: ShapeRenderer, time: Float) {
        // Before the burst a tight glow gathers and spins faster; then the rays blow open.
        val gatherGlow = 0.35f * JackpotBeats.span(t, 0.3f, BURST) * (1f - JackpotBeats.span(t, BURST, BURST + 0.2f))
        if (gatherGlow > 0.004f) game.sunburstBehind(shapes, homeX, cubeY, 0f, 3f, 2.2f + 1.5f * JackpotBeats.span(t, 0.3f, BURST), 14, time * 260f * (t / BURST), gold, gatherGlow, 0.35f)
        val open = JackpotBeats.span(t, BURST - 0.05f, BURST + 0.35f)
        val fade = 1f - JackpotBeats.span(t, RETURN - 0.3f, RETURN + 0.5f)
        val rays = open * fade
        if (rays > 0.004f) {
            val r = 22f * (0.2f + 0.8f * open) + 1.5f * sin(time * 3f)
            game.sunburstBehind(shapes, homeX, cubeY, 0f, 9f, r, 18, time * 22f, warm, 0.6f * rays, 0.5f)
            game.sunburstBehind(shapes, homeX, cubeY, 0f, 8f, r * 0.8f, 12, -time * 35f, white, 0.45f * rays, 0.22f)
            game.sunburstBehind(shapes, homeX, cubeY, 0f, 7f, 3.2f + 0.3f * sin(time * 9f), 24, time * 60f, white, 0.7f * rays, 0.5f)
        }
        ring(shapes, t - BURST, 1.1f, 34f, 2.4f, gold)
        ring(shapes, t - BURST, 1.1f, 34.6f, 0.7f, white) // the bright leading edge
        ring(shapes, t - SLAM, 0.8f, 16f, 1.6f, gold)
        ring(shapes, t - SLAM, 0.8f, 16.4f, 0.5f, white)
    }

    private val ringA = Color()
    private val ringB = Color()
    private val ringM = com.badlogic.gdx.math.Matrix4()

    /** A flat wave on the road around the cube, [age] seconds old, spreading to [reach]. */
    private fun ring(shapes: ShapeRenderer, age: Float, life: Float, reach: Float, width: Float, col: Color) {
        if (age < 0f || age > life) return
        val p = age / life
        val e = 1f - (1f - p) * (1f - p)
        val r0 = reach * e
        val r1 = r0 + width * (1f + 2f * p)
        val a = 0.95f * (1f - p * p)
        ringA.set(col.r, col.g, col.b, a)
        ringB.set(col.r, col.g, col.b, 0f)
        // Lay the 2D fan flat on the road: local y becomes world z.
        ringM.setToTranslation(homeX, 0.06f, 0f).rotate(Vector3.X, 90f)
        shapes.transformMatrix = ringM
        val n = 48
        for (k in 0 until n) {
            val a0 = k * 6.2832f / n; val a1 = (k + 1) * 6.2832f / n
            val x0 = cos(a0); val z0 = sin(a0); val x1 = cos(a1); val z1 = sin(a1)
            // the leading edge is bright, the trailing edge fades into the road
            shapes.triangle(x0 * r1, z0 * r1, x1 * r1, z1 * r1, x0 * r0, z0 * r0, ringA, ringA, ringB)
            shapes.triangle(x1 * r1, z1 * r1, x1 * r0, z1 * r0, x0 * r0, z0 * r0, ringA, ringB, ringB)
        }
        shapes.identity()
    }
}
