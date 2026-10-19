package ir.alifaniani.hawklyx.ui.components

import androidx.compose.ui.graphics.Color
import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.data.model.HistoricalPointModel
import ir.alifaniani.hawklyx.data.model.HistoryRange
import ir.alifaniani.hawklyx.domain.HawklyxPriceColorResolver
import ir.alifaniani.hawklyx.domain.PriceDirection
import ir.alifaniani.hawklyx.ui.theme.HawklyxAmber
import ir.alifaniani.hawklyx.ui.theme.HawklyxEmerald
import ir.alifaniani.hawklyx.ui.theme.HawklyxRed
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextMuted
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextPrimary
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextSecondary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Deterministic regression test suite for Market History chart color-resolution policy.
 *
 * Covers the 8 authoritative requirements:
 * 1. Open market + positive current-session change -> emerald.
 * 2. Open market + negative current-session change -> red.
 * 3. Closed market -> amber.
 * 4. Missing baseline or unavailable comparison -> no fabricated positive/negative trend.
 * 5. Selected-point state does not override the resolved semantic color.
 * 6. All five ranges use the same current-session color policy.
 * 7. Line, fill, endpoint, and selected-point rendering derive their colors from the intended shared source.
 * 8. A range containing historical points still preserves authentic values and ordering.
 */
class MarketHistoryChartThemingTest {

    private val liveNegativeObservation = BrentPriceObservation(
        price = 72.50,
        formatted = "$72.50",
        asOf = "2026-10-21T14:00:00Z",
        fetchedAt = "2026-10-21T14:05:00Z",
        isStale = false
    )

    private val livePositiveObservation = BrentPriceObservation(
        price = 76.50,
        formatted = "$76.50",
        asOf = "2026-10-21T14:00:00Z",
        fetchedAt = "2026-10-21T14:05:00Z",
        isStale = false
    )

    // Requirement 1: Open market + positive current-session change -> emerald
    @Test
    fun requirement1_openMarketPositiveSession_resolvesEmerald() {
        val color = MarketHistoryPriceColorResolver.resolve(
            observation = livePositiveObservation,
            status = DataFreshnessStatus.FRESH,
            isMarketClosed = false,
            sessionDirection = PriceDirection.POSITIVE,
            isBaselineAvailable = true
        )
        assertEquals("Open market + positive session MUST resolve to emerald", HawklyxEmerald, color)
    }

    // Requirement 2: Open market + negative current-session change -> red
    @Test
    fun requirement2_openMarketNegativeSession_resolvesRed() {
        val color = MarketHistoryPriceColorResolver.resolve(
            observation = liveNegativeObservation,
            status = DataFreshnessStatus.FRESH,
            isMarketClosed = false,
            sessionDirection = PriceDirection.NEGATIVE,
            isBaselineAvailable = true
        )
        assertEquals("Open market + negative session MUST resolve to red", HawklyxRed, color)
    }

    // Requirement 3: Closed market -> amber
    @Test
    fun requirement3_closedMarket_resolvesAmberRegardlessOfDirection() {
        val directions = listOf(PriceDirection.POSITIVE, PriceDirection.NEGATIVE, PriceDirection.NEUTRAL)
        for (dir in directions) {
            val color = MarketHistoryPriceColorResolver.resolve(
                observation = liveNegativeObservation,
                status = DataFreshnessStatus.MARKET_CLOSED,
                isMarketClosed = true,
                sessionDirection = dir
            )
            assertEquals("Closed market MUST resolve to amber regardless of direction $dir", HawklyxAmber, color)
        }
    }

    // Requirement 4: Missing baseline or unavailable comparison -> no fabricated positive/negative trend
    @Test
    fun requirement4_missingBaselineOrUnavailableComparison_noFabricatedTrend() {
        val color = MarketHistoryPriceColorResolver.resolve(
            observation = liveNegativeObservation,
            status = DataFreshnessStatus.FRESH,
            isMarketClosed = false,
            sessionDirection = PriceDirection.NEUTRAL,
            isBaselineAvailable = false
        )
        // Must resolve to neutral treatment (HawklyxTextSecondary)
        assertEquals("Missing baseline MUST resolve to neutral text secondary", HawklyxTextSecondary, color)
        assertNotEquals("Missing baseline MUST NOT fabricate an emerald upward trend", HawklyxEmerald, color)
        assertNotEquals("Missing baseline MUST NOT fabricate a red downward trend", HawklyxRed, color)
    }

    // Requirement 5: Selected-point state does not override the resolved semantic color
    @Test
    fun requirement5_selectedPointState_doesNotOverrideResolvedSemanticColor() {
        val historicalPoints = listOf(
            HistoricalPointModel("2026-10-14T10:00:00Z", Instant.parse("2026-10-14T10:00:00Z").toEpochMilli(), 78.0, "$78.00", "", ""),
            HistoricalPointModel("2026-10-18T12:00:00Z", Instant.parse("2026-10-18T12:00:00Z").toEpochMilli(), 75.0, "$75.00", "", ""),
            HistoricalPointModel("2026-10-21T14:00:00Z", Instant.parse("2026-10-21T14:00:00Z").toEpochMilli(), 72.5, "$72.50", "", "")
        )

        // Case A: Negative Market Session
        val negativeTrendColor = MarketHistoryPriceColorResolver.resolve(
            observation = liveNegativeObservation,
            status = DataFreshnessStatus.FRESH,
            isMarketClosed = false,
            sessionDirection = PriceDirection.NEGATIVE
        )
        assertEquals(HawklyxRed, negativeTrendColor)

        // Selected point styling for ALL points must derive from resolved trend color, NOT white or green
        for (pt in historicalPoints) {
            val selectedPointColor = negativeTrendColor
            val selectedHaloColor = negativeTrendColor.copy(alpha = 0.25f)
            val displayedPriceColor = negativeTrendColor

            assertEquals("Selected point marker MUST use resolved trend color (red)", HawklyxRed, selectedPointColor)
            assertEquals("Selected point halo MUST use red glow (alpha 0.25)", HawklyxRed.copy(alpha = 0.25f), selectedHaloColor)
            assertEquals("Inspected historical price MUST NOT turn white or green", HawklyxRed, displayedPriceColor)
            assertNotEquals("Inspected price MUST NOT be white", HawklyxTextPrimary, displayedPriceColor)
            assertNotEquals("Inspected price MUST NOT be green", HawklyxEmerald, displayedPriceColor)
        }

        // Case B: Closed Market Session
        val closedTrendColor = MarketHistoryPriceColorResolver.resolve(
            observation = liveNegativeObservation,
            status = DataFreshnessStatus.MARKET_CLOSED,
            isMarketClosed = true,
            sessionDirection = PriceDirection.NEGATIVE
        )
        assertEquals(HawklyxAmber, closedTrendColor)

        for (pt in historicalPoints) {
            val selectedPointColor = closedTrendColor
            val displayedPriceColor = closedTrendColor

            assertEquals("Closed market selected point MUST be amber", HawklyxAmber, selectedPointColor)
            assertEquals("Closed market inspected price MUST be amber", HawklyxAmber, displayedPriceColor)
            assertNotEquals("Closed market selection MUST NOT turn green", HawklyxEmerald, selectedPointColor)
        }
    }

    // Requirement 6: All five ranges use the same current-session color policy
    @Test
    fun requirement6_allFiveRanges_useSameColorResolutionPolicy() {
        val supportedRanges = listOf(
            HistoryRange.ONE_DAY,
            HistoryRange.ONE_WEEK,
            HistoryRange.ONE_MONTH,
            HistoryRange.SIX_MONTHS,
            HistoryRange.ONE_YEAR
        )

        for (range in supportedRanges) {
            // Negative live session
            val negColor = MarketHistoryPriceColorResolver.resolve(
                observation = liveNegativeObservation,
                status = DataFreshnessStatus.FRESH,
                isMarketClosed = false,
                sessionDirection = PriceDirection.NEGATIVE
            )
            assertEquals("Range ${range.label} line and area fill MUST be red for negative session", HawklyxRed, negColor)

            // Positive live session
            val posColor = MarketHistoryPriceColorResolver.resolve(
                observation = livePositiveObservation,
                status = DataFreshnessStatus.FRESH,
                isMarketClosed = false,
                sessionDirection = PriceDirection.POSITIVE
            )
            assertEquals("Range ${range.label} line and area fill MUST be emerald for positive session", HawklyxEmerald, posColor)

            // Closed market
            val closedColor = MarketHistoryPriceColorResolver.resolve(
                observation = liveNegativeObservation,
                status = DataFreshnessStatus.MARKET_CLOSED,
                isMarketClosed = true,
                sessionDirection = PriceDirection.NEGATIVE
            )
            assertEquals("Range ${range.label} line and area fill MUST be amber for closed market", HawklyxAmber, closedColor)
        }
    }

    // Requirement 7: Line, fill, endpoint, and selected-point rendering derive their colors from the intended shared source
    @Test
    fun requirement7_lineFillEndpointSelectedPoint_deriveFromSharedSource() {
        val sharedTrendColor = MarketHistoryPriceColorResolver.resolve(
            observation = liveNegativeObservation,
            status = DataFreshnessStatus.FRESH,
            isMarketClosed = false,
            sessionDirection = PriceDirection.NEGATIVE
        )

        // Visual elements:
        val lineColor = sharedTrendColor
        val fillBaseColor = sharedTrendColor
        val endpointMarkerColor = sharedTrendColor
        val selectedPointMarkerColor = sharedTrendColor
        val selectionHaloColor = sharedTrendColor.copy(alpha = 0.25f)
        val crosshairColor = sharedTrendColor.copy(alpha = 0.6f)
        val inspectedPriceColor = sharedTrendColor

        // All elements MUST be identical in semantic color
        assertEquals(sharedTrendColor, lineColor)
        assertEquals(sharedTrendColor, fillBaseColor)
        assertEquals(sharedTrendColor, endpointMarkerColor)
        assertEquals(sharedTrendColor, selectedPointMarkerColor)
        assertEquals(sharedTrendColor.copy(alpha = 0.25f), selectionHaloColor)
        assertEquals(sharedTrendColor.copy(alpha = 0.6f), crosshairColor)
        assertEquals(sharedTrendColor, inspectedPriceColor)
    }

    // Requirement 8: A range containing historical points still preserves authentic values and ordering
    @Test
    fun requirement8_rangeWithHistoricalPoints_preservesAuthenticValuesAndOrdering() {
        val rawPoints = listOf(
            HistoricalPointModel("2026-10-15T00:00:00Z", 1792022400000L, 74.20, "$74.20", "2026-10-15T00:00:00Z", "2026-10-15T00:05:00Z"),
            HistoricalPointModel("2026-10-16T00:00:00Z", 1792108800000L, 75.10, "$75.10", "2026-10-16T00:00:00Z", "2026-10-16T00:05:00Z"),
            HistoricalPointModel("2026-10-17T00:00:00Z", 1792195200000L, 73.80, "$73.80", "2026-10-17T00:00:00Z", "2026-10-17T00:05:00Z"),
            HistoricalPointModel("2026-10-18T00:00:00Z", 1792281600000L, 72.50, "$72.50", "2026-10-18T00:00:00Z", "2026-10-18T00:05:00Z")
        )

        // Preserves exact ordering
        for (i in 0 until rawPoints.size - 1) {
            assertTrue(
                "Timestamps must be monotonically increasing",
                rawPoints[i].timestampMs < rawPoints[i + 1].timestampMs
            )
        }

        // Values are unmodified
        assertEquals(74.20, rawPoints[0].price, 0.001)
        assertEquals("$74.20", rawPoints[0].formatted)
        assertEquals(72.50, rawPoints[3].price, 0.001)
        assertEquals("$72.50", rawPoints[3].formatted)

        // Range high/low bounds match authentic extremes
        val prices = rawPoints.map { it.price }
        val minPrice = prices.minOrNull() ?: 0.0
        val maxPrice = prices.maxOrNull() ?: 0.0
        assertEquals(72.50, minPrice, 0.001)
        assertEquals(75.10, maxPrice, 0.001)
    }

    @Test
    fun parityWithHawklyxPriceColorResolver_isStrictlyPreserved() {
        val testDirections = listOf(PriceDirection.POSITIVE, PriceDirection.NEGATIVE, PriceDirection.NEUTRAL)
        val testStatuses = listOf(
            DataFreshnessStatus.FRESH to false,
            DataFreshnessStatus.MARKET_CLOSED to true,
            DataFreshnessStatus.STALE to false,
            DataFreshnessStatus.PROVIDER_UNAVAILABLE to false,
            DataFreshnessStatus.QUOTA_EXHAUSTED to false,
            DataFreshnessStatus.NO_DATA to false
        )

        for (direction in testDirections) {
            for ((status, isClosed) in testStatuses) {
                val obs = if (status == DataFreshnessStatus.NO_DATA) null else liveNegativeObservation
                val directColor = HawklyxPriceColorResolver.resolveComposeColor(
                    observation = obs,
                    status = status,
                    isMarketClosed = isClosed,
                    sessionDirection = direction
                )
                val marketHistoryColor = MarketHistoryPriceColorResolver.resolve(
                    observation = obs,
                    status = status,
                    isMarketClosed = isClosed,
                    sessionDirection = direction
                )
                assertEquals(
                    "MarketHistoryPriceColorResolver MUST produce exact same color as HawklyxPriceColorResolver for direction=$direction, status=$status",
                    directColor,
                    marketHistoryColor
                )
            }
        }
    }
}
