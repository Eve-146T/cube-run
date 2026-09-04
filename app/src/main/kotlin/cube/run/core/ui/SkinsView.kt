package cube.run.core.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import cube.run.R
import cube.run.core.Haptics
import cube.run.core.Palette
import cube.run.core.Progress
import cube.run.core.Skins
import cube.run.core.SoundFx
import cube.run.core.Stage
import kotlin.math.abs

/**
 * The wardrobe, full screen: the game shows the cube itself on a dark stage
 * ([Stage.SKINS], [Stage.previewSkin]) while this page frames it — name and
 * price below, dots for position, arrows or a horizontal swipe to browse,
 * EQUIP / BUY. Nothing here draws the cube: the engine does, in 3D.
 */
@SuppressLint("SetTextI18n", "ViewConstructor", "ClickableViewAccessibility")
class SkinsView(activity: Activity, kit: UiKit, onClose: () -> Unit) : FullScreen(activity, kit, "SKINS", dark = true, onClosed = onClose) {

    private var index = Progress.skin.coerceIn(0, Skins.all.size - 1)
    private val balance = kit.stageText("", 18f, Ui.GOLD, heavy = true)
    private val name = kit.stageText("", 30f, heavy = true)
    private val status = kit.stageText("", 15f, Palette.withAlpha(Color.WHITE, 200))
    private val dots = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    private val action: TextView
    private val left: View
    private val right: View
    private var downX = 0f
    private var downY = 0f

    init {
        Stage.previewSkin = index
        Stage.mode = Stage.SKINS
        addRight(balance)

        // ---- side arrows, mid-screen: big round targets that bounce when used
        left = kit.iconButton(R.drawable.ic_chevron_left, Color.WHITE, activity.getString(R.string.cd_prev)) { step(-1) }
        right = kit.iconButton(R.drawable.ic_chevron_right, Color.WHITE, activity.getString(R.string.cd_next)) { step(1) }
        content.addView(left, FrameLayout.LayoutParams(dp(60f), dp(60f)).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START; leftMargin = dp(12f) })
        content.addView(right, FrameLayout.LayoutParams(dp(60f), dp(60f)).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END; rightMargin = dp(12f) })

        // ---- bottom: name, status, dots, action
        action = kit.button("", UiKit.Style.FILLED) { act() }
        val bottom = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(name)
            addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2f) })
            addView(dots, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14f) })
            addView(action, LinearLayout.LayoutParams(dp(220f), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18f) })
        }
        content.addView(bottom, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM; bottomMargin = dp(24f)
        })
        render()
    }

    /** A horizontal swipe anywhere browses; taps fall through to the buttons. Returns true on a swipe. */
    private var swiped = false // one step per gesture

    private fun swipe(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; swiped = false }
            MotionEvent.ACTION_MOVE -> {
                if (swiped) return true
                val dx = ev.x - downX; val dy = ev.y - downY
                if (abs(dx) > dp(48f) && abs(dx) > abs(dy) * 1.5f) {
                    swiped = true
                    step(if (dx < 0) 1 else -1)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { val was = swiped; swiped = false; return was }
        }
        return false
    }

    // a touch on a child (arrow/button) is watched from here; a touch on the empty
    // stage lands in onTouchEvent directly — both feed the same swipe detector
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = swipe(ev)
    override fun onTouchEvent(event: MotionEvent): Boolean { swipe(event); return true }

    private fun step(d: Int) {
        val n = Skins.all.size
        index = ((index + d) % n + n) % n
        Stage.previewSkin = index
        SoundFx.play("tick", rate = 1.3f, vol = 0.5f); Haptics.tick()
        name.translationX = d * dp(40f).toFloat(); name.alpha = 0f
        name.animate().translationX(0f).alpha(1f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        val arrow = if (d > 0) right else left // the arrow you went through nudges along
        arrow.animate().cancel()
        arrow.translationX = d * dp(8f).toFloat()
        arrow.animate().translationX(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        render()
    }

    private fun render() {
        val s = Skins.all[index]
        val owned = Progress.ownsSkin(s.id)
        val equipped = Progress.skin == s.id
        balance.text = "${Ui.COIN} ${Progress.coins}"
        name.text = s.name.uppercase()
        status.text = when {
            equipped -> "Equipped"
            owned -> "Owned"
            else -> "${Ui.COIN} ${s.price}"
        }
        status.setTextColor(when {
            equipped -> kit.accent
            owned -> Palette.withAlpha(Color.WHITE, 200)
            s.price <= Progress.coins -> Ui.GOLD
            else -> Palette.withAlpha(Color.WHITE, 140)
        })
        action.text = when {
            equipped -> "EQUIPPED"
            owned -> "EQUIP"
            else -> "BUY  ${Ui.COIN} ${s.price}"
        }
        action.alpha = if (equipped) 0.55f else 1f
        dots.removeAllViews()
        for (i in Skins.all.indices) {
            dots.addView(View(activity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (i == index) Color.WHITE else Palette.withAlpha(Color.WHITE, 70))
                }
            }, LinearLayout.LayoutParams(dp(if (i == index) 9f else 6f), dp(if (i == index) 9f else 6f)).apply { leftMargin = dp(3f); rightMargin = dp(3f) })
        }
    }

    private fun act() {
        val s = Skins.all[index]
        when {
            Progress.skin == s.id -> {}
            Progress.ownsSkin(s.id) -> { Progress.selectSkin(s.id); SoundFx.play("tap"); Haptics.tick(); render() }
            Progress.buySkin(s.id) -> { Progress.selectSkin(s.id); SoundFx.play("coin"); Haptics.success(); render() }
            else -> { // can't afford: nudge the balance
                SoundFx.play("tap", rate = 0.6f); Haptics.tick()
                balance.animate().cancel(); balance.translationX = 0f
                balance.animate().translationX(dp(6f).toFloat()).setDuration(50).withEndAction {
                    balance.animate().translationX(0f).setDuration(120).start()
                }.start()
            }
        }
    }

    override fun onBack() {
        Stage.previewSkin = -1
        Stage.mode = Stage.NONE
        close()
    }
}
