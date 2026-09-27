package cube.run.game.stage

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
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

    private val violet = hsvInto(Color(), 262f, 0.65f, 1f)
    private val lilac = hsvInto(Color(), 270f, 0.35f, 1f)
    private val gold = hsvInto(Color(), 44f, 0.85f, 1f)
    private val face = hsvInto(Color(), 50f, 0.55f, 1f)
    private val rose = hsvInto(Color(), 320f, 0.6f, 1f)
    private val teal = hsvInto(Color(), 190f, 0.6f, 0.9f)
    private val ember = Color(0.45f, 0.04f, 0.1f, 1f) // coins burning up at the horizon
    private val white = Color(1f, 1f, 1f, 1f)
    private val glowCol = Color()
    private val cubeCol = Color()
    private val colA = Color()
    private val colB = Color()
    private val voidTop = Color.valueOf("150a36")
    private val voidBottom = Color.valueOf("020008")
    // The sky only warms a little at the blast: the light belongs to the core, not a screen wash.
    private val novaTop = Color.valueOf("3a2470")
    private val novaBottom = Color.valueOf("170a33")
    private val orange = hsvInto(Color(), 26f, 0.85f, 1f)
    private val cyan = hsvInto(Color(), 195f, 0.35f, 1f)
    private val ringRight = Vector3()
    private val ringUp = Vector3()

    // The cube's show pose.
    private var cubeX = 0f
    private var cubeY = 0f
    private var cubeZ = 0f
    private var cubeYaw = 0f
    private var cubeScale = 1f
    private var cubeGlow = 0f
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
    private val shardCount = 26
    private val shards = FloatArray(shardCount * 4)
    // Nebula wisps left by the blast: offset x y z, size, seed.
    private val puffCount = 4
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
            val a = i * 2.39996f + 0.6f
            val d = 0.8f + (i % 2) * 1.1f
            puffs[o] = cos(a) * d * 1.3f; puffs[o + 1] = sin(a) * d * 0.8f; puffs[o + 2] = (i % 3 - 1) * 0.6f
            puffs[o + 3] = 4.2f + (i % 3) * 1.1f; puffs[o + 4] = i * 3.71f + 1.3f
        }
    }

    fun start(px: Float, py: Float) {
        active = true
        t = 0f; spin = 0f; gulp = 0f; heat = 0f; eaten = 0; cubeYaw = 0f
        home.set(px, py, 0f)
        hole.set(px, py + 2.6f, -5.2f)
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
            gulp = min(1.2f, gulp + 0.22f)
            SoundFx.play("coin", rate = 1.5f - eaten * 0.03f, vol = 0.3f)
            if (eaten % 2 == 0) Haptics.tick()
        }

        poseCube(dt)

        // ---- beats
        if (crossed(0.9f)) { SoundFx.play("boom", rate = 0.5f, vol = 0.7f); Haptics.heavy() }
        if (crossed(0.4f)) { // the cube is gone: a little puff where it was
            SoundFx.play("pop", rate = 0.6f, vol = 0.6f)
            game.burst3d(tmp.set(home).add(0f, 0.3f, 0f), lilac, n = 12, speed = 2.5f, size = 0.07f, life = 0.4f, gravity = 0f)
        }
        if (crossed(PULL)) SoundFx.play("rise", rate = 0.5f, vol = 0.7f)
        if (crossed(COLLAPSE + 0.1f)) SoundFx.play("rise", rate = 1.3f, vol = 0.55f)
        if (crossed(NOVA)) nova()
        if (crossed(NOVA + 0.14f)) { // hot shards, once the white-out has cleared
            game.burst3d(tmp.set(hole), gold, n = 14, speed = 8f, size = 0.14f, life = 0.55f, gravity = 0f)
            game.burst3d(tmp, orange, n = 16, speed = 6f, size = 0.16f, life = 0.7f, gravity = 0f)
        }
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
            if (t < FEED + i * 0.03f) return
            // Leave from the bank pill: a point on its pick ray out at the hole's depth.
            val ray = game.cam.getPickRay(Stage.voidCoinX * game.sw, Stage.voidCoinY * game.sh)
            tmp.set(ray.direction).scl(12.5f).add(ray.origin)
            coins[o] = tmp.x + (rnd.nextFloat() - 0.5f) * 0.5f
            coins[o + 1] = tmp.y + (rnd.nextFloat() - 0.5f) * 0.3f
            coins[o + 2] = tmp.z
            coins[o + 3] = t
            coins[o + 4] = rnd.nextFloat() * 360f
            coins[o + 5] = 0.24f + rnd.nextFloat() * 0.06f
            // The bank drops as each coin leaves it.
            Stage.voidFed = (i + 1) / coinCount.toFloat()
        }
    }

    /** A point in the disk's plane: angle [a] (radians, 0 = right, ½π = nearest), radius [r]. */
    private fun onDisk(out: Vector3, a: Float, r: Float): Vector3 {
        val x = cos(a) * r; val z = sin(a) * r
        val tilt = DISK_TILT * PI.toFloat() / 180f
        return out.set(hole.x + x, hole.y - z * sin(tilt), hole.z + z * cos(tilt))
    }

    private fun poseCube(dt: Float) {
        if (t < REBIRTH) {
            // The cube is not part of the offering: it slips out of sight as the hole opens,
            // and only comes back when it is rebuilt after the blast.
            val gone = VoidBeats.span(t, 0.05f, 0.4f)
            cubeX = home.x; cubeY = home.y + 0.3f * gone; cubeZ = home.z
            cubeScale = if (gone >= 1f) 0f else 1f - gone
            spin = 45f + 700f * gone
            cubeGlow = gone
        } else {
            // Rebuilt from shards: they fly in, then it eases back to full size.
            cubeX = home.x; cubeY = home.y; cubeZ = 0f
            val r = t - (REBIRTH + 0.3f)
            val grow = (r / 0.3f).coerceIn(0f, 1f)
            cubeScale = if (r < 0f) 0f else grow * grow * (3f - 2f * grow) * (1f + 0.12f * sin(grow * PI.toFloat()))
            spin = 45f + 700f * exp(-max(0f, r) * 3f)
            cubeGlow = if (r < 0f) 0f else exp(-r * 6f)
        }
        cubeYaw += spin * dt
    }

    private fun nova() {
        SoundFx.play("boom", rate = 0.55f, vol = 1f)
        SoundFx.play("boom", rate = 1.1f, vol = 0.6f)
        SoundFx.play("fanfare", rate = 0.6f, vol = 0.7f)
        Haptics.heavy(); Haptics.success()
        game.flash(white, 0.1f)
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
        // Violet as it slips away; white-hot as it is rebuilt.
        if (t < REBIRTH) glowCol.set(violet) else glowCol.set(white)
        val s = max(0.001f, cubeScale)
        // On the way back the cube eases into the shop's own pose, so nothing resets at the end.
        player.voidPose(time, baseHue, cubeX, cubeY, cubeZ, cubeYaw, 0f, s, s, s, cubeGlow, glowCol,
            toNormal = VoidBeats.span(t, RETURN - 0.2f, END - 0.25f))
    }

    /** How far the stage is inside the show (0 = the shop's own shot, 1 = the show's). */
    val depth get() = VoidBeats.span(t, 0f, 0.9f) * (1f - VoidBeats.span(t, RETURN - 0.25f, RETURN + 0.55f))

    /** How much of the shop's own sunburst is left: it spins up and is sucked away first. */
    val raysKept get() = 1f - VoidBeats.span(t, 0f, 0.8f) * (1f - VoidBeats.span(t, RETURN, RETURN + 0.55f))

    /** Extra spin (degrees per second) for the shop's rays: only while they are being sucked in, never on the way back. */
    val raySpin get() = if (t < RETURN) 380f * VoidBeats.span(t, 0f, 0.7f) else 0f

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
        val flash = if (t >= NOVA) exp(-(t - NOVA) * 5f) else 0f
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
            val drain = VoidBeats.span(t, COLLAPSE, NOVA)
            val d = stars[o + 3] * (1f - (0.28f * pullIn + 0.6f * drain) * (1f - blast))
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
                // Arcing out from the bank and down onto the rim of the disk.
                val e = (u / entry).let { it * (2f - it) }
                onDisk(tmp2, -0.3f, MAX_R * 2.5f)
                look.set(coins[o], coins[o + 1], coins[o + 2]).lerp(tmp2, e)
                tmp.set(look).add(-1.6f * sin(e * PI.toFloat()), 0.9f * sin(e * PI.toFloat()), 0f)
            } else {
                // Round and down: the orbit tightens and speeds up until the horizon takes it.
                val v = (u - entry) / (1f - entry)
                onDisk(tmp, -0.3f + 2.6f * PI.toFloat() * v.pow(1.5f), MAX_R * (2.5f - 1.6f * v.pow(1.2f)))
            }
            // Shrinking, reddening and going dark as they reach the horizon.
            val burn = ((u - 0.35f) / 0.55f).coerceIn(0f, 1f)
            val size = coins[o + 5] * (1f - 0.75f * u)
            colA.set(gold).lerp(orange, burn).lerp(ember, burn * burn)
            colB.set(face).lerp(orange, burn).lerp(ember, burn * burn)
            game.worldCoin(tmp.x, tmp.y, tmp.z, size, size * 0.39f, coins[o + 4] + u * 1100f, colA)
            game.worldCoin(tmp.x, tmp.y, tmp.z, size * 0.64f, size * 0.56f, coins[o + 4] + u * 1100f, colB)
        }
        val gather = ((t - REBIRTH) / 0.45f).coerceIn(0f, 1f)
        hsvInto(cubeCol, player.skin.hueAt(time, 0f), player.skin.sat, player.skin.valueAt(time))
        if (t >= REBIRTH && gather < 1f) for (i in 0 until shardCount) {
            val o = i * 4
            // Flying in and snapping onto the cube's faces.
            val d = 0.45f + 2.8f * (1f - gather).pow(2f)
            val s = 0.26f * (1f - 0.3f * gather)
            // In the cube's own colour, so the shards become the cube rather than turning into it.
            game.worldBoxSpin(home.x + shards[o] * d, home.y + shards[o + 1] * d, shards[o + 2] * d, s, s, s, shards[o + 3] + gather * 720f, cubeCol)
        }
    }

    /** Backdrop shapes: the point of light before the blast, its rays and shockwave rings, the pulsar's beams. */
    fun renderShapes(shapes: ShapeRenderer, time: Float) {
        val point = VoidBeats.span(t, COLLAPSE + 0.25f, NOVA - 0.05f) * (1f - VoidBeats.span(t, NOVA, NOVA + 0.1f))
        if (point > 0.01f) {
            game.sunburstBehind(shapes, hole.x, hole.y, hole.z, 0.5f, 0.35f + 0.9f * point, 4, time * 30f, white, point, 0.12f)
            game.sunburstBehind(shapes, hole.x, hole.y, hole.z, 0.5f, 0.2f + 0.5f * point, 4, 45f - time * 50f, lilac, point, 0.18f)
        }
        // What is left at the heart of it: a pulsar, two beams sweeping round.
        val pulsar = pulsarAmount
        if (pulsar > 0.01f) {
            game.sunburstBehind(shapes, hole.x, hole.y, hole.z, 0.5f, 4.2f, 2, time * 140f, cyan, 0.4f * pulsar, 0.05f)
            game.sunburstBehind(shapes, hole.x, hole.y, hole.z, 0.5f, 3f, 2, time * 140f, white, 0.5f * pulsar, 0.02f)
        }
    }

    private val pulsarAmount get() = VoidBeats.span(t, NOVA + 0.5f, NOVA + 1.3f) * (1f - VoidBeats.span(t, RETURN - 0.5f, RETURN))

    /** The hole, the blast and what it leaves behind (blended pass, after the opaque world). */
    fun renderBlended(cam: PerspectiveCamera, time: Float) {
        val r = renderer ?: VoidRenderer(game.mb).also { renderer = it }
        val collapse = VoidBeats.span(t, COLLAPSE, NOVA)
        val fade = 1f - VoidBeats.span(t, NOVA - 0.2f, NOVA)
        val appear = VoidBeats.span(t, 0.15f, 0.9f)
        // The show's own short clock: the game's running time is too large for mediump shaders.
        val spinUp = 1f + 2.4f * heat + 2f * VoidBeats.span(t, PULL, TAKEN) + 7f * collapse
        if (t < NOVA) r.drawHole(cam, hole, radius, t + 7f, heat, spinUp, appear * fade * (1f + 0.35f * gulp + 0.8f * collapse), appear * fade, DISK_TILT)
        // As it shrinks to nothing, what is left is light, not a black dot.
        val spark = VoidBeats.span(t, COLLAPSE + 0.3f, NOVA)
        if (spark > 0.01f && t < NOVA) r.drawGlow(cam, hole, 0.3f + 0.9f * spark, white, 2.2f * spark)
        if (t < NOVA) return

        // The flash: a white-out from the core that is gone in a few frames.
        val flash = ((t - NOVA) / 0.16f).coerceIn(0f, 1f)
        if (flash < 1f) r.drawGlow(cam, hole, 6.5f, lilac, 1.1f * min(1f, flash * 5f) * (1f - flash)) // a bloom round the core, not a screen wash
        val p = ((t - NOVA) / 1.4f).coerceIn(0f, 1f)
        if (p < 1f) {
            // A white-hot core that holds, cools through orange and shrinks away.
            colA.set(white).lerp(orange, p)
            r.drawGlow(cam, hole, 3.2f - 2.2f * p, colA, 2.4f * (1f - p))
        }
        // Shockwaves: soft rings lying in the disk's plane, tipped so they read as rings.
        ringRight.set(1f, 0f, 0f)
        ringUp.set(0f, kotlin.math.sin(RING_TILT * PI.toFloat() / 180f), -kotlin.math.cos(RING_TILT * PI.toFloat() / 180f))
        shockwave(r, cam, t - NOVA, 1.0f, 7f, white, 1.2f)
        shockwave(r, cam, t - NOVA - 0.12f, 1.5f, 6.5f, cyan, 1.8f)
        shockwave(r, cam, t - NOVA - 0.25f, 1.8f, 4.8f, orange, 1.8f)
        // The remnant: soft torn gas drifting out, violet, rose and teal.
        val gas = VoidBeats.span(t, NOVA + 0.2f, NOVA + 1.3f) * (1f - VoidBeats.span(t, RETURN - 0.5f, RETURN + 0.05f))
        if (gas > 0.01f) for (i in 0 until puffCount) {
            val o = i * 5
            val grow = 1f + 0.12f * (t - NOVA)
            tmp.set(hole).add(puffs[o] * grow, puffs[o + 1] * grow, puffs[o + 2])
            val c = when (i % 4) { 0 -> violet; 1 -> rose; 2 -> teal; else -> lilac }
            r.drawGlow(cam, tmp, puffs[o + 3] * grow, c, 0.42f * gas, gas = 1f, seed = puffs[o + 4], time = t)
        }
        // The pulsar's heart: a small white star that throbs.
        val pulsar = pulsarAmount
        if (pulsar > 0.01f) r.drawGlow(cam, hole, 0.55f + 0.12f * sin(time * 18f), white, 1.6f * pulsar)
    }

    /** Compile the shaders while the shop sits still, so the tap itself never stalls. */
    fun prewarm() { if (renderer == null) renderer = VoidRenderer(game.mb) }

    private fun shockwave(r: VoidRenderer, cam: PerspectiveCamera, age: Float, life: Float, reach: Float, col: Color, strength: Float) {
        if (age < 0f || age > life) return
        val p = age / life
        val e = 1f - (1f - p).pow(3f)
        // The quad spans ±reach; the ring's radius travels out to 0.9 of it and fades by distance.
        r.drawGlow(cam, hole, reach, col, strength * (1f - p).pow(1.5f), ring = 0.05f + 0.85f * e, planeRight = ringRight, planeUp = ringUp)
    }

    fun dispose() { renderer?.dispose(); renderer = null }

    private companion object {
        const val MAX_R = 1.5f
        const val DISK_TILT = 16f
        const val RING_TILT = 38f
        const val COIN_FLIGHT = 0.9f
    }
}
