package app.braindump.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.braindump.data.Settings
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first

object Alarms {
    const val ACTION_CHECK_IN = "app.braindump.CHECK_IN"
    const val ACTION_REVIEW = "app.braindump.REVIEW"
    const val ACTION_REVISIT = "app.braindump.REVISIT"
    private const val PINGS = "revisit_pings"

    /** One nudge on the day a thread falls due, then at most one a week until you add a note. */
    suspend fun pingDueThreads(context: Context) {
        val repo = app.braindump.data.Repository.get(context)
        val threads = repo.threads().first()
        val notes = repo.allThreadNotes().first()
        val prefs = context.getSharedPreferences(PINGS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        app.braindump.ui.dueThreads(threads, notes).forEach { t ->
            val lastNote = notes.filter { it.threadId == t.id }.maxOfOrNull { it.at } ?: t.createdAt
            val lastPing = prefs.getLong(t.id, 0L)
            val firstSinceNote = lastPing < lastNote
            if (firstSinceNote || now - lastPing >= 7 * 86_400_000L) {
                Notifications.showRevisit(context, t.id, t.title)
                prefs.edit().putLong(t.id, now).apply()
            }
        }
    }

    private const val PREFS = "checkin"
    private const val KEY_RESOLVED = "resolved_at"

    /** Remember that a nudge was answered, skipped or swiped away just now. */
    fun markResolved(context: Context, at: Long = System.currentTimeMillis()) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_RESOLVED, at).apply()

    /** The last time you did something: logged a moment, or dealt with a nudge. */
    suspend fun lastActivity(context: Context): Long? {
        val resolved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_RESOLVED, 0L).takeIf { it > 0 }
        val logged = app.braindump.data.Repository.get(context).lastLogAt()
        return listOfNotNull(resolved, logged).maxOrNull()
    }

    suspend fun scheduleAll(context: Context, settings: Settings) {
        val now = LocalDateTime.now()
        // While a nudge is still waiting for you, don't stack another one on top.
        if (!Notifications.isCheckInShowing(context)) {
            val last = lastActivity(context)?.let { LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
            set(context, ACTION_CHECK_IN, 1,
                Schedule.nextAfterActivity(now, last, settings.intervalMinutes, settings.startMinute, settings.endMinute))
        }
        set(context, ACTION_REVIEW, 2, Schedule.nextDaily(now, settings.reviewMinute))
        // Thread revisits: once a day, an hour into your waking hours.
        set(context, ACTION_REVISIT, 3, Schedule.nextDaily(now, minOf(settings.startMinute + 60, settings.endMinute)))
    }

    private fun set(context: Context, action: String, requestCode: Int, at: LocalDateTime) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }
}
