package cube.run.game

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.bot.value
import cube.run.core.GameHostSession
import cube.run.core.Stage
import cube.run.data.*
import cube.run.game.track.*
import cube.run.ui.Hud
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class ToxicAbilitiesTest {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }

    @Test fun toxicBoostsValueMarksStableGreenCoinsAndKillsThroughProtection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val saved = prefs.all; val dev = Settings.devMode
        val sound = Settings.soundEnabled; val haptics = Settings.hapticsEnabled
        try {
            Settings.setDevMode(false); Settings.setSoundEnabled(false); Settings.setHapticsEnabled(false)
            ActivityScenario.launch<GameActivity>(Intent(context, GameActivity::class.java)
                .putExtra(Hud.EXTRA_AUTOSTART, false)).use { scenario ->
                scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
                val done = CountDownLatch(1); var failure: Throwable? = null
                Gdx.app.postRunnable {
                    try {
                        val game = Gdx.app.applicationListener as CubeRun
                        val track: Track = value(game, "track")
                        val player: Player = value(game, "player")
                        val bubble: Bubble = value(game, "bubble")
                        val powers: PowerUps = value(game, "powerUps")
                        val prepare = CubeRun::class.java.getDeclaredMethod("prepareToxicCoins").apply { isAccessible = true }
                        val collect = CubeRun::class.java.getDeclaredMethod("collectCoin", Coin::class.java, Float::class.javaPrimitiveType).apply { isAccessible = true }
                        val collide = CubeRun::class.java.getDeclaredMethod("collide", Float::class.javaPrimitiveType).apply { isAccessible = true }
                        fun reset(skin: Int = 11, rich: Int = 0) {
                            prefs.edit().putInt("owned_skins", Progress.ownedSkins or (1 shl 11) or 1)
                                .putInt("skin", skin).putInt("perk_coinvalue", rich).putInt("revives", 3).commit()
                            Progress.init(context)
                            (game.session as GameHostSession).resetToMenu(); game.resetToMenu()
                            Stage.paused = false; game.onTap(.5f, .5f)
                            track.rows.clear(); player.forceGround(0f); bubble.reset()
                        }
                        reset()
                        assertEquals(listOf(Skins.Ability.TOXIC_FORTUNE), Skins.get(11).abilities)
                        assertEquals(1.6f, Skins.get(11).coinMultiplier, 0f)
                        repeat(10) { collect.invoke(game, Coin(0f, .5f, 0f), 0f) }
                        assertEquals("Fractional bonuses accumulate exactly", 16, field(game, "coinsRun").getInt(game))
                        reset(rich = 10); field(game, "bonus").setInt(game, Bonus.KALEIDO)
                        repeat(10) { collect.invoke(game, Coin(0f, .5f, 0f), 0f) }
                        assertEquals("Stacks with Rich Coins and Kaleidoscope", 96, field(game, "coinsRun").getInt(game))
                        reset(); field(game, "toxicRandom").set(game, Random(927))
                        val sample = ArrayList<Coin>(100000).apply { repeat(100000) { add(Coin(0f, .5f, 0f)) } }
                        track.rows.add(Row(-50f, arrayListOf()).apply { coins = sample })
                        prepare.invoke(game)
                        assertTrue(sample.all { it.toxicAssigned })
                        val greens = sample.count { it.toxic }
                        assertTrue("1% chance over 100000 coins; got $greens", greens in 900..1100)
                        val flags = sample.map { it.toxic }; repeat(3) { prepare.invoke(game) }
                        assertEquals("Visible poison never rerolls", flags, sample.map { it.toxic })
                        track.rows.clear()
                        field(game, "toxicRandom").set(game, object : Random() {
                            override fun nextBits(bitCount: Int) = 0
                            var roll = 0
                            override fun nextFloat() = if (roll++ == 0) .009999f else .01f
                        })
                        val boundary = arrayListOf(Coin(0f, .5f, 0f), Coin(0f, .5f, 0f))
                        track.rows.add(Row(-50f, arrayListOf()).apply { coins = boundary })
                        prepare.invoke(game); assertTrue(boundary[0].toxic); assertFalse(boundary[1].toxic)
                        track.rows.clear()
                        repeat(10) { collect.invoke(game, Coin(0f, .5f, 0f), 0f) }
                        bubble.activate(0f, player.py, quiet = true); powers.magnet.start(10f)
                        val poison = Coin(0f, player.py, 0f).apply { toxic = true; toxicAssigned = true }
                        val afterPoison = Coin(0f, player.py, 0f)
                        track.rows.add(Row(0f, arrayListOf()).apply { coins = arrayListOf(poison, afterPoison) })
                        val revives = Progress.revives
                        val greed = Progress.metric("greed")
                        collide.invoke(game, .016f)
                        assertTrue("Green pickup kills immediately with a bubble and magnet", field(game, "dead").getBoolean(game))
                        assertTrue(poison.taken); assertFalse("No pickups after death", afterPoison.taken)
                        assertEquals("Revive is not consumed or allowed to save poison", revives, Progress.revives)
                        assertEquals("Poison pays nothing; earlier earnings stay", 16, field(game, "coinsRun").getInt(game))
                        assertEquals("Poison death is not a risky-box death", greed, Progress.metric("greed"))
                        reset(skin = 0)
                        val safe = Coin(0f, .5f, 0f)
                        track.rows.add(Row(-50f, arrayListOf()).apply { coins = arrayListOf(safe) })
                        prepare.invoke(game); assertFalse(safe.toxic); assertFalse(safe.toxicAssigned)
                        collect.invoke(game, safe, 0f); assertEquals(1, field(game, "coinsRun").getInt(game))
                    } catch (t: Throwable) { failure = t }
                    finally { done.countDown() }
                }
                assertTrue(done.await(35, TimeUnit.SECONDS)); failure?.let { throw it }
            }
        } finally {
            val edit = prefs.edit().clear()
            saved.forEach { (key, v) -> when (v) {
                is Int -> edit.putInt(key, v)
                is Long -> edit.putLong(key, v)
                is Float -> edit.putFloat(key, v)
                is Boolean -> edit.putBoolean(key, v)
                is String -> edit.putString(key, v)
                is Set<*> -> edit.putStringSet(key, v.filterIsInstance<String>().toSet())
            } }
            edit.commit(); Progress.init(context); Settings.setDevMode(dev)
            Settings.setSoundEnabled(sound); Settings.setHapticsEnabled(haptics)
        }
    }
}
