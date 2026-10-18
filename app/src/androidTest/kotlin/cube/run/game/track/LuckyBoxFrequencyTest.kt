package cube.run.game.track

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.data.Progress
import cube.run.data.Settings
import cube.run.game.Lanes
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import kotlin.random.Random

/** Check generated roads, including the pickups a purchased perk must leave alone. */
class LuckyBoxFrequencyTest {
    private class FixtureContext(base: Context) : ContextWrapper(base) {
        private val prefix = "lucky_boxes_${UUID.randomUUID()}_"
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences(prefix + name, mode)
        fun dispose() { for (name in listOf("progress", "scores", "settings")) baseContext.deleteSharedPreferences(prefix + name) }
    }

    private fun fixture(test: (Context) -> Unit) {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        val context = FixtureContext(real)
        val section = Settings.testSection
        val dev = Settings.devMode
        val performance = Settings.performanceCourse
        val courseField = Settings.javaClass.getDeclaredField("performanceCourseSelected").apply { isAccessible = true }
        try {
            courseField.setBoolean(Settings, false)
            Settings.init(context); Settings.setDevMode(false); Settings.testSection = -1
            Lanes.reset()
            test(context)
        } finally {
            Settings.init(real); Settings.setDevMode(dev); Settings.testSection = section
            courseField.setBoolean(Settings, performance)
            Progress.init(real); Lanes.reset(); context.dispose()
        }
    }

    private fun level(context: Context, value: Int) {
        context.getSharedPreferences("progress", Context.MODE_PRIVATE).edit().clear().putInt(Progress.LUCKYBOX.key, value).commit()
        Progress.init(context)
    }

    private fun track(): Track {
        val random = Random(1800)
        return Track(random, ObstacleFactory(random)).apply { reset(.2f, 205f) }
    }

    private fun generate(track: Track, rows: Int, score: Int = 100) {
        while (track.rowsSpawned < rows) track.spawn(1f, 205f, score)
    }

    private fun otherPickups(track: Track) = track.rows.mapIndexedNotNull { index, row ->
        if (row.pickup == Pickup.NONE || row.pickup == Pickup.BOX) null else Triple(index, row.pickup, row.pickupX)
    }

    @Test fun everyLevelMatchesItsMultiplierWithoutReducingOtherPickups() = fixture { context ->
        level(context, 0)
        val baseline = track().also { generate(it, 20000) }
        val boxes = baseline.rows.count { it.pickup == Pickup.BOX }
        assertTrue("A long sample includes plenty of boxes", boxes > 50)
        val others = otherPickups(baseline)
        for (lvl in 1..Progress.LUCKYBOX.max) {
            level(context, lvl)
            val upgraded = track().also { generate(it, 20000) }
            assertEquals("Other pickups retain their road positions at level $lvl", others, otherPickups(upgraded))
            assertEquals("Box frequency matches the shop at level $lvl",
                boxes * Progress.LUCKYBOX.duration(lvl), upgraded.rows.count { it.pickup == Pickup.BOX }.toFloat(), 1f)
        }
    }

    @Test fun scoreGateAndNewRunResetStillApplyWithPurchasedLuck() = fixture { context ->
        level(context, 3)
        val reused = track().also { generate(it, 2000, score = 99) }
        assertTrue("Luck cannot bypass the score gate", reused.rows.none { it.pickup == Pickup.BOX })
        val offers = Track::class.java.getDeclaredField("boxOffers").apply { isAccessible = true }
        while (offers.getInt(reused) == 0) reused.spawn(1f, 205f, 100)
        assertTrue("The first skipped slot only banks partial bonus progress", reused.rows.none { it.pickup == Pickup.BOX })
        reused.reset(.2f, 205f)
        while (offers.getInt(reused) == 0) reused.spawn(1f, 205f, 100)
        assertTrue("A new run cannot inherit enough partial progress to pay out its first skipped slot",
            reused.rows.none { it.pickup == Pickup.BOX })
    }
}
