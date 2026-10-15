package cube.run.game

import cube.run.bot.JetDescent
import cube.run.game.track.LandingStrip
import cube.run.game.track.Track
import kotlin.math.exp
import kotlin.math.max

/**
 * A jetpack flight from take-off to touchdown. The pickup lays the landing
 * where the flight will end; the jet's clock is its arrival, so the flight
 * always comes down on open road. Until the jet cuts out the cube cruises
 * above everything; from then on it drops, the road can reach it again (the
 * landing keeps it clear) and there is no hidden safe time after.
 */
class JetFlight(private val track: Track, private val player: Player) {

    companion object {
        /** The jet's speed boost (+75%), eased in and out by the game. */
        const val SPEED_UP = 0.75f
    }

    /**
     * Take off for [seconds] at a cruising speed of [cruise] (the boost is
     * eased in at the game's rate from [boost]): lay the landing where the
     * flight will come down, clear it and lift the coins onto the flight line.
     */
    fun begin(seconds: Float, cruise: Float, boost: Float) {
        val easing = (1f - boost) * (1f - exp(-2f * seconds)) / 2f // the boost still to wind up
        val reach = cruise * (seconds + SPEED_UP * (seconds - easing))
        val descentLen = JetDescent.SECONDS * cruise * (1f + SPEED_UP)
        track.landing = LandingStrip(-reach, descentLen, max(14f, cruise))
        track.flight++
        track.airCoins = true
        track.liftCoins()
        track.clearStrip()
    }

    /** Seconds until the landing reaches the cube at speed [spd]: the jet's clock. */
    fun secondsLeft(spd: Float): Float? = track.landing?.let { max(0f, -it.touchZ) / max(1f, spd) }

    /** Steer the cube down its drop: [left] seconds to touchdown, settling at [landingY]. */
    fun steer(left: Float, landingY: Float) {
        player.descending = left < JetDescent.SECONDS
        player.flyY = landingY + (Player.FLY_Y - landingY) * JetDescent.height(1f - left / JetDescent.SECONDS)
    }

    /** The flight is over: onto the landing at [y], like any other landing. */
    fun touchDown(y: Float) {
        track.airCoins = false
        track.dropCoins()
        player.touchDown(y)
    }
}
