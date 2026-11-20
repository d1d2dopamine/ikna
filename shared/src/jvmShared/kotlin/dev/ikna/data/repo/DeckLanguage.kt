package dev.ikna.data.repo

import com.optimaize.langdetect.LanguageDetectorBuilder
import com.optimaize.langdetect.ngram.NgramExtractors
import com.optimaize.langdetect.profiles.LanguageProfileReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val deckLanguageDetector by lazy {
    LanguageDetectorBuilder.create(NgramExtractors.standard())
        .withProfiles(LanguageProfileReader().readAllBuiltIn())
        .build()
}

/** A short or inconsistent deck remains unclassified. Translation text is excluded. */
internal suspend fun detectDeckLanguage(sentences: List<String>): String? = withContext(Dispatchers.Default) {
    val usable = sentences.asSequence().distinct().filter { line -> line.count(Char::isLetter) >= 15 }.toList()
    if (usable.size < 3) return@withContext null
    val sample = List(minOf(usable.size, 12)) { index ->
        usable[index * usable.size / minOf(usable.size, 12)].take(400)
    }
    if (sample.sumOf { it.count(Char::isLetter) } < 100) return@withContext null
    fun match(text: String, confidence: Double): String? =
        deckLanguageDetector.getProbabilities(text).firstOrNull()
            ?.takeIf { it.probability >= confidence }?.locale?.language
    val overall = match(sample.joinToString(" "), 0.95) ?: return@withContext null
    val reliable = sample.mapNotNull { match(it, 0.98) }
    overall.takeIf { reliable.count { code -> code != overall } <= 1 }
}
