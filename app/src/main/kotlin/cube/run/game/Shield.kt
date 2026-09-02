package cube.run.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.math.Vector3
import cube.run.core.Gdx3DGame
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.core.hsvInto
import kotlin.math.sin

/**
 * The bubble shield (Subway-Surfers hoverboard, but a bubble): timed, absorbs
 * exactly one crash — the game then smashes the row and pops it. Rendered as
 * an additive iridescent sphere around the player plus a draining HUD bar.
 */
class Shield(private val game: Gdx3DGame) {

    /** The countdown (drawn by [PowerUps.drawBars]). */
    val timer = PowerUps.Timer(0.45f, 0.92f, 1f)
    /** Full duration (upgrade-dependent). */
    var duration = 10f
    val active: Boolean get() = timer.active
    val timeLeft: Float get() = timer.left

    private lateinit var inst: ModelInstance
    private lateinit var col: Color
    private lateinit var blend: BlendingAttribute
    private val tmp = Vector3()
    private val tmpCol = Color()

    fun init() {
        inst = ModelInstance(game.sphere(1f, Color.WHITE, div = 24))
        blend = BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE, 0.3f) // additive: reads as light, not glass
        inst.materials.first().set(ColorAttribute.createDiffuse(game.gdxHsv(190f, 0.5f, 1f)), blend)
        col = (inst.materials.first().get(ColorAttribute.Diffuse) as ColorAttribute).color
    }

    fun activate(px: Float, py: Float) {
        timer.start(duration)
        SoundFx.play("rise", rate = 1.3f)
        SoundFx.play("perfect", rate = 0.9f)
        Haptics.success()
        game.flash(hsvInto(tmpCol, 190f, 0.45f, 1f), 0.25f)
        game.burst3d(tmp.set(px, py, 0f), hsvInto(tmpCol, 190f, 0.5f, 1f), n = 24, speed = 6f, size = 0.14f, life = 0.7f)
        game.burst3d(tmp, Color.WHITE, n = 8, speed = 8f, size = 0.09f, life = 0.4f)
    }

    fun pop(px: Float, py: Float) {
        timer.stop()
        SoundFx.play("pop", rate = 0.55f)
        Haptics.click()
        game.burst3d(tmp.set(px, py, 0f), hsvInto(tmpCol, 190f, 0.5f, 1f), n = 36, speed = 8f, size = 0.12f, life = 0.8f)
        game.burst3d(tmp, Color.WHITE, n = 10, speed = 10f, size = 0.08f, life = 0.45f)
    }

    /** Tick the timer; poses the sphere around the player. Returns true on the frame it runs out. */
    fun update(dt: Float, time: Float, px: Float, py: Float): Boolean {
        if (!active) return false
        if (timer.tick(dt)) { // ran out quietly
            SoundFx.play("pop", rate = 0.7f, vol = 0.6f)
            game.burst3d(tmp.set(px, py, 0f), hsvInto(tmpCol, 190f, 0.5f, 1f), n = 16, speed = 4f, size = 0.1f, life = 0.6f)
            return true
        }
        // iridescent wobbling sphere; flickers in its last seconds
        val ending = timeLeft < 3f
        val wob = 0.06f * sin(time * 9f)
        val base = if (ending && sin(time * 28f) < 0f) 0.10f else 0.30f
        blend.opacity = base + 0.06f * sin(time * 5f)
        hsvInto(col, 170f + 90f * (0.5f + 0.5f * sin(time * 1.7f)), 0.55f, 1f)
        inst.transform.setToTranslation(px, py + 0.1f, 0f)
            .rotate(Vector3.Y, time * 40f)
            .scale(2.2f + wob, 2.2f - wob, 2.2f + wob * 0.5f)
        return false
    }

    fun render(batch: ModelBatch, env: Environment) {
        if (active) batch.render(inst, env)
    }
}
