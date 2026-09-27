package cube.run.game

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.bot.value
import cube.run.core.Stage
import cube.run.data.*
import cube.run.ui.Hud
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class MintAbilitiesTest {
    @Test fun mintLoadoutsGateJumpsDurationAndSecretDisclosure() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<GameActivity>(Intent(context, GameActivity::class.java)
            .putExtra(Hud.EXTRA_AUTOSTART, false)).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val done = CountDownLatch(1); var failure: Throwable? = null
            Gdx.app.postRunnable {
                val oldCube = Progress.skin; val oldBubble = Progress.bubbleSkin
                val sound = Settings.soundEnabled; val haptics = Settings.hapticsEnabled
                try {
                    Settings.setSoundEnabled(false); Settings.setHapticsEnabled(false)
                    val grant = Progress::class.java.getDeclaredMethod("grant", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        .apply { isAccessible = true }
                    grant.invoke(Progress, Wardrobe.CUBE, Skins.MINT_ID)
                    grant.invoke(Progress, Wardrobe.BUBBLE, BubbleSkins.MINT_ID)
                    val game = Gdx.app.applicationListener as CubeRun
                    game.finishOpening(); Stage.paused = false; game.onTap(.5f, .5f)
                    val p: Player = value(game, "player"); val b: Bubble = value(game, "bubble")
                    val refresh = CubeRun::class.java.getDeclaredMethod("refreshJumpAbility").apply { isAccessible = true }
                    val duration = CubeRun::class.java.getDeclaredMethod("bubbleDurationMultiplier").apply { isAccessible = true }
                    fun loadout(cube: Int, bubble: Int, double: Boolean, triple: Boolean, multiplier: Float) {
                        Progress.equip(Wardrobe.CUBE, cube); Progress.equip(Wardrobe.BUBBLE, bubble)
                        game.javaClass.getDeclaredField("runSkin").apply { isAccessible = true }.set(game, Skins.get(cube))
                        game.javaClass.getDeclaredField("runBubble").apply { isAccessible = true }.set(game, BubbleSkins.get(bubble))
                        b.reset(); b.activate(0f, .45f, quiet = true); refresh.invoke(game)
                        assertEquals(double, p.doubleJumpEnabled); assertEquals(triple, p.tripleJumpEnabled)
                        assertEquals(multiplier, duration.invoke(game) as Float, .001f)
                        for (cat in listOf(Wardrobe.CUBE, Wardrobe.BUBBLE)) {
                            val id = if (cat == Wardrobe.CUBE) Skins.MINT_ID else BubbleSkins.MINT_ID
                            assertEquals(triple, Skins.Ability.MIND_SYNERGY in Wardrobe.abilities(cat, id))
                        }
                        assertFalse(Skins.Ability.MIND_SYNERGY in Wardrobe.abilities(Wardrobe.CUBE, 0))
                        b.pop(0f, .45f); refresh.invoke(game)
                        assertFalse(p.doubleJumpEnabled); assertFalse(p.tripleJumpEnabled)
                    }
                    loadout(0, 0, false, false, 1f)
                    loadout(Skins.MINT_ID, 0, true, false, 1f)
                    loadout(0, BubbleSkins.MINT_ID, true, false, 1f)
                    loadout(Skins.MINT_ID, BubbleSkins.MINT_ID, true, true, 1.3f)
                } catch (t: Throwable) { failure = t }
                finally {
                    Progress.equip(Wardrobe.CUBE, oldCube); Progress.equip(Wardrobe.BUBBLE, oldBubble)
                    Settings.setSoundEnabled(sound); Settings.setHapticsEnabled(haptics)
                    done.countDown()
                }
            }
            assertTrue(done.await(25, TimeUnit.SECONDS)); failure?.let { throw it }
        }
    }
}
