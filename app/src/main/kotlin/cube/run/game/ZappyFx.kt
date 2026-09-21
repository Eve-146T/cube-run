package cube.run.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/** Two short, reusable electrical traces; fast opposite swipes never wait for an animation. */
internal class ZappyFx(unit: Model) {
    private class Piece(unit: Model, color: Color) {
        val instance = ModelInstance(unit)
        val blend = BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE, 0f)
        init {
            instance.materials.first().set(ColorAttribute.createDiffuse(color),
                ColorAttribute.createEmissive(color), blend,
                DepthTestAttribute(GL20.GL_LEQUAL, 0f, 1f, false))
        }
    }
    private class Trace(unit: Model) {
        var age = LIFE
        var fresh = false
        var from = 0f
        var to = 0f
        var y = 0f
        var drift = 0f
        val pose = Matrix4()
        val ghost = Piece(unit, Color(.58f, .18f, 1f, 1f))
        val flash = Piece(unit, Color(.9f, .78f, 1f, 1f))
        val arc = Array(8) { Piece(unit, if (it % 2 == 0) Color(.6f, .16f, 1f, 1f) else Color(.96f, .87f, 1f, 1f)) }
        val sparks = Array(4) { Piece(unit, if (it % 2 == 0) Color(.76f, .4f, 1f, 1f) else Color(.98f, .93f, 1f, 1f)) }
    }
    private val traces = Array(2) { Trace(unit) }
    private var next = 0
    private var latest = -1
    private val position = Vector3()

    fun clear() { traces.forEach { it.age = LIFE }; latest = -1 }

    fun fire(from: Float, to: Float, pose: Matrix4) {
        val trace = traces[next]
        latest = next
        next = (next + 1) % traces.size
        trace.age = 0f; trace.fresh = true
        trace.from = from; trace.to = to; trace.drift = 0f
        trace.pose.set(pose)
        pose.getTranslation(position)
        // A second swipe may arrive before update rebuilt the body transform.
        trace.pose.setTranslation(from, position.y, position.z)
        trace.y = position.y
    }

    fun update(dt: Float, movement: Float) {
        for (trace in traces) {
            if (trace.age >= LIFE) continue
            // Guarantee one visible attack frame even when input arrives on a long frame.
            if (trace.fresh) trace.fresh = false else trace.age += dt
            trace.drift += movement * .35f
        }
    }

    fun render(batch: ModelBatch, env: Environment, ground: Float, livePose: Matrix4) {
        for (index in traces.indices) {
            val trace = traces[index]
            if (trace.age >= LIFE) continue
            val age = trace.age
            val direction = if (trace.to > trace.from) 1f else -1f
            val tail = max(0f, 1f - age / .15f)
            val travel = age / LIFE
            if (tail > 0f) {
                trace.ghost.blend.opacity = .32f * tail * tail
                trace.ghost.instance.transform.set(trace.pose)
                    .setTranslation(trace.from + direction * travel * .22f, trace.y + ground, trace.drift)
                    .scale(1f - travel * .7f, 1f + travel * .1f, 1f)
                batch.render(trace.ghost.instance, env)
            }
            val attack = max(0f, 1f - age / .075f)
            if (attack > 0f) {
                trace.flash.blend.opacity = .62f * attack * attack
                if (index == latest) trace.flash.instance.transform.set(livePose).scale(1.07f, 1.07f, 1.07f)
                else trace.flash.instance.transform.set(trace.pose)
                    .setTranslation(trace.to, trace.y + ground, trace.drift).scale(1.07f, 1.07f, 1.07f)
                batch.render(trace.flash.instance, env)
            }
            val electricity = max(0f, 1f - age / .105f)
            if (electricity > 0f) {
                // One deliberate jagged stroke establishes direction without covering the road.
                var x = trace.from + direction * .28f
                var y = trace.y + ground
                for (segment in 0..3) {
                    val fraction = (segment + 1) * .25f
                    val xx = trace.from + direction * .28f + (trace.to - trace.from - direction * .56f) * fraction
                    val yy = trace.y + ground + when (segment) { 0 -> .14f; 1 -> -.12f; 2 -> .09f; else -> 0f }
                    val dx = xx - x; val dy = yy - y
                    val length = sqrt(dx * dx + dy * dy)
                    val angle = atan2(dy, dx) * 57.29578f
                    for (layer in 0..1) {
                        val piece = trace.arc[segment * 2 + layer]
                        val thickness = (if (layer == 0) .065f else .022f) * (.45f + .55f * electricity)
                        piece.blend.opacity = electricity * electricity * if (layer == 0) .48f else .92f
                        piece.instance.transform.setToTranslation((x + xx) * .5f, (y + yy) * .5f, .16f + trace.drift)
                            .rotate(Vector3.Z, angle).scale(length, thickness, thickness)
                        batch.render(piece.instance, env)
                    }
                    x = xx; y = yy
                }
            }
            val sparkFade = 1f - travel
            for (i in trace.sparks.indices) {
                val piece = trace.sparks[i]
                val upper = if (i < 2) 1f else -1f
                val side = if (i % 2 == 0) direction else -direction
                val reach = .48f + travel * .32f
                piece.blend.opacity = .8f * sparkFade * sparkFade
                piece.instance.transform.setToTranslation(trace.to + side * reach,
                    trace.y + ground + upper * (.18f + travel * .2f), .16f + trace.drift)
                    .rotate(Vector3.Z, side * upper * 28f)
                    .scale(.16f * sparkFade + .025f, .026f * sparkFade + .008f, .026f)
                batch.render(piece.instance, env)
            }
        }
    }

    private companion object { const val LIFE = .18f }
}
