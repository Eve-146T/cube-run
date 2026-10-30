package cube.run.crash

import android.content.Context
import java.io.File

/**
 * Crash reports as plain text files in the app's private storage. Plain files on purpose:
 * they are written from a dying process, so nothing here may depend on game state.
 * A report that has not been shown yet is "pending": the next launch shows it.
 */
object CrashLog {
    private const val KEEP = 8

    private fun dir(ctx: Context) = File(ctx.filesDir, "crashes").apply { mkdirs() }
    private fun pendingFile(ctx: Context) = File(dir(ctx), "pending")
    private fun checkedFile(ctx: Context) = File(dir(ctx), "exits-checked")

    /** Store [text] as a new report, mark it pending and drop the oldest beyond [KEEP]. */
    fun save(ctx: Context, text: String, at: Long = System.currentTimeMillis()): File {
        val file = File(dir(ctx), "crash-$at.txt")
        file.writeText(text)
        pendingFile(ctx).writeText(file.name)
        reports(ctx).drop(KEEP).forEach { it.delete() }
        return file
    }

    /** Every stored report, newest first. */
    fun reports(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.name.startsWith("crash-") && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.name.removePrefix("crash-").removeSuffix(".txt").toLongOrNull() ?: 0L }
            .orEmpty()

    /** The report waiting to be shown, if any. */
    fun pending(ctx: Context): File? {
        val name = runCatching { pendingFile(ctx).readText().trim() }.getOrNull() ?: return null
        return File(dir(ctx), name).takeIf { name.isNotEmpty() && it.isFile }
    }

    fun clearPending(ctx: Context) { pendingFile(ctx).delete() }

    /** The report called [name], confined to the crash folder. */
    fun named(ctx: Context, name: String?): File? =
        name?.takeIf { it.startsWith("crash-") && '/' !in it }?.let { File(dir(ctx), it) }?.takeIf { it.isFile }

    /** When system exit records were last read; null before the first look. */
    fun exitsCheckedAt(ctx: Context): Long? = runCatching { checkedFile(ctx).readText().trim().toLong() }.getOrNull()

    fun setExitsCheckedAt(ctx: Context, at: Long) { checkedFile(ctx).writeText(at.toString()) }
}
