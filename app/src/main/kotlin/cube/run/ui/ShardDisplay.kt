package cube.run.ui

import cube.run.R

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import cube.run.data.Shards
import cube.run.data.Skins

/** The collectible, count and unlock action share the same candy slab. */
class ShardDisplay(private val kit: UiKit) {
    fun bind(button: CandyButton, skin: Skins.Skin, have: Int) {
        val count = have.coerceAtLeast(0)
        val ready = count >= skin.shardsNeeded
        val kind = Shards.get(skin.shardType)
        val color = Theme.hsv(kind.hue, .6f, 1f)
        val icon = ShardIcon(color).apply { setBounds(0, 0, kit.dp(29f), kit.dp(29f)) }
        val label = SpannableStringBuilder("  ")
        label.setSpan(CenteredImageSpan(icon), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        label.append(if (ready) kit.ctx.getString(R.string.text_unlock) else "$count/${skin.shardsNeeded}")
        val subtitle = label.length
        label.append("\n").append(kit.ctx.gameText(kind.name).uppercase(kit.ctx.resources.configuration.locales[0]))
        label.setSpan(RelativeSizeSpan(.52f), subtitle, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        button.maxLines = 2
        button.setLabel(label)
        button.color = if (ready) color else Theme.lighten(Theme.INK, .22f)
        button.setProgress(count.toFloat() / skin.shardsNeeded, Theme.darken(color, .30f))
        button.setTextColor(Theme.WHITE)
        button.contentDescription = if (ready) kit.ctx.getString(R.string.text_unlock_shards, kit.ctx.gameText(skin.name), skin.shardsNeeded, kit.ctx.gameText(kind.name))
            else kit.ctx.getString(R.string.text_shard_progress, kit.ctx.gameText(skin.name), count, skin.shardsNeeded, kit.ctx.gameText(kind.name))
    }
}
