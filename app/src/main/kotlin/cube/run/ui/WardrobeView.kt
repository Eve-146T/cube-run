package cube.run.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import cube.run.R
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Shards
import cube.run.data.Skins
import cube.run.data.Wardrobe
import cube.run.ui.Anim.move
import kotlin.math.abs

/**
 * The wardrobe, full screen on the engine's dark stage: three categories —
 * CUBE skins, BUBBLE skins and TRAILs — as candy tabs under the title. The
 * game itself shows the item ([Stage.SKINS] + the preview fields): the cube
 * turning, the cube inside its bubble, or the cube looping through the air
 * shedding its trail. This page frames it: name and price, dots, arrows or
 * a swipe to browse, EQUIP / BUY.
 */
@SuppressLint("SetTextI18n", "ViewConstructor", "ClickableViewAccessibility")
class WardrobeView(activity: Activity, kit: UiKit, onClose: () -> Unit) : Page(activity, kit, "WARDROBE", dark = true, onClosed = onClose) {

    private var cat = Wardrobe.CUBE
    private var index = Progress.equipped(cat)
    private val balance = kit.iconPill(CoinIcon(), "", Theme.INK, 16f)
    private val tabs = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; clipChildren = false; clipToPadding = false }
    private val tabViews = ArrayList<TextView>()
    private val name = kit.stageText("", 32f, stroke = 4f)
    private val status = kit.stageText("", 15f, Theme.alpha(Theme.WHITE, 220), weight = 600, stroke = 2f)
    private val shardBar = kit.segments(10).apply { visibility = View.GONE; offColor = Theme.alpha(Theme.WHITE, 60) }
    private val dots = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    private val action: CandyButton
    private val left: View
    private val right: View
    private var paying = false
    private var downX = 0f
    private var downY = 0f
    private var swiped = false

    init {
        Stage.previewCat = cat
        Stage.mode = Stage.SKINS
        applyPreview()
        addRight(balance)

        // ---- tabs under the title
        for (c in Wardrobe.cats) {
            val t = kit.text(Wardrobe.label(c), 13f, Theme.WHITE, 700).apply {
                letterSpacing = 0.1f
                setPadding(dp(16f), dp(7f), dp(16f), dp(7f))
                setOnClickListener { switchTo(c) }
            }
            tabViews.add(t)
            tabs.addView(t, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(4f); rightMargin = dp(4f) })
        }
        content.addView(tabs, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.TOP; topMargin = dp(4f) })

        // ---- side arrows, mid-screen
        left = kit.chip(R.drawable.ic_chevron_left, Theme.WHITE, Theme.INK, activity.getString(R.string.cd_prev)) { step(-1) }
        right = kit.chip(R.drawable.ic_chevron_right, Theme.WHITE, Theme.INK, activity.getString(R.string.cd_next)) { step(1) }
        content.addView(left, FrameLayout.LayoutParams(dp(54f), dp(58f)).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START; leftMargin = dp(12f) })
        content.addView(right, FrameLayout.LayoutParams(dp(54f), dp(58f)).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END; rightMargin = dp(12f) })

        // ---- bottom: name, status, dots, action
        action = kit.button("", Theme.PLAY, UiKit.Size.BIG) { act() }
        val bottom = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false; clipToPadding = false
            addView(name)
            addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = -dp(4f) })
            addView(shardBar, LinearLayout.LayoutParams(dp(180f), dp(10f)).apply { topMargin = dp(8f) })
            addView(dots, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
            addView(action, LinearLayout.LayoutParams(dp(230f), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18f) })
        }
        content.addView(bottom, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM; bottomMargin = dp(36f)
        })
        render()
        Anim.stagger(tabs, dpf(16f), 160, 50)
        Anim.popIn(left, 260, 0.5f); Anim.popIn(right, 300, 0.5f)
        Anim.riseIn(bottom, 220, dpf(40f))
    }

    /** Push the browsed item into the engine's preview slots. */
    private fun applyPreview() {
        Stage.previewCat = cat
        when (cat) {
            Wardrobe.CUBE -> Stage.previewSkin = index
            Wardrobe.BUBBLE -> Stage.previewBubble = index
            else -> Stage.previewTrail = index
        }
    }

    private fun switchTo(c: Int) {
        if (c == cat) return
        SoundFx.play("tap"); Haptics.tick()
        Stage.clearPreview()
        cat = c
        index = Progress.equipped(cat)
        applyPreview() // the stage morphs: the bubble inflates / the cube glides out onto its loop
        Anim.riseIn(name, distancePx = dpf(16f), duration = 240)
        render()
    }

    /** A horizontal swipe anywhere browses; taps fall through to the buttons. Returns true on a swipe. */
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

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = swipe(ev)
    override fun onTouchEvent(event: MotionEvent): Boolean { swipe(event); return true }

    private fun step(d: Int) {
        val n = Wardrobe.count(cat)
        index = ((index + d) % n + n) % n
        applyPreview()
        Stage.previewKicks.incrementAndGet() // the stage spin-flips the cube with a pop
        Haptics.tick()
        Anim.slideIn(name, fromX = d * dpf(40f), duration = 220)
        val arrow = if (d > 0) right else left
        Anim.reset(arrow)
        arrow.translationX = d * dpf(8f)
        arrow.move().translationX(0f).setDuration(220).setInterpolator(Anim.ease).start()
        render()
    }

    private fun render() {
        val owned = Progress.owns(cat, index)
        val equipped = Progress.equipped(cat) == index
        val price = Wardrobe.price(cat, index)
        val shardSkin = if (cat == Wardrobe.CUBE) Skins.get(index).takeIf { it.shardOnly } else null
        kit.labelOf(balance).text = Progress.coins.toString()
        name.text = Wardrobe.name(cat, index).uppercase()
        shardBar.visibility = View.GONE
        status.text = when {
            equipped -> "EQUIPPED"
            owned -> "OWNED"
            shardSkin != null -> { // how far the shards have come: "34 / 100 ember shards" + a bar
                val k = Shards.get(shardSkin.shardType)
                val have = Progress.shards(k.id)
                shardBar.visibility = View.VISIBLE
                shardBar.level = (have * 10 / shardSkin.shardsNeeded).coerceIn(0, 10)
                shardBar.color = Theme.hsv(k.hue, 0.75f, 1f)
                "$have / ${shardSkin.shardsNeeded} ${k.name.uppercase()}"
            }
            else -> ""
        }
        status.setTextColor(when {
            equipped -> Theme.MINT
            shardSkin != null && !owned -> Theme.hsv(Shards.get(shardSkin.shardType).hue, 0.6f, 1f)
            else -> Theme.alpha(Theme.WHITE, 220)
        })
        val canUnlock = shardSkin != null && Progress.shards(shardSkin.shardType) >= shardSkin.shardsNeeded
        action.setLabel(when {
            equipped -> "EQUIPPED"
            owned -> "EQUIP"
            shardSkin != null -> android.text.SpannableStringBuilder("UNLOCK  ").also { sb ->
                val d = ShardIcon(Theme.hsv(Shards.get(shardSkin.shardType).hue, 0.75f, 1f)); val px = kit.dp(24f); d.setBounds(0, 0, px, px)
                sb.append("\u2009 ", CenteredImageSpan(d), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE).append(" ${shardSkin.shardsNeeded}")
            }
            else -> android.text.SpannableStringBuilder("BUY ").append(kit.coins(price, 22f))
        })
        action.color = when {
            equipped -> Theme.alpha(Theme.WHITE, 200)
            owned -> Theme.PLAY
            shardSkin != null -> if (canUnlock) Theme.hsv(Shards.get(shardSkin.shardType).hue, 0.7f, 1f) else Theme.alpha(Theme.MUTED, 200)
            price <= Progress.coins -> Theme.GOLD
            else -> Theme.alpha(Theme.MUTED, 200)
        }
        action.alpha = if (equipped) 0.7f else 1f
        for ((i, t) in tabViews.withIndex()) {
            val on = Wardrobe.cats[i] == cat
            t.setTextColor(if (on) Theme.INK else Theme.WHITE)
            t.background = GradientDrawable().apply {
                cornerRadius = dpf(20f)
                setColor(if (on) Theme.WHITE else Theme.alpha(Theme.WHITE, 40))
                setStroke(dp(1.5f), Theme.alpha(Theme.WHITE, if (on) 0 else 90))
            }
        }
        dots.removeAllViews()
        val n = Wardrobe.count(cat)
        for (i in 0 until n) {
            dots.addView(View(activity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(when { i == index -> Theme.WHITE; Progress.owns(cat, i) -> Theme.alpha(Theme.WHITE, 130); else -> Theme.alpha(Theme.WHITE, 55) })
                }
            }, LinearLayout.LayoutParams(dp(if (i == index) 9f else 6f), dp(if (i == index) 9f else 6f)).apply { leftMargin = dp(3f); rightMargin = dp(3f) })
        }
    }

    private fun act() {
        if (paying) return
        val before = Progress.coins
        val id = index; val c = cat
        val shardSkin = if (cat == Wardrobe.CUBE) Skins.get(index).takeIf { it.shardOnly } else null
        when {
            Progress.equipped(cat) == index -> {}
            Progress.owns(cat, index) -> { Progress.equip(cat, index); SoundFx.play("tap"); Haptics.tick(); Anim.pulse(name, 1.15f); Stage.previewKicks.incrementAndGet(); render() }
            shardSkin != null -> { // shards, not coins
                if (Progress.unlockWithShards(index)) {
                    Progress.equip(c, id)
                    Stage.previewBuys.incrementAndGet()
                    Anim.pulse(name, 1.25f)
                    render()
                } else { SoundFx.play("tap", rate = 0.6f); Haptics.tick(); Anim.shake(status, dpf(8f)) }
            }
            Progress.buy(cat, index) -> { // paid: coins fly from the balance into the button, then the cube celebrates and it's yours
                paying = true
                // Commit the choice with the purchase; its visual completion may
                // be cancelled if the user leaves the page while coins are flying.
                Progress.equip(c, id)
                Haptics.click()
                val ms = PayFx.fly(this, kit, balance, action, n = 6, onDone = {
                    paying = false
                    if (!closing && cat == c && index == id) {
                        Stage.previewBuys.incrementAndGet()
                        Anim.pulse(name, 1.25f)
                        render()
                    }
                })
                Anim.countTo(kit.labelOf(balance), before, Progress.coins, ms)
            }
            else -> { // can't afford: nudge the balance
                SoundFx.play("tap", rate = 0.6f); Haptics.tick()
                Anim.shake(balance, dpf(8f))
            }
        }
    }

    override fun onBack() {
        Stage.clearPreview()
        Stage.mode = Stage.NONE
        close()
    }
}
