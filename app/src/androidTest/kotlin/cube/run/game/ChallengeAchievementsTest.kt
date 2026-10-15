package cube.run.game

import android.content.SharedPreferences
import com.badlogic.gdx.graphics.Color
import cube.run.core.Stage
import cube.run.data.Achievements
import cube.run.data.Progress
import cube.run.data.Skins
import cube.run.game.track.*
import org.junit.Assert.*
import org.junit.Test

/** Icarus, Poltergeist, Juggernaut and Full Throttle are earned the way the page says (#73). */
class ChallengeAchievementsTest {
    private fun cube(id: Int, extra: (SharedPreferences.Editor) -> Unit = {}): (SharedPreferences.Editor) -> Unit = {
        it.putInt("owned_skins", 1 or (1 shl id)).putInt("skin", id); extra(it)
    }
    /** A road-wide pillar, or a narrow one at [x] that the cube does not touch. */
    private fun pillar(x: Float? = null) = Ob(Color.RED, x ?: 0f, .31f, if (x == null) 3f else .7f, ObType.SOLID, 1.4f, .62f, .7f)
    private fun earned(id: String) = Achievements.snapshot(Achievements.all.single { it.id == id }).earnedTiers
    private fun method(name: String, vararg types: Class<*>) =
        CubeRun::class.java.getDeclaredMethod(name, *types).apply { isAccessible = true }

    @Test fun juggernautCountsEverySolidABubbleSmashesInOneRun() = playingRun(cube(0) { it.putInt("bubbles", 5) }) { run ->
        val bubble = run.read<Bubble>("bubble")
        bubble.activate(0f, .5f, quiet = true)
        run.rows(listOf(
            Row(-6f, arrayListOf(pillar(), pillar(-2f))),
            Row(-10f, arrayListOf(pillar(), Ob(Color.BLUE, 2f, .5f, .7f, ObType.PLAT, 1.4f, 1f, 2f))),
        ))
        run.frames(40)
        assertFalse("The bubble took the hit", run.read<Boolean>("dead"))
        assertEquals("Both solids in the row hit and the one in the breather zone; the platform is not an obstacle", 3, Progress.metric("juggernaut"))
        bubble.activate(0f, .5f, quiet = true)
        run.rows(listOf(Row(-6f, arrayListOf(pillar()))))
        run.frames(40)
        assertEquals("Smashes add up within a run", 4, Progress.metric("juggernaut"))
        run.restart()
        run.read<Bubble>("bubble").activate(0f, .5f, quiet = true)
        run.rows(listOf(Row(-6f, arrayListOf(pillar()))))
        run.frames(40)
        assertEquals("A new run starts from zero and keeps the best", 4, Progress.metric("juggernaut"))
        assertEquals(0, earned("juggernaut"))
        Progress.bestMetric("juggernaut", 300)
        assertEquals(1, earned("juggernaut"))
    }

    @Test fun aSecondWindSmashCountsTowardsJuggernaut() = playingRun(cube(0) { it.putInt("revives", 1) }) { run ->
        run.rows(listOf(Row(-6f, arrayListOf(pillar()))))
        run.frames(40)
        assertFalse(run.read<Boolean>("dead"))
        assertEquals(1, Progress.metric("juggernaut"))
    }

    @Test fun poltergeistCountsEachGhostPhaseAcrossRuns() = playingRun(cube(13)) { run ->
        run.rows(listOf(Row(-6f, arrayListOf(pillar()))))
        run.frames(40)
        assertFalse("The Ghost slipped through", run.read<Boolean>("dead"))
        assertEquals(1, Progress.metric("poltergeist"))
        run.rows(listOf(Row(-6f, arrayListOf(pillar()))))
        run.frames(40)
        assertTrue("One phase per run", run.read<Boolean>("dead"))
        assertEquals(1, Progress.metric("poltergeist"))
        run.restart()
        run.rows(listOf(Row(-6f, arrayListOf(pillar()))))
        run.frames(40)
        assertEquals("Lifetime count", 2, Progress.metric("poltergeist"))
        Progress.addMetric("poltergeist", 98)
        assertEquals(1, earned("poltergeist"))
    }

    @Test fun icarusNeedsAToxicDeathInFlight() = playingRun(cube(Skins.TOXIC_ID)) { run ->
        val collect = method("collectCoin", Coin::class.java, Float::class.javaPrimitiveType!!)
        val player = run.read<Player>("player")
        collect.invoke(run.game, Coin(0f, .5f, 0f).apply { toxic = true; toxicAssigned = true }, 0f)
        assertTrue(run.read<Boolean>("dead"))
        assertEquals("On the ground it does not count", 0, Progress.metric("icarus"))
        run.restart()
        player.setFlying(true)
        collect.invoke(run.game, Coin(0f, 3f, 0f).apply { toxic = true; toxicAssigned = true }, 0f)
        assertTrue(run.read<Boolean>("dead"))
        assertEquals(1, Progress.metric("icarus"))
        assertEquals(1, earned("icarus"))
    }

    @Test fun icarusIsOnlyForTheToxicCube() = playingRun(cube(3)) { run ->
        run.read<Player>("player").setFlying(true)
        method("die").invoke(run.game)
        assertTrue(run.read<Boolean>("dead"))
        assertEquals(0, Progress.metric("icarus"))
    }

    @Test fun fullThrottleNeedsAllTenBoosts() = playingRun({ it.putInt("perk_fasterstart", 5) }) { run ->
        Stage.boostRequests.set(9); run.frames(1)
        run.game.session.setScore(1600)
        assertEquals("Nine boosts are not enough", 0, Progress.metric("full_throttle"))
        Stage.boostRequests.set(1); run.frames(1)
        run.game.session.setScore(1480)
        assertEquals("Progress counts in steps of 50", 1450, Progress.metric("full_throttle"))
        assertEquals(0, earned("full_throttle"))
        run.game.session.setScore(1500)
        assertEquals(1500, Progress.metric("full_throttle"))
        assertEquals(1, earned("full_throttle"))
        run.game.session.setScore(2237)
        assertEquals("The record keeps climbing past the goal", 2200, Progress.metric("full_throttle"))
        run.game.session.gameOver()
        assertEquals("Game over records the exact score", 2237, Progress.metric("full_throttle"))
    }

    @Test fun finishedChallengesKeepTheRealRecord() = playingRun(cube(0)) { run ->
        Progress.recordCenteredScore(140); Progress.recordCoinlessScore(75)
        repeat(12) { run.game.session.mysteryBoxMissed() }
        run.game.session.runSeconds(613)
        run.game.session.gameOver()
        Progress.init(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)
        assertEquals("Stay Centered", 140, Progress.bestCenteredScore)
        assertEquals("Homeress", 75, Progress.bestCoinlessScore)
        assertEquals("Gambliphobic", 12, Progress.maxRunMissedBoxes)
        assertEquals("Long Con: the exact time, not the last ten", 613, Progress.metric("long_con"))
    }
}
