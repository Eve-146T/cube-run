package cube.run.game.track

import androidx.test.ext.junit.runners.AndroidJUnit4
import cube.run.data.Settings
import cube.run.game.Lanes
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/** A jetpack landing never leaves a wall only a pad can clear without its pad (pads and walls go together). */
@RunWith(AndroidJUnit4::class)
class LaunchPadTest {
    private var previousSection = -1
    private var previousDev = false

    @Before fun setup() {
        previousSection = Settings.testSection; previousDev = Settings.devMode
        Settings.setDevMode(false); Lanes.reset()
    }

    @After fun restore() {
        Settings.testSection = previousSection; Settings.setDevMode(previousDev)
        Lanes.reset()
    }

    /** Every tall wall still standing has a pad on the row right before it. */
    private fun assertPadded(track: Track, label: String) {
        val near = track.rows.sortedByDescending { it.z }
        for ((i, row) in near.withIndex()) {
            if (i == 0 || row.z > -2f || !row.needsLaunch || row.obs.none { it.type == ObType.SOLID }) continue
            assertTrue("$label: a tall wall at ${row.z} lost its pad", near[i - 1].obs.any { it.type == ObType.PAD })
        }
    }

    @Test fun landingsNeverStrandATallWall() {
        for (section in listOf(65, 57, 70)) { // TRAMPOLINE, SPRINGBOARD, BIG AIR
            Settings.testSection = section
            for (start in 0 until 200 step 3) {
                Lanes.reset()
                val random = Random(start)
                val track = Track(random, ObstacleFactory(random)).apply { reset(0.2f, 205f) }
                var t = 0f
                repeat(1200) { frame ->
                    if (frame == start) { // a flight takes off: its landing lies far ahead, past the newest rows
                        track.landing = LandingStrip(-112f - (start % 7) * 3f, 30f, 14f)
                        track.clearStrip()
                    }
                    track.spawn(0.25f, 205f, 1000)
                    t += 1f / 60f
                    track.scroll(0.25f, t, 1f / 60f)
                    assertPadded(track, "section $section, flight at frame $start, frame $frame")
                }
            }
        }
    }
}
