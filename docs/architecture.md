# Architecture (experimental branch)

Two threads, one rule: the game simulates and draws on the GL thread; the
HUD lives on the UI thread. They only meet through `core.GameSession`
(GL → UI: score, coins, boxes, world, box rewards) and `core.Stage`
(UI → GL: which stage to show, pause, wardrobe try-ons, open-box requests).

## Packages

- `cube.run` — `App` (service init) and `GameActivity` (libGDX surface + `Hud` on top).
- `core` — the engine and platform services: `Gdx3DGame` (frame loop, camera,
  sky, slow-motion, shake/flash/shards), `SoundFx`, `Haptics`, `Stage`, the
  session bridge. `core.gfx` holds the batched renderers: `WorldBoxBatch`
  (every box in one draw call), `PrismBatch` (every coin in one), `ShardSystem`
  (particles), `BubbleRenderer` (the fresnel soap-bubble shader), `TouchInput`.
- `data` — persistence and catalogues: `Progress` (bank, upgrades, wardrobe,
  box rewards), `Settings`, `Scores`, `Skins` (cube skins, bubble skins,
  trails, the `Wardrobe` categories), `Worlds` (the biomes).
- `game` — the run. `CubeRun` is the conductor (state, input, the collision
  pass); everything else is a piece it drives: `Player`, `Bubble`, `PowerUps`,
  `Difficulty`, `FireBoost`, `RunCamera` (the rig), `RunFx` (every sound /
  flash / burst in one place).
  - `game.track` — `Track` (the lane-walk director), `Obstacles` (data +
    factory), `Sections` (the hand-authored library + step alphabet),
    `TrackRenderer` (how the road looks).
  - `game.world` — `WorldRunner` (which world, the gate, the sky cross-fade)
    and `Scenery` (floor, roadside decoration per world, streaks, gates).
  - `game.stage` — the 3D stages the HUD borrows: `Showcase` (wardrobe /
    results pedestal) and `GiftStage` (the mystery box).
- `ui` — the candy look and every screen: `Theme` (tokens, font), `Widgets`
  (`UiKit`, candy buttons/chips, outlined text, segment bars, icons in
  `Icons`), `Anim` (the motion vocabulary), `Page`/`Sheet` (full-screen page
  and floating card bases), `Hud` (in-run HUD + page host), `MainMenu`,
  `ShopView`, `WardrobeView`, `SectionsView`, `PauseSheet`, `RunOverFlow`,
  `CelebrationView`.

## Collision

Every solid obstacle is a box with a `bottom` and `top`; the cube hits it
when it overlaps sideways and vertically. That one rule covers pillars
(clear them from a platform or a bounce), walls (jump), bars (roll), tar,
pistons, sweepers, stompers, zappers (only while lit), pendulums and the
windmill arm (whose sideways gap depends on where the arm is right now).
Bounce pads are harmless triggers; platforms raise the ground.

## Worlds

A run starts in Candy Fields. Every 140 rows a gate is dropped at the
horizon in the next world's colour; everything born behind it (tiles,
roadside, obstacle hues) is already in the new style, and when the gate
passes the player the sky and haze cross-fade. Obstacle kinds keep fixed hue
offsets from the world hue, so they read the same in every world.

## Test tools

Dev mode (this launch only): free coins, a pickup every few rows (boxes
included), and an END RUN button on the pause card so the results and box
stage can be reached from any run. The section explorer loops one section;
on its own it lays no pickups.

## Rendering note

Over the GL surface, Android's renderer was seen to leave the top of a
freshly translated view unpainted until the next layout pass (a page's
title bar never appeared). `Anim` therefore ends every entrance by pinning
the final values and requesting a layout.

The display font is Fredoka (SIL OFL), bundled in `assets/fonts`.

## Portals and bonus worlds

`data.Bonus` lists the bonus worlds and the best score that unlocks each.
The track lays a portal row every so often (`Track.portalEvery`, shortened
by the Portal luck perk); crossing it calls `Track.crossPortal`, which
reshapes the road through `game.Lanes` (lane count and spacing, the one
place lane geometry lives) and tells the game which world it entered:

- Wide Open: five lanes, a wandering two-lane corridor of pillars.
- Rollercoaster: `game.Terrain` adds a rolling height to everything the
  batched passes draw (the cube and the row it meets share a z, so
  collision is untouched).
- Zero-G: the lanes ease apart and the cube hovers (`Player.hover`),
  drifting lazily between them; only pillar sections are served.
- Kaleidoscope: the palette cycles, the camera sways, coins pay double.

An exit portal after ~46 rows brings the normal road back. The results
page lists the portals visited and any newly unlocked.

## Perks

Beyond the four power-up durations, `Progress.perks` are rule changes:
Head start (boost taps pre-lit), Rich coins (coin value), Portal luck,
Lucky boxes; plus Second wind, a stock of revives (a crash becomes a smash
with a short bubble). The shop shows them in their own section.

## Debug launch extras

`adb shell am start -n cube.run/.GameActivity --ez dev true --ei section 56 --ei bonus 3 --ei world 1`
turns dev mode on (free coins, pickups and portals galore, END RUN on the
pause card), loops one section, forces which bonus world portals open to,
and picks the starting world. All process-scoped.
