package ir.alifaniani.hawklyx.data.schedule

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Deterministic schedule calculations aligned to UTC quarter-hour boundaries:
 * 00:00, 00:15, 00:30, 00:45 UTC.
 *
 * Provides target epoch timestamps and countdown formatting independent of
 * local device time zones and daylight-saving shifts.
 */
object UtcRefreshSchedule {

    /**
     * Calculates the exact epoch millisecond timestamp of the next UTC quarter-hour boundary.
     * If the current time is exactly on a boundary, the next boundary 15 minutes ahead is returned.
     */
    fun calculateNextCadenceEpochMs(nowMs: Long): Long {
        val instant = Instant.ofEpochMilli(nowMs)
        val utcZoned = instant.atZone(ZoneOffset.UTC)
        val minute = utcZoned.minute
        val second = utcZoned.second
        val nano = utcZoned.nano

        val isExactlyOnBoundary = (minute % 15 == 0) && second == 0 && nano == 0

        val nextQuarterMinute = when {
            isExactlyOnBoundary -> (minute + 15)
            minute < 15 -> 15
            minute < 30 -> 30
            minute < 45 -> 45
            else -> 60
        }

        return if (nextQuarterMinute >= 60) {
            val nextHour = utcZoned.plusHours(1)
            nextHour.withMinute(nextQuarterMinute - 60)
                .withSecond(0)
                .withNano(0)
                .toInstant()
                .toEpochMilli()
        } else {
            utcZoned.withMinute(nextQuarterMinute)
                .withSecond(0)
                .withNano(0)
                .toInstant()
                .toEpochMilli()
        }
    }

    /**
     * Returns the remaining whole seconds until the next UTC quarter-hour cadence.
     */
    fun getRemainingSeconds(nowMs: Long): Int {
        val nextBoundaryMs = calculateNextCadenceEpochMs(nowMs)
        val diffSec = (nextBoundaryMs - nowMs) / 1000
        return diffSec.coerceAtLeast(0L).toInt()
    }

    /**
     * Formats seconds into a standardized mm:ss display (e.g. 14:59).
     */
    fun formatRemaining(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val m = s / 60
        val remainingSec = s % 60
        return "%02d:%02d".format(m, remainingSec)
    }
}
