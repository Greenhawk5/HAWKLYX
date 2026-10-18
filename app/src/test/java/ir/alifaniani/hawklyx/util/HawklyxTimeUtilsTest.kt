package ir.alifaniani.hawklyx.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Unit tests verifying local time standardization across HAWKLYX.
 * Validates deterministic timezone conversions, daylight saving time shifts,
 * and strict elimination of UTC/GMT/offset technical annotations from user-facing strings.
 */
class HawklyxTimeUtilsTest {

    private val tehranZone = ZoneId.of("Asia/Tehran")
    private val parisZone = ZoneId.of("Europe/Paris")
    private val newYorkZone = ZoneId.of("America/New_York")
    private val tokyoZone = ZoneId.of("Asia/Tokyo")

    @Test
    fun formatIsoToLocalTime_convertsUtcInstantToLocalTime() {
        // 2026-10-18T10:00:00Z -> In New York (EDT, UTC-4): 06:00:00
        val utcIso = "2026-10-18T10:00:00Z"
        val nyTime = HawklyxTimeUtils.formatIsoToLocalTime(utcIso, newYorkZone)
        assertEquals("06:00:00", nyTime)

        // In Tokyo (JST, UTC+9): 19:00:00
        val tokyoTime = HawklyxTimeUtils.formatIsoToLocalTime(utcIso, tokyoZone)
        assertEquals("19:00:00", tokyoTime)

        // In Tehran (UTC+03:30 standard time in 2026): 13:30:00
        val tehranTime = HawklyxTimeUtils.formatIsoToLocalTime(utcIso, tehranZone)
        assertEquals("13:30:00", tehranTime)
    }

    @Test
    fun formatIsoToShortTime_formatsConciseLocalTimeForWidgets() {
        val utcIso = "2026-10-18T14:05:00Z"

        // In Paris (CEST, UTC+2): 16:05
        val parisTime = HawklyxTimeUtils.formatIsoToShortTime(utcIso, parisZone)
        assertEquals("16:05", parisTime)

        // In New York (EDT, UTC-4): 10:05
        val nyTime = HawklyxTimeUtils.formatIsoToShortTime(utcIso, newYorkZone)
        assertEquals("10:05", nyTime)
    }

    @Test
    fun formatEpochToShortTime_handlesEpochMillisecondsAccurately() {
        val epochMs = Instant.parse("2026-10-18T14:00:00Z").toEpochMilli()
        val nyTime = HawklyxTimeUtils.formatEpochToShortTime(epochMs, newYorkZone)
        assertEquals("10:00", nyTime)
    }

    @Test
    fun formatIsoToDateTime_formatsReadableMonthDayTime() {
        val utcIso = "2026-10-18T23:30:00Z"

        // In Tokyo (UTC+9), 23:30 UTC crosses midnight into next day Oct 19, 08:30
        val tokyoTime = HawklyxTimeUtils.formatIsoToDateTime(utcIso, tokyoZone)
        assertEquals("Oct 19, 08:30", tokyoTime)

        // In New York (UTC-4), it is still Oct 18, 19:30
        val nyTime = HawklyxTimeUtils.formatIsoToDateTime(utcIso, newYorkZone)
        assertEquals("Oct 18, 19:30", nyTime)
    }

    @Test
    fun daylightSavingTransition_handledCorrectlyWithoutHardcodedOffsets() {
        // Paris summer time (CEST, UTC+2) on 2026-07-01
        val summerIso = "2026-07-01T12:00:00Z"
        val summerParis = HawklyxTimeUtils.formatIsoToShortTime(summerIso, parisZone)
        assertEquals("14:00", summerParis)

        // Paris winter time (CET, UTC+1) on 2026-12-01
        val winterIso = "2026-12-01T12:00:00Z"
        val winterParis = HawklyxTimeUtils.formatIsoToShortTime(winterIso, parisZone)
        assertEquals("13:00", winterParis)
    }

    @Test
    fun formatters_neverAppendTechnicalTimezoneAnnotations() {
        val testIso = "2026-10-18T14:05:00Z"
        val epochMs = Instant.parse(testIso).toEpochMilli()

        val outputs = listOf(
            HawklyxTimeUtils.formatIsoToLocalTime(testIso, tehranZone),
            HawklyxTimeUtils.formatIsoToShortTime(testIso, tehranZone),
            HawklyxTimeUtils.formatEpochToShortTime(epochMs, tehranZone),
            HawklyxTimeUtils.formatIsoToDateTime(testIso, tehranZone),
            HawklyxTimeUtils.formatEpochToDateTime(epochMs, tehranZone),
            HawklyxTimeUtils.formatEpochToDailyDate(epochMs, tehranZone)
        )

        for (out in outputs) {
            assertFalse("Output '$out' must not contain 'UTC'", out.contains("UTC", ignoreCase = true))
            assertFalse("Output '$out' must not contain 'GMT'", out.contains("GMT", ignoreCase = true))
            assertFalse("Output '$out' must not contain offset '+03'", out.contains("+03"))
            assertFalse("Output '$out' must not end with 'Z'", out.endsWith("Z"))
        }
    }

    @Test
    fun nullOrBlankInputs_handledGracefullyWithoutCrashing() {
        assertEquals("--:--:--", HawklyxTimeUtils.formatIsoToLocalTime(null))
        assertEquals("--:--:--", HawklyxTimeUtils.formatIsoToLocalTime(""))
        assertEquals("--:--", HawklyxTimeUtils.formatIsoToShortTime(null))
        assertEquals("--:--", HawklyxTimeUtils.formatEpochToShortTime(-1L))
        assertEquals("Awaiting sync", HawklyxTimeUtils.formatIsoToDateTime(null))
        assertEquals("", HawklyxTimeUtils.formatEpochToDailyDate(0L))
    }
}
