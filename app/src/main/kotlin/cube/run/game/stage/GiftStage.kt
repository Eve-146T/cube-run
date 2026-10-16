package cube.run.game.stage

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import cube.run.core.Gdx3DGame
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.core.hsvInto
import cube.run.data.Progress
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The run-over mystery-box stage, rendered by the engine in 3D: a gift box
 * drops in and waits, bobbing and turning, with a soft sunburst behind it.
 * A tap (see [Stage.openRequests]) rattles it harder and harder until it
 * bursts: the box shatters into shards and is gone, the sunburst blazes in
 * the reward's colour, a few coins spill, and the prize — a coin pile, a
 * bubble, or a brand-new wardrobe item shown as itself — hangs where the
 * box was. No screen shake anywhere. The reward is reported through
 * `session.boxOpened`. The next request sends the prize up and away and
 * drops the next box in, already opening.
 */
class GiftStage(private val game: Gdx3DGame) {

    var active = false
        private set

    private val IDLE = 0
    private val SHAKE = 1
    private val OPENED = 2
    private var phase = IDLE
    private var autoOpen = false
    private var t = 0f
    private var yaw = 0f
    private var drop = 0f          // extra height while a fresh box falls in
    private var dropV = 0f
    private var glow = 0f
    private var prizeUp = 0f       // the previous prize flying away
    private var reward: Progress.BoxReward? = null
    private val art = GiftArt(game)
    private val rayCol = Color()
    private val tmp = Vector3()
    private val tmpCol = Color()

    // spilled coins: x y z vx vy vz spin
    private val maxCoins = 16
    private val cp = FloatArray(maxCoins * 7)
    private var coinsLive = 0

    private val boxY = 0.62f

    init {
        hsvInto(rayCol, 46f, 0.5f, 1f)
    }

    fun enter(bgTop: Color, bgBottom: Color) {
        active = true
        phase = IDLE; t = 0f; yaw = 20f; glow = 0f; autoOpen = false; reward = null; prizeUp = 0f
        newBox()
        hsvInto(bgTop, 265f, 0.55f, 0.28f)
        hsvInto(bgBottom, 250f, 0.6f, 0.06f)
    }

    fun exit() { active = false }

    private fun newBox() {
        drop = 4f; dropV = 0f
        coinsLive = 0
    }

    fun update(dt: Float, time: Float) {
        t += dt
        glow = max(0f, glow - dt * 0.6f)
        if (prizeUp > 0f) prizeUp += dt * 9f
        if (drop > 0f) { // a fresh box falls in and lands with a soft thump
            dropV += 22f * dt
            drop = max(0f, drop - dropV * dt)
            if (drop == 0f) {
                SoundFx.play("pop", rate = 0.6f, vol = 0.5f); Haptics.click()
                game.burst3d(tmp.set(0f, 0.05f, 0f), art.body, n = 8, speed = 2.5f, size = 0.08f, life = 0.4f)
                if (autoOpen) { autoOpen = false; shake() }
            }
        }
        when (phase) {
            IDLE -> {
                yaw += 35f * dt
                if (drop == 0f && Stage.openRequests.getAndSet(0) > 0) shake()
            }
            SHAKE -> { // rattles harder and harder, then bursts
                yaw += sin(t * 55f) * (200f + 700f * t) * dt
                if (t > 0.85f) burst()
            }
            OPENED -> { // the prize turns where the box was; the next request sends it off and drops the next box
                yaw += 30f * dt
                if (Stage.openRequests.getAndSet(0) > 0) { phase = IDLE; t = 0f; autoOpen = true; prizeUp = 0.01f; newBox() }
            }
        }
        if (Stage.skipBoxRequests.getAndSet(0) > 0) {
            when (phase) {
                IDLE -> if (autoOpen || Stage.openRequests.getAndSet(0) > 0) {
                    drop = 0f; dropV = 0f; autoOpen = false
                    shake(); burst(); t = 1f
                }
                SHAKE -> { burst(); t = 1f }
                OPENED -> t = max(t, 1f)
            }
        }
        reward?.let { if (phase == OPENED) art.emitTrail(it, 0f, prizeY(time), 0f, dt, time) }
        var i = 0
        while (i < coinsLive) {
            val o = i * 7
            cp[o + 4] -= 16f * dt
            cp[o] += cp[o + 3] * dt; cp[o + 1] += cp[o + 4] * dt; cp[o + 2] += cp[o + 5] * dt
            cp[o + 6] += 540f * dt
            if (cp[o + 1] < -0.5f) { // fell off the stage: retire (swap-remove)
                coinsLive--
                val l = coinsLive * 7
                for (k in 0 until 7) cp[o + k] = cp[l + k]
                continue
            }
            i++
        }
    }

    private fun shake() {
        phase = SHAKE; t = 0f
        prizeUp = 0f; reward = null
        SoundFx.play("pop", rate = 0.75f, vol = 0.3f) // a soft knock as it starts to rattle (a noise burst here was all hiss)
    }

    /** The box shatters and is gone; the prize is what is left. */
    private fun burst() {
        phase = OPENED; t = 0f
        glow = 1f
        val r = Progress.openBox()
        reward = r
        game.session.boxOpened(r.kind, r.amount, r.cat, r.id)
        val bubble = r.kind == Progress.BoxReward.BUBBLE
        val skin = r.kind == Progress.BoxReward.SKIN
        val shards = r.kind == Progress.BoxReward.SHARDS
        // Soft and round: two loud jingles on a boom used to stack past full scale and clip.
        SoundFx.play("boom", rate = 1.6f, vol = 0.25f)
        SoundFx.play(if (bubble || shards || skin) "buy" else "coin", rate = if (shards) 1.12f else 1f, vol = 0.6f)
        if (r.rare) SoundFx.play("bell", rate = 1.25f, vol = 0.45f)
        Haptics.success()
        game.slowMo(0.6f, 0.15f)
        val col = art.rayColor(r, tmpCol)
        rayCol.set(col)
        game.flash(col, 0.18f)
        art.shatter(0f, boxY, 0f) // the box itself becomes the shards
        if (!bubble && !skin && !shards) { // a few coins spill out and fall away
            coinsLive = if (r.rare) maxCoins else maxCoins / 2
            for (i in 0 until coinsLive) {
                val o = i * 7
                val a = i * 2.39996f
                cp[o] = 0f; cp[o + 1] = boxY + 0.4f; cp[o + 2] = 0f
                cp[o + 3] = cos(a) * (0.8f + (i % 5) * 0.35f); cp[o + 4] = 5f + (i % 7) * 0.6f; cp[o + 5] = sin(a) * (0.6f + (i % 5) * 0.25f) - 1.2f
                cp[o + 6] = i * 40f
            }
        }
    }

    /** Stage camera: close, slightly above, looking at the box. */
    fun aimCamera(cam: PerspectiveCamera) {
        cam.position.set(0f, 2.6f, 7.6f)
        cam.lookAt(0f, 0.9f, 0f)
        cam.up.set(0f, 1f, 0f)
        cam.fieldOfView = 40f
    }

    private fun prizeY(time: Float): Float = boxY + 0.35f + 0.1f * sin(time * 2.5f) + prizeUp * prizeUp

    fun render(time: Float) {
        if (phase != OPENED) art.box(0f, boxY + drop + 0.05f * sin(t * 3f), 0f, 1.2f, yaw) // bobbing (and falling in)
        for (i in 0 until coinsLive) {
            val o = i * 7
            game.worldCoin(cp[o], cp[o + 1], cp[o + 2], 0.17f, 0.07f, cp[o + 6], art.gold)
        }
        val r = reward
        if (phase == OPENED && r != null) art.prize(r, 0f, prizeY(time), 0f, min(1f, t * 2.2f), time) // where the box was
    }

    /** The one sunburst behind the box: a quiet violet while it waits, the reward's colour once it is open — centred on the box / prize as seen on screen. */
    fun renderShapes(shapes: ShapeRenderer, time: Float) {
        val open = phase == OPENED
        val g = if (open) min(1f, t * 3f) else 0f
        val y = if (open) prizeY(time) else boxY + drop + 0.2f
        hsvInto(tmpCol, 268f, 0.5f, 0.75f)
        tmpCol.lerp(rayCol, g)
        game.sunburstBehind(shapes, 0f, y, 0f, 3f, 9f + 3f * g, 12, time * (12f + 14f * g), tmpCol, 0.3f + 0.15f * g, 0.5f)
    }

    /** The bubble prize (or bubble-skin prize) where the box was. */
    fun renderBlended(cam: PerspectiveCamera, time: Float) {
        val r = reward ?: return
        if (phase == OPENED) art.prizeBlended(cam, r, 0f, prizeY(time), 0f, min(1f, t * 2.2f), time)
    }
}
