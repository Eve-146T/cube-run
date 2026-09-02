package cube.run.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import cube.run.core.Gdx3DGame
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.core.hsvInto
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The fire boost: a button shown for the first [window] seconds of a run. Each
 * tap front-loads your speed; [maxTaps] taps launch you at 80% of the blue-line
 * (cruise) speed. Drawn as 5 stacked "^" chevrons top-right that flicker in the
 * window's final seconds, then disappear.
 */
class FireBoost(private val game: Gdx3DGame, private val difficulty: Difficulty) {

    private val window = 15f
    private val maxTaps = 5
    private val maxSpd = difficulty.cruiseSpd * 0.8f
    private val maxDiff = (maxSpd - difficulty.minSpd) / (difficulty.maxSpd - difficulty.minSpd)
    private var taps = 0
    private var runTime = 0f
    private val tmp = Vector3()
    private val tmpCol = Color()

    fun reset() { taps = 0; runTime = 0f }
    fun tick(dt: Float) { runTime += dt }

    /** Visible while the window is open (the game passes whether a run is live). */
    fun visible(live: Boolean): Boolean = live && runTime < window
    fun available(live: Boolean): Boolean = visible(live) && taps < maxTaps

    // button geometry (touch coords: origin top-left, y down) — top-right, a bit down
    private fun cx() = game.sw * 0.85f
    private fun cy() = game.sh * 0.20f
    private fun r() = game.sw * 0.14f

    fun inZone(x: Float, y: Float): Boolean {
        val dx = x - cx(); val dy = y - cy()
        return dx * dx + dy * dy < r() * r()
    }

    /** One tap: bump difficulty a notch with a hot orange punch. Returns true if it counted. */
    fun tap(px: Float, py: Float, live: Boolean): Boolean {
        if (!available(live)) return false
        taps++
        difficulty.boostTo(difficulty.startDiff + (maxDiff - difficulty.startDiff) * (taps / maxTaps.toFloat()))
        SoundFx.play("rise", rate = 0.85f + taps * 0.12f)
        Haptics.click()
        game.flash(hsvInto(tmpCol, 22f, 0.85f, 1f), 0.12f)
        game.burst3d(tmp.set(px, py + 0.3f, 0.3f), hsvInto(tmpCol, 26f, 0.9f, 1f), n = 12, speed = 6f, size = 0.12f, life = 0.55f)
        return true
    }

    fun drawHud(shapes: ShapeRenderer, w: Float, h: Float, time: Float, live: Boolean) {
        if (!visible(live)) return
        // Minimalist: one clean opaque orange outline each (no glow/overlap). Each
        // tap lights one; lit chevrons carry a gentle fluid shimmer.
        val cx = cx()
        val cyDraw = h - cy()               // touch-space centre → draw space (y is up here)
        val gap = h * 0.024f
        val chevW = w * 0.055f
        val chevH = h * 0.020f
        val lineW = w * 0.014f
        val expiring = runTime > window - 4f
        val flick = if (expiring && sin(time * 26f) < -0.1f) 0.3f else 1f
        val y0 = cyDraw - 2f * gap          // bottom chevron; stack centred on cyDraw
        for (i in 0 until maxTaps) {
            val yBase = y0 + i * gap
            val lit = i < taps
            if (lit) {                      // bright orange with a subtle fluid shimmer
                val wave = 0.5f + 0.5f * sin(time * 5f - i * 0.8f)
                val v = 0.9f + 0.1f * wave
                shapes.setColor(v, 0.5f * v, 0.05f * v, flick)
            } else {                        // waiting: dim
                shapes.setColor(0.5f, 0.28f, 0.1f, 0.5f * flick)
            }
            chevron(shapes, cx, yBase, chevW, chevH, if (lit) lineW else lineW * 0.85f)
        }
    }

    /**
     * One constant-width "^" drawn as a single mitered band (4 triangles that abut
     * exactly — no overlap), so the whole chevron is one consistent opaque outline.
     */
    private fun chevron(shapes: ShapeRenderer, cx: Float, yBase: Float, chevW: Float, chevH: Float, lineW: Float) {
        val l = sqrt(chevW * chevW + chevH * chevH)
        val hw = lineW * 0.5f
        val ox = -chevH / l * hw   // outer-perpendicular offset
        val oy = chevW / l * hw
        val axO = cx - chevW + ox; val ayO = yBase + oy   // left tip, outer (top) edge
        val axI = cx - chevW - ox; val ayI = yBase - oy   // left tip, inner (under) edge
        val cxO = cx + chevW - ox                         // right tip, outer
        val cxI = cx + chevW + ox                         // right tip, inner
        val oTopY = ayO + (chevW - ox) / chevW * chevH    // apex, outer corner (on x = cx)
        val oBotY = ayI + (chevW + ox) / chevW * chevH    // apex, inner corner
        shapes.triangle(axO, ayO, cx, oTopY, cx, oBotY)   // left arm band
        shapes.triangle(axO, ayO, cx, oBotY, axI, ayI)
        shapes.triangle(cx, oTopY, cxO, ayO, cxI, ayI)    // right arm band
        shapes.triangle(cx, oTopY, cxI, ayI, cx, oBotY)
    }
}
