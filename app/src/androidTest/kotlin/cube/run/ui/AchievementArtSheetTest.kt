package cube.run.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.data.Achievements
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit artwork review: -e captureArt true renders every tile and reward on one white sheet. */
class AchievementArtSheetTest {
    @Test fun renderTilesAndRewards() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureArt") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val kit = UiKit(context)
        val cell = kit.dp(84f); val tile = kit.dp(60f); val columns = 7
        val ids = Achievements.all.map { it.id }
        val rows = (ids.size + columns - 1) / columns
        val width = columns * cell
        val height = rows * cell + kit.dp(150f)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.INK_SOFT; textSize = kit.dpf(9f); textAlign = Paint.Align.CENTER }
        for ((i, id) in ids.withIndex()) {
            val x = (i % columns) * cell; val y = (i / columns) * cell
            AchievementTile(id, AchievementCards.accent(id)).apply {
                setBounds(x + (cell - tile) / 2, y + kit.dp(4f), x + (cell + tile) / 2, y + kit.dp(4f) + tile); draw(canvas)
            }
            canvas.drawText(id, x + cell / 2f, y + cell - kit.dpf(8f), label)
        }
        val medal = kit.dp(60f); val top = rows * cell + kit.dp(10f)
        val rewards = (0..3).map { BadgeArt(it, true) } + PlaqueArt(true) + (0..3).map { BadgeArt(it, false) } + PlaqueArt(false)
        for ((i, art) in rewards.withIndex()) {
            val x = (i % 5) * (medal + kit.dp(12f)) + kit.dp(12f) + if (i >= 5) 0 else 0
            val y = top + (i / 5) * (medal + kit.dp(8f))
            art.setBounds(x, y, x + medal, y + medal); art.draw(canvas)
        }
        val output = File(context.getExternalFilesDir(null), "achievement-art").apply { mkdirs() }
        File(output, "sheet.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
