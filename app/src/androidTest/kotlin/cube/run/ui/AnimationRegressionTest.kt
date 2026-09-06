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
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Quaternion
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    private fun cubeYaw(): Float {
        val done = CountDownLatch(1)
        var yaw = 0f
        var failure: Throwable? = null
        Gdx.app.postRunnable {
            try {
                val player = field<Any>(Gdx.app.applicationListener, "player")
                yaw = field<ModelInstance>(player, "inst").transform.getRotation(Quaternion(), true).yaw
            } catch (t: Throwable) { failure = t } finally { done.countDown() }
        }
        assertTrue("GL frame completed", done.await(5, TimeUnit.SECONDS))
        failure?.let { throw it }
        return yaw
    }
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

    @Test fun shopKeepsTheSameBalanceViewPinnedThroughNavigation() {
        waitFor(900)
        lateinit var hud: Hud
        lateinit var menu: MainMenu
        lateinit var bank: View
        val corner = IntArray(2)
        ui {
            hud = field(it, "hud"); menu = field(hud, "menu"); bank = menu.shopBalance
            bank.getLocationInWindow(corner)
            bank.performClick()
        }
        repeat(5) {
            waitFor(90)
            ui {
                val current = IntArray(2)
                bank.getLocationInWindow(current)
                assertArrayEquals("The real balance must not slide or be replaced", corner, current)
                assertSame(menu, bank.parent)
                settled(bank)
            }
        }
        ui { field<Page>(hud, "page").close() }
        waitFor(400)
        ui {
            assertNull(field<Page?>(hud, "page"))
            assertEquals(Stage.NONE, Stage.mode)
            settled(bank)
            settled(field(menu, "top"))
            assertEquals(2, field<ArrayList<ValueAnimator>>(menu, "anims").size)
        }
    }

    @Test fun preparedShopIsLaidOutBeforeTheTapAndStartsOnTheNextDraw() {
        waitFor(1400)
        lateinit var hud: Hud
        lateinit var prepared: ShopView
        val started = CountDownLatch(1)
        var elapsed = 0L
        ui {
            hud = field(it, "hud")
            prepared = field(hud, "preparedShop")
            assertNull("Preparation must not open a hidden screen", prepared.parent)
            assertTrue(prepared.width > 0 && prepared.height > 0)
            assertEquals(Stage.NONE, Stage.mode)
            val tappedAt = SystemClock.uptimeMillis()
            field<MainMenu>(hud, "menu").shopBalance.performClick()
            assertSame("Opening should reuse the already constructed cards", prepared, field(hud, "page"))
            prepared.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    prepared.viewTreeObserver.removeOnPreDrawListener(this)
                    elapsed = SystemClock.uptimeMillis() - tappedAt
                    started.countDown()
                    return true
                }
            })
        }
        assertTrue(started.await(2, TimeUnit.SECONDS))
        android.util.Log.i("ShopMotion", "Prepared shop first draw: $elapsed ms")
        assertTrue("Prepared entrance took $elapsed ms to reach its first draw", elapsed < 120)
        ui { prepared.close() }
        waitFor(500)
    }

    @Test fun aPreparedShopWithOutdatedPricesIsRebuiltBeforeOpening() {
        waitFor(1400)
        ui {
            val hud = field<Hud>(it, "hud")
            val prepared = field<ShopView>(hud, "preparedShop")
            val coins = Progress::class.java.getDeclaredField("coins").apply { isAccessible = true }
            val original = Progress.coins
            // Simulate a balance change without altering persisted progress or lifetime totals.
            try {
                coins.setInt(null, original + 120)
                field<MainMenu>(hud, "menu").shopBalance.performClick()
                val opened = field<ShopView>(hud, "page")
                assertNotSame(prepared, opened)
                assertTrue(opened.isCurrent())
            } finally {
                coins.setInt(null, original)
                field<Page?>(hud, "page")?.close()
            }
        }
        waitFor(500)
    }

    @Test fun closingShopBeforeItsFirstDrawCannotStartTheShowroomLater() {
        lateinit var hud: Hud
        ui {
            hud = field(it, "hud")
            field<MainMenu>(hud, "menu").shopBalance.performClick()
            val shop = field<Page>(hud, "page")
            shop.close(); shop.close()
        }
        waitFor(500)
        ui {
            assertNull(field<Page?>(hud, "page"))
            assertEquals(Stage.NONE, Stage.mode)
            field<MainMenu>(hud, "menu").shopBalance.performClick()
        }
        waitFor(500)
        ui {
            assertEquals(Stage.SHOP, Stage.mode)
            assertEquals(1f, Stage.shopProgress, 0.001f)
            field<Page>(hud, "page").close()
        }
        waitFor(400)
        ui { assertEquals(Stage.NONE, Stage.mode) }
    }

    @Test fun shopCanReverseDuringItsEntranceAndReopenWithoutReplayingTheMenu() {
        lateinit var hud: Hud
        ui { hud = field(it, "hud"); field<MainMenu>(hud, "menu").shopBalance.performClick() }
        waitFor(140)
        ui { field<Page>(hud, "page").close() }
        waitFor(400)
        ui {
            assertNull(field<Page?>(hud, "page"))
            assertEquals(0f, Stage.shopProgress, 0.001f)
            val menu = field<MainMenu>(hud, "menu")
            settled(field(menu, "logo")); settled(field(menu, "rightChips"))
            menu.shopBalance.performClick()
        }
        waitFor(500)
        ui {
            assertEquals(1f, Stage.shopProgress, 0.001f)
            field<Page>(hud, "page").close()
        }
        waitFor(400)
    }

    @Test fun cubeKeepsTurningRightThroughShopEntranceIdleAndReturn() {
        waitFor(2000)
        lateinit var hud: Hud
        var previous = cubeYaw()
        ui { hud = field(it, "hud"); field<MainMenu>(hud, "menu").shopBalance.performClick() }
        repeat(28) { frame ->
            if (frame == 16) ui { field<Page>(hud, "page").close() }
            waitFor(45)
            val yaw = cubeYaw()
            val turn = (yaw - previous + 540f) % 360f - 180f
            assertTrue("Frame $frame reversed the cube by $turn degrees", turn >= -0.5f)
            assertTrue("Frame $frame snapped the cube by $turn degrees", turn < 70f)
            previous = yaw
        }
        ui { assertEquals(Stage.NONE, Stage.mode) }
    }

    @Test fun menuControlsReturnAboveTheMovingSheetBeforeItCloses() {
        lateinit var hud: Hud
        lateinit var menu: MainMenu
        ui {
            hud = field(it, "hud"); menu = field(hud, "menu")
            menu.shopBalance.performClick()
        }
        waitFor(500)
        ui { field<Page>(hud, "page").close() }
        waitFor(220)
        ui {
            val sheet = field<Page?>(hud, "page")
            assertNotNull(sheet)
            assertTrue("The controls must draw above the departing sheet", hud.indexOfChild(menu) > hud.indexOfChild(sheet))
            val chips = field<View>(menu, "rightChips")
            assertTrue("Controls should already be returning, not hidden until the last frame", chips.alpha > 0.1f)
            assertTrue(chips.translationY < kit.dpf(20f))
        }
        waitFor(300)
        ui { settled(field(menu, "rightChips")) }
    }

    private fun results(boxes: Int = 0) = RunOverFlow(activity, kit, 350, 100, true, 75, boxes, "Candy", emptyList(), {}, {})

    @Test fun returningFromResultsStartsImmediatelyAndKeepsTheOldScreenVisible() {
        lateinit var flow: RunOverFlow
        var returns = 0
        ui {
            flow = RunOverFlow(it, kit, 80, 100, false, 5, 0, "Candy", emptyList(), {}, { returns++ })
            attach(flow)
        }
        waitFor(400)
        ui {
            field<View>(flow, "page").performClick() // finish the count
            field<View>(flow, "page").performClick() // return
            assertEquals("Do not wait for an outgoing fade before preparing the menu", 1, returns)
            assertEquals("Keep the results visible underneath the incoming activity", 1f, flow.alpha, 0.001f)
            field<View>(flow, "page").performClick()
            assertEquals("Repeated taps must not launch duplicate menus", 1, returns)
        }
    }

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
