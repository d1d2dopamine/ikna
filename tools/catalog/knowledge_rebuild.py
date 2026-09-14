#!/usr/bin/env python3
"""Part 8: audit direct-pair WikiMatrix and build a non-publishing Knowledge pool.

The tool deliberately separates acquisition from admission.  Upstream rows may be
acquired with a permissive floor; this script then applies one inspectable policy
per physical language pair, preserves alignment scores, and emits deterministic
samples plus a filtered candidate pool.  The pool is preview material only while
any pair used by the run is still marked ``review``.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable

HERE = Path(__file__).resolve().parent

from ingest.model import Candidate

DEFAULT_POLICY = HERE / "sources" / "catalogue-v2-wikimatrix-quality.json"


def _open(path: str):
    return gzip.open(path, "rt", encoding="utf-8", errors="replace") if path.endswith(".gz") else open(path, encoding="utf-8", errors="replace")


def iter_candidates(paths: Iterable[str]):
    for path in paths:
        with _open(path) as handle:
            for number, line in enumerate(handle, start=1):
                line = line.strip()
                if not line:
                    continue
                try:
                    yield Candidate.from_dict(json.loads(line))
                except Exception as exc:
                    raise ValueError(f"{path}:{number}: {exc}") from exc


def pair_key(first: str, second: str) -> str:
    return "-".join(sorted((first.lower(), second.lower())))


def load_policy(path: str) -> dict[str, Any]:
    raw = json.loads(Path(path).read_text(encoding="utf-8"))
    if raw.get("policyVersion") != 1:
        raise ValueError("unsupported WikiMatrix quality policy version")
    default = raw.get("default")
    pairs = raw.get("pairs")
    if not isinstance(default, dict) or not isinstance(pairs, dict):
        raise ValueError("WikiMatrix quality policy needs default and pairs objects")
    if default.get("action") not in {"review", "accept", "reject"}:
        raise ValueError("default WikiMatrix action must be review/accept/reject")
    threshold = default.get("minScore")
    if threshold is not None and not isinstance(threshold, (int, float)):
        raise ValueError("default minScore must be numeric or null")
    for key, value in pairs.items():
        if not isinstance(value, dict) or value.get("action") not in {"review", "accept", "reject"}:
            raise ValueError(f"invalid WikiMatrix pair rule {key}")
        if value.get("minScore") is not None and not isinstance(value.get("minScore"), (int, float)):
            raise ValueError(f"invalid minScore for {key}")
    return raw


def rule_for(policy: dict[str, Any], lang: str, meaning_lang: str) -> dict[str, Any]:
    base = dict(policy["default"])
    override = policy["pairs"].get(pair_key(lang, meaning_lang))
    if override:
        base.update(override)
    return base


def _score_update(stats: dict[str, float | int | None], value: float) -> None:
    stats["count"] = int(stats.get("count") or 0) + 1
    stats["sum"] = float(stats.get("sum") or 0.0) + value
    current_min = stats.get("min")
    current_max = stats.get("max")
    stats["min"] = value if current_min is None else min(float(current_min), value)
    stats["max"] = value if current_max is None else max(float(current_max), value)


def _score_public(stats: dict[str, float | int | None]) -> dict[str, float | int | None]:
    count = int(stats.get("count") or 0)
    return {
        "count": count,
        "min": stats.get("min"),
        "mean": (float(stats.get("sum") or 0.0) / count) if count else None,
        "max": stats.get("max"),
    }

def _sample_score(record: Candidate) -> int:
    return int.from_bytes(hashlib.sha256(record.id.encode("utf-8")).digest(), "big")


def build_report(args: argparse.Namespace) -> dict[str, Any]:
    policy = load_policy(args.policy)
    heaps: dict[str, list[tuple[int, Candidate]]] = defaultdict(list)
    score_stats: dict[str, dict[str, float | int | None]] = defaultdict(lambda: {"count": 0, "sum": 0.0, "min": None, "max": None})
    raw_counts = Counter()
    accepted_counts = Counter()
    reviewed_actions: dict[str, str] = {}

    pool_path = Path(args.pool)
    pool_path.parent.mkdir(parents=True, exist_ok=True)
    pool_open = gzip.open if str(pool_path).endswith(".gz") else open
    pool_kwargs = {"encoding": "utf-8", "newline": ""}
    if str(pool_path).endswith(".gz"):
        pool_kwargs["compresslevel"] = 6
    with pool_open(pool_path, "wt", **pool_kwargs) as pool_handle:
        for record in iter_candidates(args.candidates):
            if record.collection != "knowledge":
                raise ValueError(f"Knowledge experiment received {record.collection} candidate")
            families = {origin.source_family for origin in record.origins}
            if families != {"wikimatrix"}:
                raise ValueError("Knowledge experiment only accepts WikiMatrix candidates")
            key = pair_key(record.lang, record.meaning_lang)
            rule = rule_for(policy, record.lang, record.meaning_lang)
            action = rule["action"]
            reviewed_actions[key] = action
            raw_counts[key] += 1
            scores = [origin.alignment_score for origin in record.origins if origin.alignment_score is not None]
            if not scores:
                raise ValueError(f"WikiMatrix candidate {record.id} lost its alignment score")
            score = max(scores)
            _score_update(score_stats[key], score)
            threshold = rule.get("minScore")
            keep = action != "reject" and (threshold is None or score >= float(threshold))
            if keep:
                accepted_counts[key] += 1
                pool_handle.write(json.dumps(record.to_dict(), ensure_ascii=False, sort_keys=True) + "\n")
            if args.sample_per_pair > 0:
                bucket = heaps[key]
                bucket.append((_sample_score(record), record))
                bucket.sort(key=lambda item: item[0])
                del bucket[args.sample_per_pair:]

    if not raw_counts:
        raise ValueError("Knowledge experiment received no candidates")

    all_pairs = sorted(raw_counts)
    pair_rows = []
    for key in all_pairs:
        first, second = key.split("-", 1)
        rule = rule_for(policy, first, second)
        pair_rows.append({
            "pair": key,
            "action": rule["action"],
            "minScore": rule.get("minScore"),
            "inputRows": raw_counts[key],
            "retainedRows": accepted_counts[key],
            "rejectedRows": raw_counts[key] - accepted_counts[key],
            "score": _score_public(score_stats[key]),
            "note": rule.get("note", ""),
        })

    samples = []
    for key in sorted(heaps):
        for _score, record in sorted(heaps[key], key=lambda item: item[0]):
            origin_score = max(origin.alignment_score for origin in record.origins if origin.alignment_score is not None)
            samples.append({
                "pair": key,
                "lang": record.lang,
                "meaningLang": record.meaning_lang,
                "context": record.context,
                "meaning": record.meaning,
                "alignmentScore": origin_score,
                "candidateId": record.id,
            })

    unresolved = sorted(key for key, action in reviewed_actions.items() if action == "review")
    rejected = sorted(key for key, action in reviewed_actions.items() if action == "reject")
    report = {
        "reportVersion": 1,
        "part": 8,
        "status": "requires-manual-review" if unresolved else "policy-resolved",
        "publicationSafe": False,
        "policyFile": str(args.policy),
        "summary": {
            "physicalPairsSeen": len(all_pairs),
            "inputCandidates": sum(raw_counts.values()),
            "retainedCandidates": sum(accepted_counts.values()),
            "reviewPairs": len(unresolved),
            "rejectedPairs": len(rejected),
        },
        "reviewPairs": unresolved,
        "rejectedPairs": rejected,
        "pairs": pair_rows,
        "samples": samples,
    }
    return report


def markdown(report: dict[str, Any]) -> str:
    s = report["summary"]
    lines = [
        "# Knowledge direct-pair experiment",
        "",
        "This is a non-publishing Part 8 preview. A WikiMatrix file existing upstream is not by itself a quality decision.",
        "",
        f"Status: **{report['status']}**",
        "",
        f"- physical pairs seen: **{s['physicalPairsSeen']}**",
        f"- input candidates: **{s['inputCandidates']:,}**",
        f"- retained after pair policy: **{s['retainedCandidates']:,}**",
        f"- pairs still requiring review: **{s['reviewPairs']}**",
        f"- rejected pairs: **{s['rejectedPairs']}**",
        "",
        "| pair | action | min score | input | retained | score mean |",
        "| --- | --- | ---: | ---: | ---: | ---: |",
    ]
    for row in report["pairs"]:
        lines.append("| {pair} | {action} | {minScore} | {inputRows:,} | {retainedRows:,} | {median} |".format(
            pair=row["pair"], action=row["action"], minScore=row["minScore"] if row["minScore"] is not None else "-",
            inputRows=row["inputRows"], retainedRows=row["retainedRows"], median=("%.3f" % row["score"]["mean"]) if row["score"]["mean"] is not None else "-",
        ))
    if report["reviewPairs"]:
        lines += ["", "Pairs still marked `review`: " + ", ".join(report["reviewPairs"])]
    lines += ["", "No row from this report is published automatically.", ""]
    return "\n".join(lines)


def samples_markdown(report: dict[str, Any]) -> str:
    lines = ["# WikiMatrix deterministic manual-review samples", ""]
    for row in report["samples"]:
        lines += [
            f"## {row['lang']} -> {row['meaningLang']} · score {row['alignmentScore']:.3f}",
            "",
            row["context"],
            "",
            row["meaning"],
            "",
        ]
    return "\n".join(lines)


def parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--candidates", nargs="+", required=True)
    ap.add_argument("--policy", default=str(DEFAULT_POLICY))
    ap.add_argument("--pool", required=True)
    ap.add_argument("--json", required=True)
    ap.add_argument("--markdown", required=True)
    ap.add_argument("--samples", required=True)
    ap.add_argument("--sample-per-pair", type=int, default=8)
    return ap


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    if args.sample_per_pair < 0:
        raise SystemExit("--sample-per-pair must be non-negative")
    report = build_report(args)
    Path(args.json).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    Path(args.markdown).write_text(markdown(report), encoding="utf-8")
    Path(args.samples).write_text(samples_markdown(report), encoding="utf-8")
    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
