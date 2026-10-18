package ir.alifaniani.hawklyx.data.model

import ir.alifaniani.hawklyx.domain.HistoricalPoint
import java.time.Instant

/**
 * Standard supported ranges for the HAWKLYX historical price pipeline.
 * Maps cleanly to the backend range parameter ("1d", "7d", "30d", "6m", "1y").
 */
enum class HistoryRange(val apiValue: String, val label: String) {
    ONE_DAY("1d", "1D"),
    ONE_WEEK("7d", "1W"),
    ONE_MONTH("30d", "1M"),
    SIX_MONTHS("6m", "6M"),
    ONE_YEAR("1y", "1Y");

    companion object {
        fun fromApiValue(value: String): HistoryRange {
            return entries.firstOrNull { it.apiValue.equals(value, ignoreCase = true) } ?: ONE_MONTH
        }
    }
}

/**
 * Canonical historical observation point or daily OHLC aggregate point.
 * Matches backend `HistoricalDataPoint` contract.
 */
data class HistoricalPointModel(
    val timestamp: String,
    override val timestampMs: Long,
    override val price: Double,
    val formatted: String,
    val asOf: String,
    val fetchedAt: String,
    val open: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val close: Double? = null,
    val sampleCount: Int? = null,
    val isComplete: Boolean? = null
) : HistoricalPoint {
    val isDailyAggregate: Boolean
        get() = open != null && close != null
}

/**
 * Summary metrics computed by the backend edge service.
 * Matches backend `HistorySummary` contract.
 */
data class HistorySummaryModel(
    val firstPrice: Double,
    val latestPrice: Double,
    val changeAbsolute: Double,
    val changePercent: Double,
    val high: Double,
    val low: Double
)

/**
 * Complete typed response from the backend historical price endpoint (`GET /api/v1/price/history`).
 * Never fabricates or synthesizes points.
 */
data class HistoryResponseModel(
    val status: String,
    val instrument: String = "BRENT_CRUDE_USD",
    val currency: String = "USD",
    val unit: String = "USD / barrel",
    val range: HistoryRange = HistoryRange.ONE_MONTH,
    val resolution: String = "hourly",
    val collectionStartDate: String? = null,
    val earliestTimestamp: String? = null,
    val latestTimestamp: String? = null,
    val count: Int = 0,
    val points: List<HistoricalPointModel> = emptyList(),
    val summary: HistorySummaryModel? = null,
    val isPartial: Boolean = false,
    val serverTime: String = "",
    val message: String? = null
) {
    val isEmpty: Boolean
        get() = points.isEmpty() || status == "empty"
}

/**
 * Comprehensive UI state for the historical chart component.
 * Decoupled from the Hero live-price refresh cadence.
 */
data class HistoryUiState(
    val selectedRange: HistoryRange = HistoryRange.ONE_MONTH,
    val isLoading: Boolean = false,
    val isStale: Boolean = false,
    val data: HistoryResponseModel? = null,
    val errorMessage: String? = null
)
