package cube.run.game

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.ScreenUtils
import cube.run.GameActivity
import cube.run.core.gfx.BoxMeshKit
import cube.run.core.gfx.PrismBatch
import cube.run.core.gfx.TerrainHeight
import cube.run.core.gfx.WorldBoxBatch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatchVisibilityTest {
    @Test fun cullingPreservesPixelsAcrossCameraEdgesRotationsAndHills() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val done = CountDownLatch(1)
            var failure: Throwable? = null
            Gdx.app.postRunnable {
                try {
                    val kit = BoxMeshKit(ModelBuilder())
                    val boxes = WorldBoxBatch(kit, 500)
                    val coins = PrismBatch(kit, max = 400)
                    val target = FrameBuffer(Pixmap.Format.RGBA8888, 160, 320, true)
                    val camera = PerspectiveCamera(67f, 160f, 320f).apply {
                        near = 0.5f; far = 65f
                    }
                    val boxCount = WorldBoxBatch::class.java.getDeclaredField("count").apply { isAccessible = true }
                    val coinCount = PrismBatch::class.java.getDeclaredField("count").apply { isAccessible = true }
                    val boxYaws = WorldBoxBatch::class.java.getDeclaredField("spinYaw").apply { isAccessible = true }.get(boxes) as FloatArray
                    val coinYaw = PrismBatch::class.java.getDeclaredField("lightYaw").apply { isAccessible = true }
                    try {
                        for (phase in 0 until 12) {
                            camera.position.set((phase % 3 - 1) * 3f, 2f + phase % 4, 6f)
                            camera.up.set(0f, 1f, 0f)
                            camera.lookAt(0f, 0f, -22f)
                            camera.update()
                            val terrain = TerrainHeight { z -> sin(z * 0.24f + phase) * (phase % 3) }
                            boxes.terrain = terrain; coins.terrain = terrain
                            fun draw(cull: Boolean): ByteArray {
                                boxes.begin(if (cull) camera else null)
                                coins.begin(if (cull) camera else null)
                                // Near/behind camera, both screen edges and beyond the far plane.
                                for (row in -2..24) {
                                    val z = -row * 3f
                                    for (lane in -3..3) {
                                        val x = lane * 3f
                                        boxes.box(x, -0.2f, z, 3f, 0.3f, 3f, Color.GREEN, followTerrain = true)
                                        // The reference recomputes lighting every time; the optimized
                                        // path must still respect each object's color, fog and size.
                                        if (!cull) boxYaws.fill(Float.NaN)
                                        boxes.boxSpin(x, 1.2f, z, 2.5f, 2f, 0.8f, phase * 31f + row,
                                            if (lane % 2 == 0) Color.CORAL else Color.VIOLET, (row + 2) / 30f)
                                        if (!cull) coinYaw.setFloat(coins, Float.NaN)
                                        coins.coin(x + 1.3f, 2.8f, z, 0.8f, 0.2f, phase * 29f + row, Color.GOLD)
                                        if (!cull) coinYaw.setFloat(coins, Float.NaN)
                                        coins.coin(x + 1.3f, 2.8f, z, 0.5f, 0.3f, phase * 29f + row,
                                            Color.YELLOW, (row + 2) / 30f)
                                    }
                                }
                                target.begin()
                                try {
                                    Gdx.gl.glDepthMask(true)
                                    Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
                                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                                    boxes.render(camera); coins.render(camera)
                                    return ScreenUtils.getFrameBufferPixels(0, 0, 160, 320, false)
                                } finally { target.end() }
                            }
                            val reference = draw(false)
                            val allBoxes = boxCount.getInt(boxes)
                            val allCoins = coinCount.getInt(coins)
                            val culled = draw(true)
                            assertTrue("Fixture must render visible objects", reference.indices.any {
                                it % 4 != 3 && reference[it].toInt() != 0
                            })
                            assertTrue("Offscreen boxes were not removed", boxCount.getInt(boxes) < allBoxes)
                            assertTrue("Offscreen coins were not removed", coinCount.getInt(coins) < allCoins)
                            assertArrayEquals("Visible pixels changed at camera/hill phase $phase", reference, culled)
                        }
                    } finally {
                        target.dispose(); coins.dispose(); boxes.dispose(); kit.dispose()
                    }
                } catch (t: Throwable) { failure = t } finally { done.countDown() }
            }
            assertTrue("Visibility regression timed out", done.await(30, TimeUnit.SECONDS))
            failure?.let { throw it }
        }
    }
}
