#!/usr/bin/env python3
"""Tiny synthetic tests only; never reads real control/staging or runs a corpus."""
from __future__ import annotations

import argparse
from contextlib import ExitStack
import hashlib
import importlib
import json
import os
from pathlib import Path
import pstats
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import dense_pair_probe as probe

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent / "ikna-work"
TOOLS = REPO / "tools" / "catalog"
sys.dont_write_bytecode = True
sys.path.insert(0, str(TOOLS))
selection = importlib.import_module("selection_experiment")
policy = importlib.import_module("selection_policy")


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")


def make_staging(path, count=42):
    path.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(path)
    db.executescript(selection.SCHEMA)
    for index in range(count):
        cid, ckey = f"candidate-{index:05d}", f"context-key-{index:05d}"
        text = f"I shared this natural sentence number {index} with the whole group."
        origins = json.dumps([{"sourceFamily": "tatoeba", "sourceVersion": "fixture-only",
                              "contextRef": f"tatoeba:{index + 1}", "meaningRef": f"tatoeba:{index + 100}"}])
        choices = json.dumps([[100, "shared", "exact-target-shared", "beginner"]])
        db.execute("INSERT INTO candidate VALUES(?,?,?,?,?,?,?,?,?)",
                   (cid, "everyday", "en", "de", ckey, text, f"Fixture translation {index}.", origins, None))
        db.execute("INSERT INTO context VALUES(?,?,?,?)", ("everyday", "en", ckey, text))
        db.execute("INSERT INTO context_analysis VALUES(?,?,?,?,?)", ("everyday", "en", ckey, 13, choices))
    db.executescript(selection.POST_STAGE_INDEXES)
    db.commit()
    db.close()


def make_producer(control):
    """Synthetic metadata exercises gates; its full-pool counts are never evidence."""
    paths = probe.producer_paths(control)
    make_staging(paths["staging"])
    size = paths["staging"].stat().st_size
    report = {"status": "admitted-everyday-selection-review", "publicationSafe": False, "morphologyUsed": False,
              "policy": {"maxTargetsPerLevel": 8000}, "qualityPolicy": {"id": "boundary-diversity-v2"},
              "selectedMaterial": {"logicalSha256": probe.HISTORICAL_LOGICAL},
              "stage": {"uniqueCandidates": 11099490, "duplicateCandidates": 0},
              "admittedInput": {"poolSha256": "p", "poolReportSha256": "r", "registrySha256": "g"}}
    profile = {"status": "complete-full-saved-input-diagnostic", "publicationSafe": False, "inputScope": "full-saved-input",
               "settings": {"runs": 1, "max_deck": 8000, "quality_policy": "boundary-diversity-v2", "measurement_mode": "stages"},
               "runs": [{"run": 1, "stagingBytes": size, "stageCounts": report["stage"], "stageMetrics": {"status": "complete"},
                         "outputIdentity": {"selectedLogicalSha256": probe.HISTORICAL_LOGICAL,
                                            "reportSha256": probe.canonical_hash(report)}}],
               "inputPins": {"pool": "p", "poolReport": "r", "registry": "g"}}
    save(paths["profile"], profile)
    save(paths["selectionReport"], report)
    save(paths["guard"], {"status": "child-exited-zero", "exitCode": 0})
    comparison = {"status": "complete-independent-control-comparison", "historicalLogicalIdentityMatches": True,
                  "reportedIntegrityMatches": True, "fullSelectedGzipIntegrityRead": True,
                  "profileFileSha256": probe.hash_file(paths["profile"]),
                  "selectionReportFileSha256": probe.hash_file(paths["selectionReport"]),
                  "stagingSha256": probe.hash_file(paths["staging"]), "stagingSizeBytes": size,
                  "independentlyComputedSelected": {"logicalSha256": probe.HISTORICAL_LOGICAL}}
    save(paths["comparison"], comparison)
    return paths, comparison, profile, report


def rebind(paths, comparison, profile, report):
    profile["runs"][0]["outputIdentity"]["reportSha256"] = probe.canonical_hash(report)
    save(paths["profile"], profile)
    save(paths["selectionReport"], report)
    comparison["profileFileSha256"] = probe.hash_file(paths["profile"])
    comparison["selectionReportFileSha256"] = probe.hash_file(paths["selectionReport"])
    save(paths["comparison"], comparison)


class ProbeTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="probe-fixture-", dir=HERE)
        self.root = Path(self.temporary.name)
        self.addCleanup(self.temporary.cleanup)

    def test_streamed_canonical_output_preserves_lists_and_unicode(self):
        value = {"targets": [{"text": "Straße 日本語", "contexts": ["second", "first"], "origins": [{"id": 2}, {"id": 1}]}],
                 "stats": {"rejected": 3, "candidateRows": 40}}
        path = self.root / "result.json"
        identity = probe.write_json_new(path, value)
        expected = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode() + b"\n"
        self.assertEqual(path.read_bytes(), expected)
        self.assertEqual(identity, {"sha256": hashlib.sha256(expected).hexdigest(), "sizeBytes": len(expected)})
        with self.assertRaises(FileExistsError):
            probe.write_json_new(path, value)

    def test_schema_and_exact_original_function_prefix(self):
        path = self.root / "stage.sqlite3"
        make_staging(path)
        with probe.open_readonly(path) as db:
            probe.verify_schema(db)
            identity = probe.prefix_identity(db, 40)
            self.assertEqual(identity["firstCandidateId"], "candidate-00000")
            self.assertEqual(identity["lastCandidateId"], "candidate-00039")
            adapter = probe.PrefixConnection(db, 40)
            targets, counts = selection.pair_evidence(adapter, *probe.PAIR, None, 12, diverse=True)
            self.assertEqual(counts["candidateRows"], 40)
            self.assertEqual(counts["eligibleTargetOccurrences"], 40)
            self.assertEqual(targets[0]["evidenceContexts"], 40)
            self.assertLess(len(targets[0]["contexts"]), 37)
            self.assertEqual(probe.prefix_identity(db, 40), identity)

    def test_readonly_write_rejected(self):
        path = self.root / "stage.sqlite3"
        make_staging(path, 2)
        db = probe.open_readonly(path)
        self.addCleanup(db.close)
        with self.assertRaises(sqlite3.OperationalError):
            db.execute("DELETE FROM candidate")

    def test_adapter_refuses_other_sql_pair_or_second_call(self):
        path = self.root / "stage.sqlite3"
        make_staging(path, 2)
        db = probe.open_readonly(path)
        self.addCleanup(db.close)
        for sql, params in (("DELETE FROM candidate", probe.PAIR), (probe.PAIR_SQL, ("everyday", "de", "en")),
                            (probe.PAIR_SQL + " LIMIT 1", probe.PAIR)):
            with self.assertRaises(ValueError):
                probe.PrefixConnection(db, 2).execute(sql, params)
        adapter = probe.PrefixConnection(db, 2)
        self.assertIsInstance(adapter.execute(probe.PAIR_SQL, probe.PAIR), sqlite3.Cursor)
        with self.assertRaisesRegex(ValueError, "more than one"):
            adapter.execute(probe.PAIR_SQL, probe.PAIR)

    def test_missing_analysis_cannot_shift_prefix(self):
        path = self.root / "stage.sqlite3"
        make_staging(path, 6)
        db = sqlite3.connect(path)
        db.execute("DELETE FROM context_analysis WHERE context_key='context-key-00000'")
        db.commit()
        db.close()
        db = probe.open_readonly(path)
        self.addCleanup(db.close)
        with self.assertRaisesRegex(ValueError, "lost/reordered"):
            probe.prefix_identity(db, 4)

    def test_prefix_requires_requested_count(self):
        path = self.root / "stage.sqlite3"
        make_staging(path, 2)
        db = probe.open_readonly(path)
        self.addCleanup(db.close)
        with self.assertRaisesRegex(ValueError, "short"):
            probe.prefix_identity(db, 3)

    def test_exact_feature_string_change_changes_digest(self):
        path = self.root / "stage.sqlite3"
        make_staging(path, 2)
        db = sqlite3.connect(path)
        before = probe.prefix_identity(db, 2)
        db.execute("UPDATE context_analysis SET choices_json=choices_json || ' '")
        db.commit()
        self.assertNotEqual(probe.prefix_identity(db, 2)["sevenValueRowsSha256"], before["sevenValueRowsSha256"])
        db.close()

    def test_staging_bytes_and_sidecars_fail_closed(self):
        path = self.root / "stage.sqlite3"
        make_staging(path, 2)
        comparison = {"stagingSha256": probe.hash_file(path), "stagingSizeBytes": path.stat().st_size}
        identity = probe.verify_staging(path, comparison)
        probe.verify_staging(path, comparison, identity)
        Path(str(path) + "-wal").write_bytes(b"untrusted")
        with self.assertRaisesRegex(ValueError, "sidecar"):
            probe.verify_staging(path, comparison)
        Path(str(path) + "-wal").unlink()
        comparison["stagingSha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "hash mismatch"):
            probe.verify_staging(path, comparison)

    def test_producer_gates_accept_complete_synthetic_metadata(self):
        control = self.root / "control"
        make_producer(control)
        self.assertEqual(probe.load_producer(control)[1]["historicalLogicalIdentityMatches"], True)

    def test_producer_missing_binding_refused(self):
        control = self.root / "control"
        paths, comparison, _, _ = make_producer(control)
        del comparison["stagingSha256"]
        save(paths["comparison"], comparison)
        with self.assertRaisesRegex(ValueError, "stagingSha256"):
            probe.load_producer(control)

    def test_producer_unsuccessful_guard_refused(self):
        control = self.root / "control"
        paths, *_ = make_producer(control)
        save(paths["guard"], {"status": "running", "exitCode": None})
        with self.assertRaisesRegex(ValueError, "guard"):
            probe.load_producer(control)

    def test_producer_incomplete_or_wrong_cap_or_morphology_refused(self):
        for field in ("status", "cap", "morphology", "logical", "canonical", "runs", "stages"):
            with self.subTest(field=field):
                control = self.root / field
                paths, comparison, profile, report = make_producer(control)
                if field == "status": profile["status"] = "incomplete"
                if field == "cap": profile["settings"]["max_deck"] = 12000
                if field == "morphology": report["morphologyUsed"] = True
                if field == "logical": report["selectedMaterial"]["logicalSha256"] = "0" * 64
                if field == "runs": profile["settings"]["runs"] = 2
                if field == "stages": profile["runs"][0]["stageMetrics"]["status"] = "incomplete"
                rebind(paths, comparison, profile, report)
                if field == "canonical":
                    report["extra"] = 1
                    save(paths["selectionReport"], report)
                    comparison["selectionReportFileSha256"] = probe.hash_file(paths["selectionReport"])
                    save(paths["comparison"], comparison)
                with self.assertRaises(ValueError):
                    probe.load_producer(control)

    def test_producer_profile_tamper_refused(self):
        control = self.root / "control"
        paths, *_ = make_producer(control)
        paths["profile"].write_text("{}")
        with self.assertRaisesRegex(ValueError, "binding mismatch"):
            probe.load_producer(control)

    def test_source_pins_and_registry_are_checked(self):
        repo = self.root / "repo"
        tools = repo / "tools" / "catalog"
        (tools / "sources").mkdir(parents=True)
        (tools / "a.py").write_text("pass\n")
        (tools / "requirements-selection-quality.txt").write_text("fixture\n")
        (tools / "sources" / "selection-quarantine.json").write_text("{}")
        (tools / "sources" / "catalogue-v2-sources.json").write_text("{}")
        pins = {name: probe.hash_file(tools / name) for name in
                ("a.py", "requirements-selection-quality.txt", "sources/selection-quarantine.json")}
        profile = {"pipelineSha256": pins, "inputPins": {"registry": probe.hash_file(tools / "sources/catalogue-v2-sources.json")}}
        report = {"selectionPipelineSha256": {"a.py": pins["a.py"]},
                  "qualityPolicy": {"sourceQuarantine": {"sha256": pins["sources/selection-quarantine.json"]}}}
        probe.verify_source_pins(repo, profile, report)
        (tools / "a.py").write_text("changed\n")
        with self.assertRaisesRegex(ValueError, "source changed"):
            probe.verify_source_pins(repo, profile, report)

    def test_new_unpinned_source_refused(self):
        repo = self.root / "repo"
        tools = repo / "tools" / "catalog"
        tools.mkdir(parents=True)
        (tools / "new.py").write_text("pass\n")
        with self.assertRaisesRegex(ValueError, "path sets differ"):
            probe.verify_source_pins(repo, {"pipelineSha256": {"old.py": "0" * 64}}, {})

    def test_fresh_baseline_profile_fixture_complete_equality(self):
        control = self.root / "control"
        make_producer(control)
        common = dict(repo=REPO, control_dir=control)
        with ExitStack() as stack:
            stack.enter_context(patch.object(probe, "PREFIX_ROWS", 40))
            stack.enter_context(patch.object(probe, "verify_source_pins", return_value={"fixture": "fixture"}))
            stack.enter_context(patch.object(probe, "verify_runtime", return_value={"fixtureRuntime": True}))
            stack.enter_context(patch.object(probe, "resource_preflight", return_value={"fixture": True}))
            policy._written_counts.cache_clear()
            baseline = probe.run(argparse.Namespace(**common, output_dir=self.root / "baseline", mode="baseline", reference_baseline=None), {"fixtureOnly": True})
            policy._written_counts.cache_clear()
            profiled = probe.run(argparse.Namespace(**common, output_dir=self.root / "profile", mode="cprofile",
                                                   reference_baseline=self.root / "baseline/PROBE.json"), {"fixtureOnly": True})
        self.assertEqual(baseline["outputIdentity"], profiled["outputIdentity"])
        self.assertTrue(profiled["baselineComparison"]["exactReturnedEvidenceEqual"])
        stats = pstats.Stats(str(self.root / "profile/PAIR.pstats"))
        self.assertTrue(any(key[2] == "choose_contexts" for key in stats.stats))
        self.assertEqual((self.root / "baseline/EVIDENCE.json").read_bytes(), (self.root / "profile/EVIDENCE.json").read_bytes())

    def test_wrong_baseline_contract_or_changed_output_refused(self):
        output = self.root / "baseline"
        output.mkdir()
        contract = {key: {"fixture": True} for key in ("producerBindings", "helperSha256", "settings", "runtime", "prefixIdentity", "sqliteConnection")}
        identity = probe.write_json_new(output / "EVIDENCE.json", {"targets": [], "stats": {}})
        reference = {**contract, "status": "complete-dense-pair-prefix-diagnostic", "mode": "baseline", "outputIdentity": identity}
        save(output / "PROBE.json", reference)
        probe.validate_baseline(output / "PROBE.json", contract)
        with self.assertRaisesRegex(ValueError, "contract differs"):
            probe.validate_baseline(output / "PROBE.json", {**contract, "settings": {"changed": True}})
        (output / "EVIDENCE.json").write_text("{}")
        with self.assertRaisesRegex(ValueError, "evidence changed"):
            probe.validate_baseline(output / "PROBE.json", contract)

    def test_existing_output_refused_before_producer_read(self):
        args = argparse.Namespace(repo=REPO, control_dir=self.root, output_dir=self.root, mode="baseline", reference_baseline=None)
        with self.assertRaisesRegex(ValueError, "must be new"):
            probe.run(args, {})

    def test_runtime_python_mismatch_fails_before_icu_access(self):
        with self.assertRaisesRegex(ValueError, "runtime differs"):
            probe.verify_runtime({"environment": {"python": "wrong"}}, {}, selection)

    def test_metadata_snapshot_refuses_same_size_rewrite(self):
        path = self.root / "metadata.json"
        path.write_text('{"a": 1}')
        snapshot = probe.snapshot_files([path])
        probe.verify_snapshot(snapshot)
        path.write_text('{"a": 2}')
        with self.assertRaisesRegex(ValueError, "bound evidence changed"):
            probe.verify_snapshot(snapshot)

    def test_sqlite_configuration_is_reported_readonly(self):
        path = self.root / "stage.sqlite3"
        make_staging(path, 2)
        db = probe.open_readonly(path)
        self.addCleanup(db.close)
        settings = probe.connection_settings(db)
        self.assertEqual(settings["query_only"], 1)
        self.assertIn("mmap_size", settings)
        self.assertIn("cache_size", settings)

    def test_resource_limits_applied_in_separate_tiny_process(self):
        code = "import dense_pair_probe as p,resource,json; print(json.dumps(p.install_limits()))"
        result = subprocess.run([sys.executable, "-B", "-c", code], cwd=HERE, capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)
        limits = json.loads(result.stdout)
        self.assertLessEqual(limits["addressSpaceBytes"]["hard"], 2 * 1024**3)
        self.assertLessEqual(limits["cpuSeconds"]["hard"], 300)


if __name__ == "__main__":
    unittest.main(verbosity=2)
