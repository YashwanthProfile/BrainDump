package app.braindump.data

/** Summary of completed nights, from (bed, wake) pairs given as minute-of-day plus duration. */
data class Night(val bedMinute: Int, val wakeMinute: Int, val durationMinutes: Long)

data class SleepSummary(
    val nights: Int,
    val avgDurationMinutes: Long,
    val avgBedMinute: Int,
    val avgWakeMinute: Int,
    val metWakeGoal: Int,
)

object SleepStats {
    fun summarize(nights: List<Night>, wakeGoalMinute: Int): SleepSummary? {
        if (nights.isEmpty()) return null
        return SleepSummary(
            nights = nights.size,
            avgDurationMinutes = nights.sumOf { it.durationMinutes } / nights.size,
            avgBedMinute = averageClock(nights.map { it.bedMinute }, pivot = 12 * 60),
            avgWakeMinute = averageClock(nights.map { it.wakeMinute }, pivot = 0),
            metWakeGoal = nights.count { it.wakeMinute <= wakeGoalMinute },
        )
    }

    /**
     * Averages clock times that may wrap past midnight. Times are measured from [pivot]
     * so bedtimes like 23:30 and 01:30 average to 00:30, not noon.
     */
    fun averageClock(minutes: List<Int>, pivot: Int): Int {
        val shifted = minutes.map { ((it - pivot) % 1440 + 1440) % 1440 }
        return ((shifted.average().toInt() + pivot) % 1440 + 1440) % 1440
    }
}
