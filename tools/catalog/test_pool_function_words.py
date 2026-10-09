#!/usr/bin/env python3
"""Prefix export parity with the real staging/ranking contract, plus refusal paths."""
import gzip
import hashlib
import json
from pathlib import Path
import platform
import sqlite3
import tempfile
import unittest
import zipfile

import pool_function_words as subject
from build_catalogue_v2 import build_ranks, stage_candidates
from ingest.model import Candidate, Origin
from segmentation import prepare


def row(text, ref, meaning="A translation.", meaning_lang="es", lang="en", alternate=None):
    origins = [Origin("tatoeba", "fixture-v1", "tatoeba:" + str(ref), "tatoeba:999")]
    if alternate:
        origins.append(Origin("tatoeba", "fixture-v1", "tatoeba:" + str(alternate), "tatoeba:998"))
    return Candidate("everyday", lang, meaning_lang, text, meaning, origins).to_dict()


class PoolPrefixTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def compare(self, rows, languages):
        plain = self.root / "rows.jsonl"
        plain.write_bytes(b"".join((json.dumps(r, ensure_ascii=False) + "\n").encode("utf-8") for r in rows))
        stage_candidates([str(plain)], self.root / "original.sqlite")
        reference = sqlite3.connect(self.root / "original.sqlite")
        slim = sqlite3.connect(":memory:")
        report = {"learn": languages, "meanings": ["en", "es", "fr", "ja", "ko", "zh"], "sourceVersion": "fixture-v1"}
        try:
            census = subject.stage_contexts(rows, slim, report)
            prefix = subject.function_prefix(slim, languages, 2)
            for lang in languages:
                expected = build_ranks(reference, "everyday", lang)
                self.assertEqual(prefix[lang]["functionForms"], [{"form": f, "rank": n} for f, n in expected.items() if n <= 2])
                self.assertEqual(build_ranks(slim, "everyday", lang), expected)
            return census, prefix
        finally:
            reference.close()
            slim.close()

    def test_primary_context_once_translations_and_alternate_origins_do_not_multiply_counts(self):
        rows = [row("Zulu alpha.", 1, alternate=50),
                row("Zulu alpha.", 1, meaning="Another translation.", meaning_lang="fr"),
                row("beta alpha.", 2), row("beta zulu.", 3)]
        census, prefix = self.compare(rows, ["en"])
        self.assertEqual(census["uniquePrimaryContexts"], 3)
        self.assertEqual(census["candidateRows"], 4)
        self.assertEqual(prefix["en"]["functionForms"], [{"form": "zulu", "rank": 1}, {"form": "alpha", "rank": 2}])

    def test_same_text_at_distinct_primary_refs_and_first_seen_context_text_match_builder(self):
        rows = [row("First beta.", 10), row("First beta.", 11, meaning="A second meaning."),
                row("Changed gamma.", 10, meaning="A third meaning.")]
        census, _ = self.compare(rows, ["en"])
        self.assertEqual(census["uniquePrimaryContexts"], 2)

    def test_cjk_ranks_use_the_same_icu_boundaries(self):
        prepare(["ja", "ko", "zh"])
        rows = [row("私は日本語を学びます。", 1, lang="ja"),
                row("저는 한국어를 배웁니다.", 2, lang="ko"),
                row("我喜欢中文。", 3, lang="zh")]
        self.compare(rows, ["ja", "ko", "zh"])

    def test_foreign_source_version_and_invalid_candidate_identity_fail(self):
        for kind in ("version", "id"):
            value = row("A valid sentence.", 1)
            if kind == "version":
                value["origins"][0]["sourceVersion"] = "different"
            else:
                value["id"] = "wrong"
            with sqlite3.connect(":memory:") as db:
                with self.assertRaises(ValueError):
                    subject.stage_contexts([value], db, {"learn": ["en"], "meanings": ["es"], "sourceVersion": "fixture-v1"})

    def fixture(self):
        rows = [row("Alpha beta gamma.", 1), row("Gamma alpha delta.", 2)]
        logical = b"".join((json.dumps(r) + "\n").encode() for r in rows)
        pool = gzip.compress(logical, mtime=0)
        sha = lambda data: hashlib.sha256(data).hexdigest()
        report = {"part": 7, "status": "admitted-source-pool-evidence", "sourceAdmissionPassed": True,
            "completeInputScan": True, "publicationSafe": False, "corpusCoverage": "provided-input-files-only",
            "learn": ["en"], "meanings": ["es"], "sourceVersion": "fixture-v1",
            "registrySha256": subject.sha_file(subject.DEFAULT_REGISTRY),
            "pipelineSha256": {"build_catalogue_v2.py": subject.sha_file(subject.HERE / "build_catalogue_v2.py")},
            "environment": {"python": platform.python_version(), "segmentation": prepare(["en"])},
            "inputs": {"scopedCandidates": 2},
            "sieve": {"functionTop": 2}, "pool": {"sha256": sha(pool), "sizeBytes": len(pool),
                "logicalSha256": sha(logical), "logicalSizeBytes": len(logical), "uniqueCandidates": 2,
                "duplicateCandidatesMerged": 0},
            "staging": {"uniqueContexts": 2}, "pairs": [{"lang": "en", "meaningLang": "es", "inputCandidates": 2}]}
        return pool, report

    def archive(self, pool, report):
        path = self.root / "input.zip"
        raw = subject.encode(report)
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr(subject.POOL_MEMBER, pool)
            archive.writestr(subject.REPORT_MEMBER, raw)
        return path, subject.sha_file(path), hashlib.sha256(raw).hexdigest()

    def test_completed_archive_export_and_existing_output_refusal(self):
        path, archive_sha, report_sha = self.archive(*self.fixture())
        output = self.root / "result"
        result = subject.run(path, archive_sha, report_sha, output)
        self.assertEqual(result["census"]["candidateRows"], 2)
        self.assertEqual(result["languages"]["en"]["functionForms"][0]["form"], "alpha")
        self.assertFalse(result["publicationSafe"])
        self.assertEqual(sorted(p.name for p in output.iterdir()), ["EVERYDAY-POOL.json", "FUNCTION-WORDS.json"])
        with self.assertRaisesRegex(ValueError, "new directory"):
            subject.run(path, archive_sha, report_sha, output)

    def test_part7_pair_counts_are_before_merging_not_retained_pool_counts(self):
        pool, report = self.fixture()
        report["inputs"]["scopedCandidates"] = 3
        report["pairs"][0]["inputCandidates"] = 3
        report["pool"]["duplicateCandidatesMerged"] = 1
        path, archive_sha, report_sha = self.archive(pool, report)
        result = subject.run(path, archive_sha, report_sha, self.root / "merged-result")
        self.assertEqual(result["census"]["candidatesByPair"], {"en->es": 2})
        self.assertEqual(result["census"]["removedDuplicateCandidatesByPair"], {"en->es": 1})

    def test_hash_logical_environment_and_count_failures_cannot_leave_a_success_report(self):
        for kind in ("archive", "report", "logical", "python", "count", "registry", "pipeline", "premerge", "duplicates"):
            pool, report = self.fixture()
            if kind == "logical": report["pool"]["logicalSha256"] = "0" * 64
            if kind == "python": report["environment"]["python"] = "0.0.0"
            if kind == "count": report["pool"]["uniqueCandidates"] = 3
            if kind == "registry": report["registrySha256"] = "0" * 64
            if kind == "pipeline": report["pipelineSha256"]["build_catalogue_v2.py"] = "0" * 64
            if kind == "premerge": report["inputs"]["scopedCandidates"] = 3
            if kind == "duplicates": report["pool"]["duplicateCandidatesMerged"] = 1
            path, archive_sha, report_sha = self.archive(pool, report)
            output = self.root / kind
            with self.assertRaises(ValueError):
                subject.run(path, "0" * 64 if kind == "archive" else archive_sha,
                            "0" * 64 if kind == "report" else report_sha, output)
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
