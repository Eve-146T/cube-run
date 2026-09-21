# AGENTS.md

Cube Run is a hyperstimulating Android endless runner built with libGDX,
created by Eve-146T. Worktrees belong in `~/Worktrees/cube-run/`.

## Autonomy

- Complete implementation, verification and review before returning the work.
  Make routine implementation decisions yourself; do not make the developer
  supervise steps already authorized by the task or this file.
- Ask only when missing information materially changes the result and cannot
  reasonably be resolved from the project or conversation. Continue independent
  work while waiting.
- Respect the developer's time: keep updates concise, fix issues you can resolve,
  and report concrete outcomes and remaining blockers.

## Devices and builds

- Any phone currently attached through adb is authorized for Cube Run testing,
  including a newly attached or replacement phone. Do not ask again because the
  device or installed game version changed.
- Overwrite existing Cube Run builds. Cube Run save data is disposable: uninstall
  or clear it when useful, without backup, restoration or further confirmation.
  This authorization concerns Cube Run, not unrelated apps or device data.
- If another agent appears to be using a phone, use another attached device or an
  emulator instead of disrupting its run. Select the device explicitly when
  several are connected.
- `./gradlew :app:assembleDebug` builds the debug APK; `./install` builds, installs
  and launches it. `./gradlew :app:lintDebug` runs Android lint.

## UI quality and visual verification

- Assume the new or changed UI you made is terrible. Treat the first version as
  a draft to critique and improve, not something to defend or rubber-stamp.
- For changes affecting appearance or interaction, run the actual app on a phone
  or emulator. Inspect screenshots for layout and styling; interact with the
  affected flow and inspect motion live or in a recording. Compilation and
  passing automated tests do not establish visual quality.
- Actively look for poor hierarchy, awkward spacing, weak contrast, unreadable
  text, clipping, misalignment, obstructed gameplay, confusing controls and
  abrupt or broken transitions. Check relevant states, not just the nicest frame.
- Fix the concrete flaws you find, then inspect the updated result again.
  Finish when the requested flow works and the latest review finds no remaining
  actionable visual or interaction problems in the affected work.
- When using subagents for review, use a fresh subagent for each review iteration.
  Give it the task requirements and current evidence; ask it to find defects
  independently rather than confirm your assessment.
- Report what you actually inspected and any verification you could not perform.
  Documentation-only changes do not require launching the app.

## Tests and failure tracking

- Run checks appropriate to the change. Add or retain tests for useful behavior,
  not merely to increase the count or preserve a one-off investigation.
- Whenever a test fails, record it in `docs/test-failures.md` (create it if needed).
  Include the date, test name, revision, command/device and failure.
- After investigation, record whether it caught an application bug, a stale
  expectation, a flaky test or an environment problem, and what was done about it.
  Keep the entry even if a rerun passes. Use this history to judge which tests
  earn their maintenance cost and which should be removed.
