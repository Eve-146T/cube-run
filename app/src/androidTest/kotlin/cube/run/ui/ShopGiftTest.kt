package cube.run.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Settings
import org.junit.Assert.*
import org.junit.Test

/**
 * A mystery box bought in the shop opens in the showroom on the real GL stage: the
 * shop never leaves the screen, the reward is banked once, and the pill saying what
 * came out goes away again.
 */
class ShopGiftTest {
    private fun field(owner: Any, name: String): java.lang.reflect.Field {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true } }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        throw NoSuchFieldException(name)
    }
    private fun hud(activity: GameActivity) = field(activity, "hud").get(activity) as Hud
    private fun shop(activity: GameActivity) = field(hud(activity), "page").get(hud(activity)) as ShopView
    private fun pill(shop: ShopView) = field(shop, "rewardPill").get(shop) as View?
    private fun paying(shop: ShopView) = field(shop, "paying").getBoolean(shop)
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun awaitUi(scenario: ActivityScenario<GameActivity>, label: String, timeout: Long = 15000,
                        predicate: (GameActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { ready = predicate(it) }
            if (!ready) SystemClock.sleep(16)
        }
        assertTrue(label, ready)
    }
    private fun restore(prefs: SharedPreferences, saved: Map<String, *>) {
        val edit = prefs.edit().clear()
        for ((key, value) in saved) when (value) {
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is String -> edit.putString(key, value)
            is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
        edit.commit()
    }

    @Test fun purchasedBoxOpensInTheShowroomWithoutLeavingTheShop() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val scores = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
        val saved = prefs.all; val savedScores = scores.all; val savedDev = Settings.devMode
        try {
            prefs.edit().clear().putInt("coins", 100000).commit()
            scores.edit().clear().commit()
            Settings.init(context); Settings.setDevMode(false); Progress.init(context)
            ActivityScenario.launch(GameActivity::class.java).use { scenario ->
                awaitUi(scenario, "HUD ready") { field(it, "hud").get(it) != null }
                scenario.onActivity {
                    Hud::class.java.getDeclaredMethod("openShop").apply { isAccessible = true }.invoke(hud(it))
                }
                awaitUi(scenario, "Shop entrance settles") { field(shop(it), "progress").getFloat(shop(it)) == 1f }
                lateinit var originalShop: ShopView
                val boxesBefore = Progress.boxesOpened
                val burstsBefore = Stage.shopGiftOpened.get()
                scenario.onActivity {
                    originalShop = shop(it)
                    val card = (field(originalShop, "cards").get(originalShop) as Map<*, *>)["mystery"] as View
                    val purchase = descendants(card).filterIsInstance<CandyButton>().last()
                    purchase.requestRectangleOnScreen(Rect(0, 0, purchase.width, purchase.height), true)
                    assertTrue("Actual mystery purchase", purchase.performClick())
                    assertTrue("The page holds still while the box is on its way", paying(originalShop))
                }
                val bankAfterReward = Progress.coins
                assertEquals(boxesBefore + 1, Progress.boxesOpened)
                awaitUi(scenario, "The GL showroom bursts the box and the shop says what came out", 5000) {
                    pill(shop(it)) != null
                }
                scenario.onActivity {
                    assertTrue("The engine opened it (not the time-out fallback)", Stage.shopGiftOpened.get() > burstsBefore)
                    assertSame("The shop never left", originalShop, shop(it))
                    assertEquals(View.VISIBLE, originalShop.visibility)
                    assertEquals(Stage.SHOP, Stage.mode)
                    assertFalse("Shopping resumes as soon as the box is open", paying(originalShop))
                }
                scenario.onActivity { shop(it).findViewWithTag<View>("shop_gift_reward")!!.let { p -> assertTrue(p.isShown) } }
                // A tap on the showroom hurries the prize into the cube and takes the pill away.
                scenario.onActivity { ShopView::class.java.getDeclaredMethod("skipGift").apply { isAccessible = true }.invoke(shop(it)) }
                awaitUi(scenario, "The pill goes", 3000) { pill(shop(it)) == null && it.window.decorView.findViewWithTag<View>("shop_gift_reward") == null }
                awaitUi(scenario, "The bank lands on the banked total", 3000) {
                    UiKit(it).labelOf(menuOf(it).shopBalance).text.toString() == Progress.coins.toString()
                }
                assertEquals(boxesBefore + 1, Progress.boxesOpened)
                assertEquals("The show never pays out twice", bankAfterReward, Progress.coins)
                scenario.onActivity { hud(it).navigateBack() }
                awaitUi(scenario, "Back still leaves the shop") { field(hud(it), "page").get(hud(it)) == null }
            }
        } finally {
            Settings.setDevMode(false)
            restore(prefs, saved); restore(scores, savedScores); Progress.init(context); Settings.setDevMode(savedDev)
        }
    }

    private fun menuOf(activity: GameActivity) = field(hud(activity), "menu").get(hud(activity)) as MainMenu
}
