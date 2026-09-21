package cube.run.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.os.SystemClock
import android.text.Layout
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import cube.run.data.Achievements
import cube.run.ui.Anim.move

/** A quiet run companion: one award at a time, no sounds, and generous breathing room. */
@SuppressLint("ViewConstructor")
class AchievementToast(activity: Activity, private val kit: UiKit) : FrameLayout(activity) {
    private val pending = LinkedHashMap<String, Achievements.Unlock>()
    private var active = false
    private var showing = false
    private var currentUnlock: Achievements.Unlock? = null
    private var nextAt = 0L
    private val badge = ImageView(activity)
    private val title = kit.text("", 15f, Theme.INK, 700, Gravity.START).apply {
        setSingleLine()
        setHorizontallyScrolling(false)
    }
    private val detail = kit.text("", 12f, Theme.INK, 700, Gravity.START)
    private val words = object : LinearLayout(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight - kit.dp(2f)
            val measurePaint = TextPaint(title.paint).apply {
                textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, title.resources.displayMetrics)
            }
            val needed = Layout.getDesiredWidth(title.text, measurePaint)
            if (needed > 0 && available > 0) {
                val fitted = measurePaint.textSize * (available / needed).coerceIn(.5f, 1f)
                if (kotlin.math.abs(title.textSize - fitted) > .1f) title.setTextSize(TypedValue.COMPLEX_UNIT_PX, fitted)
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply {
        orientation = LinearLayout.VERTICAL
        addView(title, LinearLayout.LayoutParams(-1, -2))
        addView(detail, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(3f) })
    }
    private val card = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = kit.cardDrawable(Theme.WHITE, null, 20f)
        setPadding(kit.dp(14f), kit.dp(10f), kit.dp(16f), kit.dp(14f))
        addView(badge, LinearLayout.LayoutParams(kit.dp(40f), kit.dp(44f)))
        addView(words, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = kit.dp(12f) })
        visibility = INVISIBLE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        badge.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        title.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        detail.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val advance = Runnable { showNext() }
    private val retreat = Runnable {
        card.move().alpha(0f).translationY(-kit.dpf(8f)).setDuration(220).withEndAction {
            card.visibility = INVISIBLE
            showing = false
            currentUnlock = null
            schedule()
        }.start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        card.layoutParams.width = (MeasureSpec.getSize(widthMeasureSpec) - kit.dp(32f)).coerceIn(kit.dp(180f), kit.dp(340f))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    init {
        isClickable = false; isFocusable = false
        clipChildren = false; clipToPadding = false
        addView(card, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        })
    }

    /** Multiple thresholds crossed together become a single toast for the highest medal. */
    fun enqueue(unlocks: List<Achievements.Unlock>) {
        for (unlock in unlocks) {
            val old = pending[unlock.definition.id]
            if (old == null || unlock.tier > old.tier) pending[unlock.definition.id] = unlock
        }
        schedule()
    }

    /** Pause/page transitions hide immediately; waiting awards resume after gameplay resumes. */
    fun setRunActive(value: Boolean) {
        if (active == value) return
        active = value
        removeCallbacks(advance); removeCallbacks(retreat)
        if (!value) {
            requeueInterrupted()
            Anim.cancelTree(card)
            card.visibility = INVISIBLE
            showing = false
        } else {
            nextAt = maxOf(nextAt, SystemClock.uptimeMillis() + 2200L)
            schedule()
        }
    }

    /** Call for a new run, so old awards never spill into the opening seconds. */
    fun reset() {
        setRunActive(false)
        pending.clear()
        currentUnlock = null
        nextAt = 0L
    }

    /** Pausing in the middle of the reveal must not consume an award the player missed. */
    private fun requeueInterrupted() {
        val interrupted = currentUnlock ?: return
        val laterTier = pending.remove(interrupted.definition.id)
        val resume = laterTier?.takeIf { it.tier > interrupted.tier } ?: interrupted
        val waiting = pending.toMap()
        pending.clear()
        pending[resume.definition.id] = resume
        pending.putAll(waiting)
        currentUnlock = null
    }

    private fun schedule() {
        if (!active || showing || pending.isEmpty() || !isAttachedToWindow) return
        removeCallbacks(advance)
        postDelayed(advance, (nextAt - SystemClock.uptimeMillis()).coerceAtLeast(0L))
    }

    private fun showNext() {
        if (!active || showing || pending.isEmpty()) return
        val first = pending.entries.first()
        val unlock = first.value
        pending.remove(first.key)
        val color = if (unlock.definition.tiered) medalColor(unlock.tier) else Theme.MINT
        badge.setImageDrawable(if (unlock.definition.tiered) MedalIcon(color, true, unlock.tier == 3) else AchievementCheckIcon())
        title.text = unlock.definition.title
        val reward = Achievements.reward(unlock.definition, unlock.tier)
        detail.text = kit.coins(number(reward), 12f)
        card.contentDescription = "${title.text}. ${unlock.tierName}. Reward: ${number(reward)} coins."
        showing = true
        currentUnlock = unlock
        card.visibility = VISIBLE
        Anim.reset(card)
        Anim.riseIn(card, 0L, -kit.dpf(10f), 280L)
        nextAt = SystemClock.uptimeMillis() + 10000L
        postDelayed(retreat, 2800L)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); schedule() }
    override fun onDetachedFromWindow() {
        removeCallbacks(advance); removeCallbacks(retreat)
        requeueInterrupted()
        Anim.cancelTree(card)
        showing = false
        card.visibility = INVISIBLE
        super.onDetachedFromWindow()
    }
}
