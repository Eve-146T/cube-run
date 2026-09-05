package cube.run.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.view.Gravity
import android.widget.LinearLayout
import cube.run.R
import cube.run.data.Settings

/**
 * The pause: a compact candy card over the frozen, dimmed run (the HUD
 * behind it still shows the score and the haul — nothing is repeated
 * here). One big RESUME, RESTART / MENU, and the sound and vibration
 * toggles. Tapping the scrim or the back chip resumes too.
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class PauseSheet(
    activity: Activity,
    kit: UiKit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onMenu: () -> Unit,
) : Sheet(activity, kit, onResume) {

    init {
        card.addView(LinearLayout(activity).apply { // header: back chip + title
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            addView(kit.chip(R.drawable.ic_back, Theme.CARD_ALT, Theme.INK, activity.getString(R.string.cd_back)) { dismiss() }, LinearLayout.LayoutParams(dp(42f), dp(46f)))
            addView(kit.text("PAUSED", 24f, Theme.INK, 700, Gravity.START).apply { letterSpacing = 0.06f },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(12f) })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        card.addView(kit.button("RESUME", Theme.PLAY, UiKit.Size.BIG) { dismiss() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(20f) })
        card.addView(kit.button("RESTART", Theme.SKY, UiKit.Size.BIG) { onRestart() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
        card.addView(kit.button("MENU", Theme.LAVENDER, UiKit.Size.BIG) { onMenu() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })

        card.addView(divider(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2f)).apply { topMargin = dp(18f); leftMargin = dp(20f); rightMargin = dp(20f) })
        card.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false; clipToPadding = false
            val size = dp(46f)
            addView(kit.toggle(R.drawable.ic_sound_on, R.drawable.ic_sound_off, activity.getString(R.string.cd_sound), Theme.SKY,
                { Settings.soundEnabled }, { Settings.setSoundEnabled(it) }), LinearLayout.LayoutParams(size, size + dp(4f)))
            addView(kit.toggle(R.drawable.ic_haptic_on, R.drawable.ic_haptic_off, activity.getString(R.string.cd_haptics), Theme.SKY,
                { Settings.hapticsEnabled }, { Settings.setHapticsEnabled(it) }), LinearLayout.LayoutParams(size, size + dp(4f)).apply { leftMargin = dp(14f) })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16f) })
    }
}
