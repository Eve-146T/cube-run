package cube.run.game.track

import cube.run.game.Lanes
import kotlin.math.max

/** Clear central runway, with side blocks to compare solid and wireframe geometry. */
class PillTestWorld(private val factory: ObstacleFactory) {
    private var distance = 0f
    private var nextPill = 2f
    private var index = 0

    fun reset(rows: ArrayList<Row>, hue: Float) {
        distance = 0f; nextPill = 2f; index = 0
        for (z in -24 downTo -96 step 12) scenery(rows, z.toFloat(), hue)
    }

    fun spawn(rows: ArrayList<Row>, mv: Float, dt: Float, hue: Float) {
        distance += mv
        while (distance >= 12f) {
            distance -= 12f
            scenery(rows, -96f + distance, hue)
        }
        if (dt <= 0f) return
        nextPill -= dt
        if (nextPill <= 0f) {
            nextPill += 18f // Full 12-second effect, its fade-out, then normal scenery.
            val approach = max(18f, mv / dt * 2f)
            rows.add(Row(-approach - Row.PICKUP_DZ, arrayListOf()).apply {
                pickup = Pickup.RED_PILL; pickupX = 0f; safeLane = 1
            })
        }
    }

    private fun scenery(rows: ArrayList<Row>, z: Float, hue: Float) {
        val tint = hue + index++ * 23f
        rows.add(Row(z, arrayListOf(
            factory.pillar(-Lanes.NORMAL_W, tint),
            factory.pillar(Lanes.NORMAL_W, tint + 60f),
        )).apply { safeLane = 1 })
    }
}
