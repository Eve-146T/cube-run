package cube.run.crash

/**
 * Reads the parts of Android's native crash record (debuggerd's tombstone.proto, handed out
 * by ApplicationExitInfo from Android 12) that say what crashed: the signal, the abort
 * message, the crashing thread's backtrace and the process's last log lines. A minimal
 * protobuf wire reader instead of a protobuf library.
 */
object Tombstone {
    private class Field(val number: Int, val varint: Long, val bytes: ByteArray?) {
        val text get() = bytes?.toString(Charsets.UTF_8).orEmpty()
    }

    /** The top-level fields of one protobuf message; stops quietly at malformed data. */
    private fun fields(data: ByteArray): List<Field> {
        val out = ArrayList<Field>()
        var i = 0
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (i < data.size) {
                val b = data[i++].toInt()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                if (shift > 63) break
            }
            throw IllegalArgumentException("bad varint")
        }
        try {
            while (i < data.size) {
                val key = varint()
                val number = (key ushr 3).toInt()
                when ((key and 7).toInt()) {
                    0 -> out += Field(number, varint(), null)
                    1 -> { require(i + 8 <= data.size); out += Field(number, le(data, i, 8), null); i += 8 }
                    2 -> {
                        val len = varint().toInt()
                        require(len >= 0 && i + len <= data.size)
                        out += Field(number, 0, data.copyOfRange(i, i + len)); i += len
                    }
                    5 -> { require(i + 4 <= data.size); out += Field(number, le(data, i, 4), null); i += 4 }
                    else -> break
                }
            }
        } catch (_: IllegalArgumentException) {}
        return out
    }

    private fun le(data: ByteArray, at: Int, n: Int): Long {
        var v = 0L
        for (k in n - 1 downTo 0) v = (v shl 8) or (data[at + k].toLong() and 0xFF)
        return v
    }

    private fun List<Field>.text(n: Int) = firstOrNull { it.number == n }?.text.orEmpty()
    private fun List<Field>.num(n: Int) = firstOrNull { it.number == n }?.varint ?: 0L
    private fun List<Field>.messages(n: Int) = filter { it.number == n && it.bytes != null }.map { fields(it.bytes!!) }

    /** A readable summary of [data], or null when it holds no tombstone. */
    fun describe(data: ByteArray, maxFrames: Int = 40, maxLogLines: Int = 40): String? {
        val top = fields(data)
        if (top.isEmpty()) return null
        val tid = top.num(6)
        val out = StringBuilder()
        top.messages(10).firstOrNull()?.let { signal ->
            out.append("Signal: ").append(signal.text(2).ifEmpty { "#" + signal.num(1) })
            signal.text(4).takeIf { it.isNotEmpty() }?.let { out.append(" (").append(it).append(')') }
            if (signal.num(8) != 0L) out.append(", fault address 0x").append(java.lang.Long.toHexString(signal.num(9)))
            out.append('\n')
        }
        top.text(14).takeIf { it.isNotBlank() }?.let { out.append("Abort message: ").append(it.trim()).append('\n') }
        for (cause in top.messages(15)) cause.text(1).takeIf { it.isNotBlank() }?.let { out.append("Cause: ").append(it).append('\n') }
        // threads is map<uint32, Thread>: entries of key = 1, value = 2.
        val threads = top.messages(16).mapNotNull { entry -> entry.messages(2).firstOrNull()?.let { entry.num(1) to it } }
        val crashed = threads.firstOrNull { it.first == tid }?.second ?: threads.firstOrNull()?.second
        if (crashed != null) {
            out.append("Thread: ").append(crashed.text(2)).append(" (tid ").append(crashed.num(1)).append(")\n")
            for ((index, frame) in crashed.messages(4).take(maxFrames).withIndex()) {
                out.append(String.format(java.util.Locale.US, "  #%02d pc %08x  %s", index, frame.num(1), frame.text(6)))
                frame.text(4).takeIf { it.isNotEmpty() }?.let { out.append(" (").append(it).append('+').append(frame.num(5)).append(')') }
                frame.text(8).takeIf { it.isNotEmpty() }?.let { out.append(" BuildId: ").append(it) }
                out.append('\n')
            }
        }
        // Info and louder only (priority 4+); debug chatter buries the useful lines.
        val logs = top.messages(18).flatMap { it.messages(2) }.filter { it.num(4) >= 4 }.takeLast(maxLogLines)
        if (logs.isNotEmpty()) {
            out.append("\n--- recent log ---\n")
            val levels = "??VDIWEF"
            for (line in logs) {
                val level = levels.getOrElse(line.num(4).toInt()) { '?' }
                out.append(line.text(1)).append(' ').append(level).append('/').append(line.text(5)).append(": ")
                    .append(line.text(6).trimEnd()).append('\n')
            }
        }
        return out.toString().ifBlank { null }
    }
}
