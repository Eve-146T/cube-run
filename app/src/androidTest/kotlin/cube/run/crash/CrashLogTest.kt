package cube.run.crash

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Reports are stored, shown once and trimmed; native crash records decode into a readable backtrace. */
class CrashLogTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun aSavedReportIsPendingUntilShownAndOldOnesAreDropped() {
        val before = CrashLog.reports(context).map { it.name }.toSet()
        val future = System.currentTimeMillis() + 86_400_000L // newer than any real report on the device
        try {
            val first = CrashLog.save(context, "one", at = future)
            assertEquals(first, CrashLog.pending(context))
            val last = (2..12).map { CrashLog.save(context, "report $it", at = future + it) }.last()
            assertEquals(last, CrashLog.pending(context))
            assertEquals("report 12", CrashLog.reports(context).first().readText())
            assertTrue(CrashLog.reports(context).size <= 8)
            assertNull("the oldest report is gone", CrashLog.named(context, first.name))
            assertNull("names cannot leave the crash folder", CrashLog.named(context, "crash-../../shared_prefs/settings.xml"))
            CrashLog.clearPending(context)
            assertNull(CrashLog.pending(context))
        } finally {
            CrashLog.reports(context).filter { it.name !in before }.forEach { it.delete() }
            CrashLog.clearPending(context)
        }
    }

    @Test fun aReportStartsWithTheBasicsAndStaysPasteable() {
        val text = CrashReport.build(context, "Crash", 0L, listOf("Thread: main"), "x".repeat(100_000), "log line")
        assertTrue(text.startsWith("=== ${CrashReport.TITLE} ===\nWhat: Crash\n"))
        assertTrue(text.contains("Thread: main") && text.contains("Device: ") && text.contains("log line"))
        assertTrue(text.length < 30_000)
        assertTrue(text.endsWith("=== end ===\n"))
    }

    @Test fun aTombstoneDecodesToSignalAndBacktrace() {
        val frame = message(varintField(1, 0x1234), stringField(4, "glDrawElements"), varintField(5, 48), stringField(6, "/vendor/lib64/libGLESv2.so"))
        val thread = message(varintField(1, 77), stringField(2, "GLThread 12"), bytesField(4, frame))
        val other = message(varintField(1, 5), stringField(2, "main"))
        val signal = message(varintField(1, 11), stringField(2, "SIGSEGV"), stringField(4, "SEGV_MAPERR"), varintField(8, 1), varintField(9, 0))
        val log = message(stringField(1, "10-30 16:00:00.000"), varintField(4, 6), stringField(5, "Adreno"), stringField(6, "oops"))
        val tombstone = message(
            varintField(6, 77), bytesField(10, signal), stringField(14, "abort!"),
            bytesField(16, message(varintField(1, 5), bytesField(2, other))),
            bytesField(16, message(varintField(1, 77), bytesField(2, thread))),
            bytesField(18, message(stringField(1, "main"), bytesField(2, log))),
        )
        val text = Tombstone.describe(tombstone)!!
        assertTrue(text, text.contains("Signal: SIGSEGV (SEGV_MAPERR), fault address 0x0"))
        assertTrue(text, text.contains("Abort message: abort!"))
        assertTrue(text, text.contains("Thread: GLThread 12 (tid 77)"))
        assertTrue(text, text.contains("#00 pc 00001234  /vendor/lib64/libGLESv2.so (glDrawElements+48)"))
        assertTrue(text, text.contains("E/Adreno: oops"))
        assertNull(Tombstone.describe(ByteArray(0)))
        assertNull("garbage is not a crash", Tombstone.describe(byteArrayOf(0x0f, 0x7f)))
    }

    @Test fun aFreezeKeepsOnlyTheMainThread() {
        val dump = listOf(
            "Subject: Input dispatching timed out", "", "----- pid 42 at 2026-10-30 -----", "Cmd line: cube.run", "",
            "\"Signal Catcher\" daemon prio=10 tid=7 Runnable", "  native: #00 pc 1234 libart.so", "",
            "\"main\" prio=5 tid=1 Sleeping", "  at java.lang.Thread.sleep(Native method)", "  at cube.run.crash.CrashDrill.start(CrashDrill.kt:18)", "",
            "\"GLThread 12\" prio=5 tid=20 Native", "  at android.opengl.GLES20.glDrawElements(Native method)",
        ).joinToString("\n")
        val text = ExitReasons.mainThread(dump)
        assertTrue(text, text.startsWith("Subject: Input dispatching timed out"))
        assertTrue(text, text.contains("\"main\" prio=5 tid=1 Sleeping\n  at java.lang.Thread.sleep(Native method)\n  at cube.run.crash.CrashDrill.start"))
        assertFalse(text, text.contains("GLThread") || text.contains("Signal Catcher"))
    }

    private fun varint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) { out.write(((v and 0x7F) or 0x80).toInt()); v = v ushr 7 }
        out.write(v.toInt())
    }
    private fun varintField(n: Int, v: Long) = ByteArrayOutputStream().also { varint(it, (n shl 3).toLong()); varint(it, v) }.toByteArray()
    private fun bytesField(n: Int, b: ByteArray) = ByteArrayOutputStream().also { varint(it, ((n shl 3) or 2).toLong()); varint(it, b.size.toLong()); it.write(b) }.toByteArray()
    private fun stringField(n: Int, s: String) = bytesField(n, s.toByteArray())
    private fun message(vararg parts: ByteArray) = ByteArrayOutputStream().also { o -> parts.forEach { o.write(it) } }.toByteArray()
}
