package cube.run.core

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import cube.run.R

/**
 * The game's HUD overlay: score + best at the top, animated center banners,
 * and the game-over card. Must only be touched from the UI thread
 * (GameHostSession marshals for you).
 *
 * Created programmatically (never inflated from XML) and shows dynamic,
 * single-locale game text (scores, banners), so ViewConstructor / SetTextI18n
 * are intentionally suppressed.
 */
@SuppressLint("SetTextI18n", "ViewConstructor")
class GameChromeView(private val activity: Activity, private val accent: Int) : FrameLayout(activity) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    private val scoreText = TextView(activity).apply {
        textSize = 52f
        setTextColor(Color.WHITE)
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        gravity = Gravity.CENTER_HORIZONTAL
        setShadowLayer(dp(6f).toFloat(), 0f, dp(2f).toFloat(), 0x80000000.toInt())
        text = "0"
    }

    private val bestText = TextView(activity).apply {
        textSize = 15f
        setTextColor(Palette.withAlpha(Color.WHITE, 170))
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private val bannerText = TextView(activity).apply {
        textSize = 40f
        setTextColor(accent)
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        gravity = Gravity.CENTER
        setShadowLayer(dp(8f).toFloat(), 0f, dp(2f).toFloat(), 0xA0000000.toInt())
        alpha = 0f
    }

    // Low-key prompt shown (softly pulsing) until the first touch starts a run.
    private val startHint = TextView(activity).apply {
        text = "Tap to start"
        textSize = 22f
        setTextColor(Palette.withAlpha(Color.WHITE, 160))
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setShadowLayer(dp(6f).toFloat(), 0f, dp(1f).toFloat(), 0x80000000.toInt())
    }

    private var overCard: View? = null
    private var bestPulse: ValueAnimator? = null
    private var startPulse: ValueAnimator? = null
    private val topBox = LinearLayout(activity)

    // Pre-run options: experimental "smooth control" toggle + its sensitivity. Hidden once a run begins.
    private val sensBar = SeekBar(activity).apply {
        max = 100
        progress = (Settings.smoothSensitivity * 100f).toInt()
        progressTintList = ColorStateList.valueOf(accent)
        thumbTintList = ColorStateList.valueOf(accent)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) Settings.setSmoothSensitivity(p / 100f)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { Haptics.tick() }
        })
    }
    private val sensRow = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = if (Settings.smoothControl) VISIBLE else GONE
        addView(TextView(activity).apply {
            text = "Sensitivity"
            setTextColor(Palette.withAlpha(Color.WHITE, 200))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        })
        addView(sensBar, LinearLayout.LayoutParams(dp(150f), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(10f)
        })
    }
    private val smoothCheck = CheckBox(activity).apply {
        isChecked = Settings.smoothControl
        buttonTintList = ColorStateList.valueOf(accent)
        setOnCheckedChangeListener { _, c ->
            SoundFx.play("tap"); Haptics.tick()
            Settings.setSmoothControl(c)
            sensRow.visibility = if (c) VISIBLE else GONE
        }
    }
    private val optionsBox = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16f), dp(8f), dp(18f), dp(10f))
        background = GradientDrawable().apply {
            cornerRadius = dp(22f).toFloat()
            setColor(Palette.withAlpha(Color.BLACK, 115))
            setStroke(dp(1f), Palette.withAlpha(accent, 150))
        }
        addView(LinearLayout(activity).apply { // toggle row (tapping the label toggles too)
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            addView(smoothCheck)
            addView(TextView(activity).apply {
                text = "Smooth control (beta)"
                setTextColor(Color.WHITE)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            })
            setOnClickListener { smoothCheck.toggle() }
        })
        addView(sensRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2f)
        })
    }

    /**
     * A round, icon-only pre-run toggle (styled to match [optionsBox]: translucent
     * black fill, accent ring). Reflects [isOn]; tapping flips it, gives audio + haptic
     * confirmation that honours the *new* state (muting is silent, un-muting isn't),
     * then repaints.
     */
    private fun makeToggle(
        iconOn: Int,
        iconOff: Int,
        label: String,
        isOn: () -> Boolean,
        set: (Boolean) -> Unit,
    ): ImageView = ImageView(activity).apply {
        val pad = dp(11f)
        setPadding(pad, pad, pad, pad)
        scaleType = ImageView.ScaleType.FIT_CENTER
        isClickable = true
        isFocusable = true
        fun paint() {
            val on = isOn()
            setImageResource(if (on) iconOn else iconOff)
            imageTintList = ColorStateList.valueOf(if (on) accent else Palette.withAlpha(Color.WHITE, 110))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Palette.withAlpha(Color.BLACK, 115))
                setStroke(dp(1f), Palette.withAlpha(if (on) accent else Color.WHITE, if (on) 150 else 60))
            }
            contentDescription = "$label ${if (on) "on" else "off"}"
        }
        paint()
        setOnClickListener {
            set(!isOn())
            SoundFx.play("tap"); Haptics.tick()
            paint()
        }
    }

    private val soundBtn = makeToggle(
        R.drawable.ic_sound_on, R.drawable.ic_sound_off, activity.getString(R.string.cd_sound),
        { Settings.soundEnabled }, { Settings.setSoundEnabled(it) },
    )
    private val hapticBtn = makeToggle(
        R.drawable.ic_haptic_on, R.drawable.ic_haptic_off, activity.getString(R.string.cd_haptics),
        { Settings.hapticsEnabled }, { Settings.setHapticsEnabled(it) },
    )
    private val toggleBox = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val size = dp(46f)
        addView(soundBtn, LinearLayout.LayoutParams(size, size))
        addView(hapticBtn, LinearLayout.LayoutParams(size, size).apply { leftMargin = dp(12f) })
    }

    init {
        isClickable = false
        isFocusable = false

        topBox.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(scoreText)
            addView(bestText)
        }
        addView(topBox, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(48f)
        })
        addView(bannerText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        })
        addView(startHint, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        })
        startPulse = ValueAnimator.ofFloat(0.45f, 0.8f).apply {
            duration = 1000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { a -> startHint.alpha = a.animatedValue as Float }
            start()
        }
        // The smooth-control toggle + sensitivity only exist when the feature flag is on.
        if (Settings.SMOOTH_CONTROL_UI) {
            addView(optionsBox, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(168f) // sit above the bottom edge / debug slider
            })
        }

        // Sound + vibration toggles, tucked into the bottom-left of the pre-run menu.
        addView(toggleBox, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            leftMargin = dp(20f)
            bottomMargin = dp(28f)
        })

        // Keep the HUD clear of the status bar / cutout (top) and nav bar / cutout (bottom-left).
        setOnApplyWindowInsetsListener { _, insets ->
            val top: Int
            val left: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= 30) {
                top = insets.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top
                val nav = insets.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
                left = nav.left; bottom = nav.bottom
            } else {
                @Suppress("DEPRECATION") top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION") left = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION") bottom = insets.systemWindowInsetBottom
            }
            (topBox.layoutParams as LayoutParams).topMargin = maxOf(dp(48f), top + dp(10f))
            topBox.requestLayout()
            (toggleBox.layoutParams as LayoutParams).apply {
                leftMargin = dp(20f) + left
                bottomMargin = dp(28f) + bottom
            }
            toggleBox.requestLayout()
            insets
        }
    }

    override fun onDetachedFromWindow() {
        bestPulse?.cancel()
        bestPulse = null
        startPulse?.cancel()
        startPulse = null
        super.onDetachedFromWindow()
    }

    fun setBest(best: Int) {
        bestText.text = if (best > 0) "BEST $best" else ""
    }

    /** Hide the "Tap to start" prompt and the pre-run options once a run has begun. */
    fun hideOptions() {
        startPulse?.cancel(); startPulse = null
        if (startHint.visibility == VISIBLE) {
            startHint.animate().alpha(0f).setDuration(160)
                .withEndAction { startHint.visibility = GONE }.start()
        }
        if (optionsBox.parent != null && optionsBox.visibility == VISIBLE) {
            optionsBox.animate().alpha(0f).setDuration(160)
                .withEndAction { optionsBox.visibility = GONE }.start()
        }
        if (toggleBox.visibility == VISIBLE) {
            toggleBox.animate().alpha(0f).setDuration(160)
                .withEndAction { toggleBox.visibility = GONE }.start()
        }
    }

    fun setScore(v: Int) {
        scoreText.text = v.toString()
        scoreText.animate().cancel()
        scoreText.scaleX = 1.18f
        scoreText.scaleY = 1.18f
        scoreText.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
    }

    /** A vivid random hue for the center banner — fresh each time; the drop shadow
     *  (not an outline) keeps it legible on whatever colour the world is. */
    private fun randomBannerColor(): Int =
        Color.HSVToColor(floatArrayOf((Math.random() * 360.0).toFloat(), 0.75f, 1f))

    fun banner(text: String) {
        bannerText.animate().cancel()
        bannerText.text = text
        bannerText.setTextColor(randomBannerColor())
        bannerText.alpha = 1f
        bannerText.scaleX = 0.5f
        bannerText.scaleY = 0.5f
        bannerText.animate()
            .scaleX(1.1f).scaleY(1.1f)
            .setStartDelay(0)
            .setInterpolator(OvershootInterpolator(2.4f))
            .setDuration(220)
            .withEndAction {
                bannerText.animate().alpha(0f)
                    .setStartDelay(380)
                    .setInterpolator(DecelerateInterpolator())
                    .setDuration(300)
                    .withEndAction { bannerText.animate().setStartDelay(0) }
                    .start()
            }
            .start()
    }

    fun showGameOver(score: Int, best: Int, isNewBest: Boolean) {
        if (overCard != null) return
        scoreText.visibility = GONE
        bestText.visibility = GONE

        val scrim = FrameLayout(activity).apply {
            setBackgroundColor(0xB8000000.toInt())
            isClickable = true
            alpha = 0f
            animate().alpha(1f).setDuration(260).start()
        }

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28f), dp(26f), dp(28f), dp(24f))
            background = GradientDrawable().apply {
                cornerRadius = dp(24f).toFloat()
                setColor(0xFF1E1840.toInt())
                setStroke(dp(2f), accent)
            }
        }

        card.addView(TextView(activity).apply {
            text = "GAME OVER"
            textSize = 26f
            setTextColor(accent)
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        card.addView(TextView(activity).apply {
            text = score.toString()
            textSize = 58f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8f)
        })

        val bestLabel = TextView(activity).apply {
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            if (isNewBest) {
                text = "★ NEW BEST! ★"
                setTextColor(accent)
            } else {
                text = "BEST $best"
                setTextColor(Palette.withAlpha(Color.WHITE, 180))
            }
        }
        card.addView(bestLabel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4f); bottomMargin = dp(20f)
        })
        if (isNewBest) {
            bestPulse = ValueAnimator.ofFloat(1f, 1.12f, 1f).apply {
                duration = 700; repeatCount = ValueAnimator.INFINITE
                addUpdateListener { a ->
                    val s = a.animatedValue as Float
                    bestLabel.scaleX = s; bestLabel.scaleY = s
                }
                start()
            }
        }

        fun button(label: String, filled: Boolean, onClick: () -> Unit) = TextView(activity).apply {
            text = label
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(36f), dp(13f), dp(36f), dp(13f))
            if (filled) {
                setTextColor(0xFF14102E.toInt())
                background = GradientDrawable().apply {
                    cornerRadius = dp(30f).toFloat(); setColor(accent)
                }
            } else {
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = dp(30f).toFloat(); setColor(Color.TRANSPARENT)
                    setStroke(dp(2f), Palette.withAlpha(Color.WHITE, 140))
                }
            }
            setOnClickListener {
                SoundFx.play("tap"); Haptics.click()
                onClick()
            }
        }

        // finish + relaunch (NOT recreate): libGDX only disposes GL resources when
        // the activity is truly finishing, so recreate() would leak native meshes.
        // The relaunch must stay INSIDE the current task: a fresh intent (never a
        // copy of activity.intent, whose launcher FLAG_ACTIVITY_NEW_TASK would spawn
        // a new task) started BEFORE finish() (so the task never empties). A
        // task-to-task swap always plays the OEM's default slide on Android 12+ —
        // apps cannot suppress task transitions — whereas this in-task activity open
        // honours FLAG_ACTIVITY_NO_ANIMATION and cuts instantly. The old activity
        // finishes hidden underneath, releasing its GL resources as before.
        card.addView(button("RESTART", true) {
            val relaunch = Intent(activity, activity.javaClass)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            activity.startActivity(relaunch)
            activity.finish()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        card.addView(button("EXIT", false) { activity.finish() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10f)
            })

        scrim.addView(card, LayoutParams(dp(290f), LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        })
        // No slide-up: the card just fades in with the scrim, so the death
        // animation that now plays beforehand isn't cut off by a moving panel.

        overCard = scrim
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
}
