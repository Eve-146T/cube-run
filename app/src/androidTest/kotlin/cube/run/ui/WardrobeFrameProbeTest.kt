package cube.run.ui

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.R
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Wardrobe
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in pacing measurement on the actual GL thread, without screenshot/recording overhead. */
class WardrobeFrameProbeTest {
    @Test fun measureLiveCosmeticFrames() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("probeWardrobe") == "true")
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            SystemClock.sleep(1400)
            val navigation = ReviewNavigation(scenario)
            navigation.tap(R.string.cd_skins)
            SystemClock.sleep(700)
            for ((cat,id) in listOf(Wardrobe.CUBE to 0, Wardrobe.CUBE to 19, Wardrobe.BUBBLE to 4, Wardrobe.BUBBLE to 7)) {
                navigation.tapText(Wardrobe.label(cat))
                SystemClock.sleep(300)
                fun selected() = if (cat == Wardrobe.CUBE) Stage.previewSkin else Stage.previewBubble
                var steps = 0
                while (selected() != id && steps++ < Wardrobe.count(cat)) {
                    navigation.tap(R.string.cd_next)
                    SystemClock.sleep(260)
                }
                check(selected() == id) { "Preview not reached through normal browsing" }
                SystemClock.sleep(900)
                val done = CountDownLatch(1)
                val frames = ArrayList<Double>()
                var last = 0L
                val probe = object : Runnable {
                    override fun run() {
                        val now = System.nanoTime()
                        if(last > 0) frames.add((now-last)/1000000.0)
                        last = now
                        if(frames.size < 120) Gdx.app.postRunnable(this) else done.countDown()
                    }
                }
                Gdx.app.postRunnable(probe)
                assertTrue("Renderer keeps producing frames",done.await(12,TimeUnit.SECONDS))
                frames.sort()
                android.util.Log.i("WARDROBE_FRAMES","cat=$cat id=$id frames=${frames.size} p50=${frames[60]} p95=${frames[114]} max=${frames.last()}")
            }
            navigation.tap(R.string.cd_back)
            SystemClock.sleep(500)
        }
    }
}
