package cube.run.game.space

import cube.run.game.track.Coin
import cube.run.game.track.Row
import cube.run.game.track.Step

/**
 * Space's own coin shapes, traced on the low-gravity arcs: the rift's one
 * long float, a ring's slow launch, a comet's weave, and a wider, higher arc
 * over anything you jump.
 */
object SpaceCoins {
    /**
     * Lay [row]'s coins for step [code] on the walk lane at [x] into [coins].
     * [continued]: a later slice of a rift (its first slice carries the arc).
     * [weave] gives the x of the comet wake's next coin. Returns false when the
     * ordinary rules apply instead.
     */
    inline fun lay(row: Row, code: Int, x: Float, continued: Boolean, speed: Float, coins: ArrayList<Coin>, weave: () -> Float): Boolean {
        when {
            code == Step.RF -> { // one arc over the whole chasm, laid by its first slice
                if (continued) return true
                for (k in 0 until 6) {
                    val dz = 2.2f - k * 1.5f
                    val u = (dz + SpaceSpacing.RIFT_SLICE) / 4.4f
                    coins.add(Coin(x, 0.55f + 1.25f * (1f - u * u).coerceAtLeast(0f), dz))
                }
            }
            Step.isPad(code) -> { // the ring's arc at this speed: follow the launch and you take them all
                for (k in 1..5) {
                    val t = k * 0.24f
                    coins.add(Coin(x, SpaceSpacing.ringArcY(t), -speed * t))
                }
            }
            code == Step.CT -> for (k in 0 until 4) coins.add(Coin(weave(), 0.5f, -k * 1.6f)) // two coins a lane, weaving across
            Step.isJump(code) && !Step.isTall(code) -> { // a long, high arc: low gravity carries you further
                coins.add(Coin(x, 1.45f, 2.4f)); coins.add(Coin(x, 2.0f, 0f)); coins.add(Coin(x, 1.45f, -2.4f))
            }
            else -> return false
        }
        row.coins = coins
        return true
    }
}
