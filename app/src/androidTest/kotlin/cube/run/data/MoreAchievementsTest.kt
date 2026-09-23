package cube.run.data

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.core.GameHostSession
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** The new cards are driven by real persisted events and per-run host state. */
class MoreAchievementsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var prefs: SharedPreferences
    private lateinit var saved: Map<String, *>
    private lateinit var scorePrefs: SharedPreferences
    private lateinit var savedScores: Map<String, *>

    @Before fun setUp() {
        prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        saved = prefs.all
        scorePrefs = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
        savedScores = scorePrefs.all
        prefs.edit().clear().putBoolean("achievements_unlocked", true).commit()
        scorePrefs.edit().clear().commit()
        Settings.init(context)
        Progress.init(context)
        Scores.init(context)
    }

    @After fun tearDown() {
        fun restore(store: SharedPreferences, values: Map<String, *>) {
            val edit = store.edit().clear()
            for ((key, value) in values) when (value) {
                is Int -> edit.putInt(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is String -> edit.putString(key, value)
            }
            edit.commit()
        }
        restore(prefs, saved); restore(scorePrefs, savedScores)
        Progress.init(context)
    }

    private fun value(id: String) = Achievements.snapshot().single { it.definition.id == id }.value
    private fun session() = GameHostSession(Activity(), "cuberun").also { it.runStarted() }

    @Test fun allSelectedAchievementsHaveTargetsAndPayouts() {
        val all = Achievements.all
        assertEquals(35, all.size)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertFalse(all.any { it.id == "portal_hopper" })
        for (definition in all) {
            assertTrue(definition.title, definition.thresholds.all { it > 0 })
            assertTrue(definition.title, definition.thresholds.toList().zipWithNext().all { (a, b) -> a < b })
            for (tier in definition.thresholds.indices) assertTrue(definition.title, Achievements.reward(definition, tier) > 0)
        }
    }

    @Test fun runCountersUseBoundariesAndPersist() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
        val s = session()
        assertEquals(1, value("regular"))
        s.nearMiss(); s.boxCollected(); s.coalCollected(); s.fullKitHeld()
        s.runSeconds(600); s.distanceCovered(125); s.distanceCovered(249)
        s.setBonus(Bonus.WIDE); s.setBonus(Bonus.HILLS); s.setBonus(Bonus.FLOAT); s.setBonus(Bonus.KALEIDO)
        s.addShard(0); s.addShard(1); s.addShard(2)
        s.setCoins(10_000)
        s.jackpotWon(5_000)
        s.runCrashed(2f)
        assertEquals(0, value("stage_fright"))
        s.gameOver()
        assertEquals(249, value("long_hauler"))
        assertEquals(1, value("near_miss"))
        assertEquals(1, value("greedy"))
        assertEquals(1, value("coal_miner"))
        assertEquals(1, value("full_kit"))
        assertEquals(600, value("long_con"))
        assertEquals(4, value("globetrotter"))
        assertEquals(4, value("scenic_route"))
        assertEquals(3, value("shard_hunter"))
        assertEquals(10_000, value("magpie"))
        assertEquals(1, value("house_loses"))
        assertEquals(3, value("shardsmith"))
        Progress.init(context)
        assertEquals(249, value("long_hauler"))
        assertEquals(4, value("globetrotter"))
        }
    }

    @Test fun qualifyingRunsAndShopVisitsDoNotLeakAcrossRuns() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
        assertEquals(0, value("bankrupt"))
        val first = session()
        first.setScore(149); first.powerupPickedUp(); first.setScore(150)
        assertEquals(149, value("untouchable"))
        first.setBonus(Bonus.WIDE); first.setBonus(Bonus.HILLS)
        first.runCrashed(1.9f)
        assertEquals(1, value("stage_fright"))
        val second = session()
        second.setBonus(Bonus.FLOAT); second.setBonus(Bonus.KALEIDO)
        assertEquals(2, value("scenic_route"))
        second.setScore(150)
        assertEquals(150, value("untouchable"))
        Progress.shopOpened(); Progress.shopClosed()
        assertEquals(1, value("just_browsing"))
        Progress.addCoins(120)
        Progress.shopOpened(); assertTrue(Progress.buyBubble()); Progress.shopClosed()
        assertEquals(0, value("just_browsing"))
        assertEquals(0, value("bubble_popper")) // Buying does not spend a bubble.
        assertEquals(1, value("bankrupt"))
        }
    }

    @Test fun pointlessSwipesAndSilentRunRequireTheExactActions() {
        val sound = Settings.soundEnabled
        val haptics = Settings.hapticsEnabled
        try {
            Settings.setSoundEnabled(false); Settings.setHapticsEnabled(false)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val first = session()
                first.laneChanged(0, 3) // A portal remap or generic lane event is not a swipe.
                assertEquals(0, value("two_ez"))
                first.userLaneSwipe(1, 0); first.userLaneSwipe(0, 1)
                assertEquals(1, value("two_ez"))
                first.setScore(100)
                Settings.setSoundEnabled(true); Settings.setSoundEnabled(false)
                first.gameOver()
                assertEquals(0, value("silent_treatment"))
                val second = session()
                second.setScore(100); second.gameOver()
                assertEquals(1, value("silent_treatment"))
            }
        } finally {
            Settings.setSoundEnabled(sound); Settings.setHapticsEnabled(haptics)
        }
    }
}
