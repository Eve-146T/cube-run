package cube.run.data

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import cube.run.R
import java.util.Locale

/** Add a resource translation and an entry here to offer another language. */
object Languages {
    data class Option(val code: String, val nativeName: String, val country: Int, val flag: Int)

    val options = listOf(
        Option("en", "American", R.string.country_us, R.drawable.flag_us),
        Option("de", "Deutsch", R.string.country_germany, R.drawable.flag_de),
        Option("he", "עברית", R.string.country_israel, R.drawable.flag_il),
    )

    private fun normalize(code: String) = if (code == "iw") "he" else code

    fun current(ctx: Context): String {
        val saved = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("language", null)
        options.firstOrNull { it.code == normalize(saved.orEmpty()) }?.let { return it.code }
        val system = android.content.res.Resources.getSystem().configuration.locales
        for (i in 0 until system.size()) {
            val code = normalize(system[i].language)
            if (options.any { it.code == code }) return code
        }
        return "en"
    }

    fun select(ctx: Context, code: String) {
        require(options.any { it.code == code }) { "Unsupported language: $code" }
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("language", code).apply()
    }

    fun wrap(ctx: Context): Context {
        val code = current(ctx)
        val locale = Locale.forLanguageTag(if (code == "en") "en-US" else code)
        val config = Configuration(ctx.resources.configuration).apply {
            setLocales(LocaleList(locale))
            setLayoutDirection(locale)
        }
        return ctx.createConfigurationContext(config)
    }
}
