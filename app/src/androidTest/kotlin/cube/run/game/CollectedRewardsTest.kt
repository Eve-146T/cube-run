package cube.run.game

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.GameActivity
import cube.run.bot.LiveBotDriver.Companion.gl
import cube.run.core.GameHostSession
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Settings
import cube.run.data.Skins
import cube.run.game.track.Coin
import cube.run.game.track.Pickup
import cube.run.game.track.Row
import cube.run.ui.Hud
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CollectedRewardsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun call(game: CubeRun, name: String, vararg args: Any) {
        game.javaClass.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(game, *args)
    }
    @Before fun prepare() {
        Settings.setDevMode(false); Settings.testPillWorld = false; Settings.testSection = -1
        Progress.zenRun = false; Progress.clearRunCoins()
    }
    @After fun cleanup() { Progress.zenRun = false; Stage.paused = false }

    private fun session(action: (GameHostSession) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            action(GameHostSession(Activity(), "cuberun").also { it.runStarted() })
        }
    }

    @Test fun collectedCoinsAndShardsBankOnceBeforeTheRunEnds() {
        val coins = Progress.coins; val total = Progress.totalCoins
        val shards = IntArray(3) { Progress.shards(it) }
        session { run ->
            run.setCoins(123); run.setCoins(123); run.setCoins(100); run.setCoins(150)
            run.addShard(0); run.addShard(1); run.addShard(1); run.addShard(2)
            run.addShard(-1); run.addShard(3)
            assertEquals(coins + 150, Progress.coins)
            assertEquals(total + 150, Progress.totalCoins)
            assertEquals(Progress.totalCoins, Progress.achievementCoins)
            assertArrayEquals(intArrayOf(shards[0]+1, shards[1]+2, shards[2]+1), IntArray(3) { Progress.shards(it) })
            run.gameOver(); run.gameOver(); run.setCoins(999); run.addShard(0)
            assertEquals(coins + 150, Progress.coins)
            assertArrayEquals(intArrayOf(shards[0]+1, shards[1]+2, shards[2]+1), IntArray(3) { Progress.shards(it) })
        }
        assertTrue(Progress.flush()); Progress.init(context)
        assertEquals(coins + 150, Progress.coins)
        assertEquals(shards[1]+2, Progress.shards(1))
    }

    @Test fun menuAndRestartKeepCollectedRewardsAndStartANewTotal() {
        val coins = Progress.coins; val shards = Progress.shards(0)
        session { run ->
            run.setCoins(37); run.addShard(0)
            run.resetToMenu()
            assertEquals(coins + 37, Progress.coins)
            assertEquals(shards + 1, Progress.shards(0))
            run.runStarted(); run.setCoins(12); run.gameOver()
            assertEquals(coins + 49, Progress.coins)
        }
        assertTrue(Progress.flush()); Progress.init(context)
        assertEquals(coins + 49, Progress.coins)
    }

    @Test fun zenStillKeepsNoCoinsOrShards() {
        val coins = Progress.coins; val total = Progress.totalCoins; val shards = Progress.shards(0)
        session { run ->
            Progress.zenRun = true
            run.setCoins(100); run.bankRunCoins(1000); run.addShard(0); run.gameOver()
            assertEquals(coins, Progress.coins); assertEquals(total, Progress.totalCoins)
            assertEquals(shards, Progress.shards(0))
        }
    }

    @Test fun jackpotAndBubbleRewardsSurviveLeavingBeforeTheShowFinishes() {
        var expectedCoins = 0; var expectedBubbles = 0
        ActivityScenario.launch<GameActivity>(Intent(context, GameActivity::class.java)
            .putExtra(Hud.EXTRA_AUTOSTART, true)).use {
            val until = SystemClock.uptimeMillis() + 10000
            var started = false
            while (!started && SystemClock.uptimeMillis() < until) {
                gl { game -> started = field(game, "started").getBoolean(game) }
                if (!started) SystemClock.sleep(30)
            }
            assertTrue(started)
            gl { game ->
                Stage.paused = true
                field(game, "runSkin").set(game, Skins.all.first { Skins.Ability.BUBBLE_DIVIDEND in it.abilities })
                val bubbles = Progress.bubbles
                val row = Row(0f, arrayListOf()).apply { pickup = Pickup.BUBBLE }
                call(game, "collectPickup", row, 0f); call(game, "collectPickup", row, 0f)
                assertEquals("Ocean's doubled pickup pays once", bubbles + 2, Progress.bubbles)
                field(game, "coinsRun").setInt(game, 199)
                field(game, "coinsRunF").setDouble(game, 199.0)
                game.session.setCoins(199)
                call(game, "collectCoin", Coin(0f, 1f, 0f), 0f)
                assertTrue("Coin milestones add saved bubble stock", Progress.bubbles > bubbles + 2)
                assertTrue(Progress.useBubble())
                expectedBubbles = Progress.bubbles
                val before = Progress.coins
                call(game, "awardJackpot", 5000)
                assertTrue((field(game, "jackpot").get(game) as Jackpot).active)
                expectedCoins = before + 5000
                assertEquals("The win is banked before the show advances", expectedCoins, Progress.coins)
                // The later HUD update must not pay the same jackpot again.
                game.session.setCoins(field(game, "coinsRun").getInt(game))
                assertEquals(expectedCoins, Progress.coins)
            }
        }
        Progress.init(context)
        assertEquals(expectedCoins, Progress.coins)
        assertEquals(expectedBubbles, Progress.bubbles)
    }
}
