package cube.run.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.core.Stage
import cube.run.data.Progress

/**
 * The shop, full screen on the engine's stage with your cube up top: bubble
 * shields to stock up on, one bold card per power-up whose duration you
 * level up, then the perks. Cards are glass over the stage — a coloured
 * header band with the item's icon, what you have now and what the next
 * level gives, a bar of segments that pops as it fills, and a coin price
 * button. Every purchase goes through [Progress] and plays out on the cube
 * (see [Stage.demoRequests]): the bubble goes up around it, coins fly into
 * it, it lifts on a jet of flame…
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class ShopView(activity: Activity, kit: UiKit, onClose: () -> Unit) : Page(activity, kit, "SHOP", dark = true, onClosed = onClose) {

    private val list = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false; clipToPadding = false
        setPadding(dp(16f), dp(96f), dp(16f), dp(28f)) // the cube shows above the first card
    }
    private val balance = kit.iconPill(CoinIcon(), "", Theme.INK, 16f)
    private val bars = HashMap<String, SegmentBar>()
    private var first = true
    private val glass = Theme.alpha(Theme.WHITE, 36)
    private val glassLine = Theme.alpha(Theme.WHITE, 80)

    /** Each power-up's colour, matching its pickup on the track. */
    private fun colorOf(u: Progress.Upgrade): Int = when (u) {
        Progress.BUBBLE -> Theme.BUBBLE
        Progress.MAGNET -> Theme.MAGNET
        Progress.MULT -> Theme.MULT
        else -> Theme.JET
    }

    private fun iconOf(u: Progress.Upgrade): Drawable = when (u) {
        Progress.BUBBLE -> BubbleIcon()
        Progress.MAGNET -> MagnetIcon()
        Progress.MULT -> MultIcon()
        else -> JetIcon()
    }

    private fun blurb(u: Progress.Upgrade): String = when (u) {
        Progress.BUBBLE -> "How long a bubble stays up"
        Progress.MAGNET -> "Pulls in every coin from far away"
        Progress.MULT -> "Every row counts double"
        else -> "Fly above everything along a coin line"
    }

    private fun demoOf(u: Progress.Upgrade): Int = when (u) {
        Progress.BUBBLE -> Stage.DEMO_BUBBLE
        Progress.MAGNET -> Stage.DEMO_MAGNET
        Progress.MULT -> Stage.DEMO_MULT
        Progress.JET -> Stage.DEMO_JET
        Progress.HEADSTART -> Stage.DEMO_HEADSTART
        Progress.COINVALUE -> Stage.DEMO_COINS
        Progress.PORTALS -> Stage.DEMO_PORTAL
        else -> Stage.DEMO_BOX
    }

    init {
        Stage.mode = Stage.SHOP
        addRight(balance)
        content.addView(ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            clipChildren = false; clipToPadding = false
            addView(list)
        })
        render()
    }

    /** Rebuild the whole list from [Progress] (cheap; done after every purchase). */
    private fun render(popped: Progress.Upgrade? = null) {
        kit.labelOf(balance).text = Progress.coins.toString()
        list.removeAllViews()
        bars.clear()
        list.addView(bubbleCard(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        list.addView(heading("POWER-UPS"), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(22f); leftMargin = dp(8f); bottomMargin = dp(2f) })
        for (u in Progress.upgrades) {
            list.addView(upgradeCard(u), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
        }
        list.addView(heading("PERKS"), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(22f); leftMargin = dp(8f); bottomMargin = dp(2f) })
        list.addView(reviveCard(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
        for (u in Progress.perks) {
            list.addView(perkCard(u), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
        }
        if (first) { first = false; Anim.stagger(list, dpf(36f), 120, 45) }
        popped?.let { u -> // the segment just bought swells and settles
            val bar = bars[u.key] ?: return@let
            bar.popIndex = Progress.level(u) - 1
            ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 420; interpolator = Anim.spring
                addUpdateListener { a -> bar.pop = a.animatedValue as Float }
                start()
            }
        }
    }

    private fun heading(t: String) = kit.stageText(t, 13f, Theme.alpha(Theme.WHITE, 230), weight = 700, gravity = Gravity.START, stroke = 1.5f).apply { letterSpacing = 0.16f }

    /** A glass card with a coloured header band (icon + name + blurb). */
    private fun card(color: Int, icon: Drawable, name: String, blurb: String, body: LinearLayout.() -> Unit): View {
        val outer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            background = GradientDrawable().apply { cornerRadius = dpf(24f); setColor(glass); setStroke(dp(1.5f), glassLine) }
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(color, Theme.lighten(color, 0.22f))).apply {
                cornerRadii = floatArrayOf(dpf(24f), dpf(24f), dpf(24f), dpf(24f), 0f, 0f, 0f, 0f)
            }
            addView(ImageView(activity).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(44f), dp(44f)))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(kit.text(name, 19f, Theme.onColor(color), 700, Gravity.START))
                addView(kit.text(blurb, 12f, Theme.alpha(Theme.onColor(color), 215), 500, Gravity.START))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(12f) })
        }
        outer.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        outer.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            setPadding(dp(16f), dp(12f), dp(16f), dp(14f))
            body()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return outer
    }

    /** A rack of [slots] icons, the first [n] lit, "+k" past the rack. */
    private fun rack(slots: Int, n: Int, icon: () -> Drawable, plusColor: Int): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        for (i in 0 until slots) {
            addView(ImageView(activity).apply { setImageDrawable(icon()); alpha = if (i < n) 1f else 0.22f }, LinearLayout.LayoutParams(dp(28f), dp(28f)).apply { if (i > 0) leftMargin = dp(4f) })
        }
        if (n > slots) addView(kit.stageText("+${n - slots}", 15f, plusColor, stroke = 2f), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(4f) })
    }

    private fun bubbleCard(): View = card(Theme.BUBBLE, BubbleIcon(), "Bubble shield", "Double-tap in a run: takes one hit, smashes the row") {
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            addView(rack(5, Progress.bubbles, { BubbleIcon() }, Theme.lighten(Theme.CYAN, 0.5f)), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(priceButton(Progress.BUBBLE_PRICE) { if (Progress.buyBubble()) bought(null, Stage.DEMO_BUBBLE) else broke() },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(10f) })
        })
    }

    /** "now → next", the level bar, the price: shared by power-ups and perks. */
    private fun levelBody(host: LinearLayout, u: Progress.Upgrade, color: Int, now: String, next: String?) {
        val lvl = Progress.level(u)
        val price = Progress.nextPrice(u)
        val maxed = price == null
        host.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            addView(kit.stageText(now, 28f, Theme.lighten(color, 0.35f), stroke = 3f, gravity = Gravity.START).apply { maxLines = 1 })
            if (next != null) {
                addView(kit.stageText("→", 18f, Theme.alpha(Theme.WHITE, 200), stroke = 2f), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(6f); rightMargin = dp(6f) })
                addView(kit.stageText(next, 18f, Theme.alpha(Theme.WHITE, 230), stroke = 2f, gravity = Gravity.START).apply { maxLines = 1 })
            }
            addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(priceButton(price) { if (Progress.buyUpgrade(u)) bought(u, demoOf(u)) else broke() })
        })
        val bar = kit.segments(u.max).apply { level = lvl; this.color = if (maxed) Theme.GOLD else Theme.lighten(color, 0.15f); offColor = Theme.alpha(Theme.WHITE, 60) }
        bars[u.key] = bar
        host.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12f)).apply { topMargin = dp(12f) })
    }

    private fun upgradeCard(u: Progress.Upgrade): View {
        val color = colorOf(u)
        val lvl = Progress.level(u)
        val now = u.duration(lvl)
        val maxed = Progress.nextPrice(u) == null
        return card(color, iconOf(u), u.name, blurb(u)) {
            levelBody(this, u, color, "${fmt(now)} s", if (maxed) null else "${fmt(now + u.step)} s")
        }
    }

    private fun perkColor(u: Progress.Upgrade): Int = when (u) {
        Progress.HEADSTART -> Theme.ORANGE
        Progress.COINVALUE -> Theme.GOLD
        Progress.PORTALS -> Theme.MINT
        else -> Theme.GRAPE
    }

    private fun perkIcon(u: Progress.Upgrade): Drawable = when (u) {
        Progress.HEADSTART -> FlameIcon(Theme.YELLOW)
        Progress.COINVALUE -> CoinIcon()
        Progress.PORTALS -> PortalIcon(Theme.WHITE)
        else -> BoxIcon()
    }

    private fun perkBlurb(u: Progress.Upgrade): String = when (u) {
        Progress.HEADSTART -> "Boost taps already lit at the start"
        Progress.COINVALUE -> "Every coin is worth more"
        Progress.PORTALS -> "Portals to bonus worlds open sooner"
        else -> "Mystery boxes turn up more often"
    }

    private fun perkValue(u: Progress.Upgrade, lvl: Int): String = when (u) {
        Progress.HEADSTART -> "$lvl taps"
        Progress.COINVALUE -> "×${fmt(u.duration(lvl))}"
        Progress.PORTALS -> "${110 - 14 * lvl} rows"
        else -> "×${fmt(1f + 0.5f * lvl)}"
    }

    private fun perkCard(u: Progress.Upgrade): View {
        val color = perkColor(u)
        val lvl = Progress.level(u)
        val maxed = Progress.nextPrice(u) == null
        return card(color, perkIcon(u), u.name, perkBlurb(u)) {
            levelBody(this, u, color, perkValue(u, lvl), if (maxed) null else perkValue(u, lvl + 1))
        }
    }

    /** Second wind: a stock of revives. */
    private fun reviveCard(): View = card(Theme.PINK, HeartIcon(Theme.WHITE), "Second wind", "A crash is not the end: back up, bubbled, still running") {
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            addView(rack(3, Progress.revives, { HeartIcon(Theme.PINK) }, Theme.lighten(Theme.PINK, 0.4f)), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(priceButton(Progress.REVIVE_PRICE) { if (Progress.buyRevive()) bought(null, Stage.DEMO_REVIVE) else broke() },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(10f) })
        })
    }

    /** Gold when affordable, a quiet glass slab when not, a MAX badge when there is nothing left to buy. */
    private fun priceButton(price: Int?, onBuy: () -> Unit): View {
        if (price == null) return kit.pill("MAX", Theme.alpha(Theme.WHITE, 60), Theme.WHITE, 13f).apply { letterSpacing = 0.1f }
        val can = price <= Progress.coins
        return kit.button(kit.coins(price, 15f), if (can) Theme.GOLD else Theme.alpha(Theme.WHITE, 46), UiKit.Size.SMALL) { onBuy() }.apply {
            if (!can) setTextColor(Theme.alpha(Theme.WHITE, 170))
        }
    }

    private fun fmt(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)

    /** Bought: the cube plays it out, the balance bounces, a little confetti from the cube. */
    private fun bought(u: Progress.Upgrade?, demo: Int) {
        SoundFx.play("coin"); SoundFx.play("success", rate = 1.4f, vol = 0.5f); Haptics.success()
        Stage.demoRequests.set(demo)
        render(u)
        Anim.pulse(balance, 1.2f)
        addView(CelebrationView(activity, focusY = 0.16f, rays = false, count = 50, burst = true, seconds = 1.8f), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun broke() {
        SoundFx.play("tap", rate = 0.6f); Haptics.tick()
        Anim.shake(balance, dpf(8f))
    }

    override fun onBack() {
        Stage.mode = Stage.NONE
        close()
    }
}
