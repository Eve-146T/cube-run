package cube.run.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import cube.run.data.UpdateNotes
import java.util.concurrent.Executors

/**
 * The "In this update" card's tiles, painted to bitmaps on a worker while the menu settles.
 * Drawn on the main thread, cold, the tiles' art took most of the card's first frame (about
 * 46 ms of 54 on the Moto g7), a hitch right as the card appears.
 */
internal object UpdateNotesArt {
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "update-notes-art") }
    @Volatile private var tiles: Map<String, Bitmap> = emptyMap()
    @Volatile private var size = 0

    /** Start painting the tiles of the notes at [sizePx]; the card picks them up when it opens. */
    fun prepare(context: Context, sizePx: Int) {
        val icons = UpdateNotes.items(context).map { it.icon }
        worker.execute {
            tiles = icons.associateWith { icon ->
                Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bitmap ->
                    AchievementTile(icon, AchievementCards.accent(icon)).apply { setBounds(0, 0, sizePx, sizePx) }.draw(Canvas(bitmap))
                }
            }
            size = sizePx
        }
    }

    /** The painted tile for [icon] at [sizePx], if it is ready; the card draws it itself otherwise. */
    fun take(icon: String, sizePx: Int): Bitmap? = if (size == sizePx) tiles[icon] else null
}
