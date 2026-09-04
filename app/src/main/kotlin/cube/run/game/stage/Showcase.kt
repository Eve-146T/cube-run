package cube.run.game.stage

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import cube.run.core.Gdx3DGame
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.core.hsvInto
import cube.run.data.Wardrobe
import cube.run.game.Bubble
import cube.run.game.Player
import cube.run.game.RunCamera
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The stage the wardrobe, the shop and the results use: the cube floating
 * in a deep, world-tinted sky with a ring of sparkles, and — on the results
 * and in the shop — a sunburst spinning behind it, centred on it. Nothing
 * snaps: the wardrobe's categories morph into each other (the bubble
 * inflates around the cube, the cube glides out onto its trail loop and
 * back), every item switch is a spin-flip with a pop, and purchases play
 * their [Demos] on the cube.
 */
class Showcase(private val game: Gdx3DGame, private val player: Player, private val bubble: Bubble) {

    var active = false
        private set

    private val skyTopSave = Color()
    private val skyBottomSave = Color()
    /** The stage's own sky (the game blends back out of it after [exit]). */
    val stageTop = Color()
    val stageBottom = Color()
    private val gold = Color()
    private val spark = Color()
    private val rayCol = Color()
    private val tmp = Vector3()
    private val tmpCol = Color()
    private val demos = Demos(game, player, bubble)
    private var enterT = 0f
    private var trailMix = 0f      // eased 0..1: the cube is out on its trail loop
    private var bubbleMix = 0f     // eased 0..1: the wardrobe bubble is inflated
    private var kickV = 0f         // spin-flip velocity on an item switch (deg/s)
    private var kickA = 0f         // accumulated extra yaw
    private var pop = 0f           // 1 → 0: the switch pop (scale + a burst of rays)
    private var wide = 0f          // eased camera pull-back for the loop

    private fun cat(): Int = if (Stage.mode == Stage.SKINS) Stage.previewCat else Wardrobe.CUBE

    /** [tint] is the world's hue: the backdrop stays a deep version of where you were. */
    fun enter(bgTop: Color, bgBottom: Color, tint: Float) {
        active = true
        enterT = 0f
        trailMix = 0f; bubbleMix = 0f; kickV = 0f; kickA = 0f; pop = 0f; wide = 0f
        demos.reset()
        skyTopSave.set(bgTop); skyBottomSave.set(bgBottom)
        hsvInto(stageTop, tint - 20f, 0.65f, 0.42f)
        hsvInto(stageBottom, tint + 20f, 0.75f, 0.10f)
        hsvInto(spark, tint + 180f, 0.5f, 1f)
        hsvInto(gold, 46f, 0.8f, 1f)
    }

    /** Ease the sky from where the run was into the stage's deep tint. */
    fun tintSky(bgTop: Color, bgBottom: Color) {
        val k = min(1f, enterT * 2.8f)
        bgTop.set(skyTopSave).lerp(stageTop, k)
        bgBottom.set(skyBottomSave).lerp(stageBottom, k)
    }

    fun exit(bgTop: Color, bgBottom: Color) {
        active = false
        bgTop.set(skyTopSave); bgBottom.set(skyBottomSave)
    }

    fun update(dt: Float, time: Float, baseHue: Float) {
        enterT += dt
        val c = cat()
        trailMix += ((if (c == Wardrobe.TRAIL) 1f else 0f) - trailMix) * min(1f, dt * 3.2f)
        bubbleMix += ((if (c == Wardrobe.BUBBLE) 1f else 0f) - bubbleMix) * min(1f, dt * 4.5f)
        wide += ((if (c == Wardrobe.TRAIL) 1f else 0f) - wide) * min(1f, dt * 3f)
        if (Stage.previewKicks.getAndSet(0) > 0) { // a new item: spin-flip, pop, shards in its colour
            kickV = 1100f; pop = 1f
            SoundFx.play("pop", rate = 1.25f, vol = 0.8f); Haptics.click()
            game.burst3d(tmp.set(player.px, player.py, 0.3f), player.trailCol(), n = 18, speed = 4.5f, size = 0.11f, life = 0.55f)
        }
        if (Stage.previewBuys.getAndSet(0) > 0) { // bought: a double flip, gold rays, a shower of gold and its own colour
            kickV = 2000f; pop = 1.6f
            SoundFx.play("success", rate = 1.2f); SoundFx.play("boom", rate = 1.8f, vol = 0.3f); Haptics.success()
            game.flash(gold, 0.25f)
            game.burst3d(tmp.set(player.px, player.py, 0.3f), gold, n = 30, speed = 7f, size = 0.13f, life = 0.9f)
            game.burst3d(tmp, player.trailCol(), n = 20, speed = 5f, size = 0.12f, life = 0.7f)
            game.burst3d(tmp, Color.WHITE, n = 12, speed = 10f, size = 0.08f, life = 0.45f)
        }
        kickA += kickV * dt
        kickV = max(0f, kickV - 2400f * dt)
        pop = max(0f, pop - dt * 2.6f)
        // where the cube is: the centre, or out on the loop (eased between)
        val lx = 0.9f * sin(time * 1.4f)
        val ly = 1.0f + 0.35f * sin(time * 2.8f)
        val x = lx * trailMix
        val y = 1.0f + (ly - 1.0f) * trailMix + demos.lift
        val spin = 45f + 115f * trailMix
        val scale = 1f + 0.22f * sin(min(1f, pop) * 3.14159f)
        player.showcase(time, baseHue, x, y, spin, extraYaw = kickA, scale = scale)
        if (trailMix > 0.08f) player.emitTrail(dt, time, x - 0.3f * cos(time * 1.4f), y, 0.3f, boost = 1.6f * trailMix, scale = 2.4f)
        demos.update(dt, time, player.px, player.py)
    }

    fun aim(rig: RunCamera) {
        when (Stage.mode) {
            Stage.RESULT -> rig.results(game.fractionForDp(36f + 120f))         // the results column starts below this
            Stage.SHOP -> rig.shop(game.fractionForDp(36f + 60f + 75f))         // the middle of the showroom strip
            else -> rig.wardrobe(wide)
        }
    }

    /** The ring of sparkles around the cube, plus whatever demo is running. */
    fun render(time: Float) {
        val t = min(1f, enterT * 1.5f)
        for (i in 0 until 8) {
            val a = time * 0.9f + i * 0.785f
            val r = (2.1f + 0.25f * sin(time * 2f + i)) * t
            val y = player.py + 0.5f * sin(time * 1.6f + i * 1.3f)
            val s = 0.07f + 0.05f * (0.5f + 0.5f * sin(time * 5f + i * 2f))
            game.worldBoxSpin(player.px + cos(a) * r, y, sin(a) * r, s, s, s, time * 120f + i * 45f, spark)
        }
        demos.render(time)
    }

    /** The sunburst behind the cube: the results' hype pattern, a soft one in the shop, a flash on a wardrobe switch. */
    fun renderShapes(shapes: ShapeRenderer, time: Float) {
        val grow = min(1f, enterT * 1.4f)
        val g = 1f - (1f - grow) * (1f - grow)
        when (Stage.mode) {
            Stage.RESULT -> { // one sunburst, centred on the cube as seen on screen
                hsvInto(rayCol, Stage.resultHue, 0.5f, 0.9f)
                game.sunburstBehind(shapes, player.px, player.py, 0f, 3f, 12f * g, 14, time * 18f, rayCol, (if (Stage.resultRecord) 0.5f else 0.36f) * g, 0.5f)
            }
            Stage.SHOP -> {
                hsvInto(rayCol, 46f, 0.45f, 0.9f)
                game.sunburstBehind(shapes, player.px, player.py, 0f, 3f, 10f * g, 12, time * 14f, rayCol, 0.28f * g, 0.45f)
            }
            else -> if (pop > 0.01f) {
                val p = min(1f, pop)
                game.sunburst(shapes, player.px, player.py, -2.5f, 3f + 8f * (1f - p), 12, time * 60f, if (pop > 1f) gold else Color.WHITE, p * p * 0.55f, 0.4f)
            }
        }
    }

    fun renderBlended(cam: PerspectiveCamera, time: Float) {
        if (bubbleMix > 0.01f) bubble.showcase(cam, time, player.px, player.py, inflate = bubbleMix)
        demos.renderBlended(cam, time)
    }
}
