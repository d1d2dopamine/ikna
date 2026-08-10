package dev.ikna.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object WorkScheduler {

    /**
     * @param dayStartHour the hour the study day rolls over, from the governor
     *   config. The nightly plan is anchored just after it: a periodic worker
     *   with no initial delay fires at whatever time the app happened to be
     *   installed, so a plan built at 15:00 was a plan built halfway through the
     *   day it was for.
     */
    fun schedule(context: Context, dayStartHour: Int = DEFAULT_DAY_START_HOUR) {
        val wm = WorkManager.getInstance(context)

        wm.enqueueUniquePeriodicWork(
            DailyPlanWorker.NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyPlanWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(dayStartHour, 30), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
        )

        wm.enqueueUniquePeriodicWork(
            ExportWorker.NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ExportWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
        )
    }

    /**
     * One reminder a day, at a time the user picked, and only if the day's
     * minimum is still unmet. UPDATE rather than KEEP so changing the time in
     * settings actually moves it.
     *
     * The initial delay only aims the first run; after that WorkManager repeats
     * on a 24-hour interval measured from whenever the previous run actually
     * happened, and Doze can hold a run for hours. Left alone, an 20:00 reminder
     * drifts later every day until it is arriving at midnight. So the worker
     * calls this again on every fire and re-aims at tomorrow's clock time — the
     * drift is reset daily instead of accumulating. UPDATE is safe from inside
     * the running worker: it is applied after the current run finishes, unlike
     * REPLACE, which would cancel the worker doing the asking.
     */
    fun scheduleReminder(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(ReminderWorker.NAME)
            return
        }
        wm.enqueueUniquePeriodicWork(
            ReminderWorker.NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(hour, minute), TimeUnit.MILLISECONDS)
                .build()
        )
    }

    /** Milliseconds from now until the next occurrence of hour:minute. */
    private fun delayUntil(hour: Int, minute: Int): Long {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var target = now
            .withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMillis().coerceAtLeast(0L)
    }

    /** Mirrors governor.json. Only used when a caller has no config to hand. */
    private const val DEFAULT_DAY_START_HOUR = 4
}
