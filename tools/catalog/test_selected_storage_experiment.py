#!/usr/bin/env python3
"""Serialized round-trip and refusal regressions for selected-snapshot storage."""
import gzip
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import selected_storage_experiment as storage


def fixture(root):
    rows = []
    for meaning_lang, level, meaning in (("es", "beginner", "Nos importa el trabajo."),
                                          ("es", "middle", "Nos importa el trabajo."),
                                          ("fr", "beginner", "Ce travail nous importe.")):
        deck = f"en-{meaning_lang}-everyday-{level}"
        contexts = [{"context": "We care about the work. 🛠", "contextId": "tatoeba:1",
                     "meaning": meaning, "meaningId": "tatoeba:" + ("2" if meaning_lang == "es" else "3"),
                     "candidateId": "fixture-" + meaning_lang, "sourceFamilies": ["tatoeba"],
                     "alignmentScore": None, "tokenCount": 5,
                     "unknownContextEvidence": {"nested": [False, 7, None, "ß"]},
                     "origins": [{"sourceVersion": "fixture", "sourceFamily": "tatoeba",
                                  "contextRef": "tatoeba:1", "meaningRef": "fixture:" + meaning_lang,
                                  "attribution": {"contextContributor": "author", "meaningContributor": meaning_lang},
                                  "unknownOriginEvidence": [1, 2]}]}]
        if level == "beginner" and meaning_lang == "es":
            contexts.append({**contexts[0], "context": "They care about us.", "contextId": "tatoeba:4"})
        rows.append({"deckId": deck, "lang": "en", "meaningLang": meaning_lang,
                     "collection": "everyday", "level": level, "text": "care",
                     "freqRank": 900, "targetId": "t2:en:0123456789abcdef",
                     "unknownRootEvidence": {"preserve": True}, "contexts": contexts})
    raw = b"".join(storage.encode(row) for row in rows)
    selected = root / "selected.gz"
    selected.write_bytes(gzip.compress(raw, mtime=0))
    material = {"format": "selected-memberships-jsonl-gzip-v1", "completeIncludedDecisions": True,
                "publicationSafe": False, "sha256": storage.sha_file(selected), "sizeBytes": selected.stat().st_size,
                "logicalSha256": hashlib.sha256(raw).hexdigest(), "logicalSizeBytes": len(raw),
                "memberships": 3, "contexts": 4, "origins": 4,
                "decks": {row["deckId"]: 1 for row in rows}}
    snapshot = root / "snapshot.json"
    snapshot.write_bytes(storage.encode({"snapshotVersion": 1, "snapshotId": "fixture",
                                        "readyForPart12Measurement": True, "everydaySelectedSnapshotFrozen": True,
                                        "publicationSafe": False, "selectedMaterial": material}))
    return selected, snapshot


class StorageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.selected, self.snapshot = fixture(self.root)

    def test_serialized_unknown_fields_order_provenance_and_determinism(self):
        first = storage.run(self.selected, self.snapshot, self.root / "a")
        storage.run(self.selected, self.snapshot, self.root / "b")
        for mode in first["modes"]:
            self.assertEqual((3, 4, 4), (mode["memberships"], mode["contexts"], mode["origins"]))
        # Cross-meaning pooling retains exact origin/meaning coupling inline,
        # but can still reuse the identical learned-language payload.
        pair, language = first["modes"]
        self.assertEqual(2, len(pair["groups"]))
        self.assertEqual(1, len(language["groups"]))
        self.assertEqual(2, language["groups"][0]["uniqueContextPayloads"])
        self.assertEqual(2, language["groups"][0]["uniqueMeaningPayloads"])
        self.assertFalse(first["appReaderAccepted"])
        self.assertEqual(3, first["baseline"]["missingRequiredPackFields"]["tokens"])
        for path in (self.root / "a").rglob("*"):
            if path.is_file():
                self.assertEqual(path.read_bytes(), (self.root / "b" / path.relative_to(self.root / "a")).read_bytes())

    def test_wrong_raw_or_logical_pin_never_reports_success(self):
        snapshot = json.loads(self.snapshot.read_bytes())
        snapshot["selectedMaterial"]["sha256"] = "0" * 64
        self.snapshot.write_bytes(storage.encode(snapshot))
        with self.assertRaisesRegex(ValueError, "compressed identity"):
            storage.run(self.selected, self.snapshot, self.root / "a")
        self.assertFalse((self.root / "a").exists())
        self.selected, self.snapshot = fixture(self.root)
        snapshot = json.loads(self.snapshot.read_bytes())
        snapshot["selectedMaterial"]["logicalSha256"] = "0" * 64
        self.snapshot.write_bytes(storage.encode(snapshot))
        with self.assertRaisesRegex(ValueError, "logical identity"):
            storage.run(self.selected, self.snapshot, self.root / "a")
        self.assertFalse((self.root / "a/STORAGE.json").exists())

    def test_unaccepted_snapshot_and_output_reuse_refused(self):
        snapshot = json.loads(self.snapshot.read_bytes())
        snapshot["readyForPart12Measurement"] = False
        self.snapshot.write_bytes(storage.encode(snapshot))
        with self.assertRaisesRegex(ValueError, "accepted for Part12"):
            storage.run(self.selected, self.snapshot, self.root / "a")
        self.selected, self.snapshot = fixture(self.root)
        with self.assertRaisesRegex(ValueError, "new directory"):
            storage.run(self.selected, self.snapshot, self.root)
        self.assertTrue(self.selected.is_file())

    def test_serialized_payload_corruption_detected_even_with_updated_file_hash(self):
        result = storage.run(self.selected, self.snapshot, self.root / "a", ("pair",))
        group_root = self.root / "a/pair/en-es"
        metadata = json.loads((group_root / "group.json").read_bytes())
        pool = group_root / "contexts.jsonl.gz"
        rows = list(storage.json_rows(pool))
        rows[0]["context"] = "This was silently changed."
        pool.write_bytes(gzip.compress(b"".join(storage.encode(r) for r in rows), mtime=0))
        metadata["files"][0]["sha256"] = storage.sha_file(pool)
        decks = [d for d in result["baseline"]["decks"] if d["meaningLang"] == "es"]
        with self.assertRaisesRegex(ValueError, "serialized row differs"):
            storage.verify_group(self.root / "a/self-contained", decks, group_root, metadata)

    def test_invalid_reference_fails_before_reconstruction(self):
        with self.assertRaisesRegex(ValueError, "out of range"):
            storage.restore({"t": {}, "c": [[-1, 0, {}]]}, [{}], [{}])
        with self.assertRaisesRegex(ValueError, "out of range"):
            storage.restore({"t": {}, "c": [[True, 0, {}]]}, [{}], [{}])

    def test_baseline_closes_each_deck_before_opening_next(self):
        class TrackedWriter(storage.Writer):
            active = peak = 0

            def __enter__(self):
                TrackedWriter.active += 1
                TrackedWriter.peak = max(TrackedWriter.peak, TrackedWriter.active)
                return super().__enter__()

            def __exit__(self, *args):
                super().__exit__(*args)
                TrackedWriter.active -= 1

        with patch.object(storage, "Writer", TrackedWriter):
            baseline = storage.prepare_baseline(self.selected, storage.read_snapshot(self.snapshot), self.root / "base")
        self.assertEqual(1, TrackedWriter.peak)
        self.assertEqual(0, TrackedWriter.active)
        self.assertFalse((self.root / "base/storage-stage.sqlite3").exists())
        self.assertEqual(3, sum(d["serializedRowsVerified"] for d in baseline["decks"]))

    def test_partial_gzip_is_not_exposed_as_a_finished_asset(self):
        path = self.root / "closed-only.gz"
        with self.assertRaisesRegex(RuntimeError, "interrupted"):
            with storage.Writer(path) as writer:
                writer.write({"test": "before failure"})
                self.assertFalse(path.exists())
                raise RuntimeError("interrupted")
        self.assertFalse(path.exists())
        with storage.Writer(path) as writer:
            writer.write({"test": "complete"})
            self.assertFalse(path.exists())
        self.assertEqual([{"test": "complete"}], list(storage.json_rows(path)))


if __name__ == "__main__":
    unittest.main()
