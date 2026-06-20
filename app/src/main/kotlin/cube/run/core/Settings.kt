package cube.run.core

import android.content.Context
import android.content.SharedPreferences

/** Persistent player-facing options. Safe to read from the GL thread (values are @Volatile). */
object Settings {
    private lateinit var prefs: SharedPreferences

    /** "Smooth control": steer continuously without lifting your finger between moves. */
    @Volatile var smoothControl: Boolean = false
        private set

    /** Smooth-control sensitivity, 0..1 (higher = smaller finger movement per move). */
    @Volatile var smoothSensitivity: Float = 0.5f
        private set

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        smoothControl = prefs.getBoolean("smooth_control", false)
        smoothSensitivity = prefs.getFloat("smooth_sensitivity", 0.5f)
    }

    fun setSmoothControl(v: Boolean) {
        smoothControl = v
        prefs.edit().putBoolean("smooth_control", v).apply()
    }

    fun setSmoothSensitivity(v: Float) {
        smoothSensitivity = v.coerceIn(0f, 1f)
        prefs.edit().putFloat("smooth_sensitivity", smoothSensitivity).apply()
    }
}
