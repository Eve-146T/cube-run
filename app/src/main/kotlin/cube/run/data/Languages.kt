package cube.run.data

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.util.TypedValue
import cube.run.R
import java.util.Locale

/** Register a locale in supported_languages and supply its translated metadata and flag. */
object Languages {
    data class Option(val code: String, val nativeName: String, val country: String, val flag: Int)

    fun options(ctx: Context): List<Option> {
        val codes = ctx.resources.getStringArray(R.array.supported_languages)
        return codes.map { code ->
            val locale = Locale.forLanguageTag(if (code == "en") "en-US" else code)
            val config = Configuration(ctx.resources.configuration).apply {
                setLocales(LocaleList(locale))
                setLayoutDirection(locale)
            }
            val localizedCtx = ctx.createConfigurationContext(config)
            Option(
                code = code,
                nativeName = localizedCtx.getString(R.string.language_name),
                country = localizedCtx.getString(R.string.language_country),
                flag = TypedValue().also {
                    localizedCtx.resources.getValue(R.drawable.language_flag, it, true)
                }.resourceId
            )
        }
    }

    private fun normalize(code: String) = if (code == "iw") "he" else code

    fun current(ctx: Context): String {
        val saved = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("language", null)
        val opts = options(ctx)
        opts.firstOrNull { it.code == normalize(saved.orEmpty()) }?.let { return it.code }
        val system = android.content.res.Resources.getSystem().configuration.locales
        for (i in 0 until system.size()) {
            val code = normalize(system[i].language)
            if (opts.any { it.code == code }) return code
        }
        return "en"
    }

    fun select(ctx: Context, code: String) {
        require(options(ctx).any { it.code == code }) { "Unsupported language: $code" }
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
