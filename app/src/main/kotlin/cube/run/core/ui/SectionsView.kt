package cube.run.core.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import cube.run.core.Haptics
import cube.run.core.Palette
import cube.run.core.Settings
import cube.run.core.SoundFx
import cube.run.game.Sect
import cube.run.game.Sections

/**
 * A little map of one section: three lane columns, one row per step, first
 * step at the bottom (the way it arrives). Colours follow the game's language.
 */
@SuppressLint("ViewConstructor")
class SectionThumbView(ctx: Context, private val sect: Sect) : View(ctx) {
    private val cells = Sections.preview(sect)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val colors = intArrayOf(
        0x00000000,             // empty
        0xFF4FD6C9.toInt(),     // pillar (dodge)
        0xFF7CE24A.toInt(),     // wall (jump)
        0xFFC86BFF.toInt(),     // bar (duck)
        0xFF1A1424.toInt(),     // tar
        0xFFFFB03A.toInt(),     // piston
        0xFFF2E24A.toInt(),     // sweeper
        0xFF4C7DFF.toInt(),     // stomper
        0xFF3AD4FF.toInt(),     // slider
        0xFFB8E066.toInt(),     // platform
        0xFF2EB8FF.toInt(),     // pincer
    )

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val rows = cells.size.coerceAtLeast(1)
        val pad = w * 0.06f
        val laneW = (w - pad * 2) / 3f
        val rowH = ((h - pad * 2) / rows).coerceAtMost(laneW * 0.9f)
        val y0 = h - pad
        paint.color = 0x22FFFFFF
        canvas.drawRoundRect(pad, y0 - rows * rowH, w - pad, y0, w * 0.04f, w * 0.04f, paint) // the floor
        for ((r, row) in cells.withIndex()) {
            val top = y0 - (r + 1) * rowH + rowH * 0.12f
            val bottom = y0 - r * rowH - rowH * 0.12f
            for (l in 0..2) {
                val k = row[l]
                if (k == Sections.C_EMPTY) continue
                paint.color = colors[k]
                val x0 = pad + l * laneW + laneW * 0.1f
                val x1 = x0 + laneW * 0.8f
                when (k) {
                    Sections.C_BAR, Sections.C_SWEEP -> rect.set(x0 - laneW * 0.08f, top + (bottom - top) * 0.3f, x1 + laneW * 0.08f, top + (bottom - top) * 0.6f)
                    Sections.C_WALL, Sections.C_TAR -> rect.set(x0 - laneW * 0.08f, top + (bottom - top) * 0.55f, x1 + laneW * 0.08f, bottom)
                    Sections.C_PLAT -> rect.set(x0, top - rowH * 0.15f, x1, bottom + rowH * 0.15f)
                    else -> rect.set(x0, top, x1, bottom)
                }
                canvas.drawRoundRect(rect, laneW * 0.12f, laneW * 0.12f, paint)
            }
        }
    }
}

/**
 * The section explorer (a test tool): every section as a thumbnail in a grid;
 * tap one to play it on loop (`Settings.testSection`), PLAY ALL to go back to
 * the normal director.
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class SectionsView(
    private val activity: Activity,
    private val kit: UiKit,
    private val onClose: () -> Unit,
) : FrameLayout(activity) {

    private fun dp(v: Float) = kit.dp(v)

    init {
        setBackgroundColor(Ui.SCRIM)
        isClickable = true
        alpha = 0f
        animate().alpha(1f).setDuration(200).start()

        val card = kit.card()
        card.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22f), dp(18f), dp(22f), dp(6f))
            addView(kit.text("SECTIONS", 24f, Ui.INK, heavy = true, gravity = Gravity.START),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(kit.text("${Sections.lib.size}", 16f, Ui.MUTED))
        })
        card.addView(kit.text("Tap a section to play it on loop", 12f, Ui.MUTED, bold = false, gravity = Gravity.START),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(22f) })

        val grid = GridLayout(activity).apply {
            columnCount = 3
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
        }
        for (s in Sections.lib.sortedWith(compareBy({ it.tier }, { it.id }))) {
            grid.addView(cell(s), GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply {
                width = 0; setMargins(dp(4f), dp(4f), dp(4f), dp(4f))
            })
        }
        card.addView(ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            addView(grid)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        card.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(22f), dp(6f), dp(22f), dp(18f))
            addView(kit.button("PLAY ALL", UiKit.Style.OUTLINE, small = true) {
                Settings.testSection = -1
                close()
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(kit.button("CLOSE", UiKit.Style.FILLED, small = true) { close() },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(10f) })
        })

        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            leftMargin = dp(14f); rightMargin = dp(14f); topMargin = dp(48f); bottomMargin = dp(48f)
        })
    }

    private fun cell(s: Sect): View = LinearLayout(activity).apply {
        val chosen = Settings.testSection == s.id
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(4f), dp(6f), dp(4f), dp(6f))
        isClickable = true
        background = GradientDrawable().apply {
            cornerRadius = dp(12f).toFloat()
            setColor(if (chosen) Palette.withAlpha(kit.accent, 60) else Ui.CARD_ALT)
            if (chosen) setStroke(dp(2f), Ui.GREEN_INK)
        }
        addView(SectionThumbView(activity, s).apply {
            background = GradientDrawable().apply { cornerRadius = dp(8f).toFloat(); setColor(0xFF2A2350.toInt()) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(96f)))
        addView(kit.text(s.name, 10f, Ui.INK).apply { maxLines = 1 },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(5f) })
        addView(kit.text("tier ${s.tier} · ${s.steps.size} rows", 9f, Ui.MUTED, bold = false))
        setOnClickListener {
            Settings.testSection = s.id
            SoundFx.play("tap"); Haptics.click()
            close()
        }
    }

    private fun close() {
        animate().alpha(0f).setDuration(160).withEndAction {
            (parent as? FrameLayout)?.removeView(this)
            onClose()
        }.start()
    }
}
