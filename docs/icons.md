# Small icons

How to draw the small pictures: achievement tiles, the "In this update" rows, shop and settings tiles. They are drawn in code on a 48-unit grid, mostly in `ui/ChallengeIcons.kt`, and sit on a candy tile (`AchievementTile` in `ui/AchievementBadges.kt`).

## Appealing first, accurate second

**Making it look appealing has priority over depicting something actually useful.** An icon is a little candy you want to collect, not a diagram of the rule. If the literal picture of a goal is fiddly or ugly, draw something simpler and nicer that only hints at it. The name and the goal text under it do the explaining.

- Go abstract when the literal picture gets busy. A loose symbol (a bolt, a burst, a shard) beats a small scene with several props.
- Don't add things just to be correct. If removing a detail makes the icon nicer and it still hints at the goal, remove it.
- If two ideas tie on looks, pick the one that reads at a glance.

## What works

- One bold, glossy, single silhouette that fills most of the tile. The favourites so far are the lightning bolt (Power Collector), the shard (Shardsmith), the calendar with ticks (Regular), the moon (Insomniac), the pause bars (Nervous Tic) and the hexagon badges.
- Flat saturated candy colours from `Theme`, a darker copy of the shape dropped 2.5 units below it as its lip (`lipped`), and one white gloss stroke on top. The lip always drops straight down, even on a tilted shape.
- White or pale pictures on a saturated tile chosen to contrast with them (`AchievementCards.accent`).
- The tile's pale top stripe stays: it does a lot of work making the tile read as a chunky candy.
- A twinkle or sparkle in a corner is fine as a garnish, never as the subject.
- Let `ChallengeIcon` centre the painted pixels (it measures them), and check that the result looks centred, not only that it measures centred.

## What doesn't

- The isometric cube and anything built on it (rated 1–2, "shitty cube"). The rounded toy cube is tolerated, not loved.
- The mystery box and the gift box (rated 1–2 in every form).
- Small scenes: a road with a flag, a box with three ribbons, a ball on a chain with flying chunks. They look cluttered at tile size.
- Hairlines, folds and small inner details that turn to mush at 64 dp (the Poltergeist's fold was removed for this).
- Text in an icon, unless it is the whole point (67), and then in a bold rounded font.
- Gradients, drop shadows and photoreal shading.

## Reviewing a new icon

Render it on its real tile, beside its neighbours, at the size it ships, before showing it. The art-sheet renderer (`AchievementArtSheetTest`) is a scratch tool: copy it in to render, and never commit it. Try two or three ideas side by side and show the best, with a line on why the others lost.
