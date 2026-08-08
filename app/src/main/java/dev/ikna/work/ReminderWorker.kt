package dev.ikna.work

import dev.ikna.ui.text.S

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.ikna.IknaApp
import dev.ikna.MainActivity
import dev.ikna.R

/**
 * The daily nudge.
 *
 * Two rules, both about not becoming another guilt machine: nothing is sent if
 * the minimum is already done, and the text asks for one card — never for a
 * streak, a queue size or a number of days missed. Nothing has to be muted for a
 * break either: the schedule absorbs unused days on its own, so a reminder after
 * an absence still points at a small day.
 */
class ReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as IknaApp
        val container = app.container
        val settings = container.settings.current()

        if (!settings.reminderEnabled) return Result.success()

        val repo = container.learningRepository
        if (repo.answeredToday() >= repo.dailyMinimum()) return Result.success()

        // Straight into the cards. A reminder that lands on the deck list asks
        // the question a second time, and the answer given at the notification
        // does not always survive being asked again.
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_START_SESSION, true)
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, IknaApp.REMINDER_CHANNEL_ID)
            // The platform draws this as a white silhouette from the alpha
            // channel alone, so it is a dedicated file rather than the launcher
            // icon: the launcher icon has a field behind it, and a field turns
            // into a solid white square in the status bar. Until now this was a
            // stock Android bell, which belonged to no app in particular.
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(S.t("remind.001"))
            .setContentText(S.t("remind.002"))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        // On Android 13+ this is a no-op without the runtime permission, which is
        // requested from the settings screen where the switch lives.
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        }
        return Result.success()
    }

    companion object {
        const val NAME = "ikna-reminder"
        private const val NOTIFICATION_ID = 1001
    }
}
