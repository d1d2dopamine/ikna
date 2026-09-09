package dev.ikna.ui.text

import java.util.Locale

enum class QuantityRule { SLAVIC, ONE_OTHER }

/** One source of truth for every interface language on every target. */
data class UiLanguage(
    val code: String,
    val nativeLabel: String,
    val strings: Map<String, String>,
    val quantityRule: QuantityRule
)

val UI_LANGUAGES: List<UiLanguage> = listOf(
    UiLanguage(LANG_RU, "РУССКИЙ", STRINGS_RU, QuantityRule.SLAVIC),
    UiLanguage(LANG_EN, "ENGLISH", STRINGS_EN, QuantityRule.ONE_OTHER),
    UiLanguage(LANG_PL, "POLSKI", STRINGS_PL, QuantityRule.SLAVIC),
    UiLanguage(LANG_ES, "ESPAÑOL", STRINGS_ES, QuantityRule.ONE_OTHER),
    UiLanguage(LANG_FR, "FRANÇAIS", STRINGS_FR, QuantityRule.ONE_OTHER),
    UiLanguage(LANG_DE, "DEUTSCH", STRINGS_DE, QuantityRule.ONE_OTHER),
    UiLanguage(LANG_PT, "PORTUGUÊS (BRASIL)", STRINGS_PT, QuantityRule.ONE_OTHER)
)

private val UI_LANGUAGES_BY_CODE = UI_LANGUAGES.associateBy { it.code }

fun uiLanguage(code: String): UiLanguage? =
    UI_LANGUAGES_BY_CODE[code.lowercase(Locale.ROOT)]

fun uiLanguageLabel(code: String): String? = uiLanguage(code)?.nativeLabel
