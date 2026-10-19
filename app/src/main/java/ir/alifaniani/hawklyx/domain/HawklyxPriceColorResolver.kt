package ir.alifaniani.hawklyx.domain

import androidx.compose.ui.graphics.Color
import ir.alifaniani.hawklyx.R
import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.ui.theme.HawklyxAmber
import ir.alifaniani.hawklyx.ui.theme.HawklyxEmerald
import ir.alifaniani.hawklyx.ui.theme.HawklyxRed
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextMuted
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextPrimary
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextSecondary

/**
 * Centralized, authoritative price-direction color resolver for HAWKLYX.
 *
 * Guarantees strict visual and semantic parity across:
 * - Home Price Card
 * - Market History Screen / Chart
 * - Market Snapshot Widget
 * - Market Intelligence Widget
 *
 * Semantic Rules:
 * - Market closed: Amber
 * - Stale observation / Offline: Amber
 * - Quota exhausted: Text Secondary
 * - Live market open:
 *     - Price above session reference: Emerald Green
 *     - Price below session reference: Red
 *     - Price equal to reference: Neutral Emerald / Primary
 * - No data: Text Muted
 */
object HawklyxPriceColorResolver {

    fun resolveComposeColor(
        observation: BrentPriceObservation?,
        status: DataFreshnessStatus,
        isMarketClosed: Boolean,
        sessionDirection: PriceDirection = PriceDirection.NEUTRAL,
        isBaselineAvailable: Boolean = true
    ): Color {
        val hasObservation = observation != null
        val isClosed = isMarketClosed || status == DataFreshnessStatus.MARKET_CLOSED
        val isStaleObs = observation?.isStale == true

        return when {
            !hasObservation -> HawklyxTextMuted
            isClosed -> HawklyxAmber
            status == DataFreshnessStatus.STALE || isStaleObs -> HawklyxAmber
            status == DataFreshnessStatus.PROVIDER_UNAVAILABLE || status == DataFreshnessStatus.INVALID_RESPONSE -> HawklyxAmber
            status == DataFreshnessStatus.QUOTA_EXHAUSTED -> HawklyxTextSecondary
            !isBaselineAvailable -> HawklyxTextSecondary
            status == DataFreshnessStatus.FRESH -> when (sessionDirection) {
                PriceDirection.POSITIVE -> HawklyxEmerald
                PriceDirection.NEGATIVE -> HawklyxRed
                PriceDirection.NEUTRAL -> HawklyxEmerald
            }
            else -> HawklyxTextPrimary
        }
    }

    fun resolveResourceColor(
        observation: BrentPriceObservation?,
        status: DataFreshnessStatus,
        isMarketClosed: Boolean,
        sessionDirection: PriceDirection = PriceDirection.NEUTRAL,
        isBaselineAvailable: Boolean = true
    ): Int {
        val hasObservation = observation != null
        val isClosed = isMarketClosed || status == DataFreshnessStatus.MARKET_CLOSED
        val isStaleObs = observation?.isStale == true

        return when {
            !hasObservation -> R.color.hawklyx_text_muted
            isClosed -> R.color.hawklyx_amber
            status == DataFreshnessStatus.STALE || isStaleObs -> R.color.hawklyx_amber
            status == DataFreshnessStatus.PROVIDER_UNAVAILABLE || status == DataFreshnessStatus.INVALID_RESPONSE -> R.color.hawklyx_amber
            status == DataFreshnessStatus.QUOTA_EXHAUSTED -> R.color.hawklyx_text_secondary
            !isBaselineAvailable -> R.color.hawklyx_text_secondary
            status == DataFreshnessStatus.FRESH -> when (sessionDirection) {
                PriceDirection.POSITIVE -> R.color.hawklyx_emerald
                PriceDirection.NEGATIVE -> R.color.hawklyx_red
                PriceDirection.NEUTRAL -> R.color.hawklyx_emerald
            }
            else -> R.color.hawklyx_text_primary
        }
    }
}
