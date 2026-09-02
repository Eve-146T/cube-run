package cube.run.core.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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
class SkinsView(
    private val activity: Activity,
    private val kit: UiKit,
    private val onClose: () -> Unit,
) : FrameLayout(activity) {

    private fun dp(v: Float) = kit.dp(v)
    private var index = Progress.skin.coerceIn(0, Skins.all.size - 1)
    private val balance = kit.text("", 18f, Ui.GOLD, heavy = true).also { shadow(it) }
    private val name = kit.text("", 30f, Ui.CARD, heavy = true).also { shadow(it) }
    private val status = kit.text("", 15f, Palette.withAlpha(Ui.CARD, 200)).also { shadow(it) }
    private val dots = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    private val action: TextView
    private val top = LinearLayout(activity)
    private var downX = 0f
    private var downY = 0f

    private fun shadow(t: TextView) = t.setShadowLayer(dp(6f).toFloat(), 0f, dp(2f).toFloat(), 0xA0000000.toInt())

    init {
        isClickable = true // the page owns every touch: the game must not start under it
        alpha = 0f
        animate().alpha(1f).setDuration(220).start()
        Stage.previewSkin = index
        Stage.mode = Stage.SKINS

        // ---- header: title + balance
        top.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24f), 0, dp(24f), 0)
            addView(kit.text("SKINS", 26f, Ui.CARD, heavy = true, gravity = Gravity.START).also { shadow(it); it.letterSpacing = 0.06f },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(balance)
        }
        addView(top, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(48f) })

        // ---- side arrows, mid-screen
        addView(arrow("‹") { step(-1) }, LayoutParams(dp(56f), dp(56f)).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START; leftMargin = dp(10f) })
        addView(arrow("›") { step(1) }, LayoutParams(dp(56f), dp(56f)).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END; rightMargin = dp(10f) })

        // ---- bottom: name, status, dots, action, close
        action = kit.button("", UiKit.Style.FILLED) { act() }
        val bottom = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(name)
            addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2f) })
            addView(dots, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14f) })
            addView(action, LinearLayout.LayoutParams(dp(220f), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18f) })
            addView(kit.button("CLOSE", UiKit.Style.OUTLINE, small = true) { close() }.apply { setTextColor(Ui.CARD) },
                LinearLayout.LayoutParams(dp(140f), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
        }
        addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM; bottomMargin = dp(40f)
        })
        setOnApplyWindowInsetsListener { _, insets ->
            val top: Int; val bottomInset: Int
            if (Build.VERSION.SDK_INT >= 30) {
                top = insets.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top
                bottomInset = insets.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout()).bottom
            } else {
                @Suppress("DEPRECATION") top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION") bottomInset = insets.systemWindowInsetBottom
            }
            (this.top.layoutParams as LayoutParams).topMargin = maxOf(dp(48f), top + dp(12f))
            (bottom.layoutParams as LayoutParams).bottomMargin = dp(40f) + bottomInset
            requestLayout()
            insets
        }
        render()
    }

    private fun arrow(glyph: String, onClick: () -> Unit) = kit.text(glyph, 34f, Ui.CARD, heavy = true).apply {
        shadow(this)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Palette.withAlpha(Ui.CARD, 40)) }
        isClickable = true
        setOnClickListener { onClick() }
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
            owned -> Palette.withAlpha(Ui.CARD, 200)
            s.price <= Progress.coins -> Ui.GOLD
            else -> Palette.withAlpha(Ui.CARD, 140)
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
                    setColor(if (i == index) Ui.CARD else Palette.withAlpha(Ui.CARD, 70))
                }
            }, LinearLayout.LayoutParams(dp(if (i == index) 10f else 7f), dp(if (i == index) 10f else 7f)).apply { leftMargin = dp(4f); rightMargin = dp(4f) })
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

    private fun close() {
        Stage.previewSkin = -1
        Stage.mode = Stage.NONE
        animate().alpha(0f).setDuration(160).withEndAction {
            (parent as? FrameLayout)?.removeView(this)
            onClose()
        }.start()
    }
}
