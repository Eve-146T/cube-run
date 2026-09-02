package cube.run.core.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.view.Gravity
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import cube.run.core.Haptics
import cube.run.core.Palette
import cube.run.core.Progress
import cube.run.core.SoundFx

/**
 * The pre-run shop: spend banked coins on permanent upgrades and bubble
 * shields (skins live in [SkinsView]). Pure UI-thread Android views; every
 * purchase goes through [Progress] (which the game reads at run start).
 *
 * Programmatic single-locale game UI, so SetTextI18n / ViewConstructor are
 * intentionally suppressed.
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class ShopView(
    private val activity: Activity,
    private val kit: UiKit,
    private val onClose: () -> Unit,
) : FrameLayout(activity) {

    private fun dp(v: Float) = kit.dp(v)

    private val list = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22f), dp(4f), dp(22f), dp(18f))
    }
    private val balance = kit.text("", 20f, Ui.GOLD_INK, heavy = true, gravity = Gravity.END)

    init {
        setBackgroundColor(Ui.SCRIM)
        isClickable = true // swallow touches so the game underneath doesn't start
        alpha = 0f
        animate().alpha(1f).setDuration(200).start()

        val card = kit.card()
        card.addView(LinearLayout(activity).apply { // header: title + balance
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22f), dp(18f), dp(22f), dp(6f))
            addView(kit.text("SHOP", 24f, Ui.INK, heavy = true, gravity = Gravity.START),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(balance)
        })
        card.addView(ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            addView(list)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        card.addView(kit.button("CLOSE", UiKit.Style.FILLED) { close() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(22f); rightMargin = dp(22f); topMargin = dp(6f); bottomMargin = dp(18f)
            })

        addView(card, LayoutParams(dp(330f), LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.CENTER
            topMargin = dp(56f); bottomMargin = dp(56f)
        })
        render()
    }

    private fun close() {
        animate().alpha(0f).setDuration(160).withEndAction {
            (parent as? FrameLayout)?.removeView(this)
            onClose()
        }.start()
    }

    /** Rebuild the whole list from [Progress] (cheap; done after every purchase). */
    private fun render() {
        balance.text = "${Ui.COIN} ${Progress.coins}"
        list.removeAllViews()

        header("POWER-UP DURATION")
        list.addView(kit.text("Each level adds a few seconds. Ten levels each.", 12f, Ui.MUTED, bold = false, gravity = Gravity.START))
        for (u in Progress.upgrades) {
            val lvl = Progress.level(u)
            val price = Progress.nextPrice(u)
            val now = u.duration(lvl); val max = u.duration(Progress.MAX_LEVEL)
            val desc = if (price == null) "${fmt(now)} s — maxed out" else "${fmt(now)} s now  ·  ${fmt(now + u.step)} s next  ·  ${fmt(max)} s max"
            list.addView(itemRow(u.name, desc, lvl to Progress.MAX_LEVEL, price) {
                if (Progress.buyUpgrade(u)) bought() else broke()
            })
        }

        header("BUBBLE SHIELD")
        list.addView(itemRow(
            "Bubble  ×${Progress.bubbles} in stock",
            "Double-tap during a run. Absorbs one crash and smashes the obstacle.",
            null, Progress.BUBBLE_PRICE,
        ) { if (Progress.buyBubble()) bought() else broke() })
    }

    private fun header(t: String) {
        list.addView(kit.text(t, 13f, Ui.GREEN_INK, gravity = Gravity.START).apply { letterSpacing = 0.12f },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16f); bottomMargin = dp(4f) })
    }

    /** One list entry: title + description + optional level pips on the left, a price button on the right. */
    private fun itemRow(title: String, desc: String, pips: Pair<Int, Int>?, price: Int?, onBuy: () -> Unit): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7f), 0, dp(7f))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(kit.text(title, 16f, Ui.INK, gravity = Gravity.START))
                addView(kit.text(desc, 12f, Ui.MUTED, bold = false, gravity = Gravity.START))
                if (pips != null) {
                    val (l, m) = pips
                    addView(levelBar(l, m), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8f)).apply { topMargin = dp(6f); rightMargin = dp(6f) })
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val style = when {
                price == null -> UiKit.Style.MUTED
                price <= Progress.coins -> UiKit.Style.GOLD
                else -> UiKit.Style.OUTLINE
            }
            addView(kit.button(if (price == null) "MAX" else "${Ui.COIN} $price", style, small = true) { if (price != null) onBuy() },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(10f) })
        }

    private fun fmt(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)

    /** [level] of [max] segments lit, so you can watch it fill toward the max. */
    private fun levelBar(level: Int, max: Int): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        for (i in 0 until max) {
            addView(View(activity).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(3f).toFloat()
                    setColor(if (i < level) (if (level >= max) Ui.GOLD else Ui.GREEN_INK) else Palette.withAlpha(Ui.MUTED, 60))
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
