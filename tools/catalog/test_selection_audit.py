#!/usr/bin/env python3
"""Saved-preview regressions; no corpus, network or publication."""
from copy import deepcopy
import gzip
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

import selection_audit as subject
from catalogue_v2 import target_id
from segmentation import prepare, word_spans


def fixture(lang="en", sentence="I have enough time to learn today.", text="time"):
    learn = sorted([lang, "ru"])
    pipeline = {n: subject.sha((subject.HERE / n).read_bytes()) for n in subject.SELECTION_FILES}
    registry_hash = subject.sha((subject.HERE / "sources/catalogue-v2-sources.json").read_bytes())
    pool = {"reportVersion": 1, "part": 7, "status": "admitted-source-pool-evidence", "publicationSafe": False,
            "sourceAdmissionPassed": True, "completeInputScan": True,
            "sourceDecision": {"tatoeba": "admitted", "massive": "excluded"}, "sourceVersion": "fixture-2026-10-07",
            "learn": learn, "meanings": learn, "registrySha256": registry_hash, "pipelineSha256": {},
            "environment": {"segmentation": prepare(learn)}, "sieve": {"functionTop": 60},
            "pool": {"sha256": "a" * 64, "logicalSha256": "b" * 64, "uniqueCandidates": 2}}
    raw = (json.dumps(pool) + "\n").encode()
    admitted = {"poolReportSha256": subject.sha(raw), "poolSha256": "a" * 64, "poolLogicalSha256": "b" * 64,
                "uniqueCandidates": 2, "poolEnvironment": pool["environment"], "poolPipelineSha256": {}}
    admitted.update({k: pool[k] for k in ("sourceVersion", "learn", "meanings", "registrySha256")})
    rows = []
    for a, b, t, context, meaning, cid, mid in [(lang, "ru", text, sentence, "Сегодня есть время учиться.", "1", "2"),
                                              ("ru", lang, "время", "Сегодня есть время учиться.", sentence, "2", "1")]:
        tid = target_id(a, t)
        origin = {"sourceFamily": "tatoeba", "sourceVersion": pool["sourceVersion"], "contextRef": "tatoeba:" + cid,
                  "meaningRef": "tatoeba:" + mid, "attribution": {"contextContributor": "fixture", "meaningContributor": "fixture"}}
        evidence = {"candidateId": subject.candidate_id("everyday", a, b, context, meaning), "contextId": origin["contextRef"],
                    "meaningId": origin["meaningRef"], "context": context, "meaning": meaning, "origins": [origin],
                    "sourceFamilies": ["tatoeba"], "tokenCount": len(word_spans(context, a)), "alignmentScore": None}
        rows.append({"lang": a, "meaningLang": b, "collection": "everyday", "level": "beginner", "text": t,
                     "targetId": tid, "lemmaKey": tid, "lemmaSource": "unavailable", "freqRank": 61,
                     "evidenceContexts": 1, "sourceFamilies": ["tatoeba"], "contexts": [evidence],
                     "deckId": f"{a}-{b}-everyday-beginner"})
    decks = []
    for a in learn:
        for b in learn:
            if a == b:
                continue
            for level in subject.core.LEVELS:
                count = int(level == "beginner")
                decks.append({"deckId": f"{a}-{b}-everyday-{level}", "lang": a, "meaningLang": b,
                              "collection": "everyday", "level": level, "eligibleTargets": count, "selectedTargets": count,
                              "decision": "publish" if count else "omit-no-quality-supply", "budgetRejected": 0,
                              "nearDuplicateContextsRejected": 0, "contextCollisionTargetsRejected": 0})
    report = {"reportVersion": 1, "part": 10, "status": "admitted-everyday-selection-review", "publicationSafe": False,
              "admittedInput": admitted, "stage": {"uniqueCandidates": 2, "inputCandidates": 2, "duplicateCandidates": 0},
              "reviewScope": {"previewIsCompleteSelection": False, "materialReviewCompleted": False,
                              "contentFrozen": False, "rowsPerPublishableDeck": 1}, "plannedDirectedPairs": 2,
              "decks": decks, "previewRows": 2, "morphologyUsed": False,
              "policy": {"previewLimitPerDeck": 1, "functionTop": 60, "maxTargetsPerLevel": 10,
                         "minDeck": 1, "thinDeck": 1, "contextsPerTarget": 3, "nearDuplicateJaccard": 0.85},
              "summary": {"publish": 2, "omit-no-quality-supply": 4, "eligibleTargets": 2, "selectedTargets": 2,
                          "budgetRejected": 0, "nearDuplicateContextsRejected": 0, "contextCollisionTargetsRejected": 0},
              "selectionPipelineSha256": pipeline, "selectionEnvironment": {"segmentation": prepare(learn)}}
    identity = {"archiveSha256": "d" * 64, "members": {subject.MEMBERS[0]: {"sha256": subject.sha(raw)}},
                "previewLogicalSha256": "e" * 64}
    return pool, report, rows, identity, raw


class AuditTests(unittest.TestCase):
    def test_valid_case_variant_and_non_bmp_utf16(self):
        pool, report, rows, identity, _ = fixture(sentence="😀 Time to learn something new today.")
        result, visible = subject.audit(pool, report, rows, identity)
        self.assertFalse(result["publicationSafe"])
        self.assertFalse(result["fullPoolBytesChecked"])
        self.assertEqual(result["preview"]["caseVariantContexts"], 1)
        self.assertEqual(visible[0]["contexts"][0]["highlight"], {"start": 3, "end": 7, "surface": "Time"})

    def test_reject_broken_hash_origin_identity_boundary_inventory(self):
        for kind in ("report-hash", "source", "version", "id", "candidate-id", "boundary", "duplicate", "inventory", "pipeline-path", "freeze", "icu"):
            with self.subTest(kind=kind):
                pool, report, rows, identity, _ = fixture()
                if kind == "report-hash": report["admittedInput"]["poolReportSha256"] = "f" * 64
                if kind == "source": rows[0]["contexts"][0]["origins"][0]["sourceFamily"] = "massive"
                if kind == "version": rows[0]["contexts"][0]["origins"][0]["sourceVersion"] = "other"
                if kind == "id": rows[0]["targetId"] = "t2:en:wrong"
                if kind == "candidate-id": rows[0]["contexts"][0]["candidateId"] = "c1:everyday:en-ru:" + "c" * 20
                if kind == "boundary":
                    c = rows[0]["contexts"][0]
                    c["context"] = "There is sometimes a new lesson."
                    c["candidateId"] = subject.candidate_id("everyday", "en", "ru", c["context"], c["meaning"])
                if kind == "duplicate": rows.append(deepcopy(rows[0]))
                if kind == "inventory": report["decks"].pop()
                if kind == "pipeline-path": report["selectionPipelineSha256"]["../../outside"] = "f" * 64
                if kind == "freeze": report["reviewScope"]["contentFrozen"] = True
                if kind == "icu": report["selectionEnvironment"]["segmentation"] = {"engine": "other"}
                with self.assertRaises(ValueError): subject.audit(pool, report, rows, identity)

    def test_one_character_cjk_is_not_automatically_rejected(self):
        for lang, sentence, text in [("zh", "我用两千日元买了这本书。", "用"), ("ko", "지난 방학 때 뭐 했어요?", "때"),
                                      ("ja", "神よ、我が願いを聞き給え。", "き")]:
            with self.subTest(lang=lang):
                pool, report, rows, identity, _ = fixture(lang, sentence, text)
                result, visible = subject.audit(pool, report, rows, identity)
                self.assertEqual(result["preview"]["memberships"], 2)
                self.assertEqual(visible[0]["auditFlags"], ["ja-single-hiragana"] if lang == "ja" else [])

    def test_similarity_is_only_a_review_signal(self):
        self.assertTrue(subject.similar("Если бы не вы, меня бы сегодня здесь не было.", "Если бы не ты, меня бы сегодня здесь не было.", "ru"))
        self.assertFalse(subject.similar("I have enough time to learn today.", "There was a time when nobody knew this.", "en"))

    def test_json_and_zip_identity_validation(self):
        pool, report, rows, identity, raw = fixture()
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "review.zip"
            with zipfile.ZipFile(path, "w") as z:
                z.writestr(subject.MEMBERS[0], raw)
                z.writestr(subject.MEMBERS[1], json.dumps(report))
                z.writestr(subject.MEMBERS[2], gzip.compress(b"\n".join(json.dumps(r).encode() for r in rows)))
            got_pool, got_report, got_rows, got_identity = subject.read_artifact(path, subject.sha(path.read_bytes()))
            subject.audit(got_pool, got_report, got_rows, got_identity)
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"): subject.read_artifact(path, "f" * 64)
        for raw in (b'{"key":1,"key":2}', b'{"a":NaN}', b'"\xff"'):
            with self.assertRaises((ValueError, UnicodeError)): subject.strict_json(raw)

    def test_viewer_does_not_embed_executable_source_text(self):
        result = {"input": {"archiveSha256": "d" * 64}}
        page = subject.viewer(result, [{"text": "</script><script>alert(1)</script>"}])
        self.assertNotIn("</script><script>alert(1)</script>", page)
        self.assertIn("\\u003c/script>", page)
        self.assertNotIn("innerHTML", page)

    def test_manual_notes_require_same_artifact_and_membership(self):
        _, _, rows, identity, _ = fixture()
        result = {"input": identity}
        notes = {"archiveSha256": identity["archiveSha256"], "cards": [
            {"deckId": rows[0]["deckId"], "targetId": rows[0]["targetId"], "status": "needs-review", "note": "Check register."}]}
        annotated = subject.attach_notes(result, rows, notes)
        self.assertEqual(annotated[0]["manualFinding"]["note"], "Check register.")
        notes["archiveSha256"] = "f" * 64
        with self.assertRaisesRegex(ValueError, "another review ZIP"): subject.attach_notes(result, rows, notes)


if __name__ == "__main__":
    unittest.main()
