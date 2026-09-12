package cube.run.core

import android.app.Activity
import cube.run.data.Progress
import cube.run.data.Scores
import cube.run.ui.Hud
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** The GameSession implementation: marshals score/coins/game-over to the HUD on the UI thread. */
class GameHostSession(
    private val activity: Activity,
    private val id: String,
    private val chrome: Hud,
) : GameSession {

    private val scoreV = AtomicInteger(0)
    private val coinsV = AtomicInteger(0)
    private val boxesV = AtomicInteger(0)
    private val over = AtomicBoolean(false)

    override val score: Int get() = scoreV.get()
    override val isOver: Boolean get() = over.get()

    override fun setScore(v: Int) {
        if (over.get()) return
        scoreV.set(v)
        ui { chrome.setScore(v) }
    }

    override fun addScore(d: Int) {
        if (over.get()) return
        val v = scoreV.addAndGet(d)
        ui { chrome.setScore(v) }
    }

    override fun runStarted() {
        ui { chrome.hideOptions() }
    }

    override fun setCoins(v: Int) {
        if (over.get()) return
        coinsV.set(v)
        ui { chrome.setRunCoins(v) }
    }

    override fun setBubbles(v: Int) {
        ui { chrome.setBubbles(v) }
    }

    override fun setBubbleCooldown(seconds: Int) { ui { chrome.setBubbleCooldown(seconds) } }

    override fun setBoxes(v: Int) {
        if (over.get()) return
        boxesV.set(v)
        ui { chrome.setBoxes(v) }
    }

    override fun setWorld(name: String) {
        ui { chrome.setWorld(name) }
    }

    override fun setBoost(open: Boolean, taps: Int, max: Int) {
        ui { chrome.setBoost(open, taps, max) }
    }

    override fun setBonus(id: Int) {
        ui { chrome.setBonus(id) }
    }

    override fun boxOpened(kind: Int, amount: Int, cat: Int, id: Int) {
        ui { chrome.onBoxOpened(kind, amount, cat, id) }
    }

    override fun gameOver() {
        if (!over.compareAndSet(false, true)) return
        val finalScore = scoreV.get()
        val runCoins = coinsV.get()
        val boxes = boxesV.get()
        Progress.addCoins(runCoins)
        Progress.countRun()
        val prevBest = Scores.best(id)
        val isNew = finalScore > 0 && Scores.submit(id, finalScore)
        if (isNew) {
            SoundFx.play("success")
            Haptics.success()
        } else {
            SoundFx.play("fail")
            Haptics.fail()
        }
        ui { chrome.showRunOver(finalScore, prevBest, isNew, runCoins, boxes) }
    }

    private fun ui(block: () -> Unit) = activity.runOnUiThread(block)
}
