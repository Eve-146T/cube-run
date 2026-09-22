package cube.run.game.stage

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import cube.run.core.Gdx3DGame
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.core.VoidBeats
import cube.run.core.VoidBeats.COLLAPSE
import cube.run.core.VoidBeats.END
import cube.run.core.VoidBeats.FEED
import cube.run.core.VoidBeats.NOVA
import cube.run.core.VoidBeats.PULL
import cube.run.core.VoidBeats.REBIRTH
import cube.run.core.VoidBeats.RETURN
import cube.run.core.VoidBeats.TAKEN
import cube.run.core.gfx.VoidRenderer
import cube.run.core.hsvInto
import cube.run.game.Player
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Paying the void, in 3D. The shop's sky drains to black and a black hole opens above
 * the cube: a tilted accretion disk, the lensed far side arching over the shadow. Your
 * coins leave the bank, fall onto the disk and orbit down into it, heating it gold. Then
 * it takes the cube too — pulled up, stretched thin, darkened and gone behind the horizon.
 * The disk spins up as the hole shrinks to a single point of light… and it goes supernova:
 * a hard flash, a hot core, a blast front and shockwave rings, leaving a pulsar sweeping
 * its beams through a nebula. The cube is assembled again from shards, the void says its
 * piece, and the shop comes back. One GL-owned clock ([Stage.voidClock]) drives it all.
 */
class VoidShow(private val game: Gdx3DGame, private val player: Player) {

    var active = false
        private set
    private var t = 0f
    private val rnd = Random(146)
    private var renderer: VoidRenderer? = null

    private val hole = Vector3()
    private val home = Vector3()
    private val camShop = Vector3()
    private val tmp = Vector3()
    private val tmp2 = Vector3()
    private val look = Vector3()
    private val ringM = Matrix4()

    private val violet = hsvInto(Color(), 262f, 0.65f, 1f)
    private val lilac = hsvInto(Color(), 270f, 0.35f, 1f)
    private val gold = hsvInto(Color(), 44f, 0.85f, 1f)
    private val face = hsvInto(Color(), 50f, 0.55f, 1f)
    private val rose = hsvInto(Color(), 320f, 0.6f, 1f)
    private val teal = hsvInto(Color(), 190f, 0.6f, 0.9f)
    private val ember = Color(0.45f, 0.04f, 0.1f, 1f)
    private val white = Color(1f, 1f, 1f, 1f)
    private val glowCol = Color()
    private val colA = Color()
    private val colB = Color()
    private val voidTop = Color.valueOf("150a36")
    private val voidBottom = Color.valueOf("020008")
    private val novaTop = Color.valueOf("8a2fb8")
    private val novaBottom = Color.valueOf("24073f")

    // The cube's show pose.
    private var cubeX = 0f
    private var cubeY = 0f
    private var cubeZ = 0f
    private var cubeYaw = 0f
    private var cubeTip = 0f
    private var stretch = 0f
    private var cubeScale = 1f
    private var cubeGlow = 0f
    private var darkness = 0f
    private var spin = 0f

    // The hole.
    private var radius = 0f
    private var gulp = 0f
    private var heat = 0f

    // Coins: start x y z, launch time, yaw, size.
    private val coinCount = 26
    private val coins = FloatArray(coinCount * 6)
    private val coinDone = BooleanArray(coinCount)
    private var eaten = 0

    // Far stars behind the hole: direction x y z, distance, size, phase.
    private val starCount = 110
    private val stars = FloatArray(starCount * 6)
    // Shards that fly in to rebuild the cube: direction x y z, spin.
    private val shardCount = 14
    private val shards = FloatArray(shardCount * 4)
    // Nebula wisps left by the blast: offset x y z, size, seed.
    private val puffCount = 9
    private val puffs = FloatArray(puffCount * 5)

    init {
        for (i in 0 until starCount) {
            val o = i * 6
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val y = rnd.nextFloat() * 2f - 1f
            val r = kotlin.math.sqrt(1f - y * y)
            stars[o] = cos(a) * r; stars[o + 1] = y * 0.75f; stars[o + 2] = -0.35f - 0.65f * kotlin.math.abs(sin(a) * r)
            stars[o + 3] = 16f + rnd.nextFloat() * 14f
            stars[o + 4] = 0.05f + rnd.nextFloat() * rnd.nextFloat() * 0.16f
            stars[o + 5] = rnd.nextFloat() * 6.28f
        }
        for (i in 0 until shardCount) {
            val o = i * 4
            tmp.setToRandomDirection()
            shards[o] = tmp.x; shards[o + 1] = tmp.y; shards[o + 2] = kotlin.math.abs(tmp.z) * 0.5f; shards[o + 3] = rnd.nextFloat() * 360f
        }
        for (i in 0 until puffCount) {
            val o = i * 5
            val a = i * 2.39996f
            val d = 1.2f + (i % 4) * 0.9f
            puffs[o] = cos(a) * d * 1.3f; puffs[o + 1] = sin(a) * d * 0.8f; puffs[o + 2] = (i % 3 - 1) * 0.8f
            puffs[o + 3] = 2.2f + (i % 3) * 0.8f; puffs[o + 4] = i * 1.37f
        }
    }

    fun start(px: Float, py: Float) {
        active = true
        t = 0f; spin = 0f; gulp = 0f; heat = 0f; eaten = 0; cubeYaw = 0f
        home.set(px, py, 0f)
        hole.set(px, py + 2.1f, -5.2f)
        java.util.Arrays.fill(coinDone, false)
        java.util.Arrays.fill(coins, 0f)
        Stage.voidFed = 0f
        Stage.voidClock = 0f
        SoundFx.play("drain", vol = 0.9f)
        SoundFx.play("whoosh", rate = 0.5f, vol = 0.6f)
        Haptics.click()
    }

    fun update(dt: Float) {
        if (!active) return
        if (Stage.voidSkips.getAndSet(0) > 0 && t > NOVA + 0.4f && t < RETURN) t = RETURN
        val before = t
        t += dt
        Stage.voidClock = t
        fun crossed(beat: Float) = before < beat && t >= beat

        // ---- the hole: opens, swells with every coin, spins up and collapses to a point
        val open = VoidBeats.span(t, 0.1f, 1.0f)
        val shrink = ((t - COLLAPSE) / (NOVA - COLLAPSE)).coerceIn(0f, 1f).pow(1.8f)
        gulp = max(0f, gulp - dt * 3.5f)
        radius = MAX_R * (open * (1f + 0.12f * gulp) * (1f - 0.97f * shrink))
        heat += ((if (t in FEED..NOVA) 1f else 0f) - heat) * min(1f, dt * 2.2f)

        // ---- the coins leave the bank, fall onto the disk and orbit down
        if (t >= FEED && t < TAKEN) launchCoins()
        for (i in 0 until coinCount) if (!coinDone[i] && coins[i * 6 + 3] > 0f && t >= coins[i * 6 + 3] + COIN_FLIGHT) {
            coinDone[i] = true; eaten++
            Stage.voidFed = eaten / coinCount.toFloat()
            gulp = min(1.2f, gulp + 0.22f)
            SoundFx.play("coin", rate = 1.5f - eaten * 0.03f, vol = 0.3f)
            if (eaten % 2 == 0) Haptics.tick()
        }

        poseCube(dt)

        // ---- beats
        if (crossed(0.9f)) { SoundFx.play("boom", rate = 0.5f, vol = 0.7f); Haptics.heavy() }
        if (crossed(PULL)) { SoundFx.play("rise", rate = 0.5f, vol = 0.8f); SoundFx.play("whoosh", rate = 0.55f, vol = 0.9f) }
        if (crossed(TAKEN - 0.08f)) { SoundFx.play("boom", rate = 0.6f, vol = 0.7f); Haptics.heavy() }
        if (crossed(COLLAPSE + 0.1f)) SoundFx.play("rise", rate = 1.3f, vol = 0.55f)
        if (crossed(NOVA)) nova()
        if (crossed(REBIRTH + 0.45f)) {
            SoundFx.play("pop", rate = 0.85f); SoundFx.play("success", rate = 0.75f, vol = 0.6f); Haptics.success()
            game.burst3d(tmp.set(home), white, n = 10, speed = 4f, size = 0.06f, life = 0.35f, gravity = 0f)
        }
        if (crossed(RETURN)) SoundFx.play("whoosh", rate = 0.9f, vol = 0.7f)
        if (t >= END) finish()
    }

    private fun launchCoins() {
        for (i in 0 until coinCount) {
            val o = i * 6
            if (coins[o + 3] > 0f || coinDone[i]) continue
            if (t < FEED + i * 0.036f) return
            // Leave from the bank pill: a point on its pick ray out at the hole's depth.
            val ray = game.cam.getPickRay(Stage.voidCoinX * game.sw, Stage.voidCoinY * game.sh)
            tmp.set(ray.direction).scl(12.5f).add(ray.origin)
            coins[o] = tmp.x + (rnd.nextFloat() - 0.5f) * 0.5f
            coins[o + 1] = tmp.y + (rnd.nextFloat() - 0.5f) * 0.3f
            coins[o + 2] = tmp.z
            coins[o + 3] = t
            coins[o + 4] = rnd.nextFloat() * 360f
            coins[o + 5] = 0.15f + rnd.nextFloat() * 0.05f
        }
    }

    /** A point in the disk's plane: angle [a] (radians, 0 = right, ½π = nearest), radius [r]. */
    private fun onDisk(out: Vector3, a: Float, r: Float): Vector3 {
        val x = cos(a) * r; val z = sin(a) * r
        val tilt = DISK_TILT * PI.toFloat() / 180f
        return out.set(hole.x + x, hole.y - z * sin(tilt), hole.z + z * cos(tilt))
    }

    private fun poseCube(dt: Float) {
        val tremble = VoidBeats.span(t, FEED, PULL)
        if (t < REBIRTH) {
            val u = ((t - PULL) / (TAKEN - PULL)).coerceIn(0f, 1f)
            val e = u * u
            // Up into the hole's heart with a swirl; the horizon swallows it from the front.
            cubeX = home.x + (hole.x - home.x) * e + 1.1f * sin(e * PI.toFloat()) * (1f - e)
            cubeY = home.y + 0.25f * tremble + (hole.y - home.y - 0.25f * tremble) * e + 0.04f * sin(t * 41f) * tremble * (1f - e)
            cubeZ = home.z + (hole.z - home.z) * e
            // Stretch along the pull: the cube's long axis points at the hole.
            val dx = hole.x - cubeX; val dy = hole.y - cubeY
            val aim = -Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())).toFloat()
            stretch = e
            cubeTip = aim * (0.3f * tremble + 0.7f * u)
            spin = 45f + 160f * tremble + 500f * e
            cubeScale = if (t >= TAKEN) 0f else 1f - e.pow(6f) * 0.6f
            cubeGlow = (0.35f * tremble + 0.65f * e).coerceIn(0f, 1f)
            darkness = e
        } else {
            // Rebuilt from shards: they fly in, then it pops back to full size.
            cubeX = home.x; cubeY = home.y; cubeZ = 0f
            stretch = 0f; cubeTip = 0f; darkness = 0f
            val r = t - (REBIRTH + 0.3f)
            cubeScale = if (r < 0f) 0f else 1f - exp(-r * 7f) * cos(r * 12f)
            spin = 45f + 700f * exp(-max(0f, r) * 3f)
            cubeGlow = if (r < 0f) 0f else exp(-r * 3f)
        }
        cubeYaw += spin * dt
    }

    private fun nova() {
        SoundFx.play("boom", rate = 0.55f, vol = 1f)
        SoundFx.play("boom", rate = 1.1f, vol = 0.6f)
        SoundFx.play("fanfare", rate = 0.6f, vol = 0.7f)
        Haptics.heavy(); Haptics.success()
        game.flash(white, 0.75f)
        game.burst3d(tmp.set(hole), white, n = 26, speed = 17f, size = 0.1f, life = 0.7f, gravity = 0f)
        game.burst3d(tmp, lilac, n = 22, speed = 12f, size = 0.12f, life = 0.9f, gravity = 0f)
        game.burst3d(tmp, gold, n = 16, speed = 8f, size = 0.11f, life = 1.0f, gravity = 0f)
    }

    private fun finish() {
        active = false
        Stage.voidClock = -1f
        player.endVoidPose()
    }

    /** Stop at once (the stage was left). */
    fun cancel() { if (active) finish() }

    // ---------------------------------------------------------------- pose, camera, sky

    fun posePlayer(time: Float, baseHue: Float) {
        // Violet as it is drawn in, then burnt to a dark ember; white-hot as it is rebuilt.
        if (t < REBIRTH) glowCol.set(violet).lerp(ember, darkness) else glowCol.set(white)
        val s = max(0.001f, cubeScale)
        player.voidPose(time, baseHue, cubeX, cubeY, cubeZ, cubeYaw, cubeTip,
            s * (1f - 0.7f * stretch), s * (1f + 2.6f * stretch), s * (1f - 0.7f * stretch), cubeGlow, glowCol)
    }

    /** How far the stage is inside the show (0 = the shop's own shot, 1 = the show's). */
    val depth get() = VoidBeats.span(t, 0f, 0.9f) * (1f - VoidBeats.span(t, RETURN, RETURN + 0.55f))

    /** How much of the shop's own sunburst is left: it spins up and is sucked away first. */
    val raysKept get() = 1f - VoidBeats.span(t, 0f, 0.8f) * (1f - VoidBeats.span(t, RETURN, RETURN + 0.55f))

    /**
     * Blends the shop camera (already aimed this frame) to the show's: pulled back so the
     * hole fills the upper screen with the cube beneath it, creeping closer while it feeds.
     */
    fun aimCamera(cam: PerspectiveCamera) {
        val k = depth
        if (k <= 0f) return
        camShop.set(cam.position)
        val creep = 1.4f * VoidBeats.span(t, FEED, COLLAPSE) - 1.4f * VoidBeats.span(t, NOVA, NOVA + 1.2f)
        val drift = 0.5f * sin(t * 0.6f)
        tmp.set(home.x + drift, home.y + 1.0f, 10.8f - creep)
        look.set(hole.x, hole.y - 0.75f, hole.z * 0.4f)
        tmp2.set(cam.direction).scl(10f).add(cam.position) // where the shop was looking
        cam.position.set(camShop).lerp(tmp, k)
        tmp2.lerp(look, k)
        cam.up.set(Vector3.Y)
        cam.lookAt(tmp2)
        cam.fieldOfView += (58f - cam.fieldOfView) * k
    }

    fun tintSky(top: Color, bottom: Color) {
        val k = depth
        if (k <= 0f) return
        val flash = if (t >= NOVA) exp(-(t - NOVA) * 3.2f) else 0f
        colA.set(voidTop).lerp(novaTop, flash)
        colB.set(voidBottom).lerp(novaBottom, flash)
        top.lerp(colA, k); bottom.lerp(colB, k)
    }

    // ---------------------------------------------------------------- render

    /** Coins in flight, the far stars and the shards rebuilding the cube (batched pass). */
    fun render(time: Float) {
        val k = depth
        val pullIn = VoidBeats.span(t, FEED, COLLAPSE)
        val blast = VoidBeats.span(t, NOVA, NOVA + 1.5f)
        for (i in 0 until starCount) {
            val o = i * 6
            // The sky's stars sag towards the hole while it feeds, and are thrown back out by the blast.
            val d = stars[o + 3] * (1f - 0.28f * pullIn * (1f - blast))
            val tw = 0.55f + 0.45f * sin(time * 2.1f + stars[o + 5])
            val s = stars[o + 4] * k * tw
            if (s < 0.01f) continue
            game.worldBoxSpin(hole.x + stars[o] * d, hole.y + stars[o + 1] * d, hole.z + stars[o + 2] * d, s, s, s, time * 40f + i * 20f, if (i % 5 == 0) lilac else white)
        }
        for (i in 0 until coinCount) {
            val o = i * 6
            if (coinDone[i] || coins[o + 3] <= 0f || t < coins[o + 3]) continue
            val u = ((t - coins[o + 3]) / COIN_FLIGHT).coerceIn(0f, 1f)
            val entry = 0.3f
            if (u < entry) {
                // Falling from the bank onto the rim of the disk.
                val e = (u / entry).let { it * it }
                onDisk(tmp2, 0f, MAX_R * 4.3f)
                tmp.set(coins[o], coins[o + 1], coins[o + 2]).lerp(tmp2, e)
            } else {
                // Round and down: the orbit tightens and speeds up until the horizon takes it.
                val v = (u - entry) / (1f - entry)
                onDisk(tmp, 2.6f * PI.toFloat() * v.pow(1.5f), MAX_R * (4.3f - 3.35f * v.pow(1.2f)))
            }
            val size = coins[o + 5] * (1f - 0.5f * u)
            game.worldCoin(tmp.x, tmp.y, tmp.z, size, size * 0.39f, coins[o + 4] + u * 1100f, gold)
            game.worldCoin(tmp.x, tmp.y, tmp.z, size * 0.64f, size * 0.56f, coins[o + 4] + u * 1100f, face)
        }
        val gather = ((t - REBIRTH) / 0.4f).coerceIn(0f, 1f)
        if (t >= REBIRTH && gather < 1f) for (i in 0 until shardCount) {
            val o = i * 4
            val d = 3.2f * (1f - gather * gather)
            val s = 0.16f * (1f - 0.4f * gather)
            game.worldBoxSpin(home.x + shards[o] * d, home.y + shards[o + 1] * d, shards[o + 2] * d, s, s, s, shards[o + 3] + gather * 720f, player.trailCol())
        }
    }

    /** Backdrop shapes: the point of light before the blast, its rays and shockwave rings, the pulsar's beams. */
    fun renderShapes(shapes: ShapeRenderer, time: Float) {
        val point = VoidBeats.span(t, COLLAPSE + 0.25f, NOVA - 0.05f) * (1f - VoidBeats.span(t, NOVA, NOVA + 0.1f))
        if (point > 0.01f) {
            game.sunburstBehind(shapes, hole.x, hole.y, hole.z, 0.5f, 0.35f + 0.9f * point, 4, time * 30f, white, point, 0.12f)
            game.sunburstBehind(shapes, hole.x, hole.y, hole.z, 0.5f, 0.2f + 0.5f * point, 4, 45f - time * 50f, lilac, point, 0.18f)
        }
        val rays = VoidBeats.span(t, NOVA - 0.02f, NOVA + 0.15f) * (1f - VoidBeats.span(t, NOVA + 0.25f, NOVA + 1.1f))
        if (rays > 0.01f) {
            val grow = VoidBeats.span(t, NOVA, NOVA + 0.8f)
            game.sunburstBehind(shapes, hole.x, hole.y, hole.z, 2f, 3f + 20f * grow, 12, time * 12f, white, 0.3f * rays, 0.12f)
        }
        ring(shapes, t - NOVA, 1.3f, 9f, 1.4f, lilac)
        ring(shapes, t - NOVA, 1.3f, 9.3f, 0.4f, white)
        ring(shapes, t - NOVA - 0.1f, 1.6f, 6f, 0.9f, gold)
        // What is left at the heart of it: a pulsar, two beams sweeping round.
        val pulsar = pulsarAmount
        if (pulsar > 0.01f) {
            game.sunburstBehind(shapes, hole.x, hole.y, hole.z, 0.5f, 9f, 2, time * 140f, lilac, 0.45f * pulsar, 0.05f)
            game.sunburstBehind(shapes, hole.x, hole.y, hole.z, 0.5f, 6f, 2, time * 140f, white, 0.55f * pulsar, 0.02f)
        }
    }

    private val pulsarAmount get() = VoidBeats.span(t, NOVA + 0.5f, NOVA + 1.3f) * (1f - VoidBeats.span(t, RETURN - 0.4f, RETURN + 0.2f))

    /** A shockwave, tipped towards the camera so it reads as a ring, [age] seconds old, spreading to [reach]. */
    private fun ring(shapes: ShapeRenderer, age: Float, life: Float, reach: Float, width: Float, col: Color) {
        if (age < 0f || age > life) return
        val p = age / life
        val e = 1f - (1f - p) * (1f - p) * (1f - p)
        val r0 = reach * e
        val r1 = r0 + width * (1f + p)
        colA.set(col.r, col.g, col.b, 0.85f * (1f - p) * (1f - p))
        colB.set(col.r, col.g, col.b, 0f)
        ringM.setToTranslation(hole).rotate(Vector3.X, 90f + RING_TILT)
        shapes.transformMatrix = ringM
        val n = 56
        for (k in 0 until n) {
            val a0 = k * 6.2832f / n; val a1 = (k + 1) * 6.2832f / n
            val x0 = cos(a0); val y0 = sin(a0); val x1 = cos(a1); val y1 = sin(a1)
            shapes.triangle(x0 * r1, y0 * r1, x1 * r1, y1 * r1, x0 * r0, y0 * r0, colA, colA, colB)
            shapes.triangle(x1 * r1, y1 * r1, x1 * r0, y1 * r0, x0 * r0, y0 * r0, colA, colB, colB)
        }
        shapes.identity()
    }

    /** The hole, the blast and what it leaves behind (blended pass, after the opaque world). */
    fun renderBlended(cam: PerspectiveCamera, time: Float) {
        val r = renderer ?: VoidRenderer(game.mb).also { renderer = it }
        val collapse = VoidBeats.span(t, COLLAPSE, NOVA)
        val fade = 1f - VoidBeats.span(t, NOVA - 0.2f, NOVA)
        val appear = VoidBeats.span(t, 0.15f, 0.9f)
        // The show's own short clock: the game's running time is too large for mediump shaders.
        val spinUp = 1f + 2.4f * heat + 2f * VoidBeats.span(t, PULL, TAKEN) + 7f * collapse
        r.drawHole(cam, hole, radius, t + 7f, heat, spinUp, appear * fade * (1f + 0.35f * gulp + 0.8f * collapse), appear * fade, DISK_TILT)
        if (t < NOVA) return

        val p = ((t - NOVA) / 1.3f).coerceIn(0f, 1f)
        if (p < 1f) {
            // A hard white-hot core that cools and shrinks, and a thin blast front racing out.
            colA.set(white).lerp(gold, p)
            r.drawGlow(cam, hole, 3.6f - 2.4f * p, colA, 2.4f * (1f - p).pow(2f))
            val e = 1f - (1f - p).pow(3f)
            colB.set(white).lerp(gold, p).lerp(rose, p * p)
            r.drawShell(cam, hole, 0.3f + 8.5f * e, colB, 2f * (1f - p).pow(1.8f), t)
        }
        val q = ((t - NOVA - 0.1f) / 2f).coerceIn(0f, 1f)
        if (q in 0.001f..0.999f) r.drawShell(cam, hole, 0.2f + 5.5f * (1f - (1f - q).pow(2.5f)), rose, 1.2f * (1f - q).pow(1.5f), t * 0.7f)
        // The remnant: soft torn gas drifting out, violet, rose and teal.
        val gas = VoidBeats.span(t, NOVA + 0.2f, NOVA + 1.3f) * (1f - VoidBeats.span(t, RETURN - 0.4f, RETURN + 0.3f))
        if (gas > 0.01f) for (i in 0 until puffCount) {
            val o = i * 5
            val grow = 1f + 0.12f * (t - NOVA)
            tmp.set(hole).add(puffs[o] * grow, puffs[o + 1] * grow, puffs[o + 2])
            val c = when (i % 4) { 0 -> violet; 1 -> rose; 2 -> teal; else -> lilac }
            r.drawGlow(cam, tmp, puffs[o + 3] * grow, c, 0.32f * gas, gas = 1f, seed = puffs[o + 4], time = t)
        }
        // The pulsar's heart: a small white star that throbs.
        val pulsar = pulsarAmount
        if (pulsar > 0.01f) r.drawGlow(cam, hole, 0.55f + 0.12f * sin(time * 18f), white, 1.6f * pulsar)
    }

    fun dispose() { renderer?.dispose(); renderer = null }

    private companion object {
        const val MAX_R = 0.95f
        const val DISK_TILT = 11f
        const val RING_TILT = 38f
        const val COIN_FLIGHT = 1.05f
    }
}
