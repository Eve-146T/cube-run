package cube.run.game

import android.content.Intent
import android.os.Debug
import cube.run.bot.LiveBotDriver
import java.util.concurrent.atomic.AtomicBoolean
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.Gdx3DGame
import cube.run.core.Stage
import cube.run.data.Bonus
import cube.run.game.track.Coin
import cube.run.game.track.ObstacleFactory
import cube.run.game.track.Row
import cube.run.game.track.Track
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** On-device, vsync-paced stress runs. No purchased stock or saved scores are consumed. */
@RunWith(AndroidJUnit4::class)
class RunPerformanceTest {
    private lateinit var badge: android.widget.TextView
    private var botEnabled = true
    private var statusGeneration = 0

    private fun showStatus(mode: String, hits: Int, hit: Boolean = false) {
        badge.post {
            val generation = ++statusGeneration
            badge.text = if (hit) "COLLISION · TEST PROTECTION SAVED THE BOT ($hits)"
                else if (botEnabled) "BOT PERFORMANCE TEST · PROTECTED · $mode · hits $hits"
                else "STATIC PERFORMANCE TEST · IMMUNITY · $mode"
            badge.setBackgroundColor(if (hit) 0xEEAA2935.toInt() else 0xDD161322.toInt())
            if (hit) badge.postDelayed({ if (generation == statusGeneration) showStatus(mode, hits) }, 1500)
        }
    }

    private fun field(type: Class<*>, name: String) = type.getDeclaredField(name).apply { isAccessible = true }

    @Test fun highSpeedAndSecondWind() {
        val args = InstrumentationRegistry.getArguments()
        botEnabled = args.getString("bot") != "false"
        val intent = Intent(ApplicationProvider.getApplicationContext(), GameActivity::class.java)
            .putExtra("world", args.getString("world")?.toInt() ?: -1)
        ActivityScenario.launch<GameActivity>(intent).use { scenario ->
            scenario.onActivity {
                it.setShowWhenLocked(true); it.setTurnScreenOn(true)
                badge = android.widget.TextView(it).apply {
                    text = if (botEnabled) "BOT PERFORMANCE TEST · PROTECTED" else "STATIC PERFORMANCE TEST · IMMUNITY"
                    setTextColor(android.graphics.Color.WHITE); setBackgroundColor(0xDD161322.toInt())
                    textSize = 14f; setPadding(16, 8, 16, 8)
                }
                it.addContentView(badge, android.widget.FrameLayout.LayoutParams(-2, -2).apply {
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                })
            }
            val ready = CountDownLatch(1)
            Gdx.app.postRunnable { ready.countDown() }
            assertTrue("Game surface did not start", ready.await(20, TimeUnit.SECONDS))
            SystemClock.sleep(2000)
            val seconds = (args.getString("seconds")?.toInt() ?: 25).coerceIn(10, 600)
            val profile = args.getString("profile") == "true"
            if (profile) Debug.startMethodTracingSampling(java.io.File(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
                "performance.trace").absolutePath, 32 * 1024 * 1024, 10_000)
            try {
                for (mode in (args.getString("modes") ?: "cruise,hills,second-wind").split(',')) benchmark(mode, seconds)
            } finally { if (profile) Debug.stopMethodTracing() }
        }
    }

    private fun benchmark(mode: String, seconds: Int) {
        require(mode in listOf("cruise", "hills", "second-wind", "jet", "wide", "late", "five-boosts"))
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        val frames = ArrayList<Float>(seconds * 65)
        val cpu = ArrayList<Float>(seconds * 65)
        val threadCpu = ArrayList<Float>(seconds * 65)
        val bursts = ArrayList<Float>(20)
        val game = Gdx.app.applicationListener as CubeRun
        val track = field(CubeRun::class.java, "track").get(game) as Track
        val difficulty = field(CubeRun::class.java, "difficulty").get(game) as Difficulty
        val fire = field(CubeRun::class.java, "fire").get(game) as FireBoost
        val boostTaps = field(FireBoost::class.java, "taps")
        val player = field(CubeRun::class.java, "player").get(game) as Player
        val powers = field(CubeRun::class.java, "powerUps").get(game) as PowerUps
        val grace = field(CubeRun::class.java, "jetGrace")
        val speed = field(CubeRun::class.java, "spd")
        val dead = field(CubeRun::class.java, "dead")
        val perf = field(Gdx3DGame::class.java, "perf").get(game)
        val samples = field(perf.javaClass, "cpuMs").get(perf) as FloatArray
        val slot = field(perf.javaClass, "curSlot")
        val secondWind = CubeRun::class.java.getDeclaredMethod("secondWind").apply { isAccessible = true }
        val factory = ObstacleFactory(Random(42))
        val collisionSeen = AtomicBoolean()
        var previousHit = false
        var protectedHits = 0
        var start = 0L
        var previous = 0L
        var previousCpu = 0L
        var nextBurst = 0L
        var count = 0
        var maxRows = 0
        var maxSpeed = 0f
        var allocations = 0L
        var collections = 0L
        val callback = object : Runnable {
            override fun run() {
                try {
                    val now = System.nanoTime()
                    val nowCpu = Debug.threadCpuTimeNanos()
                    if (start == 0L) {
                        Stage.paused = false
                        game.onDown(360f, 760f)
                        if (mode == "five-boosts") Stage.boostRequests.set(5)
                        else {
                            difficulty.boostTo(1f)
                            field(CubeRun::class.java, "runT").setFloat(game, 10f)
                        }
                        game.session.setScore(10000)
                        track.portalEvery = Int.MAX_VALUE
                        powers.reset()
                        player.setFlying(false)
                        track.airCoins = false
                        track.dropCoins()
                        track.forceBonus(Bonus.NONE)
                        Terrain.set(mode == "hills")
                        if (mode == "wide") {
                            track.forceBonus(Bonus.WIDE)
                            field(Track::class.java, "bonusRowsLeft").setInt(track, 1_000_000)
                        }
                        if (mode == "late") {
                            field(Gdx3DGame::class.java, "time").setFloat(game, 10000f)
                            field(CubeRun::class.java, "dist").setFloat(game, 300000f)
                            field(CubeRun::class.java, "rowsPassed").setInt(game, 30000)
                        }
                        if (mode == "jet") {
                            powers.jet.start(seconds + 30f)
                            player.setFlying(true)
                            track.airCoins = true
                            track.liftCoins()
                        }
                        start = now
                        nextBurst = now
                    }
                    val hit = collisionSeen.getAndSet(false)
                    if (hit && !previousHit) { protectedHits++; showStatus(mode, protectedHits, hit = true)
                        Log.w("RUN_BENCH", "$mode protected collision episode $protectedHits") }
                    previousHit = hit
                    val elapsed = (now - start) / 1e9
                    if (!botEnabled) grace.setFloat(game, 100f) // render real obstacles without ending the unattended run
                    if (elapsed >= 5 && previous != 0L) {
                        frames.add((now - previous) / 1e6f)
                        cpu.add(samples[slot.getInt(perf)])
                        threadCpu.add((nowCpu - previousCpu) / 1e6f)
                    }
                    if (elapsed >= 5 && allocations == 0L) {
                        if (mode == "five-boosts") assertEquals("All five opening boosts must apply", 5, boostTaps.getInt(fire))
                        allocations = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()
                        collections = Debug.getRuntimeStat("art.gc.gc-count").toLong()
                    }
                    previous = now
                    previousCpu = nowCpu
                    maxRows = maxOf(maxRows, track.rows.size)
                    maxSpeed = maxOf(maxSpeed, speed.getFloat(game))
                    if (mode == "second-wind" && now >= nextBurst) {
                        // Ten dense rows within the real 30-unit/s revive clearance, plus the horizon.
                        track.rows.clear()
                        repeat(16) { rowIndex ->
                            track.rows.add(Row(-rowIndex * 7f, arrayListOf(
                                factory.pillar(-1.7f, rowIndex * 19f),
                                factory.pillar(0f, rowIndex * 19f),
                                factory.pillar(1.7f, rowIndex * 19f),
                            )).apply {
                                pop = 1f
                                val lane = if (botEnabled) (rowIndex % 3 - 1) * 1.7f else 0f
                                coins = arrayListOf(Coin(lane, 0.5f, -2f), Coin(lane, 0.5f, -4f))
                            })
                        }
                        val before = System.nanoTime()
                        secondWind.invoke(game)
                        val ms = (System.nanoTime() - before) / 1e6f
                        if (elapsed >= 5) bursts.add(ms)
                        count++
                        nextBurst = now + 2_000_000_000L
                    }
                    assertFalse("Run died during $mode", game.session.isOver || dead.getBoolean(game))
                    if (elapsed < seconds) Gdx.app.postRunnable(this)
                    else {
                        val allocated = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong() - allocations
                        val gc = Debug.getRuntimeStat("art.gc.gc-count").toLong() - collections
                        Log.i("RUN_BENCH", "$mode frames=${frames.size} frame=${stats(frames)} cpu=${stats(cpu)} threadCpu=${stats(threadCpu)} " +
                            "over25=${frames.count { it > 25f }} over50=${frames.count { it > 50f }} " +
                            "bot=$botEnabled protectedHits=$protectedHits burst=${stats(bursts)} bursts=$count maxRows=$maxRows maxSpeed=$maxSpeed allocBytes=$allocated gc=$gc")
                        assertTrue("Track rows grew without bound: $maxRows", maxRows < 100)
                        Stage.paused = true
                        done.countDown()
                    }
                } catch (t: Throwable) { failure = t; done.countDown() }
            }
        }
        val observer = field(CubeRun::class.java, "testCrashObserver")
        showStatus(mode, 0)
        LiveBotDriver.gl {
            if (botEnabled) {
                grace.setFloat(game, 0f)
                observer.set(game, { collisionSeen.set(true); Unit })
            }
        }
        try {
            Gdx.app.postRunnable(callback)
            if (botEnabled) LiveBotDriver().use { bot ->
                val deadline = SystemClock.uptimeMillis() + (seconds + 20L) * 1000L
                bot.drive({ done.count > 0 && SystemClock.uptimeMillis() < deadline })
                Log.i("RUN_BOT", "$mode ${bot.summary()} protectedHits=$protectedHits")
                assertTrue("Performance bot stopped unexpectedly", bot.alive)
            }
            assertTrue("$mode timed out", done.await(if (botEnabled) 1L else seconds + 20L, TimeUnit.SECONDS))
            failure?.let { throw it }
        } finally { LiveBotDriver.gl { observer.set(game, null); Stage.paused = true } }

    }

    private fun stats(values: List<Float>): String {
        if (values.isEmpty()) return "none"
        val sorted = values.sorted()
        return "%.2f/%.2f/%.2f/%.2f".format(java.util.Locale.US,
            sorted[sorted.size / 2], sorted[sorted.size * 95 / 100],
            sorted[sorted.size * 99 / 100], sorted.last())
    }
}
