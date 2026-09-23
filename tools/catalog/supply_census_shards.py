#!/usr/bin/env python3
"""Shard-safe helpers for the Catalogue v2 Part 5 supply census.

The production census semantics still live in :mod:`supply_census`. This module
only lets GitHub Actions split expensive WikiMatrix acquisition across jobs
without shipping the multi-gigabyte candidate pool between runners:

* ``rank-pair`` reduces one physical WikiMatrix candidate file to compact token
  counts for both learning directions in a single local pass;
* ``ranks`` merges those compact pair contributions into one learning-language
  frequency ranking while preserving the monolithic file/context order;
* ``measure-pair`` measures one physical WikiMatrix pair with those global ranks;
* ``measure-everyday`` measures the Tatoeba-only Everyday collection locally;
* ``assemble`` validates all shards and builds the normal Part 5 report envelope.

Candidate JSONL remains local to the job that produced it. Only compressed rank
maps, pair metrics, logs and inventory rows need to cross job boundaries.
"""
from __future__ import annotations

import argparse
import gzip
import json
import sqlite3
import sys
from collections import Counter
from pathlib import Path
from typing import Any, Iterable

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import catalogue_core as core
from build_catalogue_v2 import build_ranks, open_jsonl, primary_origin, stage_candidates
from ingest.model import Candidate
from segmentation import IcuUnavailable, prepare
from supply_census import (
    build_pair_row,
    load_wikimatrix_inventory,
    markdown,
    parse_codes,
    report_from_rows,
)

SHARD_VERSION = 1
RANK_MAGIC = "ikna-supply-ranks-v1"
RANK_COUNTS_MAGIC = "ikna-supply-rank-counts-v1"


def _prepare_languages(languages: Iterable[str]) -> None:
    values = list(dict.fromkeys(languages))
    unsupported = set(values) - set(core.LEARNABLE)
    if unsupported:
        raise ValueError("unsupported languages: %s" % ", ".join(sorted(unsupported)))
    try:
        prepare(values)
    except IcuUnavailable as exc:
        raise ValueError(str(exc)) from exc


def _validate_matrix(learn: list[str], meanings: list[str]) -> None:
    if not learn or not meanings:
        raise ValueError("learn/meanings must not be empty")
    unsupported = (set(learn) - set(core.LEARNABLE)) | (set(meanings) - set(core.MEANINGS))
    if unsupported:
        raise ValueError("unsupported languages: %s" % ", ".join(sorted(unsupported)))
    _prepare_languages(learn)


def build_rank_counts(
    candidate_path: str,
    collection: str,
    lang: str,
    expected_meaning: str | None = None,
) -> tuple[Counter[str], str | None]:
    """Count one physical-pair file in first-seen token order.

    Returning counts separately lets the workflow discard each large candidate
    file immediately. A compact count shard retains exactly the information
    needed to reproduce ``Counter.most_common`` after all physical pairs for one
    learning language have been scanned.
    """
    _prepare_languages([lang])
    counts: Counter[str] = Counter()
    seen_ids: set[str] = set()
    seen_contexts: set[tuple[str, str]] = set()
    file_meanings: set[str] = set()
    with open_jsonl(candidate_path) as handle:
        for number, line in enumerate(handle, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                record = Candidate.from_dict(json.loads(line))
            except Exception as exc:
                raise ValueError(f"{candidate_path}:{number}: {exc}") from exc
            if record.collection != collection or record.lang != lang:
                continue
            file_meanings.add(record.meaning_lang)
            if record.id in seen_ids:
                continue
            seen_ids.add(record.id)
            origin = primary_origin(record)
            context_key = (origin["sourceFamily"], origin["contextRef"])
            if context_key in seen_contexts:
                continue
            seen_contexts.add(context_key)
            counts.update(word.lower() for word in core.words(record.context, lang))

    if len(file_meanings) > 1:
        raise ValueError(
            f"{candidate_path} contains several {lang} meaning languages: {sorted(file_meanings)}"
        )
    meaning = next(iter(file_meanings), None)
    if expected_meaning is not None and meaning is not None and meaning != expected_meaning:
        raise ValueError(
            f"{candidate_path} contains {lang}->{meaning}, expected {lang}->{expected_meaning}"
        )
    return counts, meaning


def build_rank_counts_for_pair(
    candidate_path: str,
    collection: str,
    first: str,
    second: str,
) -> dict[str, Counter[str]]:
    """Count both learning directions from one physical-pair candidate file.

    ``wikimatrix-pair`` emits the two directions next to each other. The old
    sharded workflow reopened that large gzip once per learning language; this
    keeps identical per-language de-duplication and first-seen token order while
    reducing the local candidate file in one pass.
    """
    if first == second:
        raise ValueError("physical pair languages must differ")
    _prepare_languages([first, second])
    other = {first: second, second: first}
    counts = {first: Counter(), second: Counter()}
    seen_ids = {first: set(), second: set()}
    seen_contexts = {first: set(), second: set()}
    with open_jsonl(candidate_path) as handle:
        for number, line in enumerate(handle, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                record = Candidate.from_dict(json.loads(line))
            except Exception as exc:
                raise ValueError(f"{candidate_path}:{number}: {exc}") from exc
            if record.collection != collection or record.lang not in counts:
                continue
            lang = record.lang
            if record.meaning_lang != other[lang]:
                raise ValueError(
                    f"{candidate_path} contains {lang}->{record.meaning_lang}, "
                    f"expected {lang}->{other[lang]}"
                )
            if record.id in seen_ids[lang]:
                continue
            seen_ids[lang].add(record.id)
            origin = primary_origin(record)
            context_key = (origin["sourceFamily"], origin["contextRef"])
            if context_key in seen_contexts[lang]:
                continue
            seen_contexts[lang].add(context_key)
            counts[lang].update(word.lower() for word in core.words(record.context, lang))
    return counts


def build_rank_map(candidate_paths: list[str], collection: str, lang: str) -> dict[str, int]:
    """Reproduce ``build_ranks`` without staging every other learning language.

    Candidate files must be in the same lexical order used by the monolithic
    ``candidates/*.jsonl.gz`` expansion. Candidate identity includes meaning
    language, so IDs cannot collide across those files. We validate one physical
    shard per directed pair and preserve first-seen token order across shards.
    """
    counts: Counter[str] = Counter()
    seen_meanings: set[str] = set()
    for path in candidate_paths:
        part, meaning = build_rank_counts(path, collection, lang)
        if meaning is not None:
            if meaning in seen_meanings:
                raise ValueError(f"duplicate physical shard for directed pair {lang}->{meaning}")
            seen_meanings.add(meaning)
        counts.update(part)
    return {form: position for position, (form, _count) in enumerate(counts.most_common(), start=1)}


def write_rank_counts(
    path: str | Path,
    collection: str,
    lang: str,
    meaning: str,
    counts: Counter[str],
) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    opener = gzip.open if str(target).endswith(".gz") else open
    with opener(target, "wt", encoding="utf-8", newline="") as handle:
        handle.write(f"#{RANK_COUNTS_MAGIC}\t{collection}\t{lang}\t{meaning}\t{len(counts)}\n")
        for form, count in counts.items():
            if count < 1:
                raise ValueError(f"rank token has non-positive count: {form!r}")
            if "\t" in form or "\n" in form or "\r" in form:
                raise ValueError(f"rank token contains a line separator: {form!r}")
            handle.write(f"{count}\t{form}\n")


def load_rank_counts(
    path: str | Path,
    expected_collection: str,
    expected_lang: str,
) -> tuple[str, Counter[str]]:
    source = Path(path)
    opener = gzip.open if str(source).endswith(".gz") else open
    with opener(source, "rt", encoding="utf-8") as handle:
        header = handle.readline().rstrip("\n").split("\t")
        if len(header) != 5 or header[:3] != [
            f"#{RANK_COUNTS_MAGIC}", expected_collection, expected_lang
        ]:
            raise ValueError(f"unexpected rank-count header in {source}")
        meaning = header[3]
        try:
            expected_count = int(header[4])
        except ValueError as exc:
            raise ValueError(f"invalid rank-count size in {source}") from exc
        counts: Counter[str] = Counter()
        for number, raw in enumerate(handle, start=2):
            parts = raw.rstrip("\n").split("\t", 1)
            if len(parts) != 2:
                raise ValueError(f"{source}:{number}: malformed rank-count row")
            try:
                count = int(parts[0])
            except ValueError as exc:
                raise ValueError(f"{source}:{number}: invalid count") from exc
            form = parts[1]
            if count < 1 or not form or form in counts:
                raise ValueError(f"{source}:{number}: non-canonical rank-count row")
            counts[form] = count
        if len(counts) != expected_count:
            raise ValueError(
                f"rank-count size mismatch in {source}: expected {expected_count}, got {len(counts)}"
            )
        return meaning, counts


def build_rank_map_from_parts(
    part_paths: list[str],
    collection: str,
    lang: str,
) -> dict[str, int]:
    _prepare_languages([lang])
    counts: Counter[str] = Counter()
    seen_meanings: set[str] = set()
    for path in part_paths:
        meaning, part = load_rank_counts(path, collection, lang)
        if meaning in seen_meanings:
            raise ValueError(f"duplicate rank-count shard for directed pair {lang}->{meaning}")
        seen_meanings.add(meaning)
        counts.update(part)
    return {form: position for position, (form, _count) in enumerate(counts.most_common(), start=1)}

def write_ranks(path: str | Path, collection: str, lang: str, ranks: dict[str, int]) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    opener = gzip.open if str(target).endswith(".gz") else open
    with opener(target, "wt", encoding="utf-8", newline="") as handle:
        handle.write(f"#{RANK_MAGIC}\t{collection}\t{lang}\t{len(ranks)}\n")
        for form, rank in sorted(ranks.items(), key=lambda item: item[1]):
            if "\t" in form or "\n" in form or "\r" in form:
                raise ValueError(f"rank token contains a line separator: {form!r}")
            handle.write(f"{rank}\t{form}\n")


def load_ranks(path: str | Path, expected_collection: str, expected_lang: str) -> dict[str, int]:
    source = Path(path)
    opener = gzip.open if str(source).endswith(".gz") else open
    with opener(source, "rt", encoding="utf-8") as handle:
        header = handle.readline().rstrip("\n")
        expected_prefix = f"#{RANK_MAGIC}\t{expected_collection}\t{expected_lang}\t"
        if not header.startswith(expected_prefix):
            raise ValueError(f"unexpected rank header in {source}: {header!r}")
        try:
            expected_count = int(header[len(expected_prefix):])
        except ValueError as exc:
            raise ValueError(f"invalid rank count in {source}") from exc
        ranks: dict[str, int] = {}
        expected_rank = 1
        for number, raw in enumerate(handle, start=2):
            raw = raw.rstrip("\n")
            parts = raw.split("\t", 1)
            if len(parts) != 2:
                raise ValueError(f"{source}:{number}: malformed rank row")
            try:
                rank = int(parts[0])
            except ValueError as exc:
                raise ValueError(f"{source}:{number}: invalid rank") from exc
            form = parts[1]
            if rank != expected_rank or not form or form in ranks:
                raise ValueError(f"{source}:{number}: non-canonical rank sequence")
            ranks[form] = rank
            expected_rank += 1
        if len(ranks) != expected_count:
            raise ValueError(f"rank count mismatch in {source}: expected {expected_count}, got {len(ranks)}")
        return ranks


def measure_pair_shard(
    candidate: str,
    staging: str | Path,
    first: str,
    second: str,
    ranks_dir: str | Path,
    inventory_path: str,
    learn: list[str],
    meanings: list[str],
    max_deck: int,
    min_deck: int,
    function_top: int,
) -> dict[str, Any]:
    _validate_matrix(learn, meanings)
    if first == second:
        raise ValueError("physical pair languages must differ")
    expected_directions = []
    if first in learn and second in meanings:
        expected_directions.append((first, second))
    if second in learn and first in meanings:
        expected_directions.append((second, first))
    inventory = load_wikimatrix_inventory(inventory_path)
    expected_key = tuple(sorted((first, second)))
    if set(inventory) != {expected_key}:
        raise ValueError("pair inventory must contain exactly %s-%s" % expected_key)

    rank_root = Path(ranks_dir)
    rank_cache: dict[str, dict[str, int]] = {}
    for lang, _meaning in expected_directions:
        rank_path = rank_root / f"ranks-{lang}.tsv.gz"
        rank_cache[lang] = load_ranks(rank_path, "knowledge", lang)

    staging_path = Path(staging)
    try:
        stage = stage_candidates([candidate], staging_path)
        db = sqlite3.connect(staging_path)
        try:
            rows = [
                build_pair_row(
                    db, "knowledge", "wikimatrix", lang, meaning_lang, rank_cache[lang],
                    max_deck, min_deck, function_top, inventory,
                )
                for lang, meaning_lang in expected_directions
            ]
        finally:
            db.close()
        return {
            "shardVersion": SHARD_VERSION,
            "kind": "knowledge-pair",
            "physicalPair": [first, second],
            "stage": stage,
            "rows": rows,
        }
    finally:
        if staging_path.exists():
            staging_path.unlink()


def measure_everyday_shard(
    candidate: str,
    staging: str | Path,
    learn: list[str],
    meanings: list[str],
    max_deck: int,
    min_deck: int,
    function_top: int,
) -> dict[str, Any]:
    _validate_matrix(learn, meanings)
    staging_path = Path(staging)
    stage = stage_candidates([candidate], staging_path)
    db = sqlite3.connect(staging_path)
    rows: list[dict[str, Any]] = []
    try:
        for lang in learn:
            ranks = build_ranks(db, "everyday", lang)
            for meaning_lang in meanings:
                if lang == meaning_lang:
                    continue
                rows.append(
                    build_pair_row(
                        db, "everyday", "tatoeba", lang, meaning_lang, ranks,
                        max_deck, min_deck, function_top,
                    )
                )
    finally:
        db.close()
        if staging_path.exists():
            staging_path.unlink()
    return {
        "shardVersion": SHARD_VERSION,
        "kind": "everyday",
        "stage": stage,
        "rows": rows,
    }


def _load_shard(path: str | Path, kind: str) -> dict[str, Any]:
    value = json.loads(Path(path).read_text(encoding="utf-8"))
    if value.get("shardVersion") != SHARD_VERSION or value.get("kind") != kind:
        raise ValueError(f"unexpected {kind} shard: {path}")
    if not isinstance(value.get("stage"), dict) or not isinstance(value.get("rows"), list):
        raise ValueError(f"malformed {kind} shard: {path}")
    return value


def assemble_report(
    everyday_path: str,
    knowledge_paths: list[str],
    learn: list[str],
    meanings: list[str],
    max_deck: int,
    min_deck: int,
    function_top: int,
) -> dict[str, Any]:
    _validate_matrix(learn, meanings)
    everyday = _load_shard(everyday_path, "everyday")
    knowledge = [_load_shard(path, "knowledge-pair") for path in knowledge_paths]
    rows = list(everyday["rows"])
    for shard in knowledge:
        rows.extend(shard["rows"])

    expected = {
        (collection, lang, meaning)
        for collection in ("everyday", "knowledge")
        for lang in learn
        for meaning in meanings
        if lang != meaning
    }
    actual_keys = [(row.get("collection"), row.get("lang"), row.get("meaningLang")) for row in rows]
    if len(actual_keys) != len(set(actual_keys)):
        raise ValueError("duplicate directed rows in sharded census")
    if set(actual_keys) != expected:
        missing = sorted(expected - set(actual_keys))
        extra = sorted(set(actual_keys) - expected)
        raise ValueError(f"sharded census row mismatch; missing={missing[:8]} extra={extra[:8]}")

    union = sorted(set(learn) | set(meanings))
    physical_expected = {
        (union[left], union[right])
        for left in range(len(union))
        for right in range(left + 1, len(union))
    }
    physical_actual = [tuple(sorted(shard.get("physicalPair") or [])) for shard in knowledge]
    if len(physical_actual) != len(set(physical_actual)) or set(physical_actual) != physical_expected:
        raise ValueError("knowledge physical-pair shards are incomplete or duplicated")

    order_collection = {"everyday": 0, "knowledge": 1}
    order_lang = {lang: index for index, lang in enumerate(learn)}
    order_meaning = {lang: index for index, lang in enumerate(meanings)}
    rows.sort(key=lambda row: (
        order_collection[row["collection"]],
        order_lang[row["lang"]],
        order_meaning[row["meaningLang"]],
    ))

    stages = [everyday["stage"], *(shard["stage"] for shard in knowledge)]
    stage = {
        name: sum(int(part.get(name, 0)) for part in stages)
        for name in ("inputCandidates", "uniqueCandidates", "duplicateCandidates", "uniqueContexts")
    }
    # Physical shards have disjoint directed-pair identity, so their staged
    # pair counts are additive even when a custom learn/meaning matrix does not
    # measure every staged direction. Collection counts are not additive: all
    # Knowledge pair shards belong to the same collection.
    stage["pairs"] = sum(int(part.get("pairs", 0)) for part in stages)
    stage["collections"] = (
        (1 if int(everyday["stage"].get("collections", 0)) > 0 else 0)
        + (1 if any(int(shard["stage"].get("collections", 0)) > 0 for shard in knowledge) else 0)
    )

    return report_from_rows(learn, meanings, max_deck, min_deck, function_top, stage, rows)


def _write_json(path: str | Path, value: dict[str, Any]) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    commands = root.add_subparsers(dest="command", required=True)

    ranks = commands.add_parser("ranks")
    ranks.add_argument("--candidates", nargs="*", default=[])
    ranks.add_argument("--parts", nargs="*", default=[])
    ranks.add_argument("--collection", default="knowledge")
    ranks.add_argument("--lang", required=True)
    ranks.add_argument("--out", required=True)

    rank_pair = commands.add_parser("rank-pair")
    rank_pair.add_argument("--candidate", required=True)
    rank_pair.add_argument("--collection", default="knowledge")
    rank_pair.add_argument("--first", required=True)
    rank_pair.add_argument("--second", required=True)
    rank_pair.add_argument("--out-dir", required=True)

    pair = commands.add_parser("measure-pair")
    pair.add_argument("--candidate", required=True)
    pair.add_argument("--inventory", required=True)
    pair.add_argument("--first", required=True)
    pair.add_argument("--second", required=True)
    pair.add_argument("--ranks-dir", required=True)
    pair.add_argument("--learn", default=",".join(core.LEARNABLE))
    pair.add_argument("--meanings", default=",".join(core.MEANINGS))
    pair.add_argument("--staging", required=True)
    pair.add_argument("--out", required=True)
    pair.add_argument("--max-deck", type=int, default=8000)
    pair.add_argument("--min-deck", type=int, default=40)
    pair.add_argument("--function-top", type=int, default=core.FUNCTION_TOP)

    everyday = commands.add_parser("measure-everyday")
    everyday.add_argument("--candidate", required=True)
    everyday.add_argument("--learn", default=",".join(core.LEARNABLE))
    everyday.add_argument("--meanings", default=",".join(core.MEANINGS))
    everyday.add_argument("--staging", required=True)
    everyday.add_argument("--out", required=True)
    everyday.add_argument("--max-deck", type=int, default=8000)
    everyday.add_argument("--min-deck", type=int, default=40)
    everyday.add_argument("--function-top", type=int, default=core.FUNCTION_TOP)

    assemble = commands.add_parser("assemble")
    assemble.add_argument("--everyday", required=True)
    assemble.add_argument("--knowledge-shards", nargs="+", required=True)
    assemble.add_argument("--learn", default=",".join(core.LEARNABLE))
    assemble.add_argument("--meanings", default=",".join(core.MEANINGS))
    assemble.add_argument("--max-deck", type=int, default=8000)
    assemble.add_argument("--min-deck", type=int, default=40)
    assemble.add_argument("--function-top", type=int, default=core.FUNCTION_TOP)
    assemble.add_argument("--json", required=True)
    assemble.add_argument("--markdown", required=True)
    return root


def _validate_limits(max_deck: int, min_deck: int) -> None:
    if max_deck < 1 or min_deck < 1:
        raise ValueError("deck limits must be positive")
    if min_deck > max_deck:
        raise ValueError("min_deck cannot exceed max_deck")


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    if args.command == "ranks":
        if args.candidates and args.parts:
            raise ValueError("ranks accepts either --candidates or --parts, not both")
        ranks = (
            build_rank_map_from_parts(args.parts, args.collection, args.lang)
            if args.parts
            else build_rank_map(args.candidates, args.collection, args.lang)
        )
        write_ranks(args.out, args.collection, args.lang, ranks)
        print(json.dumps({"lang": args.lang, "rankCount": len(ranks)}, ensure_ascii=False))
        return 0
    if args.command == "rank-pair":
        counts = build_rank_counts_for_pair(
            args.candidate, args.collection, args.first, args.second
        )
        out_dir = Path(args.out_dir)
        outputs = []
        for lang, meaning in ((args.first, args.second), (args.second, args.first)):
            path = out_dir / f"rank-counts-{lang}-{meaning}.tsv.gz"
            write_rank_counts(path, args.collection, lang, meaning, counts[lang])
            outputs.append({
                "lang": lang,
                "meaningLang": meaning,
                "forms": len(counts[lang]),
            })
        print(json.dumps(
            {"physicalPair": [args.first, args.second], "outputs": outputs},
            ensure_ascii=False,
        ))
        return 0

    _validate_limits(args.max_deck, args.min_deck)
    if args.command == "measure-pair":
        value = measure_pair_shard(
            args.candidate, args.staging, args.first, args.second,
            args.ranks_dir, args.inventory, parse_codes(args.learn), parse_codes(args.meanings),
            args.max_deck, args.min_deck, args.function_top,
        )
        _write_json(args.out, value)
        return 0
    if args.command == "measure-everyday":
        value = measure_everyday_shard(
            args.candidate, args.staging, parse_codes(args.learn), parse_codes(args.meanings),
            args.max_deck, args.min_deck, args.function_top,
        )
        _write_json(args.out, value)
        return 0
    if args.command == "assemble":
        learn = parse_codes(args.learn)
        meanings = parse_codes(args.meanings)
        value = assemble_report(
            args.everyday, args.knowledge_shards, learn, meanings,
            args.max_deck, args.min_deck, args.function_top,
        )
        _write_json(args.json, value)
        Path(args.markdown).parent.mkdir(parents=True, exist_ok=True)
        Path(args.markdown).write_text(markdown(value), encoding="utf-8")
        print(json.dumps(value["summary"], ensure_ascii=False, indent=2))
        return 0
    raise AssertionError(args.command)


if __name__ == "__main__":
    raise SystemExit(main())
