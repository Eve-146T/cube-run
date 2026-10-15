package cube.run.ui

import android.graphics.Canvas
import android.graphics.Paint

/**
 * The shop's pictures, in the achievement tiles' candy language (see docs/icons.md): one bold
 * glossy subject per tile, white or bright on the item's colour. Each one is what you buy, as
 * you meet it on the road.
 */
internal class ShopIcon(private val id: String) : CandyArt("shop_$id") {
    override fun picture(canvas: Canvas) {
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        when (id) {
            BUBBLES -> {
                // A stock of bubbles: a little pile of them.
                bubble(14f, 33f, 11f); bubble(34f, 33f, 11f); bubble(24f, 15f, 11f)
            }
            REVIVES -> {
                // A second heart: one big glossy heart and a twinkle.
                lipped(Theme.WHITE) { heart(23f, 25f, 20f) }
                fill(Theme.lighten(Theme.PINK, .55f)); heart(23f, 25f, 13f)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = Theme.WHITE
                rect.set(9f, 12f, 21f, 24f); lastCanvas!!.drawArc(rect, 190f, 70f, false, paint)
                sparkle(42f, 9f, 5f)
            }
            MYSTERY -> {
                lastCanvas!!.save(); lastCanvas!!.rotate(-8f, 24f, 24f)
                mystery(24f, 42f, 46f, Theme.YELLOW)
                lastCanvas!!.restore()
                sparkle(41f, 9f, 5f)
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
            MAGNET_TIME -> magnet()
            MULT_TIME -> {
                // "2×" is the whole point, so it is the picture: fat glossy figures.
                glyph("2×", 24f, 37f, 30f, Theme.WHITE)
                sparkle(42f, 8f, 5f)
            }
            JET_TIME -> {
                val c = lastCanvas!!
                c.save(); c.rotate(28f, 24f, 24f); icon(JetIcon(Theme.WHITE), -1f, -3f, 52f); c.restore()
            }
            FAST_BUBBLES -> {
                // A bubble in a hurry: streaks behind it.
                for ((y, from) in listOf(15f to 5f, 24f to 1f, 33f to 6f)) {
                    line(from, y + 2.5f, 15f, y + 2.5f, Theme.alpha(Theme.INK, 60), 5f); line(from, y, 15f, y, Theme.WHITE, 5f)
                }
                bubble(30f, 24f, 15f)
            }
            COIN_VALUE -> {
                coinStack(16f, 42f, 3); coinStack(32f, 42f, 6)
                sparkle(41f, 9f, 4.5f)
            }
            PORTALS -> {
                // The portal ring, bold and lit.
                val c = lastCanvas!!
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 8f
                paint.color = Theme.darken(Theme.MINT, .3f); c.drawCircle(24f, 26.5f, 16f, paint)
                paint.color = Theme.MINT; c.drawCircle(24f, 24f, 16f, paint)
                fill(Theme.lighten(Theme.MINT, .7f)); c.drawCircle(24f, 24f, 10f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.6f; paint.color = Theme.alpha(Theme.WHITE, 220)
                rect.set(11f, 11f, 37f, 37f); c.drawArc(rect, 200f, 55f, false, paint)
                sparkle(42f, 8f, 4.5f)
            }
            FASTER_START -> {
                // The boost: three chevrons pointing up the road.
                for (k in 0..2) {
                    val y = 38f - k * 12f
                    lipped(if (k == 2) Theme.YELLOW else Theme.WHITE) { shape(6f, y, 24f, y - 14f, 42f, y, 42f, y + 7f, 24f, y - 7f, 6f, y + 7f) }
                }
            }
            else -> trophy()
        }
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

    /** A white trophy cup: bowl, handles, stem and base, with a gloss. */
    private fun trophy() {
        val c = lastCanvas!!
        for (pass in 0..1) {
            val dy = if (pass == 0) 2.5f else 0f
            val color = if (pass == 0) Theme.darken(Theme.WHITE, .3f) else Theme.WHITE
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 4.5f; paint.color = color
            rect.set(4f, 9f + dy, 18f, 25f + dy); c.drawArc(rect, 90f, 180f, false, paint)
            rect.set(30f, 9f + dy, 44f, 25f + dy); c.drawArc(rect, 270f, 180f, false, paint)
            fill(color)
            path.reset(); path.moveTo(11f, 5f + dy); path.lineTo(37f, 5f + dy); path.lineTo(37f, 17f + dy)
            rect.set(11f, 4f + dy, 37f, 32f + dy); path.arcTo(rect, 0f, 180f); path.close(); c.drawPath(path, paint)
            round(20.5f, 28f + dy, 27.5f, 37f + dy, 1.5f)
            round(13f, 36f + dy, 35f, 43f + dy, 3f)
        }
        fill(Theme.lighten(Theme.GOLD, .25f)); c.drawCircle(24f, 16f, 5f, paint)
        sparkle(24f, 16f, 3.5f)
    }

    /** A chunky horseshoe magnet: a red bow with white tips and a gloss. */
    private fun magnet() {
        val c = lastCanvas!!
        paint.strokeCap = Paint.Cap.BUTT
        for (pass in 0..1) {
            val dy = if (pass == 0) 2.5f else 0f
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 11f
            paint.color = if (pass == 0) Theme.darken(Theme.BERRY, .3f) else Theme.BERRY
            path.reset(); path.moveTo(12f, 8f + dy); path.lineTo(12f, 24f + dy)
            rect.set(12f, 12f + dy, 36f, 36f + dy); path.arcTo(rect, 180f, -180f); path.lineTo(36f, 8f + dy)
            c.drawPath(path, paint)
        }
        lipped(Theme.WHITE) { round(6.5f, 3f, 17.5f, 11f, 2f); round(30.5f, 3f, 41.5f, 11f, 2f) }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.6f; paint.strokeCap = Paint.Cap.ROUND; paint.color = Theme.alpha(Theme.WHITE, 200)
        rect.set(14f, 15f, 34f, 35f); c.drawArc(rect, 150f, 40f, false, paint)
        c.drawLine(9.5f, 15f, 9.5f, 22f, paint)
    }

    /** Bold rounded text as a lipped candy shape. */
    private fun glyph(text: String, x: Float, y: Float, size: Float, color: Int) {
        val c = lastCanvas!!
        paint.typeface = Fonts.cached(700); paint.textSize = size; paint.textAlign = Paint.Align.CENTER
        val outline = android.graphics.Path(); paint.getTextPath(text, 0, text.length, x, y, outline)
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
            else -> Theme.GOLD
        }

        /** The deep indigo tile the achievements use for night and space pictures. */
        private const val DEEP = 0xFF4A3FA6.toInt()
    }
}
