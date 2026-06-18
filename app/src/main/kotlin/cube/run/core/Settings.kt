package cube.run.core

import android.content.Context
import android.content.SharedPreferences

/** Persistent player-facing options. Safe to read from the GL thread (values are @Volatile). */
object Settings {
    private lateinit var prefs: SharedPreferences

    /** "Smooth control": steer continuously without lifting your finger between moves. */
    @Volatile var smoothControl: Boolean = false
        private set

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        smoothControl = prefs.getBoolean("smooth_control", false)
    }

    fun setSmoothControl(v: Boolean) {
        smoothControl = v
        prefs.edit().putBoolean("smooth_control", v).apply()
    }
}
