package cube.run.core

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import cube.run.GameActivity
import cube.run.bot.value
import cube.run.data.Settings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class SoundFxThreadingTest {
    @Test fun realSoundPoolPlaybackRunsOnWorkerAndHonorsMute() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val deadline = SystemClock.uptimeMillis() + 10000
            while (!value<Boolean>(SoundFx, "ready") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20)
            assertTrue(value<Boolean>(SoundFx, "ready"))
            val oldSound = Settings.soundEnabled
            val played = CountDownLatch(1)
            var threadName: String? = null
            var stream = 0
            var humStream = 0
            try {
                SoundFx.testObserver = { name, _, _, id ->
                    if (name == "bell") {
                        threadName = Thread.currentThread().name
                        stream = id
                        played.countDown()
                    }
                }
                Settings.setSoundEnabled(false)
                SoundFx.play("bell")
                assertEquals("Muted loop played", 0, SoundFx.loop("hum", .1f))
                assertFalse("Muted effect played", played.await(200, TimeUnit.MILLISECONDS))
                Settings.setSoundEnabled(true)
                SoundFx.play("bell", rate = 1.3f, vol = .5f)
                assertTrue("Effect never played", played.await(5, TimeUnit.SECONDS))
                assertEquals("sfx-play", threadName)
                assertTrue("SoundPool rejected effect", stream > 0)
                humStream = SoundFx.loop("hum", .1f)
                assertTrue("SoundPool rejected space loop", humStream > 0)
                SoundFx.setVolume(humStream, .05f)
            } finally {
                SoundFx.stop(humStream)
                SoundFx.testObserver = null
                Settings.setSoundEnabled(oldSound)
            }
        }
    }
}
