package cube.run.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import cube.run.bot.JetDescent
import cube.run.core.Gdx3DGame
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.data.Settings
import cube.run.game.track.LandingStrip
import cube.run.game.track.Track
import kotlin.math.exp
import kotlin.math.max

/**
 * A jetpack flight from take-off to touchdown. The pickup lays a landing
 * strip where the flight will end; the jet's clock is that strip's arrival,
 * so the flight always comes down on open road. Until the descent starts
 * the cube cruises above everything; from then on the road can reach it
 * again (the strip keeps it clear) and there is no hidden safe time after.
 */
class JetFlight(private val game: Gdx3DGame, private val track: Track, private val player: Player, private val rig: RunCamera) {

    companion object {
        /** The jet cuts out over the strip; the cube drops and lands with a thud. */
        const val LAND_FALL = 0
        /** The same drop, landed with a shock that ripples through the strip. */
        const val LAND_SLAM = 1
        /** The jet throttles down and glides onto the strip. */
        const val LAND_GLIDE = 2
        const val LANDS = 3
        /** The jet's speed boost (+75%), eased in and out by the game. */
        const val SPEED_UP = 0.75f
    }

    val variant: Int get() = Settings.testJetLand.takeIf { it in 0 until LANDS } ?: LAND_FALL
    val descent: Int get() = if (variant == LAND_GLIDE) JetDescent.GLIDE else JetDescent.FALL

    private val at = Vector3()
    private val dust = Color(0.97f, 0.95f, 0.9f, 1f)
    private val ink = Color(0.10f, 0.08f, 0.18f, 1f)

    /**
     * Take off for [seconds] at a cruising speed of [cruise] (the boost is
     * eased in at the game's rate from [boost]): lay the strip where the
     * flight will come down, clear it and lift the coins onto the flight line.
     */
    fun begin(seconds: Float, cruise: Float, boost: Float) {
        val easing = (1f - boost) * (1f - exp(-2f * seconds)) / 2f // the boost still to wind up
        val reach = cruise * (seconds + SPEED_UP * (seconds - easing))
        val descentLen = JetDescent.seconds(descent) * cruise * (1f + SPEED_UP)
        track.jetDescent = descent
        track.landing = LandingStrip(-reach, descentLen, max(16f, cruise * 1.5f))
        track.airCoins = true
        track.clearStrip()
        track.liftCoins()
    }

    /** Seconds until the strip reaches the cube at speed [spd]: the jet's clock. */
    fun secondsLeft(spd: Float): Float? = track.landing?.let { max(0f, -it.touchZ) / max(1f, spd) }

    /** Steer the cube down its descent: [left] seconds to touchdown, settling at [landingY]. */
    fun steer(left: Float, landingY: Float) {
        val d = JetDescent.seconds(descent)
        player.descending = left < d
        player.flyY = landingY + (Player.FLY_Y - landingY) * JetDescent.height(1f - left / d, descent)
    }

    /** The flight is over: onto the strip at [y], with the landing [variant]'s weight. */
    fun touchDown(y: Float, time: Float, trackColor: Color) {
        val strip = track.landing
        track.airCoins = false
        track.dropCoins()
        val px = player.px
        when (variant) {
            LAND_SLAM -> {
                player.touchDown(y, 2.2f)
                player.rebound(4.5f) // it bounces off the strip once
                strip?.let { it.impactAt = time; it.impactX = px; it.impactPower = 1f }
                SoundFx.play("boom", rate = 0.75f)
                SoundFx.play("place", rate = 0.6f)
                Haptics.heavy()
                game.flash(Color.WHITE, 0.35f)
                game.slowMo(0.3f, 0.22f)
                game.shake(0.25f)
                rig.punch(1.2f)
                game.burst3d(at.set(px, y - 0.3f, 0.2f), Color.WHITE, n = 34, speed = 11f, size = 0.2f, life = 1.1f, gravity = 16f)
                game.burst3d(at.set(px, y - 0.3f, 0.2f), ink, n = 28, speed = 10f, size = 0.18f, life = 1.1f, gravity = 16f)
                game.burst3d(at.set(px, y, 0f), player.col, n = 10, speed = 5f, size = 0.12f, life = 0.6f)
            }
            LAND_FALL -> {
                player.touchDown(y, 1.6f)
                strip?.let { it.impactAt = time; it.impactX = px; it.impactPower = 0.3f }
                SoundFx.play("place", rate = 0.7f)
                SoundFx.play("pop", rate = 0.7f)
                Haptics.heavy()
                rig.punch(0.35f)
                game.burst3d(at.set(px, y - 0.38f, 0.3f), dust, n = 14, speed = 4f, size = 0.11f, life = 0.5f, gravity = 10f)
            }
            else -> {
                player.touchDown(y, 0.8f)
                SoundFx.play("pop", rate = 0.9f)
                Haptics.click()
                game.burst3d(at.set(px, y - 0.39f, 0.4f), trackColor, n = 8, speed = 3.2f, size = 0.09f, life = 0.4f)
            }
        }
    }
}
