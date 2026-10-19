package ir.alifaniani.hawklyx.domain

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Directional movement of the Brent crude price within the current active trading session.
 */
enum class PriceDirection {
    POSITIVE, // Price is above session reference (Emerald Green)
    NEGATIVE, // Price is below session reference (Red)
    NEUTRAL   // Price is equal to session reference or market is closed/stale (Neutral / Amber)
}

/**
 * Result model for the current-session price movement.
 */
data class SessionPriceState(
    val currentPrice: Double,
    val referencePrice: Double,
    val referenceTimestampMs: Long,
    val percentageChange: Double,
    val absoluteChange: Double,
    val direction: PriceDirection,
    val formattedPercentage: String,
    val sessionId: String,
    val isAvailable: Boolean,
    val isMarketOpen: Boolean
) {
    companion object {
        fun unavailable(
            sessionId: String = "",
            isMarketOpen: Boolean = false
        ): SessionPriceState {
            return SessionPriceState(
                currentPrice = 0.0,
                referencePrice = 0.0,
                referenceTimestampMs = 0L,
                percentageChange = 0.0,
                absoluteChange = 0.0,
                direction = PriceDirection.NEUTRAL,
                formattedPercentage = "—",
                sessionId = sessionId,
                isAvailable = false,
                isMarketOpen = isMarketOpen
            )
        }
    }
}

/**
 * Domain calculator for session-based price direction and reference resolution.
 *
 * Guarantees:
 * - Strictly authentic: uses the last valid price recorded at the previous session close as baseline.
 * - Zero network requests: derives state entirely from existing cached observations and market calendar.
 * - Enforces amber neutral state when market is closed, offline, or stale.
 * - Never equates market open with price rising or market closed with price falling.
 * - Distinguishes session change from 24-hour rolling change.
 */
object SessionPriceCalculator {

    /**
     * Resolves the most recent valid observation recorded at or prior to session start
     * to serve as the baseline price for the new session.
     */
    fun findSessionReferencePoint(
        sessionStartEpochMs: Long,
        points: List<HistoricalPoint>
    ): HistoricalPoint? {
        val validPoints = points.filter {
            it.price > 0.0 && !it.price.isNaN() && !it.price.isInfinite() &&
                    it.timestampMs <= sessionStartEpochMs
        }
        return validPoints.maxByOrNull { it.timestampMs }
    }

    /**
     * Calculates session-based price direction.
     *
     * @param currentPrice Current valid price.
     * @param referencePrice Baseline price from previous session close.
     * @param referenceTimestampMs Timestamp of baseline observation.
     * @param sessionId Canonical session identifier.
     * @param isMarketOpen Whether market is actively trading.
     * @param isStale Whether the current quote is stale or offline.
     */
    fun calculateSessionPrice(
        currentPrice: Double,
        referencePrice: Double,
        referenceTimestampMs: Long,
        sessionId: String,
        isMarketOpen: Boolean,
        isStale: Boolean = false
    ): SessionPriceState {
        // 1. Inactive or closed market -> Neutral amber state
        if (!isMarketOpen) {
            return SessionPriceState(
                currentPrice = currentPrice.coerceAtLeast(0.0),
                referencePrice = referencePrice.coerceAtLeast(0.0),
                referenceTimestampMs = referenceTimestampMs,
                percentageChange = 0.0,
                absoluteChange = 0.0,
                direction = PriceDirection.NEUTRAL,
                formattedPercentage = "0.00%",
                sessionId = sessionId,
                isAvailable = false,
                isMarketOpen = false
            )
        }

        // 2. Stale or offline observation -> Neutral state (preserve freshness semantics)
        if (isStale) {
            return SessionPriceState(
                currentPrice = currentPrice.coerceAtLeast(0.0),
                referencePrice = referencePrice.coerceAtLeast(0.0),
                referenceTimestampMs = referenceTimestampMs,
                percentageChange = 0.0,
                absoluteChange = 0.0,
                direction = PriceDirection.NEUTRAL,
                formattedPercentage = "—",
                sessionId = sessionId,
                isAvailable = false,
                isMarketOpen = true
            )
        }

        // 3. Validate numeric inputs
        if (currentPrice <= 0.0 || currentPrice.isNaN() || currentPrice.isInfinite() ||
            referencePrice <= 0.0 || referencePrice.isNaN() || referencePrice.isInfinite()
        ) {
            return SessionPriceState.unavailable(sessionId = sessionId, isMarketOpen = true)
        }

        // 4. Formula: ((current - ref) / ref) * 100
        val deltaAbs = currentPrice - referencePrice
        val deltaPct = (deltaAbs / referencePrice) * 100.0

        // Round to 2 decimal places to determine sign and avoid "-0.00%"
        val roundedHundredths = (deltaPct * 100.0).roundToLong()
        val roundedValue = roundedHundredths / 100.0

        val (direction, formattedPct) = when {
            roundedHundredths > 0L -> {
                Pair(PriceDirection.POSITIVE, String.format(Locale.US, "+%.2f%%", roundedValue))
            }
            roundedHundredths < 0L -> {
                Pair(PriceDirection.NEGATIVE, String.format(Locale.US, "%.2f%%", roundedValue))
            }
            else -> {
                Pair(PriceDirection.NEUTRAL, "0.00%")
            }
        }

        return SessionPriceState(
            currentPrice = currentPrice,
            referencePrice = referencePrice,
            referenceTimestampMs = referenceTimestampMs,
            percentageChange = roundedValue,
            absoluteChange = deltaAbs,
            direction = direction,
            formattedPercentage = formattedPct,
            sessionId = sessionId,
            isAvailable = true,
            isMarketOpen = true
        )
    }
}
