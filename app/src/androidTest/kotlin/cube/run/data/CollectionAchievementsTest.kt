package cube.run.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Trailblazer and Bubble Bath (#68): every tier is reachable by buying cosmetics and is paid once. */
class CollectionAchievementsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var prefs: SharedPreferences
    private lateinit var saved: Map<String, *>

    @Before fun setUp() {
        prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE); saved = prefs.all
        prefs.edit().clear().putBoolean("achievements_unlocked", true).commit()
        Settings.init(context); Settings.setDevMode(false); Progress.init(context)
    }

    @After fun tearDown() {
        val edit = prefs.edit().clear()
        for ((key, value) in saved) when (value) {
            is Int -> edit.putInt(key, value); is Boolean -> edit.putBoolean(key, value)
            is Long -> edit.putLong(key, value); is Float -> edit.putFloat(key, value); is String -> edit.putString(key, value)
        }
        edit.commit(); Progress.init(context)
    }

    private fun definition(id: String) = Achievements.all.single { it.id == id }
    private fun snapshot(id: String) = Achievements.snapshot(definition(id))
    private fun claimAll(id: String): Int { var total = 0; while (true) { val paid = Achievements.claim(id); if (paid == 0) return total; total += paid } }
    private fun grant(cat: Int, id: Int) =
        Progress::class.java.getDeclaredMethod("grant", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }.invoke(Progress, cat, id)
    private fun sold(cat: Int) = (0 until Wardrobe.count(cat)).filter { !Wardrobe.isSecret(cat, it) }
    private val families = mapOf("trailblazer" to Wardrobe.TRAIL, "bubble_bath" to Wardrobe.BUBBLE)

    @Test fun theFreeCosmeticCountsAndDiamondFitsWhatTheWardrobeSells() {
        for ((id, cat) in families) {
            assertEquals("$id starts with the free one", 1, snapshot(id).value)
            assertTrue("$id diamond fits the ${sold(cat).size} sold", definition(id).thresholds.last() <= sold(cat).size)
        }
    }

    @Test fun everyTierCanBeEarnedByBuyingAndIsClaimedOnce() {
        for ((id, cat) in families) {
            val d = definition(id)
            assertEquals(4, d.thresholds.size)
            var owned = 1
            for ((tier, goal) in d.thresholds.withIndex()) {
                while (owned < goal - 1) grant(cat, sold(cat)[owned++])
                assertEquals("$id below tier $tier", tier, snapshot(id).earnedTiers)
                grant(cat, sold(cat)[owned++])
                assertEquals("$id at tier $tier", tier + 1, snapshot(id).earnedTiers)
                assertEquals(tier, Achievements.drainUnlocks().single { it.definition.id == id }.tier)
            }
            assertEquals(id, d.thresholds.indices.sumOf { Achievements.reward(d, it) }, claimAll(id))
            assertEquals(0, Achievements.claim(id))
            assertTrue(id, snapshot(id).allClaimed)
        }
    }
}
