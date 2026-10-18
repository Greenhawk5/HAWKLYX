package ir.alifaniani.hawklyx.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Directional movement of the 24-hour price change.
 */
enum class PriceChangeDirection {
    POSITIVE, // Price increased over 24h (green / up-trend)
    NEGATIVE, // Price decreased over 24h (red / down-trend)
    NEUTRAL   // Price unchanged or rounded to 0.00% (neutral / flat)
}

/**
 * Result model for the 24-hour price change metric.
 */
data class PriceChange24h(
    val percentageChange: Double,
    val absoluteChange: Double,
    val currentPrice: Double,
    val referencePrice: Double,
    val referenceTimestampMs: Long,
    val direction: PriceChangeDirection,
    val formattedPercentage: String,
    val formattedWithArrow: String,
    val isAvailable: Boolean
) {
    companion object {
        fun unavailable(): PriceChange24h {
            return PriceChange24h(
                percentageChange = 0.0,
                absoluteChange = 0.0,
                currentPrice = 0.0,
                referencePrice = 0.0,
                referenceTimestampMs = 0L,
                direction = PriceChangeDirection.NEUTRAL,
                formattedPercentage = "—",
                formattedWithArrow = "—",
                isAvailable = false
            )
        }
    }
}

/**
 * Common interface representing any timestamped historical price point.
 */
interface HistoricalPoint {
    val timestampMs: Long
    val price: Double
}

/**
 * Production domain calculator for the authentic 24-hour Brent crude percentage change.
 *
 * Guarantees:
 * - Strictly authentic: uses genuine historical price observations only.
 * - Zero extra network requests: computes metric from already-cached historical series.
 * - Robust tolerance & market closure handling:
 *     * Primary window: closest observation within ±3 hours of (now - 24h).
 *     * Market closure/weekend fallback: closest preceding observation before (now - 24h)
 *       within an 80-hour maximum lookback (covers Friday close to Monday afternoon).
 * - Precise rounding: prevents "-0.00%" artifacts; zero-rounding maps to NEUTRAL.
 * - Rejects non-finite, negative, or future reference points.
 */
object PriceChangeCalculator {

    const val MILLIS_24H = 24 * 3600 * 1000L
    const val PRIMARY_TOLERANCE_MS = 3 * 3600 * 1000L // ±3 hours for normal hourly cadence
    const val MAX_WEEKEND_LOOKBACK_MS = 80 * 3600 * 1000L // 80h max lookback for weekend closures

    /**
     * Calculates the 24-hour price change.
     *
     * @param currentPrice Current valid price quote.
     * @param currentTimestampMs Current observation epoch milliseconds.
     * @param points Chronological or unsorted list of historical observations.
     * @return Fully formatted [PriceChange24h] or [PriceChange24h.unavailable] if no valid reference exists.
     */
    fun calculate24hChange(
        currentPrice: Double,
        currentTimestampMs: Long,
        points: List<HistoricalPoint>
    ): PriceChange24h {
        // 1. Validate current price
        if (currentPrice <= 0.0 || currentPrice.isNaN() || currentPrice.isInfinite() || currentTimestampMs <= 0L) {
            return PriceChange24h.unavailable()
        }

        // 2. Filter for valid historical points
        val validPoints = points.filter {
            it.price > 0.0 && !it.price.isNaN() && !it.price.isInfinite() &&
                    it.timestampMs in 1 until currentTimestampMs
        }
        if (validPoints.isEmpty()) {
            return PriceChange24h.unavailable()
        }

        val targetTimeMs = currentTimestampMs - MILLIS_24H

        // 3. Selection Strategy:
        // Strategy A: Find closest point within ±3 hours of target time (normal trading session)
        val candidateInPrimaryWindow = validPoints
            .filter { abs(it.timestampMs - targetTimeMs) <= PRIMARY_TOLERANCE_MS }
            .minByOrNull { abs(it.timestampMs - targetTimeMs) }

        val referencePoint = if (candidateInPrimaryWindow != null) {
            candidateInPrimaryWindow
        } else {
            // Strategy B: Market closure / weekend fallback:
            // Find most recent point strictly before targetTimeMs within MAX_WEEKEND_LOOKBACK_MS of currentTimestampMs
            validPoints
                .filter { it.timestampMs <= targetTimeMs && (currentTimestampMs - it.timestampMs) <= MAX_WEEKEND_LOOKBACK_MS }
                .maxByOrNull { it.timestampMs }
        }

        if (referencePoint == null) {
            return PriceChange24h.unavailable()
        }

        val refPrice = referencePoint.price
        if (refPrice <= 0.0 || refPrice.isNaN() || refPrice.isInfinite()) {
            return PriceChange24h.unavailable()
        }

        // 4. Formula: ((current - ref) / ref) * 100
        val deltaAbs = currentPrice - refPrice
        val deltaPct = (deltaAbs / refPrice) * 100.0

        // 5. Round to 2 decimal places to determine sign and avoid "-0.00%"
        // Round half-up to 2 decimals:
        val roundedHundredths = (deltaPct * 100.0).roundToLong()
        val roundedValue = roundedHundredths / 100.0

        val (direction, formattedPct, formattedWithArrow) = when {
            roundedHundredths > 0L -> {
                val str = String.format(Locale.US, "+%.2f%%", roundedValue)
                Triple(PriceChangeDirection.POSITIVE, str, "↑ $str")
            }
            roundedHundredths < 0L -> {
                val str = String.format(Locale.US, "%.2f%%", roundedValue) // already has minus
                Triple(PriceChangeDirection.NEGATIVE, str, "↓ $str")
            }
            else -> {
                Triple(PriceChangeDirection.NEUTRAL, "0.00%", "→ 0.00%")
            }
        }

        return PriceChange24h(
            percentageChange = roundedValue,
            absoluteChange = deltaAbs,
            currentPrice = currentPrice,
            referencePrice = refPrice,
            referenceTimestampMs = referencePoint.timestampMs,
            direction = direction,
            formattedPercentage = formattedPct,
            formattedWithArrow = formattedWithArrow,
            isAvailable = true
        )
    }
}
