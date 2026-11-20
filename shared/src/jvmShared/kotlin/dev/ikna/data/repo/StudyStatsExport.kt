package dev.ikna.data.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

enum class StatsExportFormat(val extension: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    JSON("json", "application/json")
}

@Serializable
internal data class StatsExportAnswer(val time: String, val target: String, val level: Int, val rating: Int)

@Serializable
internal data class StatsExportReport(
    val from: String?,
    val until: String,
    val zone: String,
    val dayStartHour: Int,
    val targets: Int,
    val firstRecordedTargets: Int,
    val answers: Int,
    val repeatAnswers: Int,
    val recalledRepeatAnswers: Int,
    val unclassifiedAnswers: Int,
    val reviews: List<StatsExportAnswer>
) {
    fun render(format: StatsExportFormat): String = when (format) {
        StatsExportFormat.JSON -> Json { prettyPrint = true; encodeDefaults = true }.encodeToString(serializer(), this)
        StatsExportFormat.CSV -> buildString {
            appendLine("type,key,value,time,target,level,rating")
            val summary = linkedMapOf(
                "from" to from.orEmpty(), "until" to until, "zone" to zone,
                "day_start_hour" to dayStartHour.toString(), "targets" to targets.toString(),
                "first_recorded_targets" to firstRecordedTargets.toString(), "answers" to answers.toString(),
                "repeat_answers" to repeatAnswers.toString(), "recalled_repeat_answers" to recalledRepeatAnswers.toString(),
                "unclassified_answers" to unclassifiedAnswers.toString()
            )
            summary.forEach { (key, value) -> appendLine("summary,$key,${csvCell(value)},,,,") }
            reviews.forEach { row ->
                appendLine("review,,,${csvCell(row.time)},${csvCell(row.target)},${row.level},${row.rating}")
            }
        }
    }
}

internal fun exportTime(value: Long): String = Instant.ofEpochMilli(value).toString()

private fun csvCell(value: String): String {
    val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
    return "\"" + safe.replace("\"", "\"\"") + "\""
}
