package cube.run.game

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector3
import cube.run.GameActivity
import cube.run.bot.LiveBotDriver.Companion.gl
import cube.run.core.Stage
import cube.run.intro.OpeningClock
import cube.run.intro.OpeningPose
import org.junit.Assert.*
import org.junit.Test

class OpeningContinuityTest {
    @Test fun roadLandmarksDoNotJumpWhenTheOpeningFinishes() {
        ActivityScenario.launch(GameActivity::class.java).use {
            gl { host ->
                val input = Gdx.input.inputProcessor
                val game = CubeRun(host.session, launchOpening = true, firstWorld = 0)
                try {
                    game.create(); game.render(); game.render()
                    val opening = CubeRun::class.java.getDeclaredField("opening").apply { isAccessible = true }.get(game) as CubeOpening
                    val tick = CubeRun::class.java.getDeclaredMethod("tick", Float::class.javaPrimitiveType).apply { isAccessible = true }
                    opening.tick(OpeningPose.DURATION-.002f)
                    tick.invoke(game, 0f)
                    game.cam.update()
                    val landmarks = listOf(Vector3(0f,0f,0f), Vector3(-2f,0f,-8f), Vector3(2f,3f,-14f), Vector3(0f,4f,-40f))
                    val before = landmarks.map { game.cam.project(it.cpy()) }
                    tick.invoke(game, .004f)
                    game.cam.update()
                    val after = landmarks.map { game.cam.project(it.cpy()) }
                    val jump = before.zip(after).maxOf { (a,b) -> a.dst(b) }
                    assertTrue("Static road landmarks jumped by $jump pixels at completion", jump < .25f)
                } finally { game.dispose(); Gdx.input.inputProcessor = input; Stage.reset() }
            }
        }
    }

    @Test fun cubeKeepsTurningWhileTheMatchingFrameIsPrepared() {
        val clock = OpeningClock()
        clock.start()
        clock.adoptSystemStart(System.currentTimeMillis()-2000, 1000)
        val before = clock.motionSeconds()
        SystemClock.sleep(60)
        val after = clock.motionSeconds()
        assertTrue("The cube stopped while preparing the handoff", after-before > .04f)
        clock.releaseSystem()
        assertEquals("Removing the cover must not reset rotation", after, clock.motionSeconds(), .02f)
    }

    @Test fun cameraTravelDoesNotChangeTheCubesAngularSpeed() {
        val shot = OpeningPose()
        var previous = 0f
        for (i in 0..90) {
            val time = i/60f
            shot.update(time, 850f, time+.4f)
            if (i > 0) assertEquals("Rotation changed speed at $time", 40f/60f, shot.yaw-previous, .001f)
            previous = shot.yaw
        }
    }

    @Test fun aLateFrameCommitDoesNotFreezeTheCubeOrTrapTheOpening() {
        val clock = OpeningClock()
        clock.start()
        clock.adoptSystemStart(System.currentTimeMillis()-1000, 3000)
        val before = clock.motionSeconds()
        SystemClock.sleep(1650) // The matching frame arrives after the original intro deadline.
        assertTrue("Rotation stopped at the camera deadline", clock.motionSeconds()-before > 1.6f)
        clock.releaseSystem()
        assertEquals("Late handoff must start at the matching camera", 0f, clock.sceneSeconds(), .01f)
        SystemClock.sleep(80)
        assertTrue("A late release trapped the opening at zero", clock.sceneSeconds() > .1f)
        SystemClock.sleep(550)
        assertEquals(OpeningPose.DURATION, clock.sceneSeconds(), .001f)
    }

    @Test fun aLongSystemLeadInDoesNotTriggerAnIdleHopAtCompletion() {
        ActivityScenario.launch(GameActivity::class.java).use {
            gl { game ->
                val player = CubeRun::class.java.getDeclaredField("player").apply { isAccessible = true }.get(game) as Player
                val shot = OpeningPose().apply { update(OpeningPose.DURATION, 850f, 4.2f) }
                player.openingPose(shot, 4.2f, 320f)
                player.idle(1f/60f)
                assertFalse("Loading time must not accumulate an invisible hop", player.air)
            }
        }
    }

    @Test fun revealingTheGameWaitsForASceneRenderedAfterPhaseAdoption() {
        ActivityScenario.launch(GameActivity::class.java).use {
            gl { host ->
                val input = Gdx.input.inputProcessor
                val game = CubeRun(host.session, launchOpening = true, firstWorld = 0)
                try {
                    game.create(); repeat(4) { game.render() }
                    var fresh = false
                    game.afterFreshSceneFrame { fresh = true }
                    game.render()
                    assertFalse("The in-flight scene predates the changed pose", fresh)
                    game.render()
                    assertFalse("The replacement frame has not swapped yet", fresh)
                    game.render()
                    assertTrue("The updated scene should now be available", fresh)
                } finally { game.dispose(); Gdx.input.inputProcessor = input; Stage.reset() }
            }
        }
    }
}
