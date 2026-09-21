package cube.run.game

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Settings
import cube.run.data.Wardrobe
import cube.run.game.track.Coin
import cube.run.game.track.Pickup
import cube.run.game.track.Row
import cube.run.game.track.Track
import cube.run.ui.Hud
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Exercise equipped abilities through the real GL game's pickup and collision paths. */
class EquippedAbilitiesTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private inline fun <reified T> read(owner: Any, name: String): T = field(owner, name).get(owner) as T
    private fun call(game: CubeRun, name: String, vararg args: Any) {
        game.javaClass.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(game, *args)
    }
    private fun gl(action: (CubeRun) -> Unit) {
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        Gdx.app.postRunnable {
            try { action(Gdx.app.applicationListener as CubeRun) }
            catch (t: Throwable) { failure = t }
            finally { done.countDown() }
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
    private fun fixture(skin: Int, bubbleSkin: Int = 0, rich: Int = 0, dev: Boolean = false, action: (CubeRun) -> Unit) {
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val scores = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
        val saved = prefs.all; val savedScores = scores.all; val savedDev = Settings.devMode
        val savedBonus = Settings.testBonus; val savedBonusNow = Settings.testBonusNow
        val savedBoxes = Settings.testBoxes; val savedPill = Settings.testPillWorld
        try {
            Settings.setDevMode(dev)
            Settings.testBonus = -1; Settings.testBonusNow = -1
            Settings.testBoxes = 0; Settings.testPillWorld = false
            assertTrue(prefs.edit().clear().putBoolean("achievements_unlocked", true)
                .putInt("owned_skins", 0x1ffffff).putInt("owned_bubble_skins", 0x7ff)
                .putInt("skin", skin).putInt("bubble_skin", bubbleSkin).putInt("bubbles", 20)
                .putInt("perk_coinvalue", rich).commit())
            scores.edit().clear().commit(); Progress.init(context)
            val intent = Intent(context, GameActivity::class.java).putExtra(Hud.EXTRA_AUTOSTART, false)
            ActivityScenario.launch<GameActivity>(intent).use { scenario ->
                var ready = false
                val until = SystemClock.uptimeMillis() + 10000
                while (!ready && SystemClock.uptimeMillis() < until) {
                    scenario.onActivity {
                        val hud = field(it, "hud").get(it) as? Hud
                        ready = hud?.isAttachedToWindow == true && hud.alpha == 1f
                    }
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
            Stage.paused = false; Stage.mode = Stage.NONE
            restore(prefs, saved); restore(scores, savedScores)
            Settings.setDevMode(savedDev)
            Settings.testBonus = savedBonus; Settings.testBonusNow = savedBonusNow
            Settings.testBoxes = savedBoxes; Settings.testPillWorld = savedPill
            Progress.init(context)
        }
    }
    private fun coin(game: CubeRun) = call(game, "collectCoin", Coin(0f, .5f, 0f), 0f)
    private fun pickup(game: CubeRun, kind: Int) {
        val row = Row(0f, arrayListOf()).apply { pickup = kind }
        call(game, "collectPickup", row, 0f)
        assertEquals("Pickup is consumed", Pickup.NONE, row.pickup)
    }
    private class Draws(vararg values: Double) : Random() {
        private val draws = values.toList(); private var index = 0
        override fun nextBits(bitCount: Int): Int = error("Use nextDouble")
        override fun nextDouble(): Double = draws.getOrElse(index++) { .5 }
    }

    @Test fun newTrackShardsAreNotPowerups() = fixture(0) { game ->
        pickup(game, Pickup.SHARD_EMBER)
        pickup(game, Pickup.SHARD_FROST)
        pickup(game, Pickup.SHARD_VOID)
        assertEquals(0, Progress.totalPowerups)
        pickup(game, Pickup.MAGNET)
        assertEquals(1, Progress.totalPowerups)
    }

    @Test fun developerRunsUseTheEasierLotteryOdds() = fixture(1, dev = true) { game ->
        val lottery = read<Lottery>(game, "lottery")
        assertEquals(kotlin.math.ln1p(-Lottery.DEV_COIN_CHANCE), field(lottery, "logMiss").getDouble(lottery), 0.0)
    }

    @Test fun goldFractionsAccumulateAndEquippedAbilityStaysLockedForTheRun() = fixture(6) { game ->
        repeat(5) { coin(game) }
        assertEquals(6, read<Int>(game, "coinsRun"))
        Progress.equip(Wardrobe.CUBE, 0)
        repeat(5) { coin(game) }
        assertEquals("Wardrobe changes cannot alter a run already in progress", 12, read<Int>(game, "coinsRun"))
        assertEquals(12, Progress.achievementCoins)
    }

    @Test fun plasmaExtendsTrackPowerupsAndStacksWithPlasmaBubble() = fixture(5, bubbleSkin = 4) { game ->
        val powerups = read<PowerUps>(game, "powerUps")
        pickup(game, Pickup.MAGNET)
        assertEquals(Progress.MAGNET.duration(Progress.magnetLevel) * 1.25f, powerups.magnet.duration, .001f)
        pickup(game, Pickup.MULT)
        assertEquals(Progress.MULT.duration(Progress.multLevel) * 1.25f, powerups.mult.duration, .001f)
        pickup(game, Pickup.JET)
        assertEquals(Progress.JET.duration(Progress.jetLevel) * 1.25f, powerups.jet.duration, .001f)
        pickup(game, Pickup.RED_PILL)
        assertEquals(RedPill.DURATION * 1.25f, read<RedPill>(game, "redPill").timer.duration, .001f)
        val bubble = read<Bubble>(game, "bubble")
        assertEquals("Both equipped duration abilities multiply", Progress.BUBBLE.duration(Progress.bubbleLevel) * 1.25f * 1.3f, bubble.duration, .001f)
    }

    @Test fun bubblegumShortensBothCrashAndNaturalExpiryCooldowns() = fixture(16) { game ->
        val bubble = read<Bubble>(game, "bubble")
        bubble.activate(0f, .5f, quiet = true)
        bubble.pop(0f, .5f)
        assertEquals(3.5f, bubble.cooldownLeft, .001f)
        bubble.reset()
        bubble.activate(0f, .5f, quiet = true)
        assertTrue(bubble.update(bubble.duration + .01f, 0f, 0f, .5f))
        assertEquals(3.5f, bubble.cooldownLeft, .001f)
    }

    @Test fun coalPickupIsWorthlessButStillDisqualifiesCoinlessChallenge() = fixture(15) { game ->
        val art = read<Any>(game, "trackArt")
        assertTrue("Track renders lumps of coal", field(art, "coalCoins").getBoolean(art))
        coin(game)
        assertEquals(0, read<Int>(game, "coinsRun"))
        assertEquals(0, Progress.achievementCoins)
        game.session.setScore(60)
        assertEquals("Picking up coal is still picking up the transformed coin", 0, Progress.bestCoinlessScore)
    }

    @Test fun gamblerForfeitsOrdinaryLootAndRichCoinsGiveThreeTickets() = fixture(1, rich = 10) { game ->
        assertEquals(3f, Progress.coinValue, .001f)
        // The first geometric draw puts the win on ticket four. At 3x value,
        // pickup one loses and pickup two wins; a box then loses and one wins.
        field(game, "lottery").set(game, Lottery(Draws(.000035, .5, .5, 0.0)))
        coin(game)
        assertEquals(0, read<Int>(game, "coinsRun"))
        coin(game)
        assertEquals(Lottery.JACKPOT, read<Int>(game, "coinsRun"))
        pickup(game, Pickup.BOX)
        assertEquals(Lottery.JACKPOT, read<Int>(game, "coinsRun"))
        pickup(game, Pickup.BOX)
        assertEquals(2 * Lottery.JACKPOT, read<Int>(game, "coinsRun"))
        assertEquals("Lottery boxes never enter the ordinary opening queue", 0, read<Int>(game, "boxesRun"))
        assertEquals(2 * Lottery.JACKPOT, Progress.achievementCoins)
    }

    @Test fun missedBoxesCountOnlyAfterPassingAndOnlyOnceWhileAlive() = fixture(0) { game ->
        val track = read<Track>(game, "track")
        val box = Row(1f - Row.PICKUP_DZ, arrayListOf()).apply {
            pickup = Pickup.BOX; pickupX = 20f; scored = true
        }
        track.rows.add(box)
        call(game, "collide", 0f)
        assertEquals("Row passing is not enough; the box itself must pass", 0, Progress.maxRunMissedBoxes)
        box.z += .2f
        repeat(12) { call(game, "collide", 0f) }
        assertEquals("Repeated collision passes count one missed box once", 1, Progress.maxRunMissedBoxes)
        track.rows.add(Row(5f, arrayListOf()).apply { pickup = Pickup.BOX; pickupX = 20f; scored = true })
        field(game, "dead").setBoolean(game, true)
        call(game, "collide", 0f)
        assertEquals("Boxes behind the death animation do not count", 1, Progress.maxRunMissedBoxes)
    }

    @Test fun developerGamblerUsesTheSameJackpotAndNeverCollectsOrdinaryBoxes() = fixture(1, dev = true) { game ->
        field(game, "lottery").set(game, Lottery(Draws(0.0, .5)))
        val winningCoin = Coin(0f, .5f, 0f)
        repeat(2) { call(game, "collectCoin", winningCoin, 0f) }
        assertEquals("A consumed winning coin pays exactly once", Lottery.JACKPOT, read<Int>(game, "coinsRun"))
        val initialBoxes = read<Int>(game, "boxesRun")
        pickup(game, Pickup.BOX)
        assertEquals(initialBoxes, read<Int>(game, "boxesRun"))
        assertEquals(Lottery.JACKPOT, Progress.achievementCoins)
    }
}
