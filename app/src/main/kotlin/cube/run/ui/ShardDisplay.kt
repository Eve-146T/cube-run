package cube.run.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import cube.run.data.Shards
import cube.run.data.Skins

/** Three native shard treatments; progress inside the cube is the default. */
@SuppressLint("SetTextI18n")
class ShardDisplay(private val activity: Activity, private val kit: UiKit, val style: Int = 0) {
    val inline = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false; clipToPadding = false
        visibility = View.GONE
    }
    /** Add at MATCH_PARENT in both dimensions over the wardrobe's content. */
    val floating = FrameLayout(activity).apply {
        clipChildren = false; clipToPadding = false
        visibility = View.GONE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    var showAction = true
        private set
    private var item = ""
    private var body: View? = null
    private val location = IntArray(2)

    init {
        floating.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> position() }
    }

    /** Null clears the display, including when the cosmetic has just been unlocked. */
    fun bind(skin: Skins.Skin?, have: Int = 0) {
        val key = "${skin?.id}:$have"
        if (item == key) return
        item = key
        inline.removeAllViews(); floating.removeAllViews(); body = null
        inline.visibility = View.GONE; floating.visibility = View.GONE
        showAction = true
        if (skin == null) return

        val kind = Shards.get(skin.shardType)
        val count = have.coerceAtLeast(0)
        val ready = count >= skin.shardsNeeded
        val color = Theme.hsv(kind.hue, 0.6f, 1f)
        val progress = "$count/${skin.shardsNeeded}"
        val label = "$progress ${kind.name}"
        showAction = style != 0 || ready

        when (style) {
            1 -> { // Familiar outlined type; compact enough to retain the regular button height.
                inline.visibility = View.VISIBLE
                inline.addView(kit.stageText(if (ready) "Ready to unlock" else label, 15f,
                    if (ready) Theme.MINT else color, weight = 600, stroke = 2f))
                inline.addView(kit.segments(10).apply {
                    level = (count * 10 / skin.shardsNeeded).coerceIn(0, 10)
                    this.color = if (ready) Theme.MINT else color
                    offColor = Theme.alpha(Theme.WHITE, 45)
                }, LinearLayout.LayoutParams(kit.dp(160f), kit.dp(7f)).apply { topMargin = kit.dp(4f) })
            }
            2 -> { // The game's existing white currency pill, just under the cube.
                showFloating(kit.iconPill(ShardIcon(color), if (ready) "Ready to unlock" else label,
                    Theme.INK, 14f, fill = Theme.WHITE))
            }
            else -> { // The progress belongs to the collectible itself, with no disabled action.
                showFloating(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    contentDescription = if (ready) "${skin.name} ready to unlock" else label
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    addView(kit.stageText(if (ready) "READY" else progress, 29f,
                        if (ready) Theme.MINT else Theme.WHITE, stroke = 2.8f).apply {
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(-2, -2))
                    addView(kit.stageText(kind.name, 13f, Theme.WHITE, weight = 600, stroke = 1.7f).apply {
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = -kit.dp(3f) })
                })
            }
        }
    }

    private fun showFloating(view: View) {
        body = view
        floating.visibility = View.VISIBLE
        floating.addView(view, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> position() }
        floating.post { position() }
    }

    private fun position() {
        val view = body ?: return
        if (floating.height == 0 || view.height == 0) return
        // RunCamera.wardrobe projects the cube's centre at 45.3% of the full viewport.
        // Convert that window coordinate to this content frame, which starts below the header.
        floating.getLocationInWindow(location)
        // Position against the resting layout; the page's entrance translation should
        // carry this label along with the rest of the wardrobe, not shift its final anchor.
        var motion = 0f
        var ancestor: View? = floating
        while (ancestor != null) {
            motion += ancestor.translationY
            ancestor = ancestor.parent as? View
        }
        val center = floating.rootView.height * if (style == 2) 0.59f else 0.453f
        view.translationY = center - (location[1] - motion) - view.height / 2f
    }
}
