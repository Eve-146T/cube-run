package cube.run.bot

import com.badlogic.gdx.graphics.Color
import cube.run.data.Settings
import cube.run.game.Lanes
import cube.run.game.Player
import cube.run.game.track.*
import kotlin.random.Random

internal fun field(type: Class<*>, name: String) = type.getDeclaredField(name).apply { isAccessible = true }
internal fun <T> value(owner: Any, name: String): T {
    @Suppress("UNCHECKED_CAST")
    return field(owner.javaClass, name).get(owner) as T
}

internal object BotFixtures {
    val sections get() = Sections.lib + Sections.hillPool + Sections.intro + Sections.breather
    fun generate(section: Sect, seed: Int, mirror: Boolean, entry: Int, phase: Float, repeats: Int = 2): Course {
        val saved = Settings.testSection
        Settings.testSection = 0 // the actual decoder's bare-section policy, without pickups
        try {
            Lanes.reset()
            val rng = Random(seed)
            val track = Track(rng, ObstacleFactory(rng))
            field(Track::class.java, "curSafe").setInt(track, entry)
            field(Track::class.java, "mirror").setBoolean(track, mirror && section.mirrorable)
            val spawn = Track::class.java.getDeclaredMethod("spawnStep", Int::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).apply { isAccessible = true }
            val gap = Track::class.java.getDeclaredMethod("gapFor", Int::class.javaPrimitiveType).apply { isAccessible = true }
            var z = -16f
            repeat(repeats) {
                for (code in section.steps) {
                    z -= gap.invoke(track, code) as Float
                    spawn.invoke(track, code, z, 200f)
                }
            }
            return Course(section.id, section.name, section.tier, seed, mirror, entry,
                snapshot(track.rows), phase = phase)
        } finally { Settings.testSection = saved }
    }

    fun snapshot(rows: List<Row>): List<BotRow> = rows.map { row ->
        BotRow(row.z, row.obs.map { o -> Obstacle(o.x, o.cy, o.sy, o.halfW, o.type, o.sz, o.ramp,
            o.sliding, o.slideTo, o.slideRate, o.anim, o.phase, o.used) }, buildList {
            row.coins?.filter { !it.taken && !it.missed }?.forEach { add(Goodie(it.restX, it.y, it.dz, 1f)) }
            if (row.pickup != Pickup.NONE) add(Goodie(row.pickupX, .7f, Row.PICKUP_DZ,
                if (row.pickup == Pickup.JET || row.pickup == Pickup.BUBBLE) 8f else 5f))
        })
    }

    fun materialize(course: Course): List<Row> = course.rows.map { r ->
        Row(r.z, ArrayList(r.obstacles.map { o ->
            Ob(Color.WHITE, o.x, o.cy, o.halfW, o.type, o.halfW * 2f, o.sy, o.depth,
                o.sliding, o.slideTo, o.slideRate, o.anim, o.phase, o.ramp).apply { used = o.used }
        })).apply {
            pop = 1f
            coins = ArrayList(r.goodies.filter { it.value == 1f }.map { Coin(it.x, it.y, it.dz) })
        }
    }

    fun body(p: Player) = Body(p.lane, p.px, p.py, value(p, "vy"), p.air, p.duck,
        value(p, "duckT"), value(p, "slamming"), p.flying, p.flyY, p.hover)
    fun restore(p: Player, b: Body) {
        for ((name, v) in mapOf("lane" to b.lane, "px" to b.x, "py" to b.y, "vy" to b.vy,
            "air" to b.air, "duck" to b.duck, "duckT" to b.duckT, "slamming" to b.slam,
            "flying" to b.flying, "flyY" to b.flyY, "hover" to b.hover)) field(Player::class.java, name).set(p, v)
    }
}
