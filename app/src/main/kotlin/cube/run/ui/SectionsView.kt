package cube.run.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.data.Settings
import cube.run.game.track.Sect
import cube.run.game.track.Sections

/**
 * A little map of one section: three lane columns, one row per step, first
 * step at the bottom (the way it arrives). Colours follow the game's language.
 */
@SuppressLint("ViewConstructor")
class SectionThumbView(ctx: Context, sect: Sect) : View(ctx) {
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
        0xFFFFC533.toInt(),     // coins
        0xFFB6F32A.toInt(),     // pad
        0xFF7CE24A.toInt(),     // tall wall
        0xFFA070FF.toInt(),     // pendulum
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
                    Sections.C_TALL -> rect.set(x0 - laneW * 0.08f, top + (bottom - top) * 0.2f, x1 + laneW * 0.08f, bottom)
                    Sections.C_PLAT -> rect.set(x0, top - rowH * 0.15f, x1, bottom + rowH * 0.15f)
                    Sections.C_PAD -> rect.set(x0, top + (bottom - top) * 0.7f, x1, bottom)
                    Sections.C_PENDULUM -> { // a hanging block in the middle
                        if (l == 1) { rect.set(x0, top, x1, top + (bottom - top) * 0.7f) } else continue
                    }
                    Sections.C_COIN -> { // a little stack of coins
                        val cw = laneW * 0.22f
                        val cx = (x0 + x1) / 2f
                        for (i in 0 until 3) {
                            val cy = bottom - (i + 0.5f) * (bottom - top) / 3f
                            canvas.drawCircle(cx, cy, cw / 2f, paint)
                        }
                        continue
                    }
                    else -> rect.set(x0, top, x1, bottom)
                }
                canvas.drawRoundRect(rect, laneW * 0.12f, laneW * 0.12f, paint)
            }
        }
    }
}

/**
 * The section explorer (a test tool), full screen: every section as a
 * thumbnail in a grid; tap one to play it on loop (`Settings.testSection`).
 * While one is chosen the page says so at the top and offers PLAY NORMALLY.
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class SectionsView(activity: Activity, kit: UiKit, onClose: () -> Unit) : Page(activity, kit, "SECTIONS", dark = false, onClosed = onClose) {

    private val grid = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10f), dp(4f), dp(10f), dp(8f))
    }
    private val status = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16f), dp(10f), dp(16f), dp(14f))
        background = kit.cardDrawable(Theme.YELLOW, null, 18f)
    }

    init {
        addRight(kit.pill("${Sections.lib.size}", Theme.WHITE, Theme.INK_SOFT))
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(14f); rightMargin = dp(14f); topMargin = dp(4f); bottomMargin = dp(6f)
            })
            addView(ScrollView(activity).apply {
                isVerticalScrollBarEnabled = false
                addView(grid)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        content.addView(column)
        renderStatus()
        post { renderGrid() } // the page shows at once; the thumbnails follow a frame later
    }

    private fun render() { renderStatus(); renderGrid() }

    private fun renderStatus() {
        status.removeAllViews()
        val chosen = Sections.byId(Settings.testSection)
        if (chosen != null) {
            status.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(kit.text("TESTING ${chosen.name}", 14f, Theme.INK, 700, Gravity.START))
                addView(kit.text("The run plays only this section, on loop. No pickups.", 12f, Theme.INK_SOFT, 500, Gravity.START))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            status.addView(kit.button("PLAY NORMALLY", Theme.PLAY, UiKit.Size.SMALL) {
                Settings.testSection = -1
                render()
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(10f) })
        } else {
            status.addView(kit.text("Tap a section to play it on loop", 13f, Theme.INK, 600, Gravity.START),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun renderGrid() {
        grid.removeAllViews()
        val all = Sections.lib.sortedWith(compareBy({ it.tier }, { it.id }))
        var row: LinearLayout? = null
        for ((i, s) in all.withIndex()) {
            if (i % 3 == 0) {
                row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                grid.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
            row!!.addView(cell(s), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4f), dp(4f), dp(4f), dp(4f)) })
        }
        val last = grid.getChildAt(grid.childCount - 1) as? LinearLayout
        if (last != null) while (last.childCount < 3) last.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dp(4f), 0, dp(4f), 0) })
    }

    private fun cell(s: Sect): View = LinearLayout(activity).apply {
        val chosen = Settings.testSection == s.id
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(4f), dp(6f), dp(4f), dp(8f))
        isClickable = true
        background = kit.cardDrawable(if (chosen) Theme.lighten(Theme.MINT, 0.6f) else Theme.CARD, if (chosen) Theme.MINT else null, 14f)
        addView(SectionThumbView(activity, s).apply {
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dpf(8f); setColor(0xFF2A2350.toInt()) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(96f)))
        addView(kit.text(s.name, 10f, Theme.INK, 700).apply { maxLines = 1 },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(5f) })
        addView(kit.text("tier ${s.tier} · ${s.steps.size} rows", 9f, Theme.MUTED, 500))
        setOnClickListener {
            Settings.testSection = s.id
            SoundFx.play("tap"); Haptics.click()
            close()
        }
    }
}
