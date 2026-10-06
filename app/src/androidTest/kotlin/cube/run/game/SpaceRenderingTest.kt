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
import cube.run.core.gfx.FacetBatch
import cube.run.core.gfx.FacetShapes
import cube.run.core.gfx.WorldBend
import cube.run.game.space.SpaceLandmarks
import cube.run.game.space.SpaceBackdrop
import cube.run.game.space.SpaceWorld
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import kotlin.math.cos
import kotlin.math.sin
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpaceRenderingTest {
    @Test fun staticBackdropPreservesOriginalTwinklesAndSparkles() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val done = CountDownLatch(1)
            var failure: Throwable? = null
            Gdx.app.postRunnable {
                try {
                    val backdrop = SpaceBackdrop()
                    val shapes = ShapeRenderer()
                    val target = FrameBuffer(Pixmap.Format.RGBA8888, 160, 320, false)
                    val projection = Matrix4().setToOrtho2D(-1f, -2f, 2f, 4f)
                    val space = SpaceWorld(Gdx.app.applicationListener as CubeRun)
                    space.star.set(.9f, .85f, 1f, 1f)
                    space.neonSoft.set(.5f, .7f, 1f, 1f); space.skyBottom.set(.1f, .2f, .3f, 1f)
                    val stars = floatArrayOf(-.4f, .6f, .12f, .8f, 1.7f)
                    val blobs = floatArrayOf(.2f, -.5f, .65f, .25f, .7f, .2f)
                    val sparkles = floatArrayOf(.3f, .8f, .25f, .6f, 1f)
                    backdrop.build(stars, blobs, sparkles)
                    shapes.projectionMatrix = projection
                    try {
                        for (time in listOf(0f, 1.5f, 8f, 10000f)) {
                            fun draw(gpu: Boolean): ByteArray {
                                target.begin()
                                try {
                                    Gdx.gl.glClearColor(0f, 0f, 0f, 1f); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
                                    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST); Gdx.gl.glDisable(GL20.GL_CULL_FACE)
                                    Gdx.gl.glEnable(GL20.GL_BLEND); Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
                                    if (gpu) backdrop.render(projection, Matrix4(), time, .75f, space)
                                    else {
                                        val inner = Color(); val outer = Color()
                                        shapes.begin(ShapeRenderer.ShapeType.Filled)
                                        fun ellipse(u: Float, v: Float, ra: Float, rb: Float, angle: Float, tint: Color, alpha: Float) {
                                            inner.set(tint.r, tint.g, tint.b, alpha); outer.set(tint.r, tint.g, tint.b, 0f)
                                            val ca = cos(angle); val sa = sin(angle)
                                            var px = u + ca * ra; var py = v + sa * ra
                                            for (j in 1..14) {
                                                val a = j * 2f * Math.PI.toFloat() / 14
                                                val ex = cos(a) * ra; val ey = sin(a) * rb
                                                val x = u + ca * ex - sa * ey; val y = v + sa * ex + ca * ey
                                                shapes.triangle(u, v, px, py, x, y, inner, outer, outer)
                                                px = x; py = y
                                            }
                                        }
                                        val band = Color(space.neonSoft).lerp(space.skyBottom, .4f)
                                        ellipse(blobs[0], blobs[1], blobs[2], blobs[3], blobs[4], band, blobs[5] * .75f)
                                        val tw = .85f + .15f * sin(time * 1.1f + stars[4] * 5f)
                                        ellipse(stars[0], stars[1], stars[2], stars[2], 0f, space.star, stars[3] * tw * .75f)
                                        val twinkle = .55f + .45f * sin(time * 2.3f + sparkles[3] * 4f)
                                        val radius = sparkles[2] * (.7f + .3f * twinkle)
                                        val width = radius * .16f
                                        val spin = time * .3f + sparkles[3]
                                        inner.set(space.neonSoft.r, space.neonSoft.g, space.neonSoft.b, twinkle * .75f)
                                        outer.set(space.neonSoft.r, space.neonSoft.g, space.neonSoft.b, 0f)
                                        for (arm in 0..3) {
                                            val a = spin + arm * Math.PI.toFloat() / 2f
                                            val ox = cos(a); val oy = sin(a); val u = sparkles[0]; val v = sparkles[1]
                                            shapes.triangle(u-oy*width,v+ox*width,u+oy*width,v-ox*width,u+ox*radius,v+oy*radius,inner,inner,outer)
                                        }
                                        ellipse(sparkles[0], sparkles[1], radius*.45f, radius*.45f, 0f, space.neonSoft, twinkle*.75f*.35f)
                                        shapes.end()
                                    }
                                    assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
                                    return ScreenUtils.getFrameBufferPixels(0, 0, 160, 320, false)
                                } finally { target.end() }
                            }
                            val reference = draw(false); val gpu = draw(true)
                            var different = 0
                            for (i in gpu.indices) if (kotlin.math.abs((gpu[i].toInt() and 255) - (reference[i].toInt() and 255)) > 2) different++
                            assertTrue("Backdrop differs in $different channels at $time", different < gpu.size / 1000)
                        }
                    } finally { target.dispose(); shapes.dispose(); backdrop.dispose() }
                } catch (t: Throwable) { failure = t } finally { done.countDown() }
            }
            assertTrue(done.await(60, TimeUnit.SECONDS)); failure?.let { throw it }
        }
    }

    @Test fun gpuAndCpuPreserveVoxelLightingBandsBendsAndCoverage() {
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            val done = CountDownLatch(1)
            var failure: Throwable? = null
            Gdx.app.postRunnable {
                val oldX = WorldBend.x; val oldY = WorldBend.y
                try {
                    assertNotNull("Device must exercise GLES3 instancing", Gdx.gl30)
                    val kit = BoxMeshKit(ModelBuilder())
                    val batch = FacetBatch(kit, 100000)
                    val target = FrameBuffer(Pixmap.Format.RGBA8888, 160, 320, true)
                    val camera = PerspectiveCamera(67f, 160f, 320f).apply {
                        near = .5f; far = 120f
                        position.set(0f, 3f, 6f); lookAt(0f, 0f, -25f); update()
                    }
                    val ball = FacetShapes.voxelBall(9)
                    val ring = FacetShapes.voxelRing(14, .62f, 3)
                    val rock = FacetShapes.clump(2)
                    val palette = arrayOf(Color.CORAL, Color.SKY, Color.GOLD)
                    val bands = floatArrayOf(-.7f, -.35f, -.05f, .3f, .62f)
                    try {
                        for (phase in 0..7) {
                            WorldBend.x = if (phase % 2 == 0) .0035f else -.0035f
                            WorldBend.y = .001f
                            fun draw(gpu: Boolean, cull: Boolean, opacity: Float): Pair<ByteArray, Int> {
                                batch.gpuEnabled = gpu; batch.cullingEnabled = cull; batch.begin(camera)
                                batch.add(ball, -4f, 7f, -32f, 5f, 4f, 3f, phase * 31f, 24f, 17f,
                                    palette, .23f, Color.NAVY, glow = .08f, bands = bands, opacity = opacity)
                                batch.add(ring, -4f, 7f, -32f, 9f, 9f, 9f, phase * 17f, 23f, 4f,
                                    palette, .1f, Color.NAVY, glow = .12f, opacity = opacity)
                                batch.add(rock, 2f, 1f, -22f, 2f, 3f, 1.5f, phase * 23f, phase * 19f, 12f,
                                    palette, .15f, Color.NAVY, bent = true, opacity = opacity)
                                batch.add(ball, 1000f, 1f, -30f, 4f, 4f, 4f, 0f, 0f, 0f,
                                    palette, 0f, Color.NAVY)
                                val queued = batch.queued
                                target.begin()
                                try {
                                    Gdx.gl.glDepthMask(true)
                                    Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
                                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                                    batch.render(camera)
                                    assertEquals("GL error", GL20.GL_NO_ERROR, Gdx.gl.glGetError())
                                    return ScreenUtils.getFrameBufferPixels(0, 0, 160, 320, false) to queued
                                } finally { target.end() }
                            }
                            val opacity = if (phase < 4) 1f else .5f
                            val reference = draw(false, false, opacity)
                            val cpu = draw(false, true, opacity)
                            assertArrayEquals("Culling changed pixels, phase $phase", reference.first, cpu.first)
                            assertTrue("Invisible objects must be rejected", cpu.second < reference.second)
                            val gpu = draw(true, true, opacity).first
                            var different = 0
                            for (i in gpu.indices) if (kotlin.math.abs((gpu[i].toInt() and 255) -
                                        (reference.first[i].toInt() and 255)) > 1) different++
                            // Allow subpixel rasterization differences at rotated triangle edges.
                            assertTrue("GPU changed $different channels at phase $phase", different < gpu.size / 500)
                            assertTrue("Visible fixture required", reference.first.indices.any {
                                it % 4 != 3 && reference.first[it].toInt() != 0
                            })
                        }
                        fun coverage(alpha: Float): Int {
                            batch.begin(camera)
                            batch.add(ball, 0f, 3f, -20f, 6f, 6f, 6f, 0f, 0f, 0f,
                                arrayOf(Color.WHITE), 0f, Color.BLACK, glow = 1f, opacity = alpha)
                            target.begin()
                            try {
                                Gdx.gl.glDepthMask(true); Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
                                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                                batch.render(camera)
                                val pixels = ScreenUtils.getFrameBufferPixels(0, 0, 160, 320, false)
                                return pixels.indices.count { it % 4 == 0 && pixels[it].toInt() != 0 }
                            } finally { target.end() }
                        }
                        for (gpu in listOf(false, true)) {
                            batch.gpuEnabled = gpu
                            assertEquals("Zero opacity must leave no silhouette", 0, coverage(0f))
                            val full = coverage(1f)
                            var previous = 0
                            for (step in 1..10) {
                                val visible = coverage(SpaceLandmarks.smoothFade(step / 10f))
                                assertTrue("Coverage must grow smoothly", visible > previous)
                                if (step == 5) assertTrue("Half fade must reveal half the sky", visible in (full * .45f).toInt()..(full * .55f).toInt())
                                previous = visible
                            }
                        }
                    } finally { target.dispose(); batch.dispose(); kit.dispose() }
                } catch (t: Throwable) { failure = t }
                finally { WorldBend.x = oldX; WorldBend.y = oldY; done.countDown() }
            }
            assertTrue("Space renderer test timed out", done.await(60, TimeUnit.SECONDS))
            failure?.let { throw it }
        }
    }
}
