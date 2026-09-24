package cube.run.game

import kotlin.math.floor
import kotlin.math.ln1p
import kotlin.random.Random

/** Per-run Gambler tickets. Values already include Rich Coins and other coin multipliers. */
class Lottery(private val random: Random = Random.Default, coinChance: Double = COIN_CHANCE) {
    init { require(coinChance > 0.0 && coinChance < 1.0) }
    private val logMiss = ln1p(-coinChance)
    private var fractionalMicros = 0L
    private var ticketsUntilWin = 0.0

    /** Ordinary coins are forfeited; returns only jackpots earned by their weighted tickets. */
    fun collectCoin(value: Float): Int {
        if (!value.isFinite() || value <= 0f) return 0
        // Catalogue multipliers are decimal values; Float's 1.4 is slightly
        // smaller than 1.4. Fixed micro-coins keep ten such pickups at 14 tickets.
        val micros = Math.rint(value.toDouble() * MICROS_PER_COIN)
        var tickets = floor(micros / MICROS_PER_COIN)
        fractionalMicros += (micros % MICROS_PER_COIN).toLong()
        tickets += (fractionalMicros / MICROS_PER_COIN).toDouble()
        fractionalMicros %= MICROS_PER_COIN
        var wins = 0
        while (tickets >= 1.0) {
            if (ticketsUntilWin < 1.0) ticketsUntilWin = nextWin()
            if (tickets < ticketsUntilWin) {
                ticketsUntilWin -= tickets
                break
            }
            tickets -= ticketsUntilWin
            ticketsUntilWin = 0.0
            wins++
            // Bound both the payout and the work for corrupt/unreasonably large inputs.
            // Actual gameplay values are many orders of magnitude below this limit.
            if (wins == MAX_WINS_PER_PICKUP) return MAX_WINS_PER_PICKUP * JACKPOT
        }
        return wins * JACKPOT
    }

    /** A collected mystery box is forfeited even when its lottery ticket loses. */
    fun collectBox(): Int = if (random.nextDouble() < BOX_CHANCE) JACKPOT else 0

    fun reset() {
        fractionalMicros = 0L
        ticketsUntilWin = 0.0
    }

    // Geometric waiting time is exactly the same distribution as independent rolls
    // for each coin, including multiple jackpots in one weighted pickup. Skipping
    // losing tickets avoids doing thousands of random rolls for large multipliers.
    private fun nextWin(): Double = floor(ln1p(-random.nextDouble()) / logMiss) + 1.0

    companion object {
        private const val MICROS_PER_COIN = 1_000_000L
        const val JACKPOT = 250_000
        const val COIN_CHANCE = 1.0 / 100_000.0
        const val BOX_CHANCE = .013
        const val DEV_COIN_CHANCE = .02
        private const val MAX_WINS_PER_PICKUP = Int.MAX_VALUE / JACKPOT
    }
}
