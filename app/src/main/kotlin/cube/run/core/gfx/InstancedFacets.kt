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
        // A voxel face consists of two triangles with four identical surface vertices.
        // Index those corners so the vertex shader runs four times instead of six.
        val mesh = Mesh(true, shape.faces * 2, shape.faces * 3,
            VertexAttribute(Usage.Position, 3, "a_position"),
            VertexAttribute(Usage.Generic, 4, "a_surface"))
        val solid = FloatArray(CAPACITY * STRIDE)
        val fading = FloatArray(CAPACITY * STRIDE)
        var solidUsed = 0
        var fadingUsed = 0
        init {
            require(shape.faces % 2 == 0 && shape.faces * 2 <= 65536)
            val vertices = FloatArray(shape.faces * 14)
            val indices = ShortArray(shape.faces * 3)
            var w = 0
            var index = 0
            for (f in 0 until shape.faces step 2) {
                // c00,c10,c11 from the first triangle, c01 from the second.
                val normal = f * 3
                val direction = when {
                    shape.nrm[normal] != 0f -> if (shape.nrm[normal] > 0f) 0f else 1f
                    shape.nrm[normal + 1] != 0f -> if (shape.nrm[normal + 1] > 0f) 2f else 3f
                    else -> if (shape.nrm[normal + 2] > 0f) 4f else 5f
                }
                for (corner in intArrayOf(0, 1, 2, 5)) {
                    for (axis in 0..2) vertices[w++] = shape.pos[f * 9 + corner * 3 + axis]
                    vertices[w++] = shape.slot[f].toFloat()
                    vertices[w++] = shape.tone[f]
                    vertices[w++] = shape.lat[f]
                    vertices[w++] = direction
                }
                val base = f * 2
                for (offset in intArrayOf(0, 1, 2, 0, 2, 3)) indices[index++] = (base + offset).toShort()
            }
            mesh.setVertices(vertices); mesh.setIndices(indices)
            mesh.enableInstancedRendering(false, CAPACITY,
                *Array(10) { VertexAttribute(Usage.Generic, 4, "i_$it") })
        }
    }

    private val groups = IdentityHashMap<FacetShape, Group>()
    private val vertexShader = """
        #version 300 es
        precision highp float;
        in vec3 a_position;
        in vec4 a_surface;
        in vec4 i_0, i_1, i_2, i_3, i_4, i_5, i_6, i_7, i_8, i_9;
        uniform mat4 u_projViewTrans;
        uniform vec3 u_toL1, u_toL2, u_ambient, u_light1, u_light2;
        out vec4 v_color;
        ${WorldBend.GLSL}
        void main() {
            vec3 local = a_position * i_3.xyz;
            vec3 world = vec3(dot(i_0.xyz,local)+i_0.w, dot(i_1.xyz,local)+i_1.w, dot(i_2.xyz,local)+i_2.w);
            vec3 light = vec3(1.0);
            if (i_3.w < 1.0) {
                // Voxel normals are axis-aligned. Nonuniform scale changes only their sign,
                // so select the rotation column instead of dividing and rotating a vector.
                int axis = int(a_surface.w) / 2;
                float direction = mod(a_surface.w,2.0) < 0.5 ? 1.0 : -1.0;
                vec3 n = normalize(vec3(i_0[axis],i_1[axis],i_2[axis])) * direction * sign(i_3[axis]);
                light = u_ambient + max(0.0,dot(n,u_toL1))*u_light1 + max(0.0,dot(n,u_toL2))*u_light2;
                light = mix(light,vec3(1.0),i_3.w);
            }
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
            gl_Position = u_projViewTrans*vec4(world,1.0);
            if (i_9.w > 0.0) gl_Position += u_projViewTrans*vec4(bendOffset(world),0.0);
        }
    """.trimIndent()
    private val fadeShader = ShaderProgram(vertexShader, """
        #version 300 es
        precision highp float;
        in vec4 v_color;
        out vec4 fragColor;
        void main() { fragColor = v_color; }
    """.trimIndent())

    private val solidShader = ShaderProgram(vertexShader, """
        #version 300 es
        precision mediump float;
        in vec4 v_color;
        out vec4 fragColor;
        void main() { fragColor = vec4(v_color.rgb,1.0); }
    """.trimIndent())

    init {
        require(fadeShader.isCompiled) { "fading facet shader: ${fadeShader.log}" }
        require(solidShader.isCompiled) { "solid facet shader: ${solidShader.log}" }
    }

    fun prepare(vararg shapes: FacetShape) {
        for (shape in shapes) {
            val surface = shape.gpuSurface ?: shape
            if (!groups.containsKey(surface)) groups[surface] = Group(surface)
        }
    }

    fun begin() { for (g in groups.values) { g.solidUsed = 0; g.fadingUsed = 0 } }

    fun add(shape: FacetShape, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float,
            rotation: FloatArray, palette: Array<Color>, fog: Float, fogColor: Color,
            glow: Float, bands: FloatArray?, bent: Boolean, opacity: Float, compact: Boolean): Boolean {
        require(palette.size in 1..3 && (bands == null || bands.size <= 5))
        // Bending is quadratic: retain the original interior vertices on bent surfaces.
        val surface = if (bent || !compact) shape else shape.gpuSurface ?: shape
        val g = groups[surface] ?: Group(surface).also { groups[surface] = it }
        val solid = opacity >= 1f
        val d = if (solid) g.solid else g.fading
        var w = if (solid) g.solidUsed else g.fadingUsed
        if (w == d.size) return false
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
        if (solid) g.solidUsed = w else g.fadingUsed = w
        return true
    }

    fun render(cam: Camera) {
        if (groups.values.none { it.solidUsed > 0 || it.fadingUsed > 0 }) return
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(true)
        Gdx.gl.glDepthFunc(GL20.GL_LESS)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_BLEND)
        solidShader.bind(); solidShader.setUniformMatrix("u_projViewTrans", cam.combined)
        WorldBend.apply(solidShader); kit.setLightUniforms(solidShader)
        for (g in groups.values) if (g.solidUsed > 0) {
            g.mesh.setInstanceData(g.solid, 0, g.solidUsed)
            g.mesh.render(solidShader, GL20.GL_TRIANGLES)
        }
        if (groups.values.any { it.fadingUsed > 0 }) {
            fadeShader.bind(); fadeShader.setUniformMatrix("u_projViewTrans", cam.combined)
            WorldBend.apply(fadeShader); kit.setLightUniforms(fadeShader)
            // Keep the nearest surface of a fading assembly, then blend its color once.
            // This gives solid voxel bodies a smooth fade without exposing internal faces.
            Gdx.gl.glColorMask(false, false, false, false)
            for (g in groups.values) if (g.fadingUsed > 0) {
                g.mesh.setInstanceData(g.fading, 0, g.fadingUsed)
                g.mesh.render(fadeShader, GL20.GL_TRIANGLES)
            }
            Gdx.gl.glColorMask(true, true, true, true)
            Gdx.gl.glDepthMask(false); Gdx.gl.glDepthFunc(GL20.GL_EQUAL)
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            for (g in groups.values) if (g.fadingUsed > 0) g.mesh.render(fadeShader, GL20.GL_TRIANGLES)
        }
        Gdx.gl.glDepthMask(true); Gdx.gl.glDepthFunc(GL20.GL_LESS)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
    }

    override fun dispose() {
        for (g in groups.values) g.mesh.dispose()
        groups.clear(); fadeShader.dispose(); solidShader.dispose()
    }

    private companion object { const val STRIDE = 40; const val CAPACITY = 128 }
}
