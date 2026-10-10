package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import java.util.IdentityHashMap

/** Static voxel surfaces, with only 160 bytes uploaded per object per frame. */
internal class InstancedFacets(private val kit: BoxMeshKit) : Disposable {
    internal var fastDepthEnabled = true
    internal var atlasEnabled = true
    private class Group(@JvmField val shape: FacetShape) {
        // A voxel face consists of two triangles with four identical surface vertices.
        // Index those corners so the vertex shader runs four times instead of six.
        // Solid and fading instances each get a mesh (and instance buffer) of their own: one
        // buffer rewritten between the solid and the fading draws of a frame made objects flicker.
        private val meshDelegate = lazy { newMesh(shape).apply { setVertices(geometry.first); setIndices(geometry.second) } }
        val mesh by meshDelegate
        private val fadeMeshDelegate = lazy { newMesh(shape).apply { setVertices(geometry.first); setIndices(geometry.second) } }
        val fadeMesh by fadeMeshDelegate
        @JvmField val solid = FloatArray(CAPACITY * STRIDE)
        @JvmField val fading = FloatArray(CAPACITY * STRIDE)
        @JvmField var solidUsed = 0
        @JvmField var fadingUsed = 0
        private val geometry by lazy {
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
            Pair(vertices, indices)
        }

        fun dispose() {
            if (meshDelegate.isInitialized()) mesh.dispose()
            if (fadeMeshDelegate.isInitialized()) fadeMesh.dispose()
        }

        private companion object {
            fun newMesh(shape: FacetShape) = InstanceMesh(shape.faces * 2, shape.faces * 3, CAPACITY)
        }
    }

    private val groups = IdentityHashMap<FacetShape, Group>()
    private var groupList = emptyArray<Group>()
    private var lastShape: FacetShape? = null
    private var lastGroup: Group? = null
    private var hasSolid = false
    private var hasFading = false
    private var fallbackFrames = 0
    private var simpleFrame = true

    private fun group(shape: FacetShape): Group {
        if (lastShape === shape) return lastGroup!!
        val group = groups[shape] ?: Group(shape).also {
            groups[shape] = it
            // Snapshot only when the shape library grows, never allocate map iterators per frame.
            groupList = groups.values.toTypedArray()
        }
        lastShape = shape; lastGroup = group
        return group
    }
    private val vertexShader = """
        #version 300 es
        precision highp float;
        layout(location=0) in vec3 a_position;
        layout(location=1) in vec4 a_surface;
        ${ (0 until 10).joinToString("\n") { "layout(location=${it + 2}) in vec4 i_$it;" } }
        uniform mat4 u_projViewTrans;
        uniform vec3 u_toL1, u_toL2, u_ambient, u_light1, u_light2;
        out vec4 v_color;
        invariant gl_Position;
        ${WorldBend.GLSL}
        void main() {
            vec3 local = a_position * i_3.xyz;
            vec3 row1 = vec3(i_0.w, i_1.xy);
            vec3 row2 = vec3(i_1.zw, i_2.x);
            vec3 world = vec3(dot(i_0.xyz,local)+i_2.y, dot(row1,local)+i_2.z, dot(row2,local)+i_2.w);
            #ifndef DEPTH_ONLY
            vec3 light = vec3(1.0);
            if (i_3.w < 1.0) {
                // Voxel normals are axis-aligned. Nonuniform scale changes only their sign,
                // so select the rotation column instead of dividing and rotating a vector.
                int axis = int(a_surface.w) / 2;
                float direction = mod(a_surface.w,2.0) < 0.5 ? 1.0 : -1.0;
                vec3 n = normalize(vec3(i_0[axis],row1[axis],row2[axis])) * direction * sign(i_3[axis]);
                light = u_ambient + max(0.0,dot(n,u_toL1))*u_light1 + max(0.0,dot(n,u_toL2))*u_light2;
                light = mix(light,vec3(1.0),i_3.w);
            }
            float slot = a_surface.x;
            #ifndef SIMPLE_FACETS
            if (i_9.y >= 0.0) {
                slot = 0.0;
                if (i_9.y > 0.0 && a_surface.z > i_8.x) slot += 1.0;
                if (i_9.y > 1.0 && a_surface.z > i_8.y) slot += 1.0;
                if (i_9.y > 2.0 && a_surface.z > i_8.z) slot += 1.0;
                if (i_9.y > 3.0 && a_surface.z > i_8.w) slot += 1.0;
                if (i_9.y > 4.0 && a_surface.z > i_9.x) slot += 1.0;
            }
            slot = mod(slot,i_9.z);
            #endif
            vec3 tint = slot < 0.5 ? i_4.rgb : (slot < 1.5 ? i_5.rgb : i_6.rgb);
            vec3 rgb = min(vec3(1.0),tint*light*a_surface.y)*(1.0-i_7.w) + i_7.rgb*i_7.w;
            v_color = vec4(floor(rgb*255.0)/255.0,min(1.0,floor(floor(i_4.w*255.0)/2.0)*2.0/254.0));
            #endif
            gl_Position = u_projViewTrans*vec4(world,1.0);
            #ifndef SIMPLE_FACETS
            if (i_9.w > 0.0) gl_Position += u_projViewTrans*vec4(bendOffset(world),0.0);
            #endif
        }
    """.trimIndent()
    private val fadeShader = ShaderProgram(vertexShader, """
        #version 300 es
        precision highp float;
        in vec4 v_color;
        out vec4 fragColor;
        void main() { fragColor = v_color; }
    """.trimIndent())

    // The first fade pass only chooses depth. No palette, lighting, fog or color
    // quantization is needed. Invariant positions preserve the following EQUAL test.
    private val depthShader = ShaderProgram(vertexShader.replace("#version 300 es", "#version 300 es\n#define DEPTH_ONLY"), """
        #version 300 es
        precision mediump float;
        out vec4 fragColor;
        void main() { fragColor = vec4(0.0); }
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
        require(depthShader.isCompiled) { "depth facet shader: ${depthShader.log}" }
    }

    private val atlasDelegate = lazy { FacetAtlas() }
    private val atlas by atlasDelegate
    fun statistics() = "fallbackFrames=$fallbackFrames simple=$simpleFrame " + if (atlasDelegate.isInitialized()) atlas.statistics() else "atlas not initialized"
    private fun sharedShader(simple: Boolean): ShaderProgram {
        val vertex = FacetAtlas.vertexShader(vertexShader)
            .replace("uniform mat4 u_projViewTrans;", "uniform mediump float u_pass; uniform mat4 u_projViewTrans;")
            .replace("#ifndef DEPTH_ONLY", "v_color = vec4(0.0); if (u_pass > 0.5) {")
            .replace(Regex("#endif\\s+gl_Position"), "}\n            gl_Position")
            .let { source ->
                // Depth selection needs transforms and bend state, not palette or fog texels.
                // Keep those fetches inside the uniform color-pass branch.
                val colorRecords = (4..8).joinToString("\n") {
                    "vec4 i_$it = texelFetch(u_instances, ivec2($it, int(a_instance)), 0);"
                }
                var trimmed = source
                for (record in colorRecords.lines()) trimmed = trimmed.replace(record, "")
                trimmed.replace("if (u_pass > 0.5) {", "if (u_pass > 0.5) {\n$colorRecords")
            }
            .let { if (simple) it.replace("#version 300 es", "#version 300 es\n#define SIMPLE_FACETS") else it }
        return ShaderProgram(vertex, """
            #version 300 es
            precision highp float;
            uniform mediump float u_pass;
            in vec4 v_color;
            out vec4 fragColor;
            void main() { fragColor = vec4(v_color.rgb, u_pass < 1.5 ? 1.0 : v_color.a); }
        """.trimIndent()).also { require(it.isCompiled) { it.log } }
    }
    private val sharedGenericDelegate = lazy { sharedShader(false) }
    private val sharedGeneric by sharedGenericDelegate
    private val sharedSimpleDelegate = lazy { sharedShader(true) }
    private val sharedSimple by sharedSimpleDelegate

    fun prepare(vararg shapes: FacetShape) {
        if (atlasEnabled) {
            // A gate can change between simple and banded scenery. Compile both
            // programs before gameplay rather than on the first such frame.
            sharedGeneric.bind()
            sharedSimple.bind()
            atlas.prepareStorage(sharedSimple)
        }
        for (shape in shapes) {
            val surface = shape.gpuSurface ?: shape
            group(surface)
            if (atlasEnabled) atlas.prepare(surface)
        }
    }

    fun begin() {
        hasSolid = false; hasFading = false
        simpleFrame = true
        for (g in groupList) { g.solidUsed = 0; g.fadingUsed = 0 }
    }

    fun add(shape: FacetShape, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float,
            rotation: FloatArray, palette: Array<Color>, fog: Float, fogColor: Color,
            glow: Float, bands: FloatArray?, bent: Boolean, opacity: Float, compact: Boolean): Boolean {
        require(palette.size in 1..3 && (bands == null || bands.size <= 5))
        // Bending is quadratic: retain the original interior vertices on bent surfaces.
        val surface = if (bent || !compact) shape else shape.gpuSurface ?: shape
        val g = group(surface)
        val solid = opacity >= 1f
        val d = if (solid) g.solid else g.fading
        var w = if (solid) g.solidUsed else g.fadingUsed
        if (w == d.size) return false
        // The packed record repeats one/two-colour palettes across its three entries,
        // so direct slots 0..2 also match modulo selection for those palettes.
        if (bands != null || bent || !surface.threeColorSlots) simpleFrame = false
        // Fixed-layout writes avoid nested loops and palette modulo operations
        // in the interpreted path on low-clock devices.
        System.arraycopy(rotation, 0, d, w, 9); w += 9
        d[w++] = x; d[w++] = y; d[w++] = z
        d[w++] = sx; d[w++] = sy; d[w++] = sz; d[w++] = glow
        val c0 = palette[0]
        val c1 = palette[if (palette.size > 1) 1 else 0]
        val c2 = palette[if (palette.size > 2) 2 else 0]
        d[w++] = c0.r; d[w++] = c0.g; d[w++] = c0.b; d[w++] = opacity
        d[w++] = c1.r; d[w++] = c1.g; d[w++] = c1.b; w++ // Only i_4.w carries opacity.
        d[w++] = c2.r; d[w++] = c2.g; d[w++] = c2.b; w++
        d[w++] = fogColor.r; d[w++] = fogColor.g; d[w++] = fogColor.b; d[w++] = fog
        // The shader reads only the thresholds selected by the current band count.
        if (bands != null) System.arraycopy(bands, 0, d, w, bands.size)
        w += 5
        d[w++] = bands?.size?.toFloat() ?: -1f
        d[w++] = palette.size.toFloat(); d[w++] = if (bent) 1f else 0f
        if (solid) { g.solidUsed = w; hasSolid = true } else { g.fadingUsed = w; hasFading = true }
        return true
    }

    fun render(cam: Camera) {
        if (!hasSolid && !hasFading) return
        if (atlasEnabled) {
            if (renderAtlas(cam)) return
            fallbackFrames++
        }
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(true)
        Gdx.gl.glDepthFunc(GL20.GL_LESS)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_BLEND)
        solidShader.bind(); solidShader.setUniformMatrix("u_projViewTrans", cam.combined)
        WorldBend.apply(solidShader); kit.setLightUniforms(solidShader)
        for (g in groupList) if (g.solidUsed > 0) {
            g.mesh.setInstanceData(g.solid, 0, g.solidUsed)
            g.mesh.render(solidShader, GL20.GL_TRIANGLES)
        }
        if (hasFading) {
            val depth = if (fastDepthEnabled) depthShader else fadeShader
            depth.bind(); depth.setUniformMatrix("u_projViewTrans", cam.combined)
            WorldBend.apply(depth)
            if (!fastDepthEnabled) kit.setLightUniforms(depth)
            // Keep the nearest surface of a fading assembly, then blend its color once.
            // This gives solid voxel bodies a smooth fade without exposing internal faces.
            Gdx.gl.glColorMask(false, false, false, false)
            for (g in groupList) if (g.fadingUsed > 0) {
                g.fadeMesh.setInstanceData(g.fading, 0, g.fadingUsed)
                g.fadeMesh.render(depth, GL20.GL_TRIANGLES)
            }
            Gdx.gl.glColorMask(true, true, true, true)
            Gdx.gl.glDepthMask(false); Gdx.gl.glDepthFunc(GL20.GL_EQUAL)
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            fadeShader.bind(); fadeShader.setUniformMatrix("u_projViewTrans", cam.combined)
            WorldBend.apply(fadeShader); kit.setLightUniforms(fadeShader)
            for (g in groupList) if (g.fadingUsed > 0) g.fadeMesh.render(fadeShader, GL20.GL_TRIANGLES)
        }
        Gdx.gl.glDepthMask(true); Gdx.gl.glDepthFunc(GL20.GL_LESS)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
    }

    private fun renderAtlas(cam: Camera): Boolean {
        val atlas = this.atlas
        atlas.begin()
        for (g in groupList) {
            // Retain the original renderer for an unusually large shape library.
            if (g.solidUsed > 0 && !atlas.add(g.shape, g.solid, g.solidUsed, true)) return false
            if (g.fadingUsed > 0 && !atlas.add(g.shape, g.fading, g.fadingUsed, false)) return false
        }
        atlas.upload()
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(true)
        Gdx.gl.glDepthFunc(GL20.GL_LESS)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_BLEND)
        val shader = if (simpleFrame) sharedSimple else sharedGeneric
        shader.bind(); shader.setUniformMatrix("u_projViewTrans", cam.combined)
        if (!simpleFrame) WorldBend.apply(shader)
        kit.setLightUniforms(shader)
        val pass = shader.getUniformLocation("u_pass")
        Gdx.gl.glUniform1f(pass, 1f)
        atlas.render(shader, true)
        if (hasFading) {
            Gdx.gl.glUniform1f(pass, if (fastDepthEnabled) 0f else 2f)
            Gdx.gl.glColorMask(false, false, false, false)
            atlas.render(shader, false)
            Gdx.gl.glColorMask(true, true, true, true)
            Gdx.gl.glDepthMask(false); Gdx.gl.glDepthFunc(GL20.GL_EQUAL)
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            Gdx.gl.glUniform1f(pass, 2f)
            atlas.render(shader, false)
        }
        atlas.finishDraw()
        Gdx.gl.glDepthMask(true); Gdx.gl.glDepthFunc(GL20.GL_LESS)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE); Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        return true
    }

    override fun dispose() {
        if (atlasDelegate.isInitialized()) atlas.dispose()
        if (sharedGenericDelegate.isInitialized()) sharedGeneric.dispose()
        if (sharedSimpleDelegate.isInitialized()) sharedSimple.dispose()
        for (g in groupList) g.dispose()
        groups.clear(); groupList = emptyArray(); lastShape = null; lastGroup = null
        fadeShader.dispose(); solidShader.dispose(); depthShader.dispose()
    }

    private companion object { const val STRIDE = 40; const val CAPACITY = 128 }
}
