package app.braindump.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import app.braindump.data.Dates
import app.braindump.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try { block() } finally { pending.finish() }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async {
        val repo = Repository.get(context)
        val settings = repo.settings.current()
        when (intent.action) {
            Alarms.ACTION_CHECK_IN -> {
                if (!Notifications.isCheckInShowing(context)) {
                    // Ask about everything since you last did something (but not before today's start).
                    val coversFrom = maxOf(
                        Alarms.lastActivity(context) ?: 0L,
                        Dates.at(Dates.today(), settings.startMinute),
                    )
                    Notifications.showCheckIn(context, coversFrom, Dates.time(coversFrom))
                }
            }
            Alarms.ACTION_REVISIT -> Alarms.pingDueThreads(context)
            Alarms.ACTION_REVIEW -> {
                val count = repo.entries(Dates.today()).first().size
                Notifications.showReview(context, count)
            }
        }
        Alarms.scheduleAll(context, settings)
    }
}

/** Handles the inline "Log it" reply and "Skip" on the check-in notification. */
class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async {
        if (intent.action == ACTION_REPLY) {
            val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifications.KEY_REPLY)?.toString()
            if (!text.isNullOrBlank()) {
                val coversFrom = intent.getLongExtra(Notifications.EXTRA_COVERS_FROM, 0L).takeIf { it > 0 }
                Repository.get(context).addEntry(text, coversFrom = coversFrom)
            }
        }
        Notifications.cancelCheckIn(context)
        // Answered, skipped or swiped away: the next nudge counts from now.
        Alarms.markResolved(context)
        Alarms.scheduleAll(context, Repository.get(context).settings.current())
    }

    companion object {
        const val ACTION_REPLY = "app.braindump.REPLY"
        const val ACTION_SKIP = "app.braindump.SKIP"
        const val ACTION_DISMISS = "app.braindump.DISMISS"
    }
}

/** Alarms are cleared on reboot and app update, so set them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async {
        Alarms.scheduleAll(context, Repository.get(context).settings.current())
    }
}
