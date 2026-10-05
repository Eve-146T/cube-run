package cube.run.game.space

import cube.run.game.track.Sect
import cube.run.game.track.Step

/**
 * The Outer Space section library. Space has its own patterns — boulders
 * beside the walk lane, rifts in the floating road you float across, pads
 * that carry you over tall hulls, comet wakes to weave through — drawn with
 * the ordinary obstacles. Written for low gravity: jumps hang long, so the
 * patterns breathe more than the ordinary library.
 */
object SpaceSections {
    private val dg = Step::dg; private val ft = Step::ft; private val sld = Step::sld
    private val pn = Step::pn; private val vw = Step::vw; private val pl = Step::pl
    private val pf = Step::pf; private val mt = Step::mt; private val rg = Step::rg
    private const val JP = Step.JP; private const val DK = Step.DK; private const val SW = Step.SW
    private const val PM = Step.PM; private const val EM = Step.EM; private const val RF = Step.RF
    private const val HW = Step.HW; private const val CT = Step.CT

    /** The first stretch through the portal: a calm wake, then one easy float to feel the gravity. */
    val intro = Sect(-20, 0, 1f, "LIFTOFF", intArrayOf(EM, CT, CT, RF, RF, RF, CT, EM), mirrorable = false)

    val pool = listOf(
        // --- tier 0: one space idea each ---
        Sect(200, 0, 1.1f, "STARFALL", intArrayOf(mt(1), CT, mt(0), dg(0), mt(2))),                 // meteors land ahead
        Sect(201, 0, 1.1f, "MOON HOPS", intArrayOf(RF, RF, RF, dg(1), RF, RF, RF, ft(0))),           // float the rifts
        Sect(202, 0, 1.0f, "ASTEROID FIELD", intArrayOf(ft(0), ft(2), ft(1), sld(1), sld(2), ft(0))), // drifting rocks
        Sect(203, 0, 0.8f, "COMET WAKE", intArrayOf(CT, CT, CT, CT, EM)),                            // a calm weave of coins
        Sect(204, 0, 1.0f, "GRAVITY WELL", intArrayOf(rg(1), HW, dg(1), rg(0), HW, dg(0))),          // ring over the hull
        // --- tier 1: mixed verbs ---
        Sect(205, 1, 1.0f, "DEBRIS BELT", intArrayOf(pn(1), dg(1), pn(0), CT, pn(2))),               // rocks close in
        Sect(206, 1, 1.0f, "STATION PASS", intArrayOf(DK, dg(0), JP, dg(2), DK, JP)),                // girders and fences
        Sect(207, 1, 0.9f, "EVENT HORIZON", intArrayOf(vw(1), RF, RF, RF, vw(0), dg(1))),            // black holes and a rift
        Sect(208, 1, 0.9f, "SOLAR ARRAY", intArrayOf(SW, dg(1), PM, dg(0), SW)),                     // sweeping panels
        Sect(209, 1, 0.9f, "MOONWALK", intArrayOf(pl(1), pf(1), pf(0), pf(0), dg(0), mt(1))),        // across the moon rocks
        // --- tier 2: storms ---
        Sect(210, 2, 1.0f, "METEOR STORM", intArrayOf(mt(0), mt(1), mt(2), RF, RF, RF, mt(1))),
        Sect(211, 2, 0.9f, "SLINGSHOT", intArrayOf(rg(1), HW, dg(1), RF, RF, RF, mt(0))),
        Sect(212, 2, 0.9f, "DEEP FIELD", intArrayOf(sld(0), mt(2), pn(1), RF, RF, CT, mt(1))),
    )

    fun byId(id: Int): Sect? = if (id == intro.id) intro else pool.firstOrNull { it.id == id }

    /** Sections open at difficulty tier [tier]; space is a reward, so tier 1 is always open. */
    fun open(tier: Int, last: Int): List<Sect> = pool.filter { it.tier <= maxOf(1, tier) && it.id != last }
}
