package cube.run

import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import cube.run.core.GameHostSession
import cube.run.data.Progress
import cube.run.data.Scores
import cube.run.data.Settings
import cube.run.game.CubeRun
import cube.run.ui.Hud

/** Single-game launcher host: builds the HUD over the libGDX surface and runs Cube Run. */
class GameActivity : AndroidApplication() {

    private lateinit var hud: Hud

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Debug builds only: adb shortcuts for testing individual sections and worlds.
        if (BuildConfig.DEBUG) {
            if (intent.getBooleanExtra("dev", false) && !Settings.devMode) { Settings.setDevMode(true); Progress.enterDev() }
            intent.getIntExtra("section", -2).let { if (it >= -1) Settings.testSection = it }
            intent.getIntExtra("bonus", -2).let { if (it >= -1) Settings.testBonus = it }
            intent.getIntExtra("world", -2).let { if (it >= -1) Settings.testWorld = it }
            intent.getIntExtra("boxes", -1).let { if (it >= 0) Settings.testBoxes = it }
            intent.getIntExtra("bonusnow", -2).let { if (it >= -1) Settings.testBonusNow = it }
        }
        hud = Hud(this)
        hud.setBest(Scores.best(SCORE_ID))
        val session = GameHostSession(this, SCORE_ID, hud)
        // RESTART relaunches with this extra: the run begins on the first frame, no "tap to start"
        val game = CubeRun(session, autoStart = intent.getBooleanExtra(Hud.EXTRA_AUTOSTART, false))

        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useAccelerometer = false
            useCompass = false
            numSamples = 2
            r = 8; g = 8; b = 8; a = 8
            depth = 24 // 16-bit z-fights at the far end of the long draw distance
        }
        val gameView = initializeForView(game, config)

        val root = FrameLayout(this)
        root.addView(gameView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        root.addView(hud, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setContentView(root)
        goFullscreen()
    }

    /** Edge to edge with every system bar hidden (they come back with a swipe and hide again). */
    private fun goFullscreen() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(android.view.WindowInsets.Type.systemBars())
                it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullscreen()
    }

    /** Leaving the app mid-run (home, a call) pauses it: the run resumes from the pause card. */
    override fun onPause() {
        super.onPause()
        hud.autoPause()
    }

    private companion object {
        const val SCORE_ID = "cuberun"
    }
}
