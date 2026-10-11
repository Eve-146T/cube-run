package cube.run.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import cube.run.data.Shards

/**
 * Every achievement's artwork except the few original ones, in one filled candy language: flat
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
                // A long road winding off to a big flag.
                paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
                path.reset(); path.moveTo(6f, 46f); path.cubicTo(6f, 30f, 34f, 34f, 30f, 20f)
                for (pass in 0..1) {
                    paint.strokeWidth = 11f; paint.color = if (pass == 0) Theme.darken(Theme.INK, .1f) else 0xFF4A3F86.toInt()
                    lastCanvas!!.save(); if (pass == 0) lastCanvas!!.translate(0f, 2.5f); lastCanvas!!.drawPath(path, paint); lastCanvas!!.restore()
                }
                paint.strokeWidth = 2.6f; paint.color = Theme.WHITE
                paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 4f), 0f)
                lastCanvas!!.drawPath(path, paint); paint.pathEffect = null
                lipped(Theme.WHITE) { round(29f, 3f, 32.5f, 22f, 1.75f) }
                lipped(Theme.BERRY) { shape(32.5f, 3f, 46f, 7.5f, 32.5f, 13f) }
            }
            "cookie" -> {
                // The haptics switch, flipped on and buzzing.
                lipped(Theme.MINT) { round(5f, 13f, 43f, 35f, 11f) }
                lipped(Theme.WHITE) { lastCanvas!!.drawCircle(32f, 24f, 8.5f, paint) }
                fill(Theme.alpha(Theme.WHITE, 150)); round(9.5f, 16f, 22f, 18.5f, 1.2f)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.8f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND; paint.color = Theme.WHITE
                for (side in listOf(-1f, 1f)) {
                    val x = 24f + side * 22.5f
                    path.reset(); path.moveTo(x, 17.25f)
                    for ((k, y) in listOf(21.25f, 25.25f, 29.25f, 33.25f).withIndex()) path.lineTo(if (k % 2 == 0) x + side * 2.5f else x, y)
                    lastCanvas!!.drawPath(path, paint)
                }
            }
            "bubbles" -> {
                // One big glossy bubble, a little one beside it.
                bubble(22f, 26f, 19f); bubble(41f, 8f, 5.5f)
            }
            "center" -> {
                // Held dead centre between the lane lines.
                lipped(Theme.WHITE) { round(3f, 4f, 9f, 44f, 3f) }
                lipped(Theme.WHITE) { round(39f, 4f, 45f, 44f, 3f) }
                toyCube(12f, 10f, 24f, Theme.ORANGE)
            }
            "gambliphobic" -> {
                // No gambling, thank you: one bold no sign.
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 7.5f; paint.strokeCap = Paint.Cap.BUTT
                for (pass in 0..1) {
                    val dy = if (pass == 0) 2.8f else 0f
                    paint.color = if (pass == 0) Theme.darken(Theme.BERRY, .3f) else Theme.BERRY
                    lastCanvas!!.drawCircle(24f, 24f + dy, 18.5f, paint)
                    lastCanvas!!.drawLine(11.5f, 11.5f + dy, 36.5f, 36.5f + dy, paint)
                }
                paint.strokeWidth = 2.6f; paint.strokeCap = Paint.Cap.ROUND; paint.color = Theme.alpha(Theme.WHITE, 200)
                rect.set(9f, 9f, 39f, 39f); lastCanvas!!.drawArc(rect, 200f, 50f, false, paint)
            }
            "runner" -> {
                // The cube on a dash: thick streaks behind it, a twinkle for the best run.
                for ((y, from) in listOf(16f to 4f, 25f to 1f, 34f to 6f)) {
                    line(from, y + 2.5f, 14f, y + 2.5f, Theme.alpha(Theme.INK, 60), 5f); line(from, y, 14f, y, Theme.WHITE, 5f)
                }
                toyCube(13f, 7f, 31f, Theme.ORANGE)
                sparkle(42f, 6f, 4.5f)
            }
            "coins" -> {
                coinStack(15f, 41f, 4); coinStack(32f, 41f, 6)
                sparkle(41f, 9f, 4.5f)
            }
            "cubes" -> {
                toyCube(1f, 19f, 24f, Theme.SKY)
                toyCube(23f, 19f, 24f, Theme.YELLOW)
                toyCube(12f, 1f, 24f, Theme.GRAPE)
            }
            "powerups" -> {
                // Power: one chunky lightning bolt.
                lipped(Theme.YELLOW) { shape(25f, 3f, 37f, 3f, 30f, 18f, 39f, 18f, 17f, 45f, 21.5f, 27f, 12f, 27f) }
                fill(Theme.alpha(Theme.WHITE, 200)); shape(26.5f, 5f, 33f, 5f, 24.5f, 18f, 21f, 18f)
                sparkle(41f, 30f, 4.5f); sparkle(9f, 12f, 3.5f)
            }
            "boxes" -> {
                // The mystery itself: one big glossy question mark, leaning in.
                lastCanvas!!.save(); lastCanvas!!.rotate(-8f, 24f, 24f)
                mystery(24f, 42f, 46f, Theme.YELLOW)
                lastCanvas!!.restore()
                sparkle(41f, 9f, 5f); sparkle(8f, 32f, 3.8f)
            }
            "shardsmith" -> {
                icon(ShardIcon(Theme.LAVENDER), 7f, 5f, 41f)
                sparkle(38f, 9f, 5f)
            }
            "regular" -> {
                // A month of runs: most days ticked off.
                lipped(Theme.WHITE) { round(5f, 9f, 43f, 44f, 6f) }
                fill(Theme.BERRY); round(5f, 9f, 43f, 20f, 6f); lastCanvas!!.drawRect(5f, 15f, 43f, 20f, paint)
                for ((i, at) in listOf(14.5f to 27.5f, 24f to 27.5f, 33.5f to 27.5f, 14.5f to 37f, 24f to 37f, 33.5f to 37f).withIndex()) {
                    val (x, y) = at
                    fill(if (i < 5) Theme.MINT else Theme.LAVENDER); round(x - 4f, y - 3.5f, x + 4f, y + 3.5f, 2f)
                    if (i < 5) { line(x - 2f, y, x - .5f, y + 1.6f, Theme.WHITE, 1.8f); line(x - .5f, y + 1.6f, x + 2.2f, y - 1.8f, Theme.WHITE, 1.8f) }
                }
                line(15f, 5f, 15f, 13f, Theme.INK, 3.4f); line(33f, 5f, 33f, 13f, Theme.INK, 3.4f)
            }
            "bubble_popper" -> {
                // A pin on its way into a bubble.
                bubble(20f, 28f, 17f)
                line(29f, 19f, 39.5f, 8.5f, 0xFFDCE3F2.toInt(), 3.4f)
                lipped(Theme.BERRY) { lastCanvas!!.drawCircle(41f, 7f, 5.5f, paint) }
                fill(Theme.alpha(Theme.WHITE, 200)); lastCanvas!!.drawCircle(39.3f, 5.3f, 1.6f, paint)
            }
            "near_miss" -> {
                // Brushing past a block: a spark where the cube grazes it.
                lipped(Theme.BERRY) { round(33f, 3f, 44f, 45f, 4f) }
                line(1f, 20f, 7f, 20f, Theme.WHITE, 4f); line(3f, 29f, 7f, 29f, Theme.WHITE, 4f)
                toyCube(7f, 11f, 25f, Theme.ORANGE)
                sparkle(31.5f, 9f, 6f, Theme.YELLOW)
            }
            "untouchable" -> {
                // A rounded shield with a check.
                paint.pathEffect = android.graphics.CornerPathEffect(5f)
                lipped(Theme.LIME) { shape(24f, 4f, 40f, 9f, 38.5f, 27f, 24f, 42f, 9.5f, 27f, 8f, 9f) }
                fill(Theme.lighten(Theme.LIME, .35f)); shape(24f, 8f, 36f, 12f, 34.5f, 26.5f, 24f, 38f)
                paint.pathEffect = null
                line(16.5f, 23f, 22f, 28.5f, Theme.WHITE, 4.8f); line(22f, 28.5f, 32f, 17.5f, Theme.WHITE, 4.8f)
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
                // Mysteries piling up: three question marks, the front one biggest.
                mystery(10f, 31f, 28f, Theme.SKY)
                mystery(38f, 31f, 28f, Theme.PINK)
                mystery(24f, 44f, 40f, Theme.YELLOW)
            }
            "greed" -> {
                // Grabbing a mystery at the wrong moment: it goes off in a crash.
                burst(24f, 24f, Theme.BERRY, 23f, Theme.ORANGE)
                mystery(24f, 41f, 38f, Theme.YELLOW)
            }
            "pile_driver" -> {
                // Landing hard: the cube thumps down in a puff of dust.
                for (x in listOf(14f, 24f, 34f)) line(x, 1f, x, 6f, Theme.WHITE, 3.4f)
                toyCube(11f, 8f, 26f, Theme.ORANGE)
                fill(Theme.WHITE)
                for ((x, r) in listOf(6f to 5.5f, 12f to 4f, 42f to 5.5f, 36f to 4f)) lastCanvas!!.drawCircle(x, 41f, r, paint)
                lipped(Theme.WHITE) { round(2f, 41f, 46f, 45f, 2f) }
            }
            "scenic_route" -> {
                // A big portal swirling into another world.
                val c = lastCanvas!!
                fill(Theme.darken(Theme.MINT, .35f)); c.drawCircle(24f, 26.5f, 19f, paint)
                fill(Theme.MINT); c.drawCircle(24f, 24f, 19f, paint)
                fill(0xFF2A1F5C.toInt()); c.drawCircle(24f, 24f, 13f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = 2.6f
                paint.color = Theme.lighten(Theme.GRAPE, .35f)
                paint.strokeWidth = 3f
                rect.set(15f, 15f, 33f, 33f); c.drawArc(rect, 200f, 210f, false, paint)
                rect.set(19.5f, 19.5f, 28.5f, 28.5f); c.drawArc(rect, 20f, 210f, false, paint)
                paint.color = Theme.alpha(Theme.WHITE, 200)
                rect.set(8.5f, 8.5f, 39.5f, 39.5f); c.drawArc(rect, 200f, 55f, false, paint)
                sparkle(41f, 8f, 5f); sparkle(6f, 40f, 3.8f)
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
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.6f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND; paint.color = Theme.INK
                for (side in listOf(-1f, 1f)) {
                    val x = 24f + side * 18f
                    path.reset(); path.moveTo(x, 15.5f)
                    for ((k, y) in listOf(19.75f, 24f, 28.25f, 32.5f).withIndex()) path.lineTo(if (k % 2 == 0) x + side * 3f else x, y)
                    lastCanvas!!.drawPath(path, paint)
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
            "monk" -> {
                // The Zen cube, calm under a halo.
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
                paint.color = Theme.darken(Theme.YELLOW, .3f); rect.set(12f, 5.5f, 36f, 13.5f); canvas.drawOval(rect, paint)
                paint.color = Theme.YELLOW; rect.set(12f, 4f, 36f, 12f); canvas.drawOval(rect, paint)
                cube(8f, 15f, 32f, Theme.WHITE)
                // Closed, content eyes on the front face.
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.4f; paint.strokeCap = Paint.Cap.ROUND; paint.color = Theme.INK
                rect.set(11.5f, 30f, 17.5f, 35f); canvas.drawArc(rect, 20f, 140f, false, paint)
                rect.set(18.5f, 33.5f, 24.5f, 38.5f); canvas.drawArc(rect, 20f, 140f, false, paint)
                sparkle(41f, 12f, 4.5f)
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
    private fun sparkle(x: Float, y: Float, r: Float, color: Int = Theme.WHITE) {
        fill(color)
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

    private fun burst(x: Float, y: Float, color: Int, size: Float = 10f, core: Int = Theme.YELLOW) {
        path.reset()
        for (k in 0 until 16) {
            val r = if (k % 2 == 0) size else size / 2f
            val a = Math.toRadians(k * 22.5 - 90.0)
            val px = x + (r * Math.cos(a)).toFloat(); val py = y + (r * Math.sin(a)).toFloat()
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        fill(Theme.darken(color, .3f)); lastCanvas!!.save(); lastCanvas!!.translate(0f, 2f); lastCanvas!!.drawPath(path, paint); lastCanvas!!.restore()
        fill(color); lastCanvas!!.drawPath(path, paint)
        fill(core); lastCanvas!!.drawCircle(x, y, size * .3f, paint)
    }

    /**
     * A chunky glossy question mark in the game's rounded font, its baseline at ([x], [y]),
     * [size] high: a darker lip under it, a paler top and a white glint on the hook.
     */
    private fun mystery(x: Float, y: Float, size: Float, color: Int) {
        val c = lastCanvas!!
        paint.typeface = Fonts.cached(700); paint.textSize = size; paint.textAlign = Paint.Align.CENTER
        val glyph = Path(); paint.getTextPath("?", 0, 1, x, y, glyph)
        paint.textAlign = Paint.Align.LEFT
        val box = RectF(); glyph.computeBounds(box, true)
        fill(Theme.darken(color, .38f)); c.save(); c.translate(0f, size * .07f); c.drawPath(glyph, paint); c.restore()
        fill(color); c.drawPath(glyph, paint)
        c.save(); c.clipPath(glyph)
        fill(Theme.lighten(color, .4f)); c.drawRect(box.left, box.top, box.right, box.top + box.height() * .22f, paint)
        fill(Theme.alpha(Theme.WHITE, 230))
        rect.set(box.left + box.width() * .12f, box.top + box.height() * .08f, box.left + box.width() * .4f, box.top + box.height() * .17f)
        c.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint)
        c.restore()
    }

    /** A pointy polygon with every corner rounded off by [radius]. */
    private fun rounded(out: Path, radius: Float, vararg p: Float) {
        out.reset()
        val n = p.size / 2
        for (i in 0 until n) {
            val x = p[2 * i]; val y = p[2 * i + 1]
            val px = p[2 * ((i + n - 1) % n)]; val py = p[2 * ((i + n - 1) % n) + 1]
            val nx = p[2 * ((i + 1) % n)]; val ny = p[2 * ((i + 1) % n) + 1]
            val d1 = kotlin.math.hypot(px - x, py - y); val d2 = kotlin.math.hypot(nx - x, ny - y)
            val k1 = minOf(radius, d1 / 2f) / d1; val k2 = minOf(radius, d2 / 2f) / d2
            if (i == 0) out.moveTo(x + (px - x) * k1, y + (py - y) * k1) else out.lineTo(x + (px - x) * k1, y + (py - y) * k1)
            out.quadTo(x, y, x + (nx - x) * k2, y + (ny - y) * k2)
        }
        out.close()
    }

    /**
     * The chunky toy cube at ([x], [y]) in a [size] square: soft rounded corners, a darker lip
     * under it, a bright top, a mid left face, a shaded right face and a gloss on top.
     */
    private fun toyCube(x: Float, y: Float, size: Float, color: Int) {
        val c = lastCanvas!!
        val h = size / 2f; val r = size / 4f
        val outline = Path()
        fun silhouette(dy: Float) = rounded(outline, size * .14f, x + h, y + dy, x + size, y + r + dy, x + size, y + size - r + dy, x + h, y + size + dy, x, y + size - r + dy, x, y + r + dy)
        fill(Theme.darken(color, .45f)); silhouette(size * .09f); c.drawPath(outline, paint)
        silhouette(0f)
        c.save(); c.clipPath(outline)
        val left = { u: Float, v: Float -> floatArrayOf(x + u * h, y + r + u * r + v * 2f * r) }
        val right = { u: Float, v: Float -> floatArrayOf(x + h + u * h, y + 2f * r - u * r + v * 2f * r) }
        val top = { u: Float, w: Float -> floatArrayOf(x + u * h + w * h, y + r + u * r - w * r) }
        fun face(color: Int, map: (Float, Float) -> FloatArray, a: Float = 0f, b: Float = 1f, cc: Float = 0f, d: Float = 1f) {
            val p1 = map(a, cc); val p2 = map(b, cc); val p3 = map(b, d); val p4 = map(a, d)
            fill(color); shape(p1[0], p1[1], p2[0], p2[1], p3[0], p3[1], p4[0], p4[1])
        }
        face(color, left); face(Theme.darken(color, .2f), right); face(Theme.lighten(color, .38f), top)
        // Bevel: a light edge where the top meets the sides, and a gloss on the top's far corner.
        paint.style = Paint.Style.STROKE; paint.strokeWidth = size * .05f; paint.strokeJoin = Paint.Join.ROUND; paint.color = Theme.alpha(Theme.WHITE, 120)
        val a = left(0f, 0f); val m = left(1f, 0f); val e = right(1f, 0f)
        path.reset(); path.moveTo(a[0], a[1]); path.lineTo(m[0], m[1]); path.lineTo(e[0], e[1]); c.drawPath(path, paint)
        val g1 = top(.18f, .62f); val g2 = top(.18f, .86f); val g3 = top(.42f, .86f)
        paint.strokeWidth = size * .07f; paint.strokeCap = Paint.Cap.ROUND; paint.color = Theme.alpha(Theme.WHITE, 200)
        path.reset(); path.moveTo(g1[0], g1[1]); path.lineTo(g2[0], g2[1]); path.lineTo(g3[0], g3[1]); c.drawPath(path, paint)
        c.restore()
    }

    /** A glossy candy bubble: a cyan rim on a darker lip, a pale face and a big white glint. */
    private fun bubble(x: Float, y: Float, r: Float) {
        val c = lastCanvas!!
        fill(Theme.darken(Theme.CYAN, .3f)); c.drawCircle(x, y + r * .12f, r, paint)
        fill(Theme.CYAN); c.drawCircle(x, y, r, paint)
        fill(Theme.lighten(Theme.CYAN, .62f)); c.drawCircle(x, y, r * .78f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = r * .16f; paint.strokeCap = Paint.Cap.ROUND; paint.color = Theme.WHITE
        rect.set(x - r * .55f, y - r * .55f, x + r * .55f, y + r * .55f); c.drawArc(rect, 195f, 70f, false, paint)
        fill(Theme.WHITE); c.drawCircle(x + r * .38f, y + r * .38f, r * .1f, paint)
    }

    /** [n] coins seen edge-on, stacked from [bottom] up, the top one face-up. */
    private fun coinStack(cx: Float, bottom: Float, n: Int) {
        val w = 17f
        for (i in 0 until n) {
            val y = bottom - i * 4.6f
            fill(Theme.darken(Theme.GOLD, .38f)); rect.set(cx - w / 2f, y - 7f, cx + w / 2f, y + 1.5f); lastCanvas!!.drawRoundRect(rect, w / 2f, 3.5f, paint)
            fill(Theme.darken(Theme.GOLD, .12f)); rect.set(cx - w / 2f, y - 7f, cx + w / 2f, y - .5f); lastCanvas!!.drawRoundRect(rect, w / 2f, 3.2f, paint)
            fill(Theme.GOLD); rect.set(cx - w / 2f, y - 9f, cx + w / 2f, y - 3f); lastCanvas!!.drawOval(rect, paint)
        }
        val top = bottom - (n - 1) * 4.6f
        fill(Theme.lighten(Theme.GOLD, .35f)); rect.set(cx - w * .3f, top - 7.5f, cx + w * .3f, top - 4.5f); lastCanvas!!.drawOval(rect, paint)
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
