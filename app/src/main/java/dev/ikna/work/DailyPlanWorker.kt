package dev.ikna.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.ikna.IknaApp

/** Nightly: recompute due counts, ask the governor, introduce what it allows. */
class DailyPlanWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as IknaApp).container
        return runCatching {
            container.packLoader.installBundledPacks()
            container.learningRepository.runDailyPlan()
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object { const val NAME = "ikna-daily-plan" }
}
