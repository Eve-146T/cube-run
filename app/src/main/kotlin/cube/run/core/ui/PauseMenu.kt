package cube.run.core.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import cube.run.R
import cube.run.core.Palette
import cube.run.core.Settings

/**
 * The pause menu: the frozen run stays visible underneath a dark scrim; the
 * score so far, one big RESUME, then RESTART / MENU, and the sound and
 * vibration toggles. The back button (top-left) resumes too.
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class PauseMenu(
    activity: Activity,
    kit: UiKit,
    score: Int,
    private val onResume: () -> Unit,
    onRestart: () -> Unit,
    onMenu: () -> Unit,
) : FullScreen(activity, kit, "PAUSED", dark = true, onClosed = onResume) {

    init {
        setBackgroundColor(Ui.SCRIM_DARK)
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(kit.stageText(score.toString(), 64f, heavy = true))
            addView(kit.stageText("score so far", 14f, Palette.withAlpha(Color.WHITE, 180)))
            addView(kit.button("RESUME", UiKit.Style.FILLED) { close() },
                LinearLayout.LayoutParams(dp(240f), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(36f) })
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(kit.button("RESTART", UiKit.Style.LIGHT, small = true) { onRestart() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(kit.button("MENU", UiKit.Style.LIGHT, small = true) { onMenu() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(10f) })
            }, LinearLayout.LayoutParams(dp(240f), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                val size = dp(46f)
                addView(kit.toggleButton(R.drawable.ic_sound_on, R.drawable.ic_sound_off, activity.getString(R.string.cd_sound),
                    { Settings.soundEnabled }, { Settings.setSoundEnabled(it) }), LinearLayout.LayoutParams(size, size))
                addView(kit.toggleButton(R.drawable.ic_haptic_on, R.drawable.ic_haptic_off, activity.getString(R.string.cd_haptics),
                    { Settings.hapticsEnabled }, { Settings.setHapticsEnabled(it) }), LinearLayout.LayoutParams(size, size).apply { leftMargin = dp(14f) })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(40f) })
        }
        content.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
    }
}
