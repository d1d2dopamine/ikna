#!/usr/bin/env python3
"""Part 10 review of a hash-bound, admitted Everyday pool; never publishes."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import platform
import re
import tempfile
import zlib
from pathlib import Path

import catalogue_core as core
import selection_experiment as selection
from everyday_pool import DEFAULT_REGISTRY, MOVING_VERSIONS, codes, sha256
from ingest.model import Candidate
from ingest.registry import SourceRegistry

HERE = Path(__file__).resolve().parent


def validate_paths(args: argparse.Namespace) -> None:
    inputs = [Path(getattr(args, key)).resolve() for key in ("pool", "pool_report", "registry")]
    outputs = [Path(getattr(args, key)).resolve() for key in ("staging", "json", "markdown", "preview", "samples")]
    if len(set(inputs)) != len(inputs) or len(set(outputs)) != len(outputs) or set(inputs) & set(outputs):
        raise ValueError("output paths must be distinct and must not overwrite pool/report/registry")
    for output in outputs:
        if output.exists() and any(other != output and other.exists() and output.samefile(other)
                                   for other in inputs + outputs):
            raise ValueError("output path aliases another input/output file")
    if not 1 <= args.review_per_deck <= 100:
        raise ValueError("review-per-deck must be between 1 and 100; review rows are bounded samples")


def validate_pool(args: argparse.Namespace) -> tuple[dict, dict]:
    report_path = Path(args.pool_report)
    report_hash = sha256(report_path)
    report = json.loads(report_path.read_text(encoding="utf-8"))
    if (report.get("reportVersion") != 1 or report.get("part") != 7 or
            report.get("status") != "admitted-source-pool-evidence" or
            report.get("sourceAdmissionPassed") is not True or report.get("completeInputScan") is not True or
            report.get("publicationSafe") is not False or
            report.get("corpusCoverage") != "provided-input-files-only" or
            report.get("sourceDecision") != {"tatoeba": "admitted", "massive": "excluded"}):
        raise ValueError("expected a non-publishing admitted Part 7 report, not an experiment/preview")
    for key, allowed in (("learn", core.LEARNABLE), ("meanings", core.MEANINGS)):
        values = report.get(key)
        if not isinstance(values, list) or not all(isinstance(value, str) for value in values):
            raise ValueError("invalid pool language scope")
        if codes(",".join(values), allowed) != values:
            raise ValueError("pool language scope must be canonical")
    if not any(lang != meaning for lang in report["learn"] for meaning in report["meanings"]):
        raise ValueError("pool scope contains no directed pairs")
    version = report.get("sourceVersion")
    if not isinstance(version, str) or not version.strip() or version.casefold() in MOVING_VERSIONS:
        raise ValueError("a pinned source version is required")
    identity = report.get("pool", {})
    path = Path(args.pool)
    if identity.get("sha256") != sha256(path) or identity.get("sizeBytes") != path.stat().st_size:
        raise ValueError("pool SHA-256/size differs from Part 7 report")
    if report.get("registrySha256") != sha256(Path(args.registry)):
        raise ValueError("source registry differs from pool report; rebuild pool evidence with the current policy")
    policy = SourceRegistry.load(args.registry).get("tatoeba")
    policy.require_publication_ready()
    if policy.collection != "everyday" or policy.publication_status != "ready":
        raise ValueError("Part 10 requires admitted Tatoeba Everyday")
    logical_hash = hashlib.sha256()
    logical_bytes = count = 0
    opener = gzip.open if path.suffix == ".gz" else open
    with opener(path, "rb") as handle:
        for number, raw in enumerate(handle, 1):
            logical_hash.update(raw); logical_bytes += len(raw)
            if not raw.strip():
                continue
            try:
                record = Candidate.from_dict(json.loads(raw.decode("utf-8")))
                if record.collection != "everyday" or record.lang not in report["learn"] or record.meaning_lang not in report["meanings"] or record.lang == record.meaning_lang:
                    raise ValueError("candidate outside the exact reported Everyday scope")
                for origin in record.origins:
                    if origin.source_family != "tatoeba" or origin.source_version != version:
                        raise ValueError("non-admitted family or mismatched source version")
                    policy.validate_origin_for_publication(origin.to_dict())
                    if any(not re.fullmatch(r"tatoeba:[1-9][0-9]*", ref) for ref in (origin.context_ref, origin.meaning_ref)):
                        raise ValueError("invalid Tatoeba sentence reference")
            except Exception as error:
                raise ValueError(f"{path}:{number}: {error}") from error
            count += 1
    if (count < 1 or count != identity.get("uniqueCandidates") or
            logical_hash.hexdigest() != identity.get("logicalSha256") or logical_bytes != identity.get("logicalSizeBytes")):
        raise ValueError("pool logical identity/count differs from Part 7 report")
    if sha256(path) != identity["sha256"] or sha256(report_path) != report_hash:
        raise ValueError("pool/report changed during validation")
    return report, {"poolSha256": identity["sha256"], "poolLogicalSha256": identity["logicalSha256"],
                    "poolReportSha256": report_hash, "sourceVersion": version,
                    "uniqueCandidates": count, "registrySha256": report["registrySha256"],
                    "learn": report["learn"], "meanings": report["meanings"],
                    "poolEnvironment": report.get("environment"), "poolPipelineSha256": report.get("pipelineSha256")}


def complete_inventory(report: dict, learn: list[str], meanings: list[str]) -> None:
    present = {row["deckId"] for row in report["decks"]}
    missing_pairs = []
    for lang in learn:
        for meaning in meanings:
            if lang == meaning:
                continue
            if not any(f"{lang}-{meaning}-everyday-{level}" in present for level in core.LEVELS):
                missing_pairs.append(f"{lang}->{meaning}")
            for level in core.LEVELS:
                deck_id = f"{lang}-{meaning}-everyday-{level}"
                if deck_id in present:
                    continue
                report["decks"].append({"deckId": deck_id, "collection": "everyday", "lang": lang,
                    "meaningLang": meaning, "level": level, "eligibleTargets": 0, "selectedTargets": 0,
                    "decision": "omit-no-quality-supply", "diagnosis": "no-direct-rows",
                    "budget": report["policy"]["maxTargetsPerLevel"], "budgetRejected": 0,
                    "morphologyDeferred": 0, "nearDuplicateContextsRejected": 0,
                    "contextCollisionTargetsRejected": 0, "pairStats": {"candidateRows": 0}})
                report["summary"]["omit-no-quality-supply"] = report["summary"].get("omit-no-quality-supply", 0) + 1
    report["decks"].sort(key=lambda row: row["deckId"])
    report["missingDirectPairs"] = missing_pairs
    report["plannedDirectedPairs"] = sum(lang != meaning for lang in learn for meaning in meanings)


def build_report(args: argparse.Namespace) -> tuple[dict, list[dict]]:
    validate_paths(args)
    pool_report, identity = validate_pool(args)
    function_top = pool_report.get("sieve", {}).get("functionTop")
    if not isinstance(function_top, int) or function_top < 0:
        raise ValueError("pool report must record its function-word sieve")
    selected_args = selection.parser().parse_args([
        "--candidates", args.pool, "--json", args.json, "--markdown", args.markdown,
        "--preview", args.preview, "--staging", args.staging,
        "--learn", ",".join(identity["learn"]), "--meanings", ",".join(identity["meanings"]),
        "--function-top", str(function_top), "--max-deck", str(args.max_deck),
        "--min-deck", str(args.min_deck), "--thin-deck", str(args.thin_deck),
        "--preview-limit-per-deck", str(args.review_per_deck),
    ])
    selection.validate_args(selected_args)
    staging = Path(args.staging)
    staging.parent.mkdir(parents=True, exist_ok=True)
    # A failed selection must not destroy the previous successful staging file.
    with tempfile.TemporaryDirectory(prefix="everyday-selection-", dir=staging.parent) as td:
        selected_args.staging = str(Path(td) / "selection.sqlite3")
        report, preview = selection.build_report(selected_args)
        if report["stage"].get("uniqueCandidates") != identity["uniqueCandidates"] or report["stage"].get("duplicateCandidates") != 0:
            raise ValueError("pool is not the unique complete candidate set described by Part 7")
        if sha256(Path(args.pool)) != identity["poolSha256"] or sha256(Path(args.pool_report)) != identity["poolReportSha256"]:
            raise ValueError("pool/report changed during selection")
        complete_inventory(report, identity["learn"], identity["meanings"])
        report["status"] = "admitted-everyday-selection-review"
        report["admittedInput"] = identity
        report["selectionPipelineSha256"] = {name: sha256(HERE / name) for name in (
            "everyday_selection.py", "everyday_pool.py", "selection_experiment.py", "selection_policy.py", "build_catalogue_v2.py",
            "catalogue_core.py", "catalogue_v2.py", "segmentation.py", "ingest/model.py", "ingest/registry.py")}
        report["selectionEnvironment"] = {"python": platform.python_version(), "zlib": zlib.ZLIB_RUNTIME_VERSION,
                                          "segmentation": selection.prepare(identity["learn"])}
        report["reviewScope"] = {
            "rowsPerPublishableDeck": args.review_per_deck, "previewIsCompleteSelection": False,
            "materialReviewCompleted": False, "contentFrozen": False,
            "limitations": ["EOF covers provided files, not an unproven full upstream export.",
                "Rank-ordered samples are review aids, not semantic-quality or native-speaker evidence.",
                "Policy publish/publish-thin decisions do not publish assets or admit content to the freeze."]}
        Path(selected_args.staging).replace(staging)
    return report, preview


def markdown(report: dict) -> str:
    identity = report["admittedInput"]
    lines = ["# Everyday admitted selection review", "",
             f"Source version: `{identity['sourceVersion']}`",
             f"Pool SHA-256: `{identity['poolSha256']}`",
             f"Part 7 report SHA-256: `{identity['poolReportSha256']}`",
             f"Planned directed pairs: {report['plannedDirectedPairs']}",
             "No direct source rows: " + (", ".join(report["missingDirectPairs"]) or "none"), "",
             "Material review and freeze remain open. The JSONL preview is a bounded rank-ordered sample.", ""]
    return "\n".join(lines) + selection.markdown(report).split("\n", 1)[1]


def samples_markdown(report: dict, preview: list[dict]) -> str:
    lines = ["# Everyday selected-material review samples", "",
             "Bounded rank-ordered samples, not full selected assets. Human review and content freeze remain open.",
             "No-source pairs and omitted levels are listed in EVERYDAY-SELECTION.json/md.", "",
             f"Pool SHA-256: `{report['admittedInput']['poolSha256']}`", ""]
    for row in preview:
        lines += [f"## {row['deckId']} / {row['targetId']}", "",
                  f"Target: {row['text']} (frequency rank {row['freqRank']})", ""]
        for context in row["contexts"]:
            lines += [context["context"], "", context["meaning"], "",
                      f"References: `{context['contextId']}` / `{context['meaningId']}`", "",
                      "Origins: `" + json.dumps(context["origins"], ensure_ascii=False, sort_keys=True) + "`", ""]
    return "\n".join(lines)


def parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--pool", required=True); ap.add_argument("--pool-report", required=True)
    ap.add_argument("--registry", default=str(DEFAULT_REGISTRY))
    for name in ("staging", "json", "markdown", "preview", "samples"):
        ap.add_argument("--" + name, required=True)
    ap.add_argument("--max-deck", type=int, default=8000)
    ap.add_argument("--min-deck", type=int, default=40); ap.add_argument("--thin-deck", type=int, default=1000)
    ap.add_argument("--review-per-deck", type=int, default=10)
    return ap


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    report, preview = build_report(args)
    for name in ("json", "markdown", "preview", "samples"):
        Path(getattr(args, name)).parent.mkdir(parents=True, exist_ok=True)
    Path(args.json).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    Path(args.markdown).write_text(markdown(report), encoding="utf-8")
    selection.write_preview(args.preview, preview)
    Path(args.samples).write_text(samples_markdown(report, preview), encoding="utf-8")
    print(json.dumps(report["summary"], indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
