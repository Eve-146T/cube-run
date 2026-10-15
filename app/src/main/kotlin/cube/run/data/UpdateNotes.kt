package cube.run.data

import android.content.Context
import android.content.res.Configuration
import cube.run.BuildConfig
import cube.run.R
import java.util.Locale

/**
 * "In this update": what changed in the build you are holding. The notes live in
 * `update_notes.xml` (one per language) and every update (every worktree, too) rewrites them.
 * They pop up once after an update, the first time the app is opened, and Settings
 * opens them any time. A fresh install never gets them: there is nothing to compare with.
 */
object UpdateNotes {
    /** [icon] is an achievement id (its tile is shown) or [NEWS]. */
    data class Item(val icon: String, val title: String, val text: String)

    const val NEWS = "news"
    private const val PREFS = "update_notes"
    private const val SEEN = "seen"

    fun items(context: Context): List<Item> = parse(context.resources.getStringArray(R.array.update_notes))

    internal fun parse(lines: Array<String>): List<Item> = lines.mapNotNull { line ->
        val parts = line.split('|', limit = 3).map { it.trim() }
        if (parts.size == 3 && parts.all { it.isNotEmpty() }) Item(parts[0], parts[1], parts[2]) else null
    }

    /**
     * Which notes these are: the version and the English text. Rewriting the notes, even in a
     * worktree build installed over another with the same version, makes them new again.
     */
    internal fun key(context: Context): String {
        val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) })
        return "${BuildConfig.VERSION_CODE}:${english.resources.getStringArray(R.array.update_notes).joinToString("\n").hashCode()}"
    }

    /** True once per new set of notes after an update; also marks them seen. */
    fun takeUnseen(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = key(context)
        val seen = prefs.getString(SEEN, null)
        if (seen == key) return false
        prefs.edit().putString(SEEN, key).apply()
        if (items(context).isEmpty()) return false
        // No notes seen yet: an update from before this pop-up existed has played some runs.
        return seen != null || (!freshInstall(context) && Progress.runs > 0)
    }

    private fun freshInstall(context: Context): Boolean = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    } catch (_: Exception) { false }
}
