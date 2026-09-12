#!/usr/bin/env python3
"""Parity checks for mature Catalogue v1 selection primitives used by v2."""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import build_catalog as v1
import catalogue_core as core
import build_catalogue_v2 as v2


def main() -> int:
    for name in (
        "LEARNABLE", "MEANINGS", "NAMES", "MAX_PHRASE", "MAX_SENTENCE",
        "MAX_TRANSLATION", "MIN_SENTENCE", "MIN_PHRASE", "FUNCTION_TOP",
        "LEVEL_BEGINNER", "LEVEL_MIDDLE", "LEVELS", "SOURCE_MARK",
    ):
        assert getattr(core, name) == getattr(v1, name), name

    samples = {
        "en": "She stayed home to take care of the children.",
        "ru": "Она осталась дома, чтобы заботиться о детях.",
        "pl": "Została w domu, żeby opiekować się dziećmi.",
        "es": "Se quedó en casa para cuidar de los niños.",
        "fr": "Elle est restée chez elle pour s'occuper des enfants.",
        "de": "Sie blieb zu Hause, um sich um die Kinder zu kümmern.",
        "it": "È rimasta a casa per prendersi cura dei bambini.",
        "pt": "Ela ficou em casa para cuidar das crianças.",
        "zh": "她留在家里照顾孩子。",
        "ja": "彼女は子供たちの世話をするため家に残った。",
        "ko": "그녀는 아이들을 돌보기 위해 집에 남았다.",
    }
    # ICU may be unavailable on a developer machine. The dedicated segmentation
    # test covers CJK there; parity below uses whichever languages can tokenize.
    for lang, sentence in samples.items():
        try:
            old_words = v1.words(sentence, lang)
            new_words = core.words(sentence, lang)
        except Exception:
            if lang in {"zh", "ja"}:
                continue
            raise
        assert new_words == old_words, (lang, old_words, new_words)
        ranks = {word.lower(): i + 61 for i, word in enumerate(old_words)}
        assert core.token_list(sentence, ranks, {}, lang) == v1.token_list(sentence, ranks, {}, lang), lang
        for surface in old_words:
            assert core.offsets(sentence, surface, lang) == v1.offsets(sentence, surface, lang), (lang, surface)

    for rank in (1, 1500, 1501, 5000, 5001, 50000):
        assert core.level_of(rank) == v1.level_of(rank)


    # Production v1 does not download optional Wiktextract forms, so the
    # selection parity that matters for the published catalogue is exact written
    # form identity. v2 may skip an already selected target and choose the next
    # rarest eligible token, exactly as v1 skips an already-used identity lemma.
    sentence = "alpha beta gamma delta"
    ranks = {"alpha": 100, "beta": 400, "gamma": 300, "delta": 200}
    old = v1.pick_phrase(sentence, ranks, {}, set(), "en")
    choices = v2.phrase_choices(sentence, ranks, "en", v1.FUNCTION_TOP)
    assert old is not None and choices
    assert (choices[0][1], choices[0][0]) == (old[0], old[1])
    used = {old[2]}
    old_next = v1.pick_phrase(sentence, ranks, {}, used, "en")
    remaining = [choice for choice in choices if choice[1].lower() not in used]
    assert old_next is not None and remaining
    assert (remaining[0][1], remaining[0][0]) == (old_next[0], old_next[1])

    # Equal ranks keep source order, matching v1's strict `rank > best` update.
    tied = {"alpha": 100, "beta": 200, "gamma": 200, "delta": 100}
    old_tied = v1.pick_phrase(sentence, tied, {}, set(), "en")
    new_tied = v2.phrase_choices(sentence, tied, "en", v1.FUNCTION_TOP)
    assert old_tied is not None and new_tied[0][1] == old_tied[0] == "beta"
    print("Catalogue v1/v2 primitive parity: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
