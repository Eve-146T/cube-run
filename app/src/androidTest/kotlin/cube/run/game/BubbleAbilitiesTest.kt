package cube.run.game

import cube.run.bot.value
import cube.run.game.track.Coin
import cube.run.game.track.Row
import org.junit.Assert.*
import org.junit.Test

/** The bubble suggestions (#64): Rainbow doubles the score, Ice slows the road, Galaxy's ground pounds pull coins in. */
class BubbleAbilitiesTest {
    private fun call(game: CubeRun, name: String, vararg args: Any): Any? =
        game.javaClass.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(game, *args)

    private fun equipped(bubbleSkin: Int, body: (RunControls) -> Unit) = playingRun({
        it.putInt("owned_bubble_skins", 0x7ff).putInt("bubble_skin", bubbleSkin).putInt("bubbles", 20)
    }, body)

    private fun bubbleUp(run: RunControls) {
        assertTrue("bubble went up", call(run.game, "tryBubble") as Boolean)
        assertTrue(run.read<Bubble>("bubble").active)
    }

    private fun scoreFor(run: RunControls): Int {
        val before = run.game.session.score
        call(run.game, "scoreRow", Row(2f, arrayListOf()))
        return run.game.session.score - before
    }

    @Test fun doubleScoreBubbleScoresTwiceAsFast() = equipped(5) { run ->
        assertEquals(1, scoreFor(run))
        bubbleUp(run)
        assertEquals(2, scoreFor(run))
        run.read<PowerUps>("powerUps").mult.start(5f)
        assertEquals("stacks with the 2× pickup", 4, scoreFor(run))
    }

    @Test fun plainBubbleKeepsTheScore() = equipped(0) { run ->
        bubbleUp(run)
        assertEquals(1, scoreFor(run))
    }

    @Test fun slowMotionBubbleSlowsTheRoadAndLetsGo() = equipped(6) { run ->
        run.frames(30)
        val cruising = run.read<Float>("spd")
        bubbleUp(run)
        run.frames(90)
        val slowed = run.read<Float>("spd")
        assertEquals("the road runs at 70%", 0.7f, slowed / cruising, 0.03f)
        run.read<Bubble>("bubble").reset()
        run.frames(120)
        assertTrue("speed comes back", run.read<Float>("spd") > cruising * 0.97f)
    }

    /** Slam from a hop with a coin a lane over, 6 units ahead; returns whether the coin started flying in. */
    private fun slamPulls(run: RunControls): Boolean {
        val coin = Coin(Lanes.NORMAL_W, 0.5f, 0f)
        val row = Row(-6f, arrayListOf()).apply { coins = arrayListOf(coin); scored = true }
        run.rows(listOf(row))
        val player = run.read<Player>("player")
        player.launch(8f); run.frames(5)
        player.downAction()
        var n = 0
        while (player.air && n++ < 120) { row.z = -6f; run.frames(1) }
        assertFalse("landed", player.air)
        return coin.pullStarted
    }

    @Test fun galaxyBubbleGroundPoundPullsCoins() = equipped(9) { run ->
        assertFalse("no bubble, no pull", slamPulls(run))
        bubbleUp(run)
        assertTrue("the slam pulls a coin from the next lane", slamPulls(run))
    }

    @Test fun poundMagnetReachesAheadOnly() = equipped(9) { run ->
        val near = Coin(Lanes.NORMAL_W, 0.5f, 0f)
        val far = Coin(-Lanes.NORMAL_W, 0.5f, -30f)
        val row = Row(-6f, arrayListOf()).apply { coins = arrayListOf(near, far); scored = true }
        run.rows(listOf(row))
        call(run.game, "poundMagnet")
        assertTrue(near.pullStarted)
        assertFalse("a coin far down the road stays put", far.pullStarted)
    }

    @Test fun plainBubbleGroundPoundPullsNothing() = equipped(0) { run ->
        bubbleUp(run)
        assertFalse(slamPulls(run))
    }
}
