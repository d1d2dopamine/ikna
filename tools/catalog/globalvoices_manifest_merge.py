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
from globalvoices_manifest import alignment_inventory, normalize_document


def _load_manifest(path: str) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    with open(path, encoding="utf-8", errors="replace") as handle:
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
    counts, pair_counts = alignment_inventory(alignment_map)
    all_documents = set(counts)

    rows_by_doc: dict[str, dict[str, Any]] = {}
    duplicate_identical = 0
    for path in sorted(manifest_paths):
        for row in _load_manifest(path):
            document = row["document"]
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

    for path in sorted(report_paths):
        report = json.loads(Path(path).read_text(encoding="utf-8"))
        reports.append(report)
        shard = report.get("shard") or {}
        index = shard.get("index")
        count = shard.get("count")
        if not isinstance(index, int) or not isinstance(count, int):
            raise ValueError(f"{path}: missing shard metadata")
        if count != expected_shards:
            raise ValueError(f"{path}: shard count {count} != expected {expected_shards}")
        if index in shard_indexes:
            raise ValueError(f"duplicate shard report for index {index}")
        shard_indexes.add(index)

        summary = report.get("summary") or {}
        cache_hits += int(summary.get("cacheHits") or 0)
        network_attempts += int(summary.get("networkAttempts") or 0)
        for reason, value in (report.get("reasons") or {}).items():
            reasons[str(reason)] += int(value)
        for item in report.get("unresolved") or []:
            document = normalize_document(str(item.get("document") or ""))
            if not document:
                continue
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

    rows = [rows_by_doc[key] for key in sorted(rows_by_doc)]
    unresolved = sorted(
        unresolved_by_doc.values(),
        key=lambda row: (-int(row.get("affectedRows") or 0), str(row.get("document") or "")),
    )
    report = {
        "reportVersion": 1,
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
