package cube.run.bot

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.game.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Unprotected demo mode: real collisions, no teleporting, obstacle deletion or stock use. */
@RunWith(AndroidJUnit4::class)
class BotPlayTest {
    @Test fun playAndCollect() {
        val args = InstrumentationRegistry.getArguments()
        val seconds = (args.getString("seconds")?.toInt() ?: 60).coerceIn(5, 600)
        val boosts = (args.getString("boosts")?.toInt() ?: 0).coerceIn(0, 10)
        val intent = Intent(ApplicationProvider.getApplicationContext(), GameActivity::class.java)
            .putExtra("dev", boosts > 5).putExtra("section", args.getString("section")?.toInt() ?: -1)
            .putExtra("world", args.getString("world")?.toInt() ?: -1)
        ActivityScenario.launch<GameActivity>(intent).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            SystemClock.sleep(800)
            val oldRevives = Progress.revives
            field(Progress::class.java, "revives").setInt(Progress, 0)
            val file = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "bot-play.csv")
            try {
                LiveBotDriver.gl { game ->
                    Stage.paused = false; game.onDown(360f, 760f)
                    value<Bubble>(game, "bubble").timer.stop()
                    Stage.boostRequests.set(boosts)
                    if (args.getString("magnet") == "true") value<PowerUps>(game, "powerUps").magnet.start(20f)
                }
                LiveBotDriver().use { bot ->
                    val end = SystemClock.uptimeMillis() + seconds * 1000L
                    file.bufferedWriter().use { writer -> bot.drive({ SystemClock.uptimeMillis() < end }, writer) }
                    val summary = bot.summary()
                    Log.i("BOT_PLAY", summary); File(file.parentFile, "bot-play-summary.txt").writeText(summary)
                    if (args.getString("requireSurvival") == "true") assertTrue("Bot died: $summary", bot.alive)
                }
            } finally { field(Progress::class.java, "revives").setInt(Progress, oldRevives) }
        }
    }
}
