#!/usr/bin/env python3
"""Shared, source-independent Catalogue extraction rules.

This module is the stable bridge between the proven Catalogue v1 sieve and the
Catalogue v2 ingestion/output architecture.  It deliberately contains no corpus
I/O and no release-format code: both builders may use the same segmentation,
limits, level cuts, token classification and UTF-16 span rules without v2
importing the v1 builder as an implementation detail.
"""
from __future__ import annotations

from segmentation import CJK, word_spans, utf16_length, utf16_offset

LEARNABLE = ["en", "ru", "pl", "es", "fr", "de", "it", "pt", "zh", "ja", "ko"]
MEANINGS = list(LEARNABLE)
NAMES = {
    "en": "English", "ru": "Russian", "pl": "Polish", "es": "Spanish",
    "fr": "French", "de": "German", "it": "Italian", "pt": "Portuguese",
    "zh": "Chinese", "ja": "Japanese", "ko": "Korean",
}

MAX_PHRASE = 80
MAX_SENTENCE = 300
MAX_TRANSLATION = 160
MIN_SENTENCE = 12
MIN_PHRASE = 2
FUNCTION_TOP = 60
LEVEL_BEGINNER = 1500
LEVEL_MIDDLE = 5000
LEVELS = ["beginner", "middle", "advanced"]
SOURCE_MARK = "\n\u2014 "


def words(text: str, lang: str | None = None) -> list[str]:
    """Written surfaces in source order; punctuation is never a taught word."""
    return [span.surface for span in word_spans(text, lang)]


def minimum_phrase(lang: str | None) -> int:
    return 1 if lang in CJK else MIN_PHRASE


def token_list(
    sentence: str,
    ranks: dict[str, int],
    forms: dict[str, str] | None = None,
    lang: str | None = None,
    function_top: int = FUNCTION_TOP,
) -> list[dict]:
    """Catalogue token representation, kept byte-for-field compatible with v1."""
    forms = forms or {}
    out = []
    for surface in words(sentence, lang):
        low = surface.lower()
        rank = ranks.get(low, 10 ** 9)
        content = rank > function_top and len(surface) >= minimum_phrase(lang)
        out.append(
            {
                "surface": surface,
                "lemma": forms.get(low, low),
                "pos": "WORD" if content else "FUNC",
                "isContent": content,
            }
        )
    return out


def offsets(sentence: str, surface: str, lang: str | None = None) -> tuple[int, int] | None:
    """Resolve one complete token and return the span in UTF-16 code units."""
    found = [span for span in word_spans(sentence, lang) if span.surface == surface]
    if len(found) != 1:
        return None
    span = found[0]
    return utf16_offset(sentence, span.start), utf16_offset(sentence, span.end)


def level_of(rank: int) -> str:
    if rank <= LEVEL_BEGINNER:
        return "beginner"
    if rank <= LEVEL_MIDDLE:
        return "middle"
    return "advanced"
