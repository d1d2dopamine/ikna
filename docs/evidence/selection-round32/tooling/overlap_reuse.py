"""Experimental exact call-local overlap reuse; never installed in production.

Import the untouched tools/catalog directory before importing this module. The
only candidate change is incremental evaluation of max overlap with selected
contexts. All other ordering, equality, source and rejection policy is retained.
Inputs and deterministic policy/segmentation functions must remain unchanged for
the duration of a call. Existing token LRU hit counts and recency will differ.
"""
from __future__ import annotations

from typing import Any, Iterable

from selection_policy import context_quality_key, context_overlap, near_duplicate


def choose_contexts(
    contexts: Iterable[dict[str, Any]],
    limit: int,
    near_duplicate_threshold: float,
    *, diverse: bool = False, lang: str | None = None,
) -> tuple[list[dict[str, Any]], int]:
    """Original policy with an O(N), call-local, lazy maximum per object identity.

    selected is append-only. An entry (k, maximum) has evaluated exactly the
    selected[:k] prefix in order. Later ranking extends that prefix once instead
    of recomputing it. Entries survive the policy's two passes, but never a call.
    Dictionary equality/removal remains the original policy's responsibility;
    object identity is used solely to index independent memoization state.
    """
    ordered = sorted(contexts, key=context_quality_key)
    quality_keys = {id(context): context_quality_key(context) for context in ordered} if diverse else {}
    selected: list[dict[str, Any]] = []
    used_families: set[str] = set()
    rejected_keys: set[tuple[str, str]] = set()
    maxima: dict[int, tuple[int, float]] = {}

    def maximum_overlap(context: dict[str, Any]) -> float:
        identity = id(context)
        seen, maximum = maxima.get(identity, (0, 0.0))
        count = len(selected)
        if seen == count:
            return maximum
        while seen < count:
            overlap = context_overlap(context["context"], selected[seen]["context"], lang)
            # Same left-to-right maximum as the original generator, including
            # retaining the first equal value. No rounding or approximation.
            if seen == 0 or overlap > maximum:
                maximum = overlap
            seen += 1
        maxima[identity] = (count, maximum)
        return maximum

    for require_new_source in (True, False):
        remaining = list(ordered)
        while remaining:
            if require_new_source and used_families:
                remaining = [c for c in remaining if set(c.get("sourceFamilies") or ()) - used_families]
                if not remaining:
                    break
            if diverse and selected:
                if not lang:
                    raise ValueError("diverse contexts require a learning language")
                context = min(remaining, key=lambda c: (
                    quality_keys[id(c)][:2],
                    maximum_overlap(c),
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
