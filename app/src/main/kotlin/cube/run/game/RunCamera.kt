package cube.run.game

import com.badlogic.gdx.graphics.PerspectiveCamera
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
        val i = 1f - intro
        val g = Terrain.y(0f)
        val cy = 3.6f + lift * 0.3f * (1f - f) + f * 9.5f + deathT * 1.6f + i * 1.9f + wide * 1.4f + g * 0.8f
        cam.position.set(px * (0.45f - 0.15f * f), cy, 6.4f + f * 1.2f + deathT * 2.2f + dolly + i * 2.6f + wide * 2.2f)
        cam.lookAt(px * (0.55f - 0.15f * f), 1.0f + lift * 0.5f * (1f - f) + f * 1.6f - i * 0.4f + g * 0.5f + Terrain.y(-8f) * 0.4f, -8f - f * 6f - i * 6f)
        val rr = Math.toRadians(roll.toDouble())
        cam.up.set(kotlin.math.sin(rr).toFloat(), kotlin.math.cos(rr).toFloat(), 0f)
        kick = max(0f, kick - dt * 2.2f)
        cam.fieldOfView = 60f + max(0f, spd - 10f) * 0.42f + kick * kick * 9f
    }

    /** The wardrobe: the cube centred, a little above eye level. [wide] pulls back for the trail demo. */
    fun wardrobe(wide: Float) {
        cam.position.set(0f, 2.3f + 0.5f * wide, 7.0f + 2.8f * wide)
        cam.lookAt(0f, 0.75f + 0.15f * wide, 0f)
        cam.up.set(0f, 1f, 0f)
        cam.fieldOfView = 40f
    }

    /** The shop: the cube small, high up under the title, the cards below it. */
    fun shop() {
        cam.position.set(0f, 3.2f, 14f)
        cam.lookAt(0f, -1.9f, 0f)
        cam.up.set(0f, 1f, 0f)
        cam.fieldOfView = 40f
    }

    /** The results: the cube small and whole in the top third, the score card below it. */
    fun results() {
        cam.position.set(0f, 3.4f, 11.5f)
        cam.lookAt(0f, -2.1f, 0f)
        cam.up.set(0f, 1f, 0f)
        cam.fieldOfView = 40f
    }
}
