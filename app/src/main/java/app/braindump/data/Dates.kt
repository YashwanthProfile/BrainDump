package app.braindump.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object Dates {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val dayFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM")
    private val shortDayFmt = DateTimeFormatter.ofPattern("EEE d MMM")

    fun dayOf(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()
    fun today(): String = LocalDate.now(zone).toString()
    fun time(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(timeFmt)
    fun longDay(date: String): String = LocalDate.parse(date).format(dayFmt)
    fun shortDay(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(shortDayFmt)

    /** Millis for [date] at [minuteOfDay] in the local zone. */
    fun at(date: String, minuteOfDay: Int): Long =
        LocalDate.parse(date).atStartOfDay(zone).plusMinutes(minuteOfDay.toLong()).toInstant().toEpochMilli()

    fun minuteOfDay(millis: Long): Int {
        val t = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        return t.hour * 60 + t.minute
    }

    fun formatMinute(minuteOfDay: Int): String =
        java.time.LocalTime.of(minuteOfDay / 60 % 24, minuteOfDay % 60).format(timeFmt)

    fun duration(minutes: Long): String = "%dh %02dm".format(minutes / 60, minutes % 60)
}
