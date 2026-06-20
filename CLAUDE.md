# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Cube Run is a single-activity Android game: a minimalist 3-lane 3D endless runner built with **libGDX** and **Kotlin**. It is a privacy-respecting F-Droid-style app — only the `VIBRATE` permission, no network, no analytics, GPL v3.

## Build & run

Requires JDK 17, Android SDK platform 35, build-tools 35.0.0. The SDK location comes from `local.properties` (`sdk.dir=...`) or `$ANDROID_HOME`.

```bash
./gradlew assembleDebug     # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease   # signed if keystore present, else unsigned (see signing)
./gradlew lint              # Android lint (the only static check; ObsoleteSdkInt is disabled)
./gradlew installDebug      # install to a connected device/emulator
```

There are **no unit tests** — `app/src/test` / `androidTest` do not exist. CI (`.github/workflows/build.yml`) builds a debug APK on branches/PRs and a signed release APK + GitHub Release on `v*` tags. `fastlane debug` / `fastlane release` are local conveniences wrapping the gradle tasks.

**Signing:** the release build reads `keystore.properties` (gitignored, local) or the `KEYSTORE_FILE`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` env vars (CI). With neither, the release APK builds **unsigned** rather than failing.

**Native libs:** the libGDX `.so` files are *not* checked in. The `copyAndroidNatives` gradle task extracts them from the `gdx-platform` artifacts into `app/libs/` (gitignored) before the JNI-merge step. A clean checkout therefore has no `libs/` until the first build.

## Architecture

The package root is `cube.run` (under `app/src/main/kotlin/`).

**Startup chain:** `App` (Application) initializes the four singletons — order matters, all are `init(context)`-style objects: `Scores`, `Settings`, `Haptics`, `SoundFx`. → `GameActivity` (a libGDX `AndroidApplication`) builds a `FrameLayout` stacking the libGDX GL surface under a `GameChromeView` HUD, then runs `CubeRun`.

**Two-thread model — this is the central design constraint.** The game (`Gdx3DGame`/`CubeRun`) runs entirely on the **GL thread**. The HUD (`GameChromeView`, plain programmatic Android views) lives on the **UI thread**. They never touch each other directly: everything crosses through the `GameSession` interface, whose implementation `GameHostSession` marshals calls onto the UI thread (`activity.runOnUiThread`) and holds score/over-state in atomics. Singletons read by the GL thread (`Settings`, `Scores`) expose `@Volatile` fields. When editing, keep this boundary: game code calls `session.addScore/banner/gameOver`, never `chrome.*`.

**Engine vs. game split:**
- `core/Gdx3DGame` — reusable abstract base for any 3D game. Owns the `PerspectiveCamera`, lit `Environment`, `ModelBatch`, gradient sky, the input pipeline (tap/drag/swipe → `onTap`/`onDown`/`onDrag`/`onSwipe` callbacks on the GL thread), screen "juice" (`shake`, `flash`, `burst3d` cube-shard particles), and auto-disposed model factories (`box`/`sphere`/`cylinder`/`cone`, `gdxHsv`). Subclasses implement `init` / `tick(dt)` / `renderWorld`.
- `game/CubeRun` — the entire game, subclassing `Gdx3DGame`. All gameplay (lane-walk track generation, obstacle types, player physics, scoring, difficulty) lives here.

**Other core pieces:** `SoundFx` synthesizes all sound effects procedurally as PCM at startup (background thread), caches them as `.wav` in `cacheDir`, and plays via `SoundPool` — `play()` is a silent no-op until every sample finishes decoding. `Haptics` wraps `VibrationEffect`. `Palette` is an Android-side ARGB color helper (distinct from `Gdx3DGame.gdxHsv`, which returns libGDX `Color`).

## CubeRun gameplay model (read before touching `game/CubeRun.kt`)

- **The track is a guaranteed-solvable "lane-walk":** a continuous stream of rows where the safe lane (`curSafe`) moves by at most one between consecutive rows. Hand-authored patterns (`Sect`, e.g. SLALOM, GAUNTLET) are step-code arrays decoded by `spawnStep`; the `pickSection` director stitches them with **tier-gating** (harder sections unlock as difficulty rises) and random **mirroring**. Spacing/reachability is enforced by the walk, so sections are pure shapes.
- **Step codes** (see the `dg`/`ft`/`sld`/`JP`/`DK`/`EM` helpers): dodge / feint / slider / jump-wall / duck-bar / empty. Obstacle types are `DODGE`/`JUMP`/`DUCK`; collision is resolved per-row in `tick` against the cube's lateral gap, bottom, and (roll-tucked) head height.
- **Difficulty is one axis:** `diff` in 0..1 drives both speed (`exploreMinSpd`..`exploreMaxSpd`) and unlocked tier. It auto-ramps over real time (`rampSeconds`) up to a plateau (`autoDiffCap`) — runs end on focus, not impossible reflexes.
- **`DEBUG_DIFF_SLIDER` is a temporary tuning widget** (bottom-screen drag bar, drawn in `renderHud`). It can push `diff` past the auto cap. Set the flag to `false` / delete the slider block to ship.
- **Control schemes:** classic single-flick swipe vs. `Settings.smoothControl` (positional steering — finger position maps to lane continuously within one touch, vertical flicks jump/duck). The mode is chosen via the pre-run options in `GameChromeView`.

### Performance & lifecycle gotchas

- `tick()` is a per-frame hot path: it is written allocation-free. `setHsv` writes into an existing `Color`, a single `tmp` `Vector3` is reused, and one shared `unit` 1×1×1 box `Model` is rendered many times via per-`ModelInstance` transforms/colors. Preserve this — don't allocate per frame in the loop.
- After `gameOver()` the render loop keeps running for the death animation. Guard all gameplay mutation with `session.isOver` / the local `dead` flag.
- **RESTART finishes and relaunches the activity** (`finish()` + `startActivity`), it does **not** call `recreate()`. libGDX only disposes native GL/mesh resources when the activity truly finishes; `recreate()` would leak them. Keep this if you touch the game-over flow in `GameChromeView`.
