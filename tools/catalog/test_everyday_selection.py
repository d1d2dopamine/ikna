#!/usr/bin/env python3
"""Admitted-pool handoff, data preservation and deterministic review regressions."""
from __future__ import annotations

import dataclasses
import gzip
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import everyday_pool as pool
import everyday_selection as admitted
import selection_experiment as selection
from ingest.adapters import iter_tatoeba
from ingest.model import Candidate, Origin, write_jsonl
from ingest.registry import SourceRegistry

HERE = Path(__file__).resolve().parent
VERSION = "2026-09-12"


class EverydaySelectionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="ikna-everyday-selection-")
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.input = self.root / "input.jsonl"
        policy = SourceRegistry.load(str(pool.DEFAULT_REGISTRY)).get("tatoeba")
        self.records = list(iter_tatoeba(str(HERE / "fixtures/ingest/tatoeba"), policy, "en", "es", source_version=VERSION))
        write_jsonl(str(self.input), self.records)
        self.pool_path = self.root / "pool.jsonl.gz"
        self.pool_report = self.root / "POOL.json"
        self.pool_args = pool.parser().parse_args([
            "--candidates", str(self.input), "--source-version", VERSION,
            "--pool", str(self.pool_path), "--json", str(self.pool_report),
            "--markdown", str(self.root / "POOL.md"), "--samples", str(self.root / "POOL-SAMPLES.md"),
            "--staging", str(self.root / "pool.db"), "--learn", "en", "--meanings", "es,fr",
            "--function-top", "0"])
        self.report = pool.build_report(self.pool_args)
        self.save_report()

    def save_report(self):
        self.pool_report.write_text(json.dumps(self.report), encoding="utf-8")

    def args(self, prefix="out"):
        return admitted.parser().parse_args([
            "--pool", str(self.pool_path), "--pool-report", str(self.pool_report),
            "--staging", str(self.root / (prefix + ".db")), "--json", str(self.root / (prefix + ".json")),
            "--markdown", str(self.root / (prefix + ".md")), "--preview", str(self.root / (prefix + ".jsonl.gz")),
            "--samples", str(self.root / (prefix + "-samples.md")), "--max-deck", "20", "--min-deck", "1",
            "--thin-deck", "2", "--review-per-deck", "2"])

    def replace_pool(self, records):
        write_jsonl(str(self.pool_path), records)
        logical = gzip.decompress(self.pool_path.read_bytes())
        import hashlib
        self.report["pool"].update(sha256=pool.sha256(self.pool_path), sizeBytes=self.pool_path.stat().st_size,
            logicalSha256=hashlib.sha256(logical).hexdigest(), logicalSizeBytes=len(logical), uniqueCandidates=len(records))
        self.save_report()

    def test_exact_handoff_and_zero_supply_inventory(self):
        report, preview = admitted.build_report(self.args())
        self.assertFalse(report["publicationSafe"])
        self.assertEqual(self.report["pool"]["sha256"], report["admittedInput"]["poolSha256"])
        self.assertEqual(pool.sha256(self.pool_report), report["admittedInput"]["poolReportSha256"])
        self.assertEqual(0, report["policy"]["functionTop"])
        self.assertEqual(2, report["plannedDirectedPairs"])
        self.assertEqual(["en->fr"], report["missingDirectPairs"])
        self.assertEqual(6, len(report["decks"]))
        absent = [row for row in report["decks"] if row["meaningLang"] == "fr"]
        self.assertTrue(all(row["selectedTargets"] == 0 and row["diagnosis"] == "no-direct-rows" for row in absent))
        self.assertEqual(sum(row["selectedTargets"] for row in report["decks"]), report["summary"]["selectedTargets"])
        self.assertEqual(sum(row["decision"] == "omit-no-quality-supply" for row in report["decks"]),
                         report["summary"]["omit-no-quality-supply"])
        self.assertTrue(preview)
        self.assertFalse(report["reviewScope"]["materialReviewCompleted"])
        self.assertFalse(report["reviewScope"]["contentFrozen"])
        self.assertIn("contextContributor", admitted.samples_markdown(report, preview))

    def test_reproducible_reports_and_gzip_across_names(self):
        first, preview1 = admitted.build_report(self.args("one"))
        second, preview2 = admitted.build_report(self.args("two"))
        self.assertEqual(first, second)
        self.assertEqual(preview1, preview2)
        selection.write_preview(str(self.root / "first.gz"), preview1)
        selection.write_preview(str(self.root / "second.gz"), preview2)
        self.assertEqual((self.root / "first.gz").read_bytes(), (self.root / "second.gz").read_bytes())
        self.assertEqual(b"\0\0\0\0", (self.root / "first.gz").read_bytes()[4:8])

    def test_complete_material_stream_is_not_limited_by_preview(self):
        extra = [Candidate("everyday", "en", "es", text, "Traducción de prueba; no material publicado.",
                          [Origin("tatoeba", VERSION, f"tatoeba:{9100+i}", f"tatoeba:{9200+i}")]) for i, text in enumerate([
                              "I don't know if I have time to do it.", "The cat is asleep on the rug by the fire."])]
        write_jsonl(str(self.input), self.records + extra)
        self.report = pool.build_report(self.pool_args); self.save_report()
        args = self.args("complete")
        args.review_per_deck = 1
        args.quality_policy = "boundary-diversity-v2"
        args.selected_output = str(self.root / "selected.jsonl.gz")
        report, preview = admitted.build_report(args)
        rows = [json.loads(line) for line in gzip.decompress(Path(args.selected_output).read_bytes()).splitlines()]
        self.assertEqual(len(rows), report["selectedMaterial"]["memberships"])
        self.assertGreater(len(rows), len(preview))
        self.assertEqual(len(rows), sum(d["selectedTargets"] for d in report["decks"] if d["decision"].startswith("publish")))
        self.assertEqual(pool.sha256(Path(args.selected_output)), report["selectedMaterial"]["sha256"])
        self.assertFalse(report["selectedMaterial"]["publicationSafe"])
        self.assertEqual(report["qualityPolicy"]["id"], "boundary-diversity-v2")

    def test_failed_complete_selection_preserves_existing_handoff(self):
        args = self.args("failed-complete")
        output = self.root / "previous.jsonl.gz"; output.write_bytes(b'previous-successful-material')
        args.selected_output = str(output)
        with patch.object(selection, "build_report", side_effect=ValueError("selection interrupted")):
            with self.assertRaises(ValueError): admitted.build_report(args)
        self.assertEqual(output.read_bytes(), b'previous-successful-material')

    def test_cjk_handoff_retains_exact_contexts_and_target_identity(self):
        from catalogue_v2 import target_id
        contexts = {"zh": "我喜欢每天在安静的图书馆阅读新书。",
                    "ja": "明日は図書館で新しい本を読みます。",
                    "ko": "우리는 매일 조용한 도서관에서 새로운 책을 읽습니다."}
        for lang, text in contexts.items():
            with self.subTest(lang=lang):
                candidate = Candidate("everyday", lang, "en", text,
                    "We read new books in a quiet library every day.",
                    [Origin("tatoeba", VERSION, "tatoeba:9001", "tatoeba:9002")])
                write_jsonl(str(self.input), [candidate])
                self.pool_args.learn = lang; self.pool_args.meanings = "en"
                self.report = pool.build_report(self.pool_args); self.save_report()
                report, preview = admitted.build_report(self.args(lang))
                self.assertTrue(preview)
                self.assertEqual(1, report["plannedDirectedPairs"])
                self.assertEqual("ICU word break", report["selectionEnvironment"]["segmentation"]["engine"])
                for row in preview:
                    self.assertEqual(target_id(lang, row["text"]), row["targetId"])
                    self.assertTrue(all(context["context"] == text for context in row["contexts"]))

    def test_mixed_experiment_and_false_safety_flags_rejected(self):
        for key, bad in (("part", 6), ("reportVersion", 99), ("status", "preview"),
                         ("sourceAdmissionPassed", False), ("completeInputScan", False),
                         ("publicationSafe", True), ("corpusCoverage", "full-export")):
            with self.subTest(key=key):
                original = self.report[key]; self.report[key] = bad; self.save_report()
                with self.assertRaisesRegex(ValueError, "Part 7"):
                    admitted.build_report(self.args())
                self.report[key] = original
        self.report["sourceDecision"]["massive"] = "admitted"; self.save_report()
        with self.assertRaisesRegex(ValueError, "Part 7"):
            admitted.build_report(self.args())

    def test_hash_registry_and_logical_identity_fail_closed(self):
        for target, key, value in ((self.report["pool"], "sha256", "0" * 64),
                                   (self.report["pool"], "sizeBytes", 0),
                                   (self.report["pool"], "logicalSha256", "0" * 64),
                                   (self.report["pool"], "logicalSizeBytes", 0),
                                   (self.report, "registrySha256", "0" * 64),
                                   (self.report["pool"], "uniqueCandidates", 999)):
            with self.subTest(key=key):
                original = target[key]; target[key] = value; self.save_report()
                with self.assertRaises(ValueError): admitted.build_report(self.args())
                target[key] = original

    def test_every_origin_checked_even_with_self_consistent_hashes(self):
        for origin in (Origin("massive", VERSION, "massive:en:1", "massive:es:1"),
                       Origin("tatoeba", "weekly", "tatoeba:1", "tatoeba:2"),
                       Origin("tatoeba", VERSION, "invented:1", "tatoeba:2")):
            with self.subTest(origin=origin):
                bad = dataclasses.replace(self.records[0], origins=[self.records[0].origins[0], origin])
                self.replace_pool([bad] + self.records[1:])
                with self.assertRaises(ValueError): admitted.build_report(self.args())

    def test_foreign_scope_and_duplicate_pool_rejected(self):
        reverse = dataclasses.replace(self.records[0], lang="es", meaning_lang="en", id=None)
        self.replace_pool([reverse])
        with self.assertRaisesRegex(ValueError, "outside"):
            admitted.build_report(self.args())
        self.replace_pool(self.records + [self.records[0]])
        with self.assertRaisesRegex(ValueError, "unique complete"):
            admitted.build_report(self.args())

    def test_corrupt_utf8_is_not_repaired(self):
        bad = self.root / "bad.jsonl"
        bad.write_bytes(b'{"metadata":"\xff"}\n')
        for filename in (bad, self.root / "bad.jsonl.gz"):
            if filename.suffix == ".gz": filename.write_bytes(gzip.compress(bad.read_bytes()))
            with self.subTest(filename=filename):
                with self.assertRaises(UnicodeDecodeError):
                    with selection._open(str(filename)) as handle: list(handle)

    def test_admitted_output_aliases_preserve_all_input_bytes(self):
        protected = [self.pool_path, self.pool_report, pool.DEFAULT_REGISTRY]
        before = [path.read_bytes() for path in protected]
        for output in ("staging", "json", "markdown", "preview", "samples"):
            for source in protected:
                args = self.args(); setattr(args, output, str(source))
                with self.assertRaisesRegex(ValueError, "must not overwrite"):
                    admitted.build_report(args)
        self.assertEqual(before, [path.read_bytes() for path in protected])

    def test_symlink_and_hardlink_output_aliases_rejected(self):
        for link_type in ("symlink", "hardlink"):
            link = self.root / link_type
            if link_type == "symlink": link.symlink_to(self.pool_path)
            else: os.link(self.pool_path, link)
            args = self.args(); args.staging = str(link)
            with self.assertRaisesRegex(ValueError, "overwrite|aliases"):
                admitted.build_report(args)

    def test_generic_stage_and_outputs_cannot_delete_source_or_morphology(self):
        before = self.input.read_bytes()
        with self.assertRaisesRegex(ValueError, "staging"):
            selection.stage([str(self.input)], str(self.input))
        args = selection.parser().parse_args([
            "--candidates", str(self.input), "--staging", str(self.root / "generic.db"),
            "--json", str(self.root / "generic.json"), "--markdown", str(self.root / "generic.md"),
            "--preview", str(self.root / "generic.gz"), "--learn", "en", "--meanings", "es"])
        for output in ("staging", "json", "markdown", "preview"):
            changed = argparse_copy(args); setattr(changed, output, str(self.input))
            with self.assertRaisesRegex(ValueError, "overwrite"):
                selection.build_report(changed)
        args.morphology_db = str(self.input); args.json = str(self.input)
        with self.assertRaisesRegex(ValueError, "overwrite"):
            selection.build_report(args)
        self.assertEqual(before, self.input.read_bytes())

    def test_failed_validation_or_selection_keeps_previous_outputs(self):
        args = self.args()
        for key in ("staging", "json", "markdown", "preview", "samples"):
            Path(getattr(args, key)).write_bytes(b"previous-result")
        self.report["pool"]["sha256"] = "0" * 64; self.save_report()
        with self.assertRaises(ValueError): admitted.build_report(args)
        self.report["pool"]["sha256"] = pool.sha256(self.pool_path); self.save_report()
        with patch.object(selection, "build_report", side_effect=RuntimeError("failed selection")):
            with self.assertRaises(RuntimeError): admitted.build_report(args)
        for key in ("staging", "json", "markdown", "preview", "samples"):
            self.assertEqual(b"previous-result", Path(getattr(args, key)).read_bytes())

    def test_change_during_selection_rejects_stale_evidence(self):
        original = selection.build_report
        def changed(args):
            result = original(args)
            self.pool_report.write_text(self.pool_report.read_text() + "\n")
            return result
        with patch.object(selection, "build_report", side_effect=changed):
            with self.assertRaisesRegex(ValueError, "changed during selection"):
                admitted.build_report(self.args())
        self.assertFalse(Path(self.args().staging).exists())

    def test_numeric_and_scope_validation_before_staging(self):
        for changes in ({"max_deck": 0}, {"min_deck": 21}, {"review_per_deck": 0}, {"review_per_deck": 101}):
            args = self.args()
            for key, value in changes.items(): setattr(args, key, value)
            with self.assertRaises(ValueError): admitted.build_report(args)
            self.assertFalse(Path(args.staging).exists())
        self.report["learn"] = ["unsupported"]; self.save_report()
        with self.assertRaises(ValueError): admitted.build_report(self.args())

    def test_cli_writes_bounded_review_without_assets(self):
        args = self.args("cli")
        import subprocess, sys
        command = [sys.executable, str(HERE / "everyday_selection.py")]
        for key in ("pool", "pool_report", "staging", "json", "markdown", "preview", "samples", "max_deck", "min_deck", "thin_deck", "review_per_deck"):
            command += ["--" + key.replace("_", "-"), str(getattr(args, key))]
        subprocess.run(command, check=True, capture_output=True, text=True)
        report = json.loads(Path(args.json).read_text())
        with gzip.open(args.preview, "rt", encoding="utf-8") as handle: rows = [json.loads(line) for line in handle]
        self.assertLessEqual(len(rows), len(report["decks"]) * 2)
        self.assertEqual(report["previewRows"], len(rows))
        self.assertFalse(report["publicationSafe"])
        self.assertIn("not full selected assets", Path(args.samples).read_text())
        self.assertFalse(list(self.root.glob("index*.json")))


def argparse_copy(args):
    import argparse
    return argparse.Namespace(**vars(args))


if __name__ == "__main__": unittest.main()
