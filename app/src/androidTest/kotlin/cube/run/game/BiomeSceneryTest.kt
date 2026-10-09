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
import cube.run.game.world.Scenery
import cube.run.game.world.biome.BiomeScene
import cube.run.ui.Hud
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
