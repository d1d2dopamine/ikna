package dev.ikna

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dev.ikna.work.WorkScheduler

class IknaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createReminderChannel()
        WorkScheduler.schedule(this)
    }

    /**
     * One quiet channel, importance DEFAULT and no badge: a reminder that shames
     * you is a reminder you turn off, and then the app is gone.
     */
    private fun createReminderChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "Напоминание",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Одно напоминание в день, если минимум ещё не сделан"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val REMINDER_CHANNEL_ID = "ikna-reminder"
    }
}
