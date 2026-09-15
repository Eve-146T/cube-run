package cube.run.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import cube.run.intro.OpeningClock
import cube.run.intro.OpeningPose
import kotlin.math.cos
import kotlin.math.sin

/** Native and GL renderers continue the same shot on the same clock. */
class CubeOpening(enabled: Boolean, private val clock: OpeningClock? = null) {
    var elapsed = if (enabled) 0f else DURATION
        private set
    private val shot = OpeningPose()
    val motionSeconds: Float get() = elapsed+(clock?.leadInSeconds ?: 0f)
    val active: Boolean get() = elapsed < DURATION
    val worldAmount: Float get() = OpeningPose.ease((elapsed-.14f)/1.05f)
    val uiAmount: Float get() = OpeningPose.ease((elapsed-.67f)/.9f)

    fun tick(dt: Float) {
        if (active) elapsed = clock?.seconds() ?: (elapsed+dt).coerceAtMost(DURATION)
    }
    fun finish() { elapsed = DURATION; clock?.finish() }

    fun pose(player: Player, camera: PerspectiveCamera, worldHue: Float) {
        shot.update(elapsed, camera.viewportHeight/Gdx.graphics.density, motionSeconds)
        camera.position.set(0f, shot.cameraY, shot.cameraZ)
        camera.direction.set(0f, sin(shot.pitch), -cos(shot.pitch))
        camera.up.set(0f, 1f, 0f)
        camera.fieldOfView = Math.toDegrees(shot.fov.toDouble()).toFloat()
        player.openingPose(shot, motionSeconds, worldHue, clock?.skinAmount() ?: 1f)
    }

    companion object {
        const val DURATION = OpeningPose.DURATION
        val INK: Color = Color.valueOf("14102E")
    }
}
