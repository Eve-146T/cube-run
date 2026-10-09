package cube.run.game

import cube.run.game.track.Ob
import cube.run.game.track.Row
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * Losing the road: the cube rolls on over the lip and drops, and the run is
 * over once it is gone. A tar pit is a shaft into darkness; in Outer Space a
 * rift in the glass, or the road's edge, opens onto empty space.
 *
 * Nothing stops dead. The cube keeps rolling over the road ([speed], which
 * is what the world scrolls by): off the road's edge it glides on, and into a
 * hole it rolls on to near the middle and drops inside, framed by the
 * opening. The camera keeps its own momentum a moment longer ([coast]) and
 * turns to watch the cube fall ([look]).
 */
class VoidFall {
    enum class Kind { TAR, RIFT, EDGE }

    var kind: Kind? = null
        private set
    val active: Boolean get() = kind != null
    /** Seconds since the cube lost the road. */
    var t = 0f
        private set
    /** The cube's forward speed over the road: the world scrolls by this. */
    var speed = 0f
        private set
    /** How far the camera has glided on past the cube. */
    var coast = 0f
        private set
    /** The tar pit the cube fell into, drawn as an open shaft until the run is reset. */
    var pit: Ob? = null
        private set
    var pitRow: Row? = null
        private set

    private var decay = 0f       // the share of its speed the cube loses per second
    private var camSpeed = 0f

    /** When the run-over pages take over. */
    val duration: Float get() = if (kind == Kind.TAR) 1.3f else 1.9f
    val done: Boolean get() = t >= duration

    /**
     * How steeply the camera may tip after the cube (radians below level): far
     * down into a stilled rift; off the edge, where the road glides on, the
     * horizon stays in the frame; a shaft is dark below its first metre.
     */
    val maxDip: Float get() = when (kind) { Kind.RIFT -> 1.05f; Kind.EDGE -> 0.75f; else -> 0.5f }

    /** 0..1: how far the camera has turned from the road to the falling cube. */
    val look: Float get() = (t / 0.55f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

    fun reset() {
        kind = null; pit = null; pitRow = null
        t = 0f; speed = 0f; coast = 0f; decay = 0f; camSpeed = 0f
    }

    /**
     * The cube lost the road at [speed]. [room] is how far it rolls on into a
     * hole (the hole's middle, short of its far lip); [pit] is a tar pit,
     * which opens up into a shaft.
     */
    fun start(kind: Kind, speed: Float, room: Float = Float.MAX_VALUE, pit: Ob? = null, pitRow: Row? = null) {
        reset()
        this.kind = kind
        this.pit = pit; this.pitRow = pitRow
        if (pit != null) pitRow?.obs?.forEach { if (it.pit && abs(it.x - pit.x) < 0.01f) it.open = true } // the slab and its lip
        this.speed = speed
        camSpeed = speed
        // An exponential slowdown covers speed / decay in all: exactly the room into a hole.
        decay = if (kind == Kind.EDGE) SETTLE else (speed / room.coerceAtLeast(0.15f)).coerceAtLeast(SETTLE)
    }

    fun update(dt: Float) {
        if (!active) return
        t += dt
        speed *= exp(-decay * dt)
        camSpeed += (speed - camSpeed) * min(1f, dt / CAMERA_LAG)
        coast = min(MAX_COAST, coast + (camSpeed - speed) * dt)
    }

    companion object {
        /** A free fall's road speed fades this fast (per second): the world glides to rest. */
        const val SETTLE = 0.8f
        /** Seconds the camera's speed trails the cube's: it overshoots a hard stop by speed × lag. */
        private const val CAMERA_LAG = 0.12f
        private const val MAX_COAST = 1.8f
    }
}
