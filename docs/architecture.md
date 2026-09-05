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
  - `game.stage` — the 3D stages the HUD borrows: `Showcase` (the cube on
    the wardrobe, shop and results stages, with the sunburst behind it and
    the morphing category transitions), `Demos` (what a purchase does to the
    cube) and `GiftStage` (the mystery box).
- `ui` — the candy look and every screen: `Theme` (tokens, font), `Widgets`
  (`UiKit`, candy buttons/chips, outlined text, segment bars, icons in
  `Icons`), `Anim` (the motion vocabulary), `Page`/`Sheet` (full-screen page
  and floating card bases), `Hud` (in-run HUD + page host), `MainMenu`,
  `ShopView`, `WardrobeView`, `SectionsView`, `PauseSheet`, `RunOverFlow`,
  `CelebrationView`, `PayFx` (coins flying from the balance into the button
  you pressed: every purchase pays where you tapped).

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
passes the player the sky and haze cross-fade. Obstacle hues advance by 19°
per generated row, restoring colour variety within each biome. Kind offsets
stay consistent within a row, and colours stay fixed as obstacles approach.

## Test tools

Dev mode (debug builds only, this launch only): free coins, a pickup every few rows (boxes
included), and an END RUN button on the pause card so the results and box
stage can be reached from any run. The section explorer loops one section;
on its own it lays no pickups.

## The start

Before a run nothing moves: the cube waits at a chequered start line under
a start gate (chequered banner, a spinning star on each pylon), hopping
now and then, while the camera swoops in from high and far. The first tap
squashes the cube, sprays dust behind it, and eases the speed and the chase
camera in over a second and a half; passing under the gate is a bang, a
flash and candy raining off the beam. No coins are laid before the run.

## Screen shake

Nothing shakes the screen. Crashes, smashes, the second wind, the gift box,
the shop demos and the portals use slow motion, a flash and a
field-of-view punch instead. When the bubble smashes a row the blocks
split into chunks that tumble past the camera (`game.track.Debris`).

## Framing the stages

`RunCamera.frame` aims the stage cameras so a world point lands at a given
fraction of the screen height (from a dp height via
`Gdx3DGame.fractionForDp`), and `Gdx3DGame.sunburstBehind` puts a sunburst
on the camera's line of sight behind a point — so the cube on the results,
the cube in the shop's showroom strip and the box on the gift stage sit
exactly on their sunburst's origin on every phone, whatever its aspect.

After 12 opening rows, pickup opportunities occur on the next eligible row
every 10–18 rows. A shuffled bag includes every kind; Lucky Boxes adds extra
boxes. Jetpacks and mystery boxes require an actual run score of at least
100, then use only every second opportunity for that kind. Skipped slots
stay empty and consume their spacing, reducing their frequency by half
without increasing other pickups. The gates also apply in developer mode.
Coin fields and five-lane rows can carry pickups too. Five-lane roads have
one clear lane sweeping across all five, with a rest beat every third row,
9.5-unit spacing and a centre-lane approach before the exit. Rollercoaster
visits last about 30 rows, mixing short dodge, jump and duck patterns with
coin fields and extra spacing for visibility over the hills.

Road tiles, kerbs and roadside ground use `worldGround`: both ends sample
the hill height at their own z, so neighbouring pieces share an edge instead
of floating at separate centre heights. Face lighting follows the slope.
Obstacles and decorations keep their rigid shapes through `worldBox`.

## The run-over flow

Results (the cube in front of a sunburst, the score, the stars and coins)
→ the mystery boxes, if any (each shatters into shards
and its prize hangs where it was) → straight back to the main menu. There
is no "go again" page: restart and everything else live on the menu.

## The shop

The shop is a showroom strip with your cube (and a sunburst) over a dark
sheet of cards; the cards scroll inside the sheet, never over the cube. A
price button pays where you tapped (`ui.PayFx`), then asks the stage for a
demo (`Stage.demoRequests` → `game.stage.Demos`): the bubble inflates
around the cube, coins spiral into it, a twin splits off and slams back,
it lifts on a jet of flame, fire streams off its back, gold rains, a
portal ring spins around it, a gift box drops in and bursts.

## Rendering notes

The HUD excludes its full bounds from the SurfaceView transparent region.
Otherwise Android can punch a hole around a scaled-down entrance and clip
the view when it grows (the menu's coin pill lost its rounded top until the
next layout). Normal alpha composition keeps growing and moving overlays
intact. `Anim` repaints the view and window each frame and requests layout
after entrances without overwriting newer feedback. Replacement motions
cancel old end actions and reset timing; pulses replace only scale. Candy
press feedback animates the drawing independently of entrance/exit motion.
Delayed menu and results effects are cancelled when their screen leaves;
reward heartbeats start after their entrance and stop before the next prize.

The engine draws in this order: the batched opaque world (road and land
first, so an overflowing batch drops roadside before tiles), the batched
coins, then unlit blended shapes in world space (`Gdx3DGame.sunburst`, the
hype pattern behind a showpiece, drawn *behind* it so the cube stays in
front), the ModelBatch (the cube), blended extras (bubbles), shards.

Coins are twelve-sided prisms in one draw call (`core.gfx.PrismBatch`);
the rim quads are wound so their outward faces survive back-face culling,
and each face keeps a bright floor plus a glint as it swings through the
key light, so a spinning coin sparkles instead of going dull. There are no
drop shadows anywhere: the cube, the showcase and the gift box float.

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

`adb shell am start -n cube.run/.GameActivity --ez dev true --ei section 56 --ei bonus 3 --ei bonusnow 0 --ei world 1 --ei boxes 3`
turns dev mode on (free coins, pickups and portals galore, END RUN on the
pause card), loops one section, forces which bonus world portals open to,
starts the run already inside a bonus world, picks the starting world, and
starts the run holding mystery boxes (so the box stage is one END RUN
away). All process-scoped and ignored by release builds. The dev toggle
and section explorer buttons are also available only in debug builds.

`game.Lanes.unfold` eases the two outer lanes of the five-lane world open
and closed, so the road widens instead of snapping; lane input is bounded
by `Lanes.last`, not 2.

## Shards

`data.Shards` lists three kinds of crystal (ember, frost, void). Only
mystery boxes drop them, 1 to 30 at a time (small drops far more often),
while the skin of that kind is still locked. Each kind unlocks one
shard-only skin (`Skins.forShard`, `Skin.shardOnly`): Inferno, Glacier,
Eclipse, at 100 shards. They are never sold for coins and never handed out
whole by a box. The wardrobe shows the count, a bar and an UNLOCK button;
`Progress.unlockWithShards` spends them.

## Building

`./install` builds the debug APK, installs it on the connected phone and
launches it (arguments go to Gradle, e.g. `./install --offline`).

`./gradlew buildDebug` (an alias of `assembleDebug`) and `./gradlew
installDebug` work with no `JAVA_HOME`: `gradle/gradle-daemon-jvm.properties`
tells Gradle to run itself on a JDK 17, whatever the machine default is.
