package cube.run.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import cube.run.core.Gdx3DGame
import cube.run.core.GameSession
import cube.run.core.Haptics
import cube.run.core.Settings
import cube.run.core.SoundFx
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Cube Run — 3-lane endless runner. The world rushes toward the camera;
 * swipe LEFT/RIGHT to snap lanes, UP to jump, DOWN to roll (on the ground)
 * or slam (mid-air).
 *
 * The track is a single continuous "lane-walk": every row leaves a known safe
 * lane, and that safe lane only ever moves by at most one between consecutive
 * rows, so the run is dense but always physically solvable. Recognisable
 * [Sect] patterns (slalom, tunnel, gauntlet…) are authored over that stream
 * and stitched together by the [pickSection] director, with tier-gating and
 * mirroring for variety. Difficulty is a single axis: speed.
 *
 * Obstacles: bars/twin pillars = dodge, low walls = JUMP, overhead bars =
 * ROLL under, sliders drift into a lane. +1 per row, +2 for a near-miss.
 */
class CubeRun(session: GameSession) : Gdx3DGame(session) {

    // obstacle collision behaviour
    private val DODGE = 0   // solid — get out of its lane
    private val JUMP = 1    // low wall — be airborne / high enough
    private val DUCK = 2    // overhead bar — be rolling / low enough

    private class Ob(
        val inst: ModelInstance, var x: Float, val cy: Float, val halfW: Float,
        val type: Int, val clear: Float,
        val sx: Float, val sy: Float, val sz: Float,
        val sliding: Boolean = false, val slideTo: Float = 0f,
    )

    private class Row(var z: Float, val obs: ArrayList<Ob>) {
        var scored = false
        var minClear = 99f // tightest clearance seen while crossing (near-miss detect)
    }

    private class Tile(val inst: ModelInstance, val col: Color, var z: Float, val x: Float, val dim: Boolean)
    private class Post(val inst: ModelInstance, val col: Color, var z: Float, val x: Float)

    // ---- section model ---------------------------------------------------
    // A section is a sequence of step codes. Decoded by [spawnStep]:
    //   0,1,2   dodge: make `that lane` the only safe one (block the other two)
    //   10..12  feint: a lone pillar (two lanes stay safe) for visual density
    //   20..22  slide: a pillar drifts into that lane (a "closing gate")
    //   JP      jump a low wall   DK  roll under an overhead bar   EM  open row
    private fun dg(l: Int) = l
    private fun ft(l: Int) = 10 + l
    private fun sld(l: Int) = 20 + l
    private val JP = 30
    private val DK = 31
    private val EM = 40

    private class Sect(val id: Int, val tier: Int, val weight: Float, val steps: IntArray, val mirrorable: Boolean = true)

    /**
     * The library. Each section is a fixed, hand-tuned pattern (recognisable
     * between runs); the director randomises which appear, their order and
     * mirroring (procedural). Spacing/reachability is handled by the lane-walk,
     * so these are pure shapes.
     */
    private val sectLib = listOf(
        // --- tier 0: teach, continuous but forgiving ---
        Sect(0, 0, 1.3f, intArrayOf(dg(1), dg(0), dg(1), dg(2), dg(1), dg(0))),                 // FIRST STEPS
        Sect(1, 0, 1.1f, intArrayOf(ft(0), ft(2), ft(1), ft(0), ft(2), ft(1))),                 // WEAVE
        Sect(2, 0, 1.0f, intArrayOf(JP, dg(1), JP, dg(1), JP)),                                  // HOP
        // --- tier 1: precise lane-walks + simple combos ---
        Sect(3, 1, 1.3f, intArrayOf(dg(0), dg(1), dg(2), dg(1), dg(0), dg(1), dg(2))),           // SLALOM
        Sect(4, 1, 1.1f, intArrayOf(dg(1), dg(2), dg(1), dg(0), dg(1), dg(2), dg(1), dg(0))),    // ZIGZAG
        Sect(5, 1, 1.1f, intArrayOf(JP, dg(0), dg(2), JP, dg(1), dg(0))),                        // LEAP & WEAVE
        Sect(6, 1, 0.9f, intArrayOf(sld(1), sld(0), sld(2), sld(1))),                            // CLOSING GATES
        Sect(7, 1, 0.7f, intArrayOf(dg(0), DK, dg(2), dg(1), DK)),                               // DUCK & DODGE (ducks sparse)
        // --- tier 2: dense, verb-switching ---
        Sect(8, 2, 1.2f, intArrayOf(dg(0), JP, dg(2), dg(1), DK, dg(0), JP, dg(2))),             // GAUNTLET (one duck)
        Sect(9, 2, 1.2f, intArrayOf(dg(0), dg(1), dg(2), dg(1), dg(0), dg(1), dg(2), dg(1))),    // RAPID FIRE
        Sect(10, 2, 0.9f, intArrayOf(JP, dg(0), dg(2), DK, dg(1), JP, dg(0))),                   // STORM (one duck)
        Sect(11, 2, 0.9f, intArrayOf(sld(2), sld(1), sld(0), dg(1), dg(2))),                     // TRAPS
    )
    // teaches the controls: lone side pillars, centre always safe
    private val introSect = Sect(-2, 0, 0f, intArrayOf(ft(0), ft(2), ft(0), ft(2)), mirrorable = false)
    // an occasional short breather: a single open row
    private val breatherSect = Sect(-1, 0, 0f, intArrayOf(EM), mirrorable = false)

    private lateinit var unit: Model
    private lateinit var playerInst: ModelInstance
    private lateinit var playerCol: Color
    private lateinit var shellInst: ModelInstance
    private lateinit var shellBlend: BlendingAttribute
    private lateinit var shadowInst: ModelInstance
    private lateinit var shadowBlend: BlendingAttribute

    private val rows = ArrayList<Row>()
    private val tiles = ArrayList<Tile>()
    private val posts = ArrayList<Post>()
    private val rnd = Random(System.nanoTime())
    private val tmp = Vector3()

    private val laneW = 1.7f
    private val ground = 0.45f      // resting cube center (cube = 0.9 across)
    private val spawnZ = -64f
    private val tileD = 3f
    private val tileRows = 26
    private val postGap = 6.6f
    private val postPairs = 12

    // ---- difficulty: one normalised level `diff` (0..1) drives speed + tier ----
    // diff auto-advances as you play. Speed is linear up to the "blue line" (the
    // cruising max) and then gives diminishing returns toward the absolute ceiling.
    private val exploreMinSpd = 10f  // speed at diff = 0
    private val exploreMaxSpd = 30f  // absolute ceiling (diff = 1); only ever approached, never the cruise speed
    private val blueLine = 0.85f     // the blue line: the cruising max speed; past here speed barely climbs
    private val cruiseSpd = exploreMinSpd + (exploreMaxSpd - exploreMinSpd) * blueLine // speed at the blue line (27)
    private val rampSeconds = 700f   // real seconds for diff to auto-climb the full 0..1 range
    private val startDiff = 0.12f    // difficulty a run begins at with no fire boost
    private var diff = startDiff     // current difficulty level

    // ---- fire boost: an opening-seconds button that front-loads your speed ----
    private val fireWindow = 15f                // seconds the button stays available from the run's start
    private val fireMaxTaps = 5                 // taps before it maxes out
    private val fireMaxSpd = cruiseSpd * 0.8f   // 5 taps launches you at 80% of the blue-line speed
    private val fireMaxDiff = (fireMaxSpd - exploreMinSpd) / (exploreMaxSpd - exploreMinSpd)
    private var fireTaps = 0
    private var runTime = 0f                    // seconds since the current run began
    private var touchIsFire = false             // current touch began on the fire button (don't steer with it)

    // ---- death: let the crash animation play before the game-over card ----
    private val deathAnimTime = 1.5f            // seconds of death animation shown before the restart screen
    private var gameOverShown = false

    // row spacing (world units). Tight by default = dense; wider after a jump so you can land.
    private val dodgeGap = 6.5f
    private val jumpRecoverGap = 9.5f
    private val breatherGap = 12f

    // ---- style points: tap mid-air for an ascending combo (purely for flair) ----
    private var styleCombo = 0   // consecutive air taps; resets on landing. Never stored or shown as a total.

    // smooth-control gesture state (positional steering within one continuous touch)
    private var smoothAnchorX = 0f      // finger x where the touch began
    private var smoothAnchorLane = 1    // lane the cube was in when the touch began
    private var smoothVAccum = 0f       // accumulated vertical motion, for jump/duck flicks

    private var baseHue = 0f
    private var started = false
    private var dead = false
    private var lane = 1
    private var px = 0f
    private var py = ground
    private var vy = 0f
    private var air = false
    private var roll = 0f           // forward tumble angle
    private var squash = 0f         // landing squash timer
    private var duckT = 0f          // remaining roll/duck window (seconds)
    private var duck = 0f           // eased 0..1 roll amount (visual + collision)
    private var slamming = false    // a mid-air slam is in progress (auto-crouches on landing)
    private var nudge = 0f          // edge-bonk offset
    private var trailT = 0f
    private var spd = 4.5f
    private var dist = 0f
    private var spawnAcc = 0f
    private var rowsSpawned = 0
    private var rowsPassed = 0
    private var deathT = 0f

    // ---- director / lane-walk state ----
    private val pendingSteps = ArrayDeque<Int>()
    private var curSafe = 1         // the lane currently guaranteed safe (the walk position)
    private var prevKind = -1       // last spawned step code (drives recovery spacing)
    private var mirror = false
    private var introServed = false
    private var lastSectId = -99
    private var sectsSinceBreather = 0

    private fun laneX(l: Int) = (l - 1) * laneW
    private fun ml(l: Int) = if (mirror) 2 - l else l

    private fun diffuse(inst: ModelInstance): Color =
        (inst.materials.first().get(ColorAttribute.Diffuse) as ColorAttribute).color

    /** Allocation-free HSV write into an existing Color; returns it for chaining. */
    private fun setHsv(c: Color, h: Float, s: Float, v: Float): Color {
        val hh = (((h % 360f) + 360f) % 360f) / 60f
        val i = hh.toInt()
        val f = hh - i
        val p = v * (1f - s); val q = v * (1f - f * s); val t = v * (1f - (1f - f) * s)
        when (i % 6) {
            0 -> c.set(v, t, p, 1f); 1 -> c.set(q, v, p, 1f); 2 -> c.set(p, v, t, 1f)
            3 -> c.set(p, q, v, 1f); 4 -> c.set(t, p, v, 1f); else -> c.set(v, p, q, 1f)
        }
        return c
    }

    // reusable scratch Colors for transient flash/burst tints (flash + burst3d copy
    // their argument immediately, so a shared scratch passed sequentially is safe).
    private val tmpCol = Color()

    private fun tileHue(t: Tile) {
        // floor hue drifts with total distance; checker brightness reads as a grid
        setHsv(t.col, baseHue + dist * 1.6f, 0.7f, if (t.dim) 0.30f else 0.46f)
    }

    override fun init() {
        unit = box(1f, 1f, 1f, Color.WHITE)
        baseHue = (System.currentTimeMillis() % 360L).toFloat()
        bgTop = gdxHsv(baseHue + 30f, 0.6f, 0.4f)
        bgBottom = gdxHsv(baseHue + 70f, 0.65f, 0.1f)

        for (r in 0 until tileRows) for (l in 0..2) {
            val inst = ModelInstance(unit)
            val t = Tile(inst, diffuse(inst), 8f - r * tileD, laneX(l), (r + l) % 2 == 0)
            tileHue(t)
            tiles.add(t)
        }
        var pz = 6f
        repeat(postPairs) {
            for (side in intArrayOf(-1, 1)) {
                val inst = ModelInstance(unit)
                val p = Post(inst, diffuse(inst), pz, side * (laneW * 1.5f + 1.0f))
                setHsv(p.col, baseHue + 50f, 0.85f, 1f)
                posts.add(p)
            }
            pz -= postGap
        }

        playerInst = ModelInstance(unit)
        playerCol = diffuse(playerInst)
        setHsv(playerCol, baseHue + 180f, 0.55f, 1f)

        shellInst = ModelInstance(unit) // pulsing translucent "glow" shell
        shellBlend = BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.3f)
        shellInst.materials.first().set(ColorAttribute.createDiffuse(gdxHsv(baseHue + 180f, 0.5f, 1f)), shellBlend)

        shadowInst = ModelInstance(unit)
        shadowBlend = BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.3f)
        shadowInst.materials.first().set(ColorAttribute.createDiffuse(Color.BLACK), shadowBlend)

        cam.position.set(0f, 3.7f, 6.4f)
        cam.lookAt(0f, 1.0f, -8f)
        cam.update()
        // "Tap to start" is shown by the HUD (GameChromeView) until the first input.
    }

    // ------------------------------------------------------------- obstacles

    private fun colored(c: Color): ModelInstance {
        val inst = ModelInstance(unit)
        diffuse(inst).set(c)
        return inst
    }

    private fun makePillar(x: Float, hue: Float, sliding: Boolean = false, slideTo: Float = 0f): Ob {
        val h = 2.0f + rnd.nextFloat() * 0.6f
        val c = if (sliding) gdxHsv(hue + 230f, 0.95f, 1f) else gdxHsv(hue + 185f, 0.85f, 1f)
        return Ob(colored(c), x, h / 2f, 0.75f, DODGE, 0f, 1.5f, h, 0.9f, sliding, slideTo)
    }

    private fun makeWall(hue: Float): Ob {
        // a solid block sitting ON the ground — clearly "jump over"
        val w = laneW * 3f + 0.6f; val h = 0.62f // cube must clear this height
        return Ob(colored(gdxHsv(hue + 140f, 0.9f, 1f)), 0f, h / 2f, laneW * 1.5f + 0.3f, JUMP, h, w, h, 0.7f)
    }

    private fun makeOver(hue: Float): Ob {
        // a chunky beam floating well above the ground with a clear gap beneath it,
        // in a hue far from the low wall — unmistakably "roll under", not "jump over"
        val w = laneW * 3f + 0.6f; val bottom = 0.78f; val top = 1.5f
        return Ob(colored(gdxHsv(hue + 300f, 0.9f, 1f)), 0f, (bottom + top) / 2f, laneW * 1.5f + 0.3f, DUCK, bottom, w, top - bottom, 0.7f)
    }

    private fun makeSlider(from: Int, to: Int, hue: Float): Ob =
        makePillar(laneX(from), hue, sliding = true, slideTo = laneX(to))

    /** Wide bar leaving exactly one lane open (twin pillars when the centre is open). */
    private fun addOneOpen(open: Int, hue: Float, into: ArrayList<Ob>) {
        if (open == 1) {
            into.add(makePillar(laneX(0), hue)); into.add(makePillar(laneX(2), hue))
        } else {
            val a = if (open == 0) 1 else 0 // blocked adjacent lane pair
            val cx = (laneX(a) + laneX(a + 1)) / 2f
            val w = laneW + 1.5f
            into.add(Ob(colored(gdxHsv(hue + 185f, 0.85f, 1f)), cx, 2.3f / 2f, w / 2f, DODGE, 0f, w, 2.3f, 0.9f))
        }
    }

    // ------------------------------------------------------------- spawning

    /** Distance to leave before the row that's about to spawn. */
    private fun gapFor(code: Int): Float {
        val recover = when (prevKind) {
            JP -> jumpRecoverGap // we were airborne — give room to land
            -1 -> 0f             // very first row
            else -> dodgeGap
        }
        return if (code == EM) max(recover, breatherGap) else recover
    }

    private fun spawnStep(code: Int, z: Float) {
        val obs = ArrayList<Ob>(2)
        val hue = baseHue + dist * 1.6f // obstacles pop against the current floor hue
        when {
            code == EM -> { /* open row — a beat of rest */ }
            code == JP -> obs.add(makeWall(hue))
            code == DK -> obs.add(makeOver(hue))
            code in 20..22 -> { // slide: a pillar drifts into a lane (closing gate)
                val target = ml(code - 20)
                if (curSafe == target) curSafe = if (target == 1) (if (rnd.nextBoolean()) 0 else 2) else 1
                val from = when {
                    target == 1 -> if (curSafe == 0) 2 else 0
                    else -> 1
                }
                obs.add(makeSlider(from, target, hue))
            }
            code in 10..12 -> { // feint: a lone pillar, two lanes stay safe
                var block = ml(code - 10)
                if (block == curSafe) block = if (curSafe == 0) 1 else curSafe - 1 // never block where we stand
                obs.add(makePillar(laneX(block), hue))
            }
            else -> { // dodge: clamp the requested safe lane to within one of the walk, then block the rest
                val safe = ml(code).coerceIn(curSafe - 1, curSafe + 1).coerceIn(0, 2)
                curSafe = safe
                addOneOpen(safe, hue, obs)
            }
        }
        rows.add(Row(z, obs))
        rowsSpawned++
        prevKind = code
    }

    private fun unlockedTier(): Int = when {
        diff < 0.18f -> 0
        diff < 0.38f -> 1
        else -> 2
    }

    /** Director: intro first, an occasional breather, else a weighted pick from the unlocked tiers. */
    private fun pickSection(): Sect {
        if (!introServed) { introServed = true; return introSect }
        sectsSinceBreather++
        if (sectsSinceBreather >= 5) { sectsSinceBreather = 0; return breatherSect }
        val tier = unlockedTier()
        var pool = sectLib.filter { it.tier <= tier && it.id != lastSectId }
        if (pool.isEmpty()) pool = sectLib.filter { it.tier <= tier }
        var total = 0f; for (s in pool) total += s.weight
        var r = rnd.nextFloat() * total
        var chosen = pool[pool.size - 1]
        for (s in pool) { r -= s.weight; if (r <= 0f) { chosen = s; break } }
        lastSectId = chosen.id
        return chosen
    }

    private fun loadNextSection() {
        val s = pickSection()
        mirror = s.mirrorable && rnd.nextBoolean()
        for (c in s.steps) pendingSteps.add(c)
    }

    // --------------------------------------------------------------- events

    private fun start() {
        if (started || session.isOver) return
        started = true
        fireTaps = 0; runTime = 0f; styleCombo = 0
        pendingSteps.clear(); introServed = false; sectsSinceBreather = 0; lastSectId = -99
        curSafe = 1; prevKind = -1; spawnAcc = 0f
        // prefill so the first obstacles arrive within a couple of seconds
        var z = -30f
        repeat(5) {
            if (pendingSteps.isEmpty()) loadNextSection()
            spawnStep(pendingSteps.removeFirst(), z)
            z -= 7f
        }
        session.runStarted()
        session.banner("GO!")
        SoundFx.play("rise")
        Haptics.click()
        flash(gdxHsv(baseHue + 180f, 0.4f, 1f), 0.12f)
    }

    private fun crash() {
        if (dead) return
        dead = true
        burst3d(tmp.set(px, py, 0f), playerCol, n = 40, speed = 9f, size = 0.2f, life = 1.1f)
        burst3d(tmp, Color.WHITE, n = 12, speed = 13f, size = 0.11f, life = 0.6f)
        SoundFx.play("boom")
        Haptics.heavy()
        shake(1.0f)
        flash(Color.RED, 0.45f)
        // NB: the game-over card is deferred (see tick) so the crash animation is visible.
    }

    private fun scoreRow(row: Row) {
        rowsPassed++
        session.addScore(1)
        SoundFx.play("tick", rate = 1f + (rowsPassed % 15) * 0.025f, vol = 0.8f)
        Haptics.tick()
        if (row.minClear < 0.34f) { // shaved it — reward a close dodge with an air-rush, not a coin
            session.addScore(2)
            SoundFx.play("whoosh", rate = 1.55f + rnd.nextFloat() * 0.2f, vol = 0.7f)
            Haptics.click()
            flash(Color.WHITE, 0.07f)
            burst3d(tmp.set(px, py + 0.4f, 0.2f), Color.WHITE, n = 10, speed = 4f, size = 0.1f, life = 0.5f)
        }
        // sky drifts as you survive (mutate in place — no per-row Color allocation)
        setHsv(bgTop, baseHue + 30f + rowsPassed * 2f, 0.6f, 0.4f)
        setHsv(bgBottom, baseHue + 70f + rowsPassed * 2f, 0.65f, 0.1f)
    }

    // ---------------------------------------------------------------- input

    override fun onDown(x: Float, y: Float) {
        touchIsFire = fireAvailable() && inFireZone(x, y)
        if (touchIsFire) { tapFire(); return }
        start()
        smoothAnchorX = x; smoothAnchorLane = lane; smoothVAccum = 0f
    }

    override fun onDrag(x: Float, y: Float, dx: Float, dy: Float) {
        if (touchIsFire) return // this touch is operating the fire button
        if (!Settings.smoothControl || !started || dead || session.isOver) return
        // horizontal: finger position maps directly to a lane — no lag, no overshoot
        val laneTravel = sw * (0.32f - 0.20f * Settings.smoothSensitivity) // finger px per lane
        val target = (smoothAnchorLane + ((x - smoothAnchorX) / laneTravel).roundToInt()).coerceIn(0, 2)
        if (target != lane) moveToLane(target)
        // vertical: a clearly-vertical movement is a jump (up) or slam/roll (down) flick
        val vStep = sw * (0.16f - 0.08f * Settings.smoothSensitivity)
        if (abs(dy) > abs(dx)) smoothVAccum += dy else smoothVAccum *= 0.6f // bleed off while steering
        if (smoothVAccum <= -vStep) { smoothVAccum = 0f; jump() }
        else if (smoothVAccum >= vStep) { smoothVAccum = 0f; downAction() }
    }

    override fun onTap(x: Float, y: Float) {
        // tapping while airborne racks up a style combo (flair only — never scored)
        if (!started || dead || session.isOver || touchIsFire || !air) return
        styleTap()
    }

    override fun smoothSwipeEnabled(): Boolean = Settings.smoothControl

    override fun onSwipe(dir: Int) {
        if (touchIsFire) return // this touch is operating the fire button
        if (session.isOver || dead) return
        if (!started) start()
        when (dir) {
            Gdx3DGame.LEFT, Gdx3DGame.RIGHT -> {
                val d = if (dir == Gdx3DGame.LEFT) -1 else 1
                if (lane + d in 0..2) moveToLane(lane + d)
                else { // bonk the invisible wall
                    nudge = d * 0.4f
                    SoundFx.play("tap", rate = 0.7f)
                    Haptics.tick()
                }
            }
            Gdx3DGame.UP -> jump()
            Gdx3DGame.DOWN -> downAction()
        }
    }

    private fun moveToLane(target: Int) {
        val t = target.coerceIn(0, 2)
        if (t == lane) return
        lane = t
        SoundFx.play("whoosh", rate = 0.95f + rnd.nextFloat() * 0.15f)
        SoundFx.play("tick", rate = 1.4f, vol = 0.5f)
        Haptics.tick()
    }

    private fun jump() {
        if (air) return
        air = true; vy = 8.4f
        duckT = 0f // jumping cancels a roll
        SoundFx.play("whoosh", rate = 1.3f)
        Haptics.click()
        burst3d(tmp.set(px, 0.1f, 0.3f), playerCol, n = 6, speed = 2.5f, size = 0.08f, life = 0.35f)
    }

    /** Context-sensitive DOWN: slam when airborne, roll under when grounded. */
    private fun downAction() {
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
            burst3d(tmp.set(px, 0.06f, 0.4f), playerCol, n = 5, speed = 2.8f, size = 0.08f, life = 0.3f)
        }
    }

    // ----------------------------------------------------------------- loop

    override fun tick(dt: Float) {
        if (started && !dead) {
            spd = speedFor(diff) // blue line is the cruising max; diminishing returns past it
            runTime += dt
        } else if (!started) {
            spd = 4.5f // ambient pre-start scroll
        } else {
            spd = max(0f, spd - spd * 2.4f * dt) // death: world glides to a stop
            deathT = min(deathT + dt, 2.5f)
            // hold on the crash animation, then reveal the restart screen
            if (!gameOverShown && deathT >= deathAnimTime) { gameOverShown = true; session.gameOver() }
        }
        val mv = spd * dt
        dist += mv
        // difficulty auto-climbs over real time toward the ceiling; the speed curve
        // (speedFor) keeps the blue line as the effective cruising max.
        if (started && !dead && diff < 1f) diff = min(1f, diff + dt / rampSeconds)

        // floor tiles + neon side posts scroll and wrap, recoloring on wrap
        for (t in tiles) {
            t.z += mv
            if (t.z > 8f) { t.z -= tileRows * tileD; tileHue(t) }
            t.inst.transform.setToTranslation(t.x, -0.14f, t.z).scale(laneW * 0.92f, 0.26f, tileD * 0.9f)
        }
        for (p in posts) {
            p.z += mv
            if (p.z > 8f) { p.z -= postPairs * postGap; setHsv(p.col, baseHue + dist * 1.6f + 50f, 0.85f, 1f) }
            p.inst.transform.setToTranslation(p.x, 0.7f, p.z).scale(0.26f, 1.4f, 0.26f)
        }

        // ---- spawn the continuous lane-walk from the section queue
        if (started && !dead && !session.isOver) {
            spawnAcc += mv
            while (true) {
                if (pendingSteps.isEmpty()) loadNextSection()
                val code = pendingSteps.first()
                val gap = gapFor(code)
                if (spawnAcc >= gap) {
                    spawnAcc -= gap
                    spawnStep(code, spawnZ)
                    pendingSteps.removeFirst()
                } else break
            }
        }

        // ---- player physics + transforms
        if (!dead) {
            px += (laneX(lane) - px) * min(1f, dt * 13f) // eased lane snap
            nudge *= max(0f, 1f - 10f * dt)
            if (air) {
                vy -= 26f * dt
                py += vy * dt
                if (py <= ground) {
                    py = ground; air = false; vy = 0f
                    SoundFx.play("pop", rate = 0.95f + rnd.nextFloat() * 0.12f)
                    Haptics.click()
                    burst3d(tmp.set(px, 0.06f, 0.4f), playerCol, n = 8, speed = 3.2f, size = 0.09f, life = 0.4f)
                    squash = 1f
                    if (slamming) { slamming = false; duckT = 0.5f } // slam → auto-crouch on landing
                    if (styleCombo > 0) styleLand() // the air-tap combo "lands"
                }
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
            playerInst.transform.setToTranslation(px + nudge, py - squash * 0.08f - duY, 0f)
                .rotate(Vector3.Z, tilt)
                .rotate(Vector3.X, -roll)
                .scale(0.9f * (1f + sq + duck * 0.35f), 0.9f * (1f - sq) * (1f - duck * 0.5f), 0.9f * (1f + sq + duck * 0.1f))
            val pulse = 0.9f * (1.18f + 0.06f * sin(time * 8f))
            shellBlend.opacity = 0.22f + 0.08f * sin(time * 6f)
            shellInst.transform.setToTranslation(px + nudge, py - duY, 0f)
                .rotate(Vector3.Z, tilt).rotate(Vector3.X, -roll)
                .scale(pulse, pulse, pulse)
            shadowBlend.opacity = (0.36f * (1f - (py - ground) / 1.6f)).coerceIn(0.06f, 0.36f)
            shadowInst.transform.setToTranslation(px + nudge, 0.04f, 0f).scale(1.0f, 0.02f, 1.0f)

            if (started) { // glow trail
                trailT += dt
                if (trailT > 0.08f) {
                    trailT = 0f
                    burst3d(tmp.set(px, py, 0.55f), playerCol, n = 1, speed = 1.4f, size = 0.08f, life = 0.35f)
                }
            }
        }

        // ---- obstacle rows: move, slide, collide, score
        val cubeBottom = py - 0.45f
        val headY = py + 0.45f - duck * 0.72f // rolling tucks the head below the bars
        var i = rows.size - 1
        while (i >= 0) {
            val row = rows[i]
            row.z += mv
            for (ob in row.obs) {
                if (ob.sliding && row.z > -26f) ob.x += (ob.slideTo - ob.x) * min(1f, dt * 2.0f)
                ob.inst.transform.setToTranslation(ob.x, ob.cy, row.z).scale(ob.sx, ob.sy, ob.sz)
            }
            if (started && !dead && abs(row.z) < 0.95f) {
                for (ob in row.obs) {
                    val lat = abs(px - ob.x) - (ob.halfW + 0.36f)
                    val clear = when (ob.type) {
                        JUMP -> cubeBottom - ob.clear      // >0 = sailing over the wall
                        DUCK -> ob.clear - headY           // >0 = tucked under the bar
                        else -> lat                        // dodge: lateral gap
                    }
                    row.minClear = min(row.minClear, clear)
                    if (abs(row.z) < 0.82f && lat < 0f && (ob.type == DODGE || clear < -0.02f)) {
                        crash()
                        break
                    }
                }
            }
            if (!row.scored && row.z > 1.2f) {
                row.scored = true
                if (started && !dead) scoreRow(row)
            }
            if (row.z > 12f) rows.removeAt(i)
            i--
        }

        // ---- chase camera: above-behind, leans with the player, pulls back on death
        val cy = 3.6f + (py - ground) * 0.22f + deathT * 1.6f
        cam.position.set(px * 0.45f, cy, 6.4f + deathT * 2.2f)
        cam.lookAt(px * 0.55f, 1.0f, -8f)
        cam.up.set(0f, 1f, 0f)
    }

    // ------------------------------------------------------------- fire button
    // A boost button shown for the first [fireWindow] seconds of a run. Each tap
    // front-loads your speed; [fireMaxTaps] taps launch you at 80% of the blue-line
    // (cruise) speed. It flickers in its final seconds, then disappears.

    /** Speed for a difficulty level: linear up to the blue line, diminishing returns beyond it. */
    private fun speedFor(d: Float): Float =
        if (d <= blueLine) {
            exploreMinSpd + (exploreMaxSpd - exploreMinSpd) * d
        } else {
            val o = (d - blueLine) / (1f - blueLine)
            cruiseSpd + (exploreMaxSpd - cruiseSpd) * (1f - (1f - o) * (1f - o))
        }

    private fun fireVisible(): Boolean =
        started && !dead && !session.isOver && runTime < fireWindow
    private fun fireAvailable(): Boolean = fireVisible() && fireTaps < fireMaxTaps

    // button geometry (touch coords: origin top-left, y down) — top-right, a bit down
    private fun fireCx() = sw * 0.85f
    private fun fireCy() = sh * 0.20f
    private fun fireR() = sw * 0.14f

    private fun inFireZone(x: Float, y: Float): Boolean {
        val dx = x - fireCx(); val dy = y - fireCy()
        return dx * dx + dy * dy < fireR() * fireR()
    }

    private fun tapFire() {
        if (!fireAvailable()) return
        fireTaps++
        val boost = startDiff + (fireMaxDiff - startDiff) * (fireTaps / fireMaxTaps.toFloat())
        if (boost > diff) diff = boost
        SoundFx.play("rise", rate = 0.85f + fireTaps * 0.12f)
        Haptics.click()
        flash(setHsv(tmpCol, 22f, 0.85f, 1f), 0.12f)
        burst3d(tmp.set(px, py + 0.3f, 0.3f), setHsv(tmpCol, 26f, 0.9f, 1f), n = 12, speed = 6f, size = 0.12f, life = 0.55f)
    }

    // ------------------------------------------------------------- style points
    /** A mid-air tap: bump the combo with an ascending pitch + a spark burst (no text). */
    private fun styleTap() {
        styleCombo++
        val rate = (0.85f + 0.16f * styleCombo).coerceAtMost(2f) // pitch climbs each consecutive tap
        // a soft sine "bloop" that ascends — pleasant, not the harsh square blip/coin
        SoundFx.play("pop", rate = rate)
        SoundFx.play("tick", rate = (1f + 0.1f * styleCombo).coerceAtMost(1.6f), vol = 0.3f)
        Haptics.tick()
        // hot, non-green sparks that get richer the higher the combo
        val hue = 290f + styleCombo * 16f // purple → magenta → red, never green
        flash(setHsv(tmpCol, hue, 0.5f, 1f), 0.05f)
        burst3d(tmp.set(px, py + 0.3f, 0.2f), setHsv(tmpCol, hue, 0.9f, 1f),
            n = 10 + styleCombo * 3, speed = 5f + styleCombo, size = 0.11f, life = 0.55f)
        burst3d(tmp.set(px, py + 0.3f, 0.2f), Color.WHITE, n = 4, speed = 6f, size = 0.07f, life = 0.3f)
    }

    /** Touchdown after an air combo: a burst of flair (no text); only a 5+ combo shakes. */
    private fun styleLand() {
        SoundFx.play("perfect", rate = (1f + 0.06f * styleCombo).coerceAtMost(1.7f))
        Haptics.success()
        val hue = 300f + styleCombo * 10f // warm, non-green
        flash(setHsv(tmpCol, hue, 0.4f, 1f), 0.12f)
        burst3d(tmp.set(px, py + 0.2f, 0.2f), setHsv(tmpCol, hue, 0.85f, 1f),
            n = 14 + styleCombo * 3, speed = 7f, size = 0.13f, life = 0.7f)
        burst3d(tmp.set(px, py + 0.2f, 0.2f), Color.WHITE, n = 6, speed = 5f, size = 0.09f, life = 0.4f)
        if (styleCombo >= 5) shake(0.3f) // only a big combo earns a screen shake
        styleCombo = 0
    }

    /**
     * One constant-width "^" drawn as a single mitered band (4 triangles that abut
     * exactly — no overlap), so the whole chevron is one consistent opaque outline.
     */
    private fun chevron(shapes: ShapeRenderer, cx: Float, yBase: Float, chevW: Float, chevH: Float, lineW: Float) {
        val l = sqrt(chevW * chevW + chevH * chevH)
        val hw = lineW * 0.5f
        val ox = -chevH / l * hw   // outer-perpendicular offset
        val oy = chevW / l * hw
        val axO = cx - chevW + ox; val ayO = yBase + oy   // left tip, outer (top) edge
        val axI = cx - chevW - ox; val ayI = yBase - oy   // left tip, inner (under) edge
        val cxO = cx + chevW - ox                         // right tip, outer
        val cxI = cx + chevW + ox                         // right tip, inner
        val oTopY = ayO + (chevW - ox) / chevW * chevH    // apex, outer corner (on x = cx)
        val oBotY = ayI + (chevW + ox) / chevW * chevH    // apex, inner corner
        shapes.triangle(axO, ayO, cx, oTopY, cx, oBotY)   // left arm band
        shapes.triangle(axO, ayO, cx, oBotY, axI, ayI)
        shapes.triangle(cx, oTopY, cxO, ayO, cxI, ayI)    // right arm band
        shapes.triangle(cx, oTopY, cxI, ayI, cx, oBotY)
    }

    override fun renderHud(shapes: ShapeRenderer, w: Float, h: Float) {
        if (!fireVisible()) return
        // 5 stacked "^" chevrons, top-right. Minimalist: one clean opaque orange
        // outline each (no glow/overlap). Each tap lights one; lit chevrons carry a
        // gentle fluid shimmer. The stack flickers in the window's final seconds.
        val cx = fireCx()
        val cyDraw = h - fireCy()           // touch-space centre → draw space (y is up here)
        val gap = h * 0.024f
        val chevW = w * 0.055f
        val chevH = h * 0.020f
        val lineW = w * 0.014f
        val expiring = runTime > fireWindow - 4f
        val flick = if (expiring && sin(time * 26f) < -0.1f) 0.3f else 1f
        val y0 = cyDraw - 2f * gap          // bottom chevron; stack centred on cyDraw
        for (i in 0 until fireMaxTaps) {
            val yBase = y0 + i * gap
            val lit = i < fireTaps
            if (lit) {                      // bright orange with a subtle fluid shimmer
                val wave = 0.5f + 0.5f * sin(time * 5f - i * 0.8f)
                val v = 0.9f + 0.1f * wave
                shapes.setColor(v, 0.5f * v, 0.05f * v, flick)
            } else {                        // waiting: dim
                shapes.setColor(0.5f, 0.28f, 0.1f, 0.5f * flick)
            }
            chevron(shapes, cx, yBase, chevW, chevH, if (lit) lineW else lineW * 0.85f)
        }
    }

    override fun renderWorld(batch: ModelBatch, env: Environment) {
        for (t in tiles) batch.render(t.inst, env)
        for (p in posts) batch.render(p.inst, env)
        for (r in rows) for (ob in r.obs) batch.render(ob.inst, env)
        if (!dead) {
            batch.render(shadowInst, env)
            batch.render(playerInst, env)
            batch.render(shellInst, env)
        }
    }
}
