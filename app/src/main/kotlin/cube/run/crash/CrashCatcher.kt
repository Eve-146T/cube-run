package cube.run.crash

import android.app.Activity
import android.app.Application
import android.os.Process
import kotlin.system.exitProcess

/**
 * Instead of Android's "Cube Run keeps stopping", a crash writes a report and reopens the
 * app on the uh-oh screen, which runs in its own process so the broken game is fully gone.
 * Native crashes and freezes cannot be caught in flight; [ExitReasons] finds those on the
 * next launch and [showPending] puts them on the same screen.
 */
object CrashCatcher {
    private const val PROCESS_SUFFIX = ":uhoh"

    /** True in the uh-oh screen's own process, which must not start the game's systems. */
    val inCrashProcess: Boolean get() = Application.getProcessName().endsWith(PROCESS_SUFFIX)

    fun install(app: Application) {
        val fallback = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val shown = runCatching {
                val report = CrashLog.save(app, CrashReport.forThrowable(app, thread, error))
                app.startActivity(UhOhActivity.intent(app, report.name))
            }.isSuccess
            if (!shown) fallback?.uncaughtException(thread, error)
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    /**
     * At launch: look for crashes the handler could not catch, then show any report not yet
     * seen. Returns true when the uh-oh screen takes over and [activity] should stop.
     */
    fun showPending(activity: Activity): Boolean {
        runCatching { ExitReasons.collect(activity) }
        val report = CrashLog.pending(activity) ?: return false
        activity.startActivity(UhOhActivity.intent(activity, report.name))
        activity.finish()
        return true
    }

    fun note(key: String, value: String) { CrashReport.notes[key] = value }
}
