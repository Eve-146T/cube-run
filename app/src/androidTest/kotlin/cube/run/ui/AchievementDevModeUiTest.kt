package cube.run.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.GameActivity
import cube.run.data.Achievements
import cube.run.data.Progress
import cube.run.data.Settings
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/** Actual attached claim/shop controls; screenshots opt in with captureHardwareAchievements=true. */
class AchievementDevModeUiTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var prefs: SharedPreferences
    private lateinit var scores: SharedPreferences
    private lateinit var saved: Map<String, *>
    private lateinit var savedScores: Map<String, *>
    private var savedDev = false
    private fun field(owner: Any, name: String): java.lang.reflect.Field {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true } }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        throw NoSuchFieldException(name)
    }
    private fun hud(activity: GameActivity) = field(activity, "hud").get(activity) as Hud
    private fun page(activity: GameActivity) = field(hud(activity), "page").get(hud(activity)) as View
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun tagged(view: View, tag: String) = descendants(view).single { it.tag == tag }
    private fun awaitUi(scenario: ActivityScenario<GameActivity>, label: String, predicate: (GameActivity) -> Boolean) {
        var ready = false; val deadline = SystemClock.uptimeMillis() + 15000
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { ready = predicate(it) }
            if (!ready) SystemClock.sleep(60)
        }
        assertTrue(label, ready)
    }
    private fun open(scenario: ActivityScenario<GameActivity>, method: String) {
        scenario.onActivity { Hud::class.java.getDeclaredMethod(method).apply { isAccessible = true }.invoke(hud(it)) }
        awaitUi(scenario, "$method ready") {
            val view = field(hud(it), "page").get(hud(it)) as? View
            view != null && view.alpha == 1f && view.height > 0 && (view !is ShopView || field(view, "progress").getFloat(view) == 1f)
        }
    }
    private fun close(scenario: ActivityScenario<GameActivity>) {
        scenario.onActivity { hud(it).navigateBack() }
        awaitUi(scenario, "Page closed") { field(hud(it), "page").get(hud(it)) == null }
    }
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureHardwareAchievements") != "true") return
        SystemClock.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        val output = File(context.getExternalFilesDir(null), "achievements-hardware").apply { mkdirs() }
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun reveal(view: View) { view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true) }
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
    @Before fun prepare() {
        prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        scores = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
        saved = prefs.all; savedScores = scores.all; savedDev = Settings.devMode
        prefs.edit().clear().putBoolean("achievements_unlocked", true).putInt("coins", 10000).commit()
        scores.edit().clear().commit(); Settings.init(context); Settings.setDevMode(false); Progress.init(context)
    }
    @After fun restoreFixture() {
        Settings.setDevMode(false)
        restore(prefs, saved); restore(scores, savedScores); Progress.init(context); Settings.setDevMode(savedDev)
    }

    @Test fun developerCanClaimOnceWithoutRedundantRunnerBestAndProgressPersists() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            awaitUi(scenario, "HUD ready") { field(it, "hud").get(it) != null }
            Settings.setDevMode(true); Progress.enterDev()
            Progress.recordRunProgress(5001, 0)
            assertEquals(4, Achievements.snapshot().single { it.definition.id == "runner" }.earnedTiers)
            open(scenario, "openAchievements")
            val before = Progress.coins
            scenario.onActivity {
                assertEquals("Single-run score", (tagged(page(it), "achievement_subtitle_runner") as TextView).text.toString())
                assertFalse(descendants(tagged(page(it), "achievement_card_runner")).filterIsInstance<TextView>()
                    .any { view -> view.text.toString().contains("Best:") })
                assertTrue(descendants(tagged(page(it), "achievement_card_powerups")).filterIsInstance<TextView>()
                    .any { view -> view.text.toString() == "Power Ups collected" })
                assertTrue(descendants(tagged(page(it), "achievement_card_boxes")).filterIsInstance<TextView>()
                    .any { view -> view.text.toString() == "Mystery boxes opened" })
                val button = tagged(page(it), "achievement_claim_runner")
                assertTrue(button.isEnabled); reveal(button)
                assertTrue(button.performClick()); button.performClick()
            }
            awaitUi(scenario, "Developer claim and card update settle") { !field(page(it), "paying").getBoolean(page(it)) }
            assertEquals(before + 250, Progress.coins)
            assertEquals(1, Achievements.snapshot().single { it.definition.id == "runner" }.claimedTiers)
            scenario.onActivity {
                assertEquals("Single-run score", (tagged(page(it), "achievement_subtitle_runner") as TextView).text.toString())
                assertFalse(descendants(tagged(page(it), "achievement_card_runner")).filterIsInstance<TextView>()
                    .any { view -> view.text.toString().contains("Best:") })
                assertTrue(tagged(page(it), "achievement_claim_runner").contentDescription.toString().contains("750"))
            }
            assertEquals(5001, Achievements.snapshot().single { it.definition.id == "runner" }.value)
            capture("dev-runner-claimed-simple-subtitle")
            Progress.leaveDev(); Settings.setDevMode(false); Progress.init(context)
            assertEquals("Permanent claim payout survives leaving the developer bank", 10250, Progress.coins)
            assertEquals(1, Achievements.snapshot().single { it.definition.id == "runner" }.claimedTiers)
            assertEquals(5001, Achievements.snapshot().single { it.definition.id == "runner" }.value)
        }
    }

    @Test fun stayCenteredShowsAnIconAndClaimsItsCompletedChallengeExactlyOnce() {
        Progress.recordCenteredScore(99)
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            awaitUi(scenario, "HUD ready") { field(it, "hud").get(it) != null }
            open(scenario, "openAchievements")
            scenario.onActivity {
                val card = tagged(page(it), "achievement_card_center"); reveal(card)
                assertTrue(descendants(card).filterIsInstance<TextView>().any { view ->
                    view.text.toString() == "Reach 100 without leaving the middle lane"
                })
                assertFalse(Achievements.snapshot().single { state -> state.definition.id == "center" }.allClaimed)
            }
            close(scenario); Progress.recordCenteredScore(100)
            open(scenario, "openAchievements")
            scenario.onActivity {
                val card = tagged(page(it), "achievement_card_center"); reveal(card)
                assertTrue(descendants(card).any { view -> view.contentDescription == "Challenge complete" })
                assertFalse(descendants(card).any { view -> view.tag == "achievement_progress_center" || view.tag == "achievement-counter" })
                val icon = tagged(card, "achievement_icon_center") as android.widget.ImageView
                val visible = Rect()
                assertTrue("Centered challenge has a visible icon", icon.getGlobalVisibleRect(visible))
                assertTrue(visible.width() > 0 && visible.height() > 0)
                assertNotNull(icon.drawable)
                val bitmap = Bitmap.createBitmap(icon.width, icon.height, Bitmap.Config.ARGB_8888)
                // Draw only the artwork to avoid mistaking the raised category face for an icon.
                val canvas = android.graphics.Canvas(bitmap)
                canvas.translate(icon.paddingLeft.toFloat(), icon.paddingTop.toFloat())
                canvas.concat(icon.imageMatrix); icon.drawable.draw(canvas)
                var painted = 0
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width)
                    if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 0) painted++
                bitmap.recycle(); assertTrue("Centered icon paints actual artwork", painted > 10)
            }
            capture("stay-centered-complete")
            val before = Progress.coins
            scenario.onActivity {
                val button = tagged(page(it), "achievement_claim_center")
                assertTrue(button.performClick()); button.performClick()
            }
            awaitUi(scenario, "Centered claim settles") { !field(page(it), "paying").getBoolean(page(it)) }
            assertEquals(before + 1500, Progress.coins)
            scenario.onActivity {
                val card = tagged(page(it), "achievement_card_center"); reveal(card)
                assertTrue(descendants(card).any { view -> view.contentDescription == "Challenge complete" })
                val labels = descendants(card).filterIsInstance<TextView>().map { view -> view.text.toString() }
                assertTrue(labels.contains("Claimed"))
                assertFalse("Claimed challenge has no redundant score", labels.any { label -> label.any(Char::isDigit) })
                assertFalse(descendants(card).any { view -> view.tag == "achievement_claim_center" || view.tag == "achievement_progress_center" })
            }
            capture("stay-centered-claimed")
            Progress.init(context)
            assertTrue(Achievements.snapshot().single { it.definition.id == "center" }.allClaimed)
            assertEquals(0, Achievements.claim("center")); assertEquals(before + 1500, Progress.coins)
        }
    }

    @Test fun bouncerPersonalBestRemainsVisibleBeforeCompletionAndAfterClaim() {
        Progress.recordRunProgress(0, 42)
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            awaitUi(scenario, "HUD ready") { field(it, "hud").get(it) != null }
            open(scenario, "openAchievements")
            scenario.onActivity {
                val card = tagged(page(it), "achievement_card_bounces"); reveal(card)
                assertEquals("Best: 42\u00A0/\u00A067", (tagged(card, "achievement-counter") as TextView).text.toString())
            }
            capture("bouncer-best-in-progress")
            close(scenario); Progress.recordRunProgress(0, 93)
            open(scenario, "openAchievements")
            scenario.onActivity {
                val card = tagged(page(it), "achievement_card_bounces"); reveal(card)
                assertEquals("Best: 93 bounces", (tagged(card, "achievement_best_bounces") as TextView).text.toString())
                assertTrue(descendants(card).any { view -> view.contentDescription == "Challenge complete" })
            }
            capture("bouncer-complete-best-preserved")
            scenario.onActivity { assertTrue(tagged(page(it), "achievement_claim_bounces").performClick()) }
            awaitUi(scenario, "Bouncer claim settles") { !field(page(it), "paying").getBoolean(page(it)) }
            assertEquals(11500, Progress.coins)
            scenario.onActivity {
                val card = tagged(page(it), "achievement_card_bounces"); reveal(card)
                assertEquals("Claimed · Best: 93 bounces", (tagged(card, "achievement_best_bounces") as TextView).text.toString())
            }
            capture("bouncer-claimed-best-preserved")
            Progress.init(context); Progress.recordRunProgress(0, 7)
            assertEquals(93, Progress.maxRunBounces)
            assertTrue(Achievements.snapshot().single { it.definition.id == "bounces" }.allClaimed)
        }
    }

    @Test fun zeroLifetimeCoinsHideTheOfferingNormallyButDevCanPurchaseIt() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            awaitUi(scenario, "HUD ready") { field(it, "hud").get(it) != null }
            open(scenario, "openShop")
            scenario.onActivity { assertFalse((field(page(it), "cards").get(page(it)) as Map<*, *>).containsKey("void")) }
            assertEquals(0, Progress.totalCoins)
            close(scenario); Settings.setDevMode(true); Progress.enterDev()
            open(scenario, "openShop")
            val cost = Progress.voidPrice; val before = Progress.coins
            scenario.onActivity {
                assertTrue((field(page(it), "cards").get(page(it)) as Map<*, *>).containsKey("void"))
                val button = tagged(page(it), "void_price_button"); reveal(button)
                assertTrue(button.performClick())
            }
            awaitUi(scenario, "Developer offering finishes its fullscreen scene") {
                field(hud(it), "voidPurchase").get(hud(it)) == null && !field(page(it), "paying").getBoolean(page(it))
            }
            assertEquals(1, Progress.voidPurchases); assertEquals(before - cost, Progress.coins)
            assertEquals(0, Progress.totalCoins)
            capture("dev-zero-lifetime-void-purchased")
            close(scenario); Progress.leaveDev(); Settings.setDevMode(false)
            open(scenario, "openShop")
            scenario.onActivity { assertFalse((field(page(it), "cards").get(page(it)) as Map<*, *>).containsKey("void")) }
            assertEquals(1, Progress.voidPurchases)
        }
    }
}
