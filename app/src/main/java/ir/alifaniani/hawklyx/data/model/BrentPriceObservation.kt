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

data class PriceUiState(
    val observation: BrentPriceObservation? = null,
    val status: DataFreshnessStatus = DataFreshnessStatus.NO_DATA,
    val message: String = "Awaiting first synchronization",
    val isRefreshing: Boolean = false,
    val nextRefreshAllowedTimeMs: Long = 0L,
    val cooldownRemainingSeconds: Int = 0
)
