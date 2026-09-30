# Compact windows

Menus use a content-first layout below 480 dp of usable pane height. The decision
uses the measured window and permanent cutout insets, not physical screen size.
`CompactLayout` supplies the shared breakpoint; `Page` applies compact headers
and calls `onCompactChanged` when a live resize crosses it.

- Stage: short panes keep the 3D stage. Pages that frame a showpiece report the gap
  they left for it through `Stage.focusFraction`, and the camera frames the item there.
- Shop: no showroom; purchases keep their coin flight, card flash and rolling numbers.
  The void offering, which needs the showroom, settles directly.
- Wardrobe: the full layout, tightened. Tabs hug the title, the details sit at the
  bottom edge, the arrows flank the item name, and the stage frames the item between.
  The bubble category pulls the camera back so the bubble fits.
- Achievements: the title joins the back button's row, no trophy hero.
- Pause: essential actions, a window-constrained card, and scrolling when text
  or window height requires it. Sound and haptics remain in the main menu.
- Results: the cube and its sunburst above a smaller score and a one-line coin total;
  the column scales when a large font leaves less than 104 dp for the cube.
- Gifts: the box opens on the stage as at full height; the reward card sits low so
  the opened reward stays visible above it.
- Main menu: a one-line logo with a real word gap, the best score and the plain start
  prompt. Below 352 dp width the settings and the page buttons stand as columns at the
  edges so the cube stays visible; below 400 dp height they fall back to centred rows.
  Gameplay uses a smaller score and boost indicator.

Full-height layouts retain their original sizing. Resizing changes presentation,
not navigation, purchases or reward state. Cutout padding remains authoritative.

## Verification

`CompactLayoutTest` exercises the same view instances at 360×375, 320×426,
280×320 and 360×720 dp, including repeated compact/full transitions and 150%
text. It checks essential control bounds, the room left for the stage item, that the
ability corner clears the arrows, readable results, shop scrolling, language-sheet
width, gameplay HUD size and reward continuity.

Also run WindowGeometryTest, WardrobePriceLayoutTest, GiftBoxUiTest,
GiftBoxReturnTest, ShopResetProgressTest and LanguageTest.
