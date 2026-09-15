package cube.run.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import kotlin.math.sin
import kotlin.math.atan
import kotlin.math.tan
import com.badlogic.gdx.Gdx

/** One continuous shot: the equipped cube goes from centre stage to the idle menu. */
class CubeOpening(enabled: Boolean) {
    private var elapsed = if (enabled) 0f else DURATION
    val active: Boolean get() = elapsed < DURATION
    private val travel: Float get() = ease((elapsed-.18f)/1.15f)
    val worldAmount: Float get() = ease((elapsed-.32f)/1.05f)
    val uiAmount: Float get() = ease((elapsed-.85f)/.90f)
    private val endPosition = Vector3()
    private val endDirection = Vector3()
    private val startDirection = Vector3()

    fun tick(dt: Float) { elapsed = (elapsed+dt).coerceAtMost(DURATION) }
    fun finish() { elapsed = DURATION }

    fun pose(player: Player, camera: PerspectiveCamera) {
        val move = travel
        endPosition.set(camera.position); endDirection.set(camera.direction)
        // Begin looking directly at the actual cube, then ease into the normal road view.
        startDirection.set(0f, player.py-2.6f, -6.8f).nor()
        camera.position.set(0f, 2.6f, 6.8f).lerp(endPosition, move)
        camera.direction.set(startDirection).slerp(endDirection, move)
        camera.up.set(Vector3.Y)
        // Android's starting-window drawable has fixed dp dimensions. Match its
        // projection on every screen, then blend into the normal chase camera.
        val heightDp = camera.viewportHeight / Gdx.graphics.density
        val launchFov = Math.toDegrees(2.0 * atan(tan(Math.toRadians(20.0)) * heightDp / 640.0)).toFloat()
        camera.fieldOfView = launchFov+(camera.fieldOfView-launchFov)*move
        val settle = ((elapsed-1.22f)/.53f).coerceIn(0f, 1f)
        val squash = .12f*sin(settle*Math.PI.toFloat()*2f)*(1f-settle)
        player.openingPose(-125f*(1f-move), -12f*(1f-move), .82f+.18f*move, squash)
    }

    private fun ease(t: Float): Float = t.coerceIn(0f, 1f).let { it*it*it*(it*(it*6f-15f)+10f) }

    companion object {
        const val DURATION = 1.75f
        val INK: Color = Color.valueOf("14102E")
    }
}
