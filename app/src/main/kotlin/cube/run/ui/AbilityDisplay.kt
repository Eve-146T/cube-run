package cube.run.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import cube.run.ui.Anim.move
import cube.run.R
import cube.run.data.Skins.Ability

/** Native wardrobe alternatives for review. The default is the expandable corner icon. */
@SuppressLint("SetTextI18n")
class AbilityDisplay(private val activity: Activity, private val kit: UiKit, val style: Int = 0) {
    val inline = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false; clipToPadding = false
    }
    val floating = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false }
    private var abilities = emptyList<Ability>()
    private var item = ""
    private var selected = -1
    private var revision = 0
    private var changing = false
    private var cornerHost: LinearLayout? = null
    private val cornerChips = ArrayList<CandyChip>()
    private var cornerCard: View? = null

    init {
        floating.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (style == 0 && abilities.isNotEmpty()) Anim.riseIn(floating, 120, kit.dpf(12f), 260)
            }
            override fun onViewDetachedFromWindow(v: View) {
                revision++; changing = false
                Anim.cancelTree(floating)
            }
        })
    }
    private fun dp(v: Float) = kit.dp(v)
    private fun column() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false; clipToPadding = false
    }
    private fun row() = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        clipChildren = false; clipToPadding = false
    }

    fun bind(values: List<Ability>, itemKey: String) {
        if (abilities == values && item == itemKey) return
        abilities = values; item = itemKey; selected = -1
        val token = ++revision
        if (style != 0 || !floating.isAttachedToWindow) {
            changing = false
            render()
            return
        }
        changing = true
        val show = {
            if (revision == token && floating.isAttachedToWindow) {
                render()
                changing = false
                if (abilities.isNotEmpty()) Anim.riseIn(floating, distancePx = kit.dpf(12f), duration = 260)
            }
        }
        if (floating.visibility == View.VISIBLE && floating.childCount > 0) {
            floating.move().alpha(0f).translationY(-kit.dpf(8f)).setDuration(110)
                .withEndAction { show() }.start()
        } else show()
    }

    private fun toggle(index: Int) {
        if (changing || index !in abilities.indices) return
        selected = if (selected == index) -1 else index
        if (style == 0) revealCorner() else render()
    }

    /** Keep the pressed icon alive so its shared 220 ms release can finish. */
    private fun revealCorner() {
        val host = cornerHost ?: return
        val rtl = host.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val direction = if (rtl) -1 else 1
        cornerChips.forEachIndexed { i, chip ->
            chip.color = if (selected == i) Theme.LAVENDER else Theme.WHITE
            chip.isSelected = selected == i
            chip.contentDescription = kit.ctx.getString(if (selected == i) R.string.text_hide_ability else R.string.text_show_ability, kit.ctx.gameText(abilities[i].title))
        }
        val old = cornerCard
        cornerCard = null
        if (old != null) {
            old.move().alpha(0f).scaleX(.94f).scaleY(.97f).translationX(-kit.dpf(8f) * direction)
                .setDuration(120).withEndAction { (old.parent as? android.view.ViewGroup)?.removeView(old) }.start()
        }
        if (selected < 0) return
        // Each selected explanation expands beside the persistent ability icons.
        val panel = card(listOf(abilities[selected]))
        cornerCard = panel
        val slot = host.getChildAt(1) as FrameLayout
        if (old != null) {
            old.animate().cancel()
            slot.removeView(old)
        }
        for (i in 0 until slot.childCount) slot.getChildAt(i).animate().cancel()
        slot.removeAllViews()
        slot.addView(panel, FrameLayout.LayoutParams(-1, -2))
        panel.pivotX = if (rtl) slot.width.toFloat() else 0f; panel.pivotY = kit.dpf(24f)
        panel.alpha = 0f; panel.scaleX = .92f; panel.scaleY = .96f; panel.translationX = -kit.dpf(10f) * direction
        panel.move().alpha(1f).scaleX(1f).scaleY(1f).translationX(0f)
            .setDuration(260).setInterpolator(Anim.springSoft).start()
    }

    private fun icon(ability: Ability): Drawable = if (ability == Ability.BUBBLE_SAVER) BubbleIcon(Theme.INK) else AbilityIcon(ability)

    private fun chip(ability: Ability, index: Int): CandyChip = CandyChip(activity, if (selected == index) Theme.LAVENDER else Theme.WHITE, kit.dpf(4f), kit.dpf(16f)).apply {
        setImageDrawable(icon(ability)); setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
        contentDescription = kit.ctx.getString(if (selected == index) R.string.text_hide_ability else R.string.text_show_ability, kit.ctx.gameText(ability.title))
        isSelected = selected == index
        setOnClickListener { toggle(index) }
    }

    private fun description(ability: Ability, dark: Boolean = false) =
        if (dark) kit.stageText(kit.ctx.getString(R.string.text_ability_description, kit.ctx.gameText(ability.detail)), 15f, stroke = 1.8f, weight = 500, gravity = Gravity.CENTER)
        else kit.text(kit.ctx.getString(R.string.text_ability_description, kit.ctx.gameText(ability.detail)), 15f, Theme.INK, 500, Gravity.START)

    private fun card(values: List<Ability>, color: Int = Theme.WHITE, icons: Boolean = false): LinearLayout = column().apply {
        background = kit.cardDrawable(color, null, 20f)
        setPadding(dp(16f), dp(12f), dp(16f), dp(16f))
        for ((i, ability) in values.withIndex()) {
            val body: View = if (icons) row().apply {
                addView(ImageView(activity).apply { setImageDrawable(icon(ability)) }, LinearLayout.LayoutParams(dp(30f), dp(30f)))
                addView(description(ability), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12f) })
            } else description(ability)
            addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { if (i > 0) topMargin = dp(12f) })
        }
    }

    private fun addInline(view: View, margin: Int = 0) {
        inline.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = margin })
    }

    private fun render() {
        Anim.cancelTree(floating)
        Anim.reset(floating)
        cornerHost = null; cornerCard = null; cornerChips.clear()
        inline.removeAllViews(); floating.removeAllViews()
        inline.visibility = if (abilities.isEmpty() || style in listOf(0, 1, 5)) View.GONE else View.VISIBLE
        floating.visibility = if (abilities.isEmpty()) View.GONE else View.VISIBLE
        if (abilities.isEmpty()) return
        when (style) {
            0 -> { // One icon per ability, at the top-left of the cube; tap one to expand beside it.
                val host = row().apply { gravity = Gravity.TOP }
                cornerHost = host
                host.addView(column().apply {
                    abilities.forEachIndexed { i, ability ->
                        val button = chip(ability, i)
                        cornerChips.add(button)
                        addView(button, LinearLayout.LayoutParams(dp(48f), dp(52f)).apply { if (i > 0) topMargin = dp(8f) })
                    }
                })
                host.addView(FrameLayout(activity).apply { clipChildren = false; clipToPadding = false },
                    LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10f) })
                floating.addView(host)
            }
            1 -> { // Labeled ability tabs above the cube.
                val host = column()
                host.addView(row().apply {
                    abilities.forEachIndexed { i, ability ->
                        addView(kit.button(kit.ctx.gameText(ability.title), if (selected == i) Theme.LAVENDER else Theme.WHITE, UiKit.Size.SMALL) { toggle(i) },
                            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8f) })
                    }
                })
                if (selected >= 0) host.addView(card(listOf(abilities[selected])), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10f) })
                floating.addView(host)
            }
            2 -> abilities.forEach { addInline(description(it, dark = true), dp(4f)) }
            3 -> addInline(card(abilities, icons = true))
            4 -> { // Compact pills under the cosmetic name, expanded on demand.
                inline.addView(row().apply {
                    abilities.forEachIndexed { i, ability ->
                        addView(kit.iconPill(icon(ability), kit.ctx.gameText(ability.title), size = 13f, fill = if (selected == i) Theme.LAVENDER else Theme.WHITE).apply {
                            minimumHeight = dp(48f)
                            contentDescription = kit.ctx.getString(R.string.text_show_ability, kit.ctx.gameText(ability.title))
                            setOnClickListener { toggle(i) }
                        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(4f); marginEnd = dp(4f) })
                    }
                })
                if (selected >= 0) addInline(description(abilities[selected], dark = true), dp(8f))
            }
            5 -> { // A permanently visible speech bubble, tucked above the cube.
                floating.addView(column().apply {
                    addView(card(abilities, color = Theme.LAVENDER, icons = true))
                    addView(SpeechTail(activity), LinearLayout.LayoutParams(dp(22f), dp(16f)).apply { marginStart = dp(32f); topMargin = -dp(4f) })
                })
            }
            6 -> { // One large button opens a short sheet over the lower part of the stage.
                inline.addView(kit.button(if (abilities.size == 1) kit.ctx.getString(R.string.text_ability) else kit.ctx.getString(R.string.text_abilities), Theme.LAVENDER, UiKit.Size.SMALL) { toggle(0) })
                if (selected >= 0) floating.addView(column().apply {
                    background = kit.cardDrawable(Theme.WHITE, null, 24f)
                    setPadding(dp(18f), dp(14f), dp(18f), dp(20f))
                    addView(row().apply {
                        addView(kit.text(kit.ctx.getString(R.string.text_abilities), 20f, Theme.INK, 700, Gravity.START), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                        addView(kit.chip(R.drawable.ic_chevron_left, Theme.CARD_ALT, Theme.INK, kit.ctx.getString(R.string.text_close_abilities)) { toggle(0) }, LinearLayout.LayoutParams(dp(44f), dp(48f)))
                    })
                    addView(card(abilities, icons = true))
                })
            }
            else -> abilities.forEach { ability -> // Small icon badges beside plain stage text.
                addInline(row().apply {
                    addView(ImageView(activity).apply {
                        setImageDrawable(icon(ability)); setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
                        background = kit.cardDrawable(Theme.LAVENDER, null, 14f)
                    }, LinearLayout.LayoutParams(dp(44f), dp(48f)))
                    addView(description(ability, dark = true).apply { gravity = Gravity.START },
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12f) })
                }, dp(8f))
            }
        }
    }
}

private class SpeechTail(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.LAVENDER }
    private val path = Path()
    override fun onDraw(canvas: Canvas) {
        path.reset(); path.moveTo(0f, 0f); path.lineTo(width.toFloat(), 0f); path.lineTo(width * .22f, height.toFloat()); path.close()
        canvas.drawPath(path, paint)
    }
}

/** Two overlapping cubes for phase, a lightning bolt for speed. */
class AbilityIcon(private val ability: Ability) : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val s = minOf(bounds.width(), bounds.height()) / 48f
        canvas.save(); canvas.translate(bounds.exactCenterX() - 24f * s, bounds.exactCenterY() - 24f * s); canvas.scale(s, s)
        paint.strokeWidth = 3f; paint.strokeJoin = Paint.Join.ROUND
        if (ability == Ability.PHASE) {
            paint.style = Paint.Style.FILL; paint.color = Theme.LAVENDER
            canvas.drawRoundRect(17f, 7f, 40f, 30f, 5f, 5f, paint)
            paint.style = Paint.Style.STROKE; paint.color = Theme.INK
            canvas.drawRoundRect(17f, 7f, 40f, 30f, 5f, 5f, paint)
            paint.style = Paint.Style.FILL; paint.color = Theme.WHITE
            canvas.drawRoundRect(7f, 17f, 30f, 40f, 5f, 5f, paint)
            paint.style = Paint.Style.STROKE; paint.color = Theme.INK
            canvas.drawRoundRect(7f, 17f, 30f, 40f, 5f, 5f, paint)
        } else {
            path.reset(); path.moveTo(28f, 4f); path.lineTo(10f, 27f); path.lineTo(23f, 27f)
            path.lineTo(20f, 44f); path.lineTo(39f, 19f); path.lineTo(26f, 19f); path.close()
            paint.style = Paint.Style.FILL; paint.color = Theme.YELLOW; canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE; paint.color = Theme.INK; canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL; canvas.restore()
    }
}
