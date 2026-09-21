package cube.run.game

import kotlin.math.exp
import kotlin.math.ln1p
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deterministic odds boundaries; no statistical tolerances or game/GL state. */
class LotteryRulesTest {
    private class Rolls(vararg values: Double, private val fallback: Double = .99) : Random() {
        private val values = values.toList()
        var calls = 0
        override fun nextBits(bitCount: Int): Int = error("Only nextDouble is used")
        override fun nextDouble(): Double = values.getOrElse(calls++) { fallback }
    }

    // Pick the midpoint of the geometric CDF interval for a win on exactly ticket N.
    private fun winOn(ticket: Int): Double = -Math.expm1((ticket - .5) * ln1p(-Lottery.COIN_CHANCE))

    @Test fun developerCoinChanceIsTwoPercentAndBoxesStayUnchanged() {
        assertEquals(Lottery.JACKPOT, Lottery(Rolls(.019999), Lottery.DEV_COIN_CHANCE).collectCoin(1f))
        assertEquals(0, Lottery(Rolls(.02), Lottery.DEV_COIN_CHANCE).collectCoin(1f))
        assertEquals(0, Lottery(Rolls(.019999)).collectCoin(1f))
        val dev = Lottery(Rolls(.013), Lottery.DEV_COIN_CHANCE)
        assertEquals(0, dev.collectBox())
        val ticketThree = -Math.expm1(2.5 * ln1p(-Lottery.DEV_COIN_CHANCE))
        assertEquals(Lottery.JACKPOT, Lottery(Rolls(ticketThree), Lottery.DEV_COIN_CHANCE).collectCoin(3f))
    }

    @Test fun weightedRichCoinsBuyExactlyAsManyIndependentTicketsAsTheirValue() {
        val ordinary = Lottery(Rolls(winOn(3)))
        assertEquals(0, ordinary.collectCoin(1f))
        assertEquals(0, ordinary.collectCoin(1f))
        assertEquals(Lottery.JACKPOT, ordinary.collectCoin(1f))
        assertEquals(Lottery.JACKPOT, Lottery(Rolls(winOn(3))).collectCoin(3f))
        assertEquals(0, Lottery(Rolls(winOn(4))).collectCoin(3f))
    }

    @Test fun fractionalValuesAccumulateInsteadOfDroppingBonusTickets() {
        val lottery = Lottery(Rolls(winOn(3)))
        assertEquals(0, lottery.collectCoin(1.5f))
        assertEquals(Lottery.JACKPOT, lottery.collectCoin(1.5f))
    }

    @Test fun decimalRichCoinMultipliersDoNotLoseTheFinalTicketToFloatError() {
        val onePointFour = Lottery(Rolls(winOn(14)))
        repeat(9) { assertEquals(0, onePointFour.collectCoin(1.4f)) }
        assertEquals(Lottery.JACKPOT, onePointFour.collectCoin(1.4f))
        val onePointTwo = Lottery(Rolls(winOn(6)))
        repeat(4) { assertEquals(0, onePointTwo.collectCoin(1.2f)) }
        assertEquals(Lottery.JACKPOT, onePointTwo.collectCoin(1.2f))
    }

    @Test fun fractionalTicketsDoNotRollEarly() {
        val rolls = Rolls(0.0)
        val lottery = Lottery(rolls)
        assertEquals(0, lottery.collectCoin(.5f))
        assertEquals(0, rolls.calls)
        assertEquals(Lottery.JACKPOT, lottery.collectCoin(.5f))
    }

    @Test fun aWeightedPickupCanWinMultipleJackpots() {
        val lottery = Lottery(Rolls(0.0, 0.0, 0.0))
        assertEquals(3 * Lottery.JACKPOT, lottery.collectCoin(3f))
        assertEquals(0, lottery.collectCoin(1f))
    }

    @Test fun jackpotsCanRepeatLaterInTheSameRun() {
        val lottery = Lottery(Rolls(0.0, winOn(2)))
        assertEquals(Lottery.JACKPOT, lottery.collectCoin(1f))
        assertEquals(0, lottery.collectCoin(1f))
        assertEquals(Lottery.JACKPOT, lottery.collectCoin(1f))
    }

    @Test fun mysteryBoxThresholdIsExactlyOnePointThreePercent() {
        val lottery = Lottery(Rolls(0.0, Math.nextDown(.013), .013, .99))
        assertEquals(Lottery.JACKPOT, lottery.collectBox())
        assertEquals(Lottery.JACKPOT, lottery.collectBox())
        assertEquals(0, lottery.collectBox())
        assertEquals(0, lottery.collectBox())
    }

    @Test fun resetDropsBothFractionalTicketsAndThePreviousRunWaitingTime() {
        val lottery = Lottery(Rolls(winOn(3), 0.0))
        assertEquals(0, lottery.collectCoin(1.5f))
        lottery.reset()
        assertEquals(0, lottery.collectCoin(.5f))
        assertEquals(Lottery.JACKPOT, lottery.collectCoin(.5f))
    }

    @Test fun invalidInputsDoNotSpendRandomnessOrDestroyFractionalTickets() {
        val rolls = Rolls(0.0)
        val lottery = Lottery(rolls)
        assertEquals(0, lottery.collectCoin(.5f))
        for (value in listOf(0f, -2f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(0, lottery.collectCoin(value))
        }
        assertEquals(0, rolls.calls)
        assertEquals(Lottery.JACKPOT, lottery.collectCoin(.5f))
    }

    @Test fun hugeValuesCannotOverflowPayoutOrLoopOverAllCoins() {
        val rolls = Rolls(fallback = 0.0)
        val payout = Lottery(rolls).collectCoin(Float.MAX_VALUE)
        assertEquals(Int.MAX_VALUE / Lottery.JACKPOT * Lottery.JACKPOT, payout)
        assertTrue(rolls.calls <= Int.MAX_VALUE / Lottery.JACKPOT)
    }

    @Test fun coinProbabilityUsesOneInOneHundredThousandNotOnePercent() {
        // The first ticket's CDF is exactly p; the middle of the second is > p.
        assertTrue(winOn(1) < Lottery.COIN_CHANCE)
        assertTrue(winOn(2) > Lottery.COIN_CHANCE)
        assertEquals(Lottery.JACKPOT, Lottery(Rolls(winOn(1))).collectCoin(1f))
        assertEquals(0, Lottery(Rolls(winOn(2))).collectCoin(1f))
        // Closed-form chance of at least one win for Rich Coins 3x.
        assertEquals(1.0 - exp(3 * ln1p(-.00001)), 1.0 - Math.pow(.99999, 3.0), 1e-15)
    }
}
