# What goes in "In this update"

The "In this update" card pops up once on the first launch after an update, and Settings opens it any time. It is a short list of what a player will notice, not a changelog. Its text lives in `app/src/main/res/values*/update_notes.xml` (English, German, Hebrew), one `icon | title | text` line per item.

## Every update starts from nothing

Every update, and every worktree, rewrites the list to say what *it* changes. Nothing carries over from the last update: old news is not news.

- Delete last update's items, then write this update's.
- Set `update_notes_for` (in the English file) to the new `versionName`. Notes written for another version are stale: they never pop up and never show as new. `UpdateNotesTest.theNotesAreWrittenForThisVersion` fails until the notes are rewritten, so a version bump can't ship last update's notes by accident.
- In a worktree the version stays the same. Rewriting the text is enough to make the card pop up again on the next launch, so testers see what that worktree changed.

## What gets its own item

Something a player can see, try or chase:

- A new achievement or challenge. Its own tile is the icon: use the achievement id.
- A new cube, trail, bubble skin, world, biome, upgrade or other thing to buy or find.
- A new screen or feature, like this card itself (icon `news`).
- A change big enough that a player would be confused or surprised without being told. Only add one of these when the developer says so.

One line per item: a plain name as the title, then one sentence saying what it is or what to do. Use the glossary terms (road, world, wardrobe, shop, upgrade, bubble, shard and so on), and keep the tone of the game.

## What never gets its own item

- Balance changes: prices, cooldowns, upgrade steps, spawn rates, scores. For example, Faster bubbles going from 5 to 6 seconds is not an item.
- Bug fixes, crashes and glitches.
- Performance work.
- Moving things around in the UI, restyling, new icons for existing things, new colours.
- Translations, sounds, tests, docs and anything else under the hood.

All of that is what "And the usual" covers: "Balance changes, bug fixes, moar performance and moving stuff around in the UI." It is not in the rewritten list. It lives in `update_notes_ui.xml` and is always the last item, so nobody writes it and nobody forgets it. An update with nothing else to say shows only "And the usual" in Settings and does not pop up.

If a balance change or fix is big enough to deserve its own line, the developer will ask for it. Don't promote one on your own.

## Example

This worktree adds four challenges and the card itself, and also makes Faster bubbles slower, fixes the CLAIM button's height and redraws the Juggernaut icon. The list is:

- `icarus | Icarus | A new challenge for the Toxic cube: die to a green coin while flying the jetpack.`
- `poltergeist | Poltergeist | ...`
- `juggernaut | Juggernaut | ...`
- `full_throttle | Full Throttle | ...`
- `news | In this update | This card! ...`

The bubble nerf, the button fix and the new icon are all "And the usual".
