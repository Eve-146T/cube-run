package cube.run.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.text.Spanned
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.GameActivity
import cube.run.data.Achievements
import cube.run.data.BubbleSkins
import cube.run.data.Progress
import cube.run.data.Scores
import cube.run.data.Settings
import cube.run.data.Shards
import cube.run.data.Skins
import cube.run.data.Trails
import cube.run.data.Wardrobe
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Destructive actions are exercised only against a separate test preference namespace. */
class ShopResetProgressTest {
    private class FixtureContext(base: Context) : ContextWrapper(base) {
        private val prefix = "shop_reset_test_${UUID.randomUUID()}_"
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences(prefix + name, mode)
        fun dispose() { for (name in listOf("progress", "scores", "settings")) baseContext.deleteSharedPreferences(prefix + name) }
    }

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
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun tagged(view: View, tag: String) = descendants(view).single { it.tag == tag }
    private fun confirmation(page: ShopView) = field(page, "resetConfirmation").get(page) as View
    private fun awaitUi(scenario: ActivityScenario<GameActivity>, label: String, predicate: (GameActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { ready = predicate(it) }
            if (!ready) SystemClock.sleep(40)
        }
        assertTrue(label, ready)
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureHardwareAchievements") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "achievements-hardware").apply { mkdirs() }
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun reveal(scenario: ActivityScenario<GameActivity>, tag: String) {
        scenario.onActivity {
            val view = tagged(shop(it), tag)
            view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
        }
        awaitUi(scenario, "$tag is fully visible in the shop") {
            val view = tagged(shop(it), tag)
            val visible = Rect()
            view.getGlobalVisibleRect(visible) && visible.height() >= view.height - 1
        }
    }

    private fun withFixture(test: (ActivityScenario<GameActivity>, FixtureContext) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val real = instrumentation.targetContext
        val fixture = FixtureContext(real)
        val previousDev = Settings.devMode
        try {
            ActivityScenario.launch(GameActivity::class.java).use { scenario ->
                awaitUi(scenario, "The host HUD is ready") { field(it, "hud").get(it) != null }
                val playerProgress = real.getSharedPreferences("progress", Context.MODE_PRIVATE).all
                val playerScores = real.getSharedPreferences("scores", Context.MODE_PRIVATE).all
                val playerSettings = real.getSharedPreferences("settings", Context.MODE_PRIVATE).all
                scenario.onActivity {
                    Settings.init(fixture); Settings.setDevMode(false)
                    Scores.init(fixture); Progress.init(fixture)
                }
                try { test(scenario, fixture) }
                finally {
                    assertEquals("The real player's progress is never reset", playerProgress, real.getSharedPreferences("progress", Context.MODE_PRIVATE).all)
                    assertEquals("The real player's scores are never reset", playerScores, real.getSharedPreferences("scores", Context.MODE_PRIVATE).all)
                    assertEquals("The real player's settings are never changed", playerSettings, real.getSharedPreferences("settings", Context.MODE_PRIVATE).all)
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                Settings.init(real); Settings.setDevMode(previousDev)
                Scores.init(real); Progress.init(real)
            }
            fixture.dispose()
        }
    }

    private fun openShop(scenario: ActivityScenario<GameActivity>) {
        scenario.onActivity { activity ->
            val owner = hud(activity)
            Hud::class.java.getDeclaredMethod("openShop").apply { isAccessible = true }.invoke(owner)
        }
        awaitUi(scenario, "Shop entrance completes") { field(shop(it), "progress").getFloat(shop(it)) >= 1f }
    }

    private fun seed(fixture: FixtureContext) {
        Settings.setDevMode(true)
        Settings.setSoundEnabled(false); Settings.setHapticsEnabled(false); Settings.setSmoothSensitivity(.73f)
        val edit = fixture.getSharedPreferences("progress", Context.MODE_PRIVATE).edit()
            .putInt("coins", 54321).putInt("bubbles", 67).putInt("revives", 3)
            .putInt("skin", Skins.VOID_ID).putInt("bubble_skin", BubbleSkins.VOID_ID).putInt("trail", Trails.VOID_ID)
            .putInt("owned_skins", (1 shl Skins.all.size) - 1)
            .putInt("owned_bubble_skins", (1 shl BubbleSkins.all.size) - 1)
            .putInt("owned_trails", (1 shl Trails.all.size) - 1)
            .putInt("total_coins", 999999).putInt("runs", 99).putInt("boxes_opened", 299).putInt("box_coin_streak", 2)
            .putBoolean("achievements_unlocked", true).putInt("achievement_best_score", 7654)
            .putInt("total_powerups", 5678).putInt("max_bubbles", 1234).putInt("max_run_bounces", 90)
            .putInt("void_purchases", 35).putInt("future_game_counter", 17)
        for (upgrade in Progress.upgrades + Progress.perks) edit.putInt(upgrade.key, upgrade.max)
        for (kind in Shards.all) edit.putInt("shards_${kind.id}", 42)
        for (achievement in Achievements.all) {
            edit.putInt("achievement_${achievement.id}", achievement.thresholds.size)
            edit.putInt("achievement_claimed_${achievement.id}", 1)
        }
        assertTrue(edit.commit())
        assertTrue(fixture.getSharedPreferences("scores", Context.MODE_PRIVATE).edit()
            .putInt("best_cuberun", 7654).putInt("best_other", 321).commit())
        Progress.init(fixture)
        Progress.enterDev()
        Progress.recordRunCoins(77)
    }

    @Test fun unlockedStatusHasNoPressAnimationAndResetExistsOnlyAtTheDevShopBottom() = withFixture { scenario, fixture ->
        scenario.onActivity {
            fixture.getSharedPreferences("progress", Context.MODE_PRIVATE).edit()
                .putBoolean("achievements_unlocked", true).putInt("coins", 54321).commit()
            Progress.init(fixture)
        }
        openShop(scenario)
        reveal(scenario, "achievements_unlocked_status")
        lateinit var status: CandyButton
        scenario.onActivity {
            val page = shop(it)
            status = tagged(page, "achievements_unlocked_status") as CandyButton
            assertFalse(status.isClickable); assertFalse(status.isFocusable)
            assertTrue("Check and label share a centered text run", status.text is Spanned &&
                (status.text as Spanned).getSpans(0, status.text.length, CenteredImageSpan::class.java).size == 1)
            assertTrue(descendants(page).none { child -> child.tag == "reset_progress" })
            val before = fixture.getSharedPreferences("progress", Context.MODE_PRIVATE).all
            assertFalse("Reset API is also unavailable outside dev mode", Progress.resetForDeveloper())
            assertEquals(before, fixture.getSharedPreferences("progress", Context.MODE_PRIVATE).all)
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, status.width / 2f, status.height / 2f, 0)
            try { assertFalse(status.dispatchTouchEvent(down)) } finally { down.recycle() }
        }
        SystemClock.sleep(120)
        scenario.onActivity {
            assertEquals("A status never sinks into a press without a release", 0f, (field(status, "painter").get(status) as CandyPainter).press, .001f)
        }
        capture("achievements-unlocked-status")
        scenario.onActivity {
            val page = shop(it)
            Settings.setDevMode(true)
            assertFalse("Prepared shop state tracks dev visibility even when the bank is unchanged", page.isCurrent())
            page.refreshAfterBox()
            val rows = field(page, "list").get(page) as ViewGroup
            assertEquals("reset_progress", rows.getChildAt(rows.childCount - 1).tag)
            Settings.setDevMode(false)
            page.refreshAfterBox()
            assertTrue(descendants(page).none { child -> child.tag == "reset_progress" })
        }
    }

    @Test fun cancelAndBackLeaveEveryFixtureValueUntouched() = withFixture { scenario, fixture ->
        scenario.onActivity { seed(fixture) }
        openShop(scenario)
        reveal(scenario, "reset_progress")
        capture("reset-dev-bottom")
        val before = fixture.getSharedPreferences("progress", Context.MODE_PRIVATE).all
        val beforeScores = fixture.getSharedPreferences("scores", Context.MODE_PRIVATE).all
        for (useBack in listOf(false, true)) {
            scenario.onActivity {
                val page = shop(it)
                assertTrue(tagged(page, "reset_progress").performClick())
                val dialog = confirmation(page)
                assertEquals("reset_progress_confirmation", dialog.tag)
                assertSame("Confirmation is above all shared menu controls", dialog,
                    (page.parent as ViewGroup).let { host -> host.getChildAt(host.childCount - 1) })
                assertEquals("Opening the confirmation changes no progress", before, fixture.getSharedPreferences("progress", Context.MODE_PRIVATE).all)
            }
            awaitUi(scenario, "Confirmation settles above the whole window") {
                val dialog = confirmation(shop(it))
                val face = field(dialog, "card").get(dialog) as View
                dialog.width == (dialog.parent as View).width && dialog.height == (dialog.parent as View).height &&
                    dialog.alpha >= .99f && face.alpha >= .99f
            }
            if (!useBack) capture("reset-confirmation")
            scenario.onActivity {
                val page = shop(it)
                val dialog = confirmation(page)
                val now = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(now, now, action, 1f, 1f, 0)
                    try { assertTrue("The confirmation owns even top-corner backdrop touches", dialog.dispatchTouchEvent(event)) }
                    finally { event.recycle() }
                }
                assertEquals(before, fixture.getSharedPreferences("progress", Context.MODE_PRIVATE).all)
                if (useBack) page.navigateBack() else assertTrue(tagged(dialog, "reset_progress_cancel").performClick())
            }
            awaitUi(scenario, "Cancellation closes only the confirmation") {
                field(shop(it), "resetConfirmation").get(shop(it)) == null && shop(it).isAttachedToWindow
            }
            assertEquals(before, fixture.getSharedPreferences("progress", Context.MODE_PRIVATE).all)
            assertEquals(beforeScores, fixture.getSharedPreferences("scores", Context.MODE_PRIVATE).all)
            if (!useBack) capture("reset-cancelled")
        }
    }

    @Test fun confirmedResetPersistsFreshDefaultsAndRefreshesHudWithoutChangingSettings() = withFixture { scenario, fixture ->
        scenario.onActivity { activity ->
            seed(fixture)
            hud(activity).setBest(7654); hud(activity).setBubbles(Progress.bubbles)
        }
        openShop(scenario)
        reveal(scenario, "reset_progress")
        val settingsBefore = fixture.getSharedPreferences("settings", Context.MODE_PRIVATE).all
        scenario.onActivity {
            val page = shop(it)
            assertTrue(tagged(page, "reset_progress").performClick())
            assertTrue(tagged(confirmation(page), "reset_progress_confirm").performClick())
        }
        awaitUi(scenario, "Reset completes and dismisses the confirmation") { field(shop(it), "resetConfirmation").get(shop(it)) == null }
        reveal(scenario, "reset_progress")
        capture("reset-complete-shop")
        scenario.onActivity { activity ->
            fun assertFresh() {
                assertEquals(0, Progress.coins); assertEquals(0, Progress.bubbles); assertEquals(0, Progress.revives)
                assertEquals(0, Progress.totalCoins); assertEquals(0, Progress.achievementCoins); assertEquals(0, Progress.runs)
                assertEquals(0, Progress.boxesOpened); assertEquals(0, Progress.bestRunScore); assertEquals(0, Progress.totalPowerups)
                assertEquals(0, Progress.maxBubbles); assertEquals(0, Progress.maxRunBounces); assertEquals(0, Progress.voidPurchases)
                assertFalse(Progress.achievementsUnlocked)
                for (cat in Wardrobe.cats) { assertEquals(0, Progress.equipped(cat)); assertTrue(Progress.owns(cat, 0)) }
                assertEquals(1, Progress.ownedSkins); assertEquals(1, Progress.ownedBubbleSkins); assertEquals(1, Progress.ownedTrails)
                for (upgrade in Progress.upgrades + Progress.perks) assertEquals(0, Progress.level(upgrade))
                for (kind in Shards.all) assertEquals(0, Progress.shards(kind.id))
                for (state in Achievements.snapshot()) { assertEquals(0, state.earnedTiers); assertEquals(0, state.claimedTiers) }
                assertTrue(Achievements.drainUnlocks().isEmpty())
                assertEquals(0, Scores.best("cuberun"))
            }
            assertFresh()
            assertTrue(Settings.devMode); assertFalse(Settings.soundEnabled); assertFalse(Settings.hapticsEnabled)
            assertEquals(settingsBefore, fixture.getSharedPreferences("settings", Context.MODE_PRIVATE).all)
            assertTrue(fixture.getSharedPreferences("scores", Context.MODE_PRIVATE).all.isEmpty())
            assertFalse(fixture.getSharedPreferences("progress", Context.MODE_PRIVATE).contains("bank_before_dev"))
            assertFalse(fixture.getSharedPreferences("progress", Context.MODE_PRIVATE).contains("future_game_counter"))
            val page = shop(activity)
            assertNull(page.darknessFocus())
            assertTrue(descendants(page).none { it.tag == "achievements_unlocked_status" })
            assertEquals("reset_progress", (field(page, "list").get(page) as ViewGroup).let { it.getChildAt(it.childCount - 1).tag })
            val owner = hud(activity)
            assertEquals("HUD best score refreshes immediately", 0, field(owner, "best").getInt(owner))
            val menu = field(owner, "menu").get(owner) as MainMenu
            assertEquals(View.GONE, (field(menu, "achievements").get(menu) as View).visibility)
            assertEquals("0", UiKit(activity).labelOf(field(page, "balance").get(page) as LinearLayout).text.toString())
            Progress.leaveDev()
            assertEquals("Turning dev off cannot resurrect the old bank", 0, Progress.coins)
            Progress.init(fixture); Scores.init(fixture)
            assertFresh()
        }
    }
}
