package cube.run.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.Assert.*
import org.junit.Test

class SoundPlaybackQueueTest {
    @Test fun stalledBackendDoesNotBlockCallerAndBacklogIsBounded() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(2)
        val events = ConcurrentLinkedQueue<Int>()
        SoundPlaybackQueue<Int>(capacity = 1, maxAgeNs = Long.MAX_VALUE) { value ->
            if (value == 1) { entered.countDown(); release.await(5, TimeUnit.SECONDS) }
            events.add(value)
            finished.countDown()
        }.use { queue ->
            try {
                assertTrue(queue.offer(1))
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                // The backend stays blocked until these caller-side checks complete.
                assertTrue(queue.offer(2))
                assertFalse(queue.offer(3))
                assertTrue(events.isEmpty())
                release.countDown()
                assertTrue(finished.await(5, TimeUnit.SECONDS))
                assertEquals(listOf(1, 2), events.toList())
            } finally { release.countDown() }
        }
    }

    @Test fun staleEffectsAreDiscardedAfterBackendStall() {
        val now = AtomicLong(0)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val events = ConcurrentLinkedQueue<Int>()
        SoundPlaybackQueue<Int>(maxAgeNs = 100, clock = now::get) { value ->
            if (value == 1) { entered.countDown(); release.await(5, TimeUnit.SECONDS) }
            events.add(value)
            if (value == 3) finished.countDown()
        }.use { queue ->
            try {
                queue.offer(1)
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                queue.offer(2)
                now.set(101)
                queue.offer(3)
                release.countDown()
                assertTrue(finished.await(5, TimeUnit.SECONDS))
                assertEquals(listOf(1, 3), events.toList())
            } finally { release.countDown() }
        }
    }
}
