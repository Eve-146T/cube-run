package cube.run.game

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Settings
import cube.run.data.Wardrobe
import cube.run.game.track.Coin
import cube.run.game.track.Row
import cube.run.game.track.Track
import cube.run.ui.Hud
import cube.run.ui.WardrobeView
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/** Opt-in on-device screenshots: -e captureHardwareAchievements true. */
class AbilityVisualReviewTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun field(owner: Any, name: String): java.lang.reflect.Field {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true } }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        error("Missing field $name")
    }
    private fun call(owner: Any, name: String, vararg args: Any) = owner.javaClass.declaredMethods
        .single { it.name == name && it.parameterCount == args.size }.apply { isAccessible = true }.invoke(owner, *args)
    private fun gl(action: (CubeRun) -> Unit = {}) {
        val latch = CountDownLatch(1); var failure: Throwable? = null
        Gdx.app.postRunnable {
            try { action(Gdx.app.applicationListener as CubeRun) }
            catch (t: Throwable) { failure = t }
            finally { latch.countDown() }
        }
        assertTrue(latch.await(15, TimeUnit.SECONDS)); failure?.let { throw it }
    }
    private fun hud(activity: GameActivity) = field(activity, "hud").get(activity) as Hud
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    private fun capture(name: String, settle: Long = 180) {
        if (settle > 0) SystemClock.sleep(settle)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        val folder = File(context.getExternalFilesDir(null), "achievements-hardware").apply { mkdirs() }
        File(folder, "abilities-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun restore(prefs: SharedPreferences, saved: Map<String, *>) {
        val edit = prefs.edit().clear()
        saved.forEach { (key, value) -> when (value) {
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is String -> edit.putString(key, value)
            is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
        } }
        assertTrue(edit.commit())
    }
    private fun panel(scenario: ActivityScenario<GameActivity>, category: Int, id: Int, ability: String, name: String) {
        scenario.onActivity {
            val page = field(hud(it), "page").get(hud(it)) as WardrobeView
            field(page, "cat").setInt(page, category); field(page, "index").setInt(page, id)
            call(page, "applyPreview"); call(page, "render")
        }
        SystemClock.sleep(500)
        scenario.onActivity {
            val page = field(hud(it), "page").get(hud(it)) as View
            assertTrue(descendants(page).single { v -> v.contentDescription == "Show $ability ability" }.performClick())
        }
        capture("panel-$name", 400)
    }

    @Test fun captureEquippedAbilitiesOnTheActualRenderer() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureHardwareAchievements") == "true")
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val scores = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
        val saved = prefs.all; val savedScores = scores.all; val dev = Settings.devMode
        val bonus = Settings.testBonus; val bonusNow = Settings.testBonusNow
        val boxes = Settings.testBoxes; val pill = Settings.testPillWorld
        val clearRoad = AtomicBoolean(false)
        val pump = object : Runnable {
            override fun run() {
                if (!clearRoad.get()) return
                val game = Gdx.app.applicationListener as CubeRun
                (field(game, "track").get(game) as Track).rows.clear()
                Gdx.app.postRunnable(this)
            }
        }
        try {
            Settings.setDevMode(false); Settings.testBonus = -1; Settings.testBonusNow = -1
            Settings.testBoxes = 0; Settings.testPillWorld = false
            for ((skin, label) in listOf(15 to "coal", 1 to "gambler", 22 to "eclipse", 17 to "cloud")) {
                if (InstrumentationRegistry.getArguments().getString("jackpotOnly") == "true" && skin != 1) continue
                prefs.edit().clear().putInt("coins", 1000000).putInt("owned_skins", 0x1ffffff)
                    .putInt("owned_bubble_skins", 0x7ff).putInt("skin", skin).putInt("bubbles", 20).commit()
                scores.edit().clear().commit(); Progress.init(context)
                val intent = Intent(context, GameActivity::class.java).putExtra(Hud.EXTRA_AUTOSTART, false)
                ActivityScenario.launch<GameActivity>(intent).use { scenario ->
                    var ready = false; val until = SystemClock.uptimeMillis() + 15000
                    while (!ready && SystemClock.uptimeMillis() < until) {
                        scenario.onActivity { ready = (field(it, "hud").get(it) as? Hud)?.isAttachedToWindow == true }
                        if (!ready) SystemClock.sleep(50)
                    }
                    assertTrue(ready); SystemClock.sleep(900)
                    if (skin == 15) {
                        scenario.onActivity { call(hud(it), "openWardrobe") }
                        SystemClock.sleep(650)
                        panel(scenario, Wardrobe.CUBE, 1, "Lottery", "lottery")
                        panel(scenario, Wardrobe.CUBE, 6, "Midas Little Toe", "midas")
                        panel(scenario, Wardrobe.CUBE, 17, "Floaty", "floaty")
                        panel(scenario, Wardrobe.CUBE, 16, "Quick bubble", "bubblegum")
                        panel(scenario, Wardrobe.BUBBLE, 2, "Double jump", "mint")
                        scenario.onActivity { hud(it).navigateBack() }
                        SystemClock.sleep(700)
                    }
                    clearRoad.set(true); Gdx.app.postRunnable(pump)
                    gl { call(it, "start") }
                    SystemClock.sleep(2200)
                    gl { Stage.paused = true }
                    clearRoad.set(false); gl()
                    if (skin == 15) gl { game ->
                        val track = field(game, "track").get(game) as Track
                        for (i in 0 until 14) track.rows.add(Row(-5f - i * 4f, arrayListOf()).apply {
                            pop = 1f; popStart = 0f
                            coins = arrayListOf(Coin(-2f, .55f, 0f), Coin(0f, .55f, 0f), Coin(2f, .55f, 0f))
                        })
                    }
                    if (skin == 17) gl { game ->
                        val player = field(game, "player").get(game) as Player
                        player.launch(8f)
                        player.update(.12f, 0f, 0f, 180f, trail = false, groundH = 0f)
                    }
                    capture("$label-run")
                    if (skin == 1) {
                        gl { Stage.paused = false }; SystemClock.sleep(250); gl { Stage.paused = true }
                        capture("gambler-alternate-flash")
                        gl { game ->
                            field(game, "lottery").set(game, Lottery(object : Random() {
                                override fun nextBits(bitCount: Int) = 0
                                override fun nextDouble() = 0.0
                            }))
                            call(game, "collectCoin", Coin(0f, .5f, 0f), 0f)
                        }
                        gl { Stage.paused = false }
                        capture("jackpot-rise", 900)
                        if (InstrumentationRegistry.getArguments().getString("jackpotOnly") == "true") {
                            capture("jackpot-burst", 600)
                            capture("jackpot-fountain", 1400)
                            capture("jackpot-slam", 1500)
                            capture("jackpot-gather", 900)
                            capture("jackpot-return", 900)
                            capture("jackpot-finished", 1200)
                        }
                        gl { Stage.paused = true }
                    }
                    if (skin == 22) {
                        gl { game ->
                            val player = field(game, "player").get(game) as Player
                            player.moveToLane(0)
                            player.update(0f, 0f, 0f, 180f, trail = false, groundH = 0f)
                        }
                        capture("zappy-attack")
                        for ((age, name) in listOf(.045f to "trail", .1f to "fade")) {
                            gl { game ->
                                val player = field(game, "player").get(game) as Player
                                val effect = field(player, "zappyFx").get(player)!!
                                call(effect, "update", 0f, 0f)
                                call(effect, "update", age, 0f)
                                player.update(0f, 0f, 0f, 180f, trail = false, groundH = 0f)
                            }
                            capture("zappy-$name")
                        }
                    }
                }
                Stage.paused = false
            }
        } finally {
            clearRoad.set(false); Stage.paused = false; Stage.mode = Stage.NONE
            restore(prefs, saved); restore(scores, savedScores)
            Settings.setDevMode(dev); Settings.testBonus = bonus; Settings.testBonusNow = bonusNow
            Settings.testBoxes = boxes; Settings.testPillWorld = pill; Progress.init(context)
        }
    }
}
