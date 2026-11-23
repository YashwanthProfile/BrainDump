package app.braindump

import app.braindump.data.Night
import app.braindump.data.SleepStats
import app.braindump.notify.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class ScheduleTest {
    private val day = LocalDateTime.of(2026, 11, 16, 0, 0)
    private fun at(h: Int, m: Int = 0) = day.withHour(h).withMinute(m)

    // Defaults: every 3 h from 8:00 to 22:00 -> 8, 11, 14, 17, 20.
    private fun next(now: LocalDateTime) = Schedule.nextCheckIn(now, 180, 8 * 60, 22 * 60)

    @Test fun beforeStartGoesToFirstSlot() = assertEquals(at(8), next(at(6, 30)))
    @Test fun exactlyOnSlotGoesToNext() = assertEquals(at(11), next(at(8)))
    @Test fun betweenSlots() = assertEquals(at(17), next(at(15, 10)))
    @Test fun afterLastSlotRollsToTomorrow() = assertEquals(at(8).plusDays(1), next(at(20, 1)))

    @Test fun endIsInclusive() =
        assertEquals(at(22), Schedule.nextCheckIn(at(21), 120, 8 * 60, 22 * 60))

    // Activity-based nudges: one hour after the last log, inside 8:00-22:00.
    private fun after(now: LocalDateTime, last: LocalDateTime?) = Schedule.nextAfterActivity(now, last, 60, 8 * 60, 22 * 60)
    @Test fun hourAfterLastLog() = assertEquals(at(17, 27), after(at(16, 27), at(16, 27)))
    @Test fun overdueFiresSoon() = assertEquals(at(15, 1), after(at(15), at(12)))
    @Test fun lateNightWaitsForMorning() = assertEquals(at(8).plusDays(1), after(at(21, 30), at(21, 30)))
    @Test fun earlyMorningWaitsForStart() = assertEquals(at(8), after(at(6), at(6)))

    @Test fun dailyLaterToday() = assertEquals(at(22), Schedule.nextDaily(at(21), 22 * 60))
    @Test fun dailyTomorrowWhenPassed() = assertEquals(at(22).plusDays(1), Schedule.nextDaily(at(22), 22 * 60))

    @Test fun bedtimesAverageAcrossMidnight() =
        assertEquals(30, SleepStats.averageClock(listOf(23 * 60 + 30, 60 + 30), pivot = 12 * 60))

    @Test fun summaryCountsWakeGoal() {
        val s = SleepStats.summarize(
            listOf(Night(23 * 60, 6 * 60, 420), Night(60, 11 * 60, 600), Night(0, 5 * 60 + 50, 350)),
            wakeGoalMinute = 6 * 60,
        )!!
        assertEquals(2, s.metWakeGoal)
        assertEquals(456L, s.avgDurationMinutes)
    }

    @Test fun emptySummaryIsNull() = assertNull(SleepStats.summarize(emptyList(), 360))
}
