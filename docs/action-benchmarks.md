# User action benchmarks

`UserActionBenchmarkTest` and `tools/performance/actions.py` are maintained
benchmarks for the real Android host, menus and game surface. They use the
visible controls and inject a road tap to launch a run. They can measure older
APKs that recreate the host on restart as well as builds that retain it.

The default sequence measures shop, wardrobe, achievements, settings, languages,
the debug section explorer, their Back actions, run launch, pause, resume,
pause-menu restart and pause-menu return to the main menu. Achievements are
temporarily made available so new save files exercise that menu too.

## Run

Build the application and instrumentation, then select an attached device:

```sh
mkdir -p .build-tmp/java
export TMPDIR="$PWD/.build-tmp/java"
export UV_CACHE_DIR="$PWD/.build-tmp/uv-cache"
TMPDIR="$PWD/.build-tmp/java" ./gradlew \
  -Djava.io.tmpdir="$PWD/.build-tmp/java" \
  :app:assembleDebug :app:assembleDebugAndroidTest
uv run tools/performance/actions.py --serial 35291JEHN20033 \
  --repeats 5 --cold-starts 3 --out .build-tmp/actions/pixel
```

Both APKs install by default. Use `--apk` and `--test-apk` to select snapshots,
or `--skip-install` for an already installed pair. Both installed APK hashes
are recorded. `--cold-starts 0` skips cold launches; it does not turn warm
activity launches into cold-start samples. The output directory must be new.

The runner backs up and restores preferences, and verifies the restored file
contents. It changes no clock or display controls. It leaves the app stopped
after the run. Raw artifacts and preference backups belong in ignored local
directories, not source control.

## Read the measurements

Each action records three separate times from dispatch on the main thread:

- `first_ui_frame_ms`: first committed Android response frame. If a restart
  destroys the old window before it commits, the replacement window is used
  and `first_ui_frame_window` records that choice.
- `controls_settled_ms`: the target controls are visible with their entrance
  opacity, scale and position settled. Shop readiness also includes its stage
  transition; achievements include their asynchronous content preparation.
  Sheets include their inner card, with opacity ≥ 0.99, scale within 0.001 of
  1 and translation within 0.5 px on each axis.
- `ready_ms`: those conditions plus a complete fresh GL scene and a subsequent
  committed Android frame. The conservative GL check waits past a possibly
  in-flight frame, adding up to a few frame intervals. It measures submitted
  frames, not SurfaceFlinger's exact physical presentation time.

On Android versions before 29, UI timing uses animation callbacks rather than
frame-commit callbacks; `ui_frame_metric` distinguishes that fallback. Menu
idle effects continue; they are not mistaken for entrance animations.

The first visit and repeat visits are summarized separately. `results.json`
retains every sample, median/p95/min/max, device and APK identity, and cold
startup milestones. It records the app's active/requested refresh rate, sound,
haptics, start-speed preset and Zen state. `summary.md` contains readable tables. First visits can
already be preloaded by the app; they are not labeled uncached.

Cold launch samples force-stop the process and start its normal opening. They
record Android's activity-display timing and the app's `CUBE_START` milestones
for scene reveal and opening completion. App milestones start at Activity
creation, excluding earlier process startup. Host-observed opening time also
includes adb and polling overhead. These are separate clocks and endpoints;
do not compare them as interchangeable definitions of startup latency. The
raw `am start -W` output and launch logs are retained.

To compare builds on the same device:

```sh
uv run tools/performance/actions.py --serial 35291JEHN20033 \
  --apk path/to/candidate.apk --test-apk path/to/test.apk \
  --out .build-tmp/actions/candidate \
  --baseline .build-tmp/actions/baseline/results.json
```

The runner rejects incomplete baselines and reports from a different device.
Keep display mode, settings, sound, thermal conditions and instrumentation
consistent. The report includes first-use/repeat median changes; a p95 from
four repeat samples is descriptive, not a sustained performance guarantee.
Failures produce partial results and an instrumentation log and exit with an
error instead of reporting an incomplete sequence as successful.

## Pause restart regression

`PauseRestartTimingTest` checks the optimized behavior separately from the
benchmark: the Activity, GL surface and renderer allocations are retained;
score, flying, powerups and slow motion reset; rapid duplicate restart/input
does not start extra transitions; the pause and available boost controls return
fully visible. Old HUD state is cleared before the new run publishes its state.
`MenuReturnTimingTest` also exercises restarting followed immediately by
pause → MENU, including pending menu animations, and results → MENU.

Pause restart resets on the GL thread and retains the card until the next
complete scene swap. The new HUD appears without the old run's delayed
entrance animations. Leaving the foreground during that handoff pauses the
new run. Results-screen restart continues to use its existing launch path.
Closed shop cards are retained when progress is unchanged, so the shorter
restart does not make the next shop visit pay for rebuilding them.

## Recorded comparison: 2026-10-10

Baseline: `9c871ed`, before the pause-restart changes. Candidate: the pause
restart and shop reuse changes documented above. Each device used the same
instrumentation APK and identical saved preferences for both builds. Five
sequences produced 100 action samples per build/device, with the first visit
separated from four repeats. Three normal cold launches were also recorded.
Pixel 7a ran at 90 Hz; Moto G7 Power ran at 60 Hz.

Repeat-visit `ready_ms` medians (including the conservative frame barriers):

| Device | Action | Before | After | After p95 |
| --- | --- | ---: | ---: | ---: |
| Pixel 7a | Pause restart | 1,117.3 ms | 54.9 ms | 55.1 ms |
| Moto G7 Power | Pause restart | 1,299.0 ms | 108.6 ms | 116.7 ms |
| Pixel 7a | Open shop | 376.3 ms | 375.4 ms | 376.9 ms |
| Moto G7 Power | Open shop | 394.6 ms | 397.7 ms | 400.5 ms |
| Pixel 7a | Launch run | 585.8 ms | 585.8 ms | 593.8 ms |
| Moto G7 Power | Launch run | 617.3 ms | 614.3 ms | 617.2 ms |
| Pixel 7a | Pause → menu | 57.2 ms | 51.9 ms | 58.4 ms |
| Moto G7 Power | Pause → menu | 68.6 ms | 69.2 ms | 83.2 ms |

Pause restart improved by about 95% on Pixel and 92% on Moto. Shop and other
transitions remain near their previous timings. Cold startup was measured,
but this change does not optimize it: median first-scene milestones were
815 → 803 ms on Pixel and 1,949 → 1,968 ms on Moto, measured from Activity
creation. These small sample sets describe these runs, not every device/load.

Installed APK SHA-256 values:

```text
baseline: dfb292dabbeb1da835c4fe6b0949136567c4b98bdc68594222ace288209d8154
candidate: be3ae10fcf40d52be464c195ee063390e9064ae402f0e7e16d98e973f35ee26b
instrumentation: 26bb713d3f9f749e733ccd3018e2624b4264659b3992af0aa247607fd9443611
```

Local raw reports are in `.build-tmp/restart-review/{pixel,moto}-final-{before,after}/`.
Five functional restart/menu tests passed on each device. Debug and release
builds and debug lint passed. Visual recordings are kept separately from the
timed runs under `.build-tmp/restart-review/*-final-actions-visual.mp4`.
The Pixel recording covers the final transitions. Motorola's final recorder
stopped during startup; direct pause/restart screenshots (`moto-final-paused.png`
and `moto-final-restarted.png`) verify its final HUD and boost control alongside
the functional tests and the earlier full recording.
