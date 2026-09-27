package cube.run.game

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.Gdx3DGame
import cube.run.data.Bonus
import cube.run.data.Progress
import cube.run.data.Settings
import cube.run.game.track.ObType
import cube.run.game.track.ObstacleFactory
import cube.run.game.track.Pickup
import cube.run.game.track.Track
import cube.run.ui.Hud
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.random.Random

/** Use the ordinary track generator to check that rare goals have a playable source. */
class AchievementObtainabilityTest {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun gl(action: (CubeRun) -> Unit) {
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        Gdx.app.postRunnable {
            try { action(Gdx.app.applicationListener as CubeRun) }
            catch (t: Throwable) { failure = t }
            finally { done.countDown() }
        }
        assertTrue("GL callback completed", done.await(15, TimeUnit.SECONDS))
        failure?.let { throw it }
    }
    private fun restore(store: SharedPreferences, saved: Map<String, *>) {
        val edit = store.edit().clear()
        for ((key, value) in saved) when (value) {
            is Int -> edit.putInt(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is String -> edit.putString(key, value)
        }
        edit.commit()
    }

    @Test fun ordinaryOutsideLaneCrashEarnsStageFrightAfterGate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val saved = prefs.all
        val scores = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
        val savedScores = scores.all
        try {
            prefs.edit().clear().commit(); scores.edit().clear().commit()
            Settings.init(context); Settings.setDevMode(false); Progress.init(context)
            ActivityScenario.launch<GameActivity>(Intent(context, GameActivity::class.java)
                .putExtra(Hud.EXTRA_AUTOSTART, true)).use {
                val deadline = SystemClock.uptimeMillis() + 15_000L
                var swiped = false
                while (!swiped && SystemClock.uptimeMillis() < deadline) {
                    if (Gdx.app != null) gl { game ->
                        if (field(game, "startGateRunT").getFloat(game) >= 0f) {
                            game.onSwipe(Gdx3DGame.LEFT)
                            swiped = true
                        }
                    }
                    if (!swiped) SystemClock.sleep(20)
                }
                assertTrue("Start gate crossed", swiped)
                while (Progress.metric("stage_fright") == 0 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20)
                assertEquals("First outside-lane hazard is reachable in two seconds", 1, Progress.metric("stage_fright"))
            }
        } finally {
            restore(prefs, saved); restore(scores, savedScores); Progress.init(context)
        }
    }

    @Test fun generatedRoadOffersAllRequiredPickupsAndWorlds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Settings.init(context)
        Settings.setDevMode(false)
        Settings.testSection = -1
        Lanes.reset()
        Progress.init(context)
        val random = Random(146)
        val track = Track(random, ObstacleFactory(random)).apply {
            portalPool = Bonus.all.map { it.id }
            portalEvery = 110
            reset(0.2f, 320f)
        }
        // The gate is 30 m ahead and the first solid row 38 m ahead.
        // Its outside-lane pillar can be hit inside Stage Fright's two-second gate window.
        val first = track.rows.first()
        assertEquals(-38f, first.z, 0.01f)
        assertTrue(first.obs.any { it.type == ObType.SOLID && abs(it.x - Lanes.x(0)) < 0.1f })

        var distance = 0f
        var boxes = 0
        var shardMask = 0
        var bonusMask = 0
        var safeCoins = 0
        var lastMagnet = -1f
        var lastMult = -1f
        var fullKitSequence = false
        repeat(15_000) {
            val before = track.rows.size
            track.spawn(6.5f, 320f, 1000)
            for (row in track.rows.drop(before)) {
                when (row.pickup) {
                    Pickup.BOX -> boxes++
                    Pickup.MAGNET -> lastMagnet = distance
                    Pickup.MULT -> lastMult = distance
                    Pickup.JET -> if (lastMagnet >= 0f && lastMult >= 0f &&
                        distance - lastMagnet < 22.5f * 20f && distance - lastMult < 27.5f * 20f)
                        fullKitSequence = true
                    in Pickup.SHARD_EMBER..Pickup.SHARD_VOID -> shardMask = shardMask or (1 shl Pickup.shardType(row.pickup))
                }
                if (row.portal in 0..3 && !row.portalExit) bonusMask = bonusMask or (1 shl row.portal)
                safeCoins += row.coins?.count { abs(it.restX - row.safeX()) < 0.5f } ?: 0
            }
            track.scroll(6.5f, distance / 20f, 0.325f)
            distance += 6.5f
        }
        assertTrue("13 boxes are offered on one ordinary endless road", boxes >= 13)
        assertEquals("All shard types are offered in one run", 0b111, shardMask)
        assertEquals("All four bonus worlds are offered in one run", 0b1111, bonusMask)
        assertTrue("Magnet, multiplier and jetpack can overlap at max upgrades", fullKitSequence)
        assertTrue("The safe path supplies 5,000 coal pickups", safeCoins >= 5_000)
        assertTrue("Rich Coins and Gold can yield 10,000 coins on one road", safeCoins * 3.6f >= 10_000f)
    }
}
