package cube.run.game

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.ScreenUtils
import cube.run.GameActivity
import cube.run.core.Stage
import cube.run.core.gfx.BoxMeshKit
import cube.run.core.gfx.FixedLightShaderProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FixedLightShaderTest {
    @Test fun fixedRigMatchesStockAcrossMaterialsFramesAndShaderReload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            ActivityScenario.launch<GameActivity>(Intent(context, GameActivity::class.java)
                .putExtra("autostart", true)).use {
                val done = CountDownLatch(1)
                var failure: Throwable? = null
                Gdx.app.postRunnable {
                    try {
                        Stage.paused = true
                        val kit = BoxMeshKit(ModelBuilder())
                        val rig = kit.environment()
                        val other = Environment().apply {
                            set(ColorAttribute.createAmbientLight(.3f, .2f, .4f, 1f))
                            add(PointLight().set(Color.CYAN, 0f, 3f, 3f, 15f))
                        }
                        val model = ModelBuilder().createBox(1f, 1f, 1f,
                            Material(ColorAttribute.createDiffuse(Color.ORANGE)), (Usage.Position or Usage.Normal).toLong())
                        val instances = Array(8) { i -> ModelInstance(model).apply {
                            materials[0].set(ColorAttribute.createDiffuse(Color((i + 1) / 9f, .5f, 1f - i / 9f, 1f)))
                            if (i % 3 == 0) materials[0].set(BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, .45f))
                            if (i == 7) materials[0].set(ColorAttribute.createAmbientLight(.1f, .4f, .2f, 1f))
                        } }
                        val provider = FixedLightShaderProvider(rig)
                        val stock = ModelBatch(); val optimized = ModelBatch(provider)
                        val target = FrameBuffer(Pixmap.Format.RGBA8888, 240, 400, true)
                        val cam = PerspectiveCamera(60f, 240f, 400f).apply {
                            near = .1f; far = 100f; position.set(2f, 4f, 8f); lookAt(0f, 0f, -3f); update()
                        }
                        try {
                            fun pixels(batch: ModelBatch, environment: Environment): ByteArray {
                                target.begin()
                                try {
                                    Gdx.gl.glDepthMask(true); Gdx.gl.glClearColor(.1f, .2f, .3f, 1f)
                                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                                    batch.begin(cam)
                                    for (instance in instances) batch.render(instance, environment)
                                    batch.end()
                                    return ScreenUtils.getFrameBufferPixels(0, 0, 240, 400, false)
                                } finally { target.end() }
                            }
                            for (frame in 0..11) {
                                for (i in instances.indices) instances[i].transform.setToTranslation(
                                    (i % 3 - 1) * 1.4f, i / 3 * 1.3f, -i * .6f).rotate(0f, 1f, 0f, frame * 23f + i * 17f)
                                for (environment in listOf(rig, other, rig))
                                    assertArrayEquals("Rig frame=$frame fixed=${environment === rig}", pixels(stock, environment), pixels(optimized, environment))
                                if (frame == 5) { ShaderProgram.invalidateAllShaderPrograms(Gdx.app); provider.resetLightUniforms() }
                            }
                            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
                        } finally { target.dispose(); optimized.dispose(); stock.dispose(); model.dispose(); kit.dispose() }
                    } catch (t: Throwable) { failure = t }
                    finally { done.countDown() }
                }
                assertTrue("GL fixture timed out", done.await(45, TimeUnit.SECONDS))
                failure?.let { throw it }
            }
        } finally { Stage.paused = false }
    }
}
