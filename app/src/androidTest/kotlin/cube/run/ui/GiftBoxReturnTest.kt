package cube.run.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Settings
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Holds the real GL handoff, rather than assuming an Android animation means a GL frame rendered. */
class GiftBoxReturnTest {
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
    private fun flow(activity: GameActivity) = field(hud(activity), "shopBox").get(hud(activity))!!
    private fun tap(flow: Any) = flow.javaClass.getDeclaredMethod("tapBox").apply { isAccessible = true }.invoke(flow)
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

    @Test fun purchasedGiftStaysCoveredUntilTheShopHasRenderedOnGl() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val scores = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
        val saved = prefs.all; val savedScores = scores.all; val savedDev = Settings.devMode
        val release = CountDownLatch(1)
        try {
            prefs.edit().clear().putInt("coins", 100000).commit()
            scores.edit().clear().commit()
            Settings.init(context); Settings.setDevMode(false); Progress.init(context)
            ActivityScenario.launch(GameActivity::class.java).use { scenario ->
                try {
                    awaitUi(scenario, "HUD ready") { field(it, "hud").get(it) != null }
                    scenario.onActivity {
                        Hud::class.java.getDeclaredMethod("openShop").apply { isAccessible = true }.invoke(hud(it))
                    }
                    awaitUi(scenario, "Shop entrance settles") { field(shop(it), "progress").getFloat(shop(it)) == 1f }
                    lateinit var originalShop: ShopView
                    val boxesBefore = Progress.boxesOpened
                    scenario.onActivity {
                        originalShop = shop(it)
                        val card = (field(originalShop, "cards").get(originalShop) as Map<*, *>)["mystery"] as View
                        val purchase = descendants(card).filterIsInstance<CandyButton>().last()
                        purchase.requestRectangleOnScreen(Rect(0, 0, purchase.width, purchase.height), true)
                        assertTrue("Actual mystery purchase", purchase.performClick())
                    }
                    val bankAfterReward = Progress.coins
                    awaitUi(scenario, "Purchased gift appears") { field(hud(it), "shopBox").get(hud(it)) != null && Stage.giftShowing }
                    scenario.onActivity { tap(flow(it)) }
                    awaitUi(scenario, "Actual GL reward arrives") { field(flow(it), "boxRewardReady").getBoolean(flow(it)) }
                    scenario.onActivity { if (field(flow(it), "boxBusy").getBoolean(flow(it))) tap(flow(it)) }

                    val entered = CountDownLatch(1)
                    Gdx.app.postRunnable {
                        entered.countDown()
                        release.await(8, TimeUnit.SECONDS) // Never strand GL after an assertion failure.
                    }
                    assertTrue("GL hold entered", entered.await(3, TimeUnit.SECONDS))
                    scenario.onActivity { tap(flow(it)) }
                    awaitUi(scenario, "UI reaches opaque curtain while GL is held", 3000) {
                        (field(hud(it), "giftReturnCover").get(hud(it)) as? View)?.alpha == 1f
                    }
                    scenario.onActivity {
                        val chrome = hud(it)
                        val cover = field(chrome, "giftReturnCover").get(chrome) as View
                        assertTrue("The gift renderer has not exited behind the curtain", Stage.giftShowing)
                        assertSame(originalShop, shop(it))
                        assertEquals(View.VISIBLE, originalShop.visibility)
                        assertEquals(chrome.width, cover.width); assertEquals(chrome.height, cover.height)
                        assertSame(cover, chrome.getChildAt(chrome.childCount - 1))
                        val bitmap = Bitmap.createBitmap(cover.width, cover.height, Bitmap.Config.ARGB_8888)
                        cover.draw(Canvas(bitmap))
                        for (x in listOf(0, bitmap.width / 2, bitmap.width - 1))
                            for (y in listOf(0, bitmap.height / 2, bitmap.height - 1))
                                assertEquals("Curtain is opaque across the viewport", 255, Color.alpha(bitmap.getPixel(x, y)))
                        bitmap.recycle()
                        val scrolls = descendants(originalShop).filterIsInstance<ScrollView>()
                        val offsets = scrolls.map { view -> view.scrollY }
                        val now = SystemClock.uptimeMillis()
                        for ((index, action) in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP).withIndex()) {
                            val event = MotionEvent.obtain(now, now + index * 30L, action,
                                chrome.width * .5f, chrome.height * if (index == 0) .8f else .25f, 0)
                            assertTrue("Curtain consumes touches", chrome.dispatchTouchEvent(event)); event.recycle()
                        }
                        chrome.navigateBack()
                        assertSame(cover, field(chrome, "giftReturnCover").get(chrome))
                        assertSame(originalShop, shop(it))
                        assertEquals(offsets, scrolls.map { view -> view.scrollY })
                        assertEquals(bankAfterReward, Progress.coins)
                        assertEquals(boxesBefore + 1, Progress.boxesOpened)
                    }
                    release.countDown()
                    awaitUi(scenario, "Shop frame permits a gradual curtain reveal") {
                        val cover = field(hud(it), "giftReturnCover").get(hud(it)) as? View
                        cover != null && cover.alpha > 0f && cover.alpha < 1f && !Stage.giftShowing && Stage.mode == Stage.SHOP
                    }
                    awaitUi(scenario, "Curtain and gift flow finish") {
                        field(hud(it), "giftReturnCover").get(hud(it)) == null && field(hud(it), "shopBox").get(hud(it)) == null
                    }
                    scenario.onActivity {
                        assertSame(originalShop, shop(it)); assertEquals(View.VISIBLE, originalShop.visibility)
                        assertEquals(1f, originalShop.alpha, 0f)
                    }
                    assertFalse(Stage.giftShowing)
                    assertEquals(boxesBefore + 1, Progress.boxesOpened)
                    assertEquals("Return never charges or rewards a second box", bankAfterReward, Progress.coins)
                } finally { release.countDown() }
            }
        } finally {
            release.countDown(); Settings.setDevMode(false)
            restore(prefs, saved); restore(scores, savedScores); Progress.init(context); Settings.setDevMode(savedDev)
        }
    }
}
