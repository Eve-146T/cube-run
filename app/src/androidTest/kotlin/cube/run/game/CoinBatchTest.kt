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
import cube.run.core.gfx.MatrixWireBatch
import cube.run.core.gfx.PrismBatch
import cube.run.core.gfx.TerrainHeight
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual GPU output: the optimized coin must keep its facets, glints, fog and fade. */
@RunWith(AndroidJUnit4::class)
class CoinBatchTest {
    @Test fun instancesMatchCpuAcrossGlintsFogFadeTerrainAndMatrixTransitions() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val done = CountDownLatch(1)
            var failure: Throwable? = null
            Gdx.app.postRunnable {
                try {
                    assertNotNull("This regression must exercise a GLES3 renderer", Gdx.gl30)
                    val kit = BoxMeshKit(ModelBuilder())
                    val wires = MatrixWireBatch(kit)
                    val coins = PrismBatch(kit, max = 64, wires = wires)
                    val target = FrameBuffer(Pixmap.Format.RGBA8888, 240, 400, true)
                    val camera = PerspectiveCamera(60f, 240f, 400f).apply {
                        near = .1f; far = 100f
                        position.set(1f, 4f, 8f); lookAt(0f, 1f, -5f); update()
                    }
                    try {
                        coins.fogColor.set(.17f, .09f, .28f, 1f)
                        for (phase in 0..23) {
                            coins.terrain = TerrainHeight { z -> sin(z * .5f) * .5f }
                            coins.opacity = if (phase % 3 == 0) .37f else 1f
                            // Include switching back to CPU at exactly the same orientation:
                            // cached GPU yaw must never reuse stale CPU face lighting.
                            for (matrix in floatArrayOf(0f, .45f, 1f, 0f)) {
                                fun draw(instanced: Boolean): ByteArray {
                                    wires.begin(); coins.begin(camera, instanced)
                                    wires.amount = matrix // runtime sets this after begin, too
                                    for (row in 0..4) for (lane in -1..1) {
                                        val fog = row * .18f
                                        val yaw = phase * 15f + if (phase % 4 == 0) 0f else row * 23f
                                        coins.coin(lane * 1.5f, 1f, -row * 2.1f, .58f, .2f, yaw, Color.GOLD, fog)
                                        coins.coin(lane * 1.5f, 1f, -row * 2.1f, .37f, .29f, yaw, Color.YELLOW, fog)
                                    }
                                    target.begin()
                                    try {
                                        Gdx.gl.glDepthMask(true)
                                        // ModelBatch can leave LEQUAL behind; Matrix wires
                                        // restore LESS. Both images need the same depth rule,
                                        // including the very first translucent CPU reference.
                                        Gdx.gl.glDepthFunc(GL20.GL_LESS)
                                        Gdx.gl.glClearColor(.04f, .02f, .09f, 1f)
                                        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                                        val selected = PrismBatch::class.java.getDeclaredField("useInstances").apply { isAccessible = true }
                                        assertEquals("Requested coin path was not selected", instanced && matrix == 0f, selected.getBoolean(coins))
                                        coins.render(camera); wires.render(camera)
                                        return ScreenUtils.getFrameBufferPixels(0, 0, 240, 400, false)
                                    } finally { target.end() }
                                }
                                val reference = draw(false)
                                assertTrue("Coin fixture must contain visible geometry", reference.indices.any {
                                    it % 4 != 3 && reference[it] != reference[it % 4]
                                })
                                val optimized = draw(true)
                                if (!reference.contentEquals(optimized)) {
                                    val errors = reference.indices.map { kotlin.math.abs((reference[it].toInt() and 255) - (optimized[it].toInt() and 255)) }
                                    android.util.Log.e("COIN_PIXELS", "phase=$phase matrix=$matrix channels=${errors.count { it != 0 }} max=${errors.maxOrNull()}")
                                }
                                assertArrayEquals("Coin pixels phase=$phase matrix=$matrix", reference, optimized)
                                assertArrayEquals("CPU lighting after instance transition phase=$phase matrix=$matrix", reference, draw(false))
                            }
                        }
                    } finally { target.dispose(); coins.dispose(); wires.dispose(); kit.dispose() }
                } catch (t: Throwable) { failure = t } finally { done.countDown() }
            }
            assertTrue("Coin framebuffer regression timed out", done.await(60, TimeUnit.SECONDS))
            failure?.let { throw it }
        }
    }
}
