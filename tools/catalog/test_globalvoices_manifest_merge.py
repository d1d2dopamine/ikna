#!/usr/bin/env python3
"""Offline regression fixtures for full World provenance evidence/recovery."""
from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from globalvoices_manifest import FetchResult, build_manifest, load_cache, shard_index_for_document
from globalvoices_manifest_merge import main, merge


class ManifestMergeTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.alignment = self.root / "alignment.jsonl"
        self.docs = ["en/2012_07_06_example_.xml", "es/2012_07_06_example_.xml"]
        self.alignment.write_text(json.dumps({"line": 1, "contextDocument": self.docs[0],
                                             "meaningDocument": self.docs[1]}) + "\n", encoding="utf-8")
        self.manifests: list[str] = []
        self.reports: list[str] = []
        self.prepare()

    @staticmethod
    def verified(url: str) -> FetchResult:
        html = '<link rel="canonical" href="' + url + '"><script type="application/ld+json">' + json.dumps({
            "@type": "NewsArticle", "url": url, "author": {"@type": "Person", "name": "Fixture Author"}
        }) + '</script>'
        return FetchResult(url, html)

    def prepare(self, fetcher=None, max_documents: int = 0) -> None:
        self.manifests.clear()
        self.reports.clear()
        for index in range(2):
            rows, report = build_manifest(str(self.alignment), workers=1, shard_count=2,
                                          shard_index=index, fetcher=fetcher or self.verified,
                                          max_documents=max_documents)
            mp = self.root / f"manifest-{index}.jsonl"
            rp = self.root / f"report-{index}.json"
            mp.write_text("".join(json.dumps(row, sort_keys=True) + "\n" for row in rows), encoding="utf-8")
            rp.write_text(json.dumps(report), encoding="utf-8")
            self.manifests.append(str(mp))
            self.reports.append(str(rp))

    def update_report(self, index: int, mutate) -> None:
        path = Path(self.reports[index])
        report = json.loads(path.read_text(encoding="utf-8"))
        mutate(report)
        path.write_text(json.dumps(report), encoding="utf-8")

    def result(self):
        return merge(str(self.alignment), self.manifests, self.reports, expected_shards=2)

    def test_complete_reproducible_merge_and_input_hashes(self) -> None:
        rows, report = self.result()
        self.assertEqual(len(rows), 2)
        self.assertTrue(report["completeScan"])
        self.assertEqual(report["summary"]["verifiedAlignmentRows"], 1)
        self.assertEqual(report["recovery"]["retryShardIndexes"], [])
        self.assertEqual(report["inputEvidence"]["legacyShardReportsWithoutInputHash"], 0)
        reversed_result = merge(str(self.alignment), list(reversed(self.manifests)),
                                list(reversed(self.reports)), expected_shards=2)
        self.assertEqual((rows, report), reversed_result)

    def test_transport_failure_targets_only_affected_shard(self) -> None:
        def fetch(url):
            if "es.globalvoices" in url:
                raise OSError("fixture transport failure")
            return self.verified(url)
        self.prepare(fetch)
        _, report = self.result()
        self.assertFalse(report["completeScan"])
        self.assertEqual(report["summary"]["missingShards"], 0)
        self.assertEqual(report["summary"]["unattemptedDocuments"], 0)
        self.assertEqual(report["summary"]["retryableUnresolvedDocuments"], 1)
        self.assertEqual(report["recovery"]["retryableDocuments"], [self.docs[1]])
        self.assertEqual(report["recovery"]["retryShardIndexes"], [shard_index_for_document(self.docs[1], 2)])

    def test_stable_rejection_completes_scan_without_publishing_failed_document(self) -> None:
        self.prepare(lambda url: FetchResult(url, "<html>No article attribution</html>"))
        rows, report = self.result()
        self.assertEqual(rows, [])
        self.assertTrue(report["completeScan"])
        self.assertEqual(report["summary"]["unresolvedDocuments"], 2)
        self.assertEqual(report["summary"]["verifiedAlignmentRows"], 0)
        self.assertEqual(report["recovery"]["retryShardIndexes"], [])

    def test_missing_shard_remains_incomplete(self) -> None:
        rows, report = merge(str(self.alignment), self.manifests, self.reports[:1], expected_shards=2)
        self.assertEqual(len(rows), 2)
        self.assertFalse(report["completeScan"])
        self.assertEqual(report["recovery"]["missingShardIndexes"], [1])
        self.assertEqual(report["recovery"]["retryShardIndexes"], [1])

    def test_full_recovery_inventory_is_not_preview_capped(self) -> None:
        with self.alignment.open("w", encoding="utf-8") as handle:
            for i in range(300):
                handle.write(json.dumps({"line": i + 1, "contextDocument": f"en/2012_07_06_example{i}_.xml",
                                         "meaningDocument": f"es/2012_07_06_example{i}_.xml"}) + "\n")
        self.prepare(max_documents=2)
        _, report = self.result()
        self.assertEqual(len(report["sampleUnattemptedDocuments"]), 200)
        self.assertEqual(len(report["recovery"]["unattemptedDocuments"]), 598)
        self.assertFalse(report["completeScan"])

    def test_mixed_alignment_inputs_fail(self) -> None:
        self.update_report(0, lambda r: r.update(alignmentMapSha256="0" * 64))
        with self.assertRaisesRegex(ValueError, "hash differs"):
            self.result()

    def test_new_report_requires_input_hash(self) -> None:
        self.update_report(0, lambda r: r.pop("alignmentMapSha256"))
        with self.assertRaisesRegex(ValueError, "missing alignment map hash"):
            self.result()

    def test_unsupported_or_unsafe_reports_fail_closed(self) -> None:
        for fields in ({"reportVersion": 99}, {"part": 7}, {"publicationSafe": False}):
            with self.subTest(fields=fields):
                self.prepare()
                self.update_report(0, lambda r: r.update(fields))
                with self.assertRaisesRegex(ValueError, "unsupported or unsafe"):
                    self.result()

    def test_identical_duplicates_are_deduplicated_but_conflicts_fail(self) -> None:
        rows, _ = self.result()
        duplicate = self.root / "duplicate.jsonl"
        duplicate.write_text(json.dumps(rows[0]) + "\n", encoding="utf-8")
        self.manifests.append(str(duplicate))
        merged, report = self.result()
        self.assertEqual(merged, rows)
        self.assertEqual(report["summary"]["duplicateIdenticalRows"], 1)
        duplicate.write_text(json.dumps({**rows[0], "contributors": ["Different Author"]}) + "\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "conflicting verified manifest rows"):
            self.result()

    def test_legacy_reports_are_explicitly_labelled(self) -> None:
        for index in range(2):
            self.update_report(index, lambda r: (r.update(reportVersion=2), r.pop("alignmentMapSha256")))
        _, report = self.result()
        self.assertTrue(report["completeScan"])
        self.assertEqual(report["inputEvidence"]["legacyShardReportsWithoutInputHash"], 2)

    def test_invalid_shard_indexes_fail(self) -> None:
        for index in (-1, 2, True):
            with self.subTest(index=index):
                self.prepare()
                self.update_report(0, lambda r: r["shard"].update(index=index))
                with self.assertRaises(ValueError):
                    self.result()

    def test_foreign_verified_document_fails(self) -> None:
        path = Path(self.manifests[0])
        with path.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps({"document": "en/foreign.xml", "articleUrl": "https://globalvoices.org/example/",
                                     "contributors": ["Fixture Author"]}) + "\n")
        with self.assertRaisesRegex(ValueError, "outside alignment inventory"):
            self.result()

    def test_wrong_shard_unresolved_document_fails(self) -> None:
        self.prepare(lambda url: FetchResult(url, "<html>No article attribution</html>"))
        doc = self.docs[0]
        own = shard_index_for_document(doc, 2)
        other = 1 - own
        own_path = Path(self.reports[own])
        own_report = json.loads(own_path.read_text(encoding="utf-8"))
        item = next(row for row in own_report["unresolved"] if row["document"] == doc)
        own_report["unresolved"].remove(item)
        own_path.write_text(json.dumps(own_report), encoding="utf-8")
        self.update_report(other, lambda r: r["unresolved"].append(item))
        with self.assertRaisesRegex(ValueError, "belongs to another shard"):
            self.result()

    def test_conflicting_resolved_and_unresolved_outcome_fails(self) -> None:
        doc = self.docs[0]
        self.update_report(shard_index_for_document(doc, 2), lambda r: r["unresolved"].append({
            "document": doc, "affectedRows": 1, "reason": "fetch-error:fixture"
        }))
        with self.assertRaisesRegex(ValueError, "conflicting document outcome"):
            self.result()

    def test_changed_affected_row_counts_fail(self) -> None:
        self.prepare(lambda url: FetchResult(url, "<html>No article attribution</html>"))
        index = shard_index_for_document(self.docs[0], 2)
        self.update_report(index, lambda r: r["unresolved"][0].update(affectedRows=99))
        with self.assertRaisesRegex(ValueError, "affectedRows differs"):
            self.result()

    def test_corrupt_utf8_manifest_is_not_silently_repaired(self) -> None:
        Path(self.manifests[0]).write_bytes(b'{"document":"en/foreign.xml","contributors":["A\xff"]}\n')
        with self.assertRaises(UnicodeDecodeError):
            self.result()

    def test_corrupt_utf8_alignment_fails_before_verification(self) -> None:
        self.alignment.write_bytes(b'{"contextDocument":"en/a\xff.xml","meaningDocument":"es/a.xml"}\n')
        with self.assertRaises(UnicodeDecodeError):
            self.prepare()

    def test_corrupt_utf8_cache_is_not_reused(self) -> None:
        cache = self.root / "cache.jsonl"
        cache.write_bytes(b'{"cacheVersion":1,"document":"en/a.xml","outcome":"unresolved","reason":"bad\xff"}\n')
        with self.assertRaises(UnicodeDecodeError):
            load_cache(str(cache))

    def test_cli_rejects_output_aliases_before_overwriting_inputs(self) -> None:
        before = self.alignment.read_bytes()
        common = ["--alignment-map", str(self.alignment), "--manifest-glob", str(self.root / "manifest-*.jsonl"),
                  "--report-glob", str(self.root / "report-*.json"), "--expected-shards", "2"]
        for output in (self.alignment, Path(self.manifests[0]), Path(self.reports[0])):
            with self.subTest(output=output):
                with self.assertRaisesRegex(SystemExit, "must not overwrite"):
                    main(common + ["--out", str(output), "--json", str(self.root / "merged.json"),
                                   "--markdown", str(self.root / "merged.md")])
        with self.assertRaisesRegex(SystemExit, "must be distinct"):
            main(common + ["--out", str(self.root / "same"), "--json", str(self.root / "same"),
                           "--markdown", str(self.root / "merged.md")])
        self.assertEqual(before, self.alignment.read_bytes())


if __name__ == "__main__":
    unittest.main(verbosity=2)
