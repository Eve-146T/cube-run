package cube.run.intro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import cube.run.core.LaunchTrace
import cube.run.data.Skins
import kotlin.math.*

/** Antialiased lit 3D cube: twelve projected quads, no GL, textures, or media decoder. */
@android.annotation.SuppressLint("ViewConstructor") // Constructed with the shared shot clock, never inflated from XML.
class NativeCubeView(context: Context, val clock: OpeningClock, private val skin: Skins.Skin,
                     private val worldHue: Float) : View(context) {
    private val pose = OpeningPose()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val vertices = FloatArray(24)
    private val projected = FloatArray(16)
    private val hsv = FloatArray(3)
    private var first = true
    private var moving = false
    var onFirstDraw: (() -> Unit)? = null
    var secondsForTest: Float? = null
    var drawingCube = true
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        if (!drawingCube) return
        clock.start()
        val t = secondsForTest ?: clock.sceneSeconds()
        val spin = secondsForTest ?: clock.motionSeconds()
        pose.update(t, height/resources.displayMetrics.density, spin)
        canvas.drawColor(OpeningPose.INK)
        val yaw = Math.toRadians(pose.yaw.toDouble()).toFloat()
        val tilt = Math.toRadians(pose.tilt.toDouble()).toFloat()
        val cy = cos(yaw); val sy = sin(yaw); val ct = cos(tilt); val st = sin(tilt)
        val pitchSin = sin(pose.pitch); val pitchCos = cos(pose.pitch)
        val focal = height/(2f*tan(pose.fov/2f))
        val mix = clock.skinAmount()
        val source = clock.launchAppearance
        val opacity = source.skin.opacity+(skin.opacity-source.skin.opacity)*mix
        for (shell in 0..1) {
            val sx = if (shell == 0) pose.scaleX else pose.shellScale
            val sz = sx
            val scaleY = if (shell == 0) pose.scaleY else pose.shellScale
            for (i in 0..7) {
                val x = (if (i and 1 == 0) -.5f else .5f)*sx
                val y = (if (i and 2 == 0) -.5f else .5f)*scaleY
                val z = (if (i and 4 == 0) -.5f else .5f)*sz
                val rx = x*ct-y*st; val ry = x*st+y*ct
                val wx = rx*cy+z*sy; val wy = ry+.45f; val wz = -rx*sy+z*cy
                vertices[i*3] = wx; vertices[i*3+1] = wy; vertices[i*3+2] = wz
                val dy = wy-pose.cameraY; val dz = wz-pose.cameraZ
                val depth = dy*pitchSin-dz*pitchCos
                projected[i*2] = width/2f+wx*focal/depth
                projected[i*2+1] = height/2f-(dy*pitchCos+dz*pitchSin)*focal/depth
            }
            hsv[0] = ((skin.hueAt(spin, worldHue)%360f)+360f)%360f
            hsv[1] = skin.sat*(if (shell == 0) 1f else .9f)
            hsv[2] = if (shell == 0) skin.valueAt(spin) else 1f
            val equipped = Color.HSVToColor(hsv)
            val from = source.color(spin, shell == 1)
            val base = Color.rgb(
                (Color.red(from)+(Color.red(equipped)-Color.red(from))*mix).roundToInt(),
                (Color.green(from)+(Color.green(equipped)-Color.green(from))*mix).roundToInt(),
                (Color.blue(from)+(Color.blue(equipped)-Color.blue(from))*mix).roundToInt())
            val targetShell = ((.22f+.08f*sin(spin*6f))*skin.glow).coerceAtMost(.75f)*(if (skin.opacity < 1f) .35f else 1f)
            val alpha = if (shell == 0) opacity else
                source.shellOpacity(spin)+(targetShell-source.shellOpacity(spin))*mix
            for (face in 0..5) {
                val normal = normals[face]
                val rx = normal[0]*ct-normal[1]*st; val ny = normal[0]*st+normal[1]*ct
                val nx = rx*cy+normal[2]*sy; val nz = -rx*sy+normal[2]*cy
                val point = faces[face][0]*3
                if (nx*(-vertices[point])+ny*(pose.cameraY-vertices[point+1])+nz*(pose.cameraZ-vertices[point+2]) <= 0f) continue
                val l1 = max(0f, (nx*.45f+ny*.85f+nz*.35f)/L1_LENGTH)
                val l2 = max(0f, (-nx*.6f+ny*.2f-nz*.5f)/L2_LENGTH)
                val emissive = if (shell == 1) 0f else (if (source.skin.id == 13) 1f-mix else 0f)+(if (skin.id == 13) mix else 0f)
                fun channel(value: Int, ambient: Float, light1: Float, light2: Float, emission: Float): Int =
                    (value*(ambient+l1*light1+l2*light2)+emissive*emission*255f).roundToInt().coerceIn(0, 255)
                paint.color = Color.argb((alpha*255f).roundToInt(),
                    channel(Color.red(base), .55f, .85f, .25f, .24f),
                    channel(Color.green(base), .55f, .85f, .22f, .25f),
                    channel(Color.blue(base), .6f, .8f, .3f, .26f))
                path.rewind()
                for ((index, vertex) in faces[face].withIndex()) {
                    if (index == 0) path.moveTo(projected[vertex*2], projected[vertex*2+1])
                    else path.lineTo(projected[vertex*2], projected[vertex*2+1])
                }
                path.close(); canvas.drawPath(path, paint)
            }
        }
        if (first) { first = false; LaunchTrace.mark("native cube draw"); onFirstDraw?.invoke() }
        if (!moving && t > .016f) { moving = true; LaunchTrace.mark("native cube motion") }
        if (secondsForTest == null && t < OpeningPose.DURATION) postInvalidateOnAnimation()
    }

    private companion object {
        val L1_LENGTH = sqrt(.45f*.45f+.85f*.85f+.35f*.35f)
        val L2_LENGTH = sqrt(.6f*.6f+.2f*.2f+.5f*.5f)
        val normals = arrayOf(floatArrayOf(1f,0f,0f), floatArrayOf(-1f,0f,0f), floatArrayOf(0f,1f,0f),
            floatArrayOf(0f,-1f,0f), floatArrayOf(0f,0f,1f), floatArrayOf(0f,0f,-1f))
        val faces = arrayOf(intArrayOf(1,5,7,3), intArrayOf(4,0,2,6), intArrayOf(2,3,7,6),
            intArrayOf(0,4,5,1), intArrayOf(5,4,6,7), intArrayOf(0,1,3,2))
    }
}
