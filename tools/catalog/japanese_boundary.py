"""Offline Japanese target-boundary evidence, not stemming or target identity."""
from __future__ import annotations

from bisect import bisect_right
import hashlib
from importlib import metadata
from pathlib import Path

from catalogue_v2 import canonical_target
from segmentation import word_spans
from selection_policy import filter_unique_choices

ENGINE_VERSION = "0.6.10"
DICTIONARY_VERSION = "20250825"
# Filled from the pinned dictionary wheel, not a moving dictionary download.
DICTIONARY_SHA256 = "d28ffc33b196e5c2ca731e8147fd1ef47d35ba79928ef9f0871962859d70ac23"


def filter_boundaries(text, choices, units):
    """Keep original ICU targets only when neither boundary cuts a morpheme.

    A target may cover several complete units (e.g. a compound). Exact surface,
    frequency and targetId are never rewritten. Ambiguous/OOV cuts are deferred
    just like known cuts, with a different reason, while originals stay in pool.
    """
    units = [(a, b, oov) for a, b, oov in units if a != b]
    if any(not 0 <= a < b <= len(text) for a, b, _ in units):
        raise ValueError("Japanese analyzer returned an invalid source range")
    if any(units[i][1] > units[i + 1][0] for i in range(len(units) - 1)):
        raise ValueError("Japanese analyzer returned overlapping source ranges")
    starts = [a for a, _, _ in units]
    spans = word_spans(text, "ja")
    by_form = {}
    for span in spans:
        by_form.setdefault(canonical_target(span.surface), []).append(span)
    unique, rejected, examples = filter_unique_choices(text, choices, "ja")
    kept = []
    for choice in unique:
        matches = by_form.get(canonical_target(choice[1]), [])
        if len(matches) != 1:
            raise ValueError("Japanese choice is not one complete ICU token")
        span = matches[0]
        cuts = []
        for boundary in (span.start, span.end):
            i = bisect_right(starts, boundary) - 1
            if i >= 0 and units[i][0] < boundary < units[i][1]:
                cuts.append(units[i])
        if cuts:
            reason = "ja-oov-boundary-deferred" if any(oov for _, _, oov in cuts) else "ja-morpheme-cut-deferred"
            rejected[reason] += 1
            examples.append({"targetId": choice[2], "text": choice[1], "reason": reason,
                             "context": text, "containingUnits": [text[a:b] for a, b, _ in cuts]})
        else:
            # A morpheme analyzer must actually cover the target. Missing source
            # coverage is a tooling failure, never a character-splitting fallback.
            covered = sum(max(0, min(span.end, b) - max(span.start, a)) for a, b, _ in units)
            if covered != span.end - span.start:
                raise ValueError("Japanese analyzer does not cover target source range")
            kept.append(choice)
    return kept, rejected, examples


class JapaneseBoundaryGuard:
    def __init__(self):
        try:
            import sudachidict_core
            from sudachipy import dictionary, tokenizer
        except ImportError as error:
            raise ValueError("Japanese quality policy needs the pinned offline Sudachi dependencies; use its workflow") from error
        versions = {"SudachiPy": metadata.version("SudachiPy"), "SudachiDict-core": metadata.version("SudachiDict-core")}
        if versions != {"SudachiPy": ENGINE_VERSION, "SudachiDict-core": DICTIONARY_VERSION}:
            raise ValueError("Japanese quality dependency versions differ from the pinned policy")
        path = Path(sudachidict_core.__file__).parent / "resources/system.dic"
        with path.open("rb") as handle:
            digest = hashlib.file_digest(handle, "sha256").hexdigest()
        if digest != DICTIONARY_SHA256:
            raise ValueError("Japanese quality dictionary SHA-256 mismatch")
        self.dictionary = dictionary.Dictionary(dict="core")
        self.tokenizer = self.dictionary.create()
        self.mode = tokenizer.Tokenizer.SplitMode.A
        self.evidence = {"engine": "SudachiPy", "version": ENGINE_VERSION, "dictionary": "SudachiDict-core",
                         "dictionaryVersion": DICTIONARY_VERSION, "dictionarySha256": digest, "splitMode": "A",
                         "licence": "Apache-2.0", "purpose": "boundary validation only; no lemma/normalization/identity rewrite"}

    def filter(self, text, choices):
        units = [(m.begin(), m.end(), m.is_oov()) for m in self.tokenizer.tokenize(text, self.mode)]
        return filter_boundaries(text, choices, units)

    def close(self):
        self.dictionary.close()
