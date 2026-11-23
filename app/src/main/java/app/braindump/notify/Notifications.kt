package app.braindump.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import app.braindump.MainActivity
import app.braindump.R

object Notifications {
    const val CHANNEL_CHECK_IN = "check_in"
    const val CHANNEL_REVIEW = "review"
    const val ID_CHECK_IN = 10
    const val ID_REVIEW = 11
    const val CHANNEL_REVISIT = "revisit"
    const val KEY_REPLY = "reply"
    const val EXTRA_COVERS_FROM = "covers_from"
    const val EXTRA_OPEN = "open"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CHECK_IN, "Check-ins", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Gentle reminders to log what you did" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REVIEW, "Evening review", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "A nudge to look back at your day" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REVISIT, "Thread revisits", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "When a thread is due for a fresh look" }
        )
    }

    private fun canPost(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context, open: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN, open)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun showCheckIn(context: Context, coversFrom: Long, sinceLabel: String) {
        if (!canPost(context)) return
        val replyIntent = Intent(context, ReplyReceiver::class.java)
            .setAction(ReplyReceiver.ACTION_REPLY)
            .putExtra(EXTRA_COVERS_FROM, coversFrom)
        // Mutable is required so the system can attach the typed reply.
        val replyPi = PendingIntent.getBroadcast(
            context, 20, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val remoteInput = RemoteInput.Builder(KEY_REPLY).setLabel("What did you do?").build()
        val reply = NotificationCompat.Action.Builder(R.drawable.ic_note, "Log it", replyPi)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            // Typing into the journal from the lock screen needs the phone unlocked first.
            .setAuthenticationRequired(true)
            .build()
        val skipPi = PendingIntent.getBroadcast(
            context, 21,
            Intent(context, ReplyReceiver::class.java).setAction(ReplyReceiver.ACTION_SKIP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_CHECK_IN)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle("What have you been up to?")
            .setContentText("Since $sinceLabel. One line is enough.")
            .setContentIntent(openApp(context, "today", 30))
            .addAction(reply)
            .addAction(R.drawable.ic_note, "Skip", skipPi)
            .setDeleteIntent(PendingIntent.getBroadcast(
                context, 22,
                Intent(context, ReplyReceiver::class.java).setAction(ReplyReceiver.ACTION_DISMISS),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(ID_CHECK_IN, n)
    }

    fun showReview(context: Context, entryCount: Int) {
        if (!canPost(context)) return
        val text = if (entryCount == 0) "Nothing logged yet today. Want to fill in the day?"
        else "You logged $entryCount ${if (entryCount == 1) "thing" else "things"} today. Take a quiet look back."
        val n = NotificationCompat.Builder(context, CHANNEL_REVIEW)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle("Time for your evening review")
            .setContentText(text)
            .setContentIntent(openApp(context, "review", 31))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(ID_REVIEW, n)
    }

    fun showRevisit(context: Context, threadId: String, title: String) {
        if (!canPost(context)) return
        val id = 1000 + (threadId.hashCode() and 0x7fff)
        val n = NotificationCompat.Builder(context, CHANNEL_REVISIT)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle("Revisit: $title")
            .setContentText("How do you see it now?")
            .setContentIntent(openApp(context, "thread:$threadId", id))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    fun isCheckInShowing(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == ID_CHECK_IN }

    fun cancelCheckIn(context: Context) = NotificationManagerCompat.from(context).cancel(ID_CHECK_IN)
}
