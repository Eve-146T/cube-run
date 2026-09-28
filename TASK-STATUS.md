Task: Fix issue 27 and adjacent layout and split-screen issues.

Follow-up finding: Entering split screen from Recents after Cube Run had launched
left a second Android splash view covering the live game indefinitely.

Status: Fixed the repeated splash exit handling. Verified the actual Recents →
Split screen → Settings flow on an emulator and an attached phone. The game
remains visible and responds to input in the split pane. Build, lint, and 18
targeted on-device tests passed.
