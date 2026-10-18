package ir.alifaniani.hawklyx.data.model

/**
 * Validated Brent Crude price observation received from the HAWKLYX edge service.
 */
data class BrentPriceObservation(
    val code: String = "BRENT_CRUDE_USD",
    val name: String = "Brent Crude Oil",
    val price: Double,
    val currency: String = "USD",
    val unit: String = "USD / barrel",
    val formatted: String,
    val asOf: String,
    val collectedAt: String? = null,
    val createdAt: String? = null,
    val fetchedAt: String,
    val isStale: Boolean = false,
    val isSynthetic: Boolean = false,
    val source: String = "OilPriceAPI"
)

enum class DataFreshnessStatus {
    FRESH,
    STALE,
    REFRESHING,
    PROVIDER_UNAVAILABLE,
    QUOTA_EXHAUSTED,
    NO_DATA,
    INVALID_RESPONSE
}

/**
 * Genuine historical observation point recorded from the edge service.
 * Never fabricated or synthetically interpolated.
 */
data class HistoricalObservationPoint(
    val timestampMs: Long,
    val price: Double,
    val formatted: String,
    val asOfIso: String
)

/**
 * Comprehensive UI state for the HAWKLYX application.
 * Fully decouples observation presence, network state, cooldown countdown,
 * and background cadence scheduling.
 */
data class PriceUiState(
    val observation: BrentPriceObservation? = null,
    val status: DataFreshnessStatus = DataFreshnessStatus.NO_DATA,
    val message: String = "Awaiting first synchronization",
    val isRefreshing: Boolean = false,
    val nextRefreshAllowedTimeMs: Long = 0L,
    val cooldownRemainingSeconds: Int = 0,
    val nextScheduledCadenceTimeMs: Long = 0L,
    val cadenceRemainingSeconds: Int = 0,
    val history: List<HistoricalObservationPoint> = emptyList(),
    val lastSyncAttemptTimeMs: Long = 0L,
    val lastSyncSuccess: Boolean = false
)
