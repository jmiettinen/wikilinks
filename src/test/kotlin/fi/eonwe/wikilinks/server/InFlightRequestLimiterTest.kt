package fi.eonwe.wikilinks.server

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.jupiter.api.Test

class InFlightRequestLimiterTest {
    @Test
    fun `acquire fails when at capacity and succeeds after release`() {
        val limiter = InFlightRequestLimiter(1)
        val first = limiter.tryAcquire()
        first.shouldNotBeNull()

        limiter.tryAcquire().shouldBeNull()

        first.close()
        limiter.tryAcquire().shouldNotBeNull()
    }

    @Test
    fun `permit release is idempotent`() {
        val limiter = InFlightRequestLimiter(1)
        val permit = limiter.tryAcquire()
        permit.shouldNotBeNull()

        permit.close()
        permit.close()

        limiter.tryAcquire().shouldNotBeNull()
    }
}
