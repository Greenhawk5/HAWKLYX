package ir.alifaniani.hawklyx.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PriceChangeCalculator].
 * Validates authentic 24-hour Brent crude calculation, reference observation selection,
 * weekend/closure fallbacks, rounding, and robust rejection of invalid inputs.
 */
class PriceChangeCalculatorTest {

    private data class MockHistoricalPoint(
        override val timestampMs: Long,
        override val price: Double
    ) : HistoricalPoint

    private val nowMs = 1771423200000L // e.g. 2026-10-18T14:00:00Z
    private val ms24h = PriceChangeCalculator.MILLIS_24H // 24 hours in ms
    private val target24hMs = nowMs - ms24h // 2026-10-17T14:00:00Z

    @Test
    fun positivePriceChange_calculatesAccurately() {
        val points = listOf(
            MockHistoricalPoint(target24hMs, 100.0)
        )
        val currentPrice = 101.50 // +1.50%

        val result = PriceChangeCalculator.calculate24hChange(currentPrice, nowMs, points)

        assertTrue(result.isAvailable)
        assertEquals(PriceChangeDirection.POSITIVE, result.direction)
        assertEquals(1.50, result.percentageChange, 0.001)
        assertEquals(1.50, result.absoluteChange, 0.001)
        assertEquals("+1.50%", result.formattedPercentage)
        assertEquals("↑ +1.50%", result.formattedWithArrow)
    }

    @Test
    fun negativePriceChange_calculatesAccurately() {
        val points = listOf(
            MockHistoricalPoint(target24hMs, 100.0)
        )
        val currentPrice = 98.75 // -1.25%

        val result = PriceChangeCalculator.calculate24hChange(currentPrice, nowMs, points)

        assertTrue(result.isAvailable)
        assertEquals(PriceChangeDirection.NEGATIVE, result.direction)
        assertEquals(-1.25, result.percentageChange, 0.001)
        assertEquals(-1.25, result.absoluteChange, 0.001)
        assertEquals("-1.25%", result.formattedPercentage)
        assertEquals("↓ -1.25%", result.formattedWithArrow)
    }

    @Test
    fun exactZeroChange_yieldsNeutralState() {
        val points = listOf(
            MockHistoricalPoint(target24hMs, 95.00)
        )
        val currentPrice = 95.00

        val result = PriceChangeCalculator.calculate24hChange(currentPrice, nowMs, points)

        assertTrue(result.isAvailable)
        assertEquals(PriceChangeDirection.NEUTRAL, result.direction)
        assertEquals(0.00, result.percentageChange, 0.001)
        assertEquals("0.00%", result.formattedPercentage)
        assertEquals("→ 0.00%", result.formattedWithArrow)
    }

    @Test
    fun valuesThatRoundToZero_preventNegativeZeroAndMapToNeutral() {
        // Small change: 100.00 to 99.998 -> -0.002% which rounds to 0.00%
        val points = listOf(
            MockHistoricalPoint(target24hMs, 100.0)
        )
        val currentPrice = 99.998

        val result = PriceChangeCalculator.calculate24hChange(currentPrice, nowMs, points)

        assertTrue(result.isAvailable)
        assertEquals("Must map to NEUTRAL when rounded to 0.00%", PriceChangeDirection.NEUTRAL, result.direction)
        assertEquals(0.00, result.percentageChange, 0.001)
        assertEquals("0.00%", result.formattedPercentage)
        assertFalse("Must never display -0.00%", result.formattedPercentage.contains("-"))
        assertEquals("→ 0.00%", result.formattedWithArrow)
    }

    @Test
    fun primaryToleranceSelection_picksClosestObservationWithinPlusMinusThreeHours() {
        // Target is 14:00 yesterday
        // Provide points at 12:00 (-2h), 14:15 (+15m), and 15:30 (+1.5h)
        val p1 = MockHistoricalPoint(target24hMs - 2 * 3600 * 1000L, 90.0)
        val p2 = MockHistoricalPoint(target24hMs + 15 * 60 * 1000L, 92.0) // closest (+15 min)
        val p3 = MockHistoricalPoint(target24hMs + 90 * 60 * 1000L, 94.0)

        val result = PriceChangeCalculator.calculate24hChange(95.0, nowMs, listOf(p1, p2, p3))

        assertTrue(result.isAvailable)
        assertEquals("Must select the observation closest to 24h reference time", p2.timestampMs, result.referenceTimestampMs)
        assertEquals(92.0, result.referencePrice, 0.001)
    }

    @Test
    fun weekendAndMarketClosureFallback_selectsMostRecentObservationBeforeTargetWithin80h() {
        // Simulate Monday 10:00 (nowMs). 24h ago is Sunday 10:00 (market closed, no quotes).
        // Friday close was 60 hours ago (within 80h limit).
        val fridayCloseMs = nowMs - (60 * 3600 * 1000L)
        val fridayClosePoint = MockHistoricalPoint(fridayCloseMs, 85.00)

        // Only Friday close is available
        val result = PriceChangeCalculator.calculate24hChange(87.55, nowMs, listOf(fridayClosePoint))

        assertTrue("Fallback must find last trading point within 80h weekend window", result.isAvailable)
        assertEquals(fridayCloseMs, result.referenceTimestampMs)
        assertEquals(85.00, result.referencePrice, 0.001)
        assertEquals(PriceChangeDirection.POSITIVE, result.direction)
        assertEquals(3.00, result.percentageChange, 0.01) // (87.55 - 85) / 85 * 100 = +3.00%
    }

    @Test
    fun historicalReferenceOutsideAllowedTolerance_returnsUnavailable() {
        // Point is 90 hours old (> 80h max lookback)
        val stalePoint = MockHistoricalPoint(nowMs - (90 * 3600 * 1000L), 80.00)

        val result = PriceChangeCalculator.calculate24hChange(85.00, nowMs, listOf(stalePoint))

        assertFalse("Observations beyond 80h must be rejected", result.isAvailable)
        assertEquals(PriceChangeDirection.NEUTRAL, result.direction)
        assertEquals("—", result.formattedPercentage)
        assertEquals("—", result.formattedWithArrow)
    }

    @Test
    fun missingReferenceObservation_returnsUnavailable() {
        val result = PriceChangeCalculator.calculate24hChange(85.00, nowMs, emptyList())

        assertFalse(result.isAvailable)
        assertEquals("—", result.formattedPercentage)
    }

    @Test
    fun invalidOrNonPositiveReferencePrice_returnsUnavailable() {
        val zeroRef = listOf(MockHistoricalPoint(target24hMs, 0.0))
        val negRef = listOf(MockHistoricalPoint(target24hMs, -50.0))
        val nanRef = listOf(MockHistoricalPoint(target24hMs, Double.NaN))
        val infRef = listOf(MockHistoricalPoint(target24hMs, Double.POSITIVE_INFINITY))

        assertFalse(PriceChangeCalculator.calculate24hChange(85.00, nowMs, zeroRef).isAvailable)
        assertFalse(PriceChangeCalculator.calculate24hChange(85.00, nowMs, negRef).isAvailable)
        assertFalse(PriceChangeCalculator.calculate24hChange(85.00, nowMs, nanRef).isAvailable)
        assertFalse(PriceChangeCalculator.calculate24hChange(85.00, nowMs, infRef).isAvailable)
    }

    @Test
    fun invalidCurrentPriceOrTimestamp_returnsUnavailable() {
        val validPoints = listOf(MockHistoricalPoint(target24hMs, 85.00))

        assertFalse(PriceChangeCalculator.calculate24hChange(0.0, nowMs, validPoints).isAvailable)
        assertFalse(PriceChangeCalculator.calculate24hChange(-10.0, nowMs, validPoints).isAvailable)
        assertFalse(PriceChangeCalculator.calculate24hChange(Double.NaN, nowMs, validPoints).isAvailable)
        assertFalse(PriceChangeCalculator.calculate24hChange(Double.POSITIVE_INFINITY, nowMs, validPoints).isAvailable)
        assertFalse(PriceChangeCalculator.calculate24hChange(85.00, -1L, validPoints).isAvailable)
    }

    @Test
    fun futureTimestampsInPoints_areSafelyIgnored() {
        // Point timestamp in the future (> currentTimestampMs)
        val futurePoint = MockHistoricalPoint(nowMs + 3600000L, 95.00)

        val result = PriceChangeCalculator.calculate24hChange(85.00, nowMs, listOf(futurePoint))

        assertFalse("Future reference points must be ignored", result.isAvailable)
    }
}
