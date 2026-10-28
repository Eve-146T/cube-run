package cube.run.ui

import cube.run.R

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.data.Settings
import cube.run.game.track.Sect
import cube.run.game.track.SectionFamily
import cube.run.game.track.Sections
import kotlin.math.min

private val sectionPreviews = HashMap<Sect, List<IntArray>>()
private fun preview(s: Sect) = sectionPreviews.getOrPut(s) { Sections.preview(s) }

/**
 * A little map of one section: the road's three lanes, one row per step,
 * first step at the bottom (the way it arrives). Colours follow the game's language.
 */
@SuppressLint("ViewConstructor")
class SectionThumbView(ctx: Context, sect: Sect) : View(ctx) {
    private val cells = preview(sect)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val colors = intArrayOf(
        0x00000000,             // empty
        0xFF4FD6C9.toInt(),     // pillar (dodge)
        0xFF7CE24A.toInt(),     // wall (jump)
        0xFFC86BFF.toInt(),     // bar (duck)
        0xFF120D1C.toInt(),     // tar
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
        val top0 = y0 - rows * rowH
        paint.color = 0x22FFFFFF
        canvas.drawRoundRect(pad, top0, w - pad, y0, w * 0.04f, w * 0.04f, paint) // the road
        paint.color = 0x1FFFFFFF // lane lines, dashed like the road's
        val dash = laneW * 0.16f
        for (l in 1..2) {
            val x = pad + l * laneW
            var y = top0 + dash * 0.5f
            while (y < y0) { canvas.drawRect(x - w * 0.004f, y, x + w * 0.004f, min(y0, y + dash), paint); y += dash * 2f }
        }
        for ((r, row) in cells.withIndex()) {
            val top = y0 - (r + 1) * rowH + rowH * 0.12f
            val bottom = y0 - r * rowH - rowH * 0.12f
            val rh = bottom - top
            for (l in 0..2) {
                val k = row[l]
                if (k == Sections.C_EMPTY) continue
                paint.color = colors[k]
                val x0 = pad + l * laneW + laneW * 0.1f
                val x1 = x0 + laneW * 0.8f
                when (k) {
                    Sections.C_BAR, Sections.C_SWEEP -> rect.set(x0 - laneW * 0.08f, top + rh * 0.3f, x1 + laneW * 0.08f, top + rh * 0.6f)
                    Sections.C_WALL, Sections.C_TAR -> rect.set(x0 - laneW * 0.08f, top + rh * 0.55f, x1 + laneW * 0.08f, bottom)
                    Sections.C_TALL -> rect.set(x0 - laneW * 0.08f, top + rh * 0.2f, x1 + laneW * 0.08f, bottom)
                    Sections.C_PLAT -> rect.set(x0, top - rowH * 0.15f, x1, bottom + rowH * 0.15f)
                    Sections.C_PAD -> rect.set(x0, top + rh * 0.7f, x1, bottom)
                    Sections.C_PENDULUM -> { // a hanging block in the middle
                        if (l == 1) { rect.set(x0, top, x1, top + rh * 0.7f) } else continue
                    }
                    Sections.C_COIN -> { // a little stack of coins
                        val cw = laneW * 0.22f
                        val cx = (x0 + x1) / 2f
                        for (i in 0 until 3) canvas.drawCircle(cx, bottom - (i + 0.5f) * rh / 3f, cw / 2f, paint)
                        continue
                    }
                    else -> rect.set(x0, top, x1, bottom)
                }
                canvas.drawRoundRect(rect, laneW * 0.12f, laneW * 0.12f, paint)
            }
        }
    }
}

/** The explorer's group colours: one per family, one per tier (easy is cool, expert is hot). */
internal fun SectionFamily.color() = when (this) {
    SectionFamily.MOVES -> Theme.SKY; SectionFamily.ROOFS -> Theme.LIME; SectionFamily.MACHINES -> Theme.ORANGE
    SectionFamily.CRUSHERS -> Theme.BERRY; SectionFamily.PITS -> Theme.GRAPE; SectionFamily.MIXED -> Theme.PINK
    SectionFamily.COINS -> Theme.GOLD
}
internal fun SectionFamily.title() = when (this) {
    SectionFamily.MOVES -> R.string.section_family_moves; SectionFamily.ROOFS -> R.string.section_family_roofs
    SectionFamily.MACHINES -> R.string.section_family_machines; SectionFamily.CRUSHERS -> R.string.section_family_crushers
    SectionFamily.PITS -> R.string.section_family_pits; SectionFamily.MIXED -> R.string.section_family_mixed
    SectionFamily.COINS -> R.string.section_family_coins
}
private val TIER_COLORS = intArrayOf(Theme.MINT, Theme.SKY, Theme.ORANGE, Theme.BERRY)
private val TIER_HINTS = intArrayOf(R.string.sections_tier_0, R.string.sections_tier_1, R.string.sections_tier_2, R.string.sections_tier_3)

/** A group's symbol, white on its candy tile: a family's obstacle, or a tier's rising bars. */
internal class SectionGroupIcon(private val family: SectionFamily?, private val tier: Int = 0) : Icon() {
    private val shape = Path()
    override fun draw(c: Canvas) {
        val b = bounds; val scale = min(b.width(), b.height()) / 48f
        c.save(); c.translate(b.exactCenterX() - 24 * scale, b.exactCenterY() - 24 * scale); c.scale(scale, scale)
        shape.reset(); paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.FILL
        val color = family?.color() ?: TIER_COLORS[tier.coerceIn(0, 3)]
        if (family == null) { // four bars, as many lit as the tier is hard
            for (i in 0..3) {
                val top = 36f - i * 9f
                paint.color = if (i <= tier) Theme.WHITE else Theme.alpha(Theme.darken(color, .4f), 90)
                rect.set(5f + i * 10.5f, top, 13f + i * 10.5f, 44f); c.drawRoundRect(rect, 3f, 3f, paint)
            }
            c.restore(); return
        }
        when (family) {
            SectionFamily.MOVES -> { // a lane change, arrow up the road
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 7f
                shape.moveTo(14f, 44f); shape.lineTo(14f, 32f); shape.cubicTo(14f, 22f, 34f, 24f, 34f, 12f); shape.lineTo(34f, 6f)
                shape.moveTo(26f, 12f); shape.lineTo(34f, 4f); shape.lineTo(42f, 12f)
            }
            SectionFamily.ROOFS -> { shape.moveTo(3f, 42f); shape.lineTo(19f, 24f); shape.lineTo(45f, 24f); shape.lineTo(45f, 42f); shape.close() }
            SectionFamily.MACHINES -> { // a pendulum on its pivot bar
                shape.addCircle(24f, 33f, 10f, Path.Direction.CW)
                shape.addRect(22f, 6f, 26f, 26f, Path.Direction.CW)
                shape.addRoundRect(10f, 3f, 38f, 9f, 3f, 3f, Path.Direction.CW)
            }
            SectionFamily.CRUSHERS -> { // a block slamming down on the road
                shape.addRoundRect(8f, 3f, 40f, 21f, 5f, 5f, Path.Direction.CW)
                shape.addRoundRect(4f, 38f, 44f, 45f, 3f, 3f, Path.Direction.CW)
                shape.moveTo(16f, 25f); shape.lineTo(32f, 25f); shape.lineTo(24f, 34f); shape.close()
            }
            SectionFamily.PITS -> { // a hole, and down you go
                shape.addOval(4f, 28f, 44f, 45f, Path.Direction.CW)
                shape.moveTo(16f, 12f); shape.lineTo(32f, 12f); shape.lineTo(24f, 23f); shape.close()
                shape.addRect(21f, 2f, 27f, 13f, Path.Direction.CW)
            }
            SectionFamily.MIXED -> {
                shape.addRoundRect(4f, 4f, 22f, 22f, 5f, 5f, Path.Direction.CW); shape.addRoundRect(26f, 4f, 44f, 22f, 5f, 5f, Path.Direction.CW)
                shape.addRoundRect(4f, 26f, 22f, 44f, 5f, 5f, Path.Direction.CW); shape.addRoundRect(26f, 26f, 44f, 44f, 5f, 5f, Path.Direction.CW)
            }
            SectionFamily.COINS -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 7f
                shape.addCircle(24f, 24f, 16f, Path.Direction.CW)
                shape.moveTo(24f, 16f); shape.lineTo(24f, 32f)
            }
        }
        paint.color = Theme.darken(color, .40f)
        c.save(); c.translate(0f, 2.5f); c.drawPath(shape, paint); c.restore()
        paint.color = Theme.WHITE; c.drawPath(shape, paint)
        paint.style = Paint.Style.FILL; c.restore()
    }
}

/**
 * The section explorer (a test tool), full screen: every section as a
 * thumbnail, grouped by what it is about or by tier, each group folding
 * away under its header; tap one to play it on loop (`Settings.testSection`).
 * While one is chosen the page says so at the top and offers PLAY NORMALLY.
 * The test worlds sit under the sections.
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class SectionsView(activity: Activity, kit: UiKit, private val reloadMenu: (() -> Unit)? = null, onClose: () -> Unit) : Page(activity, kit, kit.ctx.getString(R.string.text_sections), dark = false, onClosed = onClose) {

    private companion object {
        /** Process-scoped: page choices and immutable section metadata. */
        var byTier = false
        val folded = HashSet<String>()
        val sections = Sections.lib.sortedWith(compareBy({ it.tier }, { it.id }))
        val families by lazy { sections.associateWith { SectionFamily.of(preview(it)) } }
    }

    private val list = ListView(activity).apply {
        isVerticalScrollBarEnabled = false
        divider = null
        selector = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        cacheColorHint = android.graphics.Color.TRANSPARENT
        setItemsCanFocus(true)
        setPadding(dp(10f), dp(2f), dp(10f), dp(24f))
    }
    private val status = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10f), dp(10f), dp(12f), dp(14f))
        background = kit.cardDrawable(Theme.lighten(Theme.MINT, 0.72f), Theme.MINT, 20f)
    }
    private val byFamilyButton = kit.button(kit.ctx.getString(R.string.sections_by_obstacle), Theme.WHITE, UiKit.Size.SMALL) { group(false) }
    private val byTierButton = kit.button(kit.ctx.getString(R.string.sections_by_tier), Theme.WHITE, UiKit.Size.SMALL) { group(true) }
    private val cards = HashMap<Int, CandyRow>()
    private val meta = HashMap<Int, TextView>()
    private val familyLines = HashMap<Int, String>()
    private val tierLines = HashMap<Int, String>()
    private val familyGroups by lazy { groups(false).map(::buildGroup) }
    private val tierGroups by lazy { groups(true).map(::buildGroup) }
    private val testWorlds by lazy { worlds() }
    private var markedSection = Int.MIN_VALUE
    private var statusKey: List<Any>? = null
    private var entrance: ViewTreeObserver.OnPreDrawListener? = null
    private val entries = ArrayList<Entry>()
    private val adapter = SectionAdapter()
    internal val contentReady get() = entries.isNotEmpty()
    private val attachCards = Runnable { renderList(); revealChosen() }
    private val prepareCards = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            val deadline = System.nanoTime() + 3_000_000L
            do {
                cell(sections[cards.size])
            } while (cards.size < sections.size && System.nanoTime() < deadline)
            if (cards.size < sections.size) postOnAnimation(this)
            else {
                familyGroups; tierGroups
                postOnAnimation(attachCards)
            }
        }
    }

    init {
        addRight(kit.pill("${Sections.lib.size}", Theme.WHITE, Theme.INK_SOFT))
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(14f); marginEnd = dp(14f); topMargin = dp(4f); bottomMargin = dp(8f)
            })
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                for (b in listOf(byFamilyButton, byTierButton)) addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4f); marginEnd = dp(4f) })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(10f); marginEnd = dp(10f); bottomMargin = dp(4f)
            })
            addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        content.addView(column)
        list.adapter = adapter
        renderStatus()
        paintSwitch()
        post(prepareCards) // Build detached cards in small batches, then attach the finished list together.
    }

    /** A cached page comes back after a section was chosen elsewhere: show what is on loop now. */
    override fun onNavigationShown() {
        renderStatus()
        markChosen()
        revealChosen()
    }

    private fun render() { renderStatus(); markChosen() }

    private fun renderStatus() {
        val key = listOf(Settings.testSection, Settings.testPillWorld, Settings.testSpaceWorld, Settings.performanceCourse)
        if (statusKey == key) return
        statusKey = key
        status.removeAllViews()
        val chosen = Sections.byId(Settings.testSection)
        if (chosen == null && !Settings.testPillWorld && !Settings.testSpaceWorld) { status.visibility = GONE; return }
        status.visibility = VISIBLE
        val ctx = kit.ctx
        val (title, line) = when {
            Settings.performanceCourse -> ctx.getString(R.string.performance_test_title) to ctx.getString(R.string.performance_test_description)
            Settings.testPillWorld -> ctx.getString(R.string.text_red_pill_test) to ctx.getString(R.string.text_pills_on_loop_clear_middle_lane)
            Settings.testSpaceWorld -> ctx.getString(R.string.text_testing, ctx.getString(R.string.text_outer_space)) to ctx.getString(R.string.text_space_on_loop)
            else -> ctx.getString(R.string.text_testing, ctx.gameText(chosen!!.name)) to ctx.getString(R.string.text_the_run_plays_only_this_section_on_loop_no_pickups)
        }
        if (chosen != null && !Settings.testPillWorld && !Settings.testSpaceWorld && !Settings.performanceCourse)
            status.addView(SectionThumbView(activity, chosen).apply { background = thumbFloor(6f) },
                LinearLayout.LayoutParams(dp(44f), dp(54f)).apply { marginEnd = dp(10f) })
        status.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(kit.text(title, 14f, Theme.INK, 700, Gravity.START))
            addView(kit.text(line, 12f, Theme.INK_SOFT, 500, Gravity.START).apply { setPadding(0, dp(2f), 0, 0) })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        status.addView(kit.button(kit.ctx.getString(R.string.text_play_normally), Theme.PLAY, UiKit.Size.SMALL) {
            val wasPerformance = Settings.performanceCourse
            Settings.leavePerformanceCourse()
            Settings.testPillWorld = false; Settings.testSpaceWorld = false
            Settings.testSection = -1
            Settings.testBonus = -1; Settings.testBonusNow = -1
            if (wasPerformance && reloadMenu != null) reloadMenu.invoke() else render()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(10f) })
    }

    private fun paintSwitch() {
        byFamilyButton.color = if (byTier) Theme.WHITE else Theme.GRAPE
        byTierButton.color = if (byTier) Theme.GRAPE else Theme.WHITE
    }

    private fun group(tiers: Boolean) {
        if (byTier == tiers) return
        byTier = tiers
        paintSwitch()
        if (contentReady) renderList(animate = true)
        list.setSelection(0)
    }

    private class Group(val key: String, val title: String, val hint: String?, val icon: Icon, val color: Int, val sections: List<Sect>)

    private fun groups(tiers: Boolean): List<Group> {
        val ctx = kit.ctx
        return if (tiers) sections.groupBy { it.tier }.toSortedMap().map { (tier, list) ->
            val t = tier.coerceIn(0, 3)
            Group("tier$tier", ctx.getString(R.string.sections_tier_title, tier), ctx.getString(TIER_HINTS[t]),
                AchievementTile(SectionGroupIcon(null, t), TIER_COLORS[t]), TIER_COLORS[t], list)
        } else sections.groupBy { families.getValue(it) }.toSortedMap().map { (family, list) ->
            Group(family.name, ctx.getString(family.title()), null, AchievementTile(SectionGroupIcon(family), family.color()), family.color(), list)
        }
    }

    private class GroupViews(val group: Group, val header: View)
    private class Entry(val group: GroupViews?, val row: Int, val delay: Long = -1)

    private fun buildGroup(g: Group) = GroupViews(g, header(g))

    /** Only the visible rows are measured and drawn; cards and header artwork survive recycling. */
    private inner class SectionAdapter : BaseAdapter() {
        override fun getCount() = entries.size
        override fun getItem(position: Int) = entries[position]
        override fun hasStableIds() = true
        override fun getItemId(position: Int): Long {
            val e = entries[position]
            val g = e.group?.group ?: return Long.MAX_VALUE
            val group = if (g.key.startsWith("tier")) 100 + g.sections.first().tier else families.getValue(g.sections.first()).ordinal
            return (group.toLong() shl 32) + e.row + 1
        }
        override fun getViewTypeCount() = 3
        override fun getItemViewType(position: Int) = when {
            entries[position].group == null -> 2
            entries[position].row < 0 -> 0
            else -> 1
        }
        override fun areAllItemsEnabled() = false
        override fun isEnabled(position: Int) = false
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val e = entries[position]
            val container = convertView as? LinearLayout ?: LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            Anim.reset(container)
            container.removeAllViews()
            fun add(v: View, params: LinearLayout.LayoutParams) {
                (v.parent as? ViewGroup)?.removeView(v)
                container.addView(v, params)
            }
            val g = e.group
            when {
                g == null -> add(testWorlds, LinearLayout.LayoutParams(-1, -2))
                e.row < 0 -> add(g.header, LinearLayout.LayoutParams(-1, -2))
                else -> for (lane in 0..2) {
                    val s = g.group.sections.getOrNull(e.row * 3 + lane)
                    if (s != null) {
                        val line = if (byTier) familyMeta(s) else tierMeta(s)
                        meta.getValue(s.id).let { if (it.text != line) it.text = line }
                    }
                    add(if (s == null) View(activity) else cards.getValue(s.id),
                        LinearLayout.LayoutParams(0, if (s == null) 1 else -2, 1f).apply {
                            setMargins(dp(4f), if (s == null) 0 else dp(4f), dp(4f), if (s == null) 0 else dp(4f))
                        })
                }
            }
            return container
        }
    }

    private fun renderList(animate: Boolean = false, unfolded: String? = null) {
        entrance?.let { viewTreeObserver.removeOnPreDrawListener(it) }; entrance = null
        val groups = if (byTier) tierGroups else familyGroups
        entries.clear()
        var delay = 0L
        for (g in groups) {
            if (animate) Anim.reset(g.header)
            entries.add(Entry(g, -1, if (animate) delay else -1)); delay += 30
            if (g.group.key !in folded) repeat((g.group.sections.size + 2) / 3) { row ->
                val stagger = when {
                    unfolded == g.group.key -> row * 35L
                    animate && delay < 400 -> delay
                    else -> -1
                }
                entries.add(Entry(g, row, stagger)); delay += 30
            }
        }
        entries.add(Entry(null, 0))
        adapter.notifyDataSetChanged()
        markChosen()
        if (animate || unfolded != null) riseVisible(if (unfolded == null) dpf(18f) else dpf(16f), if (unfolded == null) 300 else 240)
    }

    /** Preserve the stagger without starting animations or completion layouts below the viewport. */
    private fun riseVisible(distance: Float, duration: Long) {
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                viewTreeObserver.removeOnPreDrawListener(this); entrance = null
                for (i in 0 until list.childCount) {
                    val e = entries.getOrNull(list.firstVisiblePosition + i) ?: continue
                    if (e.delay >= 0) Anim.riseIn(list.getChildAt(i), e.delay, if (e.row < 0) dpf(14f) else distance, duration, layoutOnEnd = false)
                }
                return true
            }
        }
        entrance = listener
        viewTreeObserver.addOnPreDrawListener(listener)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(prepareCards)
        removeCallbacks(attachCards)
        entrance?.let { viewTreeObserver.removeOnPreDrawListener(it) }; entrance = null
        super.onDetachedFromWindow()
    }

    /** A group's header: its tile, name, count and a chevron; a tap folds the group away or opens it. */
    private fun header(g: Group): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6f), dp(14f), dp(8f), dp(6f))
        isClickable = true
        isFocusable = true
        addView(ImageView(activity).apply { setImageDrawable(g.icon); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO },
            LinearLayout.LayoutParams(dp(34f), dp(36f)).apply { marginEnd = dp(10f) })
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(kit.text(g.title, 17f, Theme.INK, 700, Gravity.START))
            if (g.hint != null) addView(kit.text(g.hint, 12f, Theme.INK_SOFT, 500, Gravity.START).apply { setPadding(0, dp(2f), 0, 0) })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(kit.pill("${g.sections.size}", Theme.alpha(g.color, 60), Theme.INK, 12f))
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        fun chevronAngle(open: Boolean) = if (open) 90f else if (rtl) 180f else 0f
        val chevron = ImageView(activity).apply {
            setImageResource(R.drawable.ic_chevron_right)
            imageTintList = ColorStateList.valueOf(Theme.MUTED)
            rotation = chevronAngle(g.key !in folded)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(chevron, LinearLayout.LayoutParams(dp(22f), dp(22f)).apply { marginStart = dp(8f) })
        Anim.pressFeedback(this)
        setOnClickListener {
            val open = g.key in folded
            if (open) folded -= g.key else folded += g.key
            SoundFx.play("tap"); Haptics.click()
            chevron.animate().rotation(chevronAngle(open)).setDuration(180).setInterpolator(Anim.ease).start()
            renderList(unfolded = if (open) g.key else "")
        }
    }

    private fun thumbFloor(radius: Float) = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dpf(radius); setColor(0xFF2A2350.toInt()) }

    private fun rows(s: Sect) = kit.ctx.resources.getQuantityString(R.plurals.count_rows, s.steps.size, s.steps.size)
    private fun familyMeta(s: Sect) = familyLines.getOrPut(s.id) {
        kit.ctx.getString(R.string.sections_card_meta, kit.ctx.getString(families.getValue(s).title()), rows(s))
    }
    private fun tierMeta(s: Sect) = tierLines.getOrPut(s.id) { kit.ctx.getString(R.string.text_section_stats, s.tier, rows(s)) }

    private fun cell(s: Sect): CandyRow = CandyRow(activity, Theme.CARD, dpf(4f), dpf(14f)).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(5f), dp(5f), dp(5f), dp(8f))
        cards[s.id] = this
        if (markedSection == s.id) markedSection = Int.MIN_VALUE
        familyMeta(s); tierMeta(s)
        addView(FrameLayout(activity).apply {
            addView(SectionThumbView(activity, s).apply { background = thumbFloor(9f) }, FrameLayout.LayoutParams(-1, -1))
            if (s in Sections.devPool) addView(kit.pill(kit.ctx.getString(R.string.sections_new), Theme.YELLOW, Theme.INK, 9f).apply {
                setPadding(dp(6f), dp(2f), dp(6f), dp(2f))
            }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(dp(4f), dp(4f), dp(4f), dp(4f)) })
            addView(kit.pill(kit.ctx.getString(R.string.sections_on_loop), Theme.MINT, Theme.INK, 9f).apply {
                setPadding(dp(6f), dp(2f), dp(6f), dp(2f)); tag = "loop"; visibility = GONE
            }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(5f) })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(96f)))
        addView(kit.text(kit.ctx.gameText(s.name), 10f, Theme.INK, 700).apply { maxLines = 2; minLines = 2 },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6f) })
        // the meta line names what the grouping does not: the tier among families, the family among tiers
        addView(kit.text(if (byTier) familyMeta(s) else tierMeta(s), 9f, Theme.MUTED, 500).also { meta[s.id] = it })
        setOnClickListener {
            val wasPerformance = Settings.performanceCourse
            Settings.leavePerformanceCourse()
            Settings.testPillWorld = false; Settings.testSpaceWorld = false
            Settings.testSection = s.id
            SoundFx.play("tap"); Haptics.click()
            if (wasPerformance && reloadMenu != null) reloadMenu.invoke() else close()
        }
    }

    /** The chosen section's card wears a mint ring and its ON LOOP tag. */
    private fun markChosen() {
        val chosen = if (Settings.testPillWorld || Settings.testSpaceWorld) -1 else Settings.testSection
        if (markedSection == chosen) return
        for (id in listOf(markedSection, chosen)) {
            val card = cards[id] ?: continue
            val on = id == chosen
            card.ring(if (on) dpf(3f) else 0f, Theme.MINT)
            card.findViewWithTag<View>("loop")?.visibility = if (on) VISIBLE else GONE
        }
        markedSection = chosen
    }

    /** Scrolls the chosen section into view, unless its group is folded away. */
    private fun revealChosen() {
        val position = entries.indexOfFirst { e -> e.row >= 0 && (0..2).any { lane ->
            e.group?.group?.sections?.getOrNull(e.row * 3 + lane)?.id == Settings.testSection } }
        if (position < 0) return
        post {
            val row = list.getChildAt(position - list.firstVisiblePosition)
            if (row == null || row.top < list.paddingTop || row.bottom > list.height - list.paddingBottom)
                list.setSelectionFromTop(position, dp(60f))
        }
    }

    /** The test worlds: whole runs on loop rather than one section. */
    private fun worlds(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(kit.text(kit.ctx.getString(R.string.text_test_worlds), 12f, Theme.INK_SOFT, 700, Gravity.START).apply {
            setPadding(dp(6f), dp(22f), dp(6f), dp(8f))
        })
        fun add(b: View, last: Boolean = false) = addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(4f), 0, dp(4f), if (last) 0 else dp(10f))
        })
        if (cube.run.BuildConfig.DEBUG) add(kit.button(kit.ctx.getString(R.string.performance_test_start), Theme.CYAN, UiKit.Size.SMALL) {
            Settings.selectPerformanceCourse()
            reloadMenu?.invoke() ?: close()
        }.apply { tag = "performance_test_start" })
        add(kit.button(kit.ctx.getString(R.string.text_red_pill), Theme.MINT, UiKit.Size.SMALL) {
            val wasPerformance = Settings.performanceCourse
            Settings.leavePerformanceCourse()
            Settings.testPillWorld = true; Settings.testSpaceWorld = false
            Settings.testSection = -1; Settings.testBonus = -1; Settings.testBonusNow = -1
            if (wasPerformance && reloadMenu != null) reloadMenu.invoke() else close()
        })
        add(kit.button(kit.ctx.getString(R.string.text_outer_space), Theme.GRAPE, UiKit.Size.SMALL) {
            val wasPerformance = Settings.performanceCourse
            Settings.leavePerformanceCourse()
            Settings.testSpaceWorld = true; Settings.testPillWorld = false
            Settings.testSection = -1; Settings.testBonus = -1; Settings.testBonusNow = -1
            if (wasPerformance && reloadMenu != null) reloadMenu.invoke() else close()
        }.apply { tag = "space_world_test" }, last = true)
    }
}
