# Physical controls and Redmi startup investigation

Investigated 2026-09-24, based on `origin/main` at `57b6bf3`.

## Issue #23: physical keys and controllers

[Issue](https://github.com/Eve-146T/cube-run/issues/23)

The Android activity now accepts arrow keys/WASD, D-pad keys, joystick hat axes
and the left stick. Directions use the existing discrete lane/jump/duck actions
on the GL thread. Enter/Space/D-pad center/controller A performs the existing
tap action, including double-press shields. P/Start pauses or resumes,
Escape/controller B navigates back or pauses, and keyboard B/controller X
activates the visible headstart button.

Menu navigation stays inside the active page or pause card. A focus outline
marks the selected control, confirm activates it, and results/box screens use
their existing click handlers. Touch clears the hardware selection. Key repeats
are consumed without additional actions; joystick dead zones and hysteresis
require another excursion before repeating an action. Input state resets when
the activity pauses or loses window focus. Unmapped system keys retain Android
behavior.

The decoder follows Android's distinction between controller
[key events and joystick motion events](https://developer.android.com/games/sdk/game-controller/controller-input).
No controller library or new Android permissions are required.

### Verification

- Debug APK and instrumentation APK build successfully; Android lint reports
  zero errors and two existing warnings (`OldTargetApi`, `UnusedAttribute`).
- On the Android 15 emulator, all 11 initial physical-input, touch-input and
  jump-physics tests passed. Injected Android key events also selected the Start
  control and opened the shop without touch; screenshots were inspected.
- On the dedicated Android 14 emulator, the expanded suite passed 11 of 13
  cases. The remaining two passed on targeted rerun after correcting test
  setup: wait for autostart/HUD readiness, keep the slam fixture airborne until
  its key arrives, and initialize renderer resources before the existing jump
  test emits particles. All 13 cases are therefore verified, including results,
  mystery-box confirmation and the five-tap headstart limit. Logs are in
  `captures/final-physical-tests.txt` and `captures/slow-device-retests.txt`;
  the latter reports `OK (2 tests)` in 14.219 seconds.
- The existing startup renderer suite initially had one GL callback timeout
  coinciding with another app taking the shared emulator's foreground. That
  test passed separately on rerun (2.275 seconds); the other two passed in the
  original run. No Cube Run fatal exception was present in that capture.
- These are injected keyboard/controller events, not a test with a paired
  physical controller. The attached Moto phone reported ADB `unauthorized`
  before additional hardware testing could begin.

## Issue #22: crash after the introductory cube

[Issue and screenshot](https://github.com/Eve-146T/cube-run/issues/22)

### What the report establishes

The screenshot reports model **22101316UG**, **Dimensity 1080**, **8 GB RAM +
4 GB memory extension**, **Android 13 TP1A.220624.014**, **MIUI Global
14.0.12 / 14.0.12.0(TMOEUXM)** and the **2023-12-01** security patch. MediaTek
identifies the Dimensity 1080 GPU as
[Arm Mali-G68](https://i.mediatek.com/dimensity-1080).

The report does not specify the Cube Run version, installation source, whether
it is a fresh installation, or a Java/native crash trace. The only comment asks
for logs; none were attached when this investigation ran. v2.4 was the latest
GitHub release when the issue was opened. Its downloaded APK matches the
published SHA-256:

`b1e4d6c19e19eb0c5f3ddab8f00c5ec8342f10fb7a4417e7513d7b676f41fa55`

The renderer, intro implementation and `Gdx3DGame` are unchanged between the
v2.4 tag and this worktree's base. The issue cannot be attributed to either of
the two subsequently merged Safe Start/Magnet changes from the evidence given.

### Reproduction attempt

The exact published v2.4 APK completed **three out of three cold launches** on
a fresh, dedicated Android 14 / API 34 x86_64 emulator using SwiftShader GLES3.
Each launch reached the game, retained its process after eight seconds, and
kept `GameActivity` resumed. There was no Cube Run fatal exception or native
signal in the captured logs. Exit records contained only the intentional force
stops between trials. This exercises the same API 33–34 compositor-callback
branch used by the reported Android 13 device, but does **not** reproduce MIUI
or the Mali driver. No matching Redmi hardware was available.

The fresh emulator showed unrelated Bluetooth crash prompts and Google service
and System UI ANRs under host resource pressure. These are identified by their
own process names in the logs; they are not Cube Run reproductions. Startup timings from
this run should not be used as a performance benchmark.

Local evidence is in `captures/release-startup/`: `device.json`, `results.json`,
three logcat captures/screenshots, and `exit-info.txt`. The release SHA-256 was
verified before installation. The investigation remains **unconfirmed** until
the affected Redmi supplies its first fatal stack/exit reason.

### Code findings and next discriminating checks

1. **Seeing the cube does not establish that GL initialized.** Android's splash
   and `NativeCubeView` can draw the animation while the GL host starts. The
   symptom alone cannot distinguish EGL setup, shader construction or splash
   handoff failures.
2. **An ES3-capable device does not get a fallback if an instanced shader fails.**
   `WorldBoxBatch` and `ShardSystem` choose instanced renderers whenever
   `Gdx.gl30 != null`. Their constructors throw on compilation/link failure.
   `BoxMeshKit` and `BubbleRenderer` also throw on shader failure. The batch and
   bubble setup runs during the opening, making this a plausible failure path,
   but there is no Mali error log proving it happened. Look for `instanced box
   shader`, `instanced shard shader`, `box shader`, `bubble shader`, or a native
   GPU-driver stack. A GLES2-versus-GLES3 build comparison on the affected phone
   would discriminate this path; an emulator cannot validate Mali behavior.
3. **The splash handoff also merits checking.** `OpeningSplashHandoff` hides the
   copied splash icon surface, adds a replacement view, submits a compositor
   transaction and removes the splash after its callback. Android 13–14 uses
   `addTransactionCommittedListener`; Android 15+ uses
   `addTransactionCompletedListener`. The SDK API database confirms their
   introduction at API 33 and 35 respectively, and the code guards them
   correctly. There is no established unguarded-new-API bug here. A stack in
   `SplashScreenView`, `SurfaceControl` or this class would justify comparing a
   build without the custom handoff on MIUI.
4. **Do not treat varying precision as a confirmed shader defect.** The vertex
   and fragment defaults differ in several shaders, but both the
   [GLSL ES 1.00 specification](https://www.khronos.org/files/opengles_shading_language.pdf)
   and the [GLSL ES 3.00 specification](https://registry.khronos.org/OpenGL/specs/es/3.0/GLSL_ES_Specification_3.00.pdf)
   permit differing varying/input-output precision. Changing that alone is not
   an evidence-based fix for this report.
5. **Other startup threads remain possible.** The `sfx-load` thread creates a
   SoundPool and writes synthesized WAV files without an exception boundary.
   An I/O/native audio failure can terminate the app while the animation is
   visible. Available RAM in the screenshot does not diagnose or rule out a
   process-specific allocation failure either.

The most useful next evidence is the complete first failure, including any
`Caused by` chain or native tombstone, plus the installed version/source. For an
attached affected device, start logcat before launching so a short-lived PID
does not lose the crash:

```sh
adb -s SERIAL logcat -T 1 -b main -b system -b crash -v threadtime > redmi-startup.log
# In another terminal:
adb -s SERIAL shell am force-stop cube.run
adb -s SERIAL shell am start -W -n cube.run/.GameActivity
adb -s SERIAL shell dumpsys activity exit-info cube.run > redmi-exit-info.txt
adb -s SERIAL shell dumpsys package cube.run > redmi-package.txt
```

Debug builds additionally emit `CUBE_START` markers (`gl create`, `box kit`,
`cube ready`, `batches ready`, `game ready`, `system splash removed`,
`scene revealed`, `hud ready`). They narrow the failing phase. The published
release disables these markers. No speculative renderer/splash change is
included with the physical-control fix.
