package cube.run

import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import cube.run.core.GameChromeView
import cube.run.core.GameHostSession
import cube.run.core.Scores
import cube.run.game.CubeRun

/** Single-game launcher host: builds the shared HUD over the libGDX surface and runs Cube Run. */
class GameActivity : AndroidApplication() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val chrome = GameChromeView(this, ACCENT)
        chrome.setBest(Scores.best(SCORE_ID))
        val session = GameHostSession(this, SCORE_ID, chrome)
        val game = CubeRun(session)

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
        root.addView(chrome, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setContentView(root)
    }

    private companion object {
        const val SCORE_ID = "cuberun"
        const val ACCENT = 0xFF5CFA46.toInt()
    }
}
