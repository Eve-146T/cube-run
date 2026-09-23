package cube.run.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import cube.run.BuildConfig
import cube.run.core.Gdx3DGame
import cube.run.core.GameSession
import cube.run.core.Stage
import cube.run.core.SoundFx
import cube.run.core.Haptics
import cube.run.data.Skins
import cube.run.game.track.Ob
import com.badlogic.gdx.math.Vector3
import cube.run.data.Bonus
import cube.run.data.BubbleSkins
import cube.run.data.Progress
import cube.run.data.Scores
import cube.run.data.Settings
import cube.run.game.stage.GiftStage
import cube.run.game.stage.Showcase
import cube.run.game.track.Coin
import cube.run.game.track.Debris
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
 *  - [Jackpot] — the Gambler's jackpot show, which holds the run while it plays
 *  - [GiftStage] / [Showcase] — the 3D stages the run-over and wardrobe screens use
 *
 * +1 per row (2× with the multiplier), near-miss bonus for shaving an
 * obstacle; coins and mystery boxes are handed to the session and banked at
 * game over.
 */
class CubeRun(session: GameSession, private val autoStart: Boolean = false, private val idleBotStart: Boolean = false, launchOpening: Boolean = false, openingClock: cube.run.intro.OpeningClock? = null,
              private val firstWorld: Int? = null) : Gdx3DGame(session) {

    private val opening = CubeOpening(launchOpening, openingClock)
    override val hasLaunchOpening = launchOpening
    var onOpeningProgress: ((Float) -> Unit)? = null
    fun finishOpening() { opening.finish() }

    private val tmpCol = Color()
    private val phasePosition = Vector3()

    private val rnd = Random(System.nanoTime())
    private val obstacles = ObstacleFactory(rnd)
    private val track = Track(rnd, obstacles)
    private val trackArt = TrackRenderer(this)
    private val debris = Debris(this)
    private val player = Player(this, rnd)
    private val bubble = Bubble(this)
    private var shownBubbleCooldown = 0
    private val powerUps = PowerUps()
    private val redPill = RedPill()
    private val difficulty = Difficulty()
    private val fire = FireBoost(this, difficulty)
    private val scenery = Scenery(this, rnd)
    private val worlds = WorldRunner(this, scenery, rnd)
    private val gift = GiftStage(this)
    private val showcase = Showcase(this, player, bubble)
    private val fx = RunFx(this, rnd)
    private val jackpot = Jackpot(this, rnd)
    private lateinit var rig: RunCamera
    private var shopMenuIntroT = 0f

    // ---- run state ----
    private val idlePilot = IdlePilot()
    private var initialInteraction = 0
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
    private var coinsRunF = 0.0     // preserve fractional Rich coins / Gold rewards
    private var lottery = Lottery(rnd)
    private var runBubble = BubbleSkins.get(0)
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
    private var runSkin = Skins.get(0)
    private var phaseUsed = false
    private var phasedObstacle: Ob? = null
    private var groundObstacle: Ob? = null
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
    private var sideBounces = 0
    private var smoothWall = 0

    /**
     * Count the same edge contact that plays the visible bonk. Classic input
     * already emits once per touch; smooth input calls this only on wall entry.
     * Do not debounce using simulation time: Android can queue several separate
     * flicks before one GL frame, and slow motion must not discard real touches.
     */
    private fun sideBounce(dir: Int) {
        player.bonk(dir)
        sideBounces++
        Progress.recordRunProgress(session.score, sideBounces)
    }

    /** Obstacles and the cube's classic skin key off the current world's hue. */
    private fun worldHue() = worlds.hue

    override fun init() {
        Stage.reset()
        initialInteraction = Stage.interactions.get()
        Lanes.reset(); Terrain.reset()
        setTerrain { z -> Terrain.y(z) }
        worlds.reset(firstWorld)
        scenery.init(worlds.world)
        bgTop.set(worlds.skyTop); bgBottom.set(worlds.skyBottom)
        player.init(worldHue(), time)
        bubble.init()
        scenery.spawnStartGate(hsvInto(tmpCol, worldHue() + 180f, 0.7f, 1f))
        rig = RunCamera(cam)
        if (hasLaunchOpening) { introT = 1.8f; rig.intro = 0f }
        cam.position.set(0f, 3.7f, 6.4f)
        cam.lookAt(0f, 1.0f, -8f)
        cam.update()
        if (opening.active) {
            player.update(0f, 0f, time, worldHue(), trail = false, groundH = 0f)
            opening.pose(player, cam, worldHue())
            bgTop.set(CubeOpening.INK); bgBottom.set(CubeOpening.INK)
        }
        session.setBubbles(Progress.bubbles)
        session.setWorld(worlds.world.name)
    }

    // --------------------------------------------------------------- events

    private fun live() = started && !dead && !session.isOver

    override fun paused(): Boolean {
        if (Stage.paused) { player.clearJumpInput(); idlePilot.stop() }
        return Stage.paused
    }

    private fun start() {
        if (started || session.isOver) return
        finishRendererStartup()
        opening.finish()
        started = true
        runSkin = Skins.get(Progress.skin)
        runBubble = BubbleSkins.get(Progress.bubbleSkin)
        player.zappyEnabled = Skins.Ability.ZAPPY in runSkin.abilities
        player.floaty = Skins.Ability.FLOATY in runSkin.abilities
        player.doubleJumpEnabled = false
        trackArt.coalCoins = Skins.Ability.COAL in runSkin.abilities
        lottery = Lottery(rnd, if (Settings.devMode) Lottery.DEV_COIN_CHANCE else Lottery.COIN_CHANCE)
        phaseUsed = false; phasedObstacle = null; lastTapT = -9f
        runT = 0f
        sideBounces = 0; smoothWall = 0
        introAtStart = rig.intro
        scenery.release() // the start gate comes at you
        fx.launch(player.px, player.py, player.trailCol())
        player.squashForLaunch()
        fire.reset(); styleCombo = 0
        coinsRun = 0; coinsRunF = 0.0; boxesRun = 0; coinStreak = 0
        if (Settings.devMode && Settings.testBoxes > 0) { boxesRun = Settings.testBoxes; session.setBoxes(boxesRun) } // dev: boxes to open
        track.portalPool = when {
            Settings.testBonus >= 0 -> listOf(Settings.testBonus)
            Settings.devMode -> Bonus.all.map { it.id }
            else -> Bonus.unlocked(Scores.best("cuberun")).map { it.id }
        }
        track.portalEvery = if (Settings.devMode) 28 else 110 - 14 * Progress.level(Progress.PORTALS) // dev: portals galore too
        powerUps.reset(); redPill.reset(); jetGrace = 0f
        if (BuildConfig.DEBUG && BuildConfig.JACKPOT_TEST_WORLD) {
            track.portalPool = emptyList()
            powerUps.magnet.start(3600f)
        }
        bubble.reset(); shownBubbleCooldown = 0; session.setBubbleCooldown(0)
        bubble.cooldownDuration = 5f * runSkin.bubbleCooldownMultiplier
        bubble.duration = bubbleDuration()
        difficulty.reset()
        curTier = difficulty.tier()
        track.tier = curTier
        val oldCount = Lanes.count
        track.reset(coinTrailChance = 0.2f, hue = worldHue(), initialBonus = if (Settings.devMode) Settings.testBonusNow else Bonus.NONE)
        if (!track.isPillTest && Settings.devMode && Settings.testBonusNow >= 0) { // debug: begin inside a bonus world
            bonus = Settings.testBonusNow
            player.remapLane(oldCount, Lanes.count)
            Terrain.set(bonus == Bonus.HILLS)
            session.setBonus(bonus)
        }
        if (Progress.safeStartSeconds > 0f) { // the Safe start perk: a bubble is already up
            bubble.duration = Progress.safeStartSeconds * bubbleDurationMultiplier()
            bubble.activate(player.px, player.py, quiet = true)
            bubble.duration = bubbleDuration()
        }
        session.runStarted()
        if (bonus in 0..3) session.setBonus(bonus)
        session.laneChanged(player.lane, Lanes.count)
        refreshJumpAbility()
        fx.runStart(worldHue())
        rig.punch(0.8f)
    }

    // Instrumentation installs this only for protected performance runs. Release
    // builds always use the normal fatal-collision path, even if reflection sets it.
    private var testCrashObserver: (() -> Unit)? = null
    // Observation only: counts contacts even when a legitimately collected shield saves them.
    internal var pilotContacts = 0
        private set

    private fun crash() {
        if (dead) return
        if (BuildConfig.DEBUG && testCrashObserver != null) { testCrashObserver!!.invoke(); return }
        if (Progress.useRevive()) { secondWind(); return }
        session.runCrashed(runT)
        dead = true
        player.setFlying(false)
        fx.crash(player.px, player.py, player.col)
        // NB: the run-over screens are deferred (see tick) so the crash animation is visible.
    }

    /** A second wind: the crash becomes a smash — the row shatters, a bubble goes up, the run goes on. */
    private fun secondWind() {
        val zone = -(spd * 2.5f) - 4f
        for (r in track.rows) {
            if (r.z > zone && r.z < 1.2f && r.obs.isNotEmpty()) shatter(r, impact = r.z > -8f)
        }
        fx.smashFeedback()
        bubble.duration = 3f * bubbleDurationMultiplier()
        bubble.activate(player.px, player.py)
        bubble.duration = bubbleDuration()
        rig.punch(1f)
        slowMo(0.2f, 0.5f)
        flash(Color.WHITE, 0.6f)
    }

    private fun phase(ob: Ob): Boolean {
        if (phaseUsed || Skins.Ability.PHASE !in runSkin.abilities) return false
        phaseUsed = true
        phasedObstacle = ob
        SoundFx.play("whoosh", rate = 1.4f)
        Haptics.success()
        flash(Color.CYAN, 0.25f)
        burst3d(phasePosition.set(player.px, player.py, 0f), Color.CYAN, n = 20, speed = 3f, size = 0.12f, life = 0.6f)
        return true
    }

    /** A collision with a row: fatal, unless the bubble is up — then it takes the hit. */
    private fun hit(row: Row) {
        if (bubble.active) smash(row) else crash()
    }

    /** Ran into the side of a platform: fatal, or the bubble hoists you onto it. */
    private fun sideHit(groundH: Float) {
        if (idlePilot.active) pilotContacts++
        if (!bubble.active) {
            if (groundObstacle?.let { phase(it) } == true) return
            crash(); return
        }
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
        shatter(row, impact = true)
        for (r in track.rows) {
            if (r !== row && r.z > zone && r.z < 0f && r.obs.isNotEmpty()) shatter(r, impact = false)
        }
        fx.smashFeedback()
        bubble.pop(player.px, player.py)
        rig.punch(0.6f)
        session.addScore(3)
    }

    private fun shatter(row: Row, impact: Boolean) {
        fx.smashRow(row, impact)
        for (ob in row.obs) if (ob.type == ObType.SOLID) debris.smash(ob, row.z)
        row.obs.removeAll { it.type == ObType.SOLID }
    }

    private fun scoreRow(row: Row) {
        rowsPassed++
        val x2 = powerUps.mult.active
        session.addScore(if (x2) 2 else 1)
        fx.rowPassed()
        if (row.minClear < 0.34f) { // shaved it — reward a close dodge with an air-rush
            session.nearMiss()
            session.addScore(if (x2) nearMissBonus * 2 else nearMissBonus)
            fx.nearMiss(player.px, player.py)
        }
        if (!track.isPillTest) worlds.onRow(rowsPassed)
    }

    private fun collectCoin(coin: Coin, cz: Float) {
        if (coin.taken || coin.missed) return
        coin.taken = true
        session.coinPickedUp()
        val value = Progress.coinValue * (if (bonus == Bonus.KALEIDO) 2f else 1f)
        if (Skins.Ability.COAL in runSkin.abilities) {
            session.coalCollected()
            SoundFx.play("tap", rate = .75f, vol = .35f)
            burst3d(phasePosition.set(coin.x, coin.y, cz), trackArt.coal, n = 6, speed = 2.5f, size = .12f, life = .35f)
            return
        }
        if (Skins.Ability.LOTTERY in runSkin.abilities) {
            awardJackpot(lottery.collectCoin(value))
            SoundFx.play("tap", rate = 1.25f, vol = .25f)
            burst3d(phasePosition.set(coin.x, coin.y, cz), Color.RED, n = 3, speed = 2f, size = .07f, life = .2f)
            return
        }
        val preciseValue = kotlin.math.round(value.toDouble() * runSkin.coinMultiplier * 1_000_000.0) / 1_000_000.0
        coinsRunF = (coinsRunF + preciseValue).coerceAtMost(Int.MAX_VALUE.toDouble())
        coinsRun = (coinsRunF + .0000001).toInt()
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
        if (kind != Pickup.NONE && kind != Pickup.BOX && Pickup.shardType(kind) < 0) {
            Progress.recordPowerup()
            session.powerupPickedUp()
        }
        when (kind) {
            Pickup.SHARD_EMBER, Pickup.SHARD_FROST, Pickup.SHARD_VOID -> {
                session.addShard(Pickup.shardType(kind))
                fx.coin(row.pickupX, .85f, cz, 1, trackArt.colorOf(kind))
            }
            Pickup.BUBBLE -> { // one more in the stash (double-tap to use it)
                Progress.addBubble(1)
                session.setBubbles(Progress.bubbles)
                fx.pickup(hsvInto(tmpCol, 190f, 0.5f, 1f), row.pickupX, cz)
            }
            Pickup.BOX -> {
                session.boxCollected()
                if (Skins.Ability.LOTTERY in runSkin.abilities) {
                    val won = lottery.collectBox()
                    awardJackpot(won)
                    if (won == 0) {
                        SoundFx.play("tap", rate = .7f, vol = .5f)
                        burst3d(phasePosition.set(row.pickupX, .8f, cz), Color.RED, n = 10, speed = 3f, size = .1f, life = .4f)
                    }
                } else {
                    boxesRun++
                    session.setBoxes(boxesRun)
                    fx.boxPickup(trackArt.colorOf(Pickup.BOX), trackArt.colorOf(Pickup.NONE), row.pickupX, cz)
                }
            }
            Pickup.MAGNET -> { powerUps.magnet.start(Progress.MAGNET.duration(Progress.magnetLevel) * runSkin.powerupDurationMultiplier); fx.pickup(trackArt.colorOf(kind), row.pickupX, cz) }
            Pickup.MULT -> { powerUps.mult.start(Progress.MULT.duration(Progress.multLevel) * runSkin.powerupDurationMultiplier); fx.pickup(trackArt.colorOf(kind), row.pickupX, cz) }
            Pickup.RED_PILL -> {
                redPill.collect(RedPill.DURATION * runSkin.powerupDurationMultiplier)
                fx.pickup(trackArt.colorOf(kind), row.pickupX, cz)
                rig.punch(.5f)
            }
            Pickup.JET -> {
                val dur = Progress.JET.duration(Progress.jetLevel) * runSkin.powerupDurationMultiplier
                powerUps.jet.start(dur)
                player.setFlying(true)
                track.airCoins = true
                aimJetCoins(dur, difficulty.speed() * runSkin.speedMultiplier * (1f + jetSpeedUp)) // where the flight will end, at boosted speed
                track.liftCoins()
                fx.pickup(trackArt.colorOf(kind), row.pickupX, cz)
                rig.punch(1f)
            }
        }
    }

    /** The win is the run's at once; the HUD only shows it once the show has counted it in. */
    private fun awardJackpot(amount: Int) {
        if (amount <= 0) return
        val before = coinsRun
        coinsRunF = (coinsRunF + amount).coerceAtMost(Int.MAX_VALUE.toDouble())
        coinsRun = coinsRunF.toInt()
        val won = coinsRun - before
        if (won <= 0) return
        val first = !jackpot.active
        jackpot.start(won, player.px, player.py + Terrain.y(0f), cam, bgTop, bgBottom)
        if (first) {
            // The show's camera stands behind the cube: whatever the run has already passed would block it.
            // The hit's flash covers them going.
            scenery.clearPassedGates()
            for (r in track.rows) if (r.z > 0.9f) r.obs.removeAll { it.type == ObType.SOLID } // platforms may still carry the cube
            session.jackpotWon(won)
        }
    }

    /**
     * The run holds while the jackpot plays: nothing scrolls, no timer runs,
     * no input lands. Only the shockwave shatters the road ahead of it.
     */
    private fun tickJackpot(dt: Float) {
        jackpot.update(dt)
        val front = jackpot.waveZ
        for (r in track.rows) {
            if (r.z > front && r.z < 1.2f && r.obs.any { it.type == ObType.SOLID }) shatter(r, impact = r.z > -16f)
        }
        debris.update(dt, 0f)
        if (jackpot.takeBanked()) session.setCoins(coinsRun)
        if (!jackpot.active) { session.setCoins(coinsRun); return }
        jackpot.tintSky(bgTop, bgBottom)
        jackpot.aimCamera(cam)
    }

    private fun bubbleDurationMultiplier() = runSkin.powerupDurationMultiplier * runBubble.durationMultiplier
    private fun bubbleDuration() = Progress.BUBBLE.duration(Progress.bubbleLevel) * bubbleDurationMultiplier()
    private fun refreshJumpAbility() {
        player.doubleJumpEnabled = live() && bubble.active && Skins.Ability.DOUBLE_JUMP in runBubble.abilities
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
        if (!live() || !bubble.ready) return false
        if (!Progress.useBubble(runSkin.bubbleSaveChance)) { fx.emptyStock(); return false }
        session.setBubbles(Progress.bubbles)
        bubble.activate(player.px, player.py)
        refreshJumpAbility()
        rig.punch(1f)
        return true
    }

    // ---------------------------------------------------------------- input

    override fun onDown(x: Float, y: Float) {
        idlePilot.stop()
        if (jackpot.active || gift.active || showcase.active || Stage.paused) return
        smoothAnchorX = x; smoothAnchorLane = player.lane; smoothVAccum = 0f
        smoothWall = 0
    }

    override fun onDrag(x: Float, y: Float, dx: Float, dy: Float) {
        if (!Settings.smoothControl || !live() || jackpot.active || Stage.paused || Stage.mode != Stage.NONE) return
        refreshJumpAbility()
        val laneTravel = sw * (0.32f - 0.20f * Settings.smoothSensitivity) // finger px per lane
        val rawTarget = smoothAnchorLane + ((x - smoothAnchorX) / laneTravel).roundToInt()
        val target = rawTarget.coerceIn(0, Lanes.last)
        val fromLane = player.lane
        if (player.moveToLane(target)) session.userLaneSwipe(fromLane, target)
        val wall = when { rawTarget < 0 -> -1; rawTarget > Lanes.last -> 1; else -> 0 }
        if (wall != 0 && wall != smoothWall) sideBounce(wall)
        smoothWall = wall
        val vStep = sw * (0.16f - 0.08f * Settings.smoothSensitivity)
        if (abs(dy) > abs(dx)) smoothVAccum += dy else smoothVAccum *= 0.6f
        if (smoothVAccum <= -vStep) { smoothVAccum = 0f; player.jump() }
        else if (smoothVAccum >= vStep) { smoothVAccum = 0f; player.downAction() }
    }

    override fun onTap(x: Float, y: Float) {
        if (jackpot.active || Stage.paused || Stage.mode != Stage.NONE || gift.active || showcase.active) return
        if (!started) { start(); return }
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
        if (session.isOver || dead || jackpot.active || Stage.paused || gift.active || showcase.active) return
        if (!started || Stage.mode != Stage.NONE) { return }
        refreshJumpAbility()
        when (dir) {
            LEFT, RIGHT -> {
                val d = if (dir == LEFT) -1 else 1
                if (player.lane + d in 0..Lanes.last) {
                    val fromLane = player.lane
                    if (player.moveToLane(fromLane + d)) session.userLaneSwipe(fromLane, fromLane + d)
                }
                else sideBounce(d)
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
        groundObstacle = null
        for (row in track.rows) {
            if (row.z < 0f || row.z > 6.6f) continue
            for (ob in row.obs) {
                if (ob === phasedObstacle || ob.type != ObType.PLAT || abs(px - ob.x) > ob.halfW + 0.3f) continue
                val local = row.z // how far the front has passed the player
                if (local > ob.sz) continue
                val top = ob.clear
                val here = if (ob.ramp > 0f && local < ob.ramp) top * (local / ob.ramp) else top
                if (here > h) { h = here; groundObstacle = ob }
            }
        }
        return h
    }

    override fun tickOpening() {
        opening.tick(0f)
        opening.pose(player, cam, worldHue())
        alignOpeningTime(opening.motionSeconds)
    }

    override fun tick(dt: Float) {
        // Navigation can become available before the intro finishes. Its camera
        // must not be applied again when returning from a wardrobe/shop preview.
        if (Stage.mode != Stage.NONE) opening.finish()
        val wasOpening = opening.active
        opening.tick(dt)
        if (wasOpening) alignOpeningTime(opening.motionSeconds)
        onOpeningProgress?.invoke(opening.uiAmount)
        if (!opening.active) onOpeningProgress = null
        if (idlePilot.ready(dt, !started && Stage.homeScreen && Stage.mode == Stage.NONE && !session.isOver)) {
            start()
            idlePilot.start(time)
        }
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
            if (!showcase.active) {
                if (Stage.mode == Stage.SHOP) shopMenuIntroT = introT
                showcase.enter(bgTop, bgBottom, worldHue())
            }
            showcase.update(dt, time, worldHue())
            showcase.tintSky(bgTop, bgBottom)
            showcase.aim(rig)
            return
        } else if (showcase.active) {
            showcase.exit(bgTop, bgBottom)
            introT = if (showcase.shop) shopMenuIntroT else 0f
            // The shop's reverse transition already restored the menu sky. Replaying the older
            // showcase fade here jumps back to a dark sky and flashes brightly in Sunset Dunes.
            skyBlend = if (showcase.shop) 0f else 1f
        }

        if (!started && autoStart && time > 0.05f) {
            start()
            if (idleBotStart && Settings.devMode && Stage.interactions.get() == initialInteraction) idlePilot.start(time)
        }
        // Player/track still describe the start of this simulation slice.
        if (idlePilot.active && live()) idlePilot.drive(time - dt, spd, timeScale, track,
            player.pilotBody(powerUps.jet.left).apply { landingGrace = jetGrace },
            if (runT >= 1.5f) cube.run.bot.JetMotion(difficulty.speed() * runSkin.speedMultiplier,
                jetBoost, if (player.flying) powerUps.jet.left else 0f) else null, ::onSwipe)
        if (jackpot.active) { tickJackpot(dt); if (jackpot.active) return }
        if (Stage.endRun) { Stage.endRun = false; if (live()) crash() } // dev tool: END RUN from the pause card

        if (started && !dead) {
            jetBoost += ((if (player.flying) 1f else 0f) - jetBoost) * min(1f, dt * 2f)
            runT += dt
            session.runSeconds(runT.toInt())
            val ease = min(1f, runT / 1.5f).let { it * it * it * (it * (it * 6f - 15f) + 10f) } // the start: the road winds up, the camera drops in
            rig.intro = introAtStart + (1f - introAtStart) * ease
            spd = 4.5f + (difficulty.speed() * runSkin.speedMultiplier * (1f + jetSpeedUp * jetBoost) - 4.5f) * ease
            if (fire.tick(dt, player.px, player.py) > 0) rig.punch(0.45f)
            difficulty.ramp(dt)
        } else if (!started) {
            spd = 0f // nothing moves before the run: the cube waits at the line
            player.idle(dt)
            introT = if (opening.active) 1.8f else introT + dt
            val k = min(1f, introT / 1.8f).let { it * it * it * (it * (it * 6f - 15f) + 10f) }
            rig.intro = -2.2f + 2.2f * k // the menu shot swoops in from high and far back and settles
        } else {
            spd = max(0f, spd - spd * 2.4f * dt) // death: world glides to a stop
            deathT = min(deathT + dt, 2.5f)
            if (!gameOverShown && deathT >= deathAnimTime) { gameOverShown = true; session.gameOver() }
        }
        val mv = spd * dt
        dist += mv
        if (started && !dead) session.distanceCovered(dist.toInt())
        Lanes.tick(dt)
        track.alignLaneSpacing()
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
        if (live()) track.spawn(mv, worldHue(), session.score, dt)

        redPill.tick(dt, started && !dead)
        bgTop.lerp(Color.BLACK, redPill.blend)
        bgBottom.lerp(Color.BLACK, redPill.blend)
        if (opening.active) {
            bgTop.lerp(CubeOpening.INK, 1f-opening.worldAmount)
            bgBottom.lerp(CubeOpening.INK, 1f-opening.worldAmount)
        }

        // ---- timed power-ups
        if (started && !dead) {
            powerUps.magnet.tick(dt)
            powerUps.mult.tick(dt)
            if (bubble.active && powerUps.magnet.active && powerUps.mult.active && powerUps.jet.active) session.fullKitHeld()
            if (powerUps.jet.tick(dt)) endJet()
            else if (player.flying) { // keep the landing point current; glide down through the last seconds
                val left = powerUps.jet.left
                aimJetCoins(left, spd)
                val landingY = if (bonus == Bonus.FLOAT) Player.HOVER_Y else player.ground
                player.flyY = if (left < jetGlide) landingY + (Player.FLY_Y - landingY) * (left / jetGlide) else Player.FLY_Y
            }
        }

        if (!dead) {
            refreshJumpAbility()
            player.hover = bonus == Bonus.FLOAT
            val gh = if (player.flying) 0f else groundAt(player.px)
            when (player.update(dt, mv, time, worldHue(), trail = started, groundH = gh, stream = spd * 0.55f)) {
                Player.EV_LANDED -> if (styleCombo > 0) { fx.styleLand(styleCombo, player.px, player.py); styleCombo = 0 }
                Player.EV_SIDE_HIT -> if (started) sideHit(gh)
            }
        }
        bubble.update(dt, time, player.px + player.nudge, player.py + Terrain.y(0f))
        refreshJumpAbility()
        val cooldown = kotlin.math.ceil(bubble.cooldownLeft).toInt()
        if (cooldown != shownBubbleCooldown) { shownBubbleCooldown = cooldown; session.setBubbleCooldown(cooldown) }

        // ---- obstacle rows: move, collide, score; coins: magnet + collect; pickups
        track.scroll(mv, time, dt)
        debris.update(dt, mv)
        collide(dt)

        rig.chase(dt, player.px, player.py, player.ground, deathT, spd)
        if (opening.active) opening.pose(player, cam, worldHue())
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
                    if (immune || ob === phasedObstacle || ob.type != ObType.SOLID) continue
                    val lat = ob.lateral(px)
                    if (lat >= 0f) { row.minClear = min(row.minClear, lat); continue } // beside it: the lateral gap is the clearance
                    val vert = max(cubeBottom - ob.top, ob.bottom - headY)              // over / under it: the vertical gap (positive = clear)
                    row.minClear = min(row.minClear, vert)
                    if (abs(row.z) < 0.82f && vert < -0.02f) {
                        if (idlePilot.active) pilotContacts++
                        if (!bubble.active && phase(ob)) continue
                        hit(row); break
                    }
                }
            }
            if (started && !dead && row.pickup != Pickup.NONE) { // run into it
                val cz = row.z + Row.PICKUP_DZ
                if (!row.pickupMissed && abs(cz) < 0.9f && abs(px - row.pickupX) < 0.95f && py < 1.7f) collectPickup(row, cz)
                else if (!row.pickupMissed && cz > 1.1f) {
                    row.pickupMissed = true
                    if (row.pickup == Pickup.BOX && !row.idle) session.mysteryBoxMissed()
                }
            }
            row.coins?.let { coins ->
                if (row.z > -12f && started && !dead) {
                    for (c in coins) {
                        if (c.taken) continue
                        val cz = row.z + c.dz
                        if (c.missed) continue
                        if (pull > 0f && abs(cz) < pull) { // magnet: coins fly to you
                            val k = min(1f, dt * 11f)
                            c.x += (px - c.x) * k
                            c.y += (py - c.y) * k
                        }
                        if (abs(cz) < 0.8f && abs(px - c.x) < 0.85f && abs(py - c.y) < 0.85f) {
                            collectCoin(c, cz)
                        } else if (cz > 1.1f) { // it's behind you — the streak breaks; the coin keeps sliding past the camera
                            c.missed = true
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
        if (gift.active || showcase.active || dead || Stage.paused || jackpot.active) return
        powerUps.drawBars(shapes, w, h, time, if (bubble.active) bubble.timer else null, redPill.timer)
    }

    override fun renderWorldBatched() {
        setMatrixAmount(if (gift.active || showcase.active) 0f else redPill.blend)
        if (gift.active) { fogColor.set(bgBottom); syncFog(); gift.render(time); return }
        if (showcase.active) {
            if (showcase.shop && showcase.menuVisibility > 0f) {
                setWorldOpacity(showcase.menuVisibility)
                renderTrackScene()
                setWorldOpacity(1f)
            }
            fogColor.set(bgBottom); syncFog(); showcase.render(time)
            return
        }
        setWorldOpacity(opening.worldAmount)
        if (opening.worldAmount > .001f) renderTrackScene()
        setWorldOpacity(1f)
    }

    private fun renderTrackScene() {
        // haze target ≈ the sky gradient at the horizon, so the far track end and
        // freshly spawned rows dissolve into the background instead of popping in
        fogColor.set(bgBottom).lerp(bgTop, worlds.fogMix)
        syncFog()
        scenery.renderRoad()
        trackArt.render(track, time, kaleido, kaleidoHue)
        debris.render()
        val wind = if (dead) 0f else ((spd - 13f) / 15f).coerceIn(0f, 1f)
        scenery.render(if (player.flying) 1f else wind, time)
        if (jackpot.active) jackpot.render()
    }

    override fun renderWorldBackdrop(shapes: ShapeRenderer) {
        if (showcase.active && showcase.shop) showcase.renderShapes(shapes, time)
    }

    override fun renderWorldShapes(shapes: ShapeRenderer) {
        if (gift.active) gift.renderShapes(shapes, time)
        else if (showcase.active && !showcase.shop) showcase.renderShapes(shapes, time)
        else if (!showcase.active) {
            trackArt.renderCues(shapes, track, opening.worldAmount, redPill.blend)
            if (jackpot.active) jackpot.renderShapes(shapes, time)
        }
    }

    override fun renderWorld(batch: ModelBatch, env: Environment) {
        if (gift.active) return
        if (showcase.active) { player.render(batch, env); return }
        if (jackpot.active) {
            player.jackpotPose(time, worldHue(), player.px, jackpot.cubeY, jackpot.cubeYaw, jackpot.cubeTip, jackpot.cubeScale, jackpot.cubeGold, trackArt.gold)
            player.render(batch, env)
            return
        }
        if (!dead) player.render(batch, env, Terrain.y(0f))
    }

    override fun renderBlended() {
        if (gift.active) { gift.renderBlended(cam, time); return }
        if (showcase.active) { showcase.renderBlended(cam, time); return }
        if (!dead && !jackpot.active) bubble.render(cam, time)
        for (r in track.rows) { // floating bubble pickups: little soap bubbles
            if (r.pickup != Pickup.BUBBLE) continue
            val cz = r.z + Row.PICKUP_DZ
            if (cz < -Fog.end) continue
            val s = 1.25f + 0.06f * sin(time * 5f + r.visualPhase)
            bubbles.draw(cam, r.pickupX, 0.95f + 0.12f * sin(time * 3f + r.visualPhase), cz, s, s, s, time * 50f, time, 170f, 270f, 0.6f, 2.4f, 0.12f, 1f - Fog.at(cz), BubbleSkins.IRIS)
        }
    }
    override fun pause() { idlePilot.stop(); super.pause() }
    override fun dispose() { idlePilot.close(); showcase.dispose(); super.dispose() }

}
