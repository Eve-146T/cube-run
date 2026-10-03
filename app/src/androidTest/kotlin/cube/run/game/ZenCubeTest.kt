package cube.run.game

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Achievements
import cube.run.data.Scores
import cube.run.data.Settings
import cube.run.data.Skins
import cube.run.game.track.Ob
import cube.run.game.track.ObType
import cube.run.game.track.Pickup
import cube.run.game.track.Row
import cube.run.game.track.Track
import cube.run.game.track.ZenDissolve
import cube.run.ui.Hud
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The Zen cube through the real GL game: obstacles melt, bubbles still come first, nothing is recorded. */
class ZenCubeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private inline fun <reified T> read(owner: Any, name: String): T = field(owner, name).get(owner) as T
    private fun call(game: Any, name: String, vararg args: Any) {
        game.javaClass.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(game, *args)
    }
    private fun gl(action: (CubeRun) -> Unit) {
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        Gdx.app.postRunnable {
            try { action(Gdx.app.applicationListener as CubeRun) } catch (t: Throwable) { failure = t } finally { done.countDown() }
        }
        assertTrue("GL task completes", done.await(15, TimeUnit.SECONDS))
        failure?.let { throw it }
    }
    private fun restore(prefs: SharedPreferences, saved: Map<String, *>) {
        val edit = prefs.edit().clear()
        saved.forEach { (key, value) -> when (value) {
            is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is String -> edit.putString(key, value)
            is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
        } }
        assertTrue(edit.commit())
    }
    private fun fixture(skin: Int, action: (CubeRun) -> Unit) {
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val scores = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
        val saved = prefs.all; val savedScores = scores.all; val savedDev = Settings.devMode
        val savedZen = Progress.zenRun
        try {
            Settings.setDevMode(false)
            assertTrue(prefs.edit().clear().putBoolean("achievements_unlocked", true)
                .putInt("owned_skins", 1 or (1 shl Skins.ZEN_ID)).putInt("skin", skin).putInt("bubbles", 5).commit())
            scores.edit().clear().commit(); Progress.init(context)
            val intent = Intent(context, GameActivity::class.java).putExtra(Hud.EXTRA_AUTOSTART, false)
            ActivityScenario.launch<GameActivity>(intent).use { scenario ->
                var ready = false
                val until = SystemClock.uptimeMillis() + 10000
                while (!ready && SystemClock.uptimeMillis() < until) {
                    scenario.onActivity { ready = (field(it, "hud").get(it) as? Hud)?.let { h -> h.isAttachedToWindow && h.alpha == 1f } == true }
                    if (!ready) SystemClock.sleep(30)
                }
                assertTrue("Game menu ready", ready)
                gl { game ->
                    Stage.paused = true
                    call(game, "start")
                    read<Track>(game, "track").rows.clear()
                    action(game)
                }
            }
        } finally {
            Stage.paused = false; Stage.mode = Stage.NONE; Progress.zenRun = savedZen
            restore(prefs, saved); restore(scores, savedScores)
            Settings.setDevMode(savedDev)
            Progress.init(context)
        }
    }
    private fun wall() = Row(0f, arrayListOf(Ob(Color.RED, 0f, 1f, .8f, ObType.SOLID, 1.6f, 2f, 1f)))

    private fun captureDissolve(game: CubeRun, name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureZenDissolve") != "true") return
        game.render()
        val folder = java.io.File(context.getExternalFilesDir(null), "zen-dissolve").apply { mkdirs() }
        val pixmap = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(0, 0,
            Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        try {
            com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.absolute(java.io.File(folder, "$name.png").path), pixmap)
        } finally { pixmap.dispose() }
    }

    @Test fun zenMeltsTheObstacleInsteadOfCrashing() = fixture(Skins.ZEN_ID) { game ->
        val row = wall()
        call(game, "hit", row)
        assertFalse("Zen cannot crash", read<Boolean>(game, "dead"))
        assertTrue("The wall melted", row.obs.none { it.type == ObType.SOLID })
        assertEquals("No bubble was spent", 5, Progress.bubbles)
    }

    @Test fun dissolvePreservesTheBlockThenRetiresAndResets() = fixture(Skins.ZEN_ID) { game ->
        val row = wall()
        val obstacle = row.obs.single()
        val effect = read<ZenDissolve>(game, "zenDissolve")
        call(game, "hit", row)
        captureDissolve(game, "01-contact")
        val count = read<Int>(effect, "live")
        assertTrue("Collision leaves a visible block copy", count > 1)
        val tiles = read<Array<*>>(effect, "pool").take(count).filterNotNull()
        val volume = tiles.sumOf { (read<Float>(it, "sx") * read<Float>(it, "sy") * read<Float>(it, "sz")).toDouble() }
        assertEquals("Tiles cover the full block volume", (obstacle.sx * obstacle.sy * obstacle.sz).toDouble(), volume, 0.001)
        assertTrue("Release travels through the block", tiles.map { read<Float>(it, "delay") }.distinct().size > 1)
        effect.update(0.25f, 2f)
        captureDissolve(game, "02-release")
        assertEquals("The dissolve lingers after contact", count, read<Int>(effect, "live"))
        effect.update(0.3f, 1f)
        captureDissolve(game, "03-drift")
        effect.update(0.7f, 0f)
        assertEquals("Every tile retires", 0, read<Int>(effect, "live"))
        repeat(20) { effect.melt(obstacle, 0f) }
        assertEquals("Dense collisions stay within the pool", read<Array<*>>(effect, "pool").size, read<Int>(effect, "live"))
        game.resetToMenu()
        assertEquals("Returning to the menu clears the effect", 0, read<Int>(effect, "live"))
    }

    @Test fun anActiveBubbleTakesTheHitBeforeZen() = fixture(Skins.ZEN_ID) { game ->
        val bubble = read<Bubble>(game, "bubble")
        call(game, "tryBubble")
        assertTrue("A Zen bubble goes up", bubble.active)
        assertEquals("Zen bubbles are free", 5, Progress.bubbles)
        call(game, "hit", wall())
        assertFalse("The bubble took the hit and popped", bubble.active)
        assertFalse(read<Boolean>(game, "dead"))
        assertEquals("A bubble uses its own shatter effect", 0, read<Int>(read<ZenDissolve>(game, "zenDissolve"), "live"))
    }

    @Test fun zenRecordsNoAchievementsScoresOrRewards() = fixture(Skins.ZEN_ID) { game ->
        assertTrue(Progress.zenRun)
        assertEquals("A Zen run is not counted as a run", 0, Progress.metric("regular"))
        game.session.addScore(700)
        Progress.addMetric("near_miss")
        val row = Row(0f, arrayListOf()).apply { pickup = Pickup.BUBBLE }
        call(game, "collectPickup", row, 0f)
        assertEquals("Bubble pickups stock nothing", 5, Progress.bubbles)
        assertEquals(0, Progress.totalPowerups)
        assertEquals(0, Progress.bestRunScore)
        assertEquals(0, Progress.metric("near_miss"))
        call(game, "die"); repeat(120) { game.tick(1f / 60f) } // the developer's END RUN path
        assertEquals("Nothing is banked or submitted", 0, Scores.best("cuberun"))
    }

    @Test fun monkNeeds100ConsecutiveDestructionsAndAllowsStairs() = fixture(Skins.ZEN_ID) { game ->
        val monk = Achievements.all.single { it.id == "monk" }
        repeat(60) { call(game, "hit", wall()) }
        call(game, "scoreRow", wall()) // a dodged or jumped obstacle
        assertEquals(0, read<Int>(game, "zenStreak"))
        repeat(60) { call(game, "hit", wall()) }
        assertEquals("Separate streaks do not add together", 60, Progress.metric("monk"))
        assertEquals(0, Achievements.snapshot(monk).earnedTiers)
        val stairs = Row(0f, arrayListOf(Ob(Color.BLUE, 0f, 1f, .8f, ObType.PLAT, 1.6f, 2f, 4f)))
        call(game, "scoreRow", stairs)
        call(game, "scoreRow", Row(0f, arrayListOf()))
        assertEquals("Stairs and empty rows leave the streak intact", 60, read<Int>(game, "zenStreak"))
        repeat(39) { call(game, "hit", wall()) }
        assertEquals(0, Achievements.snapshot(monk).earnedTiers)
        call(game, "hit", wall())
        assertEquals(1, Achievements.snapshot(monk).earnedTiers)
        Progress.init(context)
        assertEquals("Monk persists", 100, Progress.metric("monk"))
        assertEquals(2000, Achievements.claim("monk"))
        assertEquals(0, Achievements.claim("monk"))
    }

    @Test fun bubbleShattersAndNewRunsBreakTheMonkStreak() = fixture(Skins.ZEN_ID) { game ->
        repeat(10) { call(game, "hit", wall()) }
        call(game, "tryBubble")
        call(game, "hit", wall())
        assertEquals(0, read<Int>(game, "zenStreak"))
        assertEquals("A bubble destruction does not count", 10, Progress.metric("monk"))
        call(game, "hit", wall())
        game.resetToMenu()
        (game.session as cube.run.core.GameHostSession).resetToMenu()
        call(game, "start")
        assertEquals(0, read<Int>(game, "zenStreak"))
    }

    @Test fun zenNeverSpawnsBoxesOrShardsEvenWithDeveloperPickups() = fixture(Skins.ZEN_ID) { game ->
        val track = read<Track>(game, "track")
        field(track, "runScore").setInt(track, 5000)
        val savedDev = Settings.devMode
        try {
            for (dev in listOf(false, true)) {
                Settings.setDevMode(dev)
                val seen = hashSetOf<Int>()
                repeat(700) {
                    field(track, "rowsSpawned").setInt(track, 1000)
                    field(track, "rowsSincePickup").setInt(track, 1000)
                    val row = Row(-10f, arrayListOf())
                    call(track, "layPickup", row, cube.run.game.track.Step.EM)
                    seen.add(row.pickup)
                    assertFalse("No mystery box is generated", row.pickup == Pickup.BOX)
                    assertTrue("No shard kind is generated", Pickup.shardType(row.pickup) < 0)
                }
                assertTrue("Zen still offers powerups", seen.contains(Pickup.MAGNET))
                assertTrue(seen.contains(Pickup.BUBBLE))
            }
        } finally { Settings.setDevMode(savedDev) }
        for (kind in listOf(Pickup.BOX, Pickup.SHARD_EMBER, Pickup.SHARD_FROST, Pickup.SHARD_VOID)) {
            call(game, "collectPickup", Row(0f, arrayListOf()).apply { pickup = kind }, 0f)
        }
        assertEquals("Injected pickups cannot grant rewards either", 0, read<Int>(game, "boxesRun"))
    }

    @Test fun zenBlocksEveryOtherAchievementIncludingMuteToggles() = fixture(Skins.ZEN_ID) { game ->
        val before = Achievements.snapshot().associate { it.definition.id to it.value }
        for (definition in Achievements.tracked) {
            Progress.addMetric(definition.id, 1000000)
            Progress.bestMetric(definition.id, 1000000)
            Progress.markMetricBit(definition.id, 2)
        }
        repeat(1000) { Progress.recordMuteToggle() }
        Progress.recordRunProgress(10000, 100)
        Progress.recordCenteredScore(100)
        Progress.recordCoinlessScore(60)
        Progress.recordMissedBoxes(10)
        Progress.recordPowerup()
        game.session.setScore(5000)
        game.session.setCoins(10000)
        game.session.groundPounded()
        game.session.fullKitHeld()
        game.session.redPillSurvived()
        call(game, "hit", wall())
        for (state in Achievements.snapshot()) if (state.definition.id != "monk") {
            assertEquals(state.definition.id, before[state.definition.id], state.value)
            assertEquals("No other achievement is earned", 0, state.earnedTiers)
        }
        assertEquals(1, Progress.metric("monk"))
    }

    @Test fun otherCubesStillCrashAndCount() = fixture(0) { game ->
        assertFalse(Progress.zenRun)
        assertEquals(1, Progress.metric("regular"))
        call(game, "hit", wall())
        assertTrue(read<Boolean>(game, "dead"))
        Progress.recordZenStreak(100)
        assertEquals("Other cubes cannot earn Monk", 0, Progress.metric("monk"))
    }
}
