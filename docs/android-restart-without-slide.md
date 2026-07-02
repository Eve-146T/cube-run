# Relaunching an activity without the "new screen" slide

How to make a `finish()` + `startActivity()` self-relaunch look like an in-place
swap (instant cut or crossfade) instead of the system's slide-to-a-new-screen
animation. Written after fixing Cube Run's RESTART button; applies to any app
that restarts itself this way — e.g. libGDX games, which must truly finish the
activity so native GL resources are disposed (`recreate()` leaks them).

## The symptom

Tapping RESTART plays the OEM's full activity transition (slide/fade, ~600 ms
on Motorola/Android 15) as if navigating to another app screen. None of the
usual suppression APIs help: `overridePendingTransition(0, 0)`,
`overrideActivityTransition(...)` (API 34+), `FLAG_ACTIVITY_NO_ANIMATION`,
theme `windowAnimationStyle` — all apparently ignored.

## The root cause: you're playing a *task* transition, not an *activity* one

The broken pattern looks innocent:

```kotlin
val relaunch = Intent(activity.intent)          // BUG 1
    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
activity.finish()                               // BUG 2
activity.startActivity(relaunch)
```

1. **`Intent(activity.intent)` copies the launcher's intent — including
   `FLAG_ACTIVITY_NEW_TASK`** (every launcher start has it). The relaunch
   therefore asks for a new task.
2. **`finish()` runs first**, so the only activity's task is already empty and
   dying when the launch happens.

Together, the old task closes while a brand-new task opens. That is a
**task-to-task transition**, and on Android 12+ (WM Shell transitions) *apps
cannot influence task transitions at all* — every activity-level knob above is
silently ignored, and the shell's `DefaultTransitionHandler` plays the OEM's
default task animation on the closing task (the screen you're looking at).
`FLAG_ACTIVITY_NO_ANIMATION` does land on the *opening* task's change, but the
slide you see is the *closing* one.

You can confirm this from logcat while reproducing:

```
ActivityTaskManager: START u0 {flg=0x10010000 cmp=...}   ← 0x10000000 = NEW_TASK
WindowManagerShell: Transition requested: ... type = CLOSE, triggerTask = ...
WindowManagerShell: ... c=[{m=OPEN f=NO_ANIMATION|MOVE_TO_TOP leash=Surface(name=Task=322)...},
                          {m=CLOSE f=NONE leash=Surface(name=Task=321)...}]   ← two TASKS
WindowManagerShell: start default transition animation
```

`Task=NNN` changes mean a task transition (untouchable). After the fix you
want to see one task and `FILLS_TASK` *activity* changes instead.

## The fix: keep the relaunch inside the current task

```kotlin
// Fresh explicit intent — NEVER a copy of activity.intent — and start the
// new instance BEFORE finishing the old one, so the task never empties.
activity.startActivity(Intent(activity, activity.javaClass))
activity.finish()
```

- The fresh intent carries no inherited flags, so no `NEW_TASK`.
- Start-before-finish keeps the task alive; the new instance opens *on top,
  inside the same task* — an activity-level transition, which apps fully
  control. The old activity finishes hidden underneath and goes through its
  normal teardown (for libGDX: `onPause` sees `isFinishing() == true` and
  disposes GL exactly as with finish-first, before the new instance creates).
- Requires the default `launchMode` (`standard`); `singleTask`/`singleTop`
  would route the intent to the existing instance instead of a new one.

Then pick how the swap should look:

### Option A — instant cut

```kotlin
activity.startActivity(
    Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
activity.finish()
```

For in-task activity opens the flag IS honoured. Bonus: the system holds the
old window until the new activity draws its first frame, so there's no blank
gap — but the one-frame content jump can read as a *flash* if the two screens
differ in brightness.

### Option B — short crossfade (usually reads better)

Drop the flag and drive the animation from the theme:

```xml
<style name="GameTheme" parent="...">
    <item name="android:windowAnimationStyle">@style/RunSwapAnim</item>
</style>

<style name="RunSwapAnim">
    <item name="android:activityOpenEnterAnimation">@anim/run_fade_in</item>
    <item name="android:activityOpenExitAnimation">@anim/run_hold</item>
    <item name="android:activityCloseEnterAnimation">@anim/none</item>
    <item name="android:activityCloseExitAnimation">@anim/none</item>
</style>
```

`res/anim/run_fade_in.xml` — the new screen fades in:

```xml
<alpha xmlns:android="http://schemas.android.com/apk/res/android"
    android:fromAlpha="0.0" android:toAlpha="1.0"
    android:duration="220"
    android:interpolator="@android:interpolator/decelerate_quad"/>
```

`res/anim/run_hold.xml` — the old screen stays fully opaque underneath:

```xml
<alpha xmlns:android="http://schemas.android.com/apk/res/android"
    android:fromAlpha="1.0" android:toAlpha="1.0"
    android:duration="220"/>
```

`res/anim/none.xml` — zero-length no-op for the close attrs:

```xml
<alpha xmlns:android="http://schemas.android.com/apk/res/android"
    android:fromAlpha="1.0" android:toAlpha="1.0" android:duration="0"/>
```

## Gotchas

- **`@null` window animations do nothing.** `@null` resolves to resource id 0,
  which the framework reads as "unspecified" and replaces with its **default**
  transition. Always point the attributes at a real (0-duration if needed)
  animation resource.
- **The exit "hold" must match the enter duration.** If the exit animation is
  shorter, the old window is removed mid-fade and the new screen blends with
  black — a flash to dark instead of a crossfade.
- **`task*` animation attributes are dead weight on Android 12+.** The system
  ignores app-supplied task animations; don't bother setting them (and
  entering/leaving the app *should* animate normally anyway).
- **A frozen last frame is fine as the fade source.** Even though the old
  activity's GL context is destroyed in `onPause`, its window keeps showing
  the last composited frame for the duration of the transition.
- **`overridePendingTransition` is deprecated (API 34) and unreliable before
  that on OEM skins** — don't build the fix on it.

## Verifying on a device

1. `adb logcat -c`, trigger the restart, `adb logcat -d` — check the
   `ActivityTaskManager: START` line's `flg=` (no `0x10000000`) and that the
   `WindowManagerShell` transition changes are `FILLS_TASK` activities, not
   `Task=NNN` surfaces.
2. `adb shell screenrecord`, tap restart mid-recording, then extract frames
   (`ffmpeg -i rec.mp4 -vf fps=30 f%03d.png`) and eyeball the swap — or
   compute frame-to-frame RMSE (`compare -metric RMSE`): an instant cut is a
   single huge spike; a crossfade is a ramp over `duration / 33 ms` frames; a
   residual system animation is a ~600 ms plateau.
3. `adb shell dumpsys activity activities | grep Hist` afterwards — exactly
   one instance of your activity should remain in the task.
