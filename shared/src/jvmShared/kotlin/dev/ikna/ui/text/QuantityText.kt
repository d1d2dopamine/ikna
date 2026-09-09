package dev.ikna.ui.text

/** Selects the visible noun form without applying Slavic rules to every language. */
fun quantityWord(
    count: Long,
    teenKey: String,
    oneKey: String,
    fewKey: String,
    manyKey: String
): String {
    if (S.lang != LANG_RU && S.lang != LANG_PL) {
        return S.t(if (count == 1L) oneKey else manyKey)
    }
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11L..14L -> S.t(teenKey)
        mod10 == 1L -> S.t(oneKey)
        mod10 in 2L..4L -> S.t(fewKey)
        else -> S.t(manyKey)
    }
}
