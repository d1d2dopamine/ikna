#!/usr/bin/env python3
"""Quality policy, complete handoff and evaluation regressions without corpus I/O."""
import argparse
from copy import deepcopy
import gzip
import hashlib
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
from unittest.mock import patch

import catalogue_core as core
from catalogue_v2 import target_id
from build_catalogue_v2 import phrase_choices
import everyday_selection as admitted
from japanese_boundary import filter_boundaries, JapaneseBoundaryGuard
from segmentation import word_spans
from selection_policy import choose_contexts, quarantine_reason, quarantine_rules
from selection_output import SelectionOutput
import selection_evaluate as evaluation
from test_selection_audit import fixture
from ingest.model import Candidate, Origin, write_jsonl

REQUIRE_ENGINE = False


class QualityTests(unittest.TestCase):
    def test_boundary_cuts_deferred_but_short_words_and_compounds_kept(self):
        text = "私は日本語を学びます。"
        choices = phrase_choices(text, {w.lower(): 100 for w in core.words(text, "ja")}, "ja", 0)
        # Compound 日本語 may span complete morphemes; its identity stays exact.
        units = [(0, 1, False), (1, 2, False), (2, 4, False), (4, 5, False),
                 (5, 6, False), (6, 8, False), (8, 10, False), (10, 11, False)]
        kept, rejected, _ = filter_boundaries(text, choices, units)
        self.assertIn("日本語", [c[1] for c in kept]); self.assertFalse(rejected)
        sentence = "私は夜遅くまで起きていた。"
        selected = [c for c in phrase_choices(sentence, {w.lower(): 100 for w in core.words(sentence, "ja")}, "ja", 0) if c[1] == "き"]
        kept, rejected, notes = filter_boundaries(sentence, selected, [(0, 7, False), (7, 9, False), (9, len(sentence), False)])
        self.assertFalse(kept); self.assertEqual(rejected["ja-morpheme-cut-deferred"], 1)
        self.assertEqual(notes[0]["text"], "き")

    def test_invalid_and_uncovered_analyzer_ranges_fail(self):
        text = "私は日本語を学びます。"
        choices = phrase_choices(text, {w.lower(): 100 for w in core.words(text, "ja")}, "ja", 0)
        for units in ([(0, 999, False)], [(0, 8, False), (7, 9, False)], []):
            with self.assertRaises(ValueError): filter_boundaries(text, choices, units)

    def test_diversity_reorders_without_erasing_grammar_variation(self):
        def c(ref, text, n):
            return {"candidateId": ref, "contextId": ref, "context": text, "tokenCount": n,
                    "sourceFamilies": ["tatoeba"], "alignmentScore": None}
        rows = [c("a", "Если бы не вы, меня бы сегодня здесь не было.", 10),
                c("b", "Если бы не ты, меня бы сегодня здесь не было.", 10),
                c("c", "Я сегодня купил новую книгу в магазине.", 7)]
        legacy, _ = choose_contexts(rows, 2, .85)
        diverse, _ = choose_contexts(rows, 2, .85, diverse=True, lang="ru")
        self.assertEqual(legacy[0], diverse[0])
        self.assertEqual(diverse[1]["contextId"], "c")
        self.assertNotEqual(legacy[1]["contextId"], diverse[1]["contextId"])
        variants = [c("d", "If I were a bird, I could fly to you.", 11),
                    c("e", "If I were a bird, I would fly to you.", 11)]
        chosen, _ = choose_contexts(variants, 3, .85, diverse=True, lang="en")
        self.assertEqual(len(chosen), 2)

    def test_complete_handoff_deterministic_and_abort_preserves_prior_file(self):
        _, _, rows, _, _ = fixture()
        with tempfile.TemporaryDirectory() as td:
            root = Path(td); identities = []
            for name in ("one.jsonl.gz", "two.jsonl.gz"):
                out = SelectionOutput(root / name)
                for row in rows: out.add(row)
                identities.append(out.finish()); out.abort()
            self.assertEqual(identities[0], identities[1])
            self.assertEqual((root / "one.jsonl.gz").read_bytes(), (root / "two.jsonl.gz").read_bytes())
            before = (root / "one.jsonl.gz").read_bytes()
            out = SelectionOutput(root / "one.jsonl.gz"); out.add(rows[0]); out.abort()
            self.assertEqual((root / "one.jsonl.gz").read_bytes(), before)

    def current_fixture(self, root):
        _, baseline, rows, _, _ = fixture()
        current = deepcopy(baseline)
        current["qualityPolicy"] = {"id": "boundary-diversity-v2", "version": 2, "japaneseBoundary": None}
        current["selectionPipelineSha256"].update({n: evaluation.sha((evaluation.HERE / n).read_bytes()) for n in
            ("japanese_boundary.py", "selection_output.py", "requirements-selection-quality.txt", "sources/selection-quarantine.json")})
        path = root / "selected.jsonl.gz"; out = SelectionOutput(path)
        for row in rows: out.add(row)
        current["selectedMaterial"] = out.finish(); out.abort()
        return baseline, current, rows, path

    def test_full_material_evaluation_and_refusal_paths(self):
        with tempfile.TemporaryDirectory() as td:
            baseline, current, rows, path = self.current_fixture(Path(td))
            result = evaluation.evaluate(path, current, baseline, rows)
            self.assertTrue(result["qualityGatePassed"]); self.assertFalse(result["semanticAccuracyCertified"])
            self.assertFalse(result["contentFrozen"]); self.assertEqual(result["memberships"], 2)
            self.assertEqual(result["baselinePreviewComparison"]["retained"], 2)
            bad = deepcopy(current); bad["admittedInput"]["poolSha256"] = "f" * 64
            with self.assertRaisesRegex(ValueError, "different exact input"): evaluation.evaluate(path, bad, baseline, rows)
            bad = deepcopy(current); bad["selectedMaterial"]["logicalSha256"] = "f" * 64
            with self.assertRaisesRegex(ValueError, "logical count/hash"): evaluation.evaluate(path, bad, baseline, rows)
            bad = deepcopy(current); bad["selectedMaterial"]["sha256"] = "f" * 64
            with self.assertRaisesRegex(ValueError, "raw identity"): evaluation.evaluate(path, bad, baseline, rows)
            bad = deepcopy(current); bad["selectionPipelineSha256"]["../../outside"] = "f" * 64
            with self.assertRaisesRegex(ValueError, "unexpected current pipeline"): evaluation.evaluate(path, bad, baseline, rows)
            bad = deepcopy(current); del bad["selectionPipelineSha256"]["sources/selection-quarantine.json"]
            with self.assertRaisesRegex(ValueError, "unexpected current pipeline"): evaluation.evaluate(path, bad, baseline, rows)

    def test_selected_rows_must_match_contract_even_with_new_file_hash(self):
        with tempfile.TemporaryDirectory() as td:
            baseline, current, rows, path = self.current_fixture(Path(td))
            changed = deepcopy(rows); changed[0]["targetId"] = "t2:en:wrong"
            out = SelectionOutput(path)
            for row in changed: out.add(row)
            current["selectedMaterial"] = out.finish(); out.abort()
            with self.assertRaisesRegex(ValueError, "target identity"): evaluation.evaluate(path, current, baseline, rows)

    def test_lost_pair_stops_progress_without_publishing(self):
        with tempfile.TemporaryDirectory() as td:
            baseline, current, rows, path = self.current_fixture(Path(td))
            omitted = next(d for d in current["decks"] if d["deckId"] == rows[0]["deckId"])
            omitted.update(selectedTargets=0, eligibleTargets=0, decision="omit-no-quality-supply")
            current["summary"].update(publish=1, **{"omit-no-quality-supply": 5}, selectedTargets=1, eligibleTargets=1)
            out = SelectionOutput(path); out.add(rows[1]); current["selectedMaterial"] = out.finish(); out.abort()
            result = evaluation.evaluate(path, current, baseline, rows)
            self.assertFalse(result["qualityGatePassed"]); self.assertFalse(result["publicationSafe"])
            self.assertEqual(result["lostPreviouslyIncludedPairs"], ["en->ru"])

    def test_duplicate_full_membership_is_rejected_even_with_consistent_hashes(self):
        with tempfile.TemporaryDirectory() as td:
            baseline, current, rows, path = self.current_fixture(Path(td))
            duplicated = rows + [rows[0]]
            next(d for d in current["decks"] if d["deckId"] == rows[0]["deckId"]).update(selectedTargets=2, eligibleTargets=2)
            current["summary"].update(selectedTargets=3, eligibleTargets=3)
            out = SelectionOutput(path)
            for row in duplicated: out.add(row)
            current["selectedMaterial"] = out.finish(); out.abort()
            with self.assertRaises(sqlite3.IntegrityError): evaluation.evaluate(path, current, baseline, rows)

    def test_selected_output_alias_rejected_before_mutation(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td); protected = root / "pool.jsonl.gz"; protected.write_bytes(b'input')
            args = admitted.parser().parse_args(["--pool", str(protected), "--pool-report", str(root / "pool.json"),
                "--staging", str(root / "stage.db"), "--json", str(root / "r.json"), "--markdown", str(root / "r.md"),
                "--preview", str(root / "p.gz"), "--samples", str(root / "s.md"), "--selected-output", str(protected)])
            with self.assertRaisesRegex(ValueError, "overwrite"): admitted.validate_paths(args)
            self.assertEqual(protected.read_bytes(), b'input')

    def test_source_quarantine_exact_pins_both_roles_and_regional_text(self):
        rule = next(iter(quarantine_rules().values()))
        origin = {"sourceFamily": rule["sourceFamily"], "sourceVersion": rule["sourceVersion"],
                  "contextRef": rule["sentenceRef"], "meaningRef": "tatoeba:1"}
        self.assertTrue(quarantine_reason(rule["text"], "test meaning", [origin]))
        reverse = dict(origin, contextRef="tatoeba:1", meaningRef=rule["sentenceRef"])
        self.assertTrue(quarantine_reason("test context", rule["text"], [reverse]))
        self.assertFalse(quarantine_reason(rule["text"].replace("lluvio", "lluvia"), "test meaning", [origin]))
        self.assertFalse(quarantine_reason(rule["text"], "test meaning", [dict(origin, sourceVersion="future-export")]))
        self.assertFalse(quarantine_reason('Decime, te estoy escuchando.', 'test meaning', [origin]))

    def test_real_pinned_japanese_engine(self):
        try: guard = JapaneseBoundaryGuard()
        except ValueError:
            if REQUIRE_ENGINE: raise
            self.skipTest("pinned offline Japanese dependencies absent; workflow requires real engine")
        try:
            examples = {"私は夜遅くまで起きていた。": ("き", False),
                        "子供達は外に遊びに行った。": ("行", False),
                        "魚や肉を売っているんだよ。": ("や", True),
                        "私は日本語を学びます。": ("日本語", True),
                        "😀私は木を見ました。": ("木", True)}
            for text, (surface, accepted) in examples.items():
                choices = [c for c in phrase_choices(text, {w.lower(): 100 for w in core.words(text, "ja")}, "ja", 0) if c[1] == surface]
                kept, rejected, _ = guard.filter(text, choices)
                self.assertEqual(bool(kept), accepted); self.assertEqual(bool(rejected), not accepted)
                if kept: self.assertEqual(kept[0][2], target_id("ja", surface))
        finally: guard.close()
        # Actual analyzer also flows through admitted selection and full-output
        # evaluation; test provenance is not published corpus evidence.
        from test_everyday_selection import EverydaySelectionTests, VERSION
        import everyday_pool
        case = EverydaySelectionTests(); case.setUp()
        try:
            texts = [("私は夜遅くまで起きていた。", "Я не спал допоздна."),
                     ("魚や肉を売っているんだよ。", "Они продают рыбу и мясо."),
                     ("私は日本語を学びます。", "Я изучаю японский язык.")]
            records = [Candidate("everyday", "ja", "ru", a, b,
                [Origin("tatoeba", VERSION, f"tatoeba:{9400+i}", f"tatoeba:{9500+i}")]) for i, (a, b) in enumerate(texts)]
            write_jsonl(str(case.input), records)
            case.pool_args.learn = "ja"; case.pool_args.meanings = "ru"
            case.report = everyday_pool.build_report(case.pool_args); case.save_report()
            baseline, old_rows = admitted.build_report(case.args("ja-before"))
            args = case.args("ja-after"); args.quality_policy = "boundary-diversity-v2"
            args.selected_output = str(case.root / "ja-selected.jsonl.gz")
            current, _ = admitted.build_report(args)
            self.assertEqual(current["qualityPolicy"]["counts"]["jaContextsChecked"], 3)
            self.assertGreater(current["qualityPolicy"]["counts"]["ja-morpheme-cut-deferred"], 0)
            result = evaluation.evaluate(Path(args.selected_output), current, baseline, old_rows)
            self.assertTrue(result["qualityGatePassed"])
        finally:
            case.doCleanups(); case.tmp.cleanup()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(); parser.add_argument("--require-japanese-engine", action="store_true")
    args, remaining = parser.parse_known_args(); REQUIRE_ENGINE = args.require_japanese_engine
    unittest.main(argv=[__file__] + remaining)
