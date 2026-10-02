# Zen cube

Issue #24: a way to enjoy the run (sounds, motion, haptics) without the stress of
crashing and starting over. The answer is a cheap cube, not a separate mode.

The Zen cube (id 25, 500 coins) is paper white with an ink outline; its abilities:

- **Zen** — obstacles you touch melt into slow black and white motes with a soft
  singing-bowl ring (pentatonic pitches, one ring per cluster of rows), a light tick
  and no flash, shake or slow motion. Running into a platform lifts you onto it. The
  auto-climb stops at difficulty 0.4; a fire boost may still go faster. The HUD shows
  no score, haul or stock. Bubbles are free, and an active bubble takes a hit as usual
  before Zen does. Nothing counts: `Progress.zenRun` blocks every achievement metric
  and record, bubble pickups stock nothing, boxes are not kept, and leaving through
  pause → menu banks nothing (the developer's END RUN banks nothing either).
- **Calm** (experimental) — a filter: a soft off-white haze over the 3D scene,
  heavier towards the sky, which mutes the world's colours during a run
  (`Gdx3DGame.calmWash`). One full-screen blended rect, no framebuffer pass.

Tests: `cube.run.game.ZenCubeTest`.
