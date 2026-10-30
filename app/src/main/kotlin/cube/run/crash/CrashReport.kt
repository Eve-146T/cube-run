package cube.run.crash

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import cube.run.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** The text players copy: a short header anyone can read, then what went wrong. */
object CrashReport {
    const val TITLE = "Cube Run crash log"
    private const val MAX_CHARS = 24_000

    /** Facts the game learns while running (the GPU, …), included in every report. */
    val notes = ConcurrentHashMap<String, String>()

    fun forThrowable(ctx: Context, thread: Thread, error: Throwable, at: Long = System.currentTimeMillis()): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val uptime = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        return build(ctx, "Crash", at, listOf(
            "Thread: ${thread.name}",
            "Running for: ${seconds(uptime)}",
        ), trace, recentLog(Process.myPid()))
    }

    /** [body] and [log] already describe the failure; this adds the header and trims to a pasteable size. */
    fun build(ctx: Context, kind: String, at: Long, details: List<String>, body: String, log: String? = null): String {
        val head = buildString {
            appendLine("=== $TITLE ===")
            appendLine("What: $kind")
            appendLine("When: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(at))}")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Language: ${ctx.resources.configuration.locales[0].toLanguageTag()}")
            for ((k, v) in notes.toSortedMap()) appendLine("$k: $v")
            for (line in details) appendLine(line)
            appendLine()
        }
        val tail = log?.takeIf { it.isNotBlank() }?.let { "\n--- recent log ---\n$it" }.orEmpty()
        val room = (MAX_CHARS - head.length - tail.length.coerceAtMost(MAX_CHARS / 4)).coerceAtLeast(2000)
        val main = if (body.length > room) body.take(room) + "\n… (cut)\n" else body
        return head + main.trimEnd() + "\n" + tail.takeLast(MAX_CHARS / 4) + "\n=== end ===\n"
    }

    /**
     * The last info-or-louder lines process [pid] logged, or null where logcat is unavailable.
     * AndroidRuntime is left out: it only repeats the stack trace.
     */
    fun recentLog(pid: Int, lines: Int = 60): String? = runCatching {
        val proc = ProcessBuilder("logcat", "-d", "-v", "time", "-t", lines.toString(), "--pid=$pid", "AndroidRuntime:S", "*:I")
            .redirectErrorStream(true).start()
        val text = proc.inputStream.bufferedReader().readText()
        if (!proc.waitFor(1500, TimeUnit.MILLISECONDS)) proc.destroy()
        text.lines().filterNot { it.startsWith("---------") }.joinToString("\n").trim().ifEmpty { null }
    }.getOrNull()

    private fun seconds(ms: Long) = String.format(Locale.US, "%.1f s", ms / 1000f)
}
