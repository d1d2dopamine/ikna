#!/usr/bin/env python3
"""Offline cache fixtures: exact decisions, counters and fail-closed artifacts."""
import gzip
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import selection_analysis_cache as cache
import selection_experiment as selection


class AnalysisCacheTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.db = sqlite3.connect(self.root / "staging.sqlite")
        self.addCleanup(self.db.close)
        self.db.executescript(selection.SCHEMA)
        self.collection, self.lang = "everyday", "en"
        for text in ("Straße STRASSE mountains beyond the valley.", "Walking beside beautiful rivers today."):
            self.db.execute("INSERT INTO context VALUES(?,?,?,?)", (self.collection, self.lang,
                selection._context_key(self.collection, self.lang, text), text))
        self.db.commit()
        self.ranks = selection.build_ranks(self.db, self.collection, self.lang)
        self.calls = 0
        self.options = dict(collection=self.collection, lang=self.lang, ranks=self.ranks,
            function_top=0, japanese_guard=None, canonical_unique=True,
            cache_dir=self.root / "cache", registry_sha256="a" * 64,
            parent_identity={"poolSha256": "b" * 64, "scope": ["en"]})

    def compute(self):
        self.calls += 1
        o = self.options
        return selection.build_context_analysis(self.db, o["collection"], o["lang"],
            o["ranks"], o["function_top"], japanese_guard=o["japanese_guard"],
            canonical_unique=o["canonical_unique"])

    def run_cache(self, compute=None):
        return cache.run_cached_analysis(self.db, compute=compute or self.compute, **self.options)

    def rows(self):
        return self.db.execute("SELECT * FROM context_analysis ORDER BY collection,lang,context_key").fetchall()

    def paths(self, diagnostic):
        root = Path(self.options["cache_dir"])
        return root / (diagnostic["key"] + ".jsonl.gz"), root / (diagnostic["key"] + ".json")

    def rewrite(self, diagnostic, rows=None, change=None):
        data, manifest = self.paths(diagnostic)
        envelope = json.loads(manifest.read_bytes())
        if rows is not None:
            with data.open("wb") as raw:
                with gzip.GzipFile(fileobj=raw, mode="wb", mtime=0) as out:
                    for row in rows:
                        out.write(cache._json(row) + b"\n")
            envelope["payload"]["dataBytes"] = data.stat().st_size
            envelope["payload"]["dataSha256"] = cache._file_sha(data)
        if change:
            change(envelope["payload"])
        envelope["sha256"] = cache._sha(envelope["payload"])
        manifest.write_bytes(cache._json(envelope))

    def assert_unchanged_failure(self, diagnostic):
        before = self.rows()
        paths = self.paths(diagnostic)
        artifacts = [p.read_bytes() if p.exists() else None for p in paths]
        with self.assertRaises(cache.CacheIntegrityError):
            self.run_cache()
        self.assertEqual(before, self.rows())
        self.assertEqual(artifacts, [p.read_bytes() if p.exists() else None for p in paths])

    def test_cold_hit_exact_json_rows_and_canonical_counter(self):
        expected_counts = self.compute()
        expected_rows = self.rows()
        self.assertGreater(expected_counts["canonical-target-ambiguous-deferred"], 0)
        # Existing complete rows must not trigger the original count-only {} path.
        counts, cold = self.run_cache()
        self.assertEqual(expected_counts, counts)
        self.assertEqual(expected_rows, self.rows())
        self.assertEqual("miss", cold["status"])
        self.db.execute("DELETE FROM context_analysis")
        self.db.commit()
        counts, hit = self.run_cache()
        self.assertEqual(expected_counts, counts)
        self.assertEqual(expected_rows, self.rows())
        self.assertEqual("hit", hit["status"])
        self.assertEqual(2, self.calls)
        # Existing complete analysis on a hit also restores the saved Counter.
        self.assertEqual(expected_counts, self.run_cache()[0])
        self.assertEqual(2, self.calls)

    def test_deterministic_japanese_rejections_and_exact_choice_order(self):
        class Guard:
            evidence = {"engine": "fixture", "version": "1", "dictionarySha256": "c" * 64}
        self.db.execute("UPDATE context SET lang='ja'")
        self.db.commit()
        self.options.update(lang="ja", japanese_guard=Guard(), runtime_identity={"icuLibraries": {
            "main": "d" * 64, "uc": "e" * 64, "data": "f" * 64}})
        counts = {"ja-morpheme-cut-deferred": 3, "ja-oov-boundary-deferred": 2,
                  "jaContextsChecked": 2, "canonical-target-ambiguous-deferred": 4}
        def compute():
            self.calls += 1
            for key, _ in cache._contexts(self.db, "everyday", "ja"):
                self.db.execute("INSERT INTO context_analysis VALUES(?,?,?,?,?)",
                    ("everyday", "ja", key, 3, '[[9,"川","t:1","beginner"], [4,"谷","t:2","beginner"]]'))
            self.db.commit()
            return dict(counts)
        with patch.object(cache, "prepare", return_value={"icuVersion": "fixture"}):
            self.assertEqual(counts, self.run_cache(compute)[0])
            expected = self.rows()
            self.db.execute("DELETE FROM context_analysis")
            self.db.commit()
            actual, diagnostic = self.run_cache(compute)
        self.assertEqual(counts, actual)
        self.assertEqual(expected, self.rows())
        self.assertEqual("hit", diagnostic["status"])
        self.assertEqual(1, self.calls)
        with patch.object(cache, "prepare", return_value={"icuVersion": "fixture"}):
            Guard.evidence = dict(Guard.evidence, version="2")
            changed_counts, changed = self.run_cache(compute)
            self.assertEqual(counts, changed_counts)
            self.assertEqual("miss", changed["status"])
            self.options["runtime_identity"]["icuLibraries"]["data"] = "a" * 64
            self.assertEqual("miss", self.run_cache(compute)[1]["status"])
        self.assertEqual(3, self.calls)

    def test_dependency_changes_miss(self):
        _, first = self.run_cache()
        seen = {first["key"]}
        mutations = [lambda: self.options.update(function_top=1),
                     lambda: self.options.update(canonical_unique=False),
                     lambda: self.options.update(registry_sha256="b" * 64),
                     lambda: self.options.update(ranks={k: v + 1 for k, v in self.ranks.items()}),
                     lambda: self.options.update(parent_identity={"poolSha256": "c" * 64}),
                     lambda: self.options.update(runtime_identity={"runtime": "changed"})]
        for mutate in mutations:
            mutate()
            _, diagnostic = self.run_cache()
            self.assertEqual("miss", diagnostic["status"])
            self.assertNotIn(diagnostic["key"], seen)
            seen.add(diagnostic["key"])
        # The normalized context key can stay fixed while exact text changes.
        self.db.execute("UPDATE context SET context=context || ' '")
        self.db.commit()
        _, diagnostic = self.run_cache()
        self.assertEqual("miss", diagnostic["status"])
        self.assertNotIn(diagnostic["key"], seen)

    def test_implementation_and_runtime_versions_invalidate(self):
        _, first = self.run_cache()
        with patch.object(cache, "CACHE_VERSION", 987):
            _, changed = self.run_cache()
        self.assertNotEqual(first["key"], changed["key"])
        with patch.object(cache.platform, "python_version", return_value="fixture-next"):
            self.assertEqual("miss", self.run_cache()[1]["status"])
        original = cache._file_sha
        with patch.object(cache, "_file_sha", side_effect=lambda path: "f" * 64 if str(path).endswith("selection_policy.py") else original(path)):
            self.assertEqual("miss", self.run_cache()[1]["status"])

    def test_corrupt_bytes_fail_without_mutation(self):
        _, diagnostic = self.run_cache()
        data, _ = self.paths(diagnostic)
        data.write_bytes(data.read_bytes()[:-5])
        self.assert_unchanged_failure(diagnostic)

    def test_invalid_deflate_is_normalized_without_mutation(self):
        _, diagnostic = self.run_cache()
        data, _ = self.paths(diagnostic)
        # Valid gzip header followed by reserved/invalid DEFLATE block type.
        data.write_bytes(bytes.fromhex("1f8b0800000000000003070000000000000000"))
        self.rewrite(diagnostic, change=lambda p: p.update(
            dataBytes=data.stat().st_size, dataSha256=cache._file_sha(data)))
        self.assert_unchanged_failure(diagnostic)

    def test_manifest_counter_corruption_fail_without_mutation(self):
        _, diagnostic = self.run_cache()
        _, manifest = self.paths(diagnostic)
        value = json.loads(manifest.read_bytes())
        value["payload"]["counts"]["canonical-target-ambiguous-deferred"] += 1
        manifest.write_text(json.dumps(value))  # leave original checksum
        self.assert_unchanged_failure(diagnostic)

    def test_count_mismatch_and_invalid_counter(self):
        for edit in (lambda p: p.update(rows=999), lambda p: p.update(counts={"bad": -1})):
            with self.subTest(edit=edit):
                self.options["cache_dir"] = self.root / str(id(edit))
                _, diagnostic = self.run_cache()
                self.rewrite(diagnostic, change=edit)
                self.assert_unchanged_failure(diagnostic)

    def test_foreign_missing_extra_and_invalid_schema_rows(self):
        variants = [lambda r: r[0].__setitem__(0, "world"),
                    lambda r: r[0].__setitem__(2, "foreign-context"),
                    lambda r: r.pop(), lambda r: r.append(r[0]),
                    lambda r: r[0].__setitem__(3, "bad"),
                    lambda r: r[0].__setitem__(4, '{}')]
        for n, change in enumerate(variants):
            with self.subTest(case=n):
                self.options["cache_dir"] = self.root / f"bad-{n}"
                _, diagnostic = self.run_cache()
                rows = [list(row) for row in self.rows()]
                change(rows)
                self.rewrite(diagnostic, rows=rows)
                self.assert_unchanged_failure(diagnostic)

    def test_incomplete_entries_never_recomputed_or_overwritten(self):
        for missing in (0, 1):
            self.options["cache_dir"] = self.root / f"incomplete-{missing}"
            _, diagnostic = self.run_cache()
            self.paths(diagnostic)[missing].unlink()
            calls = self.calls
            self.assert_unchanged_failure(diagnostic)
            self.assertEqual(calls, self.calls)

    def test_interrupted_publication_has_no_completion_marker(self):
        link = cache.os.link
        def interrupted(source, destination):
            if str(destination).endswith(".json"):
                raise OSError("simulated interruption")
            return link(source, destination)
        with patch.object(cache.os, "link", side_effect=interrupted):
            with self.assertRaisesRegex(cache.CacheIntegrityError, "interruption"):
                self.run_cache()
        self.assertEqual(1, len(list((self.root / "cache").glob("*.jsonl.gz"))))
        self.assertFalse(list((self.root / "cache").glob("*.json")))
        with self.assertRaisesRegex(cache.CacheIntegrityError, "incomplete"):
            self.run_cache()
        self.assertEqual(1, self.calls)

    def test_restore_sql_failure_rolls_back_partial_replacement(self):
        _, diagnostic = self.run_cache()
        self.db.execute("UPDATE context_analysis SET token_count=999")
        self.db.commit()
        second_key = self.rows()[1][2]
        self.db.execute(f"CREATE TRIGGER fail_restore BEFORE INSERT ON context_analysis WHEN NEW.context_key='{second_key}' BEGIN SELECT RAISE(ABORT,'fixture restore failure'); END")
        with patch.object(cache, "BATCH_SIZE", 1):
            self.assert_unchanged_failure(diagnostic)
        self.assertEqual("off", self.db.execute("PRAGMA journal_mode").fetchone()[0])
        self.assertTrue(all(row[3] == 999 for row in self.rows()))

    def test_staged_context_source_rows_unchanged_and_artifacts_immutable(self):
        source = self.db.execute("SELECT * FROM context ORDER BY context_key").fetchall()
        _, diagnostic = self.run_cache()
        saved = [p.read_bytes() for p in self.paths(diagnostic)]
        self.run_cache()
        self.assertEqual(source, self.db.execute("SELECT * FROM context ORDER BY context_key").fetchall())
        self.assertEqual(saved, [p.read_bytes() for p in self.paths(diagnostic)])
        self.assertFalse(list((self.root / "cache").glob(".analysis-writing-*")))

    def test_cache_cannot_share_source_or_staging_directory(self):
        for directory in (cache.SOURCE_ROOT / "cache-fixture", self.root):
            self.options["cache_dir"] = directory
            with self.assertRaises(ValueError):
                self.run_cache()
        self.assertEqual(0, self.calls)

    def test_missing_runtime_or_japanese_evidence_rejected(self):
        self.options["lang"] = "ja"
        with self.assertRaisesRegex(ValueError, "icuLibraries"):
            self.run_cache()
        self.options.update(runtime_identity={"icuLibraries": {k: "a" * 64 for k in ("main", "uc", "data")}}, japanese_guard=object())
        with self.assertRaisesRegex(ValueError, "guard evidence"):
            self.run_cache()

    def test_uncommitted_off_journal_staging_rejected_without_mutation(self):
        _, diagnostic = self.run_cache()
        self.db.execute("UPDATE context_analysis SET token_count=999")
        self.assertTrue(self.db.in_transaction)
        self.assert_unchanged_failure(diagnostic)
        self.assertTrue(self.db.in_transaction)

    def test_interrupted_compute_never_publishes(self):
        def interrupted():
            self.db.execute("INSERT INTO context_analysis VALUES('everyday','en','partial',1,'[]')")
            raise RuntimeError("interrupted analysis")
        with self.assertRaisesRegex(RuntimeError, "interrupted analysis"):
            self.run_cache(interrupted)
        self.assertFalse(list((self.root / "cache").glob("*.json*")))
        self.db.commit()
        self.assertEqual("miss", self.run_cache()[1]["status"])
        self.assertFalse(any(row[2] == "partial" for row in self.rows()))

    def test_empty_context_scope_preserves_empty_counts(self):
        self.db.execute("DELETE FROM context")
        self.db.commit()
        self.assertEqual({}, self.run_cache()[0])
        counts, diagnostic = self.run_cache()
        self.assertEqual({}, counts)
        self.assertEqual(0, diagnostic["rows"])
        self.assertEqual("hit", diagnostic["status"])


if __name__ == "__main__":
    unittest.main()
