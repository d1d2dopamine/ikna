package dev.ikna.desktop.grading

import dev.ikna.data.export.ReviewRecord
import dev.ikna.domain.grading.GradingEvaluator
import java.io.File

/** Explicit local input only. The normal app does not invoke or scan for this. */
fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: GradingLab <input.jsonl> <report.txt>" }
    val input = File(args[0]).canonicalFile
    val output = File(args[1]).canonicalFile
    require(input != output) { "The report must not overwrite the source log" }
    require(input.isFile && input.length() <= 100L * 1024L * 1024L) {
        "Input must be an existing JSONL file of at most 100 MiB"
    }
    val records = ArrayList<ReviewRecord>()
    input.bufferedReader().useLines { lines ->
        lines.forEachIndexed { index, line ->
            if (line.isNotBlank()) {
                require(line.length <= 65_536 && records.size < 200_000) { "Input limit exceeded at line ${index + 1}" }
                records += try {
                    ReviewRecord.json.decodeFromString(ReviewRecord.serializer(), line)
                } catch (error: Exception) {
                    // Do not echo personal card contents in a terminal error.
                    throw IllegalArgumentException("Invalid review JSON at line ${index + 1}", error)
                }
            }
        }
    }
    val report = GradingEvaluator.evaluate(records).asText()
    output.parentFile?.mkdirs()
    output.writeText(report)
    println(report)
}
