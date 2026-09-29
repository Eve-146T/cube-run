# Compact windows

Menus use a content-first layout below 480 dp of usable pane height. The decision
uses the measured window and permanent cutout insets, not physical screen size.
`CompactLayout` supplies the shared breakpoint; `Page` applies compact headers
and calls `onCompactChanged` when a live resize crosses it.

- Shop: no showroom; purchases update their card and balance directly. Mystery
  boxes still use the game's reward delivery and the normal return handoff.
- Wardrobe: a static cosmetic swatch, browsing controls, scrollable ability
  details, and a pinned purchase/equip action. The same controls and selected
  item survive resizing. Full-height mode restores the original stage layout.
- Achievements: progress beside the title, no trophy hero, smaller section gaps.
- Pause: essential actions, a window-constrained card, and scrolling when text
  or window height requires it. Sound and haptics remain in the main menu.
- Results: readable score/rewards, scrollable overflow, and the original plain tap-to-continue prompt.
  Compact mode settles the count rather than scaling the entire results column.
- Gifts: a small box/reward, a pinned plain tap prompt, and immediate stage skipping;
  the GL stage remains responsible for delivering each reward exactly once.
- Main menu: compact branding/best score, the original plain start prompt, and separate
  settings/navigation rows. Gameplay uses a smaller score and boost indicator.

Full-height layouts retain their original sizing. Resizing changes presentation,
not navigation, purchases or reward state. Cutout padding remains authoritative.

## Verification

`CompactLayoutTest` exercises the same view instances at 360×375, 320×426,
280×320 and 360×720 dp, including repeated compact/full transitions and 150%
text. It checks essential control bounds, readable results, long ability text,
shop scrolling, language-sheet width, gameplay HUD size and reward continuity.

Also run WindowGeometryTest, WardrobePriceLayoutTest, GiftBoxUiTest,
GiftBoxReturnTest, ShopResetProgressTest and LanguageTest. The opt-in
`CompactLayoutCaptureTest` (`-e captureCompact true`) saves attached-view
screenshots under the app's external-files `compact-review` directory.
