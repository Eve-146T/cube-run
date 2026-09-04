package cube.run.core.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import cube.run.core.Haptics
import cube.run.core.Palette
import cube.run.core.Progress
import cube.run.core.SoundFx

/**
 * The shop, full screen: bubble shields to stock up on, then one card per
 * power-up whose duration you level up — ten levels, a fill bar, the seconds
 * you have now and the seconds the next level adds. Every purchase goes
 * through [Progress] (which the game reads at run start).
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class ShopView(activity: Activity, kit: UiKit, onClose: () -> Unit) : FullScreen(activity, kit, "SHOP", dark = false, onClosed = onClose) {

    private val list = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18f), dp(4f), dp(18f), dp(18f))
    }
    private val balance = kit.text("", 20f, Ui.GOLD_INK, heavy = true, gravity = Gravity.END)

    /** Each power-up's colour, matching its pickup on the track. */
    private fun colorOf(u: Progress.Upgrade): Int = when (u) {
        Progress.BUBBLE -> Ui.CYAN
        Progress.MAGNET -> 0xFFFF5A4A.toInt()
        Progress.MULT -> 0xFFFF4FBF.toInt()
        else -> 0xFF4F7DFF.toInt()
    }

    private fun blurb(u: Progress.Upgrade): String = when (u) {
        Progress.BUBBLE -> "How long a bubble stays up once it's on."
        Progress.MAGNET -> "Pulls in every coin from far away."
        Progress.MULT -> "Every row counts double."
        else -> "Fly fast above everything along a coin line."
    }

    init {
        addRight(balance)
        content.addView(ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            addView(list)
        })
        render()
    }

    /** Rebuild the whole list from [Progress] (cheap; done after every purchase). */
    private fun render() {
        balance.text = "${Ui.COIN} ${Progress.coins}"
        list.removeAllViews()

        // ---- bubble shields: a stock, not a level
        list.addView(bubbleCard(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f) })

        header("POWER-UP DURATION", "Each level adds seconds. Ten levels each.")
        for (u in Progress.upgrades) {
            list.addView(upgradeCard(u), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10f) })
        }
    }

    private fun header(t: String, sub: String) {
        list.addView(kit.text(t, 13f, Ui.GREEN_INK, gravity = Gravity.START).apply { letterSpacing = 0.12f },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(22f); leftMargin = dp(4f) })
        list.addView(kit.text(sub, 12f, Ui.MUTED, bold = false, gravity = Gravity.START),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2f); leftMargin = dp(4f); bottomMargin = dp(2f) })
    }

    private fun cardFrame(color: Int): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        background = GradientDrawable().apply {
            cornerRadius = dp(22f).toFloat()
            setColor(Ui.CARD)
            setStroke(dp(2f), Palette.withAlpha(color, 140))
        }
    }

    /** A coloured badge with a glyph — the card's icon. */
    private fun badge(glyph: String, color: Int) = kit.text(glyph, 22f, Ui.CARD, heavy = true).apply {
        background = GradientDrawable().apply { cornerRadius = dp(14f).toFloat(); setColor(color) }
    }

    private fun bubbleCard(): View = cardFrame(Ui.CYAN).apply {
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(badge("◯", Ui.CYAN), LinearLayout.LayoutParams(dp(48f), dp(48f)))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(kit.text("Bubble shield", 17f, Ui.INK, heavy = true, gravity = Gravity.START))
                addView(kit.text("${Progress.bubbles} in stock", 13f, Ui.CYAN, gravity = Gravity.START))
                addView(kit.text("Double-tap in a run. Takes one hit and smashes the row.", 12f, Ui.MUTED, bold = false, gravity = Gravity.START))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(12f) })
            addView(priceButton(Progress.BUBBLE_PRICE) { if (Progress.buyBubble()) bought() else broke() },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(10f) })
        })
    }

    private fun upgradeCard(u: Progress.Upgrade): View {
        val color = colorOf(u)
        val lvl = Progress.level(u)
        val price = Progress.nextPrice(u)
        val now = u.duration(lvl)
        val maxed = price == null
        return cardFrame(color).apply {
            addView(LinearLayout(activity).apply { // name + level, then the seconds
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(badge(glyphOf(u), color), LinearLayout.LayoutParams(dp(48f), dp(48f)))
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(kit.text(u.name, 17f, Ui.INK, heavy = true, gravity = Gravity.START))
                    addView(kit.text(blurb(u), 12f, Ui.MUTED, bold = false, gravity = Gravity.START))
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(12f) })
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.END
                    addView(kit.text("${fmt(now)} s", 22f, color, heavy = true, gravity = Gravity.END))
                    addView(kit.text(if (maxed) "MAX" else "level $lvl / ${Progress.MAX_LEVEL}", 11f, Ui.MUTED, gravity = Gravity.END))
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8f) })
            })
            addView(levelBar(lvl, Progress.MAX_LEVEL, color), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10f)).apply { topMargin = dp(12f) })
            addView(LinearLayout(activity).apply { // what the next level buys + the button
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val next = if (maxed) "Maxed out at ${fmt(now)} s" else "Next level: ${fmt(now + u.step)} s  ·  max ${fmt(u.duration(Progress.MAX_LEVEL))} s"
                addView(kit.text(next, 12f, Ui.MUTED, bold = false, gravity = Gravity.START), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(priceButton(price) { if (Progress.buyUpgrade(u)) bought() else broke() },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(10f) })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10f) })
        }
    }

    private fun glyphOf(u: Progress.Upgrade): String = when (u) {
        Progress.BUBBLE -> "◯"
        Progress.MAGNET -> "U"
        Progress.MULT -> "×2"
        else -> "▲"
    }

    /** Gold when affordable, outlined when not, muted MAX when there is nothing left to buy. */
    private fun priceButton(price: Int?, onBuy: () -> Unit): View {
        val style = when {
            price == null -> UiKit.Style.MUTED
            price <= Progress.coins -> UiKit.Style.GOLD
            else -> UiKit.Style.OUTLINE
        }
        return kit.button(if (price == null) "MAX" else "${Ui.COIN} $price", style, small = true) { if (price != null) onBuy() }
    }

    private fun fmt(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)

    /** [level] of [max] segments lit in [color] (gold once maxed), so you can watch it fill. */
    private fun levelBar(level: Int, max: Int, color: Int): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        for (i in 0 until max) {
            addView(View(activity).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(4f).toFloat()
                    setColor(if (i < level) (if (level >= max) Ui.GOLD else color) else Palette.withAlpha(Ui.MUTED, 50))
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { if (i > 0) leftMargin = dp(3f) })
        }
    }

    private fun bought() {
        SoundFx.play("coin"); Haptics.success()
        render()
    }

    private fun broke() {
        SoundFx.play("tap", rate = 0.6f); Haptics.tick()
        // nudge the balance so it's obvious what's missing
        balance.animate().cancel()
        balance.translationX = 0f
        balance.animate().translationX(dp(6f).toFloat()).setDuration(50).withEndAction {
            balance.animate().translationX(0f).setDuration(120).start()
        }.start()
    }
}
