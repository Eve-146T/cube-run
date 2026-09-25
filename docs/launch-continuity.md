# Launch continuity: scene snap and stop/start correction

**Follow-up:** [Ghost launch flash](launch-ghost-flash.md) reproduces and fixes
an intermittent missing frame during system-splash removal.

Worktree: `~/Worktrees/cube-run/optimizing-launch-time`, branch
`optimizing-launchtime`. Previous shipped APK: `1dc1185`.

## Reproduced problems

The previous handoff fix was incomplete. Its tests checked button positions
during the fade and cube silhouettes at selected times. They did not check the
whole scene across the exact end of the intro or continuous rotation during
frame preparation.

- **Scene snap:** the opening camera ended at height 5.635, with a different
  viewing angle from the resting menu camera at height 5.5. Projecting the same
  stationary road landmarks immediately before and after completion reproduced
  a **33.41977-pixel jump** on the 1080×2340 emulator.
- **Stop/start rotation:** the clock clamped rotation to the system vector's
  one-second endpoint throughout handoff. The 80 ms system crossfade prolonged
  that hold. An extra 125-degree sweep tied to camera easing also made rotation
  accelerate and decelerate instead of continuing at its normal speed.
- **Stale GL frame:** the first complete scene could precede system-phase
  adoption. Its existence did not mean that it matched the cube being removed.
- **Late completion:** a frame commit arriving after the native intro deadline
  could leave the camera clock permanently at zero. Long system lead-ins also
  accumulated the idle-hop timer behind the cover.

Three new checks failed against the actual previous APK: camera continuity,
constant rotation, and rotation while an expired system animation transfers.
The expanded view-position test passed against that APK: the reproducible final
shift was in the 3D scene. Its observer now continues 450 ms beyond the visible
fade and checks menu/HUD groups as well as buttons.

## Changes

1. Share the resting camera position and target between the intro and normal
   game camera. The ending projection now matches the menu projection.
2. Use the same 40 degrees/second rotation throughout the system, native and GL
   stages. Preserve that angle when normal idle takes over. Reset the idle-hop
   timer during the opening.
3. Remove the timed 80 ms system/native and 50 ms native/GL crossfades. Submit a
   matching native frame before removing the system cover. Keep drawing the
   moving native cube until complete GL frames rendered after phase adoption
   have passed through buffer swap. Clear pending callbacks when disposing.
4. Separate continuous rotation time from the camera deadline. Normal launches
   keep the original 1.57-second native-clock endpoint. A late handoff gets at
   least 600 ms of camera travel, preventing a frozen or abruptly completed shot.
5. Extend the system vector's available motion to three seconds, retaining the
   saved-skin palettes and shared geometry. This does not force a three-second
   wait; the cover transfers as soon as a matching frame is ready. Android's
   duration property reports animation length and does not set splash visibility
   time. [Android splash-screen documentation](https://developer.android.com/develop/ui/views/launch/splash-screen).
6. Schedule shop-card preparation after the opening finishes, with its existing
   900 ms delay, so that work does not interrupt the visible launch.

## Verification and recordings

The dedicated `emulator-5582` uses Android 15/API 35, Pixel 5, 1080×2340,
440 dpi, 60 Hz and host GPU. Lava was equipped through the wardrobe in the
previous pass; its saved palette remains active. World 0 is pinned for visual
comparison. No device animation settings or playback speeds were changed.

The first candidate passed 21 launch, palette, native/GL, lifecycle and shop
tests, plus six gameplay timing/input tests. Three process-cold recordings are
retained under `captures/launch-continuity/candidate/`. One has conspicuously
uneven source-frame delivery; it is retained, not excluded as an outlier.
After fixing the late-frame edge case, the final APK passes **22 affected
emulator tests**. Debug, instrumentation and unsigned release builds pass;
lint reports zero errors and the same two existing warnings. The additional
six gameplay timing/input tests passed on the first candidate, before the
late-frame clock correction.

### Final APK evidence

- [Installable APK](https://apps.muxu.click/d/x36knv3j)
- [Recording 1](../captures/launch-continuity/final/launch-1.mp4),
  [recording 2](../captures/launch-continuity/final/launch-2.mp4),
  [recording 3](../captures/launch-continuity/final/launch-3.mp4)
- [Source filmstrip, run 1](../captures/launch-continuity/final/strip-1/sheet.png)
  and [run 2](../captures/launch-continuity/final/strip-2/sheet.png)

Times below are **visible source frames after the launch request**, not internal
log times. Menu settling uses the existing white-counter pixel tolerance,
confirmed on three consecutive source frames.

| Final recording | First cube | Source frame | Menu counter settled | Source frame |
| --- | ---: | ---: | ---: | ---: |
| 1 | 139.59 ms | 51 | 2,126.642 ms | 137 |
| 2 | 173.61 ms | 54 | 2,065.608 ms | 153 |
| 3 | 133.51 ms | 51 | 2,071.104 ms | 153 |

Source-frame review shows the road settling without the old completion snap and
rotation continuing across renderer transfer. The camera regression requires
less than 0.25 pixels of landmark displacement across completion, compared with
33.41977 pixels in the previous APK. Menu groups and buttons retain their screen
coordinates through the fade and the following 450 ms.

These recordings do **not** establish perfectly even frame delivery: the largest
source-frame gaps between 0.2 and 2.5 seconds are 144.93, 66.39 and 99.32 ms.
They are preserved at their original speed. The removed clock hold is a distinct,
reproduced defect; resolving it does not guarantee 60 FPS on every device.
The previous APK's single recording and the candidate runs are retained for
inspection, but this small, variable emulator sample is not a speedup benchmark.

Frame-commit callbacks prove submission, and a subsequent GL render proves the
preceding swap returned; neither alone proves the display has presented that
frame. Visible timings come from original screenrecord frames, aligned using
their validated device-clock metadata and the launch-request marker. App log
markers describe computation/submission and must not be labelled visible times.

## Size and dependencies

The longer vector costs additional resource bytes. No dependency was added or
removed in this correction. Existing opportunities remain release code shrinking
and ABI-specific distribution; the bot module is used by the live idle pilot.
See [the earlier size analysis](launch-animation.md#apk-size-and-dependencies).

Unsigned release: **9,138,147 bytes**, up **253,784 bytes** from the previous
8,884,363-byte release and 185,149 bytes above the original launch-optimization
baseline. The shared debug APK is **11,670,900 bytes**. The release increase is
the cost of the longer generated geometry/palette animation, not a size saving.
