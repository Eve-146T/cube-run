package cube.run.core.gfx

import com.badlogic.gdx.graphics.glutils.ShaderProgram

/**
 * A curved world, for the eye only: everything ahead of the player is
 * shifted sideways ([x]) and up ([y]) by the square of its distance past
 * [START], so the road ahead sweeps away round bends and over rises while
 * the stretch the cube is on stays straight (gameplay never bends). Each
 * world vertex shader adds `u_projViewTrans * vec4(bendOffset(world), 0.0)`
 * to its own unchanged projection, so with no bend (the default) every
 * pixel is exactly what it was. The sky does not bend.
 */
object WorldBend {
    private class Uniform(var x: Float, var y: Float, var handle: Int)
    private val uniforms = java.util.IdentityHashMap<ShaderProgram, Uniform>()
    internal fun resetUniforms() { uniforms.clear() }
    /** Curvature: offset per unit² of distance ahead. */
    @JvmField var x = 0f
    @JvmField var y = 0f

    /** Distance ahead of the player (−z) where the bend begins. */
    const val START = 3f

    /** Paste into a vertex shader before `main` (GLSL ES 1.0 and 3.0). One line, so it keeps the shader's indentation. */
    const val GLSL = "uniform vec2 u_bend; vec3 bendOffset(vec3 p) { float d = max(0.0, -p.z - 3.0); return vec3(u_bend * (d * d), 0.0); }"

    /** Load the current bend (or none, for the sky) into [shader]; it must be bound. */
    fun apply(shader: ShaderProgram, on: Boolean = true) {
        val bx = if (on) x else 0f; val by = if (on) y else 0f
        val handle = shader.handle
        val cached = uniforms[shader]
        if (cached != null && cached.x == bx && cached.y == by && cached.handle == handle) return
        shader.setUniformf("u_bend", bx, by)
        if (cached == null) uniforms[shader] = Uniform(bx, by, handle)
        else { cached.x = bx; cached.y = by; cached.handle = handle }
    }

    /** The bend at depth [z] (sideways), for geometry placed on the CPU. */
    fun dx(z: Float): Float { val d = -z - START; return if (d > 0f) x * d * d else 0f }
    fun dy(z: Float): Float { val d = -z - START; return if (d > 0f) y * d * d else 0f }
}
