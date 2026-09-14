#!/usr/bin/env python3
"""Measure lossless cross-deck Catalogue v2 storage layouts.

This is an experiment, not a publisher and not a client format.  It consumes an
already-built Catalogue v2 directory, so a storage question can be answered
without downloading corpora or rebuilding target/context selection.

The current release layout keeps every deck self-contained.  This script compares
that baseline with two normalized layouts:

* pair: one context/meaning pool per learning+meaning language pair;
* language: one context/meaning pool per learning language.

A normalized membership keeps target-local fields in its deck manifest and
references pooled context evidence plus pooled meaning evidence by integer id.
The split is lossless for every current row: the script reconstructs every row in
memory while reading and aborts if any field except the deterministic deck-local
``id`` changes.

The produced pool files are temporary measurement artefacts.  They deliberately
do not change ``index.json`` and are never suitable for publication by this tool.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import math
import os
import shutil
import statistics
import tempfile
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable

DEFAULT_TARGET_MIB = 220.0
DEFAULT_COLD_CAP_MIB = 24.0
MIB = 1024 * 1024

# Fields that describe one source occurrence rather than the learning target.
# Unknown fields are still preserved: fields present in an alternative context
# are added dynamically, and primary-only unknowns remain in the target shell.
CONTEXT_FIELDS = {
    "context",
    "translation",
    "targetStart",
    "targetEnd",
    "freqRank",
    "tokens",
    "ipaContext",
    "contextId",
    "meaningId",
    "sourceFamily",
}
MEANING_FIELDS = {"translation", "meaningId"}


def canonical(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def line_bytes(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8") + b"\n"


def digest(value: Any) -> bytes:
    return hashlib.sha256(canonical(value)).digest()


def open_jsonl(path: Path):
    if path.name.endswith(".gz"):
        return gzip.open(path, "rt", encoding="utf-8")
    return path.open("r", encoding="utf-8")


class DeterministicGzipWriter:
    def __init__(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.path = path
        self.raw = path.open("wb")
        self.gz = gzip.GzipFile(filename="", mode="wb", compresslevel=9, mtime=0, fileobj=self.raw)

    def write(self, value: Any) -> None:
        self.gz.write(line_bytes(value))

    def close(self) -> None:
        self.gz.close()
        self.raw.close()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        self.close()


def percentile(values: list[int], fraction: float) -> int:
    if not values:
        return 0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, math.ceil(len(ordered) * fraction) - 1))
    return ordered[index]


def fmt_mib(value: int) -> str:
    return f"{value / MIB:.1f} MiB"


def split_row(row: dict[str, Any]) -> tuple[dict[str, Any], list[tuple[dict[str, Any], dict[str, Any]]]]:
    """Split one deck row into a target shell and ordered context/meaning pairs."""
    alternatives = row.get("contexts") or []
    if not isinstance(alternatives, list) or not all(isinstance(item, dict) for item in alternatives):
        raise ValueError("contexts must be a list of objects")

    context_keys = set(CONTEXT_FIELDS)
    for alternative in alternatives:
        context_keys.update(alternative.keys())

    shell = {
        key: value
        for key, value in row.items()
        if key not in context_keys and key not in {"id", "contexts"}
    }

    def split_context(context: dict[str, Any]) -> tuple[dict[str, Any], dict[str, Any]]:
        core = {key: value for key, value in context.items() if key not in MEANING_FIELDS}
        meaning = {key: value for key, value in context.items() if key in MEANING_FIELDS}
        return core, meaning

    primary = {key: row[key] for key in context_keys if key in row}
    contexts = [split_context(primary)] + [split_context(item) for item in alternatives]
    return shell, contexts


def reconstruct_row(
    shell: dict[str, Any],
    contexts: list[tuple[dict[str, Any], dict[str, Any]]],
) -> dict[str, Any]:
    if not contexts:
        raise ValueError("membership has no primary context")
    primary_core, primary_meaning = contexts[0]
    result = {**shell, **primary_core, **primary_meaning}
    if len(contexts) > 1:
        result["contexts"] = [{**core, **meaning} for core, meaning in contexts[1:]]
    return result


def row_without_local_id(row: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in row.items() if key != "id"}


def load_index(catalogue: Path) -> dict[str, Any]:
    path = catalogue / "index.json"
    if not path.is_file():
        raise ValueError(f"missing Catalogue index: {path}")
    index = json.loads(path.read_text(encoding="utf-8"))
    if index.get("version") != 2 or index.get("catalogueVersion") != 2:
        raise ValueError("storage experiment only accepts Catalogue v2 builds")
    decks = index.get("decks") or []
    if not decks:
        raise ValueError("Catalogue index contains no decks")
    return index


def deck_path(catalogue: Path, deck: dict[str, Any]) -> Path:
    name = deck.get("file")
    if not isinstance(name, str) or "/" in name or "\\" in name:
        raise ValueError(f"unsafe deck file in index: {name!r}")
    path = catalogue / name
    if not path.is_file():
        raise ValueError(f"missing deck asset: {path}")
    return path


def grouping_key(deck: dict[str, Any], mode: str) -> tuple[str, ...]:
    if mode == "pair":
        return (str(deck["lang"]), str(deck["meaningLang"]))
    if mode == "language":
        return (str(deck["lang"]),)
    raise ValueError(f"unknown mode: {mode}")


def group_slug(key: tuple[str, ...]) -> str:
    return "-".join(key)


def baseline(catalogue: Path, decks: list[dict[str, Any]]) -> dict[str, Any]:
    per_deck = []
    total = 0
    raw_total = 0
    for deck in decks:
        path = deck_path(catalogue, deck)
        size = path.stat().st_size
        declared = int(deck.get("sizeBytes") or 0)
        if declared and declared != size:
            raise ValueError(f"{deck['id']}: sizeBytes={declared}, file={size}")
        raw = int(deck.get("uncompressedSizeBytes") or 0)
        total += size
        raw_total += raw
        per_deck.append({"id": deck["id"], "bytes": size, "rawBytes": raw})
    sizes = [row["bytes"] for row in per_deck]
    return {
        "compressedBytes": total,
        "rawBytes": raw_total,
        "deckCount": len(decks),
        "medianDeckBytes": int(statistics.median(sizes)) if sizes else 0,
        "p95DeckBytes": percentile(sizes, 0.95),
        "maxDeckBytes": max(sizes, default=0),
        "decks": per_deck,
    }


def compact_group(
    catalogue: Path,
    decks: list[dict[str, Any]],
    out: Path,
) -> dict[str, Any]:
    out.mkdir(parents=True, exist_ok=True)
    core_ids: dict[bytes, int] = {}
    meaning_ids: dict[bytes, int] = {}
    core_occurrences = 0
    meaning_occurrences = 0
    memberships = 0
    contexts = 0
    lossless_rows = 0
    manifest_sizes: dict[str, int] = {}

    cores_path = out / "contexts.jsonl.gz"
    meanings_path = out / "meanings.jsonl.gz"

    with DeterministicGzipWriter(cores_path) as core_writer, DeterministicGzipWriter(meanings_path) as meaning_writer:
        for deck in decks:
            manifest_path = out / (deck["id"] + ".refs.jsonl.gz")
            with DeterministicGzipWriter(manifest_path) as manifest:
                with open_jsonl(deck_path(catalogue, deck)) as handle:
                    position = 0
                    for line in handle:
                        if not line.strip():
                            continue
                        position += 1
                        row = json.loads(line)
                        expected_id = f"{deck['id']}-{position:05d}"
                        if row.get("id") != expected_id:
                            raise ValueError(
                                f"{deck['id']} row {position}: id {row.get('id')!r} != {expected_id!r}"
                            )
                        shell, split_contexts = split_row(row)
                        rebuilt = reconstruct_row(shell, split_contexts)
                        if canonical(rebuilt) != canonical(row_without_local_id(row)):
                            raise AssertionError(f"lossless split failed: {deck['id']} row {position}")
                        lossless_rows += 1

                        refs: list[list[int]] = []
                        for core, meaning in split_contexts:
                            core_occurrences += 1
                            meaning_occurrences += 1
                            contexts += 1

                            core_hash = digest(core)
                            core_id = core_ids.get(core_hash)
                            if core_id is None:
                                core_id = len(core_ids)
                                core_ids[core_hash] = core_id
                                core_writer.write(core)

                            meaning_hash = digest(meaning)
                            meaning_id = meaning_ids.get(meaning_hash)
                            if meaning_id is None:
                                meaning_id = len(meaning_ids)
                                meaning_ids[meaning_hash] = meaning_id
                                meaning_writer.write(meaning)

                            refs.append([core_id, meaning_id])

                        manifest.write({"t": shell, "c": refs})
                        memberships += 1

                expected = int(deck.get("chunkCount") or 0)
                if expected and position != expected:
                    raise ValueError(f"{deck['id']}: chunkCount={expected}, rows={position}")
            manifest_sizes[deck["id"]] = manifest_path.stat().st_size

    group_info = {
        "decks": [deck["id"] for deck in decks],
        "contexts": "contexts.jsonl.gz",
        "meanings": "meanings.jsonl.gz",
        "format": "catalogue-v2-storage-experiment-1",
    }
    group_path = out / "group.json"
    group_path.write_bytes(canonical(group_info) + b"\n")

    core_bytes = cores_path.stat().st_size
    meaning_bytes = meanings_path.stat().st_size
    group_bytes = group_path.stat().st_size
    manifests_bytes = sum(manifest_sizes.values())
    total = core_bytes + meaning_bytes + group_bytes + manifests_bytes
    cold = {
        deck_id: core_bytes + meaning_bytes + group_bytes + manifest_size
        for deck_id, manifest_size in manifest_sizes.items()
    }
    return {
        "bytes": total,
        "contextPoolBytes": core_bytes,
        "meaningPoolBytes": meaning_bytes,
        "groupMetadataBytes": group_bytes,
        "manifestBytes": manifests_bytes,
        "manifestSizes": manifest_sizes,
        "coldDeckBytes": cold,
        "memberships": memberships,
        "contexts": contexts,
        "losslessRows": lossless_rows,
        "contextOccurrences": core_occurrences,
        "uniqueContextPayloads": len(core_ids),
        "meaningOccurrences": meaning_occurrences,
        "uniqueMeaningPayloads": len(meaning_ids),
    }


def run_mode(
    catalogue: Path,
    decks: list[dict[str, Any]],
    mode: str,
    mode_root: Path,
) -> dict[str, Any]:
    groups: dict[tuple[str, ...], list[dict[str, Any]]] = defaultdict(list)
    for deck in decks:
        groups[grouping_key(deck, mode)].append(deck)

    result_groups = []
    total_bytes = 0
    total_memberships = 0
    total_contexts = 0
    total_lossless = 0
    context_occurrences = 0
    unique_contexts = 0
    meaning_occurrences = 0
    unique_meanings = 0
    cold_sizes: list[int] = []
    warm_sizes: list[int] = []
    max_shared_pool = 0

    for key in sorted(groups):
        selected = sorted(groups[key], key=lambda row: row["id"])
        group = compact_group(catalogue, selected, mode_root / group_slug(key))
        group["key"] = list(key)
        group["deckCount"] = len(selected)
        result_groups.append(group)
        total_bytes += group["bytes"]
        total_memberships += group["memberships"]
        total_contexts += group["contexts"]
        total_lossless += group["losslessRows"]
        context_occurrences += group["contextOccurrences"]
        unique_contexts += group["uniqueContextPayloads"]
        meaning_occurrences += group["meaningOccurrences"]
        unique_meanings += group["uniqueMeaningPayloads"]
        cold_sizes.extend(group["coldDeckBytes"].values())
        warm_sizes.extend(group["manifestSizes"].values())
        max_shared_pool = max(
            max_shared_pool,
            group["contextPoolBytes"] + group["meaningPoolBytes"] + group["groupMetadataBytes"],
        )

    return {
        "mode": mode,
        "groupCount": len(groups),
        "bytes": total_bytes,
        "memberships": total_memberships,
        "contexts": total_contexts,
        "losslessRows": total_lossless,
        "contextOccurrences": context_occurrences,
        "uniqueContextPayloads": unique_contexts,
        "meaningOccurrences": meaning_occurrences,
        "uniqueMeaningPayloads": unique_meanings,
        "medianColdDeckBytes": int(statistics.median(cold_sizes)) if cold_sizes else 0,
        "p95ColdDeckBytes": percentile(cold_sizes, 0.95),
        "maxColdDeckBytes": max(cold_sizes, default=0),
        "medianWarmDeckBytes": int(statistics.median(warm_sizes)) if warm_sizes else 0,
        "maxSharedPoolBytes": max_shared_pool,
        "maxDecksPerGroup": max((group["deckCount"] for group in result_groups), default=0),
        "groups": result_groups,
    }


def recommendation(
    baseline_bytes: int,
    modes: list[dict[str, Any]],
    target_bytes: int,
    cold_cap_bytes: int,
) -> dict[str, Any]:
    pair = next((row for row in modes if row["mode"] == "pair"), None)
    if pair is not None:
        size_ok = pair["bytes"] <= target_bytes
        cold_ok = pair["maxColdDeckBytes"] <= cold_cap_bytes
        if size_ok and cold_ok:
            return {
                "decision": "pair-candidate",
                "reason": "pair pooling passes both the total-size and one-deck cold-install gates",
            }
        failed = []
        if not size_ok:
            failed.append("total size")
        if not cold_ok:
            failed.append("cold install")
        pair_reason = " and ".join(failed)
    else:
        pair_reason = "pair mode was not measured"

    language = next((row for row in modes if row["mode"] == "language"), None)
    if language is not None and language["bytes"] <= target_bytes:
        return {
            "decision": "reject-simple-pooling",
            "reason": (
                f"pair pooling misses the acceptance gate ({pair_reason}); language pooling is only a "
                "diagnostic upper bound because it makes one deck depend on a whole learning-language pool"
            ),
        }
    return {
        "decision": "keep-self-contained",
        "reason": f"simple cross-deck pooling does not justify a client-format migration ({pair_reason})",
    }


def render_report(result: dict[str, Any]) -> str:
    base = result["baseline"]
    lines = [
        "# Catalogue v2 storage experiment",
        "",
        "This report repacks an **existing built Catalogue v2**. It does not rebuild corpora,",
        "change target/context selection, or publish anything.",
        "",
        "## Baseline",
        "",
        "| metric | value |",
        "| --- | ---: |",
        f"| self-contained deck assets | {fmt_mib(base['compressedBytes'])} |",
        f"| raw JSONL declared by index | {fmt_mib(base['rawBytes'])} |",
        f"| decks | {base['deckCount']:,} |",
        f"| median one-deck download | {fmt_mib(base['medianDeckBytes'])} |",
        f"| p95 one-deck download | {fmt_mib(base['p95DeckBytes'])} |",
        f"| largest one-deck download | {fmt_mib(base['maxDeckBytes'])} |",
        "",
        "## Lossless normalized layouts",
        "",
        "`pair` shares pools only inside one learning/meaning language pair. `language`",
        "shares across all meaning languages and is therefore a storage upper bound with a",
        "much worse cold-install dependency. Neither layout is a publication format yet.",
        "",
        "| layout | total | vs baseline | contexts reused | meanings reused | median cold deck | max cold deck |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for mode in result["modes"]:
        ratio = mode["bytes"] / base["compressedBytes"] if base["compressedBytes"] else 0
        core_reuse = 1.0 - (mode["uniqueContextPayloads"] / mode["contextOccurrences"]) if mode["contextOccurrences"] else 0
        meaning_reuse = 1.0 - (mode["uniqueMeaningPayloads"] / mode["meaningOccurrences"]) if mode["meaningOccurrences"] else 0
        lines.append(
            "| {mode} | {size} | {ratio:.1%} | {core:.1%} | {meaning:.1%} | {median} | {maximum} |".format(
                mode=mode["mode"], size=fmt_mib(mode["bytes"]), ratio=ratio,
                core=core_reuse, meaning=meaning_reuse,
                median=fmt_mib(mode["medianColdDeckBytes"]), maximum=fmt_mib(mode["maxColdDeckBytes"]),
            )
        )

    gates = result["gates"]
    rec = result["recommendation"]
    lines += [
        "",
        "## Acceptance gate",
        "",
        f"- target total size: **<= {fmt_mib(gates['targetBytes'])}**;",
        f"- maximum cold install for one deck under pair pooling: **<= {fmt_mib(gates['coldCapBytes'])}**;",
        "- the transform must be lossless for every membership row.",
        "",
        f"Decision: **{rec['decision']}**.",
        "",
        rec["reason"] + ".",
        "",
        "The `language` result is diagnostic only. Even a small total there is not permission",
        "to make somebody download an entire learning language merely to install one deck.",
        "",
        "## Integrity",
        "",
    ]
    expected_memberships = result.get("expectedMemberships")
    expected_contexts = result.get("expectedContexts")
    for mode in result["modes"]:
        membership_ok = expected_memberships is None or mode["memberships"] == expected_memberships
        context_ok = expected_contexts is None or mode["contexts"] == expected_contexts
        rows_ok = mode["losslessRows"] == mode["memberships"]
        lines.append(
            f"- `{mode['mode']}`: memberships {mode['memberships']:,} ({'OK' if membership_ok else 'MISMATCH'}), "
            f"contexts {mode['contexts']:,} ({'OK' if context_ok else 'MISMATCH'}), "
            f"lossless rows {mode['losslessRows']:,}/{mode['memberships']:,} ({'OK' if rows_ok else 'MISMATCH'})."
        )
    lines.append("")
    return "\n".join(lines)


def parse_modes(text: str) -> list[str]:
    modes = [part.strip().lower() for part in text.split(",") if part.strip()]
    allowed = {"pair", "language"}
    if not modes or any(mode not in allowed for mode in modes):
        raise argparse.ArgumentTypeError("modes must be pair, language, or pair,language")
    # Preserve order, remove duplicates.
    return list(dict.fromkeys(modes))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dir", required=True, type=Path, help="existing Catalogue v2 build directory")
    parser.add_argument("--work", type=Path, help="temporary output directory; deleted unless --keep-work")
    parser.add_argument("--report", type=Path, default=Path("storage-experiment.md"))
    parser.add_argument("--json", dest="json_path", type=Path, default=Path("storage-experiment.json"))
    parser.add_argument("--modes", type=parse_modes, default=parse_modes("pair,language"))
    parser.add_argument("--target-mib", type=float, default=DEFAULT_TARGET_MIB)
    parser.add_argument("--cold-cap-mib", type=float, default=DEFAULT_COLD_CAP_MIB)
    parser.add_argument("--keep-work", action="store_true")
    args = parser.parse_args()

    catalogue = args.dir.resolve()
    index = load_index(catalogue)
    decks = sorted(index["decks"], key=lambda row: row["id"])
    base = baseline(catalogue, decks)

    owns_work = args.work is None
    if owns_work:
        work = Path(tempfile.mkdtemp(prefix="ikna-catalog-storage-"))
    else:
        work = args.work.resolve()
        if work.exists():
            shutil.rmtree(work)
        work.mkdir(parents=True)

    try:
        measured = []
        for mode in args.modes:
            mode_root = work / mode
            print(f"Measuring {mode} pooling across {len(decks)} decks...", flush=True)
            measured.append(run_mode(catalogue, decks, mode, mode_root))
            if not args.keep_work:
                shutil.rmtree(mode_root)

        build = {}
        build_path = catalogue / "BUILD.json"
        if build_path.is_file():
            build = json.loads(build_path.read_text(encoding="utf-8"))
        output = build.get("output") or {}
        expected_memberships = output.get("targetDeckMemberships")
        expected_contexts = output.get("contexts")

        for mode in measured:
            if expected_memberships is not None and mode["memberships"] != int(expected_memberships):
                raise AssertionError(
                    f"{mode['mode']}: membership census mismatch {mode['memberships']} != {expected_memberships}"
                )
            if expected_contexts is not None and mode["contexts"] != int(expected_contexts):
                raise AssertionError(f"{mode['mode']}: context census mismatch {mode['contexts']} != {expected_contexts}")
            if mode["losslessRows"] != mode["memberships"]:
                raise AssertionError(f"{mode['mode']}: not every membership passed lossless reconstruction")

        result = {
            "format": 1,
            "catalogueVersion": index.get("catalogueVersion"),
            "baseline": base,
            "modes": measured,
            "expectedMemberships": expected_memberships,
            "expectedContexts": expected_contexts,
            "gates": {
                "targetBytes": int(args.target_mib * MIB),
                "coldCapBytes": int(args.cold_cap_mib * MIB),
            },
        }
        result["recommendation"] = recommendation(
            base["compressedBytes"], measured,
            result["gates"]["targetBytes"], result["gates"]["coldCapBytes"],
        )

        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.json_path.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(render_report(result), encoding="utf-8")
        args.json_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(args.report.read_text(encoding="utf-8"), flush=True)
        return 0
    finally:
        if owns_work and not args.keep_work:
            shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    raise SystemExit(main())
