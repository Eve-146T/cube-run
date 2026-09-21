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
    private val shardsV = java.util.concurrent.atomic.AtomicIntegerArray(3)

    override fun addShard(kind: Int) {
        if (!over.get() && kind in 0..2) shardsV.incrementAndGet(kind)
    }

    private var runHasStarted = false
    private var centerLaneObserved = false
    private var stayedCentered = true
    private var stayedCoinless = true
    private var missedBoxes = 0

    init {
        // Older versions saved dev scores to the leaderboard while excluding
        // achievement progress. Reconcile that existing best without requiring
        // another record-breaking run or a full process restart.
        Progress.recordRunProgress(Scores.best(id), 0)
    }

    override val score: Int get() = scoreV.get()
    override val isOver: Boolean get() = over.get()

    @Synchronized override fun setScore(v: Int) {
        if (over.get()) return
        scoreV.set(v)
        Progress.recordRunProgress(v, 0)
        recordRunChallenges(v)
        ui { it.setScore(v) }
    }

    @Synchronized override fun addScore(d: Int) {
        if (over.get()) return
        val v = scoreV.addAndGet(d)
        Progress.recordRunProgress(v, 0)
        recordRunChallenges(v)
        ui { it.setScore(v) }
    }

    @Synchronized override fun runStarted() {
        runHasStarted = true
        centerLaneObserved = false
        stayedCentered = true
        stayedCoinless = true
        missedBoxes = 0
        Progress.clearRunCoins()
        ui { it.hideOptions() }
    }

    @Synchronized override fun laneChanged(lane: Int, laneCount: Int) {
        if (!runHasStarted || over.get()) return
        centerLaneObserved = true
        // Once lost, eligibility stays lost even when two opposite inputs are
        // processed before the next render frame. Portal remaps preserve center.
        if (laneCount <= 0 || lane != laneCount / 2) stayedCentered = false
    }

    private fun recordRunChallenges(score: Int) {
        if (runHasStarted && centerLaneObserved && stayedCentered) Progress.recordCenteredScore(score)
        if (runHasStarted && stayedCoinless) Progress.recordCoinlessScore(score)
    }

    @Synchronized override fun coinPickedUp() {
        if (runHasStarted && !over.get()) stayedCoinless = false
    }

    @Synchronized override fun mysteryBoxMissed() {
        if (!runHasStarted || over.get()) return
        missedBoxes = (missedBoxes + 1).coerceAtMost(10)
        Progress.recordMissedBoxes(missedBoxes)
    }

    override fun jackpotWon(amount: Int) {
        if (over.get() || amount <= 0) return
        ui { it.showJackpot(amount) }
    }

    override fun setCoins(v: Int) {
        if (over.get()) return
        coinsV.set(v)
        Progress.recordRunCoins(v)
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
        val runShards = IntArray(3) { shardsV.get(it) }
        for (kind in runShards.indices) if (runShards[kind] > 0) Progress.addShards(kind, runShards[kind])
        Progress.recordRunProgress(finalScore, 0)
        Progress.clearRunCoins()
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
        ui { it.showRunOver(finalScore, prevBest, isNew, runCoins, boxes, runShards) }
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
