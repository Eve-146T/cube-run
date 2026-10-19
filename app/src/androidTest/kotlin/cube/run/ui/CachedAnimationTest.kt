package cube.run.ui

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import cube.run.GameActivity
import org.junit.Assert.*
import org.junit.Test

class CachedAnimationTest {
    @Test fun resultsDoNotRebuildHiddenMenusDuringTheirEntrance() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            SystemClock.sleep(5000)
            lateinit var hud: Hud
            var prepared: Any? = null
            val coins = cube.run.data.Progress::class.java.getDeclaredField("coins").apply { isAccessible = true }
            val originalCoins = coins.getInt(null)
            val shop = Hud::class.java.getDeclaredField("preparedShop").apply { isAccessible = true }
            try {
                scenario.onActivity { activity ->
                    hud = GameActivity::class.java.getDeclaredField("hud").apply { isAccessible = true }.get(activity) as Hud
                    prepared = shop.get(hud)
                    assertNotNull("Menu cache was prepared", prepared)
                    // Invalidate prices without persisting a change to the player's balance.
                    coins.setInt(null, originalCoins + 1)
                    hud.hideOptions(animate = false)
                    hud.showRunOver(4321, 5000, false, 1, 0)
                }
                SystemClock.sleep(2200)
                scenario.onActivity { assertSame("Results must not rebuild hidden shop cards", prepared, shop.get(hud)) }
            } finally { scenario.onActivity { coins.setInt(null, originalCoins) } }
        }
    }

    @Test fun resultsCountingKeepsTheLayoutStable() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            SystemClock.sleep(3500)
            lateinit var flow: RunOverFlow
            var layouts = 0
            scenario.onActivity { activity ->
                flow = RunOverFlow(activity, UiKit(activity), Int.MAX_VALUE, 5000, false, 0, 0, "Candy", emptyList(), {}, {})
                activity.addContentView(flow, FrameLayout.LayoutParams(-1, -1))
                flow.viewTreeObserver.addOnGlobalLayoutListener { layouts++ }
            }
            SystemClock.sleep(2000)
            scenario.onActivity {
                assertTrue("Counting should not relayout each digit: $layouts layouts", layouts < 15)
                val score = flow.javaClass.getDeclaredField("scoreText").apply { isAccessible = true }.get(flow) as TextView
                assertEquals(Int.MAX_VALUE.toString(), score.text.toString())
                assertEquals("Every digit must fit on the counter's line", score.text.length, score.layout.getLineEnd(0))
            }
        }
    }

    @Test fun idleTransformsReuseTheirDrawingAndStopWhenDetached() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            SystemClock.sleep(3500)
            lateinit var host: FrameLayout
            lateinit var picture: View
            var draws = 0
            var drawsBefore = 0
            lateinit var animator: ValueAnimator
            scenario.onActivity { activity ->
                host = FrameLayout(activity)
                activity.addContentView(host, FrameLayout.LayoutParams(-1, -1))
            }
            for (effect in listOf("bob", "heartbeat", "breathe")) {
                scenario.onActivity { activity ->
                    picture = object : View(activity) {
                        override fun onDraw(canvas: Canvas) { draws++; canvas.drawColor(Theme.PINK) }
                    }
                    host.addView(picture, FrameLayout.LayoutParams(200, 100))
                    animator = when (effect) {
                        "bob" -> Anim.bob(picture, 8f)
                        "heartbeat" -> Anim.heartbeat(picture)
                        else -> Anim.breathe(picture)
                    }
                }
                SystemClock.sleep(250)
                scenario.onActivity { assertTrue(draws > 0); drawsBefore = draws }
                SystemClock.sleep(500)
                scenario.onActivity {
                    assertTrue("$effect rerasterized cached content ${draws - drawsBefore} times", draws - drawsBefore <= 1)
                    assertTrue(animator.isRunning)
                    host.removeView(picture)
                    assertFalse("$effect must stop on detach", animator.isStarted)
                    assertEquals("Release the temporary layer", View.LAYER_TYPE_NONE, picture.layerType)
                }
            }
        }
    }

    @Test fun countersOnlyPublishChangedDigitsAndStopOnDetach() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            lateinit var host: FrameLayout
            lateinit var counter: TextView
            var changes = 0
            var last = ""
            lateinit var animator: ValueAnimator
            scenario.onActivity { activity ->
                host = FrameLayout(activity)
                activity.addContentView(host, FrameLayout.LayoutParams(-1, -1))
                counter = TextView(activity)
                host.addView(counter)
                counter.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changes++ }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                animator = Anim.countUp(counter, 1, 500)!!
            }
            SystemClock.sleep(800)
            scenario.onActivity {
                assertEquals("1", counter.text.toString())
                assertEquals("Only initial and final digits should publish", 2, changes)
                animator = Anim.countTo(counter, 1, 100, 2000)
            }
            SystemClock.sleep(100)
            scenario.onActivity { host.removeView(counter); last = counter.text.toString(); assertFalse(animator.isStarted) }
            SystemClock.sleep(200)
            scenario.onActivity { assertEquals(last, counter.text.toString()) }
        }
    }
}
