"""Test-only frozen chooser from the pre-optimization selection policy.

Never imported by production. Retained explicitly so differential tests continue
comparing independent algorithms after production chooser integration. Shared
unchanged deterministic quality/overlap/near-duplicate helpers are intentional.

Original path: tools/catalog/selection_policy.py
Original complete module SHA-256: 6100810b5bd7eca23b063f90dd0c1e81cb3a7f55ba3aeab76f02be84720fb3ae
Exact original chooser source SHA-256: 54f69e0c45fa4426d4a8dc563bd5b6055d5fd0a8d061a2c6f0f072e50c2e098c
Captured from the completed Round30/31 control source before Round32 reuse.
"""
from __future__ import annotations
from typing import Any, Iterable
from selection_policy import context_quality_key, context_overlap, near_duplicate

SOURCE_POLICY_SHA256 = "6100810b5bd7eca23b063f90dd0c1e81cb3a7f55ba3aeab76f02be84720fb3ae"
FROZEN_CHOOSER_SHA256 = "54f69e0c45fa4426d4a8dc563bd5b6055d5fd0a8d061a2c6f0f072e50c2e098c"


def choose_contexts(
    contexts: Iterable[dict[str, Any]],
    limit: int,
    near_duplicate_threshold: float,
    *, diverse: bool = False, lang: str | None = None,
) -> tuple[list[dict[str, Any]], int]:
    """Choose varied contexts without fabricating or rewriting any text."""
    ordered = sorted(contexts, key=context_quality_key)
    quality_keys = {id(context): context_quality_key(context) for context in ordered} if diverse else {}
    selected: list[dict[str, Any]] = []
    used_families: set[str] = set()
    rejected_keys: set[tuple[str, str]] = set()

    # First pass tries to add source-family diversity where multiple corpora
    # independently support the same target.
    for require_new_source in (True, False):
        remaining = list(ordered)
        while remaining:
            if require_new_source and used_families:
                remaining = [c for c in remaining if set(c.get("sourceFamilies") or ()) - used_families]
                if not remaining:
                    break
            # Keep source/align evidence ahead of diversity. Primary is the
            # unchanged best evidence. Similar alternatives are reordered, not
            # forbidden: a grammatical variation can fill a scarce target.
            if diverse and selected:
                if not lang:
                    raise ValueError("diverse contexts require a learning language")
                context = min(remaining, key=lambda c: (
                    quality_keys[id(c)][:2],
                    max(context_overlap(c["context"], old["context"], lang) for old in selected),
                    quality_keys[id(c)][2:]))
                remaining.remove(context)
            else:
                context = remaining.pop(0)
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
