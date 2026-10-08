package cube.run.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import cube.run.BuildConfig
import cube.run.R
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.data.Languages
import cube.run.data.Progress
import cube.run.data.Settings

/**
 * Settings, the one place for options: a bright page of white cards, each row
 * a small candy tile, a name and its control. Sound (tap the tile to mute,
 * drag the steps for volume) and vibration; the start speed (boost presses
 * made before the run) and coins on the road; the language (opens the
 * language sheet); in debug builds, dev mode and the section explorer.
 * Everything re-reads [Settings] whenever the page is shown, so the pause
 * card's switches never leave it stale.
 */
@SuppressLint("ViewConstructor")
class SettingsView(
    activity: Activity,
    kit: UiKit,
    private val openLanguages: () -> Unit,
    private val openSections: () -> Unit,
    private val devChanged: () -> Unit,
    onClosed: () -> Unit,
) : Page(activity, kit, activity.getString(R.string.settings_title), dark = false, onClosed = onClosed) {

    private val soundTile = tile(R.drawable.ic_sound_on, Theme.SKY, decorative = false).apply {
        setOnClickListener {
            Settings.setSoundEnabled(!Settings.soundEnabled)
            Anim.tap(this)
            Haptics.click(); SoundFx.play("tap")
            sync(animate = true)
        }
    }
    private val volume = VolumeSteps(activity, ::dpf) { activity.getString(R.string.settings_volume) }.apply {
        onPicked = { steps ->
            Settings.setVolume(steps)
            if (!Settings.soundEnabled) Settings.setSoundEnabled(true)
            SoundFx.play("tap")
            sync(animate = true)
        }
    }
    private val soundGroup = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false; clipToPadding = false
    }
    private val soundRow = row(soundTile, activity.getString(R.string.cd_sound))
    private var stackedVolume = false
    private val hapticsTile = tile(R.drawable.ic_haptic_on, Theme.SKY)
    private val hapticsSwitch = CandySwitch(activity, ::dpf)
    private val speedBar = StartSpeedBar(activity, ::dpf) { activity.getString(R.string.settings_start_speed) }.apply {
        onPicked = { presses ->
            Settings.setStartSpeed(presses)
            SoundFx.play("tap", rate = 0.8f + presses * 0.09f)
        }
    }
    private val coinGlyph = CoinGlyph()
    private val coinsTile = tile(coinGlyph, Theme.GOLD)
    private val coinsSwitch = CandySwitch(activity, ::dpf)
    private val devTile = tile(R.drawable.ic_dev_on, Theme.ORANGE)
    private val devSwitch = CandySwitch(activity, ::dpf)
    private val flag = ImageView(activity).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val languageName = kit.text("", 17f, Theme.INK_SOFT, 600)
    private val toggles = ArrayList<Triple<View, () -> Boolean, String>>()
    private val cards = ArrayList<View>()
    private var shownSound: Boolean? = null
    private var shownHaptics: Boolean? = null
    private var shownCoins: Boolean? = null
    private var shownDev: Boolean? = null
    private var shownLanguage: String? = null

    init {
        setBackgroundColor(Theme.SETTINGS_BLUE)
        soundRow.addView(volume, LinearLayout.LayoutParams(dp(166f), dp(40f)))
        soundGroup.addView(soundRow)
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            setPaddingRelative(dp(14f), dp(6f), dp(14f), dp(24f))
        }
        column.addView(card(
            soundGroup,
            switchRow(hapticsTile, activity.getString(R.string.cd_haptics), hapticsSwitch, { Settings.hapticsEnabled }) {
                Settings.setHapticsEnabled(it); Progress.recordHapticTap(); if (it) Haptics.click()
            },
        ))
        column.addView(card(
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = false; clipToPadding = false
                setPadding(0, dp(8f), 0, dp(12f))
                addView(row(tile(R.drawable.ic_speed, Theme.ORANGE), activity.getString(R.string.settings_start_speed)).apply { minimumHeight = dp(52f); setPadding(0, 0, 0, 0) })
                addView(speedBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46f)).apply { topMargin = dp(8f) })
            },
            switchRow(coinsTile, activity.getString(R.string.settings_coins), coinsSwitch, { Settings.roadCoins }) { Settings.setRoadCoins(it) },
        ))
        column.addView(card(
            row(tile(R.drawable.ic_language, Theme.CYAN), activity.getString(R.string.settings_language), LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(flag, LinearLayout.LayoutParams(dp(30f), dp(20f)))
                addView(languageName, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(9f) })
                addView(chevron(), LinearLayout.LayoutParams(dp(22f), dp(22f)).apply { marginStart = dp(4f) })
            }).apply {
                contentDescription = activity.getString(R.string.cd_languages)
                link { openLanguages() }
            },
        ))
        if (BuildConfig.DEBUG) column.addView(card(
            switchRow(devTile, activity.getString(R.string.cd_dev), devSwitch, { Settings.devMode }) {
                Settings.setDevMode(it); if (it) Progress.enterDev() else Progress.leaveDev(); devChanged()
            },
            row(tile(R.drawable.ic_sections, Theme.MUTED), activity.getString(R.string.cd_sections), chevron(), dp(22f), dp(22f)).apply {
                contentDescription = activity.getString(R.string.cd_sections)
                link { openSections() }
            },
            dashed = true,
        ))
        column.addView(kit.text(activity.getString(R.string.settings_version, BuildConfig.VERSION_NAME), 13.5f, Theme.MUTED, 600),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f) })
        content.addView(ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            clipChildren = false; clipToPadding = false
            addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        sync(animate = false)
    }

    override fun onNavigationShown() = sync(animate = false)

    /** Prepaint text once; a moving switch only dirties its small card texture. */
    internal fun warmControls() {
        cards.forEach { it.setLayerType(LAYER_TYPE_HARDWARE, null); it.buildLayer() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val narrow = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight < dp(340f) ||
            resources.configuration.fontScale > 1.15f
        val languageVisibility = if (narrow) GONE else VISIBLE
        if (languageName.visibility != languageVisibility) languageName.visibility = languageVisibility
        if (narrow != stackedVolume) {
            stackedVolume = narrow
            (volume.parent as? android.view.ViewGroup)?.removeView(volume)
            if (narrow) soundGroup.addView(volume, LinearLayout.LayoutParams(-1, dp(44f)).apply { bottomMargin = dp(12f) })
            else soundRow.addView(volume, LinearLayout.LayoutParams(dp(166f), dp(40f)))
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** Every control follows [Settings] (and the presses you own). */
    private fun sync(animate: Boolean) {
        val sound = Settings.soundEnabled
        if (shownSound != sound) {
            shownSound = sound
            paintTile(soundTile, sound, Theme.SKY)
            soundTile.setImageResource(if (sound) R.drawable.ic_sound_on else R.drawable.ic_sound_off)
            soundTile.contentDescription = context.getString(if (sound) R.string.text_toggle_on else R.string.text_toggle_off, context.getString(R.string.cd_sound))
        }
        volume.show(Settings.volume); volume.muted = !sound
        hapticsSwitch.set(Settings.hapticsEnabled, animate)
        if (shownHaptics != Settings.hapticsEnabled) {
            shownHaptics = Settings.hapticsEnabled
            paintTile(hapticsTile, Settings.hapticsEnabled, Theme.SKY)
            hapticsTile.setImageResource(if (Settings.hapticsEnabled) R.drawable.ic_haptic_on else R.drawable.ic_haptic_off)
        }
        speedBar.configure(Progress.maxStartPresses, Settings.startSpeed)
        coinsSwitch.set(Settings.roadCoins, animate)
        if (shownCoins != Settings.roadCoins) {
            shownCoins = Settings.roadCoins
            paintTile(coinsTile, Settings.roadCoins, Theme.GOLD)
            coinGlyph.off = !Settings.roadCoins; coinsTile.invalidate()
        }
        devSwitch.set(Settings.devMode, animate)
        if (shownDev != Settings.devMode) {
            shownDev = Settings.devMode
            paintTile(devTile, Settings.devMode, Theme.ORANGE)
        }
        val language = Languages.current(context)
        if (shownLanguage != language) {
            shownLanguage = language
            val option = Languages.options.single { it.code == language }
            flag.setImageResource(option.flag)
            languageName.text = option.nativeName
        }
        for ((row, on, label) in toggles) row.contentDescription =
            context.getString(if (on()) R.string.text_toggle_on else R.string.text_toggle_off, label)
    }

    private fun paintTile(tile: CandyChip, on: Boolean, color: Int) {
        tile.color = if (on) color else OFF_TILE
        // the coin glyph paints its own two colours; every other icon is a white silhouette
        tile.imageTintList = if (tile.drawable is CoinGlyph) null else ColorStateList.valueOf(if (on) Theme.WHITE else Theme.MUTED)
    }

    /** A small candy cube holding an icon. Decorative tiles pass touches to their row. */
    private fun tile(icon: Any, color: Int, decorative: Boolean = true): CandyChip =
        CandyChip(context, color, dpf(4f), dpf(13f)).apply {
            when (icon) { is Int -> setImageResource(icon); is Drawable -> setImageDrawable(icon) }
            if (icon !is CoinGlyph) imageTintList = ColorStateList.valueOf(Theme.WHITE)
            val pad = dp(11f)
            setPadding(pad, pad, pad, pad) // CandyChip adds its lip below, so the icon sits centred on the face
            if (decorative) {
                setOnTouchListener(null)
                isClickable = false; isFocusable = false
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }

    private fun chevron() = ImageView(context).apply {
        setImageResource(R.drawable.ic_chevron_right)
        imageTintList = ColorStateList.valueOf(Theme.MUTED)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Tile, name, and an optional control at the end. */
    private fun row(tile: View, name: String, end: View? = null, endWidth: Int = 0, endHeight: Int = 0): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            minimumHeight = dp(66f)
            setPadding(0, dp(8f), 0, dp(8f))
            addView(tile, LinearLayout.LayoutParams(dp(44f), dp(48f)))
            addView(kit.text(name, 19f, Theme.INK, 600, Gravity.START).apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14f); marginEnd = dp(10f) })
            if (end != null) addView(end, LinearLayout.LayoutParams(
                if (endWidth > 0) endWidth else LinearLayout.LayoutParams.WRAP_CONTENT,
                if (endHeight > 0) endHeight else LinearLayout.LayoutParams.WRAP_CONTENT))
        }

    /** A row that toggles as a whole; the switch at its end shows the state. */
    private fun switchRow(tile: View, name: String, switch: CandySwitch, isOn: () -> Boolean, set: (Boolean) -> Unit): LinearLayout =
        row(tile, name, switch, dp(58f), dp(34f)).apply {
            toggles.add(Triple(this, isOn, name))
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.Switch"
                    info.isCheckable = true
                    info.isChecked = isOn()
                }
            }
            link { set(!isOn()); sync(animate = true) }
        }

    private fun View.link(action: () -> Unit) {
        isClickable = true; isFocusable = true
        background = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(Theme.alpha(Theme.GRAPE, 22)), null,
            GradientDrawable().apply { cornerRadius = dpf(16f); setColor(Theme.WHITE) })
        Anim.pressFeedback(this)
        setOnClickListener { Anim.tap(this); SoundFx.play("tap"); Haptics.click(); action() }
    }

    /** A white card of rows with hairlines between them (a dashed outline for debug-only rows). */
    private fun card(vararg rows: View, dashed: Boolean = false): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false; clipToPadding = false
        background = if (dashed) GradientDrawable().apply {
            cornerRadius = dpf(24f)
            setColor(Theme.alpha(Theme.WHITE, 90))
            setStroke(dp(2.5f), DASH, dpf(8f), dpf(6f))
        } else kit.cardDrawable(Theme.CARD, null, 24f)
        setPaddingRelative(dp(14f), dp(2f), dp(14f), dp(2f) + if (dashed) 0 else kit.CARD_LIP)
        for ((i, r) in rows.withIndex()) {
            if (i > 0) addView(View(context).apply {
                background = GradientDrawable().apply { cornerRadius = dpf(1f); setColor(HAIRLINE) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2f)).apply { marginStart = dp(58f) })
            addView(r, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }.also { card ->
        cards.add(card)
        card.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14f) }
    }

    private companion object {
        const val OFF_TILE = 0xFFDCEBF7.toInt()
        const val HAIRLINE = 0xFFEAF4FC.toInt()
        const val DASH = 0xFFAACEEA.toInt()
    }
}
