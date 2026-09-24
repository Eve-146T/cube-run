package cube.run.ui

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import cube.run.GameActivity
import cube.run.data.Skins
import org.junit.Assert.*
import org.junit.Test

class AbilityLayoutTest {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun measure(view: View, width: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    @Test fun bubblegumButtonsSelectEachAbilityAndLotteryShowsEveryOddsLine() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val kit = UiKit(activity)
                val display = AbilityDisplay(activity, kit)
                display.bind(Skins.get(16).abilities, "bubblegum")
                val chips = descendants(display.floating).filterIsInstance<CandyChip>()
                assertEquals(2, chips.size)
                assertTrue(chips[0].performClick())
                assertTrue(descendants(display.floating).filterIsInstance<TextView>().any { it.text.toString() == "Bubble saver" })
                assertTrue(chips[1].performClick())
                assertTrue(descendants(display.floating).filterIsInstance<TextView>().any { it.text.toString() == "Quick bubble" })
                assertFalse(chips[0].isSelected); assertTrue(chips[1].isSelected)
                display.bind(Skins.get(1).abilities, "gambler")
                assertEquals(1, descendants(display.floating).filterIsInstance<CandyChip>().size)
                descendants(display.floating).filterIsInstance<CandyChip>().single().performClick()
                measure(display.floating, kit.dp(236f)) // 280dp viewport minus wardrobe margins.
                val texts = descendants(display.floating).filterIsInstance<TextView>()
                listOf("Lottery", "1 in 100,000", "per 1 coin of value", "1.3%", "per mystery box").forEach { expected ->
                    assertTrue("Missing $expected", texts.any { it.text.toString() == expected })
                }
                assertTrue(texts.any { it.text.toString().contains("The jackpot is 250k") })
                assertFalse(texts.any { it.text.toString().contains("JACKPOT") })
                texts.forEach { text ->
                    val layout = text.layout ?: return@forEach
                    assertTrue("No lines laid out for ${text.text}", layout.lineCount > 0)
                    assertEquals("Ability text omitted", text.text.length, layout.getLineEnd(layout.lineCount - 1))
                    assertTrue("Text view exceeds its panel", text.width <= (text.parent as View).width)
                    for (line in 0 until layout.lineCount) {
                        assertEquals(0, layout.getEllipsisCount(line))
                        // getLineWidth includes the trailing space at a normal word-wrap
                        // boundary. getLineMax measures the visible line without that space.
                        val visibleWidth = layout.getLineMax(line)
                        val lineText = text.text.subSequence(layout.getLineStart(line), layout.getLineEnd(line))
                        assertTrue("Ability text clipped: ${text.text}; line=$line text=[$lineText] " +
                            "visibleWidth=$visibleWidth widthWithSpaces=${layout.getLineWidth(line)} available=${layout.width}",
                            visibleWidth <= layout.width + 1f)
                    }
                }
            }
        }
    }
}
