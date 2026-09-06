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
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import cube.run.core.Gdx3DGame
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.core.hsvInto
import cube.run.game.Lanes
import cube.run.data.Progress
import cube.run.data.Skins
import cube.run.data.Trails
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The player: lane, jump/roll physics, the tumbling pose, and its look (the
 * equipped [Skins.Skin]: colour behaviour + glow shell; the equipped
 * [Trails.Trail]: what it sheds — always a cube). Input verbs are
 * [moveToLane] / [jump] / [downAction]; [update] integrates one frame and
 * returns an EV_* event.
 */
class Player(private val game: Gdx3DGame, private val rnd: Random) {

    companion object {
        /** Jetpack cruising height (cube centre) — well above every obstacle. */
        const val FLY_Y = 5.2f
        /** Zero-G hover height (cube centre). */
        const val HOVER_Y = 1.4f
        const val EV_NONE = 0
        const val EV_LANDED = 1
        const val EV_SIDE_HIT = 2   // ran into the side of a platform
    }

    val ground = 0.45f      // resting cube center (cube = 0.9 across)
    /** Jetpack: hover at [flyY], ignore the ground. Set via [setFlying]. */
    var flying = false
        private set
    /** Where the jetpack holds you: [FLY_Y] while cruising, gliding down to the ground as it runs out. */
    var flyY = FLY_Y
    /** Zero-G: hover at [HOVER_Y], drift between lanes slowly, no jumping or rolling. */
    var hover = false

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
    private var trailK = 0          // emission counter (trail colour rules)
    private var stretch = 0f        // vertical stretch after a bounce launch

    /** Collision extents. Rolling tucks the head below the bars. */
    val cubeBottom: Float get() = py - 0.45f
    val headY: Float get() = py + 0.45f - duck * 0.72f

    private lateinit var unit: Model
    private lateinit var inst: ModelInstance
    lateinit var col: Color
        private set
    private lateinit var shellInst: ModelInstance
    private lateinit var shellCol: Color
    private lateinit var shellBlend: BlendingAttribute
    private var curSkinId = -1
    var skin: Skins.Skin = Skins.get(0)
        private set
    var trail: Trails.Trail = Trails.get(0)
        private set
    private val tmp = Vector3()
    private val tmpCol = Color()

    fun laneX(l: Int) = Lanes.x(l)

    /** The road changed shape (a portal): keep the cube on a lane that still exists. */
    fun remapLane(oldCount: Int, newCount: Int) {
        lane = (lane + (newCount - oldCount) / 2).coerceIn(0, newCount - 1)
    }

    fun init(baseHue: Float, time: Float) {
        unit = game.box(1f, 1f, 1f, Color.WHITE)
        applySkin(baseHue, time)
    }

    /** The skin to show: the wardrobe's try-on if one is set, else the equipped one. */
    private fun wantedSkin(): Int = if (Stage.previewSkin >= 0) Stage.previewSkin else Progress.skin
    private fun wantedTrail(): Int = if (Stage.previewTrail >= 0) Stage.previewTrail else Progress.trail

    /** (Re)build the body + glow shell for the wanted skin. Only runs when it changes. */
    private fun applySkin(baseHue: Float, time: Float) {
        curSkinId = wantedSkin()
        skin = Skins.get(curSkinId)
        inst = ModelInstance(unit)
        col = (inst.materials.first().get(ColorAttribute.Diffuse) as ColorAttribute).color
        hsvInto(col, skin.hueAt(time, baseHue), skin.sat, skin.valueAt(time))
        shellInst = ModelInstance(unit) // pulsing translucent "glow" shell
        shellBlend = BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.3f)
        shellInst.materials.first().set(ColorAttribute.createDiffuse(Color(col)), shellBlend)
        shellCol = (shellInst.materials.first().get(ColorAttribute.Diffuse) as ColorAttribute).color
    }

    /** Shard colour for dust/bursts: white sparkle for sparkle skins, else the body colour. */
    fun trailCol(): Color = if (skin.sparkle) Color.WHITE else col

    // ---------------------------------------------------------------- verbs

    /** Returns true if the lane changed (false = already there / out of range → bonk). */
    fun moveToLane(target: Int): Boolean {
        val t = target.coerceIn(0, Lanes.last)
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
        flyY = FLY_Y
        if (on) { air = false; vy = 0f } else { air = true; vy = 0f } // ends with a fall + normal landing
    }

    /** Put the cube on a platform of height [h] (bubble save after a side hit). */
    fun forceGround(h: Float) {
        py = ground + h; air = false; vy = 0f
    }

    fun jump() {
        if (air || flying || hover) return
        air = true; vy = 8.4f
        duckT = 0f // jumping cancels a roll
        SoundFx.play("whoosh", rate = 1.3f)
        Haptics.click()
        game.burst3d(tmp.set(px, 0.1f, 0.3f), trailCol(), n = 6, speed = 2.5f, size = 0.08f, life = 0.35f)
    }

    /** The run begins: a quick squash, then it springs off (a beat of anticipation). */
    fun squashForLaunch() { squash = 1.4f }

    private var idleT = 0f
    private var idleYaw = 0f
    /** Waiting at the line: the cube turns slowly on the spot and breathes; every few seconds a small hop. */
    fun idle(dt: Float) {
        idleT += dt
        idleYaw += 40f * dt
        if (idleT > 3.2f && !air) { idleT = 0f; air = true; vy = 3.6f; quietLanding = true }
    }
    private var quietLanding = false
    /** Eased 0..1: how much of the idle pose (the slow turn) is showing; fades out as the run begins. */
    private var idleMix = 0f

    /** A bounce pad: launched high, stretched tall, whatever you were doing. */
    fun launch(v: Float) {
        air = true; vy = v; duckT = 0f; slamming = false
        stretch = 1f
    }

    /** Context-sensitive DOWN: slam when airborne, roll under when grounded. */
    fun downAction() {
        if (flying || hover) return
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
     * emits the trail. Returns an EV_* event.
     */
    fun update(dt: Float, mv: Float, time: Float, baseHue: Float, trail: Boolean, groundH: Float, stream: Float = 0f): Int {
        if (wantedSkin() != curSkinId) applySkin(baseHue, time) // a wardrobe change shows up live
        this.trail = Trails.get(wantedTrail())
        var event = EV_NONE
        val gy = ground + groundH
        px += (laneX(lane) - px) * min(1f, dt * (if (hover) 4.5f else 13f)) // eased lane snap (a lazy drift in zero-g)
        nudge *= max(0f, 1f - 10f * dt)
        if (hover) {
            py += (HOVER_Y + 0.15f * sin(time * 2.2f) - py) * min(1f, dt * 3f)
            air = false; vy = 0f
        } else if (flying) {
            py += (flyY - py) * min(1f, dt * (if (flyY < FLY_Y) 7f else 4f)) // quick to climb, tight on the glide down
        } else if (air) {
            vy -= 26f * dt
            py += vy * dt
            if (py <= gy && vy <= 0f) {
                py = gy; air = false; vy = 0f
                if (!quietLanding) { SoundFx.play("pop", rate = 0.95f + rnd.nextFloat() * 0.12f); Haptics.click() }
                quietLanding = false
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
        stretch = max(0f, stretch - dt * 2.2f)
        // roll/duck window decays; eased `duck` drives the rolling pose + low collision profile
        if (duckT > 0f) duckT = max(0f, duckT - dt)
        val duckTarget = if (duckT > 0f && !air) 1f else 0f
        duck += (duckTarget - duck) * min(1f, dt * 18f)
        val idle = mv == 0f && !flying && !hover
        idleMix += ((if (idle) 1f else 0f) - idleMix) * min(1f, dt * 4f)
        roll += mv * 42f + (if (air && !idle) 160f * dt else 0f) + duck * 260f * dt // tumble; flip in air, fast roll while ducking
        if (roll > 360f) roll -= 360f
        // a rolling cube rides up over its corners: lift it so it never sinks into the floor (no jitter, a real roll)
        val ra = Math.toRadians((roll % 90f).toDouble())
        val lift = if (air || flying) 0f else (0.45f * (kotlin.math.abs(kotlin.math.cos(ra)) + kotlin.math.abs(kotlin.math.sin(ra))).toFloat() - 0.45f) * (1f - duck)
        val tilt = ((laneX(lane) - px) * -22f).coerceIn(-32f, 32f)
        val sq = squash * 0.3f
        val st = stretch * stretch * 0.35f
        val duY = duck * 0.20f - lift // hug the ground while rolling
        // skin colours are pure functions of time — sampled every frame, no allocation
        hsvInto(col, skin.hueAt(time, baseHue), skin.sat, skin.valueAt(time))
        hsvInto(shellCol, skin.hueAt(time, baseHue), skin.sat * 0.9f, 1f)
        val breathe = 1f + 0.03f * idleMix * sin(time * 2.4f)
        inst.transform.setToTranslation(px + nudge, py - squash * 0.08f - duY, 0f)
            .rotate(Vector3.Y, idleYaw * idleMix)
            .rotate(Vector3.Z, tilt)
            .rotate(Vector3.X, -roll)
            .scale(breathe, 1f / breathe, breathe)
            .scale(0.9f * (1f + sq + duck * 0.35f - st * 0.5f), 0.9f * (1f - sq + st) * (1f - duck * 0.5f), 0.9f * (1f + sq + duck * 0.1f - st * 0.5f))
        val pulse = glowScale(time)
        shellBlend.opacity = ((0.22f + 0.08f * sin(time * 6f)) * skin.glow).coerceAtMost(0.75f)
        shellInst.transform.setToTranslation(px + nudge, py - duY, 0f)
            .rotate(Vector3.Y, idleYaw * idleMix).rotate(Vector3.Z, tilt).rotate(Vector3.X, -roll)
            .scale(pulse, pulse, pulse)

        if (trail) emitTrail(dt, time, px, py, 0.5f, if (flying) 2.5f else 1f, stream = stream)
        return event
    }

    /** Shed the equipped trail behind ([x],[y],[z]); [boost] multiplies the rate (jetpack). */
    fun emitTrail(dt: Float, time: Float, x: Float, y: Float, z: Float, boost: Float = 1f, scale: Float = 1f, stream: Float = 0f) {
        val t = this.trail
        trailT += dt
        val every = 1f / (t.rate * skin.trail * boost)
        if (trailT < every) return
        trailT = 0f
        trailK++
        val c = if (t.mode == Trails.BODY) trailCol() else hsvInto(tmpCol, t.hueAt(time, trailK), t.sat, t.value)
        // shards stream back past the camera ([stream] ≈ half the run speed), so the trail reads as motion
        game.burst3d(tmp.set(x, y, z), c, n = t.count, speed = t.speed * scale, size = t.size * scale, life = t.life * (1f + (scale - 1f) * 0.5f), gravity = t.gravity, biasZ = stream)
    }

    /**
     * Pose for the wardrobe / results stage at ([x],[y]): turning slowly,
     * bobbing. Colours keep animating so live skins show what they do.
     */
    fun showcase(time: Float, baseHue: Float, x: Float = 0f, y: Float = 0.75f, spin: Float = 45f, extraYaw: Float = 0f, scale: Float = 1f) {
        if (wantedSkin() != curSkinId) applySkin(baseHue, time)
        trail = Trails.get(wantedTrail())
        hsvInto(col, skin.hueAt(time, baseHue), skin.sat, skin.valueAt(time))
        hsvInto(shellCol, skin.hueAt(time, baseHue), skin.sat * 0.9f, 1f)
        val yy = y + 0.06f * sin(time * 2.2f)
        val yaw = time * spin + extraYaw
        val sc = 0.9f * scale
        inst.transform.setToTranslation(x, yy, 0f).rotate(Vector3.Y, yaw).rotate(Vector3.X, 12f).scale(sc, sc, sc)
        val pulse = glowScale(time) * scale
        shellBlend.opacity = ((0.22f + 0.08f * sin(time * 6f)) * skin.glow).coerceAtMost(0.75f)
        shellInst.transform.setToTranslation(x, yy, 0f).rotate(Vector3.Y, yaw).rotate(Vector3.X, 12f).scale(pulse, pulse, pulse)
        px = x; py = yy
    }

    private val liftM = com.badlogic.gdx.math.Matrix4()

    private val menuBody = Matrix4()
    private val menuShell = Matrix4()
    private var menuX = 0f
    private var menuY = 0f
    private var menuYaw = 0f
    private var menuSpin = 0f
    private val posePosition = Vector3()
    private val poseTarget = Vector3()
    private val poseScale = Vector3()
    private val poseTargetScale = Vector3()
    private val poseRotation = Quaternion()
    private val poseTargetRotation = Quaternion()
    private val spinRotation = Quaternion()

    /** Preserve the exact idle pose (including a hop) for the shop's reversible camera move. */
    fun saveMenuPose() {
        menuBody.set(inst.transform); menuShell.set(shellInst.transform)
        menuX = px; menuY = py
        menuYaw = idleYaw * idleMix
        menuSpin = 0f
    }

    private fun glowScale(time: Float) = 0.9f * (1.18f + 0.06f * sin(time * 8f))

    fun restoreMenuPose(time: Float) {
        blendFromMenu(0f, menuSpin, time)
        px = menuX; py = menuY
        // Carry the shop's rotation forward into idle instead of snapping to an old saved heading.
        idleYaw = menuYaw + menuSpin
        idleMix = 1f
    }

    fun blendFromMenu(amount: Float, spinDegrees: Float, time: Float) {
        menuSpin = spinDegrees
        blendPose(menuBody, inst.transform, amount)
        // Keep the glow on its live clock on BOTH ends of the interpolation. The captured pose
        // supplies position/rotation, not an old pulse size that would snap when idle resumes.
        blendPose(menuShell, shellInst.transform, amount, glowScale(time))
        inst.transform.getTranslation(posePosition)
        px = posePosition.x; py = posePosition.y
    }

    private fun blendPose(from: Matrix4, target: Matrix4, amount: Float, liveScale: Float? = null) {
        from.getTranslation(posePosition); target.getTranslation(poseTarget)
        from.getScale(poseScale); target.getScale(poseTargetScale)
        if (liveScale != null) poseScale.set(liveScale, liveScale, liveScale)
        from.getRotation(poseRotation, true); target.getRotation(poseTargetRotation, true)
        // The target has only shop tilt. Match its yaw to the original pose before blending tilt;
        // apply the unwrapped rightward spin afterward, so shortest-arc slerp cannot reverse it.
        poseTargetRotation.mulLeft(spinRotation.set(Vector3.Y, menuYaw))
        poseRotation.slerp(poseTargetRotation, amount).mulLeft(spinRotation.set(Vector3.Y, menuSpin)).nor()
        target.set(posePosition.lerp(poseTarget, amount), poseRotation, poseScale.lerp(poseTargetScale, amount))
    }

    /** Draw the cube; [ground] lifts everything by the rolling terrain under it. */
    fun render(batch: ModelBatch, env: Environment, ground: Float = 0f) {
        if (ground != 0f) {
            liftM.setToTranslation(0f, ground, 0f)
            inst.transform.mulLeft(liftM); shellInst.transform.mulLeft(liftM)
        }
        batch.render(inst, env)
        batch.render(shellInst, env)
        if (ground != 0f) {
            liftM.setToTranslation(0f, -ground, 0f)
            inst.transform.mulLeft(liftM); shellInst.transform.mulLeft(liftM)
        }
    }
}
