# AGENTS.md

Cube Run is a hyperstimulating Android endless runner built with libGDX, created by Eve-146T. Worktrees belong in `~/Worktrees/cube-run/`.

## Autonomy

- Complete implementation, verification and review before returning the work. Make routine implementation decisions yourself; do not make the developer supervise steps already authorized by the task or this file.
- Ask only when missing information materially changes the result and cannot reasonably be resolved from the project or conversation. Continue independent work while waiting.
- Respect the developer's time: keep updates concise, fix issues you can resolve, and report concrete outcomes and remaining blockers.

## Commit Rules
- Don't bloat the repo - do not commit unecessary stuff like temporary files. If you're tasked with merging a worktree, check if you can omit temporary files.


## Docs

Read the matching guide before starting:

- `docs/ui-design.md` for any menu or screen. Avoid scrolling menus (full-screen menus fit 360 × 720 dp; Settings with the debug card hidden), never block scrolling for an animation, and answer idle taps with `Pokes.press`, never the square-wave `blip`.
- `docs/icons.md` for any small icon, such as an achievement tile. Making it look appealing has priority over depicting something actually useful.
- `docs/release-notes.md` for every update and every worktree: rewrite the "In this update" notes (`update_notes.xml`, set `update_notes_for`) and, for a release, the F-Droid changelog and the GitHub release. Balance changes, fixes, performance and UI moves are never their own line.
- Reference: `docs/architecture.md`, `docs/achievements.md`, `docs/progression.md`, `docs/translations.md`, `docs/compact-layout.md`, `docs/performance.md`, `docs/action-benchmarks.md`, `docs/bot.md`, `docs/redeem-codes.md`.

Project skills live in `.skills/` (linked as `.claude/skills`), one folder with a `SKILL.md` each. Use `.skills/release` for every release.

Docs describe how things are and how to do them, not how they got there: git keeps the history. Update a guide when you change what it describes, and don't add investigation logs.

## Devices and builds

- Any phone currently attached through adb is authorized for Cube Run testing. 
- Overwrite existing Cube Run builds. Cube Run save data is disposable.
- If another agent appears to be using a phone, use another attached device or an emulator instead of disrupting its run. 

## Glossary 

We need to be on the same page with terminology. When communicating, use this language.

- **you** means the agent reading this file and changing No Clip.
- **road** means the place your cube rolls on when you play the game. 
- **biome** means the decoration and color theme of the main world.
- **portal** means the thing you go through to change worlds  
- **world** (sometimes called "bonus world) means something you enter through a portal, examples include the space world, and the trippy wave world. 
- **default world** is the world you start in and return to when exiting a bonus world - it can have different biomes.
- **wardrobe** is the place you end up in when pressing the purple button in the main menu with the painters icon, there you can buy cosmetics with different abilities and looks.
- **ability** means an ability a cosmetic has, such as the 20% more coins of the gold cube. 
- **shop** means the place you go to when pressing the yellow button in the main menu, where you can buy upgrades like longer bubble time and consumables like bubbles or mystery boxes. 
- **upgrade** refers to permanent perks and powerup improvements you can buy in the shop, giving you advantages such as magnets lasting longer or multiplying your coin gain rate. 
- **bubble** refers to the consumable you can buy in the shop or pick up on the road, you double tap during a run to activate it and if you hit an obstacle while your bubble is active your bubble pops and instead of dying you survive.
- **shard** refers to a specific thing you can pick up on the road rarely, if you collect 100 of one kind you can use them to unlock a special cube in the wardrobe. 
- 
More terms: 
- coin, tar pits, decoration, achievement, pause (menu), settings (menu), main menu (sometimes just called menu)
 
