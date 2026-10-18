package cube.run.game.stage

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import cube.run.core.Gdx3DGame
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.data.Progress
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A mystery box bought in the shop opens right there in the showroom: the cube
 * steps aside, the gift drops in beside it (the cube hops), rattles and shatters
 * (the cube jumps and spins round to look), and the prize
 * hangs where the box was for a moment before it flies into the cube, which
 * flips to catch it and glides back to the middle. The shop never leaves the
 * screen. The reward is already banked ([Stage.shopGift]); the UI hears the
 * burst through [Stage.shopGiftOpened] and a tap on the showroom
 * ([Stage.shopGiftSkips]) hurries the show along.
 */
class ShopGift(private val game: Gdx3DGame) {
    private val art = GiftArt(game)
    private val tmp = Vector3()
    val rayColor = Color()

    private val NONE = 0
    private val DROP = 1
    private val RATTLE = 2
    private val OPEN = 3
    private val AWAY = 4
    private var phase = NONE
    private var t = 0f
    private var reward: Progress.BoxReward? = null
    private var drop = 0f
    private var dropV = 0f
    private var yaw = 20f
    private var asideTarget = 0f

    /** Eased 0..1: how far the cube has stepped aside for the gift. */
    var aside = 0f
        private set
    /** Eased 0..1: how far the shop's sunburst has moved from the cube onto the prize. */
    var focus = 0f
        private set
    /** Set on the frame the prize lands in the cube: the cube flips to catch it. */
    var caught = false
        private set
    /** Set on the frame the box thumps down beside the cube: the cube hops. */
    var landed = false
        private set
    /** Set on the frame the box bursts: the cube jumps and spins round to see. */
    var burst = false
        private set

    val active: Boolean get() = phase != NONE
    val opened: Boolean get() = phase >= OPEN

    private val boxX = 1.0f
    private val size = 0.82f
    private val prizeScale = 0.62f

    /** How far the cube steps aside (left) while the gift is beside it. */
    val cubeShift: Float get() = -boxX * aside
    private var cubeY = 1f

    fun reset() {
        phase = NONE; reward = null; asideTarget = 0f; aside = 0f; focus = 0f; caught = false
        Stage.shopGiftSkips.set(0) // a stale tap must not burst the next box the moment it drops
    }

    /** Wrap up at once (another purchase, the void, leaving): the prize is already the player's. */
    fun finish() {
        if (phase == NONE) return
        phase = NONE; reward = null; asideTarget = 0f
    }

    fun update(dt: Float, time: Float) {
        caught = false; landed = false; burst = false
        val request = Stage.shopGiftRequests.getAndSet(0)
        if (request > 0) {
            Stage.shopGift?.let { start(it) }
        }
        val skip = Stage.shopGiftSkips.getAndSet(0) > 0
        t += dt
        when (phase) {
            DROP -> { // falls in beside the cube and lands with a soft thump
                yaw += 40f * dt
                dropV += 20f * dt
                drop = max(0f, drop - dropV * dt)
                if (drop == 0f) {
                    SoundFx.play("pop", rate = 0.6f, vol = 0.5f); Haptics.click()
                    landed = true
                    game.burst3d(tmp.set(boxX, cubeY - 0.6f, 0f), art.body, n = 6, speed = 2f, size = 0.07f, life = 0.35f)
                    phase = RATTLE; t = 0f
                    SoundFx.play("pop", rate = 0.75f, vol = 0.3f) // a soft knock, not a hiss
                } else if (skip) burst()
            }
            RATTLE -> { // harder and harder, then it bursts
                yaw += sin(t * 55f) * (200f + 800f * t) * dt
                if (t > 0.5f || skip) burst()
            }
            OPEN -> {
                val r = reward
                if (r != null) art.emitTrail(r, boxX, prizeY, 0f, dt, time, prizeScale)
                if (t > hold() || (skip && t > 0.25f)) { phase = AWAY; t = 0f }
            }
            AWAY -> if (t > 0.32f) { // into the cube
                phase = NONE; reward = null; asideTarget = 0f; caught = true
                SoundFx.play("pop", rate = 1.3f, vol = 0.45f); Haptics.tick()
            }
        }
        val ease = min(1f, dt * 7f)
        aside += (asideTarget - aside) * ease
        focus += ((if (phase == OPEN || phase == RATTLE) 1f else 0f) - focus) * min(1f, dt * 5f)
        if (phase == NONE && aside < 0.002f) aside = 0f
    }

    private var prizeY = 1f

    /** How long the prize stays on show: a new cosmetic gets a proper look. */
    private fun hold(): Float = when (reward?.kind) {
        Progress.BoxReward.SKIN -> 2.6f
        Progress.BoxReward.COINS -> 1.3f
        else -> 1.6f
    }

    private fun start(r: Progress.BoxReward) {
        reward = r
        phase = DROP; t = 0f
        drop = 3.2f; dropV = 0f; yaw = 20f
        asideTarget = 1f
    }

    private fun burst() {
        val r = reward ?: run { phase = NONE; return }
        phase = OPEN; t = 0f; drop = 0f
        burst = true
        art.rayColor(r, rayColor)
        art.shatter(boxX, cubeY, 0f, scale = 0.65f)
        game.flash(rayColor, 0.12f)
        SoundFx.play("boom", rate = 1.7f, vol = 0.25f)
        SoundFx.play(if (r.kind == Progress.BoxReward.COINS) "coin" else "buy", rate = if (r.kind == Progress.BoxReward.COINS) 1f else 1.12f, vol = 0.6f)
        if (r.rare) SoundFx.play("bell", rate = 1.25f, vol = 0.45f)
        Haptics.success()
        Stage.shopGiftOpened.incrementAndGet()
    }

    /**
     * The box (or the prize), beside a cube at ([cubeX], [cubeY]). [grow] for the prize:
     * it swells out of the shards, then shrinks into the cube on its way back.
     */
    fun render(time: Float, cubeX: Float, cubeY: Float) {
        this.cubeY = cubeY
        val r = reward ?: return
        when (phase) {
            DROP, RATTLE -> art.box(boxX, cubeY - 0.25f + drop + 0.04f * sin(t * 3f), 0f, size, yaw)
            OPEN, AWAY -> {
                val (px, py, grow) = prizePose(time, cubeX, cubeY)
                prizeY = py
                art.prize(r, px, py, 0f, grow, time, prizeScale)
            }
        }
    }

    fun renderBlended(cam: PerspectiveCamera, time: Float, cubeX: Float, cubeY: Float) {
        val r = reward ?: return
        if (phase != OPEN && phase != AWAY) return
        val (px, py, grow) = prizePose(time, cubeX, cubeY)
        art.prizeBlended(cam, r, px, py, 0f, grow, time, prizeScale)
    }

    /** Where the prize is: hanging where the box was, then flying into the cube. */
    private fun prizePose(time: Float, cubeX: Float, cubeY: Float): Triple<Float, Float, Float> {
        val hx = boxX
        val hy = cubeY + 0.08f * sin(time * 2.5f)
        return if (phase == OPEN) Triple(hx, hy, min(1f, t * 3f))
        else {
            val k = min(1f, t / 0.32f)
            val e = k * k
            Triple(hx + (cubeX - hx) * e, hy + (cubeY - hy) * e + 0.5f * sin(k * 3.14159f), 1f - 0.8f * e)
        }
    }

    /** The sunburst's centre and colour: the shop's own gold on the cube, the prize's colour on the prize. */
    fun rays(cubeX: Float, cubeY: Float, gold: Color, out: Vector3, col: Color) {
        out.set(cubeX + (boxX - cubeX) * focus, cubeY, 0f)
        col.set(gold)
        if (opened) col.lerp(rayColor, focus)
    }
}
