# Translations

English is the fallback catalog in `app/src/main/res/values/strings.xml` and
`language.xml`. German lives in `values-de`; Hebrew uses Android's compatible
`values-iw` resource qualifier and the modern `he` language code in the picker.
The first launch chooses a supported device language, falling back to English.
An explicit selection is saved in settings and takes precedence on later launches.

## Add a language

1. Copy both English catalogs into the appropriate Android `values-…` directory.
   Translate whole sentences; keep resource names and numbered format arguments
   (`%1$s`, `%2$d`) intact. Translate each plural form required by that language.
2. Add an option to `data/Languages.kt`: language tag, native language name,
   country-name resource, and a vector flag. Add the country name to every catalog.
   Flags identify the labeled country; the native language name identifies the language.
3. Run `./gradlew :app:assembleDebug :app:lintDebug` and the `cube.run.ui.LanguageTest`
   instrumentation tests. Check the picker, home, wardrobe, shop, pause and rewards
   on a small screen and with large system text. Check RTL when applicable.

## New text

Put player-facing text and accessibility labels in resources. Use `getString`
with format arguments and `getQuantityString` for counts; avoid sentence fragments
or concatenated English. Format decimals with the context's configured locale.
Keep Cube Run branding unchanged.

Model labels in `data/` and `game/` remain stable so localization cannot change
save keys or simulation behavior. `ui/LocalizedText.kt` resolves these labels at
the presentation boundary. When adding an item, add its label mapping and resource
translations together. Never store translated text as an identifier.

Use start/end layout spacing and `UiKit.tracking` for text letter spacing.
Menus mirror in Hebrew, while the 3D game and lane gestures retain their physical
directions. Numeric-only text uses left-to-right ordering. The bundled Fredoka
font includes Hebrew glyphs. All languages ship offline, including App Bundle
installs (`bundle.language.enableSplit = false`).
