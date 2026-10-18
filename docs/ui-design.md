# UI design

Cube Run's menus are candy arcade: bright, chunky and quick, and they never get in the way of the cube. Build screens on `ui.Page` (full screen) or `ui.Sheet` (a card over the dimmed stage) with `UiKit` (candy buttons, chips, outlined text), keep colours in `ui.Theme`, and animate with `ui.Anim`. Small pictures follow [icons.md](icons.md).

## Look

- Flat saturated colours and chunky rounded slabs with a darker lip under them. White cards sit on a flat backdrop: no gradients, no bokeh, no drop shadows.
- Pages of cards share one build: slabs on a solid lip, each item's picture on its candy tile, a bar underneath. Each page has its own flat backdrop: lavender for achievements (`TrophyRoom`), butter for the shop (`ShopCards`), whose cards are white too; there, the buy button runs along the card's bottom and carries the bar. Cards in each item's own colour were tried and rejected.
- The Fredoka font. Over the 3D stage, white text with an ink outline.
- Every menu is full screen, with the one standard back button top-left. The pause menu is a compact card over the frozen run and repeats nothing the HUD already shows.
- The cube is never hidden behind UI.

## Words

Say as little as possible. Icons, bars, stars and big numbers do the talking: no "RUN OVER", "LEVEL 0/10" or "none in stock". In the shop, what you are buying is the big coloured number and what you have is small. No text on screen during a run.

## Feedback

- Feedback happens where you tap: coins fly from the balance into the button you pressed, the card you tapped flashes, numbers roll. No confetti overlays.
- Only a crash may shake the screen, and only a little.
- Frequent sounds are soft and round: one mellow note, no harsh highs, no square waves.

## Idle taps

An idle tap is a tap on something that does nothing, like an achievement card or a row on the "In this update" card. It still answers the finger, quietly, so the screen feels alive without pretending something happened.

- Use `Pokes.press`: a small dip and spring back, the quiet `tap` sound and a haptic tick. Quick repeated taps on the same thing build up a wiggle; a single tap never wiggles.
- Never play the square-wave `blip`, or any loud or bright sound, for an idle tap. Never flash, pay out or animate anything else, which would look like the tap did something.

## Scrolling

Avoid scrolling menus wherever we can: a menu that fits shows everything at a glance and never hides a control below the fold.

- A full-screen menu must fit a 360 × 720 dp window without scrolling. When a new row pushes it over, make room (tighter spacing, a smaller control, combine rows) rather than letting it scroll a little. A tiny scroll range is the worst case: it looks broken.
- Settings has to fit with the debug card (dev mode and the section explorer) hidden, which is what players get. Debug builds may scroll. `CompactLayoutTest` checks this.
- Long collections (achievements, the wardrobe), very short windows such as split screen, and large accessibility fonts may scroll. Compact windows have their own rules: [compact-layout.md](compact-layout.md).
- Never block scrolling for an animation. While something plays out (a claim, coins flying), the page still scrolls; only taps that would start something new wait.

## Tests

Test logic, not looks: no new centring, layout or screenshot tests for UI that is still being designed. Check looks on the phone with screenshots instead. If an existing layout test breaks after a deliberate design change, say so and ask before changing it.
