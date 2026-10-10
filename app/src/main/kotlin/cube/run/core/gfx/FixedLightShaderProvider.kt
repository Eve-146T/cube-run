package cube.run.core.gfx

import com.badlogic.gdx.graphics.g3d.Attributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.Shader
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute
import com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider

/** ModelBatch shares the immutable BoxMeshKit light rig with the scenery batches. */
internal class FixedLightShaderProvider(private val rig: Environment) : DefaultShaderProvider() {
    private var epoch = 0
    fun resetLightUniforms() { epoch++ }
    private val fixedConfig = DefaultShader.Config().apply { numPointLights = 0; numSpotLights = 0 }
    private fun fixed(renderable: Renderable) = renderable.environment === rig && renderable.material.mask and (
        ColorAttribute.AmbientLight or ColorAttribute.Fog or DirectionalLightsAttribute.Type or
            PointLightsAttribute.Type or SpotLightsAttribute.Type) == 0L

    override fun createShader(renderable: Renderable): Shader {
        val fixedShader = fixed(renderable)
        return object : DefaultShader(renderable, if (fixedShader) fixedConfig else config) {
            private var lightHandle = -1
            private var lightEpoch = -1
            override fun canRender(renderable: Renderable) = fixed(renderable) == fixedShader && super.canRender(renderable)
            override fun bindLights(renderable: Renderable, combinedAttributes: Attributes) {
                // Other environments or material lighting overrides retain libGDX's full path.
                if (fixedShader && lightHandle == program.handle && lightEpoch == epoch) return
                super.bindLights(renderable, combinedAttributes)
                lightHandle = if (fixedShader) program.handle else -1
                lightEpoch = epoch
            }
        }
    }
}
