# Human test worlds

In the debug build, open the section grid from the main menu. The eight **Test Worlds** cards appear above the individual sections. Choose a card, then tap to start. Each course repeats; restarting begins the course again. **Play normally** in the grid clears the selection. Choosing an individual section also clears it.

| Preset | What to check |
| --- | --- |
| Jet before Zero-G | Collect the jet before entering the floating world; flight should stay above the hovering cube height. |
| Jet inside Zero-G | Enter floating mode, then collect the jet; the cube should rise to the aerial coins. |
| Jet expires in Zero-G | Let the six-second jet expire inside the floating world; descent should stop at hover height. |
| Zero-G alignment | Follow the coin path while the road widens and narrows; obstacles and collision gaps should follow their lanes. |
| Exit to left pad | After leaving Zero-G, catch the left launch pad and clear the tall wall. |
| Exit to right pad | Repeat the exit-pad check on the right. |
| Wide road transitions | Follow the five-lane corridor as the road opens and returns to three lanes. |
| Rolling hills | Jump walls, duck bars, and collect coins while the terrain rises and falls. |

The presets use the actual game pickups, portals and collisions. There is no extra immunity; normal perks and controls still apply. Course pickups and lane patterns are fixed, random pickups are suppressed, and coin trails are always present. Preset jetpacks last six seconds regardless of upgrade level, so expiry is repeatable. Normal runs retain their usual jet duration. Use the existing boost control to try different starting speeds.

This branch includes the Zero-G lane-spacing and jet/hover fixes and the permanent tick-sound removal. Experimental section deduplication remains on `fixing-sections`.

For automation, debug launch intents accept `--ei scenario 0` through `7`; `--ei scenario -1` clears the preset. Selection lasts for the app process, like the existing individual-section selector.

Validation: debug/release assembly, debug lint and bot host checks passed. The Motorola G7 Power and Pixel 7a each passed 16 device tests covering preset sequencing/looping, selection/reset, real jet pickup ordering, jet/hover interactions, track balance, bot model behavior and permanent tick removal. Grid screenshots were checked on both screens. Device saves were restored and verified after instrumentation. These checks do not establish human difficulty ratings; the presets are ready for that feedback.
