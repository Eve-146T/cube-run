package cube.run.game

import cube.run.core.Settings
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Dev-mode section cursor. Process-scoped on purpose: RESTART relaunches the
// activity within the same process, so the review cycle resumes where it left off.
private var devSectIdx = 0

/**
 * The track director + lane-walk. Produces the continuous stream of [Row]s:
 * every row leaves a known safe lane ([curSafe]) that only ever moves by at
 * most one between consecutive rows, so the run is dense but always
 * physically solvable. [Sections] are stitched over that stream with
 * tier-gating and random mirroring; coins, bubbles and mystery boxes are laid
 * on the walk lane so they lead you along the guaranteed path.
 *
 * Owns the rows' motion and obstacle animation ([scroll]); collision and
 * scoring stay in [CubeRun].
 */
class Track(private val rnd: Random, private val fx: ObstacleFactory, private val laneW: Float) {

    val rows = ArrayList<Row>()

    // row spacing (world units). Tight by default = dense; wider after a jump so you can land.
    private val dodgeGap = 6.5f
    private val jumpRecoverGap = 9.5f
    private val breatherGap = 12f
    /** Rows are born here, fully fogged, and fade in. */
    val spawnZ = -100f

    // ---- lane-walk / director state ----
    private val pendingSteps = ArrayDeque<Int>()
    private var pendingSectName: String? = null // dev mode: name to stamp on the next spawned row
    private var curSafe = 1         // the lane currently guaranteed safe (the walk position)
    private var prevKind = -1       // last spawned step code (drives recovery spacing)
    private var mirror = false
    private var introServed = false
    private var lastSectId = -99
    private var sectsSinceBreather = 0
    private var spawnAcc = 0f
    var rowsSpawned = 0
        private set

    // ---- platforms ----
    private val rampLen = 3.2f
    private var prevPlatLane = -1        // lane of the previous row's platform segment (-1 = none)

    // ---- goodies ----
    private var coinRowsLeft = 0         // rows still to carry coins in the current trail
    private var coinTrailChance = 0.16f  // chance per row to start a new coin trail (lucky upgrade)
    private val pickupMinRows = 24       // no pickups in the opening rows
    private val pickupSpacing = 14       // rows between any two pickups
    private val boxSpacing = 90          // rows between mystery boxes (rare on purpose)
    private var rowsSincePickup = 99
    private var rowsSinceBox = 99
    /** While the jetpack is on, new coin trails are laid at flying height. */
    @Volatile var airCoins = false

    /** Unlocked tier, supplied by the game each spawn (difficulty lives there). */
    var tier = 0

    private fun ml(l: Int) = if (mirror) 2 - l else l

    /** Start a run: wipe the walk and prefill the whole track down to [spawnZ]. */
    fun reset(coinTrailChance: Float, hue: Float) {
        rows.clear()
        pendingSteps.clear(); pendingSectName = null
        introServed = false; sectsSinceBreather = 0; lastSectId = -99
        curSafe = 1; prevKind = -1; rowsSpawned = 0
        coinRowsLeft = 0; prevPlatLane = -1
        rowsSincePickup = 99; rowsSinceBox = 99; airCoins = false
        this.coinTrailChance = coinTrailChance
        // prefill (first obstacles still arrive within a couple of seconds) using
        // the same gap rules as steady spawning
        var z = -30f
        var zLast = z
        while (z > spawnZ) {
            if (pendingSteps.isEmpty()) loadNextSection()
            spawnStep(pendingSteps.removeFirst(), z, hue)
            zLast = z
            if (pendingSteps.isEmpty()) loadNextSection()
            z -= gapFor(pendingSteps.first())
        }
        spawnAcc = zLast - spawnZ // steady-state spawning continues exactly one gap later
    }

    /** Steady-state spawning: after the world moved [mv], spawn whatever rows are due. */
    fun spawn(mv: Float, hue: Float) {
        spawnAcc += mv
        while (true) {
            if (pendingSteps.isEmpty()) loadNextSection()
            val code = pendingSteps.first()
            val gap = gapFor(code)
            if (spawnAcc >= gap) {
                spawnAcc -= gap
                spawnStep(code, spawnZ, hue)
                pendingSteps.removeFirst()
            } else break
        }
    }

    /** Move every row toward the player, advance the animated obstacles, drop rows that passed. */
    fun scroll(mv: Float, time: Float, dt: Float) {
        var i = rows.size - 1
        while (i >= 0) {
            val row = rows[i]
            row.z += mv
            for (ob in row.obs) {
                if (ob.sliding && row.z > -26f) ob.x += (ob.slideTo - ob.x) * min(1f, dt * ob.slideRate)
                when (ob.anim) {
                    ObAnim.PISTON -> { // sin > 0: up out of the floor (a wall); sin ≤ 0: sunk (run over it)
                        val h = 0.66f * sin(time * 3.4f + ob.phase)
                        ob.clear = h
                        ob.sy = max(0f, h)
                        ob.cy = ob.sy / 2f
                    }
                    ObAnim.SWEEP -> ob.x = sin(time * 2.3f + ob.phase) * laneW * 1.15f
                    ObAnim.STOMP -> { // hangs high most of the cycle, drops fast, lifts again
                        val s = 0.5f + 0.5f * sin(time * 2.6f + ob.phase)
                        val bottom = 0.03f + 1.75f * sqrt(s)
                        ob.clear = bottom
                        ob.cy = bottom + 0.5f
                    }
                }
            }
            if (row.z > 12f) rows.removeAt(i)
            i--
        }
    }

    // ------------------------------------------------------------- director

    /** Distance to leave before the row that's about to spawn. */
    private fun gapFor(code: Int): Float {
        val recover = when {
            prevKind == -1 -> 0f                      // very first row
            Step.isJump(prevKind) -> jumpRecoverGap   // we were airborne — give room to land
            else -> dodgeGap
        }
        return if (code == Step.EM) max(recover, breatherGap) else recover
    }

    /** True while the run is a review (dev cycle or the section explorer): names announced, no pickups. */
    private fun reviewing() = Settings.devMode || Settings.testSection >= 0

    /** Intro first, an occasional breather, else a weighted pick from the unlocked tiers. */
    private fun pickSection(): Sect {
        // section explorer: one chosen section, on loop
        Sections.byId(Settings.testSection)?.let { return it }
        // dev mode: cycle the review pool in order, nothing else mixed in
        if (Settings.devMode) return Sections.devPool[devSectIdx++ % Sections.devPool.size]
        if (!introServed) { introServed = true; return Sections.intro }
        sectsSinceBreather++
        if (sectsSinceBreather >= 5) { sectsSinceBreather = 0; return Sections.breather }
        var pool = Sections.lib.filter { it.tier <= tier && it.id != lastSectId }
        if (pool.isEmpty()) pool = Sections.lib.filter { it.tier <= tier }
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
        if (reviewing()) pendingSectName = s.name // announce when its first row nears the player
        for (c in s.steps) pendingSteps.add(c)
    }

    /** Decode one step code into a row at [z], advancing the walk. */
    private fun spawnStep(code: Int, z: Float, hue: Float) {
        val obs = ArrayList<Ob>(2)
        var platLane = -1
        when {
            code == Step.EM -> { /* open row — a beat of rest */ }
            code == Step.JP -> obs.add(fx.wall(hue))
            code == Step.DK -> obs.add(fx.over(hue))
            code == Step.VD -> fx.addTar(0, 2, hue, obs)
            code == Step.PS -> { // pistons in every lane, phase-staggered: jump, or run over a sunk one
                val base = rowsSpawned * 0.9f
                for (l in 0..2) obs.add(fx.piston(l, hue, base + l * 2.1f))
            }
            code == Step.SW -> obs.add(fx.sweeper(hue, rowsSpawned * 1.3f))
            code in 20..22 -> { // slide: a pillar drifts into a lane (closing gate)
                val target = ml(code - 20)
                if (curSafe == target) curSafe = if (target == 1) (if (rnd.nextBoolean()) 0 else 2) else 1
                val from = when {
                    target == 1 -> if (curSafe == 0) 2 else 0
                    else -> 1
                }
                obs.add(fx.slider(from, target, hue))
            }
            code in 10..12 -> { // feint: a lone pillar, two lanes stay safe
                var block = ml(code - 10)
                if (block == curSafe) block = if (curSafe == 0) 1 else curSafe - 1 // never block where we stand
                obs.add(fx.pillar(fx.laneX(block), hue))
            }
            code in 50..52 -> fx.addSegsExcept(ml(code - 50), hue, obs, over = false) // hurdle window
            code in 60..62 -> fx.addSegsExcept(ml(code - 60), hue, obs, over = true)  // duck window
            code in 70..72 -> { // hurdle gate: the inviting gap is sealed by a pillar — jump the wall
                var block = ml(code - 70)
                if (block == curSafe) block = if (curSafe == 0) 1 else curSafe - 1 // the walk lane keeps its (jumpable) wall
                fx.addSegsExcept(block, hue, obs, over = false)
                obs.add(fx.pillar(fx.laneX(block), hue))
            }
            code in 80..82 -> { // pincer: pillars sweep in from both flanks; the walk moves to the eye
                val safe = walkTo(ml(code - 80))
                for (b in 0..2) {
                    if (b == safe) continue
                    val side = if (b > safe) 1f else -1f
                    obs.add(fx.pillar(fx.laneX(b) + side * laneW * 1.6f, hue, sliding = true, slideTo = fx.laneX(b), slideRate = 2.6f))
                }
            }
            code in 110..112 -> { // stompers: the walk lane is clear, the other two get slamming blocks
                val safe = walkTo(ml(code - 110))
                var k = 0
                for (b in 0..2) {
                    if (b == safe) continue
                    obs.add(fx.stomper(b, hue, rowsSpawned * 0.8f + k * 3.1f)); k++
                }
            }
            code in 120..122 -> fx.addTarExcept(walkTo(ml(code - 120)), hue, obs) // tar window
            code in 130..132 -> { // platform climb: ramp up in the walk lane, pillars everywhere else
                val l = walkTo(ml(code - 130))
                platLane = l
                obs.add(fx.platform(l, hue, dodgeGap, if (prevPlatLane == l) 0f else rampLen))
                fx.addOneOpen(l, hue, obs)
            }
            code in 140..142 -> { // platform: the high road; ramped when it starts or shifts lane
                val l = walkTo(ml(code - 140))
                platLane = l
                obs.add(fx.platform(l, hue, dodgeGap, if (prevPlatLane == l) 0f else rampLen))
            }
            else -> fx.addOneOpen(walkTo(ml(code)), hue, obs)                      // dodge
        }
        val row = Row(z, obs)
        row.safeLane = curSafe
        row.sectName = pendingSectName // first row of a section (dev mode only)
        pendingSectName = null
        layCoins(row, code, platLane)
        layPickup(row, code)
        prevPlatLane = platLane
        rows.add(row)
        rowsSpawned++
        rowsSincePickup++
        rowsSinceBox++
        prevKind = code
    }

    /** Jetpack on: every coin still ahead rises to flying height, and rows without coins get an air line. */
    fun liftCoins(y: Float) {
        for (row in rows) {
            if (row.z > -4f) continue
            val coins = row.coins
            if (coins == null) {
                val x = fx.laneX(row.safeLane)
                row.coins = arrayListOf(Coin(x, y, -1.6f), Coin(x, y, -3.1f), Coin(x, y, -4.6f))
            } else {
                for (c in coins) if (!c.taken) c.y = y
            }
        }
    }

    /** Jetpack off: coins still ahead settle back to where they were laid. */
    fun dropCoins() {
        for (row in rows) {
            if (row.z > -3f) continue
            row.coins?.let { for (c in it) if (!c.taken) c.y = c.restY }
        }
    }

    /** Move the walk toward [wanted], at most one lane; returns the new safe lane. */
    private fun walkTo(wanted: Int): Int {
        curSafe = wanted.coerceIn(curSafe - 1, curSafe + 1).coerceIn(0, 2)
        return curSafe
    }

    /**
     * Coins come in trails of a few rows at a time, always on the walk lane: an
     * arc over jump rows, a low run under duck rows, a line trailing every other row.
     */
    private fun layCoins(row: Row, code: Int, platLane: Int) {
        val x = fx.laneX(curSafe)
        val coins = ArrayList<Coin>(5)
        if (airCoins) { // jetpack: a line at flying height, every row
            for (k in 0 until 3) coins.add(Coin(x, Player.FLY_Y, -1.6f - k * 1.5f))
            row.coins = coins
            return
        }
        if (platLane >= 0) { // the high road always pays: a line along the roof (past the ramp)
            val px = fx.laneX(platLane)
            val y = ObstacleFactory.PLAT_TOP + 0.5f
            if (prevPlatLane != platLane) { coins.add(Coin(px, y, -4.2f)); coins.add(Coin(px, y, -5.6f)) }
            else { coins.add(Coin(px, y, -1.2f)); coins.add(Coin(px, y, -3.0f)); coins.add(Coin(px, y, -4.8f)) }
            row.coins = coins
            return
        }
        if (coinRowsLeft <= 0) {
            if (rnd.nextFloat() < coinTrailChance) coinRowsLeft = 3 + rnd.nextInt(4) else return
        }
        coinRowsLeft--
        when {
            Step.isJump(code) -> { // an arc over the obstacle — collected by the jump that clears it
                coins.add(Coin(x, 1.35f, 1.7f)); coins.add(Coin(x, 1.75f, 0f)); coins.add(Coin(x, 1.35f, -1.7f))
            }
            Step.isDuck(code) -> { // hugging the floor under the bar — collected by the roll
                coins.add(Coin(x, 0.36f, 1.4f)); coins.add(Coin(x, 0.36f, 0f)); coins.add(Coin(x, 0.36f, -1.4f))
            }
            code == Step.EM -> for (k in 0 until 5) coins.add(Coin(x, 0.5f, -k * 1.5f)) // a long free line
            else -> { // a short line just past the row, in the lane you must be in anyway
                coins.add(Coin(x, 0.5f, -1.6f)); coins.add(Coin(x, 0.5f, -3.1f)); coins.add(Coin(x, 0.5f, -4.6f))
            }
        }
        row.coins = coins
    }

    /**
     * A rare pickup on the walk lane, between this row and the next: power-ups
     * (magnet / 2× / jetpack), a bubble, or — much rarer — a mystery box.
     */
    private fun layPickup(row: Row, code: Int) {
        if (reviewing() || rowsSpawned < pickupMinRows || code == Step.EM || Step.isPlatform(code)) return
        if (rowsSincePickup < pickupSpacing) return
        val r = rnd.nextFloat()
        row.pickup = when {
            r < 0.009f -> Pickup.MAGNET
            r < 0.018f -> Pickup.MULT
            r < 0.024f -> Pickup.JET
            r < 0.031f -> Pickup.BUBBLE
            r < 0.034f && rowsSinceBox >= boxSpacing -> Pickup.BOX
            else -> return
        }
        if (row.pickup == Pickup.BOX) rowsSinceBox = 0
        rowsSincePickup = 0
        row.pickupX = fx.laneX(curSafe)
    }
}
