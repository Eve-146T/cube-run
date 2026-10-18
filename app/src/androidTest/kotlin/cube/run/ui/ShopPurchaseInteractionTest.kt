package cube.run.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Scores
import cube.run.data.Settings
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Real purchase/touch flows; animated shop frames need no idle-looper synchronization. */
class ShopPurchaseInteractionTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private class FixtureContext(base: Context) : ContextWrapper(base) {
        private val prefix = "shop_interaction_${UUID.randomUUID()}_"
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences(prefix + name, mode)
        fun dispose() { for (name in listOf("progress", "scores", "settings")) baseContext.deleteSharedPreferences(prefix + name) }
    }
    private fun ui(action: () -> Unit) {
        var failure: Throwable? = null
        inst.runOnMainSync { try { action() } catch (t: Throwable) { failure = t } }
        failure?.let { throw it }
    }
    private fun field(owner: Any, name: String): java.lang.reflect.Field {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true } }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        error(name)
    }
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun await(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 12000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            ui { ready = condition() }
            if (ready) return
            SystemClock.sleep(16)
        }
        fail(label)
    }
    private fun paying(page: ShopView) = field(page, "paying").getBoolean(page)
    private fun buy(page: ShopView, key: String) {
        val card = (field(page, "cards").get(page) as Map<*, *>)[key] as View
        val button = all(card).last { it is CandyButton || it is CandyRow }
        button.requestRectangleOnScreen(Rect(0, 0, button.width, button.height), true)
        assertTrue(button.performClick())
    }
    private fun fixture(test: (ShopView) -> Unit) {
        val real = inst.targetContext
        val context = FixtureContext(real)
        val dev = Settings.devMode
        lateinit var page: ShopView
        try {
            ActivityScenario.launch(GameActivity::class.java).use { scenario ->
                lateinit var activity: GameActivity
                scenario.onActivity { activity = it }
                await("The host HUD is initialized") { field(activity, "hud").get(activity) != null }
                ui {
                    Settings.init(context); Settings.setDevMode(false); Settings.setSoundEnabled(false); Settings.setHapticsEnabled(false)
                    context.getSharedPreferences("progress", Context.MODE_PRIVATE).edit().putInt("coins", 100000).commit()
                    Scores.init(context); Progress.init(context)
                    val hud = field(activity, "hud").get(activity) as Hud
                    Hud::class.java.getDeclaredMethod("openShop").apply { isAccessible = true }.invoke(hud)
                    page = field(hud, "page").get(hud) as ShopView
                }
                await("Shop entrance settles") { Stage.shopProgress == 1f }
                try { test(page) } finally {
                    ui { Stage.paused = false; page.close() }
                    await("Shop detaches before its fixture is restored") { !page.isAttachedToWindow }
                }
            }
        } finally {
            ui { Settings.init(real); Settings.setDevMode(dev); Scores.init(real); Progress.init(real) }
            context.dispose()
        }
    }

    @Test fun consecutiveBoxesReplaceThePillAndNavigationCleansItUp() = fixture { page ->
        repeat(3) {
            ui { buy(page, "mystery") }
            await("Box opens") { !paying(page) && all(page).any { it.tag == "shop_gift_reward" } }
            ui { assertEquals("Each reveal owns one reward label", 1, all(page).count { it.tag == "shop_gift_reward" }) }
        }
        ui { assertEquals(3, Progress.boxesOpened); page.navigateBack() }
        await("Navigation removes the active pill") { !page.isAttachedToWindow }
        ui { assertTrue(all(page).none { it.tag == "shop_gift_reward" }) }
    }

    @Test fun paymentAllowsScrollingButRejectsAnotherPurchase() = fixture { page ->
        ui {
            val scroll = all(page).filterIsInstance<ScrollView>().single()
            buy(page, "mystery")
            assertTrue(paying(page))
            scroll.scrollTo(0, 0)
            val bounds = Rect(0, 0, scroll.width, scroll.height)
            page.offsetDescendantRectToMyCoords(scroll, bounds)
            val now = SystemClock.uptimeMillis()
            for (i in 0..10) {
                val action = when (i) { 0 -> MotionEvent.ACTION_DOWN; 10 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
                val event = MotionEvent.obtain(now, now + i * 16L, action, page.width * .5f, bounds.top + scroll.height * (.8f - .05f * i), 0)
                try { page.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            assertTrue("Scrolling works during the coin flight", scroll.scrollY > 0)
            val coins = Progress.coins
            val bubbles = Progress.bubbles // The mystery box may already have banked a bubble reward.
            buy(page, "bubbles")
            assertEquals("A second purchase waits", coins, Progress.coins)
            assertEquals(bubbles, Progress.bubbles)
            assertEquals(1, Progress.boxesOpened)
        }
        await("Payment still finishes after scrolling") { !paying(page) }
    }

    @Test fun showroomTapSkipsAnOpeningBoxBeforeItsRewardPillExists() = fixture { page ->
        ui {
            Stage.paused = true // Keep the GL thread from consuming the request before it is checked.
            buy(page, "mystery")
            val showroom = field(page, "showroom").get(page) as View
            val bounds = Rect(0, 0, showroom.width, showroom.height)
            page.offsetDescendantRectToMyCoords(showroom, bounds)
            val before = Stage.shopGiftSkips.get()
            val now = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(now, now, action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                try { assertTrue(page.dispatchTouchEvent(event)) } finally { event.recycle() }
            }
            assertEquals("The tap reaches the box while payment is in progress", before + 1, Stage.shopGiftSkips.get())
            Stage.paused = false
        }
        await("Skipped box still reveals and banks once") { !paying(page) }
        ui { assertEquals(1, Progress.boxesOpened) }
    }
}
