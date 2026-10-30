package cube.run.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import cube.run.GameActivity
import cube.run.R
import cube.run.data.Languages
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.data.Settings
import cube.run.ui.Anim
import cube.run.ui.CandyButton
import cube.run.ui.Theme
import cube.run.ui.UiKit
import kotlin.random.Random

/**
 * The uh-oh screen: a colourful blue screen of death with the sad cube and one big button
 * that copies the crash log, for players who would never file a bug report but can paste
 * text. The game's candy widgets in their own process; nothing from the run is restarted here.
 */
class UhOhActivity : Activity() {
    private lateinit var log: String
    private lateinit var cube: SadCube
    private lateinit var kit: UiKit
    private lateinit var copy: CandyButton

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Languages.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kit = UiKit(this)
        // The game's tap sound and buzzes, following the player's settings.
        runCatching { Settings.init(this); Haptics.init(this); SoundFx.init(this) }
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
        // The logo's look: white letters with an ink outline.
        val title = kit.stageText(caps(R.string.uhoh_title), 54f, gravity = Gravity.START, stroke = 7f).apply {
            letterSpacing = kit.tracking(0.03f)
            setShadowLayer(0f, 0f, 0f, 0) // flat backdrop: no drop shadow
        }
        column.addView(title, margins(top = 0).apply { marginStart = -dp(9) })
        val fresh = intent.getBooleanExtra(EXTRA_FRESH, false)
        val body = kit.text(getString(if (fresh) R.string.uhoh_body_now else R.string.uhoh_body_last), 19f, Theme.WHITE, 500, Gravity.START)
            .apply { setLineSpacing(0f, 1.15f) }
        column.addView(body, margins(top = 4))

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
            background = kit.cardDrawable(radius = 18f)
            setPadding(dp(14), dp(12), dp(14), dp(12) + kit.CARD_LIP)
        }
        column.addView(preview, margins(top = 20))

        copy = kit.button(caps(R.string.uhoh_copy), Theme.YELLOW, UiKit.Size.BIG) { copyLog() }.apply { fit(22f) }
        column.addView(copy, margins(top = 20))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        val share = kit.button(caps(R.string.uhoh_share), Theme.WHITE) { shareLog() }.apply { fit(18f) }
        val again = kit.button(caps(R.string.uhoh_play), Theme.PLAY) { playAgain() }.apply { fit(18f) }
        // Half-width slabs: slimmer sides and one height, so a long label shrinks a little
        // without making its button shorter than its neighbour.
        for (b in listOf(share, again)) b.setPadding(dp(10), dp(8), dp(10), dp(8))
        row.addView(share, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(6) })
        row.addView(again, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(6) })
        column.addView(row, margins(top = 12))

        val hint = kit.text(getString(R.string.uhoh_hint), 15f, Theme.WHITE, 500, Gravity.START)
        column.addView(hint, margins(top = 24))
        val link = kit.text(ISSUES_URL.removePrefix("https://"), 15f, Theme.WHITE, 600, Gravity.START).apply {
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            setPadding(0, dp(6), 0, dp(6))
            setOnClickListener { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ISSUES_URL))) } }
        }
        column.addView(link, margins(top = 0))

        backdrop.keepClear = listOf(title, body, preview, copy, row, hint, link)
        setContentView(root)
        goFullscreen()
        enter(listOf(title, body, preview), listOf(copy, share, again, hint, link))
    }

    /** Text rises in one line after another, then the buttons pop in. */
    private fun enter(texts: List<View>, actions: List<View>) {
        texts.forEachIndexed { i, v -> Anim.riseIn(v, 250L + i * 70L, dp(18).toFloat(), 260) }
        actions.forEachIndexed { i, v -> Anim.popIn(v, 520L + i * 60L, from = 0.85f, duration = 300) }
    }

    private fun copyLog() {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(CrashReport.TITLE, log))
        copy.setLabel(caps(R.string.uhoh_copied))
        Anim.pulse(copy, 1.06f, 260)
        Haptics.success()
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

    private fun caps(id: Int) = getString(id).uppercase(resources.configuration.locales[0])

    /** Long translations shrink to one line instead of wrapping inside the slab. */
    private fun CandyButton.fit(sp: Float) {
        maxLines = 1
        setAutoSizeTextTypeUniformWithConfiguration(11, sp.toInt(), 1, TypedValue.COMPLEX_UNIT_SP)
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
