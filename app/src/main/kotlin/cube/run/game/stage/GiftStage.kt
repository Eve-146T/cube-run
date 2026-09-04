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
import cube.run.data.BubbleSkins
import cube.run.data.Progress
import cube.run.data.Skins
import cube.run.data.Trails
import cube.run.data.Wardrobe
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The run-over mystery-box stage, rendered by the engine in 3D: a gift box on
 * a dark backdrop that bobs and turns. A tap (see [Stage.openRequests])
 * rattles it harder and harder until it bursts: time hitches, the lid
 * launches, a fountain of real coins and sparks pours out, a sunburst spins
 * up behind it, and the prize hovers above the open box — a coin pile, a
 * bubble, or a brand-new wardrobe item shown as itself. The reward is
 * reported through `session.boxOpened`. It then sits open until the next
 * request, which drops the next box in and opens it straight away.
 */
class GiftStage(private val game: Gdx3DGame) {

    var active = false
        private set

    private val IDLE = 0
    private val SHAKE = 1
    private val OPEN = 2
    private val OPENED = 3
    private var phase = IDLE
    private var autoOpen = false
    private var t = 0f
    private var yaw = 0f
    private var drop = 0f          // extra height while a fresh box falls in
    private var dropV = 0f
    private var lidY = 0f
    private var lidVy = 0f
    private var lidYaw = 0f
    private var lidDx = 0f
    private var glow = 0f
    private var dolly = 0f         // camera push-in on the burst
    private var reward: Progress.BoxReward? = null
    private val body = Color()
    private val band = Color()
    private val lid = Color()
    private val gold = Color()
    private val coinFace = Color()
    private val rayCol = Color()
    private val tmp = Vector3()
    private val tmpCol = Color()

    // the coin fountain: pooled particles (x y z vx vy vz spin)
    private val maxCoins = 36
    private val cp = FloatArray(maxCoins * 7)
    private var coinsLive = 0

    init {
        hsvInto(body, 282f, 0.72f, 0.95f)
        hsvInto(lid, 275f, 0.55f, 1f)
        hsvInto(band, 46f, 0.8f, 1f)
        hsvInto(gold, 46f, 0.85f, 1f)
        hsvInto(coinFace, 50f, 0.55f, 1f)
        hsvInto(rayCol, 46f, 0.5f, 1f)
    }

    fun enter(bgTop: Color, bgBottom: Color) {
        active = true
        phase = IDLE; t = 0f; yaw = 20f; glow = 0f; autoOpen = false; dolly = 0f; reward = null
        newBox()
        Stage.openRequests.set(0)
        hsvInto(bgTop, 265f, 0.55f, 0.28f)
        hsvInto(bgBottom, 250f, 0.6f, 0.06f)
    }

    fun exit() { active = false }

    private fun newBox() {
        drop = 4f; dropV = 0f
        lidY = 0f; lidVy = 0f; lidYaw = 0f; lidDx = 0f
        coinsLive = 0
        reward = null
    }

    fun update(dt: Float, time: Float) {
        t += dt
        dolly = max(0f, dolly - dt * 1.4f)
        if (drop > 0f) { // a fresh box falls in and lands with a thump
            dropV += 22f * dt
            drop = max(0f, drop - dropV * dt)
            if (drop == 0f) {
                SoundFx.play("place", rate = 0.8f); Haptics.click()
                game.shake(0.05f)
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
            SHAKE -> { // rattles harder and harder, ticking up, then bursts
                yaw += sin(t * 55f) * (200f + 700f * t) * dt
                if ((t * 12f).toInt() != ((t - dt) * 12f).toInt()) SoundFx.play("tick", rate = 1f + t * 0.8f, vol = 0.5f)
                if (t > 0.85f) burst()
            }
            OPEN -> { // the lid tumbles away while treasure keeps spilling out
                lidY += lidVy * dt; lidVy -= 14f * dt
                lidDx += 1.6f * dt
                lidYaw += 420f * dt
                yaw += 90f * dt
                if (t < 0.6f && (t * 60f).toInt() % 3 == 0) {
                    game.burst3d(tmp.set(0f, 1.2f, 0f), gold, n = 2, speed = 3.5f, size = 0.12f, life = 0.9f)
                }
                if (t > 1.1f) { phase = OPENED; t = 0f }
            }
            OPENED -> { // the open box turns slowly, the lid comes to rest; the next request swaps in the next box, already opening
                val floorLid = 0.14f - 0.62f - 1.2f * 0.52f
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
        // the coin fountain
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
        SoundFx.play("slide", rate = 1.6f, vol = 0.8f)
    }

    private fun burst() {
        phase = OPEN; t = 0f
        lidVy = 7.5f; lidDx = 0f; glow = 1f; dolly = 0.6f
        val r = Progress.openBox()
        reward = r
        game.session.boxOpened(r.kind, r.amount, r.cat, r.id)
        val bubble = r.kind == Progress.BoxReward.BUBBLE
        val skin = r.kind == Progress.BoxReward.SKIN
        SoundFx.play(if (bubble) "success" else "coin", rate = if (bubble) 1f else 1.2f)
        SoundFx.play("boom", rate = 1.6f, vol = 0.4f)
        if (r.rare) SoundFx.play("success", rate = 1.25f)
        Haptics.success()
        game.slowMo(0.55f, 0.2f)
        game.shake(0.07f)
        val col = when {
            skin -> hsvInto(tmpCol, 300f, 0.4f, 1f)
            bubble -> hsvInto(tmpCol, 190f, 0.4f, 1f)
            else -> hsvInto(tmpCol, 46f, 0.5f, 1f)
        }
        rayCol.set(col)
        game.flash(col, 0.2f)
        game.burst3d(tmp.set(0f, 1.1f, 0f), col, n = 28, speed = 5f, size = 0.15f, life = 1.1f)
        game.burst3d(tmp, Color.WHITE, n = 10, speed = 7f, size = 0.09f, life = 0.6f)
        if (!bubble && !skin) { // a fountain of real coins
            coinsLive = if (r.rare) 26 else 14
            for (i in 0 until coinsLive) {
                val o = i * 7
                val a = i * 2.39996f
                cp[o] = 0f; cp[o + 1] = 1.1f; cp[o + 2] = 0f
                // up and mostly away from the camera, so the shower stays in frame instead of hitting the lens
                cp[o + 3] = cos(a) * (0.8f + (i % 5) * 0.35f); cp[o + 4] = 6.5f + (i % 7) * 0.7f; cp[o + 5] = sin(a) * (0.6f + (i % 5) * 0.25f) - 1.2f
                cp[o + 6] = i * 40f
            }
        }
    }

    /** Stage camera: close, slightly above, looking at the box; pushes in on the burst. */
    fun aimCamera(cam: PerspectiveCamera) {
        val d = dolly * dolly
        cam.position.set(0f, 2.6f - d * 0.3f, 7.6f - d * 1.1f)
        cam.lookAt(0f, 0.9f + d * 0.3f, 0f)
        cam.up.set(0f, 1f, 0f)
        cam.fieldOfView = 40f + d * 4f
    }

    fun render(time: Float) {
        val y = 0.62f + drop + 0.05f * sin(t * 3f)
        val s = 1.2f
        val open = phase == OPEN || phase == OPENED
        // body + ribbon
        game.worldBoxSpin(0f, y, 0f, s, s * 0.82f, s, yaw, body)
        game.worldBoxSpin(0f, y, 0f, s + 0.06f, s * 0.24f, s * 0.26f, yaw, band)
        game.worldBoxSpin(0f, y, 0f, s * 0.26f, s * 0.24f, s + 0.06f, yaw, band)
        if (open || glow > 0f) { // light pouring out of the open box
            val g = if (open) 1f else glow
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
        // the coin fountain
        for (i in 0 until coinsLive) {
            val o = i * 7
            game.worldCoin(cp[o], cp[o + 1], cp[o + 2], 0.17f, 0.07f, cp[o + 6], gold)
        }
        // the prize, hovering above the open box
        val r = reward
        if (open && r != null) {
            val rise = min(1f, (if (phase == OPEN) t else 1f) * 1.6f)
            val py = y + 1.0f + rise * 0.5f + 0.1f * sin(time * 2.5f)
            when {
                r.kind == Progress.BoxReward.SKIN && r.cat == Wardrobe.CUBE -> { // the new cube, as itself
                    val sk = Skins.get(r.id)
                    hsvInto(tmpCol, sk.hueAt(time, 0f), sk.sat, sk.valueAt(time))
                    game.worldBoxSpin(0f, py, 0f, 0.9f * rise, 0.9f * rise, 0.9f * rise, time * 90f, tmpCol)
                }
                r.kind == Progress.BoxReward.SKIN && r.cat == Wardrobe.TRAIL -> { // a little cube orbiting, shedding the new trail
                    val tr = Trails.get(r.id)
                    val a = time * 3f
                    val ox = cos(a) * 1.1f; val oz = sin(a) * 1.1f
                    hsvInto(tmpCol, 0f, 0f, 1f)
                    game.worldBoxSpin(ox, py, oz, 0.45f, 0.45f, 0.45f, time * 200f, tmpCol)
                    if ((time * 30f).toInt() % 2 == 0) {
                        val c = hsvInto(tmpCol, tr.hueAt(time, (time * 30f).toInt()), tr.sat, tr.value)
                        game.burst3d(tmp.set(ox, py, oz), c, n = tr.count, speed = tr.speed, size = tr.size, life = tr.life * 1.5f, gravity = tr.gravity)
                    }
                }
                r.kind == Progress.BoxReward.COINS -> { // one big spinning coin with a raised heart (two for a big win)
                    game.worldCoin(0f, py, 0f, 0.55f * rise, 0.16f, time * 120f, gold)
                    game.worldCoin(0f, py, 0f, 0.36f * rise, 0.22f, time * 120f, coinFace)
                    if (r.amount >= 100) {
                        game.worldCoin(0.7f, py - 0.3f, 0.3f, 0.36f * rise, 0.12f, time * 120f + 60f, gold)
                        game.worldCoin(0.7f, py - 0.3f, 0.3f, 0.23f * rise, 0.17f, time * 120f + 60f, coinFace)
                    }
                }
                r.kind == Progress.BoxReward.BUBBLE -> { /* drawn in the blended pass */ }
            }
        }
    }

    /** The sunburst behind the box: pale while it waits, the reward's colour blazing once it is open. */
    fun renderShapes(shapes: ShapeRenderer, time: Float) {
        val open = phase == OPEN || phase == OPENED
        val g = if (open) min(1f, t * 3f + 0.4f) else glow
        val y = 0.62f + drop + 0.05f * sin(t * 3f)
        hsvInto(tmpCol, 275f, 0.45f, 1f)
        game.sunburst(shapes, 0f, y + 0.3f, -3f, 8f, 12, time * 12f, tmpCol, 0.22f, 0.5f)
        if (g > 0.01f) {
            game.sunburst(shapes, 0f, y + 0.6f, -2.8f, 5f + 9f * g, 16, time * 30f, rayCol, 0.6f * g, 0.5f)
            game.sunburst(shapes, 0f, y + 0.6f, -2.9f, 4f + 7f * g, 8, -time * 45f, Color.WHITE, 0.25f * g, 0.3f)
        }
    }

    /** The bubble prize (or bubble-skin prize) hovering above the box. */
    fun renderBlended(cam: PerspectiveCamera, time: Float) {
        val r = reward ?: return
        if (phase != OPEN && phase != OPENED) return
        val rise = min(1f, (if (phase == OPEN) t else 1f) * 1.6f)
        val y = 0.62f + 0.05f * sin(t * 3f) + 1.0f + rise * 0.5f + 0.1f * sin(time * 2.5f)
        val s = 1.4f * rise
        when {
            r.kind == Progress.BoxReward.BUBBLE ->
                game.bubbles.draw(cam, 0f, y, 0f, s, s, s, time * 40f, time, 170f, 270f, 0.6f, 2.6f, 0.1f, 1f, BubbleSkins.IRIS)
            r.kind == Progress.BoxReward.SKIN && r.cat == Wardrobe.BUBBLE -> {
                val b = BubbleSkins.get(r.id)
                game.bubbles.draw(cam, 0f, y, 0f, s, s, s, time * 40f, time, b.hue, b.hue2, b.sat, b.rim, b.fill, 1f, b.style)
            }
        }
    }
}
