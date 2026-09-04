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
    private val body = Color()
    private val band = Color()
    private val gold = Color()
    private val coinFace = Color()
    private val rayCol = Color()
    private val tmp = Vector3()
    private val tmpCol = Color()

    // spilled coins: x y z vx vy vz spin
    private val maxCoins = 16
    private val cp = FloatArray(maxCoins * 7)
    private var coinsLive = 0

    private val boxY = 0.62f

    init {
        hsvInto(body, 282f, 0.72f, 0.95f)
        hsvInto(band, 46f, 0.8f, 1f)
        hsvInto(gold, 46f, 0.85f, 1f)
        hsvInto(coinFace, 50f, 0.55f, 1f)
        hsvInto(rayCol, 46f, 0.5f, 1f)
    }

    fun enter(bgTop: Color, bgBottom: Color) {
        active = true
        phase = IDLE; t = 0f; yaw = 20f; glow = 0f; autoOpen = false; reward = null; prizeUp = 0f
        newBox()
        Stage.openRequests.set(0)
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
                SoundFx.play("place", rate = 0.8f); Haptics.click()
                game.burst3d(tmp.set(0f, 0.05f, 0f), body, n = 8, speed = 2.5f, size = 0.08f, life = 0.4f)
                if (autoOpen) { autoOpen = false; shake() }
            }
        }
        when (phase) {
            IDLE -> {
                yaw += 35f * dt
                if (drop == 0f && Stage.openRequests.getAndSet(0) > 0) shake()
            }
            SHAKE -> { // rattles harder and harder, ticking up, then bursts
                yaw += sin(t * 55f) * (200f + 700f * t) * dt
                if ((t * 12f).toInt() != ((t - dt) * 12f).toInt()) SoundFx.play("tick", rate = 1f + t * 0.8f, vol = 0.5f)
                if (t > 0.85f) burst()
            }
            OPENED -> { // the prize turns where the box was; the next request sends it off and drops the next box
                yaw += 30f * dt
                if (Stage.openRequests.getAndSet(0) > 0) { phase = IDLE; t = 0f; autoOpen = true; prizeUp = 0.01f; newBox() }
            }
        }
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
        SoundFx.play("slide", rate = 1.6f, vol = 0.8f)
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
        SoundFx.play(if (bubble || shards) "success" else "coin", rate = if (bubble) 1f else if (shards) 1.3f else 1.2f)
        SoundFx.play("boom", rate = 1.6f, vol = 0.35f)
        if (r.rare) SoundFx.play("success", rate = 1.25f)
        Haptics.success()
        game.slowMo(0.6f, 0.15f)
        val col = when {
            skin -> hsvInto(tmpCol, 300f, 0.4f, 1f)
            bubble -> hsvInto(tmpCol, 190f, 0.4f, 1f)
            shards -> hsvInto(tmpCol, cube.run.data.Shards.get(r.id).hue, 0.55f, 1f)
            else -> hsvInto(tmpCol, 46f, 0.5f, 1f)
        }
        rayCol.set(col)
        game.flash(col, 0.18f)
        // the box itself becomes the shards
        game.burst3d(tmp.set(0f, boxY, 0f), body, n = 34, speed = 5.5f, size = 0.2f, life = 1.2f)
        game.burst3d(tmp, band, n = 14, speed = 6f, size = 0.14f, life = 1.0f)
        game.burst3d(tmp, Color.WHITE, n = 10, speed = 7f, size = 0.09f, life = 0.5f)
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
        val s = 1.2f
        if (phase != OPENED) { // the box: body + ribbon + bow, bobbing (and falling in)
            val y = boxY + drop + 0.05f * sin(t * 3f)
            game.worldBoxSpin(0f, y, 0f, s, s * 0.82f, s, yaw, body)
            game.worldBoxSpin(0f, y, 0f, s + 0.06f, s * 0.24f, s * 0.26f, yaw, band)
            game.worldBoxSpin(0f, y, 0f, s * 0.26f, s * 0.24f, s + 0.06f, yaw, band)
            game.worldBoxSpin(0f, y + s * 0.52f, 0f, s + 0.12f, s * 0.22f, s + 0.12f, yaw, body)
            game.worldBoxSpin(0f, y + s * 0.52f, 0f, s + 0.18f, s * 0.26f, s * 0.26f, yaw, band)
            game.worldBoxSpin(0f, y + s * 0.52f, 0f, s * 0.26f, s * 0.26f, s + 0.18f, yaw, band)
            game.worldBoxSpin(0f, y + s * 0.72f, 0f, s * 0.32f, s * 0.18f, s * 0.32f, yaw + 45f, band) // the bow
        }
        for (i in 0 until coinsLive) {
            val o = i * 7
            game.worldCoin(cp[o], cp[o + 1], cp[o + 2], 0.17f, 0.07f, cp[o + 6], gold)
        }
        val r = reward
        if (phase == OPENED && r != null) { // the prize, where the box was
            val rise = min(1f, t * 2.2f)
            val py = prizeY(time)
            when {
                r.kind == Progress.BoxReward.SKIN && r.cat == Wardrobe.CUBE -> { // the new cube, as itself
                    val sk = Skins.get(r.id)
                    hsvInto(tmpCol, sk.hueAt(time, 0f), sk.sat, sk.valueAt(time))
                    game.worldBoxSpin(0f, py, 0f, 0.95f * rise, 0.95f * rise, 0.95f * rise, time * 90f, tmpCol)
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
                    game.worldCoin(0f, py, 0f, 0.6f * rise, 0.16f, time * 120f, gold)
                    game.worldCoin(0f, py, 0f, 0.4f * rise, 0.22f, time * 120f, coinFace)
                    if (r.amount >= 100) {
                        game.worldCoin(0.75f, py - 0.3f, 0.3f, 0.38f * rise, 0.12f, time * 120f + 60f, gold)
                        game.worldCoin(0.75f, py - 0.3f, 0.3f, 0.25f * rise, 0.17f, time * 120f + 60f, coinFace)
                    }
                }
                r.kind == Progress.BoxReward.SHARDS -> { // a cluster of crystals turning together, more of them for a bigger drop
                    val kind = cube.run.data.Shards.get(r.id)
                    val n = 3 + min(4, r.amount / 8)
                    for (i in 0 until n) {
                        val a = time * 1.6f + i * (6.2832f / n)
                        val rr = if (i == 0) 0f else 0.55f
                        val h = (if (i == 0) 0.9f else 0.5f + 0.1f * (i % 3)) * rise
                        hsvInto(tmpCol, kind.hue + (i % 2) * 14f, 0.75f, 1f)
                        game.worldBoxSpin(cos(a) * rr, py + (if (i == 0) 0.1f else -0.2f + 0.12f * sin(time * 3f + i)), sin(a) * rr, 0.26f * rise, h, 0.26f * rise, time * 120f + i * 40f, tmpCol)
                    }
                }
                r.kind == Progress.BoxReward.BUBBLE -> { /* drawn in the blended pass */ }
            }
        }
    }

    /** The sunburst behind the box: pale while it waits, the reward's colour blazing once it is open. */
    fun renderShapes(shapes: ShapeRenderer, time: Float) {
        val open = phase == OPENED
        val g = if (open) min(1f, t * 3f + 0.4f) else glow
        val y = if (open) prizeY(time) else boxY + drop + 0.3f
        hsvInto(tmpCol, 275f, 0.45f, 1f)
        game.sunburst(shapes, 0f, y, -3f, 8f, 12, time * 12f, tmpCol, 0.22f, 0.5f)
        if (g > 0.01f) {
            game.sunburst(shapes, 0f, y, -2.8f, 5f + 9f * g, 16, time * 30f, rayCol, 0.6f * g, 0.5f)
            game.sunburst(shapes, 0f, y, -2.9f, 4f + 7f * g, 8, -time * 45f, Color.WHITE, 0.25f * g, 0.3f)
        }
    }

    /** The bubble prize (or bubble-skin prize) where the box was. */
    fun renderBlended(cam: PerspectiveCamera, time: Float) {
        val r = reward ?: return
        if (phase != OPENED) return
        val rise = min(1f, t * 2.2f)
        val y = prizeY(time)
        val s = 1.5f * rise
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
