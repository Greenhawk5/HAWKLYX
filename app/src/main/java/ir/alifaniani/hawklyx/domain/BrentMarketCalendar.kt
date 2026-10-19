package ir.alifaniani.hawklyx.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Authoritative trading session metadata for ICE Futures Europe Brent Crude (BRENT_CRUDE_USD).
 *
 * London local trading hours (Europe/London timezone):
 * - Sunday: Opens at 23:00 London. Trading runs 23:00-24:00 (Weekly opening session).
 * - Monday - Thursday:
 *     00:00 - 22:00: Regular trading.
 *     22:00 - 23:00: Daily maintenance & settlement break (Closed).
 *     23:00 - 24:00: Regular trading resumes (Next day's session).
 * - Friday:
 *     00:00 - 22:00: Regular trading.
 *     22:00: Closes for the weekend.
 * - Saturday: Entire day closed.
 * - Sunday: 00:00 - 23:00 Closed for the weekend.
 */
data class MarketSessionInfo(
    val sessionId: String,
    val sessionStartEpochMs: Long,
    val sessionEndEpochMs: Long,
    val prevSessionCloseEpochMs: Long,
    val isMarketOpen: Boolean,
    val isDailyBreak: Boolean = false,
    val isWeekendClosed: Boolean = false,
    val isHolidayClosed: Boolean = false
)

object BrentMarketCalendar {

    val LONDON_ZONE: ZoneId = ZoneId.of("Europe/London")

    private val KNOWN_HOLIDAYS = setOf(
        LocalDate.of(2024, 1, 1),
        LocalDate.of(2024, 3, 29),
        LocalDate.of(2024, 12, 25),
        LocalDate.of(2025, 1, 1),
        LocalDate.of(2025, 4, 18),
        LocalDate.of(2025, 12, 25),
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 4, 3),
        LocalDate.of(2026, 12, 25),
        LocalDate.of(2027, 1, 1),
        LocalDate.of(2027, 3, 26),
        LocalDate.of(2027, 12, 25)
    )

    /**
     * Determines whether a given date in Europe/London is an exchange full-day holiday.
     */
    fun isHoliday(date: LocalDate): Boolean = KNOWN_HOLIDAYS.contains(date)

    /**
     * Resolves the trading session info for the given epoch millisecond timestamp.
     */
    fun getSessionInfo(nowMs: Long = System.currentTimeMillis()): MarketSessionInfo {
        val nowLondon = Instant.ofEpochMilli(nowMs).atZone(LONDON_ZONE)
        val date = nowLondon.toLocalDate()
        val hour = nowLondon.hour
        val dayOfWeek = nowLondon.dayOfWeek

        // 1. Holiday Check
        if (isHoliday(date)) {
            val prevTradingDate = findPreviousTradingDay(date)
            val prevCloseZdt = prevTradingDate.atTime(22, 0).atZone(LONDON_ZONE)
            return MarketSessionInfo(
                sessionId = "CLOSED_HOLIDAY_${date}",
                sessionStartEpochMs = prevCloseZdt.toInstant().toEpochMilli(),
                sessionEndEpochMs = prevCloseZdt.toInstant().toEpochMilli(),
                prevSessionCloseEpochMs = prevCloseZdt.toInstant().toEpochMilli(),
                isMarketOpen = false,
                isHolidayClosed = true
            )
        }

        // 2. Saturday: Entire day closed
        if (dayOfWeek == DayOfWeek.SATURDAY) {
            val fridayDate = date.minusDays(1)
            val fridayClose = fridayDate.atTime(22, 0).atZone(LONDON_ZONE).toInstant().toEpochMilli()
            val sundayOpen = date.plusDays(1).atTime(23, 0).atZone(LONDON_ZONE).toInstant().toEpochMilli()
            return MarketSessionInfo(
                sessionId = "CLOSED_WEEKEND_${date}",
                sessionStartEpochMs = sundayOpen,
                sessionEndEpochMs = sundayOpen,
                prevSessionCloseEpochMs = fridayClose,
                isMarketOpen = false,
                isWeekendClosed = true
            )
        }

        // 3. Sunday: Closed until 23:00 London
        if (dayOfWeek == DayOfWeek.SUNDAY) {
            val fridayDate = date.minusDays(2)
            val fridayClose = fridayDate.atTime(22, 0).atZone(LONDON_ZONE).toInstant().toEpochMilli()
            val sundayOpenZdt = date.atTime(23, 0).atZone(LONDON_ZONE)
            val sundayOpenMs = sundayOpenZdt.toInstant().toEpochMilli()

            if (hour < 23) {
                return MarketSessionInfo(
                    sessionId = "CLOSED_WEEKEND_${date}",
                    sessionStartEpochMs = sundayOpenMs,
                    sessionEndEpochMs = sundayOpenMs,
                    prevSessionCloseEpochMs = fridayClose,
                    isMarketOpen = false,
                    isWeekendClosed = true
                )
            }

            // Sunday 23:00 - 24:00 is Weekly Opening for Monday's trading session
            val mondayDate = date.plusDays(1)
            val mondayCloseMs = mondayDate.atTime(22, 0).atZone(LONDON_ZONE).toInstant().toEpochMilli()
            return MarketSessionInfo(
                sessionId = "SESSION_${mondayDate}",
                sessionStartEpochMs = sundayOpenMs,
                sessionEndEpochMs = mondayCloseMs,
                prevSessionCloseEpochMs = fridayClose,
                isMarketOpen = true
            )
        }

        // 4. Friday: Closes at 22:00 for the weekend
        if (dayOfWeek == DayOfWeek.FRIDAY) {
            val thursdayDate = date.minusDays(1)
            val thursdayCloseMs = thursdayDate.atTime(22, 0).atZone(LONDON_ZONE).toInstant().toEpochMilli()
            val fridaySessionStartMs = thursdayDate.atTime(23, 0).atZone(LONDON_ZONE).toInstant().toEpochMilli()
            val fridayCloseMs = date.atTime(22, 0).atZone(LONDON_ZONE).toInstant().toEpochMilli()

            if (hour >= 22) {
                // Friday weekend close has begun
                return MarketSessionInfo(
                    sessionId = "CLOSED_WEEKEND_${date}",
                    sessionStartEpochMs = fridayCloseMs,
                    sessionEndEpochMs = fridayCloseMs,
                    prevSessionCloseEpochMs = fridayCloseMs,
                    isMarketOpen = false,
                    isWeekendClosed = true
                )
            }

            return MarketSessionInfo(
                sessionId = "SESSION_${date}",
                sessionStartEpochMs = fridaySessionStartMs,
                sessionEndEpochMs = fridayCloseMs,
                prevSessionCloseEpochMs = thursdayCloseMs,
                isMarketOpen = true
            )
        }

        // 5. Monday - Thursday:
        // - 22:00 - 23:00: Daily maintenance & settlement break (Closed)
        if (hour == 22) {
            val sessionJustClosedMs = date.atTime(22, 0).atZone(LONDON_ZONE).toInstant().toEpochMilli()
            val nextSessionStartMs = date.atTime(23, 0).atZone(LONDON_ZONE).toInstant().toEpochMilli()
            return MarketSessionInfo(
                sessionId = "DAILY_BREAK_${date}",
                sessionStartEpochMs = nextSessionStartMs,
                sessionEndEpochMs = nextSessionStartMs,
                prevSessionCloseEpochMs = sessionJustClosedMs,
                isMarketOpen = false,
                isDailyBreak = true
            )
        }

        // - 23:00 - 24:00: Early opening for tomorrow's session
        if (hour >= 23) {
            val tomorrowDate = date.plusDays(1)
            val sessionStartMs = date.atTime(23, 0).atZone(LONDON_ZONE).toInstant().toEpochMilli()
            val sessionEndMs = tomorrowDate.atTime(22, 0).atZone(LONDON_ZONE).toInstant().toEpochMilli()
            val todayCloseMs = date.atTime(22, 0).atZone(LONDON_ZONE).toInstant().toEpochMilli()

            return MarketSessionInfo(
                sessionId = "SESSION_${tomorrowDate}",
                sessionStartEpochMs = sessionStartMs,
                sessionEndEpochMs = sessionEndMs,
                prevSessionCloseEpochMs = todayCloseMs,
                isMarketOpen = true
            )
        }

        // - 00:00 - 22:00: Regular trading for today's session
        val prevDayDate = date.minusDays(1)
        val prevCloseDate = if (dayOfWeek == DayOfWeek.MONDAY) date.minusDays(3) else prevDayDate
        val sessionStartMs = (if (dayOfWeek == DayOfWeek.MONDAY) date.minusDays(1) else prevDayDate)
            .atTime(23, 0).atZone(LONDON_ZONE).toInstant().toEpochMilli()
        val sessionEndMs = date.atTime(22, 0).atZone(LONDON_ZONE).toInstant().toEpochMilli()
        val prevCloseMs = prevCloseDate.atTime(22, 0).atZone(LONDON_ZONE).toInstant().toEpochMilli()

        return MarketSessionInfo(
            sessionId = "SESSION_${date}",
            sessionStartEpochMs = sessionStartMs,
            sessionEndEpochMs = sessionEndMs,
            prevSessionCloseEpochMs = prevCloseMs,
            isMarketOpen = true
        )
    }

    private fun findPreviousTradingDay(date: LocalDate): LocalDate {
        var cursor = date.minusDays(1)
        while (cursor.dayOfWeek == DayOfWeek.SATURDAY || cursor.dayOfWeek == DayOfWeek.SUNDAY || isHoliday(cursor)) {
            cursor = cursor.minusDays(1)
        }
        return cursor
    }
}
