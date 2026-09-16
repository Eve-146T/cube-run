package cube.run.intro

import android.annotation.TargetApi
import android.app.Activity
import android.os.Build
import android.view.SurfaceControl
import android.view.ViewGroup
import android.view.SurfaceView
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.window.SplashScreenView
import cube.run.core.LaunchTrace

/** Replace the copied splash before its first app-window frame can show an empty icon surface. */
@TargetApi(31)
class OpeningSplashHandoff(
    private val activity: Activity,
    private val native: NativeCubeView,
    private val clock: OpeningClock,
    private val onBegin: () -> Unit,
    private val onRemoved: () -> Unit,
) {
    private val decor = activity.window.decorView as ViewGroup
    private var prepared: SplashScreenView? = null
    private var submitted = false
    private var exitReceived = false
    private var removed = false
    private val beforeDraw = ViewTreeObserver.OnPreDrawListener {
        // Android adds this public view directly to the decor before notifying
        // the exit listener. Waiting for that listener misses the transfer frame.
        for (i in 0 until decor.childCount) {
            val child = decor.getChildAt(i)
            if (child is SplashScreenView) { prepare(child); break }
        }
        true
    }

    fun install() {
        decor.viewTreeObserver.addOnPreDrawListener(beforeDraw)
        activity.splashScreen.setOnExitAnimationListener { splash ->
            prepare(splash) // Also handles platform variants that notify before drawing.
            exitReceived = true
            removeWhenReady()
        }
    }

    private fun prepare(splash: SplashScreenView) {
        if (prepared != null) return
        prepared = splash
        dispose()
        onBegin()
        native.drawingCube = true
        splash.iconAnimationStart?.let { start ->
            clock.adoptSystemStart(start.toEpochMilli(), splash.iconAnimationDuration?.toMillis() ?: 0L)
        }
        LaunchTrace.mark("system handoff prepared before draw")
        // Destroy the empty copied surface before Android transfers its package.
        // It stays in the starting window until that window is hidden with this
        // frame. The replacement is drawn in the splash background's own buffer.
        (splash.iconView as? SurfaceView)?.visibility = View.GONE
        val cover = native.handoffCopy().apply {
            setBackgroundColor(OpeningPose.INK)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        splash.addView(cover, FrameLayout.LayoutParams(-1, -1))
        // Preparation runs after layout but before draw; size the new child for
        // this very frame instead of waiting for the requested second layout.
        cover.measure(View.MeasureSpec.makeMeasureSpec(splash.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(splash.height, View.MeasureSpec.EXACTLY))
        cover.layout(0, 0, splash.width, splash.height)
        native.invalidate()
        if (Build.VERSION.SDK_INT >= 33) {
            SurfaceControl.Transaction().use { transaction ->
                val ready = {
                    submitted = true
                    removeWhenReady()
                }
                if (Build.VERSION.SDK_INT >= 35) {
                    transaction.addTransactionCompletedListener(activity.mainExecutor) { ready() }
                } else {
                    transaction.addTransactionCommittedListener(activity.mainExecutor) { ready() }
                }
                // A View frame-commit callback only reports render submission.
                // Wait until the compositor has applied the window buffer and
                // Android's starting-window hide before releasing the old icon.
                if (decor.rootSurfaceControl?.applyTransactionOnDraw(transaction) == true) return
            }
        }
        // Android 12 has no public compositor-commit callback. Keep the live
        // replacement through a second submitted frame before releasing the host.
        decor.viewTreeObserver.registerFrameCommitCallback {
            decor.postOnAnimation {
                if (!decor.isAttachedToWindow) return@postOnAnimation
                decor.viewTreeObserver.registerFrameCommitCallback {
                    decor.post {
                        submitted = true
                        removeWhenReady()
                    }
                }
                decor.invalidate()
            }
        }
    }

    private fun removeWhenReady() {
        if (!submitted || !exitReceived || removed) return
        removed = true
        // Do not detach before Android has completed its own surface transfer.
        prepared?.remove()
        prepared = null
        if (activity.isFinishing || activity.isDestroyed) return
        clock.releaseSystem()
        LaunchTrace.mark("system splash removed")
        onRemoved()
    }

    fun dispose() {
        if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnPreDrawListener(beforeDraw)
    }
}
