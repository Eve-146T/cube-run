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
    private var chrome: Hud? = null,
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
        ui { it.setScore(v) }
    }

    override fun addScore(d: Int) {
        if (over.get()) return
        val v = scoreV.addAndGet(d)
        ui { it.setScore(v) }
    }

    override fun runStarted() {
        ui { it.hideOptions() }
    }

    override fun setCoins(v: Int) {
        if (over.get()) return
        coinsV.set(v)
        ui { it.setRunCoins(v) }
    }

    override fun setBubbles(v: Int) {
        ui { it.setBubbles(v) }
    }

    override fun setBubbleCooldown(seconds: Int) { ui { it.setBubbleCooldown(seconds) } }

    override fun setBoxes(v: Int) {
        if (over.get()) return
        boxesV.set(v)
        ui { it.setBoxes(v) }
    }

    override fun setWorld(name: String) {
        ui { it.setWorld(name) }
    }

    override fun setBoost(open: Boolean, taps: Int, max: Int) {
        ui { it.setBoost(open, taps, max) }
    }

    override fun setBonus(id: Int) {
        ui { it.setBonus(id) }
    }

    override fun boxOpened(kind: Int, amount: Int, cat: Int, id: Int) {
        ui { it.onBoxOpened(kind, amount, cat, id) }
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
        ui { it.showRunOver(finalScore, prevBest, isNew, runCoins, boxes) }
    }

    private val waiting = ArrayList<(Hud) -> Unit>()

    /** Attach after the first cube frame; keep every initial score/stock update in order. */
    fun attach(hud: Hud) {
        chrome = hud
        waiting.forEach { it(hud) }
        waiting.clear()
    }

    private fun ui(block: (Hud) -> Unit) = activity.runOnUiThread {
        val hud = chrome
        if (hud == null) waiting.add(block) else block(hud)
    }
}
