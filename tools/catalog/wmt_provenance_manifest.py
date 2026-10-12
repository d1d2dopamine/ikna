#!/usr/bin/env python3
"""Reconstruct metadata for the pinned round-20 WMT pl/ko anchor filter.

The caller supplies the two local official JSONL files; this tool never acquires
data, runs a selector/model, emits source/reference text or admits a source.
Both files must match FILES.json and the historical REPLENISHMENT.json pins
before either is parsed. Aggregate drift is an error, not a new measurement.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import sys


HERE = Path(__file__).resolve().parent
INPUTS = {
    "wmt24pp--en-pl_PL.jsonl": "en-pl_PL",
    "wmt24pp--en-ko_KR.jsonl": "en-ko_KR",
}
FIELDS = frozenset({"lp", "domain", "document_id", "segment_id", "source",
                    "target", "original_target", "is_bad_source"})
REASONS = frozenset({"bad-source", "outside-everyday-domains", "empty-human-reference"})
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
REVISION = re.compile(r"[0-9a-f]{40}\Z")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _object(pairs: list[tuple]) -> dict:
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate JSON object key")
        result[key] = value
    return result


def _invalid_constant(value: str) -> None:
    raise ValueError("non-finite JSON number")


def _json(data: bytes | str) -> object:
    # Do not put untrusted field values or fragments of corpus text in errors.
    try:
        return json.loads(data, object_pairs_hook=_object, parse_constant=_invalid_constant)
    except (ValueError, UnicodeError):
        raise ValueError("invalid JSON structure or encoding") from None


def _pins(manifest: object, revision: str) -> list[dict]:
    if not isinstance(manifest, list):
        raise ValueError("input manifest must be a list")
    found = {}
    for entry in manifest:
        if not isinstance(entry, dict):
            raise ValueError("invalid input manifest entry")
        name = entry.get("file")
        if not isinstance(name, str) or name not in INPUTS:
            continue  # Other round-20 corpora and WMT locales are not inputs.
        expected_url = ("https://huggingface.co/datasets/google/wmt24pp/resolve/"
                        + revision + "/" + INPUTS[name] + ".jsonl")
        if (name in found or "error" in entry or entry.get("url") != expected_url
                or type(entry.get("sizeBytes")) is not int or entry["sizeBytes"] <= 0
                or not isinstance(entry.get("sha256"), str)
                or not SHA256.fullmatch(entry["sha256"])):
            raise ValueError("invalid, duplicate or unpinned WMT input entry")
        found[name] = {key: entry[key] for key in ("file", "url", "sizeBytes", "sha256")}
    if set(found) != set(INPUTS):
        raise ValueError("both pinned WMT pl/ko files are required")
    return [found[name] for name in sorted(found)]


def _expected_aggregate(expected: object) -> dict:
    if not isinstance(expected, dict):
        raise ValueError("expected replenishment report must be an object")
    if any(expected.get(key) is not False for key in
           ("publicationSafe", "sourceAdmissionChanged", "catalogueBuilt", "semanticAccuracyCertified")):
        raise ValueError("expected report must retain non-admission diagnostic flags")
    aggregate = expected.get("wmt24pp")
    if not isinstance(aggregate, dict):
        raise ValueError("missing expected WMT aggregate")
    counts = {key: aggregate.get(key) for key in ("sourceSegments", "retainedDomainSegments")}
    rejected = aggregate.get("rejected")
    if (any(type(value) is not int or value < 0 for value in counts.values())
            or not isinstance(rejected, dict) or set(rejected) - REASONS
            or any(type(value) is not int or value <= 0 for value in rejected.values())
            or counts["sourceSegments"] != counts["retainedDomainSegments"] + sum(rejected.values())):
        raise ValueError("invalid expected WMT aggregate")
    return {**counts, "rejected": dict(rejected)}


def _read_verified_inputs(primary: Path, pins: list[dict]) -> dict[str, bytes]:
    verified = {}
    for pin in pins:
        path = primary / pin["file"]
        if not path.is_file() or path.stat().st_size != pin["sizeBytes"]:
            raise ValueError("WMT input size differs from pin")
        # Read no more than the expected bytes plus one. Parsing later uses these
        # exact verified bytes, never a second open susceptible to replacement.
        with path.open("rb") as handle:
            data = handle.read(pin["sizeBytes"] + 1)
        if len(data) != pin["sizeBytes"] or sha256(data) != pin["sha256"]:
            raise ValueError("WMT input SHA-256 or size differs from pin")
        verified[pin["file"]] = data
    return verified


def _parse_rows(data: bytes) -> list[dict]:
    try:
        text = data.decode("utf-8")
    except UnicodeError:
        raise ValueError("invalid WMT UTF-8 encoding") from None
    # This is the original reader's splitlines/blank-line treatment.
    return [_json(line) for line in text.splitlines() if line.strip()]


def _index(rows: list[dict], lp: str) -> dict[tuple[str, int], dict]:
    result = {}
    for row in rows:
        if not isinstance(row, dict) or set(row) != FIELDS:
            raise ValueError("invalid WMT row schema")
        if any(not isinstance(row[key], str) for key in
               ("lp", "domain", "document_id", "source", "target", "original_target")):
            raise ValueError("invalid WMT string field")
        if (not row["document_id"].strip() or type(row["segment_id"]) is not int
                or row["segment_id"] < 0):
            raise ValueError("invalid WMT document/segment anchor")
        if row["lp"] != lp:
            raise ValueError("WMT locale/file mismatch")
        if type(row["is_bad_source"]) is not bool:
            raise ValueError("invalid WMT bad-source flag")
        key = (row["document_id"], row["segment_id"])
        if key in result:
            raise ValueError("duplicate WMT anchor")
        result[key] = row
    return result


def classify(left: dict, right: dict) -> str:
    """Exact wmt_join decision order after the stricter input schema gate.

    Reference strings are not normalized: strip() is used only to test emptiness.
    Tests compare this pure classifier against the unchanged original function.
    """
    if left["source"] != right["source"] or left["domain"] != right["domain"]:
        raise ValueError("WMT anchor does not identify the same source segment")
    if left["is_bad_source"] or right["is_bad_source"]:
        return "bad-source"
    if left["domain"] not in ("social", "speech"):
        return "outside-everyday-domains"
    if not left["target"].strip() or not right["target"].strip():
        return "empty-human-reference"
    return "retained"


def reconstruct(left: list[dict], right: list[dict]) -> tuple[list[dict], dict]:
    a, b = _index(left, "en-pl_PL"), _index(right, "en-ko_KR")
    if a.keys() != b.keys():
        raise ValueError("WMT language anchor sets differ")
    rows, rejected = [], Counter()
    for document_id, segment_id in sorted(a):
        x, y = a[document_id, segment_id], b[document_id, segment_id]
        reason = classify(x, y)
        if reason != "retained":
            rejected[reason] += 1
        # Explicit allowlist: no original row, human reference, machine output,
        # text excerpt, normalized text or input-field dump can reach the report.
        rows.append({"documentId": document_id, "segmentId": segment_id,
                     "domain": x["domain"], "retained": reason == "retained", "reason": reason,
                     "sourceSha256": sha256(x["source"].encode("utf-8")),
                     "referenceSha256": {"pl_PL": sha256(x["target"].encode("utf-8")),
                                         "ko_KR": sha256(y["target"].encode("utf-8"))}})
    return rows, {"sourceSegments": len(rows), "retainedDomainSegments": len(rows) - sum(rejected.values()),
                  "rejected": dict(rejected)}


def run(primary: Path, manifest_path: Path, revisions_path: Path, expected_path: Path) -> dict:
    evidence_bytes = {"manifest": manifest_path.read_bytes(), "revisions": revisions_path.read_bytes(),
                      "expected": expected_path.read_bytes()}
    manifest, revisions, expected = (_json(evidence_bytes[key]) for key in
                                     ("manifest", "revisions", "expected"))
    aggregate = _expected_aggregate(expected)
    revision = revisions.get("wmt24pp") if isinstance(revisions, dict) else None
    if not isinstance(revision, str) or not REVISION.fullmatch(revision):
        raise ValueError("missing or invalid WMT revision")
    if not isinstance(expected.get("revisions"), dict) or expected["revisions"].get("wmt24pp") != revision:
        raise ValueError("WMT revision differs from expected report")
    pins = _pins(manifest, revision)
    if pins != _pins(expected.get("inputFiles"), revision):
        raise ValueError("WMT file pins differ from expected report")
    original_sha = sha256((HERE / "replenishment_review.py").read_bytes())
    if (not isinstance(expected.get("pipelineSha256"), dict)
            or expected["pipelineSha256"].get("replenishment_review.py") != original_sha):
        raise ValueError("original WMT filter source differs from expected report")
    data = _read_verified_inputs(primary, pins)  # Verify BOTH before parsing either.
    rows, actual = reconstruct(_parse_rows(data["wmt24pp--en-pl_PL.jsonl"]),
                               _parse_rows(data["wmt24pp--en-ko_KR.jsonl"]))
    if actual != aggregate:
        raise ValueError("reconstructed WMT aggregate differs from expected report")
    return {"reportVersion": 1, "sourceFamily": "wmt24pp", "sourceRevision": revision,
            "publicationSafe": False, "sourceAdmissionChanged": False, "catalogueBuilt": False,
            "semanticAccuracyCertified": False, "rightsStatus": "unresolved",
            "attributionStatus": "original-publication-and-contributor-review-required",
            "scope": "pl/ko anchor metadata only; retained means original domain filter, not study approval",
            "verifiedTargetLocales": ["pl_PL", "ko_KR"], "sharedEnglishSourceVerified": True,
            "otherTargetLocalesReverified": False, "expectedAggregateMatched": True,
            "hashEncoding": "SHA-256 of exact decoded string encoded as UTF-8; no normalization",
            "inputFiles": pins, "evidenceSha256": {key: sha256(value) for key, value in evidence_bytes.items()},
            "pipelineSha256": {"replenishment_review.py": original_sha,
                               "wmt_provenance_manifest.py": sha256(Path(__file__).read_bytes())},
            "wmt24pp": actual, "rows": rows}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("primary", "manifest", "revisions", "expected", "out"):
        parser.add_argument("--" + name, required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        if args.out.exists() or args.out.is_symlink() or args.out.resolve().is_relative_to(args.primary.resolve()):
            raise ValueError("output must be new and outside source data")
        result = run(args.primary, args.manifest, args.revisions, args.expected)
        encoded = json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True, allow_nan=False) + "\n"
        args.out.parent.mkdir(parents=True, exist_ok=True)
        with args.out.open("x", encoding="utf-8", newline="\n") as handle:
            handle.write(encoded)
        print(json.dumps({key: result[key] for key in
                          ("wmt24pp", "expectedAggregateMatched", "rightsStatus", "publicationSafe")},
                         sort_keys=True))
        return 0
    except ValueError as error:
        print("WMT provenance manifest refused: " + str(error), file=sys.stderr)
        return 1
    except OSError:
        print("WMT provenance manifest refused: input/output filesystem error", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
