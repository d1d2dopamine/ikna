#!/usr/bin/env python3
"""Deterministic Catalogue v2 target/context selection primitives for Part 10."""
from __future__ import annotations

import hashlib
import re
import unicodedata
from typing import Any, Iterable


def stable_hash(*parts: str) -> str:
    return hashlib.sha256("\0".join(parts).encode("utf-8")).hexdigest()


def normalized_words(text: str) -> list[str]:
    normalized = unicodedata.normalize("NFKC", text).casefold()
    return re.findall(r"[^\W_]+", normalized, flags=re.UNICODE)


def near_duplicate(a: str, b: str, threshold: float = 0.85) -> bool:
    """Conservative token-set near-duplicate test used only for context diversity.

    Exact target identity is never affected. The test suppresses alternate
    contexts that are nearly the same sentence with one small edit.
    """
    wa = normalized_words(a)
    wb = normalized_words(b)
    if wa == wb:
        return True
    if not wa or not wb or abs(len(wa) - len(wb)) > 2:
        return False
    sa, sb = set(wa), set(wb)
    union = sa | sb
    if not union:
        return True
    return len(sa & sb) / len(union) >= threshold


def context_quality_key(context: dict[str, Any]) -> tuple:
    families = tuple(sorted(context.get("sourceFamilies") or ()))
    score = context.get("alignmentScore")
    token_count = int(context.get("tokenCount") or 0)
    # More independent source evidence first; for scored mined corpora prefer
    # stronger alignment. Then prefer a compact but still informative sentence.
    return (
        -len(families),
        -(float(score) if score is not None else 0.0),
        abs(token_count - 10),
        token_count,
        len(str(context.get("context") or "")),
        stable_hash(str(context.get("candidateId") or ""), str(context.get("contextId") or "")),
    )


def choose_contexts(
    contexts: Iterable[dict[str, Any]],
    limit: int,
    near_duplicate_threshold: float,
) -> tuple[list[dict[str, Any]], int]:
    """Choose varied contexts without fabricating or rewriting any text."""
    ordered = sorted(contexts, key=context_quality_key)
    selected: list[dict[str, Any]] = []
    used_families: set[str] = set()
    rejected_keys: set[tuple[str, str]] = set()

    # First pass tries to add source-family diversity where multiple corpora
    # independently support the same target.
    for require_new_source in (True, False):
        for context in ordered:
            if len(selected) >= limit:
                break
            if context in selected:
                continue
            families = set(context.get("sourceFamilies") or ())
            if require_new_source and used_families and not (families - used_families):
                continue
            if any(near_duplicate(context["context"], old["context"], near_duplicate_threshold) for old in selected):
                rejected_keys.add((str(context.get("candidateId") or ""), str(context.get("contextId") or "")))
                continue
            selected.append(context)
            used_families.update(families)
        if len(selected) >= limit:
            break
    return selected, len(rejected_keys)


def target_quality_key(target: dict[str, Any]) -> tuple:
    return (
        int(target["freqRank"]),
        -min(int(target.get("evidenceContexts") or 0), 20),
        -len(target.get("sourceFamilies") or ()),
        target["targetId"],
    )


def select_target_ids(targets: list[dict[str, Any]], budget: int) -> tuple[list[dict[str, Any]], dict[str, int]]:
    """Select targets by usefulness, then morphology-aware diversity.

    Morphology never changes target identity.  When several exact surfaces have
    the same confidently resolved lemma, the first pass gives one slot to the
    best-ranked surface before a second form of that lemma consumes another slot.
    A second pass can still fill unused budget with those additional exact forms.
    """
    ordered = sorted(targets, key=target_quality_key)
    selected: list[dict[str, Any]] = []
    selected_ids: set[str] = set()
    seen_lemmas: set[str] = set()
    stats = {"morphologyDeferred": 0, "budgetRejected": 0}

    for target in ordered:
        lemma = str(target.get("lemmaKey") or target["targetId"])
        if lemma in seen_lemmas:
            stats["morphologyDeferred"] += 1
            continue
        selected.append(target)
        selected_ids.add(target["targetId"])
        seen_lemmas.add(lemma)
        if len(selected) >= budget:
            stats["budgetRejected"] = max(0, len(ordered) - len(selected))
            return selected, stats

    for target in ordered:
        if target["targetId"] in selected_ids:
            continue
        selected.append(target)
        selected_ids.add(target["targetId"])
        if len(selected) >= budget:
            break
    stats["budgetRejected"] = max(0, len(ordered) - len(selected))
    return selected, stats
