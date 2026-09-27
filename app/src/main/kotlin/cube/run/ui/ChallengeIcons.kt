package cube.run.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import cube.run.data.Shards

/**
 * The newer goals' artwork, in the same filled candy language as the original families: flat
 * saturated shapes on a darker lip, a white gloss, and the shop's own coin, box, bubble, magnet,
 * 2×, jetpack, shard and portal icons wherever the goal is about them. Drawn on a 48-unit grid;
 * the card supplies the round badge behind it.
 */
internal class ChallengeIcon(private val id: String) : Icon() {
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val scale = minOf(bounds.width(), bounds.height()) / 48f
        canvas.translate(bounds.exactCenterX() - 24f * scale, bounds.exactCenterY() - 24f * scale)
        canvas.scale(scale, scale)
        val shift = centering.getOrPut(id) { measureShift() }
        canvas.translate(shift.x, shift.y)
        art(canvas)
        canvas.restoreToCount(save)
    }

    /** Lips and sparkles make each drawing lopsided: move its painted pixels onto the badge's centre. */
    private fun measureShift(): android.graphics.PointF {
        val size = 96
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        art(Canvas(bitmap).apply { scale(size / 48f, size / 48f) })
        val pixels = IntArray(size * size); bitmap.getPixels(pixels, 0, size, 0, 0, size, size); bitmap.recycle()
        var left = size; var top = size; var right = -1; var bottom = -1
        for (y in 0 until size) for (x in 0 until size) if (pixels[y * size + x] ushr 24 > 24) {
            left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
        }
        if (right < 0) return android.graphics.PointF()
        val half = size / 48f
        return android.graphics.PointF(24f - (left + right + 1) / 2f / half, 24f - (top + bottom + 1) / 2f / half)
    }

    private fun art(canvas: Canvas) {
        lastCanvas = canvas
        val save = canvas.save()
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        when (id) {
            "globetrotter" -> {
                disc(24f, 24f, 18f, Theme.SKY)
                fill(Theme.MINT); shape(12f, 14f, 20f, 10f, 24f, 15f, 20f, 22f, 23f, 28f, 17f, 33f, 11f, 26f)
                shape(29f, 20f, 37f, 17f, 40f, 25f, 35f, 34f, 29f, 30f)
                gloss(24f, 24f, 18f)
            }
            "long_hauler" -> {
                lipped(Theme.darken(Theme.INK, -.2f)) { shape(18f, 6f, 30f, 6f, 44f, 42f, 4f, 42f) }
                fill(Theme.WHITE)
                for ((top, bottom) in listOf(9f to 14f, 19f to 26f, 32f to 40f)) {
                    val w1 = .8f + top / 22f; val w2 = .8f + bottom / 22f
                    shape(24f - w1, top, 24f + w1, top, 24f + w2, bottom, 24f - w2, bottom)
                }
                line(35f, 4f, 35f, 16f, Theme.WHITE, 2.4f)
                fill(Theme.BERRY); shape(35f, 4f, 43f, 7f, 35f, 10f)
            }
            "shardsmith" -> {
                icon(ShardIcon(Theme.LAVENDER), 7f, 5f, 41f)
                sparkle(38f, 9f, 5f)
            }
            "regular" -> {
                lipped(Theme.WHITE) { round(7f, 10f, 41f, 42f, 6f) }
                fill(Theme.BERRY); round(7f, 10f, 41f, 19f, 6f); canvas.drawRect(7f, 15f, 41f, 19f, paint)
                for ((x, y) in listOf(14f to 24f, 24f to 24f, 34f to 24f, 14f to 33f, 24f to 33f)) {
                    fill(Theme.LAVENDER); round(x - 3.5f, y - 3f, x + 3.5f, y + 4f, 1.5f)
                }
                fill(Theme.MINT); round(30.5f, 30f, 37.5f, 37f, 1.5f)
                line(15f, 6f, 15f, 13f, Theme.INK, 3f); line(33f, 6f, 33f, 13f, Theme.INK, 3f)
            }
            "bubble_popper" -> {
                icon(BubbleIcon(), 3f, 9f, 33f)
                for ((x, y, xx, yy) in listOf(floatArrayOf(36f, 12f, 42f, 6f), floatArrayOf(38f, 23f, 46f, 23f), floatArrayOf(35f, 33f, 41f, 39f)))
                    line(x, y, xx, yy, Theme.YELLOW, 3.2f)
                fill(Theme.WHITE); canvas.drawCircle(30f, 7f, 2f, paint); canvas.drawCircle(44f, 31f, 1.6f, paint)
            }
            "near_miss" -> {
                lipped(Theme.BERRY) { round(33f, 5f, 43f, 43f, 4f) }
                line(3f, 17f, 11f, 17f, Theme.WHITE, 3f); line(5f, 26f, 11f, 26f, Theme.WHITE, 3f); line(3f, 35f, 10f, 35f, Theme.WHITE, 3f)
                cube(12f, 12f, 21f, Theme.ORANGE)
            }
            "untouchable" -> {
                lipped(Theme.LIME) { shape(24f, 4f, 40f, 10f, 38f, 28f, 24f, 42f, 10f, 28f, 8f, 10f) }
                fill(Theme.lighten(Theme.LIME, .35f)); shape(24f, 8f, 36f, 13f, 34.5f, 27f, 24f, 38f)
                line(16f, 23f, 22f, 29f, Theme.WHITE, 4.5f); line(22f, 29f, 33f, 16f, Theme.WHITE, 4.5f)
            }
            "house_loses" -> {
                icon(CoinIcon(), 26f, 3f, 19f)
                lipped(Theme.WHITE) { round(5f, 13f, 35f, 43f, 7f) }
                fill(Theme.INK)
                for ((x, y) in listOf(12f to 20f, 28f to 20f, 20f to 28f, 12f to 36f, 28f to 36f)) canvas.drawCircle(x, y, 2.8f, paint)
            }
            "voidwalker" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f
                paint.color = Theme.ORANGE; rect.set(3f, 16f, 45f, 32f); canvas.drawOval(rect, paint)
                fill(Theme.darken(Theme.GRAPE, .55f)); canvas.drawCircle(24f, 24f, 12f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f; paint.color = Theme.lighten(Theme.GRAPE, .3f)
                canvas.drawCircle(24f, 24f, 13f, paint)
                // The near half of the disc passes in front of the hole.
                paint.strokeWidth = 5f; paint.color = Theme.YELLOW; rect.set(3f, 16f, 45f, 32f); canvas.drawArc(rect, 20f, 140f, false, paint)
            }
            "greedy" -> {
                icon(BoxIcon(Theme.BERRY), 18f, 2f, 28f)
                icon(BoxIcon(Theme.GRAPE), 3f, 17f, 30f)
                sparkle(40f, 36f, 4.5f)
            }
            "scenic_route" -> {
                for ((i, color) in listOf(Theme.SKY, Theme.MINT, Theme.BERRY, Theme.GOLD).withIndex())
                    icon(PortalIcon(color), 2f + (i % 2) * 22f, 2f + (i / 2) * 22f, 22f)
            }
            "coal_miner" -> {
                lipped(0xFF3C4258.toInt()) { shape(8f, 32f, 16f, 22f, 29f, 20f, 40f, 30f, 36f, 42f, 13f, 43f) }
                fill(0xFF5B6380.toInt()); shape(16f, 22f, 29f, 20f, 26f, 30f, 14f, 31f)
                line(13f, 6f, 30f, 25f, 0xFFB07A4A.toInt(), 4.5f)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.color = 0xFFD5DCEC.toInt()
                path.reset(); path.moveTo(5f, 14f); path.quadTo(13f, 3f, 24f, 4f); canvas.drawPath(path, paint)
            }
            "magpie" -> {
                icon(CoinIcon(), 4f, 18f, 24f); icon(CoinIcon(), 18f, 20f, 24f); icon(CoinIcon(), 11f, 4f, 26f)
                sparkle(40f, 9f, 5f)
            }
            "shard_hunter" -> {
                for ((i, kind) in Shards.all.withIndex()) icon(ShardIcon(Theme.hsv(kind.hue, .6f, 1f)),
                    listOf(2f, 25f, 13.5f)[i], listOf(4f, 4f, 22f)[i], 21f)
            }
            "full_kit" -> {
                val kit = listOf<Drawable>(BubbleIcon(), MagnetIcon(), MultIcon(), JetIcon())
                for ((i, icon) in kit.withIndex()) icon(icon, 2f + (i % 2) * 23f, 2f + (i / 2) * 23f, 21f)
            }
            "long_con" -> {
                fill(Theme.darken(Theme.ORANGE, .3f)); round(20f, 3f, 28f, 9f, 2f)
                disc(24f, 27f, 17f, Theme.ORANGE)
                fill(Theme.WHITE); canvas.drawCircle(24f, 27f, 12.5f, paint)
                line(24f, 27f, 24f, 18.5f, Theme.INK, 3f); line(24f, 27f, 30f, 30f, Theme.INK, 3f)
                fill(Theme.BERRY); canvas.drawCircle(24f, 27f, 2.2f, paint)
            }
            "insomniac" -> {
                fill(Theme.darken(Theme.YELLOW, .3f)); crescent(22f, 27f)
                fill(Theme.YELLOW); crescent(22f, 25f)
                sparkle(37f, 11f, 5f); sparkle(41f, 27f, 3.5f)
            }
            "bankrupt" -> {
                lipped(0xFFA0673F.toInt()) { round(4f, 14f, 44f, 42f, 6f) }
                fill(0xFF7E4E2F.toInt()); round(26f, 21f, 44f, 34f, 5f)
                fill(Theme.GOLD); canvas.drawCircle(33f, 27.5f, 3f, paint)
                line(9f, 9f, 13f, 5f, Theme.WHITE, 2.4f); line(20f, 8f, 20f, 3f, Theme.WHITE, 2.4f); line(31f, 9f, 27f, 5f, Theme.WHITE, 2.4f)
            }
            "exactly_67" -> {
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); paint.textSize = 30f; paint.textAlign = Paint.Align.CENTER
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.color = Theme.INK
                canvas.drawText("67", 24f, 35f, paint)
                fill(Theme.WHITE); canvas.drawText("67", 24f, 35f, paint)
                paint.textAlign = Paint.Align.LEFT
            }
            "just_browsing" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.5f; paint.color = Theme.WHITE
                path.reset(); path.moveTo(16f, 17f); path.cubicTo(16f, 3f, 32f, 3f, 32f, 17f); canvas.drawPath(path, paint)
                lipped(Theme.CYAN) { round(7f, 14f, 41f, 43f, 6f) }
                fill(Theme.WHITE); rect.set(13f, 22f, 35f, 36f); canvas.drawOval(rect, paint)
                fill(Theme.INK); canvas.drawCircle(24f, 29f, 4.5f, paint)
                fill(Theme.WHITE); canvas.drawCircle(25.5f, 27.5f, 1.4f, paint)
            }
            "two_ez" -> {
                arrow(6f, 16f, 1f, Theme.WHITE)
                arrow(42f, 33f, -1f, Theme.YELLOW)
            }
            "nervous_tic" -> {
                lipped(Theme.WHITE) { round(12f, 9f, 21f, 39f, 3.5f) }
                lipped(Theme.WHITE) { round(27f, 9f, 36f, 39f, 3.5f) }
                for (side in listOf(-1f, 1f)) {
                    val x = 24f + side * 18f
                    line(x, 16f, x + side * 3f, 20f, Theme.INK, 2.6f); line(x + side * 3f, 20f, x, 24f, Theme.INK, 2.6f)
                    line(x, 24f, x + side * 3f, 28f, Theme.INK, 2.6f)
                }
            }
            "silent_treatment" -> {
                lipped(Theme.WHITE) { shape(5f, 18f, 13f, 18f, 24f, 8f, 24f, 38f, 13f, 29f, 5f, 29f) }
                line(30f, 17f, 42f, 30f, Theme.BERRY, 4.5f); line(42f, 17f, 30f, 30f, Theme.BERRY, 4.5f)
            }
            "stage_fright" -> {
                lipped(Theme.GOLD) { round(6f, 7f, 11f, 44f, 2f) }
                lipped(Theme.GOLD) { round(37f, 7f, 42f, 44f, 2f) }
                fill(Theme.WHITE); canvas.drawRect(9f, 8f, 39f, 16f, paint)
                fill(Theme.INK); for (k in 0 until 8) canvas.drawRect(9f + k * 3.75f, if (k % 2 == 0) 8f else 12f, 12.75f + k * 3.75f, if (k % 2 == 0) 12f else 16f, paint)
                burst(24f, 33f, Theme.BERRY)
            }
            "neo" -> {
                // The red pill: a glossy capsule lying at a tilt, with its seam.
                canvas.rotate(-38f, 24f, 24f)
                lipped(PILL_RED) { round(6f, 16f, 42f, 32f, 8f) }
                line(24f, 16.5f, 24f, 31.5f, Theme.darken(PILL_RED, .25f), 1.6f)
                fill(Theme.alpha(Theme.WHITE, 200)); round(11f, 19f, 21f, 22.5f, 1.75f)
            }
        }
        canvas.restoreToCount(save)
        lastCanvas = null
    }

    private fun fill(color: Int) { paint.style = Paint.Style.FILL; paint.color = color }

    /** One shape twice: a darker copy dropped below it as its lip, then the shape itself. */
    private inline fun lipped(color: Int, draw: () -> Unit) {
        fill(Theme.darken(color, .3f)); lastCanvas?.let { it.save(); it.translate(0f, 2.5f); draw(); it.restore() }
        fill(color); draw()
    }

    /** The canvas being drawn on, for the shape helpers below. */
    private var lastCanvas: Canvas? = null

    private fun disc(x: Float, y: Float, r: Float, color: Int) {
        fill(Theme.darken(color, .3f)); lastCanvas!!.drawCircle(x, y + 2.5f, r, paint)
        fill(color); lastCanvas!!.drawCircle(x, y, r, paint)
    }

    private fun gloss(x: Float, y: Float, r: Float) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.6f; paint.color = Theme.alpha(Theme.WHITE, 200)
        rect.set(x - r + 4f, y - r + 4f, x + r - 4f, y + r - 4f); lastCanvas!!.drawArc(rect, 200f, 60f, false, paint)
    }

    private fun shape(vararg p: Float) {
        path.reset(); path.moveTo(p[0], p[1]); for (i in 2 until p.size step 2) path.lineTo(p[i], p[i + 1]); path.close()
        lastCanvas!!.drawPath(path, paint)
    }

    private fun round(l: Float, t: Float, r: Float, b: Float, radius: Float) {
        rect.set(l, t, r, b); lastCanvas!!.drawRoundRect(rect, radius, radius, paint)
    }

    private fun line(x: Float, y: Float, xx: Float, yy: Float, color: Int, width: Float) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = width; paint.color = color
        lastCanvas!!.drawLine(x, y, xx, yy, paint)
    }

    private fun icon(icon: Drawable, x: Float, y: Float, size: Float) {
        icon.setBounds(0, 0, 480, 480)
        val c = lastCanvas!!; c.save(); c.translate(x, y); c.scale(size / 480f, size / 480f); icon.draw(c); c.restore()
    }

    /** A four-point twinkle. */
    private fun sparkle(x: Float, y: Float, r: Float) {
        fill(Theme.WHITE)
        shape(x, y - r, x + r * .28f, y - r * .28f, x + r, y, x + r * .28f, y + r * .28f, x, y + r, x - r * .28f, y + r * .28f, x - r, y, x - r * .28f, y - r * .28f)
    }

    private fun crescent(x: Float, y: Float) {
        path.reset(); path.addCircle(x, y, 16f, Path.Direction.CW)
        val bite = Path().apply { addCircle(x + 9f, y - 7f, 13f, Path.Direction.CW) }
        path.op(bite, Path.Op.DIFFERENCE)
        lastCanvas!!.drawPath(path, paint)
    }

    private fun arrow(x: Float, y: Float, dir: Float, color: Int) {
        lipped(color) {
            shape(x, y - 3.5f, x + dir * 24f, y - 3.5f, x + dir * 24f, y - 9f, x + dir * 36f, y, x + dir * 24f, y + 9f, x + dir * 24f, y + 3.5f, x, y + 3.5f)
        }
    }

    private fun burst(x: Float, y: Float, color: Int) {
        path.reset()
        for (k in 0 until 16) {
            val r = if (k % 2 == 0) 10f else 5f
            val a = Math.toRadians(k * 22.5 - 90.0)
            val px = x + (r * Math.cos(a)).toFloat(); val py = y + (r * Math.sin(a)).toFloat()
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        fill(Theme.darken(color, .3f)); lastCanvas!!.save(); lastCanvas!!.translate(0f, 2f); lastCanvas!!.drawPath(path, paint); lastCanvas!!.restore()
        fill(color); lastCanvas!!.drawPath(path, paint)
        fill(Theme.YELLOW); lastCanvas!!.drawCircle(x, y, 3.5f, paint)
    }

    /** The game's bevelled isometric cube. */
    private fun cube(x: Float, y: Float, size: Float, color: Int) {
        val half = size / 2f; val rise = size / 4f
        fill(Theme.lighten(color, .4f)); shape(x, y + rise, x + half, y, x + size, y + rise, x + half, y + 2 * rise)
        fill(color); shape(x, y + rise, x + half, y + 2 * rise, x + half, y + size, x, y + size - rise)
        fill(Theme.darken(color, .2f)); shape(x + half, y + 2 * rise, x + size, y + rise, x + size, y + size - rise, x + half, y + size)
    }

    private companion object {
        val centering = java.util.concurrent.ConcurrentHashMap<String, android.graphics.PointF>()
        const val PILL_RED = 0xFFFF2E3F.toInt()
    }
}
