package cube.run.core

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import cube.run.core.gfx.BoxMeshKit
import cube.run.core.gfx.PerfMonitor
import cube.run.core.gfx.ShardSystem
import cube.run.core.gfx.TouchInput
import cube.run.core.gfx.TouchListener
import cube.run.core.gfx.WorldBoxBatch
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Base class for every 3D game (libGDX). Owns the frame: camera, lit
 * environment, gradient sky, the render order, and the "juice" ([shake],
 * [flash], [burst3d]). The heavy lifting lives in `core.gfx`:
 *  - [WorldBoxBatch] — every static box in ONE draw call, with baked lighting
 *    and distance fog ([renderWorldBatched] / [worldBox] / [worldBoxSpin])
 *  - [ShardSystem] — pooled cube-shard particles, one draw call
 *  - [TouchInput] — tap / drag / swipe → the open callbacks below (GL thread)
 *  - [PerfMonitor] — optional FPS readout + frame-time log
 *
 * Subclasses implement [init] / [tick] / [renderWorld] (ModelBatch, for the
 * rotating or blended pieces only). After session.gameOver() the loop keeps
 * running for death animation — guard gameplay logic with session.isOver.
 */
abstract class Gdx3DGame(val session: GameSession) : ApplicationAdapter(), TouchListener {

    lateinit var cam: PerspectiveCamera
    lateinit var env: Environment
    private lateinit var batch: ModelBatch
    private lateinit var shapes: ShapeRenderer
    private lateinit var kit: BoxMeshKit
    private lateinit var world: WorldBoxBatch
    private lateinit var shards: ShardSystem
    private lateinit var perf: PerfMonitor
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
    private val uiMatrix = Matrix4()

    /** Draw the on-screen FPS counter (top-left). Cheap; flip true to show it. */
    var showFps = false
    /** Emit frame-time / draw-call stats to logcat once per second (tag PERF). Flip true to benchmark. */
    var perfLog = false

    abstract fun init()
    abstract fun tick(dt: Float)

    /** While true the frame is drawn but nothing advances: [tick] gets dt = 0 and [time] holds. */
    open fun paused(): Boolean = false
    abstract fun renderWorld(batch: ModelBatch, env: Environment)

    /**
     * Fill the world-box batch: called once per frame, before the ModelBatch pass.
     * Queue boxes with [worldBox] / [worldBoxSpin]; anything needing rotation,
     * blending or custom materials stays in [renderWorld].
     */
    open fun renderWorldBatched() {}

    /**
     * Optional screen-space overlay drawn after the world (filled shapes).
     * Coordinates are pixels with origin bottom-left, y up. The [ShapeRenderer]
     * is already in [ShapeRenderer.ShapeType.Filled] begin/end with blending on.
     */
    open fun renderHud(shapes: ShapeRenderer, w: Float, h: Float) {}

    // touch callbacks (see TouchListener); override what you need
    override fun onDown(x: Float, y: Float) {}
    override fun onDrag(x: Float, y: Float, dx: Float, dy: Float) {}
    override fun onUp(x: Float, y: Float) {}
    override fun onTap(x: Float, y: Float) {}
    override fun onSwipe(dir: Int) {}

    companion object {
        const val LEFT = TouchInput.LEFT
        const val RIGHT = TouchInput.RIGHT
        const val UP = TouchInput.UP
        const val DOWN = TouchInput.DOWN
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
        kit = BoxMeshKit(mb)
        env = kit.environment()
        batch = ModelBatch()
        shapes = ShapeRenderer()
        world = WorldBoxBatch(kit)
        shards = ShardSystem(kit)
        perf = PerfMonitor(showFps, perfLog)
        Gdx.input.inputProcessor = TouchInput(this, { sw }, { session.isOver })
        init()
    }

    // ----------------------------------------------------------------- frame

    override fun render() {
        perf.beginFrame()
        val dt = if (paused()) 0f else min(Gdx.graphics.deltaTime, 0.035f)
        time += dt
        val sim0 = System.nanoTime()
        tick(dt)
        shards.update(dt)
        perf.addSim(System.nanoTime() - sim0)

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
        world.begin()
        renderWorldBatched()
        world.render(cam)           // opaque pass: 1 draw call for every world box
        batch.begin(cam)
        renderWorld(batch, env)
        batch.end()
        shards.render(cam)          // own pass: 1 draw call for all live shards
        perf.addDraw(System.nanoTime() - draw0)

        if (shaken) cam.position.set(camSave)

        // Flash overlay + screen-space HUD.
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        shapes.projectionMatrix = uiMatrix.setToOrtho2D(0f, 0f, sw.toFloat(), sh.toFloat())
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        if (flashColor.a > 0.004f) {
            shapes.setColor(flashColor)
            shapes.rect(0f, 0f, sw.toFloat(), sh.toFloat())
            flashColor.a = (flashColor.a - 2.6f * dt).coerceAtLeast(0f)
        }
        renderHud(shapes, sw.toFloat(), sh.toFloat())
        perf.drawFps(shapes, sw.toFloat(), sh.toFloat())
        shapes.end()
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        perf.endFrame(shards.count)
    }

    // ----------------------------------------------------------------- juice

    fun shake(mag: Float = 0.4f) {
        shakeMag = max(shakeMag, mag)
    }

    fun flash(color: Color = Color.WHITE, alpha: Float = 0.3f) {
        flashColor.set(color.r, color.g, color.b, max(flashColor.a, alpha))
    }

    /** Cube-shard explosion at a world position. Allocation-free in steady state (pooled). */
    fun burst3d(at: Vector3, color: Color, n: Int = 14, speed: Float = 6f, size: Float = 0.16f, life: Float = 0.8f) =
        shards.burst(at, color, n, speed, size, life)

    // ----------------------------------------------------------- world boxes

    /** Distance-haze target colour for [worldBox] fog (set per frame to match the sky). */
    val fogColor: Color get() = world.fogColor

    /** Queue one axis-aligned box (centre position, full sizes) for the batched world pass. */
    fun worldBox(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, col: Color, fog: Float = 0f) =
        world.box(x, y, z, sx, sy, sz, col, fog)

    /** Like [worldBox] but spun [yawDeg] about its vertical axis (coins, pickups). */
    fun worldBoxSpin(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, yawDeg: Float, col: Color, fog: Float = 0f) =
        world.boxSpin(x, y, z, sx, sy, sz, yawDeg, col, fog)

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

    /** Bright HSV color helper; hue in degrees. Allocates — use [hsvInto] on hot paths. */
    fun gdxHsv(h: Float, s: Float = 0.75f, v: Float = 1f): Color = hsvInto(Color(), h, s, v)

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
        shards.dispose()
        world.dispose()
        kit.dispose()
        owned.forEach { it.dispose() }
        owned.clear()
    }
}

/** Allocation-free HSV write into an existing [Color]; returns it for chaining. Hue in degrees. */
fun hsvInto(c: Color, h: Float, s: Float, v: Float): Color {
    val hh = (((h % 360f) + 360f) % 360f) / 60f
    val i = hh.toInt()
    val f = hh - i
    val p = v * (1f - s); val q = v * (1f - f * s); val t = v * (1f - (1f - f) * s)
    when (i % 6) {
        0 -> c.set(v, t, p, 1f); 1 -> c.set(q, v, p, 1f); 2 -> c.set(p, v, t, 1f)
        3 -> c.set(p, q, v, 1f); 4 -> c.set(t, p, v, 1f); else -> c.set(v, p, q, 1f)
    }
    return c
}
