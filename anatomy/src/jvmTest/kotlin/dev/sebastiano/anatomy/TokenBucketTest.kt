package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.figures.TokenBucket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenBucketTest {
    @Test
    fun `starts full and lets a burst of capacity through`() {
        val bucket = TokenBucket(capacity = 8, rate = 1.0)
        repeat(8) { assertTrue(bucket.request(), "request ${it + 1}") }
        assertFalse(bucket.request())
        assertEquals(8, bucket.passed)
        assertEquals(1, bucket.refused)
    }

    @Test
    fun `refills at the rate and never above capacity`() {
        val bucket = TokenBucket(capacity = 8, rate = 1.0)
        repeat(8) { bucket.request() }
        repeat(25) { bucket.advance(0.1) }
        assertEquals(2.5, bucket.tokens, 1e-9)
        repeat(200) { bucket.advance(0.1) }
        assertEquals(8.0, bucket.tokens, 1e-9)
    }

    @Test
    fun `a fraction of a token is not enough`() {
        val bucket = TokenBucket(capacity = 8, rate = 1.0, tokens = 0.0)
        repeat(9) { bucket.advance(0.1) }
        assertFalse(bucket.request())
        bucket.advance(0.1)
        assertTrue(bucket.request())
    }

    @Test
    fun `advance reports each whole token it delivers`() {
        val bucket = TokenBucket(capacity = 8, rate = 1.0, tokens = 0.0)
        var delivered = 0
        repeat(35) { delivered += bucket.advance(0.1) }
        assertEquals(3, delivered)
        assertEquals(3.5, bucket.added, 1e-9)
    }

    @Test
    fun `a full bucket delivers nothing and the feed stops`() {
        val bucket = TokenBucket(capacity = 8, rate = 1.0)
        assertEquals(0, bucket.advance(5.0))
        assertEquals(0.0, bucket.added, 1e-9)
    }

    @Test
    fun `under a steady flood the pass rate settles at the refill rate`() {
        val bucket = TokenBucket(capacity = 8, rate = 1.0)
        // 4 requests a second for 20 seconds.
        repeat(80) {
            bucket.request()
            bucket.advance(0.25)
        }
        // The burst of 8, then one a second.
        assertEquals(8 + 20, bucket.passed, absoluteTolerance = 1)
        assertEquals(80, bucket.passed + bucket.refused)
    }

    @Test
    fun `seconds until the next token`() {
        val bucket = TokenBucket(capacity = 8, rate = 2.0, tokens = 0.25)
        assertEquals(0.375, bucket.waitSeconds(), 1e-9)
        bucket.advance(1.0)
        assertEquals(0.0, bucket.waitSeconds(), 1e-9)
    }

    private fun assertEquals(expected: Int, actual: Int, absoluteTolerance: Int) {
        assertTrue(
            kotlin.math.abs(expected - actual) <= absoluteTolerance,
            "expected $expected ± $absoluteTolerance, was $actual",
        )
    }
}
