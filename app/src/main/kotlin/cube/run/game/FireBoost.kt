package cube.run.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import cube.run.core.Gdx3DGame
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.core.hsvInto
import cube.run.data.Settings

/**
 * The fire boost: for the first [window] seconds of a run the HUD shows a
 * BOOST button; each tap (delivered through [Stage.boostRequests])
 * front-loads your speed, and [maxTaps] taps launch you at 80% of the
 * blue-line (cruise) speed. This class owns the rules and tells the HUD
 * (through the session) when the window opens and closes.
 */
class FireBoost(private val game: Gdx3DGame, private val difficulty: Difficulty) {

    private val window = 15f
    private val maxTaps get() = if (Settings.devMode) 10 else 5
    private val maxSpd = difficulty.cruiseSpd * 0.8f
    private val maxDiff = (maxSpd - difficulty.minSpd) / (difficulty.maxSpd - difficulty.minSpd)
    private var taps = 0
    private var runTime = 0f
    private var shown = false
    private val tmp = Vector3()
    private val tmpCol = Color()

    fun reset() {
        taps = 0; runTime = 0f
        shown = true
        Stage.boostRequests.set(0)
        game.session.setBoost(true, 0, maxTaps)
    }

    /** Advance the window; apply any taps the HUD queued. Returns how many taps counted this frame. */
    fun tick(dt: Float, px: Float, py: Float): Int {
        runTime += dt
        var counted = 0
        var n = Stage.boostRequests.getAndSet(0)
        while (n-- > 0 && available()) { tap(px, py); counted++ }
        if (shown && !available()) { shown = false; game.session.setBoost(false, taps, maxTaps) }
        return counted
    }

    private fun available(): Boolean = Settings.devMode || runTime < window && taps < maxTaps

    /** One tap: bump difficulty a notch with a hot orange punch. */
    private fun tap(px: Float, py: Float) {
        taps = if (Settings.devMode) taps % 10 + 1 else taps + 1
        val target = if (taps <= 5) difficulty.startDiff + (maxDiff - difficulty.startDiff) * (taps / 5f)
            else maxDiff + (1f - maxDiff) * ((taps - 5) / 5f)
        if (Settings.devMode) difficulty.setDevBoost(target) else difficulty.boostTo(target)
        SoundFx.play("rise", rate = 0.85f + taps * 0.12f)
        Haptics.click()
        game.flash(hsvInto(tmpCol, if (taps > 5) 205f else 22f, 0.85f, 1f), 0.12f)
        game.burst3d(tmp.set(px, py + 0.3f, 0.3f), hsvInto(tmpCol, 26f, 0.9f, 1f), n = 14, speed = 6f, size = 0.12f, life = 0.55f)
        game.burst3d(tmp.set(px, py, 0.6f), hsvInto(tmpCol, 40f, 0.9f, 1f), n = 10, speed = 3f, size = 0.09f, life = 0.7f, biasZ = 9f)
        game.session.setBoost(available(), taps, maxTaps)
    }
}
