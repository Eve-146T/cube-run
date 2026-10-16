package cube.run.ui

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable

/**
 * The achievement page's colours: one flat candy lavender behind everything, and white slabs on
 * it (picked from nine backdrops shown on the real page, 2026-10-15). Every card keeps a solid
 * lip, so each one reads as a chunky slab rather than a sheet of paper. The lavender is light,
 * so the words on it are ink.
 */
internal object TrophyRoom {
    const val BACKDROP = Theme.LAVENDER
    /** The solid edge under every white card. */
    val LIP = Theme.lerp(Theme.WHITE, BACKDROP, .32f)
    /** Section names on the backdrop, and their counts. */
    const val HEADING = Theme.INK
    val HEADING_SOFT = Theme.alpha(Theme.INK, 190)

    /** A white card: its face, an optional coloured [edge], and the solid lip under it. */
    fun slab(kit: UiKit, radius: Float, edge: Int? = null, lip: Int = LIP): Drawable {
        val r = kit.dpf(radius)
        return LayerDrawable(arrayOf(
            GradientDrawable().apply { cornerRadius = r; setColor(lip) },
            GradientDrawable().apply { cornerRadius = r; setColor(Theme.CARD); if (edge != null) setStroke(kit.dp(3f), edge) },
        )).apply { setLayerInset(1, 0, 0, 0, kit.CARD_LIP) }
    }
}
