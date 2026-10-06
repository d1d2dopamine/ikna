#!/usr/bin/env python3
"""Part 7: pin and measure an admitted Tatoeba-only Everyday candidate pool.

Consumes local normalized candidates, never downloads or publishes. The complete
deduplicated pool is retained for later selection; samples are evidence only.
EOF proves coverage of the supplied files, not of an unspecified upstream corpus.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import platform
import re
import shutil
import sqlite3
import sys
import tempfile
import zlib
from collections import Counter
from pathlib import Path
from typing import Any

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import catalogue_core as core
from build_catalogue_v2 import build_ranks, stage_candidates
from everyday_rebuild import eligible_from_source
from ingest.model import Candidate, merge_candidate_files, write_jsonl
from ingest.registry import SourceRegistry
from segmentation import prepare

DEFAULT_REGISTRY = HERE / "sources" / "catalogue-v2-sources.json"
MOVING_VERSIONS = {"weekly", "latest", "current", "unknown"}


def sha256(path: Path) -> str:
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def codes(value: str, allowed: list[str] | tuple[str, ...]) -> list[str]:
    result = sorted({part.strip().lower() for part in value.split(",") if part.strip()})
    if not result or set(result) - set(allowed):
        raise ValueError("language scope must be non-empty and supported: " + value)
    return result


def strict_candidates(path: Path):
    opener = gzip.open if path.suffix == ".gz" else open
    with opener(path, "rt", encoding="utf-8") as handle:
        for number, line in enumerate(handle, 1):
            if not line.strip():
                continue
            try:
                yield Candidate.from_dict(json.loads(line))
            except Exception as exc:
                raise ValueError(f"{path}:{number}: {exc}") from exc


def validate_args(args: argparse.Namespace) -> tuple[list[str], list[str]]:
    learn = codes(args.learn, core.LEARNABLE)
    meanings = codes(args.meanings, core.MEANINGS)
    if not any(lang != meaning for lang in learn for meaning in meanings):
        raise ValueError("scope contains no directed language pairs")
    if args.function_top < 0 or args.sample_per_pair < 0:
        raise ValueError("function-top and sample-per-pair must be non-negative")
    version = args.source_version.strip()
    if not version or version.casefold() in MOVING_VERSIONS:
        raise ValueError("a pinned source version is required; moving labels are not accepted")
    args.source_version = version
    if args.expect_sha256 and len(args.expect_sha256) != len(args.candidates):
        raise ValueError("expect-sha256 must contain one digest per candidate file, in input order")
    if args.expect_sha256 and any(not re.fullmatch(r"[0-9a-fA-F]{64}", value) for value in args.expect_sha256):
        raise ValueError("expect-sha256 must contain SHA-256 hex digests")
    inputs = [Path(path).resolve() for path in args.candidates]
    outputs = [Path(getattr(args, name)).resolve() for name in ("pool", "json", "markdown", "samples", "staging")]
    if len(set(inputs)) != len(inputs):
        raise ValueError("duplicate input path")
    if len(set(outputs)) != len(outputs) or set(outputs) & (set(inputs) | {Path(args.registry).resolve()}):
        raise ValueError("output paths must be distinct and must not overwrite inputs/registry")
    return learn, meanings


def build_report(args: argparse.Namespace) -> dict[str, Any]:
    learn, meanings = validate_args(args)
    registry = SourceRegistry.load(args.registry)
    policy = registry.get("tatoeba")
    policy.require_publication_ready()
    if policy.collection != "everyday" or policy.publication_status != "ready":
        raise ValueError("Part 7 requires the admitted Everyday Tatoeba policy")
    segmentation = prepare(learn)
    pool = Path(args.pool)
    pool.parent.mkdir(parents=True, exist_ok=True)
    Path(args.staging).parent.mkdir(parents=True, exist_ok=True)
    input_files = []
    input_rows = Counter()
    rows_by_pair = Counter()
    samples = []
    pairs = []
    summary = Counter()
    # Validate the entire input before touching the previous successful pool.
    # The normalized scoped copy also isolates later passes from input changes.
    with tempfile.TemporaryDirectory(prefix="everyday-pool-", dir=pool.parent) as td:
        root = Path(td)
        scoped = root / "scoped.jsonl"

        def validated():
            for index, filename in enumerate(args.candidates):
                path = Path(filename)
                digest = sha256(path)
                if args.expect_sha256 and digest != args.expect_sha256[index].lower():
                    raise ValueError(f"input SHA-256 mismatch: {path}")
                file_rows = 0
                for record in strict_candidates(path):
                    if record.collection != "everyday":
                        raise ValueError(f"{path}: foreign collection {record.collection}")
                    if record.lang not in core.LEARNABLE or record.meaning_lang not in core.MEANINGS or record.lang == record.meaning_lang:
                        raise ValueError(f"{path}: invalid directed pair")
                    for origin in record.origins:
                        if origin.source_family != "tatoeba":
                            raise ValueError(f"{path}: non-admitted family {origin.source_family} in Tatoeba pool")
                        policy.validate_origin_for_publication(origin.to_dict())
                        if origin.source_version != args.source_version:
                            raise ValueError(f"{path}: source version differs from pinned version")
                        if any(not re.fullmatch(r"tatoeba:[1-9][0-9]*", ref) for ref in (origin.context_ref, origin.meaning_ref)):
                            raise ValueError(f"{path}: invalid Tatoeba sentence reference")
                    file_rows += 1
                    input_rows["providedCandidates"] += 1
                    if record.lang in learn and record.meaning_lang in meanings:
                        input_rows["scopedCandidates"] += 1
                        rows_by_pair[record.lang + "->" + record.meaning_lang] += 1
                        yield record
                    else:
                        input_rows["outsideRequestedScope"] += 1
                if digest != sha256(path):
                    raise ValueError(f"input changed during scan: {path}")
                input_files.append({"name": path.name, "sha256": digest, "sizeBytes": path.stat().st_size, "candidates": file_rows})

        write_jsonl(str(scoped), validated())
        if not input_rows["scopedCandidates"]:
            raise ValueError("no candidates in the requested scope")
        plain_pool = root / "pool.jsonl"
        merge_input, merge_output = merge_candidate_files([str(scoped)], str(plain_pool))
        scoped.unlink()
        stage_stats = stage_candidates([str(plain_pool)], Path(args.staging))
        db = sqlite3.connect(args.staging)
        try:
            # Exact global identities are counted separately from memberships.
            db.execute("CREATE TABLE eligible_global(target_id TEXT PRIMARY KEY)")
            for lang in learn:
                ranks = build_ranks(db, "everyday", lang)
                for meaning in meanings:
                    if lang == meaning:
                        continue
                    targets, contexts, stats, pair_samples = eligible_from_source(
                        db, "tatoeba", lang, meaning, ranks, args.function_top,
                        sample_per_pair=args.sample_per_pair,
                    )
                    for ids in targets.values():
                        db.executemany("INSERT OR IGNORE INTO eligible_global VALUES(?)", ((tid,) for tid in sorted(ids)))
                    eligible = {level: len(targets[level]) for level in core.LEVELS}
                    source_rows = rows_by_pair[lang + "->" + meaning]
                    diagnosis = "no-direct-rows" if not source_rows else "no-usable-targets" if not sum(eligible.values()) else "measured"
                    pairs.append({
                        "lang": lang, "meaningLang": meaning, "inputCandidates": source_rows,
                        "eligibleTargets": eligible, "eligibleTargetContexts": len(contexts),
                        "rows": dict(sorted(stats.items())), "diagnosis": diagnosis,
                    })
                    samples.extend(pair_samples)
                    summary["plannedDirectedPairs"] += 1
                    summary[diagnosis] += 1
                    summary["eligibleTargetMemberships"] += sum(eligible.values())
                    summary["eligibleTargetContextMemberships"] += len(contexts)
            summary["uniqueEligibleGlobalTargets"] = db.execute("SELECT COUNT(*) FROM eligible_global").fetchone()[0]
            db.commit()
        finally:
            db.close()
        temporary_pool = root / "pool-output"
        with plain_pool.open("rb") as source, temporary_pool.open("wb") as destination:
            if pool.suffix == ".gz":
                # Neither output name nor wall-clock time changes the pool bytes.
                with gzip.GzipFile(fileobj=destination, filename="", mode="wb", compresslevel=6, mtime=0) as zipped:
                    shutil.copyfileobj(source, zipped)
            else:
                shutil.copyfileobj(source, destination)
        pool_identity = {"sha256": sha256(temporary_pool), "sizeBytes": temporary_pool.stat().st_size,
                         "logicalSha256": sha256(plain_pool), "logicalSizeBytes": plain_pool.stat().st_size,
                         "uniqueCandidates": merge_output,
                         "duplicateCandidatesMerged": merge_input - merge_output}
        temporary_pool.replace(pool)

    return {
        "reportVersion": 1, "part": 7, "status": "admitted-source-pool-evidence",
        "publicationSafe": False, "sourceAdmissionPassed": True,
        "completeInputScan": True, "corpusCoverage": "provided-input-files-only",
        "sourceDecision": {"tatoeba": "admitted", "massive": "excluded"},
        "sourceVersion": args.source_version, "learn": learn, "meanings": meanings,
        "inputs": {"files": input_files, **dict(sorted(input_rows.items()))},
        "registrySha256": sha256(Path(args.registry)),
        "pipelineSha256": {name: sha256(HERE / name) for name in (
            "everyday_pool.py", "everyday_rebuild.py", "build_catalogue_v2.py", "catalogue_core.py",
            "catalogue_v2.py", "segmentation.py", "ingest/model.py", "ingest/registry.py",
        )},
        "environment": {"python": platform.python_version(), "zlib": zlib.ZLIB_RUNTIME_VERSION,
                        "segmentation": segmentation},
        "sieve": {"functionTop": args.function_top, "frequencySpace": "Tatoeba-only scoped input",
                  "preferredMeaning": "one shortest meaning per primary source context; pool retains all origins/meanings",
                  "coverageUnit": "builder primary-origin contexts; alternative merged origins remain in the full pool"},
        "pool": pool_identity, "staging": stage_stats, "summary": dict(sorted(summary.items())),
        "pairs": pairs, "samples": samples,
    }


def markdown(report: dict[str, Any]) -> str:
    summary = report["summary"]
    lines = ["# Everyday admitted-source pool", "",
             "Tatoeba-only Part 7 evidence. No assets are published. Selection, content review and freeze remain separate gates.", "",
             "The complete supplied files were scanned. This does not prove that they contain a full upstream export.", "",
             f"Source version: `{report['sourceVersion']}`",
             f"Pool SHA-256: `{report['pool']['sha256']}`", "",
             f"Unique candidates: **{report['pool']['uniqueCandidates']:,}**",
             f"Eligible global exact targets: **{summary['uniqueEligibleGlobalTargets']:,}**",
             f"Eligible target memberships across pair/levels: **{summary['eligibleTargetMemberships']:,}**", "",
             "| pair | input rows | eligible beginner / middle / advanced | target-context memberships | diagnosis |",
             "| --- | ---: | --- | ---: | --- |"]
    for row in report["pairs"]:
        counts = " / ".join(str(row["eligibleTargets"][level]) for level in core.LEVELS)
        lines.append(f"| `{row['lang']}->{row['meaningLang']}` | {row['inputCandidates']:,} | {counts} | {row['eligibleTargetContexts']:,} | {row['diagnosis']} |")
    lines += ["", "Ranks and the existing target sieve use only the scoped Tatoeba pool. No MASSIVE rank contamination, deck cap or filler is applied.",
              "Row rejection counters and input/pipeline identities are retained in the JSON report. Samples do not replace the full candidate pool.", ""]
    return "\n".join(lines)


def samples_markdown(report: dict[str, Any]) -> str:
    lines = ["# Everyday deterministic manual-review samples", "", "Evidence only; these are not the complete pool or selected decks.", ""]
    for row in report["samples"]:
        lines += [f"## {row['lang']}->{row['meaningLang']} / {row['contextId']}", "",
                  f"Target: {row['target']} (`{row['targetId']}`, {row['level']})", "",
                  row["context"], "", row["meaning"], "", f"Meaning reference: `{row['meaningId']}`", ""]
    return "\n".join(lines)


def parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--candidates", nargs="+", required=True)
    ap.add_argument("--source-version", required=True)
    ap.add_argument("--expect-sha256", nargs="+", help="optional pinned digests, one per input file")
    for name in ("pool", "json", "markdown", "samples", "staging"):
        ap.add_argument("--" + name, required=True)
    ap.add_argument("--registry", default=str(DEFAULT_REGISTRY))
    ap.add_argument("--learn", default=",".join(core.LEARNABLE))
    ap.add_argument("--meanings", default=",".join(core.MEANINGS))
    ap.add_argument("--function-top", type=int, default=core.FUNCTION_TOP)
    ap.add_argument("--sample-per-pair", type=int, default=5)
    return ap


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    report = build_report(args)
    for filename, content in ((args.json, json.dumps(report, ensure_ascii=False, indent=2) + "\n"),
                              (args.markdown, markdown(report)), (args.samples, samples_markdown(report))):
        Path(filename).parent.mkdir(parents=True, exist_ok=True)
        Path(filename).write_text(content, encoding="utf-8", newline="")
    print(json.dumps(report["summary"], indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
