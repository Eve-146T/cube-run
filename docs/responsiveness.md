# Responsiveness on the Motorola

This pass investigates [the 2.0 responsiveness report](https://github.com/Eve-146T/cube-run/issues/3) and [issue 5](https://github.com/Eve-146T/cube-run/issues/5). The performance work is recorded separately in [performance.md](performance.md).

## Method

Measured on the rooted Moto G7 Power, Android 15, 720 × 1520 at 60 Hz. USB connected, battery 100%, normal schedutil CPU policy with both clusters capped at 1,804,800 kHz. Battery temperature is recorded before and after each run. These are reproducible debug builds with sound and haptics enabled, not measurements of downloaded store APKs.

| Version | Source |
| --- | --- |
| 1.4 | tag `v1.4`, `5b5890c` |
| 2.0 | tag `v2.0`, `2cc97bd` |
| 2.1 | tag `v2.1`, `11e3539` |
| 2.2 performance candidate | `32a5329`, unreleased |

The same instrumentation wraps the input processor, Android touch listener and render callback of each historical APK; production input and movement code remain untouched. A bounded native helper writes DOWN, MOVE and UP events to the Motorola's verified NVT touchscreen event device. It records CLOCK_MONOTONIC immediately before each write. UI delivery, GL delivery, accepted actions and completed render callbacks use the same monotonic clock.

Each condition has 48 gestures, alternating left, right, jump and down, with 1,007 ms between contacts. This spacing sweeps across display phases. A five-second warmup and four unrecorded gestures prime the paths. An 8 ms flick travels 20% of screen width in one move; a 120 ms drag covers the same distance in 15 moves. Cruise pins difficulty at zero (10 world units/s), fast pins it at one (30 units/s). Version order changes between conditions. World generation remains native and random; this is the same test protocol, not pixel-identical scenery across versions.

For this isolated latency probe, rows are removed as they enter the collision zone, so the same sequence can run without dying. Far geometry, effects, movement, sound and haptics remain active. The separate performance tests use the playing bot and explicitly count protected collisions.

“Render” below means the return of the first render callback containing detectable movement. It includes GL command submission and any driver blocking within that callback. It **does not measure display presentation, scanout, physical digitizer latency or touch-to-photon latency**. The phone's actual touchscreen scan rate cannot be inferred from injected events.

## Historical measurements

Kernel DOWN to the first render callback containing movement, **median / p95 in ms**. Each cell contains 48 gestures (12 per direction).

| Version | 8 ms flick, speed 10 | 120 ms drag, speed 10 | 8 ms flick, speed 30 |
| --- | ---: | ---: | ---: |
| 1.4 | 40.9 / 48.2 | 94.3 / 101.8 | 41.2 / 49.2 |
| 2.0 | 40.8 / 48.3 | 94.7 / 102.3 | 40.4 / 48.5 |
| 2.1 | 41.0 / 48.7 | 95.0 / 102.6 | 41.1 / 48.1 |
| 2.2 performance candidate | 40.5 / 48.1 | 93.2 / 100.3 | 41.8 / 49.5 |

All **576/576** historical actions registered and produced movement. These measurements do not reproduce a general 2.0 input-latency regression on this phone. They do not establish that the report was never real: other phones, frame stalls, real finger trajectories and difficult platform situations are not fully represented. The 1.4 input adapter and 2.0/2.1 TouchInput all used the same 8.5% threshold and lane-easing coefficient 13.

## After the responsiveness fixes

| Condition | 2.2 before, median / p95 ms | 2.2 after, median / p95 ms | Median improvement |
| --- | ---: | ---: | ---: |
| 8 ms flick, speed 10 | 40.5 / 48.1 | 33.3 / 40.3 | 7.3 ms |
| 120 ms drag, speed 10 | 93.2 / 100.3 | 66.0 / 72.9 | 27.2 ms |
| 8 ms flick, speed 30 | 41.8 / 49.5 | 31.6 / 38.9 | 10.2 ms |

All **144/144** improved-build actions registered, bringing the comparison to **720/720**. Median first-MOVE kernel-to-GL delivery at speed 10 fell from 16.8 to 9.6 ms for the short flick. Since that single MOVE exceeds both old and new thresholds, this condition isolates the delivery benefit from the smaller threshold. Longer drags benefit from both changes. These are samples from one phone, with 48 gestures per condition, not a universal latency guarantee.

Rendering cost stayed similar: GL-thread CPU p95 was 5.68 → 5.71 ms for short cruise flicks and 6.41 → 6.37 ms at speed 30. The 90%-complete lane transition still takes roughly 167 ms after action acceptance. The improvement is earlier response, not faster lane animation.

The actual Player platform probe confirms a separate issue: **0/12** post-edge jumps were accepted in each of 2.0, 2.1 and the old 2.2 candidate, including an UP immediately after walking off. The improved build accepts all **9/9** sampled delays inside 100 ms at simulated 60/90 Hz and rejects all three sampled delays beyond it. A swipe 60 ms before landing also now produces a jump on the landing update; all three previous 2.x builds dropped it. Version 1.4 has no platforms, so that fixture is not applicable.

The supported conclusion is **general regression not reproduced here; platform-edge input rejection confirmed and fixed; latest input latency measurably improved**. It would be wrong to conclude that the original player's experience was imaginary or that all device-specific responsiveness problems are solved.

See [metrics](responsiveness-reference/metrics.json), [joined input timestamps](responsiveness-reference/trials.csv) and [jump cases](responsiveness-reference/jumps.csv). APK SHA-256 values are included in the metrics. Timings were taken before the later test-only replay and diagnostic-counter adjustments; production code is identical.

## Validation and final performance check

Production fixes are committed as `ed1c48b`; the initial probe is `8c6628f` and the performance pass is `32a5329`. These Cube Run branches have not been pushed or tagged.

**51 regression tests passed on the Motorola**, including gesture cancellation/release handling, real jump windows, timing, rendering, collision and bot-model parity. All **696 previous section witnesses passed**, covering **94,246 real physics/collision frames**. This preserves those successful routes; it does not rerun the full difficulty survey with the new grace windows. Release assembly, bot self-tests and lint passed (zero lint errors, one existing target-SDK warning).

The final playing-bot performance test ran three 20-second phases, each with five seconds of warmup. No frame exceeded 50 ms. The bot issues Android gestures and reports protected collisions rather than silently phasing through hazards.

| Scene | Measured frames | GL-thread CPU median / p95 ms | Frame p95 / p99 / max ms | Protected hits |
| --- | ---: | ---: | ---: | ---: |
| Five boosts | 899 | 6.34 / 9.02 | 18.99 / 20.65 / 25.49 | 2 |
| Late run | 898 | 5.09 / 7.20 | 18.70 / 21.36 / 25.68 | 0 |
| Dense Second Wind | 899 | 6.46 / 8.89 | 19.13 / 20.58 / 24.06 | 0 |

Ten Second Wind clears took 4.20 ms median and 4.40 ms maximum. The five-boost and late-run phases acknowledged all 20 and 27 gestures respectively; no jump was dropped. The Second Wind fixture required no steering during this sample because the repeated clears removed the hazards and collected their coins. Protected tests are workload measurements, not evidence that every bot decision is safe. Earlier Pixel 90 Hz and verified underclocked Motorola measurements are in the performance report; the Pixel was disconnected before this responsiveness comparison.

## Changes

- Classic flick threshold: 8.5% → 5.5% of screen width (61.2 → 39.6 px on this Motorola). Tap tolerance remains 3% (21.6 px).
- A release position can complete a flick when there was no intervening MOVE sample. Cancelled contacts and secondary fingers cannot manufacture an action; each classic contact still fires at most once.
- Classic controls request unbuffered Android motion delivery. Smooth positional controls retain batching and their existing sensitivity. Android documents the delivery mechanism in [its input guidance](https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/advanced-stylus-features); the device comparison determines its usefulness here.
- Walking off an edge grants 100 ms of jump grace. An UP up to 100 ms before landing is remembered for that landing. These are simulation-time windows. A normal jump or bounce pad grants no extra mid-air jump. DOWN, flight, hover, a save and pause clear pending jump input.
- Bot snapshots, simulation and planner state include both windows. Android parity tests compare them with the real Player, alongside position, airborne state, ducking and collisions.

Lane easing, jump launch speed and gravity are unchanged. The intent is to begin an intended action sooner and avoid dropping near-edge inputs.

## Reproduce

Build the historical tag APKs in detached worktrees and retain copies. Build the current debug app and test APK. `probe.py` restores the current debug APK and the exact pre-test preference files after each historical run. The native injector is restricted to the verified Motorola touchscreen and removes its deployed helper afterward.

```sh
uv run --no-project --python /usr/bin/python3 tools/responsiveness/probe.py \
  --apk captures/responsiveness/apks/2.0.apk \
  --output captures/responsiveness/example --count 48 --duration 120 --no-build
uv run --no-project --python /usr/bin/python3 tools/responsiveness/analyze.py \
  captures/responsiveness/example
```

Use `--fast` for the speed-30 condition, or `--jumps-only` to record actual Player acceptance around platform edges and landings (2.0 onward; 1.4 has no platforms). Set `JAVA_HOME`, `TMPDIR` and `UV_CACHE_DIR` to the local JDK/workspace paths when needed. Raw traces remain under ignored `captures/responsiveness`; published artifacts exclude game preferences.
