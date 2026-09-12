#!/usr/bin/env python3
"""Inspect a published ikna catalogue without changing it.

The report answers the questions that matter before adding a richer learning-data
layer: how much metadata already exists, how often the same exact target appears
in genuinely different source sentences, and how much of the repetition is only
the same source sentence copied into decks with another meaning language.

Input is a directory containing the assets from the GitHub release tagged
``catalog`` (or another catalogue tag). Only the Python standard library is used.
"""

from __future__ import annotations

import argparse
import hashlib
import json
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
    r"(?P<level>beginner|middle|advanced)(?P<pd>-pd)?\.jsonl$"
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
OPTIONAL_FIELDS = ("ipa", "ipaContext")


def normalized(text: str) -> str:
    """Stable exact-match normalization, deliberately not morphology."""
    return " ".join(unicodedata.normalize("NFKC", text).strip().casefold().split())


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
        return {"id": path.stem, "lang": "?", "meaningLang": "?", "level": "?"}
    return {
        "id": path.stem,
        "lang": match.group("lang"),
        "meaningLang": match.group("meaning"),
        "level": match.group("level"),
        "family": "public-domain" if match.group("pd") else "attributed",
    }


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
    files: int = 0
    parse_errors: int = 0
    empty_lines: int = 0
    duplicate_card_ids: int = 0
    offsets_bad: int = 0
    provenance_missing: int = 0
    ipa_cards: int = 0
    ipa_context_cards: int = 0
    tokens: int = 0
    lemma_informative: int = 0
    lemma_missing: int = 0
    content_tokens: int = 0
    field_present: Counter = field(default_factory=Counter)
    pos_values: Counter = field(default_factory=Counter)
    per_language_cards: Counter = field(default_factory=Counter)
    per_level_cards: Counter = field(default_factory=Counter)
    per_pair_cards: Counter = field(default_factory=Counter)


def analyse(root: Path) -> tuple[dict[str, Any], list[TargetGroup]]:
    deck_index, index = load_index(root)
    paths = sorted(root.glob("*.jsonl"))
    if not paths:
        raise SystemExit(f"no .jsonl catalogue assets found in {root}")

    stats = Stats(files=len(paths))
    seen_ids: set[str] = set()
    targets: dict[tuple[str, str], TargetGroup] = {}
    unique_target_source: set[tuple[str, str, str]] = set()
    unique_source_keys: set[tuple[str, str]] = set()

    for path in paths:
        deck = dict(deck_from_filename(path))
        deck.update(deck_index.get(path.name, {}))
        lang = str(deck.get("lang") or "?")
        meaning = str(deck.get("meaningLang") or "?")
        level = str(deck.get("level") or "?")
        deck_id = str(deck.get("id") or path.stem)

        try:
            handle = path.open(encoding="utf-8")
        except OSError as error:
            raise SystemExit(f"cannot read {path}: {error}")
        with handle:
            for line_no, line in enumerate(handle, start=1):
                if not line.strip():
                    stats.empty_lines += 1
                    continue
                try:
                    card = json.loads(line)
                except ValueError:
                    stats.parse_errors += 1
                    print(f"warning: {path.name}:{line_no}: invalid JSON", file=sys.stderr)
                    continue
                if not isinstance(card, dict):
                    stats.parse_errors += 1
                    continue

                stats.cards += 1
                stats.per_language_cards[lang] += 1
                stats.per_level_cards[level] += 1
                stats.per_pair_cards[(lang, meaning)] += 1
                for key in REQUIRED_FIELDS + OPTIONAL_FIELDS:
                    if key in card and card[key] not in (None, "", []):
                        stats.field_present[key] += 1

                card_id = str(card.get("id") or "")
                if card_id:
                    if card_id in seen_ids:
                        stats.duplicate_card_ids += 1
                    seen_ids.add(card_id)

                text = str(card.get("text") or "")
                context = str(card.get("context") or "")
                translation = str(card.get("translation") or "")
                target_norm = normalized(text)
                if not target_norm:
                    continue

                sid = source_id(translation)
                if sid is None:
                    stats.provenance_missing += 1
                    context_key = fallback_context_key(context)
                else:
                    context_key = "tatoeba:" + sid
                unique_source_keys.add((lang, context_key))
                unique_target_source.add((lang, target_norm, context_key))

                key = (lang, target_norm)
                group = targets.get(key)
                if group is None:
                    group = TargetGroup(lang=lang, display=text)
                    targets[key] = group
                group.cards += 1
                group.source_keys.add(context_key)
                if sid is not None:
                    group.source_ids.add(sid)
                group.contexts.setdefault(context_key, context)
                group.decks.add(deck_id)
                group.meanings.add(meaning)
                group.levels.add(level)

                if card.get("ipa"):
                    stats.ipa_cards += 1
                if card.get("ipaContext"):
                    stats.ipa_context_cards += 1

                try:
                    start = int(card.get("targetStart"))
                    end = int(card.get("targetEnd"))
                    if utf16_slice(context, start, end) != text:
                        stats.offsets_bad += 1
                except (TypeError, ValueError, UnicodeError):
                    stats.offsets_bad += 1

                tokens = card.get("tokens")
                if isinstance(tokens, list):
                    for token in tokens:
                        if not isinstance(token, dict):
                            continue
                        stats.tokens += 1
                        surface = str(token.get("surface") or "")
                        lemma = str(token.get("lemma") or "")
                        if not lemma:
                            stats.lemma_missing += 1
                        elif normalized(lemma) != normalized(surface):
                            stats.lemma_informative += 1
                        pos = token.get("pos")
                        if pos:
                            stats.pos_values[str(pos)] += 1
                        if token.get("isContent") is True:
                            stats.content_tokens += 1

    groups = list(targets.values())
    context_histogram = Counter(len(group.source_keys) for group in groups)
    duplicate_cards = stats.cards - len(unique_target_source)
    multi_context = [group for group in groups if len(group.source_keys) >= 2]
    cross_meaning = [group for group in groups if len(group.meanings) >= 2]
    cross_level = [group for group in groups if len(group.levels) >= 2]

    def count_at_least(n: int) -> int:
        return sum(1 for group in groups if len(group.source_keys) >= n)

    result = {
        "schemaVersion": 1,
        "input": {
            "directory": str(root),
            "indexPresent": index is not None,
            "indexBuiltAt": index.get("builtAt") if index else None,
            "deckFiles": stats.files,
            "indexDecks": len(index.get("decks", [])) if index else None,
            "indexPairs": len(index.get("pairs", [])) if index else None,
        },
        "catalogue": {
            "cards": stats.cards,
            "parseErrors": stats.parse_errors,
            "emptyLines": stats.empty_lines,
            "duplicateCardIds": stats.duplicate_card_ids,
            "uniqueSourceContexts": len(unique_source_keys),
        },
        "targets": {
            "uniqueExactTargets": len(groups),
            "uniqueTargetSourcePairs": len(unique_target_source),
            "cardsBeyondUniqueTargetSource": duplicate_cards,
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
            "tatoebaProvenanceMissing": stats.provenance_missing,
            "badTargetOffsets": stats.offsets_bad,
            "cardsWithIpa": stats.ipa_cards,
            "cardsWithContextIpa": stats.ipa_context_cards,
            "tokens": stats.tokens,
            "tokensWithNonIdentityLemma": stats.lemma_informative,
            "tokensMissingLemma": stats.lemma_missing,
            "contentTokens": stats.content_tokens,
            "posValues": dict(stats.pos_values.most_common()),
        },
        "breakdown": {
            "cardsByLearningLanguage": dict(sorted(stats.per_language_cards.items())),
            "cardsByLevel": dict(sorted(stats.per_level_cards.items())),
            "cardsByPair": {
                f"{lang}->{meaning}": count
                for (lang, meaning), count in sorted(stats.per_pair_cards.items())
            },
        },
    }
    return result, sorted(
        multi_context,
        key=lambda group: (-len(group.source_keys), -len(group.meanings), group.lang, normalized(group.display)),
    )


def pct(part: int, whole: int) -> str:
    if not whole:
        return "0.0%"
    return f"{100.0 * part / whole:.1f}%"


def markdown_report(data: dict[str, Any], groups: list[TargetGroup], top: int) -> str:
    cat = data["catalogue"]
    tar = data["targets"]
    meta = data["metadata"]
    inp = data["input"]
    field_coverage = meta["fieldCoverage"]
    cards = cat["cards"]

    lines = [
        "# Catalogue meta-info",
        "",
        "This report inspects the published catalogue as data. It does not rebuild or modify it.",
        "Exact targets use Unicode NFKC + case-folding only; morphology is deliberately not inferred.",
        "",
        "## Snapshot",
        "",
        "| metric | value |",
        "| --- | ---: |",
        f"| deck assets analysed | {inp['deckFiles']:,} |",
        f"| cards | {cards:,} |",
        f"| unique source contexts | {cat['uniqueSourceContexts']:,} |",
        f"| unique exact targets | {tar['uniqueExactTargets']:,} |",
        f"| unique target + source-context pairs | {tar['uniqueTargetSourcePairs']:,} |",
        f"| cards beyond unique target/context pairs | {tar['cardsBeyondUniqueTargetSource']:,} |",
        "",
        "## Context reuse",
        "",
        "A target counts as multi-context only when the same exact target occurs in at least two different source sentences. Reusing one Tatoeba sentence in decks with different meaning languages does not create a new context.",
        "",
        "| exact target groups | count | share of targets |",
        "| --- | ---: | ---: |",
    ]
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
    for key in REQUIRED_FIELDS + OPTIONAL_FIELDS:
        present = int(field_coverage.get(key, 0))
        lines.append(f"| `{key}` | {present:,} | {pct(present, cards)} |")
    lines.extend(
        [
            f"| Tatoeba provenance missing | {meta['tatoebaProvenanceMissing']:,} | {pct(meta['tatoebaProvenanceMissing'], cards)} |",
            f"| invalid target offsets | {meta['badTargetOffsets']:,} | {pct(meta['badTargetOffsets'], cards)} |",
            "",
            "### Token metadata",
            "",
            f"Tokens inspected: **{meta['tokens']:,}**. Lemma differs from the written surface for **{meta['tokensWithNonIdentityLemma']:,}** tokens ({pct(meta['tokensWithNonIdentityLemma'], meta['tokens'])}). A zero or very small number means the published catalogue does not yet carry much real morphology.",
            "",
            "`pos` values: " + (", ".join(f"`{name}` {count:,}" for name, count in meta["posValues"].items()) or "none"),
            "",
            "## Cards by learning language",
            "",
            "| language | cards |",
            "| --- | ---: |",
        ]
    )
    for lang, count in data["breakdown"]["cardsByLearningLanguage"].items():
        lines.append(f"| `{lang}` | {count:,} |")

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
                f"Cards carrying this target: {group.cards}",
                "",
            ]
        )
        for key, context in list(group.contexts.items())[:5]:
            source = key.removeprefix("tatoeba:") if key.startswith("tatoeba:") else None
            safe = context.replace("\n", " ").strip()
            label = f"Tatoeba #{source}" if source else "source id unavailable"
            lines.append(f"- {label}: {safe}")
        if len(group.contexts) > 5:
            lines.append(f"- ... {len(group.contexts) - 5} more contexts")
        lines.append("")

    lines.extend(
        [
            "## Integrity",
            "",
            "| check | count |",
            "| --- | ---: |",
            f"| JSON parse errors | {cat['parseErrors']:,} |",
            f"| empty lines | {cat['emptyLines']:,} |",
            f"| duplicate card IDs | {cat['duplicateCardIds']:,} |",
            "",
        ]
    )
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Inspect metadata and target/context reuse in an ikna catalogue.")
    parser.add_argument("--dir", required=True, type=Path, help="directory containing index.json and deck .jsonl assets")
    parser.add_argument("--json", dest="json_out", type=Path, help="write machine-readable report here")
    parser.add_argument("--markdown", dest="md_out", type=Path, help="write human-readable report here")
    parser.add_argument("--top", type=int, default=50, help="number of multi-context target groups to show")
    parser.add_argument("--groups-jsonl", type=Path, help="write every multi-context exact-target group here")
    args = parser.parse_args(argv)

    data, groups = analyse(args.dir)
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
                    "cardCount": group.cards,
                    "meaningLanguages": sorted(group.meanings),
                    "levels": sorted(group.levels),
                    "contexts": [
                        {"sourceKey": key, "text": text}
                        for key, text in group.contexts.items()
                    ],
                }
                handle.write(json.dumps(payload, ensure_ascii=False) + "\n")
    if not args.json_out and not args.md_out and not args.groups_jsonl:
        print(report)

    bad = data["catalogue"]["parseErrors"] + data["metadata"]["badTargetOffsets"]
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
