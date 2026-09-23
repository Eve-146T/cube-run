package cube.run.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import cube.run.data.Achievements
import org.junit.Assert.*
import org.junit.Test

class AchievementIconVarietyTest {
    @Test fun newAchievementsHaveDistinctArtworkAndSeveralAccents() {
        val newCards = Achievements.all.drop(11)
        val silhouettes = newCards.map { definition ->
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            val icon = achievementIcon(definition.id)
            icon.setBounds(0, 0, 96, 96)
            icon.draw(Canvas(bitmap))
            val pixels = IntArray(96 * 96)
            bitmap.getPixels(pixels, 0, 96, 0, 0, 96, 96)
            bitmap.recycle()
            // Ignore badge colors: every goal must have a distinct dark pictogram.
            pixels.map { it == Theme.INK }.hashCode()
        }
        assertEquals("Every new achievement has its own pictogram", newCards.size, silhouettes.toSet().size)
        assertTrue("The new cards use varied concept colours", newCards.map { AchievementCards.accent(it.id) }.toSet().size >= 9)
    }
}
