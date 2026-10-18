package dev.ikna.data.export

import java.security.MessageDigest

/** Full-journal normalization. Numeric database keys never cross the identity boundary. */
object JournalIdentity {
    fun canonical(record: ReviewRecord): ReviewRecord = record.copy(id = 0, undoOf = null)

    fun normalize(records: List<ReviewRecord>): List<ReviewRecord> {
        val byRow = records.filter { it.id != 0L }.groupBy { it.id }
        val resolved = HashMap<ReviewRecord, ReviewRecord>()
        val visiting = HashSet<ReviewRecord>()
        fun resolve(row: ReviewRecord): ReviewRecord {
            resolved[row]?.let { return it }
            require(visiting.add(row)) { "Cyclic undo history" }
            val target = row.undoEventId ?: row.undoOf?.let { id ->
                val candidates = byRow[id].orEmpty().distinct()
                require(candidates.size == 1) { "Ambiguous or missing legacy undo target" }
                resolve(candidates.single()).eventId
            }
            val payload = canonical(row).copy(eventId = null, undoEventId = target)
            val event = row.eventId ?: "legacy:" + MessageDigest.getInstance("SHA-256")
                .digest(ReviewRecord.json.encodeToString(ReviewRecord.serializer(), payload).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            require(event.matches(Regex("[A-Za-z0-9:_-]{1,128}"))) { "Invalid event identity" }
            val normalized = canonical(row).copy(eventId = event, undoEventId = target)
            visiting.remove(row)
            resolved[row] = normalized
            return normalized
        }
        val normalized = records.map(::resolve)
        // Identical old rows cannot honestly be assigned portable multiplicity.
        val legacy = records.zip(normalized).filter { it.first.eventId == null }
        require(legacy.groupBy { it.second.eventId }.values.none { group ->
            group.map { it.first.id }.distinct().size > 1
        }) { "Indistinguishable legacy reviews: preserve originals; synchronization requires history reconciliation" }
        require(records.zip(normalized).groupBy { it.second.eventId }.values.none { group ->
            group.map { it.first.id }.filter { it != 0L }.distinct().size > 1
        }) { "Duplicate portable identity in local history: reconciliation required" }
        return normalized
    }
}
