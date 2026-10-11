package cube.run.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.GameActivity
import cube.run.data.Achievements
import cube.run.data.Progress
import cube.run.data.Settings
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Measures actual painted silhouettes after the ImageView matrix, including the raised-face lip. */
class AchievementAlignmentTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun field(owner: Any, name: String): java.lang.reflect.Field {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true } }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        throw NoSuchFieldException(name)
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun restore(store: SharedPreferences, values: Map<String, *>) {
        val edit = store.edit().clear()
        for ((key, value) in values) when (value) {
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is String -> edit.putString(key, value)
            is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
        edit.commit()
    }
    private fun paintedBounds(width: Int, height: Int, draw: (Canvas) -> Unit): RectF {
        assertTrue(width > 0 && height > 0)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap))
        val pixels = IntArray(width * height); bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var left = width; var top = height; var right = -1; var bottom = -1
        for (y in 0 until height) for (x in 0 until width) {
            if (Color.alpha(pixels[y * width + x]) >= 16) {
                left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y)
            }
        }
        bitmap.recycle(); assertTrue("Drawable produces visible artwork", right >= left && bottom >= top)
        return RectF(left.toFloat(), top.toFloat(), right + 1f, bottom + 1f)
    }
    private fun artBounds(image: ImageView): RectF = paintedBounds(image.width, image.height) { canvas ->
        canvas.translate(image.paddingLeft.toFloat(), image.paddingTop.toFloat())
        canvas.concat(image.imageMatrix)
        image.drawable.draw(canvas)
    }
    private fun assertCenter(label: String, painted: RectF, expectedX: Float, expectedY: Float, tolerance: Float) {
        assertEquals("$label painted horizontal center", expectedX, painted.centerX(), tolerance)
        assertEquals("$label painted vertical center", expectedY, painted.centerY(), tolerance)
    }

    @Test fun allTilesCenterInTheirSlotsAndBadgesSitEvenlyOnTheLadder() {
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val scores = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
        val saved = prefs.all; val savedScores = scores.all
        try {
            for (earned in listOf(false, true)) {
                prefs.edit().clear().putBoolean("achievements_unlocked", true)
                    .putInt("achievement_best_score", if (earned) 5001 else 0)
                    .putInt("total_coins", if (earned) 500000 else 0)
                    .putInt("owned_skins", if (earned) 0xFFFFFF else 1)
                    .putInt("total_powerups", if (earned) 10000 else 0)
                    .putInt("boxes_opened", if (earned) 300 else 0)
                    .putInt("max_bubbles", if (earned) 1000 else 0)
                    .putInt("max_run_bounces", if (earned) 67 else 0)
                    .putInt("best_centered_score", if (earned) 100 else 0)
                    .putInt("best_coinless_score", if (earned) 60 else 0)
                    .putInt("max_run_missed_boxes", if (earned) 10 else 0)
                    .putInt("total_mute_toggles", if (earned) 1000 else 0).commit()
                scores.edit().clear().commit(); Settings.init(context); Settings.setDevMode(false); Progress.init(context)
                ActivityScenario.launch(GameActivity::class.java).use { scenario ->
                    var ready = false
                    val deadline = SystemClock.uptimeMillis() + 15000
                    while (!ready && SystemClock.uptimeMillis() < deadline) {
                        scenario.onActivity { ready = field(it, "hud").get(it) != null }
                        if (!ready) SystemClock.sleep(60)
                    }
                    assertTrue("HUD ready for real achievement layout", ready)
                    scenario.onActivity {
                        val hud = field(it, "hud").get(it) as Hud
                        Hud::class.java.getDeclaredMethod("openAchievements").apply { isAccessible = true }.invoke(hud)
                    }
                    SystemClock.sleep(700)
                    scenario.onActivity { activity ->
                        val hud = field(activity, "hud").get(activity) as Hud
                        val page = field(hud, "page").get(hud) as AchievementsView
                        val kit = UiKit(activity); val tolerance = kit.dpf(1f)
                        val images = descendants(page).filterIsInstance<ImageView>()
                        val categoryImages = Achievements.all.map { definition ->
                            images.single { it.tag == "achievement_icon_${definition.id}" }
                        }
                        for ((definition, image) in Achievements.all.zip(categoryImages)) {
                            // The tile, face and lip together, centres in its slot and stays square.
                            val painted = artBounds(image)
                            assertCenter(definition.id, painted, image.width / 2f, image.height / 2f, tolerance)
                            assertEquals("${definition.id} tile keeps square aspect", painted.width(), painted.height(), tolerance * 2)
                            if (!earned) {
                                // What a reward pays stays hidden until it can be claimed.
                                assertTrue("${definition.id} shows no future payout", descendants(page).none { it.tag == "achievement_claim_${definition.id}" })
                            }
                        }
                        for (definition in Achievements.all.filter { it.tiered }) {
                            // The four badges sit on the ladder's bar as evenly spaced checkpoints, the last at its end.
                            val track = descendants(page).single { it.tag == "achievement_track_${definition.id}" } as ViewGroup
                            val bar = descendants(track).single { it.tag == "achievement_progress_${definition.id}" }
                            val badges = (0..3).map { tier -> images.single { it.tag == "achievement_badge_${definition.id}_$tier" } }
                            val centres = badges.map { it.left + it.width / 2f }
                            val step = centres[1] - centres[0]
                            assertTrue("${definition.id} badges run left to right", step > 0f)
                            for (i in 1..3) assertEquals("${definition.id} badge $i evenly spaced", step, centres[i] - centres[i - 1], tolerance * 2)
                            assertEquals("${definition.id} diamond ends the ladder", track.width.toFloat(), badges.last().right.toFloat(), tolerance * 2)
                            assertEquals("${definition.id} bar ends under the diamond", centres.last(), bar.right.toFloat(), tolerance * 2)
                            for ((tier, badge) in badges.withIndex()) {
                                assertEquals("${definition.id} tier $tier shares the row", badges.first().top, badge.top)
                                assertEquals("${definition.id} tier $tier sits on the bar", (bar.top + bar.bottom) / 2f, (badge.top + badge.bottom) / 2f, tolerance)
                                // The lip under a badge (or over an empty slot) shifts its paint a little off centre on purpose.
                                val artwork = artBounds(badge)
                                assertEquals("${definition.id} tier $tier painted horizontal center", badge.width / 2f, artwork.centerX(), tolerance)
                                assertEquals("${definition.id} tier $tier painted vertical center", badge.height / 2f, artwork.centerY(), tolerance * 1.6f)
                            }
                        }
                        // A compact native-rendered diagnostic strip preserves the actual background/foreground relationship.
                        val gap = kit.dp(8f); val stripWidth = categoryImages.sumOf { it.width + gap } + gap
                        val stripHeight = categoryImages.maxOf { it.height } + kit.dp(30f)
                        val strip = Bitmap.createBitmap(stripWidth, stripHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(strip); canvas.drawColor(0xFF211B3D.toInt())
                        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = kit.dpf(9f) }
                        var x = gap
                        for ((definition, image) in Achievements.all.zip(categoryImages)) {
                            canvas.save(); canvas.translate(x.toFloat(), gap.toFloat()); image.draw(canvas); canvas.restore()
                            canvas.drawText(definition.id, x.toFloat(), stripHeight - kit.dpf(4f), label)
                            x += image.width + gap
                        }
                        val output = File(context.getExternalFilesDir(null), "achievements-hardware").apply { mkdirs() }
                        File(output, "achievement-alignment-${if (earned) "earned" else "locked"}.png").outputStream().use { strip.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        strip.recycle()
                    }
                }
            }
        } finally { restore(prefs, saved); restore(scores, savedScores); Progress.init(context) }
    }
}
