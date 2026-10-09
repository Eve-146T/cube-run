# Compact windows

Menus use a content-first layout below 600 dp of usable pane height (`CompactLayout.HEIGHT_DP`;
16:9 phones have 640 dp, a two-thirds split pane about 500). The decision uses the measured
window, not physical screen size. Cutout insets are reduced by whatever the window and parent
views already keep clear (`Uncovered`): in a split pane Android may lay the content out below a
visible status bar while still reporting the cutout, which used to leave a gap that came and
went with the bar.
`CompactLayout` supplies the shared breakpoint; `Page` applies compact headers
and calls `onCompactChanged` when a live resize crosses it.

- Stage: short panes keep the 3D stage. Pages that frame a showpiece report the gap
  they left for it through `Stage.focusFraction`, and the camera frames the item there.
- Shop: no showroom; the cards are the full-height cards. Purchases keep their coin flight,
  card flash and rolling numbers.
  The void offering, which needs the showroom, settles directly.
- Wardrobe: the full layout, tightened. Tabs hug the title, the details sit at the
  bottom edge, the arrows flank the item name, and the stage frames the item between.
  The bubble category pulls the camera back so the bubble fits, as does any gap too shallow
  for the cube. Below 360 dp (a third of a phone) the dots go, the button is less tall and the
  arrows flank the button instead of the name.
- Achievements: the title joins the back button's row, no trophy hero.
- Pause: essential actions (below 320 dp: RESUME, then RESTART and MENU in one row), a window-constrained card, and scrolling when text
  or window height requires it. Sound and haptics remain in the main menu.
- Results: the cube and its sunburst above a smaller score and a one-line coin total;
  the column scales when a large font leaves less than 104 dp for the cube.
- Gifts: the box opens on the stage as at full height; the reward card sits low so
  the opened reward stays visible above it.
- Main menu: a one-line logo with a real word gap, the best score and the plain start
  prompt, which gives way when a third of a phone leaves no gap for it. Below 352 dp width the settings and the page buttons stand as columns at the
  edges so the cube stays visible; below 400 dp height they fall back to centred rows.
  Gameplay uses a smaller score and boost indicator.

Full-height layouts retain their original sizing. Resizing changes presentation,
not navigation, purchases or reward state. Cutout padding remains authoritative.

Settings trims card gaps and row padding when its content only slightly exceeds
the available height, so ordinary full-screen phones do not have a tiny scroll
range. Short split panes and large accessibility fonts retain scrolling rather
than shrinking the controls below usable touch targets.

## Verification

`CompactLayoutTest` exercises the same view instances at 360×375, 320×426,
280×320, 360×500, 360×250 and 360×720 dp, including repeated compact/full transitions and 150%
text. It checks essential control bounds, the room left for the stage item, that the
ability corner clears the arrows, readable results, shop scrolling, language-sheet
width, gameplay HUD size and reward continuity.

Also run WindowGeometryTest, WardrobePriceLayoutTest, GiftBoxUiTest,
GiftBoxReturnTest, ShopResetProgressTest and LanguageTest.
