package cube.run.game.track

import cube.run.data.Bonus
import cube.run.data.Progress
import cube.run.data.Settings
import cube.run.game.Lanes
import cube.run.game.Player
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
 * Portals: every so often a portal row is laid; passing it puts the track
 * into a bonus world ([bonus]) for a stretch — a five-lane road, floating
 * pillars, or the normal library over rolling hills / shifting colours —
 * then an exit portal brings the normal road back.
 *
 * Owns the rows' motion and obstacle animation ([scroll]); collision and
 * scoring stay in the game.
 */
class Track(private val rnd: Random, private val fx: ObstacleFactory) {

    val rows = ArrayList<Row>()

    // row spacing (world units). Tight by default = dense; wider after a jump so you can land.
    private val dodgeGap = 6.5f
    private val jumpRecoverGap = 9.5f
    private val breatherGap = 12f
    /** Rows are born here, fully fogged, and fade in. */
    val spawnZ = -100f

    // ---- lane-walk / director state ----
    private val pendingSteps = ArrayDeque<Int>()
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
    private var coinTrailChance = 0.16f  // chance per row to start a new coin trail
    private val pickupMinRows = 24       // no pickups in the opening rows
    private val pickupSpacing = 14       // rows between any two pickups
    private val boxSpacing = 90          // rows between mystery boxes (rare on purpose)
    private var rowsSincePickup = 99
    private var rowsSinceBox = 99
    /** While the jetpack is on, new coin trails are laid at flying height. */
    @Volatile var airCoins = false
    /**
     * Jetpack: the z that reaches the player the moment the flight ends, and how
     * long (in world units) the glide down before it lasts. Coins ahead of the
     * end fly, coins along the glide descend with you, coins beyond it are laid
     * on the ground — so the line itself announces the landing.
     */
    var jetEndZ = 0f
    var jetGlideLen = 0f

    // ---- portals / bonus worlds ----
    /** The bonus world the road is currently in (Bonus.NONE outside one). */
    var bonus = Bonus.NONE
        private set
    private var bonusRowsLeft = 0
    private var rowsSincePortal = 0
    private var portalPending = Bonus.NONE   // a portal row is queued for the next spawn (-2 = the exit)
    private var wideSafe = 2                 // the walk in the five-lane world
    /** Unlocked bonus worlds a portal may open to (set by the game from the best score). */
    var portalPool: List<Int> = emptyList()
    /** Rows between portals (the Portal luck perk shortens it). */
    var portalEvery = 110

    /** Unlocked tier, supplied by the game each spawn (difficulty lives there). */
    var tier = 0

    private fun ml(l: Int) = if (mirror) 2 - l else l

    /** Start a run: wipe the walk and prefill the track. */
    fun reset(coinTrailChance: Float, hue: Float) {
        rows.clear()
        pendingSteps.clear()
        introServed = false; sectsSinceBreather = 0; lastSectId = -99
        curSafe = 1; prevKind = -1; rowsSpawned = 0
        coinRowsLeft = 0; prevPlatLane = -1
        rowsSincePickup = 99; rowsSinceBox = 99; airCoins = false
        bonus = Bonus.NONE; bonusRowsLeft = 0; rowsSincePortal = 0; portalPending = Bonus.NONE
        this.coinTrailChance = coinTrailChance
        var z = -38f
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

    /** Move every row toward the player, stream it in, advance the animated obstacles, drop rows that passed. */
    fun scroll(mv: Float, time: Float, dt: Float) {
        var i = rows.size - 1
        while (i >= 0) {
            val row = rows[i]
            row.z += mv
            if (row.popStart < 0f) { if (row.z > Row.POP_Z) row.popStart = time }
            if (row.popStart >= 0f && row.pop < 1f) { // spring in with a little overshoot
                val t = ((time - row.popStart) / 0.42f).coerceIn(0f, 1f)
                val k = 1f - t
                row.pop = if (t >= 1f) 1f else 1f - k * k * kotlin.math.cos(t * 5.2f)
            }
            for (ob in row.obs) {
                if (ob.sliding && row.z > -26f) ob.x += (ob.slideTo - ob.x) * min(1f, dt * ob.slideRate)
                when (ob.anim) {
                    ObAnim.PISTON -> { // sin > 0: up out of the floor (a wall); sin ≤ 0: sunk (run over it)
                        val h = 0.66f * sin(time * 3.4f + ob.phase)
                        ob.sy = max(0f, h)
                        ob.cy = ob.sy / 2f
                    }
                    ObAnim.SWEEP -> ob.x = sin(time * 2.3f + ob.phase) * Lanes.w * 1.15f
                    ObAnim.STOMP -> { // hangs high most of the cycle, drops fast, lifts again
                        val s = 0.5f + 0.5f * sin(time * 2.6f + ob.phase)
                        val bottom = 0.03f + 1.75f * sqrt(s)
                        ob.cy = bottom + 0.5f
                    }
                    ObAnim.PENDULUM -> ob.x = sin(time * 2.4f + ob.phase) * Lanes.w * 1.15f
                }
            }
            if (row.z > 12f) rows.removeAt(i)
            i--
        }
    }

    // ------------------------------------------------------------- portals

    /** The game passed a portal row: the road changes here. Returns the bonus entered, or NONE on the way out. */
    fun crossPortal(row: Row): Int {
        val id = row.portal
        row.portal = Bonus.NONE
        if (row.portalExit) {
            Lanes.count = 3; Lanes.targetW = Lanes.NORMAL_W
            curSafe = curSafe.coerceIn(0, 2)
            return Bonus.NONE
        }
        when (id) {
            Bonus.WIDE -> { Lanes.count = 5; Lanes.targetW = Lanes.NORMAL_W }
            Bonus.FLOAT -> { Lanes.count = 3; Lanes.targetW = 2.6f }
            else -> { Lanes.count = 3; Lanes.targetW = Lanes.NORMAL_W }
        }
        return id
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

    /** The section explorer alone shows the section bare: no pickups. Dev mode showers them instead. */
    private fun noPickups() = Settings.testSection >= 0 && !Settings.devMode

    /** Intro first, an occasional breather, else a weighted pick from the unlocked tiers (bonus worlds have their own pools). */
    private fun pickSection(): Sect {
        Sections.byId(Settings.testSection)?.let { return it }
        if (Settings.devMode && bonus == Bonus.NONE) return Sections.devPool[devSectIdx++ % Sections.devPool.size]
        if (!introServed) { introServed = true; return Sections.intro }
        sectsSinceBreather++
        if (sectsSinceBreather >= 5) { sectsSinceBreather = 0; return Sections.breather }
        var pool = when (bonus) {
            Bonus.FLOAT -> Sections.floatPool.filter { it.id != lastSectId }
            Bonus.HILLS, Bonus.KALEIDO -> Sections.lib.filter { it.tier <= max(1, tier) && it.id != lastSectId }
            else -> Sections.lib.filter { it.tier <= tier && it.id != lastSectId }
        }
        if (pool.isEmpty()) pool = Sections.lib.filter { it.tier <= tier }
        var total = 0f; for (s in pool) total += s.weight
        var r = rnd.nextFloat() * total
        var chosen = pool[pool.size - 1]
        for (s in pool) { r -= s.weight; if (r <= 0f) { chosen = s; break } }
        lastSectId = chosen.id
        return chosen
    }

    private fun loadNextSection() {
        // a portal is due: one row of it, then the world changes for the rows behind it
        if (bonus == Bonus.NONE && portalPending == Bonus.NONE && portalPool.isNotEmpty() && !noPickups() &&
            rowsSpawned > 40 && rowsSincePortal >= portalEvery) {
            portalPending = portalPool[rnd.nextInt(portalPool.size)]
            pendingSteps.add(Step.PORTAL)
            return
        }
        if (bonus != Bonus.NONE && bonusRowsLeft <= 0 && portalPending == Bonus.NONE) { // time to leave
            portalPending = -2
            pendingSteps.add(Step.PORTAL)
            return
        }
        if (bonus == Bonus.WIDE) { // the five-lane world writes itself: a wandering corridor
            repeat(6) { pendingSteps.add(Step.WIDE) }
            return
        }
        val s = pickSection()
        mirror = s.mirrorable && rnd.nextBoolean()
        for (c in s.steps) pendingSteps.add(c)
    }

    /** Decode one step code into a row at [z], advancing the walk. */
    private fun spawnStep(code: Int, z: Float, hue: Float) {
        val obs = ArrayList<Ob>(2)
        var platLane = -1
        if (code == Step.PORTAL) { // the doorway: an open row that flips the world when crossed
            val row = Row(z, obs)
            if (portalPending == -2) { row.portalExit = true; row.portal = bonus; bonus = Bonus.NONE; rowsSincePortal = 0 }
            else { row.portal = portalPending; bonus = portalPending; bonusRowsLeft = 46; wideSafe = 2 }
            portalPending = Bonus.NONE
            row.safeLane = curSafe
            rows.add(row)
            rowsSpawned++; rowsSincePickup++; rowsSinceBox++
            prevKind = Step.EM
            return
        }
        if (code == Step.WIDE) { // five lanes: pillars everywhere but a two-lane corridor that wanders
            val n = 5
            wideSafe = (wideSafe + rnd.nextInt(3) - 1).coerceIn(0, n - 1)
            val open2 = if (wideSafe == n - 1) wideSafe - 1 else wideSafe + 1
            for (l in 0 until n) if (l != wideSafe && l != open2 && rnd.nextFloat() < 0.85f) obs.add(fx.pillar((l - 2) * Lanes.NORMAL_W, hue))
            curSafe = wideSafe.coerceIn(0, 2)
            val row = Row(z, obs)
            row.safeLane = wideSafe
            val list = ArrayList<Coin>(3)
            val cx = (wideSafe - 2) * Lanes.NORMAL_W
            for (k in 0 until 3) list.add(Coin(cx, 0.5f, -1.6f - k * 1.5f))
            row.coins = list
            rows.add(row)
            rowsSpawned++; rowsSincePickup++; rowsSinceBox++
            bonusRowsLeft--
            prevKind = code
            return
        }
        when {
            code == Step.EM -> { /* open row — a beat of rest */ }
            code == Step.CF -> { /* open row — coins in every lane (layCoins) */ }
            code == Step.JP -> obs.add(fx.wall(hue))
            code == Step.DK -> obs.add(fx.over(hue))
            code == Step.VD -> fx.addTar(0, 2, hue, obs)
            code == Step.PS -> { // pistons in every lane, phase-staggered: jump, or run over a sunk one
                val base = rowsSpawned * 0.9f
                for (l in 0..2) obs.add(fx.piston(l, hue, base + l * 2.1f))
            }
            code == Step.SW -> obs.add(fx.sweeper(hue, rowsSpawned * 1.3f))
            code == Step.TW -> fx.tallWall(hue, obs)
            code == Step.PM -> obs.add(fx.pendulum(hue, rowsSpawned * 1.5f))
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
                if (block == curSafe) block = if (curSafe == 0) 1 else curSafe - 1
                fx.addSegsExcept(block, hue, obs, over = false)
                obs.add(fx.pillar(fx.laneX(block), hue))
            }
            code in 80..82 -> { // pincer: pillars sweep in from both flanks; the walk moves to the eye
                val safe = walkTo(ml(code - 80))
                for (b in 0..2) {
                    if (b == safe) continue
                    val side = if (b > safe) 1f else -1f
                    obs.add(fx.pillar(fx.laneX(b) + side * Lanes.w * 1.6f, hue, sliding = true, slideTo = fx.laneX(b), slideRate = 2.6f))
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
            code in 150..152 -> obs.add(fx.pad(walkTo(ml(code - 150)), hue))          // bounce pad on the walk lane
            else -> fx.addOneOpen(walkTo(ml(code)), hue, obs)                          // dodge
        }
        val row = Row(z, obs)
        row.safeLane = curSafe
        layCoins(row, code, platLane)
        layPickup(row, code)
        prevPlatLane = platLane
        rows.add(row)
        rowsSpawned++
        rowsSincePickup++
        rowsSinceBox++
        rowsSincePortal++
        if (bonus != Bonus.NONE) bonusRowsLeft--
        prevKind = code
    }

    /**
     * Height of the jet coin line at world z: cruising height ahead of the
     * glide, sloping down along it, ground level past the landing point.
     */
    private fun jetY(z: Float, restY: Float): Float {
        val glideStart = jetEndZ + jetGlideLen
        return when {
            z >= glideStart -> Player.FLY_Y
            z >= jetEndZ -> restY + (Player.FLY_Y - restY) * ((z - jetEndZ) / max(0.01f, jetGlideLen))
            else -> restY
        }
    }

    /** Jetpack on: every coin ahead rises onto the flight line (cruise, glide or ground, by where it will be met). */
    fun liftCoins() {
        for (row in rows) {
            if (row.z > -4f) continue
            val coins = row.coins
            if (coins == null) {
                val x = fx.laneX(row.safeLane.coerceIn(0, 2))
                val list = ArrayList<Coin>(3)
                for (k in 0 until 3) {
                    val dz = -1.6f - k * 1.5f
                    list.add(Coin(x, jetY(row.z + dz, 0.5f), dz, restY = 0.5f))
                }
                row.coins = list
            } else {
                for (c in coins) if (!c.taken) c.y = jetY(row.z + c.dz, c.restY)
            }
        }
    }

    /** Jetpack off: anything still in the air ahead settles back to where it belongs. */
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
        val hover = if (bonus == Bonus.FLOAT) Player.HOVER_Y - 0.45f else 0f
        if (airCoins) { // jetpack: a line every row — cruising, gliding down, or already on the ground
            for (k in 0 until 3) {
                val dz = -1.6f - k * 1.5f
                coins.add(Coin(x, jetY(row.z + dz, 0.5f), dz, restY = 0.5f))
            }
            row.coins = coins
            return
        }
        if (code == Step.CF) { // coin field: a long line in every lane
            for (l in 0..2) { val lx = fx.laneX(l); for (k in 0 until 4) coins.add(Coin(lx, 0.5f + hover, -k * 1.5f)) }
            row.coins = coins
            return
        }
        if (Step.isPad(code)) { // the bounce always pays: a high arc after the pad
            coins.add(Coin(x, 1.6f, -2.2f)); coins.add(Coin(x, 2.5f, -3.8f)); coins.add(Coin(x, 2.9f, -5.4f)); coins.add(Coin(x, 2.5f, -7.0f))
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
        if (code == Step.TW) return // you're mid-bounce here: the pad's arc already covers it
        if (coinRowsLeft <= 0) {
            val chance = if (bonus != Bonus.NONE) 1f else coinTrailChance
            if (rnd.nextFloat() < chance) coinRowsLeft = 3 + rnd.nextInt(4) else return
        }
        coinRowsLeft--
        when {
            Step.isJump(code) -> { // an arc over the obstacle — collected by the jump that clears it
                coins.add(Coin(x, 1.35f, 1.7f)); coins.add(Coin(x, 1.75f, 0f)); coins.add(Coin(x, 1.35f, -1.7f))
            }
            Step.isDuck(code) -> { // hugging the floor under the bar — collected by the roll
                coins.add(Coin(x, 0.36f, 1.4f)); coins.add(Coin(x, 0.36f, 0f)); coins.add(Coin(x, 0.36f, -1.4f))
            }
            code == Step.EM -> for (k in 0 until 5) coins.add(Coin(x, 0.5f + hover, -k * 1.5f)) // a long free line
            else -> { // a short line just past the row, in the lane you must be in anyway
                coins.add(Coin(x, 0.5f + hover, -1.6f)); coins.add(Coin(x, 0.5f + hover, -3.1f)); coins.add(Coin(x, 0.5f + hover, -4.6f))
            }
        }
        row.coins = coins
    }

    /**
     * A rare pickup on the walk lane, between this row and the next: power-ups
     * (magnet / 2× / jetpack), a bubble, or — much rarer — a mystery box.
     */
    private fun layPickup(row: Row, code: Int) {
        if (noPickups() || code == Step.EM || Step.isPlatform(code) || Step.isPad(code) || code == Step.TW || bonus == Bonus.FLOAT) return
        val galore = Settings.devMode // dev mode: a pickup every few rows, boxes included, so everything can be tried
        if (!galore && (code == Step.CF || rowsSpawned < pickupMinRows || rowsSincePickup < pickupSpacing)) return
        if (galore && rowsSincePickup < 3) return
        val r = rnd.nextFloat()
        val boxLuck = 1f + 0.5f * Progress.level(Progress.LUCKYBOX)
        row.pickup = when {
            galore -> when { r < 0.25f -> Pickup.BOX; r < 0.45f -> Pickup.BUBBLE; r < 0.65f -> Pickup.JET; r < 0.83f -> Pickup.MAGNET; else -> Pickup.MULT }
            r < 0.009f -> Pickup.MAGNET
            r < 0.018f -> Pickup.MULT
            r < 0.024f -> Pickup.JET
            r < 0.031f -> Pickup.BUBBLE
            r < 0.031f + 0.003f * boxLuck && rowsSinceBox >= (boxSpacing / boxLuck).toInt() -> Pickup.BOX
            else -> return
        }
        if (row.pickup == Pickup.BOX) rowsSinceBox = 0
        rowsSincePickup = 0
        row.pickupX = fx.laneX(curSafe)
    }
}
