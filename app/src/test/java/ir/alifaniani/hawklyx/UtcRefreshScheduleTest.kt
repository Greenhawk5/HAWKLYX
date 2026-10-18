package ir.alifaniani.hawklyx

import ir.alifaniani.hawklyx.data.schedule.UtcRefreshSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

class UtcRefreshScheduleTest {

    @Test
    fun calculateNextCadence_fromMidQuarterHour_returnsNextQuarterHour() {
        // 2026-10-18T14:07:22Z -> Next is 14:15:00Z
        val now = ZonedDateTime.of(2026, 10, 18, 14, 7, 22, 0, ZoneOffset.UTC)
            .toInstant().toEpochMilli()

        val nextCadence = UtcRefreshSchedule.calculateNextCadenceEpochMs(now)
        val nextZoned = Instant.ofEpochMilli(nextCadence).atZone(ZoneOffset.UTC)

        assertEquals(14, nextZoned.hour)
        assertEquals(15, nextZoned.minute)
        assertEquals(0, nextZoned.second)
        assertEquals(0, nextZoned.nano)
    }

    @Test
    fun calculateNextCadence_oneSecondBeforeBoundary_targetsUpcomingBoundary() {
        // 2026-10-18T14:14:59Z -> Next is 14:15:00Z (1 second remaining)
        val now = ZonedDateTime.of(2026, 10, 18, 14, 14, 59, 0, ZoneOffset.UTC)
            .toInstant().toEpochMilli()

        val nextCadence = UtcRefreshSchedule.calculateNextCadenceEpochMs(now)
        val nextZoned = Instant.ofEpochMilli(nextCadence).atZone(ZoneOffset.UTC)

        assertEquals(14, nextZoned.hour)
        assertEquals(15, nextZoned.minute)
        assertEquals(1, UtcRefreshSchedule.getRemainingSeconds(now))
    }

    @Test
    fun calculateNextCadence_oneSecondAfterBoundary_targetsFollowingBoundary() {
        // 2026-10-18T14:15:01Z -> Next is 14:30:00Z
        val now = ZonedDateTime.of(2026, 10, 18, 14, 15, 1, 0, ZoneOffset.UTC)
            .toInstant().toEpochMilli()

        val nextCadence = UtcRefreshSchedule.calculateNextCadenceEpochMs(now)
        val nextZoned = Instant.ofEpochMilli(nextCadence).atZone(ZoneOffset.UTC)

        assertEquals(14, nextZoned.hour)
        assertEquals(30, nextZoned.minute)
        assertEquals(0, nextZoned.second)
    }

    @Test
    fun calculateNextCadence_exactBoundary_targetsNextQuarterHour() {
        // Exactly at 14:30:00.000Z -> Next is 14:45:00Z
        val now = ZonedDateTime.of(2026, 10, 18, 14, 30, 0, 0, ZoneOffset.UTC)
            .toInstant().toEpochMilli()

        val nextCadence = UtcRefreshSchedule.calculateNextCadenceEpochMs(now)
        val nextZoned = Instant.ofEpochMilli(nextCadence).atZone(ZoneOffset.UTC)

        assertEquals(14, nextZoned.hour)
        assertEquals(45, nextZoned.minute)
    }

    @Test
    fun calculateNextCadence_nearMidnightUtc_rollsOverDayCleanly() {
        // 2026-10-18T23:59:45Z -> Next is 2026-10-19T00:00:00Z
        val now = ZonedDateTime.of(2026, 10, 18, 23, 59, 45, 0, ZoneOffset.UTC)
            .toInstant().toEpochMilli()

        val nextCadence = UtcRefreshSchedule.calculateNextCadenceEpochMs(now)
        val nextZoned = Instant.ofEpochMilli(nextCadence).atZone(ZoneOffset.UTC)

        assertEquals(2026, nextZoned.year)
        assertEquals(10, nextZoned.monthValue)
        assertEquals(19, nextZoned.dayOfMonth)
        assertEquals(0, nextZoned.hour)
        assertEquals(0, nextZoned.minute)
        assertEquals(0, nextZoned.second)
        assertEquals(15, UtcRefreshSchedule.getRemainingSeconds(now))
    }

    @Test
    fun formatRemaining_formatsCorrectly() {
        assertEquals("00:00", UtcRefreshSchedule.formatRemaining(0))
        assertEquals("00:45", UtcRefreshSchedule.formatRemaining(45))
        assertEquals("02:59", UtcRefreshSchedule.formatRemaining(179))
        assertEquals("15:00", UtcRefreshSchedule.formatRemaining(900))
    }
}
