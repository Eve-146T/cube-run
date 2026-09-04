package cube.run.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import cube.run.core.Gdx3DGame
import cube.run.core.GameSession
import cube.run.core.Stage
import cube.run.data.Bonus
import cube.run.data.BubbleSkins
import cube.run.data.Progress
import cube.run.data.Scores
import cube.run.data.Settings
import cube.run.game.stage.GiftStage
import cube.run.game.stage.Showcase
import cube.run.game.track.Coin
import cube.run.game.track.ObType
import cube.run.game.track.ObstacleFactory
import cube.run.game.track.Pickup
import cube.run.game.track.Row
import cube.run.game.track.Track
import cube.run.game.track.TrackRenderer
import cube.run.game.world.Fog
import cube.run.game.world.Scenery
import cube.run.game.world.WorldRunner
import cube.run.core.hsvInto
import kotlin.math.abs
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
 * hands everything else to its pieces:
 *  - [Track] + [TrackRenderer] — the lane-walk director and how it looks
 *  - [Player] — physics (platforms, bounces, jetpack flight), pose, skin, trail
 *  - [Bubble] — the shield; [PowerUps] — magnet / 2× / jetpack timers + HUD bars
 *  - [Difficulty] + [FireBoost] — speed / tier and the opening boost button
 *  - [WorldRunner] + [Scenery] — the worlds, their sky and roadside, the gates
 *  - [RunCamera] — the chase rig; [RunFx] — every sound / flash / burst
 *  - [GiftStage] / [Showcase] — the 3D stages the run-over and wardrobe screens use
 *
 * +1 per row (2× with the multiplier), near-miss bonus for shaving an
 * obstacle; coins and mystery boxes are handed to the session and banked at
 * game over.
 */
class CubeRun(session: GameSession, private val autoStart: Boolean = false) : Gdx3DGame(session) {

    private val tmpCol = Color()

    private val rnd = Random(System.nanoTime())
    private val obstacles = ObstacleFactory(rnd)
    private val track = Track(rnd, obstacles)
    private val trackArt = TrackRenderer(this)
    private val player = Player(this, rnd)
    private val bubble = Bubble(this)
    private val powerUps = PowerUps()
    private val difficulty = Difficulty()
    private val fire = FireBoost(this, difficulty)
    private val scenery = Scenery(this, rnd)
    private val worlds = WorldRunner(this, scenery, rnd)
    private val gift = GiftStage(this)
    private val showcase = Showcase(this, player, bubble)
    private val fx = RunFx(this, rnd)
    private lateinit var rig: RunCamera

    // ---- run state ----
    private var started = false
    private var dead = false
    private var spd = 4.5f
    private var dist = 0f
    private var rowsPassed = 0
    private var curTier = 0          // last tier reached (a chime marks each unlock)
    private var runT = 0f            // seconds since the run began (the start ease)
    private var introT = 0f          // seconds since launch (the menu shot's swoop in)
    private var introAtStart = 0f    // where the swoop was when the run began (the start eases on from there)
    private var skyBlend = 0f        // 1 → 0: the stage's sky fading back into the world's after a page closes
    private var bonus = Bonus.NONE   // the bonus world we are in
    private var kaleido = 0f         // eased 0..1: the Kaleidoscope's colour cycling + sway
    private var kaleidoHue = 0f
    private var coinsRunF = 0f       // coins this run, fractional (the Rich coins perk)
    private var jetGrace = 0f        // safe landing window after a jetpack flight
    private var jetBoost = 0f        // eased 0..1: how much of the jetpack's speed boost is on
    private val jetGlide = 1.4f      // the last seconds of a flight glide back down to the ground
    private val jetSpeedUp = 0.75f   // the jetpack's speed boost (+75%)

    // ---- death: let the crash animation play before the run-over screens ----
    private val deathAnimTime = 1.5f
    private var deathT = 0f
    private var gameOverShown = false

    // ---- style points: tap mid-air for an ascending combo (purely for flair) ----
    private var styleCombo = 0
    private var lastTapT = -9f       // for double-tap detection (bubble activation)

    // ---- goodies ----
    private var coinsRun = 0         // collected this run (banked by the session at game over)
    private var boxesRun = 0         // mystery boxes collected this run (opened on the run-over screens)
    private var coinStreak = 0       // consecutive pickups without a miss (the milestone chimes)
    private var coinPitch = 0        // rising coin pitch; resets after a short gap without a coin
    private var lastCoinT = -9f
    private val nearMissBonus = 2

    // smooth-control gesture state (positional steering within one continuous touch)
    private var smoothAnchorX = 0f
    private var smoothAnchorLane = 1
    private var smoothVAccum = 0f

    /** Obstacles and the cube's classic skin key off the current world's hue. */
    private fun worldHue() = worlds.hue

    override fun init() {
        Stage.reset()
        Lanes.reset(); Terrain.reset()
        setTerrain { z -> Terrain.y(z) }
        worlds.reset()
        scenery.init(worlds.world)
        bgTop.set(worlds.skyTop); bgBottom.set(worlds.skyBottom)
        player.init(worldHue(), time)
        bubble.init()
        scenery.spawnStartGate(hsvInto(tmpCol, worldHue() + 180f, 0.7f, 1f))
        rig = RunCamera(cam)
        cam.position.set(0f, 3.7f, 6.4f)
        cam.lookAt(0f, 1.0f, -8f)
        cam.update()
        session.setBubbles(Progress.bubbles)
        session.setWorld(worlds.world.name)
    }

    // --------------------------------------------------------------- events

    private fun live() = started && !dead && !session.isOver

    override fun paused(): Boolean = Stage.paused

    private fun start() {
        if (started || session.isOver) return
        started = true
        runT = 0f
        introAtStart = rig.intro
        scenery.release() // the start gate comes at you
        fx.launch(player.px, player.py, player.trailCol())
        player.squashForLaunch()
        fire.reset(); styleCombo = 0
        coinsRun = 0; coinsRunF = 0f; boxesRun = 0; coinStreak = 0
        if (Settings.devMode && Settings.testBoxes > 0) { boxesRun = Settings.testBoxes; session.setBoxes(boxesRun) } // dev: boxes to open
        // perks: a head start lights boost taps for you; portal luck opens portals sooner
        repeat(Progress.level(Progress.HEADSTART)) { Stage.boostRequests.incrementAndGet() }
        track.portalPool = when {
            Settings.testBonus >= 0 -> listOf(Settings.testBonus)
            Settings.devMode -> Bonus.all.map { it.id }
            else -> Bonus.unlocked(Scores.best("cuberun")).map { it.id }
        }
        track.portalEvery = if (Settings.devMode) 28 else 110 - 14 * Progress.level(Progress.PORTALS) // dev: portals galore too
        powerUps.reset(); jetGrace = 0f
        bubble.duration = Progress.BUBBLE.duration(Progress.bubbleLevel)
        difficulty.reset()
        curTier = difficulty.tier()
        track.tier = curTier
        track.reset(coinTrailChance = 0.2f, hue = worldHue())
        session.runStarted()
        fx.runStart(worldHue())
        rig.punch(0.8f)
    }

    private fun crash() {
        if (dead) return
        if (Progress.useRevive()) { secondWind(); return }
        dead = true
        player.setFlying(false)
        fx.crash(player.px, player.py, player.col)
        // NB: the run-over screens are deferred (see tick) so the crash animation is visible.
    }

    /** A second wind: the crash becomes a smash — the row shatters, a bubble goes up, the run goes on. */
    private fun secondWind() {
        val zone = -(spd * 2.5f) - 4f
        val ahead = track.rows.filter { it.z > zone && it.z < 1.2f && it.obs.isNotEmpty() }
        for (r in ahead) { fx.smash(r, emptyList()); r.obs.removeAll { it.type == ObType.SOLID } }
        bubble.duration = 3f
        bubble.activate(player.px, player.py)
        bubble.duration = Progress.BUBBLE.duration(Progress.bubbleLevel)
        rig.punch(1f)
        slowMo(0.2f, 0.5f)
        flash(Color.WHITE, 0.6f)
    }

    /** A collision with a row: fatal, unless the bubble is up — then it takes the hit. */
    private fun hit(row: Row) {
        if (bubble.active) smash(row) else crash()
    }

    /** Ran into the side of a platform: fatal, or the bubble hoists you onto it. */
    private fun sideHit(groundH: Float) {
        if (!bubble.active) { crash(); return }
        player.forceGround(groundH)
        bubble.pop(player.px, player.py)
        rig.punch(0.5f)
        fx.hoist()
    }

    /**
     * The bubble's one save: the row you hit shatters, everything in a short
     * breather zone ahead dissolves so you can recover, and the bubble pops.
     */
    private fun smash(row: Row) {
        val zone = -(spd * 2.2f) - 4f // ≈ 2 s of clean track
        val ahead = track.rows.filter { it !== row && it.z > zone && it.z < 0f && it.obs.isNotEmpty() }
        fx.smash(row, ahead)
        row.obs.removeAll { it.type == ObType.SOLID }
        for (r in ahead) r.obs.removeAll { it.type == ObType.SOLID }
        bubble.pop(player.px, player.py)
        rig.punch(0.6f)
        session.addScore(3)
    }

    private fun scoreRow(row: Row) {
        rowsPassed++
        val x2 = powerUps.mult.active
        session.addScore(if (x2) 2 else 1)
        fx.rowPassed(rowsPassed)
        if (row.minClear < 0.34f) { // shaved it — reward a close dodge with an air-rush
            session.addScore(if (x2) nearMissBonus * 2 else nearMissBonus)
            fx.nearMiss(player.px, player.py)
        }
        worlds.onRow(rowsPassed)
    }

    private fun collectCoin(coin: Coin, cz: Float) {
        coin.taken = true
        coinsRunF += Progress.coinValue * (if (bonus == Bonus.KALEIDO) 2f else 1f) // Rich coins perk; the Kaleidoscope pays double
        coinsRun = coinsRunF.toInt()
        coinStreak++
        session.setCoins(coinsRun)
        if (time - lastCoinT > 0.4f) coinPitch = 0 // the pitch climbs coin after coin and falls back as soon as the line breaks
        lastCoinT = time
        coinPitch++
        fx.coin(coin.x, coin.y, cz, coinPitch, trackArt.gold)
        if (coinStreak == 20 || coinStreak == 50 || coinStreak % 100 == 0) fx.coinMilestone(trackArt.gold)
    }

    private fun collectPickup(row: Row, cz: Float) {
        val kind = row.pickup
        row.pickup = Pickup.NONE
        when (kind) {
            Pickup.BUBBLE -> { // one more in the stash (double-tap to use it)
                Progress.addBubble(1)
                session.setBubbles(Progress.bubbles)
                fx.pickup(hsvInto(tmpCol, 190f, 0.5f, 1f), row.pickupX, cz)
            }
            Pickup.BOX -> {
                boxesRun++
                session.setBoxes(boxesRun)
                fx.boxPickup(trackArt.colorOf(Pickup.BOX), trackArt.colorOf(Pickup.NONE), row.pickupX, cz)
            }
            Pickup.MAGNET -> { powerUps.magnet.start(Progress.MAGNET.duration(Progress.magnetLevel)); fx.pickup(trackArt.colorOf(kind), row.pickupX, cz) }
            Pickup.MULT -> { powerUps.mult.start(Progress.MULT.duration(Progress.multLevel)); fx.pickup(trackArt.colorOf(kind), row.pickupX, cz) }
            Pickup.JET -> {
                val dur = Progress.JET.duration(Progress.jetLevel)
                powerUps.jet.start(dur)
                player.setFlying(true)
                track.airCoins = true
                aimJetCoins(dur, difficulty.speed() * (1f + jetSpeedUp)) // where the flight will end, at boosted speed
                track.liftCoins()
                fx.pickup(trackArt.colorOf(kind), row.pickupX, cz)
                rig.punch(1f)
            }
        }
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
        fx.jetEnd()
    }

    /** Try to spend a stocked bubble (double tap). Returns true when one went up. */
    private fun tryBubble(): Boolean {
        if (!live() || bubble.active) return false
        if (!Progress.useBubble()) { fx.emptyStock(); return false }
        session.setBubbles(Progress.bubbles)
        bubble.activate(player.px, player.py)
        rig.punch(1f)
        return true
    }

    // ---------------------------------------------------------------- input

    override fun onDown(x: Float, y: Float) {
        if (gift.active || showcase.active || Stage.paused) return
        start()
        smoothAnchorX = x; smoothAnchorLane = player.lane; smoothVAccum = 0f
    }

    override fun onDrag(x: Float, y: Float, dx: Float, dy: Float) {
        if (!Settings.smoothControl || !live()) return
        val laneTravel = sw * (0.32f - 0.20f * Settings.smoothSensitivity) // finger px per lane
        val target = (smoothAnchorLane + ((x - smoothAnchorX) / laneTravel).roundToInt()).coerceIn(0, 2)
        player.moveToLane(target)
        val vStep = sw * (0.16f - 0.08f * Settings.smoothSensitivity)
        if (abs(dy) > abs(dx)) smoothVAccum += dy else smoothVAccum *= 0.6f
        if (smoothVAccum <= -vStep) { smoothVAccum = 0f; player.jump() }
        else if (smoothVAccum >= vStep) { smoothVAccum = 0f; player.downAction() }
    }

    override fun onTap(x: Float, y: Float) {
        if (!live()) return
        if (time - lastTapT < 0.38f) { // a quick double tap pops a bubble shield (anywhere, any time)
            lastTapT = -9f
            if (tryBubble()) return
        } else {
            lastTapT = time
        }
        if (player.air) { styleCombo++; fx.styleTap(styleCombo, player.px, player.py) } // flair only — never scored
    }

    override fun smoothSwipeEnabled(): Boolean = Settings.smoothControl

    override fun onSwipe(dir: Int) {
        if (session.isOver || dead) return
        if (!started) start()
        when (dir) {
            LEFT, RIGHT -> {
                val d = if (dir == LEFT) -1 else 1
                if (player.lane + d in 0..2) player.moveToLane(player.lane + d)
                else player.bonk(d)
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
        if (player.hover) return 0f
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
        if (Stage.mode == Stage.SKINS || Stage.mode == Stage.RESULT || Stage.mode == Stage.SHOP) {
            if (!showcase.active) showcase.enter(bgTop, bgBottom, worldHue())
            showcase.update(dt, time, worldHue())
            showcase.tintSky(bgTop, bgBottom)
            showcase.aim(rig)
            return
        } else if (showcase.active) { showcase.exit(bgTop, bgBottom); introT = 0f; skyBlend = 1f } // back to the menu: the swoop again

        if (!started && autoStart && time > 0.05f) start() // RESTART: straight into the run
        if (Stage.endRun) { Stage.endRun = false; if (live()) crash() } // dev tool: END RUN from the pause card

        if (started && !dead) {
            jetBoost += ((if (player.flying) 1f else 0f) - jetBoost) * min(1f, dt * 2f)
            runT += dt
            val ease = min(1f, runT / 1.5f).let { it * it * it * (it * (it * 6f - 15f) + 10f) } // the start: the road winds up, the camera drops in
            rig.intro = introAtStart + (1f - introAtStart) * ease
            spd = 4.5f + (difficulty.speed() * (1f + jetSpeedUp * jetBoost) - 4.5f) * ease
            if (fire.tick(dt, player.px, player.py) > 0) rig.punch(0.45f)
            difficulty.ramp(dt)
        } else if (!started) {
            spd = 4.5f // ambient pre-start scroll
            introT += dt
            val k = min(1f, introT / 1.8f).let { it * it * it * (it * (it * 6f - 15f) + 10f) }
            rig.intro = -2.2f + 2.2f * k // the menu shot swoops in from high and far back and settles
        } else {
            spd = max(0f, spd - spd * 2.4f * dt) // death: world glides to a stop
            deathT = min(deathT + dt, 2.5f)
            if (!gameOverShown && deathT >= deathAnimTime) { gameOverShown = true; session.gameOver() }
        }
        val mv = spd * dt
        dist += mv
        Lanes.tick(dt)
        Terrain.scroll(mv, dt)
        // the Kaleidoscope: the sky and the road never hold a colour; the camera sways
        kaleido += ((if (bonus == Bonus.KALEIDO) 1f else 0f) - kaleido) * min(1f, dt * 1.5f)
        if (kaleido > 0.001f) {
            kaleidoHue += dt * 70f
            rig.roll = 7f * kaleido * sin(time * 1.1f)
        } else rig.roll = 0f
        rig.wide += ((if (Lanes.count > 3) 1f else 0f) - rig.wide) * min(1f, dt * 2f)
        if (started && !dead && !Settings.devMode) {
            val t = difficulty.tier()
            if (t > curTier) { curTier = t; fx.tierUp(t, worldHue()); rig.punch(0.5f) }
        }
        track.tier = curTier
        if (jetGrace > 0f) jetGrace = max(0f, jetGrace - dt)

        // ---- the world: sky cross-fade, roadside, the next gate
        worlds.tick(dt)
        bgTop.set(worlds.skyTop); bgBottom.set(worlds.skyBottom)
        if (skyBlend > 0f) {
            skyBlend = max(0f, skyBlend - dt * 2.2f)
            bgTop.lerp(showcase.stageTop, skyBlend); bgBottom.lerp(showcase.stageBottom, skyBlend)
        }
        if (kaleido > 0.001f) {
            bgTop.lerp(hsvInto(tmpCol, kaleidoHue, 0.85f, 1f), kaleido * 0.85f)
            bgBottom.lerp(hsvInto(tmpCol, kaleidoHue + 120f, 0.9f, 0.55f), kaleido * 0.85f)
        }
        when (scenery.scroll(mv)) {
            Scenery.PASSED_WORLD -> worlds.gatePassed()?.let { fx.worldGate(worlds.gateColor()); rig.punch(0.7f); session.setWorld(it.name) }
            Scenery.PASSED_START -> { fx.startGate(player.trailCol()); rig.punch(0.9f) }
        }
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
            player.hover = bonus == Bonus.FLOAT
            val gh = if (player.flying) 0f else groundAt(player.px)
            when (player.update(dt, mv, time, worldHue(), trail = started, groundH = gh, stream = spd * 0.55f)) {
                Player.EV_LANDED -> if (styleCombo > 0) { fx.styleLand(styleCombo, player.px, player.py); styleCombo = 0 }
                Player.EV_SIDE_HIT -> if (started) sideHit(gh)
            }
        }
        bubble.update(dt, time, player.px + player.nudge, player.py + Terrain.y(0f))

        // ---- obstacle rows: move, collide, score; coins: magnet + collect; pickups
        track.scroll(mv, time, dt)
        collide(dt)

        rig.chase(dt, player.px, player.py, player.ground, deathT, spd)
    }

    /** One pass over the rows: obstacles (box overlap), pads, pickups, coins, scoring. */
    private fun collide(dt: Float) {
        val cubeBottom = player.cubeBottom
        val headY = player.headY
        val px = player.px; val py = player.py
        val immune = player.flying || jetGrace > 0f
        val pull = if (powerUps.magnet.active) 7.5f else 0f // only a MAGNET pulls; otherwise you have to touch them
        for (row in track.rows) {
            if (row.portal >= 0 && started && !dead && row.z > 0f) crossPortal(row)
            if (started && !dead && abs(row.z) < 0.95f) {
                for (ob in row.obs) {
                    if (ob.type == ObType.PAD) { // a springboard: harmless, launches you when you run onto it
                        if (!ob.used && !player.air && !player.flying && abs(row.z) < 0.75f && abs(px - ob.x) < 0.85f) {
                            ob.used = true
                            player.launch(12.5f)
                            fx.bounce(px, py, ob.col)
                            rig.punch(0.7f)
                        }
                        continue
                    }
                    if (immune || ob.type != ObType.SOLID) continue
                    val lat = ob.lateral(px)
                    if (lat >= 0f) { row.minClear = min(row.minClear, lat); continue } // beside it: the lateral gap is the clearance
                    val vert = max(cubeBottom - ob.top, ob.bottom - headY)              // over / under it: the vertical gap (positive = clear)
                    row.minClear = min(row.minClear, vert)
                    if (abs(row.z) < 0.82f && vert < -0.02f) { hit(row); break }
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
                if (started && !dead && !row.idle) scoreRow(row)
            }
        }
    }

    /** Through a portal: the world changes shape (or back). */
    private fun crossPortal(row: Row) {
        val oldCount = Lanes.count
        val id = track.crossPortal(row)
        bonus = id
        player.remapLane(oldCount, Lanes.count)
        if (id == Bonus.NONE) player.hover = false
        val col = hsvInto(tmpCol, if (id == Bonus.NONE) 200f else Bonus.get(id).hue, 0.5f, 1f)
        fx.worldGate(col)
        flash(Color.WHITE, 0.7f)
        slowMo(0.35f, 0.4f)
        rig.punch(1f)
        Terrain.set(id == Bonus.HILLS)
        session.setBonus(id)
    }

    // ------------------------------------------------------------- rendering

    override fun renderHud(shapes: ShapeRenderer, w: Float, h: Float) {
        if (gift.active || showcase.active || dead || Stage.paused) return
        powerUps.drawBars(shapes, w, h, time, if (bubble.active) bubble.timer else null)
    }

    override fun renderWorldBatched() {
        if (gift.active) { fogColor.set(bgBottom); syncFog(); gift.render(time); return }
        if (showcase.active) { fogColor.set(bgBottom); syncFog(); showcase.render(time); return }
        // haze target ≈ the sky gradient at the horizon, so the far track end and
        // freshly spawned rows dissolve into the background instead of popping in
        fogColor.set(bgBottom).lerp(bgTop, worlds.fogMix)
        syncFog()
        scenery.renderRoad()
        trackArt.render(track, time, kaleido, kaleidoHue)
        val wind = if (dead) 0f else ((spd - 13f) / 15f).coerceIn(0f, 1f)
        scenery.render(if (player.flying) 1f else wind, time)
    }

    override fun renderWorldShapes(shapes: ShapeRenderer) {
        if (gift.active) gift.renderShapes(shapes, time)
        else if (showcase.active) showcase.renderShapes(shapes, time)
    }

    override fun renderWorld(batch: ModelBatch, env: Environment) {
        if (gift.active) return
        if (showcase.active) { player.render(batch, env); return }
        if (!dead) player.render(batch, env, Terrain.y(0f))
    }

    override fun renderBlended() {
        if (gift.active) { gift.renderBlended(cam, time); return }
        if (showcase.active) { showcase.renderBlended(cam, time); return }
        if (!dead) bubble.render(cam, time)
        for (r in track.rows) { // floating bubble pickups: little soap bubbles
            if (r.pickup != Pickup.BUBBLE) continue
            val cz = r.z + Row.PICKUP_DZ
            if (cz < -Fog.end) continue
            val s = 1.25f + 0.06f * sin(time * 5f + cz)
            bubbles.draw(cam, r.pickupX, 0.95f + 0.12f * sin(time * 3f + cz), cz, s, s, s, time * 50f, time, 170f, 270f, 0.6f, 2.4f, 0.12f, 1f - Fog.at(cz), BubbleSkins.IRIS)
        }
    }
}
