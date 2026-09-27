package cube.run.game

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.bot.value
import cube.run.core.Stage
import cube.run.data.Skins
import cube.run.game.track.Row
import cube.run.ui.Hud
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class StrobeAbilitiesTest {
    @Test fun strobeTriplesOnlyNearMissBonusAndStacksWithScoreMultiplier() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<GameActivity>(Intent(context, GameActivity::class.java)
            .putExtra(Hud.EXTRA_AUTOSTART, false)).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val done = CountDownLatch(1); var failure: Throwable? = null
            Gdx.app.postRunnable {
                try {
                    val game = Gdx.app.applicationListener as CubeRun
                    game.finishOpening(); Stage.paused = false; game.onTap(.5f, .5f)
                    val skin = CubeRun::class.java.getDeclaredField("runSkin").apply { isAccessible = true }
                    val score = CubeRun::class.java.getDeclaredMethod("scoreRow", Row::class.java).apply { isAccessible = true }
                    val power: PowerUps = value(game, "powerUps")
                    assertEquals(listOf(Skins.Ability.CLOSE_SHAVE), Skins.get(14).abilities)
                    fun check(id: Int, doubled: Boolean, clearance: Float, expected: Int) {
                        skin.set(game, Skins.get(id)); power.mult.stop()
                        if (doubled) power.mult.start(10f)
                        val before = game.session.score
                        score.invoke(game, Row(-3f, arrayListOf()).apply { minClear = clearance })
                        assertEquals("Skin $id, multiplier $doubled, clearance $clearance", expected, game.session.score - before)
                    }
                    check(0, false, .1f, 3)
                    check(14, false, .1f, 7)
                    check(0, true, .1f, 6)
                    check(14, true, .1f, 14)
                    check(14, false, .34f, 1)
                    check(14, true, .8f, 2)
                } catch (t: Throwable) { failure = t }
                finally { done.countDown() }
            }
            assertTrue(done.await(25, TimeUnit.SECONDS)); failure?.let { throw it }
        }
    }
}
