package cube.run.game

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.bot.value
import cube.run.core.GameHostSession
import cube.run.core.Stage
import cube.run.data.*
import cube.run.game.track.Row
import cube.run.game.track.Track
import cube.run.ui.Hud
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*

/** One live game, driven frame by frame on the GL thread with hand-placed rows; the save is restored afterwards. */
internal class RunControls(val game: CubeRun, private val tickMethod: java.lang.reflect.Method, private val track: Track, private val context: Context, private val prefs: SharedPreferences) {
    private var mine: List<Row> = emptyList()
    /** Run [n] frames of 1/60 s, keeping only the rows placed with [rows]. */
    fun frames(n: Int) = repeat(n) { track.rows.retainAll(mine); tickMethod.invoke(game, 1f / 60f) }
    fun rows(list: List<Row>) { mine = list; track.rows.clear(); track.rows.addAll(list) }
    /** A new run at full speed. */
    fun restart() {
        Progress.init(context)
        (game.session as GameHostSession).resetToMenu(); game.resetToMenu()
        game.finishOpening(); Stage.paused = false; game.onTap(.5f, .5f)
        game.javaClass.getDeclaredField("runT").apply { isAccessible = true }.setFloat(game, 5f)
        value<Difficulty>(game, "difficulty").boostTo(1f)
        mine = emptyList(); track.rows.clear()
    }
    fun <T> read(name: String): T = value(game, name)
}

internal fun playingRun(prefsExtra: (SharedPreferences.Editor) -> Unit = {}, body: (RunControls) -> Unit) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
    val saved = prefs.all; val dev = Settings.devMode
    val sound = Settings.soundEnabled; val haptics = Settings.hapticsEnabled
    try {
        Settings.setDevMode(false); Settings.setSoundEnabled(false); Settings.setHapticsEnabled(false)
        prefs.edit().clear().putBoolean("achievements_unlocked", true).apply(prefsExtra).commit()
        Progress.init(context)
        ActivityScenario.launch<GameActivity>(Intent(context, GameActivity::class.java)
            .putExtra(Hud.EXTRA_AUTOSTART, false)).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val done = CountDownLatch(1); var failure: Throwable? = null
            Gdx.app.postRunnable {
                try {
                    val game = Gdx.app.applicationListener as CubeRun
                    val tick = CubeRun::class.java.getDeclaredMethod("tick", Float::class.javaPrimitiveType).apply { isAccessible = true }
                    val controls = RunControls(game, tick, value(game, "track"), context, prefs)
                    controls.restart()
                    body(controls)
                } catch (t: Throwable) { failure = t }
                finally { done.countDown() }
            }
            assertTrue(done.await(60, TimeUnit.SECONDS)); failure?.let { throw it }
        }
    } finally {
        val edit = prefs.edit().clear()
        saved.forEach { (key, v) -> when (v) {
            is Int -> edit.putInt(key, v); is Long -> edit.putLong(key, v); is Float -> edit.putFloat(key, v)
            is Boolean -> edit.putBoolean(key, v); is String -> edit.putString(key, v)
        } }
        edit.commit(); Progress.init(context)
        Settings.setDevMode(dev); Settings.setSoundEnabled(sound); Settings.setHapticsEnabled(haptics)
    }
}
