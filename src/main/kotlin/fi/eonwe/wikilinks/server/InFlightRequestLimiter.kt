package fi.eonwe.wikilinks.server

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

class InFlightRequestLimiter(maxConcurrent: Int) {
    private val semaphore = Semaphore(maxConcurrent.coerceAtLeast(1), true)

    fun tryAcquire(): Permit? {
        if (!semaphore.tryAcquire()) {
            return null
        }
        return Permit(semaphore)
    }

    class Permit internal constructor(
        private val semaphore: Semaphore
    ) : AutoCloseable {
        private val released = AtomicBoolean(false)

        override fun close() {
            if (released.compareAndSet(false, true)) {
                semaphore.release()
            }
        }
    }
}
