# Section test bot

Everything stays local. The bot lives in `tools/bot` and `app/src/androidTest`;
it is an Android **test dependency only**, absent from the release application.
There are no Git push, remote upload, purchasing, or publishing commands in the tool.

The bot has two uses:

- **Section reference:** generate real track geometry, search for surviving
  routes at controlled speeds, perturb input timing, and replay successful
  witnesses against the actual game physics and collision code.
- **Live play and demos:** read the current scene on the GL thread, plan on the
  instrumentation thread, and send classic finger flicks through Android's input
  dispatcher, SurfaceView, libGDX event queue, and normal game controls. It values
  coins and powerups but discards predicted fatal paths. It does not teleport,
  clear obstacles, grant immunity, spend stock, or use paid safe-start protection.
  Real collected powerups still work normally. The optional `--magnet` fixture
  starts a 20-second magnet explicitly for lane-preference testing.

The live bot is an online controller with limited lookahead. It can still die;
its survival flag and trace report that outcome instead of silently restarting
or presenting failed runs as successful demos. It uses internal scene state, not
computer vision, so its scores are not measurements of human visual recognition.
It commits short input sequences, measures gesture acknowledgement delay, and
penalizes unnecessary midair slams and lane reversals. Predictions use the current
speed, effect time scale and lane geometry; acceleration, slow-motion transitions, newly spawned
rows and bonus transitions can still invalidate the short forecast. This is a
test/demo controller, not a guarantee of endless survival.

## Commands

From the worktree, with a test phone connected and authorized for adb (root is
only needed for `--kernel` or backing up a non-debuggable install):

```sh
uv run --no-project tools/bot/bot.py verify
uv run --no-project tools/bot/bot.py survey
uv run --no-project tools/bot/bot.py input
uv run --no-project tools/bot/bot.py input --kernel
uv run --no-project tools/bot/bot.py play --seconds 60 --boosts 5
uv run --no-project tools/bot/bot.py play --seconds 60 --boosts 5 --world 2 --record
uv run --no-project tools/bot/bot.py play --section 16 --seconds 60 --record
uv run --no-project tools/bot/bot.py play --boosts 9 --magnet --seconds 60 --record
```

Use `--device SERIAL` with multiple devices and `--output PATH` for a chosen local
result directory. `--no-build` reuses the built APKs. Default outputs live under
`captures/bot/<timestamp>`. Temporary build files stay under `captures/tmp`, never
`/tmp`. Device commands back up shared preferences, restore them in `finally`,
and verify the complete restored directory, removing files created only by tests.
Restore uses a non-PTY adb shell and waits for tar to finish before verification. Keep the saved tar if an external device
or adb failure interrupts restoration. The tested debug APK remains installed.

Recordings use Android `screenrecord`: local MP4, device resolution, no audio.
Recording commands accept 5–165 seconds so the entire test fits within the
recorder's 180-second limit. Only the specific recorder process started by the
tool is stopped. `--allow-death` retains an unsuccessful play/video as a diagnostic
without treating death as a failed command; it does not disable collisions.

Host-only runs can reuse exported fixtures without touching the phone:

```sh
uv run --no-project tools/bot/bot.py survey --no-build \
  --fixtures captures/bot/courses.bin --speeds 27,30,40,52.5 \
  --output captures/bot/new-survey
uv run --no-project tools/bot/bot.py report --survey-dir captures/bot/new-survey/survey \
  --output captures/bot/new-reference
uv run --no-project tools/bot/bot.py replay --no-build \
  --fixtures captures/bot/courses.bin --survey-dir captures/bot/new-survey/survey
```

The report command writes Markdown, CSV, JSON and a ZIP of normal-speed and
highest-solved-speed input witnesses. Each `.actions` file contains one action
per 60 Hz simulation frame: 0 wait, 1 left, 2 right, 3 jump, 4 down. The full trial
CSV records seeds, mirrors, entry lane, animation phase, input profile, speed,
search effort, solved status and jitter survival. An unsuccessful search means
**not solved**, never a proof of impossibility.

## Interpreting skill and speed

The reference is [section-ratings.md](bot-reference/section-ratings.md). It covers
the 53 authored sections, three hill patterns, warm-up and breather. Every fixture
repeats the section twice to include its own exit/entry transition, using the real
lane-walk decoder and its spacing. Both mirrors and all entry lanes are sampled.
Different-section joins, every possible animation phase, and every random seed
are not exhaustively proved.

Ratings describe surviving at speed 30 without relying on coins, pickups, shields
or revives. The live controller separately seeks rewards. The pro reference uses
at least eight 60 Hz frames between gestures (7.5 gestures/s); relaxed uses twelve
(5/s). These are explicit test assumptions, not measured human performance. The
solver has advance knowledge, like a memorized run. Successful plans are centered
within verified timing windows, then individually jittered by ±16.7 or ±50 ms.

The checked-in reference contains **24,012 trials**: 58 patterns × six variants ×
23 speeds × three input profiles. All 1,044 trials at speed 30 passed. To reproduce
the refined speed grid, add this argument to `survey`:

```sh
--speeds 12.4,21.6,27,30,32.5,35,37.5,40,42.5,45,47.5,50,52.5,55,60,65,70,75,80,82.5,85,87.5,90
```

Generation metadata, including the fixture SHA-256, accompanies the reference.
Reports reject incomplete coverage, and replay checks that the fixture hash
matches the survey. A custom grid without 30 reports its skill rating as untested.

Highest all-pass speed is a tested lower bound on capability, not an exact ceiling.
Moving obstacles and frame sampling can make success non-monotonic, so the table
also reports the first unsolved sampled speed. A displayed ≥90 means the search
stopped at 90. The bot cannot establish an unlimited or exact pro-human maximum.

The ordinary ground ceiling is 30. Testing ground traversal at 52.5 is deliberately
stricter than ordinary jetpack play: flight at that speed grants immunity. Pads
need time to lift the cube above a following wall; input throughput cannot remove
that physical constraint. Jump/duck switching and eased lane travel impose other
limits even when every gesture is parsed.

## Motorola input measurements

Test device: rooted Moto G7 Power, 720 × 1520 at 60 Hz, Android 15.

| Route | Requested gestures/s | Sent / recognized | Actual send rate | Maximum gestures in one GL frame |
| --- | ---: | ---: | ---: | ---: |
| Android UiAutomation | 30 | 60 / 60 | 30.05/s | 1 |
| Android UiAutomation | 240 | 480 / 480 | 29.94/s | 1 |
| Rooted touchscreen event path | 60 | 120 / 120 | 60.16/s | 1 |
| Rooted touchscreen event path | 120 | 240 / 240 | 120.14/s | 3 |
| Rooted touchscreen event path | 240 | 480 / 480 | 240.13/s | 6 |

No wrong directions or injection rejections occurred. The normal automation route
saturated around 30 flicks/s, but the rooted event path proved the Android/libGDX
parser can handle **at least 240/s** under rendering load. This is not a measurement
of the physical digitizer's sampling rate, and 240 was the highest tested rate,
not a proven upper bound. A flick comprises down, move, and up touch reports.
The probe renders the environment while clearing collision rows so a crash cannot
interrupt measurement; it is not a dense-obstacle frame-time benchmark. Raw
[automation](bot-reference/motorola-uiautomation.csv) and
[kernel](bot-reference/motorola-kernel.csv) measurements accompany this document.

At 240/s the rooted probe's gesture-start-to-recognition latency was 12 ms median,
21 ms p95. At low probe rates that metric also includes the deliberately longer
finger travel time, so do not compare those latencies as pure dispatcher delay.
Normal asynchronous UiAutomation delivery after warm-up measured roughly 20–26 ms.

Multiple recognized swipes can be consumed in one frame, while player position
advances only once per game update. Intermediate target lanes can therefore be
collapsed before movement occurs. The perfect-input model deliberately permits
one gesture per simulation frame; processing 240 gestures/s does not create 240
independent movement steps per second. A hitch also reduces effective control
opportunities even if the event queue successfully receives every gesture.

`--kernel` is optional and deliberately restricted to the verified Motorola NVT
touchscreen. It injects bounded events into that existing device, automatically
releases its contact, and changes no device settings. The gesture listener consumes
these probe events; they are not forwarded as gameplay, purchases, or navigation.
The helper and wrapper are removed after the command.

## Review page and current controls

The [section review page](section-review.html) contains the 58 authored/generated
patterns, visual previews, bot reference ratings and separate player feedback for
each section/boost combination. Rebuild it with `uv run --no-project
tools/section-review/build.py`. It stores drafts locally, exports JSON, and sends
feedback to the local drop service only when the player clicks Send feedback.

`--boosts` accepts 0–10. Values above five explicitly enable development mode;
the game keeps five arrows and changes them from orange to blue for boosts 6–10.
Normal play still caps opening boosts at five. The live bot now plans toward each
coin's original lane even while a magnet pulls its displayed position toward the
player. A fixture verifies that this produces the expected lane-change gesture.

`RunPerformanceTest` now uses the same real-input controller by default. It
displays **BOT PERFORMANCE TEST · PROTECTED** and counts continuous fatal
collision episodes. A red warning appears whenever test protection prevents a
crash. Ordinary bubble/jet protection still behaves normally. `-e bot false`
selects the explicitly labeled static rendering reference. Protected survival
is not evidence that a section is possible. Bot play,
model parity and section witness replay retain real collision checks.

The published reference survey used 60 Hz simulation. The current renderer can
request 90 Hz and consumes elapsed time in small slices during hitches; the old
survey is still labeled a reference, not a newly measured 90 Hz limit.

## Validation

- The model matched 10,851 real player/obstacle/collision frames on the Motorola,
  including jumps, rolls, slams, platforms, pads and moving obstacles.
- 696 refined normal/high-speed witness routes passed real-game replay: 94,246
  actual physics and collision frames, zero failures. Replay applies normal game
  action callbacks; separate live tests exercise the full Android gesture path.
- The final five-boost live demo survived 60.165 seconds: 296 score, 354 coins,
  97/97 acknowledged gestures and zero jumps ignored while airborne. The trimmed
  local video is `captures/bot/demo-five-boosts.mp4`; its complete trace and summary
  are in `captures/bot/demo-v5`. Earlier development trials failed; this is one
  successful randomized run, not a measured long-run success rate.
- Standalone bot checks cover empty/unreachable searches, held delivery frames,
  coarse action scheduling with full-rate physics, safe optional loot, timing
  centering and binary fixture round trips.
- Release build and Android lint passed; only the existing target-SDK advisory
  remains. No game release or remote branch was changed by this work.

Tested on 2026-09-12 against game revision `3d8a951` in the local `performance`
worktree. The generated trials, fixture binary, replay inputs, device logs and
recordings are retained under `captures/bot`; compact reference tables live in
`docs/bot-reference`.
