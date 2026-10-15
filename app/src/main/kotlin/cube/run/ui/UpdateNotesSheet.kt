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
    /** Uptime when the entrance has played out (set by [choreograph]). */
    private var entranceEnds = 0L

    /** Milliseconds of entrance still to play: background work waits so the card stays smooth. */
    fun entranceLeftMs(): Long = maxOf(0L, entranceEnds - android.os.SystemClock.uptimeMillis())

    init {
        card.setPadding(0, 0, 0, dp(22f))
        card.background = Back()
        // Long titles (German) may take two lines: the header grows rather than cutting them.
        header.minimumHeight = dp(108f)
        header.setPadding(dp(18f), dp(16f), dp(18f), dp(18f))
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
        for (item in UpdateNotes.items(activity)) list.addView(row(item), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2f) })
        card.addView(list, LinearLayout.LayoutParams(-1, -2))
        card.addView(done, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18f); marginStart = dp(18f); marginEnd = dp(18f) })
        choreograph()
    }

    private fun row(item: UpdateNotes.Item): View {
        val tile = ImageView(activity).apply {
            // Painted ahead off the main thread when the card was due ([UpdateNotesArt]): drawing the tiles' art
            // here, cold, took most of the card's first frame.
            setImageDrawable(UpdateNotesArt.take(item.icon, dp(TILE_DP))?.let { android.graphics.drawable.BitmapDrawable(resources, it) }
                ?: AchievementTile(item.icon, AchievementCards.accent(item.icon)))
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
            setPadding(dp(4f), dp(5f), dp(4f), dp(5f))
            isClickable = true; isFocusable = true
            contentDescription = "${item.title}. ${item.text}"
            addView(tile, LinearLayout.LayoutParams(dp(TILE_DP), dp(TILE_DP)))
            addView(words, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14f) })
            // An idle tap, like a card on the achievement page: the same soft press and tick.
            setOnClickListener {
                Pokes.press(this)
                tile.rotation = -9f
                tile.move().rotation(0f).setDuration(420).setInterpolator(Anim.spring).start()
            }
            rows.add(tile to words)
        }
    }

    /** The card lands, the title pops, then each row in turn; the button rises in last. */
    private fun choreograph() {
        enter(title, delay = 110, duration = 360, curve = Anim.spring, scale = .6f)
        enter(version, delay = 190, duration = 300, curve = Anim.ease, dy = dpf(10f))
        val first = 300L; val step = 120L
        for ((i, pair) in rows.withIndex()) {
            val (tile, words) = pair
            val at = first + i * step
            enter(tile, delay = at, duration = 380, curve = Anim.spring, scale = .35f)
            enter(words, delay = at + 30, duration = 320, curve = Anim.ease, dx = dpf(if (layoutDirection == LAYOUT_DIRECTION_RTL) -22f else 22f))
            later(at) { SoundFx.play("place", rate = 1f + i * .09f, vol = .42f) }
        }
        enter(done, delay = first + rows.size * step + 40, duration = 340, curve = Anim.ease, dy = dpf(18f))
        entranceEnds = android.os.SystemClock.uptimeMillis() + first + rows.size * step + 40 + 340 + 120
    }

    /**
     * An entrance on a hardware layer: the view is painted once and the motion only moves and
     * fades that picture. The window still repaints every frame (the rule over the GL stage, see
     * [Anim.repaint]), but the view itself is not invalidated, so its layer is never redrawn.
     */
    private fun enter(v: View, delay: Long, duration: Long, curve: android.animation.TimeInterpolator,
                      scale: Float = 1f, dx: Float = 0f, dy: Float = 0f) {
        v.animate().cancel()
        v.alpha = 0f; v.scaleX = scale; v.scaleY = scale; v.translationX = dx; v.translationY = dy
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
            .setStartDelay(delay).setDuration(duration).setInterpolator(curve).withLayer()
            .setUpdateListener { v.rootView?.invalidate() }
            .withEndAction { v.requestLayout(); v.rootView?.invalidate() }
            .start()
    }

    private fun later(ms: Long, action: () -> Unit) {
        val r = Runnable(action); pending.add(r); postDelayed(r, ms)
    }

    override fun onDetachedFromWindow() {
        pending.forEach(::removeCallbacks); pending.clear()
        super.onDetachedFromWindow()
    }

    /**
     * The card itself: the grape top, the white body and the lip under it, side by side rather
     * than stacked. Painting the grape over a white card left the white showing through the
     * anti-aliased corner pixels as a pale fringe around the top.
     */
    private inner class Back : android.graphics.drawable.Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val box = RectF()

        override fun draw(canvas: Canvas) {
            val b = bounds; val r = dpf(30f); val split = (b.top + header.bottom).toFloat()
            val lip = kit.CARD_LIP.toFloat()
            fill(canvas, GRAPE, b.top.toFloat(), split, floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f))
            fill(canvas, Theme.alpha(Theme.INK, 26), split, b.bottom.toFloat(), floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r))
            fill(canvas, Theme.CARD, split, b.bottom - lip, floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r))
        }

        private fun fill(canvas: Canvas, color: Int, top: Float, bottom: Float, radii: FloatArray) {
            if (bottom <= top) return
            box.set(bounds.left.toFloat(), top, bounds.right.toFloat(), bottom)
            path.reset(); path.addRoundRect(box, radii, Path.Direction.CW)
            paint.color = color; canvas.drawPath(path, paint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    /** The grape top of the card (painted by [Back]): soft turning rays and twinkles over it. */
    private inner class Header : FrameLayout(activity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        /** The rounded top the rays are trimmed to. */
        private val clip = Path()
        private val rays = Path()
        private val turned = Path()
        private val turning = android.graphics.Matrix()
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
            (parent as? View)?.background?.invalidateSelf()
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
            // The turned rays are cut to the rounded top as a shape and filled anti-aliased:
            // clipPath had hard, stepped corner edges, and an off-screen mask layer cost a
            // full-header buffer every frame.
            turning.setRotate(turn); turning.postTranslate(width / 2f, height * .52f)
            rays.transform(turning, turned)
            turned.op(clip, Path.Op.INTERSECT)
            paint.color = Theme.alpha(Theme.WHITE, 22); canvas.drawPath(turned, paint)
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

    companion object {
        /** A row's tile, square. */
        const val TILE_DP = 54f
        /** A deep grape, so the white title and twinkles stand out. */
        const val GRAPE = 0xFF6A35E8.toInt()
    }
}
