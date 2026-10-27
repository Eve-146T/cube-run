package cube.run.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute
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

private val ZEN_INK = Color(.05f, .05f, .06f, 1f)
/** What a cube fades to as it drops into a void. */
private val DARK = Color(.02f, .015f, .035f, 1f)

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
        /** Outer Space: gravity pulls this fraction as hard, so a jump hangs half as long again. */
        const val LOW_G = 0.52f
        /** Outer Space: take-offs and launches leave at this fraction of their speed (a jump peaks ~20% higher). */
        const val LOW_G_LAUNCH = 0.786f
        /** Outer Space: lane changes glide a little (13 on the ground). */
        const val LOW_G_LANE_RATE = 10f
        /** A falling cube's forward tumble settles to this (degrees per second). */
        const val FALL_TUMBLE = 260f
        /** Half the cube's size, for a falling cube against a hole's ends. */
        private const val HALF = 0.45f
        const val EV_NONE = 0
        const val EV_LANDED = 1
        const val EV_SIDE_HIT = 2   // ran into the side of a platform
        const val EV_GROUND_POUND = 3 // completed slam landing
        /** The Smol cube's size next to the others. */
        const val SMOL_SIZE = 0.62f
        /** A drop this small (a step of a ramp) is stepped down, not fallen: the cube stays on the ground. */
        const val STEP_DOWN = 0.5f
        /** How fast the cube steps down it (units per second). */
        const val STEP_RATE = 10f
        /** A cube that walked off an edge can still take off until it lands (well past any fall on the road). */
        const val LEDGE_GRACE = 0.6f
        const val EV_STRUCK = 4     // a falling cube struck a hole's far (or near) wall; see [struckSpeed]
    }

    val ground = 0.45f      // resting cube center (cube = 0.9 across)
    /** Jetpack: hover at [flyY], ignore the ground. Set via [setFlying]. */
    var flying = false
        private set
    /** Where the jetpack holds you: [FLY_Y] while cruising, then down its descent onto the landing strip. */
    var flyY = FLY_Y
    /** The flight is coming down: [flyY] is followed tightly, and the road can reach the cube again. */
    var descending = false
    /** Zero-G: hover at [HOVER_Y], drift between lanes slowly, no jumping or rolling. */
    var hover = false
    /** Outer Space: weaker gravity, slower take-offs, softer lane changes. */
    var lowGravity = false
    private val launchScale: Float get() = if (lowGravity) LOW_G_LAUNCH else 1f
    /** Equipped Eclipse ability, fixed for this run; wardrobe previews cannot grant it. */
    var zappyEnabled = false
        set(value) {
            field = value
            if (!value) zappyFx?.clear()
            else if (::unit.isInitialized && zappyFx == null) zappyFx = ZappyFx(unit)
        }
    /** Smol is small enough to pass under bars standing: set for the run, so a try-on in the wardrobe grants nothing. */
    var smol = false
    /** Cloud keeps the usual jump height, with a softer ascent and longer hang. */
    var floaty = false
    /** Set by the run: Mint abilities require an active shield. */
    var doubleJumpEnabled = false
    var tripleJumpEnabled = false
    var gigajumpEnabled = false
    private var airJumpAvailable = false
    private var airJumpsUsed = 0

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
    val verticalVelocity: Float get() = vy
    private var roll = 0f           // forward tumble angle
    private var squash = 0f         // landing squash timer
    private var duckT = 0f          // remaining roll/duck window (seconds)
    private var slamming = false    // a mid-air slam is in progress (auto-crouches on landing)
    private var coyoteLeft = 0f     // only a walked-off edge grants another takeoff
    private var jumpBuffer = 0f     // remember a slightly early landing swipe
    private var trailT = 0f
    private var trailK = 0          // emission counter (trail colour rules)
    private var stretch = 0f        // vertical stretch after a bounce launch

    /** Lost the road: no ground, sideways momentum, the tumble carried on (see [fall]). */
    var falling = false
        private set
    /** Outer Space: swiped off the road's edge. The cube glides on past the kerb and drops. */
    var steppedOff = false
        private set
    private var vx = 0f             // sideways speed while falling
    private var rollRate = 0f       // forward tumble (degrees/s) carried into the fall
    private var tiltAtFall = 0f     // the lean the fall started from
    private var fallTilt = 0f       // sideways tumble gathered since
    private var fallTiltRate = 0f
    private var fallGravity = 26f
    private var fallDrag = 0f       // air drag on the fall's vertical speed (a slow drift in space)
    private var fallMinX = -1e9f    // a pit's side walls (the cube stays inside the hole it fell into)
    private var fallMaxX = 1e9f
    /** Where the falling cube is along the road, ahead of (−) or behind (+) the camera's usual mark. */
    var pz = 0f
        private set
    /** A falling cube's speed over the world, forward: the run's speed it carries off the road. */
    var forward = 0f
        private set
    // The hole's ends this frame (see [fallWalls]): the cube can strike them while it is level with the road.
    private var wallNear = 1e9f
    private var wallFar = -1e9f
    private var wallTop = 0f
    private var wallBottom = 0f
    /** How hard the last [EV_STRUCK] hit was (forward speed, units/s). */
    var struckSpeed = 0f
        private set
    /** 0..1: how far the cube has sunk into darkness (a tar pit's depths). */
    var dim = 0f

    /** Collision extents. Rolling tucks the head below the bars. */
    val cubeBottom: Float get() = py - 0.45f
    val headY: Float get() = py + 0.45f - (if (smol) 0.72f else duck * 0.72f)

    private lateinit var unit: Model
    private lateinit var inst: ModelInstance
    lateinit var col: Color
        private set
    private lateinit var shellInst: ModelInstance
    private lateinit var shellCol: Color
    /** Hairline edges: Black void's faint violet seams, Zen's ink outline. */
    private var edges = emptyArray<ModelInstance>()
    private var visualTime = 0f
    private var zappyFx: ZappyFx? = null
    private lateinit var shellBlend: BlendingAttribute
    private var curSkinId = -1
    /** The run's phase charge is gone: a see-through ghost settles into a solid, bright white cube. */
    var phaseSpent = false
    private var spentMix = 0f
    var skin: Skins.Skin = Skins.get(0)
        private set
    var trail: Trails.Trail = Trails.get(0)
        private set
    private val tmp = Vector3()
    private val tmpCol = Color()

    internal fun pilotBody(flightLeft: Float) = cube.run.bot.Body(lane, px, py, vy, air, duck,
        duckT, slamming, flying, flyY, hover, flightLeft = flightLeft, lowG = lowGravity,
        coyoteLeft = coyoteLeft, jumpBuffer = jumpBuffer, airJump = lowGravity && canAirJump)

    fun laneX(l: Int) = Lanes.x(l)

    /** How big the body is drawn: Smol is a small cube; the rest are full size. */
    private fun bodySize() = if (Skins.Ability.SMOL in skin.abilities) SMOL_SIZE else 1f

    /** The road changed shape (a portal): keep the cube on a lane that still exists. */
    fun remapLane(oldCount: Int, newCount: Int) {
        lane = (lane + (newCount - oldCount) / 2).coerceIn(0, newCount - 1)
        if (zappyEnabled) px = laneX(lane)
        game.session.laneChanged(lane, newCount)
    }

    fun init(baseHue: Float, time: Float) {
        unit = game.box(1f, 1f, 1f, Color.WHITE)
        applySkin(baseHue, time)
    }

    /** Reset simulation and pose while retaining the cube's mesh and material resources. */
    fun resetToMenu(baseHue: Float, time: Float) {
        flying = false; flyY = FLY_Y; descending = false; hover = false; lowGravity = false
        zappyEnabled = false; floaty = false; smol = false; doubleJumpEnabled = false; tripleJumpEnabled = false
        gigajumpEnabled = false
        airJumpAvailable = false; airJumpsUsed = 0
        lane = 1; px = 0f; py = ground; air = false
        duck = 0f; nudge = 0f; vy = 0f; roll = 0f; squash = 0f; duckT = 0f
        slamming = false; coyoteLeft = 0f; jumpBuffer = 0f; trailT = 0f; trailK = 0; stretch = 0f
        falling = false; steppedOff = false; vx = 0f; rollRate = 0f; fallTilt = 0f; fallTiltRate = 0f; dim = 0f
        fallMinX = -1e9f; fallMaxX = 1e9f; pz = 0f; forward = 0f; noWalls()
        idleT = 0f; idleYaw = 0f; idleMix = 0f; quietLanding = false
        menuX = 0f; menuY = ground; menuYaw = 0f; menuSpin = 0f
        voidEmissiveSaved = false
        phaseSpent = false; spentMix = 0f
        applySkin(baseHue, time)
        update(0f, 0f, time, baseHue, trail = false, groundH = 0f)
    }

    /** The skin to show: the wardrobe's try-on if one is set, else the equipped one. */
    private fun wantedSkin(): Int = if (Stage.previewSkin >= 0) Stage.previewSkin else Progress.skin
    private fun wantedTrail(): Int = if (Stage.previewTrail >= 0) Stage.previewTrail else Progress.trail

    /** (Re)build the body + glow shell for the wanted skin. Only runs when it changes. */
    private fun applySkin(baseHue: Float, time: Float) {
        curSkinId = wantedSkin()
        skin = Skins.get(curSkinId)
        zappyFx?.clear()
        inst = ModelInstance(unit)
        if (skin.opacity < 1f) inst.materials.first().set(
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, skin.opacity),
            DepthTestAttribute(GL20.GL_LEQUAL, 0f, 1f, false),
        )
        if (skin.id == 13) inst.materials.first().set(ColorAttribute.createEmissive(.24f, .25f, .26f, 1f))
        if (hasEdges() && edges.isEmpty()) edges = Array(12) { ModelInstance(unit) }
        if (hasEdges()) for (e in edges) {
            if (skin.id == Skins.VOID_ID) e.materials.first().set(ColorAttribute.createDiffuse(Color(.35f, .3f, .5f, 1f)),
                ColorAttribute.createEmissive(Color(.27f, .23f, .38f, 1f)))
            else e.materials.first().set(ColorAttribute.createDiffuse(ZEN_INK), ColorAttribute.createEmissive(Color.BLACK))
        }
        col = (inst.materials.first().get(ColorAttribute.Diffuse) as ColorAttribute).color
        hsvInto(col, skin.hueAt(time, baseHue), skin.sat, skin.valueAt(time))
        shellInst = ModelInstance(unit) // pulsing translucent "glow" shell
        shellBlend = BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.3f)
        shellInst.materials.first().set(ColorAttribute.createDiffuse(Color(col)), shellBlend)
        if (skin.opacity < 1f) shellInst.materials.first().set(DepthTestAttribute(GL20.GL_LEQUAL, 0f, 1f, false))
        shellCol = (shellInst.materials.first().get(ColorAttribute.Diffuse) as ColorAttribute).color
    }

    private fun hasEdges() = skin.id == Skins.VOID_ID || skin.id == Skins.ZEN_ID

    /** Shard colour for dust/bursts: white sparkle for sparkle skins, else the body colour. */
    fun trailCol(): Color = if (skin.sparkle) Color.WHITE else col

    // ---------------------------------------------------------------- verbs

    /** Returns true if the lane changed (false = already there / out of range → bonk). */
    fun moveToLane(target: Int): Boolean {
        val t = target.coerceIn(0, Lanes.last)
        if (t == lane) return false
        val from = px
        lane = t
        game.session.laneChanged(lane, Lanes.count)
        if (zappyEnabled) {
            // Move the collision body now: no eased path, swept pickups, or extra invulnerability.
            px = laneX(lane)
            nudge = 0f
            if (::inst.isInitialized) {
                val effect = zappyFx ?: ZappyFx(unit).also { zappyFx = it }
                effect.fire(from, px, inst.transform)
                inst.transform.getTranslation(tmp)
                inst.transform.setTranslation(px, tmp.y, tmp.z)
                shellInst.transform.getTranslation(tmp)
                shellInst.transform.setTranslation(px, tmp.y, tmp.z)
            }
            SoundFx.play("blip", rate = 1.8f, vol = .48f)
        } else SoundFx.play("whoosh", rate = 0.95f + rnd.nextFloat() * 0.15f)
        Haptics.tick()
        return true
    }

    /**
     * Outer Space has no walls: a swipe past the last lane carries the cube on
     * toward where a lane would be, over the kerb and off the road.
     */
    fun stepOff(dir: Int) {
        steppedOff = true
        lane = if (dir < 0) -1 else Lanes.count
        SoundFx.play("whoosh", rate = 0.95f + rnd.nextFloat() * 0.15f)
        Haptics.tick()
    }

    /** Sideways speed right now: the lane glide, or the fall's drift. */
    val lateralVelocity: Float
        get() = if (falling) vx else if (zappyEnabled) 0f else (laneX(lane) - px) * (if (hover) 4.5f else if (lowGravity) LOW_G_LANE_RATE else 13f)

    /**
     * The ground is gone. Everything the cube was doing carries on: its
     * height and vertical speed, its lean, its sideways glide ([vx]) and the
     * forward tumble of a cube rolling at [speed]. From here it only falls,
     * under [gravity].
     * [minX]..[maxX] are a pit's walls, which keep it inside the hole.
     */
    fun fall(speed: Float, vx: Float, gravity: Float, minX: Float = -1e9f, maxX: Float = 1e9f, drag: Float = 0f) {
        if (falling) return
        falling = true
        fallGravity = gravity
        fallDrag = drag
        clearJumpInput()
        airJumpAvailable = false
        if (!air) { air = true; vy = 0f }
        slamming = false; duckT = 0f
        this.vx = vx
        forward = speed
        pz = 0f
        noWalls()
        fallMinX = minX; fallMaxX = maxX
        rollRate = speed * 42f + (if (lowGravity) 105f else 160f)
        tiltAtFall = ((laneX(lane) - px) * -22f).coerceIn(-32f, 32f)
        fallTilt = 0f
        // Tipping over a side edge turns the cube about that edge as well; straight down, it
        // still wobbles a little sideways, so the tumble reads in 3D.
        fallTiltRate = if (kotlin.math.abs(vx) > 1f) (-vx * 55f).coerceIn(-320f, 320f) else 70f * (if (rnd.nextBoolean()) 1f else -1f)
    }

    /**
     * This frame's hole under the falling cube: its ends at [near] and [far]
     * along the road, its rim from [bottom] up to [top] (a shaft's walls run
     * down for ever). A cube still level with the rim strikes the end it meets.
     */
    fun fallWalls(near: Float, far: Float, top: Float, bottom: Float) {
        wallNear = near; wallFar = far; wallTop = top; wallBottom = bottom
    }

    fun noWalls() { wallNear = 1e9f; wallFar = -1e9f }

    fun bonk(dir: Int) {
        nudge = dir * 0.4f
        SoundFx.play("tap", rate = 0.7f)
        Haptics.tick()
    }

    fun setFlying(on: Boolean) {
        if (flying == on) return
        clearJumpInput()
        airJumpAvailable = false
        flying = on
        duckT = 0f
        flyY = FLY_Y
        descending = false
        if (on) { air = false; vy = 0f } else { air = true; vy = 0f } // ends with a fall + normal landing
    }

    /** The flight ends on its landing at height [y]: onto the ground like any other landing. */
    fun touchDown(y: Float) {
        setFlying(false)
        py = y; air = false; vy = 0f
        landed(y)
    }

    /** A landing's pop, click, dust and squash. */
    private fun landed(gy: Float) {
        if (!quietLanding) {
            if (lowGravity) SoundFx.play("moonland", rate = 0.95f + Random.nextFloat() * 0.1f)
            else SoundFx.play("pop", rate = 0.95f + rnd.nextFloat() * 0.12f)
            Haptics.click()
        }
        quietLanding = false
        game.burst3d(tmp.set(px, gy - 0.39f, 0.4f), trailCol(), n = 8, speed = 3.2f, size = 0.09f, life = 0.4f)
        squash = 1f
    }

    /** Put the cube on a platform of height [h] (bubble save after a side hit). */
    fun forceGround(h: Float) {
        py = ground + h; air = false; vy = 0f
        airJumpAvailable = false
        clearJumpInput()
    }

    fun jump() {
        if (flying || hover) return
        if (air && coyoteLeft <= 0f) {
            val allowed = if (tripleJumpEnabled) 2 else if (doubleJumpEnabled) 1 else 0
            if (airJumpAvailable && airJumpsUsed < allowed) {
                airJumpsUsed++
                airJumpAvailable = airJumpsUsed < 2
                takeOff(extraJump = true)
            } else jumpBuffer = 0.1f
            return
        }
        takeOff()
    }

    fun clearJumpInput() { coyoteLeft = 0f; jumpBuffer = 0f }

    /** Airborne with a double jump still to spend. */
    val canAirJump: Boolean
        get() = air && airJumpAvailable && airJumpsUsed < (if (tripleJumpEnabled) 2 else if (doubleJumpEnabled) 1 else 0)

    /**
     * Came up out of a void just short of the far lip, the road at [groundY]
     * (cube centre) just above: the cube scrambles up onto it instead of
     * snapping there.
     */
    fun catchLedge(groundY: Float) {
        if (!air || py >= groundY) return
        val g = 26f * (if (lowGravity) LOW_G else 1f)
        vy = max(vy, kotlin.math.sqrt(2f * g * (groundY - py + 0.12f)))
    }

    private fun takeOff(extraJump: Boolean = false) {
        clearJumpInput()
        if (!extraJump) { airJumpAvailable = true; airJumpsUsed = 0 }
        // Jump height scales with velocity squared, so sqrt(2) gives twice the height.
        air = true; vy = (if (floaty) 6.9f else 8.4f) * (if (gigajumpEnabled) 1.4142136f else 1f) * launchScale
        slamming = false
        duckT = 0f // jumping cancels a roll
        if (lowGravity) SoundFx.play("moonjump", rate = if (extraJump) 1.3f else 0.95f + Random.nextFloat() * 0.1f, vol = 0.5f)
        else SoundFx.play("whoosh", rate = if (extraJump) 1.55f else 1.3f)
        Haptics.click()
        game.burst3d(tmp.set(px, if (extraJump) py - .35f else .1f, .3f),
            if (extraJump) Color(.5f, 1f, .8f, 1f) else trailCol(),
            n = if (extraJump) 12 else 6, speed = 2.5f, size = .08f, life = .35f)
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
        clearJumpInput()
        airJumpsUsed = 0
        airJumpAvailable = true
        air = true; vy = v * launchScale; duckT = 0f; slamming = false
        stretch = 1f
    }

    /** Context-sensitive DOWN: slam when airborne, roll under when grounded. */
    fun downAction() {
        clearJumpInput() // a newer DOWN replaces a pending UP
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
        zappyFx?.update(dt, mv)
        var event = EV_NONE
        coyoteLeft = max(0f, coyoteLeft - dt)
        jumpBuffer = max(0f, jumpBuffer - dt)
        val gy = ground + groundH
        // Teleports stay centred while a portal animates lane spacing; ordinary skins ease.
        if (falling) {
            px += vx * dt
            vx *= max(0f, 1f - 2.4f * dt)
            if (px < fallMinX) { px += (fallMinX - px) * min(1f, dt * 14f); vx = max(vx, 0f) }
            if (px > fallMaxX) { px += (fallMaxX - px) * min(1f, dt * 14f); vx = min(vx, 0f) }
            fallTilt += fallTiltRate * dt
            rollRate += (FALL_TUMBLE - rollRate) * min(1f, dt * 2.5f) // a blur at speed settles to a tumble you can follow
        } else if (zappyEnabled) px = laneX(lane)
        else px += (laneX(lane) - px) * min(1f, dt * (if (hover) 4.5f else if (lowGravity) LOW_G_LANE_RATE else 13f))
        nudge *= max(0f, 1f - 10f * dt)
        // A portal changes the underlying world, not an active jetpack's altitude.
        // Keep hover set so flight expiry naturally returns to the floating road.
        if (falling) {
            vy -= fallGravity * dt
            vy *= max(0f, 1f - fallDrag * dt)
            py += vy * dt
            // It flies on over the world at its own speed; the world scrolls by the camera's ([mv]).
            pz += mv - forward * dt
            forward *= max(0f, 1f - 0.12f * dt)
            if (py - HALF < wallTop && py + HALF > wallBottom) {
                if (forward > 0f && pz - HALF < wallFar) { // the far end: it bounces back off it, its spin knocked back
                    struckSpeed = forward
                    pz = wallFar + HALF; forward *= -0.22f; rollRate *= -0.3f; vy = min(vy, 0f) - 0.6f
                    event = EV_STRUCK
                } else if (forward < 0f && pz + HALF > wallNear) {
                    struckSpeed = -forward
                    pz = wallNear - HALF; forward *= -0.3f
                }
            }
        } else if (flying) {
            clearJumpInput()
            py = if (descending) flyY else py + (flyY - py) * min(1f, dt * 4f) // an easy climb; the descent is the arc itself
        } else if (hover) {
            clearJumpInput()
            py += (HOVER_Y + 0.15f * sin(time * 2.2f) - py) * min(1f, dt * 3f)
            air = false; vy = 0f
        } else if (air) {
            vy -= (if (floaty && !slamming) 16f else 26f) * (if (lowGravity) LOW_G else 1f) * dt
            py += vy * dt
            if (py <= gy && vy <= 0f) {
                py = gy; air = false; vy = 0f
                airJumpAvailable = false
                coyoteLeft = 0f
                landed(gy)
                event = if (slamming) EV_GROUND_POUND else EV_LANDED
                if (slamming) { slamming = false; duckT = 0.5f } // slam → auto-crouch on landing
                if (jumpBuffer > 0f) takeOff()
            }
        } else if (gy > py + 0.001f) {
            // the ground rose: a ramp lifts you a little each frame; a whole platform side is a wall
            if (gy - py > 0.45f) event = EV_SIDE_HIT else py = gy
        } else if (gy < py - 0.02f) {
            if (py - gy <= STEP_DOWN) py = max(gy, py - STEP_RATE * dt) // a step down: still on the ground, so a jump always counts
            else {
                air = true; vy = 0f // walked off an edge
                airJumpAvailable = true
                airJumpsUsed = 0
                coyoteLeft = LEDGE_GRACE
            }
        }
        squash = max(0f, squash - dt * 5f)
        stretch = max(0f, stretch - dt * 2.2f)
        // roll/duck window decays; eased `duck` drives the rolling pose + low collision profile
        if (duckT > 0f) duckT = max(0f, duckT - dt)
        val duckTarget = if (duckT > 0f && !air) 1f else 0f
        duck += (duckTarget - duck) * min(1f, dt * 18f)
        val idle = mv == 0f && !flying && !hover && !falling
        idleMix += ((if (idle) 1f else 0f) - idleMix) * min(1f, dt * 4f)
        roll += if (falling) rollRate * dt // the tumble carries on: the road stopping does not stop the cube
            else mv * 42f + (if (air && !idle) (if (lowGravity) 105f else 160f) * dt else 0f) + duck * 260f * dt // tumble; flip in air (a lazy one in space), fast roll while ducking
        if (roll > 360f) roll -= 360f
        // a rolling cube rides up over its corners: lift it so it never sinks into the floor (no jitter, a real roll)
        val ra = Math.toRadians((roll % 90f).toDouble())
        val lift = if (air || flying) 0f else (0.45f * (kotlin.math.abs(kotlin.math.cos(ra)) + kotlin.math.abs(kotlin.math.sin(ra))).toFloat() - 0.45f) * (1f - duck)
        val tilt = if (falling) tiltAtFall + fallTilt else ((laneX(lane) - px) * -22f).coerceIn(-32f, 32f)
        val sq = squash * 0.3f
        val st = stretch * stretch * 0.35f
        val duY = duck * 0.20f - lift // hug the ground while rolling
        spentMix += ((if (phaseSpent) 1f else 0f) - spentMix) * min(1f, dt * 4f)
        // skin colours are pure functions of time — sampled every frame, no allocation
        hsvInto(col, skin.hueAt(time, baseHue), skin.sat, skin.valueAt(time))
        visualTime = time
        hsvInto(shellCol, skin.hueAt(time, baseHue), skin.sat * 0.9f, 1f)
        if (dim > 0f) { col.lerp(DARK, dim); shellCol.lerp(DARK, dim) }
        openingMaterial(skin.opacity + (1f - skin.opacity) * spentMix, (if (skin.id == 13) 1f + 0.6f * spentMix else 0f))
        val breathe = 1f + 0.03f * idleMix * sin(time * 2.4f)
        val size = bodySize()
        val drop = (1f - size) * 0.36f // a smaller cube still rests on the road
        inst.transform.setToTranslation(px + nudge, py - squash * 0.08f - duY - drop, pz)
            .rotate(Vector3.Y, idleYaw * idleMix)
            .rotate(Vector3.Z, tilt)
            .rotate(Vector3.X, -roll)
            .scale(breathe, 1f / breathe, breathe)
            .scale(0.9f * (1f + sq + duck * 0.35f - st * 0.5f), 0.9f * (1f - sq + st) * (1f - duck * 0.5f), 0.9f * (1f + sq + duck * 0.1f - st * 0.5f))
            .scale(size, size, size)
        val pulse = glowScale(time) * size
        shellBlend.opacity = shellOpacity(time) * (1f - spentMix) * (1f - dim)
        shellInst.transform.setToTranslation(px + nudge, py - duY - drop, pz)
            .rotate(Vector3.Y, idleYaw * idleMix).rotate(Vector3.Z, tilt).rotate(Vector3.X, -roll)
            .scale(pulse, pulse, pulse)

        if (trail) emitTrail(dt, time, px, py, pz + 0.5f, if (flying) 2.5f else 1f, stream = stream)
        return event
    }

    /** Shed the equipped trail behind ([x],[y],[z]); [boost] multiplies the rate (jetpack). */
    fun emitTrail(dt: Float, time: Float, x: Float, y: Float, z: Float, boost: Float = 1f, scale: Float = 1f, stream: Float = 0f) {
        val t = this.trail
        if (t.rate <= 0f || t.count <= 0) { trailT = 0f; return }
        trailT += dt
        val every = 1f / (t.rate * skin.trail * boost)
        if (trailT < every) return
        trailT = 0f
        trailK++
        if (t.id == Trails.VOID_ID) {
            // Heavy black remnants hang in the air; a tiny cold edge marks each one.
            tmpCol.set(.008f, .006f, .014f, 1f)
            game.burst3d(tmp.set(x, y, z), tmpCol, n = 1, speed = .18f * scale,
                size = .24f * scale, life = 1.25f, gravity = -.35f, biasZ = stream)
            tmpCol.set(.65f, .57f, .86f, 1f)
            game.burst3d(tmp.set(x + .12f * scale, y + .1f * scale, z), tmpCol, n = 1,
                speed = .3f * scale, size = .025f * scale, life = .85f, gravity = -.5f, biasZ = stream)
            return
        }
        val c = if (t.mode == Trails.BODY) trailCol() else hsvInto(tmpCol, t.hueAt(time, trailK), t.sat, t.value)
        // shards stream back past the camera ([stream] ≈ half the run speed), so the trail reads as motion
        game.burst3d(tmp.set(x, y, z), c, n = t.count, speed = t.speed * scale, size = t.size * scale * 1.6f, life = t.life * 1.35f * (1f + (scale - 1f) * 0.5f), gravity = t.gravity, biasZ = stream)
    }

    /**
     * Pose for the wardrobe / results stage at ([x],[y]): turning slowly,
     * bobbing. Colours keep animating so live skins show what they do.
     */
    fun showcase(time: Float, baseHue: Float, x: Float = 0f, y: Float = 0.75f, spin: Float = 45f, extraYaw: Float = 0f, scale: Float = 1f) {
        zappyFx?.clear()
        if (wantedSkin() != curSkinId) applySkin(baseHue, time)
        trail = Trails.get(wantedTrail())
        hsvInto(col, skin.hueAt(time, baseHue), skin.sat, skin.valueAt(time))
        visualTime = time
        hsvInto(shellCol, skin.hueAt(time, baseHue), skin.sat * 0.9f, 1f)
        val yy = y + 0.06f * sin(time * 2.2f)
        val yaw = time * spin + extraYaw
        val sc = 0.9f * scale * bodySize()
        inst.transform.setToTranslation(x, yy, 0f).rotate(Vector3.Y, yaw).rotate(Vector3.X, 12f).scale(sc, sc, sc)
        val pulse = glowScale(time) * scale * bodySize()
        shellBlend.opacity = shellOpacity(time)
        shellInst.transform.setToTranslation(x, yy, 0f).rotate(Vector3.Y, yaw).rotate(Vector3.X, 12f).scale(pulse, pulse, pulse)
        px = x; py = yy
    }

    /**
     * The jackpot pose at ([x],[y]): turned [yaw], tipped [tip], its colour
     * blended [gold] of the way to [goldCol]. Leaves [px]/[py] alone, so the
     * run resumes from where the cube actually was.
     */
    fun jackpotPose(time: Float, baseHue: Float, x: Float, y: Float, yaw: Float, tip: Float, scale: Float, gold: Float, goldCol: Color) {
        zappyFx?.clear()
        hsvInto(col, skin.hueAt(time, baseHue), skin.sat, skin.valueAt(time)).lerp(goldCol, gold)
        visualTime = time
        hsvInto(shellCol, skin.hueAt(time, baseHue), skin.sat * 0.9f, 1f).lerp(goldCol, gold)
        // The Gambler already strobes yellow: the jackpot makes the cube shine from within instead.
        // The run's next update resets the emission through openingMaterial.
        val material = inst.materials.first()
        val glow = material.get(ColorAttribute.Emissive) as? ColorAttribute
            ?: ColorAttribute.createEmissive(0f, 0f, 0f, 1f).also { material.set(it) }
        glow.color.set(goldCol.r * 0.32f * gold, goldCol.g * 0.24f * gold, goldCol.b * 0.05f * gold, 1f)
        val sc = 0.9f * scale * bodySize()
        inst.transform.setToTranslation(x, y, 0f).rotate(Vector3.Y, yaw).rotate(Vector3.X, tip).scale(sc, sc, sc)
        val pulse = glowScale(time) * scale * bodySize() * (1f + 0.12f * gold)
        shellBlend.opacity = shellOpacity(time) + 0.1f * gold
        shellInst.transform.setToTranslation(x, y, 0f).rotate(Vector3.Y, yaw).rotate(Vector3.X, tip).scale(pulse, pulse, pulse)
    }

    private val voidEmissive = Color()
    private var voidEmissiveSaved = false

    /**
     * The void show's pose at ([x],[y],[z]): turned [yaw], tipped [tip] about the view axis,
     * stretched to ([sx],[sy],[sz]) and glowing [glow] of the way to [glowCol] from within.
     * Leaves [px]/[py] alone; [endVoidPose] gives the skin its own glow back.
     */
    fun voidPose(time: Float, baseHue: Float, x: Float, y: Float, z: Float, yaw: Float, tip: Float,
                 sx: Float, sy: Float, sz: Float, glow: Float, glowCol: Color, toNormal: Float = 0f) {
        // The pose the stage would show without the void, captured before it is overridden.
        voidNormalBody.set(inst.transform); voidNormalShell.set(shellInst.transform)
        zappyFx?.clear()
        hsvInto(col, skin.hueAt(time, baseHue), skin.sat, skin.valueAt(time)).lerp(glowCol, glow * 0.35f)
        visualTime = time
        hsvInto(shellCol, skin.hueAt(time, baseHue), skin.sat * 0.9f, 1f).lerp(glowCol, glow)
        val material = inst.materials.first()
        val emissive = material.get(ColorAttribute.Emissive) as? ColorAttribute
            ?: ColorAttribute.createEmissive(0f, 0f, 0f, 1f).also { material.set(it) }
        if (!voidEmissiveSaved) { voidEmissive.set(emissive.color); voidEmissiveSaved = true }
        emissive.color.set(voidEmissive).lerp(glowCol.r * 0.3f, glowCol.g * 0.3f, glowCol.b * 0.3f, 1f, glow)
        inst.transform.setToTranslation(x, y, z).rotate(Vector3.Z, tip).rotate(Vector3.Y, yaw).rotate(Vector3.X, 12f).scale(sx * 0.9f, sy * 0.9f, sz * 0.9f)
        val pulse = glowScale(time)
        shellBlend.opacity = shellOpacity(time)
        shellInst.transform.setToTranslation(x, y, z).rotate(Vector3.Z, tip).rotate(Vector3.Y, yaw).rotate(Vector3.X, 12f).scale(sx * pulse, sy * pulse, sz * pulse)
        if (toNormal > 0f) { // ease into the stage's own pose, so handing back never snaps
            mixPose(inst.transform, voidNormalBody, toNormal)
            mixPose(shellInst.transform, voidNormalShell, toNormal)
        }
    }

    private val voidNormalBody = Matrix4()
    private val voidNormalShell = Matrix4()
    private val mixA = Vector3(); private val mixB = Vector3()
    private val mixSa = Vector3(); private val mixSb = Vector3()
    private val mixQa = Quaternion(); private val mixQb = Quaternion()

    private fun mixPose(pose: Matrix4, target: Matrix4, amount: Float) {
        pose.getTranslation(mixA); target.getTranslation(mixB)
        pose.getScale(mixSa); target.getScale(mixSb)
        pose.getRotation(mixQa, true); target.getRotation(mixQb, true)
        pose.set(mixA.lerp(mixB, amount), mixQa.slerp(mixQb, amount), mixSa.lerp(mixSb, amount))
    }

    fun endVoidPose() {
        if (!voidEmissiveSaved) return
        (inst.materials.first().get(ColorAttribute.Emissive) as? ColorAttribute)?.color?.set(voidEmissive)
        voidEmissiveSaved = false
    }

    private fun shellOpacity(time: Float): Float {
        if (skin.id == Skins.VOID_ID) return .025f + .012f * sin(time * 1.8f)
        val glow = ((0.22f + 0.08f * sin(time * 6f)) * skin.glow).coerceAtMost(0.75f)
        return glow * if (skin.opacity < 1f) 0.35f else 1f
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

    private fun glowScale(time: Float) = if (skin.id == Skins.VOID_ID) .94f else 0.9f * (1.18f + 0.06f * sin(time * 8f))

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

    /** Absolute transforms shared with the native startup view; no accumulated pose. */
    fun openingPose(pose: cube.run.intro.OpeningPose, seconds: Float, baseHue: Float, skinAmount: Float = 1f,
                    launchAppearance: cube.run.intro.LaunchAppearance? = null) {
        idleYaw = pose.yaw; idleT = 0f; idleMix = 1f
        hsvInto(col, skin.hueAt(seconds, baseHue), skin.sat, skin.valueAt(seconds))
        hsvInto(shellCol, skin.hueAt(seconds, baseHue), skin.sat*.9f, 1f)
        val source = launchAppearance ?: cube.run.intro.LaunchAppearance.ROSE
        fun blend(color: Color, shell: Boolean) {
            val from = source.color(seconds, shell)
            val r = (from shr 16 and 255)/255f; val g = (from shr 8 and 255)/255f; val b = (from and 255)/255f
            color.set(r+(color.r-r)*skinAmount, g+(color.g-g)*skinAmount, b+(color.b-b)*skinAmount, color.a)
        }
        blend(col, false); blend(shellCol, true)
        openingMaterial(source.skin.opacity+(skin.opacity-source.skin.opacity)*skinAmount,
            (if (source.skin.id == 13) 1f-skinAmount else 0f)+(if (skin.id == 13) skinAmount else 0f))
        inst.transform.setToTranslation(0f, .45f, 0f)
            .rotate(Vector3.Y, pose.yaw).rotate(Vector3.Z, pose.tilt)
            .scale(pose.scaleX, pose.scaleY, pose.scaleX)
        shellInst.transform.setToTranslation(0f, .45f, 0f)
            .rotate(Vector3.Y, pose.yaw).rotate(Vector3.Z, pose.tilt)
            .scale(pose.shellScale, pose.shellScale, pose.shellScale)
        shellBlend.opacity = source.shellOpacity(seconds)+(shellOpacity(seconds)-source.shellOpacity(seconds))*skinAmount
    }

    private fun openingMaterial(opacity: Float, emission: Float) {
        val material = inst.materials.first()
        val blend = material.get(BlendingAttribute.Type) as? BlendingAttribute
        if (blend != null) blend.opacity = opacity
        else if (opacity < 1f) material.set(BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, opacity))
        val emissive = material.get(ColorAttribute.Emissive) as? ColorAttribute
        if (emissive != null) emissive.color.set(.24f*emission, .25f*emission, .26f*emission, 1f)
        else if (emission > 0f) material.set(ColorAttribute.createEmissive(.24f*emission, .25f*emission, .26f*emission, 1f))
    }

    /** Draw the cube; [ground] lifts everything by the rolling terrain under it. */
    fun render(batch: ModelBatch, env: Environment, ground: Float = 0f) {
        if (ground != 0f) {
            liftM.setToTranslation(0f, ground, 0f)
            inst.transform.mulLeft(liftM); shellInst.transform.mulLeft(liftM)
        }
        batch.render(inst, env)
        if (hasEdges()) {
            // Hairline geometry: Void keeps its black silhouette on every background; Zen gets an ink outline.
            val width = if (skin.id == Skins.ZEN_ID) .045f else .013f + .003f * sin(visualTime * 1.8f)
            var edge = 0
            for (axis in 0..2) for (ai in 0..1) for (bi in 0..1) {
                val a = ai * 2 - 1; val b = bi * 2 - 1
                val e = edges[edge++]
                e.transform.set(inst.transform)
                when (axis) {
                    0 -> e.transform.translate(0f, a * .5f, b * .5f).scale(1.015f, width, width)
                    1 -> e.transform.translate(a * .5f, 0f, b * .5f).scale(width, 1.015f, width)
                    else -> e.transform.translate(a * .5f, b * .5f, 0f).scale(width, width, 1.015f)
                }
                batch.render(e, env)
            }
        }
        if (skin.id != Skins.VOID_ID) batch.render(shellInst, env)
        zappyFx?.render(batch, env, ground, inst.transform)
        if (ground != 0f) {
            liftM.setToTranslation(0f, -ground, 0f)
            inst.transform.mulLeft(liftM); shellInst.transform.mulLeft(liftM)
        }
    }
}
