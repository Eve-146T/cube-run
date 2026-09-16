# Ghost launch: missing frame during splash removal

Follow-up to `338e42d`, in the `optimizing-launchtime` worktree.

**Result on Android 15:** 12 recorded Ghost cold launches without a missing cube
frame or excessive brightness; all 23 affected instrumentation tests pass.
[Installable APK](https://apps.muxu.click/d/3gaw8f49).

**User verification, 2026-09-16:** the user reports that this APK is very smooth
and that the earlier flash report no longer applies. The subsequent unverified
compatibility experiment was set aside; the verified implementation remains
`d9c1e1f`. No replacement APK was published.

## Reproduction

Purchased and equipped **Ghost (skin 13)** through the wardrobe on the dedicated
Android 15 emulator. The developer bank was used for the purchase; it resets on
process restart. No phone or shared emulator was used.

The first recording did not contain a blank frame. Three further recordings
caught the intermittent defect: in `before-extra/launch-3.mp4`, **source frame 32,
662.798 ms after the launch request, contains zero cube pixels**. Frames 31 and
33 contain the Ghost cube. [The full-resolution blank frame](launch-ghost-reference/before-blank.png)
confirms that this is an actual missing image.

This occurs during the system-to-app splash transfer, before native-to-GL
handoff. Android renders its animated icon in a separate surface. Removing the
splash releases that icon's host independently of the background window buffer.
[AOSP SplashScreenView implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/window/SplashScreenView.java).

## Experiments, including failures

Each candidate below has 12 process-cold recordings. All are retained under
`captures/launch-ghost/`; no failed run was removed from its batch.

| Candidate | Folder | Launches with a blank cube frame |
| --- | --- | ---: |
| Transparent cover, wait for a view frame commit after exit callback | `after/` | 3/12 |
| Prepare transparency before the copied splash's first draw | `transfer/` | 1/12 |
| Live Canvas replacement, copied icon surface behind the window | `cover/` | 3/12 |
| Detach copied icon before transfer; wait for view frame commit | `detach/` | 1/12 |
| Hide copied icon before transfer; wait for compositor commit | `compositor/` | 1/12 |
| Wait for compositor **presentation** on Android 15 | `presented/` | **0/12** |

Waiting inside the exit callback alone was too late for some transfer frames.
Changing surface alpha or layer order could affect the icon independently of
its replacement buffer. The later failures also established that a submitted
or committed frame was not a sufficient release barrier on this emulator.
For example, `compositor/` run 9 loses frame 32 at 653.080 ms, approximately 7 ms
after the splash-removal call.

## Final implementation

1. Observe pre-draw to find Android's public `SplashScreenView` as it joins the
   decor, before its first draw. Adopt the system's reported animation phase.
2. Mark the copied icon view GONE before transfer, removing its destination
   surface. Draw a live native cube inside the copied splash's background buffer,
   using the same clock, saved appearance, opacity and geometry.
3. On Android 15+, attach a transaction-completed listener to that draw and wait
   for **presentation**, plus Android's exit callback, before removing the splash
   and releasing its original icon host.
4. Continue the existing native-to-GL handoff using fresh scene frames. The cube
   keeps rotating throughout; no timed fade, paused clock, static replacement or
   extra animation duration was introduced.

The observer is removed after preparation, intro completion or Activity
destruction. Removal checks Activity destruction before scheduling a GL callback.
Only public Android APIs are used.

Android adds the copied view and transfers its surface at pre-draw, before
reporting the exit callback.
[AOSP ActivityThread transfer implementation](https://android.googlesource.com/platform/frameworks/base/+/281a76bf08f1cbb7368579f12314693a0ff6d49e/core/java/android/app/ActivityThread.java).
The replacement buffer and callback transaction are submitted together through
[AttachedSurfaceControl.applyTransactionOnDraw](https://developer.android.com/reference/android/view/AttachedSurfaceControl#applyTransactionOnDraw(android.view.SurfaceControl.Transaction)).
[TransactionCompletedListener](https://developer.android.com/reference/android/view/SurfaceControl.Transaction#addTransactionCompletedListener(java.util.concurrent.Executor,%20java.util.function.Consumer))
reports presentation; it is available from API 35.

**Older-platform limit:** Android 13–14 use the available transaction-committed
callback. Android 12 retains the live replacement through a second submitted
frame. These fallbacks do not offer the same presentation guarantee and were not
verified on those Android versions. The committed-only experiment above still
failed once on API 35; the zero-flash result applies to the API 35 presentation
path, not to every supported device.

## Verification

Dedicated `emulator-5582`: Android 15/API 35, Google APIs x86_64, Pixel 5 geometry,
1080×2340, 440 dpi, 60 Hz, host GPU. World 0 is pinned. Device animation settings
and recording speed were unchanged. Builds and instrumentation were run outside
recording batches.

- `check_flash.py` examines every original source frame from the first centred
  Ghost cube through 150 ms after scene reveal. It rejects frames with fewer
  than 100 Ghost pixels in the checked region, and centred cubes with excessive
  brightness (90th-percentile channel mean above 220/255). The region covers the
  centred cube and initial camera travel. This is a fixture-specific check,
  not a general detector for every skin and screen size.
- The check fails on the previous APK's captured blank frame. Full-resolution
  inspection confirms the failure. All 12 final recordings pass both checks.
- Reviewed a final source filmstrip through the full camera/menu transition.
- Added native/OpenGL RGB comparisons for Ghost and Classic at four poses.
  **The previous APK already passes this test.** It rules out a fixed-pose
  lighting mismatch in this reproduction; it is not evidence of a repaired
  system handoff. Final mean RGB errors range from 0.25 to 0.79 out of 255.
- All **23** affected lighting, geometry, clock, menu, lifecycle and shop tests
  pass. Debug, instrumentation and unsigned-release builds pass. Lint has zero
  errors and the same two existing warnings.

The first visible Ghost frame in the 12 final recordings is at **135–180 ms**
from the device-clock launch-request marker (median **156 ms**). This measures
first appearance, not full-game readiness or motion onset. It is not a zero-ms
launch claim or a new speedup comparison. Frame-delivery variability remains;
12 clean recordings do not prove every future launch or device will be flawless.

### Evidence

- [Final source recording, run 9](../captures/launch-ghost/presented/launch-9.mp4)
- [Final source filmstrip, run 9](../captures/launch-ghost/presented/strip-9/sheet.png)
- [All trial results, traces, frame checks and APK/video hashes](launch-ghost-reference/evidence.json)
- [Final tests](launch-ghost-reference/tests-final.txt),
  [previous-APK lighting test](launch-ghost-reference/tests-before.txt),
  [final RGB measurements](launch-ghost-reference/lighting-final.txt)
- [Build](launch-ghost-reference/build.txt) and [lint](launch-ghost-reference/lint.txt)

Raw recordings and screenshots remain under `captures/launch-ghost/`.
Final debug APK SHA-256:
`fcc03a1713b4ec455c6a43dd05242748d496ea9cc6e9a28408a05dc047b73aff`.

## Size and dependencies

No dependency or media asset was added. Debug: **11,670,900 bytes**; unsigned
release: **9,138,147 bytes**. Both byte totals match the previous shipped build,
though their contents/hashes differ. Existing opportunities remain release code
shrinking and ABI-specific distribution; see the
[earlier size analysis](launch-animation.md#apk-size-and-dependencies).
