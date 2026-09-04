package cube.run.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import cube.run.core.Gdx3DGame
import cube.run.core.GameSession
import cube.run.core.Haptics
import cube.run.core.Progress
import cube.run.core.Stage
import cube.run.core.Settings
import cube.run.core.SoundFx
import cube.run.core.hsvInto
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Cube Run — 3-lane endless runner. The world rushes toward the camera;
 * swipe LEFT/RIGHT to snap lanes, UP to jump, DOWN to roll (on the ground)
 * or slam (mid-air). Double-tap to pop a bubble shield (if you have one).
 *
 * This class is the conductor: it owns the run state (started / dead), routes
 * input, resolves collisions + pickups + scoring against the [Track], and
 * drives the camera. The pieces:
 *  - [Track] — the lane-walk director (rows, sections, platforms, coins, pickups)
 *  - [Player] — physics (incl. platform heights + jetpack flight), pose, skin
 *  - [Shield] — the bubble; [PowerUps] — magnet / 2× / jetpack timers + HUD bars
 *  - [Difficulty] + [FireBoost] — speed / tier and the opening boost button
 *  - [Scenery] — floor, posts, wind streaks
 *  - [GiftStage] — the 3D mystery-box stage shown by the run-over flow
 *
 * +1 per row (2× with the multiplier), near-miss bonus for shaving an
 * obstacle; coins and mystery boxes are handed to the session and banked at
 * game over.
 */
class CubeRun(session: GameSession, private val autoStart: Boolean = false) : Gdx3DGame(session) {

    private val laneW = 1.7f
    private val rnd = Random(System.nanoTime())
    private val obstacles = ObstacleFactory(rnd, laneW)
    private val track = Track(rnd, obstacles, laneW)
    private val player = Player(this, laneW, rnd)
    private val shield = Shield(this)
    private val powerUps = PowerUps()
    private val difficulty = Difficulty()
    private val fire = FireBoost(this, difficulty)
    private val scenery = Scenery(this, laneW, rnd)
    private val gift = GiftStage(this)
    private val tmp = Vector3()
    private val tmpCol = Color()

    // ---- run state ----
    private var baseHue = 0f
    private var started = false
    private var dead = false
    private var spd = 4.5f
    private var dist = 0f
    private var rowsPassed = 0
    private var curTier = 0          // last tier reached (a chime marks each unlock)
    private var touchIsFire = false  // current touch began on the fire button (don't steer with it)
    private var fovKick = 0f         // transient field-of-view punch (bubble, fire taps, level up)
    private var jetGrace = 0f        // safe landing window after a jetpack flight
    private var jetBoost = 0f        // eased 0..1: how much of the jetpack's speed boost is on
    private var flyCam = 0f          // eased 0..1: the high-angle flight camera
    private val jetGlide = 1.4f      // the last seconds of a flight glide back down to the ground
    private val jetSpeedUp = 0.75f   // the jetpack's speed boost (+75%)

    // ---- death: let the crash animation play before the run-over screens ----
    private val deathAnimTime = 1.5f
    private var deathT = 0f
    private var gameOverShown = false

    // ---- style points: tap mid-air for an ascending combo (purely for flair) ----
    private var styleCombo = 0   // consecutive air taps; resets on landing. Never stored or shown as a total.
    private var lastTapT = -9f   // for double-tap detection (bubble activation)

    // ---- goodies ----
    private val coinCol = Color()
    private val boxCol = Color()
    private val bandCol = Color()
    private val magnetCol = Color()
    private val multCol = Color()
    private val jetCol = Color()
    private val flameCol = Color()
    private var coinsRun = 0        // collected this run (banked by the session at game over)
    private var boxesRun = 0        // mystery boxes collected this run (opened on the run-over screens)
    private var coinStreak = 0      // consecutive pickups without a miss (the milestone chimes)
    private var coinPitch = 0       // rising coin pitch; resets after a short gap without a coin
    private var lastCoinT = -9f
    private val magnetR = 1.6f      // passive pull radius (world units); the MAGNET pickup makes it huge
    private val nearMissBonus = 2   // score for shaving an obstacle

    private lateinit var pickupInst: ModelInstance
    private lateinit var pickupBlend: BlendingAttribute

    // smooth-control gesture state (positional steering within one continuous touch)
    private var smoothAnchorX = 0f
    private var smoothAnchorLane = 1
    private var smoothVAccum = 0f

    /** Obstacles pop against the current floor hue, which drifts with distance. */
    private fun worldHue() = baseHue + dist * 1.6f

    // ---- the wardrobe: the pre-run scene swaps to a dark stage with the cube spinning on it
    private var wardrobe = false
    private val skyTopSave = Color()
    private val skyBottomSave = Color()
    private val stageShadow = Color(0.02f, 0.01f, 0.05f, 1f)

    private fun enterWardrobe() {
        wardrobe = true
        skyTopSave.set(bgTop); skyBottomSave.set(bgBottom)
        hsvInto(bgTop, baseHue + 30f, 0.5f, 0.22f)
        hsvInto(bgBottom, baseHue + 70f, 0.6f, 0.05f)
    }

    private fun exitWardrobe() {
        wardrobe = false
        bgTop.set(skyTopSave); bgBottom.set(skyBottomSave)
        cam.fieldOfView = 60f
    }

    override fun init() {
        Stage.reset()
        baseHue = (System.currentTimeMillis() % 360L).toFloat()
        bgTop = gdxHsv(baseHue + 30f, 0.6f, 0.4f)
        bgBottom = gdxHsv(baseHue + 70f, 0.65f, 0.1f)
        hsvInto(coinCol, 46f, 0.85f, 1f)
        hsvInto(boxCol, 282f, 0.72f, 0.95f)
        hsvInto(bandCol, 46f, 0.8f, 1f)
        hsvInto(magnetCol, 4f, 0.9f, 1f)
        hsvInto(multCol, 328f, 0.8f, 1f)
        hsvInto(jetCol, 215f, 0.75f, 1f)
        hsvInto(flameCol, 28f, 0.9f, 1f)

        scenery.init(baseHue)
        player.init(baseHue, time)
        shield.init()
        pickupInst = ModelInstance(sphere(1f, Color.WHITE, div = 24))
        pickupBlend = BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE, 0.5f)
        pickupInst.materials.first().set(ColorAttribute.createDiffuse(gdxHsv(190f, 0.45f, 1f)), pickupBlend)

        cam.position.set(0f, 3.7f, 6.4f)
        cam.lookAt(0f, 1.0f, -8f)
        cam.update()
        session.setBubbles(Progress.bubbles)
        // "Tap to start" is shown by the HUD (GameChromeView) until the first input.
    }

    // --------------------------------------------------------------- events

    private fun live() = started && !dead && !session.isOver

    override fun paused(): Boolean = Stage.paused

    private fun start() {
        if (started || session.isOver) return
        started = true
        fire.reset(); styleCombo = 0
        coinsRun = 0; boxesRun = 0; coinStreak = 0
        powerUps.reset(); jetGrace = 0f
        // the bubble's length is a permanent upgrade, read once per run
        shield.duration = Progress.BUBBLE.duration(Progress.bubbleLevel)
        difficulty.reset()
        curTier = difficulty.tier()
        track.tier = curTier
        track.reset(coinTrailChance = 0.2f, hue = worldHue())
        session.runStarted()
        SoundFx.play("rise")
        Haptics.click()
        flash(gdxHsv(baseHue + 180f, 0.4f, 1f), 0.12f)
    }

    private fun crash() {
        if (dead) return
        dead = true
        player.setFlying(false)
        burst3d(tmp.set(player.px, player.py, 0f), player.col, n = 40, speed = 9f, size = 0.2f, life = 1.1f)
        burst3d(tmp, Color.WHITE, n = 12, speed = 13f, size = 0.11f, life = 0.6f)
        SoundFx.play("boom")
        Haptics.heavy()
        shake(0.5f)
        flash(Color.RED, 0.45f)
        // NB: the run-over screens are deferred (see tick) so the crash animation is visible.
    }

    /** A collision with a row: fatal, unless the bubble is up — then it takes the hit. */
    private fun hit(row: Row) {
        if (shield.active) smash(row) else crash()
    }

    /** Ran into the side of a platform: fatal, or the bubble hoists you onto it. */
    private fun sideHit(groundH: Float) {
        if (!shield.active) { crash(); return }
        player.forceGround(groundH)
        shield.pop(player.px, player.py)
        fovKick = 0.5f
        SoundFx.play("perfect", rate = 1.2f)
        Haptics.heavy()
        flash(Color.WHITE, 0.2f)
    }

    /**
     * The bubble's one save: the row you hit shatters, everything in a short
     * breather zone ahead dissolves so you can recover, and the bubble pops.
     * One smash per bubble — after the breather the track is live again.
     */
    private fun smash(row: Row) {
        for (ob in row.obs) {
            if (ob.type == ObType.DECO || ob.type == ObType.PLAT) continue
            burst3d(tmp.set(ob.x, ob.cy, row.z), ob.col, n = 14, speed = 9f, size = 0.24f, life = 0.9f)
        }
        row.obs.removeAll { it.type != ObType.PLAT }
        val zone = -(spd * 2.2f) - 4f // ≈ 2 s of clean track
        for (r in track.rows) {
            if (r === row || r.z <= zone || r.z >= 0f || r.obs.isEmpty()) continue
            for (ob in r.obs) {
                if (ob.type == ObType.DECO || ob.type == ObType.PLAT) continue
                burst3d(tmp.set(ob.x, ob.cy, r.z), ob.col, n = 4, speed = 3f, size = 0.14f, life = 0.6f)
            }
            r.obs.removeAll { it.type != ObType.PLAT }
        }
        shield.pop(player.px, player.py)
        fovKick = 0.6f
        session.addScore(3)
        SoundFx.play("boom", rate = 1.4f, vol = 0.7f)
        SoundFx.play("perfect", rate = 1.2f)
        Haptics.heavy()
        shake(0.22f)
        flash(Color.WHITE, 0.2f)
    }

    private fun scoreRow(row: Row) {
        rowsPassed++
        val x2 = powerUps.mult.active
        session.addScore(if (x2) 2 else 1)
        SoundFx.play("tick", rate = 1f + (rowsPassed % 15) * 0.025f, vol = 0.8f)
        Haptics.tick()
        if (row.minClear < 0.34f) { // shaved it — reward a close dodge with an air-rush
            session.addScore(if (x2) nearMissBonus * 2 else nearMissBonus)
            SoundFx.play("whoosh", rate = 1.55f + rnd.nextFloat() * 0.2f, vol = 0.7f)
            Haptics.click()
            flash(Color.WHITE, 0.07f)
            burst3d(tmp.set(player.px, player.py + 0.4f, 0.2f), Color.WHITE, n = 10, speed = 4f, size = 0.1f, life = 0.5f)
        }
        // sky drifts as you survive (mutate in place — no per-row Color allocation)
        hsvInto(bgTop, baseHue + 30f + rowsPassed * 2f, 0.6f, 0.4f)
        hsvInto(bgBottom, baseHue + 70f + rowsPassed * 2f, 0.65f, 0.1f)
    }

    private fun collectCoin(coin: Coin, cz: Float) {
        coin.taken = true
        coinsRun++
        coinStreak++
        session.setCoins(coinsRun)
        // the pitch climbs coin after coin and falls back as soon as the line breaks
        if (time - lastCoinT > 0.4f) coinPitch = 0
        lastCoinT = time
        coinPitch++
        SoundFx.play("coin", rate = (1f + 0.05f * min(coinPitch, 14)).coerceAtMost(1.9f), vol = 0.65f)
        Haptics.tick()
        burst3d(tmp.set(coin.x, coin.y, cz), coinCol, n = 6, speed = 3.2f, size = 0.09f, life = 0.4f)
        burst3d(tmp, Color.WHITE, n = 2, speed = 4f, size = 0.06f, life = 0.25f)
        if (coinStreak == 20 || coinStreak == 50 || coinStreak % 100 == 0) { // milestones: a chime + a gold flash, no text
            SoundFx.play("perfect", rate = 1.1f)
            Haptics.success()
            flash(coinCol, 0.15f)
        }
    }

    private fun collectPickup(row: Row, cz: Float) {
        val kind = row.pickup
        row.pickup = Pickup.NONE
        when (kind) {
            Pickup.BUBBLE -> { shield.activate(player.px, player.py); fovKick = 1f }
            Pickup.BOX -> {
                boxesRun++
                session.setBoxes(boxesRun)
                announce(boxCol, row.pickupX, cz)
                burst3d(tmp.set(row.pickupX, 0.8f, cz), bandCol, n = 10, speed = 6f, size = 0.09f, life = 0.5f)
            }
            Pickup.MAGNET -> { powerUps.magnet.start(Progress.MAGNET.duration(Progress.magnetLevel)); announce(magnetCol, row.pickupX, cz) }
            Pickup.MULT -> { powerUps.mult.start(Progress.MULT.duration(Progress.multLevel)); announce(multCol, row.pickupX, cz) }
            Pickup.JET -> {
                val dur = Progress.JET.duration(Progress.jetLevel)
                powerUps.jet.start(dur)
                player.setFlying(true)
                track.airCoins = true
                aimJetCoins(dur, difficulty.speed() * (1f + jetSpeedUp)) // where the flight will end, at boosted speed
                track.liftCoins()
                announce(jetCol, row.pickupX, cz)
                fovKick = 1f
            }
        }
    }

    /** Pickup feedback: a chime, a tinted flash and a burst — no text on screen. */
    private fun announce(col: Color, x: Float, cz: Float) {
        SoundFx.play("success", rate = 1.3f, vol = 0.8f)
        Haptics.success()
        flash(col, 0.18f)
        burst3d(tmp.set(x, 0.8f, cz), col, n = 18, speed = 5f, size = 0.13f, life = 0.7f)
    }

    /** Tell the track where the flight lands, so the coin line it lays glides down to meet the ground there. */
    private fun aimJetCoins(left: Float, speed: Float) {
        track.jetEndZ = -(left * speed)
        track.jetGlideLen = jetGlide * speed
    }

    private fun endJet() {
        player.setFlying(false)
        track.airCoins = false
        track.dropCoins()
        jetGrace = 1.2f
        SoundFx.play("slide", rate = 0.8f)
    }

    /** Try to spend a stocked bubble (double tap). Returns true when one went up. */
    private fun tryBubble(): Boolean {
        if (!live() || shield.active) return false
        if (!Progress.useBubble()) {
            SoundFx.play("tap", rate = 0.6f) // empty stock
            return false
        }
        session.setBubbles(Progress.bubbles)
        shield.activate(player.px, player.py)
        fovKick = 1f
        return true
    }

    // ---------------------------------------------------------------- input

    override fun onDown(x: Float, y: Float) {
        if (gift.active || wardrobe || Stage.paused) return
        touchIsFire = fire.available(live()) && fire.inZone(x, y)
        if (touchIsFire) {
            if (fire.tap(player.px, player.py, live())) fovKick = max(fovKick, 0.45f)
            return
        }
        start()
        smoothAnchorX = x; smoothAnchorLane = player.lane; smoothVAccum = 0f
    }

    override fun onDrag(x: Float, y: Float, dx: Float, dy: Float) {
        if (touchIsFire) return // this touch is operating the fire button
        if (!Settings.smoothControl || !live()) return
        // horizontal: finger position maps directly to a lane — no lag, no overshoot
        val laneTravel = sw * (0.32f - 0.20f * Settings.smoothSensitivity) // finger px per lane
        val target = (smoothAnchorLane + ((x - smoothAnchorX) / laneTravel).roundToInt()).coerceIn(0, 2)
        player.moveToLane(target)
        // vertical: a clearly-vertical movement is a jump (up) or slam/roll (down) flick
        val vStep = sw * (0.16f - 0.08f * Settings.smoothSensitivity)
        if (abs(dy) > abs(dx)) smoothVAccum += dy else smoothVAccum *= 0.6f // bleed off while steering
        if (smoothVAccum <= -vStep) { smoothVAccum = 0f; player.jump() }
        else if (smoothVAccum >= vStep) { smoothVAccum = 0f; player.downAction() }
    }

    override fun onTap(x: Float, y: Float) {
        if (!live() || touchIsFire) return
        // a quick double tap pops a bubble shield (anywhere, any time)
        if (time - lastTapT < 0.38f) {
            lastTapT = -9f
            if (tryBubble()) return
        } else {
            lastTapT = time
        }
        // tapping while airborne racks up a style combo (flair only — never scored)
        if (player.air) styleTap()
    }

    override fun smoothSwipeEnabled(): Boolean = Settings.smoothControl

    override fun onSwipe(dir: Int) {
        if (touchIsFire) return // this touch is operating the fire button
        if (session.isOver || dead) return
        if (!started) start()
        when (dir) {
            LEFT, RIGHT -> {
                val d = if (dir == LEFT) -1 else 1
                if (player.lane + d in 0..2) player.moveToLane(player.lane + d)
                else player.bonk(d) // bonk the invisible wall
            }
            UP -> player.jump()
            DOWN -> player.downAction()
        }
    }

    // ----------------------------------------------------------------- loop

    /**
     * Walkable height under x = [px] at the player's z: the top of any platform
     * whose segment is passing (rising linearly along its front ramp), else 0.
     */
    private fun groundAt(px: Float): Float {
        var h = 0f
        for (row in track.rows) {
            if (row.z < 0f || row.z > 6.6f) continue
            for (ob in row.obs) {
                if (ob.type != ObType.PLAT || abs(px - ob.x) > ob.halfW + 0.3f) continue
                val local = row.z // how far the front has passed the player
                if (local > ob.sz) continue
                val top = ob.clear
                val here = if (ob.ramp > 0f && local < ob.ramp) top * (local / ob.ramp) else top
                if (here > h) h = here
            }
        }
        return h
    }

    override fun tick(dt: Float) {
        // ---- showcase stages take over the scene when the HUD asks
        if (Stage.mode == Stage.BOX) {
            if (!gift.active) gift.enter(bgTop, bgBottom)
        } else if (gift.active) gift.exit()
        if (gift.active) {
            gift.update(dt, time)
            gift.aimCamera(cam)
            return
        }
        if (Stage.mode == Stage.SKINS || Stage.mode == Stage.RESULT) {
            if (!wardrobe) enterWardrobe()
            player.showcase(time, baseHue)
            if (Stage.mode == Stage.SKINS) { // the wardrobe: the cube centred
                cam.position.set(0f, 2.3f, 7.0f)
                cam.lookAt(0f, 0.75f, 0f)
            } else { // the results: the cube small and whole in the top third, the score card below it
                cam.position.set(0f, 3.4f, 11.5f)
                cam.lookAt(0f, -2.1f, 0f)
            }
            cam.up.set(0f, 1f, 0f)
            cam.fieldOfView = 40f
            return
        } else if (wardrobe) exitWardrobe()

        if (!started && autoStart && time > 0.05f) start() // RESTART: straight into the run

        if (started && !dead) {
            jetBoost += ((if (player.flying) 1f else 0f) - jetBoost) * min(1f, dt * 2f)
            spd = difficulty.speed() * (1f + jetSpeedUp * jetBoost) // blue line is the cruising max; the jetpack rides above it
            fire.tick(dt)
            difficulty.ramp(dt)
        } else if (!started) {
            spd = 4.5f // ambient pre-start scroll
        } else {
            spd = max(0f, spd - spd * 2.4f * dt) // death: world glides to a stop
            deathT = min(deathT + dt, 2.5f)
            // hold on the crash animation, then reveal the run-over screens
            if (!gameOverShown && deathT >= deathAnimTime) { gameOverShown = true; session.gameOver() }
        }
        val mv = spd * dt
        dist += mv
        if (started && !dead && !Settings.devMode) {
            val t = difficulty.tier()
            if (t > curTier) { // a new section tier unlocked: a rising chime + flash marks the step up
                curTier = t
                SoundFx.play("rise", rate = 1.1f + t * 0.1f)
                Haptics.success()
                flash(hsvInto(tmpCol, baseHue + 180f, 0.4f, 1f), 0.16f)
                fovKick = max(fovKick, 0.5f)
            }
        }
        track.tier = curTier
        fovKick = max(0f, fovKick - dt * 2.2f)
        if (jetGrace > 0f) jetGrace = max(0f, jetGrace - dt)

        scenery.scroll(mv, worldHue())
        if (live()) track.spawn(mv, worldHue())

        // ---- timed power-ups
        if (started && !dead) {
            powerUps.magnet.tick(dt)
            powerUps.mult.tick(dt)
            if (powerUps.jet.tick(dt)) endJet()
            else if (player.flying) { // keep the landing point current; glide down through the last seconds
                val left = powerUps.jet.left
                aimJetCoins(left, spd)
                player.flyY = if (left < jetGlide) player.ground + (Player.FLY_Y - player.ground) * (left / jetGlide) else Player.FLY_Y
            }
        }

        if (!dead) {
            val gh = if (player.flying) 0f else groundAt(player.px)
            when (player.update(dt, mv, time, baseHue, trail = started, groundH = gh)) {
                Player.EV_LANDED -> if (styleCombo > 0) styleLand()
                Player.EV_SIDE_HIT -> if (started) sideHit(gh)
            }
            shield.update(dt, time, player.px + player.nudge, player.py)
        }

        // ---- obstacle rows: move, collide, score; coins: magnet + collect; pickups
        track.scroll(mv, time, dt)
        val cubeBottom = player.cubeBottom
        val headY = player.headY
        val px = player.px; val py = player.py
        val immune = player.flying || jetGrace > 0f
        val pull = if (powerUps.magnet.active) 7.5f else magnetR
        for (row in track.rows) {
            if (started && !dead && !immune && abs(row.z) < 0.95f) {
                for (ob in row.obs) {
                    if (ob.type == ObType.DECO || ob.type == ObType.PLAT) continue
                    val lat = abs(px - ob.x) - (ob.halfW + 0.36f)
                    val clear = when (ob.type) {
                        ObType.JUMP -> cubeBottom - ob.clear   // >0 = sailing over the wall
                        ObType.DUCK -> ob.clear - headY        // >0 = tucked under the bar
                        else -> lat                            // dodge: lateral gap
                    }
                    // vertical clearance only counts while you're actually over/under
                    // it — passing beside a partial (window) wall/bar is no near-miss
                    if (ob.type == ObType.DODGE || lat < 0f) row.minClear = min(row.minClear, clear)
                    if (abs(row.z) < 0.82f && lat < 0f && (ob.type == ObType.DODGE || clear < -0.02f)) {
                        hit(row)
                        break
                    }
                }
            }
            if (started && !dead && row.pickup != Pickup.NONE) { // run into it
                val cz = row.z + Row.PICKUP_DZ
                if (abs(cz) < 0.9f && abs(px - row.pickupX) < 0.95f && py < 1.7f) collectPickup(row, cz)
            }
            row.coins?.let { coins ->
                if (row.z > -12f && started && !dead) {
                    for (c in coins) {
                        if (c.taken) continue
                        val cz = row.z + c.dz
                        if (pull > 0f && abs(cz) < pull) { // magnet: coins fly to you
                            val k = min(1f, dt * 11f)
                            c.x += (px - c.x) * k
                            c.y += (py - c.y) * k
                        }
                        if (abs(cz) < 0.8f && abs(px - c.x) < 0.85f && abs(py - c.y) < 0.85f) {
                            collectCoin(c, cz)
                        } else if (cz > 1.1f) { // it's behind you — the streak breaks
                            c.taken = true
                            coinStreak = 0
                        }
                    }
                }
            }
            if (!row.scored && row.z > 1.2f) {
                row.scored = true
                if (started && !dead) scoreRow(row)
            }
        }

        // ---- chase camera: above-behind, leans with the player, lifts with height,
        // pulls back on death; the field of view widens with speed (and punches on bubble/level-up).
        // A jetpack flight (anything higher than a jump) swings it up into a high angle looking down the track.
        val lift = py - player.ground
        val highT = ((lift - 1.5f) / (Player.FLY_Y - player.ground - 1.5f)).coerceIn(0f, 1f)
        flyCam += (highT - flyCam) * min(1f, dt * 4f)
        val f = flyCam
        val cy = 3.6f + lift * 0.3f * (1f - f) + f * 9.5f + deathT * 1.6f
        cam.position.set(px * (0.45f - 0.15f * f), cy, 6.4f + f * 1.2f + deathT * 2.2f)
        cam.lookAt(px * (0.55f - 0.15f * f), 1.0f + lift * 0.5f * (1f - f) + f * 1.6f, -8f - f * 6f)
        cam.up.set(0f, 1f, 0f)
        cam.fieldOfView = 60f + max(0f, spd - 10f) * 0.42f + fovKick * fovKick * 9f
    }

    // ------------------------------------------------------------- style points
    /** A mid-air tap: bump the combo with an ascending pitch + a spark burst (no text). */
    private fun styleTap() {
        styleCombo++
        val rate = (0.85f + 0.16f * styleCombo).coerceAtMost(2f) // pitch climbs each consecutive tap
        SoundFx.play("pop", rate = rate)
        SoundFx.play("tick", rate = (1f + 0.1f * styleCombo).coerceAtMost(1.6f), vol = 0.3f)
        Haptics.tick()
        val hue = 290f + styleCombo * 16f // purple → magenta → red, never green
        flash(hsvInto(tmpCol, hue, 0.5f, 1f), 0.05f)
        burst3d(tmp.set(player.px, player.py + 0.3f, 0.2f), hsvInto(tmpCol, hue, 0.9f, 1f),
            n = 10 + styleCombo * 3, speed = 5f + styleCombo, size = 0.11f, life = 0.55f)
        burst3d(tmp.set(player.px, player.py + 0.3f, 0.2f), Color.WHITE, n = 4, speed = 6f, size = 0.07f, life = 0.3f)
    }

    /** Touchdown after an air combo: a burst of flair (no text, no shake). */
    private fun styleLand() {
        SoundFx.play("perfect", rate = (1f + 0.06f * styleCombo).coerceAtMost(1.7f))
        Haptics.success()
        val hue = 300f + styleCombo * 10f // warm, non-green
        flash(hsvInto(tmpCol, hue, 0.4f, 1f), 0.12f)
        burst3d(tmp.set(player.px, player.py + 0.2f, 0.2f), hsvInto(tmpCol, hue, 0.85f, 1f),
            n = 14 + styleCombo * 3, speed = 7f, size = 0.13f, life = 0.7f)
        burst3d(tmp.set(player.px, player.py + 0.2f, 0.2f), Color.WHITE, n = 6, speed = 5f, size = 0.09f, life = 0.4f)
        styleCombo = 0
    }

    // ------------------------------------------------------------- rendering

    override fun renderHud(shapes: ShapeRenderer, w: Float, h: Float) {
        if (gift.active || wardrobe || dead || Stage.paused) return
        powerUps.drawBars(shapes, w, h, time, if (shield.active) shield.timer else null)
        fire.drawHud(shapes, w, h, time, live())
    }

    /** A box that orbits a pickup's centre as the whole thing spins (offset [dx] along the spun x axis). */
    private fun spinPart(cx: Float, cz: Float, dx: Float, y: Float, sx: Float, sy: Float, sz: Float, yaw: Float, col: Color, fog: Float) {
        val rad = Math.toRadians(yaw.toDouble())
        worldBoxSpin(cx + dx * cos(rad).toFloat(), y, cz - dx * sin(rad).toFloat(), sx, sy, sz, yaw, col, fog)
    }

    private fun renderPickup(r: Row, yaw: Float) {
        val cz = r.z + Row.PICKUP_DZ
        val x = r.pickupX
        val y = 0.85f + 0.1f * sin(time * 3f + cz)
        val fog = Fog.at(cz)
        when (r.pickup) {
            Pickup.BOX -> { // a spinning gift: purple cube with a gold ribbon
                worldBoxSpin(x, y, cz, 0.62f, 0.62f, 0.62f, yaw * 0.5f, boxCol, fog)
                worldBoxSpin(x, y, cz, 0.66f, 0.16f, 0.66f, yaw * 0.5f, bandCol, fog)
                worldBoxSpin(x, y, cz, 0.16f, 0.66f, 0.66f, yaw * 0.5f, bandCol, fog)
            }
            Pickup.MAGNET -> { // a red horseshoe with white tips
                spinPart(x, cz, -0.26f, y + 0.1f, 0.2f, 0.62f, 0.2f, yaw, magnetCol, fog)
                spinPart(x, cz, 0.26f, y + 0.1f, 0.2f, 0.62f, 0.2f, yaw, magnetCol, fog)
                worldBoxSpin(x, y - 0.2f, cz, 0.72f, 0.2f, 0.2f, yaw, magnetCol, fog)
                spinPart(x, cz, -0.26f, y + 0.45f, 0.2f, 0.12f, 0.2f, yaw, Color.WHITE, fog)
                spinPart(x, cz, 0.26f, y + 0.45f, 0.2f, 0.12f, 0.2f, yaw, Color.WHITE, fog)
            }
            Pickup.MULT -> { // two stacked pink slabs: "×2"
                worldBoxSpin(x, y - 0.18f, cz, 0.7f, 0.22f, 0.7f, yaw, multCol, fog)
                worldBoxSpin(x, y + 0.18f, cz, 0.7f, 0.22f, 0.7f, yaw + 30f, multCol, fog)
                worldBoxSpin(x, y + 0.45f, cz, 0.2f, 0.2f, 0.2f, yaw, Color.WHITE, fog)
            }
            Pickup.JET -> { // twin blue tanks with flickering flames
                val flick = 0.18f + 0.1f * abs(sin(time * 21f + cz))
                spinPart(x, cz, -0.2f, y + 0.1f, 0.3f, 0.8f, 0.3f, yaw, jetCol, fog)
                spinPart(x, cz, 0.2f, y + 0.1f, 0.3f, 0.8f, 0.3f, yaw, jetCol, fog)
                spinPart(x, cz, -0.2f, y - 0.42f, 0.2f, flick, 0.2f, yaw, flameCol, fog)
                spinPart(x, cz, 0.2f, y - 0.42f, 0.2f, flick, 0.2f, yaw, flameCol, fog)
            }
        }
    }

    private fun renderPlatform(ob: Ob, front: Float, fog: Float) {
        val top = ob.clear
        val bodyLen = ob.sz - ob.ramp
        worldBox(ob.x, top / 2f, front - ob.ramp - bodyLen / 2f, ob.sx, top, bodyLen, ob.col, fog)
        if (ob.ramp > 0f) { // the ramp as 4 rising steps
            val n = 4
            val d = ob.ramp / n
            for (i in 0 until n) {
                val h = top * (i + 1) / n
                worldBox(ob.x, h / 2f, front - d * (i + 0.5f), ob.sx, h, d, ob.col, fog)
            }
        }
    }

    override fun renderWorldBatched() {
        if (gift.active) { // the mystery-box stage: just the box on a dark backdrop
            fogColor.set(bgBottom)
            gift.render()
            return
        }
        if (wardrobe) { // the wardrobe: a shadow disc under the showcased cube
            fogColor.set(bgBottom)
            worldBox(0f, 0.01f, 0f, 1.6f, 0.02f, 1.6f, stageShadow)
            return
        }
        // haze target ≈ the sky gradient at the horizon, so the far track end and
        // freshly spawned rows dissolve into the background instead of popping in
        fogColor.set(bgBottom).lerp(bgTop, 0.62f)
        val yaw = (time * 240f) % 360f
        for (r in track.rows) { // obstacles first: batch overflow drops from the back
            val fog = Fog.at(r.z)
            for (ob in r.obs) {
                if (ob.type == ObType.PLAT) { renderPlatform(ob, r.z, fog); continue }
                if (ob.anim == ObAnim.PISTON && ob.sy < 0.03f) continue // sunk into the floor
                worldBox(ob.x, ob.cy, r.z, ob.sx, ob.sy, ob.sz, ob.col, fog)
            }
            if (r.pickup != Pickup.NONE && r.pickup != Pickup.BUBBLE) renderPickup(r, yaw)
        }
        // coins: flat gold squares all spinning in lockstep (one lighting solve for the lot), bobbing
        for (r in track.rows) {
            val coins = r.coins ?: continue
            for (c in coins) {
                if (c.taken) continue
                val cz = r.z + c.dz
                worldBoxSpin(c.x, c.y + 0.07f * sin(time * 4f + c.dz * 0.9f), cz, 0.5f, 0.5f, 0.12f, yaw, coinCol, Fog.at(cz))
            }
        }
        val wind = if (dead) 0f else ((spd - 13f) / 15f).coerceIn(0f, 1f)
        scenery.render(if (player.flying) 1f else wind)
    }

    override fun renderWorld(batch: ModelBatch, env: Environment) {
        if (gift.active) return
        if (wardrobe) { player.render(batch, env); return }
        // only the rotating/blended pieces go through ModelBatch — the rest of the
        // world is a single batched draw call (renderWorldBatched)
        if (!dead) {
            player.render(batch, env)
            shield.render(batch, env)
        }
        // floating bubble pickups (rare — one instance re-posed per row is fine, the
        // batch copies the transform at render() time)
        for (r in track.rows) {
            if (r.pickup != Pickup.BUBBLE) continue
            val cz = r.z + Row.PICKUP_DZ
            if (cz < -Fog.end) continue
            pickupBlend.opacity = 0.35f + 0.15f * sin(time * 5f + cz)
            pickupInst.transform.setToTranslation(r.pickupX, 0.95f + 0.12f * sin(time * 3f + cz), cz)
                .scale(1.3f, 1.3f, 1.3f)
            batch.render(pickupInst, env)
        }
    }
}
