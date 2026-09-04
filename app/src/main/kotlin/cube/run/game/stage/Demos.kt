package cube.run.game.stage

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import cube.run.core.Gdx3DGame
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.core.hsvInto
import cube.run.game.Bubble
import cube.run.game.Player
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * What buying something does to the cube on the shop stage: a bubble goes
 * up around it, coins spiral into it, a twin splits off and slams back in,
 * it lifts on a jet of flame, a portal ring spins around it, a gift box
 * drops in beside it and bursts… One demo at a time, asked for through
 * [Stage.demoRequests]; [lift] is how high the current demo holds the cube.
 */
class Demos(private val game: Gdx3DGame, private val player: Player, private val bubble: Bubble) {

    /** Extra height for the cube (the jet demo). */
    var lift = 0f
        private set

    private var kind = 0
    private var t = 0f
    private var cx = 0f
    private var cy = 0f
    private val gold = Color()
    private val grape = Color()
    private val band = Color()
    private val flame = Color()
    private val portal = Color()
    private val pink = Color()
    private val tmp = Vector3()
    private val tmpCol = Color()

    // coins in flight: x y z vx vy vz spin (magnet: x = angle, y = radius, z = height)
    private val maxCoins = 40
    private val cp = FloatArray(maxCoins * 7)
    private var coinsLive = 0
    private var boxY = 0f
    private var boxVy = 0f
    private var boxGone = false

    init {
        hsvInto(gold, 46f, 0.85f, 1f)
        hsvInto(grape, 282f, 0.72f, 0.95f)
        hsvInto(band, 46f, 0.8f, 1f)
        hsvInto(flame, 28f, 0.9f, 1f)
        hsvInto(portal, 165f, 0.85f, 1f)
        hsvInto(pink, 328f, 0.7f, 1f)
    }

    val running: Boolean get() = kind != 0

    fun reset() { kind = 0; lift = 0f; coinsLive = 0 }

    private fun start(k: Int, px: Float, py: Float) {
        kind = k; t = 0f; cx = px; cy = py
        coinsLive = 0; lift = 0f
        when (k) {
            Stage.DEMO_BUBBLE, Stage.DEMO_REVIVE -> {
                bubble.duration = 2.4f
                bubble.activate(px, py)
                if (k == Stage.DEMO_REVIVE) {
                    game.burst3d(tmp.set(px, py, 0f), pink, n = 30, speed = 7f, size = 0.14f, life = 0.9f)
                    game.burst3d(tmp, Color.WHITE, n = 14, speed = 10f, size = 0.09f, life = 0.5f)
                }
            }
            Stage.DEMO_MAGNET -> {
                coinsLive = 26
                for (i in 0 until coinsLive) { // angle, radius, height, angular speed
                    val o = i * 7
                    cp[o] = i * 2.39996f; cp[o + 1] = 3.2f + (i % 5) * 0.5f; cp[o + 2] = py - 1.2f + (i % 7) * 0.45f
                    cp[o + 3] = 5f + (i % 3) * 1.2f
                    cp[o + 6] = i * 40f
                }
                SoundFx.play("rise", rate = 0.9f)
            }
            Stage.DEMO_COINS -> {
                coinsLive = 34
                for (i in 0 until coinsLive) {
                    val o = i * 7
                    val a = i * 2.39996f
                    cp[o] = px + cos(a) * (0.6f + (i % 6) * 0.35f); cp[o + 1] = py + 3.5f + (i % 9) * 0.5f; cp[o + 2] = sin(a) * (0.5f + (i % 4) * 0.3f)
                    cp[o + 3] = 0f; cp[o + 4] = -1f - (i % 3); cp[o + 5] = 0f; cp[o + 6] = i * 40f
                }
                SoundFx.play("coin", rate = 1.1f)
            }
            Stage.DEMO_JET -> { SoundFx.play("rise", rate = 1.2f); Haptics.click() }
            Stage.DEMO_MULT -> SoundFx.play("blip", rate = 1.2f)
            Stage.DEMO_HEADSTART -> { SoundFx.play("whoosh", rate = 1.1f); game.flash(flame, 0.25f) }
            Stage.DEMO_PORTAL -> { SoundFx.play("rise", rate = 1.4f); game.flash(portal, 0.3f) }
            Stage.DEMO_BOX -> { boxY = 6f; boxVy = 0f; boxGone = false }
        }
    }

    fun update(dt: Float, time: Float, px: Float, py: Float) {
        Stage.demoRequests.getAndSet(0).let { if (it > 0) start(it, px, py) }
        bubble.update(dt, time, px, py)
        if (kind == 0) return
        t += dt
        cx = px; cy = py
        when (kind) {
            Stage.DEMO_BUBBLE, Stage.DEMO_REVIVE -> if (!bubble.active) kind = 0
            Stage.DEMO_MAGNET -> { // every coin spirals in and lands with a chime
                var i = 0
                while (i < coinsLive) {
                    val o = i * 7
                    cp[o] += cp[o + 3] * dt
                    cp[o + 1] -= (2.2f + t * 2.5f) * dt
                    cp[o + 2] += (py - cp[o + 2]) * min(1f, dt * 3f)
                    cp[o + 6] += 540f * dt
                    if (cp[o + 1] < 0.55f) {
                        game.burst3d(tmp.set(px, py, 0f), gold, n = 4, speed = 3f, size = 0.08f, life = 0.35f)
                        SoundFx.play("coin", rate = 1.2f + (coinsLive % 6) * 0.08f, vol = 0.5f)
                        coinsLive--
                        val l = coinsLive * 7
                        for (k in 0 until 7) cp[o + k] = cp[l + k]
                        continue
                    }
                    i++
                }
                if (coinsLive == 0) kind = 0
            }
            Stage.DEMO_COINS -> { // a golden rain past the cube
                var i = 0
                while (i < coinsLive) {
                    val o = i * 7
                    cp[o + 4] -= 12f * dt
                    cp[o + 1] += cp[o + 4] * dt
                    cp[o + 6] += 420f * dt
                    if (cp[o + 1] < -1.5f) {
                        coinsLive--
                        val l = coinsLive * 7
                        for (k in 0 until 7) cp[o + k] = cp[l + k]
                        continue
                    }
                    i++
                }
                if (coinsLive == 0) kind = 0
            }
            Stage.DEMO_JET -> { // up on a jet of flame, hang, drop back
                val target = if (t < 1.3f) 1.7f else 0f
                lift += (target - lift) * min(1f, dt * (if (t < 1.3f) 5f else 9f))
                if (t < 1.3f) game.burst3d(tmp.set(px, py + lift - 0.5f, 0.2f), flame, n = 3, speed = 2.5f, size = 0.12f, life = 0.35f, gravity = -10f)
                if (t > 1.3f && t - dt <= 1.3f) SoundFx.play("slide", rate = 1.2f, vol = 0.6f)
                if (t > 2.2f) { lift = 0f; kind = 0; SoundFx.play("pop", rate = 0.9f); game.burst3d(tmp.set(px, py - 0.4f, 0.3f), player.trailCol(), n = 10, speed = 3f, size = 0.09f, life = 0.4f) }
            }
            Stage.DEMO_MULT -> if (t > 1.25f) { // the twins slam back in
                kind = 0
                game.burst3d(tmp.set(px, py, 0f), player.trailCol(), n = 30, speed = 6f, size = 0.14f, life = 0.7f)
                game.burst3d(tmp, Color.WHITE, n = 10, speed = 9f, size = 0.08f, life = 0.4f)
                SoundFx.play("perfect", rate = 1.1f); Haptics.success(); game.shake(0.15f)
            }
            Stage.DEMO_HEADSTART -> { // a stream of fire off the back, the cube bucking forward
                game.burst3d(tmp.set(px + (Math.random().toFloat() - 0.5f) * 0.5f, py - 0.1f, -0.3f), if ((t * 30f).toInt() % 2 == 0) flame else gold, n = 4, speed = 2f, size = 0.12f, life = 0.45f, gravity = -4f, biasZ = -7f)
                if (t > 1.1f) kind = 0
            }
            Stage.DEMO_PORTAL -> if (t > 2.2f) kind = 0
            Stage.DEMO_BOX -> {
                if (!boxGone) {
                    boxVy -= 22f * dt
                    boxY = max(0.45f, boxY + boxVy * dt)
                    if (boxY == 0.45f && boxVy < 0f) { boxVy = 0f; SoundFx.play("place", rate = 0.8f); Haptics.click(); game.shake(0.1f) }
                    if (boxY == 0.45f && t > 1.1f) { // pop!
                        boxGone = true
                        game.burst3d(tmp.set(px + 1.7f, 0.5f, 0f), grape, n = 34, speed = 7f, size = 0.14f, life = 0.9f)
                        game.burst3d(tmp, band, n = 16, speed = 9f, size = 0.1f, life = 0.7f)
                        game.burst3d(tmp, Color.WHITE, n = 10, speed = 11f, size = 0.08f, life = 0.45f)
                        SoundFx.play("boom", rate = 1.6f, vol = 0.5f); SoundFx.play("success", rate = 1.3f, vol = 0.6f); Haptics.success()
                        game.flash(grape, 0.3f)
                    }
                }
                if (t > 1.6f) kind = 0
            }
        }
    }

    fun render(time: Float) {
        when (kind) {
            Stage.DEMO_MAGNET -> for (i in 0 until coinsLive) {
                val o = i * 7
                game.worldCoin(cx + cos(cp[o]) * cp[o + 1], cp[o + 2], sin(cp[o]) * cp[o + 1] * 0.6f, 0.2f, 0.08f, cp[o + 6], gold)
            }
            Stage.DEMO_COINS -> for (i in 0 until coinsLive) {
                val o = i * 7
                game.worldCoin(cp[o], cp[o + 1], cp[o + 2], 0.24f, 0.09f, cp[o + 6], gold)
            }
            Stage.DEMO_MULT -> { // two twins orbit out and back
                val k = sin(min(1f, t / 1.25f) * 3.14159f)
                val r = 1.5f * k
                val a = t * 7f
                val s = 0.9f * (0.35f + 0.5f * k)
                hsvInto(tmpCol, player.skin.hueAt(time, 0f), player.skin.sat, player.skin.valueAt(time))
                game.worldBoxSpin(cx + cos(a) * r, cy + 0.3f * sin(t * 9f), sin(a) * r * 0.5f, s, s, s, t * 400f, tmpCol)
                game.worldBoxSpin(cx - cos(a) * r, cy - 0.3f * sin(t * 9f), -sin(a) * r * 0.5f, s, s, s, -t * 400f, tmpCol)
            }
            Stage.DEMO_PORTAL -> { // a bead ring spinning around the cube, growing then shrinking away
                val grow = if (t < 0.5f) t / 0.5f else if (t > 1.7f) max(0f, 1f - (t - 1.7f) / 0.5f) else 1f
                val radius = 2.1f * (1f - (1f - grow) * (1f - grow))
                val n = 14
                for (i in 0 until n) {
                    val a = time * 2.2f + i * (6.2832f / n)
                    val s = 0.26f + 0.08f * sin(time * 6f + i)
                    hsvInto(tmpCol, 165f + i * 12f, 0.75f, 1f)
                    game.worldBoxSpin(cx + cos(a) * radius, cy + sin(a) * radius, -0.6f, s, s, s, time * 200f + i * 30f, tmpCol)
                }
            }
            Stage.DEMO_BOX -> if (!boxGone) { // the gift, dropping in beside the cube
                val x = cx + 1.7f
                val yaw = t * 60f + 20f
                val squash = if (boxY == 0.45f) 1f + 0.12f * max(0f, sin((t - 0.4f) * 10f)) else 1f
                game.worldBoxSpin(x, boxY, 0f, 0.9f * squash, 0.9f / squash, 0.9f * squash, yaw, grape)
                game.worldBoxSpin(x, boxY, 0f, 0.96f * squash, 0.18f, 0.2f, yaw, band)
                game.worldBoxSpin(x, boxY, 0f, 0.2f, 0.18f, 0.96f * squash, yaw, band)
                game.worldBoxSpin(x, boxY + 0.5f, 0f, 0.3f, 0.2f, 0.3f, yaw + 45f, band)
            }
        }
    }

    fun renderBlended(cam: PerspectiveCamera, time: Float) {
        bubble.render(cam, time)
    }
}
