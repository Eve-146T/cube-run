package cube.run.game

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.GameHostSession
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Settings
import cube.run.data.Skins
import cube.run.game.track.Coin
import cube.run.game.track.Pickup
import cube.run.game.track.Row
import cube.run.ui.Hud
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OceanAbilitiesTest {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }

    @Test fun oceanDoublesPickupsAndEarnsFreeBubblesPerRunWithoutSpendingCoins() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, GameActivity::class.java).putExtra(Hud.EXTRA_AUTOSTART, false)
        ActivityScenario.launch<GameActivity>(intent).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val done = CountDownLatch(1)
            var failure: Throwable? = null
            Gdx.app.postRunnable {
                val sound = Settings.soundEnabled; val haptics = Settings.hapticsEnabled
                val dev = Settings.devMode
                try {
                    Settings.setSoundEnabled(false); Settings.setHapticsEnabled(false); Settings.setDevMode(false)
                    val game = Gdx.app.applicationListener as CubeRun
                    game.finishOpening(); Stage.paused = false; game.onTap(.5f, .5f)
                    val ocean = Skins.get(10)
                    assertEquals(listOf(Skins.Ability.BUBBLE_HAUL, Skins.Ability.BUBBLE_DIVIDEND), ocean.abilities)
                    field(game, "runSkin").set(game, ocean)
                    val collect = CubeRun::class.java.getDeclaredMethod("collectCoin", Coin::class.java, Float::class.javaPrimitiveType)
                        .apply { isAccessible = true }
                    val pickup = CubeRun::class.java.getDeclaredMethod("collectPickup", Row::class.java, Float::class.javaPrimitiveType)
                        .apply { isAccessible = true }
                    val stock = Progress.bubbles; val bank = Progress.coins
                    val row = Row(-3f, arrayListOf()).apply { this.pickup = Pickup.BUBBLE }
                    pickup.invoke(game, row, -3f)
                    assertEquals(stock + 2, Progress.bubbles)
                    pickup.invoke(game, row, -3f)
                    assertEquals("The same pickup cannot pay twice", stock + 2, Progress.bubbles)
                    var lastCoin = Coin(0f, .8f, 0f)
                    repeat(kotlin.math.ceil(450.0 / Progress.coinValue).toInt()) {
                        lastCoin = Coin(0f, .8f, 0f)
                        collect.invoke(game, lastCoin, -3f)
                        val earned = field(game, "coinsRun").getInt(game) / 200
                        assertEquals("Reward only completed 200-coin milestones", stock + 2 + earned, Progress.bubbles)
                    }
                    val saved = Progress.bubbles
                    collect.invoke(game, lastCoin, -3f)
                    assertEquals("The same coin cannot pay twice", saved, Progress.bubbles)
                    assertEquals("Free bubbles never spend banked coins", bank, Progress.coins)
                    (game.session as GameHostSession).resetToMenu(); game.resetToMenu()
                    game.onTap(.5f, .5f); field(game, "runSkin").set(game, ocean)
                    collect.invoke(game, Coin(0f, .8f, 0f), -3f)
                    assertEquals("A new run starts a new 200-coin count", saved, Progress.bubbles)
                    field(game, "runSkin").set(game, Skins.get(0))
                    repeat(kotlin.math.ceil(450.0 / Progress.coinValue).toInt()) {
                        collect.invoke(game, Coin(0f, .8f, 0f), -3f)
                    }
                    assertEquals("Classic has no coin milestone ability", saved, Progress.bubbles)
                    pickup.invoke(game, Row(-3f, arrayListOf()).apply { this.pickup = Pickup.BUBBLE }, -3f)
                    assertEquals("Classic still gets one bubble", saved + 1, Progress.bubbles)
                    Progress.init(context)
                    assertEquals("Free bubbles are persisted in the stash", saved + 1, Progress.bubbles)
                } catch (t: Throwable) { failure = t }
                finally {
                    Settings.setSoundEnabled(sound); Settings.setHapticsEnabled(haptics); Settings.setDevMode(dev)
                    done.countDown()
                }
            }
            assertTrue(done.await(25, TimeUnit.SECONDS)); failure?.let { throw it }
        }
    }
}
