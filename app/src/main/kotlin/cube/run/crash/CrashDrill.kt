package cube.run.crash

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.Process

/** Debug builds only: `adb shell am start -n cube.run/.GameActivity --es crash java|gl|native|anr|boot`. */
object CrashDrill {
    fun start(activity: Activity, kind: String) {
        if (kind == "boot") throw IllegalStateException("Crash drill: during launch")
        Handler(Looper.getMainLooper()).postDelayed({
            if (activity.isFinishing) return@postDelayed
            when (kind) {
                "java" -> throw IllegalStateException("Crash drill: on the main thread")
                "gl" -> com.badlogic.gdx.Gdx.app.postRunnable { throw IllegalStateException("Crash drill: on the GL thread") }
                "native" -> Process.sendSignal(Process.myPid(), 11) // SIGSEGV, as a broken driver would
                "anr" -> Thread.sleep(30_000)
            }
        }, 2500)
    }
}
