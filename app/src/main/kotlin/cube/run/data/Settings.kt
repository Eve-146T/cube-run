package cube.run.data

import android.content.Context
import android.content.SharedPreferences

/** Persistent player-facing options. Safe to read from the GL thread (values are @Volatile). */
object Settings {
    private lateinit var prefs: SharedPreferences

    /**
     * Master flag for the experimental "smooth control" feature *and* its UI.
     * While false the feature is fully off and its toggle/sensitivity controls are
     * hidden. To bring it back, flip this to true — the in-game toggle then drives
     * the feature (we re-enable the UI, not the feature itself).
     */
    const val SMOOTH_CONTROL_UI = false

    /** "Smooth control": steer continuously without lifting your finger between moves. */
    @Volatile private var smoothControlPref: Boolean = false

    /** Effective smooth-control state — always false while [SMOOTH_CONTROL_UI] is off. */
    val smoothControl: Boolean get() = SMOOTH_CONTROL_UI && smoothControlPref

    /** Allow swipe direction changes without lifting the finger. */
    @Volatile var multiSwipe: Boolean = false
        private set

    /** Smooth-control sensitivity, 0..1 (higher = smaller finger movement per move). */
    @Volatile var smoothSensitivity: Float = 0.5f
        private set

    /** Master mute for all procedural sound effects. Read from the GL thread. */
    @Volatile var soundEnabled: Boolean = true
        private set

    /** Master toggle for haptic feedback. Read from the GL thread. */
    @Volatile var hapticsEnabled: Boolean = true
        private set
    @Volatile var audioHapticRevision: Int = 0
        private set

    /** Sound volume in steps, 1..[VOLUME_STEPS] (muting stays a separate switch). */
    @Volatile var volume: Int = VOLUME_STEPS
        private set
    const val VOLUME_STEPS = 10

    /** What every effect is scaled by: the steps follow loudness, not amplitude, so each one sounds like a step. */
    val soundGain: Float get() = (volume.toFloat() / VOLUME_STEPS).let { it * it }

    /** Boost presses already applied when a run starts (0 = the usual standing start). Capped by the presses you own. */
    @Volatile var startSpeed: Int = 0
        private set
    /** Muting the preset keeps the selected steps for the next toggle. */
    @Volatile var startSpeedEnabled: Boolean = true
        private set
    val effectiveStartSpeed: Int get() = if (startSpeedEnabled) startSpeed else 0

    /** Quiet coins look faint and have no pickup sound or haptics. */
    @Volatile var quietCoins: Boolean = false
        private set
    val roadCoins: Boolean get() = !quietCoins
    val roadCoinOpacity: Float get() = if (roadCoins) 1f else .12f

    /**
     * Dev mode: the section director cycles the sections under review and the
     * bank is filled. A test tool, so it is process-scoped on purpose: it
     * survives an in-task RESTART but never a fresh launch.
     */
    @Volatile var devMode: Boolean = false
        private set

    /**
     * Section explorer: when ≥ 0 the director serves only this section id, on
     * loop. Process-scoped on purpose (RESTART relaunches in-process), never persisted.
     */
    @Volatile var testSection: Int = -1

    /** Debug: when ≥ 0 every portal opens to this bonus world. Process-scoped. */
    @Volatile var testBonus: Int = -1

    /** Debug: when ≥ 0 the run starts in this world. Process-scoped. */
    /** Debug: start the run already inside this bonus world (dev mode only; -1 = off). */
    @Volatile var testBonusNow = -1
    /** Debug: mystery boxes the next run starts with (dev mode only), so the box stage is one END RUN away. */
    @Volatile var testBoxes = 0
    @Volatile var testPillWorld = false
    /** Section explorer: the run starts inside Outer Space and every portal leads back there. Process-scoped. */
    @Volatile var testSpaceWorld = false
    @Volatile var testWorld: Int = -1

    @Volatile private var performanceCourseSelected = false
    val performanceCourse: Boolean get() = cube.run.BuildConfig.DEBUG && performanceCourseSelected
    private var performancePreviousDev = false
    private var performancePreviousWorld = -1

    /** A process-scoped course: retries retain it, a fresh app process does not. */
    fun selectPerformanceCourse() {
        if (!cube.run.BuildConfig.DEBUG) return
        if (!performanceCourseSelected) {
            performancePreviousDev = devMode
            performancePreviousWorld = testWorld
        }
        performanceCourseSelected = true
        setDevMode(true); Progress.enterDev()
        testSection = 56; testWorld = 2; testPillWorld = false; testSpaceWorld = false
        testBonus = -1; testBonusNow = -1; testBoxes = 0
    }

    /** Restore the pre-test bank/developer state without changing equipment. */
    fun leavePerformanceCourse() {
        if (!performanceCourseSelected) return
        performanceCourseSelected = false
        testWorld = performancePreviousWorld
        setDevMode(performancePreviousDev)
        if (!performancePreviousDev) Progress.leaveDev()
    }

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        multiSwipe = prefs.getBoolean("multi_swipe", false)
        smoothControlPref = prefs.getBoolean("smooth_control", false)
        smoothSensitivity = prefs.getFloat("smooth_sensitivity", 0.5f)
        soundEnabled = prefs.getBoolean("sound_enabled", true)
        hapticsEnabled = prefs.getBoolean("haptics_enabled", true)
        volume = prefs.getInt("volume", VOLUME_STEPS).coerceIn(1, VOLUME_STEPS)
        startSpeed = prefs.getInt("start_speed", 0).coerceIn(0, 10)
        startSpeedEnabled = prefs.getBoolean("start_speed_enabled", true)
        quietCoins = prefs.getBoolean("quiet_coins", !prefs.getBoolean("road_coins", true))
        if (prefs.contains("dev_mode")) prefs.edit().remove("dev_mode").apply() // was persisted once; never again
    }

    fun setMultiSwipe(v: Boolean) {
        multiSwipe = v
        prefs.edit().putBoolean("multi_swipe", v).apply()
    }

    fun setSmoothControl(v: Boolean) {
        smoothControlPref = v
        prefs.edit().putBoolean("smooth_control", v).apply()
    }

    fun setSmoothSensitivity(v: Float) {
        smoothSensitivity = v.coerceIn(0f, 1f)
        prefs.edit().putFloat("smooth_sensitivity", smoothSensitivity).apply()
    }

    fun setSoundEnabled(v: Boolean) {
        if (soundEnabled == v) return
        audioHapticRevision++
        soundEnabled = v
        prefs.edit().putBoolean("sound_enabled", v).apply()
    }

    fun setHapticsEnabled(v: Boolean) {
        if (hapticsEnabled == v) return
        audioHapticRevision++
        hapticsEnabled = v
        prefs.edit().putBoolean("haptics_enabled", v).apply()
    }

    fun setVolume(v: Int) {
        val next = v.coerceIn(1, VOLUME_STEPS)
        if (volume == next) return
        volume = next
        prefs.edit().putInt("volume", volume).apply()
    }

    fun setStartSpeed(v: Int) {
        val next = v.coerceIn(0, 10)
        if (startSpeed == next && startSpeedEnabled) return
        startSpeed = next
        startSpeedEnabled = true
        prefs.edit().putInt("start_speed", startSpeed).putBoolean("start_speed_enabled", true).apply()
    }

    fun setStartSpeedEnabled(v: Boolean) {
        if (startSpeedEnabled == v) return
        startSpeedEnabled = v
        prefs.edit().putBoolean("start_speed_enabled", v).apply()
    }

    fun setQuietCoins(v: Boolean) {
        if (quietCoins == v) return
        quietCoins = v
        prefs.edit().putBoolean("quiet_coins", v).apply()
    }

    fun setRoadCoins(v: Boolean) = setQuietCoins(!v)

    fun setDevMode(v: Boolean) {
        devMode = v
    }
}
