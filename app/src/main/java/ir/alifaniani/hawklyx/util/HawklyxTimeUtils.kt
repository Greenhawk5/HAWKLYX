package ir.alifaniani.hawklyx.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Standardized, device-local time formatting utility for HAWKLYX.
 *
 * Guarantees:
 * - All presentation timestamps strictly convert absolute UTC instants into the device's local timezone.
 * - Zero user-facing technical timezone annotations: no "UTC", "GMT", "Z", or numeric offsets like "+03:30".
 * - Deterministic timezone injection support for robust unit testing.
 * - Non-destructive: preserves underlying UTC data in models and API payloads.
 */
object HawklyxTimeUtils {

    /**
     * Formats an ISO-8601 instant string into concise local time "HH:mm:ss".
     * Example: "2026-10-18T14:05:22Z" at UTC+03:30 -> "17:35:22"
     */
    fun formatIsoToLocalTime(
        isoString: String?,
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        if (isoString.isNullOrBlank()) return "--:--:--"
        return try {
            val instant = Instant.parse(isoString)
            val formatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
                .withZone(zone)
            formatter.format(instant)
        } catch (_: Exception) {
            isoString.take(19).replace("T", " ")
        }
    }

    /**
     * Formats an ISO-8601 instant string or epoch milliseconds into widget-friendly "HH:mm".
     * Example: "2026-10-18T14:05:00Z" at UTC+03:30 -> "17:35"
     */
    fun formatIsoToShortTime(
        isoString: String?,
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        if (isoString.isNullOrBlank()) return "--:--"
        return try {
            val instant = Instant.parse(isoString)
            val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                .withZone(zone)
            formatter.format(instant)
        } catch (_: Exception) {
            isoString.take(16).replace("T", " ")
        }
    }

    /**
     * Formats an epoch millisecond timestamp into local "HH:mm".
     */
    fun formatEpochToShortTime(
        epochMs: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        if (epochMs <= 0L) return "--:--"
        return try {
            val instant = Instant.ofEpochMilli(epochMs)
            val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                .withZone(zone)
            formatter.format(instant)
        } catch (_: Exception) {
            "--:--"
        }
    }

    /**
     * Formats an ISO-8601 instant string into a readable local date and time: "MMM dd, HH:mm".
     * Example: "2026-10-18T14:05:00Z" at UTC+03:30 -> "Oct 18, 17:35"
     */
    fun formatIsoToDateTime(
        isoString: String?,
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        if (isoString.isNullOrBlank()) return "Awaiting sync"
        return try {
            val instant = Instant.parse(isoString)
            val formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm", Locale.getDefault())
                .withZone(zone)
            formatter.format(instant)
        } catch (_: Exception) {
            isoString.take(16).replace("T", " ")
        }
    }

    /**
     * Formats an epoch millisecond timestamp into a readable local date and time: "MMM dd, HH:mm".
     */
    fun formatEpochToDateTime(
        epochMs: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        if (epochMs <= 0L) return ""
        return try {
            val instant = Instant.ofEpochMilli(epochMs)
            val formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm", Locale.getDefault())
                .withZone(zone)
            formatter.format(instant)
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Formats a historical daily date: "EEE, MMM dd, yyyy".
     */
    fun formatEpochToDailyDate(
        epochMs: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        if (epochMs <= 0L) return ""
        return try {
            val instant = Instant.ofEpochMilli(epochMs)
            val formatter = DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy", Locale.getDefault())
                .withZone(zone)
            formatter.format(instant)
        } catch (_: Exception) {
            ""
        }
    }
}
