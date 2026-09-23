package cube.run.core

import android.app.Activity
import cube.run.data.Progress
import cube.run.data.Settings
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
        if (!over.get() && kind in 0..2) {
            shardsV.incrementAndGet(kind)
            if (runHasStarted) {
                shardMask = shardMask or (1 shl kind)
                Progress.bestMetric("shard_hunter", shardMask)
            }
        }
    }

    private var runHasStarted = false
    private var centerLaneObserved = false
    private var stayedCentered = true
    private var stayedCoinless = true
    private var missedBoxes = 0
    private var noPowerup = true
    private var runBoxesCollected = 0
    private var bonusMask = 0
    private var shardMask = 0
    private var lastDistance = 0
    private var observedDistance = 0
    private var lastSwipeFrom = -1
    private var lastSwipeTo = -1
    private var lastSwipeAt = 0L
    private var reversals = 0
    private var runSkin = 0
    private var silentAtStart = false
    private var silentRevision = 0

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
        noPowerup = true; runBoxesCollected = 0; bonusMask = 0; shardMask = 0
        lastDistance = 0; observedDistance = 0; lastSwipeFrom = -1; lastSwipeTo = -1; reversals = 0
        runSkin = Progress.skin
        silentAtStart = runSkin == 0 && !Settings.soundEnabled && !Settings.hapticsEnabled
        silentRevision = Settings.audioHapticRevision
        Progress.clearRunCoins()
        Progress.addMetric("regular")
        ui { it.hideOptions() }
    }

    @Synchronized override fun laneChanged(lane: Int, laneCount: Int) {
        if (!runHasStarted || over.get()) return
        centerLaneObserved = true
        // Once lost, eligibility stays lost even when two opposite inputs are
        // processed before the next render frame. Portal remaps preserve center.
        if (laneCount <= 0 || lane != laneCount / 2) stayedCentered = false
    }

    override fun userLaneSwipe(from: Int, to: Int) {
        if (!runHasStarted || over.get() || from == to) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (from == lastSwipeTo && to == lastSwipeFrom && now - lastSwipeAt <= 1_000L) {
            reversals++
            Progress.bestMetric("two_ez", reversals.coerceAtMost(50))
        }
        lastSwipeFrom = from; lastSwipeTo = to; lastSwipeAt = now
    }

    private fun recordRunChallenges(score: Int) {
        if (runHasStarted && centerLaneObserved && stayedCentered) Progress.recordCenteredScore(score)
        if (runHasStarted && stayedCoinless) Progress.recordCoinlessScore(score)
        if (runHasStarted && noPowerup) Progress.bestMetric("untouchable", score.coerceAtMost(150))
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
        if (amount >= 5000) Progress.bestMetric("house_loses", 1)
        ui { it.showJackpot(amount) }
    }

    override fun nearMiss() { if (runHasStarted && !over.get()) Progress.addMetric("near_miss") }
    override fun powerupPickedUp() { noPowerup = false }
    override fun boxCollected() {
        if (!runHasStarted || over.get()) return
        runBoxesCollected++
        Progress.bestMetric("greedy", runBoxesCollected.coerceAtMost(13))
    }
    override fun coalCollected() { if (runHasStarted && !over.get()) Progress.addMetric("coal_miner") }
    override fun fullKitHeld() { if (runHasStarted && !over.get()) Progress.bestMetric("full_kit", 1) }
    override fun distanceCovered(metres: Int) {
        if (!runHasStarted || over.get() || metres <= lastDistance) return
        observedDistance = metres
        if (metres - lastDistance >= 100) {
            Progress.addMetric("long_hauler", metres - lastDistance)
            lastDistance = metres
        }
    }
    override fun runSeconds(seconds: Int) {
        if (runHasStarted && !over.get() && seconds % 10 == 0) Progress.bestMetric("long_con", seconds.coerceAtMost(600))
    }
    override fun runCrashed(seconds: Float) {
        if (runHasStarted && !over.get() && seconds < 2f) Progress.addMetric("stage_fright")
    }

    override fun setCoins(v: Int) {
        if (over.get()) return
        coinsV.set(v)
        Progress.recordRunCoins(v)
        Progress.bestMetric("magpie", v.coerceAtMost(10000))
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
        if (runHasStarted && !over.get() && id in 0..3) {
            bonusMask = bonusMask or (1 shl id)
            Progress.bestMetric("scenic_route", bonusMask)
            Progress.markMetricBit("globetrotter", id)
        }
        ui { it.setBonus(id) }
    }

    override fun boxOpened(kind: Int, amount: Int, cat: Int, id: Int) {
        ui { it.onBoxOpened(kind, amount, cat, id) }
    }

    override fun gameOver() {
        if (!over.compareAndSet(false, true)) return
        Progress.addMetric("long_hauler", observedDistance - lastDistance)
        val finalScore = scoreV.get()
        val runCoins = coinsV.get()
        val boxes = boxesV.get()
        val runShards = IntArray(3) { shardsV.get(it) }
        if (finalScore == 67) Progress.bestMetric("exactly_67", 1)
        if (finalScore >= 100 && silentAtStart && silentRevision == Settings.audioHapticRevision && !Settings.soundEnabled && !Settings.hapticsEnabled)
            Progress.bestMetric("silent_treatment", 1)
        if (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) == 3)
            Progress.bestMetric("insomniac", 1)
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
