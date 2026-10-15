# AGENTS.md

Cube Run is a hyperstimulating Android endless runner built with libGDX, created by Eve-146T. Worktrees belong in `~/Worktrees/cube-run/`.

## Autonomy

- Complete implementation, verification and review before returning the work. Make routine implementation decisions yourself; do not make the developer supervise steps already authorized by the task or this file.
- Ask only when missing information materially changes the result and cannot reasonably be resolved from the project or conversation. Continue independent work while waiting.
- Respect the developer's time: keep updates concise, fix issues you can resolve, and report concrete outcomes and remaining blockers.

## Commit Rules
- Don't bloat the repo - do not commit unecessary stuff like temporary files. If you're tasked with merging a worktree, check if you can omit temporary files.


## In this update

- Every update, and every worktree, rewrites `app/src/main/res/values*/update_notes.xml` (English, German, Hebrew) from scratch to say what it changes, one `icon | title | text` line each, and sets `update_notes_for` to the versionName. The icon is an achievement id or `news`. Rewriting the notes makes the "In this update" card pop up once on the next launch.
- Only things a player can see, try or chase get an item. Balance changes, bug fixes, performance and UI moves are never listed: the fixed last item "And the usual" covers them. The developer says when something like that deserves its own line. Details: `docs/update-notes.md`.

## UI design

- Candy arcade: flat saturated colours, chunky rounded slabs with a darker lip, bold glossy single silhouettes for icons, the Fredoka font. No gradient or bokeh backdrops, no drop shadows, no confetti, and very little shake.
- Small icons: making it look appealing has priority over depicting something actually useful. Read `docs/icons.md` before drawing one.
- Feedback happens where you tap, and every screen says as little as it can: icons, bars and numbers instead of words.
- Idle clicks are taps on things that do nothing, like an achievement card or a row on the "In this update" card. They still answer the finger, softly: use `Pokes.press` (a small dip, the quiet `tap` sound, a tick of haptics, and a wiggle that only builds up with fast repeated taps). Never play the square-wave `blip` or any other loud or bright sound for an idle click, and never make one look like it did something.
- Never block scrolling for an animation. While something plays out, the page can still scroll; only taps that would start something new wait.

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
 
