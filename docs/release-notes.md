# Release notes

Every release says what changed in three places: the "In this update" card in the app, the F-Droid changelog and the GitHub release. All three are short lists of what a player will notice, not changelogs of every commit.

## Releasing

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Rewrite the in-app notes (below) and set `update_notes_for` to the new `versionName`.
3. Write `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` for F-Droid.
4. Prepare the GitHub release as a draft for tag `v<versionName>` with the notes, then push the tag. The `release` job in `.github/workflows/build.yml` builds the signed APK, attaches it to the draft and publishes it. Without a draft it publishes with GitHub's generated notes (a list of commits), which is not what players should read.

A tag with a dash (`v2.8-beta1`) is published as a prerelease.

## What gets its own line

Something a player can see, try or chase:

- A new achievement or challenge.
- A new cube, trail, bubble skin, world, biome, upgrade or other thing to buy or find.
- A new screen or feature.
- A change big enough that a player would be confused or surprised without being told. Only add one of these when the developer says so.

Never on its own line:

- Balance changes: prices, cooldowns, upgrade steps, spawn rates, scores. Faster bubbles going from 5 to 6 seconds is not a line.
- Bug fixes, crashes and glitches.
- Performance work.
- Moving things around in the UI, restyling, new icons for existing things, new colours.
- Translations, sounds, tests, docs and anything else under the hood.
- The "In this update" card itself.

All of that is "the usual". If a balance change or fix is big enough to deserve its own line, the developer will ask for it. Don't promote one on your own.

Write each line plainly, in the game's tone, with the glossary terms (road, world, wardrobe, shop, upgrade, bubble, shard and so on): a name, and what it is or what to do. An exclamation mark is fine for the big one.

## In the app: "In this update"

The card pops up once on the first launch after an update, and Settings opens it any time. A fresh install never sees it.

- The list lives in `app/src/main/res/values*/update_notes.xml` (English, German, Hebrew), one `icon | title | text` line per item. The icon is an achievement id (its tile is shown) or `news` for anything else.
- Every update starts from nothing: delete the last update's items, then write this one's. Set `update_notes_for` (English file only) to the new `versionName`. Notes written for another version are stale: they never pop up and never show as new. `UpdateNotesTest.theNotesAreWrittenForThisVersion` fails until they are rewritten.
- "And the usual" ("Balance changes, bug fixes, moar performance and moving stuff around in the UI.") is not in that list. It lives in `update_notes_ui.xml` and is always the last item, so nobody writes it and nobody forgets it. An update with nothing else to say shows only that in Settings and does not pop up.
- A worktree keeps the version and rewrites the list to say what that worktree changes. New text makes the card pop up again on the next launch, so testers see what the worktree is about.

## F-Droid: the fastlane changelog

`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, plain text, one line per change, at most 500 characters. Unlike the card, it ends with a line or two for the usual, so F-Droid readers know the release isn't only the new things. Version 2.7 (`17.txt`):

```
New bonus world: Outer Space!
Major visual overhaul of the decorations.
Settings menu!
The Safe start upgrade is now Faster bubbles.
Smoother frame rates, faster restarts from the pause menu and snappier shop and wardrobe.
Bug fixes.
```

## GitHub: the release page

The workflow titles it `Cube Run v<versionName>`. The body is one screenshot of the headline feature, then the same lines as the F-Droid changelog as a bullet list. Use a store screenshot from `fastlane/metadata/android/en-US/images/phoneScreenshots/`, linked by commit so the image never changes:

```
![A donut planet floats beside the glass road in Outer Space](https://raw.githubusercontent.com/Eve-146T/cube-run/<commit>/fastlane/metadata/android/en-US/images/phoneScreenshots/5.png)

- New bonus world: Outer Space!
- Major visual overhaul of the decorations
- ...
- Bug fixes.
```

The alt text describes the picture for someone who can't see it.
