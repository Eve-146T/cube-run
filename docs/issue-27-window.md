# Issue #27: TCL Flip 2 bottom strip

The remaining [report](https://github.com/Eve-146T/cube-run/issues/27) is a
240×320 game window covered by a solid gray strip at y=290..319. The earlier
system-bar inset fix handles split screen, but cannot account for this strip.

## Cause

Inspected the public [stock TCL T408DL Android 11 firmware dump](https://github.com/WolfLink115/tcl_flip2_romdump)
(KEFH), using jadx to inspect its framework.jar:

- [Activity](https://github.com/WolfLink115/tcl_flip2_romdump/blob/master/system/system/framework/framework.jar)
  calls `PhoneWindow.enableMenuBar(true)` in each `setContentView` overload.
- `PhoneWindow.setContentView` adds a proprietary `android.widget.MenuBar` when
  enabled. The flag starts false. TCL also offers `FEATURE_NO_MENUBAR` (15),
  but that feature is absent from the standard Android SDK.
- `DecorView.addMenuBar` attaches it at the bottom with a height of **30 pixels**,
  not dp. It is an app-window decoration, not a navigation-bar inset source.
- TCL's `DecorView.updateColorViews` handles the strip separately from
  `WindowInsets.Type.systemBars()`. Hiding the Android system bars or padding
  for their reported insets therefore cannot reliably remove it.

## Fix

Install the root with `window.setContentView(root)`, avoiding TCL's Activity
hook. This uses the standard Window API and avoids reflection, vendor feature
numbers, and hardcoded display padding. Cube Run's theme is
`Theme.Material.NoActionBar.Fullscreen`; it does not need the Activity setter's
action-bar initialization. Window still attaches the content, dispatches
`onContentChanged`, and requests insets.

## Reproduction and verification

`KeypadWindowProbeActivity` is a debug-only game host that models the relevant
TCL Activity hook by adding a gray 30px decor overlay without system-bar insets.
On the attached Moto G7 Power (Android 15), temporarily using `wm size 240x320`
and `wm density 120` reproduces the reported clipping with the original
Activity content setter. `KeypadWindowTest` fails on that version, identifying
the overlay bounds as `[0,290][240,320]`.

With direct Window installation, the same test checks launch and recreation,
the shared GL/HUD bounds, window focus, and D-pad dispatch. The overlay is absent
and the bottom controls are fully visible. The original `WindowGeometryTest`
continues to test permanent system bars, side navigation, caption bars, and
returning to fullscreen.

Verification passed on the attached phone: 16 tests covering the keypad host,
window geometry, native opening, launch/recreation/restart, and physical
controls; debug build and lint also passed. The keypad and geometry tests also
passed after restoring the native 720×1520 display and density. Before/after
screenshots and test logs are kept locally in `docs/evidence/issue-27/`.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class cube.run.game.KeypadWindowTest,cube.run.game.WindowGeometryTest cube.run.test/androidx.test.runner.AndroidJUnitRunner
```

The reproduction models the firmware hook on another device; the physical TCL
Flip 2 was not available. Reporter confirmation on that phone remains useful.

## Split-screen status bar follow-up

The shared build left an ink-colored strip behind the visible status bar in a
real split-screen pane. An A/B build using the original Activity content setter
showed the same strip. Live measurements also showed DecorView forcing a 56px
top margin and consuming the status-bar inset before the game received it,
despite `decorFitsSystemWindows = false`. The decor inset listener retains
normal platform handling, then transfers that consumed top margin back to the
game's inset handling. The GL surface and native opening extend into the status-bar area
with a negative top margin and `clipToPadding = false`, while the HUD continues
to reserve the reported bar space. Navigation bars and desktop captions retain
their usable viewport boundaries. The margin resets when bars hide again.

`WindowGeometryTest` checks that a visible status bar leaves the scene at y=0
while keeping controls below it, followed by caption/side-bar and fullscreen
transitions. Real split-screen before/after captures are in the local evidence
directory.

## Beta 2 gesture navigation

Gesture navigation reports a bottom system-gesture region without a bottom
tappable-element region. Reserve bottom space for button navigation and
captions, but let the gesture pill overlay the game. If DecorView has already
consumed that navigation inset into a bottom margin, transfer it back to the
game just as for the split-screen status bar. The scene and HUD no longer
shrink when the pill appears during a swipe toward overview.

`WindowGeometryTest` exercises a visible gesture pill with a 24px navigation
inset, verifies the scene and HUD keep their full height, then transitions to
side navigation/captions and back to hidden bars.
