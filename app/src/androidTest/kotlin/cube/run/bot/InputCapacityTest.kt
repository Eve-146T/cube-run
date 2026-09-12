package cube.run.bot

import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.core.gfx.TouchInput
import cube.run.core.gfx.TouchListener
import cube.run.game.CubeRun
import cube.run.game.Difficulty
import cube.run.game.track.Track
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.locks.LockSupport
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InputCapacityTest {
    @Test fun measureAndroidGestureDeliveryUnderRenderingLoad() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            SystemClock.sleep(1000)
            val kernel = InstrumentationRegistry.getArguments().getString("kernel") == "true"
            val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), if (kernel) "bot-input-kernel.csv" else "bot-input.csv")
            output.bufferedWriter().use { writer ->
                writer.appendLine("requested_gestures_s,sent,recognized,inject_rejections,wrong_direction,actual_send_rate,latency_p50_ms,latency_p95_ms,max_gestures_per_gl_frame")
                for (rate in listOf(10, 30, 60, 120, 240)) {
                    val total = rate * 2
                    val times = AtomicLongArray(total)
                    val seen = AtomicInteger(); val wrong = AtomicInteger()
                    val latencies = ArrayList<Long>()
                    val perFrame = HashMap<Long, Int>()
                    val ready = CountDownLatch(1)
                    val active = java.util.concurrent.atomic.AtomicBoolean(true)
                    val pump = object : Runnable {
                        override fun run() {
                            val game = Gdx.app.applicationListener as CubeRun
                            value<Track>(game, "track").rows.clear()
                            if (active.get()) Gdx.app.postRunnable(this)
                        }
                    }
                    Gdx.app.postRunnable {
                        val game = Gdx.app.applicationListener as CubeRun
                        Stage.paused = false
                        game.onDown(360f, 760f)
                        value<Difficulty>(game, "difficulty").boostTo(1f)
                        val listener = object : TouchListener {
                            override fun onDown(x: Float, y: Float) {}
                            override fun onDrag(x: Float, y: Float, dx: Float, dy: Float) {}
                            override fun onUp(x: Float, y: Float) {}
                            override fun onTap(x: Float, y: Float) {}
                            override fun onSwipe(dir: Int) {
                                val index = seen.getAndIncrement()
                                if (index < total) {
                                    if (dir != index % 4) wrong.incrementAndGet()
                                    synchronized(latencies) {
                                        latencies.add(SystemClock.uptimeMillis() - times.get(index))
                                        val frame = Gdx.graphics.frameId
                                        perFrame[frame] = (perFrame[frame] ?: 0) + 1
                                    }
                                }
                            }
                        }
                        Gdx.input.inputProcessor = TouchInput(listener, { Gdx.graphics.width }, { false })
                        Gdx.app.postRunnable(pump)
                        ready.countDown()
                    }
                    assertTrue(ready.await(10, TimeUnit.SECONDS))
                    var rejected = 0
                    val start = System.nanoTime()
                    var elapsed: Double
                    if (kernel) {
                        File(output.parentFile, "bot-input-rate.txt").writeText("$rate $total\n")
                        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                            "su -c /data/local/tmp/cube-run-bot-probe.sh")
                        ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { input ->
                            val first = input.readLine()
                            assertTrue("Kernel probe did not start: $first", first?.startsWith("START ") == true)
                            val began = first.substringAfter(' ').toLong()
                            for (i in 0 until total) times.set(i, began + i * 1000L / rate)
                            val last = input.readLine()
                            assertTrue("Kernel probe did not complete: $last", last?.startsWith("ELAPSED ") == true)
                            elapsed = last.substringAfter(' ').toDouble()
                        }
                    } else {
                      for (i in 0 until total) {
                        val target = start + i * 1_000_000_000L / rate
                        while (System.nanoTime() < target) LockSupport.parkNanos(minOf(1_000_000, target - System.nanoTime()).coerceAtLeast(1))
                        times.set(i, SystemClock.uptimeMillis())
                        if (!AndroidGestures.flick(i % 4 + 1, Gdx.graphics.width, Gdx.graphics.height, sync = false)) rejected++
                    }
                      elapsed = (System.nanoTime() - start) / 1e9
                    }
                    val deadline = SystemClock.uptimeMillis() + 3000
                    while (seen.get() < total && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(10)
                    active.set(false)
                    synchronized(latencies) {
                        val sorted = latencies.sorted()
                        assertTrue("No Android gestures reached libGDX", sorted.isNotEmpty())
                        val line = "$rate,$total,${seen.get()},$rejected,${wrong.get()},${total / elapsed},${sorted[sorted.size / 2]},${sorted[sorted.size * 95 / 100]},${perFrame.values.max()}"
                        writer.appendLine(line); writer.flush(); Log.i("BOT_INPUT", line)
                    }
                }
            }
        }
    }
}
