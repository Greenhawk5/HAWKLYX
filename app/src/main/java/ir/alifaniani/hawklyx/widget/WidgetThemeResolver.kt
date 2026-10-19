package ir.alifaniani.hawklyx.widget

import ir.alifaniani.hawklyx.R
import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.domain.HawklyxPriceColorResolver
import ir.alifaniani.hawklyx.domain.PriceDirection

/**
 * Resolved visual theme for HAWKLYX home-screen widgets.
 * Centralizes the dynamic market-state theming system ensuring strict consistency
 * between both Market Snapshot and Market Intelligence widgets and the main application.
 */
data class WidgetVisualTheme(
    val priceColorRes: Int,
    val badgeText: String,
    val badgeTextColorRes: Int,
    val badgeBgRes: Int,
    val statusMessage: String,
    val cadencePillText: String,
    val isMarketClosed: Boolean,
    val isStaleOrOffline: Boolean,
    val isRefreshing: Boolean,
    val sessionDirection: PriceDirection = PriceDirection.NEUTRAL
)

object WidgetThemeResolver {

    /**
     * Resolves the visual theme based on data freshness status, observation presence,
     * authoritative market-open/closed state, and session price direction.
     *
     * Invariants:
     * - When market is closed (status == MARKET_CLOSED or isMarketOpen == false), price switches to HawklyxAmber.
     * - When market is open and fresh:
     *     - Price is HawklyxEmerald if price has increased relative to session close baseline (PriceDirection.POSITIVE).
     *     - Price is HawklyxRed if price has decreased relative to session close baseline (PriceDirection.NEGATIVE).
     *     - Price is HawklyxEmerald if price is equal to baseline (PriceDirection.NEUTRAL).
     * - When stale or provider unavailable, price switches to HawklyxAmber to signal cached quote.
     */
    fun resolve(
        status: DataFreshnessStatus,
        observation: BrentPriceObservation?,
        isMarketOpen: Boolean = true,
        isRefreshing: Boolean = false,
        sessionDirection: PriceDirection = PriceDirection.NEUTRAL
    ): WidgetVisualTheme {
        val hasObservation = observation != null
        val isClosed = status == DataFreshnessStatus.MARKET_CLOSED || !isMarketOpen
        val isStaleObs = observation?.isStale == true

        val resolvedPriceColor = HawklyxPriceColorResolver.resolveResourceColor(
            observation = observation,
            status = if (isStaleObs && status == DataFreshnessStatus.FRESH) DataFreshnessStatus.STALE else status,
            isMarketClosed = isClosed,
            sessionDirection = sessionDirection
        )

        if (isRefreshing) {
            return WidgetVisualTheme(
                priceColorRes = resolvedPriceColor,
                badgeText = "SYNCING",
                badgeTextColorRes = R.color.hawklyx_emerald,
                badgeBgRes = R.drawable.hawklyx_badge_emerald,
                statusMessage = "Syncing with edge coordinator...",
                cadencePillText = if (isClosed) "LAST CLOSE" else "15M CADENCE",
                isMarketClosed = isClosed,
                isStaleOrOffline = false,
                isRefreshing = true,
                sessionDirection = sessionDirection
            )
        }

        // 1. Authoritative MARKET CLOSED State
        if (isClosed) {
            return WidgetVisualTheme(
                priceColorRes = resolvedPriceColor,
                badgeText = "MARKET CLOSED",
                badgeTextColorRes = R.color.hawklyx_amber,
                badgeBgRes = R.drawable.hawklyx_badge_amber,
                statusMessage = "Market closed • Last available price",
                cadencePillText = "LAST CLOSE",
                isMarketClosed = true,
                isStaleOrOffline = false,
                isRefreshing = false,
                sessionDirection = sessionDirection
            )
        }

        // 2. STALE or Stale Observation Flag
        if (status == DataFreshnessStatus.STALE || isStaleObs) {
            return WidgetVisualTheme(
                priceColorRes = resolvedPriceColor,
                badgeText = "STALE",
                badgeTextColorRes = R.color.hawklyx_amber,
                badgeBgRes = R.drawable.hawklyx_badge_amber,
                statusMessage = "Cached price observation • Tap to refresh",
                cadencePillText = "CACHED",
                isMarketClosed = false,
                isStaleOrOffline = true,
                isRefreshing = false,
                sessionDirection = sessionDirection
            )
        }

        // 3. OFFLINE / PROVIDER UNAVAILABLE
        if (status == DataFreshnessStatus.PROVIDER_UNAVAILABLE || status == DataFreshnessStatus.INVALID_RESPONSE) {
            return WidgetVisualTheme(
                priceColorRes = resolvedPriceColor,
                badgeText = "OFFLINE",
                badgeTextColorRes = R.color.hawklyx_amber,
                badgeBgRes = R.drawable.hawklyx_badge_amber,
                statusMessage = "Network offline • Preserving last price",
                cadencePillText = "OFFLINE",
                isMarketClosed = false,
                isStaleOrOffline = true,
                isRefreshing = false,
                sessionDirection = sessionDirection
            )
        }

        // 4. QUOTA EXHAUSTED
        if (status == DataFreshnessStatus.QUOTA_EXHAUSTED) {
            return WidgetVisualTheme(
                priceColorRes = resolvedPriceColor,
                badgeText = "QUOTA LIMIT",
                badgeTextColorRes = R.color.hawklyx_red,
                badgeBgRes = R.drawable.hawklyx_badge_red,
                statusMessage = "Daily quota reached • Preserving price",
                cadencePillText = "DAILY LIMIT",
                isMarketClosed = false,
                isStaleOrOffline = false,
                isRefreshing = false,
                sessionDirection = sessionDirection
            )
        }

        // 5. FRESH Live Market Observation
        if (status == DataFreshnessStatus.FRESH && hasObservation) {
            return WidgetVisualTheme(
                priceColorRes = resolvedPriceColor,
                badgeText = "LIVE",
                badgeTextColorRes = R.color.hawklyx_emerald,
                badgeBgRes = R.drawable.hawklyx_badge_emerald,
                statusMessage = "Live energy quote • 15m cadence",
                cadencePillText = "15M CADENCE",
                isMarketClosed = false,
                isStaleOrOffline = false,
                isRefreshing = false,
                sessionDirection = sessionDirection
            )
        }

        // 6. NO DATA / Initial State
        return WidgetVisualTheme(
            priceColorRes = resolvedPriceColor,
            badgeText = "NO DATA",
            badgeTextColorRes = R.color.hawklyx_text_muted,
            badgeBgRes = R.drawable.hawklyx_badge_bg,
            statusMessage = "Awaiting first synchronization",
            cadencePillText = "PENDING",
            isMarketClosed = false,
            isStaleOrOffline = false,
            isRefreshing = false,
            sessionDirection = sessionDirection
        )
    }
}
