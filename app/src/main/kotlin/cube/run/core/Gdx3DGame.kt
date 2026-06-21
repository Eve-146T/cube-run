package cube.run.core

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.profiling.GLProfiler
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Base class for every 3D game (libGDX). Provides:
 *  - PerspectiveCamera + lit Environment + ModelBatch, gradient sky
 *  - tap / drag / swipe input callbacks (GL thread, same as [tick])
 *  - auto-disposed model factories: box / sphere / cylinder / cone
 *  - juice: camera [shake], [flash] overlay, [burst3d] cube-shard explosions
 *
 * After session.gameOver() the loop keeps running for death animation —
 * guard gameplay logic with session.isOver.
 */
abstract class Gdx3DGame(val session: GameSession) : ApplicationAdapter() {

    lateinit var cam: PerspectiveCamera
    lateinit var env: Environment
    private lateinit var batch: ModelBatch
    private lateinit var shapes: ShapeRenderer
    val mb = ModelBuilder()

    var bgTop: Color = Color.valueOf("3A2A7E")
    var bgBottom: Color = Color.valueOf("120E2C")

    /** Total elapsed seconds. */
    var time = 0f
        private set

    val sw: Int get() = Gdx.graphics.width
    val sh: Int get() = Gdx.graphics.height

    private val owned = ArrayList<Model>()
    private val rnd = Random(System.nanoTime())
    private var shakeMag = 0f
    private var flashColor = Color(1f, 1f, 1f, 0f)
    private val camSave = Vector3()

    // --------------------------------------------------------------- perf HUD
    /** Draw the on-screen FPS counter (top-left). Cheap; flip true to show it. */
    var showFps = false
    /** Emit detailed frame-time / draw-call stats to logcat once per second (tag PERF).
     *  Enables [GLProfiler] (adds per-GL-call overhead) — flip true to benchmark. */
    var perfLog = false

    private var glProfiler: GLProfiler? = null
    private val cpuMs = FloatArray(512)          // ring buffer of render() CPU-build times (ms, NOT vsync-capped)
    private var ringIdx = 0
    private var ringCount = 0
    private val sortBuf = FloatArray(512)        // reused for percentile sort (no per-log alloc)
    private var renderStartNs = 0L
    private var curSlot = 0
    private var logAccum = 0f
    private var logFrames = 0
    private var winMaxDraws = 0
    private var winMaxVerts = 0f
    private var simAccNs = 0L     // per-window sum: tick() + updateShards()
    private var drawAccNs = 0L    // per-window sum: ModelBatch begin..end (build+submit)
    // 7-segment masks for 0..9 (bit a=0x01 b=0x02 c=0x04 d=0x08 e=0x10 f=0x20 g=0x40)
    private val segMasks = intArrayOf(0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F)

    abstract fun init()
    abstract fun tick(dt: Float)
    abstract fun renderWorld(batch: ModelBatch, env: Environment)

    open fun onTap(x: Float, y: Float) {}
    open fun onDown(x: Float, y: Float) {}
    open fun onDrag(x: Float, y: Float, dx: Float, dy: Float) {}
    open fun onUp(x: Float, y: Float) {}
    open fun onSwipe(dir: Int) {}

    /**
     * When true, swipes fire continuously within a single touch: every time the
     * finger travels far enough from the last fired point, another [onSwipe] is
     * emitted (so you can steer left/right/left without lifting). When false, a
     * touch yields at most one swipe (the classic flick).
     */
    open fun smoothSwipeEnabled(): Boolean = false

    /**
     * Optional screen-space overlay drawn after the world (filled shapes).
     * Coordinates are pixels with origin bottom-left, y up. The [ShapeRenderer]
     * is already in [ShapeRenderer.ShapeType.Filled] begin/end with blending on.
     */
    open fun renderHud(shapes: ShapeRenderer, w: Float, h: Float) {}

    companion object {
        const val LEFT = 0
        const val RIGHT = 1
        const val UP = 2
        const val DOWN = 3
    }

    // ----------------------------------------------------------------- setup

    override fun create() {
        cam = PerspectiveCamera(60f, sw.toFloat(), sh.toFloat()).apply {
            position.set(7f, 7f, 7f)
            lookAt(0f, 0f, 0f)
            near = 0.1f
            far = 400f
            update()
        }
        env = Environment().apply {
            set(ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.55f, 0.6f, 1f))
            add(DirectionalLight().set(0.85f, 0.85f, 0.8f, -0.45f, -0.85f, -0.35f))
            add(DirectionalLight().set(0.25f, 0.22f, 0.3f, 0.6f, -0.2f, 0.5f))
        }
        batch = ModelBatch()
        shapes = ShapeRenderer()
        if (perfLog) glProfiler = GLProfiler(Gdx.graphics).also { it.enable() }
        prewarmShardPool()
        setupShardBatch()

        Gdx.input.inputProcessor = object : InputAdapter() {
            private var downX = 0f
            private var downY = 0f
            private var lastX = 0f
            private var lastY = 0f
            private var downAt = 0L
            private var swiped = false
            private val swipeDist = sw * 0.085f
            private val tapSlop = sw * 0.03f

            override fun touchDown(x: Int, y: Int, pointer: Int, button: Int): Boolean {
                if (pointer != 0 || session.isOver) return false
                downX = x.toFloat(); downY = y.toFloat()
                lastX = downX; lastY = downY
                downAt = System.currentTimeMillis()
                swiped = false
                onDown(downX, downY)
                return true
            }

            override fun touchDragged(x: Int, y: Int, pointer: Int): Boolean {
                if (pointer != 0 || session.isOver) return false
                val fx = x.toFloat(); val fy = y.toFloat()
                onDrag(fx, fy, fx - lastX, fy - lastY)
                lastX = fx; lastY = fy
                // smooth mode: the game interprets the drag positionally (in onDrag).
                // classic mode: a single flick per touch.
                if (!smoothSwipeEnabled() && !swiped) {
                    val dx = fx - downX
                    val dy = fy - downY
                    if (abs(dx) > swipeDist || abs(dy) > swipeDist) {
                        swiped = true
                        onSwipe(
                            if (abs(dx) > abs(dy)) { if (dx > 0) RIGHT else LEFT }
                            else { if (dy > 0) DOWN else UP }
                        )
                    }
                }
                return true
            }

            override fun touchUp(x: Int, y: Int, pointer: Int, button: Int): Boolean {
                if (pointer != 0 || session.isOver) return false
                val fx = x.toFloat(); val fy = y.toFloat()
                onUp(fx, fy)
                if (!swiped && abs(fx - downX) < tapSlop && abs(fy - downY) < tapSlop &&
                    System.currentTimeMillis() - downAt < 350
                ) {
                    onTap(fx, fy)
                }
                return true
            }
        }
        init()
    }

    // ----------------------------------------------------------------- frame

    override fun render() {
        renderStartNs = System.nanoTime()
        glProfiler?.reset()
        curSlot = ringIdx                               // cpuMs[curSlot] filled at end of render()
        ringIdx = (ringIdx + 1) % cpuMs.size
        if (ringCount < cpuMs.size) ringCount++

        val dt = min(Gdx.graphics.deltaTime, 0.035f)
        time += dt
        val sim0 = System.nanoTime()
        tick(dt)
        updateShards(dt)
        simAccNs += System.nanoTime() - sim0

        Gdx.gl.glViewport(0, 0, sw, sh)
        Gdx.gl.glClearColor(bgBottom.r, bgBottom.g, bgBottom.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        // Gradient sky.
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        shapes.projectionMatrix = uiMatrix.setToOrtho2D(0f, 0f, sw.toFloat(), sh.toFloat())
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.rect(0f, 0f, sw.toFloat(), sh.toFloat(), bgBottom, bgBottom, bgTop, bgTop)
        shapes.end()
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        val shaken = shakeMag > 0.005f
        if (shaken) {
            camSave.set(cam.position)
            cam.position.add(
                (rnd.nextFloat() * 2f - 1f) * shakeMag,
                (rnd.nextFloat() * 2f - 1f) * shakeMag,
                (rnd.nextFloat() * 2f - 1f) * shakeMag,
            )
            shakeMag *= (1f - 6.5f * dt).coerceAtLeast(0f)
        }
        cam.viewportWidth = sw.toFloat()
        cam.viewportHeight = sh.toFloat()
        cam.update()

        val draw0 = System.nanoTime()
        batch.begin(cam)
        renderWorld(batch, env)
        batch.end()
        renderShardsBatched()       // own pass: 1 draw call for all live shards
        drawAccNs += System.nanoTime() - draw0

        if (shaken) cam.position.set(camSave)

        if (flashColor.a > 0.004f) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
            shapes.projectionMatrix = uiMatrix.setToOrtho2D(0f, 0f, sw.toFloat(), sh.toFloat())
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            shapes.setColor(flashColor)
            shapes.rect(0f, 0f, sw.toFloat(), sh.toFloat())
            shapes.end()
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
            flashColor.a = (flashColor.a - 2.6f * dt).coerceAtLeast(0f)
        }

        // Screen-space HUD overlay (debug widgets, etc.).
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        shapes.projectionMatrix = uiMatrix.setToOrtho2D(0f, 0f, sw.toFloat(), sh.toFloat())
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        renderHud(shapes, sw.toFloat(), sh.toFloat())
        if (showFps) drawFps(shapes, sw.toFloat(), sh.toFloat())
        shapes.end()
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        perfTick()
    }

    // ------------------------------------------------------------ perf report

    /** Aggregate frame stats once per second to logcat (tag PERF). */
    private fun perfTick() {
        cpuMs[curSlot] = (System.nanoTime() - renderStartNs) / 1_000_000f
        glProfiler?.let { p ->
            if (p.drawCalls > winMaxDraws) winMaxDraws = p.drawCalls
            if (p.vertexCount.total > winMaxVerts) winMaxVerts = p.vertexCount.total
        }
        logAccum += Gdx.graphics.rawDeltaTime
        logFrames++
        if (logAccum < 1f) return
        if (perfLog) {
            val n = ringCount
            // CPU frame-build (NOT vsync-capped — the real headroom signal) percentiles.
            // Split into sim (tick+updateShards) vs draw (ModelBatch build+submit) below.
            System.arraycopy(cpuMs, 0, sortBuf, 0, n)
            java.util.Arrays.sort(sortBuf, 0, n)
            val cp50 = sortBuf[n / 2]; val cp95 = sortBuf[(n * 95 / 100).coerceIn(0, n - 1)]
            val cMax = sortBuf[n - 1]
            Gdx.app.log(
                "PERF",
                "fps=%.1f  cpu[p50/p95/max]=%.1f/%.1f/%.1f  sim=%.2f draw=%.2f  draws=%d shards=%d".format(
                    logFrames / logAccum, cp50, cp95, cMax,
                    simAccNs / logFrames / 1e6, drawAccNs / logFrames / 1e6,
                    winMaxDraws, shards.size,
                ),
            )
        }
        logAccum = 0f; logFrames = 0; winMaxDraws = 0; winMaxVerts = 0f
        simAccNs = 0L; drawAccNs = 0L
    }

    /** Minimalist 7-segment FPS readout, top-left. Green ≥55, amber ≥40, red below. */
    private fun drawFps(shapes: ShapeRenderer, w: Float, h: Float) {
        val fps = Gdx.graphics.framesPerSecond.coerceIn(0, 999)
        when {
            fps >= 55 -> shapes.setColor(0.30f, 1f, 0.45f, 0.9f)
            fps >= 40 -> shapes.setColor(1f, 0.80f, 0.20f, 0.9f)
            else -> shapes.setColor(1f, 0.30f, 0.25f, 0.95f)
        }
        val dh = h * 0.030f
        val dw = dh * 0.62f
        val t = dh * 0.16f
        val gap = dw * 0.40f
        val pad = w * 0.035f
        var x = pad
        val y = h - pad - dh
        val s = fps.toString()
        for (ch in s) { drawDigit(shapes, ch - '0', x, y, dw, dh, t); x += dw + gap }
    }

    private fun drawDigit(shapes: ShapeRenderer, d: Int, x: Float, y: Float, dw: Float, dh: Float, t: Float) {
        val seg = segMasks[d]
        val half = (dh - t) * 0.5f
        if (seg and 0x01 != 0) shapes.rect(x, y + dh - t, dw, t)              // a  top
        if (seg and 0x02 != 0) shapes.rect(x + dw - t, y + half, t, half + t) // b  top-right
        if (seg and 0x04 != 0) shapes.rect(x + dw - t, y, t, half + t)        // c  bottom-right
        if (seg and 0x08 != 0) shapes.rect(x, y, dw, t)                       // d  bottom
        if (seg and 0x10 != 0) shapes.rect(x, y, t, half + t)                 // e  bottom-left
        if (seg and 0x20 != 0) shapes.rect(x, y + half, t, half + t)          // f  top-left
        if (seg and 0x40 != 0) shapes.rect(x, y + half, dw, t)                // g  middle
    }

    private val uiMatrix = Matrix4()

    // ----------------------------------------------------------------- juice

    fun shake(mag: Float = 0.4f) {
        shakeMag = max(shakeMag, mag)
    }

    fun flash(color: Color = Color.WHITE, alpha: Float = 0.3f) {
        flashColor.set(color.r, color.g, color.b, max(flashColor.a, alpha))
    }

    /**
     * A pooled cube shard: just a transform + colour + alpha (no ModelInstance —
     * shards are drawn as one batched mesh, see [renderShardsBatched]). Allocated
     * once, then reused — [burst3d] only re-seeds the value fields (zero allocation).
     */
    private class Shard {
        val transform = Matrix4()     // translate * rotate * uniform-scale
        val color = Color(1f, 1f, 1f, 1f)
        val vel = Vector3()
        val rotAxis = Vector3()
        val pos = Vector3()
        var rotSpeed = 0f
        var life = 0f
        var maxLife = 0f
        var size = 0f
        var alpha = 1f
    }

    private val maxShards = 240
    private val shards = ArrayList<Shard>(maxShards)      // active (updated + rendered)
    private val shardPool = ArrayList<Shard>(maxShards)   // free list — reused across bursts

    /** Build the whole pool up front (load time) so no burst ever allocates mid-run. */
    private fun prewarmShardPool() {
        if (shardPool.isNotEmpty()) return
        repeat(maxShards) { shardPool.add(Shard()) }
    }

    /** Take a free shard: from the pool, or a fresh one until the cap, else recycle oldest. */
    private fun obtainShard(): Shard = when {
        shardPool.isNotEmpty() -> shardPool.removeAt(shardPool.size - 1)
        shards.size < maxShards -> Shard()
        else -> shards.removeAt(0) // at cap: retire the oldest, re-seed it below
    }

    /** Cube-shard explosion at a world position. Allocation-free in steady state (pooled). */
    fun burst3d(at: Vector3, color: Color, n: Int = 14, speed: Float = 6f, size: Float = 0.16f, life: Float = 0.8f) {
        repeat(n) {
            val s = obtainShard()
            s.color.set(color)
            s.alpha = 1f
            s.vel.set(
                rnd.nextFloat() * 2f - 1f,
                rnd.nextFloat() * 1.6f - 0.3f,
                rnd.nextFloat() * 2f - 1f,
            ).nor().scl(speed * (0.4f + rnd.nextFloat() * 0.9f))
            s.rotAxis.set(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()).nor()
            s.rotSpeed = (rnd.nextFloat() - 0.5f) * 720f
            val l = life * (0.5f + rnd.nextFloat() * 0.7f)
            s.life = l; s.maxLife = l
            s.size = size * (0.6f + rnd.nextFloat() * 0.9f)
            s.pos.set(at)
            shards.add(s)
        }
    }

    private fun updateShards(dt: Float) {
        var i = shards.size - 1
        while (i >= 0) {
            val s = shards[i]
            s.life -= dt
            if (s.life <= 0f) {
                val last = shards.size - 1
                shards[i] = shards[last]      // swap-remove: O(1), no array shift
                shards.removeAt(last)
                shardPool.add(s)              // return to the pool for reuse
            } else {
                s.vel.y -= 14f * dt
                s.pos.mulAdd(s.vel, dt)
                val k = (s.life / s.maxLife).coerceIn(0f, 1f)
                s.alpha = k
                val sc = s.size * (0.4f + 0.6f * k)
                s.transform.idt()
                    .translate(s.pos)
                    .rotate(s.rotAxis, s.rotSpeed * (s.maxLife - s.life))
                    .scale(sc, sc, sc)
            }
            i--
        }
    }

    // ----- batched shard renderer: all live shards in ONE dynamic mesh / draw call.
    // GPU has huge headroom; the cost was CPU-side ModelBatch submission of ~240
    // renderables/frame. We bake the exact Environment lighting + per-shard alpha
    // into vertex colours so the look is identical to the old per-instance render.
    private var shardMesh: Mesh? = null
    private var shardShader: ShaderProgram? = null
    private lateinit var shardVerts: FloatArray   // (pos3 + packedColor1) * 24 verts * maxShards
    private lateinit var tplPos: FloatArray        // unit-cube template: 24 vertex positions (xyz)
    private var tplVerts = 0                        // template vertex count (24)
    private var tplIdxCount = 0                     // template index count (36)
    // per-cube structure precomputed once: each of the 24 verts maps to one of 8
    // shared corners and one of 6 faces, so a frame transforms 8 corners + lights
    // 6 faces (not 24 of each).
    private val cornerLocal = floatArrayOf(        // 8 corners of a unit cube (±0.5)
        -.5f, -.5f, -.5f, .5f, -.5f, -.5f, -.5f, .5f, -.5f, .5f, .5f, -.5f,
        -.5f, -.5f, .5f, .5f, -.5f, .5f, -.5f, .5f, .5f, .5f, .5f, .5f,
    )
    private val faceNrm = floatArrayOf(            // +X -X +Y -Y +Z -Z
        1f, 0f, 0f, -1f, 0f, 0f, 0f, 1f, 0f, 0f, -1f, 0f, 0f, 0f, 1f, 0f, 0f, -1f,
    )
    private lateinit var cornerOf: IntArray        // vert -> corner index (0..7)
    private lateinit var faceOf: IntArray          // vert -> face index (0..5)
    private val wc = FloatArray(24)                 // scratch: 8 transformed corners (xyz)
    private val packed = FloatArray(6)              // scratch: per-face packed colour
    private val sN = Vector3()                      // scratch: world normal
    private val sV = Vector3()                      // scratch: world position
    // light rig — MUST mirror the Environment built in create()
    private val ambR = 0.55f; private val ambG = 0.55f; private val ambB = 0.6f
    private val toL1 = Vector3(-0.45f, -0.85f, -0.35f).scl(-1f).nor()
    private val l1R = 0.85f; private val l1G = 0.85f; private val l1B = 0.8f
    private val toL2 = Vector3(0.6f, -0.2f, 0.5f).scl(-1f).nor()
    private val l2R = 0.25f; private val l2G = 0.22f; private val l2B = 0.3f

    private fun setupShardBatch() {
        // Extract a unit-cube template (positions + normals + winding) from libGDX's
        // own box builder so culling/normals match the old ModelBatch shards exactly.
        val tpl = box(1f, 1f, 1f, Color.WHITE)
        val m0 = tpl.meshes.first()
        val vCount = m0.numVertices                 // 24
        val fpv = m0.vertexSize / 4                 // floats per vertex
        val raw = FloatArray(vCount * fpv)
        m0.getVertices(raw)
        val pOff = m0.getVertexAttribute(Usage.Position).offset / 4
        val nOff = m0.getVertexAttribute(Usage.Normal).offset / 4
        tplPos = FloatArray(vCount * 3)
        cornerOf = IntArray(vCount)
        faceOf = IntArray(vCount)
        for (v in 0 until vCount) {
            val px = raw[v * fpv + pOff]; val py = raw[v * fpv + pOff + 1]; val pz = raw[v * fpv + pOff + 2]
            tplPos[v * 3] = px; tplPos[v * 3 + 1] = py; tplPos[v * 3 + 2] = pz
            cornerOf[v] = (if (px > 0) 1 else 0) or (if (py > 0) 2 else 0) or (if (pz > 0) 4 else 0)
            val nx = raw[v * fpv + nOff]; val ny = raw[v * fpv + nOff + 1]; val nz = raw[v * fpv + nOff + 2]
            faceOf[v] = when {
                abs(nx) > 0.5f -> if (nx > 0) 0 else 1
                abs(ny) > 0.5f -> if (ny > 0) 2 else 3
                else -> if (nz > 0) 4 else 5
            }
        }
        val tplIdx = ShortArray(m0.numIndices)      // 36
        m0.getIndices(tplIdx)
        tplVerts = vCount
        tplIdxCount = tplIdx.size

        shardVerts = FloatArray(maxShards * vCount * 4)
        val mesh = Mesh(
            false, maxShards * vCount, maxShards * tplIdx.size,
            VertexAttribute(Usage.Position, 3, "a_position"),
            VertexAttribute(Usage.ColorPacked, 4, "a_color"),
        )
        val idx = ShortArray(maxShards * tplIdx.size)
        for (c in 0 until maxShards) {
            val ib = c * tplIdx.size; val vb = c * vCount
            for (k in tplIdx.indices) idx[ib + k] = (tplIdx[k] + vb).toShort()
        }
        mesh.setIndices(idx)
        shardMesh = mesh

        ShaderProgram.pedantic = false
        shardShader = ShaderProgram(
            """
            attribute vec3 a_position;
            attribute vec4 a_color;
            uniform mat4 u_projViewTrans;
            varying vec4 v_color;
            void main() { v_color = a_color; gl_Position = u_projViewTrans * vec4(a_position, 1.0); }
            """.trimIndent(),
            """
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec4 v_color;
            void main() { gl_FragColor = v_color; }
            """.trimIndent(),
        ).also { require(it.isCompiled) { "shard shader: ${it.log}" } }
    }

    /** One draw call for every live shard. Run after the world (depth already written). */
    private fun renderShardsBatched() {
        val n = shards.size
        if (n == 0) return
        val mesh = shardMesh ?: return
        val shader = shardShader ?: return
        var w = 0
        for (i in 0 until n) {
            val s = shards[i]; val m = s.transform
            val cr = s.color.r; val cg = s.color.g; val cb = s.color.b; val a = s.alpha
            // 8 shared cube corners -> world space (not 24 verts)
            for (c in 0 until 8) {
                val ci = c * 3
                sV.set(cornerLocal[ci], cornerLocal[ci + 1], cornerLocal[ci + 2]).mul(m)
                wc[ci] = sV.x; wc[ci + 1] = sV.y; wc[ci + 2] = sV.z
            }
            // 6 faces -> baked lit colour (mirrors the Environment's Lambert lighting)
            for (f in 0 until 6) {
                val fi = f * 3
                sN.set(faceNrm[fi], faceNrm[fi + 1], faceNrm[fi + 2]).rot(m).nor()
                val d1 = max(0f, sN.dot(toL1)); val d2 = max(0f, sN.dot(toL2))
                val r = min(1f, cr * (ambR + d1 * l1R + d2 * l2R))
                val g = min(1f, cg * (ambG + d1 * l1G + d2 * l2G))
                val b = min(1f, cb * (ambB + d1 * l1B + d2 * l2B))
                packed[f] = Color.toFloatBits(r, g, b, a)
            }
            // assemble the 24 cube verts from precomputed corner+face lookups (cheap copies)
            for (v in 0 until tplVerts) {
                val ci = cornerOf[v] * 3
                shardVerts[w++] = wc[ci]; shardVerts[w++] = wc[ci + 1]; shardVerts[w++] = wc[ci + 2]
                shardVerts[w++] = packed[faceOf[v]]
            }
        }
        mesh.setVertices(shardVerts, 0, w)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)                 // blended: test against scene, don't occlude each other
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shader.bind()
        shader.setUniformMatrix("u_projViewTrans", cam.combined)
        mesh.render(shader, GL20.GL_TRIANGLES, 0, n * tplIdxCount)
        // Restore the state ModelBatch's RenderContext.end() used to leave behind,
        // so the following ShapeRenderer passes (flash, HUD fire-boost chevrons, FPS
        // counter) aren't affected by our cull-face / depth / blend settings.
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    // ----------------------------------------------------------- model utils

    private val attrs = (Usage.Position or Usage.Normal).toLong()

    fun mat(color: Color): Material = Material(ColorAttribute.createDiffuse(color))

    fun box(w: Float, h: Float, d: Float, color: Color): Model =
        mb.createBox(w, h, d, mat(color), attrs).also { owned.add(it) }

    fun sphere(diameter: Float, color: Color, div: Int = 20): Model =
        mb.createSphere(diameter, diameter, diameter, div, div, mat(color), attrs).also { owned.add(it) }

    fun cylinder(diameter: Float, height: Float, color: Color, div: Int = 24): Model =
        mb.createCylinder(diameter, height, diameter, div, mat(color), attrs).also { owned.add(it) }

    fun cone(diameter: Float, height: Float, color: Color, div: Int = 16): Model =
        mb.createCone(diameter, height, diameter, div, mat(color), attrs).also { owned.add(it) }

    /** Bright HSV color helper; hue in degrees. */
    fun gdxHsv(h: Float, s: Float = 0.75f, v: Float = 1f): Color {
        val hh = (((h % 360f) + 360f) % 360f) / 60f
        val i = hh.toInt() % 6
        val f = hh - hh.toInt()
        val p = v * (1f - s)
        val q = v * (1f - f * s)
        val t = v * (1f - (1f - f) * s)
        return when (i) {
            0 -> Color(v, t, p, 1f)
            1 -> Color(q, v, p, 1f)
            2 -> Color(p, v, t, 1f)
            3 -> Color(p, q, v, 1f)
            4 -> Color(t, p, v, 1f)
            else -> Color(v, p, q, 1f)
        }
    }

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
        shardMesh?.dispose()
        shardShader?.dispose()
        owned.forEach { it.dispose() }
        owned.clear()
    }
}
