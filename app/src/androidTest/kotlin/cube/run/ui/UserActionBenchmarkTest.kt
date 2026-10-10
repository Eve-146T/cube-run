package cube.run.ui

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage.RESUMED
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.R
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.game.CubeRun
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Maintained action-to-frame benchmark. Cold process launches are measured by actions.py. */
class UserActionBenchmarkTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: GameActivity
    private val rows = JSONArray()
    private var pendingAction: String? = null
    private val environment = JSONObject()
    private fun ui(action: () -> Unit) = inst.runOnMainSync(action)
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner: Any, name: String): T {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true }.get(owner) as T }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        error("Missing $name")
    }
    private fun hud() = field<Hud>(activity, "hud")
    private fun menu() = field<MainMenu>(hud(), "menu")
    private fun game() = Gdx.app.applicationListener as CubeRun
    private fun settled(view: View) = view.isShown && view.alpha >= .99f &&
        kotlin.math.abs(view.translationX) < .5f && kotlin.math.abs(view.translationY) < .5f &&
        kotlin.math.abs(view.scaleX - 1f) < .001f && kotlin.math.abs(view.scaleY - 1f) < .001f
    private fun sheetReady(sheet: Sheet?) = sheet != null && settled(sheet) && settled(field<View>(sheet, "card"))
    private fun menuReady() = field<Page?>(hud(), "page") == null && !field<Boolean>(hud(), "runStarted") &&
        menu().isShown && listOf("top", "middle", "leftChips", "rightChips", "bank")
        .all { settled(field<View>(menu(), it)) }
    private fun pageReady(): Boolean {
        val page = field<Page?>(hud(), "page") ?: return false
        if (page is AchievementsView && !page.contentReady) return false
        return settled(page) && settled(field<View>(page, "content")) &&
            (page !is ShopView || Stage.shopProgress >= .999f)
    }
    private fun runReady() = field<Boolean>(hud(), "runStarted") &&
        field<PauseSheet?>(hud(), "pauseSheet") == null && settled(field<View>(hud(), "pauseChip")) &&
        (Progress.zenRun || settled(field<View>(hud(), "topBox")))
    private fun awaitReady(label: String, ready: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            var complete = false
            ui {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(RESUMED)
                    .filterIsInstance<GameActivity>().firstOrNull { !it.isFinishing }?.let { activity = it }
                complete = runCatching { ready() }.getOrDefault(false)
            }
            if (complete) return
            SystemClock.sleep(5)
        }
        error("$label did not become visible and settled")
    }
    private fun measure(name: String, sample: Int, trigger: () -> Unit, ready: () -> Boolean) {
        pendingAction = name
        val firstFrame = CountDownLatch(1)
        val originalActivity = activity
        var start = 0L
        var firstMs = 0.0
        ui {
            start = SystemClock.elapsedRealtimeNanos()
            trigger()
            val root = activity.window.decorView
            val painted = {
                if (firstFrame.count > 0) {
                    firstMs = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
                    firstFrame.countDown()
                }
            }
            if (Build.VERSION.SDK_INT >= 29) root.viewTreeObserver.registerFrameCommitCallback(painted)
            else root.postOnAnimation(painted)
            root.invalidate()
        }
        awaitReady(name, ready)
        val settledMs = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
        // Older builds replace the host on RESTART. Its destroyed window may never
        // commit; measure the replacement window instead of assuming host reuse.
        var firstFrameWindow = "original"
        if (firstFrame.count > 0) {
            firstFrameWindow = if (activity === originalActivity) "original" else "replacement"
            ui {
                val root = activity.window.decorView
                val painted = {
                    if (firstFrame.count > 0) {
                        firstMs = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
                        firstFrame.countDown()
                    }
                }
                if (Build.VERSION.SDK_INT >= 29) root.viewTreeObserver.registerFrameCommitCallback(painted)
                else root.postOnAnimation(painted)
                root.invalidate()
            }
        }
        assertTrue("$name response frame", firstFrame.await(10, TimeUnit.SECONDS))
        val scene = CountDownLatch(1)
        game().afterFreshSceneFrame { scene.countDown() }
        assertTrue("$name fresh GL scene", scene.await(10, TimeUnit.SECONDS))
        val presented = CountDownLatch(1)
        ui {
            val root = activity.window.decorView
            if (Build.VERSION.SDK_INT >= 29) root.viewTreeObserver.registerFrameCommitCallback { presented.countDown() }
            else root.postOnAnimation { presented.countDown() }
            root.invalidate()
        }
        assertTrue("$name settled UI frame", presented.await(10, TimeUnit.SECONDS))
        rows.put(JSONObject().put("action", name).put("sample", sample)
            .put("phase", if (sample == 0) "first" else "repeat")
            .put("first_ui_frame_ms", firstMs)
            .put("first_ui_frame_window", firstFrameWindow)
            .put("controls_settled_ms", settledMs)
            .put("ready_ms", (SystemClock.elapsedRealtimeNanos() - start) / 1e6))
        pendingAction = null
    }
    private fun clickDescription(root: View, resource: Int) {
        val description = activity.getString(resource)
        fun find(view: View): View? {
            if (view.contentDescription?.toString() == description && view.isShown && view.isClickable) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        val button = find(root) ?: error("Missing visible control: $description")
        assertTrue(button.performClick())
    }
    private fun tapRoad() {
        val surface = field<View>(activity, "gameSurface")
        val position = IntArray(2); surface.getLocationOnScreen(position)
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, time, action,
                position[0] + surface.width * .5f, position[1] + surface.height * .55f, 0)
            assertTrue(inst.uiAutomation.injectInputEvent(event, false)); event.recycle()
        }
    }

    @Test fun userActions() {
        val args = InstrumentationRegistry.getArguments()
        val repeats = (args.getString("repeats")?.toInt() ?: 5).coerceIn(1, 100)
        val originalUnlocked = Progress.achievementsUnlocked
        val unlockedField = Progress::class.java.getDeclaredField("achievementsUnlocked").apply { isAccessible = true }
        activity = inst.startActivitySync(Intent(inst.targetContext, GameActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(Hud.EXTRA_AUTOSTART, false)) as GameActivity
        try {
            ui { unlockedField.setBoolean(null, true); menu().refresh() }
            awaitReady("initial menu", ::menuReady)
            ui {
                @Suppress("DEPRECATION")
                val display = activity.windowManager.defaultDisplay
                environment.put("active_refresh_hz", display.refreshRate)
                    .put("preferred_refresh_hz", activity.window.attributes.preferredRefreshRate)
                    .put("sound_enabled", cube.run.data.Settings.soundEnabled)
                    .put("haptics_enabled", cube.run.data.Settings.hapticsEnabled)
                    .put("start_speed", cube.run.data.Settings.effectiveStartSpeed)
                    .put("zen_run", Progress.zenRun)
            }
            for (sample in 0 until repeats) {
                for ((name, description) in listOf("shop" to R.string.cd_shop, "wardrobe" to R.string.cd_skins,
                    "achievements" to R.string.achievements_title, "settings" to R.string.settings_title)) {
                    measure("open_$name", sample, { clickDescription(menu(), description) }, ::pageReady)
                    if (name == "settings") {
                        measure("open_languages", sample, { clickDescription(field<Page>(hud(), "page"), R.string.cd_languages) },
                            { sheetReady(field<LanguageSheet?>(hud(), "languageSheet")) })
                        measure("back_languages", sample, { field<LanguageSheet>(hud(), "languageSheet").dismiss() },
                            { field<LanguageSheet?>(hud(), "languageSheet") == null && pageReady() })
                    }
                    measure("back_$name", sample, { field<Page>(hud(), "page").navigateBack() }, ::menuReady)
                }
                if (cube.run.BuildConfig.DEBUG) {
                    measure("open_settings_for_sections", sample,
                        { clickDescription(menu(), R.string.settings_title) }, ::pageReady)
                    measure("open_sections", sample,
                        { clickDescription(field<Page>(hud(), "page"), R.string.cd_sections) }, ::pageReady)
                    measure("back_sections", sample, { field<Page>(hud(), "page").navigateBack() }, ::menuReady)
                }
                measure("launch_run", sample, ::tapRoad, ::runReady)
                measure("open_pause", sample, { field<View>(hud(), "pauseChip").performClick() },
                    { sheetReady(field<PauseSheet?>(hud(), "pauseSheet")) })
                measure("resume_run", sample,
                    { field<View>(field<PauseSheet>(hud(), "pauseSheet"), "resume").performClick() }, ::runReady)
                measure("open_pause_before_restart", sample, { field<View>(hud(), "pauseChip").performClick() },
                    { sheetReady(field<PauseSheet?>(hud(), "pauseSheet")) })
                val oldGame = game()
                measure("pause_restart", sample,
                    { field<View>(field<PauseSheet>(hud(), "pauseSheet"), "restart").performClick() }, ::runReady)
                rows.getJSONObject(rows.length() - 1).put("reused_game", oldGame === game())
                measure("open_pause_after_restart", sample, { field<View>(hud(), "pauseChip").performClick() },
                    { sheetReady(field<PauseSheet?>(hud(), "pauseSheet")) })
                measure("pause_to_menu", sample,
                    { field<View>(field<PauseSheet>(hud(), "pauseSheet"), "menu").performClick() }, ::menuReady)
            }
        } finally {
            val report = JSONObject().put("schema_version", 1).put("repeats", repeats)
                .put("device", Build.MODEL).put("sdk", Build.VERSION.SDK_INT)
                .put("ui_frame_metric", if (Build.VERSION.SDK_INT >= 29) "frame_commit" else "animation_callback")
                .put("environment", environment).put("pending_action", pendingAction ?: JSONObject.NULL)
                .put("samples", rows)
            File(activity.filesDir, "user-action-benchmark.json").writeText(report.toString(2))
            ui { unlockedField.setBoolean(null, originalUnlocked); activity.finish() }
        }
    }
}
