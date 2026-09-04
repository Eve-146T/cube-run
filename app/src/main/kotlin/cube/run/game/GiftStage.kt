package cube.run.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import cube.run.core.Gdx3DGame
import cube.run.core.Haptics
import cube.run.core.Progress
import cube.run.core.Stage
import cube.run.core.SoundFx
import cube.run.core.hsvInto
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The run-over mystery-box stage, rendered by the engine in 3D: a gift box on
 * a dark backdrop that bobs and turns, shakes when the player taps (see
 * [Stage.openRequests]), launches its lid with a fountain of gold and
 * reports the reward through `session.boxOpened`. It then sits open until
 * the next request, which drops the next box in and opens it straight away —
 * one tap per box, never a closed box waiting for a second tap.
 */
class GiftStage(private val game: Gdx3DGame) {

    var active = false
        private set

    private val IDLE = 0
    private val SHAKE = 1
    private val OPEN = 2
    private val OPENED = 3
    private var phase = IDLE
    private var autoOpen = false   // the box dropping in now opens by itself once it lands
    private var t = 0f
    private var yaw = 0f
    private var drop = 0f          // extra height while a fresh box falls in
    private var dropV = 0f
    private var lidY = 0f
    private var lidVy = 0f
    private var lidYaw = 0f
    private var lidDx = 0f
    private var glow = 0f
    private val body = Color()
    private val band = Color()
    private val lid = Color()
    private val gold = Color()
    private val shadow = Color(0.02f, 0.01f, 0.05f, 1f)
    private val tmp = Vector3()
    private val tmpCol = Color()

    init {
        hsvInto(body, 282f, 0.72f, 0.95f)
        hsvInto(lid, 275f, 0.55f, 1f)
        hsvInto(band, 46f, 0.8f, 1f)
        hsvInto(gold, 46f, 0.85f, 1f)
    }

    fun enter(bgTop: Color, bgBottom: Color) {
        active = true
        phase = IDLE; t = 0f; yaw = 20f; glow = 0f; autoOpen = false
        newBox()
        Stage.openRequests.set(0)
        hsvInto(bgTop, 265f, 0.55f, 0.28f)
        hsvInto(bgBottom, 250f, 0.6f, 0.06f)
    }

    fun exit() { active = false }

    private fun newBox() {
        drop = 4f; dropV = 0f
        lidY = 0f; lidVy = 0f; lidYaw = 0f; lidDx = 0f
    }

    fun update(dt: Float, time: Float) {
        t += dt
        if (drop > 0f) { // a fresh box falls in and lands with a thump
            dropV += 22f * dt
            drop = max(0f, drop - dropV * dt)
            if (drop == 0f) {
                SoundFx.play("place", rate = 0.8f); Haptics.click()
                game.burst3d(tmp.set(0f, 0.05f, 0f), body, n = 10, speed = 3f, size = 0.08f, life = 0.4f)
                if (autoOpen) { autoOpen = false; shake() }
            }
        }
        when (phase) {
            IDLE -> {
                yaw += 35f * dt
                glow = max(0f, glow - dt * 1.5f)
                if (drop == 0f && Stage.openRequests.getAndSet(0) > 0) shake()
            }
            SHAKE -> { // rattles harder and harder, then bursts
                yaw += sin(t * 55f) * (200f + 600f * t) * dt
                if (t > 0.75f) {
                    phase = OPEN; t = 0f
                    lidVy = 7.5f; lidDx = 0f; glow = 1f
                    val reward = Progress.openBox()
                    game.session.boxOpened(reward.kind, reward.amount)
                    val bubble = reward.kind == Progress.BoxReward.BUBBLE
                    SoundFx.play(if (bubble) "success" else "coin", rate = if (bubble) 1f else 1.2f)
                    SoundFx.play("boom", rate = 1.6f, vol = 0.4f)
                    Haptics.success()
                    game.flash(if (bubble) hsvInto(tmpCol, 190f, 0.4f, 1f) else hsvInto(tmpCol, 46f, 0.5f, 1f), 0.3f)
                    game.burst3d(tmp.set(0f, 1.1f, 0f), if (bubble) hsvInto(tmpCol, 190f, 0.5f, 1f) else gold, n = 46, speed = 6f, size = 0.16f, life = 1.2f)
                    game.burst3d(tmp, Color.WHITE, n = 14, speed = 8f, size = 0.09f, life = 0.6f)
                }
            }
            OPEN -> { // the lid tumbles away while treasure keeps spilling out
                lidY += lidVy * dt; lidVy -= 14f * dt
                lidDx += 1.6f * dt
                lidYaw += 420f * dt
                yaw += 90f * dt
                if (t < 0.7f && (t * 60f).toInt() % 3 == 0) {
                    game.burst3d(tmp.set(0f, 1.2f, 0f), gold, n = 3, speed = 4f, size = 0.12f, life = 0.9f)
                }
                if (t > 1.2f) { phase = OPENED; t = 0f }
            }
            OPENED -> { // the open box turns slowly, the lid comes to rest beside it; the next request swaps in the next box, already opening
                val floorLid = 0.14f - 0.62f - 1.2f * 0.52f // lidY at which the lid sits on the floor
                if (lidY > floorLid) {
                    lidY += lidVy * dt; lidVy -= 14f * dt
                    lidDx += 1.6f * dt
                    lidYaw += 420f * dt
                    if (lidY <= floorLid) { lidY = floorLid; lidVy = 0f; SoundFx.play("place", rate = 1.3f, vol = 0.5f) }
                }
                yaw += 30f * dt
                if (Stage.openRequests.getAndSet(0) > 0) { phase = IDLE; t = 0f; autoOpen = true; newBox() }
            }
        }
    }

    private fun shake() {
        phase = SHAKE; t = 0f
        SoundFx.play("slide", rate = 1.6f, vol = 0.8f)
    }

    /** Fixed stage camera: close, slightly above, looking at the box. */
    fun aimCamera(cam: PerspectiveCamera) {
        cam.position.set(0f, 2.6f, 7.6f)
        cam.lookAt(0f, 0.9f, 0f)
        cam.up.set(0f, 1f, 0f)
        cam.fieldOfView = 40f
    }

    fun render() {
        val y = 0.62f + drop + 0.05f * sin(t * 3f)
        val s = 1.2f
        // shadow disc (a flat dark box), shrinking while the box is up in the air
        val sh = (1f - drop * 0.18f).coerceIn(0.3f, 1f)
        game.worldBox(0f, 0.01f, 0f, 1.5f * sh, 0.02f, 1.5f * sh, shadow)
        // body + ribbon
        game.worldBoxSpin(0f, y, 0f, s, s * 0.82f, s, yaw, body)
        game.worldBoxSpin(0f, y, 0f, s + 0.06f, s * 0.24f, s * 0.26f, yaw, band)
        game.worldBoxSpin(0f, y, 0f, s * 0.26f, s * 0.24f, s + 0.06f, yaw, band)
        if (phase == OPEN || phase == OPENED || glow > 0f) { // light pouring out of the open box
            val g = if (phase == OPEN || phase == OPENED) 1f else glow
            hsvInto(tmpCol, 50f, 0.35f * g, 1f)
            game.worldBoxSpin(0f, y + s * 0.44f, 0f, s * 0.86f, 0.12f, s * 0.86f, yaw, tmpCol)
        }
        // lid (+ bow), launched on open
        val rad = Math.toRadians(yaw.toDouble())
        val lx = lidDx * cos(rad).toFloat(); val lz = -lidDx * sin(rad).toFloat()
        val ly = y + s * 0.41f + s * 0.11f + lidY
        game.worldBoxSpin(lx, ly, lz, s + 0.12f, s * 0.22f, s + 0.12f, yaw + lidYaw, lid)
        game.worldBoxSpin(lx, ly, lz, s + 0.18f, s * 0.26f, s * 0.26f, yaw + lidYaw, band)
        game.worldBoxSpin(lx, ly, lz, s * 0.26f, s * 0.26f, s + 0.18f, yaw + lidYaw, band)
        game.worldBoxSpin(lx, ly + s * 0.2f, lz, s * 0.32f, s * 0.18f, s * 0.32f, yaw + lidYaw + 45f, band) // the bow
    }
}
