package cube.run.core

import java.util.concurrent.ArrayBlockingQueue
import kotlin.concurrent.thread

/** Keeps platform audio startup off UI/GL threads, without accumulating late effects. */
internal class SoundPlaybackQueue<T>(
    capacity: Int = 32,
    private val maxAgeNs: Long = 100_000_000L,
    private val clock: () -> Long = System::nanoTime,
    private val play: (T) -> Unit,
) : AutoCloseable {
    private data class Pending<T>(val value: T, val queuedAt: Long)
    private val pending = ArrayBlockingQueue<Pending<T>>(capacity)
    private val worker = thread(name = "sfx-play", isDaemon = true) {
        try {
            while (!Thread.currentThread().isInterrupted) {
                val next = pending.take()
                if (clock() - next.queuedAt <= maxAgeNs) play(next.value)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** A full queue drops the new effect; callers never wait for platform audio. */
    fun offer(value: T): Boolean = pending.offer(Pending(value, clock()))

    override fun close() { worker.interrupt() }
}
