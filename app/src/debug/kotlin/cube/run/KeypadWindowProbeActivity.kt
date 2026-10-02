package cube.run

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Models the TCL Flip 2 framework's Activity.setContentView override: it enables
 * a 30px MenuBar in DecorView, outside WindowInsets.Type.systemBars(). Keeping
 * this at the Activity boundary tests how the real game installs its content.
 */
class KeypadWindowProbeActivity : GameActivity() {
    override fun setContentView(view: View) {
        super.setContentView(view)
        val decor = window.decorView as ViewGroup
        decor.addView(View(this).apply {
            tag = MENU_BAR_TAG
            setBackgroundColor(Color.rgb(68, 68, 68))
        }, FrameLayout.LayoutParams(-1, 30, Gravity.BOTTOM))
    }

    companion object {
        const val MENU_BAR_TAG = "keypad-menu-bar"
    }
}
