package cube.run.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/**
 * The shop's pictures, in the achievement tiles' candy language (see docs/icons.md): one bold
 * glossy subject per tile, white or bright on the item's colour. Each one is what you buy, as
 * you meet it on the road.
 */
internal class ShopIcon(private val id: String) : CandyArt("shop_$id") {
    /** True while a tilted picture draws its lip: every colour is the darker lip shade. */
    private var lip = false

    override fun picture(canvas: Canvas) {
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        when (id) {
            BUBBLES -> {
                // A stock of bubbles: a big one in front of another.
                bubble(33f, 16f, 12f); bubble(18f, 29f, 16f)
                sparkle(8f, 9f, 4.5f)
            }
            REVIVES -> {
                // Another life: one big white heart.
                lipped(Theme.WHITE) { heart(24f, 24f, 21f) }
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = Theme.lighten(Theme.PINK, .6f)
                rect.set(10f, 11f, 22f, 23f); lastCanvas!!.drawArc(rect, 190f, 70f, false, paint)
                sparkle(41f, 8f, 5f); sparkle(7f, 38f, 3.6f)
            }
            MYSTERY -> {
                mystery(24f, 44f, 50f, Theme.YELLOW, 1.2f)
                sparkle(41f, 9f, 5f); sparkle(8f, 34f, 3.8f)
            }
            LUCKY -> clover()
            BUBBLE_TIME -> {
                // The cube safe inside its bubble.
                val c = lastCanvas!!
                fill(Theme.darken(Theme.CYAN, .3f)); c.drawCircle(24f, 26.5f, 21f, paint)
                fill(Theme.CYAN); c.drawCircle(24f, 24f, 21f, paint)
                fill(Theme.lighten(Theme.CYAN, .62f)); c.drawCircle(24f, 24f, 16.5f, paint)
                toyCube(14f, 14.5f, 20f, Theme.ORANGE)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.2f; paint.color = Theme.WHITE
                rect.set(11f, 11f, 37f, 37f); c.drawArc(rect, 195f, 65f, false, paint)
            }
            RACK_BUBBLE -> bubble(24f, 22f, 21f)
            RACK_HEART -> {
                lipped(Theme.PINK) { heart(24f, 23f, 21f) }
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 4.5f; paint.color = Theme.alpha(Theme.WHITE, 210)
                rect.set(8f, 10f, 24f, 26f); lastCanvas!!.drawArc(rect, 190f, 70f, false, paint)
            }
            MAGNET_TIME -> {
                tiltedLipped(-28f) { magnet() }
                sparkle(41f, 10f, 4.5f)
            }
            MULT_TIME -> {
                // "2×" is the whole point, so it is the picture: a fat white 2 and a yellow ×.
                glyph("2", 18f, 41f, 44f, Theme.WHITE, 1.2f); glyph("×", 37f, 39f, 32f, Theme.YELLOW, 1.4f)
                sparkle(42f, 7f, 4.5f)
            }
            JET_TIME -> tiltedLipped(35f, 1.22f) { rocket() }
            FAST_BUBBLES -> {
                // A bubble in a hurry: streaks behind it.
                for ((y, from) in listOf(15f to 5f, 24f to 1f, 33f to 6f)) {
                    line(from, y + 2.5f, 15f, y + 2.5f, Theme.alpha(Theme.INK, 60), 5f); line(from, y, 15f, y, Theme.WHITE, 5f)
                }
                bubble(30f, 24f, 15f)
            }
            COIN_VALUE -> {
                // Richer coins: a big one and a little one.
                coin(35f, 14f, 9.5f); coin(21f, 28f, 17f)
                sparkle(42f, 38f, 4.5f)
            }
            PORTALS -> portal()
            FASTER_START -> {
                // The boost: two chevrons pointing up the road.
                for (k in 0..1) {
                    val y = 34f - k * 15f
                    lipped(if (k == 1) Theme.YELLOW else Theme.WHITE) { shape(8f, y, 24f, y - 14f, 40f, y, 40f, y + 9f, 24f, y - 5f, 8f, y + 9f) }
                }
            }

            else -> {
                // The achievements: the gold badge their cards pay out.
                icon(BadgeArt(BadgeArt.GOLD, true), -1f, -1f, 50f)
                sparkle(42f, 7f, 4.5f)
            }
        }
    }

    /** The colour to paint with: the darker lip shade while a tilted picture draws its lip. */
    private fun tone(color: Int) = if (lip) Theme.darken(color, .3f) else color

    /** Draw [draw] turned by [degrees] about the centre, twice: its lip dropped straight down, then itself. */
    private inline fun tiltedLipped(degrees: Float, scale: Float = 1f, draw: () -> Unit) {
        val c = lastCanvas!!
        for (pass in 0..1) {
            lip = pass == 0
            c.save(); if (lip) c.translate(0f, 2.5f)
            c.rotate(degrees, 24f, 24f); c.scale(scale, scale, 24f, 24f); draw(); c.restore()
        }
        lip = false
    }

    /** A heart centred at ([x], [y]), [r] from its centre to its sides. */
    private fun heart(x: Float, y: Float, r: Float) {
        path.reset()
        path.moveTo(x, y + r * .95f)
        path.cubicTo(x - r * 1.35f, y + r * .05f, x - r * .95f, y - r * 1.05f, x, y - r * .45f)
        path.cubicTo(x + r * .95f, y - r * 1.05f, x + r * 1.35f, y + r * .05f, x, y + r * .95f)
        path.close()
        lastCanvas!!.drawPath(path, paint)
    }

    /** A four-leaf clover for luck: four round leaves, a stem and a gloss. */
    private fun clover() {
        val c = lastCanvas!!
        line(24f, 26f, 31f, 44f, Theme.darken(Theme.LIME, .3f), 5f)
        for (pass in 0..1) {
            fill(if (pass == 0) Theme.darken(Theme.LIME, .3f) else Theme.LIME)
            val dy = if (pass == 0) 2.5f else 0f
            for ((x, y) in listOf(15f to 15f, 33f to 15f, 15f to 31f, 33f to 31f)) c.drawCircle(x, y + dy, 9.5f, paint)
            c.drawCircle(24f, 23f + dy, 8f, paint)
        }
        fill(Theme.darken(Theme.LIME, .12f)); c.drawCircle(24f, 23f, 3.5f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.6f; paint.color = Theme.alpha(Theme.WHITE, 210)
        rect.set(9f, 9f, 21f, 21f); c.drawArc(rect, 195f, 70f, false, paint)
        sparkle(42f, 41f, 4.5f)
    }

    /** A chunky horseshoe magnet: a red bow with white tips and a gloss. */
    private fun magnet() {
        val c = lastCanvas!!
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 11f; paint.color = tone(Theme.BERRY)
        path.reset(); path.moveTo(12f, 8f); path.lineTo(12f, 24f)
        rect.set(12f, 12f, 36f, 36f); path.arcTo(rect, 180f, -180f); path.lineTo(36f, 8f)
        c.drawPath(path, paint)
        fill(tone(Theme.WHITE)); round(6.5f, 3f, 17.5f, 11f, 2f); round(30.5f, 3f, 41.5f, 11f, 2f)
        if (lip) return
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.6f; paint.strokeCap = Paint.Cap.ROUND; paint.color = Theme.alpha(Theme.WHITE, 200)
        rect.set(14f, 15f, 34f, 35f); c.drawArc(rect, 150f, 40f, false, paint)
        c.drawLine(9.5f, 15f, 9.5f, 22f, paint)
    }

    /** An upright candy rocket: a white body with a red nose and fins, a porthole and a flame. */
    private fun rocket() {
        val c = lastCanvas!!
        val body = Path().apply {
            moveTo(17f, 34f); lineTo(17f, 18f); cubicTo(17f, 10f, 21f, 5f, 24f, 3f); cubicTo(27f, 5f, 31f, 10f, 31f, 18f); lineTo(31f, 34f); close()
        }
        fill(tone(Theme.ORANGE)); shape(18f, 34f, 30f, 34f, 24f, 47f)
        fill(tone(Theme.YELLOW)); shape(20.5f, 34f, 27.5f, 34f, 24f, 42f)
        fill(tone(Theme.BERRY))
        shape(17f, 22f, 9f, 33f, 9f, 38f, 17f, 34f); shape(31f, 22f, 39f, 33f, 39f, 38f, 31f, 34f)
        fill(tone(Theme.WHITE)); c.drawPath(body, paint)
        if (lip) return
        c.save(); c.clipPath(body); fill(Theme.BERRY); c.drawRect(14f, 0f, 34f, 11f, paint); c.restore()
        fill(Theme.darken(Theme.SKY, .35f)); c.drawCircle(24f, 20f, 5.5f, paint)
        fill(Theme.lighten(Theme.SKY, .45f)); c.drawCircle(24f, 20f, 3.8f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.4f; paint.color = Theme.alpha(Theme.WHITE, 230)
        rect.set(22f, 18f, 26f, 22f); c.drawArc(rect, 190f, 80f, false, paint)
    }

    /** A coin like the app's own: a bevelled gold rim, a raised paler centre and a gloss, on a lip. */
    private fun coin(x: Float, y: Float, r: Float) {
        val c = lastCanvas!!
        fill(Theme.darken(Theme.GOLD, .5f)); c.drawCircle(x, y + r * .14f, r, paint)
        fill(Theme.darken(Theme.GOLD, .3f)); c.drawCircle(x, y, r, paint)
        fill(Theme.GOLD); c.drawCircle(x, y, r * .8f, paint)
        fill(Theme.darken(Theme.GOLD, .16f)); c.drawCircle(x, y + r * .06f, r * .5f, paint)
        fill(Theme.lighten(Theme.GOLD, .32f)); c.drawCircle(x, y - r * .02f, r * .46f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = r * .14f; paint.color = Theme.alpha(Theme.WHITE, 210)
        rect.set(x - r * .64f, y - r * .64f, x + r * .64f, y + r * .64f); c.drawArc(rect, 200f, 55f, false, paint)
    }

    /** A portal: a fat mint ring around a dark hole, swirling into another world. */
    private fun portal() {
        val c = lastCanvas!!
        fill(Theme.darken(Theme.MINT, .35f)); c.drawCircle(24f, 26.5f, 20f, paint)
        fill(Theme.MINT); c.drawCircle(24f, 24f, 20f, paint)
        fill(0xFF2A1F5C.toInt()); c.drawCircle(24f, 24f, 13.5f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.2f; paint.color = Theme.lighten(Theme.GRAPE, .35f)
        rect.set(14.5f, 14.5f, 33.5f, 33.5f); c.drawArc(rect, 200f, 210f, false, paint)
        rect.set(19.5f, 19.5f, 28.5f, 28.5f); c.drawArc(rect, 20f, 210f, false, paint)
        paint.color = Theme.alpha(Theme.WHITE, 210); paint.strokeWidth = 2.8f
        rect.set(8f, 8f, 40f, 40f); c.drawArc(rect, 200f, 55f, false, paint)
        sparkle(42f, 7f, 5f); sparkle(6f, 41f, 3.6f)
    }

    /** Bold rounded text as a lipped candy shape, grown by [weight] for fatter figures. */
    private fun glyph(text: String, x: Float, y: Float, size: Float, color: Int, weight: Float = 0f) {
        val c = lastCanvas!!
        paint.typeface = Fonts.cached(700); paint.textSize = size; paint.textAlign = Paint.Align.CENTER
        val outline = Path(); paint.getTextPath(text, 0, text.length, x, y, outline)
        if (weight > 0f) fatten(outline, weight)
        paint.textAlign = Paint.Align.LEFT
        fill(Theme.darken(color, .3f)); c.save(); c.translate(0f, 2.5f); c.drawPath(outline, paint); c.restore()
        fill(color); c.drawPath(outline, paint)
    }

    companion object {
        const val BUBBLES = "bubbles"
        const val REVIVES = "revives"
        const val MYSTERY = "mystery"
        const val BUBBLE_TIME = "bubble_time"
        const val MAGNET_TIME = "magnet_time"
        const val MULT_TIME = "mult_time"
        const val JET_TIME = "jet_time"
        const val FAST_BUBBLES = "perk_safestart"
        const val COIN_VALUE = "perk_coinvalue"
        const val PORTALS = "perk_portals"
        const val LUCKY = "perk_luckybox"
        const val FASTER_START = "perk_fasterstart"
        const val ACHIEVEMENTS = "achievements"
        /** The small stock pictures in a card's rack. */
        const val RACK_BUBBLE = "rack_bubble"
        const val RACK_HEART = "rack_heart"

        /** Each item's tile colour: the bar and the big number share it. Chosen so the picture stands out. */
        fun color(id: String): Int = when (id) {
            BUBBLES -> Theme.GRAPE
            REVIVES -> Theme.PINK
            MYSTERY -> Theme.GRAPE
            BUBBLE_TIME -> Theme.ORANGE
            MAGNET_TIME -> Theme.MINT
            MULT_TIME -> Theme.PINK
            JET_TIME -> Theme.SKY
            FAST_BUBBLES -> Theme.GRAPE
            COIN_VALUE -> Theme.BERRY
            PORTALS -> DEEP
            LUCKY -> Theme.SKY
            FASTER_START -> Theme.ORANGE
            ACHIEVEMENTS -> Theme.LAVENDER
            else -> Theme.GOLD
        }

        /** The deep indigo tile the achievements use for night and space pictures. */
        private const val DEEP = 0xFF4A3FA6.toInt()
    }
}
