package cube.run.game

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Scores
import cube.run.data.Settings
import cube.run.data.Skins
import cube.run.game.track.Ob
import cube.run.game.track.ObType
import cube.run.game.track.Pickup
import cube.run.game.track.Row
import cube.run.game.track.Track
import cube.run.ui.Hud
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The Zen cube through the real GL game: obstacles melt, bubbles still come first, nothing is recorded. */
class ZenCubeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private inline fun <reified T> read(owner: Any, name: String): T = field(owner, name).get(owner) as T
    private fun call(game: CubeRun, name: String, vararg args: Any) {
        game.javaClass.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(game, *args)
    }
    private fun gl(action: (CubeRun) -> Unit) {
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        Gdx.app.postRunnable {
            try { action(Gdx.app.applicationListener as CubeRun) } catch (t: Throwable) { failure = t } finally { done.countDown() }
        }
        assertTrue("GL task completes", done.await(15, TimeUnit.SECONDS))
        failure?.let { throw it }
    }
    private fun restore(prefs: SharedPreferences, saved: Map<String, *>) {
        val edit = prefs.edit().clear()
        saved.forEach { (key, value) -> when (value) {
            is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is String -> edit.putString(key, value)
            is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
        } }
        assertTrue(edit.commit())
    }
    private fun fixture(skin: Int, action: (CubeRun) -> Unit) {
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val scores = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
        val saved = prefs.all; val savedScores = scores.all; val savedDev = Settings.devMode
        try {
            Settings.setDevMode(false)
            assertTrue(prefs.edit().clear().putBoolean("achievements_unlocked", true)
                .putInt("owned_skins", 1 or (1 shl Skins.ZEN_ID)).putInt("skin", skin).putInt("bubbles", 5).commit())
            scores.edit().clear().commit(); Progress.init(context)
            val intent = Intent(context, GameActivity::class.java).putExtra(Hud.EXTRA_AUTOSTART, false)
            ActivityScenario.launch<GameActivity>(intent).use { scenario ->
                var ready = false
                val until = SystemClock.uptimeMillis() + 10000
                while (!ready && SystemClock.uptimeMillis() < until) {
                    scenario.onActivity { ready = (field(it, "hud").get(it) as? Hud)?.let { h -> h.isAttachedToWindow && h.alpha == 1f } == true }
                    if (!ready) SystemClock.sleep(30)
                }
                assertTrue("Game menu ready", ready)
                gl { game ->
                    Stage.paused = true
                    call(game, "start")
                    read<Track>(game, "track").rows.clear()
                    action(game)
                }
            }
        } finally {
            Stage.paused = false; Stage.mode = Stage.NONE
            restore(prefs, saved); restore(scores, savedScores)
            Settings.setDevMode(savedDev)
            Progress.init(context)
        }
    }
    private fun wall() = Row(0f, arrayListOf(Ob(Color.RED, 0f, 1f, .8f, ObType.SOLID, 1.6f, 2f, 1f)))

    @Test fun zenMeltsTheObstacleInsteadOfCrashing() = fixture(Skins.ZEN_ID) { game ->
        val row = wall()
        call(game, "hit", row)
        assertFalse("Zen cannot crash", read<Boolean>(game, "dead"))
        assertTrue("The wall melted", row.obs.none { it.type == ObType.SOLID })
        assertEquals("No bubble was spent", 5, Progress.bubbles)
    }

    @Test fun anActiveBubbleTakesTheHitBeforeZen() = fixture(Skins.ZEN_ID) { game ->
        val bubble = read<Bubble>(game, "bubble")
        call(game, "tryBubble")
        assertTrue("A Zen bubble goes up", bubble.active)
        assertEquals("Zen bubbles are free", 5, Progress.bubbles)
        call(game, "hit", wall())
        assertFalse("The bubble took the hit and popped", bubble.active)
        assertFalse(read<Boolean>(game, "dead"))
    }

    @Test fun zenRecordsNoAchievementsScoresOrRewards() = fixture(Skins.ZEN_ID) { game ->
        assertTrue(Progress.zenRun)
        assertEquals("A Zen run is not counted as a run", 0, Progress.metric("regular"))
        game.session.addScore(700)
        Progress.addMetric("near_miss")
        val row = Row(0f, arrayListOf()).apply { pickup = Pickup.BUBBLE }
        call(game, "collectPickup", row, 0f)
        assertEquals("Bubble pickups stock nothing", 5, Progress.bubbles)
        assertEquals(0, Progress.totalPowerups)
        assertEquals(0, Progress.bestRunScore)
        assertEquals(0, Progress.metric("near_miss"))
        call(game, "die"); repeat(120) { game.tick(1f / 60f) } // the developer's END RUN path
        assertEquals("Nothing is banked or submitted", 0, Scores.best("cuberun"))
    }

    @Test fun otherCubesStillCrashAndCount() = fixture(0) { game ->
        assertFalse(Progress.zenRun)
        assertEquals(1, Progress.metric("regular"))
        call(game, "hit", wall())
        assertTrue(read<Boolean>(game, "dead"))
    }
}
