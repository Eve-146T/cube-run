package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import java.util.IdentityHashMap

/** Static voxel surfaces, with only 160 bytes uploaded per object per frame. */
internal class InstancedFacets(private val kit: BoxMeshKit) : Disposable {
    private class Group(shape: FacetShape) {
        val mesh = Mesh(true, shape.faces * 3, 0,
            VertexAttribute(Usage.Position, 3, "a_position"),
            VertexAttribute(Usage.Normal, 3, "a_normal"),
            VertexAttribute(Usage.Generic, 3, "a_surface"))
        val data = FloatArray(CAPACITY * STRIDE)
        var used = 0
        init {
            val vertices = FloatArray(shape.faces * 27)
            var w = 0
            for (f in 0 until shape.faces) for (v in 0 until 3) {
                for (axis in 0..2) vertices[w++] = shape.pos[f * 9 + v * 3 + axis]
                for (axis in 0..2) vertices[w++] = shape.nrm[f * 3 + axis]
                vertices[w++] = shape.slot[f].toFloat()
                vertices[w++] = shape.tone[f]
                vertices[w++] = shape.lat[f]
            }
            mesh.setVertices(vertices)
            mesh.enableInstancedRendering(false, CAPACITY,
                *Array(10) { VertexAttribute(Usage.Generic, 4, "i_$it") })
        }
    }

    private val groups = IdentityHashMap<FacetShape, Group>()
    private val shader = ShaderProgram("""
        #version 300 es
        precision highp float;
        in vec3 a_position, a_normal, a_surface;
        in vec4 i_0, i_1, i_2, i_3, i_4, i_5, i_6, i_7, i_8, i_9;
        uniform mat4 u_projViewTrans;
        uniform vec3 u_toL1, u_toL2, u_ambient, u_light1, u_light2;
        out vec4 v_color;
        ${WorldBend.GLSL}
        void main() {
            vec3 local = a_position * i_3.xyz;
            vec3 world = vec3(dot(i_0.xyz,local)+i_0.w, dot(i_1.xyz,local)+i_1.w, dot(i_2.xyz,local)+i_2.w);
            vec3 raw = a_normal / i_3.xyz;
            vec3 n = normalize(vec3(dot(i_0.xyz,raw),dot(i_1.xyz,raw),dot(i_2.xyz,raw)));
            vec3 light = u_ambient + max(0.0,dot(n,u_toL1))*u_light1 + max(0.0,dot(n,u_toL2))*u_light2;
            light = mix(light,vec3(1.0),i_3.w);
            float slot = a_surface.x;
            if (i_9.y >= 0.0) {
                slot = 0.0;
                if (i_9.y > 0.0 && a_surface.z > i_8.x) slot += 1.0;
                if (i_9.y > 1.0 && a_surface.z > i_8.y) slot += 1.0;
                if (i_9.y > 2.0 && a_surface.z > i_8.z) slot += 1.0;
                if (i_9.y > 3.0 && a_surface.z > i_8.w) slot += 1.0;
                if (i_9.y > 4.0 && a_surface.z > i_9.x) slot += 1.0;
            }
            slot = mod(slot,i_9.z);
            vec3 tint = slot < 0.5 ? i_4.rgb : (slot < 1.5 ? i_5.rgb : i_6.rgb);
            vec3 rgb = min(vec3(1.0),tint*light*a_surface.y)*(1.0-i_7.w) + i_7.rgb*i_7.w;
            v_color = vec4(floor(rgb*255.0)/255.0,min(1.0,floor(floor(i_4.w*255.0)/2.0)*2.0/254.0));
            gl_Position = u_projViewTrans*vec4(world,1.0) + u_projViewTrans*vec4(bendOffset(world)*i_9.w,0.0);
        }
    """.trimIndent(), """
        #version 300 es
        precision highp float;
        in vec4 v_color;
        out vec4 fragColor;
        void main() {
            // Screen-space coverage fade: no opaque haze silhouette or translucent self-overlap.
            float threshold = fract(52.9829189 * fract(dot(floor(gl_FragCoord.xy),vec2(0.06711056,0.00583715))));
            if (v_color.a <= threshold) discard;
            fragColor = vec4(v_color.rgb,1.0);
        }
    """.trimIndent())

    init { require(shader.isCompiled) { "instanced facet shader: ${shader.log}" } }

    fun prepare(vararg shapes: FacetShape) { for (shape in shapes) if (!groups.containsKey(shape)) groups[shape] = Group(shape) }

    fun begin() { for (g in groups.values) g.used = 0 }

    fun add(shape: FacetShape, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float,
            rotation: FloatArray, palette: Array<Color>, fog: Float, fogColor: Color,
            glow: Float, bands: FloatArray?, bent: Boolean, opacity: Float): Boolean {
        require(palette.size in 1..3 && (bands == null || bands.size <= 5))
        val g = groups[shape] ?: Group(shape).also { groups[shape] = it }
        if (g.used == g.data.size) return false
        val d = g.data
        var w = g.used
        for (row in 0..2) {
            for (col in 0..2) d[w++] = rotation[row * 3 + col]
            d[w++] = when (row) { 0 -> x; 1 -> y; else -> z }
        }
        d[w++] = sx; d[w++] = sy; d[w++] = sz; d[w++] = glow
        for (i in 0..2) {
            val c = palette[i % palette.size]
            d[w++] = c.r; d[w++] = c.g; d[w++] = c.b; d[w++] = opacity
        }
        d[w++] = fogColor.r; d[w++] = fogColor.g; d[w++] = fogColor.b; d[w++] = fog
        for (i in 0..4) d[w++] = bands?.getOrNull(i) ?: 0f
        d[w++] = bands?.size?.toFloat() ?: -1f
        d[w++] = palette.size.toFloat(); d[w++] = if (bent) 1f else 0f
        g.used = w
        return true
    }

    fun render(cam: Camera) {
        if (groups.values.none { it.used > 0 }) return
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(true)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_BLEND)
        shader.bind(); shader.setUniformMatrix("u_projViewTrans", cam.combined)
        WorldBend.apply(shader); kit.setLightUniforms(shader)
        for (g in groups.values) if (g.used > 0) {
            g.mesh.setInstanceData(g.data, 0, g.used)
            g.mesh.render(shader, GL20.GL_TRIANGLES)
        }
        Gdx.gl.glDisable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
    }

    override fun dispose() { for (g in groups.values) g.mesh.dispose(); groups.clear(); shader.dispose() }

    private companion object { const val STRIDE = 40; const val CAPACITY = 128 }
}
