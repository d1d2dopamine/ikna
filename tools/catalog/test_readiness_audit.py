#!/usr/bin/env python3
"""Deterministic checks for the Part 4.5 Catalogue readiness audit."""

import json
import tempfile
import unittest
from pathlib import Path

import readiness_audit


def clean_meta():
    return {
        "input": {
            "catalogueVersion": 2,
            "indexVersion": 2,
            "buildVerified": True,
            "missingIndexAssets": [],
            "unindexedAssets": [],
        },
        "catalogue": {
            "cardsInDecks": 2000,
            "targetDeckMemberships": 2000,
            "contexts": 4000,
            "deckAssetBytes": 1000,
            "observedUncompressedBytes": 10000,
            "parseErrors": 0,
            "duplicateCardIds": 0,
            "duplicateTargetContextPairsWithinDecks": 0,
            "deckDeclarationMismatches": 0,
        },
        "targets": {
            "uniqueExactTargets": 1500,
            "targetsAcrossMultipleLevels": 25,
        },
        "metadata": {
            "badTargetOffsets": 0,
            "provenanceMissing": 0,
            "missingRequiredCardFields": {},
            "missingRequiredContextFields": {},
            "missingRequiredTokenFields": {},
            "tokens": 5000,
            "tokensWithNonIdentityLemma": 500,
            "tokensMissingLemma": 0,
            "emptyTargets": 0,
            "targetsLongerThan80Chars": 0,
            "contextsLongerThan500Chars": 0,
            "morphologyByLearningLanguage": {
                "en": {"tokens": 5000, "nonIdentityLemma": 500, "missingLemma": 0}
            },
        },
        "inventory": {
            "rawClientCapBytes": 24 * 1024 * 1024,
            "maxDeckTargets": 8000,
            "decksAtTargetCap": 0,
            "cardCount": {"min": 1000, "median": 1000, "p95": 1000, "max": 1000, "below100": 0, "below1000": 0},
            "decks": [
                {"id": "en-ru-everyday-beginner", "rawBytes": 10 * 1024 * 1024},
                {"id": "en-es-everyday-beginner", "rawBytes": 11 * 1024 * 1024},
            ],
        },
        "breakdown": {
            "bySourceFamily": [
                {"key": "tatoeba", "decks": 2, "cards": 2000, "uniqueTargets": 1500, "contexts": 4000}
            ]
        },
        "build": {"limits": {"maxDeckTargets": 8000}},
    }


class ReadinessAuditTests(unittest.TestCase):
    def test_clean_catalogue_passes(self):
        audit = readiness_audit.evaluate(clean_meta())
        self.assertEqual(audit["overall"], "PASS")
        self.assertEqual(audit["blockers"], [])

    def test_structural_corruption_fails(self):
        meta = clean_meta()
        meta["catalogue"]["duplicateCardIds"] = 2
        meta["metadata"]["badTargetOffsets"] = 1
        audit = readiness_audit.evaluate(meta)
        self.assertEqual(audit["overall"], "FAIL")
        self.assertTrue(any("Integrity" in item for item in audit["blockers"]))

    def test_thin_and_near_cap_are_warnings_not_blockers(self):
        meta = clean_meta()
        meta["inventory"]["cardCount"]["below1000"] = 1
        meta["inventory"]["decks"][0]["rawBytes"] = int(24 * 1024 * 1024 * 0.95)
        audit = readiness_audit.evaluate(meta)
        self.assertEqual(audit["overall"], "WARN")
        self.assertEqual(audit["blockers"], [])
        self.assertGreaterEqual(len(audit["warnings"]), 2)

    def test_over_client_cap_fails(self):
        meta = clean_meta()
        meta["inventory"]["decks"][0]["rawBytes"] = 25 * 1024 * 1024
        audit = readiness_audit.evaluate(meta)
        self.assertEqual(audit["overall"], "FAIL")
        self.assertTrue(any("Storage" in item for item in audit["blockers"]))

    def test_sample_manifest_is_counted(self):
        with tempfile.TemporaryDirectory() as temp:
            samples = Path(temp)
            (samples / "manifest.json").write_text(
                json.dumps({
                    "selection": "fixture",
                    "perDeckLimit": 20,
                    "decks": {
                        "en-ru-everyday-beginner": 20,
                        "en-es-everyday-beginner": 20,
                    },
                }),
                encoding="utf-8",
            )
            audit = readiness_audit.evaluate(clean_meta(), samples)
            self.assertTrue(audit["samples"]["present"])
            self.assertEqual(audit["samples"]["rows"], 40)
            self.assertEqual(audit["overall"], "PASS")


if __name__ == "__main__":
    unittest.main(verbosity=2)
