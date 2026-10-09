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
    /**
     * The batch holds 900. The road goes first, then the course, then the roadside, so too much
     * scenery only loses scenery. The biome's own layers are facets; only its kerb is boxes. The
     * busiest roadside (Lava Caves' crystal clusters on the five-lane road) peaks at 622 in view.
     */
    private val LIMIT = 640

    private fun field(owner: Any, cls: Class<*>, name: String) = cls.getDeclaredField(name).apply { isAccessible = true }.get(owner)

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
