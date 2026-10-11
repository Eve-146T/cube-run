package cube.run.ui

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle
import android.os.Build

/** Bundled Fredoka for Latin/Hebrew and Fredoka One Cyrillic for Cyrillic. */
object Fonts {
    private const val FREDOKA = "fonts/Fredoka.ttf"
    private const val CYRILLIC = "fonts/FredokaOneCyrillic-Regular.ttf"
    private enum class Family { FREDOKA, CYRILLIC, COMBINED }
    private data class Key(val family: Family, val weight: Int)
    private val cache = HashMap<Key, Typeface>()

    /** Cyrillic has one fixed style. [language] selects a face on Android 9 only. */
    fun get(ctx: Context, weight: Int, language: String = ctx.resources.configuration.locales[0].language): Typeface {
        val family = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> Family.COMBINED
            language == "ru" || language == "uk" -> Family.CYRILLIC
            else -> Family.FREDOKA
        }
        val key = Key(family, if (family == Family.CYRILLIC) 400 else weight)
        return cache.getOrPut(key) {
            when (family) {
                Family.COMBINED -> combined(ctx, weight)
                Family.CYRILLIC -> Typeface.Builder(ctx.assets, CYRILLIC).build()
                Family.FREDOKA -> Typeface.Builder(ctx.assets, FREDOKA)
                    .setFontVariationSettings(variation(weight)).build()
            }
        }
    }

    private fun variation(weight: Int) = "'wght' $weight, 'wdth' 100"

    @TargetApi(Build.VERSION_CODES.Q)
    private fun combined(ctx: Context, weight: Int): Typeface {
        val primary = Font.Builder(ctx.assets, FREDOKA)
            .setFontVariationSettings(variation(weight)).setWeight(weight).build()
        // Match the requested style without synthesizing bold on the fixed Cyrillic outlines.
        val cyrillic = Font.Builder(ctx.assets, CYRILLIC).setWeight(weight).build()
        return Typeface.CustomFallbackBuilder(FontFamily.Builder(primary).build())
            .addCustomFallback(FontFamily.Builder(cyrillic).build())
            .setStyle(FontStyle(weight, FontStyle.FONT_SLANT_UPRIGHT))
            .build()
    }
}
