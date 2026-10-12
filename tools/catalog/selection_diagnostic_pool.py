#!/usr/bin/env python3
"""Extract a hash-pinned pair-stratified diagnostic pool; never production data."""
from __future__ import annotations
import argparse
import gzip
import hashlib
import heapq
import json
from collections import Counter
from pathlib import Path

import everyday_pool as pool

SAMPLE_VERSION = "pair-bottom-sha256-v1"


def sample(args):
    sampler_hash = pool.sha256(Path(__file__))
    source = Path(args.pool)
    parent_report = Path(args.pool_report)
    output = Path(args.output_dir)
    if not 1 <= args.per_pair <= 10000:
        raise ValueError("per-pair must be 1..10000")
    if output.exists():
        raise ValueError("output directory must be new")
    parent_hash = pool.sha256(parent_report)
    parent = json.loads(parent_report.read_text(encoding="utf-8"))
    identity = parent["pool"]
    if source.stat().st_size != identity["sizeBytes"] or pool.sha256(source) != identity["sha256"]:
        raise ValueError("parent pool raw identity mismatch")
    if (parent.get("status") != "admitted-source-pool-evidence" or
            parent.get("sourceAdmissionPassed") is not True or parent.get("completeInputScan") is not True or
            parent.get("publicationSafe") is not False):
        raise ValueError("expected complete non-publishing admitted parent evidence")
    if parent["registrySha256"] != pool.sha256(Path(args.registry)):
        raise ValueError("parent registry differs from current registry")
    heaps = {}
    counts = Counter()
    logical = hashlib.sha256()
    logical_bytes = records = 0
    opener = gzip.open if source.suffix == ".gz" else open
    with opener(source, "rb") as handle:
        for line, raw in enumerate(handle, 1):
            logical.update(raw); logical_bytes += len(raw)
            if not raw.strip():
                continue
            row = json.loads(raw)
            pair = (row["lang"], row["meaningLang"])
            if (pair[0] not in parent["learn"] or pair[1] not in parent["meanings"] or
                    pair[0] == pair[1] or row["collection"] != "everyday"):
                raise ValueError("candidate outside parent scope")
            records += 1
            counts[pair] += 1
            score = int.from_bytes(hashlib.sha256((SAMPLE_VERSION + "\0" + row["id"]).encode()).digest(), "big")
            entry = (-score, -line, raw)
            heap = heaps.setdefault(pair, [])
            if len(heap) < args.per_pair:
                heapq.heappush(heap, entry)
            elif entry > heap[0]:
                heapq.heapreplace(heap, entry)
    if (records != identity["uniqueCandidates"] or logical_bytes != identity["logicalSizeBytes"] or
            logical.hexdigest() != identity["logicalSha256"]):
        raise ValueError("parent logical identity mismatch")
    if pool.sha256(source) != identity["sha256"] or pool.sha256(parent_report) != parent_hash:
        raise ValueError("parent changed during sampling")
    output.mkdir(parents=True)
    # Original source bytes and order, without normalized or generated sentences.
    ordered = sorted((-line, raw) for heap in heaps.values() for _score, line, raw in heap)
    candidate_path = output / "diagnostic-source.jsonl"
    with candidate_path.open("wb") as handle:
        for _line, raw in ordered:
            handle.write(raw)
    options = pool.parser().parse_args([
        "--candidates", str(candidate_path), "--source-version", parent["sourceVersion"],
        "--registry", args.registry, "--pool", str(output / "diagnostic-pool.jsonl.gz"),
        "--staging", str(output / "diagnostic-pool.sqlite3"), "--json", str(output / "EVERYDAY-POOL.json"),
        "--markdown", str(output / "EVERYDAY-POOL.md"), "--samples", str(output / "SAMPLES.md"),
        "--learn", ",".join(parent["learn"]), "--meanings", ",".join(parent["meanings"]),
        "--function-top", str(parent["sieve"]["functionTop"])])
    report = pool.build_report(options)
    if (report["registrySha256"] != parent["registrySha256"] or
            pool.sha256(Path(args.registry)) != parent["registrySha256"]):
        raise ValueError("registry changed during diagnostic build")
    if pool.sha256(Path(__file__)) != sampler_hash:
        raise ValueError("sampler changed during diagnostic build")
    # The original builder revalidates all sampled origins and exact identities.
    (output / "EVERYDAY-POOL.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    result = {"diagnosticVersion": 1, "publicationSafe": False, "productionSnapshot": False,
              "sampling": SAMPLE_VERSION, "samplerSha256": sampler_hash, "perPairMaximum": args.per_pair,
              "ordering": "original parent row order, preserved bytes before existing pool builder",
              "parentPool": identity, "parentReportSha256": parent_hash,
              "sampleSourceSha256": pool.sha256(candidate_path),
              "samplePool": report["pool"],
              "pairs": [{"lang": lang, "meaningLang": meaning, "parentRecords": counts[(lang, meaning)],
                         "sampleRecords": len(heaps[(lang, meaning)])} for lang, meaning in sorted(heaps)],
              "limitations": ["Pairs are equally capped, not population-weighted; no representativeness claim within defect types.",
                              "Ranks are rebuilt on the bounded sample, not the accepted full-pool frequency space.",
                              "This scan verifies full parent logical identity; it does not independently validate every unsampled origin.",
                              "The independent unchanged full-input validator supplies all-row provenance/schema evidence.",
                              "Do not use diagnostic selection to replace the accepted production snapshot or predict full-corpus speed."]}
    temporary = output / "DIAGNOSTIC.json.tmp"
    temporary.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(output / "DIAGNOSTIC.json")
    return result


def parser():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--pool", required=True)
    ap.add_argument("--pool-report", required=True)
    ap.add_argument("--registry", default=str(pool.DEFAULT_REGISTRY))
    ap.add_argument("--output-dir", required=True)
    ap.add_argument("--per-pair", type=int, default=128)
    return ap


if __name__ == "__main__":
    result = sample(parser().parse_args())
    print(json.dumps({"pairs": len(result["pairs"]), "sampleRecords": result["samplePool"]["uniqueCandidates"]}))
