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
internal class ChallengeIcon(private val id: String) : CandyArt(id) {
    override fun picture(canvas: Canvas) {
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
            "very_silent_treatment" -> {
                // Silence all the way down: the muted speaker, asleep.
                lipped(Theme.WHITE) { shape(4f, 20f, 12f, 20f, 23f, 10f, 23f, 40f, 12f, 31f, 4f, 31f) }
                line(29f, 22f, 39f, 32f, Theme.BERRY, 4.5f); line(39f, 22f, 29f, 32f, Theme.BERRY, 4.5f)
                paint.typeface = Fonts.cached(700); paint.textAlign = Paint.Align.CENTER
                for ((x, y, size) in listOf(Triple(33f, 14f, 10f), Triple(41f, 7f, 8f))) {
                    paint.textSize = size; fill(Theme.LAVENDER); lastCanvas!!.drawText("z", x, y, paint)
                }
                paint.textAlign = Paint.Align.LEFT
            }
            "secret" -> {
                // Locked away until you find it.
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.strokeCap = Paint.Cap.BUTT
                for (pass in 0..1) {
                    paint.color = if (pass == 0) Theme.darken(Theme.GOLD, .35f) else Theme.lighten(Theme.GOLD, .1f)
                    rect.set(15f, 5f + (if (pass == 0) 2f else 0f), 33f, 29f + (if (pass == 0) 2f else 0f))
                    lastCanvas!!.drawArc(rect, 180f, 180f, false, paint)
                    lastCanvas!!.drawLine(15f, 17f + (if (pass == 0) 2f else 0f), 15f, 22f, paint); lastCanvas!!.drawLine(33f, 17f + (if (pass == 0) 2f else 0f), 33f, 22f, paint)
                }
                lipped(Theme.GOLD) { round(9f, 21f, 39f, 44f, 5f) }
                fill(Theme.alpha(Theme.WHITE, 150)); round(13f, 24f, 28f, 26.5f, 1.2f)
                fill(Theme.darken(Theme.GOLD, .5f)); lastCanvas!!.drawCircle(24f, 31f, 3f, paint); round(22.5f, 31f, 25.5f, 38f, 1.5f)
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
            "icarus" -> {
                // A poison coin that flew too high: a skull, on white wings.
                val c = lastCanvas!!
                for (side in listOf(-1f, 1f)) lipped(Theme.WHITE) {
                    path.reset()
                    path.moveTo(24f + side * 10f, 17f)
                    path.cubicTo(24f + side * 20f, 6f, 24f + side * 23f, 8f, 24f + side * 23f, 11f)
                    path.quadTo(24f + side * 23.5f, 16f, 24f + side * 19f, 18f)
                    path.quadTo(24f + side * 22f, 21f, 24f + side * 17f, 24f)
                    path.quadTo(24f + side * 19f, 28f, 24f + side * 13f, 30f)
                    path.close(); c.drawPath(path, paint)
                }
                disc(24f, 25f, 14f, TOXIC)
                fill(Theme.darken(TOXIC, .18f)); c.drawCircle(24f, 25f, 10.5f, paint)
                fill(Theme.WHITE); c.drawCircle(24f, 23.5f, 7f, paint); round(20f, 26f, 28f, 32.5f, 2.2f)
                fill(Theme.darken(TOXIC, .55f)); c.drawCircle(21.3f, 23.5f, 2.2f, paint); c.drawCircle(26.7f, 23.5f, 2.2f, paint)
                for (x in listOf(22.2f, 24.6f)) round(x, 29f, x + 1.3f, 32.5f, .65f)
                gloss(24f, 25f, 14f)
            }
            "sky_sweeper" -> {
                // A jetpack climbing away up the coin line it has swept clean.
                icon(CoinIcon(), -2f, 29f, 18f); icon(CoinIcon(), 8f, 18f, 19f)
                val c = lastCanvas!!
                c.save(); c.rotate(40f, 31f, 18f); icon(JetIcon(), 13f, -2f, 38f); c.restore()
                sparkle(6f, 8f, 4.5f)
            }
            "poltergeist" -> {
                // The Ghost cube's spirit: a sheet with a wavy hem.
                fun sheet() {
                    path.reset(); path.moveTo(9f, 40f); path.lineTo(9f, 22f)
                    path.cubicTo(9f, 3f, 39f, 3f, 39f, 22f); path.lineTo(39f, 40f)
                    path.quadTo(36.5f, 44.5f, 34f, 40f); path.quadTo(31.5f, 35.5f, 29f, 40f)
                    path.quadTo(26.5f, 44.5f, 24f, 40f); path.quadTo(21.5f, 35.5f, 19f, 40f)
                    path.quadTo(16.5f, 44.5f, 14f, 40f); path.quadTo(11.5f, 35.5f, 9f, 40f); path.close()
                    lastCanvas!!.drawPath(path, paint)
                }
                lipped(Theme.WHITE) { sheet() }
                fill(Theme.INK); rect.set(16f, 17f, 22f, 25f); canvas.drawOval(rect, paint); rect.set(26f, 17f, 32f, 25f); canvas.drawOval(rect, paint)
                fill(Theme.alpha(Theme.INK, 200)); rect.set(21f, 28f, 27f, 33f); canvas.drawOval(rect, paint)
                fill(Theme.WHITE); canvas.drawCircle(18.2f, 19.5f, 1.3f, paint); canvas.drawCircle(28.2f, 19.5f, 1.3f, paint)
            }
            "juggernaut" -> {
                // A candy steamroller (Dampfwalze, the German name): nothing in its lane stays standing.
                val c = lastCanvas!!
                lipped(Theme.INK) { round(19f, 9f, 24f, 21f, 1.5f) }
                fill(Theme.WHITE); c.drawCircle(21.5f, 6f, 3.2f, paint); c.drawCircle(25.5f, 3.5f, 2.4f, paint)
                lipped(Theme.YELLOW) { round(17f, 19f, 45f, 35f, 4f) }
                lipped(Theme.YELLOW) { round(28f, 5f, 43f, 21f, 3.5f) }
                fill(Theme.lighten(Theme.SKY, .45f)); round(31f, 8.5f, 40f, 16f, 2f)
                fill(Theme.alpha(Theme.WHITE, 200)); round(33f, 23f, 42f, 25.5f, 1.25f)
                disc(38f, 37f, 7.5f, Theme.INK); fill(Theme.LAVENDER); c.drawCircle(38f, 37f, 2.6f, paint)
                disc(15f, 32f, 13f, Theme.BERRY)
                fill(Theme.darken(Theme.BERRY, .2f)); c.drawCircle(15f, 32f, 5.2f, paint)
                fill(Theme.YELLOW); c.drawCircle(15f, 32f, 2.5f, paint)
                gloss(15f, 32f, 13f)
            }
            "full_throttle" -> {
                // The speedometer pinned at the top of its dial.
                val c = lastCanvas!!
                fill(Theme.darken(Theme.WHITE, .3f)); rect.set(3f, 9.5f, 45f, 51.5f); c.drawArc(rect, 180f, 180f, true, paint)
                round(3f, 28f, 45f, 35f, 3.5f)
                fill(Theme.WHITE); rect.set(3f, 7f, 45f, 49f); c.drawArc(rect, 180f, 180f, true, paint)
                round(3f, 26f, 45f, 32.5f, 3.5f)
                paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.BUTT; paint.strokeWidth = 5.5f
                rect.set(9f, 13f, 39f, 43f)
                paint.color = Theme.MINT; c.drawArc(rect, 182f, 58f, false, paint)
                paint.color = Theme.YELLOW; c.drawArc(rect, 241f, 58f, false, paint)
                paint.color = Theme.BERRY; c.drawArc(rect, 300f, 58f, false, paint)
                line(24f, 28f, 38.5f, 21.5f, Theme.INK, 4f)
                fill(Theme.INK); c.drawCircle(24f, 28f, 4.2f, paint)
                fill(Theme.ORANGE); c.drawCircle(24f, 28f, 2f, paint)
                sparkle(42f, 6f, 5f, Theme.YELLOW)
            }
            "news" -> {
                // What's new: a fat glossy star with a twinkle (the "In this update" card).
                val star = Path()
                rounded(star, 3f, *FloatArray(20) { k ->
                    val i = k / 2; val r = if (i % 2 == 0) 21f else 10f
                    val a = Math.toRadians(i * 36.0 - 90.0)
                    if (k % 2 == 0) 24f + (r * Math.cos(a)).toFloat() else 26f + (r * Math.sin(a)).toFloat()
                })
                lipped(Theme.YELLOW) { lastCanvas!!.drawPath(star, paint) }
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.8f; paint.strokeCap = Paint.Cap.ROUND; paint.color = Theme.alpha(Theme.WHITE, 220)
                canvas.drawLine(17f, 22f, 21f, 15f, paint)
                sparkle(41f, 8f, 5f)
            }
            "tweaks" -> {
                // "And the usual" on the update card: a chunky wrench, tilted, with a twinkle.
                val c = lastCanvas!!
                val wrench = Path().apply {
                    addCircle(24f, 12f, 10.5f, Path.Direction.CW)
                    addRoundRect(RectF(20f, 14f, 28f, 41f), 4f, 4f, Path.Direction.CW)
                    addCircle(24f, 39f, 6.5f, Path.Direction.CW)
                    op(Path().apply { addRoundRect(RectF(20.2f, -1f, 27.8f, 12.5f), 2.5f, 2.5f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
                    op(Path().apply { addCircle(24f, 39f, 2.6f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
                }
                // The lip drops straight down, as on every other icon, so it is offset before the tilt.
                c.save(); c.translate(0f, 2.5f); c.rotate(42f, 24f, 25f)
                fill(Theme.darken(Theme.WHITE, .3f)); c.drawPath(wrench, paint)
                c.restore()
                c.save(); c.rotate(42f, 24f, 25f)
                fill(Theme.WHITE); c.drawPath(wrench, paint)
                fill(Theme.alpha(Theme.MINT, 120)); round(22.2f, 17f, 25.8f, 33f, 1.8f)
                c.restore()
                sparkle(40f, 8f, 5f, Theme.YELLOW)
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
    }

    private companion object {
        const val PILL_RED = 0xFFFF2E3F.toInt()
        /** The Toxic cube's poison green. */
        const val TOXIC = 0xFF5BE33A.toInt()
    }
}
