package ir.alifaniani.hawklyx.domain

import ir.alifaniani.hawklyx.R
import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.ui.theme.HawklyxAmber
import ir.alifaniani.hawklyx.ui.theme.HawklyxEmerald
import ir.alifaniani.hawklyx.ui.theme.HawklyxRed
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextMuted
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextSecondary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Deterministic unit tests for [HawklyxPriceColorResolver].
 * Ensures strict color parity between Compose screens and RemoteViews widgets:
 * - Market closed -> Amber
 * - Open + POSITIVE -> Emerald
 * - Open + NEGATIVE -> Red
 * - Open + NEUTRAL -> Emerald
 * - Stale / Offline -> Amber
 * - Quota exhausted -> Text secondary
 * - No data -> Text muted
 */
class HawklyxPriceColorResolverTest {

    private val liveObservation = BrentPriceObservation(
        price = 74.50,
        formatted = "$74.50",
        asOf = "2026-10-21T14:00:00Z",
        fetchedAt = "2026-10-21T14:05:00Z",
        isStale = false
    )

    private val staleObservation = BrentPriceObservation(
        price = 74.50,
        formatted = "$74.50",
        asOf = "2026-10-21T10:00:00Z",
        fetchedAt = "2026-10-21T10:05:00Z",
        isStale = true
    )

    @Test
    fun marketClosed_returnsAmberForBothComposeAndResource() {
        val composeColor = HawklyxPriceColorResolver.resolveComposeColor(
            observation = liveObservation,
            status = DataFreshnessStatus.MARKET_CLOSED,
            isMarketClosed = true,
            sessionDirection = PriceDirection.POSITIVE
        )
        assertEquals(HawklyxAmber, composeColor)

        val resColor = HawklyxPriceColorResolver.resolveResourceColor(
            observation = liveObservation,
            status = DataFreshnessStatus.MARKET_CLOSED,
            isMarketClosed = true,
            sessionDirection = PriceDirection.POSITIVE
        )
        assertEquals(R.color.hawklyx_amber, resColor)
    }

    @Test
    fun marketOpen_positiveDirection_returnsEmerald() {
        val composeColor = HawklyxPriceColorResolver.resolveComposeColor(
            observation = liveObservation,
            status = DataFreshnessStatus.FRESH,
            isMarketClosed = false,
            sessionDirection = PriceDirection.POSITIVE
        )
        assertEquals(HawklyxEmerald, composeColor)

        val resColor = HawklyxPriceColorResolver.resolveResourceColor(
            observation = liveObservation,
            status = DataFreshnessStatus.FRESH,
            isMarketClosed = false,
            sessionDirection = PriceDirection.POSITIVE
        )
        assertEquals(R.color.hawklyx_emerald, resColor)
    }

    @Test
    fun marketOpen_negativeDirection_returnsRed() {
        val composeColor = HawklyxPriceColorResolver.resolveComposeColor(
            observation = liveObservation,
            status = DataFreshnessStatus.FRESH,
            isMarketClosed = false,
            sessionDirection = PriceDirection.NEGATIVE
        )
        assertEquals(HawklyxRed, composeColor)

        val resColor = HawklyxPriceColorResolver.resolveResourceColor(
            observation = liveObservation,
            status = DataFreshnessStatus.FRESH,
            isMarketClosed = false,
            sessionDirection = PriceDirection.NEGATIVE
        )
        assertEquals(R.color.hawklyx_red, resColor)
    }

    @Test
    fun marketOpen_neutralDirection_returnsEmerald() {
        val composeColor = HawklyxPriceColorResolver.resolveComposeColor(
            observation = liveObservation,
            status = DataFreshnessStatus.FRESH,
            isMarketClosed = false,
            sessionDirection = PriceDirection.NEUTRAL
        )
        assertEquals(HawklyxEmerald, composeColor)

        val resColor = HawklyxPriceColorResolver.resolveResourceColor(
            observation = liveObservation,
            status = DataFreshnessStatus.FRESH,
            isMarketClosed = false,
            sessionDirection = PriceDirection.NEUTRAL
        )
        assertEquals(R.color.hawklyx_emerald, resColor)
    }

    @Test
    fun staleObservation_returnsAmberEvenWhenOpen() {
        val composeColor = HawklyxPriceColorResolver.resolveComposeColor(
            observation = staleObservation,
            status = DataFreshnessStatus.STALE,
            isMarketClosed = false,
            sessionDirection = PriceDirection.POSITIVE
        )
        assertEquals(HawklyxAmber, composeColor)

        val resColor = HawklyxPriceColorResolver.resolveResourceColor(
            observation = staleObservation,
            status = DataFreshnessStatus.STALE,
            isMarketClosed = false,
            sessionDirection = PriceDirection.POSITIVE
        )
        assertEquals(R.color.hawklyx_amber, resColor)
    }

    @Test
    fun offlineStatus_returnsAmber() {
        val composeColor = HawklyxPriceColorResolver.resolveComposeColor(
            observation = liveObservation,
            status = DataFreshnessStatus.PROVIDER_UNAVAILABLE,
            isMarketClosed = false,
            sessionDirection = PriceDirection.POSITIVE
        )
        assertEquals(HawklyxAmber, composeColor)

        val resColor = HawklyxPriceColorResolver.resolveResourceColor(
            observation = liveObservation,
            status = DataFreshnessStatus.PROVIDER_UNAVAILABLE,
            isMarketClosed = false,
            sessionDirection = PriceDirection.POSITIVE
        )
        assertEquals(R.color.hawklyx_amber, resColor)
    }

    @Test
    fun quotaExhaustedStatus_returnsSecondary() {
        val composeColor = HawklyxPriceColorResolver.resolveComposeColor(
            observation = liveObservation,
            status = DataFreshnessStatus.QUOTA_EXHAUSTED,
            isMarketClosed = false,
            sessionDirection = PriceDirection.POSITIVE
        )
        assertEquals(HawklyxTextSecondary, composeColor)

        val resColor = HawklyxPriceColorResolver.resolveResourceColor(
            observation = liveObservation,
            status = DataFreshnessStatus.QUOTA_EXHAUSTED,
            isMarketClosed = false,
            sessionDirection = PriceDirection.POSITIVE
        )
        assertEquals(R.color.hawklyx_text_secondary, resColor)
    }

    @Test
    fun nullObservation_returnsMuted() {
        val composeColor = HawklyxPriceColorResolver.resolveComposeColor(
            observation = null,
            status = DataFreshnessStatus.NO_DATA,
            isMarketClosed = false,
            sessionDirection = PriceDirection.NEUTRAL
        )
        assertEquals(HawklyxTextMuted, composeColor)

        val resColor = HawklyxPriceColorResolver.resolveResourceColor(
            observation = null,
            status = DataFreshnessStatus.NO_DATA,
            isMarketClosed = false,
            sessionDirection = PriceDirection.NEUTRAL
        )
        assertEquals(R.color.hawklyx_text_muted, resColor)
    }

    @Test
    fun missingBaseline_returnsNeutralSecondary_noFabricatedPositiveOrNegativeTrend() {
        // When comparison baseline is missing or unavailable, it MUST NOT resolve to green or red
        val composeColor = HawklyxPriceColorResolver.resolveComposeColor(
            observation = liveObservation,
            status = DataFreshnessStatus.FRESH,
            isMarketClosed = false,
            sessionDirection = PriceDirection.NEUTRAL,
            isBaselineAvailable = false
        )
        assertEquals(HawklyxTextSecondary, composeColor)
        org.junit.Assert.assertNotEquals(HawklyxEmerald, composeColor)
        org.junit.Assert.assertNotEquals(HawklyxRed, composeColor)

        val resColor = HawklyxPriceColorResolver.resolveResourceColor(
            observation = liveObservation,
            status = DataFreshnessStatus.FRESH,
            isMarketClosed = false,
            sessionDirection = PriceDirection.NEUTRAL,
            isBaselineAvailable = false
        )
        assertEquals(R.color.hawklyx_text_secondary, resColor)
        org.junit.Assert.assertNotEquals(R.color.hawklyx_emerald, resColor)
        org.junit.Assert.assertNotEquals(R.color.hawklyx_red, resColor)
    }
}
