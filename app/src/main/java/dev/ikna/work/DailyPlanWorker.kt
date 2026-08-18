package dev.ikna.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.ikna.IknaApp
import dev.ikna.widget.TodayWidget

/** Nightly: recompute due counts, ask the governor, introduce what it allows. */
class DailyPlanWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as IknaApp).container
        return runCatching {
            container.awaitSchedulerReady()
            container.packLoader.installBundledPacks()
            container.learningRepository.runDailyPlan()

            // The widget is written by the deck list, which is the one place
            // that knows both the count and the user's language. That left it
            // showing yesterday's number every morning until the app was opened
            // - on a widget whose entire purpose is to be read instead of
            // opening the app. The number is refreshed here; the words around it
            // stay as the deck list last wrote them, because a worker has no
            // business loading the string catalogue.
            val remaining = container.learningRepository.remainingByDeck().values.sum()
            TodayWidget.publishCount(applicationContext, remaining)
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object { const val NAME = "ikna-daily-plan" }
}
