package cube.run

import android.app.Activity
import android.os.Bundle
import android.graphics.drawable.AnimatedVectorDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.VideoView
import cube.run.core.LaunchTrace
import cube.run.data.Skins
import cube.run.intro.NativeCubeView
import cube.run.intro.OpeningClock

/** Debug-only side-by-side startup experiments; none of the video payload ships in release. */
class IntroProbeActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        LaunchTrace.mark("probe activity start")
        super.onCreate(state)
        if (android.os.Build.VERSION.SDK_INT >= 31) splashScreen.setOnExitAnimationListener { it.remove() }
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF14102E.toInt()) }
        when (intent.getStringExtra("mode")) {
            "video" -> {
                val video = VideoView(this)
                root.addView(video, FrameLayout.LayoutParams(-1, -1))
                video.setVideoURI(android.net.Uri.parse("android.resource://$packageName/${R.raw.intro_probe}"))
                video.setOnPreparedListener { player ->
                    LaunchTrace.mark("video prepared")
                    player.setOnInfoListener { _, what, _ ->
                        if (what == android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) LaunchTrace.mark("video first frame")
                        false
                    }
                    video.start()
                }
            }
            "vector" -> {
                val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER }
                val animation = getDrawable(R.drawable.launch_cube_motion) as AnimatedVectorDrawable
                image.setImageDrawable(animation)
                root.addView(image, FrameLayout.LayoutParams(-1, -1))
                image.post { animation.start(); LaunchTrace.mark("vector started") }
            }
            else -> root.addView(NativeCubeView(this, OpeningClock(), Skins.get(0), 30f), FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(root)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.hide(android.view.WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = 5894
        }
        LaunchTrace.mark("probe attached")
    }
}
