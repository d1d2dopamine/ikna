package dev.ikna.domain.fsrs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Exact parameters of one answer; never a pointer to a mutable local fit. */
@Serializable
data class FsrsSnapshot(val formatVersion: Int, val modelVersion: Int,
    val weights: List<Double>, val desiredRetention: Double) {
    fun isValid(): Boolean = formatVersion == 1 && modelVersion == 6 &&
        weights.size == FsrsParams.PARAMETER_COUNT &&
        weights.withIndex().all { (i, w) -> w.isFinite() && w in FsrsOptimizer.BOUNDS[i] } &&
        desiredRetention.isFinite() && desiredRetention > 0 && desiredRetention < 1
    fun parameters(): FsrsParams {
        require(isValid()) { "Invalid or unsupported FSRS parameters" }
        return FsrsParams(weights.toList(), desiredRetention)
    }
    companion object { fun of(p: FsrsParams) = FsrsSnapshot(1, 6, p.w.toList(), p.desiredRetention) }
}
object FsrsSnapshotCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    fun encode(params: FsrsParams): String {
        val snapshot = FsrsSnapshot.of(params)
        require(snapshot.isValid())
        return json.encodeToString(FsrsSnapshot.serializer(), snapshot)
    }
    fun decode(text: String): FsrsParams {
        require(text.length <= 8192)
        return json.decodeFromString(FsrsSnapshot.serializer(), text).parameters()
    }
}
/** One shared cap for production scheduling AND fitting replay. */
fun boundDerivedMemoryV1(good: MemoryState, raw: MemoryState, rating: Rating): MemoryState {
    require(rating == Rating.HARD || rating == Rating.EASY)
    val low = if (rating == Rating.HARD) 0.7 else 1.0
    val high = if (rating == Rating.EASY) 1.3 else 1.0
    return raw.copy(stability = raw.stability.coerceIn(good.stability * low, good.stability * high))
}
