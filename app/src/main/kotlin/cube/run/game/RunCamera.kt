package cube.run.game

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import cube.run.intro.OpeningPose
import kotlin.math.max
import kotlin.math.min

/**
 * The camera rig. In a run it chases from above-behind, leans with the
 * player, lifts with height, swings into a high angle for a jetpack flight
 * and pulls back on death; the field of view widens with speed and punches
 * on big moments ([kick]). The stages (wardrobe, results) have fixed shots.
 */
class RunCamera(private val cam: PerspectiveCamera) {

    private var flyCam = 0f     // eased 0..1: the high-angle flight camera
    private var kick = 0f       // transient field-of-view punch
    private var dolly = 0f      // eased extra pull-back (bounces)
    /** 0 = the idle shot (higher, further back, looking down the road), 1 = the chase. Eased by the game at run start. */
    var intro = -2.2f
    /** Camera roll in degrees (the Kaleidoscope sways), eased. */
    var roll = 0f
    /** Extra pull-back for wide roads, eased. */
    var wide = 0f

    /** A field-of-view punch (bubble, fire taps, level up, bounces). Bigger wins. */
    fun punch(amount: Float) { kick = max(kick, amount) }

    fun reset() { flyCam = 0f; kick = 0f; dolly = 0f; intro = -2.2f }

    /**
     * One frame of the chase. [lift] is the player's height above its ground,
     * [ground] the resting height, [deathT] seconds since the crash.
     */
    fun chase(dt: Float, px: Float, py: Float, ground: Float, deathT: Float, spd: Float) {
        val lift = py - ground
        val highT = ((lift - 1.5f) / (Player.FLY_Y - ground - 1.5f)).coerceIn(0f, 1f)
        flyCam += (highT - flyCam) * min(1f, dt * 4f)
        val f = flyCam
        val g = Terrain.y(0f)
        val cy = OpeningPose.MENU_CAMERA_Y + lift * 0.3f * (1f - f) + f * 9.5f + deathT * 1.6f - intro * 1.9f + wide * 1.4f + g * 0.8f
        cam.position.set(px * (0.45f - 0.15f * f), cy, OpeningPose.MENU_CAMERA_Z + f * 1.2f + deathT * 2.2f + dolly - intro * 2.6f + wide * 2.2f)
        look.set(px * (0.55f - 0.15f * f), OpeningPose.MENU_TARGET_Y + lift * 0.5f * (1f - f) + f * 1.6f + intro * 0.4f + g * 0.5f + Terrain.y(-8f) * 0.4f, OpeningPose.MENU_TARGET_Z - f * 6f + intro * 6f)
        cam.lookAt(look)
        val rr = Math.toRadians(roll.toDouble())
        cam.up.set(kotlin.math.sin(rr).toFloat(), kotlin.math.cos(rr).toFloat(), 0f)
        kick = max(0f, kick - dt * 2.2f)
        cam.fieldOfView = 60f + max(0f, spd - 10f) * 0.42f + kick * kick * 9f
    }

    private val look = Vector3()

    /**
     * After [chase]: the cube has lost the road. The camera leans a little
     * sideways after it, still looking down the road; it only tips down (and
     * turns) as far as it must to keep the falling cube at ([x], [y], [z]) in
     * the lower part of the frame — never
     * steeper than [maxDip] radians below level — after which the cube drops
     * away out of the bottom. [amount] 0..1 eases this in.
     */
    fun watchFall(x: Float, y: Float, z: Float, amount: Float, maxDip: Float) {
        cam.position.x += (x * 0.35f - cam.position.x) * amount
        cam.position.y += 0.4f * amount
        val p = cam.position
        val roadPitch = pitchTo(look.x - p.x, look.y - p.y, look.z - p.z)
        val roadYaw = kotlin.math.atan2(look.x - p.x, p.z - look.z)
        val cubePitch = pitchTo(x - p.x, y - p.y, z - p.z)
        val cubeYaw = kotlin.math.atan2(x - p.x, p.z - z)
        val halfV = Math.toRadians(cam.fieldOfView / 2.0).toFloat()
        val halfH = kotlin.math.atan(kotlin.math.tan(halfV) * cam.viewportWidth / cam.viewportHeight.coerceAtLeast(1f))
        val pitch = roadPitch + (min(roadPitch, cubePitch + 0.4f * halfV).coerceAtLeast(-maxDip) - roadPitch) * amount
        val yaw = roadYaw + (cubeYaw.coerceIn(roadYaw - 0.7f * halfH, roadYaw + 0.7f * halfH) - roadYaw) * amount
        val c = kotlin.math.cos(pitch)
        cam.direction.set(c * kotlin.math.sin(yaw), kotlin.math.sin(pitch), -c * kotlin.math.cos(yaw))
    }

    private val peekAt = Vector3()
    private val peekDir = Vector3()
    private val peekUp = Vector3()

    /**
     * After [watchFall], down a tar pit: the camera glides on over the hole at
     * ([holeX], [holeZ]) and peers down it at the cube ([x], [y], [z]), so the
     * fall reads as a drop down a bottomless well. [amount] 0..1 eases it in.
     */
    fun peekDown(holeX: Float, holeZ: Float, x: Float, y: Float, z: Float, amount: Float) {
        if (amount <= 0f) return
        cam.position.lerp(peekAt.set(holeX * 0.9f, 5.4f, holeZ + 0.9f), amount)
        peekDir.set(x, max(y, -12f), z).sub(cam.position).nor()
        cam.direction.lerp(peekDir, amount).nor()
        // Looking nearly straight down, "up" on screen becomes the road ahead.
        cam.up.lerp(peekUp.set(0f, 0f, -1f), amount * 0.9f).nor()
    }

    private fun pitchTo(dx: Float, dy: Float, dz: Float) = kotlin.math.atan2(dy, kotlin.math.sqrt(dx * dx + dz * dz))

    /** The wardrobe: the cube at [fraction] of the screen (centred by default), a little above eye level. [wide] pulls back for the trail demo. */
    fun wardrobe(wide: Float, fraction: Float = 0.5f) =
        frame(0.75f + 0.15f * wide, fraction, 2.3f + 0.5f * wide, 7.0f + 2.8f * wide, 40f)

    /**
     * Frame the world point (0, [targetY], 0) at [fraction] of the screen
     * height from the top, from a camera at (0, [cy], [cz]) with a [fov]:
     * the same shot on every phone whatever its aspect.
     */
    private fun frame(targetY: Float, fraction: Float, cy: Float, cz: Float, fov: Float) {
        val half = Math.toRadians((fov / 2f).toDouble())
        val above = kotlin.math.atan((0.5 - fraction) * 2.0 * kotlin.math.tan(half))      // the target's angle above the view centre
        val below = kotlin.math.atan(((cy - targetY) / cz).toDouble())                      // the target's angle below horizontal
        val lookY = cy - cz * kotlin.math.tan(below + above).toFloat()
        cam.position.set(0f, cy, cz)
        cam.lookAt(0f, lookY, 0f)
        cam.up.set(0f, 1f, 0f)
        cam.fieldOfView = fov
    }

    /** The shop: the cube small, centred in the showroom strip ([fraction] of the screen from the top). */
    fun shop(fraction: Float) = frame(1.0f, fraction, 3.2f, 14f, 40f)

    /** The results: the cube small and whole up top, the score below it. */
    fun results(fraction: Float) = frame(1.0f, fraction, 3.4f, 11.5f, 40f)
}
