package cube.run.core.gfx

import com.badlogic.gdx.graphics.Camera
import kotlin.math.abs

/** Frame-local camera planes; rejecting a whole bounding box never trims visible geometry. */
internal class BatchVisibility {
    private val planes = FloatArray(6 * 7)
    private var enabled = false

    fun begin(camera: Camera?) {
        enabled = camera != null
        if (camera == null) return
        for (i in 0 until 6) {
            val plane = camera.frustum.planes[i]
            val p = i * 7
            planes[p] = plane.normal.x
            planes[p + 1] = plane.normal.y
            planes[p + 2] = plane.normal.z
            planes[p + 3] = plane.d
            planes[p + 4] = abs(plane.normal.x)
            planes[p + 5] = abs(plane.normal.y)
            planes[p + 6] = abs(plane.normal.z)
        }
    }

    fun visible(x: Float, y: Float, z: Float, hx: Float, hy: Float, hz: Float): Boolean {
        if (!enabled) return true
        var p = 0
        while (p < planes.size) {
            val distance = planes[p] * x + planes[p + 1] * y + planes[p + 2] * z + planes[p + 3]
            val radius = planes[p + 4] * hx + planes[p + 5] * hy + planes[p + 6] * hz
            // Small margin protects geometry touching a plane from float rounding.
            if (distance + radius < -0.001f) return false
            p += 7
        }
        return true
    }
}
