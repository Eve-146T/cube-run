package cube.run

import android.os.Bundle
import android.view.WindowManager
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.MotionEvent
import android.widget.FrameLayout
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import cube.run.core.GameHostSession
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Scores
import cube.run.data.Settings
import cube.run.game.CubeRun
import cube.run.ui.Hud
import android.view.View

/** Single-game launcher host: builds the HUD over the libGDX surface and runs Cube Run. */
class GameActivity : AndroidApplication() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(cube.run.data.Languages.wrap(newBase))
    }

    private var languageResources: android.content.res.Resources? = null
    override fun getResources(): android.content.res.Resources = languageResources ?: super.getResources()
    private lateinit var hostSession: GameHostSession
    private var changingLanguage = false

    /** Rebuild only the localized overlay; the GL surface and world keep running uninterrupted. */
    fun changeLanguage(code: String) {
        if (changingLanguage || code == cube.run.data.Languages.current(this)) return
        val parent = hud.parent as? FrameLayout ?: return
        require(cube.run.data.Languages.options.any { it.code == code })
        changingLanguage = true
        cube.run.data.Languages.select(this, code)
        val previous = hud
        previous.settleLanguageTransition()
        languageResources = cube.run.data.Languages.wrap(baseContext).resources
        hud = Hud(this).apply {
            layoutDirection = resources.configuration.layoutDirection
            setBest(Scores.best(SCORE_ID))
            showLanguagesAfterChange()
            alpha = 0f
        }
        hostSession.attach(hud)
        parent.addView(hud, FrameLayout.LayoutParams(-1, -1))
        // Block taps during the crossfade, including outside-menu taps, until both trees settle.
        val blocker = View(this).apply { isClickable = true; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        parent.addView(blocker, FrameLayout.LayoutParams(-1, -1))
        previous.animate().alpha(0f).setDuration(180).start()
        hud.animate().alpha(1f).setDuration(180).withEndAction {
            parent.removeView(previous)
            parent.removeView(blocker)
            changingLanguage = false
            hud.resumeLanguageIdle()
        }.start()
    }

    private lateinit var hud: Hud
    private var openingClock: cube.run.intro.OpeningClock? = null
    private var gameSurface: SurfaceView? = null
    private var openingSplash: cube.run.intro.OpeningSplashHandoff? = null
    private var backCallback: android.window.OnBackInvokedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        cube.run.core.LaunchTrace.mark("activity start")
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Debug builds only: adb shortcuts for testing individual sections and worlds.
        if (BuildConfig.DEBUG) {
            if (intent.getBooleanExtra("dev", false) && !Settings.devMode) { Settings.setDevMode(true); Progress.enterDev() }
            intent.getIntExtra("section", -2).let { if (it >= -1) { Settings.testSection = it; Settings.testPillWorld = false } }
            if (intent.hasExtra("pillworld")) Settings.testPillWorld = intent.getBooleanExtra("pillworld", false)
            intent.getIntExtra("bonus", -2).let { if (it >= -1) Settings.testBonus = it }
            intent.getIntExtra("world", -2).let { if (it >= -1) Settings.testWorld = it }
            intent.getIntExtra("boxes", -1).let { if (it >= 0) Settings.testBoxes = it }
            intent.getIntExtra("bonusnow", -2).let { if (it >= -1) Settings.testBonusNow = it }
        }
        val session = GameHostSession(this, SCORE_ID)
        hostSession = session
        // RESTART relaunches with this extra: the run begins on the first frame, no "tap to start"
        val launchOpening = !intent.hasExtra(Hud.EXTRA_AUTOSTART) && savedInstanceState == null
        val clock = if (launchOpening) cube.run.intro.OpeningClock(cube.run.intro.LaunchAppearance.saved(this)).also { openingClock = it } else null
        val firstWorld = when {
            Settings.testPillWorld -> 1
            Settings.testWorld >= 0 -> Settings.testWorld
            else -> cube.run.data.Worlds.all.random().id
        }
        cube.run.intro.LaunchAppearance.remember(this, firstWorld)
        val game = CubeRun(session, autoStart = intent.getBooleanExtra(Hud.EXTRA_AUTOSTART, false),
            idleBotStart = intent.getBooleanExtra(Hud.EXTRA_IDLE_BOT, false), launchOpening = launchOpening,
            openingClock = clock, firstWorld = firstWorld)
        val openingTouch = if (clock != null) cube.run.intro.NativeCubeView(this, clock,
            cube.run.data.Skins.get(Progress.skin), cube.run.data.Worlds.get(firstWorld).hue).apply {
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnClickListener { com.badlogic.gdx.Gdx.app.postRunnable { game.finishOpening() } }
        } else null
        var openingAmount = if (launchOpening) 0f else 1f
        var finishReported = false
        if (launchOpening) {
            game.onOpeningProgress = { amount -> runOnUiThread {
                openingAmount = amount
                if (::hud.isInitialized) {
                    hud.setOpeningProgress(amount)
                    if (amount >= 1f) {
                        (openingTouch?.parent as? FrameLayout)?.removeView(openingTouch)
                        if (!finishReported) {
                            finishReported = true
                            openingSplash?.dispose()
                            cube.run.core.LaunchTrace.mark("opening finished")
                        }
                    }
                }
            } }
        } else {
            hud = Hud(this); hud.setBest(Scores.best(SCORE_ID)); session.attach(hud)
        }

        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useAccelerometer = false
            useCompass = false
            useRotationVectorSensor = false
            // All game audio uses SoundFx. Avoid opening a second, unused SoundPool.
            disableAudio = true
            useGL30 = true // libGDX falls back to GLES 2 where GLES 3 is unavailable
            numSamples = 2
            r = 8; g = 8; b = 8; a = 8
            depth = 24 // 16-bit z-fights at the far end of the long draw distance
        }
        cube.run.core.LaunchTrace.mark("gdx host begin")
        val gameView = initializeForView(game, config)
        cube.run.core.LaunchTrace.mark("gdx host ready")
        gameSurface = gameView as? SurfaceView
        // Explicitly request the game's render cadence; Android can still lower
        // it for battery/thermal policy. Never change the user's display settings.
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val mode = display.supportedModes.filter {
            it.physicalWidth == display.mode.physicalWidth && it.physicalHeight == display.mode.physicalHeight && it.refreshRate <= 90.5f
        }.maxByOrNull { it.refreshRate } ?: display.mode
        window.attributes = window.attributes.apply { preferredRefreshRate = mode.refreshRate }
        (gameView as? SurfaceView)?.holder?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = requestRate(holder)
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = requestRate(holder)
            override fun surfaceDestroyed(holder: SurfaceHolder) {}
            private fun requestRate(holder: SurfaceHolder) {
                if (android.os.Build.VERSION.SDK_INT >= 30 && holder.surface.isValid)
                    holder.surface.setFrameRate(mode.refreshRate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
            }
        })

        val root = FrameLayout(this).apply { setBackgroundColor(0xFF14102E.toInt()) }
        root.addView(gameView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        if (::hud.isInitialized) root.addView(hud, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        openingTouch?.let { root.addView(it, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT) }
        var splashHandoff = false
        var sceneReady = false
        fun revealScene() {
            if (!sceneReady || splashHandoff || isFinishing || isDestroyed) return
            cube.run.core.LaunchTrace.mark("scene revealed")
            openingTouch?.drawingCube = false
        }
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            if (clock != null && openingTouch != null) {
                openingSplash = cube.run.intro.OpeningSplashHandoff(this, openingTouch, clock,
                    onBegin = { splashHandoff = true },
                    onRemoved = {
                        game.afterFreshSceneFrame { runOnUiThread {
                            splashHandoff = false
                            sceneReady = true
                            revealScene()
                        } }
                    }).also { it.install() }
            } else splashScreen.setOnExitAnimationListener { it.remove() }
        }
        game.onSceneFrame = { runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            sceneReady = true
            revealScene()
            if (launchOpening) root.postDelayed({
                if (!isFinishing && !isDestroyed && !::hud.isInitialized) {
                    cube.run.core.LaunchTrace.mark("hud begin")
                    hud = Hud(this, openingEntrance = true); hud.setBest(Scores.best(SCORE_ID))
                    hud.setOpeningProgress(openingAmount)
                    root.rootWindowInsets?.let { hud.dispatchApplyWindowInsets(it) }
                    root.addView(hud, 1, FrameLayout.LayoutParams(-1, -1))
                    session.attach(hud)
                    if (openingAmount >= 1f) openingTouch?.let { root.removeView(it) }
                    cube.run.core.LaunchTrace.mark("hud ready")
                }
            }, 40L)
        } }
        setContentView(root)
        cube.run.core.LaunchTrace.mark("content attached")
        goFullscreen()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            backCallback = android.window.OnBackInvokedCallback { if (::hud.isInitialized) hud.navigateBack() }.also {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, it)
            }
        }
    }

    @Deprecated("Legacy Back dispatch")
    override fun onBackPressed() { if (::hud.isInitialized) hud.navigateBack() }

    override fun onDestroy() {
        openingSplash?.dispose()
        if (android.os.Build.VERSION.SDK_INT >= 33) backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        super.onDestroy()
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

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> Stage.userInteraction(down = true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> Stage.pointerDown = false
        }
        // Discrete flicks need the first threshold crossing, not resampled drag
        // positions. Keep Android's normal batching for smooth positional control.
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !Settings.smoothControl &&
            Stage.mode == Stage.NONE && !Stage.paused)
            gameSurface?.requestUnbufferedDispatch(event)
        return super.dispatchTouchEvent(event)
    }

    /** Leaving the app mid-run (home, a call) pauses it: the run resumes from the pause card. */
    override fun onPause() {
        Stage.userInteraction()
        openingClock?.pause()
        if (::hud.isInitialized) hud.autoPause()
        super.onPause()
    }

    override fun onResume() {
        openingClock?.resume()
        super.onResume()
        goFullscreen()
    }

    companion object {
        private const val SCORE_ID = "cuberun"
    }
}
