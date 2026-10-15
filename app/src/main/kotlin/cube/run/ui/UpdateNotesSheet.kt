package cube.run.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import cube.run.BuildConfig
import cube.run.R
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.data.UpdateNotes
import cube.run.ui.Anim.move
import kotlin.math.cos
import kotlin.math.sin

/**
 * "In this update": a candy card over the dimmed stage. A grape header with slow rays and the
 * title, then every change on its own row (its achievement tile, or the news star), popping in
 * one after another with a soft rising tick, and one big LET'S GO. Tapping a row presses it.
 */
@SuppressLint("ViewConstructor")
class UpdateNotesSheet(activity: Activity, kit: UiKit, onDismissed: () -> Unit) : Sheet(activity, kit, onDismissed) {

    private val header = Header()
    private val title = kit.stageText(activity.getString(R.string.update_notes_title), 27f, stroke = 4.5f)
    private val version = kit.pill(activity.getString(R.string.update_notes_version, BuildConfig.VERSION_NAME), Theme.alpha(Theme.WHITE, 46), Theme.WHITE, 13f)
    private val rows = ArrayList<Pair<View, View>>()
    private val done = kit.button(activity.getString(R.string.update_notes_done), Theme.GOLD, UiKit.Size.BIG) { dismiss() }
    private val pending = ArrayList<Runnable>()

    init {
        card.setPadding(0, 0, 0, dp(22f))
        // Long titles (German) may take two lines: the header grows rather than cutting them.
        header.minimumHeight = dp(124f)
        header.setPadding(dp(18f), dp(20f), dp(18f), dp(22f))
        header.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false; clipToPadding = false
            addView(title, LinearLayout.LayoutParams(-1, -2))
            addView(version, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4f) })
        }, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        card.addView(header, LinearLayout.LayoutParams(-1, -2))

        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            setPadding(dp(18f), dp(14f), dp(18f), 0)
        }
        for (item in UpdateNotes.items(activity)) list.addView(row(item), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6f) })
        card.addView(list, LinearLayout.LayoutParams(-1, -2))
        card.addView(done, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18f); marginStart = dp(18f); marginEnd = dp(18f) })
        choreograph()
    }

    private fun row(item: UpdateNotes.Item): View {
        val tile = ImageView(activity).apply {
            setImageDrawable(AchievementTile(item.icon, AchievementCards.accent(item.icon)))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val words = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(kit.text(item.title, 18f, Theme.INK, 700, Gravity.START))
            addView(kit.text(item.text, 14.5f, Theme.INK_SOFT, 600, Gravity.START).apply { setLineSpacing(0f, 1.08f) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3f) })
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            setPadding(dp(4f), dp(7f), dp(4f), dp(7f))
            isClickable = true; isFocusable = true
            contentDescription = "${item.title}. ${item.text}"
            addView(tile, LinearLayout.LayoutParams(dp(54f), dp(54f)))
            addView(words, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14f) })
            Anim.pressFeedback(this)
            setOnClickListener {
                Anim.tap(this)
                tile.rotation = -9f
                tile.move().rotation(0f).setDuration(420).setInterpolator(Anim.spring).start()
                SoundFx.play("blip", rate = 1.1f, vol = .5f); Haptics.click()
            }
            rows.add(tile to words)
        }
    }

    /** The card lands, the title pops, then each row in turn; the button rises in last. */
    private fun choreograph() {
        Anim.popIn(title, delay = 110, from = .6f, duration = 360)
        Anim.riseIn(version, delay = 190, distancePx = dpf(10f))
        val first = 300L; val step = 120L
        for ((i, pair) in rows.withIndex()) {
            val (tile, words) = pair
            val at = first + i * step
            Anim.popIn(tile, delay = at, from = .35f, duration = 380)
            Anim.slideIn(words, delay = at + 30, fromX = dpf(if (layoutDirection == LAYOUT_DIRECTION_RTL) -22f else 22f), duration = 320)
            later(at) { SoundFx.play("place", rate = 1f + i * .09f, vol = .42f) }
        }
        Anim.riseIn(done, delay = first + rows.size * step + 40, distancePx = dpf(18f), duration = 340)
    }

    private fun later(ms: Long, action: () -> Unit) {
        val r = Runnable(action); pending.add(r); postDelayed(r, ms)
    }

    override fun onDetachedFromWindow() {
        pending.forEach(::removeCallbacks); pending.clear()
        super.onDetachedFromWindow()
    }

    /** The grape top of the card: rounded like the card above, square below, with soft turning rays. */
    private inner class Header : FrameLayout(activity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val clip = Path()
        private val rays = Path()
        private val box = RectF()
        private val star = Path()
        private var turn = 0f
        private val spin = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 60_000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { turn = it.animatedValue as Float; Anim.repaint(this@Header) }
        }

        init {
            setWillNotDraw(false)
            clipChildren = false; clipToPadding = false
        }

        override fun onAttachedToWindow() { super.onAttachedToWindow(); spin.start() }
        override fun onDetachedFromWindow() { spin.cancel(); super.onDetachedFromWindow() }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            val r = dpf(30f)
            box.set(0f, 0f, w.toFloat(), h.toFloat())
            clip.reset(); clip.addRoundRect(box, floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f), Path.Direction.CW)
            rays.reset()
            val reach = maxOf(w, h).toFloat()
            for (k in 0 until 12) {
                val a = Math.toRadians(k * 30.0); val b = Math.toRadians(k * 30.0 + 13.0)
                rays.moveTo(0f, 0f)
                rays.lineTo((cos(a) * reach).toFloat(), (sin(a) * reach).toFloat())
                rays.lineTo((cos(b) * reach).toFloat(), (sin(b) * reach).toFloat())
                rays.close()
            }
        }

        override fun onDraw(canvas: Canvas) {
            val save = canvas.save()
            canvas.clipPath(clip)
            canvas.drawColor(GRAPE)
            canvas.translate(width / 2f, height * .52f)
            canvas.rotate(turn)
            paint.color = Theme.alpha(Theme.WHITE, 22); canvas.drawPath(rays, paint)
            canvas.restoreToCount(save)
            // The same twinkles the achievement art uses, fixed in the corners.
            twinkle(canvas, width * .12f, height * .3f, dpf(7f))
            twinkle(canvas, width * .88f, height * .24f, dpf(9f))
            twinkle(canvas, width * .82f, height * .74f, dpf(5f))
        }

        private fun twinkle(canvas: Canvas, x: Float, y: Float, r: Float) {
            paint.color = Theme.alpha(Theme.YELLOW, 235)
            val p = star; p.reset()
            p.moveTo(x, y - r); p.lineTo(x + r * .28f, y - r * .28f); p.lineTo(x + r, y); p.lineTo(x + r * .28f, y + r * .28f)
            p.lineTo(x, y + r); p.lineTo(x - r * .28f, y + r * .28f); p.lineTo(x - r, y); p.lineTo(x - r * .28f, y - r * .28f); p.close()
            canvas.drawPath(p, paint)
        }
    }

    private companion object {
        /** The achievement page's grape, so the two new-things screens match. */
        const val GRAPE = 0xFF6A35E8.toInt()
    }
}
