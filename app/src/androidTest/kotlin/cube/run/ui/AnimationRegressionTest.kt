package cube.run.ui

import android.animation.ValueAnimator
import android.graphics.Region
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.ui.Anim.move
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android animators over the game's SurfaceView; no mocked animation clock. */
@RunWith(AndroidJUnit4::class)
class AnimationRegressionTest {
    private lateinit var scenario: ActivityScenario<GameActivity>
    private lateinit var activity: GameActivity
    private lateinit var host: FrameLayout
    private lateinit var kit: UiKit

    @Before fun launch() {
        scenario = ActivityScenario.launch(GameActivity::class.java)
        ui {
            activity = it
            kit = UiKit(it)
            host = FrameLayout(it).apply { clipChildren = false }
            it.addContentView(host, FrameLayout.LayoutParams(-1, -1))
        }
    }

    @After fun finish() {
        ui { host.removeAllViews(); Stage.mode = Stage.NONE }
        scenario.close()
    }

    private fun ui(action: (GameActivity) -> Unit) { scenario.onActivity(action) }
    private fun waitFor(ms: Long) = SystemClock.sleep(ms)
    private fun attach(v: View) { host.addView(v, FrameLayout.LayoutParams(-1, -1)) }
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner: Any, name: String): T =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner) as T
    private fun settled(v: View) {
        assertEquals(1f, v.alpha, 0.01f)
        assertEquals(1f, v.scaleX, 0.01f)
        assertEquals(1f, v.scaleY, 0.01f)
        assertEquals(0f, v.translationX, 0.01f)
        assertEquals(0f, v.translationY, 0.01f)
    }

    @Test fun growingMenuPillsAreNeverInTheSurfaceTransparentRegion() {
        ui {
            val hud = field<Hud>(it, "hud")
            val region = Region(0, 0, hud.width, hud.height)
            hud.gatherTransparentRegion(region)
            assertTrue("The compositor must retain every pixel an overlay can grow into", region.isEmpty)
        }
    }

    @Test fun touchDuringAStaggeredEntranceDoesNotStrandTheButton() {
        lateinit var button: CandyButton
        ui {
            button = kit.button("PRESS", Theme.GOLD, UiKit.Size.BIG) {}
            attach(button)
            Anim.riseIn(button, 200, 80f, 300)
        }
        waitFor(250)
        ui {
            val now = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL)) {
                val event = MotionEvent.obtain(now, now, action, 30f, 30f, 0)
                button.dispatchTouchEvent(event)
                event.recycle()
            }
        }
        waitFor(600)
        ui { settled(button) }
    }

    @Test fun pulseDuringEntranceFinishesBothTheFadeAndTheSlide() {
        lateinit var value: TextView
        ui { value = kit.text("123", 20f); attach(value); Anim.riseIn(value, distancePx = 80f, duration = 600) }
        waitFor(160)
        ui { Anim.pulse(value) }
        waitFor(750)
        ui { settled(value) }
    }

    @Test fun exitCancelsDelayedEntranceAndCannotBeUndoneByItsEndAction() {
        lateinit var value: TextView
        var exited = false
        ui {
            value = kit.text("123", 20f); attach(value)
            Anim.popIn(value, delay = 400)
            value.move().alpha(0f).translationY(70f).setDuration(120).withEndAction { exited = true }.start()
        }
        waitFor(900)
        ui {
            assertTrue(exited)
            assertEquals(0f, value.alpha, 0.01f)
            assertEquals(70f, value.translationY, 0.01f)
        }
    }

    @Test fun switchingWardrobeCategoryThenItemDoesNotLeaveTheNameOffset() {
        lateinit var wardrobe: WardrobeView
        ui { wardrobe = WardrobeView(it, kit) {}; attach(wardrobe) }
        waitFor(750)
        ui { field<ArrayList<TextView>>(wardrobe, "tabViews")[1].performClick() }
        waitFor(50)
        ui { field<View>(wardrobe, "right").performClick() }
        waitFor(500)
        ui { settled(field(wardrobe, "name")); settled(field(wardrobe, "right")) }
    }

    @Test fun hidingMenuBeforeTheLogoDelayDoesNotRestartItsIdleEffects() {
        lateinit var menu: MainMenu
        ui { menu = MainMenu(it, kit, {}, {}, {}, {}); attach(menu); menu.setShown(false) }
        waitFor(850)
        ui { assertTrue(field<ArrayList<ValueAnimator>>(menu, "anims").isEmpty()) }
    }

    @Test fun rapidMenuReturnsStartOnlyOneLogoRipple() {
        lateinit var menu: MainMenu
        ui { menu = MainMenu(it, kit, {}, {}, {}, {}); attach(menu) }
        repeat(3) {
            waitFor(100)
            ui { menu.setShown(false); menu.show() }
        }
        waitFor(1000)
        ui {
            assertEquals("One hint breath and one logo ripple", 2, field<ArrayList<ValueAnimator>>(menu, "anims").size)
            settled(field(menu, "bank"))
        }
    }

    private fun results(boxes: Int = 0) = RunOverFlow(activity, kit, 350, 100, true, 75, boxes, "Candy", emptyList(), {}, {})

    @Test fun skippingResultsKeepsTheFinalScoreInsteadOfCountingBackwards() {
        lateinit var flow: RunOverFlow
        ui { flow = results(); attach(flow) }
        waitFor(180)
        ui { field<View>(flow, "page").performClick() }
        repeat(5) {
            waitFor(120)
            ui {
                assertEquals("350", field<TextView>(flow, "scoreText").text.toString())
                assertEquals("+75", field<TextView>(flow, "coinText").text.toString())
            }
        }
    }

    @Test fun leavingResultsCancelsPendingStarsAndCounters() {
        lateinit var flow: RunOverFlow
        lateinit var counter: ValueAnimator
        ui { flow = results(); attach(flow); counter = field(flow, "scoreAnim") }
        waitFor(100)
        ui { host.removeView(flow) }
        waitFor(1700)
        ui {
            assertFalse(counter.isStarted)
            assertTrue(field<ArrayList<Runnable>>(flow, "pending").isEmpty())
            assertTrue(field<ArrayList<ValueAnimator>>(flow, "anims").isEmpty())
        }
    }

    @Test fun rareRewardHeartbeatWaitsForEntranceAndStopsForTheNextReward() {
        lateinit var flow: RunOverFlow
        ui {
            flow = results(boxes = 2); attach(flow)
            field<View>(flow, "page").performClick()
            field<View>(flow, "page").performClick()
            flow.onBoxOpened(Progress.BoxReward.COINS, 200, 0, 0)
            assertNull(field<ValueAnimator?>(flow, "rewardBeat"))
        }
        waitFor(650)
        ui { assertTrue(field<ValueAnimator>(flow, "rewardBeat").isStarted) }
        ui {
            flow.onBoxOpened(Progress.BoxReward.BUBBLE, 1, 0, 0)
            assertNull(field<ValueAnimator?>(flow, "rewardBeat"))
            assertEquals(36f, field<TextView>(flow, "rewardBig").textSize / activity.resources.displayMetrics.scaledDensity, 0.1f)
        }
        waitFor(600)
        ui { settled(field(flow, "rewardCard")); assertNull(field<ValueAnimator?>(flow, "rewardBeat")) }
    }

    @Test fun paymentCannotCompleteOnADetachedPage() {
        lateinit var paymentHost: FrameLayout
        var completed = false
        ui {
            paymentHost = FrameLayout(it); attach(paymentHost)
            val from = kit.text("BANK", 20f); val to = kit.text("BUY", 20f)
            paymentHost.addView(from); paymentHost.addView(to)
            PayFx.fly(paymentHost, kit, from, to) { completed = true }
        }
        waitFor(120)
        ui { host.removeView(paymentHost) }
        waitFor(800)
        assertFalse(completed)
    }
}
