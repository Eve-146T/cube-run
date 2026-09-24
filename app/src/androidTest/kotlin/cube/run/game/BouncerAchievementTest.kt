package cube.run.game

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.bot.Action
import cube.run.bot.AndroidGestures
import cube.run.core.Stage
import cube.run.data.Achievements
import cube.run.data.Progress
import cube.run.data.Settings
import cube.run.game.track.Track
import cube.run.ui.Hud
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Android gestures, including queued touches during a delayed GL frame. */
class BouncerAchievementTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun gl(action: (CubeRun) -> Unit = {}) {
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
    private fun awaitUi(scenario: ActivityScenario<GameActivity>, label: String, predicate: (Hud) -> Boolean) {
        val until = SystemClock.uptimeMillis() + 10000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < until) {
            scenario.onActivity {
                val hud = field(it, "hud").get(it) as? Hud
                ready = hud != null && hud.isAttachedToWindow && predicate(hud)
            }
            if (!ready) SystemClock.sleep(30)
        }
        assertTrue(label, ready)
    }
    private fun inject(kind: Int, downAt: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), kind, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        try { assertTrue("Android accepts touch event", instrumentation.uiAutomation.injectInputEvent(event, true)) }
        finally { event.recycle() }
    }
    private fun start(scenario: ActivityScenario<GameActivity>) {
        scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
        awaitUi(scenario, "Main menu ready") { !field(it, "runStarted").getBoolean(it) && it.alpha == 1f }
        val downAt = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, downAt, Gdx.graphics.width * .5f, Gdx.graphics.height * .58f)
        inject(MotionEvent.ACTION_UP, downAt, Gdx.graphics.width * .5f, Gdx.graphics.height * .58f)
        awaitUi(scenario, "A real Android tap starts the run") { field(it, "runStarted").getBoolean(it) }
    }
    private fun flick() {
        assertTrue("Android accepts distinct outward flick", AndroidGestures.flick(Action.LEFT, Gdx.graphics.width, Gdx.graphics.height))
        // Android dispatch ends before libGDX drains its input queue. Runnables
        // execute before that drain, so cross two frame boundaries before reading.
        gl(); gl()
    }
    private fun count(expected: Int) = gl {
        assertEquals("Every real wall bonk is counted once", expected, field(it, "sideBounces").getInt(it))
    }
    private fun restore(prefs: SharedPreferences, saved: Map<String, *>) {
        val edit = prefs.edit().clear()
        for ((key, value) in saved) when (value) {
            is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is String -> edit.putString(key, value)
            is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
        edit.commit()
    }

    @Test fun realAndroidEdgeFlicksCountWithoutLosingBatchedGestures() {
        exerciseEdgeFlicks(devMode = false)
    }

    @Test fun developerEdgeFlicksEarnAndKeepTheSamePersonalBest() {
        exerciseEdgeFlicks(devMode = true)
    }

    private fun exerciseEdgeFlicks(devMode: Boolean) {
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val scores = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
        val saved = prefs.all
        val savedScores = scores.all
        val savedDevMode = Settings.devMode
        val pumpRunning = AtomicBoolean(false)
        // Keep both input exercises repeatable without changing the game clock
        // or calling the game's swipe handler directly.
        val clearRoad = object : Runnable {
            override fun run() {
                if (!pumpRunning.get()) return
                val game = Gdx.app.applicationListener as CubeRun
                (field(game, "track").get(game) as Track).rows.clear()
                Gdx.app.postRunnable(this)
            }
        }
        try {
            prefs.edit().clear().putBoolean("achievements_unlocked", true).commit()
            scores.edit().clear().commit()
            Settings.setDevMode(devMode); Progress.init(context)
            val intent = Intent(context, GameActivity::class.java).putExtra(Hud.EXTRA_AUTOSTART, false)
            ActivityScenario.launch<GameActivity>(intent).use { scenario ->
                pumpRunning.set(true)
                Gdx.app.postRunnable(clearRoad)
                start(scenario)
                flick() // Centre -> outer lane is a lane change, not a wall contact.
                count(0)

                val now = SystemClock.uptimeMillis()
                val x = Gdx.graphics.width * .5f; val y = Gdx.graphics.height * .58f
                inject(MotionEvent.ACTION_DOWN, now, x, y)
                repeat(12) { inject(MotionEvent.ACTION_MOVE, now, x - Gdx.graphics.width * (.13f + it * .002f), y) }
                inject(MotionEvent.ACTION_UP, now, x - Gdx.graphics.width * .16f, y)
                gl(); gl(); count(1) // Holding/moving one gesture never adds more hits.

                // Reproduce Android accumulating several distinct touches while
                // rendering is delayed. The old simulation-time debounce lost
                // all but one, although every DOWN/UP pair reached TouchInput.
                // Five gestures per held frame keep each pause short even on
                // slower phones; all 65 touches still exercise real queueing.
                repeat(13) { batch ->
                    val frameHeld = CountDownLatch(1)
                    val resumeFrame = CountDownLatch(1)
                    val timedOut = AtomicBoolean(false)
                    Gdx.app.postRunnable {
                        frameHeld.countDown()
                        timedOut.set(!resumeFrame.await(5, TimeUnit.SECONDS))
                    }
                    assertTrue("Rendering frame held for input batch", frameHeld.await(5, TimeUnit.SECONDS))
                    try {
                        repeat(5) {
                            assertTrue(AndroidGestures.flick(Action.LEFT, Gdx.graphics.width, Gdx.graphics.height))
                        }
                    } finally { resumeFrame.countDown() }
                    gl(); gl()
                    assertFalse("Input batch finishes before its frame-hold timeout", timedOut.get())
                    count(1 + (batch + 1) * 5)
                }
                assertEquals(66, Progress.maxRunBounces)
                assertEquals(0, Achievements.snapshot().single { it.definition.id == "bounces" }.earnedTiers)

                gl { Stage.paused = true }
                flick(); count(66)
                gl { Stage.paused = false }
                flick(); count(67)
                assertEquals(67, Progress.maxRunBounces)
                assertEquals(1, prefs.getInt("achievement_bounces", 0))
                assertEquals(1, Achievements.snapshot().single { it.definition.id == "bounces" }.earnedTiers)

                pumpRunning.set(false)
                gl { Stage.paused = true }
            }
            // Finishing and launching a fresh activity mirrors the game's
            // restart lifecycle and lets libGDX dispose its native resources.
            // Only the run counter resets; the earned record remains persisted.
            ActivityScenario.launch<GameActivity>(intent).use { scenario ->
                pumpRunning.set(true)
                Gdx.app.postRunnable(clearRoad)
                start(scenario)
                flick(); count(0)
                flick(); count(1)
                assertEquals(67, Progress.maxRunBounces)
                Progress.init(context)
                assertEquals(1, Achievements.snapshot().single { it.definition.id == "bounces" }.earnedTiers)
                pumpRunning.set(false)
            }
        } finally {
            pumpRunning.set(false)
            Stage.paused = false; Stage.mode = Stage.NONE
            restore(prefs, saved); restore(scores, savedScores)
            Settings.setDevMode(savedDevMode); Progress.init(context)
        }
    }
}
