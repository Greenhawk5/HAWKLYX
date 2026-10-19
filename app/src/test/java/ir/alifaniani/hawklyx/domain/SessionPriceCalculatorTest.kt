package ir.alifaniani.hawklyx.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic unit tests for [SessionPriceCalculator].
 * Validates session change percentage, direction classification (POSITIVE, NEGATIVE, NEUTRAL),
 * baseline resolution from authentic history, closed/stale market semantics, and independence from 24h change.
 */
class SessionPriceCalculatorTest {

    private data class MockHistoricalPoint(
        override val timestampMs: Long,
        override val price: Double
    ) : HistoricalPoint

    @Test
    fun priceAboveBaseline_returnsPositiveDirection() {
        val currentPrice = 75.50
        val baselinePrice = 74.00
        val sessionId = "SESSION_2026-10-21"

        val state = SessionPriceCalculator.calculateSessionPrice(
            currentPrice = currentPrice,
            referencePrice = baselinePrice,
            referenceTimestampMs = 1771460000000L,
            sessionId = sessionId,
            isMarketOpen = true,
            isStale = false
        )

        assertTrue(state.isAvailable)
        assertEquals(PriceDirection.POSITIVE, state.direction)
        assertEquals(2.03, state.percentageChange, 0.001)
        assertEquals("+2.03%", state.formattedPercentage)
        assertEquals("SESSION_2026-10-21", state.sessionId)
    }

    @Test
    fun priceBelowBaseline_returnsNegativeDirection() {
        val currentPrice = 72.80
        val baselinePrice = 74.00
        val sessionId = "SESSION_2026-10-21"

        val state = SessionPriceCalculator.calculateSessionPrice(
            currentPrice = currentPrice,
            referencePrice = baselinePrice,
            referenceTimestampMs = 1771460000000L,
            sessionId = sessionId,
            isMarketOpen = true,
            isStale = false
        )

        assertTrue(state.isAvailable)
        assertEquals(PriceDirection.NEGATIVE, state.direction)
        assertEquals(-1.62, state.percentageChange, 0.001)
        assertEquals("-1.62%", state.formattedPercentage)
    }

    @Test
    fun priceEqualToBaseline_returnsNeutralDirection() {
        val currentPrice = 74.00
        val baselinePrice = 74.00
        val sessionId = "SESSION_2026-10-21"

        val state = SessionPriceCalculator.calculateSessionPrice(
            currentPrice = currentPrice,
            referencePrice = baselinePrice,
            referenceTimestampMs = 1771460000000L,
            sessionId = sessionId,
            isMarketOpen = true,
            isStale = false
        )

        assertTrue(state.isAvailable)
        assertEquals(PriceDirection.NEUTRAL, state.direction)
        assertEquals(0.0, state.percentageChange, 0.001)
        assertEquals("0.00%", state.formattedPercentage)
    }

    @Test
    fun marketClosed_returnsNeutralDirectionEvenIfPriceMoved() {
        val currentPrice = 76.00
        val baselinePrice = 74.00
        val sessionId = "SESSION_2026-10-21"

        val state = SessionPriceCalculator.calculateSessionPrice(
            currentPrice = currentPrice,
            referencePrice = baselinePrice,
            referenceTimestampMs = 1771460000000L,
            sessionId = sessionId,
            isMarketOpen = false,
            isStale = false
        )

        assertFalse(state.isAvailable)
        // When market is closed, session direction accent MUST be neutralized
        assertEquals(PriceDirection.NEUTRAL, state.direction)
        assertEquals(0.0, state.percentageChange, 0.001)
    }

    @Test
    fun staleQuote_returnsNeutralDirection() {
        val currentPrice = 76.00
        val baselinePrice = 74.00
        val sessionId = "SESSION_2026-10-21"

        val state = SessionPriceCalculator.calculateSessionPrice(
            currentPrice = currentPrice,
            referencePrice = baselinePrice,
            referenceTimestampMs = 1771460000000L,
            sessionId = sessionId,
            isMarketOpen = true,
            isStale = true
        )

        assertFalse(state.isAvailable)
        // Stale quote must never signal an active green/red live direction
        assertEquals(PriceDirection.NEUTRAL, state.direction)
    }

    @Test
    fun missingOrInvalidBaseline_returnsUnavailableState() {
        val stateZero = SessionPriceCalculator.calculateSessionPrice(
            currentPrice = 75.00,
            referencePrice = 0.0,
            referenceTimestampMs = 0L,
            sessionId = "SESSION_2026-10-21",
            isMarketOpen = true,
            isStale = false
        )
        assertFalse(stateZero.isAvailable)
        assertEquals(PriceDirection.NEUTRAL, stateZero.direction)
        assertEquals(0.0, stateZero.percentageChange, 0.001)

        val stateNegative = SessionPriceCalculator.calculateSessionPrice(
            currentPrice = 75.00,
            referencePrice = -10.0,
            referenceTimestampMs = 0L,
            sessionId = "SESSION_2026-10-21",
            isMarketOpen = true,
            isStale = false
        )
        assertFalse(stateNegative.isAvailable)
        assertEquals(PriceDirection.NEUTRAL, stateNegative.direction)
    }

    @Test
    fun findSessionReferencePoint_picksMostRecentPointAtOrBeforeSessionStart() {
        val sessionStartMs = 1771455600000L // e.g., 23:00 London

        val points = listOf(
            MockHistoricalPoint(sessionStartMs - 7200000L, 73.50), // 2 hours before
            MockHistoricalPoint(sessionStartMs - 3600000L, 74.10), // 1 hour before (previous close)
            MockHistoricalPoint(sessionStartMs + 1800000L, 74.80)  // 30 min into current session
        )

        val baseline = SessionPriceCalculator.findSessionReferencePoint(sessionStartMs, points)

        assertNotNull(baseline)
        assertEquals(74.10, baseline!!.price, 0.001)
        assertEquals(sessionStartMs - 3600000L, baseline.timestampMs)
    }

    @Test
    fun findSessionReferencePoint_ignoresInvalidPoints() {
        val sessionStartMs = 1771455600000L

        val points = listOf(
            MockHistoricalPoint(sessionStartMs - 7200000L, 73.50),
            MockHistoricalPoint(sessionStartMs - 3600000L, Double.NaN),
            MockHistoricalPoint(sessionStartMs - 1800000L, -5.0)
        )

        val baseline = SessionPriceCalculator.findSessionReferencePoint(sessionStartMs, points)

        assertNotNull(baseline)
        assertEquals("Must skip NaN and negative points", 73.50, baseline!!.price, 0.001)
    }

    @Test
    fun findSessionReferencePoint_returnsNullWhenNoPointsBeforeSessionStart() {
        val sessionStartMs = 1771455600000L
        val points = listOf(
            MockHistoricalPoint(sessionStartMs + 1000L, 74.00)
        )
        val baseline = SessionPriceCalculator.findSessionReferencePoint(sessionStartMs, points)
        assertNull(baseline)
    }

    @Test
    fun sessionChangeAnd24hChange_remainSemanticallyIndependent() {
        val nowMs = 1771460000000L
        val sessionStartMs = nowMs - (4 * 3600000L) // Session opened 4h ago
        val dayAgoMs = nowMs - (24 * 3600000L) // 24 hours ago

        val currentPrice = 75.00
        val historyPoints = listOf(
            MockHistoricalPoint(dayAgoMs, 76.50), // 24h ago price was higher ($76.50)
            MockHistoricalPoint(sessionStartMs, 74.00) // Session baseline was lower ($74.00)
        )

        // Session change: from $74.00 -> $75.00 = +1.35% (POSITIVE)
        val baseline = SessionPriceCalculator.findSessionReferencePoint(sessionStartMs, historyPoints)
        assertNotNull(baseline)
        val sessionResult = SessionPriceCalculator.calculateSessionPrice(
            currentPrice = currentPrice,
            referencePrice = baseline!!.price,
            referenceTimestampMs = baseline.timestampMs,
            sessionId = "SESSION_2026-10-21",
            isMarketOpen = true,
            isStale = false
        )
        assertEquals(PriceDirection.POSITIVE, sessionResult.direction)
        assertTrue(sessionResult.percentageChange > 0)

        // 24h change: from $76.50 -> $75.00 = -1.96% (NEGATIVE)
        val change24h = PriceChangeCalculator.calculate24hChange(currentPrice, nowMs, historyPoints)
        assertEquals(PriceChangeDirection.NEGATIVE, change24h.direction)
        assertTrue(change24h.percentageChange < 0)

        // Verifies separation: session direction is POSITIVE while 24h change is NEGATIVE
        assertEquals(PriceDirection.POSITIVE, sessionResult.direction)
        assertEquals(PriceChangeDirection.NEGATIVE, change24h.direction)
    }
}
