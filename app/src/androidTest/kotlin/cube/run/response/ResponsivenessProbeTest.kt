package cube.run.response

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.backends.android.AndroidApplication
import cube.run.GameActivity
import java.io.File
import java.lang.reflect.Field
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** One test APK probes untouched 1.4/2.0/2.1 APKs and the 2.2 candidate. */
@RunWith(AndroidJUnit4::class)
class ResponsivenessProbeTest {
    private class Trial(val index: Int) {
        val action = index % 4 + 1
        @Volatile var uiDown = 0L
        @Volatile var uiMove = 0L
        @Volatile var glDown = 0L
        @Volatile var glMove = 0L
        @Volatile var applied = 0L
        @Volatile var rendered = 0L
        @Volatile var half = 0L
        @Volatile var ninety = 0L
        var x = 0f; var y = 0f; var duck = 0f; var target = 0f
    }

    private fun field(owner: Any, name: String): Field {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true } }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        error("Missing ${owner.javaClass.name}.$name")
    }
    private fun get(owner: Any, name: String): Any = field(owner, name).get(owner)!!
    private fun gl(action: () -> Unit) {
        val done = CountDownLatch(1); var failure: Throwable? = null
        Gdx.app.postRunnable { try { action() } catch (t: Throwable) { failure = t } finally { done.countDown() } }
        assertTrue("Probe GL callback timed out", done.await(15, TimeUnit.SECONDS)); failure?.let { throw it }
    }
    private fun surface(view: View): SurfaceView? {
        if (view is SurfaceView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) surface(view.getChildAt(i))?.let { return it }
        return null
    }

    @Test fun recordKernelTouchToActionAndRenderedMovement() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val count = (args.getString("count")?.toInt() ?: 48).coerceIn(4, 256)
        val duration = (args.getString("duration")?.toInt() ?: 8).coerceIn(8, 200)
        val fast = args.getString("fast") == "true"
        val trials = Array(count) { Trial(it) }
        val uiIndex = AtomicInteger(-1)
        var glIndex = -1
        val output = instrumentation.targetContext.getExternalFilesDir(null)!!
        fun inject(durationMs: Int, n: Int): String {
            // UiAutomation tokenizes its command; it does not interpret shell quotes.
            val script = File(output, "response-command.sh")
            script.writeText("exec su -c '/data/local/cube-run-responsiveness/touch_probe /dev/input/event1 $durationMs $n' 2>&1\n")
            val fd = instrumentation.uiAutomation.executeShellCommand("sh ${script.absolutePath}")
            val result = ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
            check(result.startsWith("trial,action,")) { "Native probe failed: $result" }
            return result
        }
        var renderFrames = 0
        var renderCpuNs = 0L
        var lastFrame = 0L
        val frameTimes = ArrayList<Long>(count * 65)
        val cpuTimes = ArrayList<Long>(count * 65)
        val capture = AtomicBoolean()
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            lateinit var touchView: SurfaceView
            scenario.onActivity {
                it.setShowWhenLocked(true); it.setTurnScreenOn(true)
                touchView = surface(it.window.decorView) ?: error("No game SurfaceView")
                val badge = android.widget.TextView(it).apply {
                    text = "RESPONSE TEST · ${if (fast) "FAST RUN" else "CRUISE"} · $duration ms FLICKS"
                    setTextColor(-1); setBackgroundColor(0xDD161322.toInt()); textSize = 13f; setPadding(12, 6, 12, 6)
                }
                it.addContentView(badge, android.widget.FrameLayout.LayoutParams(-2, -2).apply {
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                })
            }
            gl {} // wait for the GL create callback and initial track geometry
            val game = Gdx.app.applicationListener
            val listenerField = AndroidApplication::class.java.getDeclaredField("listener").apply { isAccessible = true }
            val body = runCatching { get(game, "player") }.getOrDefault(game)
            val lane = field(body, "lane"); val px = field(body, "px"); val py = field(body, "py")
            val vy = field(body, "vy"); val duck = field(body, "duck"); val duckT = field(body, "duckT")
            val laneX = body.javaClass.getDeclaredMethod("laneX", Int::class.javaPrimitiveType).apply { isAccessible = true }
            val track = runCatching { get(game, "track") }.getOrDefault(game)
            @Suppress("UNCHECKED_CAST") val rows = get(track, "rows") as MutableList<Any>
            var rowZ: java.lang.reflect.Field? = null
            val difficulty = runCatching { get(game, "difficulty") }.getOrDefault(game)
            val diff = field(difficulty, "diff")
            val originalInput = Gdx.input.inputProcessor
            val androidInput = Gdx.input as View.OnTouchListener
            var current: Trial? = null
            fun applyEvent(action: () -> Boolean): Boolean {
                val t = current
                val beforeLane = lane.getInt(body); val beforeVy = vy.getFloat(body); val beforeDuck = duckT.getFloat(body)
                val handled = action()
                if (t != null && t.applied == 0L && (lane.getInt(body) != beforeLane ||
                    vy.getFloat(body) != beforeVy || duckT.getFloat(body) != beforeDuck)) {
                    t.applied = System.nanoTime()
                    t.target = (laneX.invoke(body, lane.getInt(body)) as Number).toFloat()
                }
                return handled
            }
            try {
                // Persisted settings are backed up/restored by the host harness.
                val settings = runCatching { Class.forName("cube.run.data.Settings") }.getOrElse { Class.forName("cube.run.core.Settings") }
                val settingsObject = settings.getField("INSTANCE").get(null)
                for ((name, value) in listOf("setSmoothControl" to false, "setSoundEnabled" to true, "setHapticsEnabled" to true))
                    settings.getMethod(name, Boolean::class.javaPrimitiveType).invoke(settingsObject, value)
                gl {
                    game.javaClass.getMethod("onDown", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).invoke(game, 360f, 760f)
                    runCatching { field(game, "runT") }.getOrElse { field(game, "runTime") }.setFloat(game, 10f)
                    runCatching { get(game, "bubble") }.getOrNull()?.let { b -> get(b, "timer").javaClass.getMethod("stop").invoke(get(b, "timer")) }
                    Gdx.input.inputProcessor = object : InputProcessor by originalInput {
                        override fun touchDown(x: Int, y: Int, pointer: Int, button: Int): Boolean {
                            if (pointer == 0 && capture.get()) {
                                glIndex++; current = trials.getOrNull(glIndex)
                                current?.let { t -> t.glDown = System.nanoTime(); t.x = px.getFloat(body); t.y = py.getFloat(body); t.duck = duck.getFloat(body) }
                            }
                            return applyEvent { originalInput.touchDown(x, y, pointer, button) }
                        }
                        override fun touchDragged(x: Int, y: Int, pointer: Int): Boolean {
                            if (pointer == 0) current?.let { if (it.glMove == 0L) it.glMove = System.nanoTime() }
                            return applyEvent { originalInput.touchDragged(x, y, pointer) }
                        }
                        override fun touchUp(x: Int, y: Int, pointer: Int, button: Int): Boolean =
                            applyEvent { originalInput.touchUp(x, y, pointer, button) }
                    }
                    listenerField.set(Gdx.app, object : ApplicationListener by game {
                        override fun render() {
                            // Keep the historical apps alive without changing their input code:
                            // remove rows only as they enter the collision/pickup zone. Far scenery,
                            // obstacles, coins, player physics, sound and haptics keep running.
                            val it = rows.iterator()
                            while (it.hasNext()) {
                                val row = it.next()
                                val z = rowZ ?: field(row, "z").also { rowZ = it }
                                if (z.getFloat(row) > -5f) it.remove()
                            }
                            diff.setFloat(difficulty, if (fast) 1f else 0f)
                            val cpuBefore = android.os.Debug.threadCpuTimeNanos()
                            game.render()
                            val now = System.nanoTime()
                            if (capture.get()) {
                                val cpuNs = android.os.Debug.threadCpuTimeNanos() - cpuBefore
                                renderCpuNs += cpuNs; renderFrames++; cpuTimes.add(cpuNs)
                                if (lastFrame != 0L) frameTimes.add(now - lastFrame)
                                lastFrame = now
                                current?.let { t ->
                                    if (t.applied > 0) {
                                        val x = px.getFloat(body)
                                        val moved = when (t.action) {
                                            1, 2 -> abs(x - t.x) > .005f
                                            3 -> py.getFloat(body) > t.y + .005f
                                            else -> duck.getFloat(body) > t.duck + .005f
                                        }
                                        if (moved && t.rendered == 0L) t.rendered = now
                                        if (t.action <= 2 && abs(t.target - t.x) > .1f) {
                                            val fraction = abs(x - t.x) / abs(t.target - t.x)
                                            if (fraction >= .5f && t.half == 0L) t.half = now
                                            if (fraction >= .9f && t.ninety == 0L) t.ninety = now
                                        }
                                    }
                                }
                            }
                        }
                    })
                }
                instrumentation.runOnMainSync {
                    touchView.setOnTouchListener { view, event ->
                        if (capture.get()) {
                            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                                val index = uiIndex.incrementAndGet(); trials.getOrNull(index)?.uiDown = System.nanoTime()
                            } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                                trials.getOrNull(uiIndex.get())?.let { if (it.uiMove == 0L) it.uiMove = System.nanoTime() }
                            }
                        }
                        androidInput.onTouch(view, event)
                    }
                }
                SystemClock.sleep(5000)
                // Prime every input/action path before the measured trials.
                inject(8, 4)
                SystemClock.sleep(1000)
                gl { capture.set(true) }
                File(output, "response-kernel.csv").writeText(inject(duration, count))
                SystemClock.sleep(900)
                gl { capture.set(false) }
                File(output, "response-trials.csv").bufferedWriter().use { writer ->
                    writer.appendLine("trial,action,ui_down_ns,ui_first_move_ns,gl_down_ns,gl_first_move_ns,applied_ns,first_render_ns,half_lane_ns,ninety_lane_ns")
                    for (t in trials) writer.appendLine("${t.index},${t.action},${t.uiDown},${t.uiMove},${t.glDown},${t.glMove},${t.applied},${t.rendered},${t.half},${t.ninety}")
                }
                File(output, "response-frames.csv").bufferedWriter().use { writer ->
                    writer.appendLine("frame_interval_ns,render_thread_cpu_ns")
                    for (i in frameTimes.indices) writer.appendLine("${frameTimes[i]},${cpuTimes.getOrElse(i + 1) { 0L }}")
                }
                File(output, "response-summary.txt").writeText("count=$count duration=$duration fast=$fast ui=${uiIndex.get()+1} gl=${glIndex+1} applied=${trials.count { it.applied>0 }} rendered=${trials.count { it.rendered>0 }} renderFrames=$renderFrames meanRenderCpuMs=${renderCpuNs/1e6/renderFrames}\n")
                assertEquals("Lost or unexpected UI touch-downs", count, uiIndex.get() + 1)
                assertEquals("Lost or unexpected GL touch-downs", count, glIndex + 1)
                assertTrue("No input actions were applied", trials.any { it.applied > 0 })
            } finally {
                gl { capture.set(false); listenerField.set(Gdx.app, game); Gdx.input.inputProcessor = originalInput }
                instrumentation.runOnMainSync { touchView.setOnTouchListener(androidInput) }
            }
        }
    }
}
