package cube.run.ui

import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.data.Progress
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Window frame costs, including entrances, on the real GL-backed screens. No profiler attached. */
class ScreenSmoothnessBenchmarkTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: GameActivity
    private fun ui(action: () -> Unit) = inst.runOnMainSync(action)
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner: Any, name: String): T =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner) as T
    private fun hud() = field<Hud>(activity, "hud")

    private fun launch() {
        activity = inst.startActivitySync(Intent(inst.targetContext, GameActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(Hud.EXTRA_AUTOSTART, false)) as GameActivity
        ui {
            Progress::class.java.getDeclaredField("achievementsUnlocked").apply { isAccessible = true }.setBoolean(null, true)
            field<MainMenu>(hud(), "menu").refresh()
            hud().javaClass.getDeclaredMethod("schedulePagePreparation").apply { isAccessible = true }.invoke(hud())
        }
        // Opening and hidden-page preparation are outside the measured screen entrance.
        SystemClock.sleep(5000)
        val ready = CountDownLatch(1)
        (Gdx.app.applicationListener as cube.run.core.Gdx3DGame).afterFreshSceneFrame { ready.countDown() }
        assertTrue("Live GL scene", ready.await(10, TimeUnit.SECONDS))
    }

    @Test fun screens() {
        val args = InstrumentationRegistry.getArguments()
        val repeats = (args.getString("repeats")?.toInt() ?: 3).coerceIn(1, 20)
        val selected = args.getString("screens")?.split(",")
            ?: listOf("menu", "wardrobe", "shop", "settings", "achievements", "results", "record_results")
        val menus = listOf("menu", "wardrobe", "shop", "settings", "achievements").filter { it in selected }
        require(selected.isNotEmpty() && selected.all { it in menus || it == "results" || it == "record_results" })
        val originalUnlocked = Progress.achievementsUnlocked
        val rows = JSONArray()
        val worker = HandlerThread("screen-frame-metrics").apply { start() }
        val handler = Handler(worker.looper)
        try {
            for (sample in 0 until repeats) {
                if (menus.isNotEmpty()) {
                    launch()
                    try {
                        for (name in menus) {
                            measure(rows, handler, name, sample, 3000) {
                                if (name != "menu") hud().javaClass.getDeclaredMethod("open" + name.replaceFirstChar { it.uppercase() })
                                    .apply { isAccessible = true }.invoke(hud())
                            }
                            if (name != "menu") {
                                ui { field<Page>(hud(), "page").navigateBack() }
                                SystemClock.sleep(600)
                            }
                        }
                    } finally { ui { activity.finish() } }
                }
                for (record in listOf(false, true)) {
                    val name = if (record) "record_results" else "results"
                    if (name !in selected) continue
                    launch()
                    try {
                        measure(rows, handler, name, sample, 6500) {
                            hud().hideOptions(animate = false)
                            // A finished run changes the balance and invalidates the hidden page caches.
                            Progress.addCoins(1)
                            hud().showRunOver(4321, 5000, record, 1234, 0)
                        }
                    } finally { ui { activity.finish() } }
                }
            }
        } finally {
            ui { Progress::class.java.getDeclaredField("achievementsUnlocked").apply { isAccessible = true }.setBoolean(null, originalUnlocked) }
            worker.quitSafely(); worker.join(3000)
            File(inst.targetContext.filesDir, "screen-smoothness.json").writeText(
                JSONObject().put("samples", rows).put("model", android.os.Build.MODEL)
                    .put("sdk", android.os.Build.VERSION.SDK_INT).put("fingerprint", android.os.Build.FINGERPRINT)
                    .put("sound_enabled", cube.run.data.Settings.soundEnabled)
                    .put("haptics_enabled", cube.run.data.Settings.hapticsEnabled).toString(2))
        }
    }

    private fun measure(rows: JSONArray, handler: Handler, name: String, sample: Int, duration: Long, trigger: () -> Unit) {
        val frames = ArrayList<LongArray>()
        val game = Gdx.app.applicationListener as cube.run.core.Gdx3DGame
        val glTimes = LongArray(4096)
        var glCount = 0
        val armed = CountDownLatch(1)
        Gdx.app.postRunnable {
            game.renderProbe = { stage ->
                if (stage == "swap" && glCount < glTimes.size) glTimes[glCount++] = System.nanoTime()
            }
            armed.countDown()
        }
        assertTrue(armed.await(5, TimeUnit.SECONDS))
        val lock = Any()
        var collecting = true
        var dropped = 0
        var layouts = 0
        val layout = android.view.ViewTreeObserver.OnGlobalLayoutListener { layouts++ }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, lost ->
            synchronized(lock) {
                if (collecting) {
                    frames.add(longArrayOf(metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
                        metrics.getMetric(FrameMetrics.TOTAL_DURATION), metrics.getMetric(FrameMetrics.DRAW_DURATION),
                        metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)))
                    dropped += lost
                }
            }
        }
        ui {
            activity.window.decorView.viewTreeObserver.addOnGlobalLayoutListener(layout)
            activity.window.addOnFrameMetricsAvailableListener(listener, handler)
            trigger()
        }
        SystemClock.sleep(duration)
        val stopped = CountDownLatch(1)
        Gdx.app.postRunnable { game.renderProbe = null; stopped.countDown() }
        assertTrue(stopped.await(5, TimeUnit.SECONDS))
        ui {
            activity.window.removeOnFrameMetricsAvailableListener(listener)
            activity.window.decorView.viewTreeObserver.removeOnGlobalLayoutListener(layout)
        }
        val drained = CountDownLatch(1)
        handler.post { drained.countDown() }
        assertTrue(drained.await(5, TimeUnit.SECONDS))
        synchronized(lock) { collecting = false }
        assertTrue("$name produced GL frames", glCount > 20)
        fun summary(values: List<Double>): JSONObject {
            if (values.isEmpty()) return JSONObject().put("count", 0)
            val sorted = values.sorted()
            fun at(fraction: Double) = sorted[((sorted.size - 1) * fraction).toInt()]
            return JSONObject().put("count", values.size).put("p50", at(.5)).put("p95", at(.95)).put("p99", at(.99))
                .put("max", sorted.last()).put("over_25_ms", values.count { it > 25.0 })
        }
        val row = JSONObject().put("screen", name).put("sample", sample).put("frames", frames.size)
            .put("metrics_dropped", dropped).put("layouts", layouts)
            .put("gl_interval_ms", summary((1 until glCount).map { (glTimes[it] - glTimes[it - 1]) / 1e6 }))
            .put("interval_ms", summary(frames.zipWithNext { a, b -> (b[0] - a[0]) / 1e6 }))
            .put("total_ms", summary(frames.map { it[1] / 1e6 }))
            .put("draw_ms", summary(frames.map { it[2] / 1e6 }))
            .put("layout_ms", summary(frames.map { it[3] / 1e6 }))
            .put("gl_raw_ns", JSONArray(glTimes.take(glCount)))
            .put("raw_ns", JSONArray(frames.map { JSONArray(it.toList()) }))
        rows.put(row)
        android.util.Log.i("SCREEN_BENCH", "$name sample=$sample uiFrames=${frames.size} layouts=$layouts GL=${row.getJSONObject("gl_interval_ms")} draw=${row.getJSONObject("draw_ms")}")
    }
}
