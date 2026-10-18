package cube.run.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import cube.run.R
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Shards
import cube.run.data.Wardrobe

/**
 * What came out of a mystery box bought in the shop, on a white candy pill in
 * the showroom under the prize: "+2136" with a coin, "+4" with a bubble, a
 * shard count with the shard's name, or a new cosmetic's category and name.
 */
@SuppressLint("ViewConstructor")
internal class ShopRewardPill(activity: Activity, kit: UiKit, reward: Progress.BoxReward) : LinearLayout(activity) {
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        tag = "shop_gift_reward"
        val lip = when (reward.kind) {
            Progress.BoxReward.SKIN -> Theme.PINK
            Progress.BoxReward.BUBBLE -> Theme.CYAN
            Progress.BoxReward.SHARDS -> Theme.hsv(Shards.get(reward.id).hue, 0.7f, 0.9f)
            else -> Theme.GOLD
        }
        background = TrophyRoom.slab(kit, 18f, lip = Theme.darken(lip, 0.15f))
        setPadding(kit.dp(18f), kit.dp(6f), kit.dp(18f), kit.dp(6f) + kit.CARD_LIP)
        val locale = activity.resources.configuration.locales[0]
        // Wrap, not match, the pill: the small line must count towards its width too.
        fun sub(t: String, color: Int) = addView(kit.text(t, 11f, color, 700).apply {
            letterSpacing = kit.tracking(0.1f); setSingleLine()
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        fun big(v: android.view.View) = addView(v, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        when (reward.kind) {
            Progress.BoxReward.SKIN -> {
                sub(activity.getString(R.string.text_new_cosmetic, activity.gameText(Wardrobe.label(reward.cat))), Theme.darken(Theme.PINK, .35f))
                big(kit.text(activity.gameText(Wardrobe.name(reward.cat, reward.id)).uppercase(locale), 20f, Theme.INK, 700).apply { setSingleLine() })
            }
            Progress.BoxReward.SHARDS -> {
                val k = Shards.get(reward.id)
                sub(activity.gameText(k.name).uppercase(locale), Theme.darken(Theme.hsv(k.hue, 0.7f, 0.9f), .45f))
                big(kit.iconText(ShardIcon(Theme.hsv(k.hue, 0.75f, 1f)), "+${reward.amount}", 22f, Theme.INK))
            }
            Progress.BoxReward.BUBBLE -> big(kit.iconText(BubbleIcon(), "+${reward.amount}", 22f, Theme.INK))
            else -> big(kit.iconText(CoinIcon(), "+${reward.amount}", if (reward.rare) 26f else 22f, Theme.INK))
        }
        contentDescription = (0 until childCount).joinToString(" ") { i ->
            when (val v = getChildAt(i)) {
                is android.widget.TextView -> v.text
                is LinearLayout -> kit.labelOf(v).text
                else -> ""
            }
        }
    }
}

/**
 * A mystery box bought in the shop, after the coins have flown: [await] watches the showroom
 * burst it, then [show] puts the [ShopRewardPill] under the prize (at the top of a short pane
 * with no showroom) and flies any coins it held into the bank. A tap on the showroom [skip]s
 * the show along; leaving [end]s it at once (the prize is banked already).
 */
internal class ShopGiftReveal(private val host: FrameLayout, private val kit: UiKit, private val balance: LinearLayout, private val showroom: FrameLayout) {
    /** Where the prize hangs: the engine puts it one unit right of the middle. */
    private val anchor = View(host.context)
    private var pill: View? = null
    private val pills = mutableSetOf<View>()
    private var watch: Runnable? = null

    /** The pill is up. */
    val showing: Boolean get() = pill != null

    init {
        showroom.addView(anchor, FrameLayout.LayoutParams(kit.dp(1f), kit.dp(1f), Gravity.CENTER).apply { leftMargin = kit.dp(74f) })
    }

    /** Call [onOpen] once the showroom box has burst past [opened] (or after a few seconds: the reward is banked anyway). */
    fun await(opened: Int, onOpen: () -> Unit) {
        val deadline = SystemClock.uptimeMillis() + 3000
        val next = object : Runnable {
            override fun run() {
                if (watch !== this) return
                if (Stage.shopGiftOpened.get() != opened || SystemClock.uptimeMillis() > deadline || !host.isAttachedToWindow) {
                    watch = null
                    onOpen()
                } else host.postOnAnimation(this)
            }
        }
        watch = next
        host.postOnAnimation(next)
    }

    /** Say what [box] gave; the bank shows [shown] until its coins land. Returns the bank's count-up, if any. */
    fun show(box: Progress.BoxReward, compact: Boolean, top: Int, shown: Int): ValueAnimator? {
        clearPills()
        val p = ShopRewardPill(host.context as Activity, kit, box)
        pill = p
        pills.add(p)
        if (compact) host.addView(p, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = top + kit.dp(8f) })
        else {
            showroom.addView(p, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = kit.dp(6f) })
            p.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> // under the prize, as far as the edge allows
                v.translationX = minOf(anchor.x + anchor.width / 2f - showroom.width / 2f, (showroom.width - v.width) / 2f - kit.dpf(10f)).coerceAtLeast(0f)
            }
        }
        Anim.popIn(p, 0, 0.4f, 380)
        // As long as the engine holds the prize up (a new cosmetic gets a proper look).
        val hold = when (box.kind) { Progress.BoxReward.SKIN -> 2700L; Progress.BoxReward.COINS -> 1500L; else -> 1800L }
        host.postDelayed({ if (pill === p) dismiss() }, hold)
        if (box.kind != Progress.BoxReward.COINS) { kit.labelOf(balance).text = Progress.coins.toString(); return null }
        val ms = PayFx.fly(host, kit, if (compact) p else anchor, balance, n = 6, onDone = {})
        return Anim.countTo(kit.labelOf(balance), shown, Progress.coins, ms)
    }

    /** Open the box now, or send the prize into the cube and drop the pill. */
    fun skip() {
        Stage.shopGiftSkips.incrementAndGet()
        dismiss()
    }

    private fun dismiss() {
        val p = pill ?: return
        pill = null
        p.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(200).setInterpolator(Anim.ease)
            .setUpdateListener { Anim.repaint(host) }
            .withEndAction { (p.parent as? ViewGroup)?.removeView(p); pills.remove(p) }.start()
    }

    /** Also remove a pill whose fade was interrupted by navigation or another reveal. */
    private fun clearPills() {
        for (p in pills) {
            p.animate().cancel()
            (p.parent as? ViewGroup)?.removeView(p)
        }
        pills.clear()
        pill = null
    }

    fun end() {
        if (watch != null || pill != null) Stage.shopGiftSkips.incrementAndGet()
        watch = null
        clearPills()
    }
}
