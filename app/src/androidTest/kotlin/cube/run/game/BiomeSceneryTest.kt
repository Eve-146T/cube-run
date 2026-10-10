package cube.run.game

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.core.gfx.WorldBoxBatch
import cube.run.data.Settings
import cube.run.data.Worlds
import cube.run.game.world.Fog
import cube.run.game.world.Scenery
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.ScreenUtils
import cube.run.game.track.Track
import cube.run.game.world.biome.BiomeScene
import cube.run.ui.Hud
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import android.graphics.Bitmap
import android.view.View
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Every biome's scenery fits the box batch with room left for the course, and its layers stay bounded. */
class BiomeSceneryTest {
    @Test fun atlasPreservesChunkOrderAfterCapacityFallbackAndReload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            ActivityScenario.launch<GameActivity>(Intent(context, GameActivity::class.java)
                .putExtra(Hud.EXTRA_AUTOSTART, true)).use {
                gl {
                    Stage.paused = true
                    val kit = cube.run.core.gfx.BoxMeshKit(com.badlogic.gdx.graphics.g3d.utils.ModelBuilder())
                    val facets = cube.run.core.gfx.FacetBatch(kit)
                    val shapes = Array(18) {
                        cube.run.core.gfx.FacetShapes.model(listOf(
                            cube.run.core.gfx.FacetShapes.Box(-.3f, 0f, 0f, .25f, .4f, .3f),
                            cube.run.core.gfx.FacetShapes.Box(.3f, 0f, 0f, .25f, .4f, .3f)))
                    }
                    val colors = Array(18) { arrayOf(com.badlogic.gdx.graphics.Color((it + 1) / 19f, .6f, 1f - it / 19f, 1f)) }
                    facets.prepare(*shapes)
                    val fog = com.badlogic.gdx.graphics.Color(.1f, .2f, .3f, 1f)
                    val target = FrameBuffer(Pixmap.Format.RGBA8888, 160, 320, true)
                    val camera = PerspectiveCamera(60f, 160f, 320f).apply {
                        near = .1f; far = 100f; position.set(0f, 2f, 4f); lookAt(0f, 0f, -12f); update()
                    }
                    try {
                        fun pixels(atlas: Boolean, count: Int): ByteArray {
                            facets.atlasEnabled = atlas
                            target.begin()
                            try {
                                Gdx.gl.glDepthMask(true); Gdx.gl.glClearColor(.1f, .2f, .3f, 1f)
                                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                                facets.begin(camera)
                                for (shape in shapes.indices) for (i in 0 until count) {
                                    facets.add(shapes[shape], (i % 5 - 2) * .4f, (i % 3) * .3f, -4f - i * .15f,
                                        .6f, .6f, .6f, 0f, 0f, 0f, colors[shape], .1f, fog,
                                        opacity = if (i % 2 == 0) 1f else .5f, preculled = true)
                                }
                                facets.render(camera)
                                return ScreenUtils.getFrameBufferPixels(0, 0, 160, 320, false)
                            } finally { target.end() }
                        }
                        for (count in listOf(110, 120, 110, 105, 110)) {
                            val reference = pixels(false, count)
                            org.junit.Assert.assertArrayEquals("Atlas count=$count", reference, pixels(true, count))
                            if (count == 120) assertTrue("Fixture must exceed capacity: ${facets.statistics()}",
                                !facets.statistics().contains("fallbackFrames=0"))
                        }
                        assertTrue("Fixture must cross chunks: ${facets.statistics()}", !facets.statistics().contains("chunks=1 "))
                        val before = pixels(true, 110)
                        com.badlogic.gdx.graphics.Mesh.invalidateAllMeshes(Gdx.app)
                        com.badlogic.gdx.graphics.glutils.ShaderProgram.invalidateAllShaderPrograms(Gdx.app)
                        kit.resetLightUniforms()
                        org.junit.Assert.assertArrayEquals("Atlas reload after fallback", before, pixels(true, 110))
                        org.junit.Assert.assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
                    } finally { target.dispose(); facets.dispose(); kit.dispose() }
                }
            }
        } finally { Stage.paused = false }
    }

    @Test fun staticStarsPreserveBothBiomeSkies() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            ActivityScenario.launch<GameActivity>(Intent(context, GameActivity::class.java)
                .putExtra(Hud.EXTRA_AUTOSTART, true)).use { scenario ->
                scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
                SystemClock.sleep(1800)
                gl { game ->
                    Stage.paused = true
                    val scenery = field(game, CubeRun::class.java, "scenery") as Scenery
                    var shapes = com.badlogic.gdx.graphics.glutils.ShapeRenderer(5000)
                    val target = FrameBuffer(Pixmap.Format.RGBA8888, 240, 480, true)
                    val cam = PerspectiveCamera(60f, 240f, 480f).apply {
                        near = .1f; far = 400f; position.set(1f, 4f, 6f); lookAt(0f, 1f, -8f); update()
                    }
                    try {
                        for (id in listOf(1, 5)) {
                            scenery.init(Worlds.get(id), seed = 146)
                            for (time in listOf(0f, .8f, 3.2f, 10000f)) for (alpha in listOf(0f, .001f, .25f, .75f, 1f)) {
                                fun pixels(gpu: Boolean): ByteArray {
                                    scenery.biome.sky.fastStarsEnabled = gpu
                                    target.begin()
                                    try {
                                        Gdx.gl.glDepthMask(true); Gdx.gl.glClearColor(.12f, .17f, .25f, 1f)
                                        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                                        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(false)
                                        Gdx.gl.glEnable(GL20.GL_BLEND)
                                        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
                                        shapes.projectionMatrix = cam.combined
                                        shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled)
                                        scenery.renderBackdrop(shapes, cam, time, alpha)
                                        shapes.end()
                                        return ScreenUtils.getFrameBufferPixels(0, 0, 240, 480, false)
                                    } finally { target.end() }
                                }
                                val reference = pixels(false); val optimized = pixels(true)
                                var max = 0
                                for (i in reference.indices) max = maxOf(max,
                                    kotlin.math.abs((reference[i].toInt() and 255) - (optimized[i].toInt() and 255)))
                                assertTrue("Stars world=$id time=$time alpha=$alpha maximum channel difference=$max", max <= 2)
                                if (time == 10000f && alpha == 1f) {
                                    com.badlogic.gdx.graphics.Mesh.invalidateAllMeshes(Gdx.app)
                                    com.badlogic.gdx.graphics.Texture.invalidateAllTextures(Gdx.app)
                                    com.badlogic.gdx.graphics.glutils.ShaderProgram.invalidateAllShaderPrograms(Gdx.app)
                                    shapes.dispose(); shapes = com.badlogic.gdx.graphics.glutils.ShapeRenderer(5000)
                                    game.resume()
                                    org.junit.Assert.assertArrayEquals("Managed stars world=$id", optimized, pixels(true))
                                }
                            }
                        }
                        assertTrue(Gdx.gl.glGetError() == GL20.GL_NO_ERROR)
                    } finally {
                        scenery.biome.sky.fastStarsEnabled = true
                        Gdx.gl.glDepthMask(true); Gdx.gl.glEnable(GL20.GL_DEPTH_TEST); Gdx.gl.glDisable(GL20.GL_BLEND)
                        shapes.dispose(); target.dispose()
                    }
                }
            }
        } finally { Stage.paused = false; Lanes.reset(); Settings.testWorld = -1 }
    }

    /** Compare the full lighting reference; culling, depth selection and reload must remain exact. */
    @Test fun optimizedSceneryPreservesEveryBiome() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, GameActivity::class.java).putExtra(Hud.EXTRA_AUTOSTART, true)
        try {
            ActivityScenario.launch<GameActivity>(intent).use { scenario ->
                scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
                SystemClock.sleep(1800)
                gl { game ->
                    Stage.paused = true
                    val scenery = field(game, CubeRun::class.java, "scenery") as Scenery
                    var shapes = com.badlogic.gdx.graphics.glutils.ShapeRenderer(5000)
                    val target = FrameBuffer(Pixmap.Format.RGBA8888, 240, 480, true)
                    val cam = PerspectiveCamera(60f, 240f, 480f).apply {
                        near = 0.1f; far = 400f
                    }
                    try {
                        for (world in Worlds.all) {
                            scenery.init(world, seed = 146)
                            for (step in 0 until 12) {
                                scenery.scroll(25f); scenery.tick(0.8f, 25f)
                                cam.position.set(if (step % 2 == 0) -2f else 2f, 3.8f + step % 3, 6.4f)
                                cam.lookAt(0f, 1f, -8f); cam.update()
                                fun pixels(fastDepth: Boolean, culling: Boolean, atlas: Boolean = false): ByteArray {
                                    game.facets.atlasEnabled = atlas
                                    scenery.biome.sky.fastRaysEnabled = atlas
                                    game.facets.fastDepthEnabled = fastDepth
                                    game.facets.cullingEnabled = culling
                                    target.begin()
                                    try {
                                        Gdx.gl.glDepthMask(true); Gdx.gl.glClearColor(.12f, .17f, .25f, 1f)
                                        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                                        game.facets.begin(cam)
                                        scenery.biome.render(step * 0.8f, if (step % 3 == 0) 15f else 0f)
                                        game.facets.render(cam)
                                        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(false)
                                        Gdx.gl.glEnable(GL20.GL_BLEND)
                                        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
                                        shapes.projectionMatrix = cam.combined
                                        shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled)
                                        scenery.renderShapes(shapes, step * .8f, 1f)
                                        shapes.end()
                                        Gdx.gl.glDepthMask(true); Gdx.gl.glDisable(GL20.GL_BLEND)
                                        return ScreenUtils.getFrameBufferPixels(0, 0, 240, 480, false)
                                    } finally { target.end() }
                                }
                                val reference = pixels(false, false)
                                val culled = pixels(false, true)
                                org.junit.Assert.assertArrayEquals("Culling ${world.name} step $step", reference, culled)
                                val optimized = pixels(false, true, true)
                                assertAtlasColors("Atlas and rays ${world.name} step $step", reference, optimized)
                                org.junit.Assert.assertArrayEquals("Atlas depth ${world.name} step $step", optimized, pixels(true, true, true))
                                if (world.id == 2 && step == 6) {
                                    com.badlogic.gdx.graphics.Mesh.invalidateAllMeshes(Gdx.app)
                                    com.badlogic.gdx.graphics.Texture.invalidateAllTextures(Gdx.app)
                                    com.badlogic.gdx.graphics.glutils.ShaderProgram.invalidateAllShaderPrograms(Gdx.app)
                                    // Stock libGDX ShapeRenderer retains obsolete VAO attribute bindings on
                                    // invalidation. Recreate the reference fixture; our custom meshes must restore.
                                    shapes.dispose(); shapes = com.badlogic.gdx.graphics.glutils.ShapeRenderer(5000)
                                    game.resume()
                                    org.junit.Assert.assertArrayEquals("Managed atlas and rays restoration", optimized, pixels(true, true, true))
                                }
                            }
                        }
                        org.junit.Assert.assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
                        assertTrue("Every biome must fit the atlas: ${game.facets.statistics()}",
                            game.facets.statistics().startsWith("fallbackFrames=0 "))
                    } finally {
                        game.facets.atlasEnabled = true; game.facets.fastDepthEnabled = true; game.facets.cullingEnabled = true
                        scenery.biome.sky.fastRaysEnabled = true
                        shapes.dispose(); target.dispose()
                    }
                }
            }
        } finally { Stage.paused = false; Lanes.reset(); Settings.testWorld = -1 }
    }

    /**
     * The batch holds 900. The road goes first, then the course, then the roadside, so too much
     * scenery only loses scenery. The biome's own layers are facets; only its kerb is boxes. The
     * busiest roadside (Lava Caves' crystal clusters on the five-lane road) peaks at 622 in view.
     */
    private val LIMIT = 640

    private fun field(owner: Any, cls: Class<*>, name: String) = cls.getDeclaredField(name).apply { isAccessible = true }.get(owner)

    private fun assertAtlasColors(label: String, reference: ByteArray, optimized: ByteArray) {
        org.junit.Assert.assertEquals("$label size", reference.size, optimized.size)
        var maximum = 0
        for (i in reference.indices) {
            val difference = kotlin.math.abs((reference[i].toInt() and 255) - (optimized[i].toInt() and 255))
            // Mali can round a color channel one byte differently between the
            // attribute and texture-fetch vertex programs. Alpha stays exact.
            if (i % 4 == 3 && difference != 0)
                org.junit.Assert.assertEquals("$label alpha at $i", 0, difference)
            maximum = maxOf(maximum, difference)
        }
        assertTrue("$label maximum color difference=$maximum", maximum <= 1)
    }

    private fun gl(action: (CubeRun) -> Unit) {
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        Gdx.app.postRunnable {
            try { action(Gdx.app.applicationListener as CubeRun) }
            catch (t: Throwable) { failure = t }
            finally { done.countDown() }
        }
        assertTrue("GL work timed out", done.await(30, TimeUnit.SECONDS))
        failure?.let { throw it }
    }

    @Test fun everyBiomeLeavesRoomForTheCourse() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, GameActivity::class.java).putExtra(Hud.EXTRA_AUTOSTART, false)
        try {
            ActivityScenario.launch<GameActivity>(intent).use {
                SystemClock.sleep(1800)
                gl { game ->
                    Stage.paused = true
                    val scenery = field(game, CubeRun::class.java, "scenery") as Scenery
                    val batch = field(game, game.javaClass.superclass, "world") as WorldBoxBatch
                    val report = StringBuilder()
                    for (world in Worlds.all) for (wide in listOf(false, true)) {
                        Lanes.unfold = if (wide) 1f else 0f
                        scenery.init(world, seed = 146)
                        var worstBoxes = 0; var worstSeen = 0; var worstFacets = 0
                        repeat(40) { step -> // a long stretch: landmarks come and go, weather settles in
                            scenery.scroll(7f); scenery.tick(1f / 2f, 7f)
                            for (seen in listOf(false, true)) {
                                batch.begin(if (seen) game.cam else null) // what the camera sees, and every box: the worst case
                                game.facets.begin(game.cam)
                                scenery.renderRoad()
                                scenery.render(1f, step * 0.5f)
                                val n = field(batch, WorldBoxBatch::class.java, "count") as Int
                                if (seen) worstSeen = maxOf(worstSeen, n) else worstBoxes = maxOf(worstBoxes, n)
                                worstFacets = maxOf(worstFacets, game.facets.queued)
                            }
                        }
                        val counts = scenery.biome.counts
                        report.append("${world.name}${if (wide) " wide" else ""}: boxes $worstBoxes (seen $worstSeen), facet verts $worstFacets, layers ${counts.joinToString()}\n")
                        android.util.Log.i("BIOME_BUDGET", report.lines().last { it.isNotEmpty() })
                        assertTrue("${world.name} leaves too few boxes for the course: $worstSeen", worstSeen <= LIMIT)
                        assertTrue("${world.name} landmarks pile up", counts[2] <= 1)
                    }
                }
            }
        } finally {
            Stage.paused = false
            Lanes.reset()
            Settings.testWorld = -1
        }
    }

    /**
     * What the biome layers cost a frame, GPU-synchronised, rendered off screen at the phone's size
     * from the run camera: the road and roadside (boxes) against the biome's facets. Logged (tag
     * BIOME_COST); a sanity bound only, since emulators render in software.
     */
    @Test fun biomeLayersCostLittleOfAFrame() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, GameActivity::class.java).putExtra(Hud.EXTRA_AUTOSTART, false)
        try {
            ActivityScenario.launch<GameActivity>(intent).use {
                SystemClock.sleep(1800)
                gl { game ->
                    Stage.paused = true
                    val scenery = field(game, CubeRun::class.java, "scenery") as Scenery
                    val batch = field(game, game.javaClass.superclass, "world") as WorldBoxBatch
                    val target = FrameBuffer(Pixmap.Format.RGBA8888, 720, 1520, true)
                    val cam = PerspectiveCamera(60f, 720f, 1520f).apply {
                        near = 0.1f; far = 400f; position.set(0f, 3.8f, 6.4f); lookAt(0f, 1f, -8f); update()
                    }
                    val report = StringBuilder()
                    try {
                        target.begin()
                        for (world in Worlds.all) {
                            scenery.init(world, seed = 146)
                            repeat(30) { scenery.scroll(7f); scenery.tick(0.5f, 7f) }
                            fun cost(layers: Int): DoubleArray {
                                scenery.biome.layers = layers
                                val boxes = ArrayList<Double>(); val facets = ArrayList<Double>()
                                for (round in 0 until 40) {
                                    Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
                                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                                    Gdx.gl.glFinish()
                                    val t0 = System.nanoTime()
                                    batch.begin(cam); game.facets.begin(cam)
                                    scenery.renderRoad()
                                    scenery.render(1f, round * 0.016f) // queues the roadside boxes and the biome's facets
                                    batch.render(cam); Gdx.gl.glFinish()
                                    val t1 = System.nanoTime()
                                    game.facets.render(cam); Gdx.gl.glFinish()
                                    val t2 = System.nanoTime()
                                    if (round >= 8) { boxes.add((t1 - t0) / 1e6); facets.add((t2 - t1) / 1e6) }
                                }
                                boxes.sort(); facets.sort()
                                return doubleArrayOf(boxes[16], facets[16], facets[28])
                            }
                            val all = cost(BiomeScene.ALL)
                            val parts = listOf("sky" to BiomeScene.SKY, "props" to BiomeScene.PROPS, "streams" to BiomeScene.STREAMS,
                                "far" to BiomeScene.FAR, "landmarks" to BiomeScene.LANDMARKS, "motes" to BiomeScene.MOTES)
                                .joinToString { (name, bit) -> "$name %.2f".format(cost(bit)[1]) }
                            scenery.biome.layers = BiomeScene.ALL
                            val line = "${world.name}: road+roadside %.2f ms, biome facets p50 %.2f p90 %.2f ms (%s)".format(all[0], all[1], all[2], parts)
                            android.util.Log.i("BIOME_COST", line)
                            report.append(line).append('\n')
                            assertTrue(line, all[1] < 12.0)
                        }
                        assertTrue(Gdx.gl.glGetError() == GL20.GL_NO_ERROR)
                    } finally { target.end(); target.dispose() }
                }
            }
        } finally {
            Stage.paused = false
            Lanes.reset()
        }
    }

    /**
     * Opt-in review captures (-e captureBiomes true): every biome's landmarks in turn, brought up
     * the road to where they show best, from the run camera with the HUD hidden. Saved to the
     * app's files/biome-review on the device.
     */
    @Test fun captureLandmarks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureBiomes") == "true")
        val context = instrumentation.targetContext
        val directory = File(context.getExternalFilesDir(null), "biome-review").apply { mkdirs() }
        val intent = Intent(context, GameActivity::class.java).putExtra(Hud.EXTRA_AUTOSTART, true)
            .putExtra(Hud.EXTRA_IDLE_BOT, true).putExtra("dev", true).putExtra("section", 56) // a started run, no obstacles
        try {
            ActivityScenario.launch<GameActivity>(intent).use { scenario ->
                SystemClock.sleep(2500)
                scenario.onActivity { (field(it, GameActivity::class.java, "hud") as View).visibility = View.INVISIBLE }
                for (world in Worlds.all) {
                    var kinds = 0
                    gl { game ->
                        val scenery = field(game, CubeRun::class.java, "scenery") as Scenery
                        kinds = scenery.biome.lookOf(world).landmarkKinds
                    }
                    for (kind in 0 until kinds) for (shot in 0..1) {
                        gl { game ->
                            Stage.paused = true
                            val scenery = field(game, CubeRun::class.java, "scenery") as Scenery
                            val biome = scenery.biome
                            scenery.init(world, seed = 146)
                            val worlds = field(game, CubeRun::class.java, "worlds") as cube.run.game.world.WorldRunner
                            worlds.reset(world.id) // its sky, too
                            BiomeScene::class.java.getDeclaredField("nextLandmark").apply { isAccessible = true }.setFloat(biome, 0f)
                            BiomeScene::class.java.getDeclaredField("landmarkTurn").apply { isAccessible = true }.setInt(biome, kind)
                            repeat(if (shot == 0) 28 else 50) { scenery.scroll(5f); scenery.tick(0.25f, 5f) }
                            game.cam.position.set(0f, 3.8f, 6.4f); game.cam.lookAt(0f, 1f, -8f); game.cam.update()
                        }
                        SystemClock.sleep(500)
                        val bitmap = instrumentation.uiAutomation.takeScreenshot()
                        File(directory, "landmark-${world.id}-$kind-$shot.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                    }
                }
            }
        } finally {
            Stage.paused = false
        }
    }

    /**
     * Nothing pops into view: the game frozen on the run camera, the scenery scrolled one frame's
     * worth at a time, each drawn frame read back. Where things are born (the far end, up to the
     * sky above it) a newcomer drawn whole shows as a solid blob of changed pixels; things that
     * fade in, or merely move, change by a little or along thin edges. Measured with the fade-in
     * off and on (logged, tag BIOME_POP). What still jumps with it on is motion near the camera
     * (towers, sparks, a car) or a lamp blinking, and varies from run to run: a measurement, not a gate.
     */
    @Test fun nothingPopsIntoView() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, GameActivity::class.java).putExtra(Hud.EXTRA_AUTOSTART, true)
            .putExtra(Hud.EXTRA_IDLE_BOT, true).putExtra("dev", true).putExtra("section", 56)
        try {
            ActivityScenario.launch<GameActivity>(intent).use {
                SystemClock.sleep(3000)
                for (pass in 0..Worlds.all.size) {
                    val space = pass == Worlds.all.size // last: Outer Space, its glass road coming out of the dark
                    val world = Worlds.get(if (space) 0 else pass)
                    val pops = IntArray(2)
                    val jumps = IntArray(2) // frames with a solid jump of 60+ pixels: a newcomer drawn whole
                    for (fade in listOf(false, true)) {
                        var window = IntArray(4)
                        var previous: ByteArray? = null
                        gl { game ->
                            Stage.paused = true
                            Fog.fadeIn = fade
                            val scenery = field(game, CubeRun::class.java, "scenery") as Scenery
                            (field(game, CubeRun::class.java, "track") as Track).rows.clear()
                            (field(game, CubeRun::class.java, "worlds") as cube.run.game.world.WorldRunner).reset(world.id)
                            scenery.init(world, seed = 146)
                            val outer = field(game, CubeRun::class.java, "space") as cube.run.game.space.SpaceWorld
                            outer.reset()
                            if (space) { outer.enter(146, instant = true); outer.tick(0.016f, 0f, game.time, true) }
                            repeat(20) { scenery.scroll(5f); scenery.tick(0.25f, 5f) }
                            val cam = game.cam
                            val far = cam.project(Vector3(0f, 0f, -Fog.appearFar))
                            val sky = cam.project(Vector3(0f, 22f, -Fog.appearFar))
                            window = intArrayOf((game.sw * 0.04f).toInt(), far.y.toInt() - 6, (game.sw * 0.96f).toInt(), sky.y.toInt())
                        }
                        repeat(150) { step ->
                            var frame: ByteArray? = null
                            gl { game ->
                                val scenery = field(game, CubeRun::class.java, "scenery") as Scenery
                                scenery.scroll(0.35f); scenery.tick(1f / 60f, 0.35f) // one frame at 21 units a second
                                game.render() // draw it here and read it back before the swap (between frames the buffer is undefined)
                                val w = window
                                frame = ScreenUtils.getFrameBufferPixels(w[0], w[1], w[2] - w[0], w[3] - w[1], false)
                            }
                            val now = frame!!
                            previous?.let {
                                val n = solidChange(it, now, window[2] - window[0], window[3] - window[1])
                                val k = if (fade) 1 else 0
                                if (step > 2 && n >= 60) jumps[k]++
                                if (step == 50 || (space && step in 60..67)) {
                                    val dir = File(context.getExternalFilesDir(null), "biome-pop").apply { mkdirs() }
                                    for ((tag, px) in listOf("a" to it, "b" to now)) {
                                        val bmp = Bitmap.createBitmap(window[2] - window[0], window[3] - window[1], Bitmap.Config.ARGB_8888)
                                        bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(px))
                                        File(dir, "step$step-${if (space) "space" else world.id}-$k-$tag.png").outputStream().use { o -> bmp.compress(Bitmap.CompressFormat.PNG, 100, o) }
                                    }
                                }
                                if (step > 2 && n > pops[k]) {
                                    pops[k] = n
                                    val dir = File(context.getExternalFilesDir(null), "biome-pop").apply { mkdirs() }
                                    for ((tag, px) in listOf("a" to it, "b" to now)) {
                                        val bmp = Bitmap.createBitmap(window[2] - window[0], window[3] - window[1], Bitmap.Config.ARGB_8888)
                                        bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(px))
                                        File(dir, "pop-${if (space) "space" else world.id}-$k-$tag.png").outputStream().use { o -> bmp.compress(Bitmap.CompressFormat.PNG, 100, o) }
                                    }
                                }
                            }
                            previous = now
                        }
                    }
                    if (space) gl { game -> (field(game, CubeRun::class.java, "space") as cube.run.game.space.SpaceWorld).reset() }
                    android.util.Log.i("BIOME_POP", "${if (space) "Outer Space" else world.name}: frames with a solid jump, without the fade-in ${jumps[0]}, with it ${jumps[1]} " +
                        "(largest ${pops[0]} / ${pops[1]} px)")
                }
            }
        } finally {
            Fog.fadeIn = true
            Stage.paused = false
        }
    }

    /**
     * Pixels in the middle of a solid 7×7 patch that changed a lot from [a] to [b]: an object
     * appearing whole. Things moving a few pixels a frame only change along their edges.
     */
    private fun solidChange(a: ByteArray, b: ByteArray, w: Int, h: Int): Int {
        val sum = IntArray((w + 1) * (h + 1)) // summed-area table of strongly changed pixels
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            var d = 0
            for (c in 0..2) d = maxOf(d, kotlin.math.abs((a[i * 4 + c].toInt() and 255) - (b[i * 4 + c].toInt() and 255)))
            sum[(y + 1) * (w + 1) + x + 1] = (if (d > 40) 1 else 0) + sum[y * (w + 1) + x + 1] + sum[(y + 1) * (w + 1) + x] - sum[y * (w + 1) + x]
        }
        val r = 3
        var n = 0
        for (y in r until h - r) for (x in r until w - r) {
            val x0 = x - r; val y0 = y - r; val x1 = x + r + 1; val y1 = y + r + 1
            if (sum[y1 * (w + 1) + x1] - sum[y0 * (w + 1) + x1] - sum[y1 * (w + 1) + x0] + sum[y0 * (w + 1) + x0] == 49) n++
        }
        return n
    }
}
