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
    private var mysteryDisplay: MysteryAbilityDisplay? = null

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
        if (abilities[index] == Ability.SECRET) {
            mysteryDisplay?.toggle()
            return
        }
        selected = if (selected == index) -1 else index
        if (style == 0) revealCorner() else render()
    }

    /** Keep the pressed icon alive so its shared 220 ms release can finish. */
    private fun revealCorner() {
        val host = cornerHost ?: return
        cornerChips.forEachIndexed { i, chip ->
            chip.color = if (selected == i) Theme.LAVENDER else Theme.WHITE
            chip.isSelected = selected == i
            chip.contentDescription = "${if (selected == i) "Hide" else "Show"} ${abilities[i].title} ability"
        }
        val old = cornerCard
        cornerCard = null
        if (old != null) {
            old.move().alpha(0f).scaleX(.94f).scaleY(.97f).translationX(-kit.dpf(8f))
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
        panel.pivotX = 0f; panel.pivotY = kit.dpf(24f)
        panel.alpha = 0f; panel.scaleX = .92f; panel.scaleY = .96f; panel.translationX = -kit.dpf(10f)
        panel.move().alpha(1f).scaleX(1f).scaleY(1f).translationX(0f)
            .setDuration(260).setInterpolator(Anim.springSoft).start()
    }

    private fun icon(ability: Ability): Drawable = if (ability == Ability.BUBBLE_SAVER) BubbleIcon(Theme.INK) else AbilityIcon(ability)

    private fun chip(ability: Ability, index: Int): CandyChip = CandyChip(activity, if (selected == index) Theme.LAVENDER else Theme.WHITE, kit.dpf(4f), kit.dpf(16f)).apply {
        setImageDrawable(icon(ability)); setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
        contentDescription = "${if (selected == index) "Hide" else "Show"} ${ability.title} ability"
        isSelected = selected == index
        setOnClickListener { toggle(index) }
    }

    private fun description(ability: Ability, dark: Boolean = false) =
        if (dark) kit.stageText(ability.detail, 15f, stroke = 1.8f, weight = 500, gravity = Gravity.CENTER)
        else kit.text(ability.detail, 15f, Theme.INK, 500, Gravity.START)

    private fun card(values: List<Ability>, color: Int = Theme.WHITE, icons: Boolean = false): LinearLayout = column().apply {
        background = kit.cardDrawable(color, null, 20f)
        setPadding(dp(14f), dp(12f), dp(14f), dp(14f))
        for ((i, ability) in values.withIndex()) {
            val section = column()
            section.addView(row().apply {
                if (icons) addView(ImageView(activity).apply { setImageDrawable(icon(ability)) },
                    LinearLayout.LayoutParams(dp(26f), dp(26f)).apply { rightMargin = dp(8f) })
                addView(kit.text(ability.title, 17f, Theme.INK, 700, Gravity.START),
                    LinearLayout.LayoutParams(0, -2, 1f))
            })
            if (ability == Ability.LOTTERY) {
                val label = android.text.SpannableStringBuilder("All the coins you collect are spent on playing the Lottery!\n\nThe jackpot is 250k")
                label.append(kit.coins("", 14f)).append(", here are the chances:")
                section.addView(kit.text("", 14f, Theme.INK, 500, Gravity.START).apply { text = label },
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })
                fun odds(drawable: Drawable, chance: String, basis: String) {
                    section.addView(row().apply {
                        gravity = Gravity.TOP
                        addView(ImageView(activity).apply { setImageDrawable(drawable) },
                            LinearLayout.LayoutParams(dp(22f), dp(22f)).apply { rightMargin = dp(8f); topMargin = dp(2f) })
                        addView(column().apply {
                            addView(kit.text(chance, 15f, Theme.INK, 700, Gravity.START))
                            addView(kit.text(basis, 12f, Theme.INK, 500, Gravity.START))
                        }, LinearLayout.LayoutParams(0, -2, 1f))
                    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })
                }
                odds(CoinIcon(), if (cube.run.data.Settings.devMode) "2%" else "1 in 100,000", "per 1 coin of value")
                odds(BoxIcon(), "1.3%", "per mystery box")
            } else section.addView(description(ability), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })
            addView(section, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(14f) })
        }
    }

    private fun addInline(view: View, margin: Int = 0) {
        inline.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = margin })
    }

    private fun render() {
        Anim.cancelTree(floating)
        Anim.reset(floating)
        cornerHost = null; cornerCard = null; cornerChips.clear(); mysteryDisplay = null
        inline.removeAllViews(); floating.removeAllViews()
        inline.visibility = if (abilities.isEmpty() || style in listOf(0, 1, 5)) View.GONE else View.VISIBLE
        floating.visibility = if (abilities.isEmpty()) View.GONE else View.VISIBLE
        if (abilities.isEmpty()) return
        if (Ability.SECRET in abilities) {
            inline.visibility = View.GONE
            val mystery = MysteryAbilityDisplay(activity, kit, canToggle = { !changing }, onExpandedChanged = {
                selected = if (it) abilities.indexOf(Ability.SECRET) else -1
            })
            mysteryDisplay = mystery
            cornerChips.add(mystery.button)
            floating.addView(row().apply {
                gravity = Gravity.TOP
                addView(mystery.button, LinearLayout.LayoutParams(dp(48f), dp(52f)))
                addView(mystery.panel, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(10f) })
            }, FrameLayout.LayoutParams(-1, -2))
            return
        }
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
                    LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(10f) })
                floating.addView(host)
            }
            1 -> { // Labeled ability tabs above the cube.
                val host = column()
                host.addView(row().apply {
                    abilities.forEachIndexed { i, ability ->
                        addView(kit.button(ability.title, if (selected == i) Theme.LAVENDER else Theme.WHITE, UiKit.Size.SMALL) { toggle(i) },
                            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(8f) })
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
                        addView(kit.iconPill(icon(ability), ability.title, size = 13f, fill = if (selected == i) Theme.LAVENDER else Theme.WHITE).apply {
                            minimumHeight = dp(48f)
                            contentDescription = "Show ${ability.title} ability"
                            setOnClickListener { toggle(i) }
                        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(4f); rightMargin = dp(4f) })
                    }
                })
                if (selected >= 0) addInline(description(abilities[selected], dark = true), dp(8f))
            }
            5 -> { // A permanently visible speech bubble, tucked above the cube.
                floating.addView(column().apply {
                    addView(card(abilities, color = Theme.LAVENDER, icons = true))
                    addView(SpeechTail(activity), LinearLayout.LayoutParams(dp(22f), dp(16f)).apply { leftMargin = dp(32f); topMargin = -dp(4f) })
                })
            }
            6 -> { // One large button opens a short sheet over the lower part of the stage.
                inline.addView(kit.button(if (abilities.size == 1) "ABILITY" else "ABILITIES", Theme.LAVENDER, UiKit.Size.SMALL) { toggle(0) })
                if (selected >= 0) floating.addView(column().apply {
                    background = kit.cardDrawable(Theme.WHITE, null, 24f)
                    setPadding(dp(18f), dp(14f), dp(18f), dp(20f))
                    addView(row().apply {
                        addView(kit.text("ABILITIES", 20f, Theme.INK, 700, Gravity.START), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                        addView(kit.chip(R.drawable.ic_chevron_left, Theme.CARD_ALT, Theme.INK, "Close abilities") { toggle(0) }, LinearLayout.LayoutParams(dp(44f), dp(48f)))
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
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(12f) })
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
        if (ability == Ability.SECRET) {
            path.reset(); path.moveTo(14f, 15f)
            path.cubicTo(14f, 5f, 34f, 5f, 34f, 16f)
            path.cubicTo(34f, 23f, 24f, 24f, 24f, 30f)
            paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
            paint.color = Theme.INK; paint.strokeWidth = 8f
            canvas.drawPath(path, paint)
            paint.color = Theme.LAVENDER; paint.strokeWidth = 5f
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL; paint.color = Theme.INK
            canvas.drawCircle(24f, 39f, 4f, paint)
            paint.color = Theme.LAVENDER
            canvas.drawCircle(24f, 39f, 2.5f, paint)
        } else if (ability == Ability.PHASE) {
            paint.style = Paint.Style.FILL; paint.color = Theme.LAVENDER
            canvas.drawRoundRect(17f, 7f, 40f, 30f, 5f, 5f, paint)
            paint.style = Paint.Style.STROKE; paint.color = Theme.INK
            canvas.drawRoundRect(17f, 7f, 40f, 30f, 5f, 5f, paint)
            paint.style = Paint.Style.FILL; paint.color = Theme.WHITE
            canvas.drawRoundRect(7f, 17f, 30f, 40f, 5f, 5f, paint)
            paint.style = Paint.Style.STROKE; paint.color = Theme.INK
            canvas.drawRoundRect(7f, 17f, 30f, 40f, 5f, 5f, paint)
        } else if (ability == Ability.GOLD_COINS) {
            paint.style = Paint.Style.FILL; paint.color = Theme.GOLD
            canvas.drawCircle(21f, 27f, 15f, paint)
            paint.style = Paint.Style.STROKE; paint.color = Theme.INK
            canvas.drawCircle(21f, 27f, 15f, paint); canvas.drawCircle(21f, 27f, 9f, paint)
            canvas.drawLine(21f, 22f, 21f, 32f, paint)
            canvas.drawLine(36f, 5f, 36f, 17f, paint); canvas.drawLine(30f, 11f, 42f, 11f, paint)
        } else if (ability == Ability.LOTTERY) {
            canvas.save(); canvas.rotate(-12f, 24f, 24f)
            // A notched raffle ticket: bold silhouette and one clear prize star.
            path.reset(); path.moveTo(9f, 10f); path.lineTo(39f, 10f)
            path.quadTo(43f, 10f, 43f, 14f); path.lineTo(43f, 19f)
            path.cubicTo(36f, 19f, 36f, 29f, 43f, 29f); path.lineTo(43f, 34f)
            path.quadTo(43f, 38f, 39f, 38f); path.lineTo(9f, 38f)
            path.quadTo(5f, 38f, 5f, 34f); path.lineTo(5f, 29f)
            path.cubicTo(12f, 29f, 12f, 19f, 5f, 19f); path.lineTo(5f, 14f)
            path.quadTo(5f, 10f, 9f, 10f); path.close()
            paint.style = Paint.Style.FILL; paint.color = Theme.GOLD; canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE; paint.color = Theme.INK; paint.strokeWidth = 2.8f
            canvas.drawPath(path, paint)
            paint.strokeWidth = 2f; paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(33f, 14f, 33f, 17f, paint)
            canvas.drawLine(33f, 22f, 33f, 26f, paint)
            canvas.drawLine(33f, 31f, 33f, 34f, paint)
            path.reset(); path.moveTo(21f, 15f); path.lineTo(24f, 21f)
            path.lineTo(30f, 22f); path.lineTo(25.5f, 26f); path.lineTo(26.5f, 32f)
            path.lineTo(21f, 29f); path.lineTo(15.5f, 32f); path.lineTo(16.5f, 26f)
            path.lineTo(12f, 22f); path.lineTo(18f, 21f); path.close()
            paint.style = Paint.Style.FILL; paint.color = 0xFFF04C63.toInt(); canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE; paint.color = Theme.INK; paint.strokeWidth = 1.8f
            canvas.drawPath(path, paint)
            canvas.restore()
        } else if (ability == Ability.COAL) {
            path.reset(); path.moveTo(10f, 15f); path.lineTo(24f, 7f); path.lineTo(36f, 14f)
            path.lineTo(42f, 29f); path.lineTo(30f, 41f); path.lineTo(12f, 37f); path.lineTo(6f, 26f); path.close()
            paint.style = Paint.Style.FILL; paint.color = Theme.INK; canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE; paint.color = Theme.LAVENDER; paint.strokeWidth = 2f
            canvas.drawLine(15f, 18f, 25f, 13f, paint); canvas.drawLine(25f, 13f, 32f, 20f, paint)
        } else if (ability == Ability.FLOATY) {
            path.reset(); path.moveTo(13f, 34f)
            path.cubicTo(0f, 34f, 3f, 17f, 15f, 19f)
            path.cubicTo(13f, 4f, 35f, 4f, 35f, 20f)
            path.cubicTo(48f, 18f, 47f, 35f, 35f, 35f); path.close()
            paint.style = Paint.Style.FILL; paint.color = Theme.LAVENDER; canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE; paint.color = Theme.INK; canvas.drawPath(path, paint)
            canvas.drawLine(16f, 41f, 30f, 41f, paint)
        } else if (ability == Ability.DOUBLE_JUMP) {
            paint.style = Paint.Style.STROKE; paint.color = Theme.INK; paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = 4f
            for (y in listOf(8f, 25f)) {
                path.reset(); path.moveTo(12f, y + 11f); path.lineTo(24f, y); path.lineTo(36f, y + 11f)
                canvas.drawPath(path, paint)
            }
            paint.color = Theme.MINT; canvas.drawLine(15f, 43f, 33f, 43f, paint)
        } else if (ability == Ability.ZAPPY) {
            paint.style = Paint.Style.STROKE; paint.color = Theme.INK; paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(7f, 7f, 7f, 41f, paint); canvas.drawLine(41f, 7f, 41f, 41f, paint)
            path.reset(); path.moveTo(19f, 8f); path.lineTo(31f, 8f); path.lineTo(22f, 23f)
            path.lineTo(33f, 23f); path.lineTo(17f, 42f); path.lineTo(23f, 28f); path.lineTo(14f, 28f); path.close()
            paint.style = Paint.Style.FILL; paint.color = Theme.LAVENDER; canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE; paint.color = Theme.INK; canvas.drawPath(path, paint)
        } else if (ability == Ability.POWER_STRETCH || ability == Ability.QUICK_BUBBLE || ability == Ability.LONG_BUBBLE) {
            paint.style = Paint.Style.FILL; paint.color = if (ability == Ability.POWER_STRETCH) Theme.LAVENDER else Theme.CYAN
            canvas.drawCircle(22f, 25f, 15f, paint)
            paint.style = Paint.Style.STROKE; paint.color = Theme.INK; paint.strokeCap = Paint.Cap.ROUND
            canvas.drawCircle(22f, 25f, 15f, paint)
            canvas.drawLine(22f, 15f, 22f, 25f, paint); canvas.drawLine(22f, 25f, 28f, 28f, paint)
            canvas.drawLine(17f, 5f, 27f, 5f, paint)
            paint.style = Paint.Style.FILL; paint.color = Theme.WHITE
            canvas.drawCircle(37f, 36f, 9f, paint)
            paint.style = Paint.Style.STROKE; paint.color = Theme.INK
            canvas.drawLine(32f, 36f, 42f, 36f, paint)
            if (ability != Ability.QUICK_BUBBLE) canvas.drawLine(37f, 31f, 37f, 41f, paint)
        } else {
            path.reset(); path.moveTo(28f, 4f); path.lineTo(10f, 27f); path.lineTo(23f, 27f)
            path.lineTo(20f, 44f); path.lineTo(39f, 19f); path.lineTo(26f, 19f); path.close()
            paint.style = Paint.Style.FILL; paint.color = Theme.YELLOW; canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE; paint.color = Theme.INK; canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL; canvas.restore()
    }
}
