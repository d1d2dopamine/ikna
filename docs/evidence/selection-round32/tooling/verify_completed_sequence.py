#!/usr/bin/env python3
"""Verify finalized Round32 outputs; never invoke selection or open staging."""
from __future__ import annotations

import argparse
import datetime
import hashlib
import json
from pathlib import Path
import pstats
import statistics

EXPECTED = "f551785d003b9a8f0793bedd687829f0a3364ebf47cab80f83d2a1f052ee12bd"
NAMES = ["baseline-01", "candidate-01", "candidate-02", "baseline-02", "baseline-03", "candidate-03", "candidate-cprofile-01"]


def sha(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while data := handle.read(1024 * 1024):
            digest.update(data)
    return digest.hexdigest()


def identical(a, b):
    with a.open("rb") as left, b.open("rb") as right:
        while True:
            x, y = left.read(1024 * 1024), right.read(1024 * 1024)
            if x != y:
                return False
            if not x:
                return True


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def verify(results, round31, output):
    sequence_path = results / "REAL-SEQUENCE.json"
    sequence_sha = sha(sequence_path)
    sequence = read(sequence_path)
    assert sequence["status"] == "complete-seven-serial-probes", "Sequence is incomplete"
    assert [r["name"] for r in sequence["runs"]] == NAMES
    assert all(r["exitCode"] == 0 for r in sequence["runs"])
    assert not output.exists(), "Use a new verification output directory"
    stamp = lambda value: datetime.datetime.fromisoformat(value)
    assert all(stamp(a["finishedUtc"]) <= stamp(b["startedUtc"]) for a, b in zip(sequence["runs"], sequence["runs"][1:]))
    probes = {n: read(results / n / "PROBE.json") for n in NAMES}
    baseline = probes["baseline-01"]
    baseline_payload = results / "baseline-01" / "EVIDENCE.json"
    reference = round31 / "baseline-01" / "EVIDENCE.json"
    assert sha(reference) == EXPECTED and reference.stat().st_size == 31461064
    assert identical(baseline_payload, reference)
    same_keys = ("producerBindings", "helperSha256", "candidateBinding", "settings", "runtime", "prefixIdentity",
                 "sqliteConnection", "sourcePinsWithResolvedPaths", "pairStats", "outputIdentity", "limits", "queryPlan")
    verification_rows, summary_rows = [], []
    for n, rec in zip(NAMES, sequence["runs"]):
        p = probes[n]
        path, payload = results / n / "PROBE.json", results / n / "EVIDENCE.json"
        assert p["status"] == "complete-dense-pair-overlap-experiment"
        assert p["mode"] == rec["mode"] and p["publicationSafe"] is False
        assert p["originalChooserRestored"] is True
        assert p["candidateModuleExecuted"] is (p["mode"] != "baseline")
        assert all(p[key] == baseline[key] for key in same_keys)
        identity = {"sha256": sha(payload), "sizeBytes": payload.stat().st_size}
        assert identity == p["outputIdentity"] == {"sha256": EXPECTED, "sizeBytes": 31461064}
        assert identical(payload, baseline_payload)
        assert p["writtenTokenCache"]["before"] == {"currsize": 0, "hits": 0, "maxsize": 8192, "misses": 0}
        assert p["quarantineRuleCache"]["before"] == {"currsize": 0, "hits": 0, "maxsize": 1, "misses": 0}
        for name, digest in p["helperSha256"].items():
            assert sha(results / name) == digest
        assert sha(Path(p["candidateBinding"]["path"])) == p["candidateBinding"]["sha256"]
        for binding in p["sourcePinsWithResolvedPaths"].values():
            assert sha(Path(binding["path"])) == binding["sha256"]
        for comparison in ("baselineComparison", "candidateProfileComparison"):
            if comparison in p:
                ref = p[comparison]
                assert ref["exactReturnedEvidenceEqual"] is True and sha(Path(ref["path"])) == ref["sha256"]
        verification_rows.append({"name": n, "manifestSha256": sha(path), "outputIdentity": identity,
                                  "byteEqualToBaseline": True, "exitCode": rec["exitCode"]})
        summary_rows.append({"name": n, "mode": p["mode"], "startedUtc": rec["startedUtc"], "finishedUtc": rec["finishedUtc"],
                             "pair": p["pairMeasurement"], "totalElapsedSeconds": p["totalElapsedSeconds"],
                             "totalProcessCpuSeconds": p["totalProcessCpuSeconds"], "finalProcessEvidence": p["finalProcessEvidence"],
                             "resourcePreflight": p["resourcePreflight"], "writtenTokenCache": p["writtenTokenCache"],
                             "quarantineRuleCache": p["quarantineRuleCache"], "outputIdentity": identity})
    profile = probes["candidate-cprofile-01"]
    for name, digest in profile["profileArtifacts"].items():
        assert sha(results / "candidate-cprofile-01" / name) == digest
    raw_stats = pstats.Stats(str(results / "candidate-cprofile-01" / "PAIR.pstats"))
    for entry in profile["callProfile"]["focusedCalls"]:
        primitive, calls, own, cumulative, _ = raw_stats.stats[(entry["path"], entry["line"], entry["function"])]
        assert (primitive, calls, own, cumulative) == (entry["primitiveCalls"], entry["calls"], entry["selfSeconds"], entry["cumulativeSeconds"])
    previous_profile = read(round31 / "cprofile-01" / "PROBE.json")
    previous_overlap = next(e for e in previous_profile["callProfile"]["focusedCalls"] if e["function"] == "context_overlap")
    candidate_overlap = next(e for e in profile["callProfile"]["focusedCalls"] if e["function"] == "context_overlap")
    pairs = []
    for i in range(1, 4):
        b, c = probes[f"baseline-{i:02}"], probes[f"candidate-{i:02}"]
        bw, cw = b["pairMeasurement"]["elapsedSeconds"], c["pairMeasurement"]["elapsedSeconds"]
        bc, cc = b["pairMeasurement"]["processCpuSeconds"], c["pairMeasurement"]["processCpuSeconds"]
        pairs.append({"baseline": f"baseline-{i:02}", "candidate": f"candidate-{i:02}",
                      "baselinePairElapsedSeconds": bw, "candidatePairElapsedSeconds": cw,
                      "observedBaselineOverCandidateWallRatio": bw / cw, "observedWallReductionFraction": 1 - cw / bw,
                      "observedBaselineOverCandidateCpuRatio": bc / cc})
    btimes = [probes[f"baseline-{i:02}"]["pairMeasurement"]["elapsedSeconds"] for i in range(1, 4)]
    ctimes = [probes[f"candidate-{i:02}"]["pairMeasurement"]["elapsedSeconds"] for i in range(1, 4)]
    summary = {"status": "complete-seven-serial-probes-independently-verified", "publicationSafe": False,
               "scope": baseline["settings"], "prefixIdentity": baseline["prefixIdentity"], "pairStats": baseline["pairStats"],
               "runs": summary_rows, "pairedUnprofiledObservations": pairs,
               "unprofiledWallSummary": {"baselineMinSeconds": min(btimes), "baselineMaxSeconds": max(btimes),
                    "baselineMedianSeconds": statistics.median(btimes), "candidateMinSeconds": min(ctimes),
                    "candidateMaxSeconds": max(ctimes), "candidateMedianSeconds": statistics.median(ctimes),
                    "observedRatioOfMedians": statistics.median(btimes) / statistics.median(ctimes)},
               "candidateProfile": {"focusedCalls": profile["callProfile"]["focusedCalls"],
                    "totalCalls": raw_stats.total_calls, "primitiveCalls": raw_stats.prim_calls,
                    "referenceComparison": profile["candidateProfileComparison"],
                    "originalRound31OverlapCalls": previous_overlap["calls"], "candidateOverlapCalls": candidate_overlap["calls"],
                    "overlapCallReductionFraction": 1 - candidate_overlap["calls"] / previous_overlap["calls"]},
               "limits": baseline["limitations"] + ["Three unprofiled observations per implementation; no randomized trial or confidence interval.",
                    "C2 is paired with B2 for timing here, while all candidate manifests bind B1 for identity.",
                    "Profile binds C1/B1 as actually executed; historical harness guide suggested C3/B3."]}
    assert sha(sequence_path) == sequence_sha
    verification = {"status": "complete-independent-round32-verification", "publicationSafe": False,
                    "verifiedAtUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                    "sequenceSha256": sequence_sha, "runs": verification_rows, "allFullOutputsStreamHashed": True,
                    "allFullOutputsByteCompared": True, "round31BaselineByteEqual": True,
                    "sourceAndHelperBindingsRechecked": True, "rawPstatsMatchesReportedFocusedMetrics": True,
                    "selectorExecuted": False, "fullStagingRehashedByThisVerifier": False,
                    "verifierSha256": sha(Path(__file__))}
    output.mkdir()
    for name, value in (("AGGREGATE.json", summary), ("INDEPENDENT-VERIFICATION.json", verification)):
        (output / name).write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": verification["status"], "outputs": len(NAMES), "wallSummary": summary["unprofiledWallSummary"],
                      "candidateOverlapCalls": candidate_overlap["calls"]}, sort_keys=True))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results", required=True, type=Path)
    parser.add_argument("--round31", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    verify(args.results.resolve(), args.round31.resolve(), args.output.resolve())
