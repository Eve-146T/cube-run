Ability display review

Eight native wardrobe layouts are implemented in `AbilityDisplay`. The app uses
the selected style 0 (the expandable corner icon), positioned just below the tabs. Existing
wardrobe calls keep that default; the capture test passes styles 0–7 explicitly.
Abilities are lists on cube, bubble, and trail definitions, and each explanation
uses the same wording in every layout.

To refresh the gallery, build and install the debug and Android test APKs, then run:

```sh
adb shell am instrument -w -e class cube.run.ui.AbilityReviewTest -e captureAbilities true cube.run.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/cube.run/files/ability-review captures/ability-review
```

Copy `index.html` into a review directory with those images under `assets/`, then
publish that directory with `drop web`. The capture test is skipped unless
`captureAbilities=true` is supplied. Its two-ability Ghost is a layout fixture;
it does not change owned cosmetics or grant another gameplay ability.

Pass `-e abilityStyle 0` to capture only the selected layout.
