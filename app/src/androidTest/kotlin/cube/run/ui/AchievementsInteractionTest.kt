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
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Achievements
import cube.run.data.Progress
import cube.run.data.Settings
import cube.run.game.CubeRun
import cube.run.game.track.Track
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit device regressions: -e captureHardwareAchievements true. */
class AchievementsInteractionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun field(owner: Any, name: String): java.lang.reflect.Field {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true } }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        throw NoSuchFieldException(name)
    }
    private fun hud(activity: GameActivity) = field(activity, "hud").get(activity) as Hud
    private fun page(activity: GameActivity) = field(hud(activity), "page").get(hud(activity)) as AchievementsView
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun tag(view: View, value: String) = descendants(view).single { it.tag == value }
    private fun awaitUi(scenario: ActivityScenario<GameActivity>, label: String, predicate: (GameActivity) -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < end) {
            scenario.onActivity { ready = predicate(it) }
            if (!ready) SystemClock.sleep(60)
        }
        assertTrue(label, ready)
    }
    private fun capture(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        val output = File(context.getExternalFilesDir(null), "achievements-hardware").apply { mkdirs() }
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
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
    private fun gl(action: (CubeRun) -> Unit) {
        val done = CountDownLatch(1); var failure: Throwable? = null
        Gdx.app.postRunnable {
            try { action(Gdx.app.applicationListener as CubeRun) } catch (error: Throwable) { failure = error }
            finally { done.countDown() }
        }
        assertTrue(done.await(15, TimeUnit.SECONDS)); failure?.let { throw it }
    }
    private fun headerPixels(page: AchievementsView): IntArray {
        val top = field(page, "topBar").get(page) as View
        val title = field(page, "titleView").get(page) as View
        val topRect = Rect(0, 0, top.width, top.height); page.offsetDescendantRectToMyCoords(top, topRect)
        val titleRect = Rect(0, 0, title.width, title.height); page.offsetDescendantRectToMyCoords(title, titleRect)
        // Exclude the changing coin balance. Include back, title, and their shared header background.
        val width = titleRect.right.coerceAtMost(page.width)
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.BLACK); page.draw(canvas)
        val pixels = IntArray(width * topRect.height())
        bitmap.getPixels(pixels, 0, width, 0, topRect.top, width, topRect.height())
        bitmap.recycle(); return pixels
    }

    @Test fun partialScrollFlingAndClaimsCannotPaintOverTheHeader() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureHardwareAchievements") == "true")
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val saved = prefs.all
        try {
            prefs.edit().clear().putBoolean("achievements_unlocked", true).putInt("coins", 10000)
                .putInt("achievement_best_score", 501).putInt("total_coins", 500000)
                .putInt("achievement_claimed_coins", 3)
                .putInt("max_bubbles", 1734).putInt("max_run_bounces", 93)
                .putInt("best_coinless_score", 60).putInt("max_run_missed_boxes", 10)
                .putInt("total_mute_toggles", 1000).commit()
            Settings.init(context); Settings.setDevMode(false); Progress.init(context)
            ActivityScenario.launch(GameActivity::class.java).use { scenario ->
                awaitUi(scenario, "HUD ready") { field(it, "hud").get(it) != null }
                scenario.onActivity { Hud::class.java.getDeclaredMethod("openAchievements").apply { isAccessible = true }.invoke(hud(it)) }
                awaitUi(scenario, "One-list achievement page ready") { page(it).alpha == 1f && page(it).height > 0 }
                SystemClock.sleep(500)
                lateinit var baseline: IntArray
                scenario.onActivity {
                    assertEquals(11, descendants(page(it)).count { view -> view.tag?.toString()?.startsWith("achievement_card_") == true })
                    assertTrue("Lifetime target uses US grouping", descendants(tag(page(it), "achievement_card_coins")).filterIsInstance<TextView>()
                        .any { view -> view.text.toString().contains("500,000") })
                    baseline = headerPixels(page(it))
                    (field(page(it), "scroll").get(page(it)) as ScrollView).scrollTo(0, UiKit(it).dp(120f))
                }
                SystemClock.sleep(200)
                scenario.onActivity { assertArrayEquals("Partial scroll paints only inside its viewport", baseline, headerPixels(page(it))) }
                capture("achievement-partial-scroll-clipped")
                scenario.onActivity { (field(page(it), "scroll").get(page(it)) as ScrollView).fling(7000) }
                repeat(8) {
                    SystemClock.sleep(50)
                    scenario.onActivity { assertArrayEquals("Fling frame keeps header unobscured", baseline, headerPixels(page(it))) }
                }
                capture("achievement-fling-clipped")
                scenario.onActivity { (field(page(it), "scroll").get(page(it)) as ScrollView).fling(-12000) }
                repeat(8) {
                    SystemClock.sleep(50)
                    scenario.onActivity { assertArrayEquals("Reverse fling/overscroll keeps header unobscured", baseline, headerPixels(page(it))) }
                }
                scenario.onActivity {
                    val scroll = field(page(it), "scroll").get(page(it)) as ScrollView
                    scroll.fling(0); scroll.scrollTo(0, UiKit(it).dp(120f))
                    val claim = tag(page(it), "achievement_claim_runner")
                    assertTrue(claim.performClick()); claim.performClick()
                }
                awaitUi(scenario, "Bronze reward claimed exactly once") {
                    Achievements.snapshot().single { it.definition.id == "runner" }.claimedTiers == 1 && !field(page(it), "paying").getBoolean(page(it))
                }
                assertEquals(10250, Progress.coins)
                SystemClock.sleep(500)
                scenario.onActivity { assertArrayEquals("Claim rebuild cannot paint above viewport", baseline, headerPixels(page(it))) }
                capture("achievement-scrolled-after-claim")
                for ((id, reward) in listOf("bubbles" to 2000, "bounces" to 1500, "homeress" to 1500, "gambliphobic" to 1500, "cookie" to 2000)) {
                    val before = Progress.coins
                    scenario.onActivity {
                        val card = tag(page(it), "achievement_card_$id")
                        assertFalse("Completed challenge hides all counters", descendants(card).any { view -> view.tag == "achievement-counter" })
                        assertFalse("Completed challenge hides progress bars", descendants(card).any { view -> view.tag == "achievement_progress_$id" })
                        assertTrue(descendants(card).filterIsInstance<TextView>().any { view -> view.text.toString().contains("Complete", true) })
                        val claim = tag(card, "achievement_claim_$id")
                        claim.requestRectangleOnScreen(Rect(0, 0, claim.width, claim.height), true)
                        assertTrue(claim.performClick())
                    }
                    awaitUi(scenario, "$id claimed") { Achievements.snapshot().single { it.definition.id == id }.allClaimed && !field(page(it), "paying").getBoolean(page(it)) }
                    assertEquals(before + reward, Progress.coins)
                    SystemClock.sleep(300)
                    scenario.onActivity {
                        val card = tag(page(it), "achievement_card_$id")
                        assertTrue(descendants(card).filterIsInstance<TextView>().any { view -> view.text.toString().startsWith("Claimed", true) })
                        if (id == "bounces") assertTrue(descendants(card).filterIsInstance<TextView>().any { view -> view.text.toString().contains("Best: 93 bounces") })
                        assertArrayEquals("Challenge claim keeps header clear", baseline, headerPixels(page(it)))
                    }
                    capture("achievement-$id-checked-and-claimed")
                }
                scenario.onActivity {
                    val header = field(page(it), "topBar").get(page(it)) as View
                    val back = descendants(header).first { view -> view is CandyChip }
                    val position = IntArray(2); back.getLocationInWindow(position)
                    val now = SystemClock.uptimeMillis()
                    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                        val event = MotionEvent.obtain(now, now + 60, action, position[0] + back.width / 2f, position[1] + back.height / 2f, 0)
                        it.dispatchTouchEvent(event); event.recycle()
                    }
                }
                awaitUi(scenario, "Header back button remains touchable after scrolling and claims") { field(hud(it), "page").get(hud(it)) == null }
            }
        } finally { restore(prefs, saved); Progress.init(context) }
    }

    @Test fun muteControlsCountUserChangesButRefreshesDoNot() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureHardwareAchievements") == "true")
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val saved = prefs.all
        Settings.init(context)
        val savedSound = Settings.soundEnabled
        try {
            prefs.edit().clear().putBoolean("achievements_unlocked", true).putInt("total_mute_toggles", 998).commit()
            Settings.setDevMode(false); Progress.init(context)
            ActivityScenario.launch(GameActivity::class.java).use { scenario ->
                awaitUi(scenario, "HUD ready") { field(it, "hud").get(it) != null }
                scenario.onActivity { activity ->
                    val menu = field(hud(activity), "menu").get(hud(activity)) as MainMenu
                    val label = activity.getString(cube.run.R.string.cd_sound)
                    fun soundButton(view: View) = descendants(view).single {
                        it is CandyChip && it.contentDescription?.toString()?.startsWith(label) == true
                    }
                    Settings.setSoundEnabled(!Settings.soundEnabled)
                    menu.refresh(); menu.refresh()
                    assertEquals("Programmatic settings and refresh do not count", 998, Progress.totalMuteToggles)
                    val before = Settings.soundEnabled
                    assertTrue(soundButton(menu).performClick())
                    assertEquals(!before, Settings.soundEnabled)
                    assertEquals("Menu toggle counts once", 999, Progress.totalMuteToggles)
                    val pause = PauseSheet(activity, UiKit(activity), {}, {}, {})
                    assertEquals("Constructing pause controls does not count", 999, Progress.totalMuteToggles)
                    assertTrue(soundButton(pause).performClick())
                    assertEquals(before, Settings.soundEnabled)
                    assertEquals("Pause toggle counts the unmute as well", 1000, Progress.totalMuteToggles)
                    val cookie = Achievements.snapshot().single { it.definition.id == "cookie" }
                    assertEquals("Cookie Clicker unlocks at the actual thousandth toggle", 1, cookie.earnedTiers)
                    Anim.cancelTree(pause)
                }
            }
        } finally {
            restore(prefs, saved); Progress.init(context); Settings.setSoundEnabled(savedSound)
        }
    }

    @Test fun newlyRevealedRewardBarsFillWithoutRebuildingThePage() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureHardwareAchievements") == "true")
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val saved = prefs.all
        try {
            prefs.edit().clear().putBoolean("achievements_unlocked", true)
                .putInt("total_powerups", 100).putInt("boxes_opened", 10).commit()
            Settings.init(context); Settings.setDevMode(false); Progress.init(context)
            ActivityScenario.launch(GameActivity::class.java).use { scenario ->
                awaitUi(scenario, "HUD ready") { field(it, "hud").get(it) != null }
                scenario.onActivity { Hud::class.java.getDeclaredMethod("openAchievements").apply { isAccessible = true }.invoke(hud(it)) }
                awaitUi(scenario, "Achievement page laid out") { page(it).alpha == 1f && page(it).height > 0 }
                SystemClock.sleep(1300)
                lateinit var bar: View
                scenario.onActivity {
                    bar = tag(page(it), "achievement_progress_boxes")
                    assertFalse("Mystery Seeker begins outside the viewport", bar.getLocalVisibleRect(Rect()))
                    // Scroll a cached lower card into view; do not force page.draw or rebuild its views.
                    bar.requestRectangleOnScreen(Rect(0, 0, bar.width, bar.height), true)
                }
                awaitUi(scenario, "Newly visible claim-ready bar reaches its full target") {
                    bar.getLocalVisibleRect(Rect()) && field(bar, "amount").getFloat(bar) >= .999f
                }
                scenario.onActivity {
                    assertSame("Scrolling preserves the existing card/bar", bar, tag(page(it), "achievement_progress_boxes"))
                    val bitmap = Bitmap.createBitmap(bar.width, bar.height, Bitmap.Config.ARGB_8888)
                    bar.draw(Canvas(bitmap))
                    assertEquals("Full reward bar paints its earned green fill", Theme.MINT, bitmap.getPixel(bar.width / 2, bar.height / 2))
                    bitmap.recycle()
                }
                capture("newly-visible-reward-bar-filled")
            }
        } finally { restore(prefs, saved); Progress.init(context) }
    }

    /** Root can select this method alone while adb screenrecord records the moving run and popup. */
    @Test fun liveRunnerPopupWhileWorldMoves() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureHardwareAchievements") == "true")
        val recordPopup = InstrumentationRegistry.getArguments().getString("recordPopup") == "true"
        val output = File(context.getExternalFilesDir(null), "achievements-hardware").apply { mkdirs() }
        val recordReady = File(output, "popup-record-ready")
        recordReady.delete()
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val scores = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
        val saved = prefs.all; val savedScores = scores.all
        try {
            // Isolate Runner's live toast; the score challenges have their own live-run coverage.
            prefs.edit().clear().putBoolean("achievements_unlocked", true)
                .putInt("best_centered_score", 100).putInt("best_coinless_score", 60).commit(); scores.edit().clear().commit()
            Settings.init(context); Settings.setDevMode(false); Progress.init(context)
            ActivityScenario.launch(GameActivity::class.java).use { scenario ->
                awaitUi(scenario, "Live HUD ready") { field(it, "hud").get(it) != null }
                if (recordPopup) {
                    awaitUi(scenario, "Live game screen visible for recording") { hud(it).isShown && hud(it).alpha == 1f && hud(it).width > 0 }
                    // Root watches this marker, then starts screenrecord. Sleep is on the test thread.
                    recordReady.writeText("ready\n")
                    SystemClock.sleep(2000)
                }
                gl { game -> Stage.mode = Stage.NONE; Stage.paused = false; game.onTap(360f, 760f) }
                awaitUi(scenario, "Running HUD active") { field(hud(it), "runStarted").getBoolean(hud(it)) }
                gl { game -> game.session.setScore(480) }
                var sawPopup = false; var startTime = 0f; var endTime = 0f
                gl { startTime = field(it, "time").getFloat(it) }
                repeat(if (recordPopup) 130 else 75) { step ->
                    gl { game ->
                        // Keep a safe road while production session updates advance a genuinely moving run.
                        (field(game, "track").get(game) as Track).rows.clear()
                        if (step in (if (recordPopup) 10..29 else 0..19)) game.session.addScore(2)
                        assertFalse(game.session.isOver); assertFalse(Stage.paused)
                        endTime = field(game, "time").getFloat(game)
                    }
                    scenario.onActivity {
                        val toast = field(hud(it), "achievementToast").get(hud(it)) as AchievementToast
                        if (field(toast, "showing").getBoolean(toast)) {
                            val title = field(toast, "title").get(toast) as TextView
                            if (title.text.toString() == "Good Runner") sawPopup = true
                        }
                    }
                    if (step == 32) capture("live-running-achievement-popup")
                    SystemClock.sleep(100)
                }
                assertTrue("Production score events show a popup during live play", sawPopup)
                assertTrue("World time advances throughout popup delivery", endTime - startTime > 5f)
                assertTrue(Progress.bestRunScore > 500)
            }
        } finally {
            Stage.paused = false; Stage.mode = Stage.NONE
            restore(prefs, saved); restore(scores, savedScores); Progress.init(context)
        }
    }
}
