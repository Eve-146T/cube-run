---
name: release
description: Cube Run release checklist. Use when preparing, cutting or tagging a release of Cube Run (a new versionCode/versionName, an F-Droid or GitHub release, "ship it", "make a release", "prepare v2.8"). Covers translations for new content, the performance benchmark on the Moto G7 Power, writing the three sets of release notes, and getting the developer's review before anything is published.
---

# Releasing Cube Run

Work through the steps in order and tick them off in your updates. Nothing is pushed, tagged or published until the developer has approved the notes (step 6). If a step fails, stop and report it with the output; don't skip it silently.

## 1. Know what is in the release

- Find the last release tag: `git describe --tags --abbrev=0` (tags look like `v2.7`).
- List what changed since then: `git log --oneline <tag>..main` and `git diff --stat <tag>..main`.
- Sort the changes into **player-visible** things (new achievements, cubes, bubble skins, trails, worlds, biomes, upgrades, screens) and **the usual** (balance, fixes, performance, UI moves, translations, sounds, tests, docs). The rules for which is which are in `docs/release-notes.md`; read it now.
- Check the open worktrees aren't half-merged: `git worktree list`. Only `main` ships.

## 2. Translations for new content

Every player-facing string ships in English, German and Hebrew (`app/src/main/res/values`, `values-de`, `values-iw`). Read `docs/translations.md`.

- Find strings added since the last release: `git diff <tag>..main -- app/src/main/res/values/` and check each new `name=` exists in `values-de` and `values-iw` too.
- New model labels (ability titles, cosmetic names, achievement names) need a mapping in `ui/LocalizedText.kt` as well as the resources.
- Use the game's existing words for things (look up how a term is already translated before inventing one, e.g. "Bodenstampfer" for a ground pound in German).
- Run `./gradlew :app:assembleDebug :app:lintDebug` and fix any `MissingTranslation` or format-argument errors.
- On a device, run the `cube.run.ui.LanguageTest` instrumentation tests and look at the new screens in German and Hebrew (Hebrew mirrors the menus; check nothing overflows on a 360 × 720 dp screen).

## 3. Performance benchmark

The release must hold a steady frame rate on the Moto G7 Power (serial `ZY323NNKTB`). Read `docs/performance.md` for the method and `docs/action-benchmarks.md` for menu timings.

- The phone is shared with other sessions: check `adb shell dumpsys activity activities | grep topResumedActivity` first and don't disrupt another agent's run.
- Build and run `RunPerformanceTest` (commands in `docs/performance.md`), with `-e seconds 120 -e modes cruise,hills,second-wind,jet,wide,late` for the release run.
- Compare against the last release APK with `tools/performance/compare.py` (same phone, battery level and temperature, no profiling while measuring).
- Report p50/p95/p99 frame interval and frames over 25 ms for each mode, before and after. A regression in p95/p99 or long frames blocks the release until the developer decides.

## 4. Version and tests

- Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
- Run what CI runs: `./gradlew assembleDebug lintDebug testDebugUnitTest assembleDebugAndroidTest`, then the logic instrumentation tests on an emulator (`ANDROID_SERIAL=emulator-… ./gradlew connectedDebugAndroidTest`, so they don't also run on the shared phone). Note which failures also fail on the previous release; don't hide new ones.
- Build the release APK the way the `release` job does (`./gradlew assembleRelease`) to be sure it builds.

## 5. Write the release notes

Three places, all short lists of what a player will notice (`docs/release-notes.md` has the rules and examples):

- **In the app**: rewrite `app/src/main/res/values*/update_notes.xml` from nothing (en, de, iw) and set `update_notes_for` to the new `versionName`. "And the usual" is added automatically; don't write it.
- **F-Droid**: `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, plain text, one line per change, at most 500 characters, ending with a line for the usual.
- **GitHub**: a draft body with one store screenshot of the headline feature (linked by commit) and the F-Droid lines as bullets.

Sane notes: player words from the AGENTS.md glossary, a name and what it is or what to do, no balance numbers, no bug-fix lines, no internal names, no commit-log style. If you're unsure whether something deserves a line, leave it in the usual and ask.

## 6. Developer review

Show the developer, in one message:

- the three sets of notes (in-app in all three languages, F-Droid, GitHub body),
- the benchmark numbers before and after,
- the translation and test results, including anything that failed.

Wait for an explicit OK. Apply their edits and show the changed notes again if they changed anything substantial.

## 7. Publish (only after the OK)

- Commit the version bump and notes on `main`.
- Create the GitHub release as a **draft** for tag `v<versionName>` with the approved body, then push the commit and the tag. The `release` job builds, signs and publishes it. A tag with a dash (`v2.8-beta1`) becomes a prerelease.
- Close the issues the release fixes (for issues only partly fixed, close a sub-issue for the done part instead; leave the parent open).
- Report the release URL and the issues closed.
