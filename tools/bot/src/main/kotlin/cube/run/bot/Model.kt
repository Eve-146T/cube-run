package cube.run.bot

import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.*

/** Test-only model of Player.update / Track.scroll / CubeRun.collide, checked on Android. */
data class Body(var lane: Int = 1, var x: Float = 0f, var y: Float = .45f,
    var vy: Float = 0f, var air: Boolean = false, var duck: Float = 0f,
    var duckT: Float = 0f, var slam: Boolean = false, var flying: Boolean = false,
    var flyY: Float = 5.2f, var hover: Boolean = false, var pads: Long = 0L,
    var flightLeft: Float = Float.POSITIVE_INFINITY,
    var coyoteLeft: Float = 0f, var jumpBuffer: Float = 0f)

data class Obstacle(val x: Float, val cy: Float, val sy: Float, val halfW: Float,
    val type: Int, val depth: Float, val ramp: Float, val sliding: Boolean,
    val slideTo: Float, val slideRate: Float, val anim: Int, val phase: Float,
    val used: Boolean = false)
data class Goodie(val x: Float, val y: Float, val dz: Float, val value: Float)
data class BotRow(val z: Float, val obstacles: List<Obstacle>, val goodies: List<Goodie> = emptyList())
data class Course(val id: Int, val name: String, val tier: Int, val seed: Int,
    val mirror: Boolean, val entry: Int, val rows: List<BotRow>, val lanes: Int = 3,
    val width: Float = 1.7f, val phase: Float = 0f)

object Action {
    const val NONE = 0; const val LEFT = 1; const val RIGHT = 2; const val JUMP = 3; const val DOWN = 4
    val names = arrayOf("wait", "left", "right", "jump", "down")
    fun apply(b: Body, action: Int, lanes: Int) {
        when (action) {
            LEFT -> b.lane = max(0, b.lane - 1)
            RIGHT -> b.lane = min(lanes - 1, b.lane + 1)
            JUMP -> if (!b.flying && !b.hover) {
                if (b.air && b.coyoteLeft <= 0f) b.jumpBuffer = .1f
                else takeOff(b)
            }
            DOWN -> if (!b.flying && !b.hover) {
                b.coyoteLeft = 0f; b.jumpBuffer = 0f
                if (b.air) { if (b.vy > -12f) { b.vy = -19f; b.slam = true } }
                else b.duckT = .5f
            }
        }
    }
    fun takeOff(b: Body) {
        b.air = true; b.vy = 8.4f; b.duckT = 0f; b.slam = false
        b.coyoteLeft = 0f; b.jumpBuffer = 0f
    }
}

data class FrameOb(val x: Float, val bottom: Float, val top: Float, val halfW: Float,
    val type: Int, val rowZ: Float, val depth: Float, val ramp: Float, val pad: Int,
    val used: Boolean)
data class Frame(val before: List<FrameOb>, val after: List<FrameOb>, val goods: List<Goodie>, val time: Float)

/** Precompute obstacle motion once, independently of candidate player trajectories. */
class Timeline(val course: Course, val speed: Float, val dt: Float = 1f / 60f,
    seconds: Float = (-course.rows.minOf { it.z } + 9f) / speed,
    val conservative: Boolean = true, val safetyMargin: Float = 0f) {
    val frames: List<Frame>
    init {
        require(speed > 0 && dt > 0)
        val xs = course.rows.flatMap { it.obstacles }.map { it.x }.toFloatArray()
        val zs = course.rows.map { it.z }.toFloatArray()
        fun positions(frame: Int, update: Boolean): List<FrameOb> {
            val out = ArrayList<FrameOb>()
            var index = 0
            var pad = 0
            val time = course.phase + (frame + 1) * dt
            if (update) for (i in zs.indices) zs[i] += speed * dt
            for ((ri, row) in course.rows.withIndex()) for (o in row.obstacles) {
                val z = zs[ri]
                if (update && o.sliding && z > -26f) xs[index] += (o.slideTo - xs[index]) * min(1f, dt * o.slideRate)
                var x = xs[index]; var bottom = o.cy - o.sy / 2f; var top = o.cy + o.sy / 2f
                if (update) when (o.anim) {
                    1 -> { bottom = 0f; top = max(0f, .66f * sin(time * 3.4f + o.phase)) }
                    2 -> x = sin(time * 2.3f + o.phase) * course.width * 1.15f
                    3 -> { bottom = .03f + 1.75f * sqrt(.5f + .5f * sin(time * 2.6f + o.phase)); top = bottom + 1f }
                    4 -> x = sin(time * 2.4f + o.phase) * course.width * 1.15f
                }
                if (o.type == 3) require(pad < 63) { "Split courses with more than 63 pads" }
                val padId = if (o.type == 3) pad++ else -1
                if (z in -2f..(max(7f, o.depth) + speed * dt)) out.add(FrameOb(x, bottom, top, o.halfW,
                    o.type, z, o.depth, o.ramp, padId, o.used))
                index++
            }
            return out
        }
        var previous = positions(-1, false)
        frames = List(ceil(seconds / dt).toInt()) { frame ->
            val next = positions(frame, true)
            val goods = ArrayList<Goodie>()
            for (r in course.rows) for (g in r.goodies) {
                val oldZ = r.z + g.dz + frame * speed * dt
                if (oldZ <= 0 && oldZ + speed * dt > 0) goods.add(g)
            }
            Frame(previous, next, goods, course.phase + (frame + 1) * dt).also { previous = next }
        }
    }

    /** Returns false on a fatal collision; pickups never buy permission to hit an obstacle. */
    fun step(b: Body, frame: Int, action: Int): Boolean {
        Action.apply(b, action, course.lanes)
        if (b.flying && b.flightLeft.isFinite()) {
            b.flightLeft = max(0f, b.flightLeft - dt)
            if (b.flightLeft == 0f) {
                b.flying = false; b.air = true; b.vy = 0f; b.flyY = 5.2f
                b.coyoteLeft = 0f; b.jumpBuffer = 0f
            }
            else b.flyY = if (b.flightLeft < 1.4f) .45f + (5.2f - .45f) * (b.flightLeft / 1.4f) else 5.2f
        }
        val f = frames[frame]
        var ground = 0f
        if (!b.hover && !b.flying) for (o in f.before) {
            if (o.type == 2 && o.rowZ in 0f..6.6f && o.rowZ <= o.depth && abs(b.x - o.x) <= o.halfW + .3f) {
                ground = max(ground, if (o.ramp > 0 && o.rowZ < o.ramp) o.top * o.rowZ / o.ramp else o.top)
            }
        }
        b.x += ((b.lane - (course.lanes - 1) / 2f) * course.width - b.x) * min(1f, dt * if (b.hover) 4.5f else 13f)
        val gy = .45f + ground
        b.coyoteLeft = max(0f, b.coyoteLeft - dt)
        b.jumpBuffer = max(0f, b.jumpBuffer - dt)
        if (b.hover || b.flying) { b.coyoteLeft = 0f; b.jumpBuffer = 0f }
        when {
            b.hover -> { b.y += (1.4f + .15f * sin(f.time * 2.2f) - b.y) * min(1f, dt * 3f); b.air = false; b.vy = 0f }
            b.flying -> b.y += (b.flyY - b.y) * min(1f, dt * if (b.flyY < 5.2f) 7f else 4f)
            b.air -> {
                b.vy -= 26f * dt; b.y += b.vy * dt
                if (b.y <= gy && b.vy <= 0f) {
                    b.y = gy; b.air = false; b.vy = 0f
                    if (b.slam) { b.slam = false; b.duckT = .5f }
                    if (b.jumpBuffer > 0f) Action.takeOff(b)
                }
            }
            gy > b.y + .001f -> { if (gy - b.y > .45f) return false else b.y = gy }
            gy < b.y - .02f -> { b.air = true; b.vy = 0f; b.coyoteLeft = .1f }
        }
        b.duckT = max(0f, b.duckT - dt)
        b.duck += ((if (b.duckT > 0 && !b.air) 1f else 0f) - b.duck) * min(1f, dt * 18f)
        for (o in f.after) {
            if (o.type == 3 && !o.used && b.pads and (1L shl o.pad) == 0L && !b.air && !b.flying &&
                abs(o.rowZ) < .75f && abs(b.x - o.x) < .85f) {
                b.pads = b.pads or (1L shl o.pad); b.air = true; b.vy = 12.5f; b.duckT = 0f; b.slam = false
                b.coyoteLeft = 0f; b.jumpBuffer = 0f
            }
            // Expand the z test to cover a swept frame: high speed must not win by tunnelling.
            val crossed = if (conservative) o.rowZ >= -.82f && o.rowZ - speed * dt <= .82f else abs(o.rowZ) < .82f
            if (!b.flying && o.type == 0 && crossed && abs(b.x - o.x) < o.halfW + .36f + safetyMargin &&
                max(b.y - .45f - o.top, o.bottom - (b.y + .45f - b.duck * .72f)) < safetyMargin - .02f) return false
        }
        return true
    }
}

object CourseFile {
    fun write(out: DataOutputStream, courses: List<Course>) {
        out.writeInt(0x43524231); out.writeInt(courses.size)
        for (c in courses) {
            out.writeInt(c.id); out.writeUTF(c.name); out.writeInt(c.tier); out.writeInt(c.seed)
            out.writeBoolean(c.mirror); out.writeInt(c.entry); out.writeInt(c.lanes); out.writeFloat(c.width); out.writeFloat(c.phase)
            out.writeInt(c.rows.size)
            for (r in c.rows) {
                out.writeFloat(r.z); out.writeInt(r.obstacles.size)
                for (o in r.obstacles) {
                    for (v in floatArrayOf(o.x, o.cy, o.sy, o.halfW)) out.writeFloat(v)
                    out.writeInt(o.type); out.writeFloat(o.depth); out.writeFloat(o.ramp); out.writeBoolean(o.sliding)
                    out.writeFloat(o.slideTo); out.writeFloat(o.slideRate); out.writeInt(o.anim); out.writeFloat(o.phase); out.writeBoolean(o.used)
                }
                out.writeInt(r.goodies.size)
                for (g in r.goodies) for (v in floatArrayOf(g.x, g.y, g.dz, g.value)) out.writeFloat(v)
            }
        }
    }
    fun read(input: DataInputStream): List<Course> {
        require(input.readInt() == 0x43524231)
        return List(input.readInt()) {
            val id = input.readInt(); val name = input.readUTF(); val tier = input.readInt(); val seed = input.readInt()
            val mirror = input.readBoolean(); val entry = input.readInt(); val lanes = input.readInt(); val width = input.readFloat(); val phase = input.readFloat()
            val rows = List(input.readInt()) {
                val z = input.readFloat()
                val obs = List(input.readInt()) { Obstacle(input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat(),
                    input.readInt(), input.readFloat(), input.readFloat(), input.readBoolean(), input.readFloat(), input.readFloat(), input.readInt(), input.readFloat(), input.readBoolean()) }
                val goods = List(input.readInt()) { Goodie(input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat()) }
                BotRow(z, obs, goods)
            }
            Course(id, name, tier, seed, mirror, entry, rows, lanes, width, phase)
        }
    }
}
