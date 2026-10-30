package cube.run.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import cube.run.GameActivity
import cube.run.R
import cube.run.data.Languages
import cube.run.ui.Fonts
import cube.run.ui.Theme
import kotlin.random.Random

/**
 * The uh-oh screen: a colourful blue screen of death with a sad cube, a fake progress count
 * and one big button that copies the crash log, for players who would never file a bug report
 * but can paste text. Plain Android views in its own process, nothing from the game runs here.
 */
class UhOhActivity : Activity() {
    private lateinit var log: String
    private lateinit var cube: SadCube

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Languages.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashLog.named(this, intent.getStringExtra(EXTRA_REPORT)) ?: CrashLog.pending(this) ?: CrashLog.reports(this).firstOrNull()
        CrashLog.clearPending(this)
        if (report == null) { playAgain(); return }
        log = report.readText()
        val seed = report.name.filter { it.isDigit() }.toLongOrNull() ?: 0L
        val random = Random(seed)
        val color = if (random.nextFloat() < 0.4f) BSOD_BLUE else intArrayOf(Theme.GRAPE, Theme.BERRY, Theme.PINK, TANGERINE, BSOD_BLUE).random(random)
        window.setBackgroundDrawable(ColorDrawable(color))

        val root = FrameLayout(this)
        val backdrop = GlitchBackdrop(this, color, seed)
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(column)
            setOnScrollChangeListener { _, _, _, _, _ -> backdrop.invalidate() }
        }, FrameLayout.LayoutParams(-1, -1))
        root.setOnApplyWindowInsetsListener { _, insets ->
            // Keep clear of cutouts and of the bars a swipe brings back.
            val (l, t, r, b) = if (android.os.Build.VERSION.SDK_INT >= 30) {
                insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    .let { listOf(it.left, it.top, it.right, it.bottom) }
            } else {
                insets.displayCutout.let { listOf(it?.safeInsetLeft ?: 0, it?.safeInsetTop ?: 0, it?.safeInsetRight ?: 0, it?.safeInsetBottom ?: 0) }
            }
            column.setPadding(dp(28) + l, dp(20) + t, dp(28) + r, dp(28) + b)
            insets
        }

        cube = SadCube(this)
        column.addView(cube, LinearLayout.LayoutParams(dp(150), dp(150)).apply { gravity = Gravity.START; marginStart = -dp(18) })
        val title = text(getString(R.string.uhoh_title), 52f, 700)
        column.addView(title)
        val fresh = intent.getBooleanExtra(EXTRA_FRESH, false)
        val body = text(getString(if (fresh) R.string.uhoh_body_now else R.string.uhoh_body_last), 20f, 500).apply { setLineSpacing(0f, 1.1f) }
        column.addView(body, margins(top = 6))
        val progress = text(getString(R.string.uhoh_progress, 0), 20f, 600)
        column.addView(progress, margins(top = 18))

        val preview = TextView(this).apply {
            // The error itself rather than the device header, for players who send a screenshot.
            text = log.substringAfter("\n\n", log).trimStart()
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTextColor(Theme.INK)
            maxLines = 9
            ellipsize = android.text.TextUtils.TruncateAt.END
            textDirection = View.TEXT_DIRECTION_LTR
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            background = slab(Theme.WHITE, Theme.darken(color, 0.35f), 16, 5)
            setPadding(dp(14), dp(12), dp(14), dp(17))
        }
        column.addView(preview, margins(top = 18))

        val copy = button(getString(R.string.uhoh_copy), Theme.YELLOW, 22f).apply { setOnClickListener { copyLog(this) } }
        column.addView(copy, margins(top = 20, height = 64))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val share = button(getString(R.string.uhoh_share), Theme.WHITE, 18f).apply { setOnClickListener { shareLog() } }
        val again = button(getString(R.string.uhoh_play), Theme.MINT, 18f).apply { setOnClickListener { playAgain() } }
        row.addView(share, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(6) })
        row.addView(again, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(6) })
        column.addView(row, margins(top = 12))

        val hint = text(getString(R.string.uhoh_hint), 15f, 500)
        column.addView(hint, margins(top = 22))
        val link = text(ISSUES_URL.removePrefix("https://"), 15f, 600).apply {
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            setPadding(0, dp(4), 0, dp(4))
            setOnClickListener { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ISSUES_URL))) } }
        }
        column.addView(link)

        backdrop.keepClear = listOf(title, body, progress, hint, link)
        setContentView(root)
        goFullscreen()
        enter(listOf(title, body, progress, preview), listOf(copy, row, hint, link), progress)
    }

    /** Text slides up in a stagger, the percentage counts up blue-screen style, then the buttons pop. */
    private fun enter(texts: List<View>, actions: List<View>, progress: TextView) {
        texts.forEachIndexed { i, v ->
            v.alpha = 0f; v.translationY = dp(18).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(250L + i * 70L).setDuration(260).start()
        }
        actions.forEach { it.alpha = 0f; it.scaleX = 0.85f; it.scaleY = 0.85f }
        android.animation.ValueAnimator.ofInt(0, 100).apply {
            startDelay = 450
            duration = 1100
            interpolator = android.view.animation.DecelerateInterpolator(1.6f)
            addUpdateListener { progress.text = getString(R.string.uhoh_progress, it.animatedValue as Int) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    actions.forEachIndexed { i, v ->
                        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(i * 60L).setDuration(280)
                            .setInterpolator(OvershootInterpolator(2f)).start()
                    }
                }
            })
        }.start()
    }

    private fun copyLog(button: TextView) {
        button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(CrashReport.TITLE, log))
        button.text = getString(R.string.uhoh_copied)
        button.animate().cancel()
        button.scaleX = 0.92f; button.scaleY = 0.92f
        button.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(320).setInterpolator(OvershootInterpolator(3f)).start()
        cube.cheer()
    }

    private fun shareLog() {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, CrashReport.TITLE).putExtra(Intent.EXTRA_TEXT, log)
        runCatching { startActivity(Intent.createChooser(send, getString(R.string.uhoh_share))) }
        cube.cheer()
    }

    private fun playAgain() {
        startActivity(Intent(this, GameActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullscreen()
    }

    private fun goFullscreen() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }

    // ----------------------------------------------------------------- tiny candy kit

    private fun text(value: String, sp: Float, weight: Int) = TextView(this).apply {
        text = value
        typeface = Fonts.get(this@UhOhActivity, weight)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(Theme.WHITE)
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    }

    /** A chunky slab button with a darker lip; pressing pushes the face down onto it. */
    private fun button(label: String, color: Int, sp: Float) = text(label, sp, 700).apply {
        setTextColor(Theme.INK)
        gravity = Gravity.CENTER
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        isAllCaps = true
        val lip = Theme.darken(color, 0.28f)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), slab(Theme.darken(color, 0.06f), lip, 18, 6, pressed = true))
            addState(intArrayOf(), slab(color, lip, 18, 6))
        }
        setPadding(dp(12), 0, dp(12), dp(6))
    }

    private fun slab(face: Int, lip: Int, radius: Int, depth: Int, pressed: Boolean = false): Drawable {
        fun round(c: Int) = GradientDrawable().apply { setColor(c); cornerRadius = dp(radius).toFloat() }
        return LayerDrawable(arrayOf(round(lip), round(face))).apply {
            setLayerInset(0, 0, dp(depth), 0, 0)
            if (pressed) setLayerInset(1, 0, dp(depth - 2), 0, 2) else setLayerInset(1, 0, 0, 0, dp(depth))
        }
    }

    private fun margins(top: Int, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT) =
        LinearLayout.LayoutParams(-1, if (height > 0) dp(height) else height).apply { topMargin = dp(top) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_REPORT = "report"
        private const val EXTRA_FRESH = "fresh"
        private const val ISSUES_URL = "https://github.com/Eve-146T/cube-run/issues"
        private const val BSOD_BLUE = 0xFF1F5BFF.toInt()
        private const val TANGERINE = 0xFFFF6A1F.toInt()

        /** [fresh]: the game crashed a moment ago (rather than last time it ran). */
        fun intent(ctx: Context, report: String, fresh: Boolean): Intent = Intent(ctx, UhOhActivity::class.java)
            .putExtra(EXTRA_REPORT, report).putExtra(EXTRA_FRESH, fresh)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
