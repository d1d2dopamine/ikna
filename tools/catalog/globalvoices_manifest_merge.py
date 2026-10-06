#!/usr/bin/env python3
"""Merge Global Voices manifest shards into one deterministic publication manifest.

Each shard contains only verified document -> canonical Global Voices article
mappings. Shard reports also preserve unresolved documents and retryable network
failures. The merger rejects duplicate/conflicting document rows, re-computes
alignment coverage from the authoritative alignment map, and distinguishes a
publication-safe partial manifest from a *complete* provenance scan.
"""
from __future__ import annotations

import argparse
import glob
import json
from collections import Counter
from pathlib import Path
from typing import Any

from globalvoices_attribution import valid_globalvoices_url
from globalvoices_manifest import alignment_inventory, normalize_document, sha256_file, shard_index_for_document


def _load_manifest(path: str) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    with open(path, encoding="utf-8") as handle:
        for physical, line in enumerate(handle, start=1):
            line = line.strip()
            if not line:
                continue
            value = json.loads(line)
            document = normalize_document(str(value.get("document") or ""))
            url = str(value.get("articleUrl") or "")
            contributors = value.get("contributors")
            if not document or not valid_globalvoices_url(url):
                raise ValueError(f"{path}:{physical}: invalid verified manifest row")
            if not isinstance(contributors, list) or not contributors or not all(isinstance(x, str) and x.strip() for x in contributors):
                raise ValueError(f"{path}:{physical}: contributors must be a non-empty string list")
            row = dict(value)
            row["document"] = document
            row["contributors"] = [x.strip() for x in contributors]
            out.append(row)
    return out


def merge(
    alignment_map: str,
    manifest_paths: list[str],
    report_paths: list[str],
    *,
    expected_shards: int,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    if type(expected_shards) is not int or not 1 <= expected_shards <= 256:
        raise ValueError("expected_shards must be in 1..256")
    counts, pair_counts = alignment_inventory(alignment_map)
    all_documents = set(counts)
    alignment_sha256 = sha256_file(alignment_map)

    rows_by_doc: dict[str, dict[str, Any]] = {}
    duplicate_identical = 0
    for path in sorted(manifest_paths):
        for row in _load_manifest(path):
            document = row["document"]
            if document not in all_documents:
                raise ValueError(f"{path}: verified document outside alignment inventory: {document}")
            previous = rows_by_doc.get(document)
            if previous is None:
                rows_by_doc[document] = row
            elif previous == row:
                duplicate_identical += 1
            else:
                raise ValueError(f"conflicting verified manifest rows for {document}")

    reports: list[dict[str, Any]] = []
    shard_indexes: set[int] = set()
    attempted_documents: set[str] = set()
    unresolved_by_doc: dict[str, dict[str, Any]] = {}
    reasons = Counter()
    cache_hits = 0
    network_attempts = 0
    retryable_documents: set[str] = set()
    legacy_reports = 0

    for path in sorted(report_paths):
        report = json.loads(Path(path).read_text(encoding="utf-8"))
        if report.get("reportVersion") not in (2, 3) or report.get("part") != 9 or report.get("publicationSafe") is not True:
            raise ValueError(f"{path}: unsupported or unsafe World shard report")
        reports.append(report)
        shard = report.get("shard") or {}
        index = shard.get("index")
        count = shard.get("count")
        if type(index) is not int or type(count) is not int:
            raise ValueError(f"{path}: missing shard metadata")
        if count != expected_shards:
            raise ValueError(f"{path}: shard count {count} != expected {expected_shards}")
        if not 0 <= index < count:
            raise ValueError(f"{path}: shard index outside 0..{count - 1}")
        if index in shard_indexes:
            raise ValueError(f"duplicate shard report for index {index}")
        shard_indexes.add(index)
        input_hash = report.get("alignmentMapSha256")
        if input_hash is None:
            if report.get("reportVersion", 0) >= 3:
                raise ValueError(f"{path}: missing alignment map hash")
            legacy_reports += 1
        elif input_hash != alignment_sha256:
            raise ValueError(f"{path}: alignment map hash differs from merge input")

        summary = report.get("summary") or {}
        cache_hits += int(summary.get("cacheHits") or 0)
        network_attempts += int(summary.get("networkAttempts") or 0)
        for reason, value in (report.get("reasons") or {}).items():
            reasons[str(reason)] += int(value)
        for item in report.get("unresolved") or []:
            document = normalize_document(str(item.get("document") or ""))
            if not document or document not in all_documents:
                raise ValueError(f"{path}: unresolved document outside alignment inventory: {document}")
            if shard_index_for_document(document, expected_shards) != index:
                raise ValueError(f"{path}: unresolved document belongs to another shard: {document}")
            if document in unresolved_by_doc or document in rows_by_doc:
                raise ValueError(f"{path}: duplicate/conflicting document outcome: {document}")
            if not isinstance(item.get("reason"), str) or not item["reason"].strip():
                raise ValueError(f"{path}: unresolved document requires a reason")
            if item.get("affectedRows") != counts[document]:
                raise ValueError(f"{path}: unresolved affectedRows differs from alignment inventory")
            unresolved_by_doc[document] = dict(item)
            attempted_documents.add(document)
            if str(item.get("reason") or "").startswith("fetch-error:"):
                retryable_documents.add(document)

    attempted_documents.update(rows_by_doc)
    missing_shards = sorted(set(range(expected_shards)) - shard_indexes)
    unattempted_documents = all_documents - attempted_documents
    resolved_docs = set(rows_by_doc)
    verified_alignment_rows = sum(
        affected for (left, right), affected in pair_counts.items()
        if left in resolved_docs and right in resolved_docs
    )
    total_alignment_rows = sum(pair_counts.values())
    complete_scan = not missing_shards and not unattempted_documents and not retryable_documents
    retry_shards = sorted(set(missing_shards) | {
        shard_index_for_document(doc, expected_shards)
        for doc in unattempted_documents | retryable_documents
    })

    rows = [rows_by_doc[key] for key in sorted(rows_by_doc)]
    unresolved = sorted(
        unresolved_by_doc.values(),
        key=lambda row: (-int(row.get("affectedRows") or 0), str(row.get("document") or "")),
    )
    report = {
        "reportVersion": 2,
        "part": 9,
        "status": "pass" if complete_scan else "retry-required",
        "publicationSafe": True,
        "completeScan": complete_scan,
        "summary": {
            "alignmentDocuments": len(all_documents),
            "alignmentRows": total_alignment_rows,
            "expectedShards": expected_shards,
            "completedShards": len(shard_indexes),
            "missingShards": len(missing_shards),
            "attemptedDocuments": len(attempted_documents),
            "resolvedDocuments": len(rows),
            "unresolvedDocuments": len(unresolved_by_doc),
            "retryableUnresolvedDocuments": len(retryable_documents),
            "unattemptedDocuments": len(unattempted_documents),
            "verifiedAlignmentRows": verified_alignment_rows,
            "verifiedAlignmentRate": (verified_alignment_rows / total_alignment_rows) if total_alignment_rows else 0.0,
            "cacheHits": cache_hits,
            "networkAttempts": network_attempts,
            "duplicateIdenticalRows": duplicate_identical,
        },
        "missingShardIndexes": missing_shards,
        "reasons": dict(sorted(reasons.items())),
        "highestImpactUnresolved": unresolved[:500],
        "sampleUnattemptedDocuments": sorted(unattempted_documents)[:200],
        "inputEvidence": {
            "alignmentMapSha256": alignment_sha256,
            "legacyShardReportsWithoutInputHash": legacy_reports,
            "manifests": [{"file": Path(p).name, "sha256": sha256_file(p)} for p in sorted(manifest_paths)],
            "reports": [{"file": Path(p).name, "sha256": sha256_file(p)} for p in sorted(report_paths)],
        },
        "recovery": {
            "action": "none" if complete_scan else "retry-listed-shards",
            "retryShardIndexes": retry_shards,
            "retryableDocuments": sorted(retryable_documents),
            "unattemptedDocuments": sorted(unattempted_documents),
            "missingShardIndexes": missing_shards,
        },
    }
    return rows, report


def markdown(report: dict[str, Any]) -> str:
    s = report["summary"]
    lines = [
        "# Global Voices full provenance manifest",
        "",
        f"Status: **{report['status']}**",
        f"Publication-safe verified subset: **{'yes' if report['publicationSafe'] else 'no'}**",
        f"Complete scan: **{'yes' if report['completeScan'] else 'no'}**",
        "",
        f"- alignment documents: **{s['alignmentDocuments']:,}**",
        f"- alignment rows: **{s['alignmentRows']:,}**",
        f"- shards completed: **{s['completedShards']:,}/{s['expectedShards']:,}**",
        f"- documents attempted: **{s['attemptedDocuments']:,}**",
        f"- verified document/article mappings: **{s['resolvedDocuments']:,}**",
        f"- unresolved documents: **{s['unresolvedDocuments']:,}**",
        f"- retryable transport failures: **{s['retryableUnresolvedDocuments']:,}**",
        f"- unattempted documents: **{s['unattemptedDocuments']:,}**",
        f"- aligned rows with both documents verified: **{s['verifiedAlignmentRows']:,} ({s['verifiedAlignmentRate']:.1%})**",
        f"- cache hits / network attempts: **{s['cacheHits']:,} / {s['networkAttempts']:,}**",
        f"- shards requiring further verification: **{report['recovery']['retryShardIndexes']}**",
        f"- alignment map SHA-256: `{report['inputEvidence']['alignmentMapSha256']}`",
        f"- legacy shard reports without input hash: **{report['inputEvidence']['legacyShardReportsWithoutInputHash']}**",
        "",
        "Only verified rows enter the publication manifest. Unresolved, missing-shard, and transient-network cases remain rejected evidence; a full Catalogue freeze should use this result only when `completeScan` is true.",
    ]
    if report.get("reasons"):
        lines += ["", "## Outcomes", ""]
        for key, value in report["reasons"].items():
            lines.append(f"- `{key}`: **{value:,}**")
    if report.get("highestImpactUnresolved"):
        lines += ["", "## Highest-impact unresolved documents", "", "| document | affected rows | reason |", "| --- | ---: | --- |"]
        for row in report["highestImpactUnresolved"][:100]:
            lines.append(f"| `{row.get('document','')}` | {int(row.get('affectedRows') or 0):,} | `{row.get('reason','')}` |")
    lines.append("")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--alignment-map", required=True)
    ap.add_argument("--manifest-glob", required=True)
    ap.add_argument("--report-glob", required=True)
    ap.add_argument("--expected-shards", type=int, required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--json", required=True)
    ap.add_argument("--markdown", required=True)
    args = ap.parse_args(argv)
    if args.expected_shards < 1 or args.expected_shards > 256:
        raise SystemExit("--expected-shards must be in 1..256")
    manifests = sorted(glob.glob(args.manifest_glob))
    reports = sorted(glob.glob(args.report_glob))
    if not manifests:
        raise SystemExit("no manifest shard files matched")
    if not reports:
        raise SystemExit("no shard report files matched")
    outputs = [Path(p).resolve() for p in (args.out, args.json, args.markdown)]
    inputs = {Path(p).resolve() for p in [args.alignment_map, *manifests, *reports]}
    if len(set(outputs)) != len(outputs) or set(outputs) & inputs:
        raise SystemExit("merge outputs must be distinct and must not overwrite evidence inputs")
    rows, report = merge(args.alignment_map, manifests, reports, expected_shards=args.expected_shards)
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
    Path(args.json).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    Path(args.markdown).write_text(markdown(report), encoding="utf-8")
    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
