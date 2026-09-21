package cube.run.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import android.view.animation.PathInterpolator
import android.view.animation.LinearInterpolator
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.data.Wardrobe
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.hypot
import kotlin.math.sin

/** A still, deliberately quiet marker; the purchase is what wakes it up. */
internal class VoidSigilView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    override fun onDraw(c: Canvas) {
        val r = min(width, height) * .39f
        val x = width / 2f; val y = height / 2f
        // A torn, square aperture echoes the game's cube silhouettes. Layered bevels
        // recede into an entirely black centre; no planetary orbit around the shop item.
        for (i in 0..3) {
            val scale = 1f - i * .16f
            c.save(); c.rotate(-14f + i * 8f, x, y)
            paint.style = Paint.Style.FILL
            paint.color = intArrayOf(0xff30283f.toInt(), 0xff70617f.toInt(), 0xffb6a5ca.toInt(), 0xff09060f.toInt())[i]
            val half = r * scale
            c.drawRoundRect(x - half, y - half, x + half, y + half, r * .09f, r * .09f, paint)
            paint.color = if (i == 3) 0xff000000.toInt() else 0xff09070e.toInt()
            val inset = r * .045f
            c.drawRoundRect(x - half + inset, y - half + inset, x + half - inset, y + half - inset, r * .055f, r * .055f, paint)
            c.restore()
        }
        paint.color = 0xffd7c9eb.toInt()
        for (i in 0..3) {
            val angle = i * 1.5708f + .3f
            val dx = x + cos(angle) * r * 1.27f; val dy = y + sin(angle) * r * 1.27f
            val size = r * (if (i % 2 == 0) .05f else .035f)
            path.reset(); path.moveTo(dx, dy - size); path.lineTo(dx + size, dy)
            path.lineTo(dx, dy + size); path.lineTo(dx - size, dy); path.close()
            c.drawPath(path, paint)
        }
    }
}

/** An opaque, full-window payment scene. Only its contents fade; the shop never bleeds through. */
@android.annotation.SuppressLint("ViewConstructor")
internal class VoidPurchaseView(
    context: Context,
    private val line: String,
    discovery: Int? = null,
    private val finished: () -> Unit,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.get(context, 700)
        color = Theme.WHITE
    }
    private val density = resources.displayMetrics.density
    private val revealedIcon = discovery?.let { SecretCosmeticIcon(it) }
    private var dialogue: StaticLayout? = null
    private var animator: ValueAnimator? = null
    private var returnAnimator: ValueAnimator? = null
    private val returnMask = Path()
    private var returnRadius = -1f
    private var returnX = 0f
    private var returnY = 0f
    private var time = 0f
    private var impact = false
    private var completed = false

    init {
        // This is a background, not a scrim. It covers even frame zero and the final hold.
        setBackgroundColor(0xff030408.toInt())
        isClickable = true
        isFocusable = true
        contentDescription = "The void stirs"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }

    override fun isOpaque() = returnRadius < 0f

    override fun draw(canvas: Canvas) {
        if (returnRadius < 0f) { super.draw(canvas); return }
        val saved = canvas.save()
        returnMask.reset()
        returnMask.addCircle(returnX, returnY, returnRadius, Path.Direction.CW)
        canvas.clipPath(returnMask)
        super.draw(canvas)
        canvas.restoreToCount(saved)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        @Suppress("DEPRECATION")
        val scaledDensity = resources.displayMetrics.scaledDensity
        textPaint.textSize = min(25f * scaledDensity, w * .08f)
        dialogue = StaticLayout.Builder.obtain(line, 0, line.length, textPaint,
            (w - 64f * density).toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false)
            .setLineSpacing(5f * density, 1f).build()
        revealedIcon?.setBounds(-w / 7, -w / 7, w / 7, w / 7)
    }

    fun play() {
        if (animator != null || completed) return
        SoundFx.play("tap", rate = 0.55f, vol = 0.8f)
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3400
            interpolator = LinearInterpolator()
            addUpdateListener {
                time = it.animatedValue as Float
                if (time >= .47f && !impact) {
                    impact = true
                    Haptics.success()
                    SoundFx.play("success", rate = .6f, vol = .65f)
                    contentDescription = line
                }
                Anim.repaint(this@VoidPurchaseView)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled && !completed) { completed = true; finished() }
                }
            })
            start()
        }
    }

    // Leaving the app must not silently consume the reveal while its window is hidden.
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) animator?.resume() else animator?.pause()
        if (visibility == VISIBLE) returnAnimator?.resume() else returnAnimator?.pause()
    }

    /** The opaque scene folds into the shop item instead of disappearing in one bright frame. */
    fun returnTo(targetX: Float, targetY: Float, onReturned: () -> Unit) {
        if (returnAnimator != null) return
        val x = targetX.coerceIn(0f, width.toFloat())
        val y = targetY.coerceIn(0f, height.toFloat())
        val radius = hypot(maxOf(x, width - x), maxOf(y, height - y)) + 2f
        returnX = x; returnY = y; returnRadius = radius
        returnAnimator = ValueAnimator.ofFloat(radius, 0f).apply {
            duration = 580
            interpolator = PathInterpolator(.4f, 0f, .2f, 1f)
            addUpdateListener {
                returnRadius = it.animatedValue as Float
                Anim.repaint(this@VoidPurchaseView)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) { returnAnimator = null; onReturned() }
                }
            })
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        val cx = width * .5f
        val cy = height * .42f
        val radius = min(width, height) * .155f
        val arrive = smooth(time / .12f)
        val depart = 1f - smooth((time - .94f) / .06f)
        val visible = arrive * depart
        val pull = smooth(time / .47f)
        val burst = ((time - .47f) / .22f).coerceIn(0f, 1f)
        val reveal = smooth((time - .57f) / .12f)
        val collapse = smooth((time - .35f) / .12f)
        val coreScale = if (time < .47f) 1f + pull * .13f - collapse * .5f else .63f + smooth(burst / .55f) * .37f

        // Sparse threads of dust accelerate toward the centre. Each has its own fixed
        // route; nothing jumps to a new random position or wraps around mid-flight.
        if (time < .49f) for (i in 0 until 54) {
            val start = (i * 29 % 53) / 53f * .2f
            val travel = ((time - start) / (.47f - start)).coerceIn(0f, 1f)
            val distance = radius * (.6f + (1f - travel * travel) * (2.6f + (i % 5) * .65f))
            val angle = i * 2.39996f + travel * travel * 2.9f
            val x = cx + cos(angle) * distance
            val y = cy + sin(angle) * distance * .8f
            val dustAlpha = visible * smooth(travel * 5f) * (1f - smooth((travel - .83f) / .17f))
            paint.color = Theme.alpha(0xffb3a7cd.toInt(), (dustAlpha * (90 + i % 4 * 22)).toInt())
            paint.strokeWidth = density * (.6f + travel)
            paint.style = Paint.Style.STROKE
            val tail = (4f + travel * travel * 22f) * density
            c.drawLine(x, y, x + cos(angle - .45f) * tail, y + sin(angle - .45f) * tail * .8f, paint)
        }

        // Actual little gold coins fold into the dark rim, then leave the scene entirely.
        if (time < .47f) for (i in 0 until 12) {
            val start = .025f + i * .012f
            val travel = ((time - start) / (.43f - start)).coerceIn(0f, 1f)
            if (time < start || travel >= 1f) continue
            val eased = travel * travel
            val distance = radius * (.57f + (1f - eased) * (3f + i % 3 * .8f))
            val angle = i * 2.39996f + eased * 3.4f
            val x = cx + cos(angle) * distance
            val y = cy + sin(angle) * distance * .78f
            val size = density * (4.5f - travel * 2f)
            val opacity = smooth(travel * 6f) * visible
            c.save(); c.translate(x, y); c.rotate(travel * 320f + i * 29f)
            paint.style = Paint.Style.FILL
            paint.color = Theme.alpha(0xffa87027.toInt(), (opacity * 255).toInt())
            c.drawOval(-size, -size * .75f, size, size * .95f, paint)
            paint.color = Theme.alpha(Theme.GOLD, (opacity * 255).toInt())
            c.drawOval(-size, -size, size, size * .65f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = density * .8f
            paint.color = Theme.alpha(0xffffedaa.toInt(), (opacity * 230).toInt())
            c.drawOval(-size * .6f, -size * .72f, size * .6f, size * .32f, paint)
            c.restore()
        }

        if (time >= .47f && burst < 1f) {
            paint.style = Paint.Style.STROKE
            for (i in 0..3) {
                val expansion = ((burst - i * .075f) / (1f - i * .075f)).coerceIn(0f, 1f)
                if (expansion == 0f) continue
                paint.strokeWidth = density * (2.4f - expansion * 1.8f)
                paint.color = Theme.alpha(0xffd8d0ef.toInt(), ((1f - expansion) * visible * (160 - i * 25)).toInt())
                c.drawCircle(cx, cy, radius * (.65f + (1f - (1f - expansion) * (1f - expansion)) * 5f), paint)
            }
        }

        val iconReveal = if (revealedIcon != null) reveal else 0f
        drawAccretion(c, cx, cy, radius * coreScale * (.82f + arrive * .18f),
            pull, visible * (1f - iconReveal))
        if (iconReveal > 0f) {
            c.save(); c.translate(cx, cy)
            val scale = .7f + .3f * iconReveal
            c.scale(scale, scale)
            revealedIcon?.alpha = (255 * visible * iconReveal).toInt()
            revealedIcon?.draw(c)
            c.restore()
        }
        if (reveal > 0f) dialogue?.let { layout ->
            textPaint.alpha = (255 * reveal * depart).toInt()
            c.save()
            c.translate((width - layout.width) * .5f, height * .61f + density * 10f * (1f - reveal))
            layout.draw(c)
            c.restore()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel(); animator = null
        returnAnimator?.cancel(); returnAnimator = null
        super.onDetachedFromWindow()
    }

    private fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

    /** The shop's quiet sigil becomes a layered, accelerating disk during an offering. */
    private fun drawAccretion(c: Canvas, x: Float, y: Float, r: Float, pull: Float, opacity: Float) {
        if (opacity <= 0f) return
        c.save(); c.translate(x, y); c.rotate(-21f + sin(time * 4f) * 7f)
        val spin = time * 85f + pull * pull * 260f
        paint.style = Paint.Style.STROKE
        // Different radii and velocities give the outside disk depth without a flat glow layer.
        for (i in 0..7) {
            val ring = r * (1.25f + i * .078f)
            paint.strokeWidth = r * (if (i % 3 == 0) .023f else .009f)
            paint.color = Theme.alpha(if (i % 3 == 0) 0xffaaa0c3.toInt() else 0xff625675.toInt(),
                (opacity * (130 + i % 3 * 35)).toInt())
            c.drawArc(-ring, -ring * .34f, ring, ring * .34f,
                spin * (1f + i * .07f) + i * 47f, 75f + i * 9f, false, paint)
        }
        // A completely dark centre occludes the far half of the spinning disk.
        paint.style = Paint.Style.FILL; paint.color = Theme.alpha(0xff000000.toInt(), (255 * opacity).toInt())
        c.drawCircle(0f, 0f, r, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Theme.alpha(0xff524960.toInt(), (200 * opacity).toInt()); paint.strokeWidth = r * .025f
        c.drawCircle(0f, 0f, r * 1.015f, paint)
        paint.color = Theme.alpha(0xffc2b5dc.toInt(), (220 * opacity).toInt()); paint.strokeWidth = r * .012f
        c.drawCircle(0f, 0f, r * .987f, paint)
        paint.color = Theme.alpha(0xfff5edff.toInt(), (235 * opacity).toInt()); paint.strokeWidth = r * .024f
        c.drawArc(-r, -r, r, r, spin * .48f + 190f, 78f, false, paint)
        paint.color = Theme.alpha(0xffa096b8.toInt(), (160 * opacity).toInt()); paint.strokeWidth = r * .008f
        c.drawArc(-r * 1.07f, -r * 1.07f, r * 1.07f, r * 1.07f, spin * -.3f, 115f, false, paint)
        // The near ribbon crosses in front. Narrow bright lanes move independently within it.
        paint.color = Theme.alpha(0xff43394f.toInt(), (240 * opacity).toInt()); paint.strokeWidth = r * .09f
        c.drawArc(-r * 1.61f, -r * .49f, r * 1.61f, r * .49f, 4f, 172f, false, paint)
        paint.color = Theme.alpha(0xffaa9bbf.toInt(), (240 * opacity).toInt()); paint.strokeWidth = r * .035f
        c.drawArc(-r * 1.61f, -r * .46f, r * 1.61f, r * .46f, 9f, 162f, false, paint)
        paint.color = Theme.alpha(0xffeee4fa.toInt(), (245 * opacity).toInt()); paint.strokeWidth = r * .012f
        c.drawArc(-r * 1.6f, -r * .45f, r * 1.6f, r * .45f, 18f, 141f, false, paint)
        for (i in 0..4) {
            val offset = ((spin * (1f + i * .06f) + i * 33f) % 130f) + 14f
            val ring = r * (1.57f + i * .028f)
            paint.color = Theme.alpha(0xfff5edff.toInt(), (opacity * (130 + 90 * sin(offset / 180f * Math.PI).toFloat())).toInt())
            paint.strokeWidth = r * .009f
            c.drawArc(-ring, -r * (.41f + i * .037f), ring, r * (.41f + i * .037f), offset, 16f, false, paint)
        }
        paint.style = Paint.Style.FILL
        c.restore()
    }
}

private fun drawVoid(c: Canvas, p: Paint, x: Float, y: Float, r: Float, angle: Float, opacity: Float) {
    p.style = Paint.Style.FILL; p.color = Theme.alpha(0xff000000.toInt(), (255 * opacity).toInt())
    c.drawCircle(x, y, r, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.025f
    p.color = Theme.alpha(0xff81758f.toInt(), (210 * opacity).toInt())
    c.drawCircle(x, y, r, p)
    c.save(); c.rotate(angle - 22f, x, y)
    p.color = Theme.alpha(0xffe8dff7.toInt(), (230 * opacity).toInt())
    p.strokeWidth = r * 0.045f
    c.drawArc(x - r * 1.32f, y - r * 0.41f, x + r * 1.32f, y + r * 0.41f, 8f, 151f, false, p)
    p.color = Theme.alpha(0xff696074.toInt(), (210 * opacity).toInt())
    c.drawArc(x - r * 1.32f, y - r * 0.41f, x + r * 1.32f, y + r * 0.41f, 185f, 152f, false, p)
    c.restore(); p.style = Paint.Style.FILL
}

internal class UnlockedCheckIcon : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val b = bounds; val r = min(b.width(), b.height()) * .4f
        val x = b.exactCenterX(); val y = b.exactCenterY()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = r * .32f
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND; paint.color = Theme.INK
        path.reset(); path.moveTo(x - r, y); path.lineTo(x - r * .3f, y + r * .65f); path.lineTo(x + r, y - r * .7f)
        canvas.drawPath(path, paint)
    }
}

internal class AchievementShopIcon : Icon() {
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val b = bounds; val x = b.exactCenterX(); val y = b.exactCenterY(); val r = min(b.width(), b.height()) * 0.39f
        paint.color = Theme.INK; paint.style = Paint.Style.FILL
        path.reset(); path.moveTo(x - r * 0.7f, y - r); path.lineTo(x + r * 0.7f, y - r)
        path.lineTo(x + r * 0.55f, y - r * 0.03f); path.quadTo(x, y + r * 0.7f, x - r * 0.55f, y - r * 0.03f); path.close()
        canvas.drawPath(path, paint)
        canvas.drawRect(x - r * 0.12f, y + r * 0.25f, x + r * 0.12f, y + r * 0.78f, paint)
        canvas.drawRoundRect(x - r * 0.48f, y + r * 0.7f, x + r * 0.48f, y + r * 0.94f, r * 0.1f, r * 0.1f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = r * 0.17f
        canvas.drawArc(x - r, y - r * 0.78f, x, y + r * 0.02f, 65f, 205f, false, paint)
        canvas.drawArc(x, y - r * 0.78f, x + r, y + r * 0.02f, -90f, 205f, false, paint)
        paint.style = Paint.Style.FILL
    }
}

/** Quiet monochrome previews retain each secret's silhouette before purchase. */
internal class SecretCosmeticIcon(private val category: Int) : Icon() {
    private val path = Path()
    private var opacity = 255
    override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun getAlpha() = opacity
    override fun draw(c: Canvas) {
        val checkpoint = if (opacity == 255) c.save() else
            c.saveLayerAlpha(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), opacity)
        val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY()
        val r = min(bounds.width(), bounds.height()) * 0.34f
        val pale = 0xffc7bfd8.toInt()
        when (category) {
            Wardrobe.CUBE -> {
                path.reset(); path.moveTo(cx, cy - r); path.lineTo(cx + r, cy - r * 0.5f)
                path.lineTo(cx + r, cy + r * 0.5f); path.lineTo(cx, cy + r)
                path.lineTo(cx - r, cy + r * 0.5f); path.lineTo(cx - r, cy - r * 0.5f); path.close()
                paint.style = Paint.Style.FILL; paint.color = 0xff030306.toInt(); c.drawPath(path, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = r * 0.065f; paint.color = pale; c.drawPath(path, paint)
                paint.color = 0xff716780.toInt()
                c.drawLine(cx - r, cy - r * 0.5f, cx, cy, paint); c.drawLine(cx, cy, cx + r, cy - r * 0.5f, paint)
                c.drawLine(cx, cy, cx, cy + r, paint)
            }
            Wardrobe.TRAIL -> {
                for (i in 0..4) {
                    val x = cx - r + i * r * 0.42f; val y = cy + r * 0.55f - i * r * 0.26f
                    val size = r * (0.12f + i * 0.075f)
                    paint.style = Paint.Style.FILL; paint.color = Theme.alpha(pale, 45 + i * 45)
                    c.save(); c.rotate(-17f, x, y); c.drawRoundRect(x - size, y - size, x + size, y + size, size * 0.24f, size * 0.24f, paint); c.restore()
                }
            }
            else -> {
                drawVoid(c, paint, cx, cy, r * 0.82f, -5f, 1f)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = r * 0.03f; paint.color = 0xff62596f.toInt()
                c.drawCircle(cx, cy, r * 1.17f, paint)
            }
        }
        paint.style = Paint.Style.FILL
        c.restoreToCount(checkpoint)
    }
}
