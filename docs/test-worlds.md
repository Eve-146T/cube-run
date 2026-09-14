# Red Pill test world

In the debug build, open Sections and choose **Red Pill** under **Test Worlds**.
The middle lane stays clear and a pill arrives every 18 seconds, leaving time
for its full entry, effect and exit before the next one. Side pillars make the
wireframe transformation easy to see. Use the normal speed control to test faster
runs. **Play normally** or choosing a section clears this test world.

The pill uses the same capsule, wireframe renderer, duration and fades as a normal
pickup. The course suppresses other pickups, coins and portals. It adds no immunity.
Debug launch intents accept `--ez pillworld true`; pass false to clear the preset.

Normal runs, including dev mode's pickup-rich runs, offer Red Pills at 1/30 the
magnet rate. A rejected pill slot stays empty; it is not rerolled into another
pickup. Only the explicitly selected Red Pill test world uses scheduled pills.

The earlier jet and lane-transition test worlds were removed after validation.
