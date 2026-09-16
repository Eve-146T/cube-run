package cube.run.intro

import kotlin.math.*

/** Small, renderer-independent description of the opening shot. Seconds are visible time. */
class OpeningPose {
    var yaw = 0f; private set
    var tilt = 0f; private set
    var scaleX = 1f; private set
    var scaleY = 1f; private set
    var shellScale = 1f; private set
    var cameraY = 2.6f; private set
    var cameraZ = 6.8f; private set
    var pitch = 0f; private set
    var fov = 40f; private set
    var worldAmount = 0f; private set
    var uiAmount = 0f; private set

    fun update(seconds: Float, heightDp: Float, spinSeconds: Float = seconds) {
        val t = seconds.coerceIn(0f, DURATION)
        val move = ease(t/1.15f)
        // The cube turns from the first frame; camera travel still eases into the road.
        yaw = -125f + 40f*spinSeconds
        tilt = -12f*(1f-move)
        val settle = ((t-1.04f)/.53f).coerceIn(0f, 1f)
        val squash = .12f*sin(settle*PI.toFloat()*2f)*(1f-settle)
        val size = .82f+.18f*move
        val breathe = 1f+.03f*move*sin(spinSeconds*2.4f)
        scaleX = .9f*size*(1f+squash*.5f)*breathe
        scaleY = .9f*size*(1f-squash)/breathe
        shellScale = .9f*(1.18f+.06f*sin(spinSeconds*8f))*size
        cameraY = 2.6f+(MENU_CAMERA_Y-2.6f)*move
        cameraZ = 6.8f+(MENU_CAMERA_Z-6.8f)*move
        pitch = atan2(-2.15f, 6.8f)*(1f-move) + atan2(MENU_TARGET_Y-MENU_CAMERA_Y, MENU_CAMERA_Z-MENU_TARGET_Z)*move
        val initialFov = 2f*atan(tan(PI.toFloat()/9f)*heightDp/REFERENCE_HEIGHT_DP)
        fov = initialFov*(1f-move) + PI.toFloat()/3f*move
        worldAmount = ease((t-.14f)/1.05f)
        uiAmount = ease((t-.67f)/.9f)
    }

    companion object {
        const val DURATION = 1.57f
        const val REFERENCE_HEIGHT_DP = 620f
        const val MENU_CAMERA_Y = 5.5f
        const val MENU_CAMERA_Z = 9f
        const val MENU_TARGET_Y = .6f
        const val MENU_TARGET_Z = -14f
        const val INK = 0xFF14102E.toInt()
        fun ease(t: Float): Float = t.coerceIn(0f, 1f).let { it*it*it*(it*(it*6f-15f)+10f) }
    }
}
