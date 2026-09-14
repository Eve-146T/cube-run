package cube.run.ui

import android.graphics.Insets
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Skins.Ability
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class WardrobeMotionTest {
    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    private fun find(view: View, description: String): View? {
        if (view.contentDescription == description) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), description)?.let { return it }
        return null
    }

    @Test fun revealingAndClosingPreserveThePressedAbilityButton() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            lateinit var display: AbilityDisplay
            lateinit var button: View
            scenario.onActivity {
                display = AbilityDisplay(it, UiKit(it))
                display.bind(listOf(Ability.PHASE, Ability.BUBBLE_SAVER), "two")
                it.addContentView(display.floating, FrameLayout.LayoutParams(-1, -2))
            }
            SystemClock.sleep(500)
            scenario.onActivity {
                button = find(display.floating, "Show Phase ability")!!
                button.performClick()
                assertSame(button, find(display.floating, "Hide Phase ability"))
                val card = field(display, "cornerCard") as View
                assertEquals(0f, card.alpha, .01f)
            }
            SystemClock.sleep(400)
            scenario.onActivity {
                assertEquals(1f, (field(display, "cornerCard") as View).alpha, .01f)
                button.performClick()
                assertSame(button, find(display.floating, "Show Phase ability"))
                assertNull(field(display, "cornerCard"))
            }
            SystemClock.sleep(200)
            scenario.onActivity {
                val host = field(display, "cornerHost") as ViewGroup
                assertEquals(0, (host.getChildAt(1) as ViewGroup).childCount)
                // Reopen, switch ability, then close before either reveal completes.
                button.performClick()
                find(display.floating, "Show Bubble saver ability")!!.performClick()
                find(display.floating, "Hide Bubble saver ability")!!.performClick()
            }
            SystemClock.sleep(400)
            scenario.onActivity {
                val host = field(display, "cornerHost") as ViewGroup
                assertEquals(0, (host.getChildAt(1) as ViewGroup).childCount)
            }
        }
    }

    @Test fun rapidCosmeticChangesCannotResurrectAnOldAbility() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            lateinit var display: AbilityDisplay
            scenario.onActivity {
                display = AbilityDisplay(it, UiKit(it))
                display.bind(listOf(Ability.PHASE), "ghost")
                it.addContentView(display.floating, FrameLayout.LayoutParams(-1, -2))
            }
            SystemClock.sleep(500)
            scenario.onActivity { display.bind(emptyList(), "classic") }
            SystemClock.sleep(35)
            scenario.onActivity { display.bind(listOf(Ability.BUBBLE_SAVER), "bubblegum") }
            SystemClock.sleep(35)
            scenario.onActivity { display.bind(listOf(Ability.SPEED), "speedy") }
            SystemClock.sleep(500)
            scenario.onActivity {
                assertNull(find(display.floating, "Show Phase ability"))
                assertNull(find(display.floating, "Show Bubble saver ability"))
                assertNotNull(find(display.floating, "Show Speed ability"))
                assertEquals(1f, display.floating.alpha, .01f)
                assertEquals(0f, display.floating.translationY, .01f)
                display.bind(emptyList(), "classic")
            }
            SystemClock.sleep(250)
            scenario.onActivity { assertEquals(View.GONE, display.floating.visibility) }
        }
    }

    @Test fun transientSystemBarsDoNotMoveControls() {
        assumeTrue(Build.VERSION.SDK_INT >= 30)
        val hidden = WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(), Insets.NONE).build()
        val shown = WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(), Insets.of(0, 72, 0, 80)).build()
        assertArrayEquals(insetsOf(hidden), insetsOf(shown))
    }

    @Test fun returningFromAndroidPauseHasAnAlreadySettledPauseCard() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            lateinit var hud: Hud
            scenario.onActivity {
                hud = field(it, "hud") as Hud
                hud.javaClass.getDeclaredField("runStarted").apply { isAccessible = true }.setBoolean(hud, true)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity {
                val sheet = field(hud, "pauseSheet") as PauseSheet
                val card = Sheet::class.java.getDeclaredField("card").apply { isAccessible = true }.get(sheet) as View
                assertTrue(Stage.paused)
                assertEquals(1f, sheet.alpha, .01f)
                assertEquals(1f, card.alpha, .01f)
                assertEquals(1f, card.scaleX, .01f)
                assertEquals(0f, card.translationY, .01f)
            }
        }
        Stage.paused = false
    }
}
