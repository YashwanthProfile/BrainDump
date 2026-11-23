package app.braindump.notify

import java.time.LocalDateTime

/** Pure scheduling rules, kept free of Android so they are unit tested. */
object Schedule {
    /**
     * Check-ins fall at [startMinute], then every [intervalMinutes], up to and including [endMinute].
     * Returns the first slot strictly after [now], rolling over to tomorrow's first slot.
     */
    fun nextCheckIn(now: LocalDateTime, intervalMinutes: Int, startMinute: Int, endMinute: Int): LocalDateTime {
        require(intervalMinutes > 0)
        val today = now.toLocalDate().atStartOfDay()
        var m = startMinute
        while (m <= endMinute) {
            val slot = today.plusMinutes(m.toLong())
            if (slot.isAfter(now)) return slot
            m += intervalMinutes
        }
        return today.plusDays(1).plusMinutes(startMinute.toLong())
    }

    /** The next time the clock shows [minuteOfDay], strictly after [now]. */
    fun nextDaily(now: LocalDateTime, minuteOfDay: Int): LocalDateTime {
        val today = now.toLocalDate().atStartOfDay().plusMinutes(minuteOfDay.toLong())
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /**
     * Activity-based check-ins: the next nudge is [intervalMinutes] after the last thing you did
     * (a log, or answering/dismissing a nudge), kept inside waking hours [startMinute]..[endMinute].
     */
    fun nextAfterActivity(now: LocalDateTime, lastActivity: LocalDateTime?, intervalMinutes: Int, startMinute: Int, endMinute: Int): LocalDateTime {
        var c = (lastActivity ?: now).plusMinutes(intervalMinutes.toLong())
        if (c.isBefore(now)) c = now.plusMinutes(1)
        val m = c.hour * 60 + c.minute
        val day = c.toLocalDate().atStartOfDay()
        return when {
            m < startMinute -> day.plusMinutes(startMinute.toLong())
            m > endMinute -> day.plusDays(1).plusMinutes(startMinute.toLong())
            else -> c
        }
    }
}
