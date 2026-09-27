package cube.run.game

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import cube.run.GameActivity
import cube.run.bot.value
import cube.run.core.GameHostSession
import cube.run.core.Stage
import cube.run.data.*
import cube.run.game.track.*
import cube.run.ui.Hud
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class GreedAchievementTest {
    @Test fun fatalBoxAttemptsUnlockButSafePickupsAndSavesDoNot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val saved = prefs.all
        val dev = Settings.devMode
        try {
            Settings.setDevMode(false)
            ActivityScenario.launch<GameActivity>(Intent(context, GameActivity::class.java)
                .putExtra(Hud.EXTRA_AUTOSTART, false)).use { scenario ->
                scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
                val done = CountDownLatch(1); var failure: Throwable? = null
                Gdx.app.postRunnable {
                    try {
                        val game = Gdx.app.applicationListener as CubeRun
                        val track: Track = value(game, "track")
                        val player: Player = value(game, "player")
                        val bubble: Bubble = value(game, "bubble")
                        val collide = CubeRun::class.java.getDeclaredMethod("collide", Float::class.javaPrimitiveType).apply { isAccessible = true }
                        val collect = CubeRun::class.java.getDeclaredMethod("collectPickup", Row::class.java, Float::class.javaPrimitiveType).apply { isAccessible = true }
                        fun reset(revives: Int = 0) {
                            prefs.edit().putInt("skin", 0).putInt("revives", revives)
                                .putInt("metric_greed", 0).remove("achievement_greed").remove("achievement_claimed_greed")
                                .putBoolean("achievements_unlocked", true).commit()
                            Progress.init(context)
                            (game.session as GameHostSession).resetToMenu(); game.resetToMenu()
                            Stage.paused = false; game.onTap(.5f, .5f)
                            track.rows.clear(); player.setFlying(false); player.forceGround(0f); bubble.reset()
                        }
                        fun wall() = Row(0f, arrayListOf(Ob(Color.RED, 0f, 1f, .8f, ObType.SOLID, 1.6f, 2f, 1f)))
                        fun check(expected: Int, dies: Boolean = true) {
                            collide.invoke(game, .016f)
                            assertEquals(expected, Progress.metric("greed"))
                            assertEquals(dies, value<Boolean>(game, "dead"))
                        }
                        reset(); track.rows.add(wall().apply { pickup = Pickup.BOX }); check(1)
                        val greed = Achievements.all.single { it.id == "greed" }
                        assertEquals(1, Achievements.snapshot(greed).earnedTiers)
                        Progress.init(context); assertEquals(1, Achievements.snapshot(greed).earnedTiers)
                        assertEquals(1500, Achievements.claim("greed")); assertEquals(0, Achievements.claim("greed"))
                        reset(); track.rows.add(wall().apply { pickup = Pickup.BOX; pickupX = 3.2f }); check(0)
                        reset(); track.rows.add(wall().apply { pickup = Pickup.BOX; pickupMissed = true }); check(0)
                        reset(); track.rows.add(wall()); check(0)
                        reset()
                        val grabbed = Row(3.2f, arrayListOf()).apply { pickup = Pickup.BOX }
                        track.rows.add(grabbed); collect.invoke(game, grabbed, 0f)
                        assertEquals("Safe collection alone is not Greed", 0, Progress.metric("greed"))
                        track.rows.add(wall()); check(1)
                        reset()
                        val oldBox = Row(20f, arrayListOf()).apply { pickup = Pickup.BOX }
                        collect.invoke(game, oldBox, 0f); track.rows.add(wall()); check(0)
                        reset(); track.rows.add(wall().apply { pickup = Pickup.BOX })
                        bubble.activate(0f, .45f, quiet = true); check(0, false)
                        reset(1); track.rows.add(wall().apply { pickup = Pickup.BOX }); check(0, false)
                        reset(); track.rows.add(wall()); check(0)
                    } catch (t: Throwable) { failure = t }
                    finally { done.countDown() }
                }
                assertTrue(done.await(30, TimeUnit.SECONDS)); failure?.let { throw it }
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
        }
    }
}
