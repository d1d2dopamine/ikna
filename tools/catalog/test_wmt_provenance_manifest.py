#!/usr/bin/env python3
"""Synthetic-only WMT metadata reconstruction and original-filter regressions.

No network, model, corpus file or selector execution. Importing the existing
join requires no optional analyzers; only that pure function is called here.
"""
from __future__ import annotations

from contextlib import redirect_stderr, redirect_stdout
from copy import deepcopy
import io
import itertools
import json
from pathlib import Path
import random
import tempfile
import unittest
from unittest.mock import patch

from replenishment_review import wmt_join
import wmt_provenance_manifest as manifest


REVISION = "a" * 40
NAMES = tuple(manifest.INPUTS)
ROW_FIELDS = {"documentId", "segmentId", "domain", "retained", "reason", "sourceSha256", "referenceSha256"}


def row(segment=1, locale="en-pl_PL", **updates):
    result = {"lp": locale, "domain": "social", "document_id": "fixture-document", "segment_id": segment,
              "source": "SYNTHETIC SOURCE SENTINEL.\nSecond fixture line.",
              "target": "SYNTHETIC HUMAN REFERENCE SENTINEL " + locale,
              "original_target": "SYNTHETIC MACHINE OUTPUT SENTINEL " + locale, "is_bad_source": False}
    result.update(updates)
    return result


class ClassifierTests(unittest.TestCase):
    def pair(self, **updates):
        return row(**updates), row(locale="en-ko_KR", **updates)

    def test_original_join_parity_and_decision_precedence(self):
        for bad_pl, bad_ko, domain, pl, ko in itertools.product(
                (False, True), (False, True), ("social", "speech", "news", "literary", "Social", ""),
                ("synthetic reference", "", " \n\t", "\u200b"),
                ("synthetic reference", "", " \n\t", "\u200b")):
            with self.subTest(bad_pl=bad_pl, bad_ko=bad_ko, domain=domain, pl=pl, ko=ko):
                left = row(is_bad_source=bad_pl, domain=domain, target=pl)
                right = row(locale="en-ko_KR", is_bad_source=bad_ko, domain=domain, target=ko)
                old_rows, old_stats = wmt_join([left], [right])
                new_rows, new_stats = manifest.reconstruct([left], [right])
                self.assertEqual(new_stats, old_stats)
                self.assertEqual(new_rows[0]["retained"], bool(old_rows))
                self.assertEqual(new_rows[0]["reason"], "retained" if old_rows else next(iter(old_stats["rejected"])))

    def test_original_join_parity_over_shuffled_multirow_fixture(self):
        rng = random.Random(200035)
        left, right = [], []
        for index in range(300):
            common = {"domain": rng.choice(("social", "speech", "news", "literary")),
                      "document_id": "fixture-" + str(index % 11), "source": "Synthetic source " + str(index)}
            left.append(row(index, **common, target=rng.choice(("", "Synthetic Polish fixture")),
                            is_bad_source=rng.choice((False, False, True))))
            right.append(row(index, "en-ko_KR", **common, target=rng.choice(("", "Synthetic Korean fixture")),
                             is_bad_source=rng.choice((False, False, True))))
        before = deepcopy((left, right))
        old_rows, old_stats = wmt_join(left, right)
        new_rows, new_stats = manifest.reconstruct(left, right)
        self.assertEqual(old_stats, new_stats)
        self.assertEqual([anchor for anchor, _, _ in old_rows],
                         [str(item["documentId"]) + ":" + str(item["segmentId"])
                          for item in new_rows if item["retained"]])
        self.assertEqual((left, right), before)
        rng.shuffle(left)
        rng.shuffle(right)
        self.assertEqual(manifest.reconstruct(left, right), (new_rows, new_stats))

    def test_raw_utf8_hashes_and_separate_original_ids(self):
        left, right = self.pair(document_id="fixture:part:007", segment_id=0,
                                source="  Synthetic e\u0301 fixture\r\n ", target="  Synthetic reference\n")
        right["target"] = "Synthetic 한국어 reference\t"
        rows, _ = manifest.reconstruct([left], [right])
        item = rows[0]
        self.assertEqual(item["documentId"], "fixture:part:007")
        self.assertIs(type(item["segmentId"]), int)
        self.assertEqual(item["segmentId"], 0)
        self.assertEqual(item["sourceSha256"], manifest.sha256(left["source"].encode("utf-8")))
        self.assertNotEqual(item["sourceSha256"], manifest.sha256(left["source"].strip().encode("utf-8")))
        for locale, original in (("pl_PL", left), ("ko_KR", right)):
            self.assertEqual(item["referenceSha256"][locale], manifest.sha256(original["target"].encode("utf-8")))

    def test_duplicate_and_mismatched_anchors_refuse(self):
        left, right = self.pair()
        for a, b in (([left, left], [right]), ([left], [right, right]),
                     ([left], []), ([], [right]), ([left], [{**right, "segment_id": 2}]),
                     ([left], [{**right, "document_id": "another-fixture"}])):
            with self.subTest(a=len(a), b=len(b)), self.assertRaises(ValueError):
                manifest.reconstruct(a, b)

    def test_source_and_domain_mismatches_refuse_even_for_bad_sources(self):
        left, right = self.pair(is_bad_source=True)
        for key, value in (("source", "MISMATCHED SYNTHETIC SOURCE SENTINEL"), ("domain", "speech")):
            with self.subTest(key=key):
                changed = {**right, key: value}
                with self.assertRaises(ValueError):
                    wmt_join([left], [changed])
                with self.assertRaises(ValueError) as error:
                    manifest.reconstruct([left], [changed])
                self.assertNotIn(value, str(error.exception))

    def test_missing_fields_and_unexpected_schema_refuse(self):
        left, right = self.pair()
        for field in manifest.FIELDS:
            changed = {key: value for key, value in left.items() if key != field}
            with self.subTest(field=field), self.assertRaises(ValueError):
                manifest.reconstruct([changed], [right])
        for invalid in (None, [], "SYNTHETIC LEAK SENTINEL", {**left, "extra": "SYNTHETIC LEAK SENTINEL"}):
            with self.subTest(invalid_type=type(invalid)), self.assertRaises(ValueError):
                manifest.reconstruct([invalid], [right])

    def test_invalid_flag_types_and_locale_refuse(self):
        left, right = self.pair()
        for value in (None, 0, 1, "false", [], {}):
            for side in (0, 1):
                rows = [dict(left), dict(right)]
                rows[side]["is_bad_source"] = value
                with self.subTest(side=side, value=value), self.assertRaises(ValueError):
                    manifest.reconstruct([rows[0]], [rows[1]])
        for locale in ("en-pl", "en-ko_KR", "pl_PL", "", None):
            with self.subTest(locale=locale), self.assertRaises(ValueError):
                manifest.reconstruct([{**left, "lp": locale}], [right])
        with self.assertRaises(ValueError):
            manifest.reconstruct([left], [{**right, "lp": "en-pl_PL"}])

    def test_malformed_anchor_and_text_types_refuse(self):
        left, right = self.pair()
        for key, values in {
                "document_id": (None, "", " \n", 1, [], True),
                "segment_id": (None, "1", 1.0, True, -1, [], {}),
                "domain": (None, [], 1), "source": (None, [], 1),
                "target": (None, [], 1), "original_target": (None, [], 1)}.items():
            for value in values:
                with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                    manifest.reconstruct([{**left, key: value}], [right])

    def test_json_duplicates_bad_encoding_nonfinite_and_malformed_refuse(self):
        for data in (b'{"target":"SYNTHETIC LEAK SENTINEL","target":"other"}',
                     b'{"source":"SYNTHETIC LEAK SENTINEL",', b'\xff', b'{"segment_id":NaN}'):
            with self.subTest(data=data), self.assertRaises(ValueError) as error:
                manifest._parse_rows(data)
            self.assertNotIn("SYNTHETIC LEAK SENTINEL", str(error.exception))

    def test_blank_lines_follow_original_reader(self):
        encoded = ("\n \t\n" + json.dumps(row()) + "\r\n\n").encode()
        self.assertEqual(manifest._parse_rows(encoded), [row()])


class FileGateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="ikna-wmt-metadata-fixture-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.primary = self.root / "synthetic-input"
        self.primary.mkdir()
        self.manifest_path = self.root / "FILES.json"
        self.revisions_path = self.root / "REVISIONS.json"
        self.expected_path = self.root / "REPLENISHMENT.json"
        self.out = self.root / "result" / "WMT-PROVENANCE.json"
        self.left, self.right = [], []
        for segment, updates in enumerate(({}, {"is_bad_source": True, "domain": "news", "target": ""},
                                          {"domain": "news", "target": ""}, {"domain": "speech", "target": ""}), 1):
            self.left.append(row(segment, **updates))
            self.right.append(row(segment, "en-ko_KR", **updates))
        self.write_fixture()

    def write_fixture(self):
        self.pins = []
        for name, rows in zip(NAMES, (self.left, self.right)):
            data = ("\n".join(json.dumps(value, ensure_ascii=False) for value in rows) + "\n").encode("utf-8")
            (self.primary / name).write_bytes(data)
            self.pins.append({"file": name, "sizeBytes": len(data), "sha256": manifest.sha256(data),
                              "url": "https://huggingface.co/datasets/google/wmt24pp/resolve/"
                              + REVISION + "/" + manifest.INPUTS[name] + ".jsonl"})
        _, stats = wmt_join(self.left, self.right)
        self.expected = {"wmt24pp": {**stats, "sharedAnchorsVerifiedLanguages": 11},
                         "revisions": {"wmt24pp": REVISION}, "inputFiles": deepcopy(self.pins),
                         "pipelineSha256": {"replenishment_review.py": manifest.sha256(
                             (manifest.HERE / "replenishment_review.py").read_bytes())},
                         "publicationSafe": False, "sourceAdmissionChanged": False,
                         "catalogueBuilt": False, "semanticAccuracyCertified": False}
        self.write_json(self.manifest_path, self.pins)
        self.write_json(self.revisions_path, {"wmt24pp": REVISION})
        self.write_json(self.expected_path, self.expected)

    @staticmethod
    def write_json(path, value):
        path.write_text(json.dumps(value), encoding="utf-8")

    def run_fixture(self):
        return manifest.run(self.primary, self.manifest_path, self.revisions_path, self.expected_path)

    def cli(self, out=None):
        stdout, stderr = io.StringIO(), io.StringIO()
        with redirect_stdout(stdout), redirect_stderr(stderr):
            status = manifest.main(["--primary", str(self.primary), "--manifest", str(self.manifest_path),
                                    "--revisions", str(self.revisions_path), "--expected", str(self.expected_path),
                                    "--out", str(out or self.out)])
        return status, stdout.getvalue(), stderr.getvalue()

    def test_exact_aggregate_metadata_only_and_determinism(self):
        first = self.run_fixture()
        self.assertEqual(first, self.run_fixture())
        self.assertEqual(first["wmt24pp"], {"sourceSegments": 4, "retainedDomainSegments": 1,
                                          "rejected": {"bad-source": 1, "outside-everyday-domains": 1,
                                                       "empty-human-reference": 1}})
        self.assertTrue(first["expectedAggregateMatched"])
        self.assertEqual(first["rightsStatus"], "unresolved")
        for field in ("publicationSafe", "sourceAdmissionChanged", "catalogueBuilt", "semanticAccuracyCertified",
                      "otherTargetLocalesReverified"):
            self.assertIs(first[field], False)
        self.assertEqual(first["verifiedTargetLocales"], ["pl_PL", "ko_KR"])
        self.assertNotIn("sharedAnchorsVerifiedLanguages", first["wmt24pp"])
        serialized = json.dumps(first)
        self.assertNotIn(str(self.root), serialized)
        for text in ("SYNTHETIC SOURCE SENTINEL", "SYNTHETIC HUMAN REFERENCE SENTINEL",
                     "SYNTHETIC MACHINE OUTPUT SENTINEL", '"original_target"', '"source"', '"target"'):
            self.assertNotIn(text, serialized)
        for item in first["rows"]:
            self.assertEqual(set(item), ROW_FIELDS)
            self.assertEqual(set(item["referenceSha256"]), {"pl_PL", "ko_KR"})

    def test_cli_repeat_bytes_and_safe_stdout(self):
        self.assertEqual(self.cli()[0], 0)
        second = self.out.with_name("second.json")
        status, stdout, stderr = self.cli(second)
        self.assertEqual((status, stderr), (0, ""))
        self.assertEqual(self.out.read_bytes(), second.read_bytes())
        self.assertNotIn("SYNTHETIC", stdout)
        self.assertEqual(json.loads(stdout)["rightsStatus"], "unresolved")

    def test_hash_and_size_failures_before_any_parsing(self):
        # pl is the SECOND pin in sorted file order. Even the valid ko file must
        # not be parsed until verification of this second file has succeeded.
        path = self.primary / NAMES[0]
        good = path.read_bytes()
        for bad in (b"X" + good[1:], good + b"\n", good[:-1]):
            path.write_bytes(bad)
            with self.subTest(size=len(bad)), patch.object(manifest, "_parse_rows") as parse:
                with self.assertRaises(ValueError):
                    self.run_fixture()
                parse.assert_not_called()
        self.assertFalse(self.out.exists())

    def test_parse_uses_already_verified_bytes(self):
        original_parse = manifest._parse_rows
        def replace_after_verification(data):
            for name in NAMES:
                (self.primary / name).write_bytes(b"REPLACED SYNTHETIC INPUT")
            return original_parse(data)
        with patch.object(manifest, "_parse_rows", side_effect=replace_after_verification):
            self.assertEqual(self.run_fixture()["wmt24pp"]["sourceSegments"], 4)

    def test_only_two_requested_files_are_needed(self):
        extra = [{"file": "wmt24pp--en-de_DE.jsonl", "error": "not downloaded"},
                 {"file": "mkqa--dataset--mkqa.jsonl.gz", "error": "not downloaded"}]
        self.write_json(self.manifest_path, extra + list(reversed(self.pins)))
        report = self.run_fixture()
        self.assertEqual([item["file"] for item in report["inputFiles"]], sorted(NAMES))

    def test_missing_duplicate_and_malformed_pins_refuse(self):
        cases = [self.pins[:1], self.pins + [self.pins[0]], {},
                 [{**self.pins[0], "file": "../" + NAMES[0]}, self.pins[1]]]
        for update in ({"sizeBytes": True}, {"sizeBytes": -1}, {"sha256": "0" * 63},
                       {"sha256": "G" * 64}, {"error": "unavailable"},
                       {"url": "https://example.invalid/unpinned"}):
            cases.append([{**self.pins[0], **update}, self.pins[1]])
        for value in cases:
            self.write_json(self.manifest_path, value)
            with self.subTest(value=value), self.assertRaises(ValueError):
                self.run_fixture()

    def test_expected_pins_revision_and_original_source_must_match(self):
        changes = [lambda value: value["inputFiles"][0].update(sha256="0" * 64),
                   lambda value: value["revisions"].update(wmt24pp="b" * 40),
                   lambda value: value["pipelineSha256"].update({"replenishment_review.py": "0" * 64})]
        for change in changes:
            value = deepcopy(self.expected)
            change(value)
            self.write_json(self.expected_path, value)
            with self.assertRaises(ValueError):
                self.run_fixture()
        self.write_json(self.expected_path, self.expected)
        for invalid in (None, "main", "g" * 40, []):
            self.write_json(self.revisions_path, {"wmt24pp": invalid})
            with self.subTest(revision=invalid), self.assertRaises(ValueError):
                self.run_fixture()

    def test_aggregate_drift_and_rejection_reason_drift_refuse_without_output(self):
        for value in ({"sourceSegments": 5, "retainedDomainSegments": 2, "rejected": self.expected["wmt24pp"]["rejected"]},
                      {"sourceSegments": 4, "retainedDomainSegments": 1,
                       "rejected": {"bad-source": 2, "outside-everyday-domains": 1}},
                      {"sourceSegments": 4, "retainedDomainSegments": 2,
                       "rejected": {"bad-source": 1, "outside-everyday-domains": 1}}):
            changed = {**self.expected, "wmt24pp": value}
            self.write_json(self.expected_path, changed)
            status, stdout, stderr = self.cli()
            self.assertEqual((status, stdout), (1, ""))
            self.assertIn("aggregate differs", stderr)
            self.assertFalse(self.out.exists())

    def test_invalid_expected_counts_flags_and_reasons_refuse(self):
        for update in ({"sourceSegments": True}, {"sourceSegments": -1}, {"retainedDomainSegments": 1.0},
                       {"rejected": {"new-reason": 3}}, {"rejected": {"bad-source": -1}},
                       {"rejected": {"bad-source": True}}, {"rejected": {"bad-source": 0}}, {"rejected": []}):
            changed = {**self.expected, "wmt24pp": {**self.expected["wmt24pp"], **update}}
            self.write_json(self.expected_path, changed)
            with self.subTest(update=update), self.assertRaises(ValueError):
                self.run_fixture()
        for key in ("publicationSafe", "sourceAdmissionChanged", "catalogueBuilt", "semanticAccuracyCertified"):
            self.write_json(self.expected_path, {**self.expected, key: True})
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.run_fixture()

    def test_missing_file_and_malformed_verified_json_have_safe_errors(self):
        path = self.primary / NAMES[0]
        path.unlink()
        status, stdout, stderr = self.cli()
        self.assertEqual((status, stdout), (1, ""))
        self.assertFalse(self.out.exists())
        self.assertNotIn("SYNTHETIC SOURCE", stderr)
        # A fixture pin can intentionally bind malformed bytes; syntax still
        # fails without printing their contents or creating a result file.
        bad = b'{"target":"SYNTHETIC LEAK SENTINEL",\n'
        path.write_bytes(bad)
        self.pins[0].update(sizeBytes=len(bad), sha256=manifest.sha256(bad))
        self.expected["inputFiles"] = deepcopy(self.pins)
        self.write_json(self.manifest_path, self.pins)
        self.write_json(self.expected_path, self.expected)
        status, stdout, stderr = self.cli()
        self.assertEqual((status, stdout), (1, ""))
        self.assertNotIn("SYNTHETIC LEAK SENTINEL", stderr)
        self.assertFalse(self.out.exists())

    def test_output_cannot_overwrite_or_enter_input_directory(self):
        self.out.parent.mkdir()
        self.out.write_text("KEEP SYNTHETIC RESULT", encoding="utf-8")
        for path in (self.out, self.primary / "new.json", self.primary / NAMES[0], self.manifest_path):
            before = path.read_bytes() if path.exists() else None
            self.assertEqual(self.cli(path)[0], 1)
            self.assertEqual(path.read_bytes() if path.exists() else None, before)
        link = self.root / "source-alias"
        link.symlink_to(self.primary, target_is_directory=True)
        self.assertEqual(self.cli(link / "new.json")[0], 1)
        dangling = self.root / "dangling.json"
        dangling.symlink_to(self.root / "not-created.json")
        self.assertEqual(self.cli(dangling)[0], 1)

    def test_committed_round20_metadata_contract_is_still_605(self):
        # Read evidence metadata only, never the original corpus files.
        evidence = manifest.HERE.parent.parent / "docs" / "evidence" / "everyday-round20"
        expected = json.loads((evidence / "REPLENISHMENT.json").read_text(encoding="utf-8"))
        pins = json.loads((evidence / "FILES.json").read_text(encoding="utf-8"))
        revisions = json.loads((evidence / "REVISIONS.json").read_text(encoding="utf-8"))
        self.assertEqual(manifest._expected_aggregate(expected),
                         {"sourceSegments": 998, "retainedDomainSegments": 605,
                          "rejected": {"bad-source": 38, "outside-everyday-domains": 355}})
        self.assertEqual(manifest._pins(pins, revisions["wmt24pp"]),
                         manifest._pins(expected["inputFiles"], revisions["wmt24pp"]))
        self.assertEqual(expected["pipelineSha256"]["replenishment_review.py"],
                         manifest.sha256((manifest.HERE / "replenishment_review.py").read_bytes()))


if __name__ == "__main__":
    unittest.main()
