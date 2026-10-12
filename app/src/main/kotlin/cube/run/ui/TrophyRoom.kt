package cube.run.ui

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable

/**
 * The achievement page's colours: one flat candy grape behind everything, and white slabs on
 * it. The grape is dark enough that a white card stands out at a glance, and every card keeps
 * a solid lavender lip, so each one reads as a chunky slab rather than a sheet of paper.
 */
internal object TrophyRoom {
    const val BACKDROP = 0xFF6A35E8.toInt()
    /** The solid edge under every white card. */
    val LIP = Theme.lerp(Theme.WHITE, BACKDROP, .32f)
    /** Section names on the backdrop, and their counts. */
    const val HEADING = Theme.WHITE
    val HEADING_SOFT = Theme.alpha(Theme.WHITE, 190)

    /** A white card: its face, an optional coloured [edge], and the solid lip under it. */
    fun slab(kit: UiKit, radius: Float, edge: Int? = null, lip: Int = LIP): Drawable {
        val r = kit.dpf(radius)
        return LayerDrawable(arrayOf(
            GradientDrawable().apply { cornerRadius = r; setColor(lip) },
            GradientDrawable().apply { cornerRadius = r; setColor(Theme.CARD); if (edge != null) setStroke(kit.dp(3f), edge) },
        )).apply { setLayerInset(1, 0, 0, 0, kit.CARD_LIP) }
    }
}
