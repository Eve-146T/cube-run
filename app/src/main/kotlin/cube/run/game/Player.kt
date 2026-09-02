package cube.run.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.math.Vector3
import cube.run.core.Gdx3DGame
import cube.run.core.Haptics
import cube.run.core.Progress
import cube.run.core.Skins
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.core.hsvInto
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The player: lane, jump/roll physics, the tumbling pose, and its look (the
 * equipped [Skins.Skin]: shape + colour behaviour + glow shell + trail).
 * Input verbs are [moveToLane] / [jump] / [downAction]; [update] integrates
 * one frame and returns true on a landing.
 */
class Player(private val game: Gdx3DGame, private val laneW: Float, private val rnd: Random) {

    companion object {
        /** Jetpack cruising height (cube centre) — above every obstacle. */
        const val FLY_Y = 3.3f
        const val EV_NONE = 0
        const val EV_LANDED = 1
        const val EV_SIDE_HIT = 2   // ran into the side of a platform
    }

    val ground = 0.45f      // resting cube center (cube = 0.9 across)
    /** Jetpack: hover at [FLY_Y], ignore the ground. Set via [setFlying]. */
    var flying = false
        private set

    var lane = 1
        private set
    var px = 0f
        private set
    var py = ground
        private set
    var air = false
        private set
    /** Eased 0..1 roll amount (visual + collision). */
    var duck = 0f
        private set
    /** Edge-bonk offset (visual only). */
    var nudge = 0f
    private var vy = 0f
    private var roll = 0f           // forward tumble angle
    private var squash = 0f         // landing squash timer
    private var duckT = 0f          // remaining roll/duck window (seconds)
    private var slamming = false    // a mid-air slam is in progress (auto-crouches on landing)
    private var trailT = 0f

    /** Collision extents. Rolling tucks the head below the bars. */
    val cubeBottom: Float get() = py - 0.45f
    val headY: Float get() = py + 0.45f - duck * 0.72f

    private lateinit var unit: Model
    private lateinit var orb: Model
    private lateinit var pyramid: Model
    private lateinit var inst: ModelInstance
    lateinit var col: Color
        private set
    private lateinit var shellInst: ModelInstance
    private lateinit var shellCol: Color
    private lateinit var shellBlend: BlendingAttribute
    private lateinit var shadowInst: ModelInstance
    private lateinit var shadowBlend: BlendingAttribute
    private var curSkinId = -1
    var skin: Skins.Skin = Skins.get(0)
        private set
    private val tmp = Vector3()

    fun laneX(l: Int) = (l - 1) * laneW

    fun init(baseHue: Float, time: Float) {
        unit = game.box(1f, 1f, 1f, Color.WHITE)
        orb = game.sphere(1f, Color.WHITE, div = 18)
        pyramid = game.cone(1.25f, 1f, Color.WHITE, div = 4)
        shadowInst = ModelInstance(unit)
        shadowBlend = BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.3f)
        shadowInst.materials.first().set(ColorAttribute.createDiffuse(Color.BLACK), shadowBlend)
        applySkin(baseHue, time)
    }

    /** The skin to show: the wardrobe's try-on if one is set, else the equipped one. */
    private fun wantedSkin(): Int = if (Stage.previewSkin >= 0) Stage.previewSkin else Progress.skin

    /** (Re)build the body + glow shell for the wanted skin. Only runs when it changes. */
    private fun applySkin(baseHue: Float, time: Float) {
        curSkinId = wantedSkin()
        skin = Skins.get(curSkinId)
        val model = when (skin.shape) { Skins.ORB -> orb; Skins.PYRAMID -> pyramid; else -> unit }
        inst = ModelInstance(model)
        col = (inst.materials.first().get(ColorAttribute.Diffuse) as ColorAttribute).color
        hsvInto(col, skin.hueAt(time, baseHue), skin.sat, skin.valueAt(time))
        shellInst = ModelInstance(model) // pulsing translucent "glow" shell
        shellBlend = BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.3f)
        shellInst.materials.first().set(ColorAttribute.createDiffuse(Color(col)), shellBlend)
        shellCol = (shellInst.materials.first().get(ColorAttribute.Diffuse) as ColorAttribute).color
    }

    /** Shard colour for dust/trail: white sparkle for sparkle skins, else the body colour. */
    fun trailCol(): Color = if (skin.sparkle) Color.WHITE else col

    // ---------------------------------------------------------------- verbs

    /** Returns true if the lane changed (false = already there / out of range → bonk). */
    fun moveToLane(target: Int): Boolean {
        val t = target.coerceIn(0, 2)
        if (t == lane) return false
        lane = t
        SoundFx.play("whoosh", rate = 0.95f + rnd.nextFloat() * 0.15f)
        SoundFx.play("tick", rate = 1.4f, vol = 0.5f)
        Haptics.tick()
        return true
    }

    fun bonk(dir: Int) {
        nudge = dir * 0.4f
        SoundFx.play("tap", rate = 0.7f)
        Haptics.tick()
    }

    fun setFlying(on: Boolean) {
        if (flying == on) return
        flying = on
        duckT = 0f
        if (on) { air = false; vy = 0f } else { air = true; vy = 0f } // ends with a fall + normal landing
    }

    /** Put the cube on a platform of height [h] (bubble save after a side hit). */
    fun forceGround(h: Float) {
        py = ground + h; air = false; vy = 0f
    }

    fun jump() {
        if (air || flying) return
        air = true; vy = 8.4f
        duckT = 0f // jumping cancels a roll
        SoundFx.play("whoosh", rate = 1.3f)
        Haptics.click()
        game.burst3d(tmp.set(px, 0.1f, 0.3f), trailCol(), n = 6, speed = 2.5f, size = 0.08f, life = 0.35f)
    }

    /** Context-sensitive DOWN: slam when airborne, roll under when grounded. */
    fun downAction() {
        if (flying) return
        if (air) {
            if (vy > -12f) { // slam back down fast
                vy = -19f
                slamming = true // auto-crouch the instant we land
                SoundFx.play("slide", rate = 1.3f)
                Haptics.tick()
            }
        } else {
            duckT = 0.5f // duck/roll window
            SoundFx.play("slide", rate = 1.05f)
            Haptics.tick()
            game.burst3d(tmp.set(px, 0.06f, 0.4f), trailCol(), n = 5, speed = 2.8f, size = 0.08f, life = 0.3f)
        }
    }

    // ---------------------------------------------------------------- frame

    /**
     * Integrate one frame. [groundH] is the walkable height under the cube (0 on
     * the floor, a platform's top on its roof, rising along a ramp); [trail]
     * emits the glow trail. Returns an EV_* event.
     */
    fun update(dt: Float, mv: Float, time: Float, baseHue: Float, trail: Boolean, groundH: Float): Int {
        if (wantedSkin() != curSkinId) applySkin(baseHue, time) // a wardrobe change shows up live
        var event = EV_NONE
        val gy = ground + groundH
        px += (laneX(lane) - px) * min(1f, dt * 13f) // eased lane snap
        nudge *= max(0f, 1f - 10f * dt)
        if (flying) {
            py += (FLY_Y - py) * min(1f, dt * 4f)
        } else if (air) {
            vy -= 26f * dt
            py += vy * dt
            if (py <= gy && vy <= 0f) {
                py = gy; air = false; vy = 0f
                SoundFx.play("pop", rate = 0.95f + rnd.nextFloat() * 0.12f)
                Haptics.click()
                game.burst3d(tmp.set(px, gy - 0.39f, 0.4f), trailCol(), n = 8, speed = 3.2f, size = 0.09f, life = 0.4f)
                squash = 1f
                if (slamming) { slamming = false; duckT = 0.5f } // slam → auto-crouch on landing
                event = EV_LANDED
            }
        } else if (gy > py + 0.001f) {
            // the ground rose: a ramp lifts you a little each frame; a whole platform side is a wall
            if (gy - py > 0.45f) event = EV_SIDE_HIT else py = gy
        } else if (gy < py - 0.02f) {
            air = true; vy = 0f // walked off an edge
        }
        squash = max(0f, squash - dt * 5f)
        // roll/duck window decays; eased `duck` drives the rolling pose + low collision profile
        if (duckT > 0f) duckT = max(0f, duckT - dt)
        val duckTarget = if (duckT > 0f && !air) 1f else 0f
        duck += (duckTarget - duck) * min(1f, dt * 18f)
        roll += mv * 90f + (if (air) 160f * dt else 0f) + duck * 260f * dt // tumble; flip in air, fast roll while ducking
        if (roll > 360f) roll -= 360f
        val tilt = ((laneX(lane) - px) * -22f).coerceIn(-32f, 32f)
        val sq = squash * 0.3f
        val duY = duck * 0.20f // hug the ground while rolling
        // skin colours are pure functions of time — sampled every frame, no allocation
        hsvInto(col, skin.hueAt(time, baseHue), skin.sat, skin.valueAt(time))
        hsvInto(shellCol, skin.hueAt(time, baseHue), skin.sat * 0.9f, 1f)
        inst.transform.setToTranslation(px + nudge, py - squash * 0.08f - duY, 0f)
            .rotate(Vector3.Z, tilt)
            .rotate(Vector3.X, -roll)
            .scale(0.9f * (1f + sq + duck * 0.35f), 0.9f * (1f - sq) * (1f - duck * 0.5f), 0.9f * (1f + sq + duck * 0.1f))
        val pulse = 0.9f * (1.18f + 0.06f * sin(time * 8f))
        shellBlend.opacity = ((0.22f + 0.08f * sin(time * 6f)) * skin.glow).coerceAtMost(0.75f)
        shellInst.transform.setToTranslation(px + nudge, py - duY, 0f)
            .rotate(Vector3.Z, tilt).rotate(Vector3.X, -roll)
            .scale(pulse, pulse, pulse)
        shadowBlend.opacity = (0.36f * (1f - (py - gy) / 1.6f)).coerceIn(0.06f, 0.36f)
        shadowInst.transform.setToTranslation(px + nudge, gy - ground + 0.04f, 0f).scale(1.0f, 0.02f, 1.0f)

        if (trail) { // glow trail
            trailT += dt
            if (trailT > 0.08f / skin.trail) {
                trailT = 0f
                game.burst3d(tmp.set(px, py, 0.55f), trailCol(), n = if (flying) 3 else 1, speed = if (flying) 4f else 1.4f, size = 0.08f, life = 0.35f)
            }
        }
        return event
    }

    /** Pose for the wardrobe stage: at the origin on a pedestal, turning slowly, bobbing. */
    fun showcase(time: Float, baseHue: Float) {
        if (wantedSkin() != curSkinId) applySkin(baseHue, time)
        hsvInto(col, skin.hueAt(time, baseHue), skin.sat, skin.valueAt(time))
        hsvInto(shellCol, skin.hueAt(time, baseHue), skin.sat * 0.9f, 1f)
        val y = 0.75f + 0.06f * sin(time * 2.2f)
        val yaw = time * 45f
        inst.transform.setToTranslation(0f, y, 0f).rotate(Vector3.Y, yaw).rotate(Vector3.X, 12f).scale(0.9f, 0.9f, 0.9f)
        val pulse = 0.9f * (1.18f + 0.06f * sin(time * 8f))
        shellBlend.opacity = ((0.22f + 0.08f * sin(time * 6f)) * skin.glow).coerceAtMost(0.75f)
        shellInst.transform.setToTranslation(0f, y, 0f).rotate(Vector3.Y, yaw).rotate(Vector3.X, 12f).scale(pulse, pulse, pulse)
        shadowBlend.opacity = 0.3f
        shadowInst.transform.setToTranslation(0f, 0.04f, 0f).scale(1.0f, 0.02f, 1.0f)
    }

    fun render(batch: ModelBatch, env: Environment) {
        batch.render(shadowInst, env)
        batch.render(inst, env)
        batch.render(shellInst, env)
    }
}
