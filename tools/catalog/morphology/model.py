#!/usr/bin/env python3
"""Small shared rules for deterministic Catalogue v2 morphology."""

from __future__ import annotations

import hashlib
import unicodedata

MORPHOLOGY_RULE_VERSION = 1
MORPHOLOGY_POLICY = "ud-exact-context-then-unanimous-form"


def normalized_text(text: str) -> str:
    """NFKC + whitespace normalization without erasing sentence case."""
    return " ".join(unicodedata.normalize("NFKC", text).split())


def canonical_text(text: str) -> str:
    """NFKC + whitespace normalization + case-folding for form lookup."""
    return normalized_text(text).casefold()


def surface_key(text: str) -> str:
    return canonical_text(text)


def context_key(text: str) -> str:
    normalized = normalized_text(text)
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()[:32]


def canonical_feats(value: str | None) -> str | None:
    """Return canonical CoNLL-U FEATS ordering, or None for unknown/invalid."""
    if value is None:
        return None
    value = value.strip()
    if not value or value == "_":
        return None
    parts = []
    for raw in value.split("|"):
        raw = raw.strip()
        if not raw or "=" not in raw:
            return None
        name, item = raw.split("=", 1)
        name = name.strip()
        item = item.strip()
        if not name or not item:
            return None
        parts.append((name, item))
    parts.sort(key=lambda pair: (pair[0], pair[1]))
    return "|".join("%s=%s" % pair for pair in parts)
