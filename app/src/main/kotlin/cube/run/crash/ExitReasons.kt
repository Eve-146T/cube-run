package cube.run.crash

import android.annotation.TargetApi
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

/**
 * Native crashes (a GPU driver, libGDX's natives) and freezes kill the game before any Java
 * code can react. Android 11+ keeps a record of why each process died; on the next launch
 * this turns new records of those kinds into reports. Java crashes are left to [CrashCatcher].
 */
object ExitReasons {
    fun collect(ctx: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        val since = CrashLog.exitsCheckedAt(ctx)
        val am = ctx.getSystemService(ActivityManager::class.java) ?: return
        val records = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 8)
        CrashLog.setExitsCheckedAt(ctx, maxOf(since ?: System.currentTimeMillis(), records.maxOfOrNull { it.timestamp } ?: 0L))
        // Records from before the first look predate the crash screen; leave them alone.
        if (since == null) return
        records.filter { it.timestamp > since && it.processName == ctx.packageName &&
            (it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE || it.reason == ApplicationExitInfo.REASON_ANR) }
            .sortedBy { it.timestamp }
            .forEach { CrashLog.save(ctx, report(ctx, it), it.timestamp) }
    }

    @TargetApi(30) // only reached after collect's version check
    private fun report(ctx: Context, info: ApplicationExitInfo): String {
        val native = info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE
        val trace = runCatching { info.traceInputStream?.use { it.readBytes() } }.getOrNull()
        val body = when {
            trace == null -> "(Android kept no trace for this one)"
            native -> Tombstone.describe(trace) ?: "(unreadable native crash record)"
            else -> mainThread(trace.toString(Charsets.UTF_8))
        }
        return CrashReport.build(ctx, if (native) "Native crash" else "Freeze (app not responding)", info.timestamp,
            listOfNotNull(info.description?.let { "Android says: $it" }, "Process: ${info.pid}"), body)
    }

    /** From an ANR trace dump, the header lines and the main thread's stack. */
    internal fun mainThread(dump: String): String {
        val lines = dump.lines()
        val head = lines.takeWhile { it.isNotBlank() }.filter { it.startsWith("Subject") || it.startsWith("Cmd line") || it.startsWith("----- pid") }
        val start = lines.indexOfFirst { it.startsWith("\"main\"") }
        if (start < 0) return (head + lines.take(80)).joinToString("\n")
        val end = (start until lines.size).firstOrNull { lines[it].isBlank() } ?: lines.size
        return (head + "" + lines.subList(start, end)).joinToString("\n")
    }
}
