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

    /** Smooth-control sensitivity, 0..1 (higher = smaller finger movement per move). */
    @Volatile var smoothSensitivity: Float = 0.5f
        private set

    /** Master mute for all procedural sound effects. Read from the GL thread. */
    @Volatile var soundEnabled: Boolean = true
        private set

    /** Master toggle for haptic feedback. Read from the GL thread. */
    @Volatile var hapticsEnabled: Boolean = true
        private set

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
    @Volatile var testWorld: Int = -1

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        smoothControlPref = prefs.getBoolean("smooth_control", false)
        smoothSensitivity = prefs.getFloat("smooth_sensitivity", 0.5f)
        soundEnabled = prefs.getBoolean("sound_enabled", true)
        hapticsEnabled = prefs.getBoolean("haptics_enabled", true)
        if (prefs.contains("dev_mode")) prefs.edit().remove("dev_mode").apply() // was persisted once; never again
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
        soundEnabled = v
        prefs.edit().putBoolean("sound_enabled", v).apply()
    }

    fun setHapticsEnabled(v: Boolean) {
        hapticsEnabled = v
        prefs.edit().putBoolean("haptics_enabled", v).apply()
    }

    fun setDevMode(v: Boolean) {
        devMode = v
    }
}
