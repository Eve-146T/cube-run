package cube.run.game

import androidx.test.core.app.ActivityScenario
import android.os.SystemClock
import cube.run.GameActivity
import cube.run.bot.LiveBotDriver
import cube.run.bot.value
import cube.run.core.SoundFx
import cube.run.data.Settings
import org.junit.Assert.*
import org.junit.Test

class TickRemovalTest {
    @Test fun ticksAreAbsentInBothModesAndOtherSoundsStillPlay() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val deadline = SystemClock.uptimeMillis() + 10000
            while (!value<Boolean>(SoundFx, "ready") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20)
            assertTrue(value<Boolean>(SoundFx, "ready"))
            assertFalse(value<Map<String, Int>>(SoundFx, "ids").containsKey("tick"))
            LiveBotDriver.gl { game ->
                val sound = Settings.soundEnabled; val dev = Settings.devMode
                val events = java.util.concurrent.ConcurrentLinkedQueue<Pair<String, Int>>()
                try {
                    Settings.setSoundEnabled(true)
                    val fx: RunFx = value(game, "fx")
                    for (enabled in listOf(false, true)) {
                        Settings.setDevMode(enabled); events.clear()
                        val played = java.util.concurrent.CountDownLatch(4)
                        SoundFx.testObserver = { name, _, _, stream -> events.add(name to stream); played.countDown() }
                        fx.rowPassed(); SoundFx.play("tick")
                        assertTrue(events.isEmpty())
                        for (name in listOf("whoosh", "coin", "pop", "slide")) SoundFx.play(name)
                        assertTrue("Queued effects did not play", played.await(5, java.util.concurrent.TimeUnit.SECONDS))
                        assertEquals(listOf("whoosh", "coin", "pop", "slide"), events.map { it.first })
                        assertTrue(events.all { it.second > 0 })
                    }
                } finally { SoundFx.testObserver = null; Settings.setSoundEnabled(sound); Settings.setDevMode(dev) }
            }
        }
    }
}
