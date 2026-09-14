package cube.run.game

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.GameActivity
import cube.run.bot.LiveBotDriver.Companion.gl
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Settings
import cube.run.game.track.Track
import org.junit.Assert.*
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in, actual idle pilot, normal collision path. Any contact fails, including shield saves. */
class IdlePilotSoakTest {
    private val originalDev = Settings.devMode
    private val originalSection = Settings.testSection
    private val originalBonus = Settings.testBonusNow
    private val originalPillWorld = Settings.testPillWorld
    private val originalRevives = Progress.revives
    private val originalSkin = Progress.skin
    @After fun restore() {
        Stage.userInteraction(); Stage.paused = false
        Settings.setDevMode(originalDev); Settings.testSection = originalSection
        Settings.testBonusNow = originalBonus; Settings.testPillWorld = originalPillWorld
        field(Progress, "revives").setInt(Progress, originalRevives)
        field(Progress, "skin").setInt(Progress, originalSkin)
    }
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST") private fun <T> value(owner: Any, name: String): T = field(owner, name).get(owner) as T

    @Test fun surviveRealCourse() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("soak") == "true")
        val seconds = args.getString("seconds")?.toInt() ?: 60
        val fast = args.getString("fast") == "true"
        val speedy = args.getString("speedy") == "true"
        field(Progress, "skin").setInt(Progress, if (speedy) 23 else 0)
        val out = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "idle-pilot-soak.csv")
        val intent = Intent(ApplicationProvider.getApplicationContext(), GameActivity::class.java)
            .putExtra("dev", true).putExtra("section", args.getString("section")?.toInt() ?: -1)
            .putExtra("bonusnow", args.getString("bonus")?.toInt() ?: -1).putExtra("pillworld", false)
            .putExtra("autostart", true).putExtra("idle_bot", true)
        ActivityScenario.launch<GameActivity>(intent).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            SystemClock.sleep(1500)
            gl { game ->
                Stage.paused = false
                val pilot: IdlePilot = value(game, "idlePilot")
                if (!pilot.active) pilot.start(game.time)
                if (fast) value<Difficulty>(game,"difficulty").boostTo(1f)
                // Disable stored lives only in this test. No protected observer is installed.
                field(Progress,"revives").setInt(Progress, 0)
                value<Bubble>(game,"bubble").reset()
            }
            val deadline = SystemClock.uptimeMillis()+seconds*1000L
            var failure = ""
            out.bufferedWriter().use { log ->
                log.appendLine("time,speed,body,contacts,dead,pilot,nearby")
                while (SystemClock.uptimeMillis() < deadline && failure.isEmpty()) {
                    gl { game ->
                        val player: Player = value(game,"player")
                        val track: Track = value(game,"track")
                        val pilot: IdlePilot = value(game,"idlePilot")
                        val dead: Boolean = value(game,"dead")
                        val body = player.pilotBody(value<PowerUps>(game,"powerUps").jet.left)
                            .apply { landingGrace = value(game,"jetGrace") }
                        val nearby = track.rows.filter { it.z in -30f..3f }.joinToString(";") { row ->
                            "${row.z}:"+row.obs.joinToString("|") { "${it.type}/${it.anim}@${it.x}/${it.cy}/${it.sy}/${it.halfW}" }
                        }
                        log.appendLine("${game.time},${value<Float>(game,"spd")},$body,${game.pilotContacts},$dead,${value<Any?>(pilot,"decision")},$nearby")
                        if (dead || game.pilotContacts > 0 || !pilot.active) {
                            failure = "time=${game.time} speed=${value<Float>(game,"spd")} contacts=${game.pilotContacts} dead=$dead active=${pilot.active} body=$body nearby=$nearby"
                            Stage.userInteraction()
                        }
                    }
                    log.flush()
                    SystemClock.sleep(80)
                }
            }
            File(out.parentFile,"idle-pilot-soak-result.txt").writeText(if (failure.isEmpty()) "PASS seconds=$seconds fast=$fast speedy=$speedy" else "FAIL $failure")
            assertTrue(failure, failure.isEmpty())
        }
    }
}
