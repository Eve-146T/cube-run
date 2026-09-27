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

class CoalAlchemyTest {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }

    @Test fun coalConvertsMonotonicallyPaysThreeAndRevealsPermanently() {
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
                        val prepare = CubeRun::class.java.getDeclaredMethod("prepareCoalGems").apply { isAccessible = true }
                        val collect = CubeRun::class.java.getDeclaredMethod("collectCoin", Coin::class.java, Float::class.javaPrimitiveType).apply { isAccessible = true }
                        fun reset(skin: Int = 15, rich: Int = 0) {
                            prefs.edit().putInt("owned_skins", Progress.ownedSkins or (1 shl 15) or 1)
                                .putInt("skin", skin).putInt("perk_coinvalue", rich).putInt("revives", 3).commit()
                            Progress.init(context)
                            (game.session as GameHostSession).resetToMenu(); game.resetToMenu()
                            Stage.paused = false; game.onTap(.5f, .5f)
                            track.rows.clear(); player.forceGround(0f); bubble.reset()
                        }
                        prefs.edit().putBoolean("coal_alchemy_revealed", false).commit()
                        reset(skin = 0)
                        game.session.setScore(3000)
                        assertFalse(Progress.coalAlchemyRevealed)
                        reset()
                        assertFalse(Wardrobe.abilities(Wardrobe.CUBE, 15).contains(Skins.Ability.COAL_ALCHEMY))
                        val sample = ArrayList<Coin>().apply { repeat(10000) { add(Coin(0f, .5f, 0f)) } }
                        track.rows.add(Row(-50f, arrayListOf()).apply { coins = sample })
                        field(game, "coalRandom").set(game, Random(927))
                        game.session.setScore(999); prepare.invoke(game)
                        assertTrue(sample.none { it.gem }); assertFalse(Progress.coalAlchemyRevealed)
                        game.session.setScore(1000); prepare.invoke(game)
                        assertTrue(sample.count { it.gem } in 50..150)
                        assertTrue(Progress.coalAlchemyRevealed)
                        Progress.init(context)
                        assertTrue("Discovery survives reloading progress", Progress.coalAlchemyRevealed)
                        assertTrue(Wardrobe.abilities(Wardrobe.CUBE, 15).contains(Skins.Ability.COAL_ALCHEMY))
                        val first = sample.filter { it.gem }
                        game.session.setScore(2000); prepare.invoke(game)
                        assertTrue(sample.count { it.gem } in 4800..5300)
                        assertTrue(first.all { it.gem })
                        val flags = sample.map { it.gem }; prepare.invoke(game)
                        assertEquals(flags, sample.map { it.gem })
                        game.session.setScore(3000); prepare.invoke(game)
                        assertTrue(sample.all { it.gem })
                        reset(rich = 10); field(game, "bonus").setInt(game, Bonus.KALEIDO)
                        val gem = Coin(0f, .5f, 0f).apply { this.gem = true }
                        val coalBefore = Progress.metric("coal_miner")
                        collect.invoke(game, gem, 0f); collect.invoke(game, gem, 0f)
                        assertEquals("Exactly three, even with perks, and only once", 3, field(game, "coinsRun").getInt(game))
                        assertEquals(coalBefore, Progress.metric("coal_miner"))
                        collect.invoke(game, Coin(0f, .5f, 0f), 0f)
                        assertEquals(3, field(game, "coinsRun").getInt(game))
                        assertEquals(coalBefore + 1, Progress.metric("coal_miner"))
                        game.session.setScore(3000)
                        val newCoal = Coin(0f, .5f, 0f)
                        track.rows.add(Row(-50f, arrayListOf()).apply { coins = arrayListOf(newCoal) })
                        prepare.invoke(game); assertTrue("New rows are also all gems", newCoal.gem)
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
