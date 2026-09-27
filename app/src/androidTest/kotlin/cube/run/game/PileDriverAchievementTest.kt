package cube.run.game

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.bot.value
import cube.run.core.GameHostSession
import cube.run.core.Stage
import cube.run.core.Gdx3DGame
import cube.run.data.*
import cube.run.game.track.Track
import cube.run.ui.Hud
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class PileDriverAchievementTest {
    @Test fun actualSlamLandingsCountOnceAndRestartClearsRunCount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val saved = prefs.all; val dev = Settings.devMode
        val sound = Settings.soundEnabled; val haptics = Settings.hapticsEnabled
        try {
            prefs.edit().putInt("metric_pile_driver", 0).remove("achievement_pile_driver")
                .putInt("skin", 0).putInt("bubble_skin", 0).commit()
            Progress.init(context); Settings.setDevMode(false)
            Settings.setSoundEnabled(false); Settings.setHapticsEnabled(false)
            ActivityScenario.launch<GameActivity>(Intent(context, GameActivity::class.java)
                .putExtra(Hud.EXTRA_AUTOSTART, false)).use { scenario ->
                scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
                val done = CountDownLatch(1); var failure: Throwable? = null
                Gdx.app.postRunnable {
                    try {
                        val game = Gdx.app.applicationListener as CubeRun
                        game.finishOpening(); Stage.paused = false; game.onTap(.5f, .5f)
                        val player: Player = value(game, "player")
                        val track: Track = value(game, "track")
                        val tick = CubeRun::class.java.getDeclaredMethod("tick", Float::class.javaPrimitiveType).apply { isAccessible = true }
                        fun frame(dt: Float) { track.rows.clear(); tick.invoke(game, dt) }
                        fun slam() {
                            game.onSwipe(Gdx3DGame.UP); frame(.05f)
                            repeat(5) { game.onSwipe(Gdx3DGame.DOWN) }
                            assertTrue(player.air)
                            frame(.03f); assertFalse(player.air)
                        }
                        repeat(10) { game.onSwipe(Gdx3DGame.DOWN); frame(.03f) }
                        assertEquals("Grounded rolls do not count", 0, Progress.metric("pile_driver"))
                        game.onSwipe(Gdx3DGame.UP); repeat(40) { frame(.02f) }
                        assertFalse(player.air)
                        assertEquals("Ordinary landings do not count", 0, Progress.metric("pile_driver"))
                        repeat(150) { slam() }
                        assertEquals("Repeated DOWN counts one completed slam", 150, Progress.metric("pile_driver"))
                        (game.session as GameHostSession).resetToMenu(); game.resetToMenu(); game.onTap(.5f, .5f)
                        repeat(150) { slam() }
                        assertEquals("Restart does not combine runs", 150, Progress.metric("pile_driver"))
                        repeat(149) { slam() }
                        val definition = Achievements.all.single { it.id == "pile_driver" }
                        assertEquals(299, Progress.metric("pile_driver")); assertEquals(0, Achievements.snapshot(definition).earnedTiers)
                        slam(); assertEquals(1, Achievements.snapshot(definition).earnedTiers)
                        assertEquals(300, Progress.metric("pile_driver"))
                    } catch (t: Throwable) { failure = t }
                    finally { done.countDown() }
                }
                assertTrue(done.await(35, TimeUnit.SECONDS)); failure?.let { throw it }
            }
        } finally {
            val edit = prefs.edit().clear()
            saved.forEach { (key, v) -> when (v) {
                is Int -> edit.putInt(key, v)
                is Long -> edit.putLong(key, v)
                is Float -> edit.putFloat(key, v)
                is Boolean -> edit.putBoolean(key, v)
                is String -> edit.putString(key, v)
                is Set<*> -> edit.putStringSet(key, v.filterIsInstance<String>().toSet())
            } }
            edit.commit(); Progress.init(context); Settings.setDevMode(dev)
            Settings.setSoundEnabled(sound); Settings.setHapticsEnabled(haptics)
        }
    }
}
