#!/usr/bin/env python3
"""Technical sentinels only: raw-source analysis must not cross token boundaries."""
import itertools
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest

from catalogue_v2 import canonical_target, target_id
from ingest.model import Candidate, Origin, write_jsonl
from segmentation import word_spans, utf16_offset, utf16_slice
import selection_experiment as selection
from build_catalogue_v2 import phrase_choices
from selection_policy import filter_unique_choices
import selection_analysis_cache as cache
import allocation_diagnostic as diagnostic


class SourceIdentityTests(unittest.TestCase):
    def fixture(self, root, order=(0, 1)):
        texts = ["We visit İstanbul every summer.", "We visit i\u0307stanbul every summer."]
        rows = [Candidate("everyday", "en", meaning_lang, text, "Technical sentinel " + str(i),
                         [Origin("tatoeba", "technical-fixture", "tatoeba:" + str(i + 1),
                                 "tatoeba:" + str(i + 11))])
                for i, (text, meaning_lang) in enumerate(zip(texts, ("pl", "de")))]
        path = root / "sentinels.jsonl"
        write_jsonl(str(path), [rows[i] for i in order])
        database = root / "stage.sqlite"
        selection.stage([str(path)], str(database), batch_size=1)
        return sqlite3.connect(database), rows

    def test_raw_variant_cannot_inherit_a_nonexistent_token(self):
        for order in itertools.permutations(range(2)):
            with self.subTest(order=order), tempfile.TemporaryDirectory() as td:
                db, rows = self.fixture(Path(td), order)
                try:
                    ranks = {w.lower(): 100 + i for i, w in enumerate(selection.core.words(rows[0].context, "en"))}
                    selection.build_context_analysis(db, "everyday", "en", ranks, 0, canonical_unique=True)
                    for meaning_lang in ("pl", "de"):
                        targets, _ = selection.pair_evidence(db, "everyday", "en", meaning_lang, None, 60, diverse=True)
                        for target in targets:
                            for context in target["contexts"]:
                                spans = word_spans(context["context"], "en")
                                matches = [s for s in spans if canonical_target(s.surface) == canonical_target(target["text"])]
                                self.assertEqual(1, len(matches), (order, target["text"], context["context"]))
                                self.assertEqual(len(spans), context["tokenCount"])
                                span = matches[0]
                                self.assertEqual(span.surface, utf16_slice(context["context"],
                                    utf16_offset(context["context"], span.start), utf16_offset(context["context"], span.end)))
                        if meaning_lang == "de":
                            self.assertNotIn(target_id("en", "İstanbul"), {t["targetId"] for t in targets})
                        else:
                            self.assertIn(target_id("en", "İstanbul"), {t["targetId"] for t in targets})
                finally:
                    db.close()

    def test_exact_analysis_is_shared_across_meanings_and_frequency_identity_stays_historical(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            db, rows = self.fixture(root)
            try:
                self.assertEqual(1, db.execute("SELECT COUNT(*) FROM context").fetchone()[0])
                self.assertEqual(2, db.execute("SELECT COUNT(*) FROM analysis_context").fetchone()[0])
                expected = {w.lower(): i for i, (w, _) in enumerate(
                    selection.Counter(w.lower() for w in selection.core.words(rows[0].context, "en")).most_common(), 1)}
                self.assertEqual(expected, selection.build_ranks(db, "everyday", "en"))
                extra = Candidate("everyday", "en", "fr", rows[0].context, "Another technical sentinel.",
                                  [Origin("tatoeba", "technical-fixture", "tatoeba:1", "tatoeba:30")])
                write_jsonl(str(root / "repeated.jsonl"), rows + [extra])
            finally:
                db.close()
            selection.stage([str(root / "repeated.jsonl")], str(root / "repeat.sqlite"))
            with sqlite3.connect(root / "repeat.sqlite") as repeated:
                self.assertEqual(3, repeated.execute("SELECT COUNT(*) FROM candidate").fetchone()[0])
                self.assertEqual(2, repeated.execute("SELECT COUNT(*) FROM analysis_context").fetchone()[0])
                self.assertEqual(expected, selection.build_ranks(repeated, "everyday", "en"))

    def test_ordinary_case_and_whitespace_preserve_representative_choices(self):
        texts = ["We study Straße every summer.", "WE  STUDY STRASSE EVERY SUMMER."]
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            rows = [Candidate("everyday", "de", meaning, text, "Technical sentinel " + str(i),
                    [Origin("tatoeba", "technical-fixture", f"tatoeba:{i+1}", f"tatoeba:{i+11}")])
                    for i, (text, meaning) in enumerate(zip(texts, ("en", "pl")))]
            write_jsonl(str(root / "input.jsonl"), rows)
            selection.stage([str(root / "input.jsonl")], str(root / "stage.sqlite"))
            with sqlite3.connect(root / "stage.sqlite") as db:
                ranks = {w.lower(): 100 for text in texts for w in selection.core.words(text, "de")}
                expected = filter_unique_choices(texts[0], phrase_choices(texts[0], ranks, "de", 0), "de")[0]
                selection.build_context_analysis(db, "everyday", "de", ranks, 0, canonical_unique=True)
                choices = [json.loads(row[0]) for row in db.execute("SELECT choices_json FROM context_analysis")]
                self.assertEqual(2, len(choices))
                self.assertTrue(all(choice == [list(c) for c in expected] for choice in choices))

    def test_variant_cache_cold_warm_and_scoped_diagnostic_equal_direct_analysis(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            db, rows = self.fixture(root)
            try:
                ranks = {w.lower(): 100 for w in selection.core.words(rows[0].context, "en")}
                def compute():
                    return selection.build_context_analysis(db, "everyday", "en", ranks, 0, canonical_unique=True)
                compute()
                expected = {meaning: selection.pair_evidence(db, "everyday", "en", meaning, None, 60, diverse=True)
                            for meaning in ("pl", "de")}
                arguments = dict(collection="everyday", lang="en", ranks=ranks, function_top=0,
                                 japanese_guard=None, canonical_unique=True, cache_dir=root / "cache",
                                 registry_sha256="a" * 64)
                counts, cold = cache.run_cached_analysis(db, compute=compute, **arguments)
                saved = db.execute("SELECT * FROM context_analysis ORDER BY analysis_key").fetchall()
                db.execute("DELETE FROM context_analysis"); db.commit()
                warm_counts, warm = cache.run_cached_analysis(db, compute=lambda: self.fail("warm recomputed"), **arguments)
                self.assertEqual(("miss", "hit"), (cold["status"], warm["status"]))
                self.assertEqual(counts, warm_counts)
                self.assertEqual(saved, db.execute("SELECT * FROM context_analysis ORDER BY analysis_key").fetchall())
                for meaning in expected:
                    self.assertEqual(expected[meaning], selection.pair_evidence(db, "everyday", "en", meaning, None, 60, diverse=True))
            finally:
                db.close()
            diagnostic.stage_scoped(root / "sentinels.jsonl", root / "global.sqlite", root / "subset.sqlite",
                                    [("en", "de")], ["en"])
            with sqlite3.connect(root / "subset.sqlite") as subset:
                selection.build_context_analysis(subset, "everyday", "en", ranks, 0, canonical_unique=True)
                self.assertEqual(expected["de"], selection.pair_evidence(subset, "everyday", "en", "de", None, 60, diverse=True))

    def test_japanese_guard_checks_actual_case_variant(self):
        texts = ["今日は ALPHA を読みます。", "今日は alpha を読みます。"]
        checked = []
        class Guard:
            def filter(self, text, choices):
                checked.append(text)
                kept = [c for c in choices if not (text == texts[1] and c[1] == "alpha")]
                return kept, {"technical-guard-deferral": len(choices) - len(kept)}, []
        with sqlite3.connect(":memory:") as db:
            db.executescript(selection.SCHEMA)
            key = selection._context_key("everyday", "ja", texts[0])
            db.execute("INSERT INTO context VALUES(?,?,?,?)", ("everyday", "ja", key, texts[0]))
            for text in texts:
                db.execute("INSERT INTO analysis_context VALUES(?,?,?,?,?)", ("everyday", "ja",
                    selection._analysis_key("everyday", "ja", text), key, text))
            ranks = {w.lower(): 100 for text in texts for w in selection.core.words(text, "ja")}
            counts = selection.build_context_analysis(db, "everyday", "ja", ranks, 0,
                canonical_unique=True, japanese_guard=Guard())
            self.assertCountEqual(texts, checked)
            self.assertEqual(2, counts["jaContextsChecked"])
            self.assertEqual(1, counts["technical-guard-deferral"])
            actual = json.loads(db.execute("SELECT choices_json FROM context_analysis WHERE analysis_key=?",
                (selection._analysis_key("everyday", "ja", texts[1]),)).fetchone()[0])
            self.assertNotIn(target_id("ja", "alpha"), {c[2] for c in actual})

    def test_same_canonical_identity_keeps_actual_unchanged_rank_and_level(self):
        texts = ["We study Straße every summer.", "WE STUDY STRASSE EVERY SUMMER."]
        ranks = {"we": 100, "study": 101, "every": 102, "summer": 103,
                 "straße": 1500, "strasse": 1501}
        for order in itertools.permutations(range(2)):
            with self.subTest(order=order), tempfile.TemporaryDirectory() as td:
                root = Path(td)
                rows = [Candidate("everyday", "de", meaning, text, "Technical sentinel " + str(i),
                        [Origin("tatoeba", "technical-fixture", f"tatoeba:{i+1}", f"tatoeba:{i+11}")])
                        for i, (text, meaning) in enumerate(zip(texts, ("en", "pl")))]
                write_jsonl(str(root / "input.jsonl"), [rows[i] for i in order])
                selection.stage([str(root / "input.jsonl")], str(root / "stage.sqlite"))
                with sqlite3.connect(root / "stage.sqlite") as db:
                    before = selection.build_ranks(db, "everyday", "de")
                    selection.build_context_analysis(db, "everyday", "de", ranks, 0, canonical_unique=True)
                    self.assertEqual(before, selection.build_ranks(db, "everyday", "de"))
                    for meaning, rank, level in (("en", 1500, "beginner"), ("pl", 1501, "middle")):
                        targets, _ = selection.pair_evidence(db, "everyday", "de", meaning, None, 60, diverse=True)
                        actual = next(t for t in targets if t["targetId"] == target_id("de", "Straße"))
                        self.assertEqual((rank, level), (actual["freqRank"], actual["level"]))

    def test_missing_frequency_representative_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            db, _ = self.fixture(Path(td))
            try:
                db.execute("DELETE FROM context")
                with self.assertRaisesRegex(ValueError, "missing a frequency-context representative"):
                    selection.build_context_analysis(db, "everyday", "en", {}, 0, canonical_unique=True)
            finally:
                db.close()


if __name__ == "__main__":
    unittest.main()
