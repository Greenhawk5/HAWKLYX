package ir.alifaniani.hawklyx.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Deterministic unit tests for [BrentMarketCalendar].
 * Validates ICE Futures Europe market hours in Europe/London timezone:
 * - Weekly open: Sunday 23:00 London
 * - Daily break: Mon-Thu 22:00-23:00 London
 * - Weekly close: Friday 22:00 London
 * - Weekend close: Friday 22:00 to Sunday 23:00 London
 * - Deterministic session ID generation
 */
class BrentMarketCalendarTest {

    private val londonZone = ZoneId.of("Europe/London")

    @Test
    fun regularTradingSession_isDetectedAsOpen() {
        // Wednesday 14:00 London
        val zdt = ZonedDateTime.of(2026, 10, 21, 14, 0, 0, 0, londonZone)
        val epochMs = zdt.toInstant().toEpochMilli()

        val sessionInfo = BrentMarketCalendar.getSessionInfo(epochMs)

        assertTrue("Wednesday 14:00 London must be open", sessionInfo.isMarketOpen)
        assertEquals("Wednesday session must belong to 2026-10-21", "SESSION_2026-10-21", sessionInfo.sessionId)
    }

    @Test
    fun weekdayMaintenanceBreak_isDetectedAsClosed() {
        // Tuesday 22:30 London (daily maintenance break between 22:00 and 23:00)
        val zdt = ZonedDateTime.of(2026, 10, 20, 22, 30, 0, 0, londonZone)
        val epochMs = zdt.toInstant().toEpochMilli()

        val sessionInfo = BrentMarketCalendar.getSessionInfo(epochMs)

        assertFalse("Daily maintenance break (22:00-23:00) must be closed", sessionInfo.isMarketOpen)
    }

    @Test
    fun weekend_isDetectedAsClosed() {
        // Saturday 12:00 London
        val saturdayZdt = ZonedDateTime.of(2026, 10, 24, 12, 0, 0, 0, londonZone)
        val saturdayMs = saturdayZdt.toInstant().toEpochMilli()

        val saturdayInfo = BrentMarketCalendar.getSessionInfo(saturdayMs)
        assertFalse("Saturday 12:00 London must be closed", saturdayInfo.isMarketOpen)

        // Sunday 18:00 London (before 23:00 open)
        val sundayZdt = ZonedDateTime.of(2026, 10, 25, 18, 0, 0, 0, londonZone)
        val sundayMs = sundayZdt.toInstant().toEpochMilli()

        val sundayInfo = BrentMarketCalendar.getSessionInfo(sundayMs)
        assertFalse("Sunday 18:00 London must be closed", sundayInfo.isMarketOpen)
    }

    @Test
    fun sundayWeeklyOpen_at2300_startsNewSessionForMonday() {
        // Sunday 23:05 London
        val zdt = ZonedDateTime.of(2026, 10, 25, 23, 5, 0, 0, londonZone)
        val epochMs = zdt.toInstant().toEpochMilli()

        val sessionInfo = BrentMarketCalendar.getSessionInfo(epochMs)

        assertTrue("Sunday 23:05 London must be open", sessionInfo.isMarketOpen)
        assertEquals("Sunday 23:00+ session belongs to Monday trade date", "SESSION_2026-10-26", sessionInfo.sessionId)
    }

    @Test
    fun fridaySessionClose_at2200_closesTrading() {
        // Friday 22:01 London
        val zdt = ZonedDateTime.of(2026, 10, 23, 22, 1, 0, 0, londonZone)
        val epochMs = zdt.toInstant().toEpochMilli()

        val sessionInfo = BrentMarketCalendar.getSessionInfo(epochMs)

        assertFalse("Friday after 22:00 London must be closed for weekend", sessionInfo.isMarketOpen)
    }

    @Test
    fun previousSessionCloseBaseline_beforeMondaySession_isFridayClose() {
        // Monday 02:00 London (session started Sunday 23:00)
        val mondayZdt = ZonedDateTime.of(2026, 10, 26, 2, 0, 0, 0, londonZone)
        val epochMs = mondayZdt.toInstant().toEpochMilli()

        val sessionInfo = BrentMarketCalendar.getSessionInfo(epochMs)
        val baselineCloseMs = sessionInfo.prevSessionCloseEpochMs

        val baselineZdt = Instant.ofEpochMilli(baselineCloseMs).atZone(londonZone)
        assertEquals("Baseline for Monday session must be Friday", DayOfWeek.FRIDAY, baselineZdt.dayOfWeek)
        assertEquals("Baseline close hour must be 22:00", 22, baselineZdt.hour)
    }

    @Test
    fun previousSessionCloseBaseline_beforeWednesdaySession_isTuesdayClose() {
        // Wednesday 10:00 London
        val wedZdt = ZonedDateTime.of(2026, 10, 21, 10, 0, 0, 0, londonZone)
        val epochMs = wedZdt.toInstant().toEpochMilli()

        val sessionInfo = BrentMarketCalendar.getSessionInfo(epochMs)
        val baselineCloseMs = sessionInfo.prevSessionCloseEpochMs

        val baselineZdt = Instant.ofEpochMilli(baselineCloseMs).atZone(londonZone)
        assertEquals("Baseline for Wednesday session must be Tuesday", DayOfWeek.TUESDAY, baselineZdt.dayOfWeek)
        assertEquals("Baseline close hour must be 22:00", 22, baselineZdt.hour)
    }
}
