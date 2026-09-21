package cube.run.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.GameHostSession
import cube.run.core.Gdx3DGame
import cube.run.core.Stage
import cube.run.data.Achievements
import cube.run.data.Progress
import cube.run.data.Settings
import cube.run.data.Skins
import cube.run.data.Trails
import cube.run.data.BubbleSkins
import cube.run.data.Wardrobe
import cube.run.game.CubeRun
import cube.run.game.Player
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit real-button and game-event review: -e captureHardwareAchievements true. */
class AchievementsHardwareFlowTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun field(owner: Any, name: String): java.lang.reflect.Field {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true } }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        throw NoSuchFieldException("${owner.javaClass.name}.$name")
    }
    private fun call(owner: Any, name: String) = owner.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(owner)
    private fun hud(activity: GameActivity) = field(activity, "hud").get(activity) as Hud
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun awaitUi(scenario: ActivityScenario<GameActivity>, label: String, timeout: Long = 15000, predicate: (GameActivity) -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < end) {
            scenario.onActivity { ready = predicate(it) }
            if (!ready) SystemClock.sleep(60)
        }
        assertTrue(label, ready)
    }
    private fun capture(name: String, settle: Long = 400) {
        if (settle > 0) SystemClock.sleep(settle)
        val output = File(context.getExternalFilesDir(null), "achievements-hardware").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun mysteryDisclosure(scenario: ActivityScenario<GameActivity>, name: String, owner: (GameActivity) -> View) {
        scenario.onActivity {
            val button = descendants(owner(it)).single { view -> view.contentDescription == "Show unknown ability" }
            button.requestRectangleOnScreen(android.graphics.Rect(0, 0, button.width, button.height), true)
            assertTrue(button.performClick())
        }
        awaitUi(scenario, "$name unknown ability expands") {
            descendants(owner(it)).any { view -> view.contentDescription?.toString() in listOf("???", "??? ??? ???") && view.visibility == View.VISIBLE && view.alpha == 1f }
        }
        if (name.startsWith("shop-")) awaitUi(scenario, "$name expanded discovery keeps its entire purchase button visible") {
            val page = owner(it) as ShopView
            val card = (field(page, "cards").get(page) as Map<*, *>)["darkness"] as View
            val purchase = descendants(card).filterIsInstance<CandyButton>().last()
            val visible = android.graphics.Rect()
            purchase.width > 0 && purchase.height > 0 && purchase.getGlobalVisibleRect(visible) &&
                visible.width() >= purchase.width - 1 && visible.height() >= purchase.height - 1
        }
        scenario.onActivity {
            val panel = descendants(owner(it)).single { view -> view.contentDescription?.toString() in listOf("???", "??? ??? ???") }
            val text = descendants(panel).filterIsInstance<TextView>().map { view -> view.text.toString() }
            assertEquals(listOf("???"), text)
            assertTrue(descendants(owner(it)).single { view -> view.contentDescription == "Hide unknown ability" }.isSelected)
        }
        capture("$name-unknown-expanded")
        scenario.onActivity { assertTrue(descendants(owner(it)).single { view -> view.contentDescription == "Hide unknown ability" }.performClick()) }
        awaitUi(scenario, "$name unknown ability collapses") {
            descendants(owner(it)).single { view -> view.contentDescription?.toString() in listOf("???", "??? ??? ???") }.visibility == View.GONE
        }
        capture("$name-unknown-collapsed")
        scenario.onActivity {
            val button = descendants(owner(it)).single { view -> view.contentDescription == "Show unknown ability" }
            repeat(3) { assertTrue(button.performClick()) } // Open/close/open before any frame can finish.
        }
        awaitUi(scenario, "$name rapid toggle settles open") {
            val views = descendants(owner(it))
            views.single { view -> view.contentDescription?.toString() in listOf("???", "??? ??? ???") }.let { panel -> panel.visibility == View.VISIBLE && panel.alpha == 1f } &&
                views.single { view -> view.contentDescription == "Hide unknown ability" }.isSelected
        }
        SystemClock.sleep(300)
        scenario.onActivity {
            val panel = descendants(owner(it)).single { view -> view.contentDescription?.toString() in listOf("???", "??? ??? ???") }
            assertEquals("Canceled collapse cannot hide reopened panel", View.VISIBLE, panel.visibility)
            assertEquals(1f, panel.alpha, 0f)
            assertEquals(0f, panel.translationY, 0f)
            assertTrue(descendants(owner(it)).single { view -> view.contentDescription == "Hide unknown ability" }.performClick())
        }
        awaitUi(scenario, "$name rapid-toggle review ends collapsed") {
            descendants(owner(it)).single { view -> view.contentDescription?.toString() in listOf("???", "??? ??? ???") }.visibility == View.GONE
        }
    }

    private fun verifyVoidOverlay(scenario: ActivityScenario<GameActivity>) {
        awaitUi(scenario, "Fullscreen void overlay laid out") {
            val overlay = field(hud(it), "voidPurchase").get(hud(it)) as? View
            overlay != null && overlay.width > 0 && overlay.height > 0
        }
        var animator: ValueAnimator? = null
        scenario.onActivity {
            val chrome = hud(it)
            val overlay = field(chrome, "voidPurchase").get(chrome) as View
            animator = field(overlay, "animator").get(overlay) as ValueAnimator
            animator!!.pause()
            assertSame(chrome, overlay.parent)
            assertSame(overlay, chrome.getChildAt(chrome.childCount - 1))
            assertEquals(chrome.width, overlay.width); assertEquals(chrome.height, overlay.height)
            assertEquals(0, overlay.left); assertEquals(0, overlay.top); assertEquals(1f, overlay.alpha, 0f)
        }
        for ((name, fraction) in listOf("first" to 0f, "pull" to .25f, "middle" to .5f, "reveal" to .78f, "last" to 1f)) {
            scenario.onActivity {
                val overlay = field(hud(it), "voidPurchase").get(hud(it)) as View
                animator!!.currentPlayTime = (animator!!.duration * fraction).toLong().coerceIn(1, animator!!.duration - 1)
                val bitmap = Bitmap.createBitmap(overlay.width, overlay.height, Bitmap.Config.ARGB_8888)
                overlay.draw(Canvas(bitmap))
                for ((x, y) in listOf(0 to 0, bitmap.width - 1 to 0, 0 to bitmap.height - 1, bitmap.width - 1 to bitmap.height - 1))
                    assertEquals("$name frame corner stays opaque", 255, Color.alpha(bitmap.getPixel(x, y)))
                bitmap.recycle()
            }
            capture("void-fullscreen-$name")
        }
        scenario.onActivity {
            val chrome = hud(it); val page = shop(it)
            val overlay = field(chrome, "voidPurchase").get(chrome)
            val scrolls = descendants(page).filterIsInstance<ScrollView>()
            val offsets = scrolls.map { view -> view.scrollY }
            val bank = Progress.coins; val count = Progress.voidPurchases
            val now = SystemClock.uptimeMillis()
            for ((action, y) in listOf(MotionEvent.ACTION_DOWN to chrome.height * .8f, MotionEvent.ACTION_MOVE to chrome.height * .25f, MotionEvent.ACTION_UP to chrome.height * .25f)) {
                val event = MotionEvent.obtain(now, now + 100, action, chrome.width * .5f, y, 0)
                assertTrue("Fullscreen overlay consumes touch", chrome.dispatchTouchEvent(event)); event.recycle()
            }
            chrome.navigateBack()
            assertSame(overlay, field(chrome, "voidPurchase").get(chrome)); assertSame(page, shop(it))
            assertEquals(offsets, scrolls.map { view -> view.scrollY })
            assertEquals(bank, Progress.coins); assertEquals(count, Progress.voidPurchases)
            animator!!.resume()
        }
        awaitUi(scenario, "Opaque scene enters a masked return instead of vanishing") {
            val overlay = field(hud(it), "voidPurchase").get(hud(it)) as? View
            (overlay?.let { view -> field(view, "returnAnimator").get(view) } as? ValueAnimator)?.let { returning ->
                returning.pause(); true
            } ?: false
        }
        // The old native reveal crashed when Android hid the window and called Animator.pause().
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        var previousOpaque = Int.MAX_VALUE
        for ((name, fraction) in listOf("early" to .15f, "middle" to .5f, "late" to .85f)) {
            scenario.onActivity {
                val chrome = hud(it)
                val overlay = field(chrome, "voidPurchase").get(chrome) as? View
                assertNotNull("$name return retains the touch-blocking mask", overlay)
                assertEquals(1f, overlay!!.alpha, 0f)
                val returning = field(overlay, "returnAnimator").get(overlay) as ValueAnimator
                returning.pause(); returning.currentPlayTime = (returning.duration * fraction).toLong()
                val bitmap = Bitmap.createBitmap(overlay.width, overlay.height, Bitmap.Config.ARGB_8888)
                overlay.draw(Canvas(bitmap))
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val opaque = pixels.count { pixel -> Color.alpha(pixel) == 255 }
                assertTrue("$name return keeps part of the opaque scene", opaque > 0)
                assertTrue("$name return uncovers part of the shop", pixels.any { pixel -> Color.alpha(pixel) == 0 })
                assertTrue("Return mask contracts progressively", opaque < previousOpaque)
                previousOpaque = opaque; bitmap.recycle()
                val bank = Progress.coins
                chrome.navigateBack()
                assertSame(overlay, field(chrome, "voidPurchase").get(chrome))
                assertEquals(bank, Progress.coins)
            }
            capture("void-return-$name", settle = 100)
        }
        scenario.onActivity {
            val overlay = field(hud(it), "voidPurchase").get(hud(it)) as View
            (field(overlay, "returnAnimator").get(overlay) as ValueAnimator).resume()
        }
    }
    private fun shop(activity: GameActivity) = field(hud(activity), "page").get(hud(activity)) as ShopView
    private fun shopReady(activity: GameActivity): Boolean {
        val page = field(hud(activity), "page").get(hud(activity)) as? ShopView ?: return false
        return field(page, "progress").getFloat(page) >= 1f && !field(page, "paying").getBoolean(page)
    }
    private fun purchase(activity: GameActivity, key: String): View {
        val page = shop(activity)
        val card = (field(page, "cards").get(page) as Map<*, *>)[key] as View
        val button = descendants(card).filterIsInstance<CandyButton>().last()
        button.requestRectangleOnScreen(android.graphics.Rect(0, 0, button.width, button.height), true)
        assertTrue("Real $key purchase button handles click", button.performClick())
        return button
    }
    private fun assertShopKeepsOnlyTheOffering(activity: GameActivity) {
        val page = shop(activity)
        val cards = field(page, "cards").get(page) as Map<*, *>
        assertTrue("The coin sink always remains in the main shop", cards.containsKey("void"))
        assertFalse("Discoveries belong only in the wardrobe", cards.keys.any { it.toString().startsWith("secret-") })
        assertFalse("The main shop contains no secret ability control", descendants(page).any {
            it.contentDescription?.toString()?.contains("unknown ability", ignoreCase = true) == true
        })
    }
    private fun buyDiscoveryInWardrobe(scenario: ActivityScenario<GameActivity>, category: Int, id: Int, reviewName: String? = null) {
        scenario.onActivity { hud(it).navigateBack() }
        awaitUi(scenario, "Shop closes before entering wardrobe") { field(hud(it), "page").get(hud(it)) == null }
        scenario.onActivity {
            call(hud(it), "openWardrobe")
            val page = field(hud(it), "page").get(hud(it)) as WardrobeView
            field(page, "cat").setInt(page, category); field(page, "index").setInt(page, id)
            call(page, "applyPreview"); call(page, "render")
        }
        awaitUi(scenario, "Discovered wardrobe item is ready") {
            val page = field(hud(it), "page").get(hud(it)) as WardrobeView
            page.alpha == 1f && !field(page, "paying").getBoolean(page)
        }
        if (reviewName != null) mysteryDisclosure(scenario, "wardrobe-$reviewName") { field(hud(it), "page").get(hud(it)) as WardrobeView }
        val bank = Progress.coins; val price = Wardrobe.price(category, id)
        scenario.onActivity {
            val page = field(hud(it), "page").get(hud(it)) as WardrobeView
            val button = field(page, "action").get(page) as CandyButton
            assertTrue("Real wardrobe BUY action purchases the discovery", button.performClick())
        }
        awaitUi(scenario, "Wardrobe discovery purchase settles") {
            val page = field(hud(it), "page").get(hud(it)) as WardrobeView
            Progress.owns(category, id) && !field(page, "paying").getBoolean(page)
        }
        assertEquals(bank - price, Progress.coins)
        capture("wardrobe-${reviewName ?: "cube"}-purchased")
        scenario.onActivity { hud(it).navigateBack() }
        awaitUi(scenario, "Wardrobe closes after purchase") { field(hud(it), "page").get(hud(it)) == null }
        scenario.onActivity { call(hud(it), "openShop") }
        awaitUi(scenario, "Main shop restored after wardrobe purchase", predicate = ::shopReady)
        scenario.onActivity { assertShopKeepsOnlyTheOffering(it) }
    }
    private fun gl(action: (CubeRun) -> Unit) {
        val done = CountDownLatch(1); var error: Throwable? = null
        Gdx.app.postRunnable {
            try { action(Gdx.app.applicationListener as CubeRun) } catch (failure: Throwable) { error = failure }
            finally { done.countDown() }
        }
        assertTrue("GL event completed", done.await(15, TimeUnit.SECONDS)); error?.let { throw it }
    }
    private fun restore(store: SharedPreferences, values: Map<String, *>) {
        val edit = store.edit().clear()
        for ((key, value) in values) when (value) {
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is String -> edit.putString(key, value)
            is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
        edit.commit()
    }

    @Test fun detachingAnActiveVoidReturnCancelsWithoutFinishingOrCrashing() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureHardwareAchievements") == "true")
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            awaitUi(scenario, "Detach review HUD ready") { field(it, "hud").get(it) != null }
            lateinit var overlay: VoidPurchaseView
            var finished = false
            scenario.onActivity {
                overlay = VoidPurchaseView(it, "Fine.", null) {}
                hud(it).addView(overlay, android.widget.FrameLayout.LayoutParams(-1, -1))
            }
            awaitUi(scenario, "Detach review overlay laid out") { overlay.width > 0 && overlay.height > 0 }
            scenario.onActivity {
                overlay.returnTo(overlay.width / 2f, overlay.height * .8f) { finished = true }
                val returning = field(overlay, "returnAnimator").get(overlay) as ValueAnimator
                returning.pause(); returning.currentPlayTime = returning.duration / 2
                hud(it).removeView(overlay)
                assertNull(overlay.parent)
                assertNull(field(overlay, "returnAnimator").get(overlay))
                assertFalse("Canceling a detached return cannot complete a purchase callback", finished)
            }
        }
    }

    @Test fun actualShopButtonsAndRunEventsReachThePlayer() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureHardwareAchievements") == "true")
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val scores = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
        val saved = prefs.all; val savedScores = scores.all
        try {
            // Keep Homeress pre-earned so Stay Centered remains this flow's first live toast.
            prefs.edit().clear().putInt("coins", 1000000).putInt("total_coins", 100000).putInt("void_purchases", 9)
                .putInt("best_coinless_score", 60).commit()
            scores.edit().clear().commit(); Settings.init(context); Settings.setDevMode(false); Progress.init(context)
            ActivityScenario.launch(GameActivity::class.java).use { scenario ->
                awaitUi(scenario, "HUD ready") { field(it, "hud").get(it) != null }
                scenario.onActivity { call(hud(it), "openShop") }
                awaitUi(scenario, "Shop entrance complete", predicate = ::shopReady)
                scenario.onActivity { purchase(it, "achievements") }
                assertTrue(Progress.achievementsUnlocked); assertEquals(997000, Progress.coins)
                assertTrue("Buying achievements never floods old awards", Achievements.drainUnlocks().isEmpty())
                awaitUi(scenario, "Achievement purchase settles", predicate = ::shopReady)
                scenario.onActivity {
                    val card = (field(shop(it), "cards").get(shop(it)) as Map<*, *>)["achievements"] as View
                    assertTrue(descendants(card).filterIsInstance<TextView>().any { text -> text.text.toString() == "Unlock the ability to collect achievments!" })
                    val badge = descendants(card).filterIsInstance<CandyButton>().single { button -> button.text.toString().contains("UNLOCKED") }
                    assertFalse("Unlocked badge is status, not another purchase", badge.isClickable)
                }
                capture("achievement-purchased")

                val initialPrice = Progress.voidPrice
                scenario.onActivity {
                    val button = purchase(it, "void")
                    val overlay = field(hud(it), "voidPurchase").get(hud(it)) as? View
                    assertNotNull("Void overlay starts in the purchase callback, before any coin flight", overlay)
                    (field(overlay!!, "animator").get(overlay) as ValueAnimator).pause()
                    button.performClick() // A second click during payment must not spend again.
                }
                assertEquals(10, Progress.voidPurchases); assertEquals(997000 - initialPrice, Progress.coins)
                verifyVoidOverlay(scenario)
                awaitUi(scenario, "Void animation completes", predicate = ::shopReady)
                assertTrue(Progress.secretAvailable(Wardrobe.CUBE, Skins.VOID_ID))
                assertFalse("Milestone reveals purchase, not free ownership", Progress.owns(Wardrobe.CUBE, Skins.VOID_ID))
                assertEquals("Fine.", Progress.voidLine)
                scenario.onActivity {
                    assertShopKeepsOnlyTheOffering(it)
                    val price = descendants(shop(it)).single { view -> view.tag == "void_price_button" }
                    val visible = android.graphics.Rect()
                    assertTrue(price.getGlobalVisibleRect(visible))
                    // The settled offering gives a small pulse on return. Compare its actual
                    // transformed bounds, not the unscaled layout height, to detect clipping.
                    val transform = android.graphics.Matrix()
                    price.transformMatrixToGlobal(transform)
                    val drawn = android.graphics.RectF(0f, 0f, price.width.toFloat(), price.height.toFloat())
                    transform.mapRect(drawn)
                    assertEquals("The full void price button is visible after returning", drawn.height(), visible.height().toFloat(), 2f)
                    val hostPosition = IntArray(2); hud(it).getLocationOnScreen(hostPosition)
                    assertTrue("The void return preserves space below the price button",
                        visible.bottom + UiKit(it).dp(8f) <= hostPosition[1] + hud(it).height)
                }
                capture("void-tenth-offering-continues")
                val nextPrice = Progress.voidPrice; val nextBank = Progress.coins
                scenario.onActivity { purchase(it, "void") }
                awaitUi(scenario, "Coin sink continues after reveal", predicate = ::shopReady)
                assertEquals(11, Progress.voidPurchases); assertEquals(nextBank - nextPrice, Progress.coins)
                assertEquals("There was more.", Progress.voidLine)
                assertFalse("The next offering does not require buying the cube", Progress.owns(Wardrobe.CUBE, Skins.VOID_ID))
                buyDiscoveryInWardrobe(scenario, Wardrobe.CUBE, Skins.VOID_ID)

                val recordGift = InstrumentationRegistry.getArguments().getString("recordGift") == "true"
                val giftRecordAt = SystemClock.uptimeMillis()
                if (recordGift) {
                    scenario.onActivity {
                        val card = (field(shop(it), "cards").get(shop(it)) as Map<*, *>)["mystery"] as View
                        card.requestRectangleOnScreen(android.graphics.Rect(0, 0, card.width, card.height), true)
                    }
                    val output = File(context.getExternalFilesDir(null), "achievements-hardware").apply { mkdirs() }
                    File(output, "gift-record-ready").writeText("ready")
                    SystemClock.sleep(2000)
                }
                val boxesBefore = Progress.boxesOpened
                scenario.onActivity { purchase(it, "mystery") }
                val bankAfterReward = Progress.coins
                awaitUi(scenario, "Actual mystery purchase opens presentation") { field(hud(it), "shopBox").get(hud(it)) != null }
                scenario.onActivity { call(field(hud(it), "shopBox").get(hud(it))!!, "tapBox") }
                awaitUi(scenario, "GL delivers already-banked mystery reward") {
                    val flow = field(hud(it), "shopBox").get(hud(it))!!
                    field(flow, "boxRewardReady").getBoolean(flow)
                }
                scenario.onActivity {
                    val flow = field(hud(it), "shopBox").get(hud(it))!!
                    if (field(flow, "boxBusy").getBoolean(flow)) call(flow, "tapBox")
                }
                capture("actual-purchased-reward")
                scenario.onActivity { call(field(hud(it), "shopBox").get(hud(it))!!, "tapBox") }
                awaitUi(scenario, "Reward returns to same shop") { field(hud(it), "shopBox").get(hud(it)) == null && shopReady(it) }
                assertEquals(boxesBefore + 1, Progress.boxesOpened); assertEquals(bankAfterReward, Progress.coins)
                capture("actual-returned-shop")
                if (recordGift) {
                    val remaining = giftRecordAt + 13000 - SystemClock.uptimeMillis()
                    if (remaining > 0) SystemClock.sleep(remaining)
                }
                scenario.onActivity {
                    val page = shop(it)
                    assertEquals(View.VISIBLE, page.visibility); assertEquals(1f, page.alpha, 0f)
                    val scroll = descendants(page).filterIsInstance<ScrollView>().single()
                    assertTrue("Returned shop remains inside its scroll bounds", scroll.scrollY >= 0 && scroll.scrollY <= (scroll.getChildAt(0).height - scroll.height).coerceAtLeast(0))
                }
                scenario.onActivity { hud(it).navigateBack() }
                awaitUi(scenario, "Menu restored") { field(hud(it), "page").get(hud(it)) == null }
                scenario.onActivity {
                    val menu = field(hud(it), "menu").get(hud(it)) as MainMenu
                    val achievements = field(menu, "achievements").get(menu) as View
                    assertEquals(View.VISIBLE, achievements.visibility)
                    assertTrue(achievements.performClick())
                }
                awaitUi(scenario, "Unlocked main-menu button opens achievement page") { field(hud(it), "page").get(hud(it)) is AchievementsView }
                capture("actual-achievement-page")
                scenario.onActivity { hud(it).navigateBack() }
                awaitUi(scenario, "Menu ready for real run") { field(hud(it), "page").get(hud(it)) == null }

                gl { game ->
                    Stage.mode = Stage.NONE; Stage.paused = false
                    game.onTap(360f, 760f)
                    Stage.paused = true
                }
                awaitUi(scenario, "Run HUD activation has cleared old shop awards") {
                    field(hud(it), "runStarted").getBoolean(hud(it))
                }
                gl { game ->
                    Stage.paused = false
                    val player = field(game, "player").get(game) as Player
                    val session = game.session as GameHostSession
                    assertEquals(cube.run.game.Lanes.count / 2, player.lane)
                    session.setScore(99)
                    assertEquals(99, Progress.bestCenteredScore)
                    assertEquals(0, Achievements.snapshot().single { it.definition.id == "center" }.earnedTiers)
                    session.addScore(1)
                    assertEquals(1, Achievements.snapshot().single { it.definition.id == "center" }.earnedTiers)
                    player.moveToLane(0)
                    repeat(67) {
                        // Overlay fixture; raw Android gesture behavior is covered by BouncerAchievementTest.
                        game.onSwipe(Gdx3DGame.LEFT)
                    }
                    assertEquals(67, field(game, "sideBounces").getInt(game))
                    session.setScore(501)
                    assertEquals("Earned center challenge survives leaving the lane", 100, Progress.bestCenteredScore)
                    // Freeze world simulation only after actual inputs; HUD remains live to deliver awards.
                    Stage.paused = true
                }
                assertEquals(67, Progress.maxRunBounces); assertEquals(501, Progress.bestRunScore)
                awaitUi(scenario, "Actual game award becomes a live popup", 12000) {
                    val toast = field(hud(it), "achievementToast").get(hud(it)) as AchievementToast
                    field(toast, "showing").getBoolean(toast)
                }
                SystemClock.sleep(300); capture("actual-game-event-toast")
                scenario.onActivity {
                    val toast = field(hud(it), "achievementToast").get(hud(it)) as AchievementToast
                    val title = field(toast, "title").get(toast) as android.widget.TextView
                    assertEquals("The first live award is the new center challenge", "Stay Centered", title.text.toString())
                }
            }
        } finally {
            Stage.paused = false; Stage.mode = Stage.NONE
            restore(prefs, saved); restore(scores, savedScores); Settings.setDevMode(false); Progress.init(context)
        }
    }

    @Test fun discoveriesStayInWardrobeWhileTheShopOfferingContinues() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureHardwareAchievements") == "true")
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val saved = prefs.all
        try {
            for ((category, id, name) in listOf(Triple(Wardrobe.CUBE, Skins.VOID_ID, "cube"),
                Triple(Wardrobe.TRAIL, Trails.VOID_ID, "trail"), Triple(Wardrobe.BUBBLE, BubbleSkins.VOID_ID, "bubble"))) {
                val milestone = when (category) { Wardrobe.CUBE -> 10; Wardrobe.TRAIL -> 20; else -> 30 }
                prefs.edit().clear().putInt("coins", 2000000).putInt("total_coins", 100000).putInt("void_purchases", milestone).commit()
                Settings.init(context); Settings.setDevMode(false); Progress.init(context)
                ActivityScenario.launch(GameActivity::class.java).use { scenario ->
                    awaitUi(scenario, "$name HUD ready") { field(it, "hud").get(it) != null }
                    scenario.onActivity { call(hud(it), "openShop") }
                    awaitUi(scenario, "$name shop ready", predicate = ::shopReady)
                    scenario.onActivity { assertShopKeepsOnlyTheOffering(it); purchase(it, "void") }
                    awaitUi(scenario, "$name milestone does not block the next offering", predicate = ::shopReady)
                    assertEquals(milestone + 1, Progress.voidPurchases)
                    assertFalse(Progress.owns(category, id))
                    scenario.onActivity { assertShopKeepsOnlyTheOffering(it) }
                    capture("shop-offering-after-$milestone")
                    buyDiscoveryInWardrobe(scenario, category, id, reviewName = name)
                }
            }
        } finally {
            Stage.paused = false; Stage.mode = Stage.NONE
            restore(prefs, saved); Settings.setDevMode(false); Progress.init(context)
        }
    }

    @Test fun narrowMenuAndLargeTextAchievementsMeasureWithoutChangingDeviceSettings() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureHardwareAchievements") == "true")
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val saved = prefs.all
        try {
            prefs.edit().clear().putBoolean("achievements_unlocked", true)
                .putInt("total_coins", 499999).putInt("achievement_best_score", 4999)
                .putInt("total_powerups", 9999).putInt("boxes_opened", 299)
                .putInt("coins", 2000000000).putInt("void_purchases", 100000).commit(); Progress.init(context)
            ActivityScenario.launch(GameActivity::class.java).use { scenario ->
                awaitUi(scenario, "Layout review HUD ready") { field(it, "hud").get(it) != null }
                scenario.onActivity { activity ->
                    val configuration = android.content.res.Configuration(activity.resources.configuration).apply { fontScale = 1.5f }
                    val kit = UiKit(activity.createConfigurationContext(configuration))
                    val width = kit.dp(280f); val height = kit.dp(720f)
                    fun measure(view: View) {
                        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                        view.layout(0, 0, width, height)
                    }
                    fun save(view: View, name: String) {
                        Anim.cancelTree(view)
                        descendants(view).forEach { child -> child.alpha = 1f; child.translationX = 0f; child.translationY = 0f; child.scaleX = 1f; child.scaleY = 1f }
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        view.draw(Canvas(bitmap))
                        val output = File(context.getExternalFilesDir(null), "achievements-hardware").apply { mkdirs() }
                        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                    }
                    val menu = MainMenu(activity, kit, {}, {}, {}, {}, {})
                    measure(menu)
                    val left = field(menu, "leftChips").get(menu) as View
                    val right = field(menu, "rightChips").get(menu) as View
                    assertTrue("280dp menu controls retain a horizontal or vertical gutter",
                        right.left - left.right >= kit.dp(8f) || right.top - left.bottom >= kit.dp(8f) || left.top - right.bottom >= kit.dp(8f))
                    assertTrue(right.right <= width); assertTrue(left.left >= 0)
                    save(menu, "menu-280dp-font150")

                    val page = AchievementsView(activity, kit) {}
                    measure(page)
                    fun checkCards() {
                        measure(page)
                        val title = field(page, "titleView").get(page) as TextView
                        assertEquals("Achievement title never splits mid-word", 1, title.lineCount)
                        assertEquals("Achievement title remains fully readable", 0, title.layout.getEllipsisCount(0))
                        assertTrue("Title glyphs fit their visible width", title.layout.getLineWidth(0) <= title.width - title.compoundPaddingLeft - title.compoundPaddingRight + 1f)
                        assertEquals("All achievement families share one list", Achievements.all.size,
                            descendants(page).count { it.tag?.toString()?.startsWith("achievement_card_") == true })
                        for (id in listOf("center", "homeress", "gambliphobic", "cookie")) {
                            val subtitle = page.findViewWithTag<TextView>("achievement_subtitle_$id")
                            assertTrue("$id goal fits at enlarged text size", subtitle.lineCount <= subtitle.maxLines)
                            assertEquals("$id complete goal remains visible", subtitle.text.length,
                                subtitle.layout.getLineEnd(subtitle.lineCount - 1))
                        }
                        val stats = descendants(page).filterIsInstance<TextView>().filter { view ->
                            view.tag == "achievement-goal" || view.tag == "achievement-counter"
                        }
                        assertTrue("Responsive review finds achievement labels and counters", stats.isNotEmpty())
                        for (stat in stats) {
                            assertTrue("${stat.text} has a measured width", stat.width > 0)
                            assertEquals("${stat.text} remains on one line", 1, stat.lineCount)
                            assertEquals("${stat.text} never ellipsizes", 0, stat.layout.getEllipsisCount(0))
                            assertTrue("${stat.text} fits without horizontal scrolling", stat.paint.measureText(stat.text.toString()) <= stat.width - stat.compoundPaddingLeft - stat.compoundPaddingRight + 1f)
                            assertTrue("${stat.text} has no clipped lines", stat.layout.height <= stat.height - stat.compoundPaddingTop - stat.compoundPaddingBottom)
                            for (line in 0 until stat.lineCount) assertTrue("${stat.text} glyphs fit", stat.layout.getLineWidth(line) <= stat.width - stat.compoundPaddingLeft - stat.compoundPaddingRight + 1f)
                        }
                    }
                    checkCards()
                    save(page, "achievements-280dp-font150")

                    val pricePage = ShopView(activity, kit, kit.iconPill(CoinIcon(), "2,000,000,000", Theme.INK, 15f),
                        onProgress = {}, onVoidPurchase = { _, _ -> }, onClose = {})
                    measure(pricePage)
                    val price = descendants(pricePage).single { it.tag == "void_price_button" } as CandyButton
                    ShopView::class.java.getDeclaredMethod("place", Float::class.javaPrimitiveType).apply { isAccessible = true }.invoke(pricePage, 1f)
                    descendants(pricePage).filterIsInstance<ScrollView>().forEach { it.scrollTo(0, it.getChildAt(0).height) }
                    save(pricePage, "void-price-280dp-font150")
                    val contentWidth = price.width - price.compoundPaddingLeft - price.compoundPaddingRight
                    val parent = price.parent as View
                    val desiredWidth = android.text.Layout.getDesiredWidth(price.text, price.paint)
                    val lineWidths = (0 until price.lineCount).joinToString(",") { price.layout.getLineWidth(it).toString() }
                    val priceDiagnostics = "textSizePx=${price.textSize}, available=$contentWidth, desired=$desiredWidth, " +
                        "lineWidths=[$lineWidths], layoutWidth=${price.layout.width}, view=${price.width}x${price.height}, " +
                        "measured=${price.measuredWidth}x${price.measuredHeight}, parent=${parent.width}x${parent.height}, " +
                        "padding=${price.compoundPaddingLeft}/${price.compoundPaddingTop}/${price.compoundPaddingRight}/${price.compoundPaddingBottom}"
                    assertEquals(1000000000, Progress.voidPrice)
                    assertTrue(price.text.toString().contains("1,000,000,000"))
                    assertEquals("Capped void price remains one line: $priceDiagnostics", 1, price.lineCount)
                    assertEquals(0, price.layout.getEllipsisCount(0))
                    assertTrue("Coin and full grouped price fit as one span: $priceDiagnostics", desiredWidth <= contentWidth + 1f)
                    val groupLeft = price.compoundPaddingLeft + price.layout.getLineLeft(0)
                    val groupRight = price.compoundPaddingLeft + price.layout.getLineRight(0)
                    assertTrue(groupLeft >= price.compoundPaddingLeft - 1f)
                    assertTrue(groupRight <= price.width - price.compoundPaddingRight + 1f)
                    assertEquals("Coin-price group is centered", price.width / 2f, (groupLeft + groupRight) / 2f, kit.dpf(1f))
                    val span = (price.text as android.text.Spanned).getSpans(0, price.text.length, CenteredImageSpan::class.java).single()
                    assertTrue("Fitted coin stays inside the button face", span.drawable.bounds.height() <= price.height - price.compoundPaddingTop - price.compoundPaddingBottom)
                    assertTrue("Fitted price line stays inside the button face", price.layout.height <= price.height - price.compoundPaddingTop - price.compoundPaddingBottom)
                }
            }
        } finally { restore(prefs, saved); Progress.init(context) }
    }
}
