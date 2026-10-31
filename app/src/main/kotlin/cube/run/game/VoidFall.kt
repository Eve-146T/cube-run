package cube.run.game

import cube.run.game.track.Row
import kotlin.math.max
import kotlin.math.min

/**
 * Losing the road: the cube carries on over the lip and drops, and the run is
 * over once it has fallen away: in Outer Space a rift in the glass, or the
 * road's edge, opens onto empty space. (A tar pit is no void: it is a crash.)
 *
 * Nothing brakes for the fall. The cube keeps its own speed over the world
 * (see [Player.fall]), and the camera keeps its
 * momentum too: its speed ([speed], which the world scrolls by) trails the
 * cube's and then lets go, so it glides to rest while the cube falls away.
 * The shot never turns.
 */
class VoidFall {
    enum class Kind { RIFT, EDGE }

    var kind: Kind? = null
        private set
    val active: Boolean get() = kind != null
    /** Seconds since the cube lost the road. */
    var t = 0f
        private set
    /** The camera's speed along the road: the world scrolls by this. */
    var speed = 0f
        private set
    /** The row of the rift the cube fell into. */
    var row: Row? = null
        private set

    private var unseen = 0f

    /** The run-over pages take over once the cube has been out of sight a moment (or at the latest after [LONGEST]). */
    val done: Boolean get() = t >= LONGEST || t >= SHORTEST && unseen >= GONE

    /** Where the cube was when it was lost: the camera's shot stays framed on it. */
    var startX = 0f
        private set
    var startY = 0f
        private set

    /** 0..1: how far the camera has drifted sideways after the cube. */
    val drift: Float get() = (t / 0.4f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

    fun reset() {
        kind = null; row = null
        t = 0f; speed = 0f; unseen = 0f
    }

    /** Whether the cube could be seen this frame (in the frame, not lost in the dark). */
    fun seen(visible: Boolean, dt: Float) { unseen = if (visible) 0f else unseen + dt }

    /** The cube lost the road at [speed]. */
    fun start(kind: Kind, speed: Float, x: Float, y: Float, row: Row? = null) {
        reset()
        this.kind = kind
        startX = x; startY = y
        this.row = row
        this.speed = speed
    }

    /** One frame; [cubeForward] is the cube's own speed over the world. */
    fun update(dt: Float, cubeForward: Float) {
        if (!active) return
        t += dt
        // The camera follows the cube's pace a moment, then lets it go and glides to rest.
        // Into space it lets go sooner: the cube sails on ahead, so you watch it fall away beneath the road.
        val letGo = ((t - HOLD) / LET_GO).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        val target = max(0f, cubeForward) * (1f - letGo)
        speed += (target - speed) * min(1f, dt / CAMERA_LAG)
    }

    private companion object {
        const val SHORTEST = 0.9f
        const val LONGEST = 2.2f
        /** Seconds the cube stays out of sight before the run is over. */
        const val GONE = 0.35f
        /** Seconds the camera keeps the cube's pace before it starts to let go. */
        const val HOLD = 0.05f
        /** Seconds over which it lets go. */
        const val LET_GO = 0.45f
        /** Seconds the camera's speed trails its target: a cube stopped dead is overshot by speed × lag. */
        const val CAMERA_LAG = 0.2f
    }
}
