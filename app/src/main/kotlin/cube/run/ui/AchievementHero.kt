package cube.run.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import cube.run.data.Achievements

/**
 * The top of the achievement page: the medal case. One slot per medal (bronze, silver, gold,
 * diamond) and one for challenge plaques, each with how many of them you have collected out
 * of how many there are. Only claimed rewards count: claiming is what fills the case.
 */
@SuppressLint("ViewConstructor")
internal class AchievementHero(context: Context, private val kit: UiKit) : LinearLayout(context) {
    private val icons = ArrayList<ImageView>()
    private val counts = ArrayList<TextView>()
    private val shown = IntArray(SLOTS) { -1 }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false; clipToPadding = false
        background = kit.cardDrawable(Theme.CARD, null, 22f)
        setPadding(kit.dp(6f), kit.dp(12f), kit.dp(6f), kit.dp(10f) + kit.CARD_LIP)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        for (slot in 0 until SLOTS) addView(LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false; clipToPadding = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            val icon = ImageView(context).apply { tag = "achievement_case_$slot" }
            icons += icon
            addView(icon, LayoutParams(kit.dp(48f), kit.dp(48f)))
            val count = kit.text("", 15f, Theme.INK, 700).apply {
                maxLines = 1
                // Large text in a narrow pane: the counts shrink rather than run into each other.
                setAutoSizeTextTypeUniformWithConfiguration(9, 15, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            }
            counts += count
            addView(count, LayoutParams(-1, -2).apply { topMargin = kit.dp(4f); marginStart = kit.dp(2f); marginEnd = kit.dp(2f) })
        }, LayoutParams(0, -2, 1f))
    }

    /** The case slot a claim of [tier] of [definition] lands in. */
    fun slotFor(definition: Achievements.Definition, tier: Int): View = icons[if (definition.tiered) tier.coerceIn(0, 3) else CHALLENGES]

    /** Show [states]; [animate] pops every slot whose count went up. */
    fun bind(states: List<Achievements.Snapshot>, animate: Boolean, delay: Long = 0L) {
        val medals = states.filter { it.definition.tiered }
        val challenges = states.filterNot { it.definition.tiered }
        for (slot in 0 until SLOTS) {
            val have = if (slot == CHALLENGES) challenges.count { it.claimedTiers > 0 } else medals.count { it.claimedTiers > slot }
            val of = if (slot == CHALLENGES) challenges.size else medals.size
            icons[slot].setImageDrawable(rewardArt(slot != CHALLENGES, slot, have > 0))
            counts[slot].text = "$have/$of"
            counts[slot].setTextColor(if (have > 0) Theme.INK else Theme.MUTED)
            if (animate && shown[slot] in 0 until have) postDelayed({ Anim.popIn(icons[slot], 0, 1.5f, 380) }, delay)
            shown[slot] = have
        }
        val max = states.sumOf { it.definition.thresholds.size }
        val now = states.sumOf { it.claimedTiers }
        contentDescription = context.getString(cube.run.R.string.achievement_earned_description, now, max)
    }

    private companion object {
        const val SLOTS = 5
        const val CHALLENGES = 4
    }
}
