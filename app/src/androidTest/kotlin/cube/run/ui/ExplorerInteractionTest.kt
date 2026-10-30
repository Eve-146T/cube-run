package cube.run.ui

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.ListView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.Settings
import org.junit.Assert.*
import org.junit.Test

/** Real cached navigation: grouping and folding must retain every selectable section. */
class ExplorerInteractionTest {
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner: Any, name: String): T {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true }.get(owner) as T }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        error(name)
    }

    private fun invoke(owner: Any, name: String) =
        owner.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(owner)

    @Test fun groupingFoldingAndLoopSelectionSurviveCachedNavigation() {
        val savedSection = Settings.testSection
        val savedPill = Settings.testPillWorld
        val savedSpace = Settings.testSpaceWorld
        val intent = Intent(InstrumentationRegistry.getInstrumentation().targetContext, GameActivity::class.java)
            .putExtra(Hud.EXTRA_AUTOSTART, false)
        ActivityScenario.launch<GameActivity>(intent).use { scenario ->
            lateinit var hud: Hud
            lateinit var page: SectionsView
            scenario.onActivity { activity ->
                Settings.testSection = -1; Settings.testPillWorld = false; Settings.testSpaceWorld = false
                hud = field(activity, "hud")
                invoke(hud, "openSectionsFromSettings")
                page = field(hud, "page")
                assertTrue(Stage.sceneCovered)
            }
            try {
                val deadline = SystemClock.uptimeMillis() + 15000
                var ready = false
                while (!ready && SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity { ready = page.contentReady }
                    if (!ready) SystemClock.sleep(30)
                }
                assertTrue("All explorer rows became ready", ready)
                lateinit var retainedCards: Map<Int, List<CandyRow>>
                scenario.onActivity {
                    retainedCards = field<Map<Int, List<CandyRow>>>(page, "cards").mapValues { it.value.toList() }
                    assertTrue(retainedCards.isNotEmpty())
                    assertTrue("Each section is available in both groupings", retainedCards.values.all { it.size == 2 })
                }
                for (tiers in listOf(false, true, false)) {
                    scenario.onActivity {
                        page.javaClass.getDeclaredMethod("group", Boolean::class.javaPrimitiveType).apply { isAccessible = true }
                            .invoke(page, tiers)
                    }
                    SystemClock.sleep(500)
                    scenario.onActivity {
                        val entries = field<List<Any>>(page, if (tiers) "tierEntries" else "familyEntries")
                        val before = entries.size
                        val parked = field<ListView>(page, if (tiers) "familyList" else "tierList")
                        assertEquals(0f, parked.alpha, 0f)
                        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, parked.importantForAccessibility)
                        val headerEntry = entries.first { field<Int>(it, "row") < 0 }
                        val group = field<Any>(headerEntry, "group")
                        val header = field<View>(group, "header")
                        header.performClick()
                        assertTrue("Folding removes section rows", field<List<Any>>(page, if (tiers) "tierEntries" else "familyEntries").size < before)
                        header.performClick()
                        assertEquals("Unfolding restores all rows", before, field<List<Any>>(page, if (tiers) "tierEntries" else "familyEntries").size)
                        val current = field<Map<Int, List<CandyRow>>>(page, "cards")
                        retainedCards.forEach { (id, cards) ->
                            cards.forEachIndexed { index, card -> assertSame(card, current.getValue(id)[index]) }
                        }
                    }
                }
                scenario.onActivity {
                    val selected = retainedCards.entries.first()
                    selected.value.first().performClick()
                    assertEquals(selected.key, Settings.testSection)
                    assertFalse("Selecting a section uncovers the game", Stage.sceneCovered)
                    invoke(hud, "openSectionsFromSettings")
                    assertSame(page, field<Page>(hud, "page"))
                    assertTrue(Stage.sceneCovered)
                    selected.value.forEach { card ->
                        assertEquals("Both groupings mark the chosen section", View.VISIBLE,
                            card.findViewWithTag<View>("loop").visibility)
                    }
                    val list = field<ListView>(page, "familyList")
                    assertTrue(list.adapter.count > 0)
                    page.navigateBack()
                    assertFalse("Back uncovers the game", Stage.sceneCovered)
                    assertFalse(page.navigationShowing)
                    val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
                    try { assertFalse("A parked explorer cannot swallow menu taps", page.dispatchTouchEvent(event)) }
                    finally { event.recycle() }
                    assertNull(field<Page?>(hud, "page"))
                }
            } finally {
                scenario.onActivity {
                    Settings.testSection = savedSection; Settings.testPillWorld = savedPill; Settings.testSpaceWorld = savedSpace
                }
            }
        }
    }
}
