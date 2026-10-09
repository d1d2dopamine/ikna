#!/usr/bin/env python3
"""Anchor/hash refusal and unknown-vocabulary isolation in source diagnostics."""
import hashlib
from pathlib import Path
import tempfile

from replenishment_review import measure, pair_candidates, verify_files, wmt_join


def refuses(function):
    try:
        function()
    except ValueError:
        return
    raise AssertionError("invalid source evidence accepted")


def main():
    row = {"document_id": "d", "segment_id": 1, "source": "Original human source.",
           "target": "Widzę piękną jabłoń w naszym ogrodzie.", "domain": "social", "is_bad_source": False}
    other = {**row, "target": "나는 오늘 정원에서 사과 나무를 봅니다."}
    pairs, stats = wmt_join([row], [other])
    assert len(pairs) == 1 and stats["retainedDomainSegments"] == 1
    assert not wmt_join([row], [{**other, "is_bad_source": True}])[0]
    refuses(lambda: wmt_join([row, row], [other]))
    refuses(lambda: wmt_join([row], [{**other, "source": "A different original."}]))
    refuses(lambda: wmt_join([row], [{**other, "segment_id": 2}]))
    with tempfile.TemporaryDirectory(prefix="ikna-replenishment-test-") as td:
        root = Path(td)
        path = root / "data"
        path.write_bytes(b"pinned")
        manifest = [{"file": "data", "sizeBytes": 6, "sha256": hashlib.sha256(b"pinned").hexdigest()}]
        verify_files(root, manifest)
        path.write_bytes(b"broken")
        refuses(lambda: verify_files(root, manifest))
        manifest[0]["file"] = "../data"
        refuses(lambda: verify_files(root, manifest))
        records = pair_candidates(pairs, "fixture", "pinned")
        assert len(records) == 2 and records[0].meaning == other["target"]
        assert records[1].meaning == row["target"]  # No QA answer or generated translation.
        ranks = {"pl": {"jabłoń": 2000}, "ko": {"사과": 2000}}
        records += pair_candidates(pairs, "independent-fixture", "pinned")
        result = measure(records, ranks, root, "unknown")
        for pair in ("pl->ko", "ko->pl"):
            assert result[pair]["originPreservingMerge"] == {
                "inputCandidates": 4, "uniqueCandidates": 2, "originOccurrences": 4, "stageDuplicateCandidates": 0}
            assert result[pair]["outOfBaselineWrittenFormsBeforeSieve"] > 0
            assert result[pair]["levels"]["middle"]["eligibleTargets"] == 1
            assert result[pair]["levels"]["advanced"]["eligibleTargets"] == 0
    print("Catalogue replenishment diagnostics: OK")


if __name__ == "__main__":
    main()
