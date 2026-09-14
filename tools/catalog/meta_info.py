#!/usr/bin/env python3
"""Inspect an ikna catalogue without changing it.

The report is deliberately both human-readable and machine-readable.  It keeps
"cards in decks" (target/deck memberships), unique learning targets and retained
source contexts separate so a catalogue can be understood without knowing the
storage model first.

Only the Python standard library is used.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import heapq
import json
import math
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


SOURCE_RE = re.compile(r"(?:^|\n)\u2014\s*Tatoeba\s+#(\d+)\s*$")
DECK_RE = re.compile(
    r"^(?P<lang>[a-z]{2})-(?P<meaning>[a-z]{2})-"
    r"(?:(?P<collection>everyday|knowledge|world)-)?"
    r"(?P<level>beginner|middle|advanced)(?P<pd>-pd)?\.jsonl(?:\.gz)?$"
)
REQUIRED_FIELDS = (
    "id",
    "text",
    "context",
    "translation",
    "targetStart",
    "targetEnd",
    "freqRank",
    "tokens",
)
V2_CARD_FIELDS = ("targetId", "contextId", "meaningId", "sourceFamily")
CONTEXT_FIELDS = ("context", "translation", "targetStart", "targetEnd", "freqRank", "tokens")
V2_CONTEXT_FIELDS = ("contextId", "meaningId", "sourceFamily")
TOKEN_FIELDS = ("surface", "lemma", "pos", "isContent")
OPTIONAL_FIELDS = ("ipa", "ipaContext")
LONG_TARGET_CHARS = 80
LONG_CONTEXT_CHARS = 500
RAW_CLIENT_CAP_BYTES = 24 * 1024 * 1024


def normalized(text: str) -> str:
    """Convenience normalization for diagnostics, not Catalogue target identity."""
    return " ".join(unicodedata.normalize("NFKC", text).strip().casefold().split())


def exact_target_key(text: str) -> str:
    """Catalogue v2 exact identity: NFKC + Unicode case-folding, nothing else."""
    return unicodedata.normalize("NFKC", text).casefold()


def expected_target_id(language: str, text: str, version: int = 2) -> str:
    lang = language.strip().lower()
    canonical = exact_target_key(text)
    if not lang or not canonical:
        return ""
    digest = hashlib.sha256((lang + "\0" + canonical).encode("utf-8")).hexdigest()[:16]
    return f"t{version}:{lang}:{digest}"


def utf16_slice(text: str, start: int, end: int) -> str:
    raw = text.encode("utf-16-le")
    return raw[start * 2 : end * 2].decode("utf-16-le")


def fallback_context_key(context: str) -> str:
    digest = hashlib.sha256(normalized(context).encode("utf-8")).hexdigest()[:20]
    return "text:" + digest


def source_id(translation: str) -> str | None:
    match = SOURCE_RE.search(translation)
    return match.group(1) if match else None


def deck_from_filename(path: Path) -> dict[str, str]:
    match = DECK_RE.match(path.name)
    if not match:
        return {
            "id": path.name.removesuffix(".gz").removesuffix(".jsonl"),
            "lang": "?",
            "meaningLang": "?",
            "level": "?",
            "collection": "legacy",
            "sourceFamily": "legacy",
        }
    return {
        "id": path.name.removesuffix(".gz").removesuffix(".jsonl"),
        "lang": match.group("lang"),
        "meaningLang": match.group("meaning"),
        "level": match.group("level"),
        "collection": match.group("collection") or "legacy",
        "sourceFamily": "legacy",
    }


def open_deck(path: Path):
    if path.name.endswith(".gz"):
        return gzip.open(path, "rt", encoding="utf-8")
    return path.open(encoding="utf-8")


def load_index(root: Path) -> tuple[dict[str, dict[str, Any]], dict[str, Any] | None]:
    path = root / "index.json"
    if not path.exists():
        return {}, None
    try:
        index = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise SystemExit(f"cannot read {path}: {error}")
    by_file = {
        item["file"]: item
        for item in index.get("decks", [])
        if isinstance(item, dict) and item.get("file")
    }
    return by_file, index


def percentile(values: list[int], fraction: float) -> int | float:
    if not values:
        return 0
    ordered = sorted(values)
    if fraction == 0.5:
        middle = len(ordered) // 2
        if len(ordered) % 2:
            return ordered[middle]
        return (ordered[middle - 1] + ordered[middle]) / 2.0
    rank = max(1, math.ceil(fraction * len(ordered)))
    return ordered[min(len(ordered) - 1, rank - 1)]


def fmt_number(value: int | float) -> str:
    if isinstance(value, float) and not value.is_integer():
        return f"{value:,.1f}"
    return f"{int(value):,}"


def compact_sample(deck: dict[str, Any], card: dict[str, Any]) -> dict[str, Any]:
    contexts = []
    for item in [card] + [row for row in (card.get("contexts") or []) if isinstance(row, dict)]:
        contexts.append(
            {
                "contextId": item.get("contextId"),
                "meaningId": item.get("meaningId"),
                "sourceFamily": item.get("sourceFamily"),
                "context": item.get("context"),
                "translation": item.get("translation"),
                "targetStart": item.get("targetStart"),
                "targetEnd": item.get("targetEnd"),
                "freqRank": item.get("freqRank"),
            }
        )
    return {
        "deckId": deck.get("id"),
        "lang": deck.get("lang"),
        "meaningLang": deck.get("meaningLang"),
        "collection": deck.get("collection"),
        "level": deck.get("level"),
        "cardId": card.get("id"),
        "targetId": card.get("targetId"),
        "text": card.get("text"),
        "contexts": contexts,
    }


@dataclass
class TargetGroup:
    lang: str
    display: str
    cards: int = 0
    source_keys: set[str] = field(default_factory=set)
    source_ids: set[str] = field(default_factory=set)
    contexts: dict[str, str] = field(default_factory=dict)
    decks: set[str] = field(default_factory=set)
    meanings: set[str] = field(default_factory=set)
    levels: set[str] = field(default_factory=set)


@dataclass
class Stats:
    cards: int = 0
    contexts: int = 0
    files: int = 0
    parse_errors: int = 0
    empty_lines: int = 0
    duplicate_card_ids: int = 0
    duplicate_target_context_in_deck: int = 0
    offsets_bad: int = 0
    provenance_missing: int = 0
    ipa_cards: int = 0
    ipa_context_cards: int = 0
    tokens: int = 0
    lemma_informative: int = 0
    lemma_missing: int = 0
    content_tokens: int = 0
    empty_targets: int = 0
    long_targets: int = 0
    long_contexts: int = 0
    target_id_mismatches: int = 0
    context_namespace_mismatches: int = 0
    meaning_namespace_mismatches: int = 0
    field_present: Counter = field(default_factory=Counter)
    required_missing: Counter = field(default_factory=Counter)
    context_required_missing: Counter = field(default_factory=Counter)
    token_required_missing: Counter = field(default_factory=Counter)
    pos_values: Counter = field(default_factory=Counter)
    per_language_cards: Counter = field(default_factory=Counter)
    per_level_cards: Counter = field(default_factory=Counter)
    per_pair_cards: Counter = field(default_factory=Counter)
    per_collection_cards: Counter = field(default_factory=Counter)
    per_source_family_cards: Counter = field(default_factory=Counter)
    per_language_contexts: Counter = field(default_factory=Counter)
    per_level_contexts: Counter = field(default_factory=Counter)
    per_pair_contexts: Counter = field(default_factory=Counter)
    per_collection_contexts: Counter = field(default_factory=Counter)
    per_source_family_contexts: Counter = field(default_factory=Counter)
    per_language_decks: Counter = field(default_factory=Counter)
    per_level_decks: Counter = field(default_factory=Counter)
    per_pair_decks: Counter = field(default_factory=Counter)
    per_collection_decks: Counter = field(default_factory=Counter)
    per_source_family_decks: Counter = field(default_factory=Counter)
    tokens_by_language: Counter = field(default_factory=Counter)
    informative_lemma_by_language: Counter = field(default_factory=Counter)
    missing_lemma_by_language: Counter = field(default_factory=Counter)


def _analyse(
    root: Path,
    sample_per_deck: int = 0,
) -> tuple[dict[str, Any], list[TargetGroup], dict[str, list[dict[str, Any]]]]:
    deck_index, index = load_index(root)
    all_paths = sorted([*root.glob("*.jsonl"), *root.glob("*.jsonl.gz")])
    if not all_paths:
        raise SystemExit(f"no .jsonl/.jsonl.gz catalogue assets found in {root}")

    actual_by_name = {path.name: path for path in all_paths}
    index_decks = [item for item in (index or {}).get("decks", []) if isinstance(item, dict)]
    index_files = [str(item.get("file")) for item in index_decks if item.get("file")]
    ordered_paths = [actual_by_name[name] for name in index_files if name in actual_by_name]
    ordered_names = {path.name for path in ordered_paths}
    ordered_paths.extend(path for path in all_paths if path.name not in ordered_names)

    stats = Stats(files=len(all_paths))
    seen_ids: set[str] = set()
    targets: dict[tuple[str, str], TargetGroup] = {}
    unique_target_source: set[tuple[str, str, str]] = set()
    unique_source_keys: set[tuple[str, str]] = set()
    deck_rows: list[dict[str, Any]] = []
    deck_by_file: dict[str, dict[str, Any]] = {}
    deck_meta_by_id: dict[str, dict[str, str]] = {}
    sample_heaps: dict[str, list[tuple[int, str, dict[str, Any]]]] = defaultdict(list)

    is_v2 = bool(index and int(index.get("catalogueVersion") or index.get("version") or 1) >= 2)
    identity_version = int(((index or {}).get("targetIdentity") or {}).get("version") or 2)
    required_card_fields = REQUIRED_FIELDS + (V2_CARD_FIELDS if is_v2 else ())
    required_context_fields = CONTEXT_FIELDS + (V2_CONTEXT_FIELDS if is_v2 else ())
    target_key = exact_target_key if is_v2 else normalized

    for path in ordered_paths:
        declared = dict(deck_index.get(path.name, {}))
        deck = dict(deck_from_filename(path))
        deck.update(declared)
        lang = str(deck.get("lang") or "?")
        meaning = str(deck.get("meaningLang") or "?")
        level = str(deck.get("level") or "?")
        deck_id = str(deck.get("id") or path.name.removesuffix(".gz").removesuffix(".jsonl"))
        collection = str(deck.get("collection") or "legacy")
        source_family = str(deck.get("sourceFamily") or "legacy")
        deck_meta_by_id[deck_id] = {"collection": collection, "sourceFamily": source_family}

        stats.per_language_decks[lang] += 1
        stats.per_level_decks[level] += 1
        stats.per_pair_decks[(lang, meaning)] += 1
        stats.per_collection_decks[collection] += 1
        stats.per_source_family_decks[source_family] += 1

        deck_cards = 0
        deck_contexts = 0
        raw_bytes = 0
        deck_parse_errors = 0
        deck_missing_card_fields = 0
        deck_missing_context_fields = 0
        deck_duplicate_target_context = 0
        seen_target_context: set[tuple[str, str]] = set()

        try:
            handle = open_deck(path)
        except OSError as error:
            raise SystemExit(f"cannot read {path}: {error}")
        with handle:
            for line_no, line in enumerate(handle, start=1):
                raw_bytes += len(line.encode("utf-8"))
                if not line.strip():
                    stats.empty_lines += 1
                    continue
                try:
                    card = json.loads(line)
                except ValueError:
                    stats.parse_errors += 1
                    deck_parse_errors += 1
                    print(f"warning: {path.name}:{line_no}: invalid JSON", file=sys.stderr)
                    continue
                if not isinstance(card, dict):
                    stats.parse_errors += 1
                    deck_parse_errors += 1
                    continue

                deck_cards += 1
                stats.cards += 1
                stats.per_language_cards[lang] += 1
                stats.per_level_cards[level] += 1
                stats.per_pair_cards[(lang, meaning)] += 1
                stats.per_collection_cards[collection] += 1
                stats.per_source_family_cards[source_family] += 1

                for key in REQUIRED_FIELDS + OPTIONAL_FIELDS + (V2_CARD_FIELDS if is_v2 else ()):
                    if key in card and card[key] not in (None, "", []):
                        stats.field_present[key] += 1
                for key in required_card_fields:
                    if key not in card or card[key] in (None, "", []):
                        stats.required_missing[key] += 1
                        deck_missing_card_fields += 1

                card_id = str(card.get("id") or "")
                if card_id:
                    if card_id in seen_ids:
                        stats.duplicate_card_ids += 1
                    seen_ids.add(card_id)

                text = str(card.get("text") or "")
                target_norm = target_key(text)
                if not target_norm.strip():
                    stats.empty_targets += 1
                    continue
                if is_v2 and str(card.get("targetId") or "") != expected_target_id(lang, text, identity_version):
                    stats.target_id_mismatches += 1
                if len(text) > LONG_TARGET_CHARS:
                    stats.long_targets += 1

                key = (lang, target_norm)
                group = targets.get(key)
                if group is None:
                    group = TargetGroup(lang=lang, display=text)
                    targets[key] = group
                group.cards += 1
                group.decks.add(deck_id)
                group.meanings.add(meaning)
                group.levels.add(level)

                if card.get("ipa"):
                    stats.ipa_cards += 1

                if sample_per_deck > 0:
                    sample_key = f"{path.name}:{line_no}:{card_id}"
                    priority = int.from_bytes(hashlib.sha256(sample_key.encode("utf-8")).digest()[:8], "big")
                    payload = compact_sample(deck, card)
                    heap = sample_heaps[deck_id]
                    item = (-priority, sample_key, payload)
                    if len(heap) < sample_per_deck:
                        heapq.heappush(heap, item)
                    elif priority < -heap[0][0]:
                        heapq.heapreplace(heap, item)

                contexts = [card]
                alternatives = card.get("contexts")
                if isinstance(alternatives, list):
                    contexts.extend(item for item in alternatives if isinstance(item, dict))

                target_identity = str(card.get("targetId") or target_norm)
                for item in contexts:
                    deck_contexts += 1
                    stats.contexts += 1
                    stats.per_language_contexts[lang] += 1
                    stats.per_level_contexts[level] += 1
                    stats.per_pair_contexts[(lang, meaning)] += 1
                    stats.per_collection_contexts[collection] += 1

                    context = str(item.get("context") or "")
                    translation = str(item.get("translation") or "")
                    if len(context) > LONG_CONTEXT_CHARS:
                        stats.long_contexts += 1

                    for required in required_context_fields:
                        if required not in item or item[required] in (None, "", []):
                            stats.context_required_missing[required] += 1
                            deck_missing_context_fields += 1

                    sid = source_id(translation)
                    explicit_context = str(item.get("contextId") or "").strip()
                    if explicit_context:
                        context_key = explicit_context
                    elif sid is not None:
                        context_key = "tatoeba:" + sid
                    else:
                        stats.provenance_missing += 1
                        context_key = fallback_context_key(context)

                    item_source_family = str(item.get("sourceFamily") or source_family)
                    if is_v2 and explicit_context and item_source_family and not explicit_context.startswith(item_source_family + ":"):
                        stats.context_namespace_mismatches += 1
                    meaning_identity = str(item.get("meaningId") or "").strip()
                    if is_v2 and meaning_identity and item_source_family and not meaning_identity.startswith(item_source_family + ":"):
                        stats.meaning_namespace_mismatches += 1
                    stats.per_source_family_contexts[item_source_family] += 1
                    unique_source_keys.add((lang, context_key))
                    unique_target_source.add((lang, target_norm, context_key))
                    group.source_keys.add(context_key)
                    if sid is not None:
                        group.source_ids.add(sid)
                    group.contexts.setdefault(context_key, context)

                    pair_key = (target_identity, context_key)
                    if pair_key in seen_target_context:
                        stats.duplicate_target_context_in_deck += 1
                        deck_duplicate_target_context += 1
                    else:
                        seen_target_context.add(pair_key)

                    if item.get("ipaContext"):
                        stats.ipa_context_cards += 1

                    try:
                        start = int(item.get("targetStart"))
                        finish = int(item.get("targetEnd"))
                        if target_key(utf16_slice(context, start, finish)) != target_norm:
                            stats.offsets_bad += 1
                    except (TypeError, ValueError, UnicodeError):
                        stats.offsets_bad += 1

                    tokens = item.get("tokens")
                    if isinstance(tokens, list):
                        for token in tokens:
                            if not isinstance(token, dict):
                                stats.token_required_missing["tokenObject"] += 1
                                continue
                            for token_field in TOKEN_FIELDS:
                                if token_field not in token or token[token_field] in (None, ""):
                                    stats.token_required_missing[token_field] += 1
                            stats.tokens += 1
                            stats.tokens_by_language[lang] += 1
                            surface = str(token.get("surface") or "")
                            lemma = str(token.get("lemma") or "")
                            if not lemma:
                                stats.lemma_missing += 1
                                stats.missing_lemma_by_language[lang] += 1
                            elif normalized(lemma) != normalized(surface):
                                stats.lemma_informative += 1
                                stats.informative_lemma_by_language[lang] += 1
                            pos = token.get("pos")
                            if pos:
                                stats.pos_values[str(pos)] += 1
                            if token.get("isContent") is True:
                                stats.content_tokens += 1

        actual_size = path.stat().st_size
        declared_cards = declared.get("chunkCount")
        declared_contexts = declared.get("contextCount")
        declared_size = declared.get("sizeBytes")
        declared_raw = declared.get("uncompressedSizeBytes") or declared.get("sizeBytes")
        mismatches: list[str] = []
        if declared_cards is not None and int(declared_cards) != deck_cards:
            mismatches.append("cards")
        if declared_contexts is not None and int(declared_contexts) != deck_contexts:
            mismatches.append("contexts")
        if declared_size is not None and int(declared_size) != actual_size:
            mismatches.append("sizeBytes")
        if declared_raw is not None and int(declared_raw) != raw_bytes:
            mismatches.append("uncompressedSizeBytes")

        row = {
            "indexOrder": index_files.index(path.name) + 1 if path.name in index_files else None,
            "id": deck_id,
            "file": path.name,
            "lang": lang,
            "meaningLang": meaning,
            "collection": collection,
            "level": level,
            "sourceFamily": source_family,
            "cards": deck_cards,
            "contexts": deck_contexts,
            "contextsPerCard": round(deck_contexts / max(1, deck_cards), 3),
            "sizeBytes": actual_size,
            "rawBytes": raw_bytes,
            "storageRatio": round(actual_size / max(1, raw_bytes), 6),
            "declaredCards": int(declared_cards) if declared_cards is not None else None,
            "declaredContexts": int(declared_contexts) if declared_contexts is not None else None,
            "parseErrors": deck_parse_errors,
            "missingCardFields": deck_missing_card_fields,
            "missingContextFields": deck_missing_context_fields,
            "duplicateTargetContextPairs": deck_duplicate_target_context,
            "declarationMismatches": mismatches,
        }
        deck_rows.append(row)
        deck_by_file[path.name] = row

    groups = list(targets.values())
    context_histogram = Counter(len(group.source_keys) for group in groups)
    duplicate_cards = stats.cards - len(groups)
    multi_context = [group for group in groups if len(group.source_keys) >= 2]
    cross_meaning = [group for group in groups if len(group.meanings) >= 2]
    cross_level = [group for group in groups if len(group.levels) >= 2]

    unique_by_language = Counter(group.lang for group in groups)
    unique_by_level: Counter = Counter()
    unique_by_pair: Counter = Counter()
    unique_by_collection: Counter = Counter()
    unique_by_source_family: Counter = Counter()
    for group in groups:
        for value in group.levels:
            unique_by_level[value] += 1
        for value in group.meanings:
            unique_by_pair[(group.lang, value)] += 1
        group_collections = {deck_meta_by_id.get(deck_id, {}).get("collection", "legacy") for deck_id in group.decks}
        group_families = {deck_meta_by_id.get(deck_id, {}).get("sourceFamily", "legacy") for deck_id in group.decks}
        for value in group_collections:
            unique_by_collection[value] += 1
        for value in group_families:
            unique_by_source_family[value] += 1

    def count_at_least(n: int) -> int:
        return sum(1 for group in groups if len(group.source_keys) >= n)

    actual_files = set(actual_by_name)
    indexed_files = set(index_files)
    missing_index_assets = sorted(indexed_files - actual_files)
    unindexed_assets = sorted(actual_files - indexed_files) if index is not None else []

    actual_raw_bytes = sum(int(row["rawBytes"]) for row in deck_rows)
    deck_asset_bytes = sum(int(row["sizeBytes"]) for row in deck_rows)
    declared_uncompressed_bytes = sum(
        int(item.get("uncompressedSizeBytes") or item.get("sizeBytes") or 0)
        for item in index_decks
    )
    if not declared_uncompressed_bytes:
        declared_uncompressed_bytes = actual_raw_bytes

    deck_cards_values = [int(row["cards"]) for row in deck_rows]
    deck_context_values = [int(row["contexts"]) for row in deck_rows]
    deck_declaration_mismatches = sum(1 for row in deck_rows if row["declarationMismatches"])
    last_index_deck = None
    if index_files:
        last_file = index_files[-1]
        last_index_deck = dict(deck_by_file.get(last_file) or {"file": last_file, "missing": True})

    def breakdown_rows(
        cards_counter: Counter,
        contexts_counter: Counter,
        decks_counter: Counter,
        unique_counter: Counter,
        key_formatter=lambda key: key,
    ) -> list[dict[str, Any]]:
        keys = set(cards_counter) | set(contexts_counter) | set(decks_counter) | set(unique_counter)
        rows = []
        for key in sorted(keys):
            rows.append(
                {
                    "key": key_formatter(key),
                    "decks": int(decks_counter.get(key, 0)),
                    "cards": int(cards_counter.get(key, 0)),
                    "uniqueTargets": int(unique_counter.get(key, 0)),
                    "contexts": int(contexts_counter.get(key, 0)),
                }
            )
        return rows

    result = {
        "schemaVersion": 2,
        "input": {
            "directory": str(root),
            "indexPresent": index is not None,
            "indexVersion": index.get("version") if index else None,
            "catalogueVersion": index.get("catalogueVersion") if index else None,
            "indexBuiltAt": index.get("builtAt") if index else None,
            "targetIdentity": (index or {}).get("targetIdentity"),
            "deckFiles": stats.files,
            "indexDecks": len(index_decks) if index else None,
            "indexPairs": len(index.get("pairs", [])) if index else None,
            "missingIndexAssets": missing_index_assets,
            "unindexedAssets": unindexed_assets,
        },
        "catalogue": {
            "targetDeckMemberships": stats.cards,
            "cardsInDecks": stats.cards,
            "contexts": stats.contexts,
            "deckAssetBytes": deck_asset_bytes,
            "observedUncompressedBytes": actual_raw_bytes,
            "declaredUncompressedBytes": declared_uncompressed_bytes,
            "parseErrors": stats.parse_errors,
            "emptyLines": stats.empty_lines,
            "duplicateCardIds": stats.duplicate_card_ids,
            "duplicateTargetContextPairsWithinDecks": stats.duplicate_target_context_in_deck,
            "deckDeclarationMismatches": deck_declaration_mismatches,
            "uniqueSourceContexts": len(unique_source_keys),
        },
        "targets": {
            "uniqueExactTargets": len(groups),
            "uniqueTargetSourcePairs": len(unique_target_source),
            "targetMembershipsBeyondUniqueTargets": duplicate_cards,
            "targetsWithAtLeast2Contexts": count_at_least(2),
            "targetsWithAtLeast3Contexts": count_at_least(3),
            "targetsWithAtLeast5Contexts": count_at_least(5),
            "targetsWithAtLeast10Contexts": count_at_least(10),
            "targetsAcrossMultipleMeaningLanguages": len(cross_meaning),
            "targetsAcrossMultipleLevels": len(cross_level),
            "contextCountHistogram": dict(sorted(context_histogram.items())),
        },
        "metadata": {
            "fieldCoverage": dict(stats.field_present),
            "missingRequiredCardFields": dict(stats.required_missing),
            "missingRequiredContextFields": dict(stats.context_required_missing),
            "missingRequiredTokenFields": dict(stats.token_required_missing),
            "provenanceMissing": stats.provenance_missing,
            "tatoebaProvenanceMissing": stats.provenance_missing,
            "badTargetOffsets": stats.offsets_bad,
            "emptyTargets": stats.empty_targets,
            "targetsLongerThan80Chars": stats.long_targets,
            "contextsLongerThan500Chars": stats.long_contexts,
            "targetIdMismatches": stats.target_id_mismatches,
            "contextIdNamespaceMismatches": stats.context_namespace_mismatches,
            "meaningIdNamespaceMismatches": stats.meaning_namespace_mismatches,
            "cardsWithIpa": stats.ipa_cards,
            "cardsWithContextIpa": stats.ipa_context_cards,
            "tokens": stats.tokens,
            "tokensWithNonIdentityLemma": stats.lemma_informative,
            "tokensMissingLemma": stats.lemma_missing,
            "contentTokens": stats.content_tokens,
            "posValues": dict(stats.pos_values.most_common()),
            "morphologyByLearningLanguage": {
                lang: {
                    "tokens": int(stats.tokens_by_language.get(lang, 0)),
                    "nonIdentityLemma": int(stats.informative_lemma_by_language.get(lang, 0)),
                    "missingLemma": int(stats.missing_lemma_by_language.get(lang, 0)),
                }
                for lang in sorted(stats.tokens_by_language)
            },
        },
        "inventory": {
            "decks": deck_rows,
            "lastIndexDeck": last_index_deck,
            "cardCount": {
                "min": min(deck_cards_values) if deck_cards_values else 0,
                "median": percentile(deck_cards_values, 0.5),
                "p95": percentile(deck_cards_values, 0.95),
                "max": max(deck_cards_values) if deck_cards_values else 0,
                "below100": sum(value < 100 for value in deck_cards_values),
                "below1000": sum(value < 1000 for value in deck_cards_values),
            },
            "contextCount": {
                "min": min(deck_context_values) if deck_context_values else 0,
                "median": percentile(deck_context_values, 0.5),
                "p95": percentile(deck_context_values, 0.95),
                "max": max(deck_context_values) if deck_context_values else 0,
            },
            "rawClientCapBytes": RAW_CLIENT_CAP_BYTES,
            "decksAtOrAbove90PercentRawCap": sum(
                int(row["rawBytes"]) >= int(RAW_CLIENT_CAP_BYTES * 0.9) for row in deck_rows
            ),
            "decksOverRawCap": sum(int(row["rawBytes"]) > RAW_CLIENT_CAP_BYTES for row in deck_rows),
        },
        "breakdown": {
            "byLearningLanguage": breakdown_rows(
                stats.per_language_cards,
                stats.per_language_contexts,
                stats.per_language_decks,
                unique_by_language,
            ),
            "byLevel": breakdown_rows(
                stats.per_level_cards,
                stats.per_level_contexts,
                stats.per_level_decks,
                unique_by_level,
            ),
            "byPair": breakdown_rows(
                stats.per_pair_cards,
                stats.per_pair_contexts,
                stats.per_pair_decks,
                unique_by_pair,
                lambda pair: f"{pair[0]}->{pair[1]}",
            ),
            "byCollection": breakdown_rows(
                stats.per_collection_cards,
                stats.per_collection_contexts,
                stats.per_collection_decks,
                unique_by_collection,
            ),
            "bySourceFamily": breakdown_rows(
                stats.per_source_family_cards,
                stats.per_source_family_contexts,
                stats.per_source_family_decks,
                unique_by_source_family,
            ),
            # Compatibility aliases for older report consumers.
            "targetMembershipsByLearningLanguage": dict(sorted(stats.per_language_cards.items())),
            "targetMembershipsByLevel": dict(sorted(stats.per_level_cards.items())),
            "targetMembershipsByPair": {
                f"{lang}->{meaning}": count
                for (lang, meaning), count in sorted(stats.per_pair_cards.items())
            },
            "targetMembershipsByCollection": dict(sorted(stats.per_collection_cards.items())),
            "targetMembershipsBySourceFamily": dict(sorted(stats.per_source_family_cards.items())),
            "cardsByLearningLanguage": dict(sorted(stats.per_language_cards.items())),
            "cardsByLevel": dict(sorted(stats.per_level_cards.items())),
            "cardsByPair": {
                f"{lang}->{meaning}": count
                for (lang, meaning), count in sorted(stats.per_pair_cards.items())
            },
            "cardsByCollection": dict(sorted(stats.per_collection_cards.items())),
            "cardsBySourceFamily": dict(sorted(stats.per_source_family_cards.items())),
        },
    }

    samples: dict[str, list[dict[str, Any]]] = {}
    for deck_id, heap in sample_heaps.items():
        selected = [(-neg_priority, sample_key, payload) for neg_priority, sample_key, payload in heap]
        selected.sort(key=lambda row: (row[0], row[1]))
        samples[deck_id] = [payload for _, _, payload in selected]

    return (
        result,
        sorted(
            multi_context,
            key=lambda group: (-len(group.source_keys), -len(group.meanings), group.lang, normalized(group.display)),
        ),
        samples,
    )


def analyse(root: Path) -> tuple[dict[str, Any], list[TargetGroup]]:
    data, groups, _ = _analyse(root, sample_per_deck=0)
    return data, groups


def verify_build(data: dict[str, Any], path: Path) -> dict[str, Any]:
    expected = json.loads(path.read_text(encoding="utf-8"))
    output = expected.get("output") or {}
    actual = data["catalogue"]
    targets = data["targets"]
    checks = {
        "targetDeckMemberships": (actual["targetDeckMemberships"], output.get("targetDeckMemberships")),
        "contexts": (actual["contexts"], output.get("contexts")),
        "uniqueSourceContexts": (actual["uniqueSourceContexts"], output.get("uniqueSourceContexts")),
        "uniqueTargets": (targets["uniqueExactTargets"], output.get("uniqueTargets")),
        "decks": (data["input"]["deckFiles"], output.get("decks")),
        "compressedDeckBytes": (actual["deckAssetBytes"], output.get("compressedDeckBytes")),
        "uncompressedDeckBytes": (actual["observedUncompressedBytes"], output.get("uncompressedDeckBytes")),
    }
    mismatches = [
        f"{name}: census={got}, build={want}"
        for name, (got, want) in checks.items()
        if want is not None and got != want
    ]
    if mismatches:
        raise SystemExit("census does not describe this Catalogue v2 build: " + "; ".join(mismatches))
    data["input"]["buildVerified"] = True
    data["build"] = {
        "verified": True,
        "path": str(path),
        "limits": expected.get("limits") or {},
        "morphology": expected.get("morphology"),
        "phonetics": expected.get("phonetics"),
    }
    max_deck = int((expected.get("limits") or {}).get("maxDeckTargets") or 0)
    if max_deck:
        data["inventory"]["maxDeckTargets"] = max_deck
        data["inventory"]["decksAtTargetCap"] = sum(
            int(row["cards"]) >= max_deck for row in data["inventory"]["decks"]
        )
        for row in data["inventory"]["decks"]:
            row["atTargetCap"] = int(row["cards"]) >= max_deck
    return expected


def pct(part: int, whole: int) -> str:
    if not whole:
        return "0.0%"
    return f"{100.0 * part / whole:.1f}%"


def mib(value: int) -> str:
    return f"{value / 1048576:.1f} MiB"


def append_breakdown(lines: list[str], title: str, rows: list[dict[str, Any]]) -> None:
    lines.extend(
        [
            "",
            f"## {title}",
            "",
            "| group | decks | cards in decks | unique targets | contexts |",
            "| --- | ---: | ---: | ---: | ---: |",
        ]
    )
    for row in rows:
        lines.append(
            "| `%s` | %s | %s | %s | %s |"
            % (
                row["key"],
                f"{row['decks']:,}",
                f"{row['cards']:,}",
                f"{row['uniqueTargets']:,}",
                f"{row['contexts']:,}",
            )
        )


def markdown_report(data: dict[str, Any], groups: list[TargetGroup], top: int) -> str:
    cat = data["catalogue"]
    tar = data["targets"]
    meta = data["metadata"]
    inp = data["input"]
    inventory = data["inventory"]
    field_coverage = meta["fieldCoverage"]
    cards = cat["targetDeckMemberships"]
    contexts = cat["contexts"]

    lines = [
        "# Catalogue meta-info",
        "",
        "This report inspects catalogue assets as data. It does not rebuild or modify them.",
        "Catalogue v2 exact targets use Unicode NFKC + case-folding only; morphology is deliberately not inferred. Legacy v1 inspection keeps its historical census normalization.",
        "",
        f"Catalogue index version: **{inp.get('indexVersion') or '?'}** · generation: **{inp.get('catalogueVersion') or '?'}** · built: **{inp.get('indexBuiltAt') or '?'}**.",
        *(["BUILD.json cross-check: **verified**."] if inp.get("buildVerified") else []),
        "",
        "## Human snapshot",
        "",
        "| what is in the catalogue | value |",
        "| --- | ---: |",
        f"| cards in all decks | **{cards:,}** |",
        f"| unique learning targets | **{tar['uniqueExactTargets']:,}** |",
        f"| retained natural contexts | **{contexts:,}** |",
        f"| unique source contexts | **{cat['uniqueSourceContexts']:,}** |",
        f"| deck assets | **{inp['deckFiles']:,}** |",
        f"| language pairs | **{inp.get('indexPairs') if inp.get('indexPairs') is not None else '?'}** |",
        f"| deck assets on disk | **{mib(cat['deckAssetBytes'])}** |",
        f"| raw JSONL represented by those assets | **{mib(cat['observedUncompressedBytes'])}** |",
        "",
        "**Cards in all decks** means target/deck memberships: one downloadable deck row equals one study card for that deck. The same global target may occur in several decks, so this number is intentionally larger than **unique learning targets**.",
        "",
    ]

    last = inventory.get("lastIndexDeck")
    lines.extend(["## Last deck in index order", ""])
    if last and not last.get("missing"):
        lines.extend(
            [
                "This is the last entry in `index.json`; it is an order fact, not a claim that this deck was built later than the others.",
                "",
                "| metric | value |",
                "| --- | ---: |",
                f"| deck | `{last['id']}` |",
                f"| file | `{last['file']}` |",
                f"| cards | **{last['cards']:,}** |",
                f"| contexts | **{last['contexts']:,}** |",
                f"| contexts / card | **{last['contextsPerCard']:.2f}** |",
                f"| compressed asset | **{mib(last['sizeBytes'])}** |",
                f"| raw JSONL | **{mib(last['rawBytes'])}** |",
                "",
            ]
        )
    elif last:
        lines.extend([f"The last indexed asset `{last.get('file')}` is missing from the inspected directory.", ""])
    else:
        lines.extend(["No index order is available for these assets.", ""])

    count_stats = inventory["cardCount"]
    context_stats = inventory["contextCount"]
    lines.extend(
        [
            "## Deck distribution",
            "",
            "| measure | min | median | p95 | max |",
            "| --- | ---: | ---: | ---: | ---: |",
            f"| cards / deck | {fmt_number(count_stats['min'])} | {fmt_number(count_stats['median'])} | {fmt_number(count_stats['p95'])} | {fmt_number(count_stats['max'])} |",
            f"| contexts / deck | {fmt_number(context_stats['min'])} | {fmt_number(context_stats['median'])} | {fmt_number(context_stats['p95'])} | {fmt_number(context_stats['max'])} |",
            "",
            f"Decks below 1,000 cards: **{count_stats['below1000']:,}** · below 100 cards: **{count_stats['below100']:,}**.",
        ]
    )
    if inventory.get("maxDeckTargets"):
        lines.append(
            f"Decks at the configured `{inventory['maxDeckTargets']:,}` target cap: **{inventory.get('decksAtTargetCap', 0):,}**."
        )
    lines.extend(
        [
            f"Decks at or above 90% of the 24 MiB raw client cap: **{inventory['decksAtOrAbove90PercentRawCap']:,}** · over cap: **{inventory['decksOverRawCap']:,}**.",
            "",
            "## Snapshot (technical names)",
            "",
            "| metric | value |",
            "| --- | ---: |",
            f"| deck assets analysed | {inp['deckFiles']:,} |",
            f"| target-deck memberships | {cards:,} |",
            f"| natural contexts retained | {contexts:,} |",
            f"| unique source contexts | {cat['uniqueSourceContexts']:,} |",
            f"| unique exact targets | {tar['uniqueExactTargets']:,} |",
            f"| unique target + source-context pairs | {tar['uniqueTargetSourcePairs']:,} |",
            f"| target memberships beyond unique exact targets | {tar['targetMembershipsBeyondUniqueTargets']:,} |",
            f"| deck assets on disk | {mib(cat['deckAssetBytes'])} |",
            f"| observed raw JSONL | {mib(cat['observedUncompressedBytes'])} |",
            f"| storage ratio | {100.0 * cat['deckAssetBytes'] / max(1, cat['observedUncompressedBytes']):.1f}% |",
            "",
            "## Context reuse",
            "",
            "A target counts as multi-context only when the same exact target occurs in at least two distinct source contexts. Reusing one source context in decks with different meaning languages does not create a new context.",
            "",
            "| exact target groups | count | share of targets |",
            "| --- | ---: | ---: |",
        ]
    )
    unique = tar["uniqueExactTargets"]
    for threshold in (2, 3, 5, 10):
        count = tar[f"targetsWithAtLeast{threshold}Contexts"]
        lines.append(f"| at least {threshold} source contexts | {count:,} | {pct(count, unique)} |")
    lines.extend(
        [
            f"| present in multiple meaning languages | {tar['targetsAcrossMultipleMeaningLanguages']:,} | {pct(tar['targetsAcrossMultipleMeaningLanguages'], unique)} |",
            f"| present across multiple levels | {tar['targetsAcrossMultipleLevels']:,} | {pct(tar['targetsAcrossMultipleLevels'], unique)} |",
            "",
            "### Exact context-count histogram",
            "",
            "| distinct source contexts per exact target | targets |",
            "| ---: | ---: |",
        ]
    )
    histogram = {int(k): v for k, v in tar["contextCountHistogram"].items()}
    for count in sorted(histogram):
        lines.append(f"| {count} | {histogram[count]:,} |")

    lines.extend(
        [
            "",
            "## Metadata coverage",
            "",
            "| field / check | count | coverage |",
            "| --- | ---: | ---: |",
        ]
    )
    report_fields = REQUIRED_FIELDS + (V2_CARD_FIELDS if int(inp.get("catalogueVersion") or 1) >= 2 else ()) + OPTIONAL_FIELDS
    for key in report_fields:
        present = int(field_coverage.get(key, 0))
        lines.append(f"| `{key}` | {present:,} | {pct(present, cards)} |")
    lines.extend(
        [
            f"| source provenance missing | {meta['provenanceMissing']:,} | {pct(meta['provenanceMissing'], contexts)} |",
            f"| invalid target offsets | {meta['badTargetOffsets']:,} | {pct(meta['badTargetOffsets'], contexts)} |",
            "",
            "### Token metadata",
            "",
            f"Tokens inspected: **{meta['tokens']:,}**. Lemma differs from the written surface for **{meta['tokensWithNonIdentityLemma']:,}** tokens ({pct(meta['tokensWithNonIdentityLemma'], meta['tokens'])}). This measures informative morphology carried by the catalogue; it is not used to merge target identity.",
            "",
            "`pos` values: " + (", ".join(f"`{name}` {count:,}" for name, count in meta["posValues"].items()) or "none"),
            "",
            "### Morphology by learning language",
            "",
            "| language | tokens | lemma differs from surface | share | missing lemma |",
            "| --- | ---: | ---: | ---: | ---: |",
        ]
    )
    for lang, row in meta.get("morphologyByLearningLanguage", {}).items():
        lines.append(
            f"| `{lang}` | {row['tokens']:,} | {row['nonIdentityLemma']:,} | {pct(row['nonIdentityLemma'], row['tokens'])} | {row['missingLemma']:,} |"
        )

    append_breakdown(lines, "By collection", data["breakdown"]["byCollection"])
    append_breakdown(lines, "By level", data["breakdown"]["byLevel"])
    append_breakdown(lines, "By learning language", data["breakdown"]["byLearningLanguage"])
    append_breakdown(lines, "By language pair", data["breakdown"]["byPair"])
    append_breakdown(lines, "By source family", data["breakdown"]["bySourceFamily"])

    lines.extend(
        [
            "",
            "## Full deck inventory",
            "",
            "Every downloadable deck is listed here so card counts do not have to be inferred from aggregate numbers.",
            "",
            "| # | deck | cards | contexts | ctx/card | gzip/on-disk | raw JSONL | raw cap |",
            "| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: |",
        ]
    )
    for position, row in enumerate(inventory["decks"], start=1):
        raw_cap = 100.0 * int(row["rawBytes"]) / RAW_CLIENT_CAP_BYTES
        order = row.get("indexOrder") or position
        lines.append(
            f"| {order} | `{row['id']}` | {row['cards']:,} | {row['contexts']:,} | {row['contextsPerCard']:.2f} | {mib(row['sizeBytes'])} | {mib(row['rawBytes'])} | {raw_cap:.1f}% |"
        )

    lines.extend(
        [
            "",
            "## Multi-context exact targets",
            "",
            "These are candidates for later contextual-diversity experiments. The report only proves that distinct source contexts exist; it does not claim they are semantically equivalent beyond the exact target string.",
            "",
        ]
    )
    if not groups:
        lines.append("No exact target occurs in more than one source context.")
    for group in groups[:top]:
        meanings = ", ".join(sorted(group.meanings))
        levels = ", ".join(sorted(group.levels))
        lines.extend(
            [
                f"### `{group.lang}` · `{group.display}` · {len(group.source_keys)} contexts",
                "",
                f"Meaning languages: {meanings or '?'}  ",
                f"Levels: {levels or '?'}  ",
                f"Deck memberships carrying this target: {group.cards}",
                "",
            ]
        )
        for key, context in list(group.contexts.items())[:5]:
            source = key.removeprefix("tatoeba:") if key.startswith("tatoeba:") else None
            safe = context.replace("\n", " ").strip()
            label = f"Tatoeba #{source}" if source else key
            lines.append(f"- {label}: {safe}")
        if len(group.contexts) > 5:
            lines.append(f"- ... {len(group.contexts) - 5} more contexts")
        lines.append("")

    missing_card = sum(int(value) for value in meta.get("missingRequiredCardFields", {}).values())
    missing_context = sum(int(value) for value in meta.get("missingRequiredContextFields", {}).values())
    missing_token = sum(int(value) for value in meta.get("missingRequiredTokenFields", {}).values())
    lines.extend(
        [
            "## Integrity",
            "",
            "| check | count |",
            "| --- | ---: |",
            f"| JSON parse errors | {cat['parseErrors']:,} |",
            f"| empty lines | {cat['emptyLines']:,} |",
            f"| duplicate card IDs | {cat['duplicateCardIds']:,} |",
            f"| duplicate target+context pairs inside one deck | {cat['duplicateTargetContextPairsWithinDecks']:,} |",
            f"| missing required card fields | {missing_card:,} |",
            f"| missing required context fields | {missing_context:,} |",
            f"| missing required token fields | {missing_token:,} |",
            f"| targetId identity mismatches | {meta.get('targetIdMismatches', 0):,} |",
            f"| contextId namespace mismatches | {meta.get('contextIdNamespaceMismatches', 0):,} |",
            f"| meaningId namespace mismatches | {meta.get('meaningIdNamespaceMismatches', 0):,} |",
            f"| deck/index declaration mismatches | {cat['deckDeclarationMismatches']:,} |",
            f"| indexed assets missing on disk | {len(inp.get('missingIndexAssets') or []):,} |",
            f"| unindexed deck assets | {len(inp.get('unindexedAssets') or []):,} |",
            "",
        ]
    )
    return "\n".join(lines)


def write_samples(samples: dict[str, list[dict[str, Any]]], out_dir: Path, per_deck: int) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    manifest = {
        "selection": "lowest-sha256(deck-file:line:card-id)",
        "perDeckLimit": per_deck,
        "decks": {},
    }
    for deck_id in sorted(samples):
        path = out_dir / f"{deck_id}.jsonl"
        rows = samples[deck_id]
        with path.open("w", encoding="utf-8") as handle:
            for row in rows:
                handle.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")
        manifest["decks"][deck_id] = len(rows)
    (out_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Inspect metadata, inventory and target/context reuse in an ikna catalogue.")
    parser.add_argument("--dir", required=True, type=Path, help="directory containing index.json and deck .jsonl/.jsonl.gz assets")
    parser.add_argument(
        "--expect-build",
        type=Path,
        help="optional BUILD.json from this exact build; fail if census totals disagree",
    )
    parser.add_argument("--json", dest="json_out", type=Path, help="write machine-readable report here")
    parser.add_argument("--markdown", dest="md_out", type=Path, help="write human-readable report here")
    parser.add_argument("--top", type=int, default=50, help="number of multi-context target groups to show")
    parser.add_argument("--groups-jsonl", type=Path, help="write every multi-context exact-target group here")
    parser.add_argument("--samples-dir", type=Path, help="write deterministic manual-audit samples here")
    parser.add_argument("--sample-per-deck", type=int, default=20, help="sample rows per deck when --samples-dir is used")
    args = parser.parse_args(argv)

    sample_limit = max(0, args.sample_per_deck) if args.samples_dir else 0
    data, groups, samples = _analyse(args.dir, sample_per_deck=sample_limit)
    if args.expect_build:
        verify_build(data, args.expect_build)
    report = markdown_report(data, groups, max(0, args.top))

    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.md_out:
        args.md_out.parent.mkdir(parents=True, exist_ok=True)
        args.md_out.write_text(report, encoding="utf-8")
    if args.groups_jsonl:
        args.groups_jsonl.parent.mkdir(parents=True, exist_ok=True)
        with args.groups_jsonl.open("w", encoding="utf-8") as handle:
            for group in groups:
                payload = {
                    "language": group.lang,
                    "target": group.display,
                    "distinctContextCount": len(group.source_keys),
                    "targetDeckMembershipCount": group.cards,
                    "cardCount": group.cards,
                    "meaningLanguages": sorted(group.meanings),
                    "levels": sorted(group.levels),
                    "contexts": [
                        {"sourceKey": key, "text": text}
                        for key, text in group.contexts.items()
                    ],
                }
                handle.write(json.dumps(payload, ensure_ascii=False) + "\n")
    if args.samples_dir:
        write_samples(samples, args.samples_dir, sample_limit)
    if not args.json_out and not args.md_out and not args.groups_jsonl and not args.samples_dir:
        print(report)

    bad = data["catalogue"]["parseErrors"] + data["metadata"]["badTargetOffsets"]
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
