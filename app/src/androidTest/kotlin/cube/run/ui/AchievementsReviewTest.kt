package cube.run.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.GameActivity
import cube.run.data.Progress
import cube.run.core.Stage
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit screenshot review on an isolated emulator: -e captureAchievements true. */
class AchievementsReviewTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun call(owner: Any, name: String) = owner.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(owner)
    private fun capture(name: String) {
        SystemClock.sleep(1300)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        val folder = InstrumentationRegistry.getArguments().getString("captureFolder") ?: "achievements-review"
        val output = File(context.getExternalFilesDir(null), folder).apply { mkdirs() }
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun scrolls(view: View): List<ScrollView> = if (view is ScrollView) listOf(view) else
        if (view is ViewGroup) (0 until view.childCount).flatMap { scrolls(view.getChildAt(it)) } else emptyList()
    private fun clickText(view: View, text: String): Boolean {
        if (view is TextView && view.text.toString().equals(text, ignoreCase = true)) { view.performClick(); return true }
        return view is ViewGroup && (0 until view.childCount).any { clickText(view.getChildAt(it), text) }
    }

    @Test fun captureProgressAndMysteryStates() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureAchievements") == "true")
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val saved = prefs.all
        try {
            val states = InstrumentationRegistry.getArguments().getString("captureState")?.let { listOf(it) }
                ?: listOf("locked", "shop-unlocked", "progress", "unclaimed", "diamond-ready", "claimed", "challenge-complete", "void9", "void10", "void20", "void30")
            for (state in states) {
                val complete = state in listOf("complete", "unclaimed", "diamond-ready", "claimed")
                val challengeComplete = complete || state == "challenge-complete"
                val edit = prefs.edit().clear().putInt("coins", 1000000).putInt("total_coins", if (complete) 500000 else if (state.startsWith("void")) 100000 else 18400)
                    .putBoolean("achievements_unlocked", state != "locked")
                    .putInt("achievement_best_score", if (complete) 5001 else 742)
                    .putInt("owned_skins", if (complete) 0xFFFFFF else 0x7F)
                    .putInt("total_powerups", if (complete) 10000 else 637).putInt("boxes_opened", if (complete) 300 else 31)
                    .putInt("max_bubbles", if (challengeComplete) 1734 else 426)
                    .putInt("best_centered_score", if (challengeComplete) 100 else 63)
                    .putInt("best_coinless_score", if (challengeComplete) 60 else 42)
                    .putInt("max_run_missed_boxes", if (challengeComplete) 10 else 7)
                    .putInt("total_mute_toggles", if (challengeComplete) 1000 else 637)
                    .putInt("max_run_bounces", if (challengeComplete) 93 else 42).putInt("void_purchases", state.removePrefix("void").toIntOrNull() ?: 0)
                if (state == "claimed") for (definition in cube.run.data.Achievements.all)
                    edit.putInt("achievement_claimed_${definition.id}", definition.thresholds.size)
                if (state == "diamond-ready") for (definition in cube.run.data.Achievements.all.filter { it.tiered })
                    edit.putInt("achievement_claimed_${definition.id}", 3)
                edit.commit()
                Progress.init(context)
                ActivityScenario.launch(GameActivity::class.java).use { scenario ->
                    scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
                    var ready = false
                    val deadline = SystemClock.uptimeMillis() + 15000
                    while (!ready && SystemClock.uptimeMillis() < deadline) {
                        scenario.onActivity { ready = field(it, "hud").get(it) != null }
                        if (!ready) SystemClock.sleep(100)
                    }
                    assertTrue("HUD becomes ready", ready)
                    if (!state.startsWith("void")) capture("menu-$state")
                    scenario.onActivity { activity ->
                        val hud = field(activity, "hud").get(activity) as Hud
                        call(hud, if (state.startsWith("void") || state == "locked" || state == "shop-unlocked") "openShop" else "openAchievements")
                    }
                    capture("page-$state")
                    if (state.startsWith("void") || state == "shop-unlocked") {
                        scenario.onActivity { activity -> scrolls(activity.findViewById(android.R.id.content)).forEach { it.fullScroll(View.FOCUS_DOWN) } }
                        capture("bottom-$state")
                    }
                    if (state in listOf("progress", "complete", "unclaimed", "diamond-ready", "claimed", "challenge-complete")) {
                        scenario.onActivity { activity -> scrolls(activity.findViewById(android.R.id.content)).forEach { it.scrollTo(0, UiKit(activity).dp(120f)) } }
                        capture("partial-$state")
                        scenario.onActivity { activity -> scrolls(activity.findViewById(android.R.id.content)).forEach { it.fullScroll(View.FOCUS_DOWN) } }
                        capture("challenges-$state")
                    }
                    if (state == "locked") {
                        var reward: Progress.BoxReward? = null
                        var balanceAfter = 0
                        scenario.onActivity { activity ->
                            val hud = field(activity, "hud").get(activity) as Hud
                            reward = Progress.buyMysteryBox()!!; balanceAfter = Progress.coins
                            Hud::class.java.getDeclaredMethod("openPurchasedBox", Progress.BoxReward::class.java)
                                .apply { isAccessible = true }.invoke(hud, reward)
                        }
                        capture("purchased-box")
                        scenario.onActivity { activity ->
                            val hud = field(activity, "hud").get(activity) as Hud
                            call(field(hud, "shopBox").get(hud)!!, "tapBox")
                        }
                        SystemClock.sleep(2200)
                        scenario.onActivity { activity ->
                            val hud = field(activity, "hud").get(activity) as Hud
                            val flow = field(hud, "shopBox").get(hud)!!
                            assertTrue("Purchased reward delivered", field(flow, "boxRewardReady").getBoolean(flow))
                            if (field(flow, "boxBusy").getBoolean(flow)) call(flow, "tapBox")
                        }
                        capture("purchased-reward")
                        scenario.onActivity { activity ->
                            val hud = field(activity, "hud").get(activity) as Hud
                            call(field(hud, "shopBox").get(hud)!!, "tapBox")
                        }
                        capture("returned-to-shop")
                        scenario.onActivity { activity ->
                            assertNull(field(field(activity, "hud").get(activity)!!, "shopBox").get(field(activity, "hud").get(activity)))
                            assertEquals(Stage.SHOP, Stage.mode)
                            assertEquals(1, Progress.boxesOpened - 31)
                            assertEquals(balanceAfter, Progress.coins)
                        }
                    }
                }
            }
        } finally {
            val edit = prefs.edit().clear()
            for ((key, value) in saved) when (value) {
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is String -> edit.putString(key, value)
            }
            edit.commit(); Progress.init(context)
        }
    }
}
