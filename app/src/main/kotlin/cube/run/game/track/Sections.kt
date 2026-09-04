package cube.run.game.track

/**
 * Step codes — the alphabet sections are written in. Decoded by [Track.spawnStep]:
 *   0,1,2    dodge: make `that lane` the only safe one (block the other two)
 *   10..12   feint: a lone pillar (two lanes stay safe) for visual density
 *   20..22   slide: a pillar drifts into that lane (a "closing gate")
 *   JP       jump a low wall     DK  roll under an overhead bar     EM  open row
 *   CF       coin field: an open row with a coin line in EVERY lane (a magnet feast)
 *   VD       tar pit across every lane — be airborne over it
 *   PS       pistons in every lane, pumping out of phase — jump, or time a sunk one
 *   SW       a low sweeper beam gliding across all lanes — jump it
 *   TW       a tall wall across all lanes — only a bounce pad (or a platform) clears it
 *   PM       a pendulum block swinging at chest height — roll under, or step out of its arc
 *   50..52   hurdle window: low walls except that lane — jump, or thread the gap
 *   60..62   duck window: overhead bars except that lane — roll, or sidestep
 *   70..72   hurdle gate: window walls + a pillar sealing the gap — jump, don't thread
 *   80..82   pincer: pillars sweep in from both flanks; that lane is the eye
 *   110..112 stompers: slamming blocks on the two lanes beside that (safe) lane
 *   120..122 tar window: tar pits except that lane — sidestep, or jump
 *   130..132 platform climb: a ramped platform in that lane, pillars in the others — get on top
 *   140..142 platform: a platform segment in that lane (ramped if new), other lanes open — the high road
 *   150..152 bounce pad in that lane (the walk lane), other lanes open — run onto it to launch
 */
object Step {
    fun dg(l: Int) = l
    fun ft(l: Int) = 10 + l
    fun sld(l: Int) = 20 + l
    const val JP = 30
    const val DK = 31
    const val VD = 32
    const val PS = 33
    const val SW = 34
    const val TW = 37
    const val PM = 38
    const val EM = 40
    const val CF = 41
    /** A portal row (the director lays it; never in a section). */
    const val PORTAL = 45
    /** One row of the five-lane bonus road (generated, never in a section). */
    const val WIDE = 46
    fun hw(l: Int) = 50 + l
    fun dw(l: Int) = 60 + l
    fun hg(l: Int) = 70 + l
    fun pn(l: Int) = 80 + l
    fun st(l: Int) = 110 + l
    fun vw(l: Int) = 120 + l
    fun pl(l: Int) = 130 + l
    fun pf(l: Int) = 140 + l
    fun pd(l: Int) = 150 + l

    /** Rows that carry a platform segment. */
    fun isPlatform(code: Int) = code in 130..142

    /** Rows that carry a bounce pad. */
    fun isPad(code: Int) = code in 150..152

    /** Rows whose guaranteed path is a jump (they need landing room afterwards; coins arc over them). */
    fun isJump(code: Int) =
        code == JP || code == VD || code == PS || code == SW || code == TW || code in 50..52 || code in 70..72 || code in 120..122

    /** Rows whose guaranteed path is a roll (coins go low). */
    fun isDuck(code: Int) = code == DK || code == PM || code in 60..62
}

/** A hand-authored pattern: a fixed step sequence, gated by difficulty [tier], weighted for the director's roll. */
class Sect(val id: Int, val tier: Int, val weight: Float, val name: String, val steps: IntArray, val mirrorable: Boolean = true)

/**
 * The section library: a tight set of hand-tuned patterns, each with a
 * clear idea (no near-duplicates); the director ([Track.pickSection]) randomises
 * which appear, their order and mirroring. Spacing/reachability is handled by
 * the lane-walk, so these are pure shapes.
 */
object Sections {
    private val dg = Step::dg; private val ft = Step::ft; private val sld = Step::sld
    private val hw = Step::hw; private val dw = Step::dw; private val hg = Step::hg
    private val pn = Step::pn; private val st = Step::st; private val vw = Step::vw
    private val pl = Step::pl; private val pf = Step::pf; private val pd = Step::pd
    private const val JP = Step.JP; private const val DK = Step.DK; private const val VD = Step.VD
    private const val PS = Step.PS; private const val SW = Step.SW; private const val EM = Step.EM
    private const val CF = Step.CF; private const val TW = Step.TW; private const val PM = Step.PM

    val lib = listOf(
        // --- tier 0: teach, one idea each ---
        Sect(0, 0, 1.3f, "FIRST STEPS", intArrayOf(dg(1), dg(0), dg(1), dg(2), dg(1), dg(0))),           // lane changes
        Sect(1, 0, 1.0f, "WEAVE", intArrayOf(ft(0), ft(2), ft(1), ft(0), ft(2), ft(1))),                 // lone pillars
        Sect(2, 0, 1.0f, "HOP", intArrayOf(JP, dg(1), JP, dg(1), JP)),                                   // jumping
        Sect(12, 0, 0.9f, "HURDLES", intArrayOf(hw(1), ft(2), hw(1), ft(0))),                            // jump or thread
        Sect(57, 0, 0.9f, "SPRINGBOARD", intArrayOf(pd(1), TW, dg(1), pd(1), TW)),                        // meet the pad
        // --- tier 1: lane-walks + simple combos ---
        Sect(3, 1, 1.2f, "SLALOM", intArrayOf(dg(0), dg(1), dg(2), dg(1), dg(0), dg(1), dg(2))),
        Sect(5, 1, 1.1f, "LEAP & WEAVE", intArrayOf(JP, dg(0), dg(2), JP, dg(1), dg(0))),
        Sect(6, 1, 0.9f, "CLOSING GATES", intArrayOf(sld(1), sld(0), sld(2), sld(1))),
        Sect(7, 1, 0.9f, "DUCK & DODGE", intArrayOf(dg(0), DK, dg(2), dg(1), DK)),
        Sect(13, 1, 1.0f, "STAIRCASE", intArrayOf(dg(0), JP, dg(1), JP, dg(2))),                         // climb across on hops
        Sect(15, 1, 0.9f, "SKYLIGHTS", intArrayOf(dw(1), ft(2), dw(0), ft(1), dw(2))),                   // roll or sidestep
        Sect(24, 1, 1.0f, "TAR PITS", intArrayOf(vw(1), dg(1), VD, dg(0), vw(0))),                       // sidestep or hop the voids
        Sect(25, 1, 0.9f, "PUMP HOUSE", intArrayOf(PS, dg(1), PS, dg(2), PS)),                           // pistons: jump, or time it
        Sect(32, 1, 1.0f, "HIGH ROAD", intArrayOf(pl(1), pf(1), pf(1), pf(1), dg(1))),                   // climb the ramp, ride the roof
        Sect(38, 1, 0.9f, "RAMP UP", intArrayOf(pl(2), pf(2), dg(1), pl(0), pf(0), dg(1))),              // two short roofs
        Sect(47, 1, 0.8f, "GOLD RUSH", intArrayOf(CF, CF, CF, dg(1), CF, CF)),                            // coins in every lane
        Sect(48, 1, 0.9f, "LOW BRIDGE", intArrayOf(DK, dg(0), DK, dg(2), DK)),                            // roll, step, roll
        Sect(60, 1, 0.9f, "SWING SET", intArrayOf(PM, dg(1), PM, dg(0))),                                 // roll under the swing
        Sect(61, 1, 0.9f, "BOUNCE HOUSE", intArrayOf(pd(0), TW, dg(0), pd(2), TW, dg(2))),                // pads at the edges
        Sect(62, 1, 0.8f, "HIGH JUMP", intArrayOf(pd(1), TW, dg(1), dg(0))),                              // a pad, a wall, and on
        // --- tier 2: dense, verb-switching ---
        Sect(8, 2, 1.2f, "GAUNTLET", intArrayOf(dg(0), JP, dg(2), dg(1), DK, dg(0), JP, dg(2))),
        Sect(10, 2, 0.9f, "STORM", intArrayOf(JP, dg(0), dg(2), DK, dg(1), JP, dg(0))),
        Sect(11, 2, 0.9f, "TRAPS", intArrayOf(sld(2), sld(1), sld(0), dg(1), dg(2))),
        Sect(16, 2, 1.0f, "THE WAVE", intArrayOf(JP, DK, JP, DK, JP)),                                   // jump-roll metronome
        Sect(17, 2, 0.9f, "TUNNEL", intArrayOf(DK, dg(1), DK, dg(0), DK)),
        Sect(19, 2, 0.8f, "PEEKABOO", intArrayOf(hw(0), dw(2), hw(1), dw(0), hw(2))),                    // shifting windows
        Sect(26, 2, 1.0f, "STOMP", intArrayOf(st(1), st(0), st(1), st(2), st(1))),                       // slamming blocks flank the walk
        Sect(27, 2, 0.9f, "SWEEPERS", intArrayOf(SW, dg(1), SW, dg(0), SW)),                             // gliding beams — hop them
        Sect(28, 2, 0.9f, "CHASM RUN", intArrayOf(VD, dg(0), VD, dg(2), VD, dg(1))),                     // pit, dodge, pit
        Sect(33, 2, 1.0f, "ROOFTOPS", intArrayOf(pl(0), pf(0), pf(1), pf(1), pf(2), pf(2), dg(2))),      // hop roof to roof
        Sect(34, 2, 0.9f, "OVERPASS", intArrayOf(pf(2), pf(2), pf(2), dg(1), pf(0), pf(0), pf(0), dg(1))), // optional side roads
        Sect(40, 2, 1.0f, "SKYBRIDGE", intArrayOf(pl(1), pf(1), pf(1), pf(1), pf(1), pf(1), dg(1))),     // one long roof
        Sect(42, 2, 0.8f, "TRAPDOORS", intArrayOf(vw(1), vw(0), vw(2), dg(1), VD)),                      // tar everywhere
        Sect(50, 2, 0.8f, "TREASURY", intArrayOf(dg(0), CF, CF, hw(1), CF, CF, dg(2))),                   // a vault between two doors
        Sect(52, 2, 0.8f, "TAR & FEATHER", intArrayOf(vw(0), vw(2), vw(1), JP, dg(1))),                   // hop the pits, then the wall
        Sect(65, 2, 0.9f, "TRAMPOLINE", intArrayOf(pd(1), TW, pd(1), TW, dg(1))),                         // bounce, bounce
        Sect(66, 2, 0.8f, "ROOF HOP", intArrayOf(pl(1), pf(1), pf(1), dg(1), dg(0))),                     // drop off the roof onto a pillar row
        Sect(68, 2, 0.8f, "PENDULUMS", intArrayOf(PM, PM, dg(0), PM, PM)),                                // swing after swing
        // --- tier 3: expert set pieces ---
        Sect(20, 3, 1.1f, "PINCER", intArrayOf(pn(1), dg(1), pn(2), dg(2), pn(1))),                      // converging crushers
        Sect(21, 3, 1.0f, "GATEKEEPER", intArrayOf(hg(0), dg(1), hg(2), dg(1), hg(0))),                  // sealed gaps — jump it
        Sect(23, 3, 0.9f, "BLENDER", intArrayOf(sld(1), JP, dw(0), pn(2), DK, hw(1))),                   // every trick at once
        Sect(29, 3, 1.0f, "MACHINE ROOM", intArrayOf(st(0), PS, st(2), SW, st(1), VD)),                  // every moving part
        Sect(30, 3, 0.9f, "HELLRIDE", intArrayOf(vw(0), st(1), pn(1), PS, hg(2), SW)),                   // no two alike
        Sect(35, 3, 1.0f, "TRAIN YARD", intArrayOf(pl(1), pf(1), pf(0), pf(0), pf(1), pf(1), pf(2), pf(2), pf(1), dg(1))), // a long weaving ride
        Sect(43, 3, 0.9f, "CRUSH HOUR", intArrayOf(pn(1), st(1), pn(0), st(0), pn(2))),                  // pincers and stompers
        Sect(44, 3, 0.9f, "ROOF RUNNER", intArrayOf(pl(0), pf(0), pf(1), pf(2), pf(1), pf(0), dg(0))),   // weave across the roofs
        Sect(45, 3, 0.8f, "LEAPFROG", intArrayOf(hw(1), hw(0), hw(2), hw(1), JP)),                       // windows keep moving
        Sect(46, 3, 0.8f, "FINALE", intArrayOf(PS, st(1), VD, hg(1), SW, pn(1), pl(1), pf(1), dg(1))),   // everything, once
        Sect(53, 3, 0.7f, "COIN CANYON", intArrayOf(pn(1), CF, st(1), CF, pn(1), CF)),                    // riches between the crushers
        Sect(55, 3, 0.8f, "GAUNTLET II", intArrayOf(hg(1), pn(1), dw(1), hg(0), pn(2), dw(2))),           // gates, pincers, bars
        Sect(56, 3, 0.5f, "MOTHERLODE", intArrayOf(CF, CF, CF, CF, CF, CF)),                              // rare: a solid field of gold
        Sect(70, 3, 0.8f, "BIG AIR", intArrayOf(pd(1), TW, dg(1), pd(0), TW, pn(1))),                     // bounce over a pincer
        Sect(72, 3, 0.8f, "GRANDFATHER", intArrayOf(PM, st(1), PM, pn(1), dg(1), PM)),                    // the clock strikes
    )

    /** Dev mode reviews the newest sections back to back (no intro, no breathers). */
    val devPool = lib.filter { it.id >= 57 }

    /** The Zero-G world: only pillar rows (nothing to jump or roll under while you hover). */
    val floatPool = lib.filter { s -> s.steps.all { it in 0..22 || it == EM || it == CF } }

    fun byId(id: Int): Sect? = lib.firstOrNull { it.id == id }

    // preview cell kinds (for the section explorer's thumbnails)
    const val C_EMPTY = 0
    const val C_PILLAR = 1
    const val C_WALL = 2
    const val C_BAR = 3
    const val C_TAR = 4
    const val C_PISTON = 5
    const val C_SWEEP = 6
    const val C_STOMP = 7
    const val C_SLIDER = 8
    const val C_PLAT = 9
    const val C_PINCER = 10
    const val C_COIN = 11
    const val C_PAD = 12
    const val C_TALL = 13
    const val C_PENDULUM = 14

    /**
     * A static picture of a section: one 3-lane row of cell kinds per step,
     * decoded literally (no lane-walk state, no mirroring) — enough to tell
     * sections apart at a glance.
     */
    fun preview(s: Sect): List<IntArray> = s.steps.map { code ->
        val row = IntArray(3)
        fun allBut(l: Int, kind: Int) { for (k in 0..2) if (k != l) row[k] = kind }
        fun all(kind: Int) { for (k in 0..2) row[k] = kind }
        when {
            code == Step.EM -> {}
            code == Step.CF -> all(C_COIN)
            code == Step.JP -> all(C_WALL)
            code == Step.DK -> all(C_BAR)
            code == Step.VD -> all(C_TAR)
            code == Step.PS -> all(C_PISTON)
            code == Step.SW -> all(C_SWEEP)
            code == Step.TW -> all(C_TALL)
            code == Step.PM -> all(C_PENDULUM)
            code in 0..2 -> allBut(code, C_PILLAR)
            code in 10..12 -> row[code - 10] = C_PILLAR
            code in 20..22 -> row[code - 20] = C_SLIDER
            code in 50..52 -> allBut(code - 50, C_WALL)
            code in 60..62 -> allBut(code - 60, C_BAR)
            code in 70..72 -> { allBut(code - 70, C_WALL); row[code - 70] = C_PILLAR }
            code in 80..82 -> allBut(code - 80, C_PINCER)
            code in 110..112 -> allBut(code - 110, C_STOMP)
            code in 120..122 -> allBut(code - 120, C_TAR)
            code in 130..132 -> { allBut(code - 130, C_PILLAR); row[code - 130] = C_PLAT }
            code in 140..142 -> row[code - 140] = C_PLAT
            code in 150..152 -> row[code - 150] = C_PAD
        }
        row
    }

    /** Teaches the controls: lone side pillars, centre always safe. */
    val intro = Sect(-2, 0, 0f, "WARM-UP", intArrayOf(ft(0), ft(2), ft(0), ft(2)), mirrorable = false)

    /** An occasional short breather: a single open row. */
    val breather = Sect(-1, 0, 0f, "BREATHER", intArrayOf(EM), mirrorable = false)

    /** Section tier unlocked at difficulty [diff] (0..1). Tier 3 sits just past a maxed fire boost. */
    fun tierFor(diff: Float): Int = when {
        diff < 0.18f -> 0
        diff < 0.38f -> 1
        diff < 0.60f -> 2
        else -> 3
    }
}
