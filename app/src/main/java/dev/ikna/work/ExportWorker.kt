package dev.ikna.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.ikna.IknaApp

/**
 * Weekly safety net. See JsonExporter for why it writes outside the sandbox.
 * Honours the auto-export switch in settings — the switch has to actually stop it,
 * or it is a lie.
 */
class ExportWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as IknaApp).container
        if (!container.settings.current().autoExport) return Result.success()
        return runCatching { container.jsonExporter.export() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object { const val NAME = "ikna-weekly-export" }
}
