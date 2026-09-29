Task: Fix issue 27 and adjacent layout and split-screen issues.

Follow-up finding: Entering split screen from Recents after Cube Run had launched
left a second Android splash view covering the live game indefinitely.

Status: Fixed the repeated splash exit handling. Verified the actual Recents →
Split screen → Settings flow on an emulator and an attached phone. The game
remains visible and responds to input in the split pane. Build, lint, and 18
targeted on-device tests passed.

Follow-up: Content-first compact layouts below 480 dp usable height now remove
shop/result showroom space, keep wardrobe actions pinned beside scrollable
details, condense achievements and the main menu, constrain pause/language
sheets, and reduce the gameplay HUD. Native controls survive live resizing;
full-height presentations are restored when space returns. Build/lint and
compact-window, purchase, reward and language regressions are verified on the
attached Moto G7 Power. See docs/compact-layout.md for the verification matrix.
