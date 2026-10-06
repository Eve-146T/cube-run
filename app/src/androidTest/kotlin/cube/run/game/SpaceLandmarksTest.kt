package cube.run.game

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.game.space.SpaceLandmarks
import cube.run.game.space.SpaceTrip
import cube.run.game.space.SpaceWorld
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Outer Space's landmarks never crowd each other on screen, and a visit still shows most of its journey. */
@RunWith(AndroidJUnit4::class)
class SpaceLandmarksTest {
    @Test fun landmarksNeverOverlapAndMostStillShow() {
        ActivityScenario.launch(GameActivity::class.java).use {
            val done = CountDownLatch(1)
            var failure: Throwable? = null
            Gdx.app.postRunnable {
                try {
                    val game = Gdx.app.applicationListener as CubeRun
                    var shown = 0; var skipped = 0
                    for (seed in 1..30) {
                        val landmarks = SpaceLandmarks(game, SpaceWorld(game))
                        landmarks.begin(SpaceTrip(seed))
                        var travelled = 0f
                        while (travelled < 700f) { // one visit, at a typical 22 units/s and 60 fps
                            val mv = 22f / 60f
                            travelled += mv
                            landmarks.tick(mv, travelled)
                            assertFalse("seed $seed: landmarks overlap at $travelled", landmarks.overlapping())
                        }
                        shown += landmarks.shown; skipped += landmarks.skipped
                    }
                    Log.i("SPACE", "landmarks: $shown shown, $skipped skipped in 30 visits")
                    assertTrue("a visit must still pass plenty of things: $shown shown", shown >= 30 * 5)
                    assertTrue("few may be skipped: $skipped of ${shown + skipped}", skipped * 4 <= shown)
                } catch (t: Throwable) { failure = t } finally { done.countDown() }
            }
            assertTrue(done.await(120, TimeUnit.SECONDS))
            failure?.let { throw it }
        }
    }
}
