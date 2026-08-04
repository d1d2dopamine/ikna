package dev.ikna.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.ikna.IknaApp

/** Weekly safety net. See JsonExporter for why it writes outside the sandbox. */
class ExportWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        runCatching { (applicationContext as IknaApp).container.jsonExporter.export() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })

    companion object { const val NAME = "ikna-weekly-export" }
}
