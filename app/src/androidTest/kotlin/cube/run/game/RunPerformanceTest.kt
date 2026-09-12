package cube.run.game

import android.content.Intent
import android.os.Debug
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
    private fun field(type: Class<*>, name: String) = type.getDeclaredField(name).apply { isAccessible = true }

    @Test fun highSpeedAndSecondWind() {
        val args = InstrumentationRegistry.getArguments()
        val intent = Intent(ApplicationProvider.getApplicationContext(), GameActivity::class.java)
            .putExtra("world", args.getString("world")?.toInt() ?: -1)
        ActivityScenario.launch<GameActivity>(intent).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val ready = CountDownLatch(1)
            Gdx.app.postRunnable { ready.countDown() }
            assertTrue("Game surface did not start", ready.await(20, TimeUnit.SECONDS))
            SystemClock.sleep(2000)
            val seconds = (args.getString("seconds")?.toInt() ?: 25).coerceIn(10, 600)
            for (mode in (args.getString("modes") ?: "cruise,hills,second-wind").split(',')) benchmark(mode, seconds)
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
                    val elapsed = (now - start) / 1e9
                    grace.setFloat(game, 100f) // render real obstacles without ending the unattended run
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
                                coins = arrayListOf(Coin(0f, 0.5f, -2f), Coin(0f, 0.5f, -4f))
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
                            "burst=${stats(bursts)} bursts=$count maxRows=$maxRows maxSpeed=$maxSpeed allocBytes=$allocated gc=$gc")
                        assertTrue("Track rows grew without bound: $maxRows", maxRows < 100)
                        done.countDown()
                    }
                } catch (t: Throwable) { failure = t; done.countDown() }
            }
        }
        Gdx.app.postRunnable(callback)
        assertTrue("$mode timed out", done.await(seconds + 20L, TimeUnit.SECONDS))
        failure?.let { throw it }
    }

    private fun stats(values: List<Float>): String {
        if (values.isEmpty()) return "none"
        val sorted = values.sorted()
        return "%.2f/%.2f/%.2f/%.2f".format(java.util.Locale.US,
            sorted[sorted.size / 2], sorted[sorted.size * 95 / 100],
            sorted[sorted.size * 99 / 100], sorted.last())
    }
}
