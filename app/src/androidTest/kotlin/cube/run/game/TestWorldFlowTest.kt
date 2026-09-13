package cube.run.game

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import cube.run.GameActivity
import cube.run.bot.*
import cube.run.core.Stage
import cube.run.data.Bonus
import cube.run.data.Settings
import cube.run.game.track.TestWorlds
import cube.run.ui.Hud
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TestWorldFlowTest {
    private fun find(v: View, predicate: (View) -> Boolean): View? {
        if (predicate(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), predicate)?.let { return it }
        return null
    }
    @Test fun gridSelectionAndPlayNormallyControlTheNextRun() {
        val old = Settings.testScenario; val section = Settings.testSection
        try {
            ActivityScenario.launch(GameActivity::class.java).use { scenario ->
                // Let the Android launch splash finish before capturing the actual grid.
                SystemClock.sleep(2000)
                scenario.onActivity { activity ->
                    activity.setShowWhenLocked(true); activity.setTurnScreenOn(true)
                    val hud: Hud = value(activity, "hud")
                    Hud::class.java.getDeclaredMethod("openSections").apply { isAccessible = true }.invoke(hud)
                }
                SystemClock.sleep(600)
                val inst = InstrumentationRegistry.getInstrumentation()
                val shot = inst.uiAutomation.takeScreenshot()
                File(inst.targetContext.getExternalFilesDir(null), "test-world-grid.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }; shot.recycle()
                scenario.onActivity { activity ->
                    val tile = find(activity.window.decorView) { it.contentDescription?.startsWith("Test world: JET BEFORE ZERO-G.") == true }
                    assertNotNull(tile); tile!!.performClick()
                    assertEquals(0, Settings.testScenario); assertEquals(-1, Settings.testSection)
                }
                SystemClock.sleep(500)
                scenario.onActivity { activity ->
                    val hud: Hud = value(activity, "hud")
                    Hud::class.java.getDeclaredMethod("openSections").apply { isAccessible = true }.invoke(hud)
                }
                SystemClock.sleep(300)
                scenario.onActivity { activity ->
                    val button = find(activity.window.decorView) { it is TextView && it.text.toString() == "PLAY NORMALLY" }
                    assertNotNull(button)
                    var clickable = button!!
                    while (!clickable.isClickable) clickable = clickable.parent as View
                    clickable.performClick()
                    assertEquals(-1, Settings.testScenario); assertEquals(-1, Settings.testSection)
                }
            }
        } finally { Settings.testScenario = old; Settings.testSection = section }
    }

    @Test fun jetPresetsReallyCollectBeforeOrInsideFloatAndReachFlightHeight() {
        val old = Settings.testScenario; val section = Settings.testSection
        try {
            for (id in listOf(0, 1)) {
                Settings.testScenario = id; Settings.testSection = -1
                ActivityScenario.launch(GameActivity::class.java).use {
                    LiveBotDriver.gl { game ->
                        Stage.paused = false; game.onDown(360f, 760f)
                        value<Bubble>(game, "bubble").timer.stop()
                        val p: Player = value(game, "player")
                        var jetAt = -2; var reached = false
                        repeat(900) {
                            game.tick(1f / 60f)
                            val bonus: Int = value(game, "bonus")
                            if (p.flying && jetAt == -2) jetAt = bonus
                            if (bonus == Bonus.FLOAT && p.flying && p.py > 5f) reached = true
                            if (!reached) assertFalse("Preset $id died before flight", value<Boolean>(game, "dead"))
                        }
                        assertEquals(if (id == 0) Bonus.NONE else Bonus.FLOAT, jetAt)
                        assertTrue("Preset $id never reached the jet coin height", reached)
                    }
                }
            }
        } finally { Settings.testScenario = old; Settings.testSection = section }
    }
}
