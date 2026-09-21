package cube.run.data

import android.content.Context
import android.content.SharedPreferences
import android.app.Activity
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.core.GameHostSession
import cube.run.core.Gdx3DGame
import cube.run.game.Lanes
import cube.run.game.Player
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/** Purchase and milestone boundaries against the real persisted progression store. */
class AchievementsProgressTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var prefs: SharedPreferences
    private lateinit var scores: SharedPreferences
    private lateinit var saved: Map<String, *>
    private lateinit var savedScores: Map<String, *>

    @Before fun prepare() {
        prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        scores = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
        saved = prefs.all; savedScores = scores.all
        prefs.edit().clear().commit(); scores.edit().clear().commit()
        Settings.init(context); Settings.setDevMode(false); Progress.init(context)
    }

    @After fun restore() {
        fun restoreStore(store: SharedPreferences, values: Map<String, *>) {
            val edit = store.edit().clear()
            for ((key, value) in values) when (value) {
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is String -> edit.putString(key, value)
            }
            edit.commit()
        }
        Settings.setDevMode(false)
        Lanes.reset()
        restoreStore(prefs, saved); restoreStore(scores, savedScores); Progress.init(context)
    }

    private fun seed(vararg values: Pair<String, Int>, unlocked: Boolean = false) {
        val edit = prefs.edit().putBoolean("achievements_unlocked", unlocked)
        values.forEach { (key, value) -> edit.putInt(key, value) }
        edit.commit(); Progress.init(context)
    }

    private fun state(id: String) = Achievements.snapshot().single { it.definition.id == id }

    /** Real Player input callbacks and host scoring, without creating a GL surface. */
    private fun centeredRun(): Pair<GameHostSession, Player> {
        Scores.init(context)
        Lanes.reset()
        val session = GameHostSession(Activity(), "cuberun")
        val game = object : Gdx3DGame(session) {
            override fun init() {}
            override fun tick(dt: Float) {}
            override fun renderWorld(batch: ModelBatch, env: Environment) {}
        }
        val player = Player(game, Random(17))
        session.runStarted()
        session.laneChanged(player.lane, Lanes.count)
        session.coinPickedUp() // Keep center-specific notification assertions independent of Homeress.
        return session to player
    }

    @Test fun centeredChallengeUnlocksAtExactly100AndSurvivesLaterDepartureAndReload() {
        seed(unlocked = true)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val (session, player) = centeredRun()
            session.setScore(99)
            assertEquals(99, state("center").value)
            assertEquals(0, state("center").earnedTiers)
            session.addScore(1)
            assertEquals("center", Achievements.drainUnlocks().single().definition.id)
            assertTrue(player.moveToLane(0))
            session.addScore(10)
            assertEquals(1, state("center").earnedTiers)
        }
        Progress.init(context)
        assertEquals(100, Progress.bestCenteredScore)
        assertEquals(1500, Achievements.claim("center"))
        assertEquals(0, Achievements.claim("center"))
        assertTrue(state("center").allClaimed)
    }

    @Test fun leavingAndReturningBeforeOneFramePermanentlyDisqualifiesOnlyThatRun() {
        seed(unlocked = true)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val (session, player) = centeredRun()
            session.setScore(99)
            assertTrue(player.moveToLane(0))
            assertTrue(player.moveToLane(1)) // opposite input, with no update/render between
            session.addScore(2)
            assertEquals(99, Progress.bestCenteredScore)
            assertEquals(0, state("center").earnedTiers)
            assertTrue(Achievements.drainUnlocks().isEmpty())
            val (next, _) = centeredRun()
            next.addScore(100)
            assertEquals(1, state("center").earnedTiers)
        }
    }

    @Test fun centerToCenterPortalRemapsPreserveEligibilityAndOldScoresNeverGrantIt() {
        seed("achievement_best_score" to 5001, unlocked = true)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val (session, player) = centeredRun()
            assertEquals(0, state("center").earnedTiers)
            session.setScore(50)
            Lanes.count = 5; player.remapLane(3, 5)
            assertEquals(2, player.lane)
            session.addScore(25)
            Lanes.count = 3; player.remapLane(5, 3)
            assertEquals(1, player.lane)
            session.addScore(25)
            assertEquals(1, state("center").earnedTiers)
        }
        Lanes.reset()
    }

    @Test fun centeredTrackingRequiresAnObservedLiveRunAndDevStillRespectsTheAchievementGate() {
        seed("coins" to 3000)
        Settings.setDevMode(true)
        Scores.init(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val unstarted = GameHostSession(Activity(), "cuberun")
            unstarted.setScore(500)
            assertEquals(0, Progress.bestCenteredScore)
            unstarted.runStarted()
            unstarted.setScore(600) // no lane history supplied; cannot certify the challenge
            assertEquals(0, Progress.bestCenteredScore)
            val (session, _) = centeredRun()
            session.addScore(100)
            assertEquals(100, Progress.bestCenteredScore)
            assertTrue(Achievements.drainUnlocks().isEmpty())
        }
        assertEquals(0, Achievements.claim("center"))
        assertTrue(Progress.buyAchievements())
        assertTrue(Achievements.drainUnlocks().isEmpty())
        assertEquals(1500, Achievements.claim("center"))
    }

    @Test fun homeressRequires60InOneCoinlessRunAndAPhysicalZeroValuePickupDisqualifies() {
        seed("achievement_best_score" to 5001, unlocked = true)
        Scores.init(context)
        assertEquals(0, state("homeress").value) // No inference from legacy score history.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val first = GameHostSession(Activity(), "cuberun")
            first.runStarted(); first.setScore(59)
            assertEquals(0, state("homeress").earnedTiers)
            first.coinPickedUp(); first.setCoins(0) // Coal/Gambler can pay zero for a real pickup.
            first.addScore(1); first.setCoins(0); first.addScore(10)
            assertEquals(59, Progress.bestCoinlessScore)
            assertEquals(0, state("homeress").earnedTiers)
            assertTrue(Achievements.drainUnlocks().isEmpty())
            val next = GameHostSession(Activity(), "cuberun")
            next.runStarted(); next.setScore(59); next.addScore(1)
            assertEquals("homeress", Achievements.drainUnlocks().single().definition.id)
            next.coinPickedUp(); next.addScore(10) // Earned at60: a later pickup cannot revoke it.
        }
        Progress.init(context)
        assertEquals(60, Progress.bestCoinlessScore)
        assertEquals(1500, Achievements.claim("homeress"))
        assertEquals(0, Achievements.claim("homeress"))
    }

    @Test fun missedBoxesUseOneRunMaximumResetAndIgnoreInactiveSessions() {
        seed(unlocked = true)
        Scores.init(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val first = GameHostSession(Activity(), "cuberun")
            repeat(10) { first.mysteryBoxMissed() }
            assertEquals(0, Progress.maxRunMissedBoxes)
            first.runStarted(); repeat(9) { first.mysteryBoxMissed() }
            assertEquals(9, Progress.maxRunMissedBoxes)
            assertEquals(0, state("gambliphobic").earnedTiers)
            first.gameOver(); first.mysteryBoxMissed()
            assertEquals(9, Progress.maxRunMissedBoxes)
            val next = GameHostSession(Activity(), "cuberun")
            next.runStarted(); repeat(9) { next.mysteryBoxMissed() }
            assertEquals(9, Progress.maxRunMissedBoxes) // Does not sum across runs.
            next.mysteryBoxMissed()
            assertEquals("gambliphobic", Achievements.drainUnlocks().single().definition.id)
            repeat(10) { next.mysteryBoxMissed() }
            assertTrue(Achievements.drainUnlocks().isEmpty())
        }
        Progress.init(context)
        assertEquals(10, Progress.maxRunMissedBoxes)
        assertEquals(1500, Achievements.claim("gambliphobic"))
        assertEquals(0, Achievements.claim("gambliphobic"))
    }

    @Test fun cookieCountsOnlyExplicitUserToggleEventsAndPersistsAt1000() {
        seed("total_mute_toggles" to 998, unlocked = true)
        val initialSound = Settings.soundEnabled
        try {
            Settings.setSoundEnabled(!initialSound); Settings.init(context)
            Settings.setSoundEnabled(initialSound)
            assertEquals(998, Progress.totalMuteToggles)
            Progress.recordMuteToggle()
            assertEquals(999, state("cookie").value)
            assertEquals(0, state("cookie").earnedTiers)
            Progress.init(context)
            assertEquals(999, Progress.totalMuteToggles)
            Progress.recordMuteToggle()
            assertEquals("cookie", Achievements.drainUnlocks().single().definition.id)
            repeat(10) { Progress.recordMuteToggle() }
            assertTrue(Achievements.drainUnlocks().isEmpty())
            Progress.init(context)
            assertEquals(1010, Progress.totalMuteToggles)
            assertEquals(2000, Achievements.claim("cookie"))
            assertEquals(0, Achievements.claim("cookie"))
        } finally { Settings.setSoundEnabled(initialSound) }
    }

    @Test fun newChallengesTrackInDevButStayQuietAndUnclaimableBeforePurchase() {
        seed("coins" to 3000, "total_mute_toggles" to 999)
        Settings.setDevMode(true); Scores.init(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val session = GameHostSession(Activity(), "cuberun")
            session.runStarted(); session.setScore(60)
            repeat(10) { session.mysteryBoxMissed() }
        }
        Progress.recordMuteToggle()
        for (id in listOf("homeress", "gambliphobic", "cookie")) {
            assertEquals(1, state(id).earnedTiers)
            assertEquals(0, Achievements.claim(id))
        }
        assertTrue(Achievements.drainUnlocks().isEmpty())
        assertTrue(Progress.buyAchievements())
        assertTrue(Achievements.drainUnlocks().isEmpty())
        assertEquals(1500, Achievements.claim("homeress"))
        assertEquals(1500, Achievements.claim("gambliphobic"))
        assertEquals(2000, Achievements.claim("cookie"))
        Progress.init(context)
        assertEquals(5000, Progress.coins)
        assertEquals(0, Progress.totalCoins)
        for (id in listOf("homeress", "gambliphobic", "cookie")) assertTrue(state(id).allClaimed)
    }

    @Test fun lockedProgressIsRetainedAndPurchaseAwardsItQuietlyExactlyOnce() {
        Progress.addCoins(25000); Progress.recordRunProgress(2001, 67)
        repeat(100) { Progress.recordPowerup() }
        assertTrue(Achievements.drainUnlocks().isEmpty())
        assertTrue(Progress.buyAchievements()); assertEquals(22000, Progress.coins)
        assertTrue(Achievements.drainUnlocks().isEmpty())
        assertEquals(3, state("runner").earnedTiers)
        assertEquals(1, state("bounces").earnedTiers)
        assertFalse(Progress.buyAchievements()); assertEquals(22000, Progress.coins)
        Progress.init(context)
        assertTrue(Progress.achievementsUnlocked); assertTrue(Achievements.drainUnlocks().isEmpty())
    }

    @Test fun insufficientFundsNeverUnlockOrCharge() {
        Progress.addCoins(2999)
        assertFalse(Progress.buyAchievements()); assertEquals(2999, Progress.coins)
        assertFalse(Progress.achievementsUnlocked)
    }

    @Test fun scoreRequiresStrictlyHigherAndCrossedTiersCollapseToOneEvent() {
        seed(unlocked = true)
        Progress.recordRunProgress(500, 0)
        assertEquals(0, state("runner").earnedTiers); assertTrue(Achievements.drainUnlocks().isEmpty())
        Progress.recordRunProgress(501, 0); Progress.recordRunProgress(1001, 0)
        Progress.recordRunProgress(2001, 0); Progress.recordRunProgress(5000, 0)
        val events = Achievements.drainUnlocks()
        assertEquals(1, events.size); assertEquals(2, events.single().tier)
        Progress.recordRunProgress(5001, 0)
        assertEquals(3, Achievements.drainUnlocks().single().tier)
        assertNull(state("runner").nextTarget); assertEquals(1f, state("runner").fraction, 0f)
    }

    @Test fun singleRunChallengesUseMaximaAndStayEarnedAfterSpending() {
        seed(unlocked = true)
        Progress.recordRunProgress(100, 40); Progress.recordRunProgress(100, 40)
        assertEquals(40, state("bounces").value); assertEquals(0, state("bounces").earnedTiers)
        Progress.recordRunProgress(100, 67); Progress.addBubble(1000)
        assertEquals(setOf("bounces", "bubbles"), Achievements.drainUnlocks().map { it.definition.id }.toSet())
        assertTrue(Progress.useBubble(0f)); Progress.init(context)
        assertEquals(1, state("bubbles").earnedTiers); assertEquals(1000, state("bubbles").value)
    }

    @Test fun lifetimeCoinsSurviveSpendingAndPowerupsPersistAtTheBoundary() {
        seed("coins" to 3000, "total_coins" to 4999, "total_powerups" to 99, unlocked = true)
        Progress.addCoins(1); Progress.recordPowerup()
        assertEquals(setOf("coins", "powerups"), Achievements.drainUnlocks().map { it.definition.id }.toSet())
        assertTrue(Progress.buyBubble()); Progress.init(context)
        assertEquals(5000, Progress.totalCoins); assertEquals(100, Progress.totalPowerups)
        assertEquals(25000, state("coins").nextTarget)
        assertEquals(.2f, state("coins").fraction, .0001f)
    }

    @Test fun mysteryPurchaseIsAtomicAndPersistsOneRewardAndOneOpenedBox() {
        val cost = Progress.mysteryBoxPrice
        assertTrue(cost > 0); assertEquals(0, cost % 100)
        seed("coins" to cost - 1)
        assertNull(Progress.buyMysteryBox()); assertEquals(0, Progress.boxesOpened)
        seed("coins" to cost, "boxes_opened" to 9, unlocked = true)
        val bubbleRoll = object : Random() { override fun nextBits(bitCount: Int) = 0; override fun nextFloat() = .4f }
        val reward = Progress.buyMysteryBox(bubbleRoll)!!
        assertEquals(Progress.BoxReward.BUBBLE, reward.kind)
        assertEquals(0, Progress.coins); assertEquals(reward.amount, Progress.bubbles)
        assertEquals("boxes", Achievements.drainUnlocks().single().definition.id)
        Progress.init(context); assertEquals(10, Progress.boxesOpened); assertEquals(reward.amount, Progress.bubbles)
    }

    @Test fun earnedRunCoinMilestoneSurvivesClearingTransientRunBalance() {
        seed("total_coins" to 4900, unlocked = true)
        Progress.recordRunCoins(100)
        assertEquals("coins", Achievements.drainUnlocks().single().definition.id)
        Progress.clearRunCoins(); Progress.init(context)
        assertEquals(1, state("coins").earnedTiers)
        assertEquals(4900, Progress.totalCoins)
        assertTrue(Achievements.drainUnlocks().isEmpty())
    }

    @Test fun developerRunsAwardProgressAndQueueAchievementsBehindThePurchaseGate() {
        seed(unlocked = true)
        Settings.setDevMode(true)
        Progress.recordRunProgress(5001, 67); Progress.recordPowerup(); Progress.recordRunCoins(500000)
        assertEquals(setOf("runner", "bounces", "coins"), Achievements.drainUnlocks().map { it.definition.id }.toSet())
        assertEquals(4, prefs.getInt("achievement_runner", 0))
        assertEquals(4, prefs.getInt("achievement_coins", 0))
        assertEquals(1, Progress.totalPowerups)
        assertEquals(1500, Achievements.claim("bounces"))
        assertEquals(1, state("bounces").claimedTiers)
    }

    /** Exercise the production bridge used by scoreRow, multipliers and smash. */
    @Test fun normalSessionTracksTheDisplayedScoreIncludingMultiplierAndBonusDeltas() {
        seed(unlocked = true)
        Scores.init(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val session = GameHostSession(Activity(), "cuberun")
            session.runStarted()
            repeat(250) { session.addScore(2) } // 250 rows under the 2x score power-up
            assertEquals(500, session.score)
            assertEquals(session.score, state("runner").value)
            assertEquals(0, state("runner").earnedTiers)
            session.addScore(4) // doubled near-miss bonus: strict bronze boundary crossed
            assertEquals(504, session.score)
            assertEquals(session.score, state("runner").value)
            assertEquals(1, state("runner").earnedTiers)
            session.setScore(998)
            session.addScore(3) // bubble smash crosses silver without a subsequent scored row
            assertEquals(1001, session.score)
            assertEquals(2, state("runner").earnedTiers)
            session.gameOver()
            session.addScore(10000) // engine callbacks after game-over cannot change progress
            assertEquals(1001, session.score)
        }
        Progress.init(context)
        assertEquals(1001, state("runner").value)
        assertEquals(2, state("runner").earnedTiers)
        assertEquals(1001, Scores.best("cuberun"))
    }

    @Test fun startingANormalSessionReconcilesASavedBestPreviouslyExcludedFromAchievements() {
        seed(unlocked = true)
        Scores.init(context)
        // Reproduce the old mismatch without reinitializing Progress: the visible
        // leaderboard has a record, while Good Runner still has its earlier best.
        Scores.submit("cuberun", 2001)
        assertEquals(0, Progress.bestRunScore)
        // Opening achievements in the existing activity must see the saved best
        // immediately, even before a replacement session or application init.
        assertEquals(2001, state("runner").value)
        assertEquals(3, state("runner").earnedTiers)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            GameHostSession(Activity(), "cuberun")
        }
        assertEquals(2001, state("runner").value)
        assertEquals(3, state("runner").earnedTiers)
        assertEquals(0, state("runner").claimableTier)
    }

    @Test fun devSessionAndClaimsRemainConsistentWhenReturningToTheRealBank() {
        seed("coins" to 80, unlocked = true)
        Scores.init(context)
        Settings.setDevMode(true); Progress.enterDev()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val session = GameHostSession(Activity(), "cuberun")
            session.runStarted()
            session.setScore(499); session.addScore(2)
            session.setCoins(5000)
            assertEquals(501, state("runner").value)
            assertEquals(1, state("coins").earnedTiers)
            session.gameOver()
        }
        assertEquals(setOf("runner", "coins", "homeress"), Achievements.drainUnlocks().map { it.definition.id }.toSet())
        assertEquals(250, Achievements.claim("runner"))
        assertEquals(0, Achievements.claim("runner"))
        Settings.setDevMode(false); Progress.leaveDev(); Progress.init(context)
        assertEquals(330, Progress.coins)
        assertEquals(1, state("runner").claimedTiers)
        assertEquals(5000, Progress.totalCoins) // claim did not create a lifetime-coin feedback loop
    }

    @Test fun developerVoidPurchaseBypassesOnlyTheLifetimeRequirement() {
        seed("coins" to 80)
        Settings.setDevMode(true)
        assertTrue(Progress.voidAvailable)
        assertFalse(Progress.buyVoid()) // sufficient coins are still required
        Progress.enterDev()
        val cost = Progress.voidPrice
        val before = Progress.coins
        assertTrue(Progress.buyVoid())
        assertEquals(before - cost, Progress.coins)
        assertEquals(1, Progress.voidPurchases)
        assertEquals(0, Progress.totalCoins)
        Settings.setDevMode(false); Progress.leaveDev()
        assertFalse(Progress.voidAvailable)
        assertEquals(80, Progress.coins)
    }

    @Test fun oldEarnedTiersBecomeClaimableWithoutAnAutomaticPayout() {
        seed("coins" to 1000, "achievement_runner" to 4, unlocked = true)
        assertEquals(0, state("runner").claimedTiers)
        assertEquals(0, state("runner").claimableTier)
        assertEquals(250, state("runner").rewardAmount)
        assertFalse(state("runner").allClaimed)
        assertEquals(1000, Progress.coins)
        assertTrue(Achievements.drainUnlocks().isEmpty())
        for ((tier, reward) in listOf(250, 750, 2000, 5000).withIndex()) {
            assertEquals(tier, state("runner").claimableTier)
            assertEquals(reward, Achievements.claim("runner"))
            // Recreate the singleton state from actual SharedPreferences each time.
            Progress.init(context)
            assertEquals(tier + 1, state("runner").claimedTiers)
        }
        assertEquals(9000, Progress.coins)
        assertEquals(0, Progress.totalCoins)
        assertTrue(state("runner").allClaimed)
        assertNull(state("runner").claimableTier)
        assertNull(state("runner").rewardAmount)
        assertEquals(0, Achievements.claim("runner"))
        assertEquals(9000, prefs.getInt("coins", -1))
        assertEquals(4, prefs.getInt("achievement_claimed_runner", -1))
    }

    @Test fun claimsRequireTheGateAndAnEarnedTierAndNeverChargeCoins() {
        seed("achievement_runner" to 1)
        assertEquals(0, Achievements.claim("runner"))
        assertEquals(0, Progress.coins)
        assertFalse(prefs.contains("achievement_claimed_runner"))
        seed(unlocked = true)
        assertEquals(0, Achievements.claim("unknown"))
        assertEquals(0, Achievements.claim("powerups"))
        assertEquals(250, Achievements.claim("runner"))
        assertEquals(250, Progress.coins)
        assertEquals(0, Achievements.claim("runner"))
    }

    @Test fun claimBonusesCannotAdvanceLifetimeCoinMilestones() {
        seed("coins" to 100, "total_coins" to 4999, "achievement_bubbles" to 1,
            "achievement_bounces" to 1, unlocked = true)
        assertEquals(2000, Achievements.claim("bubbles"))
        assertEquals(1500, Achievements.claim("bounces"))
        Progress.init(context)
        assertEquals(3600, Progress.coins)
        assertEquals(4999, Progress.totalCoins)
        assertEquals(0, state("coins").earnedTiers)
        assertNull(state("coins").claimableTier)
        assertTrue(Achievements.drainUnlocks().isEmpty())
    }

    @Test fun racingClaimsPayAnEarnedTierOnlyOnce() {
        seed("achievement_runner" to 1, unlocked = true)
        val start = java.util.concurrent.CountDownLatch(1)
        val paid = java.util.concurrent.atomic.AtomicInteger()
        val workers = List(8) {
            Thread { start.await(); paid.addAndGet(Achievements.claim("runner")) }.apply { start() }
        }
        start.countDown()
        workers.forEach { it.join(5000); assertFalse("Claim thread must finish without a lock cycle", it.isAlive) }
        assertEquals(250, paid.get())
        Progress.init(context)
        assertEquals(250, Progress.coins)
        assertEquals(1, state("runner").claimedTiers)
    }

    @Test fun fullBankDoesNotConsumeTheUnpaidClaim() {
        seed("coins" to Int.MAX_VALUE, "achievement_runner" to 1, unlocked = true)
        assertEquals(0, Achievements.claim("runner"))
        Progress.init(context)
        assertEquals(Int.MAX_VALUE, Progress.coins)
        assertEquals(0, state("runner").claimedTiers)
        assertEquals(0, state("runner").claimableTier)
    }

    @Test fun voidGateUsesLifetimeCoinsAndAllThreeMilestonesUnlockPurchasesOnly() {
        seed("coins" to 1000000, "total_coins" to 99999)
        assertFalse(Progress.buyVoid()); assertEquals(1000000, Progress.coins)
        for ((count, category, id) in listOf(Triple(9, Wardrobe.CUBE, Skins.VOID_ID), Triple(19, Wardrobe.TRAIL, Trails.VOID_ID), Triple(29, Wardrobe.BUBBLE, BubbleSkins.VOID_ID))) {
            seed("coins" to Int.MAX_VALUE, "total_coins" to 100000, "void_purchases" to count)
            assertFalse(Progress.secretAvailable(category, id)); assertFalse(Progress.buy(category, id))
            val oldPrice = Progress.voidPrice
            assertTrue(Progress.buyVoid()); assertEquals(Int.MAX_VALUE - oldPrice, Progress.coins)
            assertTrue(Progress.voidPrice > oldPrice); assertEquals(0, Progress.voidPrice % 500)
            assertTrue(Progress.secretAvailable(category, id)); assertFalse(Progress.owns(category, id))
            assertTrue(Progress.buy(category, id)); Progress.init(context)
            assertTrue(Progress.owns(category, id)); assertEquals(count + 1, Progress.voidPurchases)
        }
        assertTrue(Progress.buyVoid()); assertEquals(31, Progress.voidPurchases)
    }
}
