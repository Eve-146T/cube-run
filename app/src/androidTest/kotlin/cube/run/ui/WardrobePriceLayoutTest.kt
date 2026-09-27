package cube.run.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.text.Spanned
import android.util.TypedValue
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.data.BubbleSkins
import cube.run.data.Progress
import cube.run.data.Settings
import cube.run.data.Skins
import cube.run.data.Trails
import cube.run.data.Wardrobe
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Price-only layout checks use the actual wardrobe with an isolated, unowned collection. */
class WardrobePriceLayoutTest {
    private class FixtureContext(base: Context) : ContextWrapper(base) {
        private val prefix = "wardrobe_price_${UUID.randomUUID()}_"
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences(prefix + name, mode)
        fun dispose() { for (name in listOf("progress", "settings")) baseContext.deleteSharedPreferences(prefix + name) }
    }

    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun browse(page: WardrobeView, cat: Int, index: Int) {
        field(page, "cat").setInt(page, cat)
        field(page, "index").setInt(page, index)
        WardrobeView::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(page)
    }

    private fun measure(page: WardrobeView, kit: UiKit, widthDp: Float) {
        page.measure(View.MeasureSpec.makeMeasureSpec(kit.dp(widthDp), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(kit.dp(800f), View.MeasureSpec.EXACTLY))
        page.layout(0, 0, page.measuredWidth, page.measuredHeight)
    }

    private fun withFixture(test: (GameActivity) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val real = instrumentation.targetContext
        val fixture = FixtureContext(real)
        try {
            ActivityScenario.launch(GameActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val original = real.getSharedPreferences("progress", Context.MODE_PRIVATE).all
                    val mode = Stage.mode
                    try {
                        Stage.mode = Stage.SKINS
                        Settings.init(fixture)
                        fixture.getSharedPreferences("progress", Context.MODE_PRIVATE).edit()
                            .putInt("coins", 1000000).putInt("void_purchases", 30).apply()
                        Progress.init(fixture)
                        test(activity)
                        assertEquals("Price previews never spend or change the real save", original,
                            real.getSharedPreferences("progress", Context.MODE_PRIVATE).all)
                    } finally {
                        Stage.clearPreview(); Stage.mode = mode
                        Settings.init(real); Progress.init(real)
                    }
                }
            }
        } finally { fixture.dispose() }
    }

    private fun assertPrice(page: WardrobeView, kit: UiKit, amount: String) {
        val button = page.findViewWithTag<CandyButton>("wardrobe_action_button")
        assertTrue("The full US-grouped price is present", button.text.toString().endsWith(amount))
        assertEquals("The price is a single line", 1, button.lineCount)
        assertEquals("The price never hides its trailing digits", 0, button.layout.getEllipsisCount(0))
        val contentWidth = button.width - button.compoundPaddingLeft - button.compoundPaddingRight
        assertTrue("Every price glyph and the coin fit inside the face", button.layout.getLineWidth(0) <= contentWidth + 1f)
        assertTrue("The fitted price remains comfortably readable", button.textSize >= kit.dpf(16f))
        assertTrue("Vertical metrics cannot clip the coin", button.layout.height <= button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
        assertEquals("Price and coin are centered as one label", contentWidth / 2f,
            (button.layout.getLineLeft(0) + button.layout.getLineRight(0)) / 2f, 1.1f)
        val text = button.text as Spanned
        val span = text.getSpans(0, text.length, CenteredImageSpan::class.java).single()
        assertEquals("Coin size uses the same physical pixels as the fitted text",
            (button.textSize * 1.15f).toInt(), span.drawable.bounds.width())
        assertEquals(button.context.getString(cube.run.R.string.cd_buy_coins, amount), button.contentDescription.toString())
    }

    @Test fun secretPricesFitNarrowLargeFontsAndNormalWidthWithoutClipping() = withFixture { activity ->
        for ((width, scale) in listOf(280f to 1.5f, 360f to 1f)) {
            val configuration = Configuration(activity.resources.configuration).apply { fontScale = scale }
            val kit = UiKit(activity.createConfigurationContext(configuration))
            val page = WardrobeView(activity, kit, onClose = {})
            try {
                for ((cat, id, amount) in listOf(
                    Triple(Wardrobe.CUBE, Skins.VOID_ID, "300,000"),
                    Triple(Wardrobe.TRAIL, Trails.VOID_ID, "500,000"),
                    Triple(Wardrobe.BUBBLE, BubbleSkins.VOID_ID, "750,000"),
                )) {
                    browse(page, cat, id)
                    measure(page, kit, width)
                    assertPrice(page, kit, amount)
                    val button = page.findViewWithTag<CandyButton>("wardrobe_action_button")
                    val settledText = button.text
                    measure(page, kit, width)
                    assertSame("Repeated layout does not replace the settled coin span", settledText, button.text)
                }
            } finally { Anim.cancelTree(page) }
        }
    }

    @Test fun ordinaryPriceKeepsNominalSizeAndChangingStatusClearsOldCoinSpan() = withFixture { activity ->
        val configuration = Configuration(activity.resources.configuration).apply { fontScale = 1f }
        val kit = UiKit(activity.createConfigurationContext(configuration))
        val page = WardrobeView(activity, kit, onClose = {})
        try {
            browse(page, Wardrobe.CUBE, 1)
            measure(page, kit, 360f)
            assertPrice(page, kit, "1,500")
            val button = page.findViewWithTag<CandyButton>("wardrobe_action_button")
            val nominal = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 22f, button.resources.displayMetrics)
            assertEquals("Ordinary prices preserve the intended22sp type", nominal, button.textSize, .01f)
            browse(page, Wardrobe.CUBE, Skins.VOID_ID)
            measure(page, kit, 280f)
            assertPrice(page, kit, "300,000")
            browse(page, Wardrobe.CUBE, 0)
            measure(page, kit, 280f)
            assertEquals("EQUIPPED", button.text.toString())
            assertEquals(1, button.lineCount)
            val spans = (button.text as? Spanned)?.getSpans(0, button.text.length, CenteredImageSpan::class.java)
            assertTrue("An equipped label cannot retain a stale price coin", spans.isNullOrEmpty())
            assertEquals(nominal, button.textSize, .01f)
        } finally { Anim.cancelTree(page) }
    }
}
