#!/usr/bin/env python3
"""Part 9: resolve OPUS Global Voices document identities to article attribution.

The aligned text is useful only when every retained line can be traced back to
both source documents and those documents have trustworthy Global Voices article
metadata.  This tool never guesses a URL or contributor from text.
"""
from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


def valid_globalvoices_url(value: str) -> bool:
    try:
        parsed = urlparse(value)
    except ValueError:
        return False
    host = (parsed.hostname or "").lower().rstrip(".")
    return parsed.scheme == "https" and (host == "globalvoices.org" or host.endswith(".globalvoices.org"))


def load_articles(path: str) -> dict[str, dict[str, Any]]:
    rows: dict[str, dict[str, Any]] = {}
    with open(path, encoding="utf-8") as handle:
        for physical, line in enumerate(handle, start=1):
            line = line.strip()
            if not line:
                continue
            value = json.loads(line)
            document = str(value.get("document") or "").strip()
            url = str(value.get("articleUrl") or "").strip()
            contributors = value.get("contributors")
            if not document:
                raise ValueError(f"{path}:{physical}: missing document")
            if document in rows:
                raise ValueError(f"{path}:{physical}: duplicate document {document}")
            if not valid_globalvoices_url(url):
                raise ValueError(f"{path}:{physical}: articleUrl is not a Global Voices https URL")
            if not isinstance(contributors, list) or not contributors or not all(isinstance(x, str) and x.strip() for x in contributors):
                raise ValueError(f"{path}:{physical}: contributors must be a non-empty string list")
            rows[document] = {"articleUrl": url, "contributors": [x.strip() for x in contributors]}
    return rows


def load_alignment_map(path: str) -> list[dict[str, Any]]:
    rows = []
    seen = set()
    with open(path, encoding="utf-8") as handle:
        for physical, line in enumerate(handle, start=1):
            line = line.strip()
            if not line:
                continue
            value = json.loads(line)
            number = value.get("line")
            context_doc = str(value.get("contextDocument") or "").strip()
            meaning_doc = str(value.get("meaningDocument") or "").strip()
            if not isinstance(number, int) or number < 1 or not context_doc or not meaning_doc:
                raise ValueError(f"{path}:{physical}: invalid line/contextDocument/meaningDocument")
            if number in seen:
                raise ValueError(f"{path}:{physical}: duplicate aligned line {number}")
            seen.add(number)
            rows.append({"line": number, "contextDocument": context_doc, "meaningDocument": meaning_doc})
    return rows


def resolve(alignment_map: str, article_manifest: str) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    articles = load_articles(article_manifest)
    alignments = load_alignment_map(alignment_map)
    out = []
    stats = Counter()
    missing_documents = Counter()
    for row in alignments:
        stats["alignmentRows"] += 1
        left = articles.get(row["contextDocument"])
        right = articles.get(row["meaningDocument"])
        if left is None or right is None:
            stats["unresolvedRows"] += 1
            if left is None:
                missing_documents[row["contextDocument"]] += 1
            if right is None:
                missing_documents[row["meaningDocument"]] += 1
            continue
        contributors = []
        seen = set()
        for name in left["contributors"] + right["contributors"]:
            if name not in seen:
                contributors.append(name)
                seen.add(name)
        out.append({
            "line": row["line"],
            # Required publication fields used by SourcePolicy.
            "articleUrl": left["articleUrl"],
            "contributors": contributors,
            # Extra evidence remains inspectable instead of being discarded.
            "contextDocument": row["contextDocument"],
            "meaningDocument": row["meaningDocument"],
            "contextArticleUrl": left["articleUrl"],
            "meaningArticleUrl": right["articleUrl"],
            "contextContributors": left["contributors"],
            "meaningContributors": right["contributors"],
        })
        stats["resolvedRows"] += 1
    report = {
        "reportVersion": 1,
        "part": 9,
        "status": "pass" if stats["unresolvedRows"] == 0 else "incomplete-attribution",
        "summary": dict(stats),
        "missingDocuments": [
            {"document": name, "affectedRows": count}
            for name, count in sorted(missing_documents.items(), key=lambda item: (-item[1], item[0]))
        ],
    }
    return out, report


def markdown(report: dict[str, Any]) -> str:
    s = report["summary"]
    lines = [
        "# Global Voices attribution gate",
        "",
        f"Status: **{report['status']}**",
        "",
        f"- aligned rows: **{s.get('alignmentRows', 0):,}**",
        f"- fully attributed rows: **{s.get('resolvedRows', 0):,}**",
        f"- rejected for missing article metadata: **{s.get('unresolvedRows', 0):,}**",
        "",
        "Unresolved rows are not publishable World material. No URL or contributor is guessed.",
    ]
    if report["missingDocuments"]:
        lines += ["", "## Missing document attribution", "", "| document | affected rows |", "| --- | ---: |"]
        for row in report["missingDocuments"][:200]:
            lines.append(f"| `{row['document']}` | {row['affectedRows']:,} |")
    lines.append("")
    return "\n".join(lines)


def parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--alignment-map", required=True)
    ap.add_argument("--article-manifest", required=True)
    ap.add_argument("--out", required=True, help="resolved attribution JSONL sidecar using original line numbers")
    ap.add_argument("--learn-file")
    ap.add_argument("--meaning-file")
    ap.add_argument("--filtered-learn")
    ap.add_argument("--filtered-meaning")
    ap.add_argument("--filtered-attribution")
    ap.add_argument("--json", required=True)
    ap.add_argument("--markdown", required=True)
    return ap


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    rows, report = resolve(args.alignment_map, args.article_manifest)
    with open(args.out, "w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")

    filtering = [args.learn_file, args.meaning_file, args.filtered_learn, args.filtered_meaning, args.filtered_attribution]
    if any(filtering) and not all(filtering):
        raise SystemExit("aligned filtering requires --learn-file, --meaning-file, --filtered-learn, --filtered-meaning and --filtered-attribution together")
    if all(filtering):
        keep = {row["line"]: row for row in rows}
        with open(args.learn_file, encoding="utf-8", errors="replace") as left, open(args.meaning_file, encoding="utf-8", errors="replace") as right, open(args.filtered_learn, "w", encoding="utf-8") as out_left, open(args.filtered_meaning, "w", encoding="utf-8") as out_right, open(args.filtered_attribution, "w", encoding="utf-8") as out_attr:
            emitted = 0
            for number, pair in enumerate(zip(left, right), start=1):
                lline, rline = pair
                attribution = keep.get(number)
                if attribution is None:
                    continue
                emitted += 1
                out_left.write(lline)
                out_right.write(rline)
                rewritten = dict(attribution)
                rewritten["line"] = emitted
                out_attr.write(json.dumps(rewritten, ensure_ascii=False, sort_keys=True) + "\n")
            if next(left, None) is not None or next(right, None) is not None:
                raise ValueError("aligned Global Voices files have different line counts")
        report["summary"]["filteredRows"] = emitted

    Path(args.json).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    Path(args.markdown).write_text(markdown(report), encoding="utf-8")
    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
