package cube.run.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import cube.run.core.Gdx3DGame
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.core.hsvInto
import cube.run.data.BubbleSkins
import cube.run.data.Progress
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The bubble shield: timed, absorbs exactly one crash — the game then
 * smashes the row and pops it. A real soap bubble (fresnel rim, iridescent
 * film, highlight) in the equipped [BubbleSkins.BubbleSkin]. Going up is an
 * event: the world hitches for a beat, the bubble inflates with a wobble,
 * a shockwave ring blows outward, shards fly. Popping is the same in reverse.
 */
class Bubble(private val game: Gdx3DGame) {

    /** The countdown (drawn by [PowerUps.drawBars]). */
    val timer = PowerUps.Timer(0.35f, 0.88f, 1f)
    /** Full duration (upgrade-dependent). */
    var duration = 10f
    val active: Boolean get() = timer.active
    val timeLeft: Float get() = timer.left
    var cooldownLeft = 0f
        private set
    val ready: Boolean get() = !active && cooldownLeft <= 0f

    fun reset() { timer.stop(); cooldownLeft = 0f; shock = 0f }

    var skin: BubbleSkins.BubbleSkin = BubbleSkins.get(0)
        private set
    private var age = 0f          // seconds since activation (inflation)
    private var shock = 0f        // 1 → 0: the expanding ring after activation / pop
    private var shockHue = 190f
    private var x = 0f
    private var y = 0f
    private var yaw = 0f
    private val tmp = Vector3()
    private val tmpCol = Color()

    private fun wanted(): Int = if (Stage.previewBubble >= 0) Stage.previewBubble else Progress.bubbleSkin

    fun init() { skin = BubbleSkins.get(wanted()) }

    /** Burst colour: the skin's first hue (white for sparkle skins). */
    private fun burstCol(): Color = if (skin.sparkle) Color.WHITE else hsvInto(tmpCol, skin.hue, max(0.4f, skin.sat), 1f)

    fun activate(px: Float, py: Float, quiet: Boolean = false) {
        skin = BubbleSkins.get(wanted())
        timer.start(duration)
        age = 0f
        shock = 1f; shockHue = skin.hue
        x = px; y = py
        if (quiet) { SoundFx.play("rise", rate = 1.3f, vol = 0.5f); return } // the Safe start perk: no hitch, no flash
        SoundFx.play("rise", rate = 1.3f)
        SoundFx.play("perfect", rate = 0.9f)
        Haptics.success()
        game.slowMo(0.25f, 0.32f)
        game.flash(hsvInto(tmpCol, skin.hue, 0.35f, 1f), 0.3f)
        game.burst3d(tmp.set(px, py, 0f), burstCol(), n = 26, speed = 6f, size = 0.14f, life = 0.7f)
        game.burst3d(tmp, Color.WHITE, n = 10, speed = 9f, size = 0.09f, life = 0.4f)
    }

    fun pop(px: Float, py: Float) {
        timer.stop()
        cooldownLeft = 5f
        shock = 1f; shockHue = skin.hue
        x = px; y = py
        SoundFx.play("pop", rate = 0.55f)
        SoundFx.play("boom", rate = 1.8f, vol = 0.35f)
        Haptics.click()
        game.slowMo(0.2f, 0.22f)
        game.burst3d(tmp.set(px, py, 0f), burstCol(), n = 40, speed = 8f, size = 0.12f, life = 0.8f)
        game.burst3d(tmp, Color.WHITE, n = 12, speed = 11f, size = 0.08f, life = 0.45f)
    }

    /** Tick the timer; keeps the sphere around the player. Returns true on the frame it runs out. */
    fun update(dt: Float, time: Float, px: Float, py: Float): Boolean {
        cooldownLeft = max(0f, cooldownLeft - dt)
        shock = max(0f, shock - dt * 2.2f)
        x = px; y = py + 0.1f
        yaw = time * 40f
        if (!active) return false
        age += dt
        if (timer.tick(dt)) { // ran out quietly
            cooldownLeft = 5f
            SoundFx.play("pop", rate = 0.7f, vol = 0.6f)
            game.burst3d(tmp.set(px, py, 0f), burstCol(), n = 16, speed = 4f, size = 0.1f, life = 0.6f)
            return true
        }
        return false
    }

    /** Inflation scale with a wobble that settles: 0 → 1 with overshoot. */
    private fun inflate(): Float {
        val t = min(1f, age * 3.2f)
        val over = 1f + 0.28f * (1f - t) * sin(t * 9.5f)
        return t * over
    }

    /** Draw the bubble (call from the blended pass). */
    fun render(cam: PerspectiveCamera, time: Float) {
        if (active) {
            val ending = timeLeft < 3f
            val flick = if (ending) .72f + .28f * sin(time * 6f) else 1f
            val s = 2.25f * inflate()
            val wob = 0.06f * sin(time * 9f) * s
            draw(cam, time, x, y, s + wob, s - wob, s + wob * 0.5f, flick)
        }
        if (shock > 0f) { // a ring blown outward: a big, thin, fading bubble
            val k = 1f - shock
            val s = 2.2f + k * 5f
            game.bubbles.draw(cam, x, y, 0f, s, s * 0.7f, s, yaw, time, shockHue, shockHue + 40f, skin.sat, 5f, 0f, shock * shock * 0.9f, BubbleSkins.SOLID)
        }
    }

    private fun draw(cam: PerspectiveCamera, time: Float, cx: Float, cy: Float, sx: Float, sy: Float, sz: Float, alpha: Float) {
        game.bubbles.draw(cam, cx, cy, 0f, sx, sy, sz, yaw, time, skin.hue, skin.hue2, skin.sat, skin.rim, skin.fill, alpha, skin.style)
    }

    private var previewId = -1
    private var previewTime = 0f
    private var previewBlend = 1f
    private var previousStyle = 0
    private val shown = FloatArray(5)
    private val from = FloatArray(5)

    private fun hueDelta(to: Float, from: Float) = ((to - from) % 360f + 540f) % 360f - 180f

    /** The wardrobe: a full bubble around ([px],[py]) in the previewed skin, no timer. */
    fun showcase(cam: PerspectiveCamera, time: Float, px: Float, py: Float, inflate: Float = 1f) {
        val target = BubbleSkins.get(wanted())
        if (previewId != target.id) {
            previousStyle = skin.style
            from.indices.forEach { from[it] = shown[it] }
            previewBlend = if (previewId < 0) 1f else 0f
            previewId = target.id
        }
        val dt = (time - previewTime).coerceIn(0f, .05f)
        previewTime = time
        previewBlend = min(1f, previewBlend + dt / .38f)
        val k = previewBlend * previewBlend * (3f - 2f * previewBlend)
        skin = target
        shown[0] = (from[0] + hueDelta(target.hue, from[0]) * k + 360f) % 360f
        val second = from[1] + hueDelta(target.hue2, from[1]) * k
        shown[1] = shown[0] + hueDelta(second, shown[0])
        shown[2] = from[2] + (target.sat - from[2]) * k
        shown[3] = from[3] + (target.rim - from[3]) * k
        shown[4] = from[4] + (target.fill - from[4]) * k
        yaw = time * 16f
        val s = 2.25f * inflate
        val wob = .018f * sin(time * 2.6f) * s
        game.bubbles.draw(cam, px, py + .1f, 0f, s + wob, s - wob, s, yaw, time,
            shown[0], shown[1], shown[2], shown[3], shown[4], min(1f, inflate * 1.5f), previousStyle, target.style, k)
    }
}
