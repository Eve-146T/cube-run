package cube.run.intro

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import cube.run.data.Progress
import cube.run.data.Skins
import cube.run.data.Worlds
import kotlin.math.sin

/** The palette Android can render before our process starts, saved when a cube is equipped. */
class LaunchAppearance(val skin: Skins.Skin, val worldHue: Float) {
    fun color(seconds: Float, shell: Boolean): Int = Color.HSVToColor(floatArrayOf(
        ((skin.hueAt(seconds, worldHue)%360f)+360f)%360f,
        skin.sat*(if (shell) .9f else 1f), if (shell) 1f else skin.valueAt(seconds)))

    fun shellOpacity(seconds: Float): Float = ((.22f+.08f*sin(seconds*6f))*skin.glow).coerceAtMost(.75f)*
        (if (skin.opacity < 1f) .35f else 1f)

    companion object {
        val ROSE = LaunchAppearance(Skins.Skin(-1, "Launch", 0, Skins.FIXED, hue = 329.33333f, sat = .45f), 0f)
        private fun prefs(context: Context) = context.getSharedPreferences("launch_appearance", Context.MODE_PRIVATE)
        fun saved(context: Context): LaunchAppearance {
            val saved = prefs(context)
            val id = saved.getInt("skin", -1)
            return if (id < 0) ROSE else LaunchAppearance(Skins.get(id), Worlds.get(saved.getInt("world", 0)).hue)
        }

        /** The platform persists the theme name for future cold starts; no launcher aliases or extra library. */
        fun remember(activity: Activity, world: Int? = null) {
            if (Build.VERSION.SDK_INT < 31) return
            val saved = prefs(activity)
            val worldId = Worlds.get(world ?: saved.getInt("world", 0)).id
            val skinId = Skins.get(Progress.skin).id
            val style = if (skinId == 0 && worldId > 0) LaunchThemes.worlds[worldId-1] else LaunchThemes.skins[skinId]
            activity.splashScreen.setSplashScreenTheme(style)
            saved.edit().putInt("skin", skinId).putInt("world", worldId).apply()
        }
    }
}
